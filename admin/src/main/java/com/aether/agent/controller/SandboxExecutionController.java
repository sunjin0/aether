package com.aether.agent.controller;

import com.aether.agent.skill.dto.ArtifactGenerationRequestDto;
import com.aether.agent.skill.service.SkillArtifactExecutionService;
import com.aether.agent.skill.vo.ArtifactGenerationVo;
import com.aether.agent.skill.vo.SandboxExecutionTaskVo;
import com.aether.entity.WebResponse;
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
@RequestMapping("/api/internal/sandbox")
public class SandboxExecutionController {
    private final SkillArtifactExecutionService executionService;
    public SandboxExecutionController(SkillArtifactExecutionService executionService) { this.executionService = executionService; }

    @PostMapping("/requests")
    public WebResponse<ArtifactGenerationVo> request(@RequestHeader("X-Aether-Delegation") String token,
                                                       @RequestBody ArtifactGenerationRequestDto request) {
        return WebResponse.OK(executionService.request(token, request));
    }

    @PostMapping("/runner/claim")
    public WebResponse<SandboxExecutionTaskVo> claim(@RequestHeader("X-Aether-Runner-Token") String token) {
        return WebResponse.OK(executionService.claimNext(token));
    }

    @PostMapping("/runner/executions/{executionId}/complete")
    public WebResponse<Void> complete(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                      @RequestParam("file") MultipartFile file, @RequestParam("sha256") String sha256,
                                      @RequestParam(value = "logSummary", required = false) String logSummary,
                                      @RequestParam(value = "finalArtifact", defaultValue = "true") boolean finalArtifact) throws IOException {
        executionService.complete(token, executionToken, executionId, file.getOriginalFilename(), file.getContentType(), file.getBytes(), sha256, logSummary, finalArtifact);
        return WebResponse.OK("Artifact accepted");
    }

    @PostMapping("/runner/executions/{executionId}/fail")
    public WebResponse<Void> fail(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                  @RequestParam("reason") String reason, @RequestParam(value = "logSummary", required = false) String logSummary) {
        executionService.fail(token, executionToken, executionId, reason, logSummary);
        return WebResponse.OK("Execution marked failed");
    }

    @PostMapping("/runner/executions/{executionId}/heartbeat")
    public WebResponse<Boolean> heartbeat(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId,
                                          @RequestParam(value = "logSummary", required = false) String logSummary) {
        return WebResponse.OK(executionService.heartbeat(token, executionToken, executionId, logSummary));
    }

    @GetMapping("/runner/executions/{executionId}/cancel")
    public WebResponse<Boolean> cancelRequested(@RequestHeader("X-Aether-Runner-Token") String token, @RequestHeader("X-Aether-Execution-Token") String executionToken, @PathVariable String executionId) {
        return WebResponse.OK(executionService.cancelRequested(token, executionToken, executionId));
    }
}
