# Agent 平台前端对接变更说明（V0.8 工具调用记录显示）

> 日期：2026-07-12
> 范围：V0.8 工具调用记录在聊天列表中显示
> 目标读者：前端开发

---

## 1. 本次前端需要改什么

本次后端主要变更：

1. `agent_tool_call_log` 表新增 `tool_call_id`、`tool_name`、`arguments` 字段
2. 会话消息接口支持 `includeToolCalls` 参数，返回 assistant 消息关联的工具调用日志
3. `AgentMessageVo` 新增 `runId` 和 `toolCallLogs` 字段

前端需要修改：

- 聊天列表支持展示工具调用记录（工具名称、参数、执行结果、状态、耗时）
- 调用会话消息接口时传入 `includeToolCalls=true`

---

## 2. 受影响接口总览

| 接口 | 方法 | 是否有变化 | 前端动作 |
|------|------|------------|----------|
| `/api/agent/conversation/{id}/messages` | GET | ✅ 变更 | 新增 `includeToolCalls` 参数，assistant 消息返回 `toolCallLogs` |

其他接口（Agent 管理、模型供应商、工具管理、聊天、SSE）本次无前端契约变化。

---

## 3. 会话消息接口变更

### 3.1 接口

```http
GET /api/agent/conversation/{id}/messages?current=1&pageSize=20&includeToolCalls=true
```

### 3.2 Query 参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `current` | Long | 1 | 当前页码 |
| `pageSize` | Long | 20 | 每页条数 |
| `includeToolCalls` | Boolean | false | 是否返回工具调用日志 |

### 3.3 响应（includeToolCalls=true）

```json
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "id": "msg_user_1",
      "conversationId": "conv-1",
      "role": "user",
      "content": "查询北京天气",
      "createdAt": 1783769933000
    },
    {
      "id": "msg_assistant_1",
      "conversationId": "conv-1",
      "role": "assistant",
      "content": "查询结果如下...",
      "reasoningContent": null,
      "toolCalls": "[{\"id\":\"call_123\",\"function\":{\"name\":\"get_weather\",\"arguments\":\"{\\\"city\\\":\\\"北京\\\"}\"}}]",
      "runId": "run_1",
      "model": "gpt-4",
      "promptTokens": 1200,
      "completionTokens": 500,
      "totalTokens": 1700,
      "latencyMs": 1500,
      "toolCallLogs": [
        {
          "id": "log_1",
          "runId": "run_1",
          "toolCallId": "call_123",
          "toolId": "tool_weather",
          "toolName": "get_weather",
          "arguments": "{\"city\":\"北京\"}",
          "agentDefinitionId": "agent_1",
          "requestUrl": "https://api.weather.com/beijing",
          "requestMethod": "GET",
          "requestHeaders": null,
          "requestBody": null,
          "responseStatus": 200,
          "responseBody": "{\"temperature\":28,\"weather\":\"晴\",\"humidity\":45}",
          "latencyMs": 230,
          "status": 0,
          "errorMsg": null,
          "createdAt": 1783769933000
        }
      ],
      "createdAt": 1783769933000
    }
  ],
  "total": 2
}
```

### 3.4 响应字段说明

#### AgentMessageVo 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 消息 ID |
| `conversationId` | String | 会话 ID |
| `role` | String | 角色：user / assistant |
| `content` | String | 消息内容 |
| `reasoningContent` | String | 推理内容（可选） |
| `toolCalls` | String | 模型返回的工具调用请求 JSON（调试用） |
| `runId` | String | 关联的运行记录 ID（仅 assistant 消息） |
| `model` | String | 使用的模型 |
| `promptTokens` | Integer | 输入 token 数 |
| `completionTokens` | Integer | 输出 token 数 |
| `totalTokens` | Integer | 总 token 数 |
| `latencyMs` | Integer | 响应延迟（毫秒） |
| `toolCallLogs` | Array | 工具调用日志列表（仅 assistant 消息，需 includeToolCalls=true） |
| `createdAt` | Long | 创建时间（毫秒时间戳） |

#### AgentToolCallLogVo 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | String | 日志 ID |
| `runId` | String | 关联的运行记录 ID |
| `toolCallId` | String | 模型返回的 tool call id（如 `call_xxx`） |
| `toolId` | String | 工具 ID |
| `toolName` | String | 工具名称 |
| `arguments` | String | 模型传给工具的原始参数 JSON |
| `requestUrl` | String | 实际请求 URL |
| `requestMethod` | String | 实际请求方法（GET/POST 等） |
| `requestHeaders` | String | 实际请求头（JSON） |
| `requestBody` | String | 实际请求体 |
| `responseStatus` | Integer | HTTP 响应状态码 |
| `responseBody` | String | 响应体 |
| `latencyMs` | Integer | 执行耗时（毫秒） |
| `status` | Integer | 状态：0-成功，1-失败，2-超时，3-安全拦截 |
| `errorMsg` | String | 错误信息 |
| `createdAt` | Long | 创建时间（毫秒时间戳） |

