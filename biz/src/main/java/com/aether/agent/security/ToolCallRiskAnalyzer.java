package com.aether.agent.security;

import com.aether.agent.entity.AgentTool;
import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic, fail-closed tool-call risk classifier.
 *
 * <p>The analyzer intentionally only trusts values in semantically meaningful
 * argument keys. A user search query containing words such as "delete" must
 * not be treated as a destructive operation, while a nested {@code action}
 * or {@code command} must not escape inspection.</p>
 */
@Component
public class ToolCallRiskAnalyzer {
    private static final Pattern SQL_START = Pattern.compile("^\\s*(?:/\\*.*?\\*/\\s*)*(select|show|describe|desc|explain|with|insert|update|delete|merge|replace|upsert|create|alter|drop|truncate|rename|grant|revoke|call|exec|execute|set|use|begin|start|commit|rollback|copy|load)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SQL_QUERY_START = Pattern.compile("^\\s*(?:/\\*.*?\\*/\\s*)*(?:select\\b|show\\b|describe\\b|desc\\b|explain\\b|with\\s+[\\w`\\\"\\[]+\\s+as\\s*\\(|insert\\s+into\\b|update\\s+[\\w`\\\"\\[]+\\s+set\\b|delete\\s+from\\b|merge\\s+into\\b|replace\\s+into\\b|upsert\\s+into\\b|create\\s+(?:table|view|index|schema|database)\\b|alter\\s+(?:table|view|index|schema|database)\\b|drop\\s+(?:table|view|index|schema|database)\\b|truncate\\s+table\\b|grant\\s+\\w+\\s+on\\b|revoke\\s+\\w+\\s+on\\b)", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SQL_WRITE = Pattern.compile("\\b(insert|update|delete|merge|replace|upsert|create|alter|drop|truncate|rename|grant|revoke|call|exec|execute|copy|load)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_SIDE_EFFECT = Pattern.compile("\\b(select\\s+.*\\binto\\b|into\\s+(outfile|dumpfile)|for\\s+update|lock\\s+in\\s+share|pg_(read|ls|stat)_file|load_file|sleep|benchmark)\\b", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern SHELL_HIGH_RISK = Pattern.compile("(^|\\s)(sudo|su|rm|rmdir|del|erase|format|mkfs|dd|shutdown|reboot|poweroff|chmod|chown|takeown|icacls|curl|wget|invoke-webrequest|iwr|git\\s+push|kubectl\\s+(delete|apply)|docker\\s+(rm|system\\s+prune)|npm\\s+(install|publish)|pip\\s+install)(\\s|$)|[|&;]|>>?|<", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHELL_READ_ONLY = Pattern.compile("^(ls|dir|pwd|cat|type|grep|rg|findstr|git\\s+(status|log|diff|show)|mvn\\s+(-v|--version)|java\\s+-version)(\\s|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SQL_KEY = Pattern.compile("(^|[_-])(sql|statement|db[_-]?query)([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern COMMAND_KEY = Pattern.compile("(^|[_-])(command|cmd|script|shell|powershell|bash)([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern METHOD_KEY = Pattern.compile("(^|[_-])(method|http[_-]?method|verb|operation|action)([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern URL_KEY = Pattern.compile("(^|[_-])(url|uri|endpoint|target|destination)([_-]|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SECRET_KEY = Pattern.compile("(password|passwd|secret|token|api[_-]?key|authorization|credential|private[_-]?key)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WRITE_VERB = Pattern.compile("^(create|add|write|update|patch|delete|remove|destroy|drop|truncate|replace|publish|send|upload|deploy|apply|grant|revoke|enable|disable|restart|stop)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_VERB = Pattern.compile("^(get|head|options|read|list|find|search|query|lookup|describe|status|show|inspect|validate|preview)$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PRIVATE_URL = Pattern.compile("^(https?://)?(localhost|127(?:\\.\\d{1,3}){3}|0(?:\\.\\d{1,3}){3}|10(?:\\.\\d{1,3}){3}|192\\.168(?:\\.\\d{1,3}){2}|172\\.(?:1[6-9]|2\\d|3[0-1])(?:\\.\\d{1,3}){2})(?::\\d+)?(?:/|$)", Pattern.CASE_INSENSITIVE);
    private static final Pattern MUTATING_TOOL = Pattern.compile("(delete|remove|destroy|drop|publish|deploy|write|update|create|insert|upload|send|grant|revoke|restart|stop|删除|移除|销毁|发布|部署|写入|更新|新增|创建|上传|发送|授权|撤销|重启|停止)", Pattern.CASE_INSENSITIVE);
    private static final Pattern READ_ONLY_TOOL = Pattern.compile("(search|read|lookup|list|inspect|query|find|show|get|retrieve|浏览|检索|搜索|查询|读取|查看|列表|获取|预览|校验)", Pattern.CASE_INSENSITIVE);

    /**
     * 处理analyze。
     */
    public Risk analyze(AgentTool tool, Map<String, Object> arguments) {
        List<Signal> signals = new ArrayList<>();
        inspectArguments(arguments, "", signals);
        inspectToolName(tool, signals);
        if (signals.isEmpty()) {
            return new Risk(Level.MEDIUM.value, "无法从参数可靠识别操作类型，按中风险处理", preview(arguments), Collections.<String>emptyList());
        }
        Signal strongest = signals.get(0);
        for (Signal signal : signals) if (signal.level.rank > strongest.level.rank) strongest = signal;
        List<String> evidence = new ArrayList<>();
        for (Signal signal : signals) evidence.add(signal.path + ": " + signal.reason);
        return new Risk(strongest.level.value, strongest.reason, strongest.preview, evidence);
    }

    /**
     * 处理inspectArguments。
     */
    @SuppressWarnings("unchecked")
    private void inspectArguments(Object value, String path, List<Signal> signals) {
        if (value instanceof Map) {
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                String key = String.valueOf(entry.getKey());
                inspectArguments(entry.getValue(), path + "/" + key, signals);
            }
            return;
        }
        if (value instanceof Collection) {
            int index = 0;
            for (Object item : (Collection<?>) value) inspectArguments(item, path + "/" + index++, signals);
            return;
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++)
                inspectArguments(Array.get(value, i), path + "/" + i, signals);
            return;
        }
        String key = leaf(path);
        String text = value == null ? "" : String.valueOf(value).trim();
        if (StringUtils.isBlank(text)) return;

        if (SQL_KEY.matcher(key).find() || ("query".equalsIgnoreCase(key) && looksLikeSql(text))) {
            signals.add(analyzeSql(path, text));
            return;
        }
        if (COMMAND_KEY.matcher(key).find()) {
            signals.add(analyzeCommand(path, text));
            return;
        }
        if (METHOD_KEY.matcher(key).find()) {
            signals.add(analyzeOperation(path, text));
            return;
        }
        if (SECRET_KEY.matcher(key).find()) {
            signals.add(new Signal(Level.HIGH, path, "调用参数包含凭据或密钥，可能导致敏感信息暴露", mask(text)));
            return;
        }
        if (URL_KEY.matcher(key).find() && PRIVATE_URL.matcher(text).find()) {
            signals.add(new Signal(Level.HIGH, path, "目标地址指向本机或私有网络，需防范 SSRF/内网访问", abbreviate(text)));
            return;
        }
        if ("query".equalsIgnoreCase(key) || "q".equalsIgnoreCase(key) || "keyword".equalsIgnoreCase(key)) {
            signals.add(new Signal(Level.MEDIUM, path, "查询文本不是可识别 SQL；需结合工具语义确认是否只读", abbreviate(text)));
        }
    }

    /**
     * 处理inspectToolName。
     */
    private void inspectToolName(AgentTool tool, List<Signal> signals) {
        if (tool == null) return;
        inspectToolMetadata("/tool/name", tool.getName(), "工具名称", signals);
        inspectToolMetadata("/tool/code", tool.getCode(), "工具编码", signals);
        inspectToolMetadata("/tool/mcpToolName", tool.getMcpToolName(), "MCP 工具名称", signals);
        inspectToolMetadata("/tool/description", tool.getDescription(), "工具描述", signals);
    }

    /**
     * 处理inspectToolMetadata。
     */
    private void inspectToolMetadata(String path, String value, String label, List<Signal> signals) {
        if (StringUtils.isBlank(value)) return;
        String normalized = value.trim();
        if (MUTATING_TOOL.matcher(normalized).find()) {
            signals.add(new Signal(Level.HIGH, path, label + "表明该工具可能产生外部状态变更", abbreviate(normalized)));
        } else if (READ_ONLY_TOOL.matcher(normalized).find()) {
            signals.add(new Signal(Level.LOW, path, label + "表明该工具用于查询或读取", abbreviate(normalized)));
        }
    }

    /**
     * 处理analyzeSql。
     */
    private Signal analyzeSql(String path, String rawSql) {
        String sql = stripSqlComments(rawSql).trim();
        if (StringUtils.isBlank(sql))
            return new Signal(Level.MEDIUM, path, "SQL 为空或仅包含注释，无法确认安全性", rawSql);
        if (hasMultipleStatements(sql))
            return new Signal(Level.HIGH, path, "SQL 包含多条语句，可能混合读取与变更操作", rawSql);
        if (SQL_WRITE.matcher(sql).find()) return new Signal(Level.HIGH, path, "SQL 包含数据变更或管理操作", rawSql);
        if (SQL_SIDE_EFFECT.matcher(sql).find())
            return new Signal(Level.HIGH, path, "查询包含锁、文件读写或资源消耗型函数", rawSql);
        String operation = mainSqlOperation(sql);
        if ("select".equals(operation) || "show".equals(operation) || "describe".equals(operation) || "desc".equals(operation) || "explain".equals(operation)) {
            return new Signal(Level.LOW, path, "SQL 为只读 " + operation.toUpperCase(Locale.ROOT) + " 查询", rawSql);
        }
        return new Signal(Level.MEDIUM, path, "SQL 会改变会话状态或无法识别主操作", rawSql);
    }

    /**
     * 处理analyzeCommand。
     */
    private Signal analyzeCommand(String path, String command) {
        String normalized = command.trim();
        if (SHELL_HIGH_RISK.matcher(normalized).find())
            return new Signal(Level.HIGH, path, "命令包含删除、权限、网络、部署、管道或重定向操作", command);
        if (SHELL_READ_ONLY.matcher(normalized).find())
            return new Signal(Level.LOW, path, "命令看起来是只读检查", command);
        return new Signal(Level.MEDIUM, path, "命令可能修改环境或文件，按中风险处理", command);
    }

    /**
     * 处理analyzeOperation。
     */
    private Signal analyzeOperation(String path, String operation) {
        String normalized = operation.trim().toLowerCase(Locale.ROOT);
        if (WRITE_VERB.matcher(normalized).matches() || "post".equals(normalized) || "put".equals(normalized) || "patch".equals(normalized) || "delete".equals(normalized)) {
            return new Signal(Level.HIGH, path, "调用声明了写入、删除或外部状态变更操作", operation);
        }
        if (READ_VERB.matcher(normalized).matches())
            return new Signal(Level.LOW, path, "调用声明了只读操作", operation);
        return new Signal(Level.MEDIUM, path, "调用操作类型未知，按中风险处理", operation);
    }

    /**
     * 处理looksLikeSql。
     */
    private boolean looksLikeSql(String value) {
        return SQL_QUERY_START.matcher(value).find();
    }

    /**
     * 处理mainSqlOperation。
     */
    private String mainSqlOperation(String sql) {
        Matcher matcher = SQL_START.matcher(sql);
        if (!matcher.find()) return "";
        String operation = matcher.group(1).toLowerCase(Locale.ROOT);
        if (!"with".equals(operation)) return operation;
        Matcher nested = Pattern.compile("\\b(select|insert|update|delete|merge|replace)\\b", Pattern.CASE_INSENSITIVE).matcher(sql);
        return nested.find() ? nested.group(1).toLowerCase(Locale.ROOT) : "with";
    }

    /**
     * 处理stripSqlComments。
     */
    private String stripSqlComments(String sql) {
        return sql.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)--[^\\r\\n]*", " ");
    }

    /**
     * 处理leaf。
     */
    private String leaf(String path) {
        int slash = path.lastIndexOf('/');
        return slash < 0 ? path : path.substring(slash + 1);
    }

    /**
     * 预览当前请求。
     */
    private String preview(Map<String, Object> arguments) {
        return JSON.toJSONString(arguments == null ? Collections.emptyMap() : arguments);
    }

    /**
     * 处理abbreviate。
     */
    private String abbreviate(String value) {
        return value.length() <= 512 ? value : value.substring(0, 512);
    }

    /**
     * 处理mask。
     */
    private String mask(String value) {
        return value.length() <= 4 ? "****" : value.substring(0, 2) + "***" + value.substring(value.length() - 2);
    }

    /**
     * 判断是否拥有MultipleStatements。
     */
    private boolean hasMultipleStatements(String sql) {
        boolean singleQuoted = false;
        boolean doubleQuoted = false;
        for (int i = 0; i < sql.length(); i++) {
            char current = sql.charAt(i);
            if (current == 39 && !doubleQuoted) {
                singleQuoted = !singleQuoted;
            } else if (current == 34 && !singleQuoted) {
                doubleQuoted = !doubleQuoted;
            } else if (current == ';' && !singleQuoted && !doubleQuoted
                    && StringUtils.isNotBlank(sql.substring(i + 1))) {
                return true;
            }
        }
        return false;
    }

    /**
     * 表示Level。
     */
    private enum Level {
        LOW("low", 1), MEDIUM("medium", 2), HIGH("high", 3);
        private final String value;
        private final int rank;

        /**
         * 创建 {@code Level} 实例。
         */
        Level(String value, int rank) {
            this.value = value;
            this.rank = rank;
        }
    }

    /**
     * 表示Signal。
     */
    private static class Signal {
        private final Level level;
        private final String path;
        private final String reason;
        private final String preview;

        /**
         * 创建 {@code Signal} 实例。
         */
        private Signal(Level level, String path, String reason, String preview) {
            this.level = level;
            this.path = path;
            this.reason = reason;
            this.preview = preview;
        }
    }

    /**
     * 表示Risk。
     */
    public static class Risk {
        private final String level;
        private final String reason;
        private final String commandPreview;
        private final List<String> evidence;

        /**
         * 创建 {@code Risk} 实例。
         */
        public Risk(String level, String reason, String commandPreview) {
            this(level, reason, commandPreview, Collections.<String>emptyList());
        }

        /**
         * 创建 {@code Risk} 实例。
         */
        public Risk(String level, String reason, String commandPreview, List<String> evidence) {
            this.level = level;
            this.reason = reason;
            this.commandPreview = commandPreview;
            this.evidence = Collections.unmodifiableList(new ArrayList<>(evidence));
        }

        /**
         * 获取Level。
         */
        public String getLevel() {
            return level;
        }

        /**
         * 获取Reason。
         */
        public String getReason() {
            return reason;
        }

        /**
         * 获取Command预览。
         */
        public String getCommandPreview() {
            return commandPreview;
        }

        /**
         * 获取Evidence。
         */
        public List<String> getEvidence() {
            return evidence;
        }
    }
}
