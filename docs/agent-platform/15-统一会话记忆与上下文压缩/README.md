# Agent 平台 - 统一会话记忆与上下文压缩

> 日期：2026-08-18
> 状态：提案
> 范围：为标准 Agent 和 Deep Agent 提供统一的会话记忆、上下文组装、压缩和容量可见性能力。
> 前提：系统尚未上线。本设计是目标模型，不包括遗留兼容、数据回填、双写或迁移桥接。

---

## 1. 目标与边界

### 1.1 目标

1. 标准 Agent 和 Deep Agent 使用同一套会话记忆与上下文预算策略。
2. 用户可以查看、修正和删除会话所使用的记忆。
3. 长会话在所选模型的输入预算内保持当前目标、已确认约束、决策和未完成工作不丢失。
4. 每个 Agent 均可配置压缩模型；未配置时使用常规聊天模型。
5. 聊天页面展示当前上下文容量以及每条消息的上下文贡献。
6. 上下文组装生成可度量、可解释的指标，但不将每个历史提供商请求保留为回放产物。

### 1.2 非目标

- 不保留模型隐藏推理或思维链。
- 不自动将密码、访问令牌、私钥或未经脱敏的敏感文件内容提升为记忆。
- 首个版本不使用向量存储作为会话记忆的事实来源。
- 不尝试精确复现每个历史提供商请求。
- 不仅为保留历史而在后续模型上下文中保留完整工具响应体。

### 1.3 原则

- 原始聊天消息持续保存，以供展示和审计；压缩仅改变后续模型输入。
- PostgreSQL 是事实来源。Redis 仅可缓存上下文视图和锁。
- 当前用户输入、平台安全策略、冻结的 Skill 指令和待处理交互状态属于受保护上下文，不得静默移除。
- 用户修正会取代旧记忆，而不是原地覆盖。
- 每个上下文区段均有预算、来源和实测 token 数。

---

## 2. 目标架构

```text
AgentConversation
  -> AgentSession (one session per conversation)
       -> Session Memory (facts, constraints, decisions, todos, artifacts)
       -> Conversation Summary (continuous historical compression)
       -> Recent Raw Messages
       -> Context Metrics (latest request and per-run snapshots)

Standard Agent request ----\
                         -> ContextAssemblyService -> ModelChatRequest
Deep Agent request --------/                         -> DeepAgentRunRequest
```

无论执行模式如何，每个会话都会创建 `AgentSession`。Deep Agent 的 Task、Plan、审批和检查点状态仍是 Deep 特有的高优先级上下文，但它们与标准聊天共用相同的记忆和预算服务。

### 2.1 上下文组装顺序

后续区段绝不覆盖前序区段建立的授权或安全约束。

信任分类从高到低为平台策略/冻结 Skill、已授权的结构化会话状态、用户确认记忆、模型生成内容，以及不可信的 RAG、工具输出和附件内容。不可信内容只能以带来源的“参考资料”渲染，且不得被提升、转述或抽取为权限、安全、审批、取消或工具授权指令；只有平台受控状态迁移和受限授权表示可改变这些指令。

1. 平台系统安全策略和 Agent 基础提示词。
2. 冻结的 Skill 指令和允许的能力边界。
3. 当前 Deep Task、计划、待处理审批或待处理用户问题。
4. 活跃会话记忆。
5. 连续会话摘要。
6. 最近的用户/助手消息和紧凑的历史工具结果。
7. 当前请求的 RAG 证据。
8. 当前用户消息。

### 2.2 上下文区段

| 区段 | 来源 | 处理方式 |
|---|---|---|
| 受保护内容 | 系统提示词、Skill 提示词、当前输入、待处理状态 | 绝不静默截断 |
| 任务 | 任务/计划/会话状态 | 高优先级；结构化紧凑形式 |
| 记忆 | `agent_session_memory` | 活跃条目，按重要性和新近度排序 |
| 摘要 | `agent_conversation_summary` | 结构化滚动摘要 |
| 近期历史 | `agent_message` | 最新的完整轮次组优先 |
| 工具历史 | 运行/工具日志 | 仅保留结果摘要和产物引用 |
| RAG | 检索结果 | 去重、排序并独立限额 |

