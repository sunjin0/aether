# 知识库 AI 审查补丁恢复前端对接

## 1. 目的

本文说明 AI 审查建议在接受后、写入草稿前的撤销操作，以及无自动补丁问题和统一应用失败时的前端处理规则。

后端新增接口后，前端需要避免将无法自动应用的问题纳入单条或批量接受操作，并允许用户撤销尚未写入草稿的已接受建议。

## 2. 撤销已接受建议

### 2.1 展示规则

仅当问题满足以下条件时展示“撤销接受”按钮：

```ts
const canUnaccept =
  issue.handleStatus === 'accepted' &&
  !issue.appliedChecksum;
```

按钮文案：`撤销接受`。

`appliedChecksum` 有值表示建议已经写入草稿，前端不得展示撤销入口。

### 2.2 接口

```http
POST /api/knowledge/ai-review/{reviewId}/issues/{issueId}/unaccept
Content-Type: application/json
```

请求体可选：

```ts
type UnacceptIssuePayload = {
  comment?: string;
};
```

示例：

```json
{
  "comment": "补丁目标与原始内容不一致，撤销接受。"
}
```

撤销成功后，后端将问题状态变更为 `rejected`。前端应重新请求 Diff 数据：

```http
GET /api/knowledge/ai-review/{reviewId}/diff
```

不要只在本地修改 `handleStatus`，因为重新加载后还需要同步 `proposedContent`、计数和其他问题的预览状态。

### 2.3 失败处理

当后端返回 `409` 时，保留当前页面状态并展示接口 `message`。

常见原因：

| 场景 | 后端消息 | 前端处理 |
| --- | --- | --- |
| 问题不是 `accepted` | `AI审查问题尚未接受` | 刷新 Diff，更新按钮状态 |
| 建议已写入草稿 | `AI审查问题已应用到草稿，不能撤销` | 隐藏撤销入口，提示用户在草稿中手动编辑 |
| 文档不再可处理 | 现有版本状态错误 | 刷新 Diff 并禁用当前审查操作 |

## 3. 无自动补丁的问题

当 Diff 问题返回：

```ts
issue.suggestedPatch == null
```

说明 AI 发现了问题，但没有可安全自动应用的补丁。前端应：

1. 不显示单条“接受”按钮。
2. 显示“忽略”或“手动处理”入口。
3. 显示提示：`该建议没有可自动应用的补丁，请手动修改文档或忽略该问题。`
4. 不允许将该问题选入批量接受列表。

单条接受和批量接受接口也会拒绝无补丁问题，并返回 `409` 和“补丁无法应用”。前端仍应在请求失败后刷新 Diff，防止页面数据过期。

## 4. 批量接受选择规则

批量接受候选项必须同时满足：

```ts
const canBatchAccept =
  issue.handleStatus === 'pending' &&
  issue.severity !== 'critical' &&
  issue.suggestedPatch != null;
```

不能仅根据 `pending` 和严重等级判断，否则无自动补丁的问题可能被错误提交。

## 5. 统一应用失败处理

统一应用接口：

```http
POST /api/knowledge/ai-review/{reviewId}/issues/apply
```

请求体：

```json
{
  "expectedChecksum": "当前diff.contentChecksum"
}
```

若接口返回 `409`，例如：

```json
{
  "code": 409,
  "message": "补丁目标不匹配",
  "data": null
}
```

前端处理规则：

1. 保留当前审核页面、筛选条件和问题清单，不关闭弹窗或跳转页面。
2. 展示后端返回的 `message`。
3. 重新请求 `/diff`，以获取服务端最新的审核状态和预览内容。
4. 引导用户撤销无效的已接受问题，或在编辑器中手动处理并将对应问题标记为 `manually_fixed`、`rejected` 或 `ignored`。
5. 不要自动重试统一应用请求。

当前错误响应不携带失败问题的 `issueId`，前端无法可靠高亮具体问题，只能展示错误消息和让用户检查已接受项目。如需精确定位，后端需要在错误响应的 `data` 或扩展字段中返回失败的 `issueId`。

## 6. 前端接口封装示例

```ts
export const unacceptAiReviewIssue = (
  reviewId: string,
  issueId: string,
  data?: UnacceptIssuePayload,
) => request<WebResponse<void>>(
  `/api/knowledge/ai-review/${reviewId}/issues/${issueId}/unaccept`,
  {
    method: 'POST',
    data,
  },
);
```

建议将撤销、单条接受、批量接受和统一应用成功或失败后的 Diff 刷新逻辑收敛到同一个 mutation 回调中，避免各入口的页面状态不一致。

## 7. 应用后的预检状态

“应用已接受修改”成功后，使用响应中的 `contentChecksum` 更新编辑会话。响应的 `reviewStatus` 仍为 `AI_REVIEWED`，`requiresAiReview` 为 `false`；前端不得提示或自动发起新的 AI 审查。

随后刷新 Diff 和问题列表。作者可以继续编辑正文，但必须将所有剩余 `pending` 问题逐项处理：接受建议、标记 `manually_fixed`、拒绝或忽略。知识库启用 AI 必审时，全部问题离开 `pending` 后才允许提交人工审批。
