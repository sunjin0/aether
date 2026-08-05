package com.aether.knowledge.model;

import java.util.ArrayList;
import java.util.List;

/** 检索评测总体指标及逐题结果。 */
public class KnowledgeRetrievalEvaluationReport {
    /** 有效评测问题数量。 */
    private int total;
    /** 总体 Recall@K。 */
    private double recallAtK;
    /** 总体 MRR。 */
    private double mrr;
    /** 总体 nDCG。 */
    private double ndcg;
    /** 检索调用发生异常的问题数量，不计入聚合指标。 */
    private int failedCount;
    /** 每条问题的详细指标和召回分块。 */
    private List<Item> items = new ArrayList<>();
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public double getRecallAtK() { return recallAtK; }
    public void setRecallAtK(double recallAtK) { this.recallAtK = recallAtK; }
    public double getMrr() { return mrr; }
    public void setMrr(double mrr) { this.mrr = mrr; }
    public double getNdcg() { return ndcg; }
    public void setNdcg(double ndcg) { this.ndcg = ndcg; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public static class Item {
        /** 被评测的问题文本。 */
        private String question;
        /** 实际召回的 chunk ID，顺序即召回名次。 */
        private List<String> retrievedChunkIds;
        private double recallAtK;
        private double mrr;
        private double ndcg;
        private String status;
        private String errorCode;
        private String errorMessage;
        public String getQuestion() { return question; }
        public void setQuestion(String question) { this.question = question; }
        public List<String> getRetrievedChunkIds() { return retrievedChunkIds; }
        public void setRetrievedChunkIds(List<String> retrievedChunkIds) { this.retrievedChunkIds = retrievedChunkIds; }
        public double getRecallAtK() { return recallAtK; }
        public void setRecallAtK(double recallAtK) { this.recallAtK = recallAtK; }
        public double getMrr() { return mrr; }
        public void setMrr(double mrr) { this.mrr = mrr; }
        public double getNdcg() { return ndcg; }
        public void setNdcg(double ndcg) { this.ndcg = ndcg; }
        public String getStatus() { return status; }
        public void setStatus(String status) { this.status = status; }
        public String getErrorCode() { return errorCode; }
        public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    }
}
