# 知识库文档 AI 预检流程实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将 AI 审查调整为上传后的预检和改稿辅助，使应用 AI 建议或手动修改后的 `AI_REVIEWED` 草稿无需重新审查即可提交人工审批。

**Architecture:** 保留现有 `DRAFT -> AI_REVIEWING -> AI_REVIEWED -> SUBMITTED` 状态模型，不增加数据库字段或新状态。`AI_REVIEWED` 改为表示当前版本至少成功完成过一次预检；正文更新时保留该状态，提交时通过最近成功预检及其全部问题均已处理来判断资格，而不比较 AI 快照与当前正文 checksum。

**Tech Stack:** Java 8、Spring Boot 2.7、MyBatis-Plus、JUnit 5、Mockito、Maven。

---

## 文件结构

- 修改：`biz/src/main/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImpl.java`
  - 保留预检后的 `AI_REVIEWED` 状态，补全 AI 必审时“成功预检 + 无待处理问题”的提交校验。
- 修改：`admin/src/main/java/com/aether/knowledge/controller/KnowledgeAiReviewController.java`
  - 应用已接受补丁后返回无需再次预检的结果；保留补丁与并发校验。
- 修改：`biz/src/test/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImplTest.java`
  - 覆盖预检后编辑、无成功预检、存在待处理问题和正常提交的服务层规则。
- 修改：`admin/src/test/java/com/aether/knowledge/controller/KnowledgeReviewControllerTest.java`
  - 覆盖应用补丁后的 `AI_REVIEWED` 响应，并修正已有测试中未定义的 `workflowService` mock。
- 修改：`docs/agent-platform/KNOWLEDGE_REVIEW_FRONTEND_INTEGRATION.md`
  - 将前端对接文档从“正文变更必须重审”更新为 AI 预检语义。
- 修改：`docs/agent-platform/KNOWLEDGE_AI_REVIEW_PATCH_RECOVERY_FRONTEND.md`
  - 移除“应用补丁后重新 AI 审查”的前端动作，说明手动修复问题的处理方式。

### Task 1: 先用测试固定预检后的草稿状态

**Files:**
- Modify: `biz/src/test/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImplTest.java:31-39,115-148`
- Modify: `biz/src/main/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImpl.java:107-137`

- [ ] **Step 1: 将现有“编辑后回到草稿”的测试替换为失败测试**

将 `editingAnAiReviewedVersionReturnsItToDraft` 重命名为 `editingAnAiReviewedVersionKeepsItAiReviewed`，断言 MyBatis-Plus 更新参数包含 `AI_REVIEWED`，而非 `DRAFT`：

```java
@Test
void editingAnAiReviewedVersionKeepsItAiReviewed() {
    KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
    KnowledgeDocument document = document();
    document.setDraftVersionId("version-1");
    when(versionService.getById("version-1")).thenReturn(version);
    when(documentService.getById("document-1")).thenReturn(document);
    when(versionService.update(any())).thenReturn(true);

    service.updateDraft("version-1", "updated content", "checksum");

    ArgumentCaptor<LambdaUpdateWrapper> versionUpdate = ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    verify(versionService).update(versionUpdate.capture());
    assertTrue(versionUpdate.getValue().getParamNameValuePairs().containsValue("AI_REVIEWED"));
}
```

- [ ] **Step 2: 运行单测并确认失败**

Run:

```powershell
mvn -pl biz -am -Dtest=KnowledgeDocumentWorkflowServiceImplTest#editingAnAiReviewedVersionKeepsItAiReviewed -DfailIfNoTests=false test
```

Expected: 测试失败，更新参数仍包含 `DRAFT`，不包含 `AI_REVIEWED`。

- [ ] **Step 3: 按当前版本状态更新正文与文档状态**

在 `updateDraft` 的 checksum 计算之后加入目标状态，并将后续三个 `DRAFT` 写入点替换为该变量：

```java
String newChecksum = checksum(content);
String nextReviewStatus = KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
        ? KnowledgeReviewStatus.AI_REVIEWED : KnowledgeReviewStatus.DRAFT;
boolean updated = versionService.update(Wrappers.lambdaUpdate(KnowledgeDocumentVersion.class)
        .eq(KnowledgeDocumentVersion::getId, versionId)
        .in(KnowledgeDocumentVersion::getReviewStatus,
                KnowledgeReviewStatus.DRAFT, KnowledgeReviewStatus.AI_REVIEWED)
        .set(KnowledgeDocumentVersion::getContent, content)
        .set(KnowledgeDocumentVersion::getStructuredContent, null)
        .set(KnowledgeDocumentVersion::getContentChecksum, newChecksum)
        .set(KnowledgeDocumentVersion::getReviewStatus, nextReviewStatus));
if (!updated) throw new ServerException(409, I18nUtils.getMessage("knowledge.document.draft-state.changed"));
updateDocumentReviewStatus(document.getId(), nextReviewStatus, versionId, null);
log(null, document.getId(), versionId, "DRAFT_UPDATED", version.getReviewStatus(), nextReviewStatus, null);
```

