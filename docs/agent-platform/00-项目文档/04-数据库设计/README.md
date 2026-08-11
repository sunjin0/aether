# Aether 数据库设计

> 数据源：`api/src/main/resources/db/migration/postgresql/V1__init.sql` ~ `V38`（Flyway）
> 数据库：PostgreSQL 16 + pgvector；更新日期：2026-08-07

---

## 1. 设计约定

- 所有表主键为应用生成的 `VARCHAR(32)` 雪花 ID，不使用自增。
- 业务表通过 `common` 模块的 `BaseEntity` 继承公共字段：
  `id, created_at, updated_at, sort_num, deleted, state`。
- `deleted`：逻辑删除标记（`@TableLogic`，`false` 未删 / `true` 已删）。
- `state`：通用状态位；`sort_num`：排序。
- 时间戳使用 `BIGINT` 毫秒值。
- JSON / 模型原始载荷使用 `TEXT` 保存。
- 密钥、认证令牌、API Key 一律加密（AES）后入库。
- 向量字段：`embedding vector(1536)`，使用 HNSW 余弦索引。
- 业务关联主要通过 ID 字段和应用层校验维护，迁移脚本未为多数跨域关联建立数据库外键；删除、权限与引用完整性由服务层负责。
- 工作流终态数据按保留期进行软删除：实例清理时同步处理节点审计、回调投递与执行任务；默认保留 90 天，`0` 可关闭自动清理。

---

## 2. 表清单总览

| 域 | 表 | 说明 |
| --- | --- | --- |
| 系统/权限 | `sys_user` `sys_role` `sys_user_role` `sys_resource` `sys_role_resource` `sys_token` `sys_config` `sys_dict` | RBAC、资源与字典 |
| 服务账号 | `sys_service_account` | 非交互服务账号（V16/V17） |
| 偏好 | `sys_admin_preference` `sys_admin_preference_event` | 管理员长期记忆 |
| 成员 | `user_member` | 前端成员 |
| 消息 | `msg_email` `msg_sms` | 邮件/短信记录 |
| Agent 平台 | `agent_model_provider` `agent_definition` `agent_mcp_server` `agent_tool` `agent_tool_binding` `agent_conversation` `agent_message` `agent_run` `agent_run_step` `agent_tool_call_log` `agent_skill` `agent_skill_version` `agent_skill_resource` `agent_skill_tool_binding` `agent_skill_knowledge_binding` `agent_definition_skill_binding` | 模型、Agent、工具、会话、运行、技能（V38） |
| 知识库 | `knowledge_base` `agent_knowledge_base_binding` `knowledge_document` `knowledge_document_version` `knowledge_document_chunk` `knowledge_index_job` `knowledge_review_task` `knowledge_review_action_log` `knowledge_ai_review` `knowledge_ai_review_issue` `knowledge_reference_log` `knowledge_retrieval_log` `knowledge_retrieval_evaluation_*` | RAG 与审核评测 |
| 工作流 | `agent_workflow` `agent_workflow_version` `agent_workflow_instance` `agent_workflow_node_instance` `agent_workflow_execution_job` `agent_workflow_callback_delivery` `agent_workflow_webhook_trigger` `agent_workflow_schedule_trigger` `agent_workflow_template` | 工作流运行时 |

---

## 3. 系统 / 权限（RBAC）

### 3.1 `sys_user` 管理员
| 字段 | 类型 | 说明 |
| --- | --- | --- |
| username | varchar(255) | 账号 |
| sex | varchar(255) | 性别 |
| type | varchar(255) | 类型（`System_Role_User` / `System_Role_Service` / 租户） |
| email | varchar(255) | 邮箱 |
| phone | varchar(255) | 手机号 |
| avatar | varchar(255) | 头像 |
| password | varchar(255) | BCrypt 哈希 |

### 3.2 `sys_role` 角色
`name`、`description`。种子数据含超级管理员角色 `root`（id=1）。

### 3.3 `sys_user_role` / `sys_role_resource`
多对多关联：`user_id → role_id`；`role_id → resource_id`。

### 3.4 `sys_resource` 资源（菜单路由 + 权限叶子）
| 字段 | 说明 |
| --- | --- |
| name / name_cn | 名称（中/英） |
| path | 路由或权限路径 |
| type | `Resource_Type_Route` / `Resource_Type_Permission` |
| parent_id | 父节点 |
| leaf | 是否叶子 |
| icon / description | 图标与描述 |

权限资源形如 `perm_*_read` / `perm_*_write`，其 `path` 为 `/sys/admin` 等，用于 `@Permission` 鉴权与前端路由树。

