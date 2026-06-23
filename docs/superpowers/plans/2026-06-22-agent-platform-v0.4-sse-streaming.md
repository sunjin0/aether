# Agent Platform V0.4 SSE Streaming Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement true SSE streaming chat for the Agent platform using OpenAI-compatible `stream=true` model responses.

**Architecture:** Extend the existing V0.3 chat abstractions instead of adding a parallel flow. `api` defines stream callbacks and result contracts, `biz` streams provider chunks and reuses chat validation/persistence, and `admin` exposes an MVC `SseEmitter` endpoint.

**Tech Stack:** Java 8, Spring Boot 2.7.18 MVC, `SseEmitter`, MyBatis-Plus, Fastjson2, `HttpURLConnection`, existing `ServerException`, existing `CurrentUser`.

---

## File Structure

- Create `api/src/main/java/com/aether/agent/model/ModelStreamCallback.java`: callback used by model clients to emit provider stream chunks.
- Create `api/src/main/java/com/aether/agent/model/ModelStreamResponse.java`: final stream result with content, model and token usage.
- Create `api/src/main/java/com/aether/agent/service/AgentStreamCallback.java`: callback used by service to emit business SSE events.
- Modify `api/src/main/java/com/aether/agent/model/ModelClient.java`: add `stream` method.
- Modify `api/src/main/java/com/aether/agent/service/AgentChatService.java`: add `stream` method.
- Modify `biz/src/main/java/com/aether/agent/model/OpenAIModelClient.java`: implement true OpenAI-compatible stream parsing.
- Modify `biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`: add stream orchestration and reuse existing persistence helpers.
- Modify `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`: add `GET /api/agent/chat/stream` returning `SseEmitter`.
- Modify `biz/src/test/java/com/aether/agent/service/impl/AgentChatServiceImplTest.java`: add stream success/failure coverage.
- Modify `docs/agent-platform/TASKS.md`: mark V0.4 implementation items complete after verification.

---

### Task 1: API Stream Contracts

**Files:**
- Create: `api/src/main/java/com/aether/agent/model/ModelStreamCallback.java`
- Create: `api/src/main/java/com/aether/agent/model/ModelStreamResponse.java`
- Create: `api/src/main/java/com/aether/agent/service/AgentStreamCallback.java`
- Modify: `api/src/main/java/com/aether/agent/model/ModelClient.java`
- Modify: `api/src/main/java/com/aether/agent/service/AgentChatService.java`

- [ ] **Step 1: Add `ModelStreamCallback`**

```java
package com.aether.agent.model;

public interface ModelStreamCallback {
    void onMessage(String chunk);
    void onToolCall(String toolCallJson);
    boolean isClosed();
}
```

- [ ] **Step 2: Add `ModelStreamResponse`**

```java
package com.aether.agent.model;

import lombok.Data;

@Data
public class ModelStreamResponse {
    private String content;
    private String model;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private String rawResponse;
}
```

- [ ] **Step 3: Add `AgentStreamCallback`**

```java
package com.aether.agent.service;

import com.aether.agent.model.ModelStreamResponse;

public interface AgentStreamCallback {
    void onMessage(String conversationId, String chunk);
    void onToolCall(String conversationId, String toolCallJson);
    void onDone(String conversationId, String messageId, ModelStreamResponse response);
    void onError(int code, String message);
    boolean isClosed();
}
```

- [ ] **Step 4: Extend `ModelClient`**

Add:

```java
ModelStreamResponse stream(ModelChatRequest request, ModelStreamCallback callback);
```

- [ ] **Step 5: Extend `AgentChatService`**

Add:

```java
void stream(AgentChatDto dto, AgentStreamCallback callback);
```

---

### Task 2: OpenAI Streaming Client

**Files:**
- Modify: `biz/src/main/java/com/aether/agent/model/OpenAIModelClient.java`

- [ ] **Step 1: Add stream implementation**

Implement `stream(ModelChatRequest, ModelStreamCallback)` using `HttpURLConnection`, `POST`, `Content-Type: application/json`, decrypted Bearer token, and request body with `stream=true`.

- [ ] **Step 2: Parse OpenAI SSE lines**

For each line starting with `data:`, ignore blanks, stop on `[DONE]`, parse JSON with Fastjson2, forward `choices[0].delta.content` via `callback.onMessage(chunk)`, append chunks into a `StringBuilder`, and capture `model` plus optional `usage` when present.

- [ ] **Step 3: Convert errors consistently**

Timeout-like IO errors throw `ServerException(503, "模型供应商调用超时")`; parse and provider errors throw `ServerException(500, "模型调用失败")` unless an existing `ServerException` was thrown.

---

### Task 3: Service Streaming Flow

**Files:**
- Modify: `biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`
- Test: `biz/src/test/java/com/aether/agent/service/impl/AgentChatServiceImplTest.java`

- [ ] **Step 1: Add stream success test**

Add a test that stubs `modelClient.stream(...)` to call `callback.onMessage("你")`, `callback.onMessage("好")`, return content `你好`, and verifies assistant message, run record, and callback events.

- [ ] **Step 2: Implement service stream method**

Follow the existing `chat` method sequence: validate request, get current user, load enabled Agent and Provider, create/validate conversation, save user message, build context, call selected client `stream`, save assistant message after stream completes, update message count, save success run, and call `callback.onDone(...)`.

- [ ] **Step 3: Add failure handling**

Catch `RuntimeException`, save failed run when user message and conversation are available, call `callback.onError(...)`, and do not save partial assistant messages.

---

### Task 4: SSE Controller Endpoint

**Files:**
- Modify: `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`

- [ ] **Step 1: Add stream endpoint**

Add `@GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)` with `agentId`, optional `conversationId`, and `message` query params. Return `SseEmitter`.

- [ ] **Step 2: Send SSE events**

Use a single-thread executor task per request. Send events named `message`, `tool_call`, `error`, and `done` with Fastjson2 `JSONObject` payloads matching `docs/agent-platform/API.md`.

- [ ] **Step 3: Handle lifecycle**

Track emitter closed state with `AtomicBoolean`; set it in `onCompletion`, `onTimeout`, and `onError`. Complete the emitter after `done` or `error`.

---

### Task 5: Verification And Task Status

**Files:**
- Modify: `docs/agent-platform/TASKS.md`

- [ ] **Step 1: Run compile**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\23672\.jdks\ms-17.0.19"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "C:\Users\23672\.m2\wrapper\dists\apache-maven-3.9.10-bin\53h08a94dg6djh6umvruv7q564\apache-maven-3.9.10\bin\mvn.cmd" clean compile -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Run targeted tests if compile passes**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\23672\.jdks\ms-17.0.19"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "C:\Users\23672\.m2\wrapper\dists\apache-maven-3.9.10-bin\53h08a94dg6djh6umvruv7q564\apache-maven-3.9.10\bin\mvn.cmd" -pl biz -am -Dtest=AgentChatServiceImplTest -DfailIfNoTests=false test
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 3: Update `TASKS.md`**

Mark V0.4 code tasks complete after compile/test pass. Leave real latency/manual client validation unchecked unless manually verified with a live provider.

---

## Self-Review

- Spec coverage: endpoint, true provider streaming, event format, connection lifecycle, full-message persistence, and run records are covered.
- Placeholder scan: no TBD/TODO placeholders remain.
- Type consistency: stream callback/result names are defined before they are referenced.
