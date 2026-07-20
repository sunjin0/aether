# Agent 平台 — 知识库 AI 审查

> 合并来源：KNOWLEDGE_REVIEW_FRONTEND_INTEGRATION.md、KNOWLEDGE_AI_REVIEW_DIFF_FRONTEND_INTEGRATION.md、KNOWLEDGE_AI_REVIEW_DIFF_WORKBENCH_SOLUTION.md、KNOWLEDGE_AI_REVIEW_PATCH_RECOVERY_FRONTEND.md
> 更新日期：2026-07-20

---

## 一、文档审查流程

### 文档/版本状态流转

```
DRAFT → AI_REVIEWING → AI_REVIEWED → SUBMITTED → APPROVED → INDEX_PENDING → INDEXED
                                              → REJECTED
```

- AI 审查仅审查草稿版本（DRAFT）
- AI 只提出补丁建议（suggestedPatch），不直接写入正文
- 审查记录固定关联 `documentVersionId` 和 `contentChecksum`

### 页面模块

| 页面 | 路由 |
|------|------|
| 知识库列表与配置 | `/knowledge/bases` |
| 文档列表 | `/knowledge/documents` |
| 文档编辑与审查工作台 | `/knowledge/documents/:id` |
| AI 审查 Diff 工作台 | `/knowledge/review/detail` |
| 审批中心 | `/knowledge/reviews` |
| 索引任务抽屉 | `/knowledge/index-jobs` |

### 轮询建议

| 状态 | 间隔 |
|------|------|
| AI_REVIEWING | 每 3 秒 |
| SUBMITTED | 每 5 秒 |
| 审批通过后查索引任务 | 每 3 秒 |
| 离开页面或失焦超过 1 分钟 | 停止轮询 |

---

## 二、AI 审查 Diff 工作台

### 布局

类似 GitHub PR 风格：
- 顶部：概览信息（文档名、版本、审查状态）
- 左右：Diff 对比（原文 ↔ 建议修改）
- 右侧：问题清单

### 状态与按钮规则

| 状态 | 表现 |
|------|------|
| `AI_REVIEWED` 且 `stale=false` | 完整 Diff 展示，可操作 |
| `stale=true` | 黄色提示条："内容已变更，建议重新审查"，禁用操作按钮 |
| `handleStatus=accepted` | 绿色标记，不可再次操作 |
| `severity=critical` | 红色标记，不进入批量采纳 |

---

## 三、补丁模型

### 操作类型

| 操作 | 说明 |
|------|------|
| `replace` | 替换指定 block 内容 |
| `insert_before` | 在指定 block 前插入 |
| `insert_after` | 在指定 block 后插入 |
| `delete` | 删除指定 block |
| `set_heading` | 修改标题级别 |

使用稳定的 `blockId` 定位，不依赖 LLM 字符偏移量。

### 并发控制

所有写操作需传 `expectedChecksum`：
- 服务端校验 checksum 匹配才执行
- 409 Conflict 时提示用户刷新页面

---

## 四、接口清单

### 审查管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 获取 Diff | GET | `/api/knowledge/ai-review/{reviewId}/diff` |
| 采纳单条建议 | POST | `/api/knowledge/ai-review/{reviewId}/issues/{issueId}/accept` |
| 忽略单条建议 | POST | `/api/knowledge/ai-review/{reviewId}/issues/{issueId}/reject` |
| 撤销已接受建议 | POST | `/api/knowledge/ai-review/{reviewId}/issues/{issueId}/unaccept` |
| 批量采纳 | POST | `/api/knowledge/ai-review/{reviewId}/issues/accept-batch` |
| 统一应用 | POST | `/api/knowledge/ai-review/{reviewId}/issues/apply` |

### 知识库管理

| 功能 | 方法 | 路径 |
|------|------|------|
| 知识库 CRUD | 标准 REST | `/api/knowledge/base/**` |
| 文档 CRUD | 标准 REST | `/api/knowledge/document/**` |
| 草稿更新 | POST | `/api/knowledge/document/draft` |
| 提交审批 | POST | `/api/knowledge/document/{id}/submit` |

### 索引任务

| 功能 | 方法 | 路径 |
|------|------|------|
| 任务列表 | POST | `/api/knowledge/index-job/list` |
| 任务详情 | GET | `/api/knowledge/index-job/{id}` |
| 重试 | POST | `/api/knowledge/index-job/{id}/retry` |

---

## 五、补丁恢复规则

| 条件 | 行为 |
|------|------|
| `handleStatus === 'accepted' && !appliedChecksum` | 可撤销（`unaccept`），撤销后变更为 `rejected` |
| `suggestedPatch == null` | 不显示"接受"按钮，显示"忽略"或"手动处理"，不可选入批量 |
| 批量采纳选择 | 必须是 `pending`、非 `critical`、有 `suggestedPatch` |
| 统一应用返回 409 | 保留页面状态，展示错误消息，重新请求 Diff，不自动重试 |
