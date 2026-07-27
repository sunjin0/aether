package com.aether.agent.controller;

import com.aether.agent.dto.ModelProviderDto;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.vo.ModelProviderVo;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.utils.AesUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 模型供应商管理 Controller
 */
@Api(tags = "模型供应商管理 API")
@Validated
@RestController
@Permission(path = "/agent/model-provider")
@RequestMapping("/api/agent/model-provider")
public class ModelProviderController {

    private final ModelProviderService modelProviderService;

    @Autowired
    public ModelProviderController(ModelProviderService modelProviderService) {
        this.modelProviderService = modelProviderService;
    }

    @ApiOperation("模型供应商列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<ModelProviderVo>> list(@RequestBody ModelProviderVo vo) {
        Page<ModelProvider> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<ModelProvider> wrapper = Wrappers.lambdaQuery(ModelProvider.class)
                .like(StringUtils.isNotBlank(vo.getName()), ModelProvider::getName, vo.getName())
                .eq(StringUtils.isNotBlank(vo.getType()), ModelProvider::getType, vo.getType())
                .eq(vo.getStatus() != null, ModelProvider::getStatus, vo.getStatus())
                .eq(ModelProvider::getDeleted, false)
                .orderByDesc(ModelProvider::getCreatedAt);
        Page<ModelProvider> result = modelProviderService.page(page, wrapper);
        List<ModelProviderVo> list = result.getRecords().stream().map(item -> {
            ModelProviderVo itemVo = new ModelProviderVo();
            BeanUtils.copyProperties(item, itemVo);
            itemVo.setApiKey(null);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 查询启用的模型供应商选项。
     * excludeEmbedding=true 用于过滤 Embedding 专用供应商，避免被聊天/审查模型误选。
     */
    @ApiOperation("模型供应商下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options(@RequestParam(value = "type", required = false) String type,
                                             @RequestParam(value = "excludeEmbedding", required = false, defaultValue = "false") boolean excludeEmbedding) {
        List<Option> options = modelProviderService.list(Wrappers.lambdaQuery(ModelProvider.class)
                        .eq(StringUtils.isNotBlank(type), ModelProvider::getType, type)
                        .notLike(excludeEmbedding, ModelProvider::getType, "embedding")
                        .eq(ModelProvider::getStatus, 1).eq(ModelProvider::getDeleted, false)
                        .orderByAsc(ModelProvider::getName))
                .stream().map(item -> new Option(item.getName(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }

    @ApiOperation("Embedding 供应商下拉选项")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/embedding-options")
    public WebResponse<List<Option>> embeddingOptions() {
        return WebResponse.OK(modelProviderService.getEmbeddingProviderOptions());
    }

    @ApiOperation("模型供应商详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "供应商ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<ModelProviderVo> detail(@PathVariable @NotBlank String id) {
        ModelProvider provider = modelProviderService.getById(id);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        ModelProviderVo vo = new ModelProviderVo();
        BeanUtils.copyProperties(provider, vo);
        vo.setApiKey(null);
        return WebResponse.OK(vo);
    }

    @ApiOperation("新增模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping
    public WebResponse<String> save(@RequestBody @Valid ModelProviderDto dto) {
        ModelProvider provider = new ModelProvider();
        BeanUtils.copyProperties(dto, provider);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            provider.setApiKey(AesUtil.encrypt(provider.getApiKey()));
        }
        ModelProvider one = modelProviderService.getOne(Wrappers.<ModelProvider>lambdaQuery()
                .eq(ModelProvider::getName, provider.getName()));
        if (one != null && !one.getId().equals(provider.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("model.provider.name.duplicate"));
        }
        boolean saved = modelProviderService.save(provider);
        return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), provider.getId());
    }

    @ApiOperation("编辑模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PutMapping("/{id}")
    public WebResponse<Void> update(@PathVariable @NotBlank String id, @RequestBody @Valid ModelProviderDto dto) {
        ModelProvider provider = new ModelProvider();
        BeanUtils.copyProperties(dto, provider);
        provider.setId(id);
        if (StringUtils.isNotBlank(provider.getApiKey())) {
            provider.setApiKey(AesUtil.encrypt(provider.getApiKey()));
        } else {
            provider.setApiKey(null);
        }
        ModelProvider one = modelProviderService.getOne(Wrappers.<ModelProvider>lambdaQuery()
                .eq(ModelProvider::getName, provider.getName()));
        if (one != null && !one.getId().equals(provider.getId())) {
            throw new ServerException(400, I18nUtils.getMessage("model.provider.name.duplicate"));
        }
        boolean updated = modelProviderService.updateById(provider);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("删除模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "供应商ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = modelProviderService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"));
    }

    @ApiOperation("启用/禁用模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @PutMapping("/{id}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String id, @RequestBody ModelProviderVo vo) {
        ModelProvider provider = new ModelProvider();
        provider.setId(id);
        provider.setStatus(vo.getStatus());
        boolean updated = modelProviderService.updateById(provider);
        return WebResponse.OK(updated ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"));
    }

    @ApiOperation("测试连接")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/{id}/test")
    public WebResponse<Boolean> testConnection(@PathVariable @NotBlank String id) {
        // TODO: V0.3 实现模型调用客户端后完善
        return WebResponse.OK(true);
    }
}