权限、安全、审批和取消状态必须使用受平台校验的结构化字段/枚举和关联的主体、资源、操作、状态版本表示；不得从自由文本摘要、记忆、RAG、工具、附件或模型输出解析或恢复。渲染这些字段时保持其受保护身份，不接受后续区段的指令覆盖。

---

## 3. 数据模型

这是目标增量架构，而非对既有表的替代声明。实施以现有架构为基础，仅通过新的 Flyway 迁移新增或变更字段/表；绝不编辑已应用迁移，也不添加无明确需要的兼容字段。

### 3.1 Agent 配置

添加至 `agent_definition`：

| 字段 | 类型 | 含义 |
|---|---|---|
| `context_compression_model_id` | varchar，可为空 | 用于摘要和记忆整理的模型目录 ID；null 表示跟随 Agent 聊天模型 |

校验：

- 配置的目录条目及其提供商必须均已启用。
- 已删除或禁用的压缩模型会使 Agent 配置无效；不得静默回退到其他模型。
- 压缩模型可以不同于回答模型，并作为压缩操作计费和记录。

### 3.2 会话

每个 `agent_conversation` 都必须有 `agent_session`。

| 字段 | 含义 |
|---|---|
| `id` | 主键 |
| `conversation_id` | 唯一会话关联 |
| `user_id`, `agent_definition_id` | 所有权和 Agent 隔离 |
| `status` | `ACTIVE`, `WAITING_USER`, `WAITING_APPROVAL`, `PAUSED`, `ARCHIVED` |
| `active_task_id` | 可选的 Deep 任务 |
| `memory_version` | 活跃记忆变更时递增 |
| `revision` | 乐观并发控制版本 |
| `last_active_at` | 按最近活动排序 |

### 3.3 会话记忆

`agent_session_memory` 是用户可见的、结构化的持久会话记忆来源。

| 字段 | 含义 |
|---|---|
| `session_id` | 所属会话 |
| `memory_type` | `GOAL`, `CONSTRAINT`, `FACT`, `DECISION`, `TODO`, `ARTIFACT`, `PREFERENCE` |
| `content` | 规范化记忆陈述 |
| `summary` | 可选的简短展示/上下文形式 |
| `importance` | 整数 1-5 |
| `confidence` | 整数 0-100 |
| `status` | `ACTIVE`, `SUPERSEDED`, `DELETED` |
| `sensitivity_level` | `NORMAL`, `SENSITIVE`, `RESTRICTED` |
| `source_message_id`, `source_run_id`, `source_task_id` | 可追溯的来源指针 |
| `superseded_by_id` | 修正后的记忆记录 |
| `correction_reason` | 用户或系统的替换原因 |
| `expires_at` | 临时事实的可选过期时间 |

规则：

- 仅 `ACTIVE` 且未过期的记录可以进入上下文。
- 一次修正在同一事务中创建新记录并将旧记录标记为 `SUPERSEDED`。
- 用户创建或修正的记录优先于同一事实的自动提取记录。
- 仅在用户明确确认后才创建 `PREFERENCE`。
- `RESTRICTED` 记忆仅对获授权用户可见，除非明确获批的策略允许，否则不进入模型输入。
- 记忆写入必须携带会话 `revision` 或条目版本及 `Idempotency-Key`；版本冲突返回可重试的冲突错误，重复请求返回首次成功结果。
- 自动提取以来源事件、提取器版本和候选内容哈希为幂等键；写入时重新校验 `memory_version`，不得覆盖并发的用户修正。

### 3.4 结构化摘要

使用 `agent_conversation_summary`，每个会话一条当前记录：

