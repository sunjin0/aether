# Agent 平台数据库设计方向

> 版本：V0.1 文档基线
> 状态：草案（待评审确认）
> 范围：数据库表结构设计方向，不创建 SQL 脚本

---

## 1. 设计原则

- **复用公共字段**：所有实体表继承 `BaseEntity` 的公共字段（`id`, `created_at`, `updated_at`, `sort_num`, `deleted`, `state` 等）
- **表名前缀统一**：所有 Agent 平台表使用 `agent_` 前缀
- **软删除**：所有实体表支持逻辑删除（`deleted` 字段），不物理删除数据
- **索引策略**：高频查询字段建立索引，避免过多索引影响写入性能
- **字段命名**：下划线命名，与现有项目风格一致
- **关联关系**：使用外键字段关联，不强制数据库级外键约束（便于分布式扩展）

---

## 2. 表结构规划

### 2.1 agent_model_provider（模型供应商）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `name` | VARCHAR(64) | 供应商名称（如 OpenAI、Azure） |
| `type` | VARCHAR(32) | 供应商类型：`openai`、`azure`、`anthropic`、`local` |
| `api_base_url` | VARCHAR(256) | API 基础地址 |
| `api_key` | VARCHAR(512) | API Key（AES 加密存储） |
| `default_model` | VARCHAR(64) | 默认模型名称（如 gpt-4） |
| `status` | TINYINT | 状态：0-禁用，1-启用 |
| `sort` | INT | 排序号 |
| `remark` | VARCHAR(512) | 备注 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_name`：`name`（未删除）
- `idx_type`：`type`
- `idx_status`：`state`

---

### 2.2 agent_definition（Agent 定义）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `name` | VARCHAR(64) | Agent 名称 |
| `code` | VARCHAR(64) | Agent 编码（唯一，用于 API 调用） |
| `description` | VARCHAR(512) | 描述 |
| `system_prompt` | TEXT | 系统提示词 |
| `model_provider_id` | BIGINT | 关联模型供应商 ID |
| `model` | VARCHAR(64) | 使用的模型名称 |
| `temperature` | DECIMAL(3,2) | 温度参数，默认 0.7 |
| `max_tokens` | INT | 最大 token 数，默认 2048 |
| `status` | TINYINT | 状态：0-草稿，1-启用，2-禁用 |
| `sort` | INT | 排序号 |
| `remark` | VARCHAR(512) | 备注 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_code`：`code`（未删除）
- `idx_name`：`name`
- `idx_model_provider_id`：`model_provider_id`
- `idx_status`：`state`

---

### 2.3 agent_tool（工具）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `name` | VARCHAR(64) | 工具名称 |
| `code` | VARCHAR(64) | 工具编码（唯一） |
| `description` | VARCHAR(512) | 描述 |
| `type` | VARCHAR(32) | 工具类型：`http` |
| `http_method` | VARCHAR(16) | HTTP 方法：`GET`、`POST` |
| `http_url` | VARCHAR(512) | HTTP 请求地址 |
| `http_headers` | TEXT | 请求头模板（JSON 格式） |
| `http_body_template` | TEXT | 请求体模板（支持占位符） |
| `response_extract_rule` | VARCHAR(512) | 响应提取规则（JSONPath 或正则） |
| `timeout_ms` | INT | 超时时间（毫秒），默认 30000 |
| `status` | TINYINT | 状态：0-禁用，1-启用 |
| `sort` | INT | 排序号 |
| `remark` | VARCHAR(512) | 备注 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_code`：`code`（未删除）
- `idx_name`：`name`
- `idx_type`：`type`
- `idx_status`：`state`

---

### 2.4 agent_tool_binding（工具绑定）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `tool_id` | BIGINT | 关联工具 ID |
| `priority` | INT | 调用优先级，默认 0 |
| `status` | TINYINT | 状态：0-禁用，1-启用 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_agent_tool`：`agent_definition_id`, `tool_id`（未删除）
- `idx_agent_id`：`agent_definition_id`
- `idx_tool_id`：`tool_id`

