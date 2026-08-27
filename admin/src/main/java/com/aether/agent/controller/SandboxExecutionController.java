package com.aether.agent.controller;

import com.aether.agent.skill.dto.ArtifactGenerationRequestDto;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.agent.skill.vo.ArtifactGenerationVo;
import com.aether.agent.skill.vo.SandboxExecutionTaskVo;
import com.aether.entity.WebResponse;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Internal boundary for the managed MCP tool and the dedicated Runner.
 * This controller intentionally has no dashboard permission: every operation
 * authenticates its own delegation or runner credential, never a user session.
 */
@Validated
@RestController
@Api(tags = "沙箱执行内部 API")
@RequestMapping("/api/internal/sandbox")
public class SandboxExecutionController {
    private final SkillArtifactExecutionService executionService;

    /**
     * 创建 {@code SandboxExecutionController} 实例。
     */
    public SandboxExecutionController(SkillArtifactExecutionService executionService) {
        this.executionService = executionService;
    }

    /**
     * 处理request。
     */
    @ApiOperation("提交受委托的制品生成请求")
    @PostMapping("/requests")
    public WebResponse<ArtifactGenerationVo> request(@RequestHeader("X-Aether-Delegation") String token,
                                                     @RequestBody ArtifactGenerationRequestDto request) {
        return WebResponse.OK(executionService.request(token, request));
    }

    /**
     * 处理claim。
     */
    @ApiOperation("领取待执行的沙箱任务")
    @PostMapping("/runner/claim")
    public WebResponse<SandboxExecutionTaskVo> claim(@RequestHeader("X-Aether-Runner-Token") String token) {
        return WebResponse.OK(executionService.claimNext(token));
    }

    /**
     * 处理complete。
     */
    @ApiOperation("提交沙箱执行生成的制品")
    @PostMapping("/runner/executions/{executionId}/complete")
    public WebResponse<Void> complete(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                      @RequestParam("file") MultipartFile file, @RequestParam("sha256") String sha256,
                                      @RequestParam(value = "logSummary", required = false) String logSummary,
                                      @RequestParam(value = "finalArtifact", defaultValue = "true") boolean finalArtifact) throws IOException {
        executionService.complete(token, executionToken, executionId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), sha256, logSummary, finalArtifact);
        return WebResponse.OK("Artifact accepted");
    }

    /**
     * 处理fail。
     */
    @ApiOperation("上报沙箱执行失败")
    @PostMapping("/runner/executions/{executionId}/fail")
    public WebResponse<Void> fail(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                  @RequestParam("reason") String reason, @RequestParam(value = "logSummary", required = false) String logSummary) {
        executionService.fail(token, executionToken, executionId, reason, logSummary);
        return WebResponse.OK("Execution marked failed");
    }

    /**
     * 处理heartbeat。
     */
    @ApiOperation("上报沙箱执行心跳")
    @PostMapping("/runner/executions/{executionId}/heartbeat")
    public WebResponse<Boolean> heartbeat(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                          @RequestParam(value = "logSummary", required = false) String logSummary) {
        return WebResponse.OK(executionService.heartbeat(token, executionToken, executionId, logSummary));
    }

    /**
     * 取消Requested。
     */
    @ApiOperation("查询沙箱执行是否被取消")
    @GetMapping("/runner/executions/{executionId}/cancel")
    public WebResponse<Boolean> cancelRequested(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId) {
        return WebResponse.OK(executionService.cancelRequested(token, executionToken, executionId));
    }
}
