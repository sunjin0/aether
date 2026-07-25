package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.entity.ModelProvider;
import com.aether.knowledge.vo.KnowledgeBaseVo;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.exception.ServerException;
import com.aether.knowledge.model.KnowledgeBaseScope;
import com.aether.knowledge.model.KnowledgeBaseVisibility;
import com.alibaba.fastjson2.JSONObject;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "Agent知识库 API")
@Validated
@RestController
@Permission(path = "/knowledge/base")
@RequestMapping("/api/knowledge/base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessService knowledgeAccessService;
    private final ModelProviderService modelProviderService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeAccessService knowledgeAccessService,
                                   ModelProviderService modelProviderService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeAccessService = knowledgeAccessService;
        this.modelProviderService = modelProviderService;
    }

    @ApiOperation("知识库列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeBaseVo>> list(@RequestBody KnowledgeBaseVo vo) {
        Page<KnowledgeBase> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        List<String> readableIds = knowledgeAccessService.readableKnowledgeBaseIds();
        if (readableIds.isEmpty()) {
            return WebResponse.Page(Collections.emptyList(), 0L);
        }
        Wrapper<KnowledgeBase> wrapper = Wrappers.lambdaQuery(KnowledgeBase.class)
                .in(KnowledgeBase::getId, readableIds)
                .eq(StringUtils.isNotBlank(vo.getScope()), KnowledgeBase::getScope, vo.getScope())
                .eq(StringUtils.isNotBlank(vo.getEmbeddingProviderId()), KnowledgeBase::getEmbeddingProviderId, vo.getEmbeddingProviderId())
                .like(StringUtils.isNotBlank(vo.getName()), KnowledgeBase::getName, vo.getName())
                .eq(vo.getStatus() != null, KnowledgeBase::getStatus, vo.getStatus())
                .eq(vo.getIndexStatus() != null, KnowledgeBase::getIndexStatus, vo.getIndexStatus())
                .eq(KnowledgeBase::getDeleted, false)
                .orderByDesc(KnowledgeBase::getCreatedAt);
        Page<KnowledgeBase> result = knowledgeBaseService.page(page, wrapper);
        List<KnowledgeBaseVo> list = result.getRecords().stream().map(item -> {
            KnowledgeBaseVo itemVo = new KnowledgeBaseVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("知识库详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeBaseVo> detail(@PathVariable @NotBlank String id) {
        KnowledgeBase kb = knowledgeAccessService.requireReadable(id);
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        BeanUtils.copyProperties(kb, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody KnowledgeBaseVo vo) {
        KnowledgeBase kb = mutableFields(vo);
        kb.setReviewConfig(validateReviewConfig(vo.getReviewConfig()));
        kb.setOwnerAdminId(knowledgeAccessService.currentAdminId());
        if (kb.getStatus() == null) {
            kb.setStatus(1);
        }
        if (kb.getIndexStatus() == null) {
            kb.setIndexStatus(0);
        }
        if (StringUtils.isBlank(kb.getScope())) {
            kb.setScope(KnowledgeBaseScope.PLATFORM);
        }
        if (StringUtils.isBlank(kb.getVisibility())) {
            kb.setVisibility(KnowledgeBaseScope.PLATFORM.equalsIgnoreCase(kb.getScope()) ? KnowledgeBaseVisibility.PLATFORM : KnowledgeBaseVisibility.PRIVATE);
        }
        boolean saved = knowledgeBaseService.save(kb);
        return WebResponse.OK(saved ? I18nUtils.getMessage("knowledge.base.create.success") : I18nUtils.getMessage("knowledge.base.create.fail"), kb.getId());
    }

    @ApiOperation("编辑知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody KnowledgeBaseVo vo) {
        KnowledgeBase existing = knowledgeAccessService.requireWritable(id);
        KnowledgeBase kb = mutableFields(vo);
        if (vo.getReviewConfig() != null) {
            kb.setReviewConfig(validateReviewConfig(vo.getReviewConfig()));
        }
        kb.setId(id);
        // Ownership can only be changed through an explicit membership/transfer workflow.
        kb.setOwnerAdminId(existing.getOwnerAdminId());
        boolean updated = knowledgeBaseService.updateById(kb);
        return WebResponse.OK(updated ? I18nUtils.getMessage("knowledge.base.update.success") : I18nUtils.getMessage("knowledge.base.update.fail"));
    }

    @ApiOperation("删除知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        knowledgeAccessService.requireWritable(id);
        boolean removed = knowledgeBaseService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("knowledge.base.delete.success") : I18nUtils.getMessage("knowledge.base.delete.fail"));
    }

    private KnowledgeBase mutableFields(KnowledgeBaseVo vo) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setScope(vo.getScope());
        kb.setEmbeddingProviderId(vo.getEmbeddingProviderId());
        kb.setVisibility(vo.getVisibility());
        kb.setRetrievalConfig(vo.getRetrievalConfig());
        kb.setReviewConfig(vo.getReviewConfig());
        kb.setName(vo.getName());
        kb.setDescription(vo.getDescription());
        kb.setStatus(vo.getStatus());
        return kb;
    }

    private String validateReviewConfig(String value) {
        if (StringUtils.isBlank(value)) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.base.review-config.required"));
        }
        try {
            JSONObject config = JSONObject.parseObject(value);
            String providerId = config.getString("reviewModelProviderId");
            if (StringUtils.isBlank(providerId)) {
                throw new ServerException(400, I18nUtils.getMessage("knowledge.base.review-provider.required"));
            }
            ModelProvider provider = modelProviderService.getById(providerId);
            if (provider == null || Boolean.TRUE.equals(provider.getDeleted()) || provider.getStatus() == null
                    || provider.getStatus() != 1 || StringUtils.isBlank(provider.getDefaultModel())
                    || StringUtils.containsIgnoreCase(provider.getDefaultModel(), "embedding")) {
                throw new ServerException(400, I18nUtils.getMessage("knowledge.base.review-provider.invalid"));
            }
            if (StringUtils.isBlank(config.getString("reviewModel"))) {
                config.put("reviewModel", provider.getDefaultModel());
            }
            putDefault(config, "autoAiReview", true);
            putDefault(config, "aiReviewRequired", true);
            putDefault(config, "blockOnCriticalIssues", true);
            putDefault(config, "requireDifferentApprover", true);
            return config.toJSONString();
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.base.review-config.invalid"));
        }
    }

    private void putDefault(JSONObject config, String key, boolean value) {
        if (!config.containsKey(key)) config.put(key, value);
    }

}