---

## 4. 前端类型定义

```typescript
// 工具调用日志
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
  createdAt: number
}

// 消息
interface AgentMessage {
  id: string
  conversationId: string
  role: 'user' | 'assistant'
  content: string
  reasoningContent?: string
  toolCalls?: string
  runId?: string
  model?: string
  promptTokens?: number
  completionTokens?: number
  totalTokens?: number
  latencyMs?: number
  toolCallLogs?: AgentToolCallLog[]
  createdAt: number
}

// 工具调用状态枚举
enum ToolCallStatus {
  SUCCESS = 0,
  FAILED = 1,
  TIMEOUT = 2,
  SECURITY_BLOCK = 3
}
```

---

## 5. API 调用示例

```typescript
// 获取会话消息（包含工具调用日志）
async function getConversationMessages(
  conversationId: string,
  current: number = 1,
  pageSize: number = 20,
  includeToolCalls: boolean = true
): Promise<{ data: AgentMessage[]; total: number }> {
  const params = new URLSearchParams({
    current: current.toString(),
    pageSize: pageSize.toString(),
    includeToolCalls: includeToolCalls.toString()
  })
  
  const response = await fetch(
    `/api/agent/conversation/${conversationId}/messages?${params}`
  )
  const result = await response.json()
  return { data: result.data, total: result.total }
}
```

---

## 6. 聊天列表渲染逻辑

### 6.1 消息渲染

```tsx
function ChatMessage({ message }: { message: AgentMessage }) {
  return (
    <div className={`chat-message ${message.role}`}>
      {/* 消息内容 */}
      <div className="message-content">
        {message.content}
      </div>

      {/* 推理内容（可选） */}
      {message.reasoningContent && (
        <div className="reasoning-content">
          <details>
            <summary>推理过程</summary>
            <p>{message.reasoningContent}</p>
          </details>
        </div>
      )}

      {/* 工具调用日志 */}
      {message.role === 'assistant' && 
       message.toolCallLogs && 
       message.toolCallLogs.length > 0 && (
        <div className="tool-call-logs">
          {message.toolCallLogs.map(log => (
            <ToolCallCard key={log.id} log={log} />
          ))}
        </div>
      )}

      {/* 消息元信息 */}
      <div className="message-meta">
        {message.model && <span className="model">{message.model}</span>}
        {message.latencyMs && (
          <span className="latency">{formatLatency(message.latencyMs)}</span>
        )}
      </div>
    </div>
  )
}
```

### 6.2 工具调用卡片

```tsx
function ToolCallCard({ log }: { log: AgentToolCallLog }) {
  const [expanded, setExpanded] = useState(false)
  
  return (
    <div className={`tool-call-card status-${log.status}`}>
      {/* 工具调用头部 */}
      <div 
        className="tool-call-header"
        onClick={() => setExpanded(!expanded)}
      >
        <span className="tool-icon">🔧</span>
        <span className="tool-name">{log.toolName || '未知工具'}</span>
        <span className={`status-badge status-${log.status}`}>
          {getToolStatusText(log.status)}
        </span>
        {log.latencyMs && (
          <span className="latency">{log.latencyMs}ms</span>
        )}
        <span className="expand-icon">{expanded ? '▼' : '▶'}</span>
      </div>

      {/* 展开详情 */}
      {expanded && (
        <div className="tool-call-details">
          {/* 请求参数 */}
          {log.arguments && (
            <div className="detail-section">
              <label>请求参数：</label>
              <pre>{formatJSON(log.arguments)}</pre>
            </div>
          )}

          {/* 执行结果 */}
          {log.responseBody && (
            <div className="detail-section">
              <label>执行结果：</label>
              <pre>{formatJSON(log.responseBody)}</pre>
            </div>
          )}

          {/* 错误信息 */}
          {log.errorMsg && (
            <div className="detail-section error">
              <label>错误信息：</label>
              <pre>{log.errorMsg}</pre>
            </div>
          )}

          {/* 详细信息 */}
          <div className="detail-section">
            <label>请求 URL：</label>
            <span>{log.requestUrl || '-'}</span>
          </div>
          <div className="detail-section">
            <label>请求方法：</label>
            <span>{log.requestMethod || '-'}</span>
          </div>
          <div className="detail-section">
            <label>响应状态码：</label>
            <span>{log.responseStatus || '-'}</span>
          </div>
        </div>
      )}
    </div>
  )
}
```

### 6.3 辅助函数

```typescript
// 工具调用状态文本
function getToolStatusText(status: number): string {
  switch (status) {
    case 0: return '成功'
    case 1: return '失败'
    case 2: return '超时'
    case 3: return '安全拦截'
    default: return '未知'
  }
}

// 格式化延迟
function formatLatency(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)}s`
  }
  return `${ms}ms`
}

