package com.aether.workflow.service.impl;

import com.aether.workflow.runtime.WorkflowPathResolver;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 统一输出映射引擎：所有产出节点（agent/tool/interaction/rule/http/notification/subflow/wait_event）
 * 共用同一份 outputs 映射，把节点原始输出或既有变量写入流程变量。
 * <p>直接驱动 {@link AgentWorkflowExecutionServiceImpl#applyNodeOutputs} 的无 Spring 单测。</p>
 */
class AgentWorkflowNodeOutputMappingTest {

    private static JSONObject definition(String outputsJson) {
        return JSON.parseObject("{\"outputs\":" + outputsJson + "}");
    }

    private static Map<String, Object> newVariables() {
        return new HashMap<String, Object>();
    }

    @Test
    void wholeOutputPassthroughWritesFullNodeOutput() {
        JSONObject output = JSON.parseObject("{\"code\":\"ok\",\"data\":{\"id\":3}}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                definition("[{\"target\":\"result\",\"source\":\"$output\"}]"), output, variables);
        assertEquals(JSON.toJSONString(output), JSON.toJSONString(variables.get("result")));
    }

    @Test
    void extractsFieldsFromInteractionStyleAnswer() {
        // 交互节点（表单模式）输出为按问题 key 组织的回答对象，映射按需取值
        JSONObject output = JSON.parseObject("{\"reason\":\"late\",\"details\":[\"high\"]}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(definition(
                "[{\"target\":\"reason\",\"source\":\"$output.reason\"},{\"target\":\"priority\",\"source\":\"$output.details.0\"}]"),
                output, variables);
        assertEquals("late", variables.get("reason"));
        assertEquals("high", variables.get("priority"));
    }

    @Test
    void descendsIntoJsonTextNodeOutput() {
        // 工具等以 JSON 文本返回的输出在 $output.<字段> 下自动穿透解析
        String textOutput = "{\"code\":\"ok\"}";
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                definition("[{\"target\":\"status\",\"source\":\"$output.code\"}]"), textOutput, variables);
        assertEquals("ok", variables.get("status"));
    }

    @Test
    void readsExistingSharedVariablesAsSource() {
        Map<String, Object> variables = newVariables();
        variables.put("order", JSON.parseObject("{\"total\":100}"));
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(definition(
                "[{\"target\":\"a\",\"source\":\"$.order.total\"},{\"target\":\"b\",\"source\":\"order.total\"}]"),
                null, variables);
        assertEquals(100, variables.get("a"));
        assertEquals(100, variables.get("b"));
    }

    @Test
    void templateRowsReferEarlierTargetsSequentially() {
        JSONObject output = JSON.parseObject("{\"name\":\"alice\"}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(definition(
                "[{\"target\":\"name\",\"source\":\"$output.name\"},{\"target\":\"greeting\",\"template\":\"Hi ${name}\"}]"),
                output, variables);
        assertEquals("alice", variables.get("name"));
        assertEquals("Hi alice", variables.get("greeting"));
    }

    @Test
    void literalValueRowAndTemplatePrecedence() {
        Map<String, Object> variables = newVariables();
        JSONObject output = JSON.parseObject("{\"real\":\"source-value\"}");
        // template 与 source 同时出现时 template 优先（先判 template）
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(definition(
                "[{\"target\":\"mode\",\"value\":\"form\"},{\"target\":\"pick\",\"template\":\"tpl-${mode}\",\"source\":\"$output.real\"}]"),
                output, variables);
        assertEquals("form", variables.get("mode"));
        assertEquals("tpl-form", variables.get("pick"));
    }

    @Test
    void blankTargetAndNonObjectRowsAreSkipped() {
        JSONObject output = JSON.parseObject("{\"name\":\"alice\"}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                JSON.parseObject("{\"outputs\":[\"junk\",{\"target\":\"\",\"source\":\"$output.name\"},{\"target\":\"kept\",\"source\":\"$output.name\"}]}"),
                output, variables);
        assertFalse(variables.containsKey(""));
        assertEquals("alice", variables.get("kept"));
        assertFalse(variables.containsKey("junk"));
    }

    @Test
    void explicitNullSourceWritesNullAndNoopCasesAreHarmless() {
        JSONObject output = JSON.parseObject("{\"name\":\"alice\"}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                definition("[{\"target\":\"z\",\"source\":null}]"), output, variables);
        assertTrue(variables.containsKey("z"));
        assertNull(variables.get("z"));

        Map<String, Object> unchanged = newVariables();
        unchanged.put("kept", 1);
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(null, output, unchanged);
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(new JSONObject(), output, unchanged);
        assertEquals(1, unchanged.get("kept"));
        assertEquals(1, unchanged.size());
    }

    @Test
    void nestedStructuredTargetsBuildObjectTreePerRowSequence() {
        JSONObject output = JSON.parseObject("{\"total\":100,\"id\":\"A1\",\"name\":\"refund\"}");
        Map<String, Object> variables = newVariables();
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(definition(
                "[{\"target\":\"result.order.total\",\"source\":\"$output.total\"},"
                        + "{\"target\":\"result.order.id\",\"source\":\"$output.id\"},"
                        + "{\"target\":\"result.type\",\"value\":\"REFUND\"}]"),
                output, variables);
        // 同行多行目标拼出一个 result 嵌套对象；顶层只出现 result 一个键
        assertEquals(1, variables.size());
        assertEquals(100, WorkflowPathResolver.resolve(variables, "result.order.total"));
        assertEquals("A1", WorkflowPathResolver.resolve(variables, "result.order.id"));
        assertEquals("REFUND", WorkflowPathResolver.resolve(variables, "result.type"));
    }

    @Test
    void nestedWriteToExistingObjectMergesWithoutWipingEarlierSubtree() {
        Map<String, Object> variables = newVariables();
        variables.put("result", JSON.parseObject("{\"order\":{\"id\":\"A1\"}}"));
        JSONObject output = JSON.parseObject("{\"total\":100}");
        AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                definition("[{\"target\":\"result.order.total\",\"source\":\"$output.total\"}]"), output, variables);
        assertEquals("A1", WorkflowPathResolver.resolve(variables, "result.order.id"));
        assertEquals(100, WorkflowPathResolver.resolve(variables, "result.order.total"));
    }

    @Test
    void nestedTargetConflictingWithScalarIntermediateFailsNodeExplicitly() {
        JSONObject output = JSON.parseObject("{\"code\":\"ok\"}");
        // 第一行把 order 写成标量，随后行想下钻 order.total → 中间层不是对象，应显式抛错而非静默覆盖
        assertThrows(IllegalArgumentException.class, () -> AgentWorkflowExecutionServiceImpl.applyNodeOutputs(
                definition("[{\"target\":\"order\",\"value\":\"REFUND\"},{\"target\":\"order.total\",\"source\":\"$output.code\"}]"),
                output, newVariables()));
    }
}
