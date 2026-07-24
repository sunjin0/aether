# 后台用户偏好系统重构 - 前端对接方案

> 日期：2026-07-18
> 范围：后台用户偏好从 `category+content` 模型重构为支持动态推理、隐式学习、衰减机制的结构化偏好系统
> 目标读者：前端开发

---

## 1. 本次前端需要改什么

后端对 `sys_admin_preference` 表进行了完全重构，从简单的 `category + content` 模型升级为结构化偏好系统。前端需要：

1. 移除 `Admin_Preference_Category` 字典下拉调用，改用前端硬编码常量
2. 更新列表页字段展示（`content` → `value` + `keyName` + `description`）
3. 新增/编辑表单增加新字段（`priority`、`scope`、`decayRate` 等）
4. 操作列增加「确认」「拒绝」「覆盖」按钮
5. 新增筛选条件（`keyName`、`scope`）

聊天接口不变，后端自动注入偏好到模型上下文。

---

## 2. 字段变更对照

### 2.1 列表字段变化

| 旧字段 | 新字段 | 说明 |
|--------|--------|------|
| `content` | `value` + `description` | 偏好内容拆分为值和描述 |
| — | `keyName` | 偏好键名（如 `output_length`、`code_language`） |
| — | `priority` | 优先级 0-100，默认 50 |
| — | `scope` | 作用域：`global`/`session`/`task_type` |
| — | `scopeDetail` | 当 `scope=task_type` 时的具体任务类型 |
| `sourceConversationId` | `source` | 来源：`explicit`(手动)/`implicit`(自动学习) |
| `sourceMessageId` | — | 移至事件日志表，前端不再展示 |
| — | `confidence` | 置信度 0.00-1.00（自动学习时变化） |
| — | `usageCount` | 使用次数 |
| — | `lastUsedAt` | 最后使用时间戳 |
| — | `expiresAt` | 过期时间戳，NULL=永不过期 |
| — | `decayRate` | 每日衰减率，0=不衰减 |
| — | `effectiveScore` | 当前有效分数（自动计算） |
| `status` | `status` | 保留：0=禁用，1=启用 |
| `createdAt` / `updatedAt` | `createdAt` / `updatedAt` | 保留，毫秒时间戳 |

### 2.2 下拉数据源变化

| 下拉项 | 旧接口 | 新方式 |
|--------|--------|--------|
| 偏好分类 | `GET /api/sys/dict/options?parentCode=Admin_Preference_Category&useValue=true` | **已废弃**，改用前端硬编码常量 |
| 作用域 | — | 前端硬编码常量 |

**分类常量映射：**

| value | 中文名 | 说明 |
|-------|--------|------|
| `language` | 语言 | 语言偏好 |
| `style` | 表达风格 | 回答风格偏好 |
| `format` | 输出格式 | 格式与结构偏好 |
| `tech_stack` | 技术栈 | 技术栈偏好 |
| `tool_strategy` | 工具策略 | 工具使用策略偏好 |

**作用域常量映射：**

| value | 中文名 | 说明 |
|-------|--------|------|
| `global` | 全局 | 全部场景生效 |
| `session` | 会话 | 当前会话生效 |
| `task_type` | 任务类型 | 仅特定任务类型生效，需配合 `scopeDetail` |

---

## 3. 接口变更

### 3.1 列表接口

```http
POST /api/sys/admin/preference/list
```

**请求**（新增 `keyName`、`scope` 筛选）：

