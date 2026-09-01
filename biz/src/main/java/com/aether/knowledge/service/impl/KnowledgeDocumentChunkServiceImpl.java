package com.aether.knowledge.service.impl;

import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.mapper.KnowledgeDocumentChunkMapper;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档分块 Service 实现。
 */
@Service
public class KnowledgeDocumentChunkServiceImpl
        extends ServiceImpl<KnowledgeDocumentChunkMapper, KnowledgeDocumentChunk>
        implements KnowledgeDocumentChunkService {

    /**
     * 处理searchSimilarChunks。
     */
    @Override
    public List<KnowledgeDocumentChunk> searchSimilarChunks(List<String> knowledgeBaseIds, String embedding, int limit) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || StringUtils.isBlank(embedding) || limit <= 0) {
            return Collections.emptyList();
        }
        List<String> filteredIds = knowledgeBaseIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (filteredIds.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectSimilarChunks(filteredIds, embedding, limit, currentTenantId());
    }

    /**
     * 处理searchLexicalChunks。
     */
    @Override
    public List<KnowledgeDocumentChunk> searchLexicalChunks(List<String> knowledgeBaseIds, String query, int limit) {
        if (knowledgeBaseIds == null || knowledgeBaseIds.isEmpty() || StringUtils.isBlank(query) || limit <= 0) {
            return Collections.emptyList();
        }
        List<String> filteredIds = knowledgeBaseIds.stream()
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.toList());
        if (filteredIds.isEmpty()) {
            return Collections.emptyList();
        }
        return baseMapper.selectLexicalChunks(filteredIds, query.trim(), limit, currentTenantId());
    }

    /**
     * 查找NeighborChunks。
     */
    @Override
    public List<KnowledgeDocumentChunk> findNeighborChunks(String documentVersionId, int chunkIndex, int radius) {
        if (StringUtils.isBlank(documentVersionId) || chunkIndex < 0 || radius <= 0) {
            return Collections.emptyList();
        }
        return baseMapper.selectNeighborChunks(documentVersionId,
                Math.max(0, chunkIndex - radius), chunkIndex + radius, currentTenantId());
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    /**
     * 保存VectorChunk。
     */
    @Override
    public boolean saveVectorChunk(KnowledgeDocumentChunk chunk) {
        if (chunk == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (StringUtils.isBlank(chunk.getId())) {
            chunk.setId(IdWorker.getIdStr());
        }
        if (chunk.getCreatedAt() == null) {
            chunk.setCreatedAt(now);
        }
        if (chunk.getUpdatedAt() == null) {
            chunk.setUpdatedAt(now);
        }
        if (chunk.getSortNum() == null) {
            chunk.setSortNum(1);
        }
        if (chunk.getDeleted() == null) {
            chunk.setDeleted(false);
        }
        if (chunk.getState() == null) {
            chunk.setState(0);
        }
        if (chunk.getReferenceCount() == null) {
            // 新分块没有被回答引用过；lastReferencedAt 保持 null 直到首次真实引用。
            chunk.setReferenceCount(0L);
        }
        return baseMapper.insertVectorChunk(chunk) > 0;
    }
}