- [ ] **Step 4: 运行单测并确认通过**

Run:

```powershell
mvn -pl biz -am -Dtest=KnowledgeDocumentWorkflowServiceImplTest#editingAnAiReviewedVersionKeepsItAiReviewed -DfailIfNoTests=false test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交此独立改动**

仅在用户明确要求提交时执行：

```powershell
git add biz/src/main/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImpl.java biz/src/test/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImplTest.java
git commit -m "fix(knowledge): retain prechecked draft status"
```

### Task 2: 以成功预检记录作为 AI 必审的提交凭据

**Files:**
- Modify: `biz/src/test/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImplTest.java:96-148`
- Modify: `biz/src/main/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImpl.java:172-191,356-368`

- [ ] **Step 1: 写入“AI_REVIEWED 但不存在成功审查记录”失败测试**

在测试类中新增以下测试。它验证版本状态不能被单独伪造为通过预检：

```java
@Test
void refusesRequiredAiSubmissionWithoutSuccessfulPrecheck() {
    KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
    KnowledgeDocument document = document();
    document.setDraftVersionId("version-1");
    KnowledgeBase base = new KnowledgeBase();
    base.setId("kb-1");
    base.setReviewConfig("{\"aiReviewRequired\":true}");
    when(versionService.getById("version-1")).thenReturn(version);
    when(documentService.getById("document-1")).thenReturn(document);
    when(accessService.requireSubmittable("kb-1")).thenReturn(base);
    when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(null);

    assertThrows(ServerException.class, () -> service.submit("version-1", null));
}
```

再新增“成功预检、无待处理问题可以提交”的失败测试，并让 `versionService.update(any())`、`taskService.save(any())` 返回成功：

```java
@Test
void submitsRequiredAiVersionAfterSuccessfulPrecheckWithNoPendingIssues() {
    KnowledgeDocumentVersion version = version("version-1", KnowledgeReviewStatus.AI_REVIEWED);
    KnowledgeDocument document = document();
    document.setDraftVersionId("version-1");
    KnowledgeBase base = new KnowledgeBase();
    base.setId("kb-1");
    base.setReviewConfig("{\"aiReviewRequired\":true}");
    KnowledgeAiReview review = new KnowledgeAiReview();
    review.setId("review-1");
    when(versionService.getById("version-1")).thenReturn(version);
    when(documentService.getById("document-1")).thenReturn(document);
    when(accessService.requireSubmittable("kb-1")).thenReturn(base);
    when(aiReviewRecordService.getOne(any(), org.mockito.ArgumentMatchers.eq(false))).thenReturn(review);
    when(aiReviewIssueService.count(any())).thenReturn(0L);
    when(versionService.update(any())).thenReturn(true);
    when(taskService.save(any(KnowledgeReviewTask.class))).thenReturn(true);

    service.submit("version-1", null);

    verify(taskService).save(any(KnowledgeReviewTask.class));
}
```

- [ ] **Step 2: 运行新增测试并确认第一项失败**

Run:

```powershell
mvn -pl biz -am -Dtest=KnowledgeDocumentWorkflowServiceImplTest -DfailIfNoTests=false test
```

Expected: `refusesRequiredAiSubmissionWithoutSuccessfulPrecheck` 失败，因为当前 `submit` 仅检查 `AI_REVIEWED` 状态。

- [ ] **Step 3: 在提交前统一取得最近成功预检，并明确校验其存在性**

将 `submit` 中 AI 必审判断和待处理问题检查改成以下结构；新增的 `latestSuccessfulAiReview` 封装现有查询，避免同一查询分散在多个条件中：

```java
boolean aiRequired = booleanConfig(base.getReviewConfig(), "aiReviewRequired", true);
KnowledgeAiReview latestSuccessfulReview = latestSuccessfulAiReview(versionId);
if (aiRequired && (!KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())
        || latestSuccessfulReview == null)) {
    throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.required-before-submission"));
}
if (!KnowledgeReviewStatus.DRAFT.equals(version.getReviewStatus())
        && !KnowledgeReviewStatus.AI_REVIEWED.equals(version.getReviewStatus())) {
    throw new ServerException(409, I18nUtils.getMessage("knowledge.document-version.submit.invalid-state"));
}
if (aiRequired && hasPendingAiIssues(latestSuccessfulReview)) {
    throw new ServerException(409, I18nUtils.getMessage("knowledge.ai-review.issues.pending"));
}
```

用以下两个方法替换原 `hasPendingAiIssues(String versionId)`：

```java
private KnowledgeAiReview latestSuccessfulAiReview(String versionId) {
    return aiReviewRecordService.getOne(Wrappers.lambdaQuery(KnowledgeAiReview.class)
            .eq(KnowledgeAiReview::getDocumentVersionId, versionId)
            .eq(KnowledgeAiReview::getStatus, "success")
            .eq(KnowledgeAiReview::getDeleted, false)
            .orderByDesc(KnowledgeAiReview::getCreatedAt)
            .last("LIMIT 1"), false);
}

