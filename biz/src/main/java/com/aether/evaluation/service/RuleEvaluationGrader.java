package com.aether.evaluation.service;

import java.util.Locale;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.google.re2j.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import java.util.List;

/** Deterministic, side-effect-free rule grader used by evaluation workers. */
public final class RuleEvaluationGrader {
    private static final Pattern JSON_PATH_PATTERN = Pattern.compile("\\$((\\.[A-Za-z_][A-Za-z0-9_]*)|(\\[[0-9]+]))*");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private RuleEvaluationGrader() { }

    public static boolean isSupportedJsonPath(String path) {
        return path != null && JSON_PATH_PATTERN.matches(path);
    }

    public static Result grade(String actual, String operator, String expected) {
        return grade(actual, operator, expected, null);
    }

    /**
     * JSON paths are deliberately limited to a root, object fields and array indexes,
     * for example $.data.items[0].status.  Expressions and filters are not executable.
     */
    public static Result grade(String actual, String operator, String expected, String jsonPath) {
        return grade(actual, operator, expected, jsonPath, null);
    }

    public static Result grade(String actual, String operator, String expected, String jsonPath, String jsonSchema) {
        if (operator == null || actual == null || (!"JSON_SCHEMA".equalsIgnoreCase(operator.trim()) && expected == null)) return new Result("ERROR", null, "GRADER_ARGUMENT_REQUIRED");
        boolean passed;
        try {
            switch (operator.trim().toUpperCase(Locale.ROOT)) {
                case "EQUALS": passed = actual.equals(expected); break;
                case "CONTAINS": passed = actual.contains(expected); break;
                case "REGEX": passed = Pattern.compile(expected).matcher(actual).find(); break;
                case "JSON_PATH_EQUALS":
                    Object value = resolveJsonPath(actual, jsonPath);
                    if (value == null) return new Result("ERROR", null, "GRADER_JSON_PATH_NOT_FOUND");
                    passed = stringifyJsonValue(value).equals(expected);
                    break;
                case "JSON_SCHEMA":
                    return gradeJsonSchema(actual, jsonSchema == null ? expected : jsonSchema);
                default: return new Result("ERROR", null, "GRADER_OPERATOR_UNSUPPORTED");
            }
        } catch (RuntimeException ex) { return new Result("ERROR", null, "GRADER_EXPRESSION_INVALID"); }
        return new Result(passed ? "PASS" : "FAIL", passed ? 100 : 0, passed ? "RULE_PASSED" : "RULE_FAILED");
    }

    public static boolean isValidJsonSchema(String schema) {
        try {
            JsonNode value = JSON_MAPPER.readTree(schema);
            if (value == null || containsRemoteReference(value) || !usesDraftSeven(value)) return false;
            SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7).getSchema(value);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private static Result gradeJsonSchema(String actual, String schema) {
        if (!isValidJsonSchema(schema)) return new Result("ERROR", null, "GRADER_SCHEMA_INVALID");
        try {
            Schema compiled = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_7)
                    .getSchema(JSON_MAPPER.readTree(schema));
            List<Error> errors = compiled.validate(JSON_MAPPER.readTree(actual));
            return new Result(errors.isEmpty() ? "PASS" : "FAIL", errors.isEmpty() ? 100 : 0,
                    errors.isEmpty() ? "RULE_PASSED" : "RULE_FAILED");
        } catch (Exception ex) {
            return new Result("ERROR", null, "GRADER_EXPRESSION_INVALID");
        }
    }

    private static boolean containsRemoteReference(JsonNode node) {
        if (node.isObject()) {
            JsonNode reference = node.get("$ref");
            if (reference != null && reference.isTextual() && !reference.textValue().startsWith("#")) return true;
            java.util.Iterator<JsonNode> values = node.elements();
            while (values.hasNext()) if (containsRemoteReference(values.next())) return true;
        } else if (node.isArray()) {
            for (JsonNode child : node) if (containsRemoteReference(child)) return true;
        }
        return false;
    }

    private static boolean usesDraftSeven(JsonNode schema) {
        JsonNode dialect = schema.get("$schema");
        return dialect == null || (dialect.isTextual()
                && "http://json-schema.org/draft-07/schema#".equals(dialect.textValue()));
    }

    private static Object resolveJsonPath(String actual, String path) {
        if (!isSupportedJsonPath(path))
            throw new IllegalArgumentException("Unsupported JSON path");
        Object current = JSON.parse(actual);
        int offset = 1;
        while (offset < path.length()) {
            if (path.charAt(offset) == '.') {
                int end = offset + 1;
                while (end < path.length() && path.charAt(end) != '.' && path.charAt(end) != '[') end++;
                if (!(current instanceof JSONObject)) return null;
                current = ((JSONObject) current).get(path.substring(offset + 1, end));
                offset = end;
            } else {
                int end = path.indexOf(']', offset);
                if (!(current instanceof JSONArray) || end < 0) return null;
                int index = Integer.parseInt(path.substring(offset + 1, end));
                JSONArray array = (JSONArray) current;
                if (index >= array.size()) return null;
                current = array.get(index);
                offset = end + 1;
            }
            if (current == null) return null;
        }
        return current;
    }

    private static String stringifyJsonValue(Object value) {
        return value instanceof String ? (String) value : JSON.toJSONString(value);
    }

    public static final class Result {
        private final String status; private final Integer score; private final String reason;
        public Result(String status, Integer score, String reason) { this.status = status; this.score = score; this.reason = reason; }
        public String getStatus() { return status; } public Integer getScore() { return score; } public String getReason() { return reason; }
    }
}
