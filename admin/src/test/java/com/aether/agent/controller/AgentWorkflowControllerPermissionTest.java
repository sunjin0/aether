package com.aether.agent.controller;

import com.aether.agent.dto.AgentWorkflowInteractionDto;
import com.aether.agent.dto.AgentWorkflowStartDto;
import com.aether.agent.vo.AgentWorkflowInstanceVo;
import com.aether.permission.Permission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentWorkflowControllerPermissionTest {

    @Test
    void protectsRuntimeEndpointsWithTheDedicatedRunPermission() throws Exception {
        assertRunWrite("start", String.class, AgentWorkflowStartDto.class);
        assertRunWrite("answer", String.class, AgentWorkflowInteractionDto.class);
        assertRunWrite("retry", String.class);
        assertRunWrite("terminate", String.class);
        assertRunWrite("updateVariables", String.class, AgentWorkflowStartDto.class);
        assertRunRead("instances", AgentWorkflowInstanceVo.class);
        assertRunRead("instance", String.class);
        assertRunRead("events", String.class);
    }

    private void assertRunRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/agent/workflow/run", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    private void assertRunWrite(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/agent/workflow/run", permission.path());
        assertEquals(Permission.Type.Write, permission.type());
    }

    private Method method(String name, Class<?>... types) throws Exception {
        return AgentWorkflowController.class.getDeclaredMethod(name, types);
    }
}
