package com.aether.agent.controller;

import com.aether.agent.sandbox.dto.*;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplate;
import com.aether.agent.sandbox.entity.SandboxExecutionTemplateVersion;
import com.aether.agent.sandbox.service.SandboxTaskService;
import com.aether.agent.sandbox.vo.SandboxRunnerTaskVo;
import com.aether.agent.sandbox.vo.SandboxTaskVo;
import com.aether.agent.skill.entity.AgentArtifact;
import com.aether.agent.skill.service.AgentArtifactService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.local.CurrentUser;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.IOException;
import javax.validation.constraints.NotBlank;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;

/**
 * User and administrator control-plane API. Runner endpoints remain internal.
 */
@RestController
@Api(tags = "沙箱任务 API")
@RequestMapping("/api/agent/sandbox")
@Permission(path = "/agent/artifact", required = false)
public class SandboxTaskController {
    private final SandboxTaskService tasks;
    private final AgentArtifactService artifacts;
    private final String runnerToken;

    /**
     * 创建 {@code SandboxTaskController} 实例。
     */
    public SandboxTaskController(SandboxTaskService tasks, AgentArtifactService artifacts, @Value("${aether.sandbox.runner-token:${AETHER_SANDBOX_RUNNER_TOKEN:}}") String runnerToken) {
        this.tasks = tasks;
        this.artifacts = artifacts;
        this.runnerToken = runnerToken;
    }

    /**
     * 创建当前请求。
     */
    @ApiOperation("创建沙箱任务")
    @PostMapping("/tasks")
    public WebResponse<SandboxTaskVo> create(@RequestBody SandboxTaskCreateDto request) {
        return WebResponse.OK(tasks.create(user(), request));
    }

    /**
     * 详情当前请求。
     */
    @ApiOperation("查询沙箱任务详情")
    @GetMapping("/tasks/{id}")
    public WebResponse<SandboxTaskVo> detail(@PathVariable @NotBlank String id) {
        return WebResponse.OK(tasks.detail(id, user(), false));
    }

    /**
     * 按运行。
     */
    @ApiOperation("按 Agent 运行查询沙箱任务")
    @GetMapping("/tasks/run/{runId}")
    public WebResponse<SandboxTaskVo> byRun(@PathVariable @NotBlank String runId) {
        return WebResponse.OK(tasks.byRun(runId, user(), false));
    }

    /**
     * 处理events。
     */
    @ApiOperation("查询沙箱任务事件")
    @GetMapping("/tasks/{id}/events")
    public WebResponse<List<SandboxTaskVo.SandboxEventVo>> events(@PathVariable @NotBlank String id) {
        return WebResponse.OK(tasks.events(id, user(), false));
    }

    /**
     * 处理artifacts。
     */
    @ApiOperation("查询沙箱任务生成的制品")
    @GetMapping("/tasks/{id}/artifacts")
    public WebResponse<List<AgentArtifact>> artifacts(@PathVariable @NotBlank String id) {
        SandboxTaskVo task = tasks.detail(id, user(), false);
        String executionId = StringUtils.defaultIfBlank(task.getLegacyExecutionId(), task.getId());
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        return WebResponse.OK(artifacts.list(Wrappers.lambdaQuery(AgentArtifact.class)
                .eq(AgentArtifact::getExecutionId, executionId).eq(AgentArtifact::getUserId, user())
                .eq(StringUtils.isNotBlank(tenantId), AgentArtifact::getTenantId, tenantId)
                .isNull(AgentArtifact::getRecycledAt).orderByDesc(AgentArtifact::getCreatedAt)));
    }

    /**
     * 审批通过当前请求。
     */
    @ApiOperation("批准沙箱任务")
    @PostMapping("/tasks/{id}/approve")
    public WebResponse<Void> approve(@PathVariable @NotBlank String id, @RequestBody(required = false) SandboxDecisionDto body) {
        tasks.approve(id, user(), body == null ? null : body.getReason());
        return WebResponse.OK(I18nUtils.getMessage("sandbox.task.approve.success"));
    }

    /**
     * 拒绝当前请求。
     */
    @ApiOperation("拒绝沙箱任务")
    @PostMapping("/tasks/{id}/reject")
    public WebResponse<Void> reject(@PathVariable @NotBlank String id, @RequestBody(required = false) SandboxDecisionDto body) {
        tasks.reject(id, user(), body == null ? null : body.getReason());
        return WebResponse.OK(I18nUtils.getMessage("sandbox.task.reject.success"));
    }

    /**
     * 处理decision。
     */
    @ApiOperation("提交沙箱任务审批决定")
    @PostMapping("/tasks/{id}/decision")
    public WebResponse<Void> decision(@PathVariable @NotBlank String id, @RequestBody SandboxDecisionDto body) {
        if (body == null || !StringUtils.equalsAnyIgnoreCase(body.getDecision(), "APPROVE", "REJECT"))
            throw new ServerException(400, I18nUtils.getMessage("sandbox.task.decision.invalid"));
        if (StringUtils.equalsIgnoreCase("APPROVE", body.getDecision())) tasks.approve(id, user(), body.getReason());
        else tasks.reject(id, user(), body.getReason());
        return WebResponse.OK(I18nUtils.getMessage("sandbox.task.decision.success"));
    }

