package com.aether.knowledge.model;

import java.util.List;

/** A labelled query used to measure retrieval quality. */
public class KnowledgeRetrievalEvaluationCase {
    private String question;
    private List<String> expectedChunkIds;
    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }
    public List<String> getExpectedChunkIds() { return expectedChunkIds; }
    public void setExpectedChunkIds(List<String> expectedChunkIds) { this.expectedChunkIds = expectedChunkIds; }
}
