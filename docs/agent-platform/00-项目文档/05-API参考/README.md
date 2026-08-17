# Aether API 接口参考

> 来源：`admin` 模块 REST 控制器；更新日期：2026-08-11
> 通用前缀：`/api`；通用响应包装：`WebResponse<T>`（`code`/`message`/`data`/`total`）。
> 鉴权：请求头 `Authorization: Bearer {token}`；权限由 `@Permission` 注解控制（见 §11）。

---

## 1. 通用约定

### 1.1 响应包装

```json
{ "code": 200, "message": "success", "data": { ... }, "total": 0 }
```

- 常规 JSON 管理接口的业务结果以响应体 `code` 为准：`200` 成功；`500` 业务/系统失败；`400` 参数错误；`401` 未授权/令牌过期；
  `403` 无权限；`429` 触发额度。`GlobalFilter` 捕获的异常可能仍以 HTTP 200 返回。
- 文件流等直接返回 `ResponseEntity` 的接口例外，调用方仍应同时检查 HTTP 状态和响应体。

### 1.2 分页

列表接口一般接受 `current`、`pageSize`（部分为 `page`/`size`），分页响应的 `data` 直接为记录数组，`total` 位于响应根级。

### 1.3 权限注解语义

- `@Permission(required=false)`：无需资源权限（仅需登录）。
- 类级 `@Permission(path="/xxx")`：整类读权限。
- 方法级 `@Permission(path="/xxx", type=Write)`：写操作。

---

## 2. 系统 / 认证

### 2.1 LoginController — `/api/sys`

| 方法   | 路径                       | 权限             | 说明                               |
|------|--------------------------|----------------|----------------------------------|
| POST | `/api/sys/verify`        | 免              | 验证账号密码                           |
| POST | `/api/sys/login`         | 免              | 账号/邮箱登录，返回 token + permissionMap |
| POST | `/api/sys/resetPassword` | 类 `/sys/admin` | 重置密码                             |
| GET  | `/api/sys/info`          | 类 `/sys/admin` | 登录用户信息                           |
| GET  | `/api/sys/getRouters`    | 类 `/sys/admin` | 用户路由树                            |
| POST | `/api/sys/send`          | 免              | 发送验证码                            |
| GET  | `/api/sys/logout`        | 类 `/sys/admin` | 退出登录                             |

### 2.2 UserController — `/api/sys/admin`

| 方法     | 路径                       | 权限    | 说明      |
|--------|--------------------------|-------|---------|
| POST   | `/api/sys/admin/list`    | 类     | 管理员列表   |
| GET    | `/api/sys/admin/options` | 免     | 管理员下拉选项 |
| GET    | `/api/sys/admin/info`    | 类     | 管理员详情   |
| POST   | `/api/sys/admin/add`     | Write | 新增管理员   |
| POST   | `/api/sys/admin/update`  | Write | 修改管理员   |
| DELETE | `/api/sys/admin/delete`  | Write | 删除管理员   |

### 2.3 ServiceAccountController — 绝对路径

| 方法     | 路径                                                  | 权限                           | 说明                        |
|--------|-----------------------------------------------------|------------------------------|---------------------------|
| POST   | `/api/auth/service-account/token`                   | 免                            | client credentials 签发访问令牌 |
| POST   | `/api/sys/service-account/list`                     | `/sys/service-account`       | 服务账号列表                    |
| POST   | `/api/sys/service-account`                          | `/sys/service-account` Write | 创建（明文密钥仅本次返回）             |
| PUT    | `/api/sys/service-account/{id}`                     | Write                        | 编辑（clientId/密钥不可改）        |
| POST   | `/api/sys/service-account/{id}/rotate-secret`       | Write                        | 轮换密钥，旧令牌失效                |
| POST   | `/api/sys/service-account/{id}/enabled?enabled=true | false`                       | Write                     | 启用/禁用（`enabled` 必填 query 参数） |
| DELETE | `/api/sys/service-account/{id}`                     | Write                        | 删除                        |

