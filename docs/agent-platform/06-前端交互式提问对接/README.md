# Agent 平台 — 前端交互式提问对接

> 合并来源：FRONTEND_INTERACTIVE_QUESTION_DESIGN.md、FRONTEND_INTERACTIVE_QUESTION_INTEGRATION.md
> 更新日期：2026-07-20

---

## 一、核心规则

- AI 通过内置工具 `ask_user` 发起结构化提问
- 一次 `ask_user` 可包含 **1-4 个问题**，后端只保存一条 `messageType=interaction` 消息
- 多个问题放在 `questionConfig.questions[]` 中，前端使用 tabs 切换
- 用户提交后继续通过同一个 SSE 流接收回复
- 前端只认 `messageType=interaction` 消息或 SSE `question` 事件
- 不使用 `batchId`，不使用 `/reply` 端点
- 用户选择后**不新增**用户气泡

---

## 二、问题类型

### Choice（选择）

```json
{
  "type": "choice",
  "questionId": "q1",
  "title": "请选择操作类型",
  "description": "描述信息",
  "multiple": false,
  "options": [
    { "label": "方案 A", "value": "A" },
    { "label": "方案 B", "value": "B" }
  ]
}
```

### Confirm（确认）

```json
{
  "type": "confirm",
  "questionId": "q2",
  "title": "确认执行？",
  "description": "即将执行操作，请确认"
}
```

---

## 三、消息结构

### SSE 事件

| 事件          | 说明      |
|-------------|---------|
| `message`   | 普通文本分片  |
| `reasoning` | 推理内容分片  |
| `question`  | 交互式提问卡片 |
| `tool_call` | 工具调用信息  |
| `done`      | 响应结束    |

**question 事件**：

```json
{
  "messageId": 1000,
  "messageType": "interaction",
  "interactionType": "group",
  "interactionStatus": "waiting",
  "questionConfig": {
    "questions": [ ... ]
  }
}
```

### 事件顺序

```
message chunks → question → done(waitingUser=true)
```

或

```
question → done(waitingUser=true)
```

### 用户提交

地址：`POST /api/agent/chat/stream`

请求体（在已有流式聊天基础上追加）：

```json
{
  "agentId": 1,
  "conversationId": 100,
  "message": "",
  "interactive": true,
  "parentMessageId": 1000,
  "answer": {
    "answers": [
      { "questionId": "q1", "value": "A" },
      { "questionId": "q2", "value": true }
    ]
  }
}
```

---

## 四、历史消息恢复

| messageType                             | 展示方式                         |
|-----------------------------------------|------------------------------|
| 空 或 `chat`                              | 普通聊天消息                       |
| `interaction` + `interactionType=group` | tabs 交互式消息卡片                 |
| `answer`                                | 默认折叠到对应 interaction 卡片，不独立展示 |

### SSE 事件处理规则

- `question` 事件：只追加 interaction 卡片，**不删除**当前 assistant 流式文本
- `done` 事件：只 finalize 当前 assistant 文本

### 消息有效性判断

```typescript
Boolean(message.content || message.reasoningContent)
```

---

## 五、对接要点

1. 聊天请求需传 `interactive: true` 以启用交互式提问能力
2. `parentMessageId` 指向触发提问的 interaction 消息 ID
3. `answer.answers` 按 `questionId` 匹配问题
4. 前端根据 `done` 事件的 `waitingUser=true` 判断是否等待用户输入
5. 历史消息推荐通过 `GET /api/agent/conversation/{id}/messages?current=1&pageSize=20` 获取，该接口会聚合工具调用日志

---

## 六、Deep Agent 交互卡片（V1.1）

Deep Agent 的 `ask_user` 与 MCP 确认同样复用交互卡片体系，通过 `approvalType` 区分：

| approvalType             | 触发场景                   | 交互形式                | 选项                                         |
|--------------------------|------------------------|---------------------|--------------------------------------------|
| `deep_ask_user`          | Deep Agent 追问补充信息      | `group` + `tabs`    | 按问题类型（choice/confirm/text 等）               |
| `deep_mcp_tool_approval` | Deep Agent 需要调用 MCP 工具 | `group` + `confirm` | `once`（仅本次）、`allow_10m`（10 分钟免确认）、`reject` |

SSE 事件仍为 `question`，`interactionType` 为 `group`；用户提交答案时同样携带 `parentMessageId` 与 `answer.answers`，由后端根据
`approvalType` 决定恢复 Deep 运行或执行已批准工具。
