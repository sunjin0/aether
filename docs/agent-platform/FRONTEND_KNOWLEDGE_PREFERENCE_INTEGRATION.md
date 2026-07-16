# 后台用户偏好与知识库 RAG 前端对接文档

> 日期：2026-07-16  
> 范围：后台用户长期偏好、Agent 知识库、纯文本/Markdown 文档、RAG 聊天增强
>
> 目标读者：前端开发

---

## 1. 前端需要新增什么

本次后端新增四组前端能力，建议按模块拆开维护，不要把知识库全部塞进 Agent 模块，也不要把后台用户偏好放到前台用户模块：

1. 后台用户偏好管理：查看、手动新增、编辑、删除、启用/禁用后台用户长期偏好。
2. 知识库管理：创建平台级或 Agent 专属知识库、编辑知识库、查看索引状态。
3. 知识库文档管理：提交纯文本/Markdown 文档，后端同步分块、Embedding、写入向量库。
4. Agent 知识库绑定：在 Agent 详情页绑定/解绑知识库，决定该 Agent 聊天时实际检索哪些知识库。

聊天接口本身不需要改请求参数。后端会在普通聊天和 SSE 聊天中自动注入：

- 当前后台用户已启用的长期偏好；
- 当前 Agent 已启用且已完成索引的知识库检索结果。

---

## 2. 模块划分与归属

### 2.1 前端模块拆分

建议前端按三个一级业务模块理解：

| 前端模块 | 页面/入口建议 | 管理对象 | 接口前缀 | 说明 |
|---|---|---|---|---|
| 系统管理 / 后台用户偏好 | 系统管理、个人偏好、用户画像 | 后台用户长期偏好 | `/api/sys/admin/preference` | 属于后台登录用户，全局生效，不按 Agent 隔离。 |
| 知识库管理 | 独立「知识库管理」菜单 | 知识库、文档、索引状态 | `/api/knowledge/base`、`/api/knowledge/document` | 知识库本体是独立资源，支持平台级和 Agent 专属两种范围。 |
| Agent 管理 / 知识库 Tab | Agent 详情页下的「知识库」Tab | Agent 与知识库绑定关系 | `/api/agent/knowledge-base-binding` | 只维护绑定、解绑、启用、禁用，不在这里直接管理知识库本体字段。 |

一句话边界：

- `sys/admin/preference` 管“后台用户记忆”。
- `knowledge/base` 和 `knowledge/document` 管“知识库资源本身”。
- `agent/knowledge-base-binding` 管“某个 Agent 使用哪些知识库”。

知识库本体不再强绑定 Agent。平台级知识库和 Agent 专属知识库可以同时存在，通过知识库 `scope` 区分，通过绑定表决定某个 Agent 实际使用哪些知识库。

### 2.2 后端代码、表、实体对应关系

| 业务对象 | Java 包/实体 | 数据表 | Controller | 前端归属 |
|---|---|---|---|---|
| 后台用户偏好 | `com.aether.sys.entity.AdminPreference` | `sys_admin_preference` | `AdminPreferenceController` | 系统管理 / 后台用户偏好 |
| 知识库 | `com.aether.knowledge.entity.KnowledgeBase` | `knowledge_base` | `KnowledgeBaseController` | 知识库管理 |
| 知识库文档 | `com.aether.knowledge.entity.KnowledgeDocument` | `knowledge_document` | `KnowledgeDocumentController` | 知识库管理 |
| 知识库分块/向量 | `com.aether.knowledge.entity.KnowledgeDocumentChunk` | `knowledge_document_chunk` | 无独立前端 CRUD | 后端索引内部结构 |
| Agent 知识库绑定 | `com.aether.agent.entity.AgentKnowledgeBaseBinding` | `agent_knowledge_base_binding` | `AgentKnowledgeBaseBindingController` | Agent 详情 / 知识库 Tab |

后端代码归属与表名保持一致：

- 知识库本体、文档、分块、Embedding 与检索服务位于 `com.aether.knowledge` 包。
- Agent 模块只保留 `AgentKnowledgeBaseBinding` 绑定关系和聊天侧 RAG 注入调用。
- 后台用户偏好位于 `com.aether.sys` 包，字段使用 `adminId/admin_id`，不再使用 `UserPreference/user_preference/userId` 命名。

### 2.3 平台级知识库与 Agent 专属知识库