| 字段 | 含义 |
|---|---|
| `conversation_id` | 唯一所属方 |
| `content_json` | 已校验的结构化摘要；仅含可进入模型的内容 |
| `covered_until_message_id`, `covered_until_created_at` | 连续覆盖游标 |
| `source_memory_version`, `source_event_range`, `source_sensitivity_max` | 生成依据的记忆版本、连续事件范围和最高敏感级别 |
| `summary_version`, `refresh_id` | 摘要 CAS 版本和幂等刷新标识 |
| `model_id` | 使用的压缩模型 |
| `input_tokens`, `output_tokens` | 压缩调用用量 |
| `status` | `READY`, `REFRESHING`, `FAILED` |
| `updated_at` | 快照时间 |

摘要 JSON 契约：

```json
{
  "goals": [{"id": "...", "content": "...", "sourceMemoryIds": ["..."], "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "constraints": [{"id": "...", "content": "...", "sourceMemoryIds": ["..."], "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "confirmedFacts": [{"id": "...", "content": "...", "sourceMemoryIds": ["..."], "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "decisions": [{"id": "...", "content": "...", "sourceMemoryIds": ["..."], "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "openQuestions": [{"id": "...", "content": "...", "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "pendingActions": [{"id": "...", "content": "...", "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}],
  "artifacts": [{"id": "...", "name": "...", "reference": "...", "sourceEventIds": ["..."], "sensitivityLevel": "NORMAL"}]
}
```

除 `openQuestions`、`pendingActions` 外，每个摘要条目都必须至少关联一个来源记忆或来源事件；条目 ID 在其摘要版本内唯一。服务会在持久化结果前校验字段名、数组长度、文本长度、来源可追溯性、敏感级别和被禁止的敏感内容。无效的模型输出不会替换有效摘要。来源记忆被修正、删除或过期时，立即将引用它的摘要条目过滤，并将摘要标记为不可用、清除缓存；在依据当前活跃记忆和连续事件重建成功前不得继续渲染。刷新以 `summary_version` 和覆盖游标 CAS 提交，使用相同 `refresh_id` 的重试必须幂等，冲突时重读后重建，绝不回退游标或覆盖较新摘要。

### 3.5 上下文指标

添加 `agent_run_context_metric`，每次模型调用一条不可变的指标快照；它以 `model_call_id` 标识调用实例，不以 `run_id` 代替调用身份。一个 run 可以有多次尝试或调用。

| 字段 | 含义 |
|---|---|
| `run_id` | 关联的执行记录 |
| `model_call_id`, `attempt_no`, `call_type` | 唯一调用、重试序号和 `ANSWER`、`DEEP_STEP`、`COMPRESSION` 等调用类型 |
| `metric_phase` | `PRELIMINARY`（派发前估算）或 `FINAL`（调用完成）；最终记录关联其初始调用 |
| `context_window_tokens` | 模型容量 |
| `output_reserve_tokens` | Agent 最大输出预留量 |
| `safety_reserve_tokens` | 协议/token 估算余量 |
| `input_budget_tokens` | 可用输入预算 |
| `prompt_tokens` | 可用时由提供商报告的输入 token 数 |
| `estimated_prompt_tokens` | 派发前的组装估算值 |
| `system_tokens`, `skill_tokens`, `task_tokens` | 受保护内容/上下文区段指标 |
| `memory_tokens`, `summary_tokens`, `history_tokens` | 会话区段指标 |
| `tool_tokens`, `rag_tokens`, `current_message_tokens` | 运行时区段指标 |
| `trimmed_message_count`, `compressed_message_count` | 压力处理结果 |
| `compression_status` | `NOT_NEEDED`, `ASYNC_PENDING`, `SYNC_COMPLETED`, `FAILED_FALLBACK` |

添加至 `agent_message`：

| 字段 | 含义 |
|---|---|
| `context_tokens` | 此消息用作历史记录时的 token 估算值 |
| `context_budget_tokens` | 已知时其来源调用的有效输入预算 |

