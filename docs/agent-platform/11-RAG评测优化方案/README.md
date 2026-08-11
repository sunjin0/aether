# Agent 平台 - RAG 检索评测优化方案

> 更新日期：2026-08-11
> 范围：知识库检索评测的可信性、可复现性、可诊断性与规模化执行。
> 非范围：本方案暂不处理资源权限、组织隔离和历史结果脱敏。

---

## 一、目标与边界

现有功能已形成“评测集 -> 问题/目标文档或章节 -> 同步运行 -> Recall@K、MRR、nDCG -> 历史明细”的基础闭环。它适合小样本人工试验，但不适合作为检索配置发布或回归决策依据。

一期目标是建设可重复执行的检索评测平台：

1. 冻结一次运行所依赖的数据集、Agent、知识库和检索配置，保证历史结果可复现。
2. 明确文档、章节和分块三种标注粒度，避免“整篇文档等同全部 Chunk 正例”造成指标失真。
3. 区分命中、未命中、无效标注和检索异常，避免基础设施故障伪装为算法退化。
4. 将运行改为异步任务，支持大样本、进度、取消、失败重试和受控并发。
5. 提供用例治理、逐题诊断、运行趋势、基线对比与导入导出能力。

本期名称保持“检索评测”。它只评估检索结果，不代表最终回答正确性；回答质量、引用质量和 groundedness 作为二期能力建设。

### 模型目录快照（V45）

评测运行快照除知识库检索参数外，还冻结向量、查询重写和 Rerank 所使用的模型目录信息：模型名称、供应商名称、能力、状态和非敏感调用地址。快照不记录目录内部 ID、API Key 或供应商密钥。页面应以名称和能力展示快照，避免向用户暴露内部 ID。

查询重写使用 `queryRewriteModelId`（`CHAT`/`MULTIMODAL`），Rerank 使用 `rerankModelId`（`RERANK`），知识库向量使用 `embeddingModelId`（`EMBEDDING`）。三者都由后端在运行时复核目录和供应商状态；不兼容或不可用时记录诊断并按既定降级策略执行。

---

## 二、现状与问题

| 问题 | 当前表现 | 风险 | 优化结论 |
| --- | --- | --- | --- |
| 历史结果不可复现 | 运行只保存指标和召回 Chunk ID，未写入已预留的 `retrievalConfigSnapshot` | 文档、标注、绑定或配置变化会污染旧结果 | 运行与逐题结果全部改为不可变快照 |
| 指标语义失真 | 整篇文档会展开为该文档全部 Chunk，并以全部 Chunk 为 Recall 分母 | 文档越长，Recall 越低；分数不反映“是否找到资料” | 按文档、章节、Chunk 分层计算指标 |
| 错误被计为未命中 | 检索服务吞掉异常后返回空结果，结果统一标记为 `EVALUATED` | 无法区分模型、索引、Provider 故障和算法问题 | 引入运行与逐题状态、错误码和错误统计 |
| 同步串行执行 | HTTP 请求内逐题检索 | 超时、无进度、重复提交、无法取消 | 改为数据库任务队列和后台受控并发执行 |
| 评测范围不一致 | 文档选择来自所有可读文档，未保证属于目标 Agent 的可检索知识库 | 目标本身不可检索时必然低分 | 标注和运行时校验 Agent 检索范围 |
| 用例管理不完整 | 页面仅支持新建集合和新增问题 | 无法修正、停用、批量治理或复用样本 | 补齐集合、用例、标签版本与导入导出 |
| 诊断信息不足 | 前端展示 ID、聚合分数和 Chunk 排名 | 无法解释回归原因 | 展示名称、目标命中、通道、分数、异常和基线差异 |
| 测试不足 | 仅覆盖指标公式的基础单测 | 快照、异常、任务、标注和对比易回归 | 为领域计算、运行编排和 API 增加测试 |

---

## 三、评测模型

### 3.1 分层评估

| 层级 | 评估内容 | 主要指标 | 阶段 |
| --- | --- | --- | --- |
| 检索层 | 正确资料是否被召回、排序是否合理 | Hit@K、MRR@K、nDCG@K、Chunk Recall@K、空召回率、延迟 | 一期 |
| 上下文层 | 上下文是否覆盖必要证据且不过度冗余 | 证据覆盖率、冗余率、Token 使用量 | 一期可选 |
| 回答层 | 最终回答是否正确、有依据、引用可信 | Correctness、Faithfulness、Citation Precision/Recall、Groundedness | 二期 |