### 2.4 角色 / 资源 / 字典 / 配置

| 方法   | 路径                                                      | 权限              | 说明       |
|------|---------------------------------------------------------|-----------------|----------|
| POST | `/api/sys/role/list`                                    | `/sys/role`     | 角色列表     |
| GET  | `/api/sys/role/info` `/options` `/resource`             | 类               | 详情/下拉/资源 |
| POST | `/api/sys/role/add` `/update`                           | Write           | 新增/修改角色  |
| GET  | `/api/sys/role/delete`                                  | Write           | 删除角色     |
| GET  | `/api/sys/role-resource/permission`                     | `/sys/role`     | 按角色查权限资源 |
| POST | `/api/sys/role-resource/save`                           | Write           | 保存角色权限   |
| POST | `/api/sys/resource/list`                                | `/sys/resource` | 资源列表     |
| GET  | `/api/sys/resource/info` `/select`                      | 类               | 详情/下拉    |
| POST | `/api/sys/resource/add` `/update`                       | Write           | 新增/修改资源  |
| GET  | `/api/sys/resource/delete`                              | Write           | 删除资源     |
| POST | `/api/sys/dict/list`                                    | `/sys/dict`     | 字典列表     |
| GET  | `/api/sys/dict/info` `/select` `/code`(免) `/options`(免) | 类/免             | 字典查询     |
| POST | `/api/sys/dict/add` `/update`                           | Write           | 新增/修改字典  |
| GET  | `/api/sys/dict/delete`                                  | Write           | 删除字典     |
| POST | `/api/sys/config/list`                                  | `/sys/config`   | 配置列表     |
| GET  | `/api/sys/config/info`                                  | 类               | 配置详情     |
| POST | `/api/sys/config/add` `/update`                         | Write           | 新增/修改配置  |
| GET  | `/api/sys/config/delete`                                | Write           | 删除配置     |

### 2.5 AdminPreferenceController — `/api/sys/preference`

| 方法     | 路径                                  | 权限    | 说明     |
|--------|-------------------------------------|-------|--------|
| POST   | `/api/sys/preference/list`          | 类     | 用户偏好列表 |
| GET    | `/api/sys/preference/{id}`          | 类     | 偏好详情   |
| POST   | `/api/sys/preference`               | Write | 新增偏好   |
| PUT    | `/api/sys/preference/{id}`          | Write | 编辑偏好   |
| DELETE | `/api/sys/preference/{id}`          | Write | 删除偏好   |
| PUT    | `/api/sys/preference/{id}/status`   | Write | 启用/禁用  |
| POST   | `/api/sys/preference/{id}/feedback` | Write | 确认偏好   |
| DELETE | `/api/sys/preference/{id}/feedback` | Write | 拒绝偏好   |
| PUT    | `/api/sys/preference/{id}/override` | Write | 覆盖偏好值  |
| GET    | `/api/sys/preference/statistics`    | 类     | 偏好统计   |

---

## 3. 成员 / 消息 / 文件

### 3.1 MemberController — `/api/user/member`

`list`(Read)、`info`(Read)、`add`/`update`/`delete`(Write)。

### 3.2 EmailController — `/api/msg/email`、SmsController — `/api/msg/sms`

`list`(类)、`info`(类)、`save`(Write)、`delete`(Write)。

### 3.3 FileController — `/api/file`（公开，无需资源权限）

| 方法   | 路径                        | 说明     |
|------|---------------------------|--------|
| POST | `/api/file/upload`        | 上传文件   |
| GET  | `/api/file/preview`       | 预览文件   |
| GET  | `/api/file/download`      | 下载文件   |
| GET  | `/api/file/chat/preview`  | 预览聊天附件 |
| GET  | `/api/file/chat/download` | 下载聊天附件 |

---

## 4. Agent 平台

### 4.1 AgentDefinitionController — `/api/agent/definition`