    /**
     * 取消当前请求。
     */
    @ApiOperation("取消沙箱任务")
    @PostMapping("/tasks/{id}/cancel")
    public WebResponse<Void> cancel(@PathVariable @NotBlank String id, @RequestBody(required = false) SandboxDecisionDto body) {
        tasks.cancel(id, user(), body == null ? null : body.getReason());
        return WebResponse.OK(I18nUtils.getMessage("sandbox.task.cancel.success"));
    }

    /**
     * 重试当前请求。
     */
    @ApiOperation("重试沙箱任务")
    @PostMapping("/tasks/{id}/retry")
    public WebResponse<SandboxTaskVo> retry(@PathVariable @NotBlank String id) {
        return WebResponse.OK(tasks.retry(id, user()));
    }

    /**
     * 处理templates。
     */
    @ApiOperation("查询可用沙箱模板")
    @GetMapping("/templates")
    public WebResponse<List<SandboxExecutionTemplate>> templates() {
        return WebResponse.OK(tasks.templates());
    }

    /**
     * 处理versions。
     */
    @ApiOperation("查询沙箱模板版本")
    @GetMapping("/templates/{id}/versions")
    public WebResponse<List<SandboxExecutionTemplateVersion>> versions(@PathVariable @NotBlank String id) {
        return WebResponse.OK(tasks.versions(id));
    }

    /**
     * 处理setTemplateEnabled。
     */
    @ApiOperation("设置沙箱模板启用状态")
    @PostMapping("/admin/templates/{id}/enabled")
    @Permission(path = "/agent/sandbox")
    public WebResponse<Void> setTemplateEnabled(@PathVariable @NotBlank String id, @RequestParam boolean enabled) {
        tasks.setTemplateEnabled(id, enabled);
        return WebResponse.OK(I18nUtils.getMessage("sandbox.template.status.update.success"));
    }

    /**
     * 发布TemplateVersion。
     */
    @ApiOperation("发布沙箱模板版本")
    @PostMapping("/admin/templates/{id}/versions")
    @Permission(path = "/agent/sandbox")
    public WebResponse<SandboxExecutionTemplateVersion> publishTemplateVersion(@PathVariable @NotBlank String id, @RequestBody SandboxTemplateVersionPublishDto request) {
        return WebResponse.OK(tasks.publishTemplateVersion(id, user(), request));
    }

