# Deep Agent 接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 `executionMode=DEEP` 的 Agent 接入外部 Deep Agent 异步服务，前端通过既有 SSE 接口接收 `run_step` 进度事件和 `done` 最终结果。

**Architecture:** 新增 `DeepAgentRunService` 负责构建外部请求并签名调用；新增 `DeepAgentCallbackController` 验签接收回调并幂等持久化；在 `AgentChatController` 中按 `executionMode` 路由，DEEP 模式拒绝非流式请求；新增 Flyway 迁移、步骤查询接口和取消接口。

**Tech Stack:** Java 8、Spring Boot 2.7.18、MyBatis-Plus、JUnit 5、Mockito、Flyway、Maven。

---

## 文件结构

- 新建：`api/src/main/resources/db/migration/postgresql/V8__deep_agent_integration.sql` — DDL 迁移
- 修改：`api/src/main/java/com/aether/agent/entity/AgentRun.java` — 已添加 `externalRunId`/`executionMode`，无需再改
- 修改：`api/src/main/java/com/aether/agent/entity/AgentDefinition.java` — 已添加 `executionMode`，无需再改
- 修改：`api/src/main/java/com/aether/agent/vo/AgentRunVo.java` — 暴露 `executionMode`/`externalRunId`
- 新建：`api/src/main/java/com/aether/agent/vo/AgentRunStepVo.java` — 步骤 VO
- 新建：`api/src/main/java/com/aether/agent/dto/DeepAgentConfig.java` — 配置属性
- 新建：`biz/src/main/java/com/aether/agent/service/DeepAgentSigningClient.java` — HMAC HTTP 客户端
- 新建：`biz/src/main/java/com/aether/agent/service/DelegationTokenService.java` — MCP 委托 JWT 签发
- 新建：`biz/src/main/java/com/aether/agent/service/DeepAgentRunService.java` — Deep Run 编排
- 新建：`admin/src/main/java/com/aether/agent/controller/DeepAgentCallbackController.java` — 回调入口
- 修改：`admin/src/main/java/com/aether/agent/controller/AgentRunController.java` — 新增步骤查询与取消
- 修改：`admin/src/main/java/com/aether/agent/controller/AgentChatController.java` — DEEP 路由与拒绝非流式
- 修改：`api/src/main/java/com/aether/agent/service/AgentRunStepService.java` — 新增幂等保存与按 runId 查询
- 修改：`biz/src/main/java/com/aether/agent/service/impl/AgentRunStepServiceImpl.java` — 实现幂等保存与查询
- 修改：`biz/src/main/java/com/aether/agent/service/ChatRunService.java` — 支持新状态与 Deep 字段

---

### Task 1: Flyway 数据库迁移

**Files:**
- Create: `api/src/main/resources/db/migration/postgresql/V8__deep_agent_integration.sql`

- [ ] **Step 1: 写入迁移 SQL**

```sql
ALTER TABLE agent_definition
    ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(16) NOT NULL DEFAULT 'STANDARD';

ALTER TABLE agent_run
    ADD COLUMN IF NOT EXISTS external_run_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS execution_mode VARCHAR(16);

CREATE TABLE IF NOT EXISTS agent_run_step (
    id VARCHAR(32) NOT NULL PRIMARY KEY,
    run_id VARCHAR(32) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    data TEXT,
    occurred_at BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT,
    updated_at BIGINT,
    sort_num INTEGER NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    state INTEGER NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS agent_run_step_uk_run_event
    ON agent_run_step (run_id, event_id) WHERE deleted = FALSE;
CREATE INDEX IF NOT EXISTS agent_run_step_idx_run_id ON agent_run_step (run_id);
CREATE INDEX IF NOT EXISTS agent_run_step_idx_occurred_at ON agent_run_step (occurred_at);
```

- [ ] **Step 2: 验证迁移可执行**

```powershell
mvn -pl admin -am -DskipTests install
mvn -pl admin -Dtest=FlywayMigrationSmokeTest test
```

Expected: 测试通过，无 SQL 错误。

- [ ] **Step 3: 提交**

使用中文提交信息：
```
git add api/src/main/resources/db/migration/postgresql/V8__deep_agent_integration.sql
git commit -m "feat: 新增 Deep Agent 数据库迁移"
```

---

### Task 2: 扩展运行状态与 VO

**Files:**
- Modify: `api/src/main/java/com/aether/agent/vo/AgentRunVo.java:68-68`
- Create: `api/src/main/java/com/aether/agent/vo/AgentRunStepVo.java`

- [ ] **Step 1: 在 `AgentRunVo` 末尾新增字段**

