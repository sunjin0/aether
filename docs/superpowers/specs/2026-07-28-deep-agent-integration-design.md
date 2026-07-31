# 外部 Deep Agent 接入设计

## 目标

为 `executionMode=DEEP` 的 Agent 接入异步外部 Deep Agent 服务。前端仍通过既有的 `POST /api/agent/chat/stream` 开始聊天并接收 SSE；Java Admin 负责创建外部任务、接收签名回调、持久化审计信息并转换 SSE 事件。标准模式 `STANDARD` 的聊天流程不改变。

## 范围

- Agent 定义支持 `STANDARD` 和 `DEEP` 两种执行模式，默认使用 `STANDARD`。
- Deep 模式仅支持流式聊天。`POST /api/agent/chat` 收到 Deep Agent 请求时返回 `422`。
- Deep 模式将本地 `agent_run.id` 作为外部 `run_id`，以同一个 ID 贯穿 Java、外部服务和前端。
- Java 对外部服务的创建任务请求及外部服务对 Java 的回调均使用 HMAC-SHA256 签名。
- 外部事件按 `event_id` 幂等落库到 `agent_run_step`，并实时转发为前端 `run_step` SSE。
- 前端断开后不重放 SSE；通过运行详情和步骤列表恢复进度。
- 每次 Deep Run 生成短期、最小授权的 MCP 委托 JWT。

## 非目标

- 不改造标准 Agent 的模型调用、工具审批和交互式提问流程。
- 不让浏览器直接请求外部 Deep Agent 服务。
- 不实现 Java 侧的任务超时扫描器；外部服务以失败回调报告其执行超时。
- 不新增 Deep Agent 专属的 Agent 配置字段。

## 执行与数据流

1. Dashboard 调用 `POST /api/agent/chat/stream`。
2. Java 根据 Agent 定义的 `executionMode` 路由。标准模式走现有逻辑；Deep 模式进入独立 Deep Run 服务。
3. Deep Run 服务校验当前用户、启用的 Agent 和会话；会话为空时创建会话，并保存用户消息。
4. Java 创建状态为 `3`（排队中）的 `agent_run`，其 ID 即为外部请求的 `run_id`。
5. Java 使用既有知识检索结果构造 `knowledge_sources`，使用启用且绑定的 MCP 工具构造 `allowed_tools`，并签发委托 JWT。
6. Java HMAC 签名调用 `POST {baseUrl}/v1/runs`。外部服务返回 `202 Accepted` 后，Java 保持当前 SSE 连接等待回调。
7. 外部服务调用 `POST /api/agent/deep-runs/callback/{runId}`。Java 验签、验证路径与回调体中的 run ID 一致、按事件 ID 幂等保存步骤、更新运行状态，并将进度发至持有该 run ID 的 SSE 连接。
8. 完成回调创建 assistant 消息、更新会话消息数和知识引用审计、更新运行记录，并发送 `done`。失败或取消回调更新运行记录并发送 `error`。

## Deep Agent 请求契约

Java 调用外部服务时使用 JSON：

```json
{
  "run_id": "agentRunId",
  "user_id": "currentUserId",
  "agent_id": "agentDefinitionId",
  "conversation_id": "conversationId",
  "task": "用户输入文本",
  "system_prompt": "Agent 系统提示词",
  "knowledge_sources": [
    { "title": "文档标题", "content": "检索片段", "citation": "【1】" }
  ],
  "allowed_tools": ["mcp_tool_name"],
  "delegation_token": "最小授权JWT",
  "max_steps": 12
}
```

- `system_prompt` 来自 `AgentDefinition.systemPrompt`。
- `max_steps` 来自 `AgentDefinition.maxToolRounds`；为空时不发送或发送外部默认值 `12`。
- 不传 `timeout_seconds`，外部服务使用其默认的 600 秒。
- `allowed_tools` 仅包含启用的 Agent 绑定 MCP 工具的 `mcpToolName`，不包含平台内置工具。
- `knowledge_sources` 由 `KnowledgeContextService` 的已有检索结果转换；引用格式保持 `【序号】`，以兼容知识引用审计。

## 服务认证和委托授权

### HMAC

创建任务和回调均携带：

- `X-Aether-Key-Id`
- `X-Aether-Timestamp`，Unix 秒时间戳
- `X-Aether-Signature`

