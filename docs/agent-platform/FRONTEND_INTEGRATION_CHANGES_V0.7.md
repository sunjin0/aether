# Agent 平台前端对接变更说明（V0.7）

> 日期：2026-07-07
> 范围：本次后端改动涉及的前端对接点
> 目标读者：前端开发

---

## 1. 本次前端需要改什么

本次后端主要变更三类能力：

1. 聊天接口新增深度思考配置参数（`thinking`、`reasoningEffort`）
2. SSE 流式新增实时推理过程事件（`reasoning`）
3. SSE 连接稳定性优化（超时延长、心跳机制）

前端需要修改：

- 聊天请求新增 `thinking` 和 `reasoningEffort` 参数
- 流式聊天监听新的 `reasoning` 事件，实时展示推理过程
- SSE 心跳处理（忽略 `comment` 类型数据，不影响现有逻辑）

---

## 2. 受影响接口总览

| 接口 | 方法 | 是否有变化 | 前端动作 |
|------|------|------------|----------|
| `/api/agent/chat` | POST | 有变化 | 请求体新增 `thinking`、`reasoningEffort` |
| `/api/agent/chat/stream` | GET | 有变化 | 新增 Query 参数 + 新增 `reasoning` SSE 事件 |

其他接口本次无前端契约变化。

---

## 3. 深度思考配置

### 3.1 参数说明

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `thinking` | Boolean | 否 | 是否启用深度思考。不传则使用 Agent 默认配置 |
| `reasoningEffort` | String | 否 | 推理力度：`low` / `medium` / `high`。`thinking=true` 时生效，不传默认 `medium` |

**配置优先级**：请求参数 > Agent 默认配置 > 不启用

### 3.2 reasoningEffort 取值

| 值 | 说明 | 适用场景 |
|----|------|----------|
| `low` | 轻度推理 | 简单问答、速度优先 |
| `medium` | 中等推理 | 一般分析、默认值 |
| `high` | 深度推理 | 复杂逻辑、数学推导、代码分析 |

### 3.3 注意事项

- `reasoningEffort` 仅在 `thinking=true` 时生效
- `reasoningEffort` 值不合法（非 low/medium/high）时，后端返回 400 错误
- 深度思考会增加响应时间和 token 消耗，前端应做好 loading 提示
- 不是所有模型都支持 `reasoning_effort`，不支持的模型会忽略该参数

---

## 4. 非流式聊天接口变化

### 4.1 接口

```http
POST /api/agent/chat
```

### 4.2 请求新增字段

```json
{
  "agentId": "agent-1",
  "conversationId": "conversation-1",
  "message": "请深度分析一下这个问题",
  "thinking": true,
  "reasoningEffort": "high"
}
```

### 4.3 响应

响应结构不变，`reasoningContent` 和 `reasoningTokens` 字段已在 V0.6 支持。

开启深度思考后，`reasoningContent` 内容会更丰富。

### 4.4 前端修改点

- 聊天输入区新增深度思考开关（可选）
- 推理力度选择器（可选，高级模式）
- 开启深度思考时展示 loading 提示："深度思考中，请耐心等待..."

---

## 5. SSE 流式聊天接口变化

### 5.1 接口

```http
GET /api/agent/chat/stream
```

### 5.2 新增 Query 参数

| 参数 | 必填 | 说明 |
|------|------|------|
| `agentId` | 是 | Agent 定义 ID |
| `message` | 是 | 消息内容 |
| `conversationId` | 否 | 会话 ID，首次对话不传 |
| `thinking` | 否 | 是否启用深度思考 |
| `reasoningEffort` | 否 | 推理力度：low/medium/high |

示例：

```
GET /api/agent/chat/stream?agentId=agent-1&message=请深度分析&thinking=true&reasoningEffort=high
```

### 5.3 新增 reasoning 事件（实时推理过程）

V0.6 中推理过程只在 `done` 事件中一次性返回。V0.7 新增 `reasoning` 事件，**实时推送**推理过程分片。

```text
event: reasoning
data: {"chunk":"让我先分析一下这个问题的背景","conversationId":"conversation-1"}

event: reasoning
data: {"chunk":"首先需要考虑几个关键因素","conversationId":"conversation-1"}

event: message
data: {"chunk":"根据分析，","conversationId":"conversation-1","messageId":null}

event: message
data: {"chunk":"我的回答是...","conversationId":"conversation-1","messageId":null}

event: done
data: {"conversationId":"conversation-1","messageId":"msg-1","content":"根据分析，我的回答是...","reasoningContent":"让我先分析一下这个问题的背景首先需要考虑几个关键因素","reasoningTokens":256,...}
```

### 5.4 reasoning 事件数据结构

```ts
interface ReasoningEvent {
  chunk: string          // 推理内容分片
  conversationId: string // 会话 ID
}
```