    /**
     * 处理audit。
     */
    @ApiOperation("分页查询沙箱任务审计记录")
    @PostMapping("/admin/audit")
    @Permission(path = "/agent/sandbox")
    public WebResponse<List<SandboxTaskVo>> audit(@RequestBody(required = false) SandboxAuditQueryDto query) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<SandboxTaskVo> page = tasks.audit(query);
        return WebResponse.Page(page.getRecords(), page.getTotal());
    }

    /**
     * 管理员详情。
     */
    @ApiOperation("查询沙箱任务管理详情")
    @GetMapping("/admin/tasks/{id}")
    @Permission(path = "/agent/sandbox")
    public WebResponse<SandboxTaskVo> adminDetail(@PathVariable @NotBlank String id) {
        return WebResponse.OK(tasks.detail(id, user(), true));
    }

    /**
     * 处理metrics。
     */
    @ApiOperation("查询沙箱任务运行指标")
    @GetMapping("/admin/metrics")
    @Permission(path = "/agent/sandbox")
    public WebResponse<com.aether.agent.sandbox.vo.SandboxMetricsVo> metrics() {
        return WebResponse.OK(tasks.metrics());
    }

    /**
     * 处理claim。
     */
    @ApiOperation("Runner 领取沙箱任务")
    @PostMapping("/runner/claim")
    public WebResponse<SandboxRunnerTaskVo> claim(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Runner-Id") String runnerId) {
        return WebResponse.OK(tasks.claim(runner(token, runnerId)));
    }

    /**
     * 处理heartbeat。
     */
    @ApiOperation("Runner 上报沙箱任务心跳")
    @PostMapping("/runner/tasks/{id}/heartbeat")
    public WebResponse<Boolean> heartbeat(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestBody(required = false) SandboxRunnerEventDto body) {
        SandboxRunnerEventDto data = body == null ? new SandboxRunnerEventDto() : body;
        return WebResponse.OK(tasks.heartbeat(id, execution, runner(runner, runnerId), data.getProgress(), data.getSummary()));
    }

    /**
     * 取消Requested。
     */
    @ApiOperation("Runner 查询沙箱任务取消状态")
    @GetMapping("/runner/tasks/{id}/cancel")
    public WebResponse<Boolean> cancelRequested(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id) {
        return WebResponse.OK(tasks.cancelRequested(id, execution, runner(runner, runnerId)));
    }

    /**
     * 下载Input。
     */
    @ApiOperation("Runner 下载沙箱任务输入文件")
    @GetMapping("/runner/tasks/{id}/inputs/{inputId}")
    public ResponseEntity<byte[]> downloadInput(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @PathVariable String inputId) {
        SandboxTaskService.RunnerInputArtifact input = tasks.downloadInput(id, inputId, execution, runner(runner, runnerId));
        MediaType type;
        try {
            type = MediaType.parseMediaType(input.getContentType());
        } catch (Exception ignored) {
            type = MediaType.APPLICATION_OCTET_STREAM;
        }
        return ResponseEntity.ok().contentType(type).contentLength(input.getContent().length).header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + input.getFileName().replace("\"", "_") + "\"").header("X-Aether-Content-SHA256", input.getSha256()).body(input.getContent());
    }

    /**
     * 处理runner事件。
     */
    @ApiOperation("Runner 上报沙箱任务事件")
    @PostMapping("/runner/tasks/{id}/events")
    public WebResponse<Void> runnerEvent(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestBody SandboxRunnerEventDto event) {
        tasks.runnerEvent(id, execution, runner(runner, runnerId), event);
        return WebResponse.OK(I18nUtils.getMessage("sandbox.runner.event.accepted"));
    }

    /**
     * 处理usage。
     */
    @ApiOperation("Runner 上报沙箱任务资源用量")
    @PostMapping("/runner/tasks/{id}/usage")
    public WebResponse<Void> usage(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestBody SandboxRunnerUsageDto usage) {
        tasks.reportUsage(id, execution, runner(runner, runnerId), usage);
        return WebResponse.OK(I18nUtils.getMessage("sandbox.runner.usage.accepted"));
    }

    /**
     * 处理succeed。
     */
    @ApiOperation("Runner 上报沙箱任务成功")
    @PostMapping("/runner/tasks/{id}/succeed")
    public WebResponse<Void> succeed(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestBody(required = false) SandboxDecisionDto body) {
        tasks.succeed(id, execution, runner(runner, runnerId), body == null ? null : body.getReason());
        return WebResponse.OK(I18nUtils.getMessage("sandbox.runner.task.completed"));
    }

    /**
     * 处理completeArtifact。
     */
    @ApiOperation("Runner 上传沙箱任务制品")
    @PostMapping("/runner/tasks/{id}/artifacts")
    public WebResponse<Void> completeArtifact(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestParam("file") MultipartFile file, @RequestParam("sha256") String sha256, @RequestParam(value = "summary", required = false) String summary, @RequestParam(value = "finalArtifact", defaultValue = "true") boolean finalArtifact) throws IOException {
        tasks.completeArtifact(id, execution, runner(runner, runnerId), file.getOriginalFilename(), file.getContentType(), file.getBytes(), sha256, summary, finalArtifact);
        return WebResponse.OK(I18nUtils.getMessage("sandbox.runner.artifact.accepted"));
    }

    /**
     * 处理fail。
     */
    @ApiOperation("Runner 上报沙箱任务失败")
    @PostMapping("/runner/tasks/{id}/fail")
    public WebResponse<Void> fail(@RequestHeader("X-Aether-Runner-Token") String runner, @RequestHeader("X-Aether-Runner-Id") String runnerId, @RequestHeader("X-Aether-Execution-Token") String execution, @PathVariable String id, @RequestParam("code") String code, @RequestParam("reason") String reason, @RequestParam(value = "summary", required = false) String summary) {
        tasks.fail(id, execution, runner(runner, runnerId), code, reason, summary);
        return WebResponse.OK(I18nUtils.getMessage("sandbox.runner.task.failed"));
    }

    /**
     * 用户当前请求。
     */
    private String user() {
        String id = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(id)) throw new ServerException(401, I18nUtils.getMessage("sandbox.user.unauthorized"));
        return id;
    }

    /**
     * 处理requireRunner。
     */
    private String requireRunner(String supplied) {
        if (StringUtils.isBlank(runnerToken) || !MessageDigest.isEqual(runnerToken.getBytes(StandardCharsets.UTF_8), StringUtils.defaultString(supplied).getBytes(StandardCharsets.UTF_8)))
            throw new ServerException(401, I18nUtils.getMessage("sandbox.runner.unauthorized"));
        return supplied;
    }

    /**
     * Authenticate the caller separately from the stable Runner identity used by leases and audit records.
     */
    private String runner(String token, String id) {
        requireRunner(token);
        if (StringUtils.isBlank(id) || !id.matches("[A-Za-z0-9][A-Za-z0-9_.-]{0,127}"))
            throw new ServerException(401, I18nUtils.getMessage("sandbox.runner.identity.invalid"));
        return id;
    }
}
