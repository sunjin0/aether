# Agent 平台前端对接变更说明（V0.6）

> 日期：2026-07-07
> 范围：本次后端改动涉及的前端对接点
> 目标读者：前端开发

---

## 1. 本次前端需要改什么

本次后端主要变更两类能力：

1. 聊天消息支持推理内容字段
2. 运行审计支持真实统计和时间范围过滤

前端需要修改：

- 消息类型增加 `reasoningContent`、`reasoningTokens`
- 聊天结果展示区区分“最终回复”和“推理过程”
- 历史消息列表支持展示已持久化的推理过程
- SSE `done` 事件处理新增推理字段
- 运行审计列表筛选增加 `startTime`、`endTime`
- 运行统计接口改为可用真实数据，统计页/看板可接入

---

## 2. 受影响接口总览

| 接口 | 方法 | 是否有变化 | 前端动作 |
|------|------|------------|----------|
| `/api/agent/chat` | POST | 有变化 | 响应 `data` 新增推理字段 |
| `/api/agent/chat/stream` | GET | 有变化 | `done` 事件返回推理字段 |
| `/api/agent/conversation/{id}/messages` | GET | 有变化 | 历史消息新增推理字段 |
| `/api/agent/run/list` | POST | 有变化 | 请求体支持 `startTime`、`endTime` |
| `/api/agent/run/statistics` | GET | 有变化 | 由占位 0 改为真实统计 |

其他 Agent 管理、模型供应商、工具管理接口本次无前端契约变化。

---

## 3. 消息模型变更

### 3.1 新增字段

所有 assistant 消息可能新增：

```ts
interface AgentMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  reasoningContent?: string
  reasoningTokens?: number
  model?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
}
```

字段说明：

| 字段 | 说明 | 展示建议 |
|------|------|----------|
| `content` | 最终回复内容 | 主回答区域 |
| `reasoningContent` | 推理过程 | 折叠面板，不要混入主回答 |
| `reasoningTokens` | 推理 token 数 | 调试信息、详情面板、统计展示 |

### 3.2 重要规则

前端不要把 `reasoningContent` 当成 `content` 展示。

推荐处理：

```ts
if (message.reasoningContent) {
  renderReasoningPanel(message.reasoningContent)
}

if (message.content) {
  renderAnswer(message.content)
} else if (message.reasoningContent) {
  renderWarning('模型仅返回推理过程，未返回最终答案')
} else {
  renderError('模型未返回有效内容')
}
```

---

## 4. 非流式聊天接口变化

### 4.1 接口

```http
POST /api/agent/chat
```

### 4.2 请求

请求不变：

```json
{
  "agentId": "agent-1",
  "conversationId": "conversation-1",
  "message": "你好"
}
```

### 4.3 响应变化

`data` 新增：

- `reasoningContent`
- `reasoningTokens`

示例：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "id": "message-assistant-1",
    "conversationId": "conversation-1",
    "role": "assistant",
    "content": "这是最终回复。",
    "reasoningContent": "这里是模型推理过程。",
    "reasoningTokens": 1896,
    "model": "qwen/qwen3.5-9b",
    "promptTokens": 0,
    "completionTokens": 0,
    "totalTokens": 0,
    "latencyMs": 1200
  }
}
```

### 4.4 前端修改点

- 更新聊天接口响应类型
- assistant 消息渲染新增推理折叠面板
- 当 `content` 为空但 `reasoningContent` 有值时，提示“未返回最终答案”

---

## 5. SSE 流式聊天接口变化

### 5.1 接口

```http
GET /api/agent/chat/stream
```

### 5.2 message 事件

`message` 事件仍表示最终回复内容分片。

```text
event: message
data: {"conversationId":"conversation-1","chunk":"你好"}
```

前端原有追加逻辑不需要改。

### 5.3 done 事件变化

`done` 事件会返回完整消息信息，并新增：

- `reasoningContent`
- `reasoningTokens`

示例：

```json
{
  "conversationId": "conversation-1",
  "messageId": "message-assistant-1",
  "content": "最终回复",
  "reasoningContent": "完整推理过程",
  "reasoningTokens": 1896,
  "model": "qwen/qwen3.5-9b",
  "promptTokens": 0,
  "completionTokens": 0,
  "totalTokens": 0
}
```

前端处理：

```ts
eventSource.addEventListener('done', (event) => {
  const data = JSON.parse(event.data)

  updateAssistantMessage(data.messageId, {
    content: data.content || getStreamingContent(data.messageId),
    reasoningContent: data.reasoningContent || '',
    reasoningTokens: data.reasoningTokens,
    model: data.model,
    promptTokens: data.promptTokens,
    completionTokens: data.completionTokens,
    totalTokens: data.totalTokens,
  })
})
```

### 5.4 注意

本次后端没有新增实时推理分片事件。

当前行为：

- `message`：实时返回最终回复分片
- `done`：一次性返回完整推理过程

前端不要监听不存在的 `reasoning` 事件。

---

## 6. 历史消息接口变化

### 6.1 接口

```http
GET /api/agent/conversation/{id}/messages
```

### 6.2 响应变化

assistant 历史消息新增：

```json
{
  "id": "message-assistant-1",
  "role": "assistant",
  "content": "最终回复",
  "reasoningContent": "推理过程",
  "reasoningTokens": 1896
}
```

### 6.3 前端修改点

- 历史会话恢复时也要渲染推理折叠面板
- 不要只在新消息里处理推理字段
- 如果历史消息 `content` 为空但 `reasoningContent` 有值，展示异常提示

---

## 7. 运行记录列表接口变化

### 7.1 接口

```http
POST /api/agent/run/list
```

### 7.2 请求新增字段

请求体现在支持：

- `startTime`
- `endTime`

单位：毫秒时间戳，与项目现有 `createdAt` 字段一致。

示例：

```json
{
  "current": 1,
  "pageSize": 20,
  "agentDefinitionId": "agent-1",
  "userId": "user-1",
  "status": 0,
  "startTime": 1783390574000,
  "endTime": 1783394174000
}
```

### 7.3 前端修改点

运行审计列表筛选区可增加：

- 开始时间
- 结束时间

传参转换：

```ts
const payload = {
  current,
  pageSize,
  agentDefinitionId,
  userId,
  status,
  startTime: dateRange?.[0]?.getTime(),
  endTime: dateRange?.[1]?.getTime(),
}
```

---

## 8. 运行统计接口变化

### 8.1 接口

```http
GET /api/agent/run/statistics
```

### 8.2 Query 参数

| 参数 | 必填 | 说明 |
|------|------|------|
| `agentId` | 否 | Agent 定义 ID |
| `startTime` | 否 | 开始时间，毫秒时间戳 |
| `endTime` | 否 | 结束时间，毫秒时间戳 |

示例：

```http
GET /api/agent/run/statistics?agentId=agent-1&startTime=1783390574000&endTime=1783394174000
```

### 8.3 响应

之前该接口返回全 0 占位数据，现在返回真实统计。

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "agentDefinitionId": "agent-1",
    "totalCalls": 100,
    "successCalls": 90,
    "failedCalls": 8,
    "timeoutCalls": 2,
    "totalPromptTokens": 12000,
    "totalCompletionTokens": 5000,
    "totalTokens": 17000,
    "avgLatencyMs": 1300,
    "errorRate": 0.1
  }
}
```

