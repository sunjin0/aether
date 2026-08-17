# Deep Integration Final Findings Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Deep completion atomic, expose a started run immediately to SSE clients, align Deep SSE lifetime with
configured execution time, and validate non-stream agents before dereference.

**Architecture:** `DeepAgentRunService.completeRun` performs a conditional active-to-success claim and every final
persistence operation in one rollback-capable transaction. `AgentChatController` derives only Deep emitter lifetime from
configuration and sends a separate `accepted` SSE event after the local run exists. The Dashboard parses that event at
the typed stream boundary and records its ID without adding a progress step.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, JUnit 5/Mockito, React 18, TypeScript, Jest.

---

### Task 1: Atomic Deep Completion

**Files:**

- Modify: `biz/src/main/java/com/aether/agent/service/DeepAgentRunService.java`
- Test: `biz/src/test/java/com/aether/agent/service/impl/DeepAgentRunServiceTest.java`

- [ ] **Step 1: Write failing tests** for a failed active-state claim that does not save an assistant message and for
  message persistence failure that propagates from completion.
- [ ] **Step 2: Run** `mvn -pl biz -am -Dtest=DeepAgentRunServiceTest -DfailIfNoTests=false test` and confirm the new
  assertions fail against the old save-before-claim ordering.
- [ ] **Step 3: Implement** `@Transactional(rollbackFor = Exception.class)` completion: claim queued/running to success;
  return null when unclaimed; save the assistant message; attach final fields including message ID; increment
  conversation count; throw when persistence reports false.
- [ ] **Step 4: Run** the focused test and confirm it passes.

### Task 2: Deep Controller Contract

**Files:**

- Modify: `api/src/main/java/com/aether/agent/dto/DeepAgentConfig.java`
- Modify: `api/src/main/resources/application-prod.yml`
- Modify: `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`
- Create: `admin/src/test/java/com/aether/agent/controller/AgentChatControllerTest.java`

- [ ] **Step 1: Write failing tests** for non-stream missing/deleted agents returning 404, a Deep-only timeout derived
  from 600 seconds plus a 30-second margin, standard emitter retaining 300000 ms, and an `accepted` event containing
  local run and conversation IDs.
- [ ] **Step 2: Run** `mvn -pl admin -am -Dtest=AgentChatControllerTest -DfailIfNoTests=false test` and confirm failures
  describe the missing behavior.
- [ ] **Step 3: Implement** `runTimeoutSeconds=600` configuration and production environment mapping; use Java 8
  overflow-safe seconds-to-milliseconds conversion with a 30-second margin and fallback; inject config into the
  controller; guard non-stream lookup; send `accepted` after `startRun` returns.
- [ ] **Step 4: Run** the focused controller test and confirm it passes.

### Task 3: Typed Dashboard Acceptance

**Files:**

- Modify: `src/services/entity/Agent.ts`
- Modify: `src/services/agent/ChatController.ts`
- Modify: `src/services/agent/ChatController.test.ts`
- Modify: `src/pages/agent/chat/index.tsx`

- [ ] **Step 1: Write failing Jest cases** verifying both normal and reply clients deliver a valid `accepted` payload to
  `onAccepted`, ignore malformed/incomplete payloads, and do not route it through progress handling.
- [ ] **Step 2: Run** `npm test -- ChatController.test.ts --runInBand` and confirm the callbacks are unavailable or
  uncalled.
- [ ] **Step 3: Implement** the accepted payload type, guarded runtime parser, shared dispatcher branch, and page
  handler that writes `deepRunIdRef` plus state for normal and reply streams without changing `deepRunSteps`.
- [ ] **Step 4: Run** the focused Dashboard test and confirm it passes.

### Task 4: Verify

**Files:**

- Verify only: Java and Dashboard changes above

- [ ] **Step 1: Run** focused Java tests and `mvn test -pl admin -am` once; record dependency/environment failures
  without modifying unrelated configuration.
- [ ] **Step 2: Run** `npm test -- ChatController.test.ts deepProgress.test.ts --runInBand` and `npm run tsc`.
- [ ] **Step 3: Inspect** `git diff` and `git status --short` in both repositories. Do not stage, commit, or modify
  unrelated worktree changes.