### 3.2 标注粒度

一条问题可拥有多个正例标签。标签不可再用“整篇文档自动展开为全部 Chunk”表达，而是明确目标层级。

| `targetType` | 命中条件 | 首选指标 |
| --- | --- | --- |
| `DOCUMENT` | Top-K 中任一 Chunk 属于目标文档 | Document Hit@K、Document MRR@K |
| `SECTION` | Top-K 中任一 Chunk 位于目标章节 | Section Hit@K、Section MRR@K |
| `CHUNK` | Top-K 命中指定 Chunk 或 Chunk 集合 | Chunk Recall@K、MRR@K、nDCG@K |

标签建议支持 `relevanceGrade`（1 到 3）和 `isRequired`。前者用于分级 nDCG，后者表示该证据是否为必须覆盖的资料。

### 3.3 聚合规则

每次运行必须保存 `metricKs`，默认建议为 `[1, 3, 5, 10]`，并指定一个主指标，例如 `DOCUMENT/HIT_RATE@5`。

聚合指标只计算成功执行且标注有效的用例。页面始终同时展示样本覆盖：

```text
Document Hit@5: 82.4%
有效评测：84 / 100
失效标注：6
检索异常：10
```

无标签用例不参与指标计算，状态为 `INVALID_LABEL`，不得按满分或零分混入总体。召回结果应按 Chunk ID 去重，MRR 与 nDCG 必须在对应 K 截断。

---

## 四、数据模型与快照

### 4.1 数据集版本

保留 `knowledge_retrieval_evaluation_set` 和 `knowledge_retrieval_evaluation_case`，新增标签和版本模型。

```text
knowledge_retrieval_evaluation_label
  id, evaluation_case_id, target_type, knowledge_base_id, document_id,
  document_version_id, section_path, chunk_id, relevance_grade, is_required,
  remark, status, created_at, updated_at, deleted

knowledge_retrieval_evaluation_set_version
  id, evaluation_set_id, version_no, name, description, dataset_snapshot,
  status, published_at, created_at, updated_at
```

用例补充字段：`category`、`difficulty`、`language`、`queryType`、`source`、`tags`、`expectedAnswer`、`remark`、`status`。发布版本后，运行只能引用版本，不引用会继续变动的草稿数据。

### 4.2 运行快照

扩展 `knowledge_retrieval_evaluation_run`：

```text
status                       // QUEUED/RUNNING/SUCCEEDED/PARTIAL_FAILED/FAILED/CANCELLED
trigger_type                 // MANUAL/BASELINE/CI/SCHEDULED
triggered_by
evaluation_set_version_id
run_config_snapshot          // K、并发、筛选条件、缓存策略
agent_snapshot               // Agent 名称、模型、版本、提示词 hash
knowledge_scope_snapshot     // 绑定知识库、索引状态和版本摘要
retrieval_config_snapshot    // 各知识库检索配置的冻结 JSON
provider_snapshot            // embedding/reranker Provider、模型与版本
dataset_snapshot             // 用例、标签、文档/Chunk 解析结果
metrics_json
latency_metrics_json
error_summary_json
baseline_run_id
duration_ms
```

运行创建时先生成完整快照，再创建任务。运行期间和详情查询都只读取快照；不能依据当前文档、当前 Chunk 或当前用例重新组装历史结果。

### 4.3 单题结果快照

扩展 `knowledge_retrieval_evaluation_result`：

```text
sequence_no
status                       // HIT/MISS/INVALID_LABEL/RETRIEVAL_ERROR/SKIPPED
error_code
error_message
question_snapshot
case_metadata_snapshot
labels_snapshot
retrieved_items_snapshot
retrieval_trace_snapshot
metric_json
latency_ms
embedding_latency_ms
rerank_latency_ms
token_count
```

`retrieved_items_snapshot` 至少保存排名、Chunk/文档/章节、文档标题、向量分数、词法分数、重排分数、检索通道和命中的标签 ID。

---

## 五、异步运行架构

### 5.1 状态机

```text
QUEUED -> RUNNING -> SUCCEEDED
                  -> PARTIAL_FAILED
                  -> FAILED
QUEUED -> CANCELLED
RUNNING -> CANCEL_REQUESTED -> CANCELLED
```

