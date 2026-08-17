package com.aether.knowledge.client;

import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.service.DelegationTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DoclingClientTest {

    @Test
    void appendsEmbeddedImageChunksToMarkdownForKnowledgeIndexing() {
        DoclingClient client = new DoclingClient("", tokenService());
        Map<String, Object> imageChunk = new LinkedHashMap<>();
        imageChunk.put("page", 3);
        imageChunk.put("text", "柱状图显示第三季度销售额最高。");
        List<Map<String, Object>> imageChunks = new ArrayList<>();
        imageChunks.add(imageChunk);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("image_chunks", imageChunks);

        String result = (String) ReflectionTestUtils.invokeMethod(
                client, "appendImageChunks", "# 季度报告", response);

        assertEquals("# 季度报告\n\n## 文档内嵌图片语义（第 3 页）\n\n柱状图显示第三季度销售额最高。", result);
    }

    @Test
    void keepsMarkdownUnchangedWhenMcpReturnsNoImageChunks() {
        DoclingClient client = new DoclingClient("", tokenService());

        String result = (String) ReflectionTestUtils.invokeMethod(
                client, "appendImageChunks", "# 季度报告", new LinkedHashMap<String, Object>());

        assertEquals("# 季度报告", result);
    }

    private DelegationTokenService tokenService() {
        DeepAgentConfig config = new DeepAgentConfig();
        config.setMcpDelegationSecret("test-delegation-secret");
        return new DelegationTokenService(config);
    }
}
