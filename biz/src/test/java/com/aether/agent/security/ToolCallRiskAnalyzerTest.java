package com.aether.agent.security;

import com.aether.agent.entity.AgentTool;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ToolCallRiskAnalyzerTest {

    private final ToolCallRiskAnalyzer analyzer = new ToolCallRiskAnalyzer();

    @Test
    void marksSelectAsLowRisk() {
        Map<String, Object> arguments = new HashMap<>();
        arguments.put("sql", "SELECT * FROM sys_user");

        assertEquals("low", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    @Test
    void marksDataChangeAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql", "DELETE FROM sys_user");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    @Test
    void marksCteSelectAsLowRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql",
                "WITH recent AS (SELECT id FROM sys_user) SELECT * FROM recent");

        assertEquals("low", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    @Test
    void marksMultipleSqlStatementsAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("sql",
                "SELECT * FROM sys_user; DELETE FROM sys_user");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("sql_query"), arguments).getLevel());
    }

    @Test
    void marksDestructiveShellCommandAsHighRisk() {
        Map<String, Object> arguments = Collections.<String, Object>singletonMap("command", "rm -rf ./build");

        assertEquals("high", analyzer.analyze(new AgentTool().setName("shell"), arguments).getLevel());
    }
}
