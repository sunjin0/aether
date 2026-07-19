package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class KnowledgeAccessServiceImpl implements KnowledgeAccessService {
    private final KnowledgeBaseService knowledgeBaseService;
    public KnowledgeAccessServiceImpl(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public String currentAdminId() {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, "current administrator is required");
        }
        return userId;
    }

    @Override
    public List<String> readableKnowledgeBaseIds() {
        currentAdminId();
        return knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                .eq(KnowledgeBase::getDeleted, false))
                .stream()
                .map(KnowledgeBase::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public KnowledgeBase requireReadable(String knowledgeBaseId) {
        KnowledgeBase base = getActive(knowledgeBaseId);
        return base;
    }

    @Override
    public KnowledgeBase requireWritable(String knowledgeBaseId) {
        KnowledgeBase base = getActive(knowledgeBaseId);
        return base;
    }

    @Override
    public KnowledgeBase requireSubmittable(String knowledgeBaseId) {
        return requireWritable(knowledgeBaseId);
    }

    @Override
    public KnowledgeBase requireApprovable(String knowledgeBaseId) {
        return getActive(knowledgeBaseId);
    }

    private KnowledgeBase getActive(String id) {
        KnowledgeBase base = knowledgeBaseService.getById(id);
        if (base == null || Boolean.TRUE.equals(base.getDeleted())) {
            throw new ServerException(404, "knowledge base not found");
        }
        return base;
    }

}