这支持如实的 UI：单条消息的贡献是估算值，而助手请求占用率在可用时使用该次最终调用的 `prompt_tokens`。

---

## 4. 上下文预算策略

### 4.1 预算计算

```text
input_budget = context_window - output_reserve - safety_reserve
safety_reserve = max(512, context_window * 5%)
```

`context_window` 来自所选模型目录/提供商元数据。`output_reserve` 来自 Agent 的最大 token 设置。当仅受保护内容就超过 `input_budget` 时，请求以明确的配置错误失败；不得通过丢弃系统或用户内容来继续。

派发前必须使用与所选模型/提供商匹配的 tokenizer 进行预检；tokenizer 不可用时使用保守上界估算并在初步指标中标记。预检超限不得调用提供商，应返回可操作的上下文超限错误；若提供商仍返回超限错误，必须写入该调用的最终失败指标，不得伪装为已完成用量。

### 4.2 默认分配

以下百分比在扣除受保护内容后适用。它们是分配目标，不是保证值。

| 区段 | 目标占比 | 选择规则 |
|---|---:|---|
| 任务/待处理状态 | 15% | 仅当前状态 |
| 会话记忆 | 15% | 重要性、用户确认、新近度 |
| 摘要 | 15% | 紧凑的结构化渲染 |
| 近期历史 | 25% | 最新的完整轮次组优先 |
| 历史工具结果 | 10% | 仅结论、状态、产物引用 |
| RAG 证据 | 20% | 分数和来源多样性 |

### 4.3 压力处理

超出预算时，按以下顺序移除或缩减：

1. 排名最低的 RAG 片段。
2. 最旧的工具结果摘要。
3. 最旧的完整历史轮次。
4. 低重要性记忆和冗长摘要字段。
5. 如果摘要游标滞后且原始历史仍然过大，则触发同步的最小摘要刷新。

当前输入、受保护提示词、当前任务状态和未解决的交互状态保持完整。指标快照记录每次裁剪和压缩结果。

### 4.4 工具和附件策略

- 历史工具输入/输出以工具名称、紧凑参数摘要、状态、关键结果和产物/引用表示。
- 完整的 `responseBody` 保留在工具审计存储中，仅按需加载。
- 附件提供文件引用、规范化文件摘要和必要的已选事实；提取的全文不会重复注入。
- RAG 证据限定于请求范围。仅通过显式记忆提取规则或用户确认才会成为记忆。
- 工具输出、附件和 RAG 的内容、元数据与模型生成文本均按不可信输入处理；可保留来源和引用，但不得成为 `CONSTRAINT`、`DECISION`、审批/取消状态或任何权限、安全指令的唯一依据。

---

## 5. 记忆与压缩生命周期

### 5.1 请求生命周期

```text
Receive message
  -> resolve conversation/session
  -> validate session ownership and source consistency
  -> load active memory and latest summary
  -> assemble and budget context
  -> persist run + model-call preliminary context metric
  -> call answer model / Deep Agent
  -> persist final model-call metric and provider token usage
  -> update message context contribution
  -> asynchronously extract memory and refresh summary
```

对于 Deep Agent，在组装前创建/加载当前 Task 和计划，然后通过相同的 `ContextAssemblyService` 注入它们。出站的 `conversation_memory` 是同一组已选记忆/摘要/历史区段的渲染版本，而非独立的记忆算法。会话归属以 `conversation_id`、`user_id`、`agent_definition_id` 强制一致；所有消息、运行、任务、工具交互、审批、取消、附件和记忆来源均必须属于该会话，跨会话或跨主体来源一律拒绝。

### 5.2 压缩执行

