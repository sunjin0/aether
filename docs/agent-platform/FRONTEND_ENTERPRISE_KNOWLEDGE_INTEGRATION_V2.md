# 知识库前端对接文档

> 基于当前后端 Controller 生成。本文仅描述已发布接口；没有接口的能力不应在前端实现为可操作功能。

## 1. 前端菜单与页面划分

```text
知识库管理
├─ 知识库列表                 /knowledge/base
├─ 知识库详情                 /knowledge/base/:id
│  └─ 文档列表、文本新建、文件上传
├─ 文档详情                   /knowledge/document/:id
│  └─ 元数据、版本历史、预览、重建索引
└─ 索引任务                   /knowledge/index-job
   └─ 全部/失败任务、任务重试

Agent 管理
└─ Agent 详情                 /agent/:id
   └─ 知识库 Tab：绑定、启停、解绑

聊天
└─ 聊天页                     /chat
   └─ 后端自动 RAG，不传知识库参数
```

职责边界：

| 页面 | 负责 | 不负责 |
|---|---|---|
| 知识库列表 | 创建、编辑、启用/禁用知识库 | Agent 绑定 |
| 知识库详情 | 文档列表、文本创建、文件上传 | 直接访问 MinIO |
| 文档详情 | 版本、预览、索引状态、重建 | 编辑 Agent 绑定 |
| 索引任务 | 监控和重试任务 | 文档正文编辑 |
| Agent 知识库 Tab | 绑定已有知识库、启停、解绑 | 创建或删除知识库 |
| 聊天页 | 聊天与后续引用展示 | 配置向量、Embedding、存储 |

前端内部建议：

```text
modules/knowledge/       # 知识库、文档、版本、索引任务 API 和页面
modules/agent/           # Agent 详情中的 KnowledgeBaseBindingTab
modules/chat/            # 聊天/RAG 状态提示
shared/api/modelProvider # Embedding 供应商下拉缓存
```

MinIO 是后端独立 `storage` 模块，前端不能保存或使用 MinIO 密钥、endpoint；文件预览只能调用后端返回的临时 URL。

## 2. 通用协议

```http
Authorization: Bearer <token>
Content-Type: application/json
```

响应结构：

```json
{ "code": 200, "message": "request.success", "data": {}, "total": 0 }
```

分页请求使用 `current`、`pageSize`。`/api/knowledge/index-job/list` 当前固定查询第 1 页、最多 50 条，前端不传分页参数。

## 3. Embedding 供应商下拉

该接口已发布，未移除：

```http
GET /api/agent/model-provider/embedding-options
```

返回 `Option` 列表；使用返回项的 `value` 保存到知识库 `embeddingProviderId`。前端不传 Embedding 模型名。

## 4. 知识库接口

| 方法 | 地址 | 用途 |
|---|---|---|
| POST | `/api/knowledge/base/list` | 分页列表 |
| GET | `/api/knowledge/base/{id}` | 详情 |
| POST | `/api/knowledge/base` | 创建 |
| PUT | `/api/knowledge/base/{id}` | 编辑 |
| DELETE | `/api/knowledge/base/{id}` | 逻辑删除 |

列表请求示例：

```json
{ "current": 1, "pageSize": 20, "scope": "PLATFORM", "name": "产品", "status": 1 }
```

创建/编辑字段：

| 字段 | 说明 |
|---|---|
| `name` | 知识库名称 |
| `scope` | `PLATFORM` 或 `AGENT`；省略时后端默认 `PLATFORM` |
| `embeddingProviderId` | 供应商下拉 `value` |
| `description` | 描述 |
| `ownerAdminId` | 后台归属用户 ID |
| `visibility` | `platform`、`private`、`shared` |
| `retrievalConfig` | JSON 字符串；当前只保存，不启用混合检索/重排 |
| `status` | `0` 禁用、`1` 启用 |

响应还包含 `indexStatus`、`referenceCount`、`lastReferencedAt`。

## 5. 文档接口

| 方法 | 地址 | 用途 |
|---|---|---|
| POST | `/api/knowledge/document/list` | 文档分页列表 |
| GET | `/api/knowledge/document/{id}` | 文档详情 |
| POST | `/api/knowledge/document` | 创建文本/Markdown 文档 |
| PUT | `/api/knowledge/document/{id}` | 更新文本内容并入队索引 |
| DELETE | `/api/knowledge/document/{id}` | 删除文档和分块 |
| POST | `/api/knowledge/document/upload` | 上传文件 |
| GET | `/api/knowledge/document/{id}/preview-url` | 获取预览临时 URL |
| GET | `/api/knowledge/document/{id}/versions` | 版本列表 |
| GET | `/api/knowledge/document/version/{versionId}/chunk/list` | 指定版本的分块列表 |
| POST | `/api/knowledge/document/version/{versionId}/rollback` | 回滚并新建索引任务 |
| POST | `/api/knowledge/document/{id}/reindex` | 重建索引 |

### 5.1 文本创建