```json
{
  "current": 1,
  "pageSize": 20,
  "category": "style",
  "keyName": "output_length",
  "value": "简洁",
  "status": 1
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `current` | Long | 是 | 当前页码 |
| `pageSize` | Long | 是 | 每页条数 |
| `adminId` | String | 否 | 不传时默认当前登录用户 |
| `category` | String | 否 | 分类筛选 |
| `keyName` | String | 否 | 键名模糊搜索 |
| `value` | String | 否 | 偏好值模糊搜索 |
| `status` | Integer | 否 | 状态筛选 |

**响应**：

```json
{
  "code": 200,
  "message": "request.success",
  "data": [
    {
      "id": "1950000000000000001",
      "adminId": "1945059543981625345",
      "category": "style",
      "keyName": "output_length",
      "value": "简洁",
      "description": "回答简洁，避免冗余解释",
      "priority": 80,
      "scope": "global",
      "scopeDetail": null,
      "source": "implicit",
      "confidence": 0.85,
      "usageCount": 12,
      "lastUsedAt": 1783769933000,
      "expiresAt": null,
      "decayRate": 0.005,
      "effectiveScore": 68.00,
      "status": 1,
      "createdAt": 1783769933000,
      "updatedAt": 1783769933000
    }
  ],
  "total": 1
}
```

### 3.2 详情接口

```http
GET /api/sys/admin/preference/{id}
```

响应字段同列表项。

### 3.3 新增接口

```http
POST /api/sys/admin/preference
```

**请求**：

```json
{
  "category": "style",
  "keyName": "output_length",
  "value": "简洁",
  "description": "回答简洁，避免冗余解释",
  "priority": 80,
  "scope": "global",
  "scopeDetail": null,
  "expiresAt": null,
  "decayRate": 0.00,
  "status": 1
}
```

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| `category` | String | 是 | — | 分类，使用硬编码常量 value |
| `keyName` | String | 是 | — | 偏好键名，如 `output_length` |
| `value` | String | 是 | — | 偏好值，如 `简洁` |
| `description` | String | 否 | 同 `value` | 人类可读描述 |
| `priority` | Integer | 否 | 50 | 优先级 0-100 |
| `scope` | String | 否 | `global` | 作用域 |
| `scopeDetail` | String | 否 | null | 仅 `scope=task_type` 时填写 |
| `expiresAt` | Long | 否 | null | 过期时间戳，null=永不过期 |
| `decayRate` | BigDecimal | 否 | 0.00 | 每日衰减率 |
| `status` | Integer | 否 | 1 | 0=禁用，1=启用 |

响应 `data` 为新建偏好 ID。

### 3.4 编辑接口

```http
PUT /api/sys/admin/preference/{id}
```

请求字段同新增。

### 3.5 删除接口

```http
DELETE /api/sys/admin/preference/{id}
```

### 3.6 启用/禁用接口

```http
PUT /api/sys/admin/preference/{id}/status
```

**请求**：

```json
{
  "status": 0
}
```

### 3.7 确认偏好（新增）

```http
POST /api/sys/admin/preference/{id}/feedback
```

**说明**：用户确认自动学习的偏好是正确的。后端将置信度 +0.10，并重新计算有效分数。

无请求体。响应：

```json
{
  "code": 200,
  "message": "request.success"
}
```

### 3.8 拒绝偏好（新增）

```http
DELETE /api/sys/admin/preference/{id}/feedback
```

**说明**：用户拒绝自动学习的偏好。后端将置信度 -0.15，若置信度低于 0.3 则自动禁用。

无请求体。响应：

```json
{
  "code": 200,
  "message": "request.success"
}
```

### 3.9 覆盖偏好值（新增）

```http
PUT /api/sys/admin/preference/{id}/override
```

**说明**：用户手动修改偏好值。后端将来源改为 `manual_override`，置信度设为 1.0，使用次数归零。

**请求**：

```json
{
  "value": "详细"
}
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `value` | String | 是 | 新的偏好值 |

---

## 4. 页面设计

### 4.1 列表页

**建议入口**：系统管理 → 后台用户偏好

**表格列**：