签名算法是 `HMAC-SHA256(sharedSecret, timestamp + "." + 原始请求体字节)`，验签时间窗口为五分钟。缺少、过期或错误的签名返回 `401`；未配置共享密钥或外部服务地址时拒绝 Deep 任务并记录失败运行。

### MCP 委托 JWT

Java 使用 `AETHER_MCP_DELEGATION_SECRET` 签发 HS256 JWT，五分钟过期，包含：

```json
{
  "runId": "agentRunId",
  "userId": "currentUserId",
  "agentId": "agentDefinitionId",
  "allowedTools": ["mcp_tool_name"],
  "iat": 0,
  "exp": 0
}
```

外部 Deep Agent 仅将该令牌作为 MCP Bearer Token 传递。MCP 服务必须验证签名和到期时间，并拒绝不在 `allowedTools` 中的工具调用。

## 回调和 SSE 契约

外部回调体：

```json
{
  "event_id": "uuid",
  "event_type": "tool.completed",
  "run_id": "agentRunId",
  "occurred_at": 1760000000000,
  "data": { "toolName": "search", "message": "Completed search" }
}
```

回调事件的处理规则：

| 外部事件 | 运行状态/持久化 | 前端事件 |
| --- | --- | --- |
| `run.started` | 运行状态改为 `4`（执行中） | `run_step` |
| `plan.updated`、`step.started`、`tool.started`、`tool.completed` | 保存步骤 | `run_step` |
| `run.completed` | 保存 assistant 消息和用量，状态改为 `0` | `done` |
| `run.failed` | 保存错误，状态改为 `1` | `error` |
| `run.cancelled` | 状态改为 `5` | `error` |

进度事件统一发为：

```json
{
  "runId": "agentRunId",
  "eventId": "uuid",
  "eventType": "tool.completed",
  "occurredAt": 1760000000000,
  "data": { "toolName": "search", "message": "Completed search" }
}
```

`done` 保持现有聊天 SSE 的会话和消息字段，并补充 `runId`、`sources`、模型和 Token 数据。Deep Agent 最终内容只在完成回调时保存和发送，不写入不完整 assistant 消息。

## 数据模型和查询接口

新增 Flyway 迁移：

- `agent_definition.execution_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD'`。
- `agent_run.external_run_id VARCHAR(64)` 和 `agent_run.execution_mode VARCHAR(16)`。
- `agent_run_step`：`run_id`、`event_id`、`event_type`、`data`、`occurred_at` 及 BaseEntity 公共列。
- `agent_run_step(run_id, event_id)` 唯一索引，保证回调重试幂等。

运行状态扩展为：`0=成功`、`1=失败`、`2=超时`、`3=排队中`、`4=执行中`、`5=已取消`。

`AgentRunVo` 返回 `executionMode` 和 `externalRunId`。新增 `AgentRunStepVo` 及 `GET /api/agent/run/{id}/steps`，确认运行存在后按 `occurredAt`、创建时间升序返回步骤。运行详情和步骤接口供前端断线恢复，不能重新向外部服务发起任务。

## 取消和连接管理

Java 为活跃 Deep Run 维护 `runId -> SseEmitter` 映射。回调无法找到活跃连接时仍必须持久化事件。浏览器主动停止 Deep 任务时调用取消接口，Java HMAC 签名转发外部 `POST /v1/runs/{runId}/cancel`；最终状态以外部 `run.cancelled` 回调为准。

## 错误处理

- Deep Agent 未配置、外部创建任务返回非 202、网络异常：本地运行标记失败，SSE 发送 `error`。
- 回调 HMAC 无效、路径/体 run ID 不一致或运行不存在：拒绝回调，不创建步骤。
- 重复 `event_id`：不重复更新运行、不重复创建消息、不重复转发 SSE。
- `run.completed` 到达时运行已处于成功、失败或取消终态：忽略终态冲突并保留原始步骤审计。
- Deep 模式的非流式聊天请求：返回 `422`，消息明确要求使用流式端点。

## 测试和验收

- Deep 请求转换测试：知识来源、MCP 工具白名单、配置映射和 HMAC 请求头正确。
- MCP JWT 测试：声明、五分钟到期时间和签名密钥正确。
- 回调测试：验签、事件 ID 幂等、状态转移、完成消息持久化、失败/取消处理和 SSE 映射。
- 控制器测试：Deep 非流式请求返回 `422`，步骤查询按顺序返回。
- 执行 `mvn clean compile -DskipTests`，并在本地依赖可用时执行 `mvn test -pl admin -am` 和 `mvn test -pl biz -am`。