### 5.5 前端处理

```ts
const eventSource = new EventSource(url)

// 实时推理过程
eventSource.addEventListener('reasoning', (event) => {
  const data = JSON.parse(event.data)
  appendReasoningContent(data.conversationId, data.chunk)
})

// 最终回复内容（原有逻辑不变）
eventSource.addEventListener('message', (event) => {
  const data = JSON.parse(event.data)
  appendMessageContent(data.conversationId, data.chunk)
})

// 完成事件（原有逻辑不变，reasoningContent 为完整推理过程）
eventSource.addEventListener('done', (event) => {
  const data = JSON.parse(event.data)
  finalizeMessage(data)
})

// 错误事件（原有逻辑不变）
eventSource.addEventListener('error', (event) => {
  // ...
})
```

### 5.6 推荐 UI 交互

```
┌─────────────────────────────────────┐
│ 🧠 深度思考中...                     │  ← reasoning 事件驱动
│ ┌─────────────────────────────────┐ │
│ │ 让我先分析一下这个问题的背景...    │ │  ← 实时追加
│ │ 首先需要考虑几个关键因素...       │ │
│ └─────────────────────────────────┘ │
│                                     │
│ 📝 回复：                           │  ← message 事件驱动
│ 根据分析，我的回答是...              │  ← 实时追加
└─────────────────────────────────────┘
```

---

## 6. SSE 心跳说明

### 6.1 心跳机制

后端每 15 秒发送一个 SSE comment 作为心跳：

```text
:heartbeat

:heartbeat

:heartbeat
```

### 6.2 前端处理

SSE comment 以 `:` 开头，浏览器 `EventSource` 会自动忽略，**不需要前端做任何处理**。

如果使用自定义 SSE 解析库，确保忽略以 `:` 开头的行。

### 6.3 超时变更

| 项目 | 旧值 | 新值 |
|------|------|------|
| SSE 连接超时 | 30 秒 | 5 分钟 |
| LLM HTTP 读超时 | 30 秒 | 5 分钟 |

前端 `EventSource` 不需要设置超时，浏览器会自动处理。如果前端有自定义超时逻辑，建议同步调整为 5 分钟以上。

---

## 7. 完整 SSE 事件总览

V0.7 版本 SSE 流式聊天包含以下事件类型：

| 事件 | 说明 | 是否新增 |
|------|------|----------|
| `reasoning` | 推理过程实时分片 | ✅ 新增 |
| `message` | 最终回复内容分片 | 原有 |
| `tool_call` | 工具调用通知 | 原有 |
| `done` | 流完成，返回完整消息 | 原有 |
| `error` | 错误 | 原有 |
| `:heartbeat` | 心跳（comment，浏览器自动忽略） | ✅ 新增 |

---

## 8. 推荐前端改动清单

### 8.1 类型定义

- [ ] 聊天请求类型新增 `thinking?: boolean`
- [ ] 聊天请求类型新增 `reasoningEffort?: 'low' | 'medium' | 'high'`

### 8.2 聊天页面

- [ ] 新增深度思考开关控件
- [ ] 新增推理力度选择器（可选，高级模式）
- [ ] 开启深度思考时展示 loading 提示
- [ ] 监听 `reasoning` 事件，实时展示推理过程
- [ ] 推理过程区域默认折叠，支持展开/收起
- [ ] `done` 事件中 `reasoningContent` 作为兜底完整数据

### 8.3 流式聊天

- [ ] `EventSource` 注册 `reasoning` 事件监听
- [ ] 推理分片实时追加到推理面板
- [ ] 区分推理内容和最终回复内容的展示区域

---

## 9. 兼容性说明

### 9.1 参数可选

`thinking` 和 `reasoningEffort` 均为可选参数。不传时行为与 V0.6 完全一致，不需要强制升级。

### 9.2 老模型兼容

不是所有模型都支持 `reasoning_effort` 参数。对于不支持的模型：

- 参数会被忽略，不影响正常回复
- 不会产生 `reasoning` 事件
- `done` 事件中 `reasoningContent` 为空

### 9.3 事件监听兼容

如果前端未监听 `reasoning` 事件，不影响其他事件的正常接收。建议新版本前端都添加该监听。

---

## 10. 后端部署提醒

已有数据库必须执行迁移：

```sql
ALTER TABLE `agent_definition`
    ADD COLUMN `default_thinking` TINYINT(1) DEFAULT 0 COMMENT '默认是否启用深度思考' AFTER `max_tool_rounds`,
    ADD COLUMN `default_reasoning_effort` VARCHAR(16) DEFAULT NULL COMMENT '默认推理力度：low/medium/high' AFTER `default_thinking`;
```

迁移脚本位置：

```text
api/src/main/resources/sql/agent-platform-v0.7-thinking.sql
```

如果未执行迁移，后端启动时会因缺少列而报错。
