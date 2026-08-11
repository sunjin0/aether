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

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;
import java.util.stream.Collectors;

/** Issues immutable, one-time sandbox jobs.  No user-controlled execution primitive crosses this boundary. */
@Service
public class SkillArtifactExecutionServiceImpl implements SkillArtifactExecutionService {
    private static final String PLATFORM_GENERIC_ARTIFACT_VERSION = "__platform_generic_artifact__";
    private static final String PLATFORM_GENERIC_ENTRY = "__platform_generic_renderer__";
    private static final long TTL_MILLIS = 5 * 60 * 1000L;
    private final AgentSkillServiceImpl skillService;
    private final AgentSkillVersionServiceImpl versionService;
    private final AgentDefinitionSkillBindingServiceImpl bindingService;
    private final AgentSkillExecutionConfigServiceImpl configService;
    private final AgentSkillResourceServiceImpl resourceService;
    private final AgentSandboxExecutionServiceImpl executionService;
    private final AgentArtifactServiceImpl artifactService;
    private final ObjectStorageService storage;
    private final DeepAgentConfig deepAgentConfig;
    private final AgentRunService runService;
    private final AgentMessageService messageService;
    private final String resourceBucket;
    private final String artifactBucket;
    private final String runnerToken;

    public SkillArtifactExecutionServiceImpl(AgentSkillServiceImpl skillService, AgentSkillVersionServiceImpl versionService,
            AgentDefinitionSkillBindingServiceImpl bindingService, AgentSkillExecutionConfigServiceImpl configService,
            AgentSkillResourceServiceImpl resourceService, AgentSandboxExecutionServiceImpl executionService,
            AgentArtifactServiceImpl artifactService, ObjectStorageService storage, DeepAgentConfig deepAgentConfig,
            AgentRunService runService, AgentMessageService messageService,
            @Value("${skill.storage.bucket:${MINIO_SKILL_BUCKET:aether-skill}}") String resourceBucket,
            @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket,
            @Value("${aether.sandbox.runner-token:${AETHER_SANDBOX_RUNNER_TOKEN:}}") String runnerToken) {
        this.skillService = skillService; this.versionService = versionService; this.bindingService = bindingService;
        this.configService = configService; this.resourceService = resourceService; this.executionService = executionService;
        this.artifactService = artifactService; this.storage = storage; this.deepAgentConfig = deepAgentConfig;
        this.runService = runService; this.messageService = messageService;
        this.resourceBucket = resourceBucket; this.artifactBucket = artifactBucket; this.runnerToken = runnerToken;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public ArtifactGenerationVo request(String delegatedToken, ArtifactGenerationRequestDto request) {
        DecodedJWT token = verifyDelegation(delegatedToken);
        if (request == null || StringUtils.isBlank(request.getFormat()) || StringUtils.isBlank(request.getContent())) {
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.request.invalid"));
        }
        String userId = token.getClaim("userId").asString(); String agentId = token.getClaim("agentId").asString(); String runId = token.getClaim("runId").asString();
        if (StringUtils.isAnyBlank(userId, agentId, runId)) throw new ServerException(401, I18nUtils.getMessage("skill.artifact.delegation.context.incomplete"));
        AgentSkillExecutionConfig config = platformGenericConfig(request.getFormat());
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("title", StringUtils.defaultIfBlank(request.getTitle(), "generated"));
        input.put("fileName", request.getFileName());
        input.put("format", request.getFormat().toLowerCase(Locale.ROOT));
        input.put("content", request.getContent());
        input.put("document", request.getDocument() == null ? Collections.emptyMap() : request.getDocument());
        AgentSandboxExecution execution = new AgentSandboxExecution(); execution.setRunId(runId);
        execution.setSkillVersionId(PLATFORM_GENERIC_ARTIFACT_VERSION); execution.setUserId(userId); execution.setAgentDefinitionId(agentId);
        execution.setExecutionConfigSnapshot(JSON.toJSONString(config)); execution.setResourceSnapshot("[]");
        execution.setInputJson(JSON.toJSONString(input));
        execution.setTokenHash(sha256(randomToken())); execution.setStatus(0); execution.setExpiresAt(System.currentTimeMillis() + TTL_MILLIS); executionService.save(execution);
        ArtifactGenerationVo result = new ArtifactGenerationVo(); result.setExecutionId(execution.getId()); result.setRunId(execution.getRunId()); result.setStatus("queued"); return result;
    }

    private AgentSkillExecutionConfig platformGenericConfig(String format) {
        String normalized = StringUtils.lowerCase(StringUtils.trim(format));
        if (!Arrays.asList("docx", "xlsx", "pdf").contains(normalized)) {
            throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.not-declared"));
        }
        AgentSkillExecutionConfig config = new AgentSkillExecutionConfig();
        config.setEnabled(true); config.setEntryResourceId(PLATFORM_GENERIC_ENTRY); config.setRuntime("PYTHON");
        config.setOutputFormats(JSON.toJSONString(Collections.singletonList(normalized)));
        config.setTimeoutSeconds(60); config.setMaxOutputFiles(1); config.setMaxOutputBytes(52_428_800L);
        return config;
    }

    @Override @Transactional(rollbackFor = Exception.class)
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
        AgentSandboxExecution job = executionService.getOne(Wrappers.lambdaQuery(AgentSandboxExecution.class).eq(AgentSandboxExecution::getStatus, 0)
                .gt(AgentSandboxExecution::getExpiresAt, now).orderByAsc(AgentSandboxExecution::getCreatedAt).last("limit 1"));
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
        AgentSkillExecutionConfig config = JSON.parseObject(job.getExecutionConfigSnapshot(), AgentSkillExecutionConfig.class);
        List<AgentSkillResource> resources = JSON.parseArray(job.getResourceSnapshot(), AgentSkillResource.class);
        SandboxExecutionTaskVo task = new SandboxExecutionTaskVo(); task.setExecutionId(job.getId()); task.setExecutionToken(executionToken); task.setRunId(job.getRunId()); task.setSkillVersionId(job.getSkillVersionId());
        task.setRuntime(config.getRuntime()); task.setEntryResourceId(config.getEntryResourceId()); task.setOutputFormats(JSON.parseArray(config.getOutputFormats(), String.class));
        task.setTimeoutSeconds(config.getTimeoutSeconds()); task.setMaxOutputFiles(config.getMaxOutputFiles()); task.setMaxOutputBytes(config.getMaxOutputBytes());
        Map<String, Object> frozenInput = new LinkedHashMap<>();
        Map parsedInput = JSON.parseObject(job.getInputJson(), Map.class);
        if (parsedInput != null) frozenInput.putAll(parsedInput);
        task.setInput(frozenInput);
        task.setResources(resources.stream().map(r -> { SandboxExecutionTaskVo.Resource item = new SandboxExecutionTaskVo.Resource(); item.setId(r.getId()); item.setName(r.getName()); item.setType(r.getType()); item.setLanguage(r.getLanguage()); item.setContentSha256(r.getContentSha256()); item.setSize(r.getSize()); return item; }).collect(Collectors.toList())); return task;
    }

