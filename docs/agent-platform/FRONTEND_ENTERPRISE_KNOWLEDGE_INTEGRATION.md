# 企业知识库前端对接文档

> 适用范围：知识库管理、文件上传、异步索引、文档预览、Agent 知识库绑定。  
> 数据库：PostgreSQL + pgvector；文件：私有 MinIO。  
> 前端不得直接连接 MinIO，所有对象操作均经后端接口完成。

## 1. 模块边界

| 前端模块 | 职责 | 后端前缀 |
|---|---|---|
| 知识库管理 | 知识库、文档、上传、索引任务 | `/api/knowledge/**` |
| Agent 管理 | 给 Agent 绑定或停用知识库 | `/api/agent/knowledge-base-binding/**` |
| 聊天页 | 显示已有聊天内容；RAG 由后端自动注入 | 现有聊天接口 |
| 存储模块 | 后端内部 MinIO 适配，不对前端暴露 | 无 |

知识库页面不要放入 Agent 页面：知识库可作为平台资源被多个 Agent 使用。Agent 详情页只管理“绑定关系”。

## 2. 通用约定

所有请求使用：

```http
Authorization: Bearer <token>
```

统一响应：

```json
{ "code": 200, "message": "request.success", "data": {}, "total": 0 }
```

分页请求使用 `current`、`pageSize`；当前索引任务列表服务端固定返回最近 50 条记录。

## 3. 知识库页面

接口沿用：

```text
POST /api/knowledge/base/list
GET  /api/knowledge/base/{id}
POST /api/knowledge/base
PUT  /api/knowledge/base/{id}
DELETE /api/knowledge/base/{id}
```

创建/编辑字段：

| 字段 | 必填 | 说明 |
|---|---:|---|
| `name` | 是 | 知识库名称 |
| `scope` | 否 | `PLATFORM` 或 `AGENT`，默认 `PLATFORM` |
| `embeddingProviderId` | 否 | Embedding 供应商 ID |
| `description` | 否 | 描述 |
| `visibility` | 否 | `platform`、`private`、`shared`；当前用于数据与权限扩展预留 |
| `retrievalConfig` | 否 | JSON 配置预留；首期无需填写 |
| `status` | 否 | `0` 停用、`1` 启用 |

Embedding 供应商下拉：

```http
GET /api/agent/model-provider/embedding-options
```

## 4. 文档管理与上传

### 文档 CRUD

```text
POST /api/knowledge/document/list
GET  /api/knowledge/document/{id}
POST /api/knowledge/document
PUT  /api/knowledge/document/{id}
DELETE /api/knowledge/document/{id}
POST /api/knowledge/document/{id}/reindex
```

纯文本/Markdown 创建示例：

```json
{
  "knowledgeBaseId": "kb-id",
  "title": "售后规则",
  "content": "# 退款规则\n……",
  "sourceUrl": "https://example.com/rule"
}
```

保存、更新、重建均会立即返回；后端会创建版本和异步索引任务。不要把“接口返回成功”当作“已可检索”。

### 文件上传

```http
POST /api/knowledge/document/upload
Content-Type: multipart/form-data
```

| 表单字段 | 必填 | 说明 |
|---|---:|---|
| `knowledgeBaseId` | 是 | 所属知识库 ID |
| `file` | 是 | `txt`、`md`、`pdf`、`docx`，最大 50 MB |
| `title` | 否 | 为空时使用原文件名 |

响应 `data` 是索引任务 ID：

```json
{ "code": 200, "message": "upload accepted", "data": "index-job-id" }
```

### 预览

```http
GET /api/knowledge/document/{id}/preview-url
```

响应 `data` 为 10 分钟有效的 MinIO 预签名 GET URL。前端只用于新窗口、内嵌预览或下载；不得持久化该 URL。

## 5. 异步索引任务

```text
POST /api/knowledge/index-job/list
GET  /api/knowledge/index-job/{id}
POST /api/knowledge/index-job/{id}/retry
```

列表筛选请求：

```json
{ "documentId": "doc-id", "status": "failed" }
```

任务状态：

| `status` | 前端展示 |
|---|---|
| `pending` | 排队中 |
| `running` | 正在解析、分块或向量化 |
| `success` | 已完成，可参与检索 |
| `failed` | 失败；显示 `errorMessage` 并提供“重试” |
| `cancelled` | 已取消 |

建议每 3 秒轮询文档对应的任务；页面离开后停止轮询。文档列表中的 `indexStatus`/`status` 也可作为概览状态：`0` 未索引、`1` 索引中、`2` 已完成。

## 6. Agent 知识库绑定

```text
POST /api/agent/knowledge-base-binding/list
GET  /api/agent/knowledge-base-binding/{id}
POST /api/agent/knowledge-base-binding
PUT  /api/agent/knowledge-base-binding/{id}
DELETE /api/agent/knowledge-base-binding/{id}
```

绑定请求：

```json
{ "agentDefinitionId": "agent-id", "knowledgeBaseId": "kb-id", "status": 1 }
```

Agent 聊天会叠加：当前 Agent 已启用绑定的知识库，以及已启用的 `PLATFORM` 范围知识库。前端应在绑定选择器中过滤禁用和未完成索引的知识库。

## 7. 聊天页约定

当前后端会在聊天前自动执行 RAG 上下文增强，聊天请求无需传知识库参数。无命中、Embedding 失败或 pgvector 异常时会自动降级为普通聊天。

当前已持久化 `agent_message.citations` 字段，但 `retrieval_start`、`retrieval_done`、`citation` SSE 事件和结构化引用响应尚未发布。前端暂时不要依赖这些事件或字段；发布后应按单独的 SSE 变更文档接入“引用来源”面板。

## 8. 前端页面建议

1. 独立“知识库管理”菜单：列表、创建/编辑、Embedding 供应商选择。
2. 知识库详情页：文档表格显示标题、类型、文件大小、分块数、索引状态、错误信息、引用次数、最后引用时间。
3. 上传弹窗：限制可选扩展名和 50 MB；上传成功后跳转或定位到对应索引任务。
4. 文档详情抽屉：显示版本、索引任务、预览按钮；失败任务提供重试。
5. Agent 详情“知识库”标签：维护绑定，不复制知识库 CRUD。

## 9. 权限与错误处理

| 权限路径 | 用途 |
|---|---|
| `/knowledge/base` | 知识库读写 |
| `/knowledge/document` | 文档、上传、索引任务读写 |
| `/agent/knowledge-base-binding` | Agent 绑定管理 |

- `403`：隐藏写按钮或提示无权限。
- `422`：文件类型、大小或解析内容不符合要求。
- `503`：MinIO 未配置或不可用；提示“文件存储服务暂不可用”。
- 索引失败不应删除文档；前端展示错误并允许重试。