private boolean hasPendingAiIssues(KnowledgeAiReview review) {
    return review != null && aiReviewIssueService.count(
            Wrappers.lambdaQuery(com.aether.knowledge.entity.KnowledgeAiReviewIssue.class)
                    .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getAiReviewId, review.getId())
                    .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getHandleStatus, "pending")
                    .eq(com.aether.knowledge.entity.KnowledgeAiReviewIssue::getDeleted, false)) > 0;
}
```

不要加入 `sourceChecksum` 与当前 `contentChecksum` 的比较，这正是预检模式允许后续编辑的业务要求。

- [ ] **Step 4: 调整既有待处理问题测试并运行服务层全部测试**

将原 `refusesSubmissionWhenCriticalAiIssueIsPending` 的 `reviewConfig` 设为 `{"aiReviewRequired":true}`，使其不依赖已废弃的 `blockOnCriticalIssues` 语义；保留 `refusesSubmissionWhenAnyAiIssueIsPending`，验证任何严重级别的 `pending` 都阻断提交。

Run:

```powershell
mvn -pl biz -am -Dtest=KnowledgeDocumentWorkflowServiceImplTest -DfailIfNoTests=false test
```

Expected: `BUILD SUCCESS`。

- [ ] **Step 5: 提交此独立改动**

仅在用户明确要求提交时执行：

```powershell
git add biz/src/main/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImpl.java biz/src/test/java/com/aether/knowledge/service/impl/KnowledgeDocumentWorkflowServiceImplTest.java
git commit -m "fix(knowledge): require resolved AI precheck issues"
```

### Task 3: 应用 AI 补丁后保持预检完成状态

**Files:**
- Modify: `admin/src/test/java/com/aether/knowledge/controller/KnowledgeReviewControllerTest.java:188-214`
- Modify: `admin/src/main/java/com/aether/knowledge/controller/KnowledgeAiReviewController.java:322-369`

- [ ] **Step 1: 修正已有测试 mock 并改写为失败期望**

在 `applyAcceptedIssuesUpdatesDraftOnce` 中，在 `versionService` 声明后补回局部 mock：

```java
KnowledgeDocumentWorkflowService workflowService = mock(KnowledgeDocumentWorkflowService.class);
```

将 `updatedVersion` 状态设为 `AI_REVIEWED`，并在现有 checksum 断言后增加：

```java
assertEquals("AI_REVIEWED", controller.applyAcceptedIssues("review-1", request)
        .getData().getReviewStatus());
assertEquals(false, controller.applyAcceptedIssues("review-1", request)
        .getData().getRequiresAiReview());