运行开始时立即写入 `startedAt`，结束后写入 `finishedAt` 和 `durationMs`。当前在评测执行完才同时设置开始和结束时间的行为必须移除。

### 5.2 任务表

```text
knowledge_retrieval_evaluation_task
  id, run_id, evaluation_result_id, sequence_no, status,
  attempt_count, max_attempts, started_at, finished_at,
  error_code, error_message, worker_id, created_at, updated_at
```

一期使用数据库任务队列即可：接口负责写入运行与任务，Spring 定时任务领取 `QUEUED` 任务，受控线程池执行并逐题持久化结果。后续接入消息队列时不改变领域模型和 API。

当前已实现数据库任务表、原子领取、30 分钟运行租约恢复、进度轮询、取消排队任务和失败任务人工重试。异步入口为 `POST /api/knowledge/evaluation/sets/{id}/runs`；保留旧的同步入口仅用于前端平滑迁移。

当前还实现了 `knowledge_retrieval_evaluation_label` 多正例标签和 `knowledge_retrieval_evaluation_set_version` 发布快照。异步运行优先使用多标签目标；没有标签的历史用例仍兼容使用其文档、章节或 Chunk 字段。

当前还实现了每个评测集唯一的运行基线、历史趋势和两次运行的聚合/逐题对比。不同数据集版本或冻结快照的运行会明确标记为不可严格比较，避免将样本变化误判为检索回归。

运行创建时已写入实际检索范围和配置快照，包括启用绑定、平台库、知识库 `retrievalConfig`、索引状态和 embedding Provider 非敏感元数据；Provider API Key 不会写入评测数据。

评测集提供健康检查，识别空问题、失效目标、标签粒度混用和不在当前有效检索范围的目标。当前草稿在发布或发起运行前必须通过该检查，防止无效标注污染运行指标；已发布的冻结版本仍可运行以保留历史可追溯性。

本轮审查修复了两项可信性缺口：没有可用检索 Provider 或 Provider 全部失败时，单题记录为 `RETRIEVAL_ERROR`，不会被计入正常未命中；新运行会冻结目标文档标题和召回 Chunk 的文档标题、章节、序号和排名，结果详情不再依赖后续变更的文档或 Chunk。阶段 3 已补齐用例编辑、删除、批量启停、JSON 导入导出及逐题状态筛选；JSON 导入采用预校验和确认写入两阶段，校验目标可用性与 Agent 检索范围。仍缺列表分页、CSV 支持和独立路由的工作台/运行详情页。

### 5.3 执行规则

1. 默认运行并发为 3，并按 Provider 设置独立并发上限。
2. 默认绕过线上检索缓存；若显式启用评测缓存，缓存键必须包含运行快照 hash。
3. 网络超时、Provider 5xx 可有限重试；配置错误、无可用知识库、无可用模型不重试。
4. 支持取消未开始任务；正在执行的任务完成后停止后续领取。
5. 任务完成后实时更新运行进度和成功、失败、失效计数。
6. 所有任务结束后计算聚合指标并完成运行状态流转。

---

## 六、后端职责划分

| 服务 | 职责 |
| --- | --- |
| `EvaluationSetService` | 集合、用例、标签、草稿与发布版本管理 |
| `EvaluationSnapshotService` | 创建运行快照、解析标签、数据健康检查 |
| `EvaluationRunService` | 创建运行、状态流转、取消、失败重试、聚合 |
| `EvaluationTaskExecutor` | 领取和执行单题任务、异常分类、重试 |
| `RetrievalEvaluationService` | 调用检索链路并生成单题诊断数据 |
| `RetrievalMetricCalculator` | 文档/章节/Chunk 的纯指标计算 |
| `EvaluationResultQueryService` | 概览、趋势、对比、逐题诊断与导出 |
| `EvaluationImportExportService` | CSV/JSON 模板、预校验、导入和导出 |

控制器只处理请求校验和服务调用。不得继续由单个 Controller 同时承担标注解析、同步检索、持久化和结果拼装。

---

## 七、API 设计

所有列表接口采用分页、排序和筛选，停止返回无上限全量数据。