| 方法     | 路径                                      | 权限    | 说明       |
|--------|-----------------------------------------|-------|----------|
| POST   | `/api/agent/definition/list`            | 类     | Agent 列表 |
| GET    | `/api/agent/definition/options`         | 免     | 下拉选项     |
| GET    | `/api/agent/definition/{id}`            | 类     | 详情       |
| POST   | `/api/agent/definition`                 | Write | 新增       |
| PUT    | `/api/agent/definition/{id}`            | Write | 编辑       |
| DELETE | `/api/agent/definition/{id}`            | Write | 删除       |
| PUT    | `/api/agent/definition/{id}/status`     | Write | 启用/禁用    |
| POST   | `/api/agent/definition/{id}/copy`       | Write | 复制       |
| GET    | `/api/agent/definition/model/providers` | 类     | 模型供应商列表  |

### 4.1.1 ModelProviderController — `/api/agent/model-provider`

| 方法                  | 路径                               | 权限    | 说明                                     |
|---------------------|----------------------------------|-------|----------------------------------------|
| POST                | `/list`                          | Read  | 供应商分页查询                                |
| GET                 | `/{id}`                          | Read  | 供应商详情；不返回 API Key                      |
| POST / PUT / DELETE | `/`、`/{id}`                      | Write | 供应商维护                                  |
| POST                | `/{id}/test`                     | Read  | 连通性诊断，返回 `success`、`elapsedMs`、`error` |
| GET                 | `/models`                        | Read  | 模型目录；可用 `providerId` 过滤                |
| GET                 | `/models/options?capability=...` | Read  | 按能力获取可用模型目录选项                          |
| GET                 | `/{id}/models/discover`          | Read  | 从供应商读取模型候选                             |
| POST                | `/models`、`/models/batch`        | Write | 单条或事务批量创建目录项                           |
| PUT / DELETE        | `/models/{id}`                   | Write | 修改或删除目录项                               |
| PUT                 | `/models/{id}/status`            | Write | 启停目录项                                  |

目录写入会校验供应商已启用、模型名称和能力非空，且能力属于受支持集合；批量接口任一项失败时整体回滚。

### 4.2 工具绑定 / 知识库绑定

| 方法     | 路径                                                        | 权限                       | 说明          |
|--------|-----------------------------------------------------------|--------------------------|-------------|
| GET    | `/api/agent/definition/{agentId}/tools`                   | `/agent/tool`            | 查询工具绑定      |
| POST   | `/api/agent/definition/{agentId}/tools`                   | `/agent/definition` Read | 绑定工具（当前实现）  |
| DELETE | `/api/agent/definition/{agentId}/tools/{toolId}`          | `/agent/definition` Read | 解绑工具（当前实现）  |
| PUT    | `/api/agent/definition/{agentId}/tools/{toolId}/priority` | `/agent/definition` Read | 调整优先级（当前实现） |
| POST   | `/api/agent/knowledge-base-binding/list`                  | `/agent/definition`      | 绑定列表        |
| POST   | `/api/agent/knowledge-base-binding`                       | Write                    | 创建绑定        |
| PUT    | `/api/agent/knowledge-base-binding/{id}/status`           | Write                    | 启用/禁用       |
| DELETE | `/api/agent/knowledge-base-binding/{id}`                  | Write                    | 删除绑定        |

### 4.3 AgentChatController — `/api/agent/chat`

| 方法   | 路径                                           | 说明                         |
|------|----------------------------------------------|----------------------------|
| POST | `/api/agent/chat`                            | 非流式聊天（已弃用）                 |
| POST | `/api/agent/chat/attachment`                 | 上传并识别聊天附件                  |
| POST | `/api/agent/chat/stream`                     | **SSE 流式聊天**（普通/Deep 统一入口） |
| POST | `/api/agent/chat/conversation/list`          | 会话列表                       |
| GET  | `/api/agent/chat/conversation/{id}/messages` | 查询会话消息                     |