```

为避免同一请求执行两次，实际测试中先保存一次结果：

```java
KnowledgeAiReviewIssueAcceptResultVo result = controller.applyAcceptedIssues("review-1", request).getData();
assertEquals("checksum-2", result.getContentChecksum());
assertEquals("AI_REVIEWED", result.getReviewStatus());
assertEquals(false, result.getRequiresAiReview());
```

- [ ] **Step 2: 运行控制器测试并确认失败**

Run:

```powershell
mvn -pl admin -am -Dtest=KnowledgeReviewControllerTest#applyAcceptedIssuesUpdatesDraftOnce -DfailIfNoTests=false test
```

Expected: 测试失败，当前实现返回 `requiresAiReview = true`。

- [ ] **Step 3: 修改补丁应用结果语义**

在 `applyAcceptedIssues` 构造响应时保持其他字段不变，仅将：

```java
result.setRequiresAiReview(true);
```

替换为：

```java
result.setRequiresAiReview(false);
```

不要移除以下检查：版本必须为 `AI_REVIEWED`，且 `review.getSourceChecksum()` 必须与应用前正文 checksum 一致。该检查只保护补丁定位正确性，并不影响补丁应用后的后续人工编辑或提交。

- [ ] **Step 4: 运行控制器测试并确认通过**

Run:

```powershell
mvn -pl admin -am -Dtest=KnowledgeReviewControllerTest -DfailIfNoTests=false test
```

Expected: `BUILD SUCCESS`，包括撤销未应用建议、拒绝建议和批量接受的既有回归测试。

- [ ] **Step 5: 提交此独立改动**

仅在用户明确要求提交时执行：

```powershell
git add admin/src/main/java/com/aether/knowledge/controller/KnowledgeAiReviewController.java admin/src/test/java/com/aether/knowledge/controller/KnowledgeReviewControllerTest.java
git commit -m "fix(knowledge): keep AI precheck after patch apply"
```

### Task 4: 同步前端对接文档与最终验证

**Files:**
- Modify: `docs/agent-platform/KNOWLEDGE_REVIEW_FRONTEND_INTEGRATION.md:20-46,97-103,159-173,208-215`
- Modify: `docs/agent-platform/KNOWLEDGE_AI_REVIEW_PATCH_RECOVERY_FRONTEND.md`
- Reference: `docs/superpowers/specs/2026-07-20-knowledge-ai-precheck-workflow-design.md`

- [ ] **Step 1: 更新主前端对接文档的状态和提交说明**

将状态图和状态表中的“`AI_REVIEWED -> DRAFT: 修改正文`”删除，改为以下说明：

```text
AI_REVIEWED -> AI_REVIEWED：应用 AI 建议、手动修改正文或处理 AI 问题
AI_REVIEWED -> SUBMITTED：AI 必审时全部预检问题已处理；未启用 AI 必审时遵循既有草稿提交规则
```

将工作台和验收条目更新为：保存正文不会要求重新 AI 审查；手动修复后，用户必须调用 `PUT /api/knowledge/ai-review/issue/{issueId}/handle` 并传递 `{"status":"manually_fixed"}`；提交前任何 `pending` 问题都会被后端以 `409` 拒绝。

- [ ] **Step 2: 更新补丁恢复文档的客户端动作**

将任何“应用补丁后重新发起 AI 审查”或“`requiresAiReview = true`”描述改为：

```text
应用成功后使用响应中的 contentChecksum 更新编辑会话；版本仍为 AI_REVIEWED，requiresAiReview 为 false。
前端刷新问题列表，要求作者将剩余问题分别接受、标记 manually_fixed、拒绝或忽略；所有问题不再为 pending 后即可提交人工审批。
```

保留以下恢复规则：无效补丁不可接受，未应用的 `accepted` 可撤销，已应用补丁不可撤销；`409` 时重新加载版本和 Diff，禁止静默重试。

- [ ] **Step 3: 运行完整相关模块测试**

Run:

```powershell
mvn -pl admin,biz -am test
```

Expected: `BUILD SUCCESS`。若测试依赖本地数据库、Redis 或模型服务而失败，记录完整失败命令和首个根因；不要将环境失败描述为代码测试通过。

- [ ] **Step 4: 执行差异与空白检查**

Run:

```powershell
git diff --check
```

Expected: `git diff --check` 无输出；差异只包含预检流程、对应测试和前端对接文档。

- [ ] **Step 5: 提交文档和最终验证改动**

仅在用户明确要求提交时执行：

```powershell
git add docs/agent-platform/KNOWLEDGE_REVIEW_FRONTEND_INTEGRATION.md docs/agent-platform/KNOWLEDGE_AI_REVIEW_PATCH_RECOVERY_FRONTEND.md
git commit -m "docs(knowledge): document AI precheck workflow"
```

## 计划自检

- 规格中的状态规则由 Task 1 实现，失败和过期审查回到 `DRAFT` 的既有 `KnowledgeAiReviewWorker` 行为不变。
- AI 必审的成功预检和无 `pending` 问题条件由 Task 2 实现；Task 2 明确禁止 checksum 一致性校验。
- 补丁应用后不再要求重新预检由 Task 1 和 Task 3 共同保证。
- `manually_fixed` 的接口已存在，Task 4 明确前端何时调用并提供确切请求状态值。
- 每个代码改动均先写失败测试、确认失败、最小实现、确认通过；计划不增加新数据库字段、枚举或无关重构。