在 `AgentRunVo` 类体中，`endTime` 字段之后添加：

```java
    @ApiModelProperty(value = "执行模式：STANDARD 或 DEEP")
    private String executionMode;

    @ApiModelProperty(value = "外部 Deep Agent 运行 ID")
    private String externalRunId;
```

- [ ] **Step 2: 新建 `AgentRunStepVo`**

```java
package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class AgentRunStepVo extends BaseEntity {

    @ApiModelProperty(value = "运行记录 ID")
    private String runId;

    @ApiModelProperty(value = "外部事件 ID，幂等键")
    private String eventId;

    @ApiModelProperty(value = "事件类型：run.started / plan.updated / step.started / tool.started / tool.completed 等")
    private String eventType;

    @ApiModelProperty(value = "事件 JSON 数据")
    private String data;

    @ApiModelProperty(value = "事件发生时间戳（毫秒）")
    private Long occurredAt;
}
```

- [ ] **Step 3: 编译验证**

```powershell
mvn clean compile -pl api -am
```

Expected: 无编译错误。

---

### Task 3: 配置属性与 Deep Agent 客户端

**Files:**
- Create: `api/src/main/java/com/aether/agent/dto/DeepAgentConfig.java`
- Create: `biz/src/main/java/com/aether/agent/service/DeepAgentSigningClient.java`

- [ ] **Step 1: 创建配置类**

```java
package com.aether.agent.dto;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "aether.deep-agent")
public class DeepAgentConfig {
    private String baseUrl;
    private String sharedSecret;
    private String keyId = "deep-agent-v1";
    private String mcpDelegationSecret;
}
```

- [ ] **Step 2: 创建 HMAC 签名 HTTP 客户端**

```java
package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

@Component
public class DeepAgentSigningClient {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentSigningClient.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;

    private final DeepAgentConfig config;
    private final RestTemplate restTemplate;

    public DeepAgentSigningClient(DeepAgentConfig config) {
        this.config = config;
        this.restTemplate = new RestTemplate();
    }

    public <T> ResponseEntity<String> signedPost(String path, T body) {
        String url = config.getBaseUrl().replaceAll("/$", "") + path;
        byte[] bodyBytes = JSON.toJSONBytes(body);
        String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
        String signature = hmacSha256(config.getSharedSecret(), timestamp + "." + new String(bodyBytes, StandardCharsets.UTF_8));

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Aether-Key-Id", config.getKeyId());
        headers.set("X-Aether-Timestamp", timestamp);
        headers.set("X-Aether-Signature", signature);

        HttpEntity<byte[]> entity = new HttpEntity<>(bodyBytes, headers);
        log.info("Deep Agent request: POST {} body={}", url, JSON.toJSONString(body));
        return restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
    }

    private String hmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            throw new RuntimeException("HMAC signing failed", e);
        }
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
mvn clean compile -pl biz -am
```

Expected: 无编译错误。

---

### Task 4: 委托令牌服务

**Files:**
- Create: `biz/src/main/java/com/aether/agent/service/DelegationTokenService.java`

- [ ] **Step 1: 创建 MCP 委托 JWT 服务**

```java
package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

@Component
public class DelegationTokenService {

    private static final long TOKEN_TTL_MINUTES = 5;
    private final DeepAgentConfig config;

    public DelegationTokenService(DeepAgentConfig config) {
        this.config = config;
    }

    public String create(String runId, String userId, String agentId, List<String> allowedTools) {
        long now = System.currentTimeMillis();
        return JWT.create()
                .withClaim("runId", runId)
                .withClaim("userId", userId)
                .withClaim("agentId", agentId)
                .withClaim("allowedTools", allowedTools)
                .withIssuedAt(new Date(now))
                .withExpiresAt(new Date(now + TOKEN_TTL_MINUTES * 60 * 1000))
                .sign(Algorithm.HMAC256(config.getMcpDelegationSecret()));
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
mvn clean compile -pl biz -am
```

Expected: 无编译错误。`common` 已依赖 `com.auth0:java-jwt`。

---

### Task 5: 运行步骤服务扩展

**Files:**
- Modify: `api/src/main/java/com/aether/agent/service/AgentRunStepService.java:7-7`
- Modify: `biz/src/main/java/com/aether/agent/service/impl/AgentRunStepServiceImpl.java:10-11`

- [ ] **Step 1: 扩展服务接口**