**SSE 事件（普通 Agent）：** `message`、`reasoning`、`tool_call`、`question`、`done`、`error`；每 15s 心跳注释。
**SSE 事件（Deep Agent）：** `accepted`、`run_step`、`question`、`done`、`error`。

### 4.4 AgentConversationController — `/api/agent/conversation`

| 方法     | 路径                                                            | 权限    | 说明                                          |
|--------|---------------------------------------------------------------|-------|---------------------------------------------|
| POST   | `/api/agent/conversation/list`                                | 类     | 会话列表                                        |
| GET    | `/api/agent/conversation/{id}`                                | 类     | 会话详情                                        |
| GET    | `/api/agent/conversation/{id}/messages?current=1&pageSize=20` | 类     | 会话消息；当前实现始终聚合工具调用日志，`includeToolCalls` 参数无效 |
| PUT    | `/api/agent/conversation/{id}/close`                          | Write | 关闭会话                                        |
| DELETE | `/api/agent/conversation/{id}`                                | Write | 删除会话                                        |
| GET    | `/api/agent/conversation/{id}/lifecycle`                      | 类     | 会话生命周期                                      |
| GET    | `/api/agent/conversation/{id}/statistics`                     | 类     | 会话消息统计                                      |

### 4.5 AgentRunController — `/api/agent/run`

| 方法   | 路径                           | 权限    | 说明            |
|------|------------------------------|-------|---------------|
| POST | `/api/agent/run/list`        | 类     | 运行记录列表        |
| GET  | `/api/agent/run/{id}`        | 类     | 运行详情          |
| GET  | `/api/agent/run/statistics`  | 类     | 运行统计          |
| GET  | `/api/agent/run/{id}/steps`  | 类     | 运行步骤（Deep 事件） |
| POST | `/api/agent/run/{id}/cancel` | Write | 取消 Deep 运行    |

### 4.6 工具 / MCP / 调用日志

| 方法     | 路径                                        | 权限                     | 说明           |
|--------|-------------------------------------------|------------------------|--------------|
| POST   | `/api/agent/tool/list`                    | `/agent/tool`          | 工具列表         |
| GET    | `/api/agent/tool/options`                 | 免                      | 下拉选项         |
| GET    | `/api/agent/tool/facets`                  | 类                      | 工具中心筛选聚合     |
| GET    | `/api/agent/tool/statistics`              | 类                      | 工具统计         |
| GET    | `/api/agent/tool/{id}`                    | 类                      | 工具详情         |
| POST   | `/api/agent/tool`                         | Write                  | 新增工具         |
| PUT    | `/api/agent/tool/{id}`                    | Write                  | 编辑工具         |
| DELETE | `/api/agent/tool/{id}`                    | Write                  | 删除工具         |
| POST   | `/api/agent/tool/{id}/test`               | Write                  | 测试工具         |
| GET    | `/api/agent/tool/user/info`               | 免                      | 模拟用户信息（mock） |
| POST   | `/api/agent/mcp-server/list`              | `/agent/mcp-server`    | MCP 服务列表     |
| GET    | `/api/agent/mcp-server/options`           | 免                      | 下拉选项         |
| GET    | `/api/agent/mcp-server/{id}`              | 类                      | 详情           |
| POST   | `/api/agent/mcp-server`                   | Write                  | 新增           |
| PUT    | `/api/agent/mcp-server/{id}`              | Write                  | 编辑           |
| DELETE | `/api/agent/mcp-server/{id}`              | Write                  | 删除           |
| POST   | `/api/agent/mcp-server/{id}/tools`        | Write                  | 发现 MCP 工具    |
| POST   | `/api/agent/mcp-server/{id}/import-tools` | Write                  | 导入工具         |
| POST   | `/api/agent/tool-call-log/list`           | `/agent/tool-call-log` | 工具调用日志列表     |
| GET    | `/api/agent/tool-call-log/{id}`           | 类                      | 工具调用日志详情     |

### 4.7 ModelProviderController — `/api/agent/model-provider`