    @Override public byte[] readResource(String suppliedRunnerToken, String executionToken, String executionId, String resourceId) {
        requireRunner(suppliedRunnerToken); AgentSandboxExecution job = running(executionId); requireExecutionToken(job, executionToken);
        List<AgentSkillResource> resources = JSON.parseArray(job.getResourceSnapshot(), AgentSkillResource.class);
        AgentSkillResource resource = resources.stream().filter(r -> resourceId.equals(r.getId())).findFirst().orElseThrow(() -> new ServerException(404, I18nUtils.getMessage("skill.artifact.resource.not-found")));
        byte[] content = storage.getObject(resourceBucket, resource.getObjectKey()); if (!sha256(content).equals(resource.getContentSha256())) throw new ServerException(409, I18nUtils.getMessage("skill.artifact.resource.checksum-mismatch")); return content;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void complete(String suppliedRunnerToken, String executionToken, String executionId, String fileName, String contentType, byte[] content, String checksum, String logSummary, boolean finalArtifact) {
        requireRunner(suppliedRunnerToken); AgentSandboxExecution job = running(executionId); requireExecutionToken(job, executionToken); AgentSkillExecutionConfig config = JSON.parseObject(job.getExecutionConfigSnapshot(), AgentSkillExecutionConfig.class);
        if (content == null || content.length == 0 || content.length > config.getMaxOutputBytes()) throw new ServerException(400, I18nUtils.getMessage("skill.artifact.size.invalid"));
        String extension = fileName == null ? "" : fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        List<String> formats = JSON.parseArray(config.getOutputFormats(), String.class); if (!formats.contains(extension)) throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.not-declared"));
        validateContentType(extension, contentType);
        if (artifactService.count(Wrappers.lambdaQuery(AgentArtifact.class).eq(AgentArtifact::getExecutionId, job.getId())) >= config.getMaxOutputFiles()) throw new ServerException(409, I18nUtils.getMessage("skill.artifact.count.exceeded"));
        String actualHash = sha256(content); if (!actualHash.equals(checksum)) throw new ServerException(409, I18nUtils.getMessage("skill.artifact.checksum-mismatch"));
        AgentArtifact artifact = new AgentArtifact(); artifact.setExecutionId(job.getId()); artifact.setRunId(job.getRunId()); artifact.setSkillVersionId(job.getSkillVersionId()); artifact.setFileName(fileName);
        artifact.setObjectKey("chat/artifacts/" + job.getId() + "/" + UUID.randomUUID().toString() + "." + extension); artifact.setContentSha256(actualHash); artifact.setContentType(StringUtils.defaultIfBlank(contentType, "application/octet-stream")); artifact.setSize((long) content.length); artifact.setExpiresAt(System.currentTimeMillis() + 7L * 24 * 3600 * 1000); artifact.setLogSummary(StringUtils.abbreviate(logSummary, 4096)); artifact.setStatus(1);
        storage.upload(artifactBucket, artifact.getObjectKey(), content, artifact.getContentType()); artifactService.save(artifact);
        attachToRunMessage(job, artifact);
        if (finalArtifact) { job.setStatus(2); job.setCompletedAt(System.currentTimeMillis()); job.setLogSummary(StringUtils.abbreviate(logSummary, 4096)); executionService.updateById(job); }
    }

    @Override public void fail(String suppliedRunnerToken, String executionToken, String executionId, String reason, String logSummary) { requireRunner(suppliedRunnerToken); AgentSandboxExecution job = running(executionId); requireExecutionToken(job, executionToken); job.setStatus(3); job.setCompletedAt(System.currentTimeMillis()); job.setFailureReason(StringUtils.abbreviate(reason, 1024)); job.setLogSummary(StringUtils.abbreviate(logSummary, 4096)); executionService.updateById(job); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void attachPendingArtifacts(String runId, String messageId) {
        if (StringUtils.isAnyBlank(runId, messageId)) return;
        AgentMessage message = messageService.getById(messageId);
        if (message == null || !"assistant".equals(message.getRole())) return;
        for (AgentArtifact artifact : artifactService.list(Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getRunId, runId).isNull(AgentArtifact::getMessageId))) {
            attachToMessage(message, artifact);
        }
    }
    private AgentSandboxExecution running(String id) { AgentSandboxExecution job = executionService.getById(id); if (job == null || !Integer.valueOf(1).equals(job.getStatus()) || job.getExpiresAt() < System.currentTimeMillis()) throw new ServerException(409, I18nUtils.getMessage("skill.artifact.execution.not-running")); return job; }
    private void requireRunner(String value) { if (StringUtils.isBlank(runnerToken) || !MessageDigest.isEqual(runnerToken.getBytes(), StringUtils.defaultString(value).getBytes())) throw new ServerException(401, I18nUtils.getMessage("skill.artifact.runner.unauthorized")); }
    private void requireExecutionToken(AgentSandboxExecution job, String value) {
        try { DecodedJWT token = JWT.require(Algorithm.HMAC256(runnerToken)).build().verify(value);
            if (!job.getId().equals(token.getClaim("executionId").asString()) || !MessageDigest.isEqual(job.getTokenHash().getBytes(), sha256(value).getBytes())) throw new ServerException(401, I18nUtils.getMessage("skill.artifact.execution-token.unauthorized"));
        } catch (ServerException e) { throw e; } catch (Exception e) { throw new ServerException(401, I18nUtils.getMessage("skill.artifact.execution-token.unauthorized")); }
    }
    private DecodedJWT verifyDelegation(String raw) { try { DecodedJWT token = JWT.require(Algorithm.HMAC256(deepAgentConfig.getMcpDelegationSecret())).build().verify(raw); if (!token.getClaim("allowedTools").asList(String.class).contains("generate_artifact")) throw new ServerException(403, I18nUtils.getMessage("skill.artifact.tool.not-delegated")); return token; } catch (ServerException e) { throw e; } catch (Exception e) { throw new ServerException(401, I18nUtils.getMessage("skill.artifact.delegation-token.invalid")); } }
    private String randomToken() { byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes); return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private void validateContentType(String extension, String contentType) {
        String expected;
        if ("pdf".equals(extension)) expected = "application/pdf";
        else if ("docx".equals(extension)) expected = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        else if ("xlsx".equals(extension)) expected = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        else throw new ServerException(400, I18nUtils.getMessage("skill.artifact.format.unsupported"));
        if (!expected.equalsIgnoreCase(StringUtils.defaultString(contentType))) throw new ServerException(400, I18nUtils.getMessage("skill.artifact.mime-type.mismatch"));
    }
    /** Deliberately small, deterministic subset of JSON Schema for Skill inputs. */
    private void validateInput(String schemaText, Map<String, Object> input) {
        if (StringUtils.isBlank(schemaText)) return;
        JSONObject schema;
        try { schema = JSON.parseObject(schemaText); } catch (Exception e) { throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.invalid")); }
        if (schema == null || (!StringUtils.isBlank(schema.getString("type")) && !"object".equals(schema.getString("type")))) throw new ServerException(422, I18nUtils.getMessage("skill.context.input-schema.object-required"));
        Map<String, Object> value = input == null ? Collections.emptyMap() : input;
        for (String required : schema.getList("required", String.class) == null ? Collections.<String>emptyList() : schema.getList("required", String.class)) if (!value.containsKey(required) || value.get(required) == null) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.required-field.missing", new Object[]{required}));
        JSONObject properties = schema.getJSONObject("properties");
        boolean additional = !Boolean.FALSE.equals(schema.getBoolean("additionalProperties"));
        for (Map.Entry<String, Object> item : value.entrySet()) {
            JSONObject property = properties == null ? null : properties.getJSONObject(item.getKey());
            if (property == null) { if (!additional) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.undeclared", new Object[]{item.getKey()})); continue; }
            String type = property.getString("type"); Object actual = item.getValue();
            if ("string".equals(type) && !(actual instanceof String) || "boolean".equals(type) && !(actual instanceof Boolean)
                    || "number".equals(type) && !(actual instanceof Number) || "integer".equals(type) && (!(actual instanceof Number) || ((Number) actual).doubleValue() % 1 != 0)
                    || "array".equals(type) && !(actual instanceof Collection) || "object".equals(type) && !(actual instanceof Map)) throw new ServerException(422, I18nUtils.getMessage("skill.context.input.field.invalid-type", new Object[]{item.getKey()}));
        }
    }
    private void attachToRunMessage(AgentSandboxExecution job, AgentArtifact artifact) {
        AgentRun run = runService.getById(job.getRunId());
        if (run == null || StringUtils.isBlank(run.getMessageId())) return;
        AgentMessage message = messageService.getById(run.getMessageId());
        // Before completeRun, this field points at the user's input message.  Leave
        // the Artifact unbound and let completeRun reconcile it to the assistant reply.
        if (message == null || !"assistant".equals(message.getRole())) return;
        attachToMessage(message, artifact);
    }
    private void attachToMessage(AgentMessage message, AgentArtifact artifact) {
        List<Map<String, Object>> attachments = new ArrayList<>();
        if (StringUtils.isNotBlank(message.getAttachments())) {
            List rawAttachments = JSON.parseArray(message.getAttachments(), Map.class);
            if (rawAttachments != null) attachments.addAll(rawAttachments);
        }
        Map<String, Object> item = new LinkedHashMap<>(); item.put("fileName", artifact.getFileName()); item.put("size", artifact.getSize()); item.put("contentType", artifact.getContentType()); item.put("objectKey", artifact.getObjectKey()); item.put("artifactId", artifact.getId()); attachments.add(item);
        message.setAttachments(JSON.toJSONString(attachments)); messageService.updateById(message); artifact.setMessageId(message.getId()); artifactService.updateById(artifact);
    }
    private String sha256(byte[] bytes) { return sha256(new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1)); }
    private String sha256(String value) { try { byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1)); StringBuilder out = new StringBuilder(); for (byte b : hash) out.append(String.format("%02x", b)); return out.toString(); } catch (Exception e) { throw new ServerException(500, I18nUtils.getMessage("skill.artifact.checksum.failure")); } }
}
