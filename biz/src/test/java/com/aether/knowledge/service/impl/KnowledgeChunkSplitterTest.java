package com.aether.knowledge.service.impl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeChunkSplitterTest {

    @Test
    void preservesMarkdownHeadingHierarchy() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(120, 20);
        String content = "前言内容。\n\n# 安装\n安装说明。\n\n## Windows\nWindows 操作步骤。\n\n## Linux\nLinux 操作步骤。";

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split(content);

        assertEquals(4, chunks.size());
        assertEquals("ROOT", chunks.get(0).getSectionPath());
        assertEquals("安装", chunks.get(1).getSectionPath());
        assertEquals("安装 > Windows", chunks.get(2).getSectionPath());
        assertEquals("安装 > Linux", chunks.get(3).getSectionPath());
    }

    @Test
    void usesSemanticBoundariesAndNeverExceedsLimit() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(60, 15);
        String content = "# 使用说明\n第一段介绍系统用途，并说明适用范围。\n\n"
                + "第二段包含较多内容，用于验证分块达到限制时优先在段落边界切开。\n\n"
                + "第三段说明最后的注意事项。";

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split(content);

        assertTrue(chunks.size() >= 2);
        for (KnowledgeChunkSplitter.Segment chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 60);
            assertEquals("使用说明", chunk.getSectionPath());
            assertFalse(chunk.getContent().startsWith("，"));
        }
    }

    @Test
    void splitsOversizedSentenceWithoutLosingContent() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(30, 5);
        String source = "这是一个没有任何标点并且长度明显超过单个知识库分块上限的连续中文文本用于测试硬切分能力";

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split(source);

        assertTrue(chunks.size() > 1);
        StringBuilder rebuilt = new StringBuilder();
        for (KnowledgeChunkSplitter.Segment chunk : chunks) {
            assertTrue(chunk.getContent().length() <= 30);
            rebuilt.append(chunk.getContent());
        }
        assertEquals(source, rebuilt.toString());
    }

    @Test
    void estimatesChineseTokensMoreRealisticallyThanAscii() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter();

        assertEquals(9, splitter.estimateTokens("知识库支持中文问答"));
        assertEquals(2, splitter.estimateTokens("abcdefgh"));
        assertEquals(0, splitter.estimateTokens("  "));
    }
}