- 常规刷新在助手消息完成后异步执行。
- 如果因摘要缺失或过期而导致原始历史无法安全容纳，请求会同步压缩满足容纳要求的最小连续最早批次。
- 每个会话一次仅运行一个刷新任务，由数据库/Redis 锁保护；持久化游标和 `summary_version` CAS 是最终的并发保护。
- 摘要批次边界是规范事件组，而非仅用户/助手消息对：一个组从用户输入或 Deep Task/交互事件开始，包含其工具调用及结果、审批请求/决定、取消和对应助手/Deep 结果，以终态事件结束；未终态的组不得拆分或压缩。
- 压缩失败时，保留之前的 `READY` 摘要不变，并使用受限的回退上下文。

### 5.3 记忆提取

压缩模型生成候选记忆变更，而非直接写入数据库。

1. 校验类型、内容大小、信任分类、敏感性策略、置信度和来源事件 ID。
2. 对照活跃记忆去重。
3. 检测与用户修正事实或更高置信度事实的冲突。
4. 通过带会话版本条件的事务应用已接受的新增/替换；重复提取返回已有结果。
5. 递增会话 `memory_version` 并清除派生上下文缓存。

自动提取可创建 `GOAL`、`CONSTRAINT`、`FACT`、`DECISION`、`TODO` 和 `ARTIFACT`。未经用户明确确认，不得创建 `PREFERENCE`；不可信来源不得自动创建 `CONSTRAINT` 或 `DECISION`，也不得创建或改变任何权限、安全、审批或取消表示。

### 5.4 用户修正与删除

修正 API 接收替换内容和必填原因：

```json
{
  "content": "项目需要 Java 8，而不是 Java 17",
  "reason": "用户修正运行环境约束"
}
```

服务器创建新的 `ACTIVE` 记忆，将旧条目标记为 `SUPERSEDED`，关联两条记录，递增 `memory_version`，并使已组装上下文缓存、引用该记忆的摘要和派生候选项立即失效。删除会将记录改为 `DELETED`，按相同规则过滤/重建摘要；过期由定时任务以同一传播流程处理。原始聊天来源数据保持不变，并按其独立保留策略处理；记忆、摘要、缓存、索引和派生候选项的删除/保留期限必须同步传播且可审计。

---

## 6. API 与事件

所有端点均校验会话所有权、Agent 权限和当前用户。

写端点要求 `If-Match` 会话/记忆版本和 `Idempotency-Key`；冲突返回当前版本及不含敏感内容的冲突信息。反馈请求还必须包含目标记忆版本，避免对已取代记录写入过时结论。

| 方法 | 路径 | 用途 |
|---|---|---|
| `GET` | `/api/agent/conversation/{id}/memory` | 活跃且可见的记忆记录 |
| `PUT` | `/api/agent/conversation/{id}/memory/{memoryId}` | 通过取代修正记忆 |
| `DELETE` | `/api/agent/conversation/{id}/memory/{memoryId}` | 从未来上下文移除记忆 |
| `POST` | `/api/agent/conversation/{id}/memory/feedback` | 将记忆标记为准确、不准确或已过期 |
| `GET` | `/api/agent/conversation/{id}/context` | 容量、最新指标、区段明细、裁剪状态 |
| `GET` | `/api/agent/conversation/{id}/messages` | 包含上下文贡献字段的消息 |

`GET .../context` 示例：

```json
{
  "model": "gpt-4o",
  "contextWindowTokens": 32768,
  "outputReserveTokens": 2048,
  "safetyReserveTokens": 1638,
  "inputBudgetTokens": 29082,
  "latestPromptTokens": 8240,
  "occupancyPercent": 28.33,
  "sections": {
    "system": 520,
    "skill": 640,
    "task": 0,
    "memory": 810,
    "summary": 660,
    "history": 2760,
    "tool": 430,
    "rag": 1880,
    "currentMessage": 540
  },
  "trimmedMessageCount": 3,
  "compressionStatus": "SYNC_COMPLETED"
}
```

聊天和 Deep 完成事件添加具有相同字段的 `contextMetric`。在重新加载持久化历史前，UI 将其视为临时数据。

