package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.knowledge.vo.KnowledgeBaseVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
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
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "Agent知识库 API")
@Validated
@RestController
@Permission(path = "/knowledge/base")
@RequestMapping("/api/knowledge/base")
public class KnowledgeBaseController {

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @ApiOperation("知识库列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeBaseVo>> list(@RequestBody KnowledgeBaseVo vo) {
        Page<KnowledgeBase> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<KnowledgeBase> wrapper = Wrappers.lambdaQuery(KnowledgeBase.class)
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
        KnowledgeBase kb = getExisting(id);
        KnowledgeBaseVo vo = new KnowledgeBaseVo();
        BeanUtils.copyProperties(kb, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody KnowledgeBaseVo vo) {
        KnowledgeBase kb = new KnowledgeBase();
        BeanUtils.copyProperties(vo, kb);
        if (kb.getStatus() == null) {
            kb.setStatus(1);
        }
        if (kb.getIndexStatus() == null) {
            kb.setIndexStatus(0);
        }
        if (StringUtils.isBlank(kb.getScope())) {
            kb.setScope("PLATFORM");
        }
        boolean saved = knowledgeBaseService.save(kb);
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), kb.getId());
    }

    @ApiOperation("编辑知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody KnowledgeBaseVo vo) {
        getExisting(id);
        KnowledgeBase kb = new KnowledgeBase();
        BeanUtils.copyProperties(vo, kb);
        kb.setId(id);
        boolean updated = knowledgeBaseService.updateById(kb);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除知识库")
    @Permission(path = "/knowledge/base", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = knowledgeBaseService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    private KnowledgeBase getExisting(String id) {
        KnowledgeBase kb = knowledgeBaseService.getById(id);
        if (kb == null || Boolean.TRUE.equals(kb.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        return kb;
    }
}
