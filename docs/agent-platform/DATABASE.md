# Agent 平台数据库设计

> 版本：V0.9
> 状态：已与 `api/src/main/resources/sql/postgresql/001-schema.sql` 同步
> 更新日期：2026-07-15
> 范围：当前 Agent 平台的表结构、关联关系和索引说明。PostgreSQL 建表以 `001-schema.sql` 为准，初始化数据以 `002-data.sql` 为准。

---

## 1. 设计约定

- 所有 Agent 表使用 `agent_` 前缀，并继承公共字段：`id`、`created_at`、`updated_at`、`sort_num`、`deleted`、`state`。
- `deleted` 为逻辑删除标记；业务关联使用 ID 字段，不创建数据库级外键约束。
- 业务状态字段（如 `status`）与公共 `state` 字段并存：前者表示领域状态，后者沿用项目公共实体状态。
- 主键由应用生成，建表 SQL 中不使用自增。
- JSON 配置和模型原始载荷以 `TEXT` 保存；密钥和 MCP 认证令牌须加密后入库。

## 2. 表结构

### 2.1 `agent_model_provider`（模型供应商）

保存 OpenAI 兼容及其他模型供应商的连接配置。

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` | VARCHAR(64) | 供应商名称；与 `deleted` 组成唯一键 |
| `type` | VARCHAR(32) | `openai`、`azure`、`anthropic`、`local` 等 |
| `api_base_url` | VARCHAR(256) | API 基础地址 |
| `api_key` | VARCHAR(512) | AES 加密后的 API Key |
| `default_model` | VARCHAR(64) | 默认模型名称 |
| `status` | TINYINT | 0 禁用，1 启用 |
| `sort` / `remark` | INT / VARCHAR(512) | 排序和备注 |

索引：`uk_name(name, deleted)`、`idx_type(type)`、`idx_status(state)`。

### 2.2 `agent_definition`（Agent 定义）

保存 Agent 配置、模型参数和默认推理配置。

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` / `code` | VARCHAR(64) | 名称和编码；`code` 与 `deleted` 组成唯一键 |
| `description` / `system_prompt` | VARCHAR(512) / TEXT | 描述和系统提示词 |
| `model_provider_id` / `model` | VARCHAR(32) / VARCHAR(64) | 模型供应商和模型名称 |
| `temperature` / `max_tokens` | DECIMAL(3,2) / INT | 默认 0.70、2048 |
| `status` | TINYINT | 0 草稿，1 启用，2 禁用 |
| `max_tool_rounds` | INT | 最大工具调用轮次，默认 1 |
| `default_thinking` | TINYINT(1) | 是否默认启用深度思考，默认 0 |
| `default_reasoning_effort` | VARCHAR(16) | 默认推理力度：`low`、`medium`、`high` |
| `access_type` | VARCHAR(16) | `private` / `public`，V1.0 预留 |
| `sort` / `remark` | INT / VARCHAR(512) | 排序和备注 |

索引：`uk_code(code, deleted)`、`idx_name(name)`、`idx_model_provider_id(model_provider_id)`、`idx_status(state)`。

### 2.3 `agent_mcp_server`（MCP 服务）

