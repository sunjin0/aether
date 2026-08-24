package com.aether.agent.skill.service.impl;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.entity.AgentMessage;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentMessageService;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.skill.dto.ArtifactGenerationRequestDto;
import com.aether.agent.skill.entity.*;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.agent.skill.vo.ArtifactGenerationVo;
import com.aether.agent.skill.vo.SandboxExecutionTaskVo;
import com.aether.agent.sandbox.dto.SandboxTaskCreateDto;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.storage.service.ObjectStorageService;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.Data;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;
import java.io.ByteArrayInputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipEntry;

/**
 * Issues immutable, one-time sandbox jobs.  No user-controlled execution primitive crosses this boundary.
 */
@Service
public class SkillArtifactExecutionServiceImpl implements SkillArtifactExecutionService {
    private static final String PLATFORM_GENERIC_ARTIFACT_VERSION = "__platform_generic_artifact__";
    private static final String PLATFORM_GENERIC_ENTRY = "__platform_generic_renderer__";
    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    private final AgentSandboxExecutionServiceImpl executionService;
    private final AgentArtifactServiceImpl artifactService;
    private final ObjectStorageService storage;
    private final DeepAgentConfig deepAgentConfig;
    private final AgentRunService runService;
    private final AgentMessageService messageService;
    private final String artifactBucket;
    private final String runnerToken;
    private final SandboxTaskService sandboxTaskService;

    /**
     * 创建 {@code SkillArtifactExecutionServiceImpl} 实例。
     */
    public SkillArtifactExecutionServiceImpl(AgentSandboxExecutionServiceImpl executionService,
                                             AgentArtifactServiceImpl artifactService, ObjectStorageService storage, DeepAgentConfig deepAgentConfig,
                                             AgentRunService runService, AgentMessageService messageService,
                                             SandboxTaskService sandboxTaskService,
                                             @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket,
                                             @Value("${aether.sandbox.runner-token:${AETHER_SANDBOX_RUNNER_TOKEN:}}") String runnerToken) {
        this.executionService = executionService;
        this.artifactService = artifactService;
        this.storage = storage;
        this.deepAgentConfig = deepAgentConfig;
        this.runService = runService;
        this.messageService = messageService;
        this.artifactBucket = artifactBucket;
        this.runnerToken = runnerToken;
        this.sandboxTaskService = sandboxTaskService;
    }

    /**
     * 处理request。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ArtifactGenerationVo request(String delegatedToken, ArtifactGenerationRequestDto request) {
        DecodedJWT token = verifyDelegation(delegatedToken);
        if (request == null || StringUtils.isBlank(request.getFormat()) || StringUtils.isBlank(request.getContent())) {
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.request.invalid"));
        }
        String userId = token.getClaim("userId").asString();
        String agentId = token.getClaim("agentId").asString();
        String runId = token.getClaim("runId").asString();
        if (StringUtils.isAnyBlank(userId, agentId, runId))
            throw new ServerException(401, I18nUtils.getMessage("skill.artifact.delegation.context.incomplete"));
        ArtifactExecutionPolicy config = platformGenericConfig(request.getFormat());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", StringUtils.defaultIfBlank(request.getTitle(), "generated"));
        input.put("fileName", request.getFileName());
        input.put("format", request.getFormat().toLowerCase(Locale.ROOT));
        input.put("content", request.getContent());
        input.put("document", request.getDocument() == null ? Collections.emptyMap() : request.getDocument());
        AgentSandboxExecution execution = new AgentSandboxExecution();
        execution.setRunId(runId);
        execution.setSkillVersionId(PLATFORM_GENERIC_ARTIFACT_VERSION);
        execution.setUserId(userId);
        execution.setAgentDefinitionId(agentId);
        execution.setExecutionConfigSnapshot(JSON.toJSONString(config));
        execution.setResourceSnapshot("[]");
        execution.setInputJson(JSON.toJSONString(input));
        execution.setTokenHash(sha256(randomToken()));
        execution.setStatus(0);
        execution.setExpiresAt(System.currentTimeMillis() + TTL_MILLIS);
        executionService.save(execution);
        // Keep the legacy dispatch ticket for the deployed Runner while making the
        // template task the auditable control-plane record.
        SandboxTaskCreateDto taskRequest = new SandboxTaskCreateDto();
        taskRequest.setTemplateCode("generic-document");
        taskRequest.setAgentDefinitionId(agentId);
        taskRequest.setRunId(runId);
        taskRequest.setInput(input);
        String taskId = sandboxTaskService.create(userId, taskRequest).getId();
        sandboxTaskService.linkLegacyExecution(taskId, execution.getId());
        ArtifactGenerationVo result = new ArtifactGenerationVo();
        result.setExecutionId(execution.getId());
        result.setRunId(execution.getRunId());
        result.setStatus("queued");
        return result;
    }

    /**
     * 处理platformGeneric配置。
     */
    private ArtifactExecutionPolicy platformGenericConfig(String format) {
        String normalized = StringUtils.lowerCase(StringUtils.trim(format));
        if (!Arrays.asList("docx", "xlsx", "pdf").contains(normalized)) {
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.not-declared"));
        }
        ArtifactExecutionPolicy config = new ArtifactExecutionPolicy();
        config.setEntryResourceId(PLATFORM_GENERIC_ENTRY);
        config.setRuntime("PYTHON");
        config.setOutputFormats(JSON.toJSONString(Collections.singletonList(normalized)));
        config.setTimeoutSeconds(60);
        config.setMaxOutputFiles(1);
        config.setMaxOutputBytes(52_428_800L);
        return config;
    }