`feedback` 请求采用 `{ "memoryId", "memoryVersion", "verdict": "ACCURATE|INACCURATE|EXPIRED", "reason": "..." }`；`reason` 对 `INACCURATE` 必填。`ACCURATE` 仅记录确认及审计时间，`INACCURATE` 创建待修正/撤销的用户反馈并立即停止该记忆进入上下文，`EXPIRED` 设置或提前触发过期并执行摘要失效传播。反馈不直接改写历史内容，必须幂等且对同一版本只生效一次。

---

## 7. 控制台设计

### 7.1 会话记忆

聊天页面为每种 Agent 模式提供共享的 `ConversationMemoryPanel`。

- 在标准聊天中，从聊天页头模型/上下文信息旁打开它。
- 在 Deep 聊天中，在 Session 工作区抽屉中渲染相同面板，并为窄屏保留页头触发器。
- 该面板按类型分组记录，并显示内容/摘要、来源、重要性、状态和更新时间。
- 用户可展开详情、修正记录或删除记录。
- 修正使用带替换文本和必填原因的受控表单；绝不原地编辑旧记录。
- 在移动端，以全高抽屉显示记忆，而不是嵌套模态框。

### 7.2 上下文容量

聊天页头在模型名称后显示紧凑的容量徽标：

```text
Context 8,240 / 29,082 tokens 28.3%
```

- 分子：最新已完成的回答或 Deep 步骤调用中提供商报告的 `promptTokens`；仅在该次调用已完成但提供商用量不可用时使用估算提示 token，并将其标示为估算值。压缩调用不作为聊天占用率分子。
- 分母：有效输入预算，而非原始模型上下文窗口。
- 提示框/抽屉明细：原始模型窗口、输出预留、安全预留、每个区段的 token 数以及裁剪/压缩状态。
- 状态：低于 60% 为正常，60-80% 为警告，高于 80% 为高。为便于无障碍访问，颜色应搭配文本/图标。
- 在请求完成前，显示 `预估本次上下文`（派发前估算），不得称为“最新调用”；没有任何已完成调用时显示 `尚无已完成上下文度量`，而非 `0%`。

### 7.3 每条消息的贡献

`AgentMessageBubble` 添加紧凑元数据：

```text
History contribution 412 tokens (1.4%)
Request context 8,240 / 29,082 (28.3%)
```

- 第一行使用 `message.contextTokens / message.contextBudgetTokens`，表示该消息对未来历史的贡献。
- 第二行显示于具有关联最终回答/Deep 步骤调用指标的助手消息，表示生成该响应的完整请求上下文；仅有初步指标时明确标记为“预估本次”。
- 较旧或不完整的数据展示为 `未度量`，绝不猜测为精确的历史值。
- 共享消息气泡也会自动更新管理端会话抽屉。

### 7.4 Agent 配置

Agent 定义编辑器添加 `上下文压缩模型`：

- 默认选项：`跟随聊天模型`。
- 其他选项：已启用的模型目录条目。
- 帮助文本：它仅用于创建会话摘要和记忆候选项；不会改变响应模型。

所有新标签、状态、错误、警告和确认文本都需要 `zh-CN` 和 `en-US` 语言区域条目。

---

## 8. 开发阶段

### 阶段 1 - 上下文指标基础

**目标：** 在改变记忆行为前，使当前上下文预算可观测。

交付内容：

- 添加聊天 API 所需的模型解析后上下文窗口元数据。
- 创建 `agent_run_context_metric` 并添加消息上下文贡献字段。
- 在 `ContextAssemblyService` 中集中处理 token 估算和区段核算。
- 在派发前持久化初步指标，并在完成后持久化最终提供商用量。
- 为每次模型调用记录独立调用 ID、尝试序号、调用类型及初步/最终阶段，重试不得覆盖先前调用指标。
- 添加 `GET /api/agent/conversation/{id}/context` 并丰富消息历史响应。
- 添加页头容量徽标和每条消息贡献 UI。

