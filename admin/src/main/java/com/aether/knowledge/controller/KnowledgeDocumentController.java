package com.aether.knowledge.controller;

import com.aether.knowledge.entity.KnowledgeDocument;
import com.aether.knowledge.entity.KnowledgeDocumentChunk;
import com.aether.knowledge.service.KnowledgeDocumentChunkService;
import com.aether.knowledge.service.KnowledgeDocumentIndexService;
import com.aether.knowledge.service.KnowledgeDocumentService;
import com.aether.knowledge.vo.KnowledgeDocumentVo;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

@Api(tags = "Agent知识库文档 API")
@Validated
@RestController
@Permission(path = "/knowledge/document")
@RequestMapping("/api/knowledge/document")
public class KnowledgeDocumentController {

    private final KnowledgeDocumentService knowledgeDocumentService;
    private final KnowledgeDocumentChunkService knowledgeDocumentChunkService;
    private final KnowledgeDocumentIndexService knowledgeDocumentIndexService;

    public KnowledgeDocumentController(KnowledgeDocumentService knowledgeDocumentService,
                                   KnowledgeDocumentChunkService knowledgeDocumentChunkService,
                                   KnowledgeDocumentIndexService knowledgeDocumentIndexService) {
        this.knowledgeDocumentService = knowledgeDocumentService;
        this.knowledgeDocumentChunkService = knowledgeDocumentChunkService;
        this.knowledgeDocumentIndexService = knowledgeDocumentIndexService;
    }

    @ApiOperation("文档列表")
    @PostMapping("/list")
    public WebResponse<List<KnowledgeDocumentVo>> list(@RequestBody KnowledgeDocumentVo vo) {
        Page<KnowledgeDocument> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<KnowledgeDocument> wrapper = Wrappers.lambdaQuery(KnowledgeDocument.class)
                .eq(StringUtils.isNotBlank(vo.getKnowledgeBaseId()), KnowledgeDocument::getKnowledgeBaseId, vo.getKnowledgeBaseId())
                .like(StringUtils.isNotBlank(vo.getTitle()), KnowledgeDocument::getTitle, vo.getTitle())
                .eq(vo.getStatus() != null, KnowledgeDocument::getStatus, vo.getStatus())
                .eq(KnowledgeDocument::getDeleted, false)
                .orderByDesc(KnowledgeDocument::getCreatedAt);
        Page<KnowledgeDocument> result = knowledgeDocumentService.page(page, wrapper);
        List<KnowledgeDocumentVo> list = result.getRecords().stream().map(item -> {
            KnowledgeDocumentVo itemVo = new KnowledgeDocumentVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("文档详情")
    @GetMapping("/{id}")
    public WebResponse<KnowledgeDocumentVo> detail(@PathVariable @NotBlank String id) {
        KnowledgeDocument document = getExisting(id);
        KnowledgeDocumentVo vo = new KnowledgeDocumentVo();
        BeanUtils.copyProperties(document, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增文档并同步索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody KnowledgeDocumentVo vo) {
        KnowledgeDocument document = new KnowledgeDocument();
        BeanUtils.copyProperties(vo, document);
        if (document.getStatus() == null) {
            document.setStatus(0);
        }
        boolean saved = knowledgeDocumentService.save(document);
        if (saved) {
            knowledgeDocumentIndexService.reindex(document);
        }
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), document.getId());
    }

    @ApiOperation("编辑文档并同步重建索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody KnowledgeDocumentVo vo) {
        getExisting(id);
        KnowledgeDocument document = new KnowledgeDocument();
        BeanUtils.copyProperties(vo, document);
        document.setId(id);
        boolean updated = knowledgeDocumentService.updateById(document);
        if (updated) {
            knowledgeDocumentIndexService.reindex(knowledgeDocumentService.getById(id));
        }
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除文档")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = knowledgeDocumentService.removeById(id);
        if (removed) {
            knowledgeDocumentChunkService.remove(Wrappers.lambdaUpdate(KnowledgeDocumentChunk.class)
                    .eq(KnowledgeDocumentChunk::getDocumentId, id));
        }
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("重建文档索引")
    @Permission(path = "/knowledge/document", type = Permission.Type.Write)
    @PostMapping("/{id}/reindex")
    public WebResponse<Void> reindex(@PathVariable @NotBlank String id) {
        knowledgeDocumentIndexService.reindex(getExisting(id));
        return WebResponse.OK(I18nUtils.getMessage("update.success"));
    }

    private KnowledgeDocument getExisting(String id) {
        KnowledgeDocument document = knowledgeDocumentService.getById(id);
        if (document == null || Boolean.TRUE.equals(document.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        return document;
    }
}