```java
package com.aether.agent.service;

import com.aether.agent.entity.AgentRunStep;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

public interface AgentRunStepService extends IService<AgentRunStep> {

    /**
     * 按事件 ID 幂等保存步骤。
     *
     * @return true 为新插入，false 为重复事件已忽略
     */
    boolean saveIfAbsent(AgentRunStep step);

    /**
     * 按运行记录 ID 查询所有步骤，按发生时间和创建时间升序。
     */
    List<AgentRunStep> listByRunId(String runId);
}
```

- [ ] **Step 2: 实现幂等保存与查询**

```java
package com.aether.agent.service.impl;

import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.mapper.AgentRunStepMapper;
import com.aether.agent.service.AgentRunStepService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AgentRunStepServiceImpl extends ServiceImpl<AgentRunStepMapper, AgentRunStep> implements AgentRunStepService {
    private static final Logger log = LoggerFactory.getLogger(AgentRunStepServiceImpl.class);

    @Override
    public boolean saveIfAbsent(AgentRunStep step) {
        long count = count(Wrappers.lambdaQuery(AgentRunStep.class)
                .eq(AgentRunStep::getRunId, step.getRunId())
                .eq(AgentRunStep::getEventId, step.getEventId())
                .eq(AgentRunStep::getDeleted, false));
        if (count > 0) {
            log.info("重复事件已忽略: runId={} eventId={}", step.getRunId(), step.getEventId());
            return false;
        }
        return save(step);
    }

    @Override
    public List<AgentRunStep> listByRunId(String runId) {
        return list(Wrappers.lambdaQuery(AgentRunStep.class)
                .eq(AgentRunStep::getRunId, runId)
                .eq(AgentRunStep::getDeleted, false)
                .orderByAsc(AgentRunStep::getOccurredAt)
                .orderByAsc(AgentRunStep::getCreatedAt));
    }
}
```

- [ ] **Step 3: 编译验证**

```powershell
mvn clean compile -pl biz -am
```

---

### Task 6: Deep Agent 运行编排服务

**Files:**
- Create: `biz/src/main/java/com/aether/agent/service/DeepAgentRunService.java`

先在 `biz/src/test` 下写一个失败测试：

- [ ] **Step 1: 写出 Deep Run 失败测试**

Create: `biz/src/test/java/com/aether/agent/service/impl/DeepAgentRunServiceTest.java`

