# 提问式功能前端对接文档

> 日期：2026-07-12
> 适用版本：Agent Platform V1.0
> 范围：单条交互式消息、tabs 多问题、通过 `/api/agent/chat/stream` 提交并继续接收流式回复。

## 1. 对接结论

前端只认后端返回的 `messageType=interaction` 消息或 SSE `question` 事件。普通 assistant 文本不能被解析成按钮、选择器或表单。

交互式提问统一为一条 group 消息：
- 不使用 `batchId`。
- 不使用 `questions[].messageId`。
- 不使用顶层 `answers`。
- 不使用 `/api/agent/chat/reply`。
- 用户选择后不新增用户气泡。
- 如果同一轮同时有 assistant 文本和 `question` 事件，前端必须同时保留文本消息和 interaction 卡片。

## 2. 接口

| 场景 | 方法 | 地址 | 说明 |
| --- | --- | --- | --- |
| 普通流式聊天 | `POST` | `/api/agent/chat/stream` | 用户发送自然语言，接收 assistant 流式回复 |
| 回答交互式问题 | `POST` | `/api/agent/chat/stream` | 用户提交 `parentMessageId + answer.answers`，继续接收 assistant 流式回复 |
| 历史消息 | `GET` | `/api/agent/chat/conversation/{id}/messages` | 恢复 chat / interaction / answer 消息 |

请求头：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

## 3. 普通流式聊天

```json
{
  "agentId": "agent_1",
  "conversationId": "conv_1",
  "message": "帮我创建一个发布计划",
  "interactive": true
}
```

如果模型需要用户选择或确认，后端会通过 SSE `question` 事件返回一条 group interaction 消息。

## 4. SSE question 事件

```json
{
  "conversationId": "conv_1",
  "runId": "run_1",
  "messageId": "msg_question_group_1",
  "content": "请确认以下 2 个问题后继续。",
  "messageType": "interaction",
  "interactionType": "group",
  "interactionStatus": "pending",
  "questionConfig": {
    "type": "group",
    "layout": "tabs",
    "question": "请确认以下 2 个问题后继续。",
    "questions": [
      {
        "id": "env",
        "type": "choice",
        "question": "请选择发布环境",
        "options": [
          { "id": "dev", "label": "开发环境", "value": "dev" },
          { "id": "prod", "label": "生产环境", "value": "prod" }
        ],
        "multiple": false
      },
      {
        "id": "confirm_deploy",
        "type": "confirm",
        "question": "确认发布到生产环境？",
        "confirmText": "确认",
        "cancelText": "取消"
      }
    ]
  }
}
```

`done` 事件会携带 `waitingUser: true`，表示本轮模型响应暂停，等待用户操作。

如果本轮 `ask_user` 前已经输出 assistant 文本，事件顺序可能是：

```text
message: "我需要确认一下部署信息。"
question: { "messageId": "msg_question_1", ... }
done: { "messageId": "msg_assistant_1", "waitingUser": true }
```

最终 UI 应保留两条消息：
- assistant 普通文本：`msg_assistant_1`
- assistant interaction 卡片：`msg_question_1`

前端收到 `question` 时必须追加卡片，不能删除或替换当前流式 assistant 文本。

## 5. 前端类型

```ts
type AgentMessageVo = {
  id: string
  conversationId: string
  role: 'user' | 'assistant' | 'tool'
  messageType?: 'chat' | 'interaction' | 'answer'
  interactionType?: 'group'
  interactionStatus?: 'pending' | 'answered' | 'cancelled' | 'expired'
  questionConfig?: string
  parentMessageId?: string
  content?: string
}

type GroupQuestionConfig = {
  type: 'group'
  layout: 'tabs'
  question: string
  questions: Array<ChoiceQuestionConfig | ConfirmQuestionConfig>
}

type ChoiceQuestionConfig = {
  id: string
  type: 'choice'
  question: string
  options: Array<{ id: string; label: string; value: string }>
  multiple?: boolean
}

type ConfirmQuestionConfig = {
  id: string
  type: 'confirm'
  question: string
  confirmText?: string
  cancelText?: string
}
```

历史消息中的 `questionConfig` 是字符串，SSE `question` 中是对象；前端可以统一转换为对象后渲染。

## 6. SSE 事件处理

推荐处理方式：

```ts
function handleSseEvent(event: string, data: any) {
  switch (event) {
    case 'message':
      appendToStreamingAssistant(data.conversationId, data.content)
      break

    case 'question':
      appendInteractionMessage(data)
      break

    case 'done':
      finalizeStreamingAssistant(data)
      break
  }
}
```