// 格式化 JSON
function formatJSON(json: string): string {
  try {
    const obj = JSON.parse(json)
    return JSON.stringify(obj, null, 2)
  } catch {
    return json
  }
}
```

---

## 7. 样式建议

```css
/* 工具调用卡片 */
.tool-call-card {
  margin: 8px 0;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  background: #f9f9f9;
}

.tool-call-header {
  display: flex;
  align-items: center;
  padding: 8px 12px;
  cursor: pointer;
  gap: 8px;
}

.tool-call-header:hover {
  background: #f0f0f0;
}

.tool-name {
  font-weight: 500;
  color: #333;
}

.status-badge {
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
  color: white;
}

.status-badge.status-0 { background: #52c41a; } /* 成功 */
.status-badge.status-1 { background: #ff4d4f; } /* 失败 */
.status-badge.status-2 { background: #faad14; } /* 超时 */
.status-badge.status-3 { background: #722ed1; } /* 安全拦截 */

.tool-call-details {
  padding: 12px;
  border-top: 1px solid #e0e0e0;
  background: white;
}

.detail-section {
  margin-bottom: 12px;
}

.detail-section label {
  display: block;
  font-weight: 500;
  margin-bottom: 4px;
  color: #666;
}

.detail-section pre {
  background: #f5f5f5;
  padding: 8px;
  border-radius: 4px;
  overflow-x: auto;
  font-size: 12px;
  margin: 0;
}

.detail-section.error label {
  color: #ff4d4f;
}

.detail-section.error pre {
  background: #fff2f0;
  border: 1px solid #ffccc7;
}
```

---

## 8. 聊天列表整合示例

```tsx
function ChatList({ conversationId }: { conversationId: string }) {
  const [messages, setMessages] = useState<AgentMessage[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function loadMessages() {
      setLoading(true)
      const { data } = await getConversationMessages(
        conversationId,
        1,
        50,
        true // includeToolCalls
      )
      setMessages(data)
      setLoading(false)
    }
    loadMessages()
  }, [conversationId])

  if (loading) return <Spinner />

  return (
    <div className="chat-list">
      {messages.map(message => (
        <ChatMessage key={message.id} message={message} />
      ))}
    </div>
  )
}
```

---

## 9. 推荐前端改动清单

### 9.1 类型定义

- [ ] 新增 `AgentToolCallLog` 接口
- [ ] 新增 `ToolCallStatus` 枚举
- [ ] 更新 `AgentMessage` 接口，新增 `runId` 和 `toolCallLogs` 字段

### 9.2 API 调用

- [ ] 更新 `getConversationMessages()` 函数，支持 `includeToolCalls` 参数

### 9.3 组件开发

- [ ] 新增 `ToolCallCard` 组件
- [ ] 更新 `ChatMessage` 组件，支持渲染工具调用日志

### 9.4 样式开发

- [ ] 新增工具调用卡片样式
- [ ] 新增状态徽章样式

### 9.5 测试

- [ ] 测试工具调用成功时的展示
- [ ] 测试工具调用失败时的展示
- [ ] 测试展开/收起详情功能
- [ ] 测试 `includeToolCalls=false` 时的降级展示

---

## 10. 兼容性说明

### 10.1 向后兼容

- `includeToolCalls` 默认为 `false`，老前端调用时响应结构不变
- `toolCallLogs` 字段仅在 `includeToolCalls=true` 时返回
- `toolCalls` 字段保留为调试用，不作为主要展示来源

### 10.2 历史数据

- 历史工具日志可能缺少 `tool_call_id`、`tool_name`、`arguments` 字段
- 前端应允许这些字段为空，仍可展示 `request_body`、`response_body`、`status`、`latency_ms`

### 10.3 权限

会话消息接口继承会话查看权限，仅当前用户可访问自己的会话。

---

## 11. 后端部署提醒

### 11.1 数据库迁移

需要执行以下迁移脚本：

```sql
-- V0.8 工具调用记录显示
ALTER TABLE `agent_tool_call_log`
    ADD COLUMN `tool_call_id` VARCHAR(128) DEFAULT NULL COMMENT '模型返回的tool call id（如call_xxx）' AFTER `tool_id`,
    ADD COLUMN `tool_name` VARCHAR(128) DEFAULT NULL COMMENT '工具名称' AFTER `tool_call_id`,
    ADD COLUMN `arguments` TEXT COMMENT '模型传给工具的原始参数JSON' AFTER `tool_name`;

CREATE INDEX idx_tool_call_log_run_call
ON agent_tool_call_log (run_id, tool_call_id);
```

### 11.2 新安装

新安装用户无需执行迁移脚本，`agent-platform-v0.2.sql` 已包含新字段。