| 方法     | 路径                                            | 权限    | 说明            |
|--------|-----------------------------------------------|-------|---------------|
| POST   | `/api/agent/model-provider/list`              | 类     | 供应商列表         |
| GET    | `/api/agent/model-provider/options`           | 免     | 下拉选项          |
| GET    | `/api/agent/model-provider/embedding-options` | 类     | Embedding 供应商 |
| GET    | `/api/agent/model-provider/{id}`              | 类     | 详情            |
| POST   | `/api/agent/model-provider`                   | Write | 新增            |
| PUT    | `/api/agent/model-provider/{id}`              | Write | 编辑            |
| DELETE | `/api/agent/model-provider/{id}`              | Write | 删除            |
| PUT    | `/api/agent/model-provider/{id}/status`       | Write | 启用/禁用         |
| POST   | `/api/agent/model-provider/{id}/test`         | Write | 测试连接          |

### 4.8 Deep Agent 回调与流（公开但受签名/归属校验）

| 方法   | 路径                                      | 说明                       |
|------|-----------------------------------------|--------------------------|
| POST | `/api/agent/deep-runs/callback/{runId}` | Deep Agent 事件回调（HMAC 验签） |
| GET  | `/api/agent/deep-runs/{runId}/stream`   | Deep 运行事件重放（SSE，归属校验）    |

### 4.9 Skill 技能管理（V38）

#### AgentSkillController — `/api/agent/skill`（类 `/agent/skill` 读）

| 方法   | 路径                                                   | 权限    | 说明                                                                           |
|------|------------------------------------------------------|-------|------------------------------------------------------------------------------|
| POST | `/api/agent/skill/list`                              | 类     | 分页查询；`name/code/category` 模糊、`status` 精确筛选                                   |
| GET  | `/api/agent/skill/{id}`                              | 类     | 详情：`skill`、`draft`（可空）、`currentVersion`、`tools`、`knowledgeBases`、`resources` |
| POST | `/api/agent/skill`                                   | Write | 创建 Skill 主记录并生成可编辑草稿                                                         |
| PUT  | `/api/agent/skill/{id}`                              | Write | 编辑草稿基本信息与依赖声明                                                                |
| POST | `/api/agent/skill/{id}/draft`                        | Write | 基于最新发布版本创建下一草稿（每 Skill 最多一个草稿）                                               |
| GET  | `/api/agent/skill/{id}/versions`                     | 类     | 版本历史（含草稿）                                                                    |
| POST | `/api/agent/skill/{id}/versions/{versionId}/publish` | Write | 发布版本；仅允许发布当前草稿                                                               |
| PUT  | `/api/agent/skill/{id}/status`                       | Write | 启用/停用（body `status`：`1` 启用、`2` 停用）                                           |

> 设计草案中的 `POST /{id}/resources`（资源上传）、`GET /{id}/resources`、`POST /{id}/preview`
> （预览合成提示词）一期暂未实现，发布与预览预算校验在服务端完成。

#### AgentDefinitionSkillBindingController — `/api/agent/definition`（类 `/agent/definition` 读）

| 方法     | 路径                                                   | 权限    | 说明                    |
|--------|------------------------------------------------------|-------|-----------------------|
| GET    | `/api/agent/definition/{agentId}/skills`             | 类     | 查询 Agent 已安装 Skill 绑定 |
| POST   | `/api/agent/definition/{agentId}/skills`             | Write | 安装已发布版本（body 见下）      |
| PUT    | `/api/agent/definition/{agentId}/skills/{bindingId}` | Write | 调整优先级、启停、升级版本         |
| DELETE | `/api/agent/definition/{agentId}/skills/{bindingId}` | Write | 卸载 Skill              |

安装 body（`AgentSkillInstallDto`）：`skillVersionId`（必填）、`priority`（默认 0）、`status`、`configOverrides`。
更新 body（`AgentSkillBindingUpdateDto`）：`skillVersionId`、`priority`、`status`、`configOverrides` 均可选。

