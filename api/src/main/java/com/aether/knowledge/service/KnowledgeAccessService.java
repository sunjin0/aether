package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeBase;

import java.util.List;

/** Centralized owner/member/visibility checks for knowledge-base resources. */
public interface KnowledgeAccessService {
    String currentAdminId();

    List<String> readableKnowledgeBaseIds();

    KnowledgeBase requireReadable(String knowledgeBaseId);

    KnowledgeBase requireWritable(String knowledgeBaseId);

    KnowledgeBase requireSubmittable(String knowledgeBaseId);

    KnowledgeBase requireApprovable(String knowledgeBaseId);

}