| 列名 | 字段 | 宽度 | 排序 | 说明 |
|------|------|------|------|------|
| 分类 | `category` | 100px | 否 | 下拉常量映射显示中文 |
| 键名 | `keyName` | 140px | 否 | 等宽字体显示 |
| 偏好值 | `value` | 180px | 否 | — |
| 描述 | `description` | 200px | 否 | 超长截断，hover 显示全文 |
| 优先级 | `priority` | 80px | 是 | 数字，可排序 |
| 作用域 | `scope` | 100px | 否 | `global`→全局，`task_type`→`任务类型:xxx` |
| 来源 | `source` | 80px | 否 | `explicit`→手动，`implicit`→自动学习 |
| 置信度 | `confidence` | 80px | 是 | 百分比显示 |
| 使用次数 | `usageCount` | 80px | 是 | 数字 |
| 有效分数 | `effectiveScore` | 80px | 是 | 数字，可排序 |
| 最后使用 | `lastUsedAt` | 120px | 是 | 时间格式化 |
| 状态 | `status` | 80px | 否 | 启用/禁用标签 |
| 操作 | — | 200px | 否 | 按钮组 |

**筛选栏**：

| 筛选项 | 类型 | 说明 |
|--------|------|------|
| 分类 | 下拉选择 | 硬编码常量 |
| 键名 | 输入框 | 模糊搜索 |
| 偏好值 | 输入框 | 模糊搜索 |
| 状态 | 下拉选择 | 全部/启用/禁用 |

**操作列按钮**：

| 按钮 | 显示条件 | 样式 | 说明 |
|------|----------|------|------|
| 编辑 | 始终 | 默认 | 打开编辑表单 |
| 确认 | `source=implicit` 且 `status=1` | 成功（绿色） | 确认自动学习的偏好 |
| 拒绝 | `source=implicit` 且 `status=1` | 危险（红色） | 拒绝自动学习的偏好 |
| 覆盖 | `status=1` | 主要（蓝色） | 修改偏好值 |
| 删除 | 始终 | 危险（红色） | 二次确认后删除 |

### 4.2 新增/编辑表单

**表单布局**（抽屉或弹窗）：

| 区域 | 字段 | 组件 | 必填 | 默认值 | 说明 |
|------|------|------|------|--------|------|
| 基本信息 | 分类 | 下拉选择 | 是 | — | 硬编码常量 |
| 基本信息 | 键名 | 输入框 | 是 | — | 如 `output_length` |
| 基本信息 | 偏好值 | 输入框 | 是 | — | 如 `简洁` |
| 基本信息 | 描述 | 输入框 | 否 | 同偏好值 | 人类可读说明 |
| 作用域 | 作用域 | 下拉选择 | 否 | `global` | `global`/`session`/`task_type` |
| 作用域 | 任务类型 | 输入框 | 条件 | — | 仅 `scope=task_type` 时显示 |
| 评分控制 | 优先级 | 数字滑块 | 否 | 50 | 0-100 |
| 评分控制 | 过期时间 | 日期时间选择 | 否 | 永不过期 | NULL=永不过期 |
| 评分控制 | 衰减率 | 数字输入 | 否 | 0.00 | 0=不衰减，建议 0.00-0.05 |
| 状态 | 启用/禁用 | 开关 | 否 | 启用 | — |

**表单交互**：

- 选择 `scope=task_type` 时，动态显示「任务类型」输入框
- 选择 `scope=global` 或 `scope=session` 时，隐藏「任务类型」输入框
- 「描述」字段不填时，后端默认使用「偏好值」

### 4.3 覆盖偏好弹窗

点击「覆盖」按钮后弹出确认弹窗：

```
标题：覆盖偏好值
内容：
  当前值：{当前 value}
  新值：[输入框]
按钮：取消 / 确认覆盖
```

调用 `PUT /api/sys/admin/preference/{id}/override`，请求体 `{ "value": "新值" }`。

### 4.4 确认/拒绝交互

点击「确认」按钮：
1. 弹出确认弹窗：「确认该偏好是正确的？」
2. 确认后调用 `POST /{id}/feedback`
3. 刷新列表