### 3.5 `sys_token` / `sys_config` / `sys_dict`
- `sys_token`：登录令牌（`token`、`refresh_token`）。
- `sys_config`：key/value 配置树（`code, parent, name, value, remark`）。
- `sys_dict`：字典树（`code, parent, name, name_cn, val, remark`），用于下拉选项与 i18n 标签（如消息模板、性别、系统角色）。

---

## 4. 服务账号（V16/V17）

### `sys_service_account`
| 字段 | 说明 |
| --- | --- |
| user_id | 关联底层 `sys_user`（类型 `System_Role_Service`） |
| client_id | 客户端 ID（唯一） |
| secret_hash | 密钥 BCrypt 哈希，明文仅在创建/轮换返回一次 |
| token_version | 令牌版本，轮换/禁用/删除时递增使旧令牌失效 |
| enabled | 是否启用 |
| allowed_workflow_ids | 允许启动的工作流 ID 白名单 JSON `'[]'` |
| max_starts_per_hour | 每小时启动额度，`0` 表示不限制 |
| last_used_at | 最后使用时间 |

---

## 5. 管理员偏好（长期记忆）

### `sys_admin_preference`
| 字段 | 说明 |
| --- | --- |
| admin_id | 管理员 |
| category / key_name | 类别与键 |
| value / description | 取值与描述 |
| priority | 优先级，默认 50 |
| scope / scope_detail | 作用域 |
| source | 来源（`explicit` / `extract` 等） |
| confidence | 置信度 DECIMAL(4,2) |
| usage_count / last_used_at | 使用统计 |
| expires_at / decay_rate | 过期与衰减 |
| effective_score | 有效分，用于排序 |

唯一约束：`(admin_id, category, key_name, scope, scope_detail)` 且未删除。

### `sys_admin_preference_event`
偏好证据事件日志：`admin_id, preference_id, event_type(extract/confirm/reject/override/use), category, key_name, value, confidence, conversation_id, message_id, context_snapshot`。

---

## 6. 成员 / 消息

### `user_member` 前端成员
`username, password, nickname, phone, email`。

### `msg_email` / `msg_sms`
发送记录：`user_id, {email|phone}, type, code, subject, body`。

---

## 7. Agent 平台

### 7.1 `agent_model_provider` 模型供应商
`name, type(openai/azure/anthropic/local), api_base_url, api_key(AES), default_model, context_window(默认32768), status`。`uk_name` 唯一。

自 V45 起，该表只维护连接配置；`default_model` 是历史字段，业务运行不再以它作为模型选择依据。

### 7.2 `agent_model_catalog` 供应商模型目录（V45）

`provider_id, name, capabilities, context_window, endpoint_override, status, sort_num, remark`。未删除记录以 `(provider_id, name)` 唯一。

- `capabilities` 为 `CHAT`、`VIDEO`、`AUDIO`、`MULTIMODAL`、`EMBEDDING`、`RERANK` 的逗号分隔集合。
- 模型目录是运行时选择模型的唯一来源；仅目录和供应商同时启用、且能力匹配时可以使用。
- `endpoint_override` 可覆盖供应商基础地址，用于模型专用接口或 Rerank 端点。

### 7.3 `agent_definition` Agent 定义
| 字段 | 说明 |
| --- | --- |
| name / code | 名称与唯一编码 |
| description / system_prompt | 描述与系统提示词 |
| model_id | V45 新增，关联模型目录；要求 `CHAT` 或 `MULTIMODAL` |
| model_provider_id / model | 历史兼容字段；不作为新运行配置来源 |
| temperature / max_tokens | 参数（默认 0.70 / 2048） |
| max_tool_rounds | 最大工具轮数，默认 1 |
| default_thinking / default_reasoning_effort | 深度思考配置 |
| access_type | `private` / `public` |
| execution_mode | `STANDARD` / `DEEP`（V8） |

### 7.4 `agent_mcp_server` MCP 服务
`name, code, transport(http/sse/streamable_http), base_url, request_headers, auth_type(none/bearer/api_key), auth_token(AES), command, args, timeout_ms(30000), status`。

### 7.5 `agent_tool` 工具 与 `agent_tool_binding`
- `agent_tool`：`name, code, tool_type, mcp_server_id, mcp_tool_name, mcp_input_schema, timeout_ms, status`；`uk_code`、`uk_server_tool` 唯一。
- `agent_tool_binding`：`agent_definition_id, tool_id, priority, status`；`uk_agent_tool` 唯一。