#### 聊天 DTO 增量字段（V38）

`AgentChatDto` 新增可选字段：

```java
private Map<String, Map<String, Object>> skillInputs; // key = 已安装 Skill 的 code
```

外层 key 必须是该 Agent 已安装 Skill 的 `code`，内层按对应版本 `input_schema` 校验；未传 `skillInputs` 时仍自动装配该
Agent 全部启用 Skill。请求不得携带 `skillIds` 或选择/跳过字段。

---

## 5. 知识库

### 5.1 KnowledgeBaseController — `/api/knowledge/base`

`list`(类)、`options`(免)、`{id}`(类)、`POST /`(Write)、`PUT /{id}`(Write)、`DELETE /{id}`(Write)。

### 5.2 KnowledgeDocumentController — `/api/knowledge/document`

| 方法     | 路径                                                               | 权限    | 说明       |
|--------|------------------------------------------------------------------|-------|----------|
| POST   | `/api/knowledge/document/list`                                   | 类     | 文档列表     |
| GET    | `/api/knowledge/document/{id}`                                   | 类     | 文档详情     |
| POST   | `/api/knowledge/document`                                        | Write | 新增并同步索引  |
| POST   | `/api/knowledge/document/upload`                                 | Write | 上传文档     |
| POST   | `/api/knowledge/document/upload/batch`                           | Write | 批量上传     |
| GET    | `/api/knowledge/document/{id}/preview-url`                       | 类     | 预览临时 URL |
| GET    | `/api/knowledge/document/{id}/versions`                          | 类     | 版本列表     |
| GET    | `/api/knowledge/document/version/{versionId}`                    | 类     | 版本详情     |
| GET    | `/api/knowledge/document/version/{versionId}/chunk/list`         | 类     | 版本分块列表   |
| POST   | `/api/knowledge/document/version/{versionId}/rollback` `/revise` | Write | 回滚/修订    |
| PUT    | `/api/knowledge/document/{id}`                                   | Write | 更新文档     |
| DELETE | `/api/knowledge/document/{id}`                                   | Write | 删除文档     |
| POST   | `/api/knowledge/document/{id}/reindex`                           | Write | 重建索引     |
| PUT    | `/api/knowledge/document/version/{versionId}/draft`              | Write | 更新草稿     |
| POST   | `/api/knowledge/document/version/{versionId}/ai-review`          | Write | 发起 AI 审查 |
| POST   | `/api/knowledge/document/version/{versionId}/submit`             | Write | 提交审批     |

### 5.3 审核 / AI 审查 / 索引 / 评测

| 方法              | 路径                                                                                        | 权限                      | 说明         |
|-----------------|-------------------------------------------------------------------------------------------|-------------------------|------------|
| POST            | `/api/knowledge/review-task/list`                                                         | `/knowledge/document`   | 审查任务列表     |
| GET             | `/api/knowledge/review-task/{id}`                                                         | 类                       | 任务详情       |
| POST            | `/api/knowledge/review-task/{id}/claim`                                                   | Write                   | 领取任务       |
| POST            | `/api/knowledge/review-task/{id}/approve` `/reject`                                       | Write                   | 通过/驳回      |
| PUT             | `/api/knowledge/review-task/{id}/edit`                                                    | Write                   | 编辑审核内容     |
| GET             | `/api/knowledge/ai-review/{id}` `/version/{versionId}/latest` `/{id}/issues` `/{id}/diff` | `/knowledge/document`   | AI 审查查询    |
| POST            | `/api/knowledge/ai-review/{reviewId}/issues/{issueId}/accept` `/unaccept` `/reject`       | Write                   | 采纳/撤销/忽略建议 |
| POST            | `/api/knowledge/ai-review/{reviewId}/issues/accept-batch` `/apply`                        | Write                   | 批量采纳/统一应用  |
| PUT             | `/api/knowledge/ai-review/issue/{issueId}/handle`                                         | Write                   | 处理问题       |
| POST            | `/api/knowledge/index-job/list`                                                           | `/knowledge/document`   | 索引任务列表     |
| GET             | `/api/knowledge/index-job/{id}`                                                           | 类                       | 索引任务详情     |
| POST            | `/api/knowledge/index-job/{id}/retry`                                                     | Write                   | 重试索引       |
| GET             | `/api/knowledge/evaluation/sets`                                                          | `/knowledge/evaluation` | 评测集列表      |
| POST/PUT/DELETE | `/api/knowledge/evaluation/sets...`                                                       | Write                   | 评测集管理      |
| GET/POST        | `/api/knowledge/evaluation/sets/{id}/cases`                                               | 类/Write                 | 评测用例管理     |
| POST            | `/api/knowledge/evaluation/sets/{id}/run`                                                 | Write                   | 运行评测       |
| GET             | `/api/knowledge/evaluation/sets/{id}/runs`                                                | 类                       | 运行记录       |
| GET             | `/api/knowledge/evaluation/sets/{setId}/runs/{runId}/results`                             | 类                       | 逐题结果       |
| GET             | `/api/knowledge/evaluation/documents` `/documents/{id}/sections`                          | 免                       | 可标注文档/章节   |
| POST            | `/api/knowledge/evaluation/run`                                                           | `/knowledge/base` Write | 批量评测       |

