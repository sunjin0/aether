# Agent 平台 — 前端知识库对接

> 合并来源：FRONTEND_ENTERPRISE_KNOWLEDGE_INTEGRATION_V2.md（主）、FRONTEND_ENTERPRISE_KNOWLEDGE_INTEGRATION.md（V1 已并入）、FRONTEND_KNOWLEDGE_PREFERENCE_INTEGRATION.md
> 更新日期：2026-07-20
> 注：V1 文档内容已合并至此，V1 不再单独维护。

---

## 一、概述

### 数据库
- PostgreSQL 16 + pgvector
- 文件存储：私有 MinIO

### 功能模块
1. 知识库管理（平台级 / Agent 专属）
2. 文档管理（上传、预览、版本管理、索引）
3. 异步索引任务
4. Agent 知识库绑定
5. 后台用户偏好管理（长期记忆）

### 前端菜单与页面

| 页面 | 路由 |
|------|------|
| 知识库管理 | `/knowledge/base` |
| 知识库详情 | `/knowledge/base/:id` |
| 文档详情 | `/knowledge/document/:id` |
| 索引任务 | `/knowledge/index-job` |
| Agent 详情知识库 Tab | `/agent/:id` |
| 聊天页 | `/chat` |

---

## 二、知识库管理

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表 | POST | `/api/knowledge/base/list` |
| 详情 | GET | `/api/knowledge/base/{id}` |
| 创建 | POST | `/api/knowledge/base` |
| 编辑 | PUT | `/api/knowledge/base/{id}` |
| 删除 | DELETE | `/api/knowledge/base/{id}` |

### 字段

`scope`（PLATFORM / AGENT）、`embeddingProviderId`、`name`、`description`、`indexStatus`、`status`

---

## 三、文档管理

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 文档列表 | POST | `/api/knowledge/document/list` |
| 文档详情 | GET | `/api/knowledge/document/{id}` |
| 创建文档 | POST | `/api/knowledge/document` |
| 更新文档 | PUT | `/api/knowledge/document/{id}` |
| 删除文档 | DELETE | `/api/knowledge/document/{id}` |
| 文件上传 | POST | `/api/knowledge/document/upload` |
| 预览 URL | GET | `/api/knowledge/document/{id}/preview-url` |
| 版本列表 | GET | `/api/knowledge/document/{id}/versions` |
| 分块列表 | GET | `/api/knowledge/document/{id}/chunk/list` |
| 回滚版本 | POST | `/api/knowledge/document/{id}/rollback` |
| 重新索引 | POST | `/api/knowledge/document/{id}/reindex` |

### 文件上传

- **Content-Type**: `multipart/form-data`
- **支持格式**: txt、md、pdf、docx
- **最大大小**: 50 MB

### 预览接口

- `GET /api/knowledge/document/{id}/preview-url`
- 返回 10 分钟有效期的临时 URL

### 创建文档

支持纯文本和 Markdown 两种格式直接提交。

---

## 四、异步索引任务

| 功能 | 方法 | 路径 |
|------|------|------|
| 任务列表 | POST | `/api/knowledge/index-job/list` |
| 任务详情 | GET | `/api/knowledge/index-job/{id}` |
| 重试 | POST | `/api/knowledge/index-job/{id}/retry` |

### 状态

`pending` → `running` → `success` / `failed` / `cancelled`

---

## 五、Agent 知识库绑定

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 绑定列表 | POST | `/api/agent/knowledge-base-binding/list` |
| 绑定创建 | POST | `/api/agent/knowledge-base-binding` |
| 状态更新 | PUT | `/api/agent/knowledge-base-binding/{id}/status` |
| 删除绑定 | DELETE | `/api/agent/knowledge-base-binding/{id}` |

### 聊天侧约定

无需新增知识库相关字段。后端自动使用当前 Agent 启用的绑定知识库进行 RAG 检索。无命中时降级为普通聊天。

---

## 六、后台用户偏好管理

### 接口

| 功能 | 方法 | 路径 |
|------|------|------|
| 列表 | POST | `/api/sys/admin/preference/list` |
| 详情 | GET | `/api/sys/admin/preference/{id}` |
| 创建 | POST | `/api/sys/admin/preference` |
| 编辑 | PUT | `/api/sys/admin/preference/{id}` |
| 删除 | DELETE | `/api/sys/admin/preference/{id}` |
| 启用/禁用 | PUT | `/api/sys/admin/preference/{id}/status` |

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
