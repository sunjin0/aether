# Agent 平台 — 架构设计

> 合并来源：API.md、DATABASE.md
> 更新日期：2026-08-11

---

## 一、API 接口设计

### 1.1 设计原则

- **统一前缀**：所有 Agent 平台接口使用 `/api/agent/**` 前缀
- **统一响应**：管理接口返回 `WebResponse<T>`（复用现有 `com.aether.entity.WebResponse`）
- **RESTful 风格**：资源路径使用名词，操作通过 HTTP 方法区分
- **权限控制**：基于 `@Permission` 注解，路径与现有权限体系兼容

### 1.2 通用响应格式

#### 管理接口响应

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "total": 0
}
```

#### 分页响应

```json
{
  "code": 200,
  "message": "success",
  "data": [ ... ],
  "total": 100
}
```

`WebResponse.Page` 将记录数组放在 `data`，总数放在根级 `total`；前端不应读取 `data.records`、`data.pages` 等不存在字段。

### 1.3 模型供应商管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表查询 | POST | `/api/agent/model-provider/list` |
| 下拉选项 | GET | `/api/agent/model-provider/options` |
| Embedding 供应商 | GET | `/api/agent/model-provider/embedding-options` |
| 详情 | GET | `/api/agent/model-provider/{id}` |
| 新增 | POST | `/api/agent/model-provider` |
| 编辑 | PUT | `/api/agent/model-provider/{id}` |
| 删除 | DELETE | `/api/agent/model-provider/{id}` |
| 启用/禁用 | PUT | `/api/agent/model-provider/{id}/status` |
| 测试连接 | POST | `/api/agent/model-provider/{id}/test` |

V45 起，供应商页面同时管理模型目录。供应商只保存连接信息，模型目录保存模型名称、能力、上下文窗口、状态和端点覆盖；Agent、知识库、查询重写、Rerank、AI 审查和 Skill 路由均通过目录项选择模型。目录写入与运行解析会校验能力、目录状态及供应商状态。

| 功能 | 方法 | 路径 |
|------|------|------|
| 查询目录 | GET | `/api/agent/model-provider/models?providerId=...` |
| 按能力获取选项 | GET | `/api/agent/model-provider/models/options?capability=...` |
| 拉取模型候选 | GET | `/api/agent/model-provider/{id}/models/discover` |
| 事务批量保存目录 | POST | `/api/agent/model-provider/models/batch` |

### 1.4 Agent 定义管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表查询 | POST | `/api/agent/definition/list` |
| 下拉选项 | GET | `/api/agent/definition/options` |
| 详情 | GET | `/api/agent/definition/{id}` |
| 新增 | POST | `/api/agent/definition` |
| 编辑 | PUT | `/api/agent/definition/{id}` |
| 删除 | DELETE | `/api/agent/definition/{id}` |
| 启用/禁用 | PUT | `/api/agent/definition/{id}/status` |
| 复制 | POST | `/api/agent/definition/{id}/copy` |

### 1.5 工具管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表查询 | POST | `/api/agent/tool/list` |
| 筛选聚合 | GET | `/api/agent/tool/facets` |
| 统计 | GET | `/api/agent/tool/statistics` |
| 详情 | GET | `/api/agent/tool/{id}` |
| 新增 | POST | `/api/agent/tool` |
| 编辑 | PUT | `/api/agent/tool/{id}` |
| 删除 | DELETE | `/api/agent/tool/{id}` |
| 测试 | POST | `/api/agent/tool/{id}/test` |

### 1.6 工具绑定管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 查询 Agent 的工具绑定 | GET | `/api/agent/definition/{agentId}/tools` |
| 绑定工具 | POST | `/api/agent/definition/{agentId}/tools` |
| 解绑工具 | DELETE | `/api/agent/definition/{agentId}/tools/{toolId}` |
| 调整优先级 | PUT | `/api/agent/definition/{agentId}/tools/{toolId}/priority` |

### 1.7 会话管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 会话列表 | POST | `/api/agent/conversation/list` |
| 会话详情 | GET | `/api/agent/conversation/{id}` |
| 会话消息 | GET | `/api/agent/conversation/{id}/messages` |
| 会话生命周期 | GET | `/api/agent/conversation/{id}/lifecycle` |
| 会话统计 | GET | `/api/agent/conversation/{id}/statistics` |
| 关闭会话 | PUT | `/api/agent/conversation/{id}/close` |
| 删除会话 | DELETE | `/api/agent/conversation/{id}` |

### 1.8 聊天接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 非流式聊天（已弃用） | POST | `/api/agent/chat` |
| 流式聊天 (SSE) | POST | `/api/agent/chat/stream` |
| 聊天附件识别 | POST | `/api/agent/chat/attachment` |

**SSE 事件格式：**

| 事件 | data 字段 |
|------|-----------|
| `message` | chunk, conversationId, messageId |
| `reasoning` | chunk, conversationId |
| `tool_call` | toolName, toolCallId, arguments |
| `question` | messageId, messageType, interactionType, questionConfig |
| `error` | code, message |
| `done` | conversationId, messageId, totalTokens, reasoningTokens |

Deep Agent 流式额外事件：`accepted`、`run_step`；Deep 运行重放：`GET /api/agent/deep-runs/{runId}/stream`。
**SSE 心跳**：每 15 秒发送 `:heartbeat` comment

### 1.9 运行审计

| 功能 | 方法 | 路径 |
|------|------|------|
| 运行记录列表 | POST | `/api/agent/run/list` |
| 运行详情 | GET | `/api/agent/run/{id}` |
| 运行统计 | GET | `/api/agent/run/statistics` |
| 运行步骤 | GET | `/api/agent/run/{id}/steps` |
| 取消 Deep 运行 | POST | `/api/agent/run/{id}/cancel` |

### 1.10 工具调用日志

| 功能 | 方法 | 路径 |
|------|------|------|
| 工具调用日志列表 | POST | `/api/agent/tool-call-log/list` |
| 工具调用详情 | GET | `/api/agent/tool-call-log/{id}` |

### 1.11 权限路径

当前权限采用**路径型** `@Permission(path, type)`，与 `sys_resource` 资源树一致：

| 权限路径 | 说明 |
|----------|------|
| `/sys/admin` `/sys/service-account` `/sys/role` `/sys/resource` `/sys/dict` `/sys/config` `/sys/preference` | 系统管理 |
| `/agent/definition` `/agent/tool` `/agent/run` `/agent/chat` `/agent/conversation` `/agent/tool-call-log` `/agent/mcp-server` `/agent/model-provider` | Agent 平台 |
| `/knowledge/base` `/knowledge/document` `/knowledge/evaluation` | 知识库 |
| `/dashboard` | 工作台 |
| `/workflow/workflow` `/workflow/run` `/workflow/operations` `/workflow/schedule` | 工作流 |

Read 要求权限映射中存在该 path；Write 要求映射值为 `true`。完整端点见 `docs/agent-platform/00-项目文档/05-API参考/README.md`。

### 1.12 错误码

| 编码 | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 参数错误 |
| 401 | 未授权 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 409 | 资源冲突 |
| 422 | 业务校验失败 |
| 500 | 系统错误 |
| 503 | 服务不可用 |

---

## 二、数据库设计

### 2.1 设计约定

- 所有 Agent 表使用 `agent_` 前缀
- 继承公共字段：`id`、`created_at`、`updated_at`、`sort_num`、`deleted`、`state`
- `deleted` 为逻辑删除标记
- 主键由应用生成，不使用自增
- JSON 配置和模型原始载荷以 `TEXT` 保存
- 密钥和 MCP 认证令牌须加密后入库

### 2.2 表结构

| 表名 | 核心字段 |
|------|----------|
| `agent_model_provider` | name, type, api_base_url, api_key（AES 加密）, default_model, status |
| `agent_definition` | name, code, description, system_prompt, model_provider_id, model, temperature(0.70), max_tokens(2048), max_tool_rounds(1), default_thinking(0), default_reasoning_effort, access_type |
| `agent_mcp_server` | name, code, transport(http/sse/streamable_http), base_url, request_headers, auth_type, auth_token（AES 加密）, command, args, timeout_ms(30000), status |
| `agent_tool` | name, code, description, tool_type, mcp_server_id, mcp_tool_name, mcp_input_schema, timeout_ms, status |
| `agent_tool_binding` | agent_definition_id, tool_id, priority, status |
| `agent_conversation` | user_id, agent_definition_id, title, message_count, status |
| `agent_message` | conversation_id, role(user/assistant/tool), message_type(chat/interaction/answer), interaction_type, interaction_status, question_config, parent_message_id, content, reasoning_content, tool_calls, tool_call_id, tool_result, model, prompt_tokens, completion_tokens, total_tokens, reasoning_tokens, latency_ms |
| `agent_run` | agent_definition_id, user_id, conversation_id, message_id, input_content, output_content, model, model_provider_id, token统计, latency_ms, status |
| `agent_tool_call_log` | run_id, tool_id, tool_call_id, tool_name, arguments, agent_definition_id, request_url, request_method, request_headers, request_body, response_status, response_body, latency_ms, status |

### 2.3 预留表

| 表名 | 用途 |
|------|------|
| `agent_workflow` | 工作流定义（预留） |
| `knowledge_base` | 知识库（预留） |
| `agent_knowledge_base_binding` | Agent 知识库绑定（预留） |
| `knowledge_document` | 知识文档（预留） |

### 2.4 知识库向量检索

- **数据库**：PostgreSQL 16 + pgvector
- **扩展**：`CREATE EXTENSION vector`
- **分块表**：`knowledge_document_chunk`
- **向量字段**：`embedding vector(1536)`
- **索引**：HNSW 索引
- **嵌入模型**：`text-embedding-3-small`

> 注：上表为 V0.x 基线结构。当前完整表结构（含服务账号、Deep Agent 运行步骤、工作流运行时、检索评测、AI 审查、偏好等）以 Flyway 迁移 `V1~V37` 为准，详见 `docs/agent-platform/00-项目文档/04-数据库设计/README.md`。
