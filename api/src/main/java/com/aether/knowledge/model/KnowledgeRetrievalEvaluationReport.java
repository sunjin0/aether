package com.aether.knowledge.model;

import java.util.ArrayList;
import java.util.List;

/** Aggregate and per-query retrieval metrics. */
public class KnowledgeRetrievalEvaluationReport {
    private int total;
    private double recallAtK;
    private double mrr;
    private double ndcg;
    private List<Item> items = new ArrayList<>();
    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }
    public double getRecallAtK() { return recallAtK; }
    public void setRecallAtK(double recallAtK) { this.recallAtK = recallAtK; }
    public double getMrr() { return mrr; }
    public void setMrr(double mrr) { this.mrr = mrr; }
    public double getNdcg() { return ndcg; }
    public void setNdcg(double ndcg) { this.ndcg = ndcg; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public static class Item {
        private String question;
        private List<String> retrievedChunkIds;
        private double recallAtK;
        private double mrr;
        private double ndcg;
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
    }
}