---

### 2.5 agent_conversation（会话）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `user_id` | BIGINT | 用户 ID |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `title` | VARCHAR(256) | 会话标题（可由首条消息生成） |
| `message_count` | INT | 消息数，默认 0 |
| `status` | TINYINT | 状态：0-进行中，1-关闭，2-归档 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_user_agent`：`user_id`, `agent_definition_id`
- `idx_user_id`：`user_id`
- `idx_agent_id`：`agent_definition_id`
- `idx_status`：`state`

---

### 2.6 agent_message（消息）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `conversation_id` | BIGINT | 关联会话 ID |
| `role` | VARCHAR(16) | 角色：`user`、`assistant`、`tool` |
| `content` | LONGTEXT | 消息内容 |
| `tool_calls` | TEXT | 工具调用请求（JSON 格式，assistant 角色时） |
| `tool_call_id` | VARCHAR(64) | 工具调用 ID（tool 角色时） |
| `tool_result` | TEXT | 工具调用结果（tool 角色时） |
| `model` | VARCHAR(64) | 使用的模型（assistant 角色时） |
| `prompt_tokens` | INT | 输入 token 数 |
| `completion_tokens` | INT | 输出 token 数 |
| `total_tokens` | INT | 总 token 数 |
| `latency_ms` | INT | 响应延迟（毫秒） |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_conversation_id`：`conversation_id`
- `idx_role`：`role`
- `idx_create_time`：`created_at`

---

### 2.7 agent_run（运行记录）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `user_id` | BIGINT | 用户 ID |
| `conversation_id` | BIGINT | 关联会话 ID |
| `message_id` | BIGINT | 关联输出消息 ID |
| `input_content` | TEXT | 输入内容摘要 |
| `output_content` | TEXT | 输出内容摘要 |
| `model` | VARCHAR(64) | 使用的模型 |
| `model_provider_id` | BIGINT | 使用的模型供应商 ID |
| `prompt_tokens` | INT | 输入 token 数 |
| `completion_tokens` | INT | 输出 token 数 |
| `total_tokens` | INT | 总 token 数 |
| `latency_ms` | INT | 总耗时（毫秒） |
| `status` | TINYINT | 状态：0-成功，1-失败，2-超时 |
| `error_msg` | VARCHAR(1024) | 错误信息 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_agent_id`：`agent_definition_id`
- `idx_user_id`：`user_id`
- `idx_conversation_id`：`conversation_id`
- `idx_status`：`state`
- `idx_create_time`：`created_at`

---

### 2.8 agent_tool_call_log（工具调用日志）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `run_id` | BIGINT | 关联运行记录 ID |
| `tool_id` | BIGINT | 关联工具 ID |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `request_url` | VARCHAR(512) | 实际请求 URL |
| `request_method` | VARCHAR(16) | 实际请求方法 |
| `request_headers` | TEXT | 实际请求头（JSON） |
| `request_body` | TEXT | 实际请求体 |
| `response_status` | INT | HTTP 响应状态码 |
| `response_body` | TEXT | 响应体（截断存储，最大 64KB） |
| `latency_ms` | INT | 执行耗时（毫秒） |
| `status` | TINYINT | 状态：0-成功，1-失败，2-超时，3-安全拦截 |
| `error_msg` | VARCHAR(1024) | 错误信息 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_run_id`：`run_id`
- `idx_tool_id`：`tool_id`
- `idx_agent_id`：`agent_definition_id`
- `idx_status`：`state`
- `idx_create_time`：`created_at`

---

