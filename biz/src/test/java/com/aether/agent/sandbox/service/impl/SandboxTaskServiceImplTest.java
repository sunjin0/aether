package com.aether.agent.sandbox.service.impl;

import com.aether.agent.sandbox.dto.SandboxTaskCreateDto;
import com.aether.agent.sandbox.dto.SandboxRunnerUsageDto;
import com.aether.agent.sandbox.dto.SandboxTemplateVersionPublishDto;
import com.aether.agent.sandbox.entity.*;
import com.aether.agent.sandbox.mapper.*;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.aether.agent.sandbox.vo.SandboxMetricsVo;
import com.aether.agent.skill.service.impl.AgentArtifactServiceImpl;
import com.aether.agent.skill.service.impl.AgentSandboxExecutionServiceImpl;
import com.aether.agent.skill.entity.AgentSandboxExecution;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.storage.service.ObjectStorageService;
import com.aether.agent.sandbox.service.ArtifactContentScanner;
import com.aether.agent.sandbox.service.WebCollectionTargetValidator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SandboxTaskServiceImplTest {
    @Mock SandboxExecutionTemplateMapper templates;
    @Mock SandboxExecutionTemplateVersionMapper versions;
    @Mock SandboxExecutionTaskMapper tasks;
    @Mock SandboxExecutionApprovalMapper approvals;
    @Mock SandboxExecutionEventMapper events;
    @Mock SandboxExecutionResourceUsageMapper resourceUsage;
    @Mock SandboxRunnerNodeMapper runnerNodes;
    @Mock AgentSandboxExecutionServiceImpl legacyExecutions;
    @Mock AgentArtifactServiceImpl artifactService;
    @Mock ObjectStorageService storage;
    @Mock ArtifactContentScanner contentScanner;
    @Mock WebCollectionTargetValidator webTargetValidator;
    SandboxTaskServiceImpl service;

    @BeforeEach void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SandboxExecutionTask.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SandboxExecutionResourceUsage.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), SandboxRunnerNode.class);
        service = new SandboxTaskServiceImpl(templates, versions, tasks, approvals, events, resourceUsage, runnerNodes, legacyExecutions, artifactService, storage, contentScanner, webTargetValidator, "aether-chat", "runner-secret");
        lenient().when(events.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        lenient().when(contentScanner.scan(anyString(), anyString(), any(byte[].class))).thenReturn(ArtifactContentScanner.ScanResult.allowed());
        lenient().doAnswer(invocation -> { SandboxExecutionTask value = invocation.getArgument(0); if (value.getId() == null) value.setId("task-1"); return 1; }).when(tasks).insert(any(SandboxExecutionTask.class));
    }

    @Test void lowRiskAskPolicyCreatesPendingApprovalTask() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("generic-document"); template.setEnabled(true); template.setRiskLevel("LOW"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\"}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("generic-document"); request.setInput(Collections.singletonMap("format", "pdf"));
        SandboxTaskVo created = service.create("user-1", request, false);
        assertEquals(SandboxTaskServiceImpl.PENDING_APPROVAL, created.getStatus()); assertTrue(created.getApprovalRequired());
        ArgumentCaptor<SandboxExecutionTask> captured = ArgumentCaptor.forClass(SandboxExecutionTask.class); verify(tasks).insert(captured.capture());
        assertEquals("generic-document", captured.getValue().getTemplateCode()); assertNotNull(captured.getValue().getInputSha256());
    }

    @Test void cancellationOfQueuedTaskIsTerminalAndIdempotent() {
        SandboxExecutionTask task = task("task-1", SandboxTaskServiceImpl.QUEUED); when(tasks.selectById("task-1")).thenReturn(task);
        service.cancel("task-1", "user-1", "no longer needed");
        assertEquals(SandboxTaskServiceImpl.CANCELLED, task.getStatus()); assertNotNull(task.getCompletedAt());
        service.cancel("task-1", "user-1", "duplicate");
        verify(tasks, times(1)).updateById(any(SandboxExecutionTask.class));
    }

    @Test void compatibilityRunnerCannotClaimPendingApprovalTask() {
        SandboxExecutionTask pending = task("task-1", SandboxTaskServiceImpl.PENDING_APPROVAL); pending.setLegacyExecutionId("legacy-1");
        when(tasks.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending);
        assertFalse(service.legacyReadyForClaim("legacy-1"));
        verify(tasks, never()).updateById(any(SandboxExecutionTask.class));
    }

    @Test void metricsExposePersistedLatencyUsageFailureAndImageRisk() {
        long now = System.currentTimeMillis();
        SandboxExecutionTask succeeded = task("success", SandboxTaskServiceImpl.SUCCEEDED); succeeded.setCreatedAt(now - 4_000); succeeded.setClaimedAt(now - 3_000); succeeded.setStartedAt(now - 2_000); succeeded.setCompletedAt(now - 500); succeeded.setConfigSnapshot("{\"imageRef\":\"registry.example/runtime@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa\"}");
        SandboxExecutionTask failed = task("failure", SandboxTaskServiceImpl.FAILED); failed.setCreatedAt(now - 3_000); failed.setClaimedAt(now - 2_500); failed.setStartedAt(now - 2_000); failed.setCompletedAt(now - 1_000); failed.setFailureCode("RUNNER_FAILED"); failed.setConfigSnapshot("{}");
        SandboxExecutionResourceUsage usage = new SandboxExecutionResourceUsage(); usage.setWallMillis(1_500L); usage.setOutputBytes(42L);
        when(tasks.selectCount(any(LambdaQueryWrapper.class))).thenReturn(0L, 0L, 0L, 1L, 1L, 0L, 0L, 0L);
        when(events.selectCount(any(LambdaQueryWrapper.class))).thenReturn(2L);
        when(tasks.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(succeeded, failed));
        when(resourceUsage.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(usage));
        SandboxRunnerNode activeRunner = new SandboxRunnerNode(); activeRunner.setLastSeenAt(now);
        SandboxRunnerNode staleRunner = new SandboxRunnerNode(); staleRunner.setLastSeenAt(now - 120_000);
        when(runnerNodes.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(activeRunner, staleRunner));

        SandboxMetricsVo metrics = service.metrics();

        assertEquals(2L, metrics.getTerminalTasks()); assertEquals(750L, metrics.getAverageQueueWaitMillis());
        assertEquals(1_250L, metrics.getAverageExecutionMillis()); assertEquals(1_500L, metrics.getTotalWallMillis());
        assertEquals(42L, metrics.getTotalOutputBytes()); assertEquals(50D, metrics.getSuccessRatePercent());
        assertEquals(1L, metrics.getFailureTypes().get("RUNNER_FAILED")); assertEquals(1L, metrics.getUnpinnedImageTaskCount());
        assertEquals(2L, metrics.getRegisteredRunners()); assertEquals(1L, metrics.getActiveRunners()); assertEquals(1L, metrics.getStaleRunners());
    }

    @Test void retentionPurgesRunnerNodesOlderThanThirtyDays() {
        SandboxRunnerNode stale = new SandboxRunnerNode(); stale.setId("stale-runner"); stale.setLastSeenAt(System.currentTimeMillis() - 31L * 24 * 60 * 60 * 1000);
        when(events.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(tasks.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        when(runnerNodes.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(stale));

        service.purgeExpiredRetentionData();

        verify(runnerNodes).deleteById("stale-runner");
    }

    @Test void compatibilityTaskIsLinkedToFinalAssistantMessageByRunId() {
        service.linkRunMessage("run-1", "assistant-message-1");
        verify(tasks).update(isNull(), any(com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper.class));
    }

    @Test void compatibilityRunnerRecordsClaimStartAndCompletionProgress() {
        SandboxExecutionTask queued = task("task-1", SandboxTaskServiceImpl.QUEUED); queued.setLegacyExecutionId("legacy-1");
        when(tasks.selectOne(any(LambdaQueryWrapper.class))).thenReturn(queued);
        service.legacyExecutionStarted("legacy-1", "compatibility-runner");
        assertEquals(SandboxTaskServiceImpl.RUNNING, queued.getStatus());
        assertNotNull(queued.getStartedAt());
        service.completeLegacyExecution("legacy-1", true, null, "generated=result.pdf");
        assertEquals(SandboxTaskServiceImpl.SUCCEEDED, queued.getStatus());
        ArgumentCaptor<SandboxExecutionEvent> captured = ArgumentCaptor.forClass(SandboxExecutionEvent.class); verify(events, atLeast(3)).insert(captured.capture());
        assertTrue(captured.getAllValues().stream().anyMatch(event -> "TASK_STARTED".equals(event.getEventType()) && Integer.valueOf(0).equals(event.getProgress())));
        assertTrue(captured.getAllValues().stream().anyMatch(event -> "TASK_SUCCEEDED".equals(event.getEventType()) && Integer.valueOf(100).equals(event.getProgress())));
    }

    @Test void compatibilityCompletionRejectsTaskThatNeverStarted() {
        SandboxExecutionTask pending = task("task-1", SandboxTaskServiceImpl.PENDING_APPROVAL); pending.setLegacyExecutionId("legacy-1");
        when(tasks.selectOne(any(LambdaQueryWrapper.class))).thenReturn(pending);
        assertThrows(com.aether.exception.ServerException.class, () -> service.completeLegacyExecution("legacy-1", true, null, "generated=result.pdf"));
        assertEquals(SandboxTaskServiceImpl.PENDING_APPROVAL, pending.getStatus());
    }

    @Test void retryCopiesFrozenInputsButCreatesNewQueueTask() {
        SandboxExecutionTask previous = task("old-task", SandboxTaskServiceImpl.FAILED); previous.setApprovalRequired(false); previous.setInputSnapshot("{\"format\":\"pdf\"}"); previous.setInputSha256("hash"); previous.setConfigSnapshot("{}"); previous.setPolicyVersion("v1"); previous.setTemplateId("template"); previous.setTemplateVersionId("version"); previous.setTemplateCode("generic-document"); when(tasks.selectById("old-task")).thenReturn(previous);
        SandboxTaskVo retry = service.retry("old-task", "user-1");
        assertEquals(SandboxTaskServiceImpl.QUEUED, retry.getStatus());
        ArgumentCaptor<SandboxExecutionTask> captured = ArgumentCaptor.forClass(SandboxExecutionTask.class); verify(tasks).insert(captured.capture());
        assertNull(captured.getValue().getExecutionTokenHash()); assertEquals("{\"format\":\"pdf\"}", captured.getValue().getInputSnapshot());
    }

    @Test void retryOfCompatibilityTaskCopiesAndLinksANewLegacyTicket() {
        SandboxExecutionTask previous = task("old-task", SandboxTaskServiceImpl.FAILED); previous.setLegacyExecutionId("legacy-old"); previous.setApprovalRequired(false); previous.setInputSnapshot("{\"format\":\"pdf\"}"); previous.setInputSha256("hash"); previous.setConfigSnapshot("{}"); previous.setPolicyVersion("v1"); previous.setTemplateId("template"); previous.setTemplateVersionId("version"); previous.setTemplateCode("generic-document");
        AgentSandboxExecution oldTicket = new AgentSandboxExecution(); oldTicket.setId("legacy-old"); oldTicket.setRunId("run-1"); oldTicket.setSkillVersionId("__platform_generic_artifact__"); oldTicket.setUserId("user-1"); oldTicket.setAgentDefinitionId("agent-1"); oldTicket.setExecutionConfigSnapshot("{}"); oldTicket.setResourceSnapshot("[]"); oldTicket.setInputJson("{\"format\":\"pdf\"}");
        when(tasks.selectById("old-task")).thenReturn(previous); when(legacyExecutions.getById("legacy-old")).thenReturn(oldTicket);
        when(legacyExecutions.save(any(AgentSandboxExecution.class))).thenAnswer(invocation -> { invocation.<AgentSandboxExecution>getArgument(0).setId("legacy-retry"); return true; });

        service.retry("old-task", "user-1");

        ArgumentCaptor<SandboxExecutionTask> taskCaptor = ArgumentCaptor.forClass(SandboxExecutionTask.class); verify(tasks).insert(taskCaptor.capture());
        assertEquals("legacy-retry", taskCaptor.getValue().getLegacyExecutionId()); verify(legacyExecutions).save(any(AgentSandboxExecution.class));
        ArgumentCaptor<SandboxExecutionEvent> eventCaptor = ArgumentCaptor.forClass(SandboxExecutionEvent.class); verify(events, atLeast(2)).insert(eventCaptor.capture());
        assertTrue(eventCaptor.getAllValues().stream().anyMatch(event -> "LEGACY_EXECUTION_LINKED".equals(event.getEventType())));
    }

    @Test void retryClonesFrozenInputIntoNewPrivatePrefix() {
        SandboxExecutionTask previous = task("old-task", SandboxTaskServiceImpl.FAILED); previous.setApprovalRequired(false); previous.setTemplateId("template"); previous.setTemplateVersionId("version"); previous.setTemplateCode("generic-document"); previous.setConfigSnapshot("{}"); previous.setPolicyVersion("v1");
        String checksum = sha256("abc".getBytes()); previous.setInputSnapshot("{\"_sandboxInputArtifacts\":[{\"id\":\"oldinput000000000\",\"fileName\":\"source.csv\",\"contentType\":\"text/csv\",\"size\":3,\"sha256\":\"" + checksum + "\",\"objectKey\":\"sandbox/inputs/old-task/oldinput-source.csv\"}]}");
        when(tasks.selectById("old-task")).thenReturn(previous); when(storage.getObject("aether-chat", "sandbox/inputs/old-task/oldinput-source.csv")).thenReturn("abc".getBytes());
        service.retry("old-task", "user-1");
        ArgumentCaptor<SandboxExecutionTask> captured = ArgumentCaptor.forClass(SandboxExecutionTask.class); verify(tasks).insert(captured.capture());
        assertFalse(captured.getValue().getInputSnapshot().contains("sandbox/inputs/old-task/")); assertTrue(captured.getValue().getInputSnapshot().contains("sandbox/inputs/" + captured.getValue().getId() + "/"));
        verify(storage).upload(eq("aether-chat"), contains("sandbox/inputs/" + captured.getValue().getId() + "/"), eq("abc".getBytes()), eq("text/csv"));
    }

    @Test void retryRejectsTaskWhosePrivateInputsWereRetainedThenPurged() {
        SandboxExecutionTask previous = task("old-task", SandboxTaskServiceImpl.FAILED); previous.setInputPurgedAt(System.currentTimeMillis()); when(tasks.selectById("old-task")).thenReturn(previous);
        assertThrows(com.aether.exception.ServerException.class, () -> service.retry("old-task", "user-1"));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void rejectsScriptWhenTemplateHasNoScriptSlot() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("generic-document"); template.setEnabled(true); template.setRiskLevel("LOW"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"scriptSlot\":false}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("generic-document"); request.setScriptLanguage("PYTHON"); request.setScript("print('unsafe')");
        assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void rejectsInputBeyondTemplateLimitBeforeInsert() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("generic-document"); template.setEnabled(true); template.setRiskLevel("LOW"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxInputBytes\":16}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("generic-document"); request.setInput(Collections.singletonMap("content", "this input is intentionally too large"));
        assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void rejectsHighRiskSecretBeforeInsert() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("generic-document"); template.setEnabled(true); template.setRiskLevel("LOW"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\"}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("generic-document"); request.setInput(Collections.singletonMap("apiKey", "very-secret-value"));
        assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void rejectsCreateWhenFrozenDailyUserQuotaIsExhausted() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("generic-document"); template.setEnabled(true); template.setRiskLevel("LOW"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxDailyTasksPerUser\":1}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version); when(tasks.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("generic-document");
        com.aether.exception.ServerException error = assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        assertTrue(error.getMessage().startsWith("429:")); verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void leavesTaskQueuedWhenItsAgentHasReachedConcurrentTemplateLimit() {
        SandboxExecutionTask queued = task("queued-task", SandboxTaskServiceImpl.QUEUED); queued.setTemplateId("template-1"); queued.setAgentDefinitionId("agent-1"); queued.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxConcurrentTasksPerAgent\":1}");
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setEnabled(true);
        when(tasks.selectOne(any(LambdaQueryWrapper.class))).thenReturn(queued); when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(tasks.selectCount(any(LambdaQueryWrapper.class))).thenReturn(1L);
        assertNull(service.claim("runner-a"));
        verify(tasks, never()).update(isNull(), any());
    }

    @Test void refusesToEnableStrictTemplateWithoutDigestPinnedImage() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setConfigSnapshot("{\"network\":\"NONE\",\"requireImageDigest\":true,\"imageRef\":\"python:latest\"}");
        when(templates.selectById("template-1")).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version);
        assertThrows(com.aether.exception.ServerException.class, () -> service.setTemplateEnabled("template-1", true));
        verify(templates, never()).updateById(any(SandboxExecutionTemplate.class));
    }

    @Test void publishesValidatedImmutableTemplateVersionAndMovesOnlyFutureTasks() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCurrentVersionId("version-1"); template.setRiskLevel("LOW");
        SandboxExecutionTemplateVersion old = new SandboxExecutionTemplateVersion(); old.setId("version-1"); old.setVersion(1); old.setConfigSnapshot("{\"runtime\":\"PYTHON\"}");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.singletonList(old));
        doAnswer(invocation -> { ((SandboxExecutionTemplateVersion) invocation.getArgument(0)).setId("version-2"); return 1; }).when(versions).insert(any(SandboxExecutionTemplateVersion.class));
        SandboxTemplateVersionPublishDto request = new SandboxTemplateVersionPublishDto(); request.setPolicyVersion("rules-2026-08"); request.setRiskLevel("MEDIUM"); request.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"network\":\"NONE\",\"executionMode\":\"SCRIPT\",\"scriptSlot\":true,\"timeoutSeconds\":60,\"maxOutputFiles\":1,\"maxOutputBytes\":1024,\"outputFormats\":[\"csv\"]}");
        SandboxExecutionTemplateVersion published = service.publishTemplateVersion("template-1", "admin-1", request);
        assertEquals(2, published.getVersion()); assertEquals("version-2", template.getCurrentVersionId()); assertEquals("MEDIUM", template.getRiskLevel()); assertEquals("version-1", old.getId());
        verify(templates).updateById(template);
    }

    @Test void refusesPolicyVersionThatWouldEnableUncontrolledNetwork() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        SandboxTemplateVersionPublishDto request = new SandboxTemplateVersionPublishDto(); request.setPolicyVersion("v2"); request.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"network\":\"INTERNET\",\"outputFormats\":[\"csv\"]}");
        assertThrows(com.aether.exception.ServerException.class, () -> service.publishTemplateVersion("template-1", "admin-1", request));
        verify(versions, never()).insert(any(SandboxExecutionTemplateVersion.class));
    }

    @Test void publishingEgressPolicyImmediatelyDisablesPreviouslyEnabledTemplate() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setEnabled(true);
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());
        doAnswer(invocation -> { ((SandboxExecutionTemplateVersion) invocation.getArgument(0)).setId("version-1"); return 1; }).when(versions).insert(any(SandboxExecutionTemplateVersion.class));
        SandboxTemplateVersionPublishDto request = new SandboxTemplateVersionPublishDto(); request.setPolicyVersion("v1"); request.setConfigSnapshot("{\"runtime\":\"NODE\",\"network\":\"EGRESS_PROXY\",\"executionMode\":\"WEB_COLLECTION\",\"timeoutSeconds\":60,\"maxOutputFiles\":1,\"maxOutputBytes\":1024,\"outputFormats\":[\"json\"]}");
        service.publishTemplateVersion("template-1", "admin-1", request);
        assertFalse(template.getEnabled()); verify(templates).updateById(template);
    }

    @Test void refusesUnknownRiskLevelDuringPolicyPublish() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        SandboxTemplateVersionPublishDto request = new SandboxTemplateVersionPublishDto(); request.setPolicyVersion("v1"); request.setRiskLevel("CRITICAL"); request.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"network\":\"NONE\",\"outputFormats\":[\"csv\"]}");
        assertThrows(com.aether.exception.ServerException.class, () -> service.publishTemplateVersion("template-1", "admin-1", request));
        verify(versions, never()).insert(any(SandboxExecutionTemplateVersion.class));
    }

    @Test void refusesPolicyVersionWithUnboundedTimeout() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template);
        SandboxTemplateVersionPublishDto request = new SandboxTemplateVersionPublishDto(); request.setPolicyVersion("v1"); request.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"network\":\"NONE\",\"timeoutSeconds\":7200,\"maxOutputFiles\":1,\"maxOutputBytes\":1024,\"outputFormats\":[\"csv\"]}");
        assertThrows(com.aether.exception.ServerException.class, () -> service.publishTemplateVersion("template-1", "admin-1", request));
        verify(versions, never()).insert(any(SandboxExecutionTemplateVersion.class));
    }

    @Test void freezesOnlyOwnedArtifactInputIntoTaskPrivateObject() {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("local-python-analysis"); template.setEnabled(true); template.setRiskLevel("MEDIUM"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v2"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxInputFiles\":2,\"maxInputBytes\":1024,\"inputFormats\":[\"csv\"]}");
        AgentArtifact source = new AgentArtifact(); source.setId("artifact-1"); source.setUserId("user-1"); source.setFileName("report.csv"); source.setObjectKey("chat/artifacts/old/report.csv"); source.setContentType("text/csv"); source.setSize(3L); source.setContentSha256("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version); when(artifactService.requireOwned("artifact-1", "user-1", false)).thenReturn(source); when(storage.getObject("aether-chat", source.getObjectKey())).thenReturn("abc".getBytes());
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("local-python-analysis"); request.setInputArtifactIds(Arrays.asList("artifact-1"));
        service.create("user-1", request, false);
        verify(storage).upload(eq("aether-chat"), contains("sandbox/inputs/"), eq("abc".getBytes()), eq("text/csv"));
        ArgumentCaptor<SandboxExecutionTask> captured = ArgumentCaptor.forClass(SandboxExecutionTask.class); verify(tasks).insert(captured.capture());
        assertTrue(captured.getValue().getInputSnapshot().contains("_sandboxInputArtifacts"));
        assertFalse(captured.getValue().getInputSnapshot().contains(source.getObjectKey()));
    }

    @Test void rejectsZipSlipBeforeCodeValidationTaskIsPersisted() throws Exception {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("python-lint-test"); template.setEnabled(true); template.setRiskLevel("HIGH"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxInputFiles\":1,\"maxInputBytes\":1024,\"inputFormats\":[\"zip\"]}");
        byte[] zip; try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream writer = new ZipOutputStream(out)) { writer.putNextEntry(new ZipEntry("../escape.py")); writer.write("x".getBytes()); writer.closeEntry(); zip = out.toByteArray(); }
        AgentArtifact source = new AgentArtifact(); source.setId("artifact-zip"); source.setFileName("snapshot.zip"); source.setObjectKey("chat/artifacts/old/snapshot.zip"); source.setContentType("application/zip"); source.setSize((long) zip.length); source.setContentSha256(sha256(zip));
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version); when(artifactService.requireOwned("artifact-zip", "user-1", false)).thenReturn(source); when(storage.getObject("aether-chat", source.getObjectKey())).thenReturn(zip);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("python-lint-test"); request.setInputArtifactIds(Collections.singletonList("artifact-zip"));
        assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void rejectsArchiveWithTooManyEntriesBeforeCodeValidationTaskIsPersisted() throws Exception {
        SandboxExecutionTemplate template = new SandboxExecutionTemplate(); template.setId("template-1"); template.setCode("python-lint-test"); template.setEnabled(true); template.setRiskLevel("HIGH"); template.setCurrentVersionId("version-1");
        SandboxExecutionTemplateVersion version = new SandboxExecutionTemplateVersion(); version.setId("version-1"); version.setPublished(true); version.setPolicyVersion("v1"); version.setConfigSnapshot("{\"runtime\":\"PYTHON\",\"maxInputFiles\":1,\"maxInputBytes\":2097152,\"inputFormats\":[\"zip\"]}");
        byte[] zip; try (ByteArrayOutputStream out = new ByteArrayOutputStream(); ZipOutputStream writer = new ZipOutputStream(out)) { for (int index = 0; index <= 10_000; index++) { writer.putNextEntry(new ZipEntry("src/" + index + ".txt")); writer.closeEntry(); } zip = out.toByteArray(); }
        AgentArtifact source = new AgentArtifact(); source.setId("artifact-many"); source.setFileName("snapshot.zip"); source.setObjectKey("chat/artifacts/old/many.zip"); source.setContentType("application/zip"); source.setSize((long) zip.length); source.setContentSha256(sha256(zip));
        when(templates.selectOne(any(LambdaQueryWrapper.class))).thenReturn(template); when(versions.selectById("version-1")).thenReturn(version); when(artifactService.requireOwned("artifact-many", "user-1", false)).thenReturn(source); when(storage.getObject("aether-chat", source.getObjectKey())).thenReturn(zip);
        SandboxTaskCreateDto request = new SandboxTaskCreateDto(); request.setTemplateCode("python-lint-test"); request.setInputArtifactIds(Collections.singletonList("artifact-many"));
        assertThrows(com.aether.exception.ServerException.class, () -> service.create("user-1", request, false));
        verify(tasks, never()).insert(any(SandboxExecutionTask.class));
    }

    @Test void boundsRunnerReportedUsageBeforePersistingAuditRecord() {
        SandboxExecutionTask running = task("usage-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-secret"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000);
        when(tasks.selectById("usage-task")).thenReturn(running); when(resourceUsage.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        SandboxRunnerUsageDto usage = new SandboxRunnerUsageDto(); usage.setWallMillis(Long.MAX_VALUE); usage.setOutputBytes(Long.MAX_VALUE); usage.setMaxRssBytes(Long.MAX_VALUE); usage.setCpuMillis(-10L); usage.setExitCode(0);
        service.reportUsage("usage-task", "token", "runner-secret", usage);
        ArgumentCaptor<SandboxExecutionResourceUsage> captured = ArgumentCaptor.forClass(SandboxExecutionResourceUsage.class); verify(resourceUsage).insert(captured.capture());
        assertEquals(24L * 3600 * 1000, captured.getValue().getWallMillis()); assertEquals(1024L * 1024 * 1024, captured.getValue().getOutputBytes()); assertEquals(0L, captured.getValue().getCpuMillis());
    }

    @Test void rejectsMalformedRunnerIdentityBeforeAcceptingCallback() {
        SandboxRunnerUsageDto usage = new SandboxRunnerUsageDto(); usage.setWallMillis(1L);
        assertThrows(com.aether.exception.ServerException.class, () -> service.reportUsage("usage-task", "token", "bad runner id", usage));
        verify(resourceUsage, never()).insert(any(SandboxExecutionResourceUsage.class));
    }

    @Test void blocksHighRiskRunnerLogsWithoutPersistingSensitivePlaintext() {
        SandboxExecutionTask running = task("log-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000);
        when(tasks.selectById("log-task")).thenReturn(running);
        when(contentScanner.scan(eq("runner.log"), eq("text/plain"), any(byte[].class))).thenReturn(ArtifactContentScanner.ScanResult.blocked("HIGH_SECRET"));
        assertTrue(service.heartbeat("log-task", "token", "runner-a", 50, "token=top-secret-value"));
        assertEquals(SandboxTaskServiceImpl.FAILED, running.getStatus()); assertEquals("SENSITIVE_LOG_BLOCKED", running.getFailureCode()); assertNull(running.getLogSummary());
        ArgumentCaptor<SandboxExecutionEvent> eventsCaptured = ArgumentCaptor.forClass(SandboxExecutionEvent.class); verify(events, atLeast(2)).insert(eventsCaptured.capture());
        assertTrue(eventsCaptured.getAllValues().stream().anyMatch(event -> "SENSITIVE_LOG_MATCH".equals(event.getEventType()) && event.getSummary().contains("HIGH_SECRET") && sha256("token=top-secret-value".getBytes()).equals(event.getSubjectSha256())));
        assertFalse(eventsCaptured.getAllValues().stream().map(SandboxExecutionEvent::getSummary).filter(java.util.Objects::nonNull).anyMatch(summary -> summary.contains("top-secret-value")));
    }

    @Test void blockedArtifactRecordsRuleAndHashWithoutUploadingPlaintext() {
        SandboxExecutionTask running = task("sensitive-artifact-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000); running.setConfigSnapshot("{\"outputFormats\":[\"json\"],\"maxOutputFiles\":1,\"maxOutputBytes\":1024}");
        byte[] content = "{\"identity\":\"11010519491231002X\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        when(tasks.selectById("sensitive-artifact-task")).thenReturn(running);
        when(contentScanner.scan(eq("result.json"), eq("application/json"), eq(content))).thenReturn(ArtifactContentScanner.ScanResult.blocked("HIGH_CN_ID"));

        assertThrows(com.aether.exception.ServerException.class, () -> service.completeArtifact("sensitive-artifact-task", "token", "runner-a", "result.json", "application/json", content, sha256(content), "", true));

        verify(storage, never()).upload(anyString(), anyString(), any(byte[].class), anyString());
        ArgumentCaptor<SandboxExecutionEvent> captured = ArgumentCaptor.forClass(SandboxExecutionEvent.class); verify(events, atLeastOnce()).insert(captured.capture());
        assertTrue(captured.getAllValues().stream().anyMatch(event -> "SENSITIVE_ARTIFACT_MATCH".equals(event.getEventType()) && event.getSummary().contains("HIGH_CN_ID") && sha256(content).equals(event.getSubjectSha256())));
        assertFalse(captured.getAllValues().stream().map(SandboxExecutionEvent::getSummary).filter(java.util.Objects::nonNull).anyMatch(summary -> summary.contains("11010519491231002X")));
    }

    @Test void recordsExplicitRunnerTimeoutAsTimedOutState() {
        SandboxExecutionTask running = task("timeout-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000);
        when(tasks.selectById("timeout-task")).thenReturn(running);
        service.fail("timeout-task", "token", "runner-a", "TIMED_OUT", "sandbox process timed out", "");
        assertEquals(SandboxTaskServiceImpl.TIMED_OUT, running.getStatus()); assertEquals("TIMED_OUT", running.getFailureCode());
    }

    @Test void recordsRunnerCancellationAsCancelledInsteadOfLeaseTimeout() {
        SandboxExecutionTask running = task("cancel-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000); running.setCancelRequestedAt(System.currentTimeMillis());
        when(tasks.selectById("cancel-task")).thenReturn(running);
        service.fail("cancel-task", "token", "runner-a", "CANCELLED", "sandbox task cancelled", "");
        assertEquals(SandboxTaskServiceImpl.CANCELLED, running.getStatus()); assertEquals("CANCELLED", running.getFailureCode()); assertNotNull(running.getCompletedAt());
    }

    @Test void recoversCancelledRunnerLeaseAsCancelledWhenRunnerIsLost() {
        SandboxExecutionTask running = task("lost-runner-task", SandboxTaskServiceImpl.RUNNING); running.setLeaseExpiresAt(System.currentTimeMillis() - 1); running.setCancelRequestedAt(System.currentTimeMillis() - 2);
        when(tasks.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList(), Collections.singletonList(running));
        service.recoverExpiredTasks();
        assertEquals(SandboxTaskServiceImpl.CANCELLED, running.getStatus()); assertEquals("CANCELLED_RUNNER_LOST", running.getFailureCode());
    }

    @Test void acceptsDuplicateArtifactCallbackWithoutSecondUpload() {
        SandboxExecutionTask running = task("artifact-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000); running.setConfigSnapshot("{\"outputFormats\":[\"csv\"],\"maxOutputFiles\":1,\"maxOutputBytes\":1024}");
        AgentArtifact existing = new AgentArtifact(); existing.setExecutionId("artifact-task"); existing.setFileName("result.csv"); existing.setContentSha256(sha256("a,b".getBytes())); existing.setStatus(1);
        when(tasks.selectById("artifact-task")).thenReturn(running); when(artifactService.getOne(any(LambdaQueryWrapper.class))).thenReturn(existing);
        service.completeArtifact("artifact-task", "token", "runner-a", "result.csv", "text/csv", "a,b".getBytes(), sha256("a,b".getBytes()), "retry", false);
        verify(storage, never()).upload(anyString(), anyString(), any(byte[].class), anyString()); verify(artifactService, never()).save(any(AgentArtifact.class));
    }

    @Test void rejectsArtifactCallbackAfterCancellationWasRequested() {
        SandboxExecutionTask running = task("cancel-artifact-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000); running.setCancelRequestedAt(System.currentTimeMillis()); running.setConfigSnapshot("{\"outputFormats\":[\"csv\"],\"maxOutputFiles\":1,\"maxOutputBytes\":1024}");
        when(tasks.selectById("cancel-artifact-task")).thenReturn(running);
        assertThrows(com.aether.exception.ServerException.class, () -> service.completeArtifact("cancel-artifact-task", "token", "runner-a", "result.csv", "text/csv", "a,b".getBytes(), sha256("a,b".getBytes()), "late result", true));
        verify(storage, never()).upload(anyString(), anyString(), any(byte[].class), anyString()); verify(artifactService, never()).save(any(AgentArtifact.class));
    }

    @Test void treatsConcurrentUniqueCallbackConflictAsIdempotentSuccess() {
        SandboxExecutionTask running = task("artifact-task", SandboxTaskServiceImpl.RUNNING); running.setClaimedBy("runner-a"); running.setExecutionTokenHash(sha256("token".getBytes())); running.setLeaseExpiresAt(System.currentTimeMillis() + 60_000); running.setConfigSnapshot("{\"outputFormats\":[\"csv\"],\"maxOutputFiles\":1,\"maxOutputBytes\":1024}");
        AgentArtifact existing = new AgentArtifact(); existing.setStatus(1);
        when(tasks.selectById("artifact-task")).thenReturn(running); when(artifactService.getOne(any(LambdaQueryWrapper.class))).thenReturn(null, existing); doThrow(new RuntimeException("unique conflict")).when(artifactService).save(any(AgentArtifact.class));
        assertDoesNotThrow(() -> service.completeArtifact("artifact-task", "token", "runner-a", "result.csv", "text/csv", "a,b".getBytes(), sha256("a,b".getBytes()), "retry", false));
        verify(storage).removeObject(eq("aether-chat"), contains("chat/artifacts/artifact-task/"));
    }

    private SandboxExecutionTask task(String id, String status) { SandboxExecutionTask task = new SandboxExecutionTask(); task.setId(id); task.setRequesterUserId("user-1"); task.setStatus(status); task.setExpiresAt(System.currentTimeMillis() + 60_000); return task; }
    private String sha256(byte[] value) { try { byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(value); StringBuilder result = new StringBuilder(); for (byte b : digest) result.append(String.format("%02x", b)); return result.toString(); } catch (Exception e) { throw new AssertionError(e); } }
}