`question` 事件只追加 interaction 卡片：

```ts
function appendInteractionMessage(q: any) {
  messages.push({
    id: q.messageId,
    conversationId: q.conversationId,
    role: 'assistant',
    messageType: 'interaction',
    interactionType: q.interactionType,
    interactionStatus: q.interactionStatus,
    content: q.content,
    questionConfig: q.questionConfig,
  })
}
```

`done` 事件只结束当前流式 assistant 文本：

```ts
function finalizeStreamingAssistant(done: any) {
  const current = getCurrentStreamingAssistant()
  if (current) {
    current.id = done.messageId
    current.content = done.content ?? current.content
    current.streaming = false
  }

  if (done.waitingUser) {
    inputDisabled = true
  }
}
```

不要在 `question` 事件中做这些操作：
- 不要清空当前 assistant 流式文本。
- 不要把当前 assistant 消息改成 `interaction`。
- 不要用 `question.messageId` 覆盖当前 assistant 消息 id。
- 不要在 `done` 时用 `done.content` 替换 interaction 卡片内容。

## 7. 用户提交

用户完成 tabs 内所有问题后，请求同一个 `/api/agent/chat/stream`：

```json
{
  "conversationId": "conv_1",
  "parentMessageId": "msg_question_group_1",
  "answer": {
    "answers": {
      "env": {
        "selected": "prod"
      },
      "confirm_deploy": {
        "confirmed": true
      }
    }
  },
  "interactive": true
}
```

提交后：
- 不追加用户气泡。
- 原 interaction 卡片进入 `submitting`。
- 建立 SSE 后继续接收 `message`、`reasoning`、`tool_call`、`question`、`done`、`error`。
- 如果再次收到 `question`，追加一条新的 interaction 卡片。
- 如果收到普通 `message` chunks，正常追加 assistant 流式消息。

## 8. 答案格式

`choice` 单选：

```json
{ "selected": "prod" }
```

`choice` 多选：

```json
{ "selected": ["test", "prod"] }
```

`confirm`：

```json
{ "confirmed": true }
```

所有答案都放在 `answer.answers` 下，以 `questions[].id` 作为 key。

## 9. 历史恢复

恢复规则：
- `messageType` 为空或 `chat`：普通聊天消息。
- `messageType=interaction && interactionType=group`：渲染一条 tabs 交互式消息。
- `messageType=answer`：默认不独立渲染为用户气泡，按 `parentMessageId` 折叠到对应 interaction 卡片。
- 如果 answer 找不到 parent，可降级显示为普通用户消息。

## 10. UI 状态

```ts
type InteractionCardState =
  | 'pending'
  | 'submitting'
  | 'answered'
  | 'expired'
  | 'error'
```

推荐转换：

```text
pending -> submitting：用户点击提交
submitting -> answered：SSE 连接建立并开始返回后续响应
submitting -> error：HTTP 或 SSE error
answered -> pending：后端返回新的 question 事件，生成新的卡片
```

## 11. 验收清单

- [ ] 普通聊天使用 `/api/agent/chat/stream` 并传 `interactive: true`。
- [ ] 收到 `question` 后只渲染一条 interaction 消息。
- [ ] 收到 `question` 时追加 interaction 卡片，不删除当前 assistant 流式文本。
- [ ] 当事件顺序是 `message -> question -> done` 时，最终显示普通 assistant 文本和选择卡片两条消息。
- [ ] `done.messageId` 只用于 finalize 当前 assistant 文本，不用于覆盖 question 卡片 id。
- [ ] 多个问题使用 tabs，不拆成多条聊天消息。
- [ ] 用户选择后不新增用户气泡。
- [ ] 提交使用 `parentMessageId + answer.answers`。
- [ ] 提交后继续通过同一个 SSE 流展示 assistant 回复。
- [ ] 历史 `messageType=answer` 默认折叠到对应 interaction 卡片。
- [ ] 409 错误会刷新历史消息，避免重复提交。

## 12. 变更记录

| 日期 | 版本 | 变更 |
| --- | --- | --- |
| 2026-07-12 | V1.0 | 重写为单条 group interaction message；删除旧 batch、多消息、`/reply` 方案 |
| 2026-07-12 | V1.1 | 补充 stream 文本和 `question` 卡片共存的前端事件处理规则 |
