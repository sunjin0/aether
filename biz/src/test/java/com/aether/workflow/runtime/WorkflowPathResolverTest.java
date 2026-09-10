package com.aether.workflow.runtime;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
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
 * 统一取值语法（节点输出映射/模板/条件/子流程映射共用）的路径解析行为。
 * 与 {@link WorkflowDefinitionValidatorTest} 一起覆盖 {@link WorkflowPathResolver}。
 */
class WorkflowPathResolverTest {

    @Test
    void nullRootOrEmptyPathReturnsSensibleDefaults() {
        assertNull(WorkflowPathResolver.resolve(null, "a.b"));
        assertNull(WorkflowPathResolver.resolve(null, null));

        JSONObject root = JSON.parseObject("{\"a\":1}");
        assertEquals(root, WorkflowPathResolver.resolve(root, ""));
        assertEquals(root, WorkflowPathResolver.resolve(root, null));
        assertEquals(root, WorkflowPathResolver.resolve(root, "   "));
    }

    @Test
    void stripsDollarPrefixAndWalksNestedMaps() {
        JSONObject root = JSON.parseObject("{\"order\":{\"total\":100,\"customer\":{\"name\":\"alice\"}}}");
        assertEquals(100, WorkflowPathResolver.resolve(root, "$.order.total"));
        assertEquals("alice", WorkflowPathResolver.resolve(root, "order.customer.name"));
    }

    @Test
    void walksListAndArrayIndexes() {
        JSONObject root = JSON.parseObject("{\"items\":[{\"name\":\"a\"},{\"name\":\"b\"}]}");
        JSONArray items = root.getJSONArray("items");
        assertEquals("b", WorkflowPathResolver.resolve(root, "items.1.name"));

        Map<String, Object> item0 = new HashMap<String, Object>();
        item0.put("price", 3);
        Object[] arrayRoot = new Object[]{new Object[]{"x", item0}};
        assertEquals("x", WorkflowPathResolver.resolve(arrayRoot, "0.0"));
        assertEquals(3, WorkflowPathResolver.resolve(arrayRoot, "0.1.price"));

        // 越界下标返回 null
        assertNull(WorkflowPathResolver.resolve(root, "items.9.name"));
        // 非数字段不能索引数组
        assertNull(WorkflowPathResolver.resolve(root, "items.name"));
    }

    @Test
    void penetratesJsonTextOutputOnDescent() {
        String textOutput = "{\"code\":\"ok\",\"data\":{\"id\":3}}";
        // 文本型 JSON 输出在继续取值时先自动解析（对应 $output.code / $output.data.id）
        assertEquals("ok", WorkflowPathResolver.resolve(textOutput, "code"));
        assertEquals(3, WorkflowPathResolver.resolve(textOutput, "data.id"));

        // 普通字符串作为根不再继续取值
        assertNull(WorkflowPathResolver.resolve("plain-text", "x"));
        assertEquals("plain-text", WorkflowPathResolver.resolve("plain-text", ""));
    }

    @Test
    void missingKeyOrWrongShapeReturnsNull() {
        JSONObject root = JSON.parseObject("{\"a\":{\"b\":1}}");
        assertNull(WorkflowPathResolver.resolve(root, "a.missing"));
        assertNull(WorkflowPathResolver.resolve(root, "nope"));
        // Map 上使用数字段
        assertNull(WorkflowPathResolver.resolve(root, "3"));
    }

    @Test
    void isPathAcceptsReferencePathsOnly() {
        assertTrue(WorkflowPathResolver.isPath("order.total"));
        assertTrue(WorkflowPathResolver.isPath("a.b.0.c"));
        assertTrue(WorkflowPathResolver.isPath("request"));
        assertFalse(WorkflowPathResolver.isPath("$.order.total"));
        assertFalse(WorkflowPathResolver.isPath("a..b"));
        assertFalse(WorkflowPathResolver.isPath("3"));
        assertFalse(WorkflowPathResolver.isPath(null));
    }

    @Test
    void writeCreatesNestedTreeAndOverwritesLeaf() {
        JSONObject root = new JSONObject();
        WorkflowPathResolver.write(root, "result.order.total", 100);
        WorkflowPathResolver.write(root, "result.order.id", "A1");
        WorkflowPathResolver.write(root, "result.flag", true);
        assertEquals(100, WorkflowPathResolver.resolve(root, "result.order.total"));
        assertEquals("A1", WorkflowPathResolver.resolve(root, "result.order.id"));
        assertEquals(true, WorkflowPathResolver.resolve(root, "result.flag"));
        // 重复写同一叶子：末段直接覆盖
        WorkflowPathResolver.write(root, "result.order.total", 120);
        assertEquals(120, WorkflowPathResolver.resolve(root, "result.order.total"));
        // $ 前缀按引用路径同样剥除
        Map<String, Object> prefixed = new HashMap<String, Object>();
        WorkflowPathResolver.write(prefixed, "$.a.b", "x");
        assertEquals("x", WorkflowPathResolver.resolve(prefixed, "a.b"));
    }

    @Test
    void writeIntoExistingObjectMapMergesWithoutWipingSiblings() {
        JSONObject root = new JSONObject();
        WorkflowPathResolver.write(root, "payload", JSON.parseObject("{\"a\":1,\"b\":{\"c\":2}}"));
        // payload 已是一整个对象，继续下钻写 b.d 只新增而不抹掉 a/c
        WorkflowPathResolver.write(root, "payload.b.d", 3);
        JSONObject payload = root.getJSONObject("payload");
        assertEquals(1, payload.getIntValue("a"));
        assertEquals(2, payload.getJSONObject("b").getIntValue("c"));
        assertEquals(3, payload.getJSONObject("b").getIntValue("d"));
    }

    @Test
    void writeConflictingScalarIntermediateThrows() {
        JSONObject root = new JSONObject();
        WorkflowPathResolver.write(root, "order", "REFUND"); // 先写为标量
        assertThrows(IllegalArgumentException.class, () -> WorkflowPathResolver.write(root, "order.total", 100));
    }
}
