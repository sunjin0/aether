package com.aether.workflow.controller;

import com.aether.workflow.dto.AgentWorkflowAnswerInstanceRequest;
import com.aether.workflow.dto.AgentWorkflowImportRequest;
import com.aether.workflow.dto.AgentWorkflowListInstancesRequest;
import com.aether.workflow.dto.AgentWorkflowStartBusinessInstanceRequest;
import com.aether.workflow.dto.AgentWorkflowStartInstanceRequest;
import com.aether.workflow.dto.AgentWorkflowUpdateInstanceVariablesRequest;
import com.aether.workflow.vo.AgentWorkflowInstanceVo;
import com.aether.permission.Permission;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 验证智能体工作流控制器权限的行为。
 */
class AgentWorkflowControllerPermissionTest {

    /**
     * 处理protectsRuntimeEndpointsWithTheDedicated运行权限。
     */
    @Test
    void protectsRuntimeEndpointsWithTheDedicatedRunPermission() throws Exception {
        assertRunWrite("start", String.class, AgentWorkflowStartInstanceRequest.class);
        assertRunWrite("startBusiness", String.class, AgentWorkflowStartBusinessInstanceRequest.class);
        assertRunWrite("answer", String.class, AgentWorkflowAnswerInstanceRequest.class);
        assertRunWrite("retry", String.class);
        assertRunWrite("replay", String.class);
        assertRunWrite("terminate", String.class);
        assertRunWrite("updateVariables", String.class, AgentWorkflowUpdateInstanceVariablesRequest.class);
        assertRunWrite("retryCallback", String.class, String.class);
        assertRunRead("instances", AgentWorkflowListInstancesRequest.class);
        assertRunRead("instance", String.class);
        assertRunRead("events", String.class);
        assertRunRead("callbacks", String.class);
    }

    /**
     * 处理protectsVersionAndImportExportEndpointsWith工作流Permissions。
     */
    @Test
    void protectsVersionAndImportExportEndpointsWithWorkflowPermissions() throws Exception {
        assertWorkflowRead("versions", String.class);
        assertWorkflowRead("versionDiff", String.class, int.class, int.class);
        assertWorkflowRead("exportWorkflow", String.class);
        assertWorkflowWrite("importWorkflow", AgentWorkflowImportRequest.class);
        assertOperationsRead("operationsMetrics");
        assertOperationsRead("deadLetters", int.class);
    }

    /**
     * 处理assert运行Read。
     */
    private void assertRunRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/run", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    /**
     * 处理assert运行Write。
     */
    private void assertRunWrite(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/run", permission.path());
        assertEquals(Permission.Type.Write, permission.type());
    }

    /**
     * 处理assert工作流Read。
     */
    private void assertWorkflowRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/workflow", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    /**
     * 处理assert工作流Write。
     */
    private void assertWorkflowWrite(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/workflow", permission.path());
        assertEquals(Permission.Type.Write, permission.type());
    }

    /**
     * 处理assertOperationsRead。
     */
    private void assertOperationsRead(String methodName, Class<?>... types) throws Exception {
        Permission permission = method(methodName, types).getAnnotation(Permission.class);
        assertEquals("/workflow/operations", permission.path());
        assertEquals(Permission.Type.Read, permission.type());
    }

    /**
     * 处理method。
     */
    private Method method(String name, Class<?>... types) throws Exception {
        return AgentWorkflowController.class.getDeclaredMethod(name, types);
    }
}
