package com.aether.agent.security;

import com.aether.agent.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic pre-execution risk analysis for MCP arguments. It deliberately
 * fails closed: unknown input is never labelled read-only.
 */
@Component
public class ToolCallRiskAnalyzer {

    private static final Pattern SQL_KEYWORD = Pattern.compile("\\b(select|show|describe|desc|explain|with|insert|update|delete|merge|replace|upsert|"
            + "create|alter|drop|truncate|rename|grant|revoke|call|exec|execute|set|use|begin|start|commit|rollback|copy|load)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_WRITE = Pattern.compile("\\b(insert|update|delete|merge|replace|upsert|create|alter|drop|truncate|rename|grant|revoke|call|exec|execute|copy|load)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_READ_WITH_SIDE_EFFECT = Pattern.compile("\\b(select\\s+.*\\binto\\b|into\\s+(outfile|dumpfile)|for\\s+update|lock\\s+in\\s+share|pg_(read|ls|stat)_file|load_file|sleep|benchmark)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SHELL_HIGH_RISK = Pattern.compile("(^|\\s)(sudo|su|rm|rmdir|del|erase|format|mkfs|dd|shutdown|reboot|poweroff|chmod|chown|takeown|icacls|"
            + "curl|wget|invoke-webrequest|iwr|git\\s+push|kubectl\\s+(delete|apply)|docker\\s+(rm|system\\s+prune)|"
            + "npm\\s+(install|publish)|pip\\s+install)(\\s|$)|[|&;]|>>?|<", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_READ_ONLY = Pattern.compile("^(ls|dir|pwd|cat|type|grep|rg|findstr|git\\s+(status|log|diff|show)|mvn\\s+(-v|--version)|java\\s+-version)(\\s|$)", Pattern.CASE_INSENSITIVE);

    public Risk analyze(AgentTool tool, Map<String, Object> arguments) {
        Candidate candidate = extractCandidate(arguments);
        if (candidate.kind == Kind.SQL) {
            return analyzeSql(candidate.value);
        }
        if (candidate.kind == Kind.COMMAND) {
            return analyzeCommand(candidate.value);
        }

        String toolName = StringUtils.defaultString(tool == null ? null : tool.getName()).toLowerCase(Locale.ROOT);
        if (toolName.contains("search") || toolName.contains("read") || toolName.contains("query")) {
            return new Risk("low", "工具名称表明其为查询/读取操作；仍需用户确认后才会发送", candidate.value);
        }
        return new Risk("medium", "无法从参数可靠识别操作类型，按中风险处理", candidate.value);
    }

    private Risk analyzeSql(String rawSql) {
        String sql = stripSqlComments(rawSql).trim();
        if (StringUtils.isBlank(sql)) {
            return new Risk("medium", "SQL 为空或仅包含注释，无法确认安全性", rawSql);
        }
        if (hasMultipleStatements(sql)) {
            return new Risk("high", "SQL 包含多条语句，可能混合读取与变更操作", rawSql);
        }
        String operation = mainSqlOperation(sql);
        if (SQL_WRITE.matcher(sql).find()) {
            return new Risk("high", "SQL 包含 " + StringUtils.defaultIfBlank(operation, "数据变更/管理") + " 操作", rawSql);
        }
        if (SQL_READ_WITH_SIDE_EFFECT.matcher(sql).find()) {
            return new Risk("high", "查询包含锁、文件读写或资源消耗型函数", rawSql);
        }
        if ("select".equals(operation) || "show".equals(operation) || "describe".equals(operation)
                || "desc".equals(operation) || "explain".equals(operation)) {
            return new Risk("low", "SQL 为只读 " + operation.toUpperCase(Locale.ROOT) + " 查询", rawSql);
        }
        if ("set".equals(operation) || "use".equals(operation) || "begin".equals(operation)
                || "start".equals(operation) || "commit".equals(operation) || "rollback".equals(operation)) {
            return new Risk("medium", "SQL 会改变会话或事务状态", rawSql);
        }
        return new Risk("medium", "无法识别 SQL 主操作，按中风险处理", rawSql);
    }

    private Risk analyzeCommand(String command) {
        String normalized = command == null ? "" : command.trim();
        if (StringUtils.isBlank(normalized)) {
            return new Risk("medium", "命令为空，无法确认安全性", command);
        }
        if (SHELL_HIGH_RISK.matcher(normalized).find()) {
            return new Risk("high", "命令包含删除、权限、网络、部署或重定向操作", command);
        }
        if (SHELL_READ_ONLY.matcher(normalized).find()) {
            return new Risk("low", "命令看起来是只读检查", command);
        }
        return new Risk("medium", "命令可能修改环境或文件，按中风险处理", command);
    }

    private Candidate extractCandidate(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return new Candidate(Kind.UNKNOWN, "{}");
        }
        for (String key : new String[]{"sql", "query", "statement"}) {
            Object value = arguments.get(key);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return new Candidate(Kind.SQL, value.toString());
            }
        }
        for (String key : new String[]{"command", "cmd", "script", "shell"}) {
            Object value = arguments.get(key);
            if (value != null && StringUtils.isNotBlank(value.toString())) {
                return new Candidate(Kind.COMMAND, value.toString());
            }
        }
        return new Candidate(Kind.UNKNOWN, JSON.toJSONString(arguments));
    }

    private String mainSqlOperation(String sql) {
        Matcher matcher = SQL_KEYWORD.matcher(sql);
        while (matcher.find()) {
            String keyword = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!"with".equals(keyword)) {
                return keyword;
            }
        }
        return "";
    }

    private String stripSqlComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--[^\\r\\n]*", " ");
    }

    private boolean hasMultipleStatements(String sql) {
        boolean singleQuote = false;
        boolean doubleQuote = false;
        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
            } else if (c == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
            } else if (c == ';' && !singleQuote && !doubleQuote && StringUtils.isNotBlank(sql.substring(i + 1))) {
                return true;
            }
        }
        return false;
    }

    private enum Kind { SQL, COMMAND, UNKNOWN }

    private static class Candidate {
        private final Kind kind;
        private final String value;

        private Candidate(Kind kind, String value) {
            this.kind = kind;
            this.value = value;
        }
    }

    public static class Risk {
        private final String level;
        private final String reason;
        private final String commandPreview;

        public Risk(String level, String reason, String commandPreview) {
            this.level = level;
            this.reason = reason;
            this.commandPreview = commandPreview;
        }

        public String getLevel() { return level; }
        public String getReason() { return reason; }
        public String getCommandPreview() { return commandPreview; }
    }
}
