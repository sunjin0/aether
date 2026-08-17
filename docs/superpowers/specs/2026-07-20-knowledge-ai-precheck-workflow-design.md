# 知识库文档 AI 预检流程设计

## 目标

将 AI 审查定位为文档上传后的预检和改稿辅助，而不是对最终正文的准入校验。作者可以接受 AI 补丁或自行修改正文，不会因此被要求再次进行
AI 审查；最终内容质量由人工审批负责。

## 范围

本设计调整文档上传、AI 审查、草稿编辑、补丁应用和提交资格，不改变现有人工审批和索引发布流程。

## 业务流程

```text
上传文件或新建文本
  -> 提取或录入正文
  -> 创建 DRAFT 草稿版本
  -> 自动或手动发起 AI 预检
  -> AI_REVIEWING
  -> AI_REVIEWED
  -> 处理全部 AI 问题
       - 接受并应用补丁
       - 手动编辑正文并标记为 manually_fixed
       - 拒绝或忽略建议
  -> 提交人工审批
  -> APPROVED 或 REJECTED
  -> 审批通过后索引并发布
```

## 状态规则

`AI_REVIEWED` 表示该版本已成功完成 AI 预检，不表示当前正文 checksum 必须与 AI 审查快照一致。AI 必审场景下，所有 AI
问题都已处理是独立的提交条件。

```text
DRAFT -> AI_REVIEWING：发起 AI 预检
AI_REVIEWING -> AI_REVIEWED：预检成功
AI_REVIEWING -> DRAFT：预检失败，或完成前审查快照已失效
AI_REVIEWED -> AI_REVIEWED：编辑正文、应用已接受补丁或处理问题
AI_REVIEWED -> SUBMITTED：全部问题已处理后提交
DRAFT -> SUBMITTED：知识库未启用 AI 必审时提交
SUBMITTED -> APPROVED | REJECTED：人工审批决定
```

`AI_REVIEWING` 时草稿只读，确保正在执行的 AI 请求具有稳定的内容快照。进入 `AI_REVIEWED` 后允许修改正文，并持续保持
`AI_REVIEWED`。

## 问题处理

当 `aiReviewRequired` 开启时，成功预检产生的每个问题都必须离开 `pending` 状态，才允许提交。

问题的终态包括：

- `accepted`：作者决定采纳 AI 建议。实际应用补丁后，将生成的正文 checksum 保存到 `appliedChecksum`。
- `manually_fixed`：作者已在编辑器中手动修复该问题。
- `rejected`：作者拒绝该建议。
- `ignored`：作者明确忽略该建议。

接受建议本身不会修改正文；独立的应用操作才会把已接受补丁写入草稿。无效或尚未应用的已接受补丁可通过现有撤销接受接口恢复；已应用补丁保留审计记录，不能撤销接受。

手动保存正文不会自动关闭问题。作者必须对相关问题逐条标记 `manually_fixed`、`rejected` 或 `ignored`，以保留明确的处理决策记录。

## 提交规则

当 `aiReviewRequired` 为 `false` 时，遵循现有草稿指针规则，`DRAFT` 或 `AI_REVIEWED` 版本均可提交。

当 `aiReviewRequired` 为 `true` 时，提交必须同时满足：

1. 当前版本至少存在一条成功的 AI 预检记录。
2. 当前版本状态为 `AI_REVIEWED`。
3. 最近一次成功预检不存在 `handleStatus = pending` 的问题。

提交时不比较成功审查记录的 `sourceChecksum` 与当前版本 checksum。因此，应用 AI 建议或后续手动修改正文均不会触发新的预检要求。未成功完成的失败或过期审查会使版本保持
`DRAFT`，不能满足 AI 必审条件。

## 后端调整

`KnowledgeDocumentWorkflowServiceImpl.updateDraft` 更新完成预检的草稿时保留 `AI_REVIEWED`。该方法仍更新 `contentChecksum`
，并通过 `expectedChecksum` 保持乐观并发控制；原本为 `DRAFT` 的版本编辑后仍为 `DRAFT`。

`KnowledgeAiReviewController.applyAcceptedIssues` 继续调用 `updateDraft`，但应用后的版本状态保持 `AI_REVIEWED`，并返回
`requiresAiReview = false`。

`KnowledgeDocumentWorkflowServiceImpl.submit` 校验当前版本存在成功 AI 预检记录，并且没有待处理问题；不得仅因审查快照
checksum 与当前正文不同而拒绝提交。

问题操作接口保持现有权限、状态迁移和审计校验；手动处理接口继续支持 `manually_fixed`。

## 前端行为

上传或新建后进入文档工作台；AI 预检处于 `pending` 或 `running` 时进行轮询。预检完成后显示“AI 预检完成”，不得暗示该文档已经获得最终审批。

Diff 工作台支持接受建议、在允许时批量接受、应用已接受补丁、拒绝或忽略建议，以及编辑正文后标记问题为手动修复。保存编辑或应用补丁后，刷新正文和
checksum，但不提示用户重新进行 AI 审查。

AI 必审时，仅当预检问题不存在 `pending` 项，前端才允许发起提交。收到 `409` 时，展示服务端消息并重新加载文档版本和最新审查记录，不能使用过期
checksum 静默重试。

## 异常处理与审计

正文写入和补丁应用仍必须执行 checksum 校验。无效补丁、并发写入冲突、重复处理问题和撤销已应用补丁仍返回 `409`。

系统保留审查快照、问题决策、处理人、处理时间、备注、接受后的替换文本和应用后的 checksum。人工审批人可据此区分 AI 原始建议与最终提交内容。

## 验证要求

自动化测试至少覆盖：

- 完成 AI 预检的版本在手动更新草稿后保持 `AI_REVIEWED`。
- 应用已接受补丁后保持 `AI_REVIEWED`，且不要求再次预检。
- AI 必审时，存在成功预检且全部问题已处理的版本，即使正文 checksum 已改变，仍可提交。
- 存在任一 `pending` 问题时提交被阻断。
- 预检失败或过期时版本回到 `DRAFT`，并在 AI 必审时阻断提交。
- AI 必审时，不存在成功预检记录的草稿不能提交。
