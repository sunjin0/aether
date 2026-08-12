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
    void keepsHeadingOnlyParentWithItsFirstChildContent() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(200, 0);
        String content = "## 目的与治理原则\n\n### 可追溯的业务目的\n每个 AI 场景必须明确业务所有者、输入数据范围和人工接管路径。\n\n### 禁止的自动化决定\n最终决定必须由具备授权的人作出。";

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split(content);

        assertEquals(2, chunks.size());
        assertEquals("目的与治理原则 > 可追溯的业务目的", chunks.get(0).getSectionPath());
        assertTrue(chunks.get(0).getContent().startsWith("## 目的与治理原则\n### 可追溯的业务目的"));
        assertTrue(chunks.get(0).getContent().contains("每个 AI 场景必须明确"));
        assertEquals("目的与治理原则 > 禁止的自动化决定", chunks.get(1).getSectionPath());
        assertTrue(chunks.get(1).getContent().startsWith("## 目的与治理原则\n### 禁止的自动化决定"));
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

    @Test
    void respectsTokenBudgetBeforeCharacterLimit() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(1000, 0, 4);

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split("alpha beta\n\ngamma delta\n\nepsilon zeta");

        assertEquals(3, chunks.size());
        for (KnowledgeChunkSplitter.Segment chunk : chunks) {
            assertTrue(splitter.estimateTokens(chunk.getContent()) <= 4);
        }
    }

    @Test
    void keepsTableHeaderWithEverySplitTableChunk() {
        KnowledgeChunkSplitter splitter = new KnowledgeChunkSplitter(70, 0);
        String table = "| 名称 | 说明 |\n| --- | --- |\n| Alpha | 第一条规则 |\n| Beta | 第二条规则 |\n| Gamma | 第三条规则 |\n| Delta | 第四条规则 |";

        List<KnowledgeChunkSplitter.Segment> chunks = splitter.split(table);

        assertTrue(chunks.size() > 1);
        for (KnowledgeChunkSplitter.Segment chunk : chunks) {
            assertTrue(chunk.getContent().startsWith("| 名称 | 说明 |\n| --- | --- |"));
        }
    }
}
