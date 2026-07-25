package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeBase;

import java.util.List;
/**
 * 知识库访问服务
 *
 * @author Aether
 */
public interface KnowledgeAccessService {
    /**
     * 获取当前管理员的ID
     *
     * @return 管理员ID
     */
    String currentAdminId();

    /**
     * 获取当前可读的知识库ID列表
     *
     * @return 可读的知识库ID列表
     */
    List<String> readableKnowledgeBaseIds();

    /**
     * 获取可读的知识库
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库
     */
    KnowledgeBase requireReadable(String knowledgeBaseId);

    /**
     * 获取可写的知识库
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库
     */
    KnowledgeBase requireWritable(String knowledgeBaseId);

    /**
     * 获取可提交的知识库
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库
     */
    KnowledgeBase requireSubmittable(String knowledgeBaseId);

    /**
     * 获取可审批的知识库
     *
     * @param knowledgeBaseId 知识库ID
     * @return 知识库
     */
    KnowledgeBase requireApprovable(String knowledgeBaseId);

}
