package com.aether.knowledge.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
/** 知识库访问范围校验服务，统一处理当前用户和知识库状态。 */
public class KnowledgeAccessServiceImpl implements KnowledgeAccessService {
    private final KnowledgeBaseService knowledgeBaseService;
    public KnowledgeAccessServiceImpl(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    /** 获取当前登录管理员 ID，未登录时抛出认证异常。 */
    public String currentAdminId() {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) {
            throw new ServerException(401, I18nUtils.getMessage("knowledge.current-admin.required"));
        }
        return userId;
    }

    @Override
    /** 返回当前用户可读取的未删除知识库 ID。 */
    public List<String> readableKnowledgeBaseIds() {
        currentAdminId();
        return knowledgeBaseService.list(Wrappers.lambdaQuery(KnowledgeBase.class)
                .eq(KnowledgeBase::getDeleted, false))
                .stream()
                .map(KnowledgeBase::getId)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    /** 校验知识库存在且处于可用状态。 */
    public KnowledgeBase requireReadable(String knowledgeBaseId) {
        KnowledgeBase base = getActive(knowledgeBaseId);
        return base;
    }

    @Override
    /** 校验知识库可写；具体角色权限由接口权限切面负责。 */
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

    /** 查询并校验知识库基础状态。 */
    private KnowledgeBase getActive(String id) {
        KnowledgeBase base = knowledgeBaseService.getById(id);
        if (base == null || Boolean.TRUE.equals(base.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("knowledge.base.not-found"));
        }
        return base;
    }

}
