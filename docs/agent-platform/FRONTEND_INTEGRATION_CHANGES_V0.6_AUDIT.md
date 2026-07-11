# Agent 平台前端对接变更说明（V0.6 运营审计）

> 日期：2026-07-12
> 范围：V0.6 运营审计模块新增接口
> 目标读者：前端开发

---

## 1. 本次前端需要改什么

本次后端主要新增两类运营审计能力：

1. 会话生命周期查询（创建时间、最后活跃时间、状态变化等）
2. 会话消息统计（消息数量、token 消耗、延迟统计等）

前端需要修改：

- 会话详情页新增"生命周期"和"消息统计"展示区域
- 新增调用对应接口获取数据的逻辑

---

## 2. 受影响接口总览

| 接口 | 方法 | 是否有变化 | 前端动作 |
|------|------|------------|----------|
| `/api/agent/conversation/{id}/lifecycle` | GET | 🆕 新增 | 调用接口获取会话生命周期信息 |
| `/api/agent/conversation/{id}/statistics` | GET | 🆕 新增 | 调用接口获取会话消息统计 |

其他 Agent 管理、模型供应商、工具管理、聊天、SSE 接口本次无前端契约变化。

---

## 3. 会话生命周期查询接口

### 3.1 接口

```http
GET /api/agent/conversation/{id}/lifecycle
```

### 3.2 Query 参数

无

### 3.3 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conversation-1",
    "createdAt": 1783769933000,
    "lastActiveAt": 1783770533000,
    "closedAt": null,
    "status": 0,
    "messageCount": 10,
    "totalUserMessages": 5,
    "totalAssistantMessages": 5,
    "durationMs": 600000
  }
}
```

### 3.4 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `conversationId` | String | 会话 ID |
| `createdAt` | Long | 会话创建时间（毫秒时间戳） |
| `lastActiveAt` | Long | 最后活跃时间（最后一条消息的时间） |
| `closedAt` | Long | 关闭时间（仅 status=1 时有值，否则为 null） |
| `status` | Integer | 会话状态：0-进行中，1-关闭，2-归档 |
| `messageCount` | Integer | 当前消息数（来自会话表的计数） |
| `totalUserMessages` | Long | 实际用户消息总数 |
| `totalAssistantMessages` | Long | 实际助手消息总数 |
| `durationMs` | Long | 会话持续时间（毫秒，从创建到最后活跃） |

### 3.5 前端展示建议

```
┌─────────────────────────────────────────┐
│ 📊 会话生命周期                          │
│                                         │
│ 创建时间：2026-07-12 10:00:00           │
│ 最后活跃：2026-07-12 10:10:00           │
│ 状态：进行中                             │
│ 持续时间：10 分钟                         │
│                                         │
│ 📈 消息统计                             │
│ 用户消息：5 条                           │
│ 助手消息：5 条                           │
└─────────────────────────────────────────┘
```

### 3.6 前端处理示例

```ts
interface ConversationLifecycle {
  conversationId: string
  createdAt: number
  lastActiveAt: number
  closedAt: number | null
  status: 0 | 1 | 2
  messageCount: number
  totalUserMessages: number
  totalAssistantMessages: number
  durationMs: number
}

async function getConversationLifecycle(conversationId: string): Promise<ConversationLifecycle> {
  const response = await fetch(`/api/agent/conversation/${conversationId}/lifecycle`)
  const result = await response.json()
  return result.data
}

// 格式化持续时间
function formatDuration(ms: number): string {
  const minutes = Math.floor(ms / 60000)
  const seconds = Math.floor((ms % 60000) / 1000)
  if (minutes > 0) {
    return `${minutes} 分钟 ${seconds} 秒`
  }
  return `${seconds} 秒`
}

// 格式化时间戳
function formatTimestamp(timestamp: number): string {
  return new Date(timestamp).toLocaleString('zh-CN')
}
```

---

## 4. 会话消息统计接口

### 4.1 接口

```http
GET /api/agent/conversation/{id}/statistics
```

### 4.2 Query 参数

无

### 4.3 响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "conversationId": "conversation-1",
    "totalMessages": 10,
    "userMessages": 5,
    "assistantMessages": 5,
    "toolMessages": 0,
    "totalPromptTokens": 12000,
    "totalCompletionTokens": 5000,
    "totalTokens": 17000,
    "avgLatencyMs": 1300
  }
}
```

### 4.4 响应字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| `conversationId` | String | 会话 ID |
| `totalMessages` | Long | 总消息数（user + assistant + tool） |
| `userMessages` | Long | 用户消息数 |
| `assistantMessages` | Long | 助手消息数 |
| `toolMessages` | Long | 工具调用消息数 |
| `totalPromptTokens` | Long | 总输入 token 数 |
| `totalCompletionTokens` | Long | 总输出 token 数 |
| `totalTokens` | Long | 总 token 数 |
| `avgLatencyMs` | Long | 平均响应延迟（毫秒） |

### 4.5 前端展示建议