### 7.6 `agent_conversation` 会话
`user_id, agent_definition_id, title, message_count, status, summary, summary_covered_message_id, summary_covered_created_at, summary_updated_at`。
`summary_*` 字段用于异步摘要的游标推进。

### 7.7 `agent_message` 消息
| 字段 | 说明 |
| --- | --- |
| conversation_id / role | 所属会话与角色(user/assistant/tool) |
| message_type | `chat` / `interaction` / `answer` |
| interaction_type / interaction_status | 交互卡片类型与状态 |
| question_config | 结构化提问配置 JSON |
| parent_message_id | 交互回复指向的父消息 |
| answered_at / expires_at | 交互作答与过期时间 |
| content / reasoning_content | 正文与推理过程 |
| rewritten_content | 查询改写后的内容（V2） |
| attachment_content / attachments | 附件识别文本与元数据（V3） |
| tool_calls / tool_call_id / tool_result | 工具调用 |
| model + token 统计 + latency_ms | 计量 |
| edited / original_content / edited_at | 编辑留痕 |
| citations | 引用来源 JSON（V1 后期） |

### 7.8 `agent_run` / `agent_run_step`
`agent_run`：`agent_definition_id, user_id, conversation_id, message_id, input/output_content, model, token统计, latency_ms, status, error_msg`；V8 增 `execution_mode`, `external_run_id`；V9 增 `retrieval_sources`。
`agent_run_step`（V8）：Deep Agent 事件流 `run_id, event_id, event_type, data, occurred_at`；`uk_run_event` 唯一用于事件去重。

### 7.9 `agent_tool_call_log` 工具调用审计
`run_id, tool_id, tool_call_id, tool_name, arguments, agent_definition_id, request_url/method/headers/body, response_status/body, latency_ms, status, error_msg`。
`status`：成功/失败/安全拦截/待审批。

### 7.10 智能体技能（Skill，V38）

#### 7.9.1 `agent_skill` 技能主记录
| 字段 | 说明 |
| --- | --- |
| name / code | 名称与全局唯一编码；`uk_code`（逻辑删除内唯一） |
| description / category | 用途说明与分类（制度问答、风险评估等） |
| status | `0` 草稿/未启用、`1` 启用、`2` 停用 |
| current_version_id | 当前最新已发布版本 ID；不影响 Agent 已固定安装的旧版本 |
| icon / tags | 展示信息 |

#### 7.9.2 `agent_skill_version` 版本快照（草稿 + 发布共用）
| 字段 | 说明 |
| --- | --- |
| skill_id / version_no | 所属 Skill 与发布递增版本号（草稿为空） |
| instruction | 领域指令 Markdown |
| input_schema / output_schema | 输入/输出契约 JSON Schema（TEXT） |
| tool_policy | 工具调用策略说明 |
| status | `0` 草稿（可编辑）、`1` 已发布（不可变） |
| change_note / published_at / published_by | 变更说明与发布信息 |

索引与约束：
- `uk(skill_id, version_no)`：仅对已发布记录生效（`version_no IS NOT NULL`）。
- `uk(skill_id)` 且 `status = 0`：每个 Skill 同时最多存在一个草稿。
- 发布在事务内复制草稿及全部子项为不可变发布快照；已发布记录及其子项绝不更新。

#### 7.9.3 `agent_skill_resource` 版本资源
`skill_version_id, name, type(MARKDOWN/SCRIPT/TEMPLATE), language, object_key, content_sha256, size, purpose, status(0 禁用/1 启用)`。
- `object_key`：不可覆盖对象键，发布后禁止替换与删除。
- `content_sha256`：文件哈希，发布事务中复核后再冻结。
- 上传校验扩展名/MIME/大小/数量；脚本一期仅作版本化资源，不执行。

#### 7.9.4 `agent_skill_tool_binding` 版本工具声明
`skill_version_id, tool_id, required, priority`；`uk(skill_version_id, tool_id)`。
只声明依赖既有 `agent_tool`，不复制凭证；仅收窄 Agent 已授权工具范围。

#### 7.9.5 `agent_skill_knowledge_binding` 版本知识库声明
`skill_version_id, knowledge_base_id`；`uk(skill_version_id, knowledge_base_id)`。
运行期最终范围 = Agent 已授权知识库 ∩ 本声明。

#### 7.9.6 `agent_definition_skill_binding` Agent 安装项
`agent_definition_id, skill_id, skill_version_id, priority, status(0/1), config_overrides`；`uk(agent_definition_id, skill_id)`。
- `skill_version_id`：安装的明确版本，不自动跟随最新。
- `config_overrides`：白名单内的安装级配置覆盖 JSON，不能扩展工具或知识库授权。

