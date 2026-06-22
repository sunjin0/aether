# Agent 平台 API 接口草案

> 版本：V0.1 文档基线
> 状态：草案（待评审确认）
> 范围：接口路径、请求/响应格式、SSE 事件定义，不实现代码

---

## 1. 设计原则

- **统一前缀**：所有 Agent 平台接口使用 `/api/agent/**` 前缀
- **统一响应**：管理接口返回 `WebResponse<T>`（复用现有 `com.aether.entity.WebResponse`）
- **RESTful 风格**：资源路径使用名词，操作通过 HTTP 方法区分
- **权限控制**：基于 `@Permission` 注解，路径与现有权限体系兼容
- **版本说明**：本草案为 V0.1 规划，后续版本可能调整

---

## 2. 通用响应格式

### 2.1 管理接口响应

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "total": 0
}
```

- `code`：业务状态码，200 表示成功
- `message`：提示信息，支持 i18n
- `data`：业务数据
- `total`：分页总条数（非分页接口为 0）

### 2.2 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "records": [ ... ],
    "total": 100,
    "pageSize": 20,
    "current": 1,
    "pages": 5
  }
}
```

---

## 3. 模型供应商管理接口

### 3.1 列表查询

- **GET** `/api/agent/model-provider/list`
- **Query**：`current`, `pageSize`, `name`, `type`, `status`
- **Response**：`WebResponse<Page<ModelProviderVO>>`

### 3.2 详情查询

- **GET** `/api/agent/model-provider/{id}`
- **Response**：`WebResponse<ModelProviderVO>`

### 3.3 新增

- **POST** `/api/agent/model-provider`
- **Body**：`ModelProviderDTO`
- **Response**：`WebResponse<Long>`（返回 ID）

### 3.4 编辑

- **PUT** `/api/agent/model-provider/{id}`
- **Body**：`ModelProviderDTO`
- **Response**：`WebResponse<Void>`

### 3.5 删除

- **DELETE** `/api/agent/model-provider/{id}`
- **Response**：`WebResponse<Void>`

### 3.6 启用/禁用

- **PUT** `/api/agent/model-provider/{id}/status`
- **Body**：`{ "status": 1 }`
- **Response**：`WebResponse<Void>`

### 3.7 测试连接

- **POST** `/api/agent/model-provider/{id}/test`
- **Response**：`WebResponse<Boolean>`

---

## 4. Agent 管理接口

### 4.1 列表查询

- **GET** `/api/agent/definition/list`
- **Query**：`current`, `pageSize`, `name`, `code`, `status`, `modelProviderId`
- **Response**：`WebResponse<Page<AgentDefinitionVO>>`

### 4.2 详情查询

- **GET** `/api/agent/definition/{id}`
- **Response**：`WebResponse<AgentDefinitionVO>`

### 4.3 新增

- **POST** `/api/agent/definition`
- **Body**：`AgentDefinitionDTO`
- **Response**：`WebResponse<Long>`

### 4.4 编辑

- **PUT** `/api/agent/definition/{id}`
- **Body**：`AgentDefinitionDTO`
- **Response**：`WebResponse<Void>`

### 4.5 删除

- **DELETE** `/api/agent/definition/{id}`
- **Response**：`WebResponse<Void>`

### 4.6 启用/禁用

- **PUT** `/api/agent/definition/{id}/status`
- **Body**：`{ "status": 1 }`
- **Response**：`WebResponse<Void>`

### 4.7 复制

- **POST** `/api/agent/definition/{id}/copy`
- **Response**：`WebResponse<Long>`（返回新 Agent ID）

---

## 5. 工具管理接口

### 5.1 列表查询

- **GET** `/api/agent/tool/list`
- **Query**：`current`, `pageSize`, `name`, `code`, `type`, `status`
- **Response**：`WebResponse<Page<AgentToolVO>>`

### 5.2 详情查询

- **GET** `/api/agent/tool/{id}`
- **Response**：`WebResponse<AgentToolVO>`

### 5.3 新增

- **POST** `/api/agent/tool`
- **Body**：`AgentToolDTO`
- **Response**：`WebResponse<Long>`

### 5.4 编辑

- **PUT** `/api/agent/tool/{id}`
- **Body**：`AgentToolDTO`
- **Response**：`WebResponse<Void>`

### 5.5 删除

- **DELETE** `/api/agent/tool/{id}`
- **Response**：`WebResponse<Void>`

### 5.6 测试工具

- **POST** `/api/agent/tool/{id}/test`
- **Body**：`{ "param1": "value1" }`（测试参数）
- **Response**：`WebResponse<AgentToolTestResultVO>`

---

## 6. 工具绑定管理接口

### 6.1 查询 Agent 的工具绑定

- **GET** `/api/agent/definition/{agentId}/tools`
- **Response**：`WebResponse<List<AgentToolBindingVO>>`

### 6.2 绑定工具

- **POST** `/api/agent/definition/{agentId}/tools`
- **Body**：`{ "toolId": 1, "priority": 0 }`
- **Response**：`WebResponse<Void>`

### 6.3 解绑工具

- **DELETE** `/api/agent/definition/{agentId}/tools/{toolId}`
- **Response**：`WebResponse<Void>`

### 6.4 调整优先级