保存可用 MCP 服务及其连接、认证配置。

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` / `code` | VARCHAR(64) | 服务名称和编码；`code` 与 `deleted` 组成唯一键 |
| `transport` | VARCHAR(32) | `http`、`sse`、`streamable_http`，默认 `http` |
| `base_url` | VARCHAR(512) | MCP endpoint |
| `request_headers` | TEXT | 请求头 JSON |
| `auth_type` | VARCHAR(32) | `none`、`bearer`、`api_key` |
| `auth_token` | VARCHAR(1024) | AES 加密后的认证令牌 |
| `command` / `args` | VARCHAR(512) / TEXT | STDIO 命令及参数 JSON，当前预留 |
| `timeout_ms` | INT | 超时毫秒数，默认 30000 |
| `status` / `remark` | TINYINT / VARCHAR(512) | 0 禁用、1 启用；备注 |

索引：`uk_code(code, deleted)`、`idx_name(name)`、`idx_status(status)`。

### 2.4 `agent_tool`（工具）

当前持久化工具均为 MCP 服务发现或配置的工具。内建工具由应用注册，不写入此表。

| 字段 | 类型 | 说明 |
|---|---|---|
| `name` / `code` | VARCHAR(64) | 工具名称和编码；`code` 与 `deleted` 组成唯一键 |
| `description` | VARCHAR(512) | 工具描述 |
| `tool_type` | VARCHAR(64) | 业务分类，如 `knowledge`、`ops`、`dev` |
| `mcp_server_id` | VARCHAR(32) | 所属 MCP 服务 ID，必填 |
| `mcp_tool_name` | VARCHAR(128) | MCP 协议中的工具名 |
| `mcp_input_schema` | TEXT | MCP `inputSchema` JSON |
| `timeout_ms` | INT | 超时毫秒数，默认 30000 |
| `status` / `remark` | TINYINT / VARCHAR(512) | 0 禁用、1 启用；备注 |

索引：`uk_code(code, deleted)`、`uk_server_tool(mcp_server_id, mcp_tool_name, deleted)`、`idx_name(name)`、`idx_tool_type(tool_type)`、`idx_mcp_server_id(mcp_server_id)`、`idx_status(status)`。

### 2.5 `agent_tool_binding`（Agent-工具绑定）

将 Agent 与 MCP 工具关联，并控制调用优先级和启用状态。

| 字段 | 类型 | 说明 |
|---|---|---|
| `agent_definition_id` / `tool_id` | VARCHAR(32) | Agent 和工具 ID |
| `priority` | INT | 调用优先级，默认 0 |
| `status` | TINYINT | 0 禁用，1 启用 |

索引：`uk_agent_tool(agent_definition_id, tool_id, deleted)`、`idx_agent_id(agent_definition_id)`、`idx_tool_id(tool_id)`。

### 2.6 `agent_conversation`（会话）

| 字段 | 类型 | 说明 |
|---|---|---|
| `user_id` / `agent_definition_id` | VARCHAR(32) | 所属用户和 Agent |
| `title` | VARCHAR(256) | 会话标题 |
| `message_count` | INT | 消息数，默认 0 |
| `status` | TINYINT | 0 进行中，1 关闭，2 归档 |

索引：`idx_user_agent(user_id, agent_definition_id)`、`idx_user_id(user_id)`、`idx_agent_id(agent_definition_id)`、`idx_status(state)`。

### 2.7 `agent_message`（消息）

除普通聊天、模型推理和工具消息外，也保存交互式提问及用户回复的状态。

| 字段 | 类型 | 说明 |
|---|---|---|
| `conversation_id` / `role` | VARCHAR(32) / VARCHAR(16) | 会话 ID；角色为 `user`、`assistant`、`tool` |
| `message_type` | VARCHAR(32) | `chat`、`interaction`、`answer`，默认 `chat` |
| `interaction_type` / `interaction_status` | VARCHAR(32) | 交互类型（当前为 `group`）；`pending`、`answered`、`cancelled`、`expired` |
| `question_config` | TEXT | 后端校验后的提问配置 JSON |
| `parent_message_id` | VARCHAR(64) | 用户回复关联的提问消息 ID |
| `answered_at` / `expires_at` | BIGINT | 回复和过期时间 |
| `content` / `reasoning_content` | LONGTEXT | 消息内容和 assistant 推理内容 |
| `tool_calls` | TEXT | assistant 的工具调用请求 JSON |
| `tool_call_id` / `tool_result` | VARCHAR(64) / TEXT | tool 消息关联的调用 ID 与执行结果 |
| `model` | VARCHAR(64) | assistant 使用的模型 |
| `prompt_tokens` / `completion_tokens` / `total_tokens` / `reasoning_tokens` | INT | Token 统计 |
| `latency_ms` | INT | 响应延迟（毫秒） |
| `edited` / `original_content` / `edited_at` | TINYINT / LONGTEXT / BIGINT | 消息编辑预留字段 |

索引：`idx_conversation_id(conversation_id)`、`idx_role(role)`、`idx_parent_message_id(parent_message_id)`、`idx_interaction_status(conversation_id, interaction_status, deleted)`、`idx_create_time(created_at)`。

### 2.8 `agent_run`（运行记录）

记录每次 Agent 调用的输入输出摘要、模型用量和结果。

| 字段 | 类型 | 说明 |
|---|---|---|
| `agent_definition_id` / `user_id` | VARCHAR(32) | Agent 和用户 ID |
| `conversation_id` / `message_id` | VARCHAR(32) | 关联会话和输出消息 ID |
| `input_content` / `output_content` | TEXT | 输入、输出摘要 |
| `model` / `model_provider_id` | VARCHAR(64) / VARCHAR(32) | 实际使用模型和供应商 |
| `prompt_tokens` / `completion_tokens` / `total_tokens` | INT | Token 统计 |
| `latency_ms` | INT | 总耗时（毫秒） |
| `status` / `error_msg` | TINYINT / VARCHAR(1024) | 0 成功，1 失败，2 超时；错误信息 |

索引：`idx_agent_id(agent_definition_id)`、`idx_user_id(user_id)`、`idx_conversation_id(conversation_id)`、`idx_status(state)`、`idx_create_time(created_at)`。

### 2.9 `agent_tool_call_log`（工具调用日志）

记录模型发起的 MCP 或内建工具调用，供审计、统计和问题定位使用。

| 字段 | 类型 | 说明 |
|---|---|---|
| `run_id` | VARCHAR(32) | 关联 `agent_run`，必填 |
| `tool_id` | VARCHAR(32) | 关联持久化工具，可为空（内建工具场景） |
| `tool_call_id` / `tool_name` | VARCHAR(128) | 模型返回的调用 ID 和工具名称 |
| `arguments` | TEXT | 模型传入的原始参数 JSON |
| `agent_definition_id` | VARCHAR(32) | 关联 Agent ID |
| `request_url` / `request_method` | VARCHAR(512) / VARCHAR(16) | 实际请求地址和方法 |
| `request_headers` / `request_body` | TEXT | 实际请求头和请求体 |
| `response_status` / `response_body` | INT / TEXT | 响应状态码和响应体（最多 64KB） |
| `latency_ms` | INT | 执行耗时（毫秒） |
| `status` / `error_msg` | TINYINT / VARCHAR(1024) | 0 成功，1 失败，2 超时，3 安全拦截；错误信息 |

索引：`idx_run_id(run_id)`、`idx_tool_id(tool_id)`、`idx_agent_id(agent_definition_id)`、`idx_status(state)`、`idx_create_time(created_at)`、`idx_tool_call_log_run_call(run_id, tool_call_id)`。

### 2.10 预留表

`agent_workflow`、`knowledge_base`、`agent_knowledge_base_binding`、`knowledge_document` 已建表，字段和索引以建表脚本为准：

- `agent_workflow`：Agent 工作流的节点和边 JSON；
- `knowledge_base`：知识库主表，支持平台级 `PLATFORM` 和 Agent 专属 `AGENT` 两种范围；
- `agent_knowledge_base_binding`：Agent 与知识库绑定关系，决定某个 Agent 聊天时实际检索哪些知识库；
- `knowledge_document`：知识库文档、来源和分块数量。

代码层同步按领域拆分：`com.aether.knowledge` 负责知识库本体、文档、分块、Embedding 和检索；`com.aether.agent` 仅保留 Agent 与知识库绑定及聊天注入调用。

### 2.11 知识库向量检索选型

应用默认配置和 PostgreSQL 专用初始化脚本现以 PostgreSQL 16 为目标；生产主库在完成一次切换前仍保留 MySQL 只读回滚路径。知识库向量检索统一采用 `pgvector`：

- 通过 `CREATE EXTENSION vector` 启用扩展；
- 新增 `knowledge_document_chunk`，保存文档分块、分块序号、文本、Token 数和固定 `embedding vector(1536)`；
- 为 `embedding` 建立与选定距离度量一致的 HNSW 索引；
- 将知识库、文档、租户/用户和逻辑删除等过滤条件保留为常规列和 B-tree 索引；
- 使用 PostgreSQL 事务保证文档元数据、分块和向量写入的一致性。

当前已提供文档分块、Embedding 写入、pgvector Top-K 检索和 Agent 聊天上下文注入；文件上传、异步索引队列、ACL 和版本管理后续实现。

## 3. 关系概览

```text
agent_model_provider 1 ── N agent_definition
agent_definition     1 ── N agent_conversation ── N agent_message
agent_definition     1 ── N agent_run ── N agent_tool_call_log
agent_definition     1 ── N agent_tool_binding N ── 1 agent_tool
agent_mcp_server     1 ── N agent_tool
agent_definition     1 ── N agent_workflow                  [预留]
agent_definition     1 ── N agent_knowledge_base_binding N ── 1 knowledge_base
knowledge_base 1 ── N knowledge_document ── N knowledge_document_chunk [pgvector]
```

## 4. 数据保留与安全

- 默认查询必须遵循逻辑删除规则，排除 `deleted = 1` 的记录。
- `agent_run` 和 `agent_tool_call_log` 是审计数据，不应物理删除；如需隐藏，使用逻辑删除并保留可追溯性。
- `api_key`、`auth_token` 和可能包含敏感信息的请求头、请求体，应在写入前脱敏或加密；查询展示时不得直接回传密文或明文凭据。
- MCP 服务和工具没有数据库外键，删除或禁用服务前应检查关联工具及 Agent 绑定。

## 5. 变更记录

| 版本 | 日期 | 说明 |
|---|---|---|
| V0.1 | 2026-06-22 | 初始数据库设计草案 |
| V0.6 | 2026-07-07 | 消息增加推理内容和推理 Token 字段 |
| V0.8 | 2026-07-15 | 同步交互式提问、内建工具审计字段、MCP 服务与 MCP 工具结构 |
| V0.9 | 2026-07-15 | 同步工具业务分类、MCP 工具唯一约束及当前统一建表脚本 |
| V1.0 | 2026-07-15 | 确认知识库向量检索：主库迁移 PostgreSQL 后采用 pgvector |
| V1.1 | 2026-07-15 | 新增 PostgreSQL 16 迁移脚本、pgvector 1536 维分块表和迁移运行手册 |
