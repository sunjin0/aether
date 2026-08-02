package com.aether.workflow.controller;

import com.aether.workflow.dto.AgentWorkflowInteractionDto;
import com.aether.workflow.dto.AgentWorkflowStartDto;
import com.aether.workflow.dto.AgentWorkflowBusinessStartDto;
import com.aether.workflow.dto.AgentWorkflowDto;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.permission.Permission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentWorkflowControllerPermissionTest {

    @Test
    void protectsRuntimeEndpointsWithTheDedicatedRunPermission() throws Exception {
        assertRunWrite("start", String.class, AgentWorkflowStartDto.class);
        assertRunWrite("startBusiness", String.class, AgentWorkflowBusinessStartDto.class);
        assertRunWrite("answer", String.class, AgentWorkflowInteractionDto.class);
        assertRunWrite("retry", String.class);
        assertRunWrite("replay", String.class);
        assertRunWrite("terminate", String.class);
        assertRunWrite("updateVariables", String.class, AgentWorkflowStartDto.class);
        assertRunWrite("retryCallback", String.class, String.class);
        assertRunRead("instances", AgentWorkflowInstanceVo.class);
        assertRunRead("instance", String.class);
        assertRunRead("events", String.class);
        assertRunRead("callbacks", String.class);
    }

    @Test
    void protectsVersionAndImportExportEndpointsWithWorkflowPermissions() throws Exception {
        assertWorkflowRead("versions", String.class);
        assertWorkflowRead("versionDiff", String.class, int.class, int.class);
        assertWorkflowRead("exportWorkflow", String.class);
        assertWorkflowWrite("importWorkflow", AgentWorkflowDto.class);
        assertWorkflowWrite("createSchedule", AgentWorkflowScheduleTriggerDto.class);
        assertWorkflowWrite("setScheduleEnabled", String.class, boolean.class);
        assertWorkflowRead("schedules", AgentWorkflowScheduleTrigger.class);
        assertOperationsRead("operationsMetrics");
        assertOperationsRead("deadLetters", int.class);
    }

    private void assertRunRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/run", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    private void assertRunWrite(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/run", permission.path());
        assertEquals(Permission.Type.Write, permission.type());
    }

    private void assertWorkflowRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/workflow", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    private void assertWorkflowWrite(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/workflow", permission.path());
        assertEquals(Permission.Type.Write, permission.type());
    }

    private void assertOperationsRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/operations", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    private Method method(String name, Class<?>... types) throws Exception {
        return AgentWorkflowController.class.getDeclaredMethod(name, types);
    }
}
