package com.aether.workflow.controller;

import com.aether.permission.Permission;
import com.aether.workflow.dto.AgentWorkflowScheduleTriggerDto;
import com.aether.workflow.entity.AgentWorkflowScheduleTrigger;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证智能体工作流调度控制器权限的行为。
 */
class AgentWorkflowScheduleControllerPermissionTest {
    /**
     * 处理protects调度EndpointsWithTheDedicated调度权限。
     */
    @Test
    void protectsScheduleEndpointsWithTheDedicatedSchedulePermission() throws Exception {
        assertPermission("create", Permission.Type.Write, AgentWorkflowScheduleTriggerDto.class);
        assertPermission("list", Permission.Type.Read, AgentWorkflowScheduleTrigger.class);
        assertPermission("update", Permission.Type.Write, String.class, AgentWorkflowScheduleTriggerDto.class);
        assertPermission("setEnabled", Permission.Type.Write, String.class, boolean.class);
        assertPermission("delete", Permission.Type.Write, String.class);
    }

    /**
     * 处理assert权限。
     */
    private void assertPermission(String methodName, Permission.Type type, Class<?>... types) throws Exception {
        Method method = AgentWorkflowScheduleController.class.getDeclaredMethod(methodName, types);
        Permission permission = method.getAnnotation(Permission.class);
        assertEquals("/workflow/schedule", permission.path());
        assertEquals(type, permission.type());
    }
}