知识库通过 `scope` 区分范围：

| `scope` | 含义 | 前端入口 | 是否可绑定多个 Agent |
|---|---|---|---|
| `PLATFORM` | 平台级知识库，可被多个 Agent 复用 | 独立知识库管理页 | 可以 |
| `AGENT` | Agent 专属知识库，业务上通常只给特定 Agent 使用 | 可在知识库管理页创建，也可从 Agent 详情页引导创建 | 技术上仍通过绑定关系决定 |

前端实现建议：

- 独立知识库管理页展示所有知识库，可用 `scope` 筛选。
- Agent 详情页只展示当前 Agent 已绑定的知识库，并提供“绑定已有知识库”的选择器。
- 如果后续要支持“在 Agent 详情页新建专属知识库”，本质也是先调用 `/api/knowledge/base` 创建 `scope=AGENT` 的知识库，再调用绑定接口。

### 2.4 数据流边界

```text
后台用户偏好页
└─ /api/sys/admin/preference
   └─ sys_admin_preference

知识库管理页
├─ /api/knowledge/base
│  └─ knowledge_base
└─ /api/knowledge/document
   ├─ knowledge_document
   └─ knowledge_document_chunk（后端自动分块/向量化）

Agent 详情 / 知识库 Tab
└─ /api/agent/knowledge-base-binding
   └─ agent_knowledge_base_binding
      └─ 关联 knowledge_base

聊天页
└─ /api/agent/chat 或 SSE
   ├─ 自动读取 sys_admin_preference
   ├─ 自动读取 agent_knowledge_base_binding
   └─ 自动检索 knowledge_document_chunk
```

---

## 3. 通用约定

### 3.1 认证

所有接口沿用现有认证方式：

```http
Authorization: Bearer <token>
Content-Type: application/json
```

### 3.2 响应结构

接口统一返回 `WebResponse`：

```json
{
  "code": 200,
  "message": "request.success",
  "data": {},
  "total": 0
}
```

分页接口：

```json
{
  "code": 200,
  "message": "request.success",
  "data": [],
  "total": 100
}
```

### 3.3 分页参数

分页接口均使用请求体字段：

| 字段 | 类型 | 说明 |
|---|---|---|
| `current` | Long | 当前页码 |
| `pageSize` | Long | 每页条数 |

### 3.4 权限提示

新增接口使用后端权限路径：

| 模块 | 权限路径 |
|---|---|
| 后台用户偏好 | `/sys/admin/preference` |
| 知识库 | `/knowledge/base` |
| 文档 | `/knowledge/document` |
| Agent 知识库绑定 | `/agent/knowledge-base-binding` |

如果环境未给当前角色配置这些权限，接口会返回 `403`。前端需要按现有权限体系控制菜单/按钮显示。

### 3.5 下拉数据源

前端不要硬编码偏好分类或 Embedding 供应商 ID。

| 下拉项 | 接口 | value | label | 用途 |
|---|---|---|---|---|
| 后台用户偏好分类 | `GET /api/sys/dict/options?parentCode=Admin_Preference_Category&useValue=true` | 字典 `val`，如 `general`、`style` | 字典中文/英文名 | `AdminPreference.category` |
| Embedding 供应商 | `GET /api/agent/model-provider/embedding-options` | `agent_model_provider.id` | 供应商名称，可能包含默认模型 | `KnowledgeBase.embeddingProviderId` |

后台用户偏好分类当前种子数据：

| value | 中文名 | 说明 |
|---|---|---|
| `general` | 通用 | 通用长期偏好 |
| `language` | 语言 | 语言偏好 |
| `style` | 表达风格 | 回答风格偏好 |
| `format` | 输出格式 | 格式与结构偏好 |
| `tech_stack` | 技术栈 | 技术栈偏好 |

Embedding 供应商下拉只返回已启用、未删除、可用于 OpenAI 兼容 Embedding 调用的模型供应商。当前 Embedding 模型固定为 `text-embedding-3-small`，前端只选择供应商 ID，不需要传模型名。

---

## 4. 后台用户偏好接口

