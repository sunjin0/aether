# Agent 平台 — 前端通用对接：文件接口

> 原文档：FRONTEND_FILE_API_INTEGRATION.md

---

## 通用文件接口

### 接口概览

| 功能   | 方法   | 地址                   | 权限       |
|------|------|----------------------|----------|
| 上传文件 | POST | `/api/file/upload`   | 公开，无资源权限 |
| 预览文件 | GET  | `/api/file/preview`  | 公开，无资源权限 |
| 下载文件 | GET  | `/api/file/download` | 公开，无资源权限 |

### 文件上传

- **Content-Type**: `multipart/form-data`
- **参数**: `file`（必填）
- **响应**:

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "objectKey": "uuid-filename.pdf",
    "fileName": "report.pdf",
    "contentType": "application/pdf",
    "size": 1024000,
    "previewUrl": "/api/file/preview?objectKey=...",
    "downloadUrl": "/api/file/download?objectKey=..."
  }
}
```

### 文件预览

- GET 请求，返回文件二进制流
- 当前 `FileController` 不强制 `Authorization` 或 `/file` 资源权限；如部署层另有限制，以部署配置为准。
- 可直接使用 URL，或按前端统一错误处理策略先获取 Blob 再生成 URL。

```javascript
// 前端预览示例
const response = await fetch(`/api/file/preview?objectKey=${key}`, {
  headers: { Authorization: `Bearer ${token}` } // 可选：同源登录态或网关要求时携带
});
const blob = await response.blob();
const url = URL.createObjectURL(blob);
```

### 文件下载

- GET 请求，返回 `Content-Disposition: attachment`
- 当前实现不强制 `Authorization` 请求头

### 错误处理

预览/下载使用 `responseType: 'blob'` 时，错误 JSON 也可能被包装为 Blob。统一拦截器需按 `Content-Type` 区分：

- `application/json` → 解析 JSON 错误信息
- `application/octet-stream` → 正常文件内容

---

## 智能体技能（Skill）前端对接

> 对应后端 V38 模块；前端仓库 `aether-dashboard`，接口封装见 `src/services/agent/SkillController.ts`，类型见
`src/services/entity/Agent.ts`。

### 权限与路由

- 路由 `/agent/skill`（技能管理）挂 `Agent 平台` 菜单下，后端 seed 已创建 `perm_agent_skill_read` /
  `perm_agent_skill_write` 并授权 `root`。
- 前端按既有模式控制写入口：`const write = permissionMap[history.location.pathname]`，读权限页面默认可见，写按钮/操作仅在
  `write` 时渲染。

### 页面与入口

| 页面            | 路径/入口                                    | 说明                                                                                                         |
|---------------|------------------------------------------|------------------------------------------------------------------------------------------------------------|
| 技能管理          | `/agent/skill`（`src/pages/agent/skill/`） | ProTable 列表：名称/编码/分类/状态/当前版本；操作列含编辑草稿、续建草稿、发布、版本、详情、启停                                                     |
| SkillForm     | `SkillForm.tsx`                          | 草稿新建/编辑：基本信息 + instruction / inputSchema / outputSchema / toolPolicy + 工具依赖 Form.List + 知识库多选 + changeNote |
| SkillDetail   | `SkillDetail.tsx`                        | 详情抽屉：基本信息、草稿/当前版本、工具表、知识库表、资源表                                                                             |
| SkillVersions | `SkillVersions.tsx`                      | 版本列表抽屉：版本号/状态/变更说明/发布时间                                                                                    |
| Agent 安装入口    | `/agent/definition` 行操作「技能」              | 打开 `AgentSkillBinding.tsx` 管理已安装技能                                                                         |

### 关键交互流程

1. **新建技能**：`POST /api/agent/skill`（`createSkillDraft`）创建主记录 + 草稿 → 打开表单编辑。
2. **编辑草稿**：`GET /api/agent/skill/{id}`（`getSkillDetail`）查详情；`draft` 为空时先 `POST /{id}/draft`（
   `createNextSkillDraft`）续建，再 `PUT /{id}`（`updateSkillDraft`）保存。
3. **发布**：取详情中 `draft.id` → `POST /{id}/versions/{draftId}/publish`（`publishSkill`）；仅当前草稿可发布。
4. **续建草稿**：`POST /{id}/draft` 基于最新发布版本复制新草稿。
5. **启停**：`PUT /{id}/status`（`updateSkillStatus`），`status` 在 `1`（启用）/ `2`（停用）间切换。
6. **Agent 安装/卸载**：`GET /api/agent/definition/{agentId}/skills`（`getAgentSkillBindings`）加载已装技能并补名称/版本号；安装弹窗先选技能（仅
   `status=1`）再选已发布版本（`getSkillVersions` 过滤 `status=1`）→ `POST /{agentId}/skills`（`installSkillToAgent`，body
   `{ skillVersionId, priority, status }`）；行操作支持启停（`updateSkillBinding`）、优先级调整、卸载（
   `uninstallSkillFromAgent`）。

### 业务规则（前端需遵循）

- 只能安装**已发布（版本 `status=1`）且技能 `status=1` 启用**的版本；技能停用后禁止新装配。
- Agent 绑定固定具体版本，不自动跟随最新；「升级」通过重新选择版本 `updateSkillBinding` 完成。
- 每个 Skill 同时最多一个草稿；发布后已发布版本不可修改。
- 列表状态筛选用 `valueEnum` 静态枚举（`0 草稿 / 1 启用 / 2 停用`），不依赖字典。

### i18n

文案统一走 `useIntl().formatMessage`，key 前缀 `pages.agent.skill.*`，双语文案在 `src/locales/zh-CN.ts` 与 `en-US.ts`（含
`pages.agent.tool.priority` 复用优先级文案）。