验收标准：

- 标准和 Deep 已完成运行均有区段指标。
- 容量分母为有效输入预算，而非最大输出 token 数或原始窗口。
- UI 区分估算输入用量和提供商报告的用量。
- 缺失的指标显示为不可用。

### 阶段 2 - 统一会话与只读记忆

**目标：** 为两种执行模式建立单一记忆来源。

交付内容：

- 要求每个会话都有一个 `agent_session`。
- 创建带有状态、来源、敏感性和取代字段的 `agent_session_memory`。
- 在 `ContextAssemblyService` 中实现活跃记忆选择和预算化渲染。
- 标准 Agent 和 Deep Agent 均使用相同的已渲染会话记忆块。
- 实现记忆查询 API 和只读 `ConversationMemoryPanel`。

验收标准：

- 无论标准模式还是 Deep 模式，同一会话只有一个会话记录。
- 仅当记忆活跃、已授权、未过期且在其分配额度内时才纳入。
- Deep Task 状态是附加上下文，而非第二套记忆实现。

### 阶段 3 - 结构化摘要与压缩模型

**目标：** 用经过校验、可配置的压缩替代非结构化滚动文本。

交付内容：

- 向 Agent 定义和配置校验添加 `context_compression_model_id`。
- 创建结构化摘要存储和经过校验的 JSON 契约。
- 按完整轮次实现异步连续摘要刷新。
- 实现过期摘要的同步最小压缩回退。
- 在上下文指标中存储压缩用量和状态。

验收标准：

- 未配置压缩模型的 Agent 使用其聊天模型。
- 明确拒绝配置了已禁用/删除压缩模型的情况。
- 摘要游标仅能连续推进。
- 无效/失败的压缩不会替换最后一个有效摘要。
- 并发刷新或重复投递不会回退摘要游标、覆盖较新版本或产生重复摘要。
- 长会话保持在预算内，同时保留目标、约束、决策和待处理操作。

### 阶段 4 - 记忆提取、修正与治理

**目标：** 使记忆实用且由用户控制。

交付内容：

- 在消息完成后生成经过校验的记忆候选项。
- 添加去重、冲突处理、信任分类、敏感内容筛查、过期和重要性选择。
- 实现修正、删除和反馈 API。
- 添加包含确认、成功、失败和加载状态的修正/删除 UI。
- 在记忆详情中添加可见的来源链接和取代原因。

验收标准：

- 修正以原子方式激活替换记录并取代旧记录。
- 已取代/删除的记忆不得进入后续模型上下文。
- `PREFERENCE` 要求用户明确确认。
- 敏感/禁止内容不得写入持久记忆，并在不泄露内容的前提下记录日志。
- 不可信来源不得提升为权限、安全、审批、取消或工具授权指令。

### 阶段 5 - Deep 运行时对齐与生产强化

**目标：** 实现完整的行为一致性和运行安全性。

交付内容：

- 向 Deep Agent 发送统一组装的记忆/摘要/历史表示。
- 通过签名回调返回并持久化 Deep 上下文区段指标。
- 对两种模式应用一致的工具/附件紧凑化处理。
- 添加压缩延迟、失败、上下文压力、裁剪和记忆修正率的控制台/指标。
- 记录保留、删除、授权和运行故障排查说明。

验收标准：

- 标准和 Deep 请求对记忆、摘要、历史、工具输出和 RAG 使用相同的选择/预算策略。
- Deep 重启/恢复会保留会话/任务状态，且不会在记忆中重复当前输入。
- 默认不注入完整历史工具响应或未经脱敏的附件内容。
- 运维人员可识别高压力会话和压缩失败，而不会暴露敏感上下文。

---

## 9. 验证策略

### 单元测试