点击「拒绝」按钮：
1. 弹出确认弹窗：「拒绝后该偏好将被降低置信度，确认拒绝？」
2. 确认后调用 `DELETE /{id}/feedback`
3. 刷新列表

---

## 5. 状态展示规则

### 5.1 状态标签

| 值 | 文案 | 样式 |
|----|------|------|
| `0` | 禁用 | 灰色 |
| `1` | 启用 | 绿色 |

### 5.2 来源标签

| 值 | 文案 | 样式 |
|----|------|------|
| `explicit` | 手动 | 蓝色 |
| `implicit` | 自动学习 | 橙色 |
| `manual_override` | 手动覆盖 | 紫色 |

### 5.3 置信度展示

| 范围 | 样式 | 说明 |
|------|------|------|
| ≥ 0.70 | 绿色 | 高置信度 |
| 0.30 - 0.70 | 橙色 | 中置信度 |
| < 0.30 | 红色 | 低置信度，可能被自动禁用 |

### 5.4 有效分数展示

有效分数由后端自动计算：`有效分数 = 优先级 × 衰减因子 × 置信度`

前端直接展示 `effectiveScore` 字段值，可排序，无需前端计算。

### 5.5 作用域展示

| scope | 显示文案 |
|-------|----------|
| `global` | 全局 |
| `session` | 会话 |
| `task_type` | 任务类型: {scopeDetail} |

---

## 6. 聊天侧无需改动

聊天接口（`POST /api/agent/chat`、SSE）请求参数不变。后端自动：

1. 查询当前用户启用的偏好
2. 按有效分数排序注入模型上下文
3. 聊天后异步提取新偏好（不阻塞聊天响应）

前端可选：在偏好页提示「自动学习的偏好会在后续聊天中生效」。

---

## 7. 错误与降级

| 场景 | 前端表现建议 |
|------|-------------|
| 确认/拒绝失败 | 提示操作失败，刷新列表 |
| 覆盖值为空 | 表单校验拦截 |
| 偏好被自动禁用（置信度<0.3） | 列表中状态显示「禁用」，置信度红色警示 |
| 偏好过期 | 不在聊天中注入，列表仍可查看 |
| 权限不足 | 按现有 403 逻辑隐藏按钮或提示无权限 |

---

## 8. 前端开发清单

- [ ] 移除 `Admin_Preference_Category` 字典下拉调用，改用前端硬编码常量
- [ ] 列表页字段从 `content` 改为 `value` + `keyName` + `description`
- [ ] 列表新增显示列：`priority`、`scope`、`source`、`confidence`、`usageCount`、`effectiveScore`、`lastUsedAt`
- [ ] 列表新增筛选条件：`keyName`（输入框）、`scope`（下拉）
- [ ] 新增/编辑表单增加字段：`keyName`、`description`、`priority`、`scope`、`scopeDetail`、`expiresAt`、`decayRate`
- [ ] `scope=task_type` 时动态显示/隐藏 `scopeDetail` 输入框
- [ ] 操作列增加「确认」按钮，仅对 `source=implicit` 且 `status=1` 的记录显示
- [ ] 操作列增加「拒绝」按钮，仅对 `source=implicit` 且 `status=1` 的记录显示
- [ ] 操作列增加「覆盖」按钮，点击弹出新值输入弹窗
- [ ] 确认调用 `POST /{id}/feedback`，拒绝调用 `DELETE /{id}/feedback`
- [ ] 覆盖调用 `PUT /{id}/override`，请求体 `{ "value": "新值" }`
- [ ] 置信度低于 0.3 时红色警示显示
- [ ] 来源字段根据 `source` 显示不同颜色标签
- [ ] 偏好页增加提示文案：「系统会在聊天后自动提取长期偏好。启用的偏好会在后续聊天中作为上下文参考。」
