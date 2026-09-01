package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.service.KnowledgeAccessService;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.entity.ModelProvider;
import com.aether.sys.entity.User;
import com.aether.sys.service.UserService;
import com.aether.knowledge.vo.KnowledgeBaseVo;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.i18n.I18nUtils;
import com.aether.exception.ServerException;
import com.aether.knowledge.model.KnowledgeBaseScope;
import com.aether.knowledge.model.KnowledgeBaseVisibility;
import com.alibaba.fastjson2.JSONObject;
import com.aether.permission.Permission;
import com.aether.local.CurrentUser;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.annotations.ApiOperation;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供知识库Base相关的 REST 接口。
 */
@Api(tags = "Agent知识库 API")
@Validated
@RestController
@Permission(path = "/knowledge/base")
@RequestMapping("/api/knowledge/base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessService knowledgeAccessService;
    private final ModelProviderService modelProviderService;
    private final ModelCatalogService modelCatalogService;
    private final UserService userService;

    /**
     * 创建 {@code KnowledgeBaseController} 实例。
     */
    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeAccessService knowledgeAccessService,
                                   ModelProviderService modelProviderService,
                                   ModelCatalogService modelCatalogService,
                                   UserService userService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeAccessService = knowledgeAccessService;
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
        this.userService = userService;
    }

    /**
     * 分页查询当前用户可见的知识库。
     */
    @ApiOperation("知识库列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeBaseVo>> list(@RequestBody ListRequest request) {
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        vo.setCurrent(request.getCurrent()); vo.setPageSize(request.getPageSize()); vo.setScope(request.getScope());
        vo.setEmbeddingProviderId(request.getEmbeddingProviderId()); vo.setName(request.getName());
        vo.setStatus(request.getStatus()); vo.setIndexStatus(request.getIndexStatus());
        Page<KnowledgeBase> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        List<String> readableIds = knowledgeAccessService.readableKnowledgeBaseIds();
        if (readableIds.isEmpty()) {
            return WebResponse.Page(Collections.emptyList(), 0L);
        }
        Wrapper<KnowledgeBase> wrapper = Wrappers.lambdaQuery(KnowledgeBase.class)
                .in(KnowledgeBase::getId, readableIds)
                .and(CurrentUser.getUser() != null && StringUtils.isNotBlank(CurrentUser.getUser().get("tenantId")), q ->
                        q.eq(KnowledgeBase::getTenantId, CurrentUser.getUser().get("tenantId"))
                                )
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

    /**
     * 查询当前用户可见的知识库选项。
     * 只返回启用且未删除的数据，indexStatus 可用于筛选已经完成索引的知识库。
     */
    @ApiOperation("知识库下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options(@RequestParam(value = "status", required = false, defaultValue = "1") Integer status,
                                             @RequestParam(value = "indexStatus", required = false) Integer indexStatus) {
        List<String> readableIds = knowledgeAccessService.readableKnowledgeBaseIds();
        if (readableIds.isEmpty()) return WebResponse.OK(Collections.emptyList());
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<KnowledgeBase> optionsQuery = Wrappers.lambdaQuery(KnowledgeBase.class)
                        .in(KnowledgeBase::getId, readableIds)
                        .and(CurrentUser.getUser() != null && StringUtils.isNotBlank(CurrentUser.getUser().get("tenantId")), q ->
                                q.eq(KnowledgeBase::getTenantId, CurrentUser.getUser().get("tenantId"))
                                        )
                        .eq(status != null, KnowledgeBase::getStatus, status)
                        .eq(indexStatus != null, KnowledgeBase::getIndexStatus, indexStatus)
                        .eq(KnowledgeBase::getDeleted, false)
                        .orderByAsc(KnowledgeBase::getName);
        List<Option> options = knowledgeBaseService.list(optionsQuery)
                .stream().map(item -> new Option(item.getName(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    /**
     * 查询知识库详情，并校验当前用户可读权限。
     */
    @ApiOperation("知识库详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeBaseVo> detail(@PathVariable @NotBlank String id) {
        KnowledgeBase kb = knowledgeAccessService.requireReadable(id);
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        BeanUtils.copyProperties(kb, vo);
        return WebResponse.OK(vo);
    }

    /**
     * 创建知识库并校验 AI 审核配置。
     */
    @ApiOperation("新增知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody CreateRequest request) {
        KnowledgeBaseVo vo = writableVo(request);
        KnowledgeBase kb = mutableFields(vo);
        if (CurrentUser.getUser() != null) {
            kb.setTenantId(CurrentUser.getUser().get("tenantId"));
        }
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

    /**
     * 更新知识库配置，保留原有归属管理员。
     */
    @ApiOperation("编辑知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody UpdateRequest request) {
        KnowledgeBaseVo vo = writableVo(request);
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

    /**
     * 软删除知识库。
     */
    @ApiOperation("删除知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        knowledgeAccessService.requireWritable(id);
        boolean removed = knowledgeBaseService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("knowledge.base.delete.success") : I18nUtils.getMessage("knowledge.base.delete.fail"));
    }

    /**
     * 处理mutableFields。
     */
    private KnowledgeBase mutableFields(KnowledgeBaseVo vo) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setScope(vo.getScope());
        kb.setEmbeddingProviderId(vo.getEmbeddingProviderId());
        kb.setEmbeddingModelId(vo.getEmbeddingModelId());
        if (StringUtils.isBlank(kb.getEmbeddingModelId()))
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.required"));
        com.aether.agent.entity.ModelCatalog catalog = modelCatalogService.requireAvailable(kb.getEmbeddingModelId(), "EMBEDDING");
        kb.setEmbeddingProviderId(catalog.getProviderId());
        kb.setVisibility(vo.getVisibility());
        kb.setRetrievalConfig(vo.getRetrievalConfig());
        kb.setReviewConfig(vo.getReviewConfig());
        kb.setName(vo.getName());
        kb.setDescription(vo.getDescription());
        kb.setStatus(vo.getStatus());
        return kb;
    }

    private KnowledgeBaseVo writableVo(BaseRequest request) {
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        vo.setScope(request.getScope()); vo.setEmbeddingModelId(request.getEmbeddingModelId());
        vo.setVisibility(request.getVisibility()); vo.setRetrievalConfig(request.getRetrievalConfig());
        vo.setReviewConfig(request.getReviewConfig()); vo.setName(request.getName());
        vo.setDescription(request.getDescription()); vo.setStatus(request.getStatus());
        return vo;
    }

    @Data
    @ApiModel("知识库列表请求")
    public static class ListRequest {
        @ApiModelProperty(value = "页码", example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", example = "20") private Long pageSize;
        @ApiModelProperty(value = "范围", example = "platform") private String scope;
        @ApiModelProperty(value = "嵌入供应商 ID", example = "provider-001") private String embeddingProviderId;
        @ApiModelProperty(value = "知识库名称关键词", example = "产品文档") private String name;
        @ApiModelProperty(value = "状态：0-禁用，1-启用", example = "1") private Integer status;
        @ApiModelProperty(value = "索引状态", example = "2") private Integer indexStatus;
    }

    @Data
    public static class BaseRequest {
        @ApiModelProperty(value = "范围", required = true, example = "platform") private String scope;
        @ApiModelProperty(value = "嵌入模型 ID", required = true, example = "model-embedding-001") private String embeddingModelId;
        @ApiModelProperty(value = "可见性", example = "platform") private String visibility;
        @ApiModelProperty(value = "检索配置 JSON", example = "{\"topK\":5}") private String retrievalConfig;
        @ApiModelProperty(value = "审核配置 JSON", required = true, example = "{\"reviewModelId\":\"model-chat-001\"}") private String reviewConfig;
        @ApiModelProperty(value = "知识库名称", required = true, example = "产品文档") private String name;
        @ApiModelProperty(value = "描述", example = "产品帮助中心文档") private String description;
        @ApiModelProperty(value = "状态：0-禁用，1-启用", example = "1") private Integer status;
    }

    @Data
    @ApiModel("新建知识库请求")
    public static class CreateRequest extends BaseRequest { }

    @Data
    @ApiModel("更新知识库请求")
    public static class UpdateRequest extends BaseRequest { }

    /**
     * 校验审核配置。
     */
    private String validateReviewConfig(String value) {
        if (StringUtils.isBlank(value)) {
            throw new ServerException(400, I18nUtils.getMessage("knowledge.base.review-config.required"));
        }
        try {
            JSONObject config = JSONObject.parseObject(value);
            String modelId = config.getString("reviewModelId");
            if (StringUtils.isBlank(modelId))
                throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.required"));
            modelCatalogService.requireAvailable(modelId, "CHAT,MULTIMODAL");
            String manualReviewerId = StringUtils.trimToNull(config.getString("manualReviewerId"));
            if (manualReviewerId != null) {
                User reviewer = userService.getById(manualReviewerId);
                if (reviewer == null || Boolean.TRUE.equals(reviewer.getDeleted())) {
                    throw new ServerException(400, I18nUtils.getMessage("knowledge.base.manual-reviewer.invalid"));
                }
                config.put("manualReviewerId", manualReviewerId);
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

    /**
     * 处理putDefault。
     */
    private void putDefault(JSONObject config, String key, boolean value) {
        if (!config.containsKey(key)) config.put(key, value);
    }

}