    /**
     * 认领下一个待处理任务。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SandboxExecutionTaskVo claimNext(String suppliedRunnerToken) {
        requireRunner(suppliedRunnerToken);
        long now = System.currentTimeMillis();
        // A Runner outage must not leave users with an eternal "queued" task.
        executionService.update(Wrappers.lambdaUpdate(AgentSandboxExecution.class)
                .set(AgentSandboxExecution::getStatus, 4)
                .set(AgentSandboxExecution::getCompletedAt, now)
                .set(AgentSandboxExecution::getFailureReason, "Execution request expired before being claimed")
                .eq(AgentSandboxExecution::getStatus, 0)
                .le(AgentSandboxExecution::getExpiresAt, now));
        List<AgentSandboxExecution> candidates = executionService.list(Wrappers.lambdaQuery(AgentSandboxExecution.class).eq(AgentSandboxExecution::getStatus, 0)
                .gt(AgentSandboxExecution::getExpiresAt, now).orderByAsc(AgentSandboxExecution::getCreatedAt).last("limit 32"));
        AgentSandboxExecution job = candidates.stream().filter(item -> sandboxTaskService.legacyReadyForClaim(item.getId())).findFirst().orElse(null);
        if (job == null) return null;
        String executionToken = JWT.create().withClaim("executionId", job.getId()).withExpiresAt(new Date(job.getExpiresAt()))
                .sign(Algorithm.HMAC256(runnerToken));
        // Several Runner replicas may observe the same queued row.  The
        // compare-and-set transition is the one-time claim boundary: only the
        // Runner that changes status 0 -> 1 receives the execution token.
        long startedAt = System.currentTimeMillis();
        boolean claimed = executionService.update(Wrappers.lambdaUpdate(AgentSandboxExecution.class)
                .set(AgentSandboxExecution::getTokenHash, sha256(executionToken))
                .set(AgentSandboxExecution::getStatus, 1)
                .set(AgentSandboxExecution::getStartedAt, startedAt)
                .eq(AgentSandboxExecution::getId, job.getId())
                .eq(AgentSandboxExecution::getStatus, 0)
                .gt(AgentSandboxExecution::getExpiresAt, startedAt));
        if (!claimed) return null;
        sandboxTaskService.legacyExecutionStarted(job.getId(), "compatibility-runner");
        ArtifactExecutionPolicy config = JSON.parseObject(job.getExecutionConfigSnapshot(), ArtifactExecutionPolicy.class);
        List<AgentSkillResource> resources = JSON.parseArray(job.getResourceSnapshot(), AgentSkillResource.class);
        SandboxExecutionTaskVo task = new SandboxExecutionTaskVo();
        task.setExecutionId(job.getId());
        task.setExecutionToken(executionToken);
        task.setRunId(job.getRunId());
        task.setSkillVersionId(job.getSkillVersionId());
        task.setRuntime(config.getRuntime());
        task.setEntryResourceId(config.getEntryResourceId());
        task.setOutputFormats(JSON.parseArray(config.getOutputFormats(), String.class));
        task.setTimeoutSeconds(config.getTimeoutSeconds());
        task.setMaxOutputFiles(config.getMaxOutputFiles());
        task.setMaxOutputBytes(config.getMaxOutputBytes());
        Map<String, Object> frozenInput = new LinkedHashMap<>();
        Map parsedInput = JSON.parseObject(job.getInputJson(), Map.class);
        if (parsedInput != null) frozenInput.putAll(parsedInput);
        task.setInput(frozenInput);
        task.setResources(resources.stream().map(r -> {
            SandboxExecutionTaskVo.Resource item = new SandboxExecutionTaskVo.Resource();
            item.setId(r.getId());
            item.setName(r.getName());
            item.setType(r.getType());
            item.setLanguage(r.getLanguage());
            item.setContentSha256(r.getContentSha256());
            item.setSize(r.getSize());
            return item;
        }).collect(Collectors.toList()));
        return task;
    }

    /**
     * 处理complete。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void complete(String suppliedRunnerToken, String executionToken, String executionId, String fileName, String contentType, byte[] content, String checksum, String logSummary, boolean finalArtifact) {
        requireRunner(suppliedRunnerToken);
        AgentSandboxExecution job = running(executionId);
        requireExecutionToken(job, executionToken);
        ArtifactExecutionPolicy config = JSON.parseObject(job.getExecutionConfigSnapshot(), ArtifactExecutionPolicy.class);
        if (content == null || content.length == 0 || content.length > config.getMaxOutputBytes())
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.size.invalid"));
        String extension = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        List<String> formats = JSON.parseArray(config.getOutputFormats(), String.class);
        if (!formats.contains(extension))
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.not-declared"));
        validateContentType(extension, contentType);
        validateArtifactFormat(extension, content);
        if (artifactService.count(Wrappers.lambdaQuery(AgentArtifact.class).eq(AgentArtifact::getExecutionId, job.getId())) >= config.getMaxOutputFiles())
            throw new ServerException(409, I18nUtils.getMessage("skill.artifact.count.exceeded"));
        String actualHash = sha256(content);
        if (!actualHash.equals(checksum))
            throw new ServerException(409, I18nUtils.getMessage("skill.artifact.checksum-mismatch"));
        AgentArtifact artifact = new AgentArtifact();
        artifact.setExecutionId(job.getId());
        artifact.setRunId(job.getRunId());
        artifact.setSkillVersionId(job.getSkillVersionId());
        artifact.setUserId(job.getUserId());
        artifact.setAgentDefinitionId(job.getAgentDefinitionId());
        artifact.setFileName(fileName);
        artifact.setObjectKey("chat/artifacts/" + job.getId() + "/" + UUID.randomUUID().toString() + "." + extension);
        artifact.setContentSha256(actualHash);
        artifact.setContentType(StringUtils.defaultIfBlank(contentType, "application/octet-stream"));
        artifact.setSize((long) content.length);
        artifact.setExpiresAt(System.currentTimeMillis() + 7L * 24 * 3600 * 1000);
        artifact.setLogSummary(StringUtils.abbreviate(logSummary, 4096));
        artifact.setStatus(1);
        storage.upload(artifactBucket, artifact.getObjectKey(), content, artifact.getContentType());
        artifactService.save(artifact);
        attachToRunMessage(job, artifact);
        if (finalArtifact) {
            job.setStatus(2);
            job.setCompletedAt(System.currentTimeMillis());
            job.setLogSummary(StringUtils.abbreviate(logSummary, 4096));
            executionService.updateById(job);
            sandboxTaskService.completeLegacyExecution(job.getId(), true, null, logSummary);
        }
    }

    /**
     * 处理fail。
     */
    @Override
    public void fail(String suppliedRunnerToken, String executionToken, String executionId, String reason, String logSummary) {
        requireRunner(suppliedRunnerToken);
        AgentSandboxExecution job = running(executionId);
        requireExecutionToken(job, executionToken);
        job.setStatus(3);
        job.setCompletedAt(System.currentTimeMillis());
        job.setFailureReason(StringUtils.abbreviate(reason, 1024));
        job.setLogSummary(StringUtils.abbreviate(logSummary, 4096));
        executionService.updateById(job);
        sandboxTaskService.completeLegacyExecution(job.getId(), false, reason, logSummary);
    }

