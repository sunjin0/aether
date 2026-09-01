package com.aether.governance.controller;

import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.entity.WebResponse;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;

/** 统一工具授权、审批和执行审计查询入口。返回内容来源于已脱敏审计字段。 */
@Api(tags = "Audit Center API")
@RestController
@Permission(path = "/agent/run")
@RequestMapping("/api/agent/governance/audit")
public class AuditCenterController {
    private final AgentToolCallLogService service;

    public AuditCenterController(AgentToolCallLogService service) { this.service = service; }

    @ApiOperation("查询工具执行审计")
    @GetMapping("/tool-calls")
    public WebResponse<List<AgentToolCallLog>> toolCalls(
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String agentDefinitionId,
            @RequestParam(required = false) String toolId,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long startTime,
            @RequestParam(required = false) Long endTime,
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "20") long pageSize) {
        QueryWrapper<AgentToolCallLog> query = new QueryWrapper<AgentToolCallLog>()
                .eq("deleted", false)
                .eq(runId != null && !runId.trim().isEmpty(), "run_id", runId)
                .eq(agentDefinitionId != null && !agentDefinitionId.trim().isEmpty(), "agent_definition_id", agentDefinitionId)
                .eq(toolId != null && !toolId.trim().isEmpty(), "tool_id", toolId)
                .eq(status != null, "status", status)
                .ge(startTime != null, "created_at", startTime)
                .le(endTime != null, "created_at", endTime)
                .orderByDesc("created_at");
        if (CurrentUser.getUser() != null && CurrentUser.getUser().get("tenantId") != null
                && !CurrentUser.getUser().get("tenantId").trim().isEmpty()) {
            query.eq("tenant_id", CurrentUser.getUser().get("tenantId"));
        }
        Page<AgentToolCallLog> page = new Page<>(Math.max(1, current), Math.min(Math.max(1, pageSize), 100));
        Page<AgentToolCallLog> result = service.page(page, query);
        return WebResponse.Page(result.getRecords(), result.getTotal());
    }

    @ApiOperation("导出工具执行审计 CSV")
    @GetMapping(value = "/tool-calls/export", produces = "text/csv")
    public ResponseEntity<byte[]> exportToolCalls(@RequestParam(required = false) Long startTime,
                                                  @RequestParam(required = false) Long endTime,
                                                  @RequestParam(defaultValue = "1000") int limit) {
        int safeLimit = Math.min(Math.max(1, limit), 10000);
        QueryWrapper<AgentToolCallLog> query = new QueryWrapper<AgentToolCallLog>()
                .eq("deleted", false)
                .ge(startTime != null, "created_at", startTime)
                .le(endTime != null, "created_at", endTime)
                .orderByDesc("created_at");
        if (CurrentUser.getUser() != null && CurrentUser.getUser().get("tenantId") != null
                && !CurrentUser.getUser().get("tenantId").trim().isEmpty())
            query.eq("tenant_id", CurrentUser.getUser().get("tenantId"));
        List<AgentToolCallLog> records = service.page(new Page<AgentToolCallLog>(1, safeLimit), query).getRecords();
        StringBuilder csv = new StringBuilder("id,runId,agentDefinitionId,toolId,status,createdAt\n");
        for (AgentToolCallLog record : records) {
            csv.append(csv(record.getId())).append(',').append(csv(record.getRunId())).append(',')
                    .append(csv(record.getAgentDefinitionId())).append(',').append(csv(record.getToolId())).append(',')
                    .append(record.getStatus() == null ? "" : record.getStatus()).append(',')
                    .append(record.getCreatedAt() == null ? "" : record.getCreatedAt()).append('\n');
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=tool-audit.csv")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String csv(Object value) {
        if (value == null) return "";
        String text = String.valueOf(value).replace("\"", "\"\"");
        return (text.indexOf(',') >= 0 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) ? "\"" + text + "\"" : text;
    }
}
