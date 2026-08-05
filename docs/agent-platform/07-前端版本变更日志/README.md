# Agent 平台 — 前端版本变更日志

> 合并来源：FRONTEND_INTEGRATION_CHANGES_V0.6.md、FRONTEND_INTEGRATION_CHANGES_V0.6_AUDIT.md、FRONTEND_INTEGRATION_CHANGES_V0.7.md、FRONTEND_INTEGRATION_CHANGES_V0.8_TOOL_CALL_DISPLAY.md
> 更新日期：2026-08-05

---

## V0.6（2026-07-07）— 推理内容 + 运行审计

### 变更内容

1. 聊天消息支持推理内容字段
2. 运行审计支持真实统计和时间范围过滤

### 受影响接口

| 接口 | 变更 |
|------|------|
| `POST /api/agent/chat` | 响应新增 `reasoningContent`、`reasoningTokens` |
| `POST /api/agent/chat/stream` | `done` 事件新增推理字段 |
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

`GET /api/agent/conversation/{id}/messages?current=1&pageSize=20`

当前实现会始终聚合工具调用日志，`includeToolCalls` 参数不会改变返回结果。

assistant 消息新增返回 `toolCallLogs` 数组，包含：
- 工具名称、参数、执行结果、状态、耗时

### 详细对接

请参阅《05-前端MCP工具对接》第八章「工具调用日志显示」。

---

## V1.0（2026-07-25）— 知识库 RAG 与混合检索

### 变更内容

1. 文档管理升级为版本模型（`version`、`chunk` 接口路径调整）。
2. 检索由向量召回扩展为「向量 + 词法」混合检索，可配置重排。

### 接口变更

- 分块列表：`GET /api/knowledge/document/version/{versionId}/chunk/list`
- 回滚：`POST /api/knowledge/document/version/{versionId}/rollback`

---

## V1.1（2026-07-28）— Deep Agent 集成

### 变更内容

1. Agent 定义新增 `executionMode=STANDARD/DEEP`。
2. 聊天 SSE 新增 Deep 事件：`accepted`、`run_step`。
3. 新增 Deep 运行步骤查看与取消。

### 接口变更

- `POST /api/agent/chat/stream`（Deep 分支返回 `accepted` → `run_step` → `question` → `done`）
- `GET /api/agent/run/{id}/steps`、`POST /api/agent/run/{id}/cancel`
- `GET /api/agent/deep-runs/{runId}/stream`（运行事件重放）

---

## V1.2（2026-07-29）— 知识库审核与检索评测

### 变更内容

1. 新增文档版本提交 / AI 审查 / 人工审批流程。
2. 新增检索评测集与评测运行。

### 新增接口

- 审查任务：`/api/knowledge/review-task/**`
- AI 审查：`/api/knowledge/ai-review/**`
- 评测：`/api/knowledge/evaluation/**`

---

## V1.3（2026-08-01）— 工作流运行时

### 变更内容

1. 工作流画布草稿 + 发布版本。
2. 实例运行时：人工节点、MCP 确认节点、重试/回放/终止。

### 新增接口

- 定义：`/api/agent/workflow/**`（草稿/发布/下线/版本/导入导出/模板）
- 实例：`/api/agent/workflow/instances/**`（含 SSE 实时事件）

---

## V1.4（2026-08-02）— 业务集成

### 变更内容

1. 服务账号 client credentials + 工作流白名单/额度。
2. Webhook 与定时触发、业务回调投递、运营指标/死信。

### 新增接口

- 服务账号：`/api/sys/service-account/**`、`/api/auth/service-account/token`
- Webhook：`/api/agent/workflow/webhooks/**`
- 定时任务：`/api/agent/workflow/schedules/**`
- 运营：`/api/agent/workflow/operations/metrics`、`/dead-letters`

---

## V1.5（2026-08-03）— 管理员偏好与工作台

### 变更内容

1. 后台用户偏好支持确认/拒绝/覆盖/统计。
2. 新增工作台聚合概览。

### 新增接口

- 偏好：`/api/sys/preference/**`（feedback / override / statistics）
- 工作台：`GET /api/workbench/overview`