### 7.1 集合、用例和版本

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/api/knowledge/evaluation/sets` | 评测集分页列表 |
| `POST` | `/api/knowledge/evaluation/sets` | 创建评测集 |
| `GET/PUT/DELETE` | `/api/knowledge/evaluation/sets/{id}` | 详情、编辑、删除草稿 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/versions` | 发布冻结版本 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/versions` | 版本列表 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/health` | 数据集健康检查 |
| `GET/POST` | `/api/knowledge/evaluation/sets/{id}/cases` | 用例分页列表、新增 |
| `GET/PUT/DELETE` | `/api/knowledge/evaluation/sets/{id}/cases/{caseId}` | 用例详情、编辑、删除 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/cases/batch-status` | 批量启停 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/cases/import` | 导入 CSV/JSON |
| `GET` | `/api/knowledge/evaluation/sets/{id}/cases/export` | 导出用例 |

### 7.2 运行和结果

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `POST` | `/api/knowledge/evaluation/sets/{id}/runs` | 创建异步运行，返回 `runId` |
| `GET` | `/api/knowledge/evaluation/sets/{id}/runs` | 运行分页列表 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}` | 运行概览和配置快照 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/progress` | 实时进度 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/cancel` | 请求取消 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/retry-failed` | 重试失败题目 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/baseline` | 标记或取消标记基线 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/results` | 逐题结果分页 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/results/{resultId}` | 单题完整诊断 |
| `POST` | `/api/knowledge/evaluation/sets/{id}/runs/compare` | 两次运行对比 |
| `GET` | `/api/knowledge/evaluation/sets/{id}/trend` | 运行趋势 |

创建运行请求示例：

```json
{
  "evaluationSetVersionId": "version-001",
  "metricKs": [1, 3, 5, 10],
  "primaryMetric": { "targetLevel": "DOCUMENT", "name": "HIT_RATE", "k": 5 },
  "concurrency": 3,
  "bypassRetrievalCache": true,
  "includeTrace": true,
  "caseFilters": { "tags": ["退款"], "difficulty": ["MEDIUM", "HARD"] },
  "baselineRunId": "run-previous"
}
```

---

## 八、前端方案

### 8.1 页面结构

| 页面 | 路由 | 主要能力 |
| --- | --- | --- |
| 评测集列表 | `/knowledge/evaluation` | 筛选、创建、复制、导入导出、进入工作台 |
| 评测集工作台 | `/knowledge/evaluation/:setId` | 用例管理、运行历史、趋势、设置 |
| 运行详情 | `/knowledge/evaluation/:setId/runs/:runId` | 实时进度、指标、诊断、对比、导出 |

不再使用一个 Drawer 同时承载集合、用例、历史、对比和结果详情。

### 8.2 评测集列表

显示名称、Agent 名称、已发布版本、有效用例数、最近运行状态、主指标、更新时间。必须显示名称而非 `agentDefinitionId` 或 `documentId`。

### 8.3 工作台

建议使用 Tabs：

| Tab | 内容 |
| --- | --- |
| 用例管理 | 分页、筛选、批量启停、编辑、标签、健康状态 |
| 运行历史 | 状态、主指标、样本覆盖、耗时、基线标识 |
| 趋势分析 | 指标、失败率、失效标注率、延迟变化 |
| 设置 | Agent、默认 K、默认运行参数、说明 |

用例健康状态至少包含 `HEALTHY`、`OUT_OF_SCOPE`、`DOCUMENT_DELETED`、`SECTION_NOT_FOUND`、`CHUNK_NOT_FOUND`、`KNOWLEDGE_BASE_NOT_INDEXED`。

### 8.4 运行详情与逐题诊断

顶部展示运行状态、耗时、评测集版本、Agent/知识库/模型快照、主指标、样本覆盖和相对基线变化。

逐题表支持筛选：全部、命中、未命中、检索异常、失效标注、相对基线退化、相对基线提升。单题抽屉展示冻结问题、标签、召回排名、检索通道、各类分数、命中标签、遗漏目标、耗时、错误信息和基线差异。

### 8.5 运行交互

1. 点击“运行评测”后先打开配置弹窗。
2. 提交后立即跳转运行详情，不等待同步完成。
3. 运行中轮询进度接口；离开页面或页面失焦时停止轮询。
4. 完成后自动刷新指标、结果和趋势。
5. 支持取消、失败重试、设置基线和导出。

---

## 九、数据集治理与导入导出

导入优先使用 UTF-8 CSV，复杂场景支持 JSON。建议字段：

```text
question, category, difficulty, query_type, tags, expected_answer,
target_type, knowledge_base_id, document_id, section_path, chunk_id,
relevance_grade, is_required, remark, status
```

同一问题的多个标签可以使用同一 `groupKey` 的多行表示。上传后先进行预校验，返回行级错误、重复问题、无效文档/章节/Chunk、超出 Agent 检索范围等问题；用户确认后才写入草稿。

发布版本前执行健康检查：

1. 空问题或空标签。
2. 失效文档、章节或 Chunk。
3. 不在 Agent 当前检索范围内的目标。
4. 重复或高度相似问题。
5. 知识库、难度、标签或问题类型分布严重偏置。
6. 关键分组样本量不足。

---

## 十、基线、对比与质量门禁

每个已发布评测集版本可以指定一个基线运行。候选运行与基线必须优先比较同一评测集版本；若版本不同，接口返回 `nonComparable=true`，同时列出用例、标签、配置和知识库快照差异。

对比结果至少包括：

1. 各 K 的聚合指标增减。
2. 成功覆盖率、异常率、失效标注率和延迟变化。
3. 新增命中、丢失命中、排名上升和排名下降的题目。
4. Agent、知识库、检索配置、模型和数据集版本差异。

一期先支持人工设置基线和人工判读；二期可在 CI 或配置发布流程中设置门禁，例如主指标下降超过 2 个百分点或错误率超过 2% 时阻断发布。

---

## 十一、实施阶段

### 阶段 1：修复结果可信性

1. 新增多标签和标注粒度模型。
2. 重构指标计算，支持 Document/Section/Chunk 和多 K。
3. 新增评测集版本与运行、逐题结果快照。
4. 引入 `HIT`、`MISS`、`INVALID_LABEL`、`RETRIEVAL_ERROR`、`SKIPPED`。
5. 校验标注目标是否在 Agent 可检索范围内。

验收：修改文档、Chunk、用例或检索配置后，历史运行详情不变化；文档级目标可以用 Hit@K 得到符合业务直觉的结果。

### 阶段 2：异步任务与运行可靠性

1. 新增运行和任务状态机。
2. 实现数据库任务队列、受控并发、进度、取消和重试。
3. 保存单题耗时、错误码和错误摘要。
4. 运行期间默认绕过实时检索缓存。

验收：1000 条用例可后台运行；页面可观察进度；Provider 故障不会被统计为正常未命中。

### 阶段 3：前端工作台与数据治理

1. 拆分列表、评测集工作台和运行详情页面。
2. 完成集合/用例/标签 CRUD、分页和远程搜索。
3. 支持 CSV/JSON 预校验导入、导出、健康检查。
4. 完成逐题诊断、趋势和基线对比。

验收：运营人员无需查看数据库即可维护数据集、定位退化题目和完成基线比较。

### 阶段 4：完整 RAG 评测

1. 记录最终答案、引用与推理链路摘要。
2. 引入人工评分和 LLM-as-a-Judge 双轨判定。
3. 评估正确性、完整性、引用精确率/召回率和 groundedness。
4. 将基线阈值接入检索配置和模型发布流程。

---

## 十二、测试要求

| 类型 | 覆盖重点 |
| --- | --- |
| 单元测试 | Document/Section/Chunk 指标、多正例、重复召回、无标签、K 截断 |
| 服务测试 | 快照生成、失效标签、范围校验、聚合、状态机、取消和重试 |
| 控制器测试 | 分页、运行创建、进度、结果筛选、对比响应 |
| 前端测试 | 运行配置、进度轮询、状态筛选、逐题诊断、导入预校验 |
| 集成测试 | 固定知识库 fixture 下的检索回归、Provider 故障、索引变更后历史快照稳定性 |

至少建立一个固定、小规模的知识库 fixture 和金标集。测试重点不仅是“指标公式正确”，还应验证“配置变化能够产生可解释的指标变化”。

---

## 十三、与当前实现的关系

当前接口 `/api/knowledge/evaluation/**` 可作为兼容入口逐步演进：

1. 保留现有评测集查询和基础 CRUD。
2. 将 `POST /sets/{id}/run` 演进为创建异步运行，返回 `runId`，不再同步返回完整报告。
3. 将运行详情和逐题结果切换为读取快照。
4. 待前端完成迁移后，废弃无版本、全量返回、同步执行的接口语义。

现有数据库的 `retrieval_config_snapshot` 字段必须在第一阶段启用；其余快照字段通过新的 Flyway 迁移补充。数据库结构以 `api/src/main/resources/db/migration/postgresql/` 的实际迁移为准。