### 4.1 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 偏好 ID |
| `adminId` | String | 后台用户 ID；新增时可不传，后端默认当前登录后台用户 |
| `category` | String | 分类；使用 `Admin_Preference_Category` 字典下拉，保存字典 `val` |
| `content` | String | 偏好内容 |
| `sourceConversationId` | String | 自动提取来源会话 ID |
| `sourceMessageId` | String | 自动提取来源消息 ID |
| `confidence` | Number | 自动提取置信度，0-1 |
| `status` | Number | `0` 禁用，`1` 启用 |
| `createdAt` / `updatedAt` | Long | 毫秒时间戳 |

### 4.2 列表

```http
POST /api/sys/admin/preference/list
```

请求：

```json
{
  "current": 1,
  "pageSize": 20,
  "category": "style",
  "content": "中文",
  "status": 1
}
```

说明：

- `adminId` 不传时，后端默认查询当前登录后台用户。
- 管理员页面如需查看指定后台用户偏好，可传 `adminId`。

响应：

```json
{
  "code": 200,
  "message": "request.success",
  "data": [
    {
      "id": "1950000000000000001",
      "adminId": "1945059543981625345",
      "category": "style",
      "content": "用户偏好使用中文回答，并希望回答简洁。",
      "sourceConversationId": "1950000000000000002",
      "sourceMessageId": "1950000000000000003",
      "confidence": 0.85,
      "status": 1,
      "createdAt": 1783769933000,
      "updatedAt": 1783769933000
    }
  ],
  "total": 1
}
```

### 4.3 详情

```http
GET /api/sys/admin/preference/{id}
```

### 4.4 新增

```http
POST /api/sys/admin/preference
```

请求：

```json
{
  "category": "style",
  "content": "用户喜欢先给结论，再给步骤。",
  "confidence": 1.0,
  "status": 1
}
```

响应 `data` 为新建偏好 ID。

### 4.5 编辑

```http
PUT /api/sys/admin/preference/{id}
```

请求：

```json
{
  "category": "style",
  "content": "用户喜欢先给结论，再给必要步骤。",
  "confidence": 1.0,
  "status": 1
}
```

### 4.6 删除

```http
DELETE /api/sys/admin/preference/{id}
```

### 4.7 启用/禁用

```http
PUT /api/sys/admin/preference/{id}/status
```

请求：

```json
{
  "status": 0
}
```

---

## 5. 知识库接口

### 5.1 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 知识库 ID |
| `scope` | String | 知识库范围：`PLATFORM` 平台级，`AGENT` Agent 专属 |
| `embeddingProviderId` | String | Embedding 模型供应商 ID；不传时后端使用默认启用供应商 |
| `name` | String | 知识库名称 |
| `description` | String | 描述 |
| `indexStatus` | Number | `0` 未索引，`1` 索引中，`2` 已索引 |
| `status` | Number | `0` 禁用，`1` 启用 |
| `createdAt` / `updatedAt` | Long | 毫秒时间戳 |

### 5.2 列表

```http
POST /api/knowledge/base/list
```

请求：

```json
{
  "current": 1,
  "pageSize": 20,
  "scope": "PLATFORM",
  "name": "产品文档",
  "status": 1,
  "indexStatus": 2
}
```

### 5.3 详情

```http
GET /api/knowledge/base/{id}
```

### 5.4 新增

```http
POST /api/knowledge/base
```

请求：

```json
{
  "scope": "PLATFORM",
  "embeddingProviderId": "1949000000000000001",
  "name": "产品文档",
  "description": "用于回答产品功能和使用方式的问题",
  "status": 1
}
```

说明：

- `indexStatus` 可不传，默认 `0`。
- `status` 可不传，默认 `1`。
- `embeddingProviderId` 使用 `GET /api/agent/model-provider/embedding-options` 下拉选择；不传时后端使用默认启用供应商。
- 响应 `data` 为知识库 ID。

### 5.5 编辑

```http
PUT /api/knowledge/base/{id}
```

### 5.6 删除

```http
DELETE /api/knowledge/base/{id}
```

---

### 5.7 Agent 知识库绑定

Agent 不再直接拥有知识库本体，而是通过绑定关系选择要参与 RAG 的知识库。

#### 5.7.1 绑定列表

```http
POST /api/agent/knowledge-base-binding/list
```

请求：
```json
{
  "current": 1,
  "pageSize": 20,
  "agentDefinitionId": "1949000000000000001",
  "status": 1
}
```

响应项除绑定自身字段外，会补充：

| 字段 | 类型 | 说明 |
|---|---|---|
| `knowledgeBaseName` | String | 知识库名称 |
| `scope` | String | `PLATFORM` 或 `AGENT` |