    /**
     * 处理heartbeat。
     */
    @Override
    public boolean heartbeat(String suppliedRunnerToken, String executionToken, String executionId, String logSummary) {
        requireRunner(suppliedRunnerToken);
        AgentSandboxExecution job = running(executionId);
        requireExecutionToken(job, executionToken);
        return sandboxTaskService.legacyHeartbeat(job.getId(), logSummary);
    }

    /**
     * 取消Requested。
     */
    @Override
    public boolean cancelRequested(String suppliedRunnerToken, String executionToken, String executionId) {
        requireRunner(suppliedRunnerToken);
        AgentSandboxExecution job = running(executionId);
        requireExecutionToken(job, executionToken);
        return sandboxTaskService.legacyCancelRequested(job.getId());
    }

    /**
     * 处理attachPendingArtifacts。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void attachPendingArtifacts(String runId, String messageId) {
        if (StringUtils.isAnyBlank(runId, messageId)) return;
        sandboxTaskService.linkRunMessage(runId, messageId);
        AgentMessage message = messageService.getById(messageId);
        if (message == null || !"assistant".equals(message.getRole())) return;
        for (AgentArtifact artifact : artifactService.list(Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getRunId, runId))) {
            attachToMessage(message, artifact);
        }
    }

    /**
     * 处理running。
     */
    private AgentSandboxExecution running(String id) {
        AgentSandboxExecution job = executionService.getById(id);
        if (job == null || !Integer.valueOf(1).equals(job.getStatus()) || job.getExpiresAt() < System.currentTimeMillis())
            throw new ServerException(409, I18nUtils.getMessage("skill.artifact.execution.not-running"));
        return job;
    }