```java
package com.aether.agent.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentConversation;
import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.entity.AgentRunStep;
import com.aether.agent.entity.AgentTool;
import com.aether.agent.service.*;
import com.aether.agent.tools.AgentToolCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepAgentRunServiceTest {

    @Mock private DeepAgentSigningClient signingClient;
    @Mock private AgentRunService agentRunService;
    @Mock private AgentRunStepService agentRunStepService;
    @Mock private AgentConversationService agentConversationService;
    @Mock private AgentMessageService agentMessageService;
    @Mock private DelegationTokenService delegationTokenService;
    @Mock private AgentToolCatalog toolCatalog;
    @Mock private KnowledgeContextService knowledgeContextService;
    @Mock private DeepAgentConfig config;

    private DeepAgentRunService service;

    @BeforeEach
    void setUp() {
        when(config.getSharedSecret()).thenReturn("test-secret");
        when(config.getBaseUrl()).thenReturn("http://deep-agent:8010");
        service = new DeepAgentRunService(agentRunService, agentRunStepService,
                signingClient, agentConversationService, agentMessageService,
                delegationTokenService, toolCatalog, knowledgeContextService, config);
    }

    @Test
    void startRunCreatesLocalRunAndCallsExternalService() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-1"); agent.setSystemPrompt("你是助手");
        agent.setMaxToolRounds(5);

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-1");
            return true;
        });
        when(delegationTokenService.create(eq("run-1"), eq("user-1"), eq("agent-1"), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-1")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.ACCEPTED).body("{\"run_id\":\"run-1\",\"status\":\"QUEUED\",\"created\":true}"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-1");
        when(agentConversationService.getById("conversation-1")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-1");
            return true;
        });

        String runId = service.startRun(agent, "user-1", "conversation-1", "你好", Collections.emptyList());

        assertEquals("run-1", runId);

        ArgumentCaptor<AgentRun> runCaptor = ArgumentCaptor.forClass(AgentRun.class);
        verify(agentRunService).save(runCaptor.capture());
        assertEquals(3, runCaptor.getValue().getStatus());
        assertEquals("DEEP", runCaptor.getValue().getExecutionMode());
    }

    @Test
    void startRunFailsWhenExternalReturnsNon202() {
        AgentDefinition agent = new AgentDefinition();
        agent.setId("agent-2"); agent.setSystemPrompt("test");

        when(agentRunService.save(any(AgentRun.class))).thenAnswer(inv -> {
            AgentRun run = inv.getArgument(0);
            run.setId("run-2");
            return true;
        });
        when(delegationTokenService.create(anyString(), anyString(), anyString(), anyList()))
                .thenReturn("delegation-jwt");
        when(toolCatalog.getBoundTools("agent-2")).thenReturn(Collections.emptyList());
        when(signingClient.signedPost(eq("/v1/runs"), anyMap()))
                .thenReturn(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("error"));

        AgentConversation conversation = new AgentConversation();
        conversation.setId("conversation-2");
        when(agentConversationService.getById("conversation-2")).thenReturn(conversation);
        when(agentMessageService.save(any(AgentMessage.class))).thenAnswer(inv -> {
            AgentMessage msg = inv.getArgument(0);
            msg.setId("message-2");
            return true;
        });

        String runId = service.startRun(agent, "user-2", "conversation-2", "hi", Collections.emptyList());

        assertEquals("run-2", runId);
        verify(agentRunService).updateById(argThat(r ->
                ((AgentRun) r).getStatus() == 1 && ((AgentRun) r).getErrorMsg() != null));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

```powershell
mvn -pl biz -am -Dtest=DeepAgentRunServiceTest -DfailIfNoTests=false test
```

Expected: 编译错误 `DeepAgentRunService` 类不存在。

- [ ] **Step 3: 实现 `DeepAgentRunService`**

```java
package com.aether.agent.service;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.*;
import com.aether.agent.tools.AgentToolCatalog;
import com.alibaba.fastjson2.JSON;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeepAgentRunService {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentRunService.class);
    private static final int STATUS_QUEUED = 3;
    private static final int STATUS_FAILED = 1;

    private final AgentRunService agentRunService;
    private final AgentRunStepService agentRunStepService;
    private final DeepAgentSigningClient signingClient;
    private final AgentConversationService agentConversationService;
    private final AgentMessageService agentMessageService;
    private final DelegationTokenService delegationTokenService;
    private final AgentToolCatalog toolCatalog;
    private final KnowledgeContextService knowledgeContextService;
    private final DeepAgentConfig config;

    public DeepAgentRunService(AgentRunService agentRunService,
                               AgentRunStepService agentRunStepService,
                               DeepAgentSigningClient signingClient,
                               AgentConversationService agentConversationService,
                               AgentMessageService agentMessageService,
                               DelegationTokenService delegationTokenService,
                               AgentToolCatalog toolCatalog,
                               KnowledgeContextService knowledgeContextService,
                               DeepAgentConfig config) {
        this.agentRunService = agentRunService;
        this.agentRunStepService = agentRunStepService;
        this.signingClient = signingClient;
        this.agentConversationService = agentConversationService;
        this.agentMessageService = agentMessageService;
        this.delegationTokenService = delegationTokenService;
        this.toolCatalog = toolCatalog;
        this.knowledgeContextService = knowledgeContextService;
        this.config = config;
    }

    public String startRun(AgentDefinition agent, String userId, String conversationId,
                           String task, List<Map<String, Object>> sources) {
        AgentConversation conversation = agentConversationService.getById(conversationId);

        AgentMessage userMsg = new AgentMessage();
        userMsg.setConversationId(conversationId);
        userMsg.setRole("user");
        userMsg.setContent(task);
        userMsg.setMessageType("chat");
        agentMessageService.save(userMsg);

        AgentRun run = new AgentRun();
        run.setAgentDefinitionId(agent.getId());
        run.setUserId(userId);
        run.setConversationId(conversationId);
        run.setMessageId(userMsg.getId());
        run.setInputContent(truncate(task));
        run.setStatus(STATUS_QUEUED);
        run.setExecutionMode("DEEP");
        run.setModel(agent.getModel());
        agentRunService.save(run);
        String runId = run.getId();

        try {
            List<String> allowedTools = toolCatalog.getBoundTools(agent.getId()).stream()
                    .filter(t -> t.getMcpToolName() != null)
                    .map(AgentTool::getMcpToolName)
                    .collect(Collectors.toList());

            String delegationToken = delegationTokenService.create(runId, userId, agent.getId(), allowedTools);

            List<Map<String, Object>> knowledgeSources = buildKnowledgeSources(sources);

            Map<String, Object> request = new LinkedHashMap<>();
            request.put("run_id", runId);
            request.put("user_id", userId);
            request.put("agent_id", agent.getId());
            request.put("conversation_id", conversationId);
            request.put("task", task);
            request.put("system_prompt", agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "");
            request.put("knowledge_sources", knowledgeSources);
            request.put("allowed_tools", allowedTools);
            request.put("delegation_token", delegationToken);
            if (agent.getMaxToolRounds() != null) {
                request.put("max_steps", agent.getMaxToolRounds());
            }

            ResponseEntity<String> response = signingClient.signedPost("/v1/runs", request);
            if (response.getStatusCode() != HttpStatus.ACCEPTED) {
                throw new RuntimeException("外部服务返回非 202: " + response.getStatusCodeValue());
            }
            log.info("Deep Agent run created: runId={}", runId);
            return runId;
        } catch (Exception e) {
            log.error("创建 Deep Agent 运行失败: runId={}", runId, e);
            agentRunService.updateById(buildFailureRun(runId, e.getMessage()));
            throw new RuntimeException("创建 Deep Agent 运行失败: " + e.getMessage(), e);
        }
    }

    public void handleCallback(String runId, String eventId, String eventType, long occurredAt, String dataJson) {
        AgentRunStep step = new AgentRunStep();
        step.setRunId(runId);
        step.setEventId(eventId);
        step.setEventType(eventType);
        step.setData(dataJson);
        step.setOccurredAt(occurredAt);
        agentRunStepService.saveIfAbsent(step);
    }

    public void markRunning(String runId) {
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(4); // RUNNING
        agentRunService.updateById(update);
    }

    public void markSucceeded(String runId, String content, String model,
                              Integer promptTokens, Integer completionTokens, Integer totalTokens) {
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(0);
        update.setOutputContent(truncate(content));
        update.setModel(model);
        update.setPromptTokens(promptTokens);
        update.setCompletionTokens(completionTokens);
        update.setTotalTokens(totalTokens);
        agentRunService.updateById(update);
    }

    public void markFailed(String runId, String errorMsg) {
        AgentRun update = buildFailureRun(runId, errorMsg);
        update.setId(runId);
        agentRunService.updateById(update);
    }

    public void markCancelled(String runId) {
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(5);
        agentRunService.updateById(update);
    }

    private AgentRun buildFailureRun(String runId, String errorMsg) {
        AgentRun update = new AgentRun();
        update.setId(runId);
        update.setStatus(STATUS_FAILED);
        update.setErrorMsg(truncate(errorMsg));
        return update;
    }

    private List<Map<String, Object>> buildKnowledgeSources(List<Map<String, Object>> sources) {
        if (sources == null || sources.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> src : sources) {
            Map<String, Object> ks = new LinkedHashMap<>();
            String title = stringValue(src.get("documentName"));
            String content = stringValue(src.get("content"));
            Integer citationIndex = src.get("citationIndex") instanceof Integer ? (Integer) src.get("citationIndex") : null;

            ks.put("title", title != null ? title : "");
            ks.put("content", content != null ? content : "");
            ks.put("citation", citationIndex != null ? "【" + citationIndex + "】" : "");
            result.add(ks);
        }
        return result;
    }

    private String truncate(String value) {
        return value == null || value.length() <= 1024 ? value : value.substring(0, 1024);
    }

    private String stringValue(Object obj) {
        return obj == null ? null : obj.toString();
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

```powershell
mvn -pl biz -am -Dtest=DeepAgentRunServiceTest -DfailIfNoTests=false test
```

Expected: 两个测试通过。

---

### Task 7: 回调控制器

**Files:**
- Create: `admin/src/main/java/com/aether/agent/controller/DeepAgentCallbackController.java`

- [ ] **Step 1: 创建回调控制器**

```java
package com.aether.agent.controller;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.service.AgentChatService;
import com.aether.agent.service.AgentStreamCallback;
import com.aether.agent.service.DeepAgentRunService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/agent/deep-runs")
public class DeepAgentCallbackController {
    private static final Logger log = LoggerFactory.getLogger(DeepAgentCallbackController.class);
    private static final long MAX_SIGNATURE_AGE_SECONDS = 300;
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final DeepAgentRunService deepAgentRunService;
    private final DeepAgentConfig config;
    private final Map<String, AgentStreamCallback> activeCallbacks = new ConcurrentHashMap<>();

    public DeepAgentCallbackController(DeepAgentRunService deepAgentRunService, DeepAgentConfig config) {
        this.deepAgentRunService = deepAgentRunService;
        this.config = config;
    }

    public void registerCallback(String runId, AgentStreamCallback callback) {
        activeCallbacks.put(runId, callback);
    }

    public void removeCallback(String runId) {
        activeCallbacks.remove(runId);
    }

    @PostMapping("/callback/{runId}")
    public ResponseEntity<Void> callback(@PathVariable String runId, HttpServletRequest request) {
        try {
            if (!verifySignature(request)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
            }

            String body = request.getReader().lines().collect(Collectors.joining());
            JSONObject event = JSON.parseObject(body);

            String eventRunId = event.getString("run_id");
            if (!runId.equals(eventRunId)) {
                log.warn("回调 run_id 不匹配: path={} body={}", runId, eventRunId);
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
            }

            String eventId = event.getString("event_id");
            String eventType = event.getString("event_type");
            long occurredAt = event.getLongValue("occurred_at", 0L);
            String dataJson = event.getString("data") != null ? event.getString("data") : "{}";

            deepAgentRunService.handleCallback(runId, eventId, eventType, occurredAt, dataJson);

            AgentStreamCallback callback = activeCallbacks.get(runId);
            if (callback != null && !callback.isClosed()) {
                JSONObject stepEvent = new JSONObject();
                stepEvent.put("runId", runId);
                stepEvent.put("eventId", eventId);
                stepEvent.put("eventType", eventType);
                stepEvent.put("occurredAt", occurredAt);
                stepEvent.put("data", JSON.parseObject(dataJson));
                callback.onRunStep(runId, stepEvent.toJSONString());
            }

            switch (eventType) {
                case "run.started":
                    deepAgentRunService.markRunning(runId);
                    break;
                case "run.completed":
                    handleCompleted(runId, dataJson, callback);
                    break;
                case "run.failed":
                    deepAgentRunService.markFailed(runId, JSON.parseObject(dataJson).getString("error"));
                    if (callback != null) callback.onError(500, JSON.parseObject(dataJson).getString("error"));
                    break;
                case "run.cancelled":
                    deepAgentRunService.markCancelled(runId);
                    if (callback != null) callback.onError(0, "运行已取消");
                    break;
            }

            return ResponseEntity.ok().build();
        } catch (Exception e) {
            log.error("回调处理失败: runId={}", runId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private void handleCompleted(String runId, String dataJson, AgentStreamCallback callback) {
        JSONObject data = JSON.parseObject(dataJson);
        String content = data.getString("content");
        String model = data.getString("model");
        Integer promptTokens = data.getInteger("promptTokens");
        Integer completionTokens = data.getInteger("completionTokens");
        Integer totalTokens = data.getInteger("totalTokens");
        deepAgentRunService.markSucceeded(runId, content, model, promptTokens, completionTokens, totalTokens);
    }

    private boolean verifySignature(HttpServletRequest request) throws Exception {
        String keyId = request.getHeader("X-Aether-Key-Id");
        String timestamp = request.getHeader("X-Aether-Timestamp");
        String signature = request.getHeader("X-Aether-Signature");
        if (keyId == null || timestamp == null || signature == null) return false;
        if (!config.getKeyId().equals(keyId)) return false;

        long ts;
        try { ts = Long.parseLong(timestamp); } catch (NumberFormatException e) { return false; }
        if (Math.abs(System.currentTimeMillis() / 1000 - ts) > MAX_SIGNATURE_AGE_SECONDS) return false;

        ServletInputStream inputStream = request.getInputStream();
        byte[] bodyBytes = inputStream.readAllBytes();
        String payload = timestamp + "." + new String(bodyBytes, StandardCharsets.UTF_8);
        String expected = hmacSha256(config.getSharedSecret(), payload);

        return signature.equals(expected);
    }

    private String hmacSha256(String secret, String payload) throws Exception {
        Mac mac = Mac.getInstance(HMAC_ALGORITHM);
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
        byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        StringBuilder hex = new StringBuilder();
        for (byte b : hash) hex.append(String.format("%02x", b));
        return hex.toString();
    }
}
```

- [ ] **Step 2: 编译验证**

```powershell
mvn clean compile -pl admin -am
```

---

### Task 8: 运行控制器扩展 —— 步骤查询与取消

**Files:**
- Modify: `admin/src/main/java/com/aether/agent/controller/AgentRunController.java:95-95`

- [ ] **Step 1: 新增步骤查询和取消接口**

在 `AgentRunController` 中添加 `AgentRunStepService` 依赖，并在类末尾新增两个接口：

```java
    private final AgentRunStepService agentRunStepService;
    private final DeepAgentSigningClient signingClient;
    private final DeepAgentConfig deepAgentConfig;
```

修改构造函数注入这三个新依赖。

新增步骤查询：

```java
    @ApiOperation("运行步骤列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/steps")
    public WebResponse<List<AgentRunStepVo>> steps(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        List<AgentRunStepVo> steps = agentRunStepService.listByRunId(id).stream().map(item -> {
            AgentRunStepVo vo = new AgentRunStepVo();
            BeanUtils.copyProperties(item, vo);
            return vo;
        }).collect(Collectors.toList());
        return WebResponse.OK(steps);
    }
```

新增取消接口：

```java
    @ApiOperation("取消 Deep Agent 运行")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    @PostMapping("/{id}/cancel")
    public WebResponse<Void> cancel(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        if (!"DEEP".equals(run.getExecutionMode())) {
            throw new ServerException(422, "仅 Deep Agent 运行支持取消");
        }
        try {
            Map<String, String> cancelBody = new HashMap<>();
            cancelBody.put("run_id", id);
            signingClient.signedPost("/v1/runs/" + id + "/cancel", cancelBody);
        } catch (Exception e) {
            log.warn("取消 Deep Agent 运行请求失败: runId={}", id, e);
        }
        return WebResponse.OK();
    }
```

- [ ] **Step 2: 编译验证**

```powershell
mvn clean compile -pl admin -am
```

---

### Task 9: 聊天控制器 Deep 模式路由

**Files:**
- Modify: `admin/src/main/java/com/aether/agent/controller/AgentChatController.java`

- [ ] **Step 1: 非流式端点拒绝 Deep Agent**

在 `AgentChatController.chat()` 方法开头添加：

```java
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public WebResponse<AgentMessageVo> chat(@RequestBody AgentChatDto dto) {
        AgentDefinition agent = agentDefinitionService.getById(dto.getAgentId());
        if ("DEEP".equals(agent.getExecutionMode())) {
            throw new ServerException(422, "Deep Agent 仅支持流式聊天，请使用 /api/agent/chat/stream");
        }
        return WebResponse.OK(agentChatService.chat(dto));
    }
```

- [ ] **Step 2: 流式端点路由 Deep Agent**

在 `AgentChatController` 中新增 `deepAgentRunService` 和 `deepAgentCallbackController` 依赖注入。

在 `stream()` 方法中，校验后、创建 SSE 前分支：

```java
    @PostMapping(value = "/stream", ...)
    public SseEmitter stream(@RequestBody AgentChatDto dto, HttpServletResponse response) {
        AgentDefinition agent = agentDefinitionService.getById(dto.getAgentId());
        if (agent == null || Boolean.TRUE.equals(agent.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        if ("DEEP".equals(agent.getExecutionMode())) {
            return streamDeep(dto, response, agent);
        }
        return openStream(dto, response);
    }
```

新增 `streamDeep` 方法：

```java
    private SseEmitter streamDeep(AgentChatDto dto, HttpServletResponse response, AgentDefinition agent) {
        String userId = CurrentUser.getUser() != null ? CurrentUser.getUser().get("userId") : null;
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, I18nUtils.getMessage("agent.unauthorized"));
        }

        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MS);
        AtomicBoolean closed = new AtomicBoolean(false);
        emitter.onCompletion(() -> { closed.set(true); });
        emitter.onTimeout(() -> { closed.set(true); emitter.complete(); });
        emitter.onError(error -> closed.set(true));

        try { emitter.send(SseEmitter.event().comment("connected")); }
        catch (IOException e) { closed.set(true); }

        ScheduledFuture<?> heartbeatTask = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) return;
            try { emitter.send(SseEmitter.event().comment("heartbeat")); }
            catch (Exception e) { closed.set(true); }
        }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);

        streamExecutor.execute(() -> {
            try {
                // 创建会话和用户消息
                AgentConversation conversation = agentConversationService.getOne(
                        Wrappers.lambdaQuery(AgentConversation.class)
                                .eq(AgentConversation::getAgentDefinitionId, agent.getId())
                                .eq(AgentConversation::getUserId, userId)
                                .eq(AgentConversation::getStatus, 0)
                                .eq(AgentConversation::getDeleted, false)
                                .orderByDesc(AgentConversation::getCreatedAt)
                                .last("LIMIT 1"));
                if (conversation == null) {
                    conversation = new AgentConversation();
                    conversation.setUserId(userId);
                    conversation.setAgentDefinitionId(agent.getId());
                    conversation.setStatus(0);
                    agentConversationService.save(conversation);
                }

                // 检索知识库
                com.aether.agent.model.ModelChatMessage sysMsg =
                        new com.aether.agent.model.ModelChatMessage("system", agent.getSystemPrompt() != null ? agent.getSystemPrompt() : "");
                List<com.aether.agent.model.ModelChatMessage> ctx = java.util.Collections.singletonList(sysMsg);
                List<Map<String, Object>> sources = knowledgeContextService.enhance(ctx, userId, conversation.getId(), agent.getId(), dto.getMessage());

                String runId = deepAgentRunService.startRun(agent, userId, conversation.getId(), dto.getMessage(), sources);
                deepAgentCallbackController.registerCallback(runId, new AgentStreamCallback() {
                    @Override public void onMessage(String cid, String chunk) {}
                    @Override public void onReasoning(String cid, String chunk) {}
                    @Override public void onToolCall(String cid, String toolCallJson) {}

                    @Override
                    public void onRunStep(String runId, String stepJson) {
                        if (closed.get()) return;
                        try { emitter.send(SseEmitter.event().name("run_step").data(stepJson)); }
                        catch (IOException e) { closed.set(true); }
                    }

                    @Override
                    public void onQuestion(String cid, String rid, AgentMessageVo q) {}
                    @Override public void onDone(String cid, String mid,
                            com.aether.agent.model.ModelStreamResponse response) {
                        if (closed.get()) return;
                        try {
                            JSONObject done = new JSONObject();
                            done.put("conversationId", cid);
                            done.put("messageId", mid);
                            done.put("runId", runId);
                            if (response != null) {
                                done.put("content", response.getContent());
                                done.put("model", response.getModel());
                                done.put("promptTokens", response.getPromptTokens());
                                done.put("completionTokens", response.getCompletionTokens());
                                done.put("totalTokens", response.getTotalTokens());
                                done.put("sources", response.getSources());
                            }
                            emitter.send(SseEmitter.event().name("done").data(done.toJSONString()));
                            closed.set(true);
                            emitter.complete();
                        } catch (Exception ignored) { closed.set(true); }
                    }

                    @Override public void onError(int code, String message) {
                        if (closed.get()) return;
                        try {
                            JSONObject err = new JSONObject();
                            err.put("code", code);
                            err.put("message", message);
                            emitter.send(SseEmitter.event().name("error").data(err.toJSONString()));
                            closed.set(true);
                            emitter.complete();
                        } catch (Exception ignored) { closed.set(true); }
                    }
                    @Override public boolean isClosed() { return closed.get(); }
                });
            } catch (Exception e) {
                log.error("Deep Agent 流式启动失败", e);
                if (!closed.get()) {
                    try {
                        JSONObject err = new JSONObject();
                        err.put("code", 500);
                        err.put("message", "Deep Agent 启动失败: " + e.getMessage());
                        emitter.send(SseEmitter.event().name("error").data(err.toJSONString()));
                        closed.set(true);
                        emitter.complete();
                    } catch (IOException ignored) {}
                }
            } finally {
                heartbeatTask.cancel(false);
            }
        });
        return emitter;
    }
```

- [ ] **Step 3: 在 `AgentStreamCallback` 接口中新增方法**

```java
default void onRunStep(String runId, String stepJson) {}
```

- [ ] **Step 4: 编译验证**

```powershell
mvn clean compile -pl admin -am
```

---

### Task 10: 后端集成测试

**Files:**
- Create: `admin/src/test/java/com/aether/agent/controller/DeepAgentCallbackControllerTest.java`
- Create: `biz/src/test/java/com/aether/agent/service/impl/DeepAgentRunServiceTest.java`

- [ ] **Step 1: 验证编译**

```powershell
mvn clean compile -DskipTests
```

- [ ] **Step 2: 运行现有 admin 测试**

```powershell
mvn -pl admin -am -DskipTests install
mvn -pl admin -Dtest=AgentConversationControllerTest,AgentToolControllerTest test
```

Expected: 已有测试通过。

- [ ] **Step 3: 运行 biz 新测试**

```powershell
mvn -pl biz -am -Dtest=DeepAgentRunServiceTest -DfailIfNoTests=false test
```

Expected: 两个 Deep Run 测试通过。

- [ ] **Step 4: 整体验证**

```powershell
mvn clean compile -DskipTests
mvn test -pl biz -am -DfailIfNoTests=false
mvn test -pl admin -am -DfailIfNoTests=false
```

Expected: 无编译错误，所有测试通过。

---

## 执行顺序与依赖

1. Task 1（迁移）→ Task 2（VO）→ Task 3（配置与客户端）→ Task 4（委托令牌）→ Task 5（步骤服务）
2. Task 6（编排服务 + 单测）→ Task 7（回调控制器）→ Task 8（运行控制器）→ Task 9（聊天路由）
3. Task 10（验证）

Task 1-5 之间无严格顺序依赖，可并行执行。Task 6 依赖 3/4/5。Task 7 依赖 6。Task 8/9 可并行执行，均依赖 6/7。
