# Agent 平台 — 前端知识库对接

> 合并来源：FRONTEND_ENTERPRISE_KNOWLEDGE_INTEGRATION_V2.md（主）、FRONTEND_ENTERPRISE_KNOWLEDGE_INTEGRATION.md（V1
> 已并入）、FRONTEND_KNOWLEDGE_PREFERENCE_INTEGRATION.md
> 更新日期：2026-08-11
> 注：V1 文档内容已合并至此，V1 不再单独维护。

---

## 一、概述

### 数据库

- PostgreSQL 16 + pgvector
- 文件存储：私有 MinIO

### 功能模块

1. 知识库管理（平台级 / Agent 专属）
2. 文档管理（上传、预览、版本管理、索引）
3. 文档审核（AI 审查 + 人工审批）
4. 异步索引任务
5. 检索评测（评测集 + Recall@K / MRR / NDCG）
6. Agent 知识库绑定
7. 后台用户偏好管理（长期记忆）

### 模型目录配置（V45）

知识库表单不再选择单一“向量供应商”，而是选择能力为 `EMBEDDING` 的模型目录项并保存到 `embeddingModelId`；后端同时回填关联的
`embeddingProviderId` 供索引记录和审计使用。已禁用模型、已禁用供应商或能力不匹配的模型不能保存。

检索配置中的查询重写与 Rerank 分别保存 `queryRewriteModelId`、`rerankModelId`。选择器仅展示 `CHAT/MULTIMODAL`、`RERANK`
能力对应的目录项。运行时再次校验，配置错误时查询重写降级为原始问题、Rerank 降级为融合排序。

AI 审查配置保存 `reviewModelId`，要求模型具有 `CHAT` 或 `MULTIMODAL` 能力；人工审核人仍由 `manualReviewerId` 指定。

### 前端菜单与页面

| 页面              | 路由                        |
|-----------------|---------------------------|
| 知识库管理           | `/knowledge/base`         |
| 知识库详情           | `/knowledge/base/:id`     |
| 文档详情            | `/knowledge/document/:id` |
| 索引任务            | `/knowledge/index-job`    |
| Agent 详情知识库 Tab | `/agent/:id`              |
| 聊天页             | `/chat`                   |

---

## 二、知识库管理

### 接口

| 功能 | 方法     | 路径                         |
|----|--------|----------------------------|
| 列表 | POST   | `/api/knowledge/base/list` |
| 详情 | GET    | `/api/knowledge/base/{id}` |
| 创建 | POST   | `/api/knowledge/base`      |
| 编辑 | PUT    | `/api/knowledge/base/{id}` |
| 删除 | DELETE | `/api/knowledge/base/{id}` |

### 字段

`scope`（PLATFORM / AGENT）、`embeddingProviderId`、`name`、`description`、`indexStatus`、`status`

---

## 三、文档管理

### 接口

| 功能     | 方法     | 路径                                                       |
|--------|--------|----------------------------------------------------------|
| 文档列表   | POST   | `/api/knowledge/document/list`                           |
| 文档详情   | GET    | `/api/knowledge/document/{id}`                           |
| 创建文档   | POST   | `/api/knowledge/document`                                |
| 更新文档   | PUT    | `/api/knowledge/document/{id}`                           |
| 删除文档   | DELETE | `/api/knowledge/document/{id}`                           |
| 文件上传   | POST   | `/api/knowledge/document/upload`                         |
| 批量上传   | POST   | `/api/knowledge/document/upload/batch`                   |
| 预览 URL | GET    | `/api/knowledge/document/{id}/preview-url`               |
| 版本列表   | GET    | `/api/knowledge/document/{id}/versions`                  |
| 版本详情   | GET    | `/api/knowledge/document/version/{versionId}`            |
| 分块列表   | GET    | `/api/knowledge/document/version/{versionId}/chunk/list` |
| 更新草稿   | PUT    | `/api/knowledge/document/version/{versionId}/draft`      |
| 回滚版本   | POST   | `/api/knowledge/document/version/{versionId}/rollback`   |
| 重新索引   | POST   | `/api/knowledge/document/{id}/reindex`                   |

### 文件上传

- **Content-Type**: `multipart/form-data`
- **支持格式**: txt、md、pdf、docx、xlsx
- **最大大小**: 50 MB