    /**
     * 处理requireRunner。
     */
    private void requireRunner(String value) {
        if (StringUtils.isBlank(runnerToken) || !MessageDigest.isEqual(runnerToken.getBytes(), StringUtils.defaultString(value).getBytes()))
            throw new ServerException(401, I18nUtils.getMessage("skill.artifact.runner.unauthorized"));
    }

    /**
     * 处理requireExecution令牌。
     */
    private void requireExecutionToken(AgentSandboxExecution job, String value) {
        try {
            DecodedJWT token = JWT.require(Algorithm.HMAC256(runnerToken)).build().verify(value);
            if (!job.getId().equals(token.getClaim("executionId").asString()) || !MessageDigest.isEqual(job.getTokenHash().getBytes(), sha256(value).getBytes()))
                throw new ServerException(401, I18nUtils.getMessage("skill.artifact.execution-token.unauthorized"));
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(401, I18nUtils.getMessage("skill.artifact.execution-token.unauthorized"));
        }
    }

    /**
     * 验证Delegation。
     */
    private DecodedJWT verifyDelegation(String raw) {
        try {
            DecodedJWT token = JWT.require(Algorithm.HMAC256(deepAgentConfig.getMcpDelegationSecret())).build().verify(raw);
            if (!token.getClaim("allowedTools").asList(String.class).contains("generate_artifact"))
                throw new ServerException(403, I18nUtils.getMessage("skill.artifact.tool.not-delegated"));
            return token;
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(401, I18nUtils.getMessage("skill.artifact.delegation-token.invalid"));
        }
    }

    /**
     * 处理random令牌。
     */
    private String randomToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 校验ContentType。
     */
    private void validateContentType(String extension, String contentType) {
        String expected;
        if ("pdf".equals(extension)) expected = "application/pdf";
        else if ("docx".equals(extension))
            expected = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if ("xlsx".equals(extension))
            expected = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        else throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
        if (!expected.equalsIgnoreCase(StringUtils.defaultString(contentType)))
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.mime-type.mismatch"));
    }