```json
{
  "knowledgeBaseId": "kb-id",
  "title": "退款规则",
  "content": "# 退款规则\n……",
  "sourceUrl": "https://example.com/rules"
}
```

创建接口的 `data` 为文档 ID。创建、更新、回滚和重建索引都会异步创建版本及任务；接口返回成功不等于已能检索。

### 5.2 文件上传

```http
POST /api/knowledge/document/upload
Content-Type: multipart/form-data
```

| 表单字段 | 必填 | 说明 |
|---|---:|---|
| `knowledgeBaseId` | 是 | 所属知识库 ID |
| `file` | 是 | `txt`、`md`、`pdf`、`docx`；最大 50 MB |
| `title` | 否 | 默认原文件名 |

上传成功的 `data` 为索引任务 ID。

### 5.3 文档字段与状态

文档列表/详情返回：`sourceType`、`originalFileName`、`fileExtension`、`mimeType`、`fileSize`、`fileChecksum`、`currentVersionNo`、`chunkCount`、`indexStatus`、`indexErrorMessage`、`indexedAt`、`referenceCount`、`lastReferencedAt`。

| `indexStatus` | 前端展示 |
|---:|---|
| 0 | 未索引 |
| 1 | 索引中 |
| 2 | 已完成，可用于检索 |
| 3 | 索引失败，显示错误并允许重试 |

预览接口 `data` 是有效期 10 分钟的 URL；不要持久化或缓存为永久地址。

## 6. 版本与索引任务

版本列表中的每项包括：`id`、`versionNo`、`content`、`indexStatus`、`indexErrorMessage`、`indexedAt`、`chunkCount`、文件对象信息。

查看版本分块：

```http
GET /api/knowledge/document/version/{versionId}/chunk/list
```

响应 `data` 按 `chunkNo` 升序返回，至少包含：

```json
{
  "id": "chunk-id",
  "chunkNo": 0,
  "content": "退款规则正文……",
  "tokenCount": 128,
  "createdAt": 1780000000000
}
```

接口不会返回 `embedding`，前端无需也不得展示向量数据。

版本与分块采用一对多快照关系：每个 `knowledge_document_version` 保留自己的分块。新版本索引期间，当前已发布版本仍可检索；只有新版本索引成功后，后端才切换 `currentVersionNo`。因此版本子行可随时调用上述接口查看其历史分块，索引失败版本不会替换线上检索内容。

索引任务接口：

| 方法 | 地址 | 用途 |
|---|---|---|
| POST | `/api/knowledge/index-job/list` | 查询最近 50 条任务 |
| GET | `/api/knowledge/index-job/{id}` | 查询任务详情 |
| POST | `/api/knowledge/index-job/{id}/retry` | 人工重新入队 |

任务列表筛选：

```json
{ "documentId": "doc-id", "status": "failed" }
```

| 状态 | 含义 | 前端动作 |
|---|---|---|
| `pending` | 等待执行/等待自动重试 | 每 3 秒轮询 |
| `running` | 解析、切块或向量化中 | 显示处理中 |
| `success` | 已完成 | 刷新文档详情 |
| `failed` | 达到最大重试次数 | 展示 `errorMessage`，显示“重试” |
| `cancelled` | 预留状态 | 仅展示 |

`reindex` 接口目前将任务 ID 写入响应 `message`，而不是 `data`；前端重建后应刷新该文档的任务列表，而不是依赖响应体解析任务 ID。

## 7. Agent 知识库绑定

| 方法 | 地址 | 用途 |
|---|---|---|
| POST | `/api/agent/knowledge-base-binding/list` | 绑定列表 |
| POST | `/api/agent/knowledge-base-binding` | 新建绑定 |
| PUT | `/api/agent/knowledge-base-binding/{id}/status` | 启用/停用 |
| DELETE | `/api/agent/knowledge-base-binding/{id}` | 解绑 |

新建绑定：

```json
{ "agentDefinitionId": "agent-id", "knowledgeBaseId": "kb-id", "status": 1 }
```

绑定列表会补充 `knowledgeBaseName` 与 `scope`。绑定页使用 `/agent/definition` 权限，而不是 `/agent/knowledge-base-binding` 权限。

## 8. 聊天页边界

聊天请求无需新增知识库字段。后端自动使用当前 Agent 启用绑定的知识库，并叠加启用且索引完成的 `PLATFORM` 知识库；无命中或检索异常时降级普通聊天。

当前不提供结构化引用响应，也不提供 `retrieval_start`、`retrieval_done`、`citation` SSE 事件。`agent_message.citations` 虽已存在，但前端暂不应把它作为已发布契约。

## 9. 权限与错误处理

| 权限路径 | 前端范围 |
|---|---|
| `/knowledge/base` | 知识库维护 |
| `/knowledge/document` | 文档、上传、版本、索引任务维护 |
| `/agent/definition` | Agent 知识库绑定 |

- `403`：按现有权限体系隐藏菜单/写操作。
- `404`：文档、版本、任务或文件不存在。
- `422`：文件格式、大小或解析失败。
- `503`：MinIO 未配置或不可用，提示文件存储服务暂不可用。