### 预览接口

- `GET /api/knowledge/document/{id}/preview-url`
- 返回 10 分钟有效期的临时 URL

### 创建文档

支持纯文本和 Markdown 两种格式直接提交。

---

## 四、异步索引任务

| 功能   | 方法   | 路径                                    |
|------|------|---------------------------------------|
| 任务列表 | POST | `/api/knowledge/index-job/list`       |
| 任务详情 | GET  | `/api/knowledge/index-job/{id}`       |
| 重试   | POST | `/api/knowledge/index-job/{id}/retry` |

### 状态

`pending` → `running` → `success` / `failed` / `cancelled`

---

## 四·A、文档审核（AI 审查 + 人工审批）

| 功能          | 方法   | 路径                                                                                    |
|-------------|------|---------------------------------------------------------------------------------------|
| 发起 AI 审查    | POST | `/api/knowledge/document/version/{versionId}/ai-review`                               |
| 提交审批        | POST | `/api/knowledge/document/version/{versionId}/submit`                                  |
| 审查任务列表      | POST | `/api/knowledge/review-task/list`                                                     |
| 任务详情        | GET  | `/api/knowledge/review-task/{id}`                                                     |
| 领取任务        | POST | `/api/knowledge/review-task/{id}/claim`                                               |
| 通过/驳回       | POST | `/api/knowledge/review-task/{id}/approve` / `reject`                                  |
| AI 审查详情/问题  | GET  | `/api/knowledge/ai-review/{id}`、`/{id}/issues`、`/{id}/diff`                           |
| 采纳/撤销/忽略建议  | POST | `/api/knowledge/ai-review/{reviewId}/issues/{issueId}/accept` / `unaccept` / `reject` |
| 批量采纳 / 统一应用 | POST | `/api/knowledge/ai-review/{reviewId}/issues/accept-batch` / `apply`                   |

状态流转与 Diff 工作台详见《08-知识库AI审查》。

---

## 四·B、检索评测

| 功能          | 方法                  | 路径                                                                                            |
|-------------|---------------------|-----------------------------------------------------------------------------------------------|
| 评测集管理       | GET/POST/PUT/DELETE | `/api/knowledge/evaluation/sets...`                                                           |
| 评测用例管理      | GET/POST            | `/api/knowledge/evaluation/sets/{id}/cases`                                                   |
| 多正例标签       | GET/POST/DELETE     | `/api/knowledge/evaluation/sets/{setId}/cases/{caseId}/labels...`                             |
| 发布/查询评测集版本  | POST/GET            | `/api/knowledge/evaluation/sets/{id}/versions`                                                |
| 数据集健康检查     | GET                 | `/api/knowledge/evaluation/sets/{id}/health`                                                  |
| 更新/删除用例     | PUT/DELETE          | `/api/knowledge/evaluation/sets/{setId}/cases/{caseId}`                                       |
| 批量启停用例      | POST                | `/api/knowledge/evaluation/sets/{id}/cases/batch-status`                                      |
| 导出草稿用例      | GET                 | `/api/knowledge/evaluation/sets/{id}/cases/export`                                            |
| 导入预校验       | POST                | `/api/knowledge/evaluation/sets/{id}/cases/import/preview`                                    |
| 确认导入草稿      | POST                | `/api/knowledge/evaluation/sets/{id}/cases/import`                                            |
| 兼容同步运行      | POST                | `/api/knowledge/evaluation/sets/{id}/run`                                                     |
| 创建异步运行      | POST                | `/api/knowledge/evaluation/sets/{id}/runs`                                                    |
| 运行进度        | GET                 | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/progress`                                   |
| 取消 / 重试失败任务 | POST                | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/cancel`、`.../retry-failed`                  |
| 运行记录 / 逐题结果 | GET                 | `/api/knowledge/evaluation/sets/{id}/runs`、`.../runs/{runId}/results`                         |
| 设置基线        | POST                | `/api/knowledge/evaluation/sets/{id}/runs/{runId}/baseline`                                   |
| 趋势 / 两次运行对比 | GET                 | `/api/knowledge/evaluation/sets/{id}/trend`、`.../runs/compare?baselineRunId=&candidateRunId=` |
| 可标注文档/章节    | GET                 | `/api/knowledge/evaluation/documents`、`/documents/{id}/sections`                              |