---

## 6. 工作台 / 工作流

### 6.1 WorkbenchController — `/api/workbench`

`GET /api/workbench/overview`（`/dashboard`）：工作台聚合概览（待办/运行中/需关注/快捷工作流）。

### 6.2 AgentWorkflowController — `/api/agent/workflow`

| 方法     | 路径                                                                                    | 权限                     | 说明              |
|--------|---------------------------------------------------------------------------------------|------------------------|-----------------|
| POST   | `/api/agent/workflow/list`                                                            | `/workflow/workflow`   | 工作流列表           |
| GET    | `/api/agent/workflow/{id}`                                                            | 类                      | 详情              |
| POST   | `/api/agent/workflow`                                                                 | Write                  | 创建草稿            |
| PUT    | `/api/agent/workflow/{id}`                                                            | Write                  | 保存画布草稿          |
| POST   | `/api/agent/workflow/{id}/publish`                                                    | Write                  | 发布版本            |
| POST   | `/api/agent/workflow/{id}/offline`                                                    | Write                  | 下线              |
| GET    | `/api/agent/workflow/{id}/versions` `/versions/diff` `/export`                        | 类                      | 版本/对比/导出        |
| POST   | `/api/agent/workflow/import`                                                          | Write                  | 导入为新草稿          |
| POST   | `/api/agent/workflow/{id}/templates`                                                  | Write                  | 从工作流创建模板        |
| POST   | `/api/agent/workflow/templates/list` `/templates/{id}/instantiate`                    | 类/Write                | 模板              |
| POST   | `/api/agent/workflow/{id}/draft/validate`                                             | Write                  | 校验草稿            |
| DELETE | `/api/agent/workflow/{id}`                                                            | Write                  | 删除工作流           |
| POST   | `/api/agent/workflow/{id}/instances`                                                  | `/workflow/run` Write  | 手动启动实例          |
| POST   | `/api/agent/workflow/{id}/business-instances`                                         | Write                  | 业务系统启动          |
| POST   | `/api/agent/workflow/webhooks` `/webhooks/list` `/{id}/rotate-secret` `/{id}/enabled` | `/workflow/workflow`   | Webhook 管理      |
| POST   | `/api/agent/workflow/webhook/{id}`                                                    | 免                      | 接收外部 Webhook 事件 |
| GET    | `/api/agent/workflow/operations/metrics` `/dead-letters`                              | `/workflow/operations` | 运营指标/死信         |
| POST   | `/api/agent/workflow/instances/list`                                                  | `/workflow/run`        | 实例列表            |
| GET    | `/api/agent/workflow/instances/{id}` `/callbacks`                                     | 类                      | 实例/回调审计         |
| POST   | `/api/agent/workflow/instances/{id}/callbacks/{deliveryId}/retry`                     | Write                  | 重投回调            |
| GET    | `/api/agent/workflow/instances/{id}/events`                                           | 类                      | 实例实时事件（SSE）     |
| POST   | `/api/agent/workflow/instances/{id}/answer` `/retry` `/replay` `/terminate`           | Write                  | 人工操作            |
| PUT    | `/api/agent/workflow/instances/{id}/variables`                                        | Write                  | 运行中改变量          |