#### 5.7.2 新增绑定

```http
POST /api/agent/knowledge-base-binding
```

请求：
```json
{
  "agentDefinitionId": "1949000000000000001",
  "knowledgeBaseId": "1950000000000000100",
  "status": 1
}
```

#### 5.7.3 启用/禁用绑定

```http
PUT /api/agent/knowledge-base-binding/{id}/status
```

请求：
```json
{
  "status": 0
}
```

#### 5.7.4 删除绑定

```http
DELETE /api/agent/knowledge-base-binding/{id}
```

---

## 6. 文档接口

### 6.1 字段说明

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | String | 文档 ID |
| `knowledgeBaseId` | String | 知识库 ID |
| `title` | String | 文档标题 |
| `content` | String | 纯文本或 Markdown 内容 |
| `sourceUrl` | String | 来源 URL，可选 |
| `chunkCount` | Number | 分块数量 |
| `status` | Number | `0` 未处理，`1` 处理中，`2` 已完成 |
| `createdAt` / `updatedAt` | Long | 毫秒时间戳 |

### 6.2 列表

```http
POST /api/knowledge/document/list
```

请求：

```json
{
  "current": 1,
  "pageSize": 20,
  "knowledgeBaseId": "1950000000000000100",
  "title": "安装",
  "status": 2
}
```

### 6.3 详情

```http
GET /api/knowledge/document/{id}
```

### 6.4 新增文档并同步索引

```http
POST /api/knowledge/document
```

请求：

```json
{
  "knowledgeBaseId": "1950000000000000100",
  "title": "快速开始",
  "content": "# 快速开始\n\n1. 安装依赖\n2. 启动服务\n3. 登录后台",
  "sourceUrl": "https://example.com/docs/quick-start"
}
```

响应：

```json
{
  "code": 200,
  "message": "add.success",
  "data": "1950000000000000200",
  "total": 0
}
```

重要说明：

- 当前版本不支持文件上传，只支持纯文本/Markdown。
- 新增文档接口会同步执行分块和 Embedding，接口耗时可能明显长于普通 CRUD。
- 如果 Embedding 服务不可用，接口会失败，前端应展示错误提示。

### 6.5 编辑文档并同步重建索引

```http
PUT /api/knowledge/document/{id}
```

请求：

```json
{
  "knowledgeBaseId": "1950000000000000100",
  "title": "快速开始",
  "content": "# 快速开始\n\n更新后的 Markdown 内容",
  "sourceUrl": "https://example.com/docs/quick-start"
}
```

说明：

- 更新成功后，后端会删除旧分块并同步重建索引。

### 6.6 删除文档

```http
DELETE /api/knowledge/document/{id}
```

说明：

- 删除文档时，后端会同时逻辑删除对应分块。

### 6.7 手动重建索引

```http
POST /api/knowledge/document/{id}/reindex
```

使用场景：

- 文档索引异常；
- 后端 Embedding 配置变更；
- 需要手动刷新分块和向量。

---

## 7. 推荐页面交互

### 7.1 后台用户偏好页

建议入口：系统管理 / 后台用户管理 / 个人偏好 / 用户画像。

页面能力：

- 列表展示：分类、内容、置信度、来源、状态、更新时间。
- 支持按分类、内容、状态筛选。
- 支持手动新增/编辑。
- 支持启用/禁用。
- 支持删除。

前端提示文案建议：

> 系统会在聊天后自动提取长期偏好。你也可以手动维护偏好；启用的偏好会在后续聊天中作为上下文参考。

### 7.2 知识库管理页

建议入口：独立「知识库管理」页面。

页面职责：

- 维护平台级知识库和 Agent 专属知识库。
- 维护知识库下的文档。
- 展示文档分块数量和索引状态。
- 发起文档新增、编辑、删除、重建索引。

页面层级：

```text
知识库管理
└─ 知识库列表
   └─ 文档列表
      └─ 文档新增/编辑抽屉或页面
```

知识库列表建议展示：

- 名称
- 描述
- 状态
- 索引状态
- 文档入口
- 创建/更新时间

文档列表建议展示：

- 标题
- 分块数
- 状态
- 来源 URL
- 更新时间
- 重建索引按钮

### 7.3 Agent 知识库绑定页

建议入口：Agent 详情页「知识库」Tab。

