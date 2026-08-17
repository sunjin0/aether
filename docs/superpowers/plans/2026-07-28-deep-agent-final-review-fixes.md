# Deep Agent Final Review Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:
> executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent invalid Deep Agent dispatches and persist complete, durable Deep Agent run and knowledge-audit
metadata.

**Architecture:** Expose the standard chat service's enabled-agent validator so the Deep controller has the same 404/422
behavior. Persist dispatch-time retrieval sources in `agent_run`, then use those trusted sources after an atomic
successful completion claim to record citations and retrieval outcomes exactly once.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, PostgreSQL Flyway, JUnit 5, Mockito.

---

### Task 1: Add Regression Tests

**Files:**

- Modify: `admin/src/test/java/com/aether/agent/controller/AgentChatControllerTest.java`
- Modify: `biz/src/test/java/com/aether/agent/service/impl/DeepAgentRunServiceTest.java`
- Modify: `admin/src/test/java/com/aether/agent/controller/DeepAgentCallbackControllerTest.java`

- [ ] Add controller tests proving disabled Deep agents fail before `startRun`, and an owned conversation tied to
  another agent receives `422 agent.conversation.agent.mismatch` without dispatch.
- [ ] Add service tests asserting `externalRunId` equals the assigned local run id, persisted retrieval metadata is used
  to record cited sources and retrieval outcomes, malformed metadata yields an empty-source outcome, and stale
  completion records neither audit.
- [ ] Add the missing static Mockito `eq` import and scan Deep tests for equivalent missing matcher imports.

### Task 2: Persist Run Metadata

**Files:**

- Create: `api/src/main/resources/db/migration/postgresql/V9__deep_agent_run_retrieval_sources.sql`
- Modify: `api/src/main/java/com/aether/agent/entity/AgentRun.java`
- Modify: `api/src/main/java/com/aether/agent/vo/AgentRunVo.java`
- Modify: `biz/src/main/java/com/aether/agent/service/DeepAgentRunService.java`

- [ ] Add a nullable PostgreSQL text/JSON-compatible `retrieval_sources` column following the existing migration style.
- [ ] Add matching entity and run-view fields.
- [ ] After local run persistence assigns its id, set `externalRunId` to that id and serialize dispatch-time retrieved
  sources into `retrievalSources` before the external request.
- [ ] Deserialize only trusted persisted metadata on successful completion; use final answer citation markers to filter
  citations and record citation and retrieval audits after the message/run/conversation writes succeed.

### Task 3: Reuse Standard Validation

**Files:**

- Modify: `api/src/main/java/com/aether/agent/service/AgentChatService.java`
- Modify: `biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`
- Modify: `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`

- [ ] Expose the existing enabled-agent validation behavior through `AgentChatService` without duplicating the status
  value.
- [ ] Call it before Deep SSE setup/dispatch.
- [ ] Apply the standard `422 agent.conversation.agent.mismatch` check to a supplied Deep conversation.

### Task 4: Verify

**Files:**

- Verify modified source and test files.

- [ ] Run targeted Maven tests if Maven is available, otherwise record the exact unavailable-command output.
- [ ] Inspect the diff and report changed files and verifiable red/green evidence. Do not commit.
