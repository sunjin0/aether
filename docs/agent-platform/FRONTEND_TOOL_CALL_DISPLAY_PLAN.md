# 工具调用记录在聊天列表中显示 - 实现计划

> 日期：2026-07-12
> 版本：V0.8（规划中，已按评审意见修订）
> 目标：在聊天列表中把工具调用记录展示在对应 assistant 消息下方，包括工具名称、请求参数、执行结果、状态和耗时。

---

## 1. 结论

推荐以 `agent_tool_call_log` 作为前端展示的事实来源，不新增持久化的 `tool` 角色消息。

原因：

- `agent_message` 表示用户和助手的自然对话，保持聊天列表排序简单。
- `agent_tool_call_log` 已经保存请求、响应、状态、耗时等审计信息，更适合作为工具调用展示来源。
- 当前工具执行结果只在本次模型上下文中临时传递；持久化为 `tool` 消息会让消息列表变成 `user -> tool -> assistant`，不利于前端在 assistant 气泡下聚合展示。
- 如果同时依赖 `agent_message.toolCalls` 和 `agent_tool_call_log`，会出现两套来源，后续容易产生关联和一致性问题。

---

## 2. 当前状态

| 组件 | 状态 | 说明 |
|------|------|------|
| `agent_tool_call_log` 表 | 已保存 | 已保存请求 URL、请求体、响应体、状态、耗时等 |
| `agent_run` 表 | 已保存 | `messageId` 最终会关联 assistant 消息 |
| `agent_message.toolCalls` | 部分保存 | 流式保存，非流式未保存；但最终 assistant 响应通常不再包含 tool calls |
| `tool` 角色消息 | 未持久化 | 仅用于本次模型上下文 |
| 会话消息接口 | 已存在 | `/api/agent/conversation/{id}/messages` 返回消息分页 |

### 2.1 推荐数据关系

```
agent_message (assistant)
    └── agent_run (message_id -> agent_message.id)
            └── agent_tool_call_log (run_id -> agent_run.id)
```

前端展示时，将同一 assistant 消息对应的 `agent_tool_call_log` 聚合到该消息的 `toolCallLogs` 字段中。

---

## 3. 数据模型调整

### Phase 1：补齐工具日志关联字段

**文件**：

- `api/src/main/java/com/aether/agent/entity/AgentToolCallLog.java`
- `api/src/main/java/com/aether/agent/vo/AgentToolCallLogVo.java`
- 数据库迁移脚本或初始化 SQL

建议给 `agent_tool_call_log` 增加字段：

| 字段 | 类型 | 说明 |
|------|------|------|
| `tool_call_id` | VARCHAR(128) | 模型返回的 tool call id，例如 `call_xxx` |
| `tool_name` | VARCHAR(128) | 模型请求调用的工具名称，便于展示和审计 |
| `arguments` | TEXT | 模型传给工具的原始参数 JSON |

推荐索引：

```sql
CREATE INDEX idx_tool_call_log_run_call
ON agent_tool_call_log (run_id, tool_call_id);
```

> 说明：如果短期不想做数据库迁移，最低限度也应在 `response_body/request_body` 中展示结果和请求体。但没有 `tool_call_id` 时，同一 run 内多次调用同一工具无法稳定匹配到模型返回的单个 tool call。

---

## 4. 后端实现方案

### Phase 2：保存工具调用的模型侧信息

**文件**：`biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`

当前 `executeToolCalls` 已经能解析：

- `toolCall.getId()`
- `toolCall.getName()`
- `toolCall.getArguments()`

调整 `saveToolCallLog` 入参和保存逻辑，写入 `toolCallId`、`toolName`、`arguments`。

示例：

```java
saveToolCallLog(
        runId,
        toolCall.getId(),
        toolCall.getName(),
        JSON.toJSONString(toolCall.getArguments()),
        tool.getId(),
        agent.getId(),
        result.getRequestUrl(),
        result.getRequestMethod(),
        result.getRequestHeaders(),
        result.getRequestBody(),
        result.getHttpStatus(),
        result.getRawResponse(),
        result.getLatencyMs(),
        result.getStatus(),
        result.getErrorMsg()
);
```