异步运行接口立即返回 `runId`，可选请求参数 `evaluationSetVersionId` 指定已发布数据集版本。前端在状态为 `RUNNING` 时每 3
秒轮询进度接口，页面失焦或离开后停止轮询。进度返回 `queued`、`running`、`succeeded`、`failed`、`invalid`、`cancelled` 和
`finished`；检索异常与无效标注不混入正常命中率。

一个用例可添加多个正例标签，但同一用例的标签必须具有相同 `targetType`（`DOCUMENT`、`SECTION` 或 `CHUNK`
）。发布版本会冻结当前启用用例和标签，供审计及后续按版本运行使用。

每个评测集只能设置一个已完成运行作为基线。对比响应提供总体 Recall@K、MRR、nDCG 的差值及逐题差异；不同发布版本或不同数据集快照的运行返回
`comparable=false`，前端应展示差异但不得将其作为严格回归结论。

运行记录的 `retrievalConfigSnapshot` 冻结创建时的 Agent 知识库绑定、平台知识库范围、知识库检索配置、索引状态以及 embedding
Provider 的标识、地址、默认模型和状态。快照不包含 Provider API Key。

在发布当前草稿版本或以当前草稿发起异步评测前，前端应请求数据集健康检查。空问题、没有可解析目标、标签粒度混用、目标文档不可用或不在
Agent 的当前有效检索范围内均为阻塞错误；存在阻塞错误时，后端会拒绝发布或创建草稿运行。已发布版本可继续运行，以保持冻结数据集的可追溯性。

批量启停请求体为 `{ "caseIds": ["..."], "status": 0 | 1 }`。用例编辑、删除和启停只影响当前草稿，已发布版本和既有运行的快照不会变化。

导入导出使用 JSON 数组，每项为
`{ "item": { "question": "...", "documentId": "...", "targetType": "DOCUMENT" }, "labels": [] }`。`labels` 为空时使用
`item` 的兼容目标字段；非空时使用多正例标签。提交导入前必须调用预校验，校验会返回行号及空问题、标签字段、文档可用性、Agent
检索范围、章节或 Chunk 解析失败等问题。只有预校验通过，确认导入接口才会写入草稿。

---

## 五、Agent 知识库绑定

### 接口

| 功能   | 方法     | 路径                                              |
|------|--------|-------------------------------------------------|
| 绑定列表 | POST   | `/api/agent/knowledge-base-binding/list`        |
| 绑定创建 | POST   | `/api/agent/knowledge-base-binding`             |
| 状态更新 | PUT    | `/api/agent/knowledge-base-binding/{id}/status` |
| 删除绑定 | DELETE | `/api/agent/knowledge-base-binding/{id}`        |

### 聊天侧约定

无需新增知识库相关字段。后端自动使用当前 Agent 启用的绑定知识库进行 RAG 检索。无命中时降级为普通聊天。

---

## 六、后台用户偏好管理

### 接口

| 功能    | 方法     | 路径                                  |
|-------|--------|-------------------------------------|
| 列表    | POST   | `/api/sys/preference/list`          |
| 详情    | GET    | `/api/sys/preference/{id}`          |
| 创建    | POST   | `/api/sys/preference`               |
| 编辑    | PUT    | `/api/sys/preference/{id}`          |
| 删除    | DELETE | `/api/sys/preference/{id}`          |
| 启用/禁用 | PUT    | `/api/sys/preference/{id}/status`   |
| 确认偏好  | POST   | `/api/sys/preference/{id}/feedback` |
| 拒绝偏好  | DELETE | `/api/sys/preference/{id}/feedback` |
| 覆盖偏好值 | PUT    | `/api/sys/preference/{id}/override` |
| 偏好统计  | GET    | `/api/sys/preference/statistics`    |

### 字段

`id`、`adminId`、`category`、`content`、`sourceConversationId`、`sourceMessageId`、`confidence`、`status`

### 菜单位置

系统管理 → 后台用户偏好

---

## 七、Embedding 供应商

`GET /api/agent/model-provider/embedding-options` — 获取 Embedding 供应商下拉选项。

---

## 八、聊天侧集成

聊天页无需修改。后端自动注入：

1. 用户启用的长期偏好
2. 当前 Agent 绑定知识库的 RAG 检索结果