- **PUT** `/api/agent/definition/{agentId}/tools/{toolId}/priority`
- **Body**：`{ "priority": 1 }`
- **Response**：`WebResponse<Void>`

---

## 7. 会话管理接口

### 7.1 查询会话列表

- **GET** `/api/agent/conversation/list`
- **Query**：`current`, `pageSize`, `agentId`, `status`
- **Response**：`WebResponse<Page<ConversationVO>>`

### 7.2 查询会话详情

- **GET** `/api/agent/conversation/{id}`
- **Response**：`WebResponse<ConversationVO>`

### 7.3 查询会话消息

- **GET** `/api/agent/conversation/{id}/messages`
- **Query**：`current`, `pageSize`
- **Response**：`WebResponse<Page<MessageVO>>`

### 7.4 关闭会话

- **PUT** `/api/agent/conversation/{id}/close`
- **Response**：`WebResponse<Void>`

### 7.5 删除会话

- **DELETE** `/api/agent/conversation/{id}`
- **Response**：`WebResponse<Void>`

---

## 8. 聊天接口

### 8.1 非流式聊天

- **POST** `/api/agent/chat`
- **Body**：
  ```json
  {
    "agentId": 1,
    "conversationId": 100,
    "message": "你好"
  }
  ```
- **Response**：`WebResponse<MessageVO>`

### 8.2 流式聊天（SSE）

- **GET** `/api/agent/chat/stream`
- **Query**：
  ```
  agentId=1&conversationId=100&message=你好
  ```
- **Response**：`text/event-stream`

#### SSE 事件格式

```
event: message
data: {"chunk": "你好", "conversationId": 100, "messageId": 1000}

event: tool_call
data: {"toolName": "weather", "toolCallId": "call_123", "arguments": {"city": "北京"}}

event: error
data: {"code": 500, "message": "模型调用失败"}

event: done
data: {"conversationId": 100, "messageId": 1000, "totalTokens": 50}
```

#### 事件类型说明

| 事件 | 说明 | 数据字段 |
|------|------|----------|
| `message` | 模型生成的文本片段 | `chunk`, `conversationId`, `messageId` |
| `tool_call` | 模型请求调用工具 | `toolName`, `toolCallId`, `arguments` |
| `error` | 模型调用或工具执行错误 | `code`, `message` |
| `done` | 流式响应结束 | `conversationId`, `messageId`, `totalTokens` |

---

## 9. 运行审计接口

### 9.1 查询运行记录

- **GET** `/api/agent/run/list`
- **Query**：`current`, `pageSize`, `agentId`, `userId`, `status`, `startTime`, `endTime`
- **Response**：`WebResponse<Page<AgentRunVO>>`

### 9.2 查询运行详情

- **GET** `/api/agent/run/{id}`
- **Response**：`WebResponse<AgentRunVO>`

### 9.3 查询运行统计

- **GET** `/api/agent/run/statistics`
- **Query**：`agentId`, `startTime`, `endTime`
- **Response**：`WebResponse<AgentRunStatisticsVO>`

---

## 10. 工具调用日志接口

### 10.1 查询工具调用日志

- **GET** `/api/agent/tool-call-log/list`
- **Query**：`current`, `pageSize`, `runId`, `toolId`, `agentId`, `status`, `startTime`, `endTime`
- **Response**：`WebResponse<Page<AgentToolCallLogVO>>`

### 10.2 查询工具调用详情

- **GET** `/api/agent/tool-call-log/{id}`
- **Response**：`WebResponse<AgentToolCallLogVO>`

---

## 11. 权限路径草案

| 权限路径 | 说明 | 对应接口 |
|----------|------|----------|
| `agent:model-provider:view` | 查看模型供应商 | 列表、详情 |
| `agent:model-provider:manage` | 管理模型供应商 | 新增、编辑、删除、启用/禁用 |
| `agent:definition:view` | 查看 Agent | 列表、详情 |
| `agent:definition:manage` | 管理 Agent | 新增、编辑、删除、启用/禁用、复制 |
| `agent:tool:view` | 查看工具 | 列表、详情 |
| `agent:tool:manage` | 管理工具 | 新增、编辑、删除、测试 |
| `agent:conversation:view` | 查看会话 | 列表、详情、消息 |
| `agent:conversation:manage` | 管理会话 | 关闭、删除 |
| `agent:run:view` | 查看运行记录 | 列表、详情、统计 |
| `agent:tool-call-log:view` | 查看工具调用日志 | 列表、详情 |
| `agent:chat` | 发起聊天 | 非流式、流式 |

---

## 12. 错误码

| 错误码 | 说明 | 场景 |
|--------|------|------|
| 200 | 成功 | 正常返回 |
| 400 | 参数错误 | 请求参数校验失败 |
| 401 | 未授权 | Token 无效或过期 |
| 403 | 无权限 | 当前用户无操作权限 |
| 404 | 资源不存在 | Agent、会话、工具不存在 |
| 409 | 资源冲突 | 编码重复、绑定已存在 |
| 422 | 业务校验失败 | Agent 未启用、模型不可用 |
| 500 | 系统错误 | 模型调用失败、工具执行异常 |
| 503 | 服务不可用 | 模型供应商不可达 |

---

## 13. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V0.1 | 2026-06-22 | 初始草案，定义管理接口、聊天接口、SSE 事件、权限路径 |