页面职责：

- 展示当前 Agent 已绑定的知识库。
- 绑定已有知识库。
- 解绑知识库。
- 启用/禁用绑定关系。
- 可选：提供“新建 Agent 专属知识库”入口；实现上先创建 `scope=AGENT` 的知识库，再创建绑定关系。

页面层级：

```text
Agent 详情
└─ 知识库 Tab
   ├─ 已绑定知识库列表
   ├─ 绑定已有知识库弹窗
   └─ 可选：新建专属知识库入口
```

Agent 详情页不建议直接编辑知识库文档内容；如果需要编辑，应跳转到独立「知识库管理」页，或复用同一套知识库文档组件。

### 7.4 状态展示建议

知识库 `indexStatus`：

| 值 | 文案 | UI 建议 |
|---|---|---|
| `0` | 未索引 | 灰色 |
| `1` | 索引中 | 蓝色 / loading |
| `2` | 已索引 | 绿色 |

文档 `status`：

| 值 | 文案 | UI 建议 |
|---|---|---|
| `0` | 未处理 | 灰色 |
| `1` | 处理中 | 蓝色 / loading |
| `2` | 已完成 | 绿色 |

通用 `status`：

| 值 | 文案 |
|---|---|
| `0` | 禁用 |
| `1` | 启用 |

---

## 8. 聊天侧对接说明

### 8.1 普通聊天

接口不变：

```http
POST /api/agent/chat
```

前端不需要额外传知识库 ID 或偏好 ID。后端会自动：

1. 根据当前后台用户查询启用的长期偏好；
2. 根据当前 Agent 查询启用且已索引的知识库；
3. 对本轮用户问题做向量检索；
4. 将偏好和知识片段注入模型上下文。

### 8.2 SSE 聊天

接口和事件格式不变。

前端只需要保持原有 SSE 处理逻辑。RAG 命中不会新增独立事件；最终回答会自然体现知识库内容。

### 8.3 自动偏好提取

聊天完成后，后端会异步提取后台用户偏好：

- 不阻塞聊天响应；
- 提取失败不影响聊天；
- 提取成功后，下一轮聊天才会稳定生效；
- 前端如需展示最新偏好，可在聊天完成后延迟刷新后台用户偏好列表。

---

## 9. 错误与降级

| 场景 | 前端表现建议 |
|---|---|
| 无知识库或无命中片段 | 聊天正常，无需提示 |
| 知识库禁用 | 不参与 RAG |
| 文档索引失败 | 新增/编辑/重建接口返回错误，展示失败原因 |
| Embedding 服务不可用 | 文档索引失败，提示检查模型供应商配置 |
| 后台用户偏好提取失败 | 聊天不受影响；偏好列表不新增 |
| 权限不足 | 按现有 `403` 逻辑提示无权限或隐藏入口 |

---

## 10. 前端开发清单

- [ ] 在系统管理或个人设置下增加后台用户偏好页面，接口只调用 `/api/sys/admin/preference`。
- [ ] 后台用户偏好分类下拉调用 `/api/sys/dict/options?parentCode=Admin_Preference_Category&useValue=true`，保存 `value`。
- [ ] 增加独立知识库管理页面，接口调用 `/api/knowledge/base`。
- [ ] 知识库 `embeddingProviderId` 下拉调用 `/api/agent/model-provider/embedding-options`，保存供应商 ID。
- [ ] 在知识库管理页下增加文档 CRUD 和重建索引按钮，接口调用 `/api/knowledge/document`。
- [ ] 在 Agent 详情页增加知识库 Tab，只调用 `/api/agent/knowledge-base-binding` 维护绑定关系。
- [ ] Agent 知识库 Tab 如需新建专属知识库，先调用 `/api/knowledge/base` 创建 `scope=AGENT`，再调用绑定接口。
- [ ] 文档新增/编辑表单使用纯文本/Markdown 输入框。
- [ ] 对文档新增、编辑、重建索引接口增加 loading 状态。
- [ ] 按 `status`、`indexStatus`、`document.status` 做状态展示。
- [ ] 确认当前角色拥有 `/sys/admin/preference`、`/knowledge/base`、`/knowledge/document`、`/agent/knowledge-base-binding` 权限。
- [ ] 聊天侧无需改接口，但可在 Agent 知识库页提示「已索引知识库会自动参与回答」。
