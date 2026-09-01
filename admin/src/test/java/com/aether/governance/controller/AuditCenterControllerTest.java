package com.aether.governance.controller;

import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentToolCallLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AuditCenterControllerTest {
    @Test
    void exportsBoundedCsvUsingOnlyAuditFields() {
        AgentToolCallLogService service = mock(AgentToolCallLogService.class);
        AgentToolCallLog record = new AgentToolCallLog();
        record.setId("log-1"); record.setRunId("run-1"); record.setToolId("tool,one");
        record.setStatus(1); record.setCreatedAt(123L);
        Page<AgentToolCallLog> page = new Page<AgentToolCallLog>(1, 10000);
        page.setRecords(java.util.Collections.singletonList(record));
        when(service.page(any(Page.class), any())).thenReturn(page);

        ResponseEntity<byte[]> response = new AuditCenterController(service).exportToolCalls(null, null, 50000);

        String csv = new String(response.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        assertTrue(csv.startsWith("id,runId,agentDefinitionId,toolId,status,createdAt\n"));
        assertTrue(csv.contains("log-1,run-1,,\"tool,one\",1,123"));
    }
}