### 2.9 agent_workflow（工作流 — V0.7 预留）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `name` | VARCHAR(64) | 工作流名称 |
| `description` | VARCHAR(512) | 描述 |
| `nodes` | LONGTEXT | 节点定义（JSON 格式，预留） |
| `edges` | LONGTEXT | 边定义（JSON 格式，预留） |
| `status` | TINYINT | 状态：0-草稿，1-启用，2-禁用 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_agent_id`：`agent_definition_id`
- `idx_status`：`state`

---

### 2.10 agent_knowledge_base（知识库 — V0.7 预留）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `agent_definition_id` | BIGINT | 关联 Agent 定义 ID |
| `name` | VARCHAR(64) | 知识库名称 |
| `description` | VARCHAR(512) | 描述 |
| `index_status` | TINYINT | 索引状态：0-未索引，1-索引中，2-已索引 |
| `status` | TINYINT | 状态：0-禁用，1-启用 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_agent_id`：`agent_definition_id`
- `idx_status`：`state`

---

### 2.11 agent_document（文档 — V0.7 预留）

| 字段 | 类型 | 说明 |
|------|------|------|
| `id` | BIGINT | 主键，自增 |
| `knowledge_base_id` | BIGINT | 关联知识库 ID |
| `title` | VARCHAR(256) | 文档标题 |
| `content` | LONGTEXT | 文档内容（纯文本或 Markdown） |
| `source_url` | VARCHAR(512) | 来源 URL（可选） |
| `chunk_count` | INT | 分块数（预留） |
| `status` | TINYINT | 状态：0-未处理，1-处理中，2-已完成 |
| *BaseEntity 字段* | - | `created_at`, `updated_at`, `sort_num`, `deleted`, `state` |

**索引**：
- `idx_knowledge_base_id`：`knowledge_base_id`
- `idx_status`：`state`

---

## 3. 关系图

```
agent_model_provider (1)
    │
    ▼
agent_definition (N) ─── agent_tool_binding (N) ─── agent_tool (N)
    │
    ├── agent_conversation (N)
    │       │
    │       ▼
    │   agent_message (N)
    │
    ├── agent_run (N)
    │       │
    │       ▼
    │   agent_tool_call_log (N)
    │
    ├── agent_workflow (N) [预留]
    │
    └── agent_knowledge_base (N) [预留]
            │
            ▼
        agent_document (N) [预留]
```

---

## 4. 软删除规则

- 所有实体表均继承 `BaseEntity` 的 `deleted` 字段（逻辑删除，布尔值）和 `state` 字段（状态）
- 软删除后，数据保留但查询默认过滤（`deleted = false`）
- 关联查询时，需确保关联表也过滤已删除记录（`deleted = false`）
- 运行记录和工具调用日志原则上不删除（审计需要），但支持软删除标记

---

## 5. 索引建议

| 表 | 索引名 | 字段 | 类型 | 说明 |
|----|--------|------|------|------|
| agent_model_provider | uk_name | `name`, `deleted` | 唯一 | 供应商名称唯一（未删除） |
| agent_definition | uk_code | `code`, `deleted` | 唯一 | Agent 编码唯一（未删除） |
| agent_tool | uk_code | `code`, `deleted` | 唯一 | 工具编码唯一（未删除） |
| agent_tool_binding | uk_agent_tool | `agent_definition_id`, `tool_id`, `deleted` | 唯一 | 同一 Agent 同一工具只能绑定一次（未删除） |
| agent_conversation | idx_user_agent | `user_id`, `agent_definition_id` | 普通 | 用户查询自己的会话 |
| agent_message | idx_conversation | `conversation_id`, `created_at` | 普通 | 会话消息按时间查询 |
| agent_run | idx_agent_time | `agent_definition_id`, `created_at` | 普通 | 按 Agent 和时间查询运行记录 |
| agent_tool_call_log | idx_run_tool | `run_id`, `tool_id` | 普通 | 按运行和工具查询日志 |

---

## 6. 变更记录

| 版本 | 日期 | 说明 |
|------|------|------|
| V0.1 | 2026-06-22 | 初始草案，定义 11 张表的结构方向 |
