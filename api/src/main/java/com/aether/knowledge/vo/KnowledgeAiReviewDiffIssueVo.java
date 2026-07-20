package com.aether.knowledge.vo;

import com.alibaba.fastjson2.JSONObject;
import lombok.Data;

@Data
public class KnowledgeAiReviewDiffIssueVo {
    private String id;
    private String blockId;
    private String issueType;
    private String severity;
    private String message;
    private String originalExcerpt;
    private JSONObject suggestedPatch;
    private String handleStatus;
    private Integer baseStartLine;
    private Integer baseEndLine;
    private Integer proposedStartLine;
    private Integer proposedEndLine;
}