#### 7.9.7 `agent_run.skill_snapshot`
V38 为 `agent_run` 增加 `skill_snapshot TEXT`：在任何模型调用、检索或外部 Deep 请求前写入的冻结快照（实际版本与绑定、脱敏输入、资源对象键与 SHA-256、最终工具/知识库 ID、合成提示词 SHA-256、预算与裁剪结果），不保存完整敏感输入或资源正文。

---

## 8. 知识库（RAG）

### 8.1 `knowledge_base` 知识库
`scope(PLATFORM/AGENT), embedding_provider_id, name, description, index_status, status, owner_admin_id, visibility(platform), retrieval_config, review_config, reference_count, last_referenced_at`。

### 8.2 `agent_knowledge_base_binding`
`agent_definition_id, knowledge_base_id, status`；`uk_agent_kb` 唯一。

### 8.3 `knowledge_document` / `knowledge_document_version`
- `knowledge_document`：`knowledge_base_id, title, content, source_url, chunk_count, status`；V1 扩展 `source_type, original_file_name, file_extension, mime_type, file_size, file_checksum, storage_bucket, storage_object_key, current_version_no, draft_version_id, submitted_version_id, review_status, index_status, parser_type, index_error_message, indexed_at, reference_count, last_referenced_at`。
- `knowledge_document_version`：不可变版本快照 `knowledge_document_id, version_no, content, original_content, structured_content, content_checksum, review_status, storage_bucket/object_key, file_checksum, parser_type, index_status, submitted_by/at, reviewed_by/at, review_comment, chunk_count`；`uk_knowledge_document_version(document_id, version_no, deleted)`。

### 8.4 `knowledge_document_chunk`（pgvector）
| 字段 | 说明 |
| --- | --- |
| knowledge_base_id / document_id / document_version_id | 归属 |
| chunk_index | 块序号 |
| content / token_count | 内容与 token |
| embedding | `vector(1536) NOT NULL`，HNSW 余弦索引 |
| page_no / section_path | 页码与章节路径 |
| content_hash / metadata | 校验与元数据 |
| reference_count / last_referenced_at | 引用统计 |

索引：
- `idx_knowledge_document_chunk_embedding_cosine ... USING hnsw (embedding vector_cosine_ops)`
- `idx_knowledge_document_chunk_content_fts ... USING gin (to_tsvector('simple', content))`（V4 词法索引）
- `uk_knowledge_document_chunk_version_active(document_version_id, chunk_index)` 未删除唯一。

### 8.5 `knowledge_index_job` 索引任务
`knowledge_base/document/version_id, job_type, status(pending/running/success/failed), retry_count, max_retry_count(3), error_message, statistics, started_at, finished_at`。支持 `claim_pending` CAS 领取。

### 8.6 审核与 AI 审查
- `knowledge_review_task`：`knowledge_base/document/version_id, submitter_id, reviewer_id, status, source_checksum, submit/review_comment, submitted/claimed/reviewed_at`。
- `knowledge_review_action_log`：操作审计 `review_task_id, operator_id, action, before/after_status, comment, metadata`。
- `knowledge_ai_review`：`knowledge_base/document/version_id, source_checksum, source_content, model_provider_id, model, prompt_version, status, score, summary, issues, statistics, error_message, started/finished_at`。
- `knowledge_ai_review_issue`：`ai_review_id, document_version_id, block_id, issue_type, severity, message, original_excerpt, suggested_patch, handle_status(pending/accepted/rejected/manually_fixed/ignored), handled_by/at, handle_comment, applied_content, applied_checksum`。

### 8.7 引用与检索日志
- `knowledge_reference_log`：最终答案引用 `agent/conversation/message_id, knowledge_base/document/version/chunk_id, similarity, citation_no, referenced_at`。
- `knowledge_retrieval_log`（V5）：每次候选检索 `query_hash(sha256), knowledge_base/document/chunk_id, similarity, retrieval_score, cited, outcome(MATCHED/NO_MATCH), retrieved_at`。

### 8.8 检索评测（V6）
- `knowledge_retrieval_evaluation_set`：`agent_definition_id, name, description, status`。
- `knowledge_retrieval_evaluation_case`：`evaluation_set_id, question, document_id, section_path, remark, status`（人工标注期望命中）。
- `knowledge_retrieval_evaluation_run`：`evaluation_set_id, retrieval_config_snapshot, total/invalid_count, recall_at_k, mrr, ndcg, started/finished_at`。
- `knowledge_retrieval_evaluation_result`：逐题结果 `run_id, evaluation_case_id, status, retrieved_chunk_ids, recall_at_k, mrr, ndcg`。

