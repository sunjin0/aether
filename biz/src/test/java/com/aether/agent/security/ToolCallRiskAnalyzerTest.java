package com.aether.agent.security;

import com.aether.agent.entity.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 验证ToolCallRiskAnalyzer的行为。
 */
class ToolCallRiskAnalyzerTest {

    private final ToolCallRiskAnalyzer analyzer = new ToolCallRiskAnalyzer();

    /**
     * 处理marksSelectAsLowRisk。
     */
    @Test
    void marksSelectAsLowRisk() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sql", "SELECT * FROM sys_user");

        assertEquals("low", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    /**
     * 处理marksDataChangeAsHighRisk。
     */
    @Test
    void marksDataChangeAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql", "DELETE FROM sys_user");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    /**
     * 处理marksCteSelectAsLowRisk。
     */
    @Test
    void marksCteSelectAsLowRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql",
                "WITH recent AS (SELECT id FROM sys_user) SELECT * FROM recent");

        assertEquals("low", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    /**
     * 处理marksMultipleSqlStatementsAsHighRisk。
     */
    @Test
    void marksMultipleSqlStatementsAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql",
                "SELECT * FROM sys_user; DELETE FROM sys_user");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    /**
     * 处理marksDestructiveShellCommandAsHighRisk。
     */
    @Test
    void marksDestructiveShellCommandAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("command", "rm -rf ./build");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("shell"), arguments).getLevel());
    }

    /**
     * 处理inspectsNestedArgumentsInsteadOfOnlyFirstCandidate。
     */
    @Test
    void inspectsNestedArgumentsInsteadOfOnlyFirstCandidate() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("filters", Collections.singletonMap("action", "delete"));
        arguments.put("query", "find recent orders");

        ToolCallRiskAnalyzer.Risk risk = analyzer.analyze(new AgentTool().setName("order_tool"), arguments);

        assertEquals("high", risk.getLevel());
        assertFalse(risk.getEvidence().isEmpty());
    }

    /**
     * 处理treatsPlain查询TextAsUnknownInsteadOfSql。
     */
    @Test
    void treatsPlainQueryTextAsUnknownInsteadOfSql() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("query", "delete the word from this document");

        assertEquals("medium", analyzer.analyze(new AgentTool().setName("document_tool"), arguments).getLevel());
    }

    /**
     * 处理marksHttpWriteAndPrivateNetworkTargetAsHighRisk。
     */
    @Test
    void marksHttpWriteAndPrivateNetworkTargetAsHighRisk() {
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> request = new HashMap<>();
        request.put("method", "POST");
        request.put("url", "http://192.168.1.8/admin");
        arguments.put("request", request);

        assertEquals("high", analyzer.analyze(new AgentTool().setName("http_client"), arguments).getLevel());
    }

    /**
     * 处理marksSecretArgumentAsHighRiskWithoutExposingFullValue。
     */
    @Test
    void marksSecretArgumentAsHighRiskWithoutExposingFullValue() {
        ToolCallRiskAnalyzer.Risk risk = analyzer.analyze(new AgentTool().setName("client"),
                Collections.<String, Object>singletonMap("apiKey", "super-secret-value"));

        assertEquals("high", risk.getLevel());
        assertFalse(risk.getCommandPreview().contains("super-secret-value"));
    }

    /**
     * 处理usesToolDescriptionAsRiskSignal。
     */
    @Test
    void usesToolDescriptionAsRiskSignal() {
        AgentTool tool = new AgentTool().setName("gateway")
                .setDescription("将配置发布到生产环境并重启服务");

        assertEquals("high", analyzer.analyze(tool, Collections.<String, Object>emptyMap()).getLevel());
    }

    /**
     * 处理recognizesReadOnlyToolDescription。
     */
    @Test
    void recognizesReadOnlyToolDescription() {
        AgentTool tool = new AgentTool().setName("catalog")
                .setDescription("仅用于查询和查看商品目录，不会修改数据");

        assertEquals("low", analyzer.analyze(tool, Collections.<String, Object>emptyMap()).getLevel());
    }
}
