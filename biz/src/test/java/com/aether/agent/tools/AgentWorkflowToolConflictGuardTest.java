package com.aether.agent.tools;

import com.aether.agent.entity.AgentTool;
import com.aether.workflow.service.AgentWorkflowTaskQueryService;
import com.aether.workflow.vo.AgentWorkflowToolOccupant;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentWorkflowToolConflictGuardTest {

    @Test
    void anOccupiedToolIsRefusedWithTheOwningInvocationNamed() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowToolOccupant occupant = new AgentWorkflowToolOccupant();
        occupant.setInvocationId("2100106253094248449");
        occupant.setWorkflowName("文件分析工作流程");
        occupant.setNodeId("tool_1788078153828");
        when(tasks.findRunningToolOwner("agent-1", "user-1", "tool-abc")).thenReturn(occupant);
        AgentWorkflowToolConflictGuard guard = new AgentWorkflowToolConflictGuard(tasks);

        String reason = guard.describeConflict(tool("tool-abc", "process_document"), "agent-1", "user-1");

        // 光说「不允许」模型只会换个姿势再试；必须点名归属，并给出该做的替代动作。
        assertNotNull(reason);
        assertTrue(reason.contains("2100106253094248449"), reason);
        assertTrue(reason.contains("文件分析工作流程"), reason);
        assertTrue(reason.contains("process_document"), reason);
        assertTrue(reason.contains("workflow_observe"), reason);
    }

    @Test
    void noRunningWorkflowMeansNoConflict() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.findRunningToolOwner(anyString(), anyString(), anyString())).thenReturn(null);
        AgentWorkflowToolConflictGuard guard = new AgentWorkflowToolConflictGuard(tasks);

        assertNull(guard.describeConflict(tool("tool-abc", "process_document"), "agent-1", "user-1"));
    }

    @Test
    void aBrokenLookupLetsTheCallThrough() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        when(tasks.findRunningToolOwner(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("db down"));
        AgentWorkflowToolConflictGuard guard = new AgentWorkflowToolConflictGuard(tasks);

        // 这是尽力而为的护栏而非安全边界：查不动时放行，不能把正常的工具调用打成不可用。
        assertNull(guard.describeConflict(tool("tool-abc", "process_document"), "agent-1", "user-1"));
    }

    @Test
    void aToolWithoutAnIdIsNeverRefused() {
        AgentWorkflowTaskQueryService tasks = mock(AgentWorkflowTaskQueryService.class);
        AgentWorkflowToolConflictGuard guard = new AgentWorkflowToolConflictGuard(tasks);

        assertNull(guard.describeConflict(tool(null, "process_document"), "agent-1", "user-1"));
        assertNull(guard.describeConflict(null, "agent-1", "user-1"));
        // 没有 id 就无从匹配，也不该白查一次库。
        org.mockito.Mockito.verifyNoInteractions(tasks);
    }

    private static AgentTool tool(String id, String name) {
        AgentTool tool = new AgentTool();
        tool.setId(id);
        tool.setName(name);
        return tool;
    }
}
