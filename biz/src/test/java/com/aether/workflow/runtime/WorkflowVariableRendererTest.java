package com.aether.workflow.runtime;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nService;
import com.aether.i18n.I18nUtils;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 模板 ${路径} 渲染（提示词/HTTP/通知/工具参数等共用）。
 */
class WorkflowVariableRendererTest {

    @BeforeAll
    static void setUpI18n() {
        I18nService i18nService = mock(I18nService.class);
        when(i18nService.getMessage(any(String.class), any(Object[].class))).thenAnswer(invocation -> invocation.getArgument(0));
        new I18nUtils(i18nService);
    }

    @Test
    void rendersSimpleAndNestedReferences() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("request", "refund");
        assertEquals("refund", WorkflowVariableRenderer.render("${request}", variables));
        assertEquals("请处理：refund", WorkflowVariableRenderer.render("请处理：${request}", variables));

        JSONObject order = new JSONObject();
        order.put("total", 100);
        order.put("items", JSON.parseArray("[{\"price\":7},{\"price\":9}]"));
        Map<String, Object> withOrder = new HashMap<String, Object>();
        withOrder.put("order", order);
        assertEquals("7", WorkflowVariableRenderer.render("${order.items.0.price}", withOrder));
        assertEquals("￥7/件", WorkflowVariableRenderer.render("￥${order.items.0.price}/件", withOrder));
    }

    @Test
    void leavesTextWithoutReferencesUntouched() {
        assertEquals("plain text", WorkflowVariableRenderer.render("plain text", new HashMap<String, Object>()));
        assertNull(WorkflowVariableRenderer.render(null, new HashMap<String, Object>()));
    }

    @Test
    void missingRootVariableThrowsAndDeepMissingRendersNull() {
        assertThrows(ServerException.class,
                () -> WorkflowVariableRenderer.render("${missing}", new HashMap<String, Object>()));

        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("order", new JSONObject());
        assertEquals("null", WorkflowVariableRenderer.render("${order.total}", variables));
    }
}
