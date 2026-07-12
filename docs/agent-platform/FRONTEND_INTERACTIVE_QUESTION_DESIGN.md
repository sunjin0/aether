# 提问式消息功能设计方案

> 日期：2026-07-12
> 版本：V1.0
> 目标：当 AI 需要用户补充选择或确认信息时，后端生成真实的交互式消息；多个问题必须是一条消息，由前端在消息内部用 tabs 渲染。

## 1. 结论

采用 **单条 group interaction message** 方案，不再兼容旧的多条问题消息、`batchId`、独立 `/reply` 同步接口。

核心规则：
- AI 只能通过内部工具 `ask_user` 发起结构化提问。
- 后端是交互式消息的唯一事实来源；前端不能从普通文本里自行识别按钮或选择器。
- 一次 `ask_user` 可以包含 1-4 个问题，但后端始终只保存一条 `messageType=interaction` 消息。
- 这条消息的 `interactionType` 固定为 `group`，`questionConfig.type` 固定为 `group`，多个问题放在 `questionConfig.questions[]`。
- 前端收到该消息后，在一条聊天消息内部使用 tabs 切换多个问题。
- 如果模型在 `ask_user` 前已经输出 assistant 文本，后端会同时保留这条普通 assistant 消息和后续 interaction 卡片。
- 用户提交后，继续请求 `/api/agent/chat/stream`，通过同一个 SSE 流接收后续 assistant 回复。
- 前端不新增“用户已选择 xxx”的用户气泡；后端仍保存 `messageType=answer` 作为上下文和审计记录。

## 2. 不再使用的旧方案

以下方案已废弃：
- 多个问题保存为多条 interaction message。
- `batchId` / `questionId` 字段。
- 顶层 `answers` 请求字段。
- `/api/agent/chat/reply` 同步回复接口。
- `form` 动态表单类型。
- 前端把多条连续 pending interaction 自行合并成 tabs。

废弃原因：这些方案会让前端误显示多条选择器，也会导致状态恢复、重复提交、权限校验、run 状态和原子提交变复杂。

## 3. 消息结构

### 3.1 SSE question 事件

无论只有一个问题还是多个问题，SSE `question` 事件都只返回一条消息：

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
        "question": "请选择部署环境",
        "options": [
          { "id": "dev", "label": "开发环境", "value": "dev" },
          { "id": "prod", "label": "生产环境", "value": "prod" }
        ],
        "multiple": false
      },
      {
        "id": "backup",
        "type": "confirm",
        "question": "发布前是否备份数据库？",
        "confirmText": "备份",
        "cancelText": "不备份"
      }
    ]
  }
}
```

### 3.2 历史消息

历史接口里的 `AgentMessageVo` 保持单条消息：

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
```

历史接口中 `questionConfig` 是 JSON 字符串；SSE `question` 事件中 `questionConfig` 是 JSON 对象。

## 4. 支持的问题类型

只支持两类问题：
- `choice`：单选或多选。
- `confirm`：确认或取消。

不支持 `form`。如果 AI 需要开放文本输入，应该返回普通 assistant 消息，由用户在输入框自然语言回复。

## 5. 用户提交

用户完成所有问题后，仍请求 `/api/agent/chat/stream`：

```json
{
  "conversationId": "conv_1",
  "parentMessageId": "msg_question_group_1",
  "answer": {
    "answers": {
      "env": {
        "selected": "prod"
      },
      "backup": {
        "confirmed": true
      }
    }
  },
  "interactive": true
}
```

提交规则：
- `parentMessageId` 指向这条 group interaction message。
- `answer.answers` 的 key 必须匹配 `questionConfig.questions[].id`。
- `choice.selected` 可以是字符串或字符串数组。
- `confirm.confirmed` 必须是布尔值。
- 后端校验消息归属、pending 状态、答案格式和选项合法性。
- 后端保存一条 `messageType=answer`，然后把原 interaction 标记为 `answered`。
- 后端继续流式请求模型，前端继续消费同一个 SSE 流。

## 6. SSE 事件顺序

当模型只返回 `ask_user`，没有普通文本时：

```text
question -> done(waitingUser=true, messageId=questionMessageId)
```

当模型先输出普通 assistant 文本，再返回 `ask_user` 时：

```text
message chunks -> question -> done(waitingUser=true, messageId=assistantMessageId)
```

第二种情况下，后端会保存两条 assistant 侧消息：
- `messageType=chat`：保存已流式输出的普通文本。
- `messageType=interaction`：保存选择卡片。

`done.messageId` 指向普通 assistant 文本消息，`question.messageId` 指向 interaction 卡片消息。前端必须把这两条消息都保留。

## 7. 前端渲染

前端只需要识别 `messageType=interaction && interactionType=group`：
- 渲染一条消息卡片。
- 卡片内部用 tabs 展示 `questionConfig.questions[]`。
- 每个 tab 内渲染一个 `choice` 或 `confirm` 控件。
- 未完成必要问题前禁用提交按钮。
- 提交后卡片进入 `submitting`，不要追加用户消息气泡。
- 收到后续 assistant `message` chunks 后，正常流式展示 assistant 回复。
- 收到 SSE `question` 时，追加 interaction 卡片；不要删除、覆盖或改写当前正在流式展示的 assistant 文本。
- 收到 SSE `done` 时，只 finalize 当前流式 assistant 文本；不要用 `done` 去替换 question 卡片。

`messageType=answer` 默认不独立渲染为用户气泡，可按 `parentMessageId` 折叠显示在对应 interaction 卡片中。

## 8. 后端流程

```text
模型返回 ask_user
  -> 如果已有 assistant 文本，先保存 1 条 chat message
  -> 后端校验 questions 数组
  -> 后端保存 1 条 group interaction message
  -> SSE 发送 question 事件
  -> done.waitingUser = true

用户选择后提交 /stream
  -> 后端校验 parentMessageId 指向 pending interaction
  -> 后端校验 answer.answers
  -> 后端保存 1 条 answer message
  -> 后端标记 interaction answered
  -> 后端继续调用模型
  -> SSE 流式返回后续 assistant 回复
```

## 9. 设计边界

这个方案刻意保持协议简单：
- 不让模型生成任意表单 schema。
- 不让前端猜测普通文本是否代表可交互问题。
- 不让多个问题拆成多条消息。
- 不引入同步回复接口。
- 不在前端伪造用户选择消息。

后续如果要支持 `layout=steps` 或 `layout=accordion`，可以只扩展 `questionConfig.layout`，不改变消息和提交模型。