    /**
     * MIME and filename are runner supplied metadata; inspect the file itself before it enters object storage.
     */
    private void validateArtifactFormat(String extension, byte[] content) {
        if ("pdf".equals(extension)) {
            if (content.length < 5 || content[0] != '%' || content[1] != 'P' || content[2] != 'D' || content[3] != 'F' || content[4] != '-')
                throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
            return;
        }
        boolean contentTypes = false, expectedPart = false;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            int entries = 0;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entries > 10_000)
                    throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
                String name = entry.getName();
                if (name.toLowerCase(Locale.ROOT).endsWith("vbaproject.bin"))
                    throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
                if ("[Content_Types].xml".equals(name)) contentTypes = true;
                if (("docx".equals(extension) && "word/document.xml".equals(name)) || ("xlsx".equals(extension) && "xl/workbook.xml".equals(name)))
                    expectedPart = true;
            }
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
        }
        if (!contentTypes || !expectedPart)
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
    }

    /**
     * Deliberately small, deterministic subset of JSON Schema for Skill inputs.
     */
    private void validateInput(String schemaText, Map<String, Object> input) {
        if (StringUtils.isBlank(schemaText)) return;
        JSONObject schema;
        try {
            schema = JSON.parseObject(schemaText);
        } catch (Exception e) {
            throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.invalid"));
        }
        if (schema == null || (!StringUtils.isBlank(schema.getString("type")) && !"object".equals(schema.getString("type"))))
            throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.object-required"));
        Map<String, Object> value = input == null ? Collections.emptyMap() : input;
        for (String required : schema.getList("required", String.class) == null ? Collections.<String>emptyList() : schema.getList("required", String.class))
            if (!value.containsKey(required) || value.get(required) == null)
                throw new ServerException(422, I18nUtils.getMessage("skill.context.input.required-field.missing", new Object[]{required}));
        JSONObject properties = schema.getJSONObject("properties");
        boolean additional = !Boolean.FALSE.equals(schema.getBoolean("additionalProperties"));
        for (Map.Entry<String, Object> item : value.entrySet()) {
            JSONObject property = properties == null ? null : properties.getJSONObject(item.getKey());
            if (property == null) {
                if (!additional)
                    throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.undeclared", new Object[]{item.getKey()}));
                continue;
            }
            String type = property.getString("type");
            Object actual = item.getValue();
            if ("string".equals(type) && !(actual instanceof String) || "boolean".equals(type) && !(actual instanceof Boolean)
                    || "number".equals(type) && !(actual instanceof Number) || "integer".equals(type) && (!(actual instanceof Number) || ((Number) actual).doubleValue() % 1 != 0)
                    || "array".equals(type) && !(actual instanceof Collection) || "object".equals(type) && !(actual instanceof Map))
                throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.invalid-type", new Object[]{item.getKey()}));
        }
    }

    /**
     * 处理attachTo运行消息。
     */
    private void attachToRunMessage(AgentSandboxExecution job, AgentArtifact artifact) {
        AgentRun run = runService.getById(job.getRunId());
        if (run == null || StringUtils.isBlank(run.getMessageId())) return;
        AgentMessage message = messageService.getById(run.getMessageId());
        // 工具确认卡同样是 assistant 消息，但不是最终答复。此时先保持产物未绑定，
        // 由 completeRun 绑定到最终聊天消息，避免文件只出现在已回答的确认卡中。
        if (message == null || !"assistant".equals(message.getRole()) || !"chat".equals(message.getMessageType())) return;
        attachToMessage(message, artifact);
    }

    /**
     * 处理attachTo消息。
     */
    private void attachToMessage(AgentMessage message, AgentArtifact artifact) {
        if (StringUtils.isNotBlank(artifact.getMessageId()) && !message.getId().equals(artifact.getMessageId())) {
            detachFromMessage(artifact.getMessageId(), artifact.getId());
        }
        List<Map<String, Object>> attachments = new ArrayList<>();
        if (StringUtils.isNotBlank(message.getAttachments())) {
            List rawAttachments = JSON.parseArray(message.getAttachments(), Map.class);
            if (rawAttachments != null) attachments.addAll(rawAttachments);
        }
        for (Map<String, Object> attachment : attachments) {
            if (artifact.getId().equals(String.valueOf(attachment.get("artifactId")))) return;
        }
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("fileName", artifact.getFileName());
        item.put("size", artifact.getSize());
        item.put("contentType", artifact.getContentType());
        item.put("objectKey", artifact.getObjectKey());
        item.put("artifactId", artifact.getId());
        attachments.add(item);
        message.setAttachments(JSON.toJSONString(attachments));
        messageService.updateById(message);
        artifact.setMessageId(message.getId());
        artifactService.updateById(artifact);
    }

    /**
     * 移除产物在旧消息中的附件引用，随后由调用方绑定到最终助手消息。
     */
    private void detachFromMessage(String messageId, String artifactId) {
        AgentMessage previous = messageService.getById(messageId);
        if (previous == null || StringUtils.isBlank(previous.getAttachments())) return;
        List<Map<String, Object>> attachments = (List<Map<String, Object>>) (List<?>) JSON.parseArray(previous.getAttachments(), Map.class);
        if (attachments == null) return;
        if (attachments.removeIf(item -> artifactId.equals(String.valueOf(item.get("artifactId"))))) {
            previous.setAttachments(JSON.toJSONString(attachments));
            messageService.updateById(previous);
        }
    }

    /**
     * 处理sha256。
     */
    private String sha256(byte[] bytes) {
        return sha256(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1));
    }

    /**
     * 处理sha256。
     */
    private String sha256(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1));
            StringBuilder out = new StringBuilder();
            for (byte b : hash) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Exception e) {
            throw new ServerException(500, I18nUtils.getMessage("skill.artifact.checksum.failure"));
        }
    }

    /**
     * Platform-owned limits for a generic file-generation job; not a Skill configuration.
     */
    @Data
    private static class ArtifactExecutionPolicy {
        private String entryResourceId;
        private String runtime;
        private String outputFormats;
        private Integer timeoutSeconds;
        private Integer maxOutputFiles;
        private Long maxOutputBytes;
    }
}
