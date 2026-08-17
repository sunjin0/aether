package com.aether.knowledge.service;

import com.aether.knowledge.entity.KnowledgeIndexJob;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 定义知识库索引Job业务服务契约。
 */
public interface KnowledgeIndexJobService extends IService<KnowledgeIndexJob> {
    /**
     * 处理claimPending。
     */
    boolean claimPending(String jobId, long startedAt);
}