### 8.4 前端修改点

可以新增或接入以下统计卡片：

- 总调用次数：`totalCalls`
- 成功次数：`successCalls`
- 失败次数：`failedCalls`
- 超时次数：`timeoutCalls`
- 总 token：`totalTokens`
- 平均耗时：`avgLatencyMs`
- 错误率：`errorRate`

错误率格式化建议：

```ts
const errorRateText = `${(data.errorRate * 100).toFixed(2)}%`
```

---

## 9. 推荐前端改动清单

### 9.1 类型定义

- [ ] `AgentMessage` 增加 `reasoningContent`
- [ ] `AgentMessage` 增加 `reasoningTokens`
- [ ] `AgentRunListQuery` 增加 `startTime`
- [ ] `AgentRunListQuery` 增加 `endTime`
- [ ] 新增或更新 `AgentRunStatistics`

### 9.2 聊天页面

- [ ] 非流式聊天结果展示推理折叠面板
- [ ] SSE `done` 事件保存推理字段
- [ ] 处理 `content` 为空但 `reasoningContent` 有值的提示
- [ ] 历史消息恢复时展示推理折叠面板

### 9.3 运行审计页面

- [ ] 列表筛选增加时间范围
- [ ] 请求 `/api/agent/run/list` 时传 `startTime/endTime`
- [ ] 接入 `/api/agent/run/statistics`
- [ ] 展示调用量、token、平均耗时、错误率

---

## 10. 兼容性说明

### 10.1 老数据

旧消息没有 `reasoningContent` 和 `reasoningTokens`，前端按空值处理即可。

```ts
const reasoningContent = message.reasoningContent || ''
```

### 10.2 token 为 0

部分本地模型或 OpenAI 兼容服务可能返回：

```json
{
  "promptTokens": 0,
  "completionTokens": 0,
  "totalTokens": 0,
  "reasoningTokens": 1896
}
```

前端不要用 `totalTokens === 0` 判断消息无效。

判断消息是否有效，应看：

```ts
Boolean(message.content || message.reasoningContent)
```

### 10.3 content 与 reasoningContent 的展示边界

推荐：

- `content`：默认展开
- `reasoningContent`：默认折叠
- 没有 `content`：显示警告，不自动把推理内容作为最终答案

---

## 11. 后端部署提醒

已有数据库必须执行迁移：

```sql
ALTER TABLE `agent_message`
    ADD COLUMN `reasoning_content` LONGTEXT COMMENT '推理内容（assistant角色时）' AFTER `content`,
    ADD COLUMN `reasoning_tokens` INT COMMENT '推理token数' AFTER `total_tokens`;
```

迁移脚本位置：

```text
api/src/main/resources/sql/agent-platform-v0.6-reasoning-message.sql
```

---

## 12. 2026-07-07 修复补充

本次后端修复后，前端按以下实际接口行为对接：

1. `/api/agent/chat/stream` 的 `done` 事件现在会返回完整最终消息字段：`conversationId`、`messageId`、`content`、`reasoningContent`、`model`、`promptTokens`、`completionTokens`、`totalTokens`、`reasoningTokens`。

2. 会话详情、会话消息、关闭会话、删除会话接口现在都会校验当前用户归属：`/api/agent/conversation/{id}`、`/api/agent/conversation/{id}/messages`、`/api/agent/conversation/{id}/close`、`/api/agent/chat/conversation/{id}/messages`。

3. 如果访问不属于当前用户的会话，后端按资源不存在处理，前端按 404/业务错误提示即可，不要展示空白历史。

4. 工具调用为后端内部能力，前端接口契约没有新增请求参数；工具调用日志中的 `toolId` 可能为空，表示模型请求了未绑定或未匹配到的工具。

5. 已有数据库迁移除 `agent_message` 推理字段外，还需要执行 `agent_tool_call_log.tool_id` 可空变更。

如果未执行迁移，聊天接口保存 assistant 消息时会因缺少列而失败。
