package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.dto.SandboxRunnerEventDto;
import com.aether.agent.sandbox.dto.SandboxTaskCreateDto;
import com.aether.agent.sandbox.entity.*;
import com.aether.agent.sandbox.mapper.*;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.agent.sandbox.service.ArtifactContentScanner;
import com.aether.agent.sandbox.service.WebCollectionTargetValidator;
import com.aether.agent.sandbox.vo.SandboxRunnerTaskVo;
import com.aether.agent.sandbox.vo.SandboxRunnerInputArtifactVo;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.service.impl.AgentArtifactServiceImpl;
import com.aether.agent.skill.entity.AgentSandboxExecution;
import com.aether.agent.skill.service.impl.AgentSandboxExecutionServiceImpl;
import com.aether.storage.service.ObjectStorageService;
import com.aether.sys.entity.User;
import com.aether.sys.service.UserService;
import com.aether.exception.ServerException;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SandboxTaskServiceImpl implements SandboxTaskService {
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL", QUEUED = "QUEUED", CLAIMED = "CLAIMED", RUNNING = "RUNNING", SUCCEEDED = "SUCCEEDED", FAILED = "FAILED", TIMED_OUT = "TIMED_OUT", CANCELLED = "CANCELLED", EXPIRED = "EXPIRED";
    private static final long TASK_TTL = 5 * 60 * 1000L, LEASE_TTL = 30 * 1000L;
    private final SandboxExecutionTemplateMapper templates;
    private final SandboxExecutionTemplateVersionMapper versions;
    private final SandboxExecutionTaskMapper tasks;
    private final SandboxExecutionApprovalMapper approvals;
    private final SandboxExecutionEventMapper events;
    private final SandboxExecutionResourceUsageMapper resourceUsage;
    private final SandboxRunnerNodeMapper runnerNodes;
    private final AgentSandboxExecutionServiceImpl legacyExecutions;
    private final String runnerToken;
    private final AgentArtifactServiceImpl artifactService;
    private final ObjectStorageService storage;
    private final String artifactBucket;
    private final ArtifactContentScanner contentScanner;
    private final WebCollectionTargetValidator webTargetValidator;
    private final UserService userService;

    public SandboxTaskServiceImpl(SandboxExecutionTemplateMapper templates, SandboxExecutionTemplateVersionMapper versions, SandboxExecutionTaskMapper tasks, SandboxExecutionApprovalMapper approvals, SandboxExecutionEventMapper events, SandboxExecutionResourceUsageMapper resourceUsage, SandboxRunnerNodeMapper runnerNodes, AgentSandboxExecutionServiceImpl legacyExecutions, AgentArtifactServiceImpl artifactService, ObjectStorageService storage, ArtifactContentScanner contentScanner, WebCollectionTargetValidator webTargetValidator, UserService userService, @Value("${artifact.storage.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String artifactBucket, @Value("${aether.sandbox.runner-token:${AETHER_SANDBOX_RUNNER_TOKEN:}}") String runnerToken) {
        this.templates = templates; this.versions = versions; this.tasks = tasks; this.approvals = approvals; this.events = events; this.resourceUsage = resourceUsage; this.runnerNodes = runnerNodes; this.legacyExecutions = legacyExecutions; this.artifactService = artifactService; this.storage = storage; this.contentScanner = contentScanner; this.webTargetValidator = webTargetValidator; this.userService = userService; this.artifactBucket = artifactBucket; this.runnerToken = runnerToken;
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public SandboxTaskVo create(String userId, SandboxTaskCreateDto request, boolean autoApprove) {
        if (request == null || StringUtils.isBlank(request.getTemplateCode())) throw bad("templateCode is required");
        SandboxExecutionTemplate template = templates.selectOne(Wrappers.lambdaQuery(SandboxExecutionTemplate.class).eq(SandboxExecutionTemplate::getCode, request.getTemplateCode()).eq(SandboxExecutionTemplate::getEnabled, true));
        if (template == null || StringUtils.isBlank(template.getCurrentVersionId())) throw bad("sandbox template is unavailable");
        SandboxExecutionTemplateVersion version = versions.selectById(template.getCurrentVersionId());
        if (version == null || !Boolean.TRUE.equals(version.getPublished())) throw bad("sandbox template version is unavailable");
        Map config = JSON.parseObject(version.getConfigSnapshot(), Map.class);
        webTargetValidator.validate(request.getInput(), config);
        enforceDailyQuota(userId, request.getAgentDefinitionId(), template.getCode(), config);
        validateScriptSlot(request, config);
        long now = System.currentTimeMillis();
        SandboxExecutionTask task = new SandboxExecutionTask();
        task.setTemplateId(template.getId()); task.setTemplateVersionId(version.getId()); task.setTemplateCode(template.getCode()); task.setRequesterUserId(userId);
        task.setAgentDefinitionId(request.getAgentDefinitionId()); task.setRunId(request.getRunId()); task.setMessageId(request.getMessageId()); task.setRiskLevel(template.getRiskLevel());
        boolean approvalRequired = !"LOW".equalsIgnoreCase(template.getRiskLevel()) || !autoApprove;
        task.setApprovalRequired(approvalRequired); task.setStatus(approvalRequired ? PENDING_APPROVAL : QUEUED);
        Map<String, Object> frozenInput = new LinkedHashMap<>();
        if (request.getInput() != null) frozenInput.putAll(request.getInput());
        if (frozenInput.containsKey("_sandboxInputArtifacts")) throw bad("sandbox input contains a reserved field");
        if (StringUtils.isNotBlank(request.getScript())) { frozenInput.put("script", request.getScript()); frozenInput.put("scriptLanguage", StringUtils.upperCase(request.getScriptLanguage())); task.setScriptSha256(hash(request.getScript())); }
        if (JSON.toJSONString(frozenInput).getBytes(StandardCharsets.UTF_8).length > maxInputBytes(config)) throw bad("sandbox input exceeds the template limit");
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        List<Map<String, Object>> frozenArtifacts = freezeInputArtifacts(userId, task.getId(), request.getInputArtifactIds(), config);
        if (!frozenArtifacts.isEmpty()) frozenInput.put("_sandboxInputArtifacts", frozenArtifacts);
        task.setInputSnapshot(JSON.toJSONString(frozenInput));
        if (containsHighRiskSecret(task.getInputSnapshot())) throw bad("sandbox input contains a high-risk secret");
        task.setInputSha256(hash(task.getInputSnapshot()));
        task.setConfigSnapshot(version.getConfigSnapshot()); task.setPolicyVersion(version.getPolicyVersion()); task.setExpiresAt(now + TASK_TTL);
        try { tasks.insert(task); }
        catch (RuntimeException e) { removeFrozenInputs(frozenArtifacts); throw e; }
        append(task.getId(), 1L, "TASK_CREATED", task.getStatus(), null, "Task frozen from template " + template.getCode());
        for (Map<String, Object> artifact : frozenArtifacts) if (StringUtils.isNotBlank((String) artifact.get("sensitiveRule"))) append(task.getId(), nextSequence(task.getId()), "SENSITIVE_INPUT_MATCH", task.getStatus(), null, "Sensitive input rule matched: " + artifact.get("sensitiveRule"), String.valueOf(artifact.get("sha256")));
        return toVo(task, false);
    }

    @Override @Transactional(rollbackFor = Exception.class)
    public void approve(String id, String userId, String reason) { decide(id, userId, reason, "APPROVED", QUEUED); }
    @Override public void linkRunMessage(String runId, String messageId) {
        if (StringUtils.isAnyBlank(runId, messageId)) return;
        tasks.update(null, Wrappers.lambdaUpdate(SandboxExecutionTask.class)
                .set(SandboxExecutionTask::getMessageId, messageId)
                .eq(SandboxExecutionTask::getRunId, runId)
                .isNull(SandboxExecutionTask::getMessageId));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void reject(String id, String userId, String reason) { decide(id, userId, reason, "REJECTED", CANCELLED); }
    private void decide(String id, String userId, String reason, String decision, String next) {
        SandboxExecutionTask task = owned(id, userId, false); if (!PENDING_APPROVAL.equals(task.getStatus())) throw conflict("task is not awaiting approval");
        long now = System.currentTimeMillis(); if (task.getExpiresAt() <= now) { expire(task, now); throw conflict("task expired"); }
        SandboxExecutionApproval approval = new SandboxExecutionApproval(); approval.setTaskId(id); approval.setDecision(decision); approval.setApproverUserId(userId); approval.setReason(StringUtils.abbreviate(reason, 1024)); approval.setDecidedAt(now); approvals.insert(approval);
        task.setStatus(next); if (CANCELLED.equals(next)) task.setCompletedAt(now); tasks.updateById(task); append(id, nextSequence(id), "APPROVAL_" + decision, next, null, StringUtils.abbreviate(reason, 1024));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void cancel(String id, String userId, String reason) {
        SandboxExecutionTask task = owned(id, userId, false); if (terminal(task.getStatus())) return; long now = System.currentTimeMillis();
        task.setCancelRequestedAt(now);
        if (PENDING_APPROVAL.equals(task.getStatus()) || QUEUED.equals(task.getStatus()) || CLAIMED.equals(task.getStatus())) { task.setStatus(CANCELLED); task.setCompletedAt(now); }
        tasks.updateById(task); append(id, nextSequence(id), "CANCEL_REQUESTED", task.getStatus(), null, StringUtils.abbreviate(reason, 1024));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public SandboxTaskVo retry(String id, String userId) {
        SandboxExecutionTask previous = owned(id, userId, false);
        if (!FAILED.equals(previous.getStatus()) && !TIMED_OUT.equals(previous.getStatus()) && !CANCELLED.equals(previous.getStatus())) throw conflict("only failed, timed out, or cancelled tasks can be retried");
        if (previous.getInputPurgedAt() != null) throw conflict("sandbox task inputs have expired and cannot be retried");
        SandboxExecutionTask task = new SandboxExecutionTask();
        task.setId(UUID.randomUUID().toString().replace("-", ""));
        task.setTemplateId(previous.getTemplateId()); task.setTemplateVersionId(previous.getTemplateVersionId()); task.setTemplateCode(previous.getTemplateCode()); task.setRequesterUserId(previous.getRequesterUserId()); task.setAgentDefinitionId(previous.getAgentDefinitionId()); task.setRunId(previous.getRunId()); task.setMessageId(previous.getMessageId()); task.setRiskLevel(previous.getRiskLevel());
        Map input = JSON.parseObject(previous.getInputSnapshot(), Map.class); List<Map<String, Object>> copies = cloneFrozenInputs(frozenInputArtifacts(input), task.getId()); if (!copies.isEmpty()) input.put("_sandboxInputArtifacts", copies);
        task.setApprovalRequired(previous.getApprovalRequired()); task.setStatus(Boolean.TRUE.equals(previous.getApprovalRequired()) ? PENDING_APPROVAL : QUEUED); task.setInputSnapshot(JSON.toJSONString(input == null ? Collections.emptyMap() : input)); task.setInputSha256(hash(task.getInputSnapshot())); task.setScriptSha256(previous.getScriptSha256()); task.setConfigSnapshot(previous.getConfigSnapshot()); task.setPolicyVersion(previous.getPolicyVersion()); task.setExpiresAt(System.currentTimeMillis() + TASK_TTL);
        if (StringUtils.isNotBlank(previous.getLegacyExecutionId())) task.setLegacyExecutionId(copyLegacyExecution(previous.getLegacyExecutionId(), task.getExpiresAt()));
        try { tasks.insert(task); } catch (RuntimeException e) { removeFrozenInputs(copies); throw e; }
        append(task.getId(), 1L, "TASK_RETRIED", task.getStatus(), null, "Retried from task " + previous.getId());
        if (StringUtils.isNotBlank(task.getLegacyExecutionId())) append(task.getId(), 2L, "LEGACY_EXECUTION_LINKED", task.getStatus(), null, "Linked retry compatibility execution");
        return toVo(task, false);
    }
    /** A legacy document job can only be retried through a new one-time dispatch ticket. */
    private String copyLegacyExecution(String previousExecutionId, long expiresAt) {
        if (legacyExecutions == null) throw conflict("legacy compatibility retry is unavailable");
        AgentSandboxExecution previous = legacyExecutions.getById(previousExecutionId);
        if (previous == null) throw conflict("legacy execution ticket is unavailable for retry");
        AgentSandboxExecution retry = new AgentSandboxExecution();
        retry.setRunId(previous.getRunId()); retry.setSkillVersionId(previous.getSkillVersionId()); retry.setMessageId(previous.getMessageId()); retry.setUserId(previous.getUserId()); retry.setAgentDefinitionId(previous.getAgentDefinitionId());
        retry.setExecutionConfigSnapshot(previous.getExecutionConfigSnapshot()); retry.setResourceSnapshot(previous.getResourceSnapshot()); retry.setInputJson(previous.getInputJson()); retry.setStatus(0); retry.setExpiresAt(expiresAt);
        if (!legacyExecutions.save(retry)) throw conflict("failed to create legacy execution retry ticket");
        return retry.getId();
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public SandboxRunnerTaskVo claim(String runnerId) {
        recoverExpiredTasks(); long now = System.currentTimeMillis(); touchRunner(runnerId, null, now);
        // A task linked to agent_sandbox_execution is owned by the deployed
        // compatibility Runner. New-protocol workers must never claim it too.
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getStatus, QUEUED).isNull(SandboxExecutionTask::getLegacyExecutionId).gt(SandboxExecutionTask::getExpiresAt, now).orderByAsc(SandboxExecutionTask::getCreatedAt).last("limit 1"));
        if (task == null) return null;
        SandboxExecutionTemplate lockedTemplate = templates.selectOne(Wrappers.lambdaQuery(SandboxExecutionTemplate.class).eq(SandboxExecutionTemplate::getId, task.getTemplateId()).last("FOR UPDATE"));
        if (lockedTemplate == null || !Boolean.TRUE.equals(lockedTemplate.getEnabled())) return null;
        Map lockedConfig = JSON.parseObject(task.getConfigSnapshot(), Map.class);
        int maxConcurrent = boundedInt(lockedConfig.get("maxConcurrentTasks"), 0, 0, 1000);
        if (maxConcurrent > 0 && tasks.selectCount(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getTemplateId, task.getTemplateId()).in(SandboxExecutionTask::getStatus, CLAIMED, RUNNING)) >= maxConcurrent) return null;
        int maxConcurrentPerAgent = boundedInt(lockedConfig.get("maxConcurrentTasksPerAgent"), 0, 0, 1000);
        if (maxConcurrentPerAgent > 0 && StringUtils.isNotBlank(task.getAgentDefinitionId()) && tasks.selectCount(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getTemplateId, task.getTemplateId()).eq(SandboxExecutionTask::getAgentDefinitionId, task.getAgentDefinitionId()).in(SandboxExecutionTask::getStatus, CLAIMED, RUNNING)) >= maxConcurrentPerAgent) return null;
        String token = randomToken();
        int updated = tasks.update(null, Wrappers.lambdaUpdate(SandboxExecutionTask.class).set(SandboxExecutionTask::getStatus, CLAIMED).set(SandboxExecutionTask::getClaimedBy, runnerId).set(SandboxExecutionTask::getClaimedAt, now).set(SandboxExecutionTask::getLeaseExpiresAt, now + LEASE_TTL).set(SandboxExecutionTask::getExecutionTokenHash, hash(token)).eq(SandboxExecutionTask::getId, task.getId()).eq(SandboxExecutionTask::getStatus, QUEUED));
        if (updated != 1) return null;
        task.setStatus(CLAIMED); touchRunner(runnerId, task.getId(), now); append(task.getId(), nextSequence(task.getId()), "TASK_CLAIMED", CLAIMED, null, "Task claimed by runner");
        Map config = JSON.parseObject(task.getConfigSnapshot(), Map.class); Map input = JSON.parseObject(task.getInputSnapshot(), Map.class);
        List<SandboxRunnerInputArtifactVo> inputs = runnerInputArtifacts(input);
        if (input != null) input.remove("_sandboxInputArtifacts");
        SandboxRunnerTaskVo result = new SandboxRunnerTaskVo(); result.setTaskId(task.getId()); result.setExecutionToken(token); result.setTemplateCode(task.getTemplateCode()); result.setRuntime((String) config.get("runtime")); result.setExecutionMode(StringUtils.defaultIfBlank((String) config.get("executionMode"), "SCRIPT")); result.setFixedCommand((String) config.get("fixedCommand")); result.setImageRef((String) config.get("imageRef")); result.setTimeoutSeconds(number(config.get("timeoutSeconds"), 60)); result.setMaxMemoryMb(boundedInt(config.get("maxMemoryMb"), 512, 64, 4096)); result.setMaxCpuCores(boundedDouble(config.get("maxCpuCores"), 1D, 0.1D, 4D)); result.setMaxPids(boundedInt(config.get("maxPids"), 128, 16, 512)); result.setMaxTempDiskMb(boundedInt(config.get("maxTempDiskMb"), 64, 16, 1024)); result.setMaxOutputFiles(number(config.get("maxOutputFiles"), 1)); result.setMaxOutputBytes(longNumber(config.get("maxOutputBytes"), 52428800L)); result.setOutputFormats(JSON.parseArray(JSON.toJSONString(config.get("outputFormats")), String.class)); result.setInputArtifacts(inputs); result.setInput(input == null ? Collections.emptyMap() : input); return result;
    }
    @Override
    public SandboxTaskService.RunnerInputArtifact downloadInput(String taskId, String inputId, String token, String runnerId) {
        SandboxExecutionTask task = runnerTask(taskId, token, runnerId);
        Map input = JSON.parseObject(task.getInputSnapshot(), Map.class);
        for (Map<String, Object> item : frozenInputArtifacts(input)) {
            if (!StringUtils.equals(inputId, String.valueOf(item.get("id")))) continue;
            byte[] content = storage.getObject(artifactBucket, String.valueOf(item.get("objectKey")));
            if (content == null || content.length != longNumber(item.get("size"), -1L) || !StringUtils.equals(hash(content), String.valueOf(item.get("sha256")))) throw new ServerException(409, "sandbox frozen input integrity check failed");
            return new SandboxTaskService.RunnerInputArtifact(String.valueOf(item.get("fileName")), String.valueOf(item.get("contentType")), String.valueOf(item.get("sha256")), content);
        }
        throw new ServerException(404, "sandbox input not found");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void reportUsage(String taskId, String token, String runnerId, com.aether.agent.sandbox.dto.SandboxRunnerUsageDto usage) {
        SandboxExecutionTask task = runnerTask(taskId, token, runnerId);
        if (usage == null) throw bad("sandbox usage is required");
        SandboxExecutionResourceUsage record = resourceUsage.selectOne(Wrappers.lambdaQuery(SandboxExecutionResourceUsage.class).eq(SandboxExecutionResourceUsage::getTaskId, task.getId()));
        if (record == null) { record = new SandboxExecutionResourceUsage(); record.setTaskId(task.getId()); }
        record.setWallMillis(boundedLong(usage.getWallMillis(), 0L, 24L * 3600 * 1000)); record.setCpuMillis(boundedLong(usage.getCpuMillis(), 0L, 24L * 3600 * 1000)); record.setMaxRssBytes(boundedLong(usage.getMaxRssBytes(), 0L, 4L * 1024 * 1024 * 1024)); record.setOutputBytes(boundedLong(usage.getOutputBytes(), 0L, 1024L * 1024 * 1024)); record.setExitCode(usage.getExitCode()); record.setReportedAt(System.currentTimeMillis());
        if (StringUtils.isBlank(record.getId())) resourceUsage.insert(record); else resourceUsage.updateById(record);
        append(taskId, nextSequence(taskId), "RESOURCE_USAGE_REPORTED", task.getStatus(), null, "Execution resource usage reported");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public boolean heartbeat(String id, String token, String runnerId, Integer progress, String summary) {
        SandboxExecutionTask task = runnerTask(id, token, runnerId); touchRunner(runnerId, task.getId(), System.currentTimeMillis()); if (CANCELLED.equals(task.getStatus()) || task.getCancelRequestedAt() != null) return true;
        if (CLAIMED.equals(task.getStatus())) { task.setStatus(RUNNING); task.setStartedAt(System.currentTimeMillis()); append(id, nextSequence(id), "TASK_STARTED", RUNNING, progress, "Runner started task"); }
        if (logBlocked(task, summary)) { failForSensitiveLog(task); return true; }
        task.setLeaseExpiresAt(System.currentTimeMillis() + LEASE_TTL); task.setLogSummary(redact(summary)); tasks.updateById(task);
        if (progress != null || StringUtils.isNotBlank(summary)) append(id, nextSequence(id), "HEARTBEAT", task.getStatus(), progress, StringUtils.abbreviate(summary, 4096)); return false;
    }
    @Override public boolean cancelRequested(String id, String token, String runnerId) { SandboxExecutionTask task = runnerTask(id, token, runnerId); return task.getCancelRequestedAt() != null || CANCELLED.equals(task.getStatus()); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void runnerEvent(String id, String token, String runnerId, SandboxRunnerEventDto event) {
        SandboxExecutionTask task = runnerTask(id, token, runnerId); if (terminal(task.getStatus())) return;
        if (event == null || event.getSequence() == null || StringUtils.isBlank(event.getEventType())) throw bad("event sequence and type are required");
        if (logBlocked(task, event.getSummary())) { failForSensitiveLog(task); return; }
        SandboxExecutionEvent found = events.selectOne(Wrappers.lambdaQuery(SandboxExecutionEvent.class).eq(SandboxExecutionEvent::getTaskId, id).eq(SandboxExecutionEvent::getSequence, event.getSequence())); if (found == null) append(id, event.getSequence(), event.getEventType(), task.getStatus(), event.getProgress(), StringUtils.abbreviate(event.getSummary(), 4096));
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void succeed(String id, String token, String runnerId, String summary) { finish(id, token, runnerId, SUCCEEDED, null, null, summary); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void fail(String id, String token, String runnerId, String code, String reason, String summary) { finish(id, token, runnerId, "TIMED_OUT".equals(code) ? TIMED_OUT : "CANCELLED".equals(code) ? CANCELLED : FAILED, code, reason, summary); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void completeArtifact(String id, String token, String runnerId, String fileName, String contentType, byte[] content, String checksum, String summary, boolean finalArtifact) {
        SandboxExecutionTask task = runnerTask(id, token, runnerId); if (task.getCancelRequestedAt() != null || CANCELLED.equals(task.getStatus())) throw conflict("sandbox task was cancelled; artifact callback rejected"); Map config = JSON.parseObject(task.getConfigSnapshot(), Map.class);
        if (content == null || content.length == 0 || content.length > longNumber(config.get("maxOutputBytes"), 52_428_800L)) throw bad("sandbox artifact size is invalid");
        if (isTextual(contentType) && containsHighRiskSecret(new String(content, StandardCharsets.UTF_8))) throw bad("sandbox artifact contains a high-risk secret");
        ArtifactContentScanner.ScanResult scan = contentScanner.scan(fileName, contentType, content);
        if (!scan.isAllowed()) {
            append(id, nextSequence(id), "SENSITIVE_ARTIFACT_MATCH", task.getStatus(), null,
                    "Sensitive artifact blocked by scanner rule " + StringUtils.defaultIfBlank(scan.getRuleId(), "unknown"), hash(content));
            throw bad("sandbox artifact blocked by scanner rule " + StringUtils.defaultIfBlank(scan.getRuleId(), "unknown"));
        }
        if (StringUtils.isNotBlank(scan.getRuleId())) append(id, nextSequence(id), "SENSITIVE_ARTIFACT_MATCH", task.getStatus(), null, "Sensitive artifact rule matched: " + scan.getRuleId(), hash(content));
        String name = StringUtils.defaultString(fileName); String extension = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT) : "";
        List<String> formats = JSON.parseArray(JSON.toJSONString(config.get("outputFormats")), String.class); if (formats == null || !formats.contains(extension)) throw bad("sandbox artifact format is not declared");
        validateTextArtifactContentType(extension, contentType);
        if (!MessageDigest.isEqual(hash(content).getBytes(StandardCharsets.UTF_8), StringUtils.defaultString(checksum).getBytes(StandardCharsets.UTF_8))) throw conflict("sandbox artifact checksum mismatch");
        AgentArtifact duplicate = artifactService.getOne(Wrappers.lambdaQuery(AgentArtifact.class).eq(AgentArtifact::getExecutionId, id).eq(AgentArtifact::getFileName, name).eq(AgentArtifact::getContentSha256, checksum).eq(AgentArtifact::getStatus, 1));
        if (duplicate != null) { if (finalArtifact) finish(id, token, runnerId, SUCCEEDED, null, null, summary); return; }
        if (artifactService.count(Wrappers.lambdaQuery(AgentArtifact.class).eq(AgentArtifact::getExecutionId, id)) >= number(config.get("maxOutputFiles"), 1)) throw conflict("sandbox artifact count exceeded");
        AgentArtifact artifact = new AgentArtifact(); artifact.setExecutionId(id); artifact.setRunId(StringUtils.defaultIfBlank(task.getRunId(), "sandbox:" + id)); artifact.setSkillVersionId(task.getTemplateVersionId()); artifact.setUserId(task.getRequesterUserId()); artifact.setAgentDefinitionId(task.getAgentDefinitionId()); artifact.setFileName(name); artifact.setObjectKey("chat/artifacts/" + id + "/" + UUID.randomUUID() + "." + extension); artifact.setContentSha256(checksum); artifact.setCallbackKey("sandbox:" + id + ":" + hash(name + ":" + checksum)); artifact.setContentType(StringUtils.defaultIfBlank(contentType, "application/octet-stream")); artifact.setSize((long) content.length); artifact.setExpiresAt(System.currentTimeMillis() + 7L * 24 * 3600 * 1000); artifact.setLogSummary(redact(summary)); artifact.setStatus(1);
        storage.upload(artifactBucket, artifact.getObjectKey(), content, artifact.getContentType());
        try { artifactService.save(artifact); }
        catch (RuntimeException e) { try { storage.removeObject(artifactBucket, artifact.getObjectKey()); } catch (Exception ignored) { } AgentArtifact existing = artifactService.getOne(Wrappers.lambdaQuery(AgentArtifact.class).eq(AgentArtifact::getCallbackKey, artifact.getCallbackKey()).eq(AgentArtifact::getStatus, 1)); if (existing != null) { if (finalArtifact) finish(id, token, runnerId, SUCCEEDED, null, null, summary); return; } throw e; }
        if (finalArtifact) finish(id, token, runnerId, SUCCEEDED, null, null, summary);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void linkLegacyExecution(String taskId, String legacyExecutionId) {
        SandboxExecutionTask task = tasks.selectById(taskId); if (task == null) throw new ServerException(404, "sandbox task not found");
        task.setLegacyExecutionId(legacyExecutionId); tasks.updateById(task); append(taskId, nextSequence(taskId), "LEGACY_EXECUTION_LINKED", task.getStatus(), null, "Linked compatibility execution");
    }
    @Override
    public boolean legacyReadyForClaim(String legacyExecutionId) {
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getLegacyExecutionId, legacyExecutionId));
        // Unlinked records are historical compatibility jobs. New platform tasks
        // must be approved into QUEUED before the old Runner can observe them.
        return task == null || QUEUED.equals(task.getStatus());
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void legacyExecutionStarted(String legacyExecutionId, String runnerId) {
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getLegacyExecutionId, legacyExecutionId));
        if (task == null) return;
        if (!QUEUED.equals(task.getStatus())) throw conflict("linked sandbox task is not queued for compatibility execution");
        long now = System.currentTimeMillis();
        task.setStatus(RUNNING); task.setClaimedBy(StringUtils.abbreviate(runnerId, 128)); task.setClaimedAt(now); task.setStartedAt(now); task.setLeaseExpiresAt(now + LEASE_TTL); tasks.updateById(task);
        append(task.getId(), nextSequence(task.getId()), "TASK_CLAIMED", CLAIMED, null, "Compatibility runner claimed task");
        append(task.getId(), nextSequence(task.getId()), "TASK_STARTED", RUNNING, 0, "Compatibility runner started task");
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void completeLegacyExecution(String legacyExecutionId, boolean success, String reason, String summary) {
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getLegacyExecutionId, legacyExecutionId));
        if (task == null) return;
        if (terminal(task.getStatus())) return;
        if (!RUNNING.equals(task.getStatus())) throw conflict("linked sandbox task is not running");
        if (logBlocked(task, summary)) { failForSensitiveLog(task); return; }
        long now = System.currentTimeMillis(); task.setStatus(success ? SUCCEEDED : FAILED); task.setCompletedAt(now); task.setFailureCode(success ? null : "LEGACY_RUNNER_FAILED"); task.setFailureReason(redact(reason)); task.setLogSummary(redact(summary)); tasks.updateById(task);
        append(task.getId(), nextSequence(task.getId()), success ? "TASK_SUCCEEDED" : "TASK_FAILED", task.getStatus(), success ? 100 : null, task.getLogSummary());
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public boolean legacyHeartbeat(String legacyExecutionId, String summary) {
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getLegacyExecutionId, legacyExecutionId));
        if (task == null || terminal(task.getStatus())) return task != null && CANCELLED.equals(task.getStatus());
        if (task.getCancelRequestedAt() != null) return true;
        long now = System.currentTimeMillis(); if (QUEUED.equals(task.getStatus()) || CLAIMED.equals(task.getStatus())) { task.setStatus(RUNNING); task.setStartedAt(now); append(task.getId(), nextSequence(task.getId()), "TASK_STARTED", RUNNING, 0, "Compatibility runner started task"); }
        if (logBlocked(task, summary)) { failForSensitiveLog(task); return true; }
        task.setLeaseExpiresAt(now + LEASE_TTL); task.setLogSummary(redact(summary)); tasks.updateById(task);
        if (StringUtils.isNotBlank(summary)) append(task.getId(), nextSequence(task.getId()), "HEARTBEAT", RUNNING, null, task.getLogSummary()); return false;
    }
    @Override public boolean legacyCancelRequested(String legacyExecutionId) { SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getLegacyExecutionId, legacyExecutionId)); return task != null && (task.getCancelRequestedAt() != null || CANCELLED.equals(task.getStatus())); }
    private void finish(String id, String token, String runnerId, String status, String code, String reason, String summary) {
        SandboxExecutionTask task = runnerTask(id, token, runnerId); if (terminal(task.getStatus())) return; long now = System.currentTimeMillis();
        if (logBlocked(task, summary)) { failForSensitiveLog(task); return; }
        task.setStatus(task.getCancelRequestedAt() == null ? status : CANCELLED); task.setCompletedAt(now); task.setFailureCode(StringUtils.abbreviate(code, 64)); task.setFailureReason(redact(reason)); task.setLogSummary(redact(summary)); tasks.updateById(task); append(id, nextSequence(id), "TASK_" + task.getStatus(), task.getStatus(), null, task.getLogSummary());
    }
    @Override public SandboxTaskVo detail(String id, String userId, boolean admin) { return toVo(owned(id, userId, admin), true); }
    @Override public SandboxTaskVo byRun(String runId, String userId, boolean admin) {
        SandboxExecutionTask task = tasks.selectOne(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getRunId, runId).eq(!admin, SandboxExecutionTask::getRequesterUserId, userId).orderByDesc(SandboxExecutionTask::getCreatedAt).last("limit 1"));
        return task == null ? null : toVo(task, true);
    }
    @Override public List<SandboxTaskVo.SandboxEventVo> events(String id, String userId, boolean admin) { owned(id, userId, admin); return eventVos(id); }
    @Override public List<SandboxExecutionTemplate> templates() { return templates.selectList(Wrappers.lambdaQuery(SandboxExecutionTemplate.class).orderByAsc(SandboxExecutionTemplate::getCode)); }
    @Override @Transactional(rollbackFor = Exception.class)
    public void setTemplateEnabled(String templateId, boolean enabled) {
        SandboxExecutionTemplate template = templates.selectById(templateId); if (template == null) throw new ServerException(404, "sandbox template not found");
        if (enabled) { SandboxExecutionTemplateVersion version = versions.selectById(template.getCurrentVersionId()); if (version == null || !Boolean.TRUE.equals(version.getPublished())) throw conflict("sandbox template has no published version"); Map config = JSON.parseObject(version.getConfigSnapshot(), Map.class); if (!"NONE".equals(StringUtils.defaultIfBlank((String) config.get("network"), "NONE"))) throw conflict("sandbox template requires an unavailable restricted egress backend"); if (Boolean.TRUE.equals(config.get("requireImageDigest")) && !validDigestImage((String) config.get("imageRef"))) throw conflict("sandbox template requires a digest-pinned imageRef"); }
        template.setEnabled(enabled); templates.updateById(template);
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public SandboxExecutionTemplateVersion publishTemplateVersion(String templateId, String administratorUserId, com.aether.agent.sandbox.dto.SandboxTemplateVersionPublishDto request) {
        if (request == null || StringUtils.isBlank(request.getConfigSnapshot()) || StringUtils.isBlank(request.getPolicyVersion())) throw bad("template configuration and policy version are required");
        SandboxExecutionTemplate template = templates.selectOne(Wrappers.lambdaQuery(SandboxExecutionTemplate.class).eq(SandboxExecutionTemplate::getId, templateId).last("FOR UPDATE"));
        if (template == null) throw new ServerException(404, "sandbox template not found");
        Map config;
        try { config = JSON.parseObject(request.getConfigSnapshot(), Map.class); } catch (Exception e) { throw bad("template configuration must be valid JSON"); }
        validateTemplateConfig(config);
        if (StringUtils.isNotBlank(request.getRiskLevel()) && !Arrays.asList("LOW", "MEDIUM", "HIGH").contains(request.getRiskLevel().toUpperCase(Locale.ROOT))) throw bad("sandbox risk level is not allowlisted");
        List<SandboxExecutionTemplateVersion> existing = versions.selectList(Wrappers.lambdaQuery(SandboxExecutionTemplateVersion.class).eq(SandboxExecutionTemplateVersion::getTemplateId, templateId).orderByDesc(SandboxExecutionTemplateVersion::getVersion));
        int nextVersion = existing.isEmpty() || existing.get(0).getVersion() == null ? 1 : existing.get(0).getVersion() + 1;
        long now = System.currentTimeMillis(); SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion();
        version.setTemplateId(templateId); version.setVersion(nextVersion); version.setPublished(true); version.setConfigSnapshot(JSON.toJSONString(config)); version.setPolicyVersion(StringUtils.abbreviate(request.getPolicyVersion(), 64)); version.setPublishedBy(administratorUserId); version.setPublishedAt(now); versions.insert(version);
        template.setCurrentVersionId(version.getId());
        if (StringUtils.isNotBlank(request.getRiskLevel())) template.setRiskLevel(request.getRiskLevel().toUpperCase(Locale.ROOT));
        // Docker Runner has no egress proxy. Switching an enabled template to a networked
        // policy must therefore fail closed immediately, before any task can be queued.
        if (!"NONE".equals(StringUtils.upperCase(String.valueOf(config.get("network"))))) template.setEnabled(false);
        templates.updateById(template);
        return version;
    }
    @Override public List<SandboxExecutionTemplateVersion> versions(String id) { return versions.selectList(Wrappers.lambdaQuery(SandboxExecutionTemplateVersion.class).eq(SandboxExecutionTemplateVersion::getTemplateId, id).orderByDesc(SandboxExecutionTemplateVersion::getVersion)); }
    @Override public Page<SandboxTaskVo> audit(com.aether.agent.sandbox.dto.SandboxAuditQueryDto query) {
        com.aether.agent.sandbox.dto.SandboxAuditQueryDto q = query == null ? new com.aether.agent.sandbox.dto.SandboxAuditQueryDto() : query;
        long current = q.getCurrent() == null ? 1L : Math.max(q.getCurrent(), 1L); long pageSize = q.getPageSize() == null ? 20L : Math.min(Math.max(q.getPageSize(), 1L), 100L);
        List<String> approvedTaskIds = StringUtils.isBlank(q.getApproverUserId()) ? null : approvals.selectList(Wrappers.lambdaQuery(SandboxExecutionApproval.class).eq(SandboxExecutionApproval::getApproverUserId, q.getApproverUserId())).stream().map(SandboxExecutionApproval::getTaskId).distinct().collect(Collectors.toList());
        if (approvedTaskIds != null && approvedTaskIds.isEmpty()) return new Page<>(current, pageSize, 0);
        Page<SandboxExecutionTask> page = tasks.selectPage(new Page<>(current, pageSize), Wrappers.lambdaQuery(SandboxExecutionTask.class).in(approvedTaskIds != null, SandboxExecutionTask::getId, approvedTaskIds).eq(StringUtils.isNotBlank(q.getStatus()), SandboxExecutionTask::getStatus, q.getStatus()).eq(StringUtils.isNotBlank(q.getTemplateCode()), SandboxExecutionTask::getTemplateCode, q.getTemplateCode()).eq(StringUtils.isNotBlank(q.getRequesterUserId()), SandboxExecutionTask::getRequesterUserId, q.getRequesterUserId()).eq(StringUtils.isNotBlank(q.getAgentDefinitionId()), SandboxExecutionTask::getAgentDefinitionId, q.getAgentDefinitionId()).eq(StringUtils.isNotBlank(q.getRiskLevel()), SandboxExecutionTask::getRiskLevel, q.getRiskLevel()).ge(q.getStartTime() != null, SandboxExecutionTask::getCreatedAt, q.getStartTime()).le(q.getEndTime() != null, SandboxExecutionTask::getCreatedAt, q.getEndTime()).orderByDesc(SandboxExecutionTask::getCreatedAt));
        Page<SandboxTaskVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal()); result.setRecords(page.getRecords().stream().map(task -> toVo(task, false)).collect(Collectors.toList())); return result;
    }
    @Override public com.aether.agent.sandbox.vo.SandboxMetricsVo metrics() {
        com.aether.agent.sandbox.vo.SandboxMetricsVo result = new com.aether.agent.sandbox.vo.SandboxMetricsVo();
        long now = System.currentTimeMillis();
        // Keep expensive latency/resource scans bounded while the status counters
        // remain exact for the whole retained task history.
        long windowStart = now - 30L * 24 * 60 * 60 * 1000;
        result.setWindowStartAt(windowStart);
        result.setPendingApproval(countStatus(PENDING_APPROVAL)); result.setQueued(countStatus(QUEUED)); result.setRunning(countStatus(RUNNING));
        result.setSucceeded(countStatus(SUCCEEDED)); result.setFailed(countStatus(FAILED)); result.setTimedOut(countStatus(TIMED_OUT));
        result.setCancelled(countStatus(CANCELLED)); result.setExpired(countStatus(EXPIRED));
        result.setSensitiveHits(events.selectCount(Wrappers.lambdaQuery(SandboxExecutionEvent.class).in(SandboxExecutionEvent::getEventType, "SENSITIVE_INPUT_MATCH", "SENSITIVE_ARTIFACT_MATCH", "SENSITIVE_LOG_MATCH")));
        List<SandboxExecutionTask> recent = tasks.selectList(Wrappers.lambdaQuery(SandboxExecutionTask.class).ge(SandboxExecutionTask::getCreatedAt, windowStart));
        long waitTotal = 0L, waitCount = 0L, executionTotal = 0L, executionCount = 0L, terminal = 0L, unpinned = 0L;
        Map<String, Long> failureTypes = new TreeMap<>();
        for (SandboxExecutionTask task : recent) {
            if (task.getClaimedAt() != null && task.getCreatedAt() != null && task.getClaimedAt() >= task.getCreatedAt()) { waitTotal += task.getClaimedAt() - task.getCreatedAt(); waitCount++; }
            if (task.getCompletedAt() != null && task.getStartedAt() != null && task.getCompletedAt() >= task.getStartedAt()) { executionTotal += task.getCompletedAt() - task.getStartedAt(); executionCount++; }
            if (isTerminal(task.getStatus())) terminal++;
            if ((FAILED.equals(task.getStatus()) || TIMED_OUT.equals(task.getStatus())) && StringUtils.isNotBlank(task.getFailureCode())) failureTypes.merge(task.getFailureCode(), 1L, Long::sum);
            if (!usesDigestPinnedImage(task.getConfigSnapshot())) unpinned++;
        }
        result.setTerminalTasks(terminal); result.setAverageQueueWaitMillis(waitCount == 0 ? null : waitTotal / waitCount);
        result.setAverageExecutionMillis(executionCount == 0 ? null : executionTotal / executionCount); result.setFailureTypes(failureTypes); result.setUnpinnedImageTaskCount(unpinned);
        long completedOutcomes = result.getSucceeded() + result.getFailed() + result.getTimedOut() + result.getCancelled();
        result.setSuccessRatePercent(completedOutcomes == 0 ? null : Math.round(result.getSucceeded() * 10000D / completedOutcomes) / 100D);
        List<SandboxExecutionResourceUsage> usage = resourceUsage.selectList(Wrappers.lambdaQuery(SandboxExecutionResourceUsage.class).ge(SandboxExecutionResourceUsage::getCreatedAt, windowStart));
        long wallMillis = 0L, outputBytes = 0L;
        for (SandboxExecutionResourceUsage item : usage) { wallMillis += item.getWallMillis() == null ? 0L : item.getWallMillis(); outputBytes += item.getOutputBytes() == null ? 0L : item.getOutputBytes(); }
        result.setTotalWallMillis(wallMillis); result.setTotalOutputBytes(outputBytes);
        List<SandboxRunnerNode> nodes = runnerNodes.selectList(Wrappers.lambdaQuery(SandboxRunnerNode.class));
        long active = nodes.stream().filter(node -> node.getLastSeenAt() != null && node.getLastSeenAt() >= now - LEASE_TTL * 3).count();
        result.setRegisteredRunners((long) nodes.size()); result.setActiveRunners(active); result.setStaleRunners(nodes.size() - active);
        return result;
    }
    private Long countStatus(String status) { return tasks.selectCount(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getStatus, status)); }
    private boolean isTerminal(String status) { return SUCCEEDED.equals(status) || FAILED.equals(status) || TIMED_OUT.equals(status) || CANCELLED.equals(status) || EXPIRED.equals(status); }
    private boolean usesDigestPinnedImage(String configSnapshot) {
        try { Map config = JSON.parseObject(configSnapshot, Map.class); return config != null && validDigestImage(String.valueOf(config.get("imageRef"))); }
        catch (Exception ignored) { return false; }
    }
    private void touchRunner(String runnerId, String taskId, long now) {
        if (runnerNodes == null || StringUtils.isBlank(runnerId)) return;
        SandboxRunnerNode node = runnerNodes.selectOne(Wrappers.lambdaQuery(SandboxRunnerNode.class).eq(SandboxRunnerNode::getRunnerId, runnerId));
        if (node == null) { node = new SandboxRunnerNode(); node.setId(UUID.randomUUID().toString().replace("-", "")); node.setRunnerId(runnerId); node.setFirstSeenAt(now); }
        node.setLastSeenAt(now); if (taskId != null) node.setLastClaimedAt(now); node.setCurrentTaskId(taskId);
        if (node.getCreatedAt() == null) runnerNodes.insert(node); else runnerNodes.updateById(node);
    }
    private SandboxExecutionTask owned(String id, String userId, boolean admin) { SandboxExecutionTask task = tasks.selectById(id); if (task == null || (!admin && !StringUtils.equals(userId, task.getRequesterUserId()))) throw new ServerException(404, "sandbox task not found"); return task; }
    private SandboxExecutionTask runnerTask(String id, String token, String runnerId) {
        // Authentication is performed by the controller against the rotating
        // shared token. This service receives the stable Runner instance ID.
        if (StringUtils.isBlank(runnerId) || !runnerId.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}")) throw new ServerException(401, "sandbox runner identity unauthorized");
        SandboxExecutionTask task = tasks.selectById(id); long now = System.currentTimeMillis();
        if (task == null || !StringUtils.equals(task.getClaimedBy(), runnerId) || !MessageDigest.isEqual(StringUtils.defaultString(task.getExecutionTokenHash()).getBytes(StandardCharsets.UTF_8), hash(token).getBytes(StandardCharsets.UTF_8))) throw new ServerException(401, "sandbox execution token unauthorized");
        if ((!CLAIMED.equals(task.getStatus()) && !RUNNING.equals(task.getStatus())) || task.getExpiresAt() <= now || task.getLeaseExpiresAt() == null || task.getLeaseExpiresAt() <= now) throw new ServerException(409, "sandbox task lease is no longer active");
        return task;
    }
    @Override @Transactional(rollbackFor = Exception.class)
    public void recoverExpiredTasks() { long now = System.currentTimeMillis(); for (SandboxExecutionTask t : tasks.selectList(Wrappers.lambdaQuery(SandboxExecutionTask.class).in(SandboxExecutionTask::getStatus, PENDING_APPROVAL, QUEUED, CLAIMED, RUNNING).le(SandboxExecutionTask::getExpiresAt, now))) expire(t, now); for (SandboxExecutionTask t : tasks.selectList(Wrappers.lambdaQuery(SandboxExecutionTask.class).in(SandboxExecutionTask::getStatus, CLAIMED, RUNNING).isNotNull(SandboxExecutionTask::getLeaseExpiresAt).le(SandboxExecutionTask::getLeaseExpiresAt, now))) { boolean cancelled = t.getCancelRequestedAt() != null; t.setStatus(cancelled ? CANCELLED : TIMED_OUT); t.setCompletedAt(now); t.setFailureCode(cancelled ? "CANCELLED_RUNNER_LOST" : "LEASE_EXPIRED"); tasks.updateById(t); append(t.getId(), nextSequence(t.getId()), cancelled ? "TASK_CANCELLED" : "LEASE_EXPIRED", t.getStatus(), null, cancelled ? "Cancellation recovered after runner lease expired" : "Runner lease expired"); } }
    @Override @Transactional(rollbackFor = Exception.class)
    public void purgeExpiredRetentionData() {
        long now = System.currentTimeMillis(), logCutoff = now - 30L * 24 * 3600 * 1000, inputCutoff = now - 7L * 24 * 3600 * 1000;
        Map<String, Integer> removedByTask = new HashMap<>();
        for (SandboxExecutionEvent event : events.selectList(Wrappers.lambdaQuery(SandboxExecutionEvent.class).lt(SandboxExecutionEvent::getOccurredAt, logCutoff))) { removedByTask.put(event.getTaskId(), removedByTask.getOrDefault(event.getTaskId(), 0) + 1); events.deleteById(event.getId()); }
        for (Map.Entry<String, Integer> entry : removedByTask.entrySet()) { SandboxExecutionTask task = tasks.selectById(entry.getKey()); if (task != null) append(task.getId(), nextSequence(task.getId()), "LOG_RETENTION_PURGED", task.getStatus(), null, "Expired execution logs purged: " + entry.getValue()); }
        for (SandboxExecutionTask task : tasks.selectList(Wrappers.lambdaQuery(SandboxExecutionTask.class).in(SandboxExecutionTask::getStatus, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, EXPIRED).isNotNull(SandboxExecutionTask::getCompletedAt).lt(SandboxExecutionTask::getCompletedAt, logCutoff).isNotNull(SandboxExecutionTask::getLogSummary))) { task.setLogSummary(null); tasks.updateById(task); }
        for (SandboxExecutionTask task : tasks.selectList(Wrappers.lambdaQuery(SandboxExecutionTask.class).in(SandboxExecutionTask::getStatus, SUCCEEDED, FAILED, TIMED_OUT, CANCELLED, EXPIRED).isNull(SandboxExecutionTask::getInputPurgedAt).isNotNull(SandboxExecutionTask::getCompletedAt).lt(SandboxExecutionTask::getCompletedAt, inputCutoff))) {
            List<Map<String, Object>> inputs = frozenInputArtifacts(JSON.parseObject(task.getInputSnapshot(), Map.class));
            for (Map<String, Object> input : inputs) try { storage.removeObject(artifactBucket, String.valueOf(input.get("objectKey"))); } catch (Exception ignored) { }
            task.setInputPurgedAt(now); task.setLogSummary(null); tasks.updateById(task); append(task.getId(), nextSequence(task.getId()), "INPUT_RETENTION_PURGED", task.getStatus(), null, "Expired task-private inputs purged: " + inputs.size());
        }
        // Runner rows are operational liveness observations, not task audit data.
        // Retain enough history to investigate outages, then remove stale nodes.
        if (runnerNodes != null) for (SandboxRunnerNode node : runnerNodes.selectList(Wrappers.lambdaQuery(SandboxRunnerNode.class).lt(SandboxRunnerNode::getLastSeenAt, logCutoff))) runnerNodes.deleteById(node.getId());
    }
    private void expire(SandboxExecutionTask task, long now) { if (terminal(task.getStatus())) return; task.setStatus(EXPIRED); task.setCompletedAt(now); tasks.updateById(task); append(task.getId(), nextSequence(task.getId()), "TASK_EXPIRED", EXPIRED, null, "Task expired"); }
    private void append(String taskId, long sequence, String type, String status, Integer progress, String summary) { append(taskId, sequence, type, status, progress, summary, null); }
    private void append(String taskId, long sequence, String type, String status, Integer progress, String summary, String subjectSha256) { SandboxExecutionEvent event = new SandboxExecutionEvent(); event.setTaskId(taskId); event.setSequence(sequence); event.setEventType(type); event.setStatus(status); event.setProgress(progress); event.setSummary(redact(summary)); event.setSubjectSha256(subjectSha256); event.setOccurredAt(System.currentTimeMillis()); events.insert(event); }
    private long nextSequence(String taskId) { SandboxExecutionEvent latest = events.selectOne(Wrappers.lambdaQuery(SandboxExecutionEvent.class).eq(SandboxExecutionEvent::getTaskId, taskId).orderByDesc(SandboxExecutionEvent::getSequence).last("limit 1")); return latest == null ? 1L : latest.getSequence() + 1; }
    private SandboxTaskVo toVo(SandboxExecutionTask task, boolean includeEvents) { SandboxTaskVo vo = new SandboxTaskVo(); BeanUtils.copyProperties(task, vo); vo.setId(task.getId()); vo.setCancelRequested(task.getCancelRequestedAt() != null); vo.setApprovalSummary(approvalSummary(task)); SandboxExecutionResourceUsage usage = resourceUsage.selectOne(Wrappers.lambdaQuery(SandboxExecutionResourceUsage.class).eq(SandboxExecutionResourceUsage::getTaskId, task.getId())); if (usage != null) { com.aether.agent.sandbox.vo.SandboxExecutionResourceUsageVo usageVo = new com.aether.agent.sandbox.vo.SandboxExecutionResourceUsageVo(); BeanUtils.copyProperties(usage, usageVo); vo.setResourceUsage(usageVo); } if (includeEvents) { vo.setEvents(eventVos(task.getId())); vo.setApprovals(approvalVos(task.getId())); } return vo; }
    private List<com.aether.agent.sandbox.vo.SandboxApprovalVo> approvalVos(String taskId) {
        List<SandboxExecutionApproval> approvalRecords = approvals.selectList(Wrappers.lambdaQuery(SandboxExecutionApproval.class).eq(SandboxExecutionApproval::getTaskId, taskId).orderByAsc(SandboxExecutionApproval::getDecidedAt));
        Set<String> approverIds = approvalRecords.stream().map(SandboxExecutionApproval::getApproverUserId).filter(StringUtils::isNotBlank).collect(Collectors.toSet());
        Map<String, String> approverNames = approverIds.isEmpty() || userService == null ? Collections.emptyMap() : userService.listByIds(approverIds).stream().collect(Collectors.toMap(User::getId, User::getUsername, (left, right) -> left));
        return approvalRecords.stream().map(approval -> { com.aether.agent.sandbox.vo.SandboxApprovalVo approvalVo = new com.aether.agent.sandbox.vo.SandboxApprovalVo(); BeanUtils.copyProperties(approval, approvalVo); approvalVo.setApproverName(approverNames.get(approvalVo.getApproverUserId())); approvalVo.setReason(redact(approvalVo.getReason())); return approvalVo; }).collect(Collectors.toList());
    }
    private Map<String, Object> approvalSummary(SandboxExecutionTask task) { Map config = JSON.parseObject(task.getConfigSnapshot(), Map.class); if (!"WEB_COLLECTION".equals(String.valueOf(config.get("executionMode")))) return null; Map input = JSON.parseObject(task.getInputSnapshot(), Map.class); Map<String, Object> summary = new LinkedHashMap<>(); summary.put("targetUrl", input.get("targetUrl")); summary.put("purpose", redact(String.valueOf(input.get("purpose")))); summary.put("allowedDomains", config.get("allowedDomains")); summary.put("allowSubdomains", config.get("allowSubdomains")); summary.put("estimatedRequests", input.get("estimatedRequests")); summary.put("pageDepth", input.get("pageDepth")); summary.put("maxRequests", config.get("maxRequests")); summary.put("maxPageDepth", config.get("maxPageDepth")); summary.put("externalSensitiveRisk", true); return summary; }
    private List<SandboxTaskVo.SandboxEventVo> eventVos(String id) { return events.selectList(Wrappers.lambdaQuery(SandboxExecutionEvent.class).eq(SandboxExecutionEvent::getTaskId, id).orderByAsc(SandboxExecutionEvent::getSequence)).stream().map(e -> { SandboxTaskVo.SandboxEventVo vo = new SandboxTaskVo.SandboxEventVo(); BeanUtils.copyProperties(e, vo); return vo; }).collect(Collectors.toList()); }
    private boolean terminal(String s) { return SUCCEEDED.equals(s) || FAILED.equals(s) || TIMED_OUT.equals(s) || CANCELLED.equals(s) || EXPIRED.equals(s); }
    private void validateScriptSlot(com.aether.agent.sandbox.dto.SandboxTaskCreateDto request, Map config) {
        if (StringUtils.isBlank(request.getScript())) return;
        if (!Boolean.TRUE.equals(config.get("scriptSlot"))) throw bad("sandbox template does not allow scripts");
        if (request.getScript().length() > 1_048_576) throw bad("sandbox script exceeds 1 MiB limit");
        String language = StringUtils.upperCase(StringUtils.trim(request.getScriptLanguage()));
        String runtime = StringUtils.upperCase(String.valueOf(config.get("runtime")));
        if (!"PYTHON".equals(language) && !"NODE".equals(language)) throw bad("sandbox script language is unsupported");
        if (!language.equals(runtime)) throw bad("sandbox script language does not match the template runtime");
    }
    /** Per-template quotas are frozen into the task policy; counts are bounded to prevent abusive values. */
    private void enforceDailyQuota(String userId, String agentId, String templateCode, Map config) {
        long start = java.time.LocalDate.now(java.time.ZoneOffset.UTC).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        int maxPerUser = boundedInt(config.get("maxDailyTasksPerUser"), 0, 0, 100_000);
        if (maxPerUser > 0 && tasks.selectCount(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getTemplateCode, templateCode).eq(SandboxExecutionTask::getRequesterUserId, userId).ge(SandboxExecutionTask::getCreatedAt, start)) >= maxPerUser) throw new ServerException(429, "sandbox template daily user quota exceeded");
        int maxPerAgent = boundedInt(config.get("maxDailyTasksPerAgent"), 0, 0, 100_000);
        if (maxPerAgent > 0 && StringUtils.isNotBlank(agentId) && tasks.selectCount(Wrappers.lambdaQuery(SandboxExecutionTask.class).eq(SandboxExecutionTask::getTemplateCode, templateCode).eq(SandboxExecutionTask::getAgentDefinitionId, agentId).ge(SandboxExecutionTask::getCreatedAt, start)) >= maxPerAgent) throw new ServerException(429, "sandbox template daily agent quota exceeded");
    }
    /** Copies only requester-owned, validated library artifacts into a task-private object prefix. */
    private List<Map<String, Object>> freezeInputArtifacts(String userId, String taskId, List<String> sourceIds, Map config) {
        if (sourceIds == null || sourceIds.isEmpty()) return Collections.emptyList();
        int maxFiles = number(config.get("maxInputFiles"), 0);
        if (maxFiles <= 0) throw bad("sandbox template does not allow input artifacts");
        if (sourceIds.size() > maxFiles || new HashSet<String>(sourceIds).size() != sourceIds.size()) throw bad("sandbox input artifact count is invalid");
        List<String> allowedFormats = JSON.parseArray(JSON.toJSONString(config.get("inputFormats")), String.class);
        long total = 0L; List<Map<String, Object>> result = new ArrayList<>();
        try {
            for (String sourceId : sourceIds) {
                AgentArtifact source = artifactService.requireOwned(sourceId, userId, false);
                String name = safeFileName(source.getFileName()); String extension = extension(name);
                if (allowedFormats == null || !allowedFormats.contains(extension)) throw bad("sandbox input artifact format is not declared");
                byte[] bytes = storage.getObject(artifactBucket, source.getObjectKey());
                if (bytes == null || source.getSize() == null || bytes.length != source.getSize().longValue() || !StringUtils.equals(hash(bytes), source.getContentSha256())) throw new ServerException(409, "sandbox source artifact integrity check failed");
                if ("zip".equals(extension)) validateSourceArchive(bytes);
                ArtifactContentScanner.ScanResult scan = contentScanner.scan(name, source.getContentType(), bytes);
                if (!scan.isAllowed()) throw bad("sandbox input blocked by scanner rule " + StringUtils.defaultIfBlank(scan.getRuleId(), "unknown"));
                total += bytes.length; if (total > maxInputBytes(config)) throw bad("sandbox input exceeds the template limit");
                String id = UUID.randomUUID().toString().replace("-", ""); String key = "sandbox/inputs/" + taskId + "/" + id + "-" + name;
                storage.upload(artifactBucket, key, bytes, source.getContentType());
                Map<String, Object> frozen = new LinkedHashMap<>(); frozen.put("id", id); frozen.put("sourceArtifactId", source.getId()); frozen.put("fileName", name); frozen.put("contentType", StringUtils.defaultIfBlank(source.getContentType(), "application/octet-stream")); frozen.put("size", (long) bytes.length); frozen.put("sha256", hash(bytes)); frozen.put("objectKey", key); if (StringUtils.isNotBlank(scan.getRuleId())) frozen.put("sensitiveRule", scan.getRuleId()); result.add(frozen);
            }
            return result;
        } catch (RuntimeException e) { removeFrozenInputs(result); throw e; }
    }
    /** A retry gets its own private object prefix so expiration of the original task cannot break it. */
    private List<Map<String, Object>> cloneFrozenInputs(List<Map<String, Object>> originals, String taskId) {
        if (originals.isEmpty()) return Collections.emptyList();
        List<Map<String, Object>> copies = new ArrayList<>();
        try {
            for (Map<String, Object> original : originals) {
                String name = safeFileName(String.valueOf(original.get("fileName"))); String oldKey = String.valueOf(original.get("objectKey"));
                byte[] bytes = storage.getObject(artifactBucket, oldKey);
                if (bytes == null || bytes.length != longNumber(original.get("size"), -1L) || !StringUtils.equals(hash(bytes), String.valueOf(original.get("sha256")))) throw new ServerException(409, "sandbox retry input integrity check failed");
                String copyId = UUID.randomUUID().toString().replace("-", ""); String key = "sandbox/inputs/" + taskId + "/" + copyId + "-" + name;
                storage.upload(artifactBucket, key, bytes, String.valueOf(original.get("contentType")));
                Map<String, Object> copy = new LinkedHashMap<>(original); copy.put("id", copyId); copy.put("objectKey", key); copies.add(copy);
            }
            return copies;
        } catch (RuntimeException e) { removeFrozenInputs(copies); throw e; }
    }
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> frozenInputArtifacts(Map input) {
        if (input == null || !(input.get("_sandboxInputArtifacts") instanceof List)) return Collections.emptyList();
        return ((List<?>) input.get("_sandboxInputArtifacts")).stream().filter(Map.class::isInstance).map(value -> (Map<String, Object>) value).collect(Collectors.toList());
    }
    private List<SandboxRunnerInputArtifactVo> runnerInputArtifacts(Map input) {
        return frozenInputArtifacts(input).stream().map(item -> { SandboxRunnerInputArtifactVo vo = new SandboxRunnerInputArtifactVo(); vo.setId(String.valueOf(item.get("id"))); vo.setFileName(String.valueOf(item.get("fileName"))); vo.setContentType(String.valueOf(item.get("contentType"))); vo.setSha256(String.valueOf(item.get("sha256"))); vo.setSize(longNumber(item.get("size"), 0L)); return vo; }).collect(Collectors.toList());
    }
    private void removeFrozenInputs(List<Map<String, Object>> items) { for (Map<String, Object> item : items) try { storage.removeObject(artifactBucket, String.valueOf(item.get("objectKey"))); } catch (Exception ignored) { } }
    private String safeFileName(String value) { String name = StringUtils.defaultIfBlank(value, "input").replace('\\', '/'); name = name.substring(name.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_"); if (StringUtils.isBlank(name) || ".".equals(name) || "..".equals(name)) throw bad("sandbox input artifact name is invalid"); return name; }
    private String extension(String name) { int at = name.lastIndexOf('.'); return at <= 0 ? "" : name.substring(at + 1).toLowerCase(Locale.ROOT); }
    /** Reject Zip Slip, links and archive bombs before a code-validation Runner ever sees a source snapshot. */
    private void validateSourceArchive(byte[] bytes) {
        int entries = 0; long expanded = 0L; byte[] buffer = new byte[8192];
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (++entries > 10_000 || StringUtils.isBlank(name) || name.startsWith("/") || name.startsWith("\\") || name.contains("..") || entry.isDirectory() && name.length() > 512) throw bad("sandbox source archive is unsafe");
                int read; while ((read = zip.read(buffer)) >= 0) { expanded += read; if (expanded > 100L * 1024 * 1024) throw bad("sandbox source archive is too large when expanded"); }
            }
        } catch (IOException e) { throw bad("sandbox source archive is invalid"); }
    }
    private long maxInputBytes(Map config) { Object configured = config.get("maxInputBytes"); return configured instanceof Number ? Math.min(Math.max(((Number) configured).longValue(), 1L), 10L * 1024 * 1024) : 1L * 1024 * 1024; }
    private String randomToken() { byte[] b = new byte[32]; new SecureRandom().nextBytes(b); return Base64.getUrlEncoder().withoutPadding().encodeToString(b); }
    private String hash(String value) { try { byte[] b = MessageDigest.getInstance("SHA-256").digest(StringUtils.defaultString(value).getBytes(StandardCharsets.UTF_8)); StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format("%02x", x)); return out.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private String hash(byte[] value) { try { byte[] b = MessageDigest.getInstance("SHA-256").digest(value); StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format("%02x", x)); return out.toString(); } catch (Exception e) { throw new IllegalStateException(e); } }
    private Integer number(Object value, int fallback) { return value instanceof Number ? ((Number)value).intValue() : fallback; }
    private Integer boundedInt(Object value, int fallback, int min, int max) { return Math.min(Math.max(number(value, fallback), min), max); }
    private Double boundedDouble(Object value, double fallback, double min, double max) { double result = value instanceof Number ? ((Number) value).doubleValue() : fallback; return Math.min(Math.max(result, min), max); }
    private Long boundedLong(Long value, long min, long max) { if (value == null) return null; return Math.min(Math.max(value, min), max); }
    private boolean validDigestImage(String imageRef) { return StringUtils.isNotBlank(imageRef) && imageRef.matches("[A-Za-z0-9./:_-]+@sha256:[a-f0-9]{64}"); }
    /** Prevent a policy publish from expanding the runner's authority beyond fixed template boundaries. */
    private void validateTemplateConfig(Map config) {
        String runtime = StringUtils.upperCase(String.valueOf(config.get("runtime")));
        if (!Arrays.asList("PYTHON", "NODE", "OFFICE").contains(runtime)) throw bad("sandbox runtime is not allowlisted");
        String network = StringUtils.upperCase(String.valueOf(config.get("network")));
        if (!Arrays.asList("NONE", "EGRESS_PROXY").contains(network)) throw bad("sandbox network mode is not allowlisted");
        Object executionModeValue = config.get("executionMode");
        String executionMode = StringUtils.upperCase(executionModeValue == null ? "SCRIPT" : StringUtils.defaultIfBlank(String.valueOf(executionModeValue), "SCRIPT"));
        config.put("executionMode", executionMode);
        if (!Arrays.asList("SCRIPT", "FIXED_COMMAND", "RUNNER_COMPAT", "WEB_COLLECTION").contains(executionMode)) throw bad("sandbox execution mode is not allowlisted");
        if ("WEB_COLLECTION".equals(executionMode) != "EGRESS_PROXY".equals(network)) throw bad("web collection must use the controlled egress proxy and no other mode may use it");
        if (Boolean.TRUE.equals(config.get("scriptSlot")) && !("PYTHON".equals(runtime) || "NODE".equals(runtime))) throw bad("script slots require Python or Node runtime");
        if ("FIXED_COMMAND".equals(executionMode) && (Boolean.TRUE.equals(config.get("scriptSlot")) || StringUtils.isBlank(String.valueOf(config.get("fixedCommand"))))) throw bad("fixed-command templates require a fixed command and no script slot");
        if (Boolean.TRUE.equals(config.get("requireImageDigest")) && !validDigestImage(String.valueOf(config.get("imageRef")))) throw bad("digest-pinned image reference is required");
        Object formats = config.get("outputFormats"); if (!(formats instanceof Collection) || ((Collection) formats).isEmpty()) throw bad("sandbox output formats are required");
        requiredQuota(config, "timeoutSeconds", 1, 3600);
        requiredQuota(config, "maxOutputFiles", 1, 20);
        requiredQuota(config, "maxOutputBytes", 1, 50L * 1024 * 1024);
        optionalQuota(config, "maxInputFiles", 1, 20);
        optionalQuota(config, "maxInputBytes", 1, 10L * 1024 * 1024);
        optionalQuota(config, "maxMemoryMb", 64, 4096);
        optionalQuota(config, "maxPids", 16, 512);
        optionalQuota(config, "maxTempDiskMb", 16, 1024);
        if (config.containsKey("maxCpuCores") && (!(config.get("maxCpuCores") instanceof Number) || ((Number) config.get("maxCpuCores")).doubleValue() < 0.1D || ((Number) config.get("maxCpuCores")).doubleValue() > 4D)) throw bad("sandbox quota maxCpuCores is out of range");
    }
    private void requiredQuota(Map config, String name, long min, long max) { if (!(config.get(name) instanceof Number)) throw bad("sandbox quota " + name + " is required"); optionalQuota(config, name, min, max); }
    private void optionalQuota(Map config, String name, long min, long max) { if (config.containsKey(name) && (!(config.get(name) instanceof Number) || ((Number) config.get(name)).longValue() < min || ((Number) config.get(name)).longValue() > max)) throw bad("sandbox quota " + name + " is out of range"); }
    /** Logs are scanned independently from input and output. Only rule IDs enter the audit trail. */
    private boolean logBlocked(SandboxExecutionTask task, String summary) {
        if (StringUtils.isBlank(summary) || contentScanner == null) return false;
        ArtifactContentScanner.ScanResult scan = contentScanner.scan("runner.log", "text/plain", summary.getBytes(StandardCharsets.UTF_8));
        if (StringUtils.isBlank(scan.getRuleId())) return false;
        append(task.getId(), nextSequence(task.getId()), "SENSITIVE_LOG_MATCH", task.getStatus(), null, "Sensitive runner log rule matched: " + scan.getRuleId(), hash(summary));
        return !scan.isAllowed();
    }
    private void failForSensitiveLog(SandboxExecutionTask task) {
        if (terminal(task.getStatus())) return;
        task.setStatus(FAILED); task.setCompletedAt(System.currentTimeMillis()); task.setFailureCode("SENSITIVE_LOG_BLOCKED"); task.setFailureReason("Runner log blocked by sensitive-data policy"); task.setLogSummary(null); tasks.updateById(task);
        append(task.getId(), nextSequence(task.getId()), "TASK_FAILED", FAILED, null, "Task blocked by sensitive runner log policy");
    }
    /** Audit and UI summaries must never retain common secret or PII plaintext. */
    private String redact(String value) { String text = StringUtils.abbreviate(value, 4096); if (text == null) return null; text = text.replaceAll("(?i)\\\"?(api[_-]?key|token|secret|password)\\\"?\\s*[:=]\\s*\\\"?([^\\s,;}\\\"]+)", "$1=***"); text = text.replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}", "***@***"); text = text.replaceAll("(?<!\\d)1[3-9]\\d{9}(?!\\d)", "***********"); text = text.replaceAll("(?<![0-9Xx])[1-9][0-9]{5}(?:18|19|20)?[0-9]{2}(?:0[1-9]|1[0-2])(?:0[1-9]|[12][0-9]|3[01])[0-9]{3}[0-9Xx](?![0-9Xx])", "******************"); return text.replaceAll("(?<![0-9])[0-9]{16,19}(?![0-9])", "****************"); }
    private boolean containsHighRiskSecret(String value) { return value != null && value.matches("(?s).*?(?i)\\\"?(api[_-]?key|token|secret|password)\\\"?\\s*[:=]\\s*\\\"?[^\\\"\\s,;}]{8,}.*"); }
    private boolean isTextual(String contentType) { String type = StringUtils.lowerCase(StringUtils.defaultString(contentType)); return type.startsWith("text/") || type.contains("json") || type.contains("yaml") || type.contains("xml"); }
    private void validateTextArtifactContentType(String extension, String contentType) {
        String expected = "csv".equals(extension) ? "text/csv" : "json".equals(extension) ? "application/json" : "md".equals(extension) ? "text/markdown" : null;
        if (expected != null && !expected.equalsIgnoreCase(StringUtils.defaultString(contentType))) throw bad("sandbox artifact MIME type does not match its extension");
    }
    private Long longNumber(Object value, long fallback) { return value instanceof Number ? ((Number)value).longValue() : fallback; }
    private ServerException bad(String message) { return new ServerException(400, message); }
    private ServerException conflict(String message) { return new ServerException(409, message); }
}
