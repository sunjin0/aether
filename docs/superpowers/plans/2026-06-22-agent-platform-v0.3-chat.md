# Agent Platform V0.3 Chat Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现 Agent 平台 V0.3 非流式聊天闭环，支持 OpenAI 兼容模型调用、会话自动创建、消息落库和运行记录审计。

**Architecture:** 在 `api` 定义聊天服务接口和模型客户端抽象，在 `biz` 实现模型客户端工厂、OpenAI 兼容客户端和聊天编排服务，在 `admin` 暴露 `POST /api/agent/chat`。模型供应商 API Key 在管理接口新增/编辑时加密保存，模型调用前解密。

**Tech Stack:** Java 8, Spring Boot 2.7.18, MyBatis-Plus, Fastjson2, Spring `RestTemplate`, existing `AesUtil`, existing `ServerException`, existing `WebResponse`.

---

## File Structure

- Create `api/src/main/java/com/aether/agent/model/ModelChatMessage.java`: 模型上下文消息 DTO。
- Create `api/src/main/java/com/aether/agent/model/ModelChatRequest.java`: 模型调用请求 DTO。
- Create `api/src/main/java/com/aether/agent/model/ModelChatResponse.java`: 模型调用响应 DTO。
- Create `api/src/main/java/com/aether/agent/model/ModelClient.java`: 模型客户端抽象接口。
- Create `api/src/main/java/com/aether/agent/service/AgentChatService.java`: Agent 聊天服务接口。
- Create `biz/src/main/java/com/aether/agent/model/ModelClientFactory.java`: 模型客户端选择器。
- Create `biz/src/main/java/com/aether/agent/model/OpenAIModelClient.java`: OpenAI 兼容非流式客户端。
- Create `biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`: 聊天闭环编排服务。
- Create `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`: 聊天 REST Controller。
- Modify `admin/src/main/java/com/aether/agent/controller/ModelProviderController.java`: 保存时加密 API Key，响应不返回 API Key。
- Modify `api/src/main/java/com/aether/agent/vo/ModelProviderVo.java`: 增加 `apiKey` 字段，仅用于接收和响应脱敏控制。
- Modify `docs/agent-platform/TASKS.md`: 实现完成并验证后勾选 V0.3 对应任务。

---

### Task 1: API Contracts

**Files:**
- Create: `api/src/main/java/com/aether/agent/model/ModelChatMessage.java`
- Create: `api/src/main/java/com/aether/agent/model/ModelChatRequest.java`
- Create: `api/src/main/java/com/aether/agent/model/ModelChatResponse.java`
- Create: `api/src/main/java/com/aether/agent/model/ModelClient.java`
- Create: `api/src/main/java/com/aether/agent/service/AgentChatService.java`

- [ ] **Step 1: Add model DTOs and client interface**

Implement immutable-enough Lombok DTOs with Java 8-compatible classes and a `ModelClient` interface:

```java
public interface ModelClient {
    boolean supports(String providerType);
    ModelChatResponse chat(ModelChatRequest request);
}
```

- [ ] **Step 2: Add AgentChatService interface**

```java
public interface AgentChatService {
    AgentMessageVo chat(AgentChatDto dto);
}
```

- [ ] **Step 3: Compile API module**

Run: Maven compile through the reactor after all tasks, because downstream implementations are added in later tasks.

---

### Task 2: Model Provider API Key Encryption

**Files:**
- Modify: `api/src/main/java/com/aether/agent/vo/ModelProviderVo.java`
- Modify: `admin/src/main/java/com/aether/agent/controller/ModelProviderController.java`

- [ ] **Step 1: Add `apiKey` to ModelProviderVo**

Add a String `apiKey` field so Spring/BeanUtils can set it to `null` before responses.

- [ ] **Step 2: Encrypt on create/update**

In `ModelProviderController`, call `AesUtil.encrypt(dto.getApiKey())` when `apiKey` is not blank.

- [ ] **Step 3: Do not overwrite API Key with blank update**

When update DTO has blank `apiKey`, set entity API key to `null` so MyBatis-Plus does not update that column.

- [ ] **Step 4: Return `apiKey = null` in list/detail**

Set VO API key to null after `BeanUtils.copyProperties`.

---

### Task 3: Model Client Factory And OpenAI Client

**Files:**
- Create: `biz/src/main/java/com/aether/agent/model/ModelClientFactory.java`
- Create: `biz/src/main/java/com/aether/agent/model/OpenAIModelClient.java`

- [ ] **Step 1: Implement factory**

Inject `List<ModelClient>` and return the first supporting client; throw `ServerException(503, "不支持的模型供应商类型")` if none match.

- [ ] **Step 2: Implement OpenAI-compatible request**

Use `RestTemplate` with `SimpleClientHttpRequestFactory` timeouts. Send `model`, `messages`, `temperature`, `max_tokens`, `stream=false`.

- [ ] **Step 3: Implement response parsing**

Parse `choices[0].message.content`, `usage.prompt_tokens`, `usage.completion_tokens`, `usage.total_tokens`, and `model` from the JSON response.

- [ ] **Step 4: Convert provider errors**

Throw `ServerException(503, "模型供应商调用超时")` for timeout-like exceptions and `ServerException(500, "模型调用失败")` for other call/parse failures.

---

### Task 4: Agent Chat Service Implementation

**Files:**
- Create: `biz/src/main/java/com/aether/agent/service/impl/AgentChatServiceImpl.java`

- [ ] **Step 1: Validate request and current user**

Reject blank `agentId` or `message` with `ServerException(400, "参数错误")`. Reject missing `CurrentUser.getUser().get("userId")` with `ServerException(401, "未授权")`.

- [ ] **Step 2: Validate Agent and Provider**

Load `AgentDefinition` and `ModelProvider`, reject not found/deleted with `404`, reject disabled with `422`.

- [ ] **Step 3: Create or validate conversation**

Create conversation when `conversationId` is blank. Validate ownership, Agent match, not deleted, and not closed when provided.

- [ ] **Step 4: Persist user message and assemble context**

Save user message first. Load last 20 non-deleted messages from the conversation and convert `user`/`assistant` roles to model messages, with system prompt first.

- [ ] **Step 5: Call model and persist assistant message**

Use `ModelClientFactory` to call the selected model client. Save assistant message with token and latency fields.

- [ ] **Step 6: Persist run record and update conversation count**

Update `message_count` and save success `AgentRun`. On failure after request validation, save failed `AgentRun` with available IDs and error message, then rethrow.

---

### Task 5: Chat Controller

**Files:**
- Create: `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`

- [ ] **Step 1: Add controller**

Expose `POST /api/agent/chat`, annotate `@Permission(path = "/agent/chat")`, accept `AgentChatDto`, return `WebResponse<AgentMessageVo>`.

---

### Task 6: Verification And Task Status

**Files:**
- Modify: `docs/agent-platform/TASKS.md`

- [ ] **Step 1: Compile with workspace JDK**

Run:

```powershell
$env:JAVA_HOME = "C:\Users\23672\.jdks\ms-17.0.19"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
& "C:\Users\23672\.m2\wrapper\dists\apache-maven-3.9.10-bin\53h08a94dg6djh6umvruv7q564\apache-maven-3.9.10\bin\mvn.cmd" clean compile -DskipTests
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Update TASKS.md**

Mark V0.3 implementation items complete only after compile succeeds. Leave integration/manual model-call validation unchecked unless actually executed with a real provider.
