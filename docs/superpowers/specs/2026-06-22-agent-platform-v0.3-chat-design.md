# Agent 平台 V0.3 普通聊天设计

## 范围

实现 `docs/agent-platform/TASKS.md` 和 `API.md` 中定义的 V0.3 非流式 Agent 聊天闭环。

本版本包含：

- OpenAI 兼容的非流式模型调用。
- 模型客户端抽象和按供应商类型分发。
- `POST /api/agent/chat` 聊天接口。
- 首次对话自动创建会话。
- 持久化用户消息和 assistant 消息。
- 成功和失败场景均写入 Agent 运行记录。
- 模型供应商 API Key 在新增/编辑时使用 AES 加密，调用模型前解密。

本版本不包含：

- SSE 流式响应。
- 工具调用。
- 多轮工具执行。
- 消息编辑和删除。
- Azure 或 Anthropic 原生协议实现。

## 架构

### API 模块

在 `com.aether.agent.model` 下新增模型调用内部契约：

- `ModelClient`：模型客户端接口。
- `ModelChatMessage`：单条模型上下文消息，包含 `role` 和 `content`。
- `ModelChatRequest`：模型调用请求，包含供应商、Agent 配置和上下文消息。
- `ModelChatResponse`：模型调用响应，包含 assistant 内容、token 用量、模型名和原始响应。

在 `com.aether.agent.service` 下新增 `AgentChatService`：

- `AgentMessageVo chat(AgentChatDto dto)`。

### Biz 模块

新增 `ModelClientFactory`：

- 通过 Spring 注入所有 `ModelClient` 实现。
- 使用 `supports(provider.getType())` 选择匹配的客户端。
- 没有匹配客户端时抛出 `503` 类型业务异常。

新增 `OpenAIModelClient`：

- 支持 `openai` 和 `local` 供应商类型。
- 调用 `POST {apiBaseUrl}/v1/chat/completions`。
- 请求体包含 `model`、`messages`、`temperature`、`max_tokens`、`stream=false`。
- 解析 `choices[0].message.content` 和 `usage` 中的 token 字段。
- 使用 `AesUtil.decrypt` 解密供应商 API Key，并写入 `Authorization: Bearer ...` 请求头。
- 设置明确超时时间，将超时和供应商调用失败转换为 `ServerException`。

新增 `AgentChatServiceImpl`：

- 校验请求参数、当前用户、Agent、模型供应商和会话。
- `conversationId` 为空时自动创建会话。
- 调用模型前先保存用户消息。
- 读取最近会话历史，并在最前面拼接 Agent 的 system prompt。
- 通过 `ModelClientFactory` 调用模型。
- 保存 assistant 消息。
- 更新会话消息数。
- 成功和失败都写入 `agent_run`。

### Admin 模块

新增 `AgentChatController`：

- 接口：`POST /api/agent/chat`。
- 权限路径：`/agent/chat`。
- 请求体：`AgentChatDto`。
- 响应：`WebResponse<AgentMessageVo>`。

更新 `ModelProviderController`：

- 新增/编辑供应商时，如果传入 `apiKey`，使用 `AesUtil.encrypt` 加密后保存。
- 列表和详情接口不返回 `apiKey`。
- 不对已有明文供应商数据做自动兼容或迁移；已有测试数据需要通过管理接口重新保存一次。

## 数据流

1. Controller 接收 `agentId`、可选 `conversationId` 和 `message`。
2. Service 从 `CurrentUser` 读取当前用户 ID。
3. 校验 Agent 存在、未删除、并且 `status = 1`。
4. 校验模型供应商存在、未删除、并且 `status = 1`。
5. 如果 `conversationId` 为空，为当前用户和 Agent 创建 `agent_conversation`。
6. 如果 `conversationId` 不为空，校验会话属于当前用户、属于当前 Agent、未删除且未关闭。
7. 保存 `user` 角色的 `agent_message`。
8. 从 system prompt 和会话历史组装模型上下文。
9. `ModelClientFactory` 选择匹配的模型客户端。
10. 客户端调用模型供应商并返回统一响应。
11. 保存 `assistant` 角色的 `agent_message`。
12. 更新会话 `message_count`。
13. 保存 `agent_run`，记录模型、供应商、token、耗时、状态和消息关联。
14. Controller 返回 `WebResponse.OK(AgentMessageVo)`。

## 错误处理

- `agentId` 或 `message` 为空：返回 `400`。
- 当前用户不存在：返回 `401`。
- Agent、模型供应商或会话不存在：返回 `404`。
- Agent 未启用、供应商禁用或会话已关闭：返回 `422`。
- 供应商类型不支持：返回 `503`。
- 模型供应商调用超时：返回 `503`。
- 模型响应格式异常或其他调用失败：返回 `500`。

如果失败发生在用户消息可以保存之后，`AgentChatServiceImpl` 应写入失败状态的 `agent_run`，记录输入内容、模型/供应商标识、耗时和错误信息。

## 测试与验证

运行：

```sh
mvn clean compile -DskipTests
```

当前工作区使用 `.jdks` 下的 JDK：

```powershell
$env:JAVA_HOME = "C:\Users\23672\.jdks\ms-17.0.19"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "C:\Users\23672\.m2\wrapper\dists\apache-maven-3.9.10-bin\53h08a94dg6djh6umvruv7q564\apache-maven-3.9.10\bin\mvn.cmd" clean compile -DskipTests
```

手动验证需要先配置可用的 `agent_model_provider` 和启用状态的 `agent_definition`。使用有效 Bearer Token 调用
`POST /api/agent/chat` 后，应确认数据库中创建或更新会话、写入两条消息，并生成一条运行记录。
