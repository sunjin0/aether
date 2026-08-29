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
            + "{\"id\":\"agent\",\"type\":\"agent\",\"resourceId\":\"agent-1\",\"prompt\":\"处理 ${request}\",\"outputKey\":\"result\"},"
            + "{\"id\":\"next\",\"type\":\"agent\",\"resourceId\":\"agent-2\",\"prompt\":\"汇总 ${result}\"},"
            + "{\"id\":\"end\",\"type\":\"end\"}]";
    private static final String EDGES = "[{\"source\":\"start\",\"target\":\"agent\"},{\"source\":\"agent\",\"target\":\"next\"},{\"source\":\"next\",\"target\":\"end\"}]";

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
     * 工具节点应使用新名称，同时保持转换节点的输入变量校验。
     */
    @Test
    void acceptsToolAliasAndValidatesTransformMappings() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"tool\",\"type\":\"tool\",\"resourceId\":\"tool-1\",\"argumentsTemplate\":\"${request}\"},"
                + "{\"id\":\"transform\",\"type\":\"transform\",\"mappings\":[{\"target\":\"summary\",\"template\":\"结果：${request}\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"tool\"},{\"source\":\"tool\",\"target\":\"transform\"},{\"source\":\"transform\",\"target\":\"end\"}]";

        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"request\"}]"));
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
                + "{\"id\":\"child\",\"type\":\"subflow\",\"workflowId\":\"workflow-child\",\"versionNo\":3,\"inputMappings\":[{\"target\":\"request\",\"source\":\"request\"}],\"outputMappings\":[{\"target\":\"result\",\"source\":\"result\"}]},"
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
                + "{\"id\":\"transform\",\"type\":\"transform\",\"mappings\":[{\"target\":\"amount\",\"source\":\"$.order.total\"}]},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"transform\"},{\"source\":\"transform\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"order\"}]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[{\"name\":\"other\"}]"));
    }

    @Test
    void validatesDeterministicParallelBranchesAndRejectsInteractiveBranch() {
        String nodes = "["
                + "{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\",\"branches\":[\"left\",\"right\"]},"
                + "{\"id\":\"left\",\"type\":\"transform\",\"mappings\":[{\"target\":\"leftValue\",\"value\":1}]},"
                + "{\"id\":\"right\",\"type\":\"rule\",\"rules\":[{\"condition\":\"true\",\"value\":2}]},"
                + "{\"id\":\"join\",\"type\":\"join\"},"
                + "{\"id\":\"end\",\"type\":\"end\"}]";
        String edges = "[{\"source\":\"start\",\"target\":\"fork\"},{\"source\":\"fork\",\"target\":\"left\"},{\"source\":\"fork\",\"target\":\"right\"},{\"source\":\"left\",\"target\":\"join\"},{\"source\":\"right\",\"target\":\"join\"},{\"source\":\"join\",\"target\":\"end\"}]";
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validate(nodes, edges));
        assertDoesNotThrow(() -> WorkflowDefinitionValidator.validateVariables(nodes, edges, "[]"));
        assertThrows(ServerException.class, () -> WorkflowDefinitionValidator.validateVariables(
                nodes.replace("transform", "human"), edges, "[]"));
    }

    @Test
    void rejectsParallelWithoutCommonJoin() {
        String nodes = "[{\"id\":\"start\",\"type\":\"start\"},"
                + "{\"id\":\"fork\",\"type\":\"parallel\",\"branches\":[\"left\",\"right\"]},"
                + "{\"id\":\"left\",\"type\":\"transform\",\"mappings\":[{\"target\":\"x\",\"value\":1}]},"
                + "{\"id\":\"right\",\"type\":\"transform\",\"mappings\":[{\"target\":\"y\",\"value\":2}]},"
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
                + "{\"id\":\"left\",\"type\":\"transform\",\"mappings\":[{\"target\":\"x\",\"value\":1}]},"
                + "{\"id\":\"right\",\"type\":\"transform\",\"mappings\":[{\"target\":\"y\",\"value\":2}]},"
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
}