### 6.3 AgentWorkflowScheduleController — `/api/agent/workflow/schedules`

`POST /`(Write)、`POST /list`(类)、`PUT /{id}`(Write)、`POST /{id}/enabled?enabled=true|false`(Write)、`DELETE /{id}`(Write)
：定时任务管理。

---

## 7. SSE 事件汇总

| 场景      | 事件                                                                                                                       | 说明                                                 |
|---------|--------------------------------------------------------------------------------------------------------------------------|----------------------------------------------------|
| 普通聊天    | `message` / `reasoning` / `tool_call` / `question` / `done` / `error`                                                    | 文本、推理、工具、提问卡片、结束/错误                                |
| Deep 运行 | `accepted` / `run_step` / `question` / `done` / `error`                                                                  | 接受创建、事件步骤、提问、结束/错误                                 |
| Deep 重放 | `run_step`                                                                                                               | `GET /api/agent/deep-runs/{runId}/stream` 重放已持久化事件 |
| 工作流实例   | `run.completed` / `run.failed` / `node.completed` / `ask_user.required` / `tool.approval.required` / `variables.updated` | `GET /api/agent/workflow/instances/{id}/events`    |

---

## 8. Deep Agent 回调事件（入站）

`POST /api/agent/deep-runs/callback/{runId}`（HMAC 验签，见架构文档）支持：
`message.delta`、`run.started`、`run.completed`、`run.failed`、`run.cancelled`、`tool.started`、`tool.completed`、
`tool.failed`、`tool.approval.required`、`ask_user.required`。

---

## 9. 工作流对外回调（出站）

终态后向业务系统投递，事件：`workflow.completed` / `workflow.failed` / `workflow.terminated` / `workflow.timed_out`
。验签与重试见[工作流业务集成](../06-工作流业务集成/README.md)。

---

## 10. 公开端点汇总（无资源权限）

- `FileController`：`/api/file/**`
- `DeepAgentCallbackController`：`POST /api/agent/deep-runs/callback/{runId}`（HMAC）
- `DeepRunStreamController`：`GET /api/agent/deep-runs/{runId}/stream`（归属校验）
- `@Permission(required=false)`：登录/验证码、各类 `options`、`dict/code`、`dict/options`、Webhook 接收、评测标注文档等。

---

## 11. 权限路径总表

| 权限路径                                                                                                                                                                 | 模块       |
|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------|
| `/sys/admin`                                                                                                                                                         | 管理员/登录   |
| `/sys/service-account`                                                                                                                                               | 服务账号     |
| `/sys/role` `/sys/resource` `/sys/dict` `/sys/config` `/sys/preference`                                                                                              | 系统管理     |
| `/user/member`                                                                                                                                                       | 前端成员     |
| `/msg/email` `/msg/sms`                                                                                                                                              | 消息       |
| `/agent/definition` `/agent/tool` `/agent/run` `/agent/chat` `/agent/conversation` `/agent/tool-call-log` `/agent/mcp-server` `/agent/model-provider` `/agent/skill` | Agent 平台 |
| `/knowledge/base` `/knowledge/document` `/knowledge/evaluation`                                                                                                      | 知识库      |
| `/dashboard`                                                                                                                                                         | 工作台      |
| `/workflow/workflow` `/workflow/run` `/workflow/operations` `/workflow/schedule`                                                                                     | 工作流      |
