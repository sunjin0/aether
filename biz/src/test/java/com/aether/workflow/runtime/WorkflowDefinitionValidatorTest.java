package com.aether.workflow.runtime;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证工作流DefinitionValidator的行为。
 */
class WorkflowDefinitionValidatorTest {

    private static final String NODES = "["
            + "{\"id\":\"start\",\"type\":\"start\"},"
            + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"处理 ${request}\",\"outputs\":[{\"target\":\"result\",\"source\":\"$output\"}]},"
            + "{\"id\":\"next\",\"type\":\"agent\",\"resourceId\":\"agent-2\",\"prompt\":\"汇总 ${result}\"},"
            + "{\"id\":\"end\",\"type\":\"end\"}]";
    private static final String EDGES = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"next\"},{\"source\":\"next\",\"target\":\"end\"}]";

    /** 常量规则节点：未命中任何规则时返回 defaultValue，作为并行分支中的确定性产出节点。 */
    private static String constRule(String id, String target, int value) {
        return "{\"id\":\"" + id + "\",\"type\":\"rule\",\"defaultValue\":" + value
                + ",\"outputs\":[{\"target\":\"" + target + "\",\"source\":\"$output\"}]}";
    }

    /**
     * 处理setUpI18n。
     */
    @BeforeAll
    static void setUpI18n() {
        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage(any(String.class), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));
        new I18nUtils(i18nService);
    }

    /**
     * 处理acceptsDeclaredInputAndNodeOutputReferences。
     */
    @Test
    void acceptsDeclaredInputAndNodeOutputReferences() {
        String schema = "[{\"name\":\"request\",\"required\":true}]";
        String nodes = NODES;

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, EDGES, schema));
    }

    /**
     * 处理rejectsUnknownVariableReferenceAt发布Time。
     */
    @Test
    void rejectsUnknownVariableReferenceAtPublishTime() {
        String nodes = NODES.replace("${request}", "${missing}");

        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateVariables(nodes, EDGES, "[{\"name\":\"request\"}]"));
    }

    /**
     * 处理rejectsMissingRequiredOrUndeclaredStartInput。
     */
    @Test
    void rejectsMissingRequiredOrUndeclaredStartInput() {
        String schema = "[{\"name\":\"request\",\"required\":true}]";

        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateStartVariables(schema, Collections.<String, Object>emptyMap()));
        Map<String, Object> unknown = new HashMap<String, Object>();
        unknown.put("other", "value");
        assertThrows(ServerException.class,
                () -> WorkflowDefinitionValidator.validateStartVariables(schema, unknown));
    }

    /**
     * 处理acceptsOutputDeclaredOnEveryPathAndRejectsInternalOrBranchOnlyValues。
     */
    @Test
    void acceptsOutputDeclaredOnEveryPathAndRejectsInternalOrBranchOnlyValues() {
        String output = "[{\"name\":\"result\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateOutputSchema(NODES, EDGES,
                "[{\"name\":\"request\"}]", output));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateOutputSchema(NODES, EDGES,
                "[{\"name\":\"request\"}]", "[{\"name\":\"unknown\"}]"));
    }

    /**
     * 工具节点校验输出映射：template 行按声明变量渲染，未声明引用应被拒绝。
     */
    @Test
    void acceptsToolNodeAndValidatesOutputMappings() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"tool\",\"type\":\"tool\",\"resourceId\":\"tool-1\",\"argumentsTemplate\":\"${request}\","
                + "\"outputs\":[{\"target\":\"summary\",\"template\":\"结果：${request}\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"tool\"},{\"source\":\"tool\",\"target\":\"end\"}]";

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"request\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(
                nodes.replace("结果：${request}", "结果：${missing}"), edges, "[{\"name\":\"request\"}]"));
    }

    @Test
    void validatesEmailNotificationConfigurationAndVariables() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"notice\",\"type\":\"notification\",\"channel\":\"email\",\"toTemplate\":\"${email}\",\"subjectTemplate\":\"处理结果\",\"bodyTemplate\":\"${result}\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"notice\"},{\"source\":\"notice\",\"target\":\"end\"}]";

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges,
                "[{\"name\":\"email\"},{\"name\":\"result\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(
                nodes.replace("\"channel\":\"email\"", "\"channel\":\"sms\""), edges));
    }

    @Test
    void requiresFixedPublishedVersionForSubflowDefinitions() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"child\",\"type\":\"subflow\",\"workflowId\":\"workflow-child\",\"versionNo\":3,"
                + "\"inputMappings\":[{\"target\":\"request\",\"source\":\"request\"}],"
                + "\"outputs\":[{\"target\":\"result\",\"source\":\"$output.result\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"child\"},{\"source\":\"child\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"request\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(
                nodes.replace("\"versionNo\":3", "\"versionNo\":0"), edges));
    }

    @Test
    void acceptsNestedVariablePathsByTheirDeclaredRoot() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"calc\",\"type\":\"rule\",\"defaultValue\":1,\"outputs\":[{\"target\":\"amount\",\"source\":\"$.order.total\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"calc\"},{\"source\":\"calc\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"order\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"other\"}]"));
    }

    @Test
    void validatesDeterministicParallelBranchesAndRejectsInteractiveBranch() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\",\"branches\":[\"left\",\"right\"]},"
                + constRule("left", "leftValue", 1) + ","
                + constRule("right", "rightValue", 2) + ","
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},{\"source\":\"fork\",\"target\":\"left\"},{\"source\":\"fork\",\"target\":\"right\"},{\"source\":\"left\",\"target\":\"join\"},{\"source\":\"right\",\"target\":\"join\"},{\"source\":\"join\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
        String interactiveNodes = nodes.replace("\"id\":\"left\",\"type\":\"rule\"",
                "\"id\":\"left\",\"type\":\"interaction\",\"mode\":\"form\",\"question\":\"确认？\"");
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(interactiveNodes, edges, "[]"));
    }

    @Test
    void rejectsParallelWithoutCommonJoin() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\",\"branches\":[\"left\",\"right\"]},"
                + constRule("left", "x", 1) + ","
                + constRule("right", "y", 2) + ","
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"left\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},"
                + "{\"source\":\"left\",\"target\":\"end\"},"
                + "{\"source\":\"right\",\"target\":\"end\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(nodes, edges));
    }

    @Test
    void validatesParallelQuotaAndTimeout() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\",\"maxBranches\":2,\"branchTimeoutMillis\":1000,\"branches\":[\"left\",\"right\"]},"
                + constRule("left", "x", 1) + ","
                + constRule("right", "y", 2) + ","
                + "{\"id\":\"join\",\"type\":\"join\"},{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},{\"source\":\"fork\",\"target\":\"left\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},{\"source\":\"left\",\"target\":\"join\"},"
                + "{\"source\":\"right\",\"target\":\"join\"},{\"source\":\"join\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(
                nodes.replace("\"maxBranches\":2", "\"maxBranches\":0"), edges));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(
                nodes.replace("\"branchTimeoutMillis\":1000", "\"branchTimeoutMillis\":0"), edges));
    }

    @Test
    void validatesEdgeDrivenParallelAndRejectsInteractiveBranches() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\"},"
                + constRule("left", "leftValue", 1) + ","
                + constRule("right", "rightValue", 2) + ","
                + "{\"id\":\"join\",\"type\":\"join\",\"joinMode\":\"ALL_SUCCESS\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"left\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},"
                + "{\"source\":\"left\",\"target\":\"join\"},"
                + "{\"source\":\"right\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
        String interactiveNodes = nodes.replace("\"id\":\"left\",\"type\":\"rule\"",
                "\"id\":\"left\",\"type\":\"interaction\",\"mode\":\"form\",\"question\":\"确认？\"");
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(interactiveNodes, edges));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(interactiveNodes, edges, "[]"));
    }

    @Test
    void acceptsAgentInsideEdgeDrivenBranchInterior() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\"},"
                + "{\"id\":\"head\",\"type\":\"rule\",\"defaultValue\":1,\"outputs\":[{\"target\":\"x\",\"source\":\"$output\"}]},"
                + "{\"id\":\"agentNode\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"处理 ${request}\"},"
                + "{\"id\":\"right\",\"type\":\"agent\",\"resourceId\":\"agent-2\",\"prompt\":\"汇总\"},"
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"head\"},"
                + "{\"source\":\"head\",\"target\":\"agentNode\"},"
                + "{\"source\":\"agentNode\",\"target\":\"join\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},"
                + "{\"source\":\"right\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges,
                "[{\"name\":\"request\"}]"));
    }

    @Test
    void rejectsInteractiveNodeInsideEdgeDrivenBranchInterior() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\"},"
                + "{\"id\":\"head\",\"type\":\"rule\",\"defaultValue\":1,\"outputs\":[{\"target\":\"x\",\"source\":\"$output\"}]},"
                + "{\"id\":\"humanNode\",\"type\":\"interaction\",\"mode\":\"form\",\"question\":\"确认？\"},"
                + constRule("right", "y", 2) + ","
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"head\"},"
                + "{\"source\":\"head\",\"target\":\"humanNode\"},"
                + "{\"source\":\"humanNode\",\"target\":\"join\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},"
                + "{\"source\":\"right\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(nodes, edges));
    }

    @Test
    void rejectsEdgeDrivenParallelWithSingleBranch() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\"},"
                + constRule("left", "x", 1) + ","
                + "{\"id\":\"join\",\"type\":\"join\"},{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"left\"},"
                + "{\"source\":\"left\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(nodes, edges));
    }

    @Test
    void rejectsEdgeDrivenParallelBranchWithoutJoin() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\"},"
                + constRule("left", "x", 1) + ","
                + constRule("right", "y", 2) + ","
                + "{\"id\":\"join\",\"type\":\"join\"},{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},"
                + "{\"source\":\"fork\",\"target\":\"left\"},"
                + "{\"source\":\"fork\",\"target\":\"right\"},"
                + "{\"source\":\"right\",\"target\":\"join\"},"
                + "{\"source\":\"join\",\"target\":\"end\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(nodes, edges));
    }

    @Test
    void requiresCorrelationKeyForWaitingEvents() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"wait\",\"type\":\"wait_event\",\"eventType\":\"payment.completed\",\"correlationKeyTemplate\":\"${orderId}\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"wait\"},{\"source\":\"wait\",\"target\":\"end\"}]";

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"orderId\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validate(
                nodes.replace(",\"correlationKeyTemplate\":\"${orderId}\"", ""), edges));
    }

    @Test
    void acceptsNestedStructuredOutputTargetByDeclaredRoot() {
        // 结构化输出目标 result.order.total：根变量 result 写入后，下游 ${result.order.total} 可达
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"处理 ${request}\","
                + "\"outputs\":[{\"target\":\"result.order.total\",\"source\":\"$output.total\"},{\"target\":\"result.order.id\",\"source\":\"$output.id\"}]},"
                + "{\"id\":\"next\",\"type\":\"agent\",\"resourceId\":\"agent-2\",\"prompt\":\"汇总 ${result.order.total} 单 ${result.order.id}\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"next\"},{\"source\":\"next\",\"target\":\"end\"}]";

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"request\"}]"));
    }

    @Test
    void rejectsMalformedNestedOutputTarget() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"x\","
                + "\"outputs\":[{\"target\":\"result..total\",\"source\":\"$output\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"end\"}]";

        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
    }

    @Test
    void rejectsOutputMappingsOnNonProducingNodeTypes() {
        // delay 不产出变量，携带 outputs 应在发布时拒绝
        String delayNodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"pause\",\"type\":\"delay\",\"delayMillis\":1000,"
                + "\"outputs\":[{\"target\":\"x\",\"source\":\"$output\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String chain = "[{\"source\":\"start\",\"target\":\"pause\"},{\"source\":\"pause\",\"target\":\"end\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(delayNodes, chain, "[]"));
    }

    @Test
    void acceptsOutputMappingsOnJoinNode() {
        // join 属于产出型节点：汇聚节点需要把各并行分支分别写入的变量组装为结构化结果，
        // 其 outputs 因此合法（见 WorkflowDefinitionValidator 的 PRODUCING_TYPES 与 joinBranchOutput 分支）。
        String joinNodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"gate\",\"type\":\"join\",\"joinMode\":\"ALL_SUCCESS\","
                + "\"outputs\":[{\"target\":\"x\",\"source\":\"$output\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String joinChain = "[{\"source\":\"start\",\"target\":\"gate\"},{\"source\":\"gate\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(joinNodes, joinChain, "[]"));
    }

    @Test
    void acceptsSameNodeOutputRowCascade() {
        // 同一节点 outputs 内，后续行可引用前面行刚写入的目标（与运行时逐行写变量一致）
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"x\","
                + "\"outputs\":["
                + "{\"target\":\"first\",\"source\":\"$output.a\"},"
                + "{\"target\":\"second\",\"template\":\"${first}\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
    }

    @Test
    void acceptsEdgeConditionReferencingSourceNodeOutput() {
        // 连线条件在源节点完成产出后求值，可引用源节点自身刚写入的输出
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"x\","
                + "\"outputs\":[{\"target\":\"result\",\"source\":\"$output\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String goodEdges = "[{\"source\":\"start\",\"target\":\"agent\"},"
                + "{\"source\":\"agent\",\"target\":\"end\",\"condition\":\"${result}\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, goodEdges, "[]"));
        String badEdges = "[{\"source\":\"start\",\"target\":\"agent\"},"
                + "{\"source\":\"agent\",\"target\":\"end\",\"condition\":\"${ghost}\"}]";
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(nodes, badEdges, "[]"));
    }

    @Test
    void validatesRuleBranchConditionsAgainstPreRuleVariables() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"ruleNode\",\"type\":\"rule\","
                + "\"rules\":[{\"condition\":\"${request.amount} > 10\",\"value\":\"big\"}],"
                + "\"defaultValue\":\"small\","
                + "\"outputs\":[{\"target\":\"level\",\"source\":\"$output\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"ruleNode\"},{\"source\":\"ruleNode\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"request\"}]"));
        String bad = nodes.replace("${request.amount} > 10", "${ghost} > 10");
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(bad, edges, "[{\"name\":\"request\"}]"));
    }

    @Test
    void acceptsRuntimeInternalUnderscoreVariableReferences() {
        // 运行期内部变量（如循环计数 _loop_<edge>_count）由运行时写入，静态校验放宽不误报
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"循环 ${_loop_e1_count}\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
    }
}
