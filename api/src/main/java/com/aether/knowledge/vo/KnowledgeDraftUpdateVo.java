package com.aether.knowledge.vo;

import lombok.Data;

/**
 * 表示知识库Draft更新VO。
 */
@Data
public class KnowledgeDraftUpdateVo {
    private String content;
    private String expectedChecksum;
}