工具未找到或工具执行异常时也要保存同样的 `toolCallId/toolName/arguments`，否则前端无法展示失败调用。

### Phase 3：保留非流式 `toolCalls` 修复，但不作为主展示来源

**文件**：`AgentChatServiceImpl.java`

非流式 `saveAssistantMessage(ModelChatResponse)` 可以补上：

```java
message.setToolCalls(modelResponse.getToolCalls());
```

但需要明确：工具调用循环结束后的最终 assistant 响应通常不再包含 `toolCalls`，所以 `toolCalls` 只作为原始模型响应留档，不作为聊天列表工具卡片的主要数据来源。

### Phase 4：增强会话消息 VO

**文件**：`api/src/main/java/com/aether/agent/vo/AgentMessageVo.java`

新增字段：

```java
private String runId;
private List<AgentToolCallLogVo> toolCallLogs;
```

### Phase 5：会话消息接口聚合工具日志

**文件**：`admin/src/main/java/com/aether/agent/controller/AgentConversationController.java`

推荐优先增强现有接口：

```http
GET /api/agent/conversation/{id}/messages?current=1&pageSize=20&includeToolCalls=true
```

默认 `includeToolCalls=false`，保证老前端不受影响。新前端传 `true` 后，接口返回：

```json
{
  "id": "msg_assistant_1",
  "role": "assistant",
  "content": "查询结果如下...",
  "runId": "run_1",
  "toolCallLogs": [
    {
      "id": "log_1",
      "runId": "run_1",
      "toolCallId": "call_123",
      "toolName": "get_weather",
      "arguments": "{\"city\":\"北京\"}",
      "requestBody": "...",
      "responseBody": "{\"temperature\":28,\"weather\":\"晴\"}",
      "status": 0,
      "latencyMs": 230
    }
  ]
}
```

实现要点：

1. 查询当前页消息。
2. 取当前页 assistant 消息 ID。
3. 一次性查询 `agent_run`：`message_id in assistantMessageIds`。
4. 一次性查询 `agent_tool_call_log`：`run_id in runIds`。
5. 按 `messageId -> run -> logs` 组装到 `AgentMessageVo.toolCallLogs`。

不要在每条消息上逐条查 run 或 tool log，避免 N+1 查询。

### Phase 6：保留独立工具日志接口，作为详情和调试入口

可以新增会话维度接口：

```http
GET /api/agent/conversation/{id}/tool-calls?runId=xxx
```

用途是工具调用详情页、调试面板、审计视图，不作为聊天列表首选数据来源。

实现注意：

- 先调用 `getOwnedConversation(id)` 校验当前用户拥有会话。
- 只允许查询该会话下的 run。
- 使用 `LambdaQueryWrapper<AgentToolCallLog>`，不要用 `Wrapper` 变量后再调用 `.eq(...)`。
- 补充工具名称时优先使用日志表的 `tool_name`，避免对 `agent_tool` 做 N+1 查询；历史数据缺失时再批量查工具表兜底。

---

## 5. 明确不做的事项

### 5.1 不持久化 `tool` 角色消息

不新增：

```java
saveToolMessage(conversationId, toolContent, toolCallId)
```

原因：

- 会改变消息列表排序，导致前端需要把散落的 `tool` 消息重新归并到 assistant 消息。
- 当前上下文重建只取 `user/assistant`，持久化 `tool` 消息对后续模型上下文没有直接收益。
- 工具请求和执行结果已经由 `agent_tool_call_log` 负责保存，重复存储会增加一致性风险。

### 5.2 不让前端直接解析 `agent_message.toolCalls` 作为最终展示

`toolCalls` 可以保留为调试字段，但聊天列表展示应以 `toolCallLogs` 为准。

---

## 6. 前端展示方案

### 6.1 类型定义

