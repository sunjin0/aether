# Agent 平台 V0.4 SSE 流式响应设计

## 范围

实现 `docs/agent-platform/TASKS.md` 和 `API.md` 中定义的 V0.4 SSE 流式聊天能力。

本版本包含：

- `GET /api/agent/chat/stream` SSE 端点。
- 基于 OpenAI 兼容接口 `stream=true` 的真实流式模型调用。
- SSE 事件类型：`message`、`error`、`done`，并保留 `tool_call` 事件格式。
- 连接超时、客户端断开和模型调用异常处理。
- 流式结束后保存完整 assistant 消息。
- 成功和失败场景写入 `agent_run`。

本版本不包含：

- WebFlux 架构迁移。
- 工具调用执行闭环。
- 按 chunk 持久化消息。
- 对非 OpenAI 兼容协议的原生流式实现。

## 架构

### API 模块

扩展模型调用契约：

- 新增 `ModelStreamCallback`，由模型客户端在收到文本分片、工具调用片段、完成、错误时回调。
- 扩展 `ModelClient`，新增 `stream(ModelChatRequest request, ModelStreamCallback callback)`。
- 新增 `ModelStreamResponse` 或等价结果对象，用于返回最终模型名、token 用量、完整内容和原始结束信息。

扩展聊天服务契约：

- 新增 `AgentStreamCallback`，面向业务层事件，包含 `onMessage`、`onToolCall`、`onError`、`onDone`、`isClosed`。
- 扩展 `AgentChatService`，新增 `void stream(AgentChatDto dto, AgentStreamCallback callback)`。

### Biz 模块

扩展 `OpenAIModelClient`：

- 请求 `POST {apiBaseUrl}/v1/chat/completions`。
- 请求体包含 `model`、`messages`、`temperature`、`max_tokens`、`stream=true`。
- 使用带超时的 HTTP 连接逐行读取响应。
- 解析 OpenAI 兼容 SSE 行：`data: {...}` 和 `data: [DONE]`。
- 对 `choices[0].delta.content` 调用 `callback.onMessage(chunk)`。
- 对 `choices[0].delta.tool_calls` 调用工具调用回调，但不执行工具。
- 聚合完整 assistant 内容，流结束后返回最终结果。

扩展 `AgentChatServiceImpl`：

- 复用 V0.3 校验逻辑：参数、当前用户、Agent、供应商、会话。
- `conversationId` 为空时自动创建会话。
- 调用模型前保存 user 消息。
- 组装 system prompt 和最近历史消息。
- 调用 `ModelClient.stream()` 并将模型 chunk 转发为业务回调事件。
- 流式结束后保存完整 assistant 消息。
- 更新会话消息数。
- 保存成功 `agent_run`。
- 失败时发送错误事件并保存失败 `agent_run`。

### Admin 模块

扩展 `AgentChatController`：

- 新增 `GET /api/agent/chat/stream`。
- Query 参数：`agentId`、`conversationId`、`message`。
- 返回 `SseEmitter`，响应类型 `text/event-stream`。
- 使用异步线程执行 `agentChatService.stream()`，避免阻塞 MVC 请求线程。
- Controller 只负责 SSE 事件发送和连接生命周期，业务校验与持久化留在 service。

## 数据流

1. 客户端请求 `GET /api/agent/chat/stream?agentId=...&conversationId=...&message=...`。
2. Controller 创建 `SseEmitter` 并启动异步任务。
3. Service 校验当前用户、Agent、模型供应商和会话。
4. 如果未传入 `conversationId`，创建新会话。
5. 保存 user 消息。
6. 组装模型上下文。
7. `ModelClientFactory` 选择支持供应商类型的模型客户端。
8. `OpenAIModelClient` 发送 `stream=true` 请求并逐行读取 provider SSE。
9. 每个文本分片通过 `message` 事件发送给客户端。
10. provider 返回 `[DONE]` 后，Service 保存完整 assistant 消息。
11. Service 更新会话消息数并保存成功 `agent_run`。
12. Controller 发送 `done` 事件并完成 SSE 连接。

## SSE 事件格式

`message`：

```json
{"chunk":"你好","conversationId":"100","messageId":null}
```

`tool_call`：

```json
{"toolName":"weather","toolCallId":"call_123","arguments":{"city":"北京"}}
```

`error`：

```json
{"code":500,"message":"模型调用失败"}
```

`done`：

```json
{"conversationId":"100","messageId":"1000","totalTokens":50}
```

V0.4 的 `message` 事件在 assistant 消息落库前发送，因此 `messageId` 为 `null`。真实 `messageId` 在 `done` 事件中返回。

## 错误处理

- `agentId` 或 `message` 为空：发送 `error` 并关闭连接。
- 当前用户不存在：发送 `error` 并关闭连接。
- Agent、模型供应商或会话不存在：发送 `error` 并关闭连接。
- Agent 未启用、供应商禁用或会话已关闭：发送 `error` 并关闭连接。
- 供应商类型不支持：发送 `error` 并关闭连接。
- 模型供应商调用超时、断流或响应格式异常：发送 `error`，保存失败 `agent_run`，关闭连接。
- 客户端断开：停止继续发送；如果已经进入模型调用，按失败 `agent_run` 记录连接中断。

V0.4 不保存不完整 assistant 消息，避免把半截回复当成正式会话消息。

## 测试与验证

新增或扩展测试：

- `AgentChatServiceImpl` 流式单元测试：验证会话创建、user 消息保存、chunk 回调、assistant 消息保存、run 保存。
- `AgentChatServiceImpl` 流式失败测试：验证发送错误回调并保存失败 run。
- `OpenAIModelClient` 流式解析测试：使用模拟 OpenAI SSE 文本验证 delta 内容拼接和 `[DONE]` 处理。

运行：

```sh
mvn clean compile -DskipTests
```

如本地测试依赖可用，运行相关模块测试：

```sh
mvn test -pl biz -am -DfailIfNoTests=false
mvn test -pl admin -am -DfailIfNoTests=false
```

手动验证需要先配置可用的 OpenAI 兼容 `agent_model_provider` 和启用状态的 `agent_definition`。使用有效 Bearer Token 调用
`GET /api/agent/chat/stream` 后，应确认客户端能收到 `message` 分片和最终 `done`，数据库写入 user/assistant 消息，并生成运行记录。