---

## 9. 工作流（Workflow）

### 9.1 `agent_workflow` 定义
`agent_definition_id(可空，V11), name, description, nodes, edges, status, input_schema, output_schema, published_version, max_concurrent_instances(V21)`。
`nodes/edges` 始终保存草稿。

### 9.2 `agent_workflow_version`（V10）
发布版本快照：`workflow_id, version_no, nodes, edges, input_schema, output_schema, published_at`；`UNIQUE(workflow_id, version_no)`。

### 9.3 `agent_workflow_instance`（V10/V12/V14）
| 字段 | 说明 |
| --- | --- |
| workflow_id / workflow_version_id | 定义与发布版本 |
| user_id | 发起人 |
| status | RUNNING / WAITING_USER / FAILED / COMPLETED / TERMINATED / TIMED_OUT |
| variables / current_node_id | 运行变量与当前节点 |
| business_type / business_id | 业务关联（V12） |
| idempotency_key | 幂等键（V12），部分唯一索引去重 |
| callback_url | 业务回调地址（V12） |
| deadline_at | 执行期限（V14），到期超时终止 |
| error_message / started_at / completed_at | 审计 |

### 9.4 `agent_workflow_node_instance`（V10）
`instance_id, node_id, node_type, status, input/output_data, interaction_config, error_message, retry_count, started/completed_at`；`UNIQUE(instance_id, node_id)`。

### 9.5 `agent_workflow_execution_job`（V15）
持久化执行任务队列：`instance_id, status(PENDING/PROCESSING/COMPLETED/FAILED), attempt_count, next_attempt_at, locked_at(租约), error_message, completed_at`。

### 9.6 `agent_workflow_callback_delivery`（V12）
终态回调投递：`instance_id, event_type, callback_url, payload, status(PENDING/DELIVERED/RETRYING/FAILED), attempt_count, response_status/body, error_message, next_attempt_at, delivered_at`；`UNIQUE(instance_id, event_type)`。

### 9.7 触发器
- `agent_workflow_webhook_trigger`（V18）：`workflow_id, service_account_id, name, business_type, business_id_expression, idempotency_key_expression, variable_mapping, signing_secret(AES), enabled, last_triggered_at, last_error_message`。
- `agent_workflow_schedule_trigger`（V20）：`workflow_id, service_account_id, name, cron_expression, business_type, business_id_template, variables, enabled, next_fire_at, locked_until, last_triggered_at, last_error_message`；`idx(=due)` 按到期时间扫描。

### 9.8 `agent_workflow_template`（V19）
模板：`name, description, agent_definition_id, nodes, edges, input/output_schema, source_workflow_id, source_version`。

### 9.9 菜单/权限资源 seed（V10 V22–V32）
新增 `agent_workflow`, `agent_workflow_run`, `agent_workflow_operations`, `agent_workflow_schedule`, `menu_workflow`, `knowledge_evaluation`, `sys_service_account` 等多个路由/权限叶子（`perm_*_read` / `perm_*_write`），并对 `root` 角色自动授权。

---

## 10. 状态字典速查

| 对象 | 取值 |
| --- | --- |
| `agent_run.status` | 0=成功，1=失败，3=排队/等待用户，4=运行中，5=已取消 |
| 文档/版本 `review_status` | DRAFT → AI_REVIEWING → AI_REVIEWED → SUBMITTED → APPROVED/REJECTED |
| `knowledge_document_chunk.index_status` | 0=待处理 … 2=已索引 |
| 工作流实例 `status` | RUNNING / WAITING_USER / FAILED / COMPLETED / TERMINATED / TIMED_OUT |
| 工具调用 `status` | 0=成功，1=失败，3=安全拦截，4=待审批 |
| `agent_skill.status` | 0=草稿/未启用，1=启用，2=停用 |
| `agent_skill_version.status` | 0=草稿（可编辑），1=已发布（不可变） |
| `agent_skill_resource.status` | 0=禁用，1=启用 |
| `agent_definition_skill_binding.status` | 0=停用，1=启用 |

---

## 11. 说明

- 完整建表与种子脚本见 `V1__init.sql`；增量变更见各 `V*.sql`。
- 种子含管理员 `admin`（`System_Role_User`）、角色 `root` 及多组资源/字典数据。
- 生产部署仅以 Flyway 迁移为准，仓库内 `sql/postgresql/` 为迁移时的历史来源脚本。
