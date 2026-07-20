# Agent 平台 — 前端 MCP 工具对接

> 合并来源：FRONTEND_MCP_TOOL_INTEGRATION.md、FRONTEND_TOOL_MANAGEMENT_STATISTICS.md、FRONTEND_TOOL_CALL_DISPLAY_PLAN.md
> 补充来源：FRONTEND_INTEGRATION_CHANGES_V0.8_TOOL_CALL_DISPLAY.md（功能部分）
> 更新日期：2026-07-20

---

## 一、概述

### 两层 MCP 模式

```
agent_mcp_server (1) ──→ (N) agent_tool
```

- **MCP 服务**：管理 MCP 服务连接配置（传输协议、认证、超时）
- **工具**：从 MCP 服务导入的具体工具，供 Agent 绑定使用

### 前端菜单建议

| 菜单 | 说明 |
|------|------|
| MCP 服务管理 | 管理 MCP 服务连接配置 |
| MCP 工具管理 | 管理从 MCP 服务导入的具体工具 |

---

## 二、字典接口

通过 `GET /api/sys/dict/options?parentCode={parentCode}&useValue=true` 获取下拉选项。

| 使用位置 | parentCode |
|----------|------------|
| MCP 服务传输类型 | `Agent_Mcp_Transport` |
| MCP 服务认证类型 | `Agent_Mcp_Auth_Type` |
| 工具业务类型 | `Agent_Tool_Type` |
| 状态 | `Agent_Status` |

---

## 三、MCP 服务管理

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表 | POST | `/api/agent/mcp-server/list` |
| 创建 | POST | `/api/agent/mcp-server` |
| 编辑 | PUT | `/api/agent/mcp-server/{id}` |
| 详情 | GET | `/api/agent/mcp-server/{id}` |
| 删除 | DELETE | `/api/agent/mcp-server/{id}` |

### 字段

`name`、`code`、`transport`（http / sse / streamable_http）、`baseUrl`、`requestHeaders`、`authType`、`authToken`、`command`、`args`、`timeoutMs`（默认 30000）、`status`

---

## 四、工具发现与导入

| 功能 | 方法 | 路径 |
|------|------|------|
| 发现服务工具 | POST | `/api/agent/mcp-server/{id}/tools` |
| 批量导入工具 | POST | `/api/agent/mcp-server/{id}/import-tools` |

---

## 五、工具管理

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表 | POST | `/api/agent/tool/list` |
| 创建 | POST | `/api/agent/tool` |
| 编辑 | PUT | `/api/agent/tool/{id}` |
| 详情 | GET | `/api/agent/tool/{id}` |
| 删除 | DELETE | `/api/agent/tool/{id}` |
| 测试 | POST | `/api/agent/tool/{id}/test` |

### 字段

`name`、`code`、`description`、`toolType`（业务类型：knowledge / ops / dev / general）、`mcpServerId`、`mcpToolName`、`mcpInputSchema`、`timeoutMs`、`status`

### 列表新增字段

- `callCount`：调用次数
- `successRate`：成功率

### 工具选择器

展示字段：`name`、`code`、`mcpServerName`、`mcpToolName`

---

## 六、工具统计

### 统计卡片接口

`GET /api/agent/tool/statistics`

```json
{
  "totalCount": 50,
  "enabledCount": 40,
  "disabledCount": 10,
  "callCount": 1200,
  "successCount": 1150,
  "successRate": 0.9583
}
```

### 页面建议

顶部展示 4 个统计卡片：
1. 工具总数
2. 启用工具数
3. 调用次数
4. 成功率

---

## 七、工具绑定管理

绑定对象为 `agent_tool`，绑定关系在 `agent_tool_binding` 表。

| 功能 | 方法 | 路径 |
|------|------|------|
| 查询 Agent 工具绑定 | GET | `/api/agent/definition/{agentId}/tools` |
| 绑定工具 | POST | `/api/agent/definition/{agentId}/tools` |
| 解绑工具 | DELETE | `/api/agent/definition/{agentId}/tools/{toolId}` |
| 调整优先级 | PUT | `/api/agent/definition/{agentId}/tools/{toolId}/priority` |

---

## 八、工具调用日志显示

### 设计结论

以 `agent_tool_call_log` 作为前端展示的事实来源，不新增持久化的 `tool` 角色消息。

### 接口

`GET /api/agent/conversation/{id}/messages?includeToolCalls=true`

assistant 消息的 VO 新增：

```json
{
  "runId": 100,
  "toolCallLogs": [
    {
      "toolCallId": "call_xxx",
      "toolName": "weather",
      "arguments": "{\"city\":\"北京\"}",
      "responseBody": "{\"temp\":25}",
      "responseStatus": 200,
      "latencyMs": 150,
      "status": "SUCCESS"
    }
  ]
}
```

### TypeScript 类型

```typescript
enum ToolCallStatus {
  SUCCESS = 'SUCCESS',
  FAILED = 'FAILED',
  TIMEOUT = 'TIMEOUT'
}

interface AgentToolCallLog {
  id: number;
  toolCallId: string;
  toolName: string;
  arguments: string;
  responseBody: string;
  responseStatus: number;
  latencyMs: number;
  status: ToolCallStatus;
  createdAt: string;
}

interface AgentMessage {
  id: number;
  content: string;
  reasoningContent: string;
  role: string;
  messageType: string;
  runId: number;
  toolCallLogs: AgentToolCallLog[];
}
```

### 前端组件

建议实现 `ToolCallCard` 组件，展示：
- 工具名称（带状态徽章）
- 参数（JSON 格式化、可展开/收起）
- 响应体（JSON 格式化、可展开/收起）
- 耗时、状态

### 明确不做

- 不持久化 `tool` 角色消息
- 不让前端直接解析 `agent_message.toolCalls` 作为最终展示