```typescript
interface AgentToolCallLog {
  id: string
  runId: string
  toolCallId?: string
  toolId?: string
  toolName?: string
  arguments?: string
  requestUrl?: string
  requestMethod?: string
  requestHeaders?: string
  requestBody?: string
  responseStatus?: number
  responseBody?: string
  latencyMs?: number
  status: 0 | 1 | 2 | 3
  errorMsg?: string
}

interface AgentMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  reasoningContent?: string
  runId?: string
  toolCallLogs?: AgentToolCallLog[]
}
```

### 6.2 渲染逻辑

```typescript
function renderMessage(message: AgentMessage) {
  return (
    <MessageBubble message={message}>
      <MessageContent content={message.content} />

      {message.role === 'assistant' &&
        message.toolCallLogs?.map(log => (
          <ToolCallCard key={log.id} log={log} />
        ))}
    </MessageBubble>
  )
}
```

### 6.3 展示效果

```text
助手：
查询结果如下...

工具调用：get_weather
参数：{ "city": "北京" }
状态：成功 | 耗时：230ms
结果：{ "temperature": 28, "weather": "晴" }
```

---

## 7. 修改文件清单

| 文件 | 修改内容 | 是否必须 |
|------|----------|----------|
| `AgentToolCallLog.java` | 新增 `toolCallId/toolName/arguments` | 是 |
| `AgentToolCallLogVo.java` | 新增 `toolCallId/toolName/arguments` | 是 |
| 数据库迁移脚本 | 给 `agent_tool_call_log` 增加字段和索引 | 是 |
| `AgentChatServiceImpl.java` | 保存工具日志时写入模型侧调用信息 | 是 |
| `AgentMessageVo.java` | 新增 `runId/toolCallLogs` | 是 |
| `AgentConversationController.java` | 消息接口支持 `includeToolCalls` 并批量聚合 | 是 |
| `AgentToolCallLogService.java` | 可选新增会话维度查询方法 | 可选 |
| `AgentToolCallLogServiceImpl.java` | 可选实现会话维度查询 | 可选 |

---

## 8. 兼容性说明

- 老前端继续调用 `/messages` 且不传 `includeToolCalls` 时，响应结构可保持原状。
- 新前端传 `includeToolCalls=true` 后，在 assistant 消息上读取 `toolCallLogs`。
- 历史工具日志没有 `tool_call_id/tool_name/arguments` 时，前端应允许字段为空，仍可展示请求体、响应体、状态、耗时。
- `agent_message.toolCalls` 字段继续保留，但不作为聊天列表展示的主数据来源。

---

## 9. 测试用例

### 9.1 单元测试

1. 工具调用成功时，`agent_tool_call_log` 保存 `toolCallId/toolName/arguments/requestBody/responseBody/status/latencyMs`。
2. 工具未找到时，仍保存 `toolCallId/toolName/arguments/status/errorMsg`。
3. 工具执行异常时，仍保存 `toolCallId/toolName/arguments/status/errorMsg`。
4. 会话消息聚合逻辑按 `messageId -> runId -> toolCallLogs` 正确组装。
5. 多工具调用、同一工具多次调用时，`toolCallId` 能区分每次调用。

### 9.2 集成测试

1. 配置一个 Agent 绑定工具。
2. 发送触发工具调用的消息。
3. 调用 `/api/agent/conversation/{id}/messages?includeToolCalls=true`。
4. 验证 assistant 消息包含 `toolCallLogs`。
5. 验证工具卡片字段包括工具名称、参数、响应、状态和耗时。
6. 验证无权限用户不能通过会话维度接口读取他人的工具调用日志。

---

## 10. 验收标准

- 聊天列表不出现独立的 `tool` 角色消息。
- 每条 assistant 消息能展示它触发的一个或多个工具调用。
- 多工具调用场景下，工具名称、参数、结果、状态和耗时不会串到其他调用。
- 历史无新增字段的数据可以降级展示，不导致前端报错。
- 查询实现没有明显 N+1 查询。