- 不同上下文窗口和输出预留量的预算计算。
- 受保护上下文溢出时返回明确错误。
- 区段裁剪顺序和完整轮次分组。
- 结构化摘要校验、游标单调性和回退行为。
- 记忆排序、过期、敏感性过滤、重复检测和取代。
- 仅当提供商 token 用量有效时，才覆盖初步估算用量。
- 规范事件组覆盖工具调用/结果、用户交互、Deep Task、审批和取消，且未终态组不被拆分。
- 摘要来源记忆修正、删除或过期后立即过滤，并在 CAS 重建后恢复；并发/重复刷新不回退游标。
- 信任分类和受限授权表示拒绝由 RAG、工具、附件或模型文本提升权限、安全、审批或取消指令。
- 压缩出站前执行内容分类、脱敏、提供商允许范围校验；分类、脱敏或提供商治理失败时不出站，保留有效摘要并使用受限回退上下文。
- Tokenizer 按模型/提供商选择；不可用时使用保守估算并标记，预检超限不调用提供商，提供商超限错误记录最终失败指标并返回可操作错误。

### 集成测试

- 对于同一会话，标准和 Deep 请求接收等价的会话记忆输出。
- 长多语言会话保持在预算内，并保留当前目标/约束/待办事项。
- 大型工具响应和附件文本不会挤占受保护内容。
- 修正和删除限定于所有权范围，并立即使上下文缓存失效。
- 模型配置拒绝不可用的压缩模型。
- 会话、消息、运行、任务、工具、审批、取消、附件和记忆来源的主体/会话不一致被拒绝；管理端授权范围不扩大用户可见性。
- 记忆修正、删除、反馈和自动提取的版本冲突、重复请求及并发用户修正不会丢失更新或重复写入。
- 记忆编辑、删除、反馈和自然过期均传播到摘要、缓存、索引和保留任务，且原始来源遵循独立保留策略。

### UI 测试

- 页头正确显示精确、估算和不可用的容量状态。
- 消息气泡渲染贡献和请求占用率时，不将缺失数据视为零。
- 记忆修正要求填写原因并报告服务器错误。
- 反馈要求对不准确项填写原因，并正确展示已停止使用、已过期和并发冲突状态。
- 容量文案区分“预估本次”和“最近已完成调用”，压缩调用不显示为聊天请求占用率。
- 桌面端和移动端记忆面板不会创建嵌套对话框或溢出 Deep 工作区抽屉。

---

## 10. 运行指标

在不存储原始提示词内容的前提下跟踪：

- 按 Agent/模型统计的上下文占用百分比。
- 区段 token 分布和裁剪计数。
- 压缩调用、延迟、token 用量、失败和同步回退频率。
- 已创建、已修正、已取代、已删除、已过期以及因敏感性被拒绝的记忆记录。
- 上下文占用率超过 80% 的会话。
- 重复提问率和记忆命中率。
- 上下文指标摄取中标准模式与 Deep 模式的一致性失败。

## 11. 实施约束

- 仅使用新的 Flyway 迁移；绝不编辑已应用的迁移文件。
- 每个上下文指标阶段记录一经写入即不可变；提供商报告的用量写入关联的 `FINAL` 记录，不得回写 `PRELIMINARY` 记录。
- 指标以模型调用为不可变身份；初步和最终阶段关联同一调用，run、重试和压缩调用不得混淆。
- 不得在任何记忆/摘要/指标字段中持久化 API 密钥、委托令牌、原始隐藏推理或被禁止的敏感内容。
- 所有记忆和上下文端点都需要用户所有权校验；管理端可见性必须单独按权限范围控制。
- Redis 丢失只能导致缓存未命中或额外压缩工作，绝不能导致权威记忆或摘要状态丢失。
- 压缩前必须按内容分类和脱敏策略决定可出站字段，并校验模型提供商、区域和数据处理许可；无法满足时不得向压缩提供商发送内容，记录无内容的治理失败原因并采用本地最小化/不压缩回退。