```
┌─────────────────────────────────────────┐
│ 📊 消息统计                             │
│                                         │
│ 总消息数：10 条                          │
│ ├─ 用户消息：5 条                        │
│ ├─ 助手消息：5 条                        │
│ └─ 工具调用：0 条                        │
│                                         │
│ 💰 Token 消耗                           │
│ ├─ 输入 token：12,000                   │
│ ├─ 输出 token：5,000                    │
│ └─ 总计：17,000                         │
│                                         │
│ ⏱️ 性能指标                             │
│ 平均延迟：1.3 秒                         │
└─────────────────────────────────────────┘
```

### 4.6 前端处理示例

```ts
interface MessageStatistics {
  conversationId: string
  totalMessages: number
  userMessages: number
  assistantMessages: number
  toolMessages: number
  totalPromptTokens: number
  totalCompletionTokens: number
  totalTokens: number
  avgLatencyMs: number
}

async function getConversationStatistics(conversationId: string): Promise<MessageStatistics> {
  const response = await fetch(`/api/agent/conversation/${conversationId}/statistics`)
  const result = await response.json()
  return result.data
}

// 格式化 token 数
function formatTokens(tokens: number): string {
  if (tokens >= 1000000) {
    return `${(tokens / 1000000).toFixed(1)}M`
  }
  if (tokens >= 1000) {
    return `${(tokens / 1000).toFixed(1)}K`
  }
  return tokens.toString()
}

// 格式化延迟
function formatLatency(ms: number): string {
  if (ms >= 1000) {
    return `${(ms / 1000).toFixed(1)} 秒`
  }
  return `${ms} 毫秒`
}
```

---

## 5. 会话详情页整合

### 5.1 推荐布局

会话详情页可以新增一个"统计信息"区域，展示生命周期和消息统计：

```ts
// 获取并展示会话统计信息
async function loadConversationStats(conversationId: string) {
  const [lifecycle, statistics] = await Promise.all([
    getConversationLifecycle(conversationId),
    getConversationStatistics(conversationId),
  ])

  renderLifecycleSection(lifecycle)
  renderStatisticsSection(statistics)
}
```

### 5.2 完整示例

```ts
// 会话详情页组件
function ConversationDetailPage({ conversationId }: { conversationId: string }) {
  const [lifecycle, setLifecycle] = useState<ConversationLifecycle | null>(null)
  const [statistics, setStatistics] = useState<MessageStatistics | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    async function loadData() {
      setLoading(true)
      const [lifecycleData, statsData] = await Promise.all([
        getConversationLifecycle(conversationId),
        getConversationStatistics(conversationId),
      ])
      setLifecycle(lifecycleData)
      setStatistics(statsData)
      setLoading(false)
    }
    loadData()
  }, [conversationId])

  if (loading) return <Spinner />

  return (
    <div className="conversation-detail">
      {/* 原有的消息列表 */}
      <MessageList conversationId={conversationId} />

      {/* 新增的统计信息区域 */}
      {lifecycle && statistics && (
        <div className="conversation-stats">
          <h3>会话统计</h3>
          
          <div className="lifecycle-section">
            <p>创建时间：{formatTimestamp(lifecycle.createdAt)}</p>
            <p>最后活跃：{formatTimestamp(lifecycle.lastActiveAt)}</p>
            <p>持续时间：{formatDuration(lifecycle.durationMs)}</p>
            <p>状态：{getStatusText(lifecycle.status)}</p>
          </div>

          <div className="statistics-section">
            <p>用户消息：{lifecycle.totalUserMessages} 条</p>
            <p>助手消息：{lifecycle.totalAssistantMessages} 条</p>
            <p>总 Token：{formatTokens(statistics.totalTokens)}</p>
            <p>平均延迟：{formatLatency(statistics.avgLatencyMs)}</p>
          </div>
        </div>
      )}
    </div>
  )
}
```

---

## 6. 推荐前端改动清单

### 6.1 类型定义

- [ ] 新增 `ConversationLifecycle` 接口
- [ ] 新增 `MessageStatistics` 接口

### 6.2 API 调用

- [ ] 新增 `getConversationLifecycle()` 函数
- [ ] 新增 `getConversationStatistics()` 函数

### 6.3 会话详情页

- [ ] 新增统计信息展示区域
- [ ] 格式化时间戳和持续时间
- [ ] 格式化 token 数和延迟

### 6.4 会话列表页（可选）

- [ ] 列表项显示消息数、最后活跃时间等摘要信息

---

## 7. 兼容性说明

### 7.1 新接口

`/api/agent/conversation/{id}/lifecycle` 和 `/api/agent/conversation/{id}/statistics` 为新增接口，不影响现有功能。

### 7.2 权限

这两个接口继承会话查看权限，仅当前用户可访问自己的会话。访问不属于当前用户的会话会返回 404。

### 7.3 性能

统计接口会查询消息表进行聚合计算，数据量大时可能有一定延迟。建议：
- 仅在会话详情页加载时调用
- 可考虑添加 loading 状态提示
- 不要在会话列表页批量调用

---

## 8. 后端部署提醒

本次无数据库迁移，仅新增代码。

如需回滚，回退代码即可。
