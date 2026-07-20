# Agent 平台 — 前端版本变更日志

> 合并来源：FRONTEND_INTEGRATION_CHANGES_V0.6.md、FRONTEND_INTEGRATION_CHANGES_V0.6_AUDIT.md、FRONTEND_INTEGRATION_CHANGES_V0.7.md、FRONTEND_INTEGRATION_CHANGES_V0.8_TOOL_CALL_DISPLAY.md
> 更新日期：2026-07-20

---

## V0.6（2026-07-07）— 推理内容 + 运行审计

### 变更内容

1. 聊天消息支持推理内容字段
2. 运行审计支持真实统计和时间范围过滤

### 受影响接口

| 接口 | 变更 |
|------|------|
| `POST /api/agent/chat` | 响应新增 `reasoningContent`、`reasoningTokens` |
| `GET /api/agent/chat/stream` | `done` 事件新增推理字段 |
| `GET /api/agent/conversation/{id}/messages` | 历史消息新增推理字段 |
| `GET /api/agent/run/list` | 请求体支持 `startTime`、`endTime` |
| `GET /api/agent/run/statistics` | 改为真实统计 |

### 前端规则

- `reasoningContent` **不能**当做 `content` 展示，应使用折叠面板
- `content` 为空但 `reasoningContent` 有值时，提示"未返回最终答案"
- 判断消息有效应使用 `Boolean(message.content || message.reasoningContent)`

---

## V0.6 运营审计（2026-07-12）— 会话审计

### 新增接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 会话生命周期 | GET | `/api/agent/conversation/{id}/lifecycle` |
| 会话消息统计 | GET | `/api/agent/conversation/{id}/statistics` |

### 生命周期响应

```json
{
  "conversationId": 100,
  "createdAt": "2026-07-12T10:00:00",
  "lastActiveAt": "2026-07-12T10:30:00",
  "closedAt": null,
  "status": "ACTIVE",
  "messageCount": 15,
  "totalUserMessages": 8,
  "totalAssistantMessages": 7,
  "durationMs": 1800000
}
```

### 消息统计响应

```json
{
  "conversationId": 100,
  "totalMessages": 20,
  "userMessages": 10,
  "assistantMessages": 8,
  "toolMessages": 2,
  "totalPromptTokens": 5000,
  "totalCompletionTokens": 3000,
  "totalTokens": 8000,
  "avgLatencyMs": 1200
}
```

### 前端要求

在会话详情页新增"统计信息"区域，同时调用两个接口并展示。

---

## V0.7（2026-07-07）— 深度思考 + SSE 优化

### 变更内容

1. 聊天接口新增深度思考配置参数
2. SSE 新增实时推理过程事件
3. SSE 连接稳定性优化

### 参数说明

| 参数 | 类型 | 说明 |
|------|------|------|
| `thinking` | Boolean | 是否启用深度思考 |
| `reasoningEffort` | String | 推理力度：`low` / `medium` / `high` |

### reasoning 事件

实时推送推理内容分片：

```
event: reasoning
data: {"chunk":"推理内容分片","conversationId":"conv-1"}
```

### SSE 心跳

后端每 15 秒发送 `:heartbeat` comment，浏览器 `EventSource` 自动忽略。

### 超时调整

SSE 连接超时从 30 秒改为 5 分钟。

---

## V0.8（2026-07-12）— 工具调用记录显示

### 变更内容

1. `agent_tool_call_log` 新增 `tool_call_id`、`tool_name`、`arguments` 字段
2. 会话消息接口支持 `includeToolCalls` 参数
3. `AgentMessageVo` 新增 `runId` 和 `toolCallLogs` 字段

### 接口变更

`GET /api/agent/conversation/{id}/messages?includeToolCalls=true`

assistant 消息新增返回 `toolCallLogs` 数组，包含：
- 工具名称、参数、执行结果、状态、耗时

### 详细对接

请参阅《05-前端MCP工具对接》第八章「工具调用日志显示」。
