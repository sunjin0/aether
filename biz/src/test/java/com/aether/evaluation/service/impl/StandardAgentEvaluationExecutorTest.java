package com.aether.evaluation.service.impl;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StandardAgentEvaluationExecutorTest {
    @Test
    void readsOnlyKnowledgeBaseIdsCapturedByTheSnapshot() throws Exception {
        StandardAgentEvaluationExecutor executor = new StandardAgentEvaluationExecutor(null, null, null, null, null, null);
        Method method = StandardAgentEvaluationExecutor.class.getDeclaredMethod("frozenKnowledgeBaseIds", JSONObject.class);
        method.setAccessible(true);

        @SuppressWarnings("unchecked")
        Set<String> ids = (Set<String>) method.invoke(executor, JSONObject.parseObject("""
                {"knowledgeBases":[{"knowledgeBaseId":"kb-2"},{"knowledgeBaseId":"kb-1"},{"knowledgeBaseId":"kb-1"},{"other":"ignored"}]}
                """));

        assertEquals(Set.of("kb-1", "kb-2"), ids);
    }
}
