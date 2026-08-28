package com.aether.agent.controller;

import com.aether.agent.dto.ModelProviderDto;
import com.aether.agent.dto.AgentControllerRequests.ModelCatalogBatch;
import com.aether.agent.dto.AgentControllerRequests.ModelCatalogRequest;
import com.aether.agent.dto.AgentControllerRequests.ModelProviderList;
import com.aether.agent.dto.AgentControllerRequests.Status;
import com.aether.agent.entity.ModelProvider;
import com.aether.agent.service.ModelProviderService;
import com.aether.agent.service.ModelCatalogService;
import com.aether.agent.entity.ModelCatalog;
import com.aether.agent.vo.ModelProviderVo;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.utils.AesUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import javax.validation.Valid;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.io.IOException;
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
    private final ModelCatalogService modelCatalogService;

    /**
     * 创建 {@code ModelProviderController} 实例。
     */
    @Autowired
    public ModelProviderController(ModelProviderService modelProviderService, ModelCatalogService modelCatalogService) {
        this.modelProviderService = modelProviderService;
        this.modelCatalogService = modelCatalogService;
    }

    /**
     * 模型供应商列表。
     */
    @ApiOperation("模型供应商列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<ModelProviderVo>> list(@RequestBody ModelProviderList vo) {
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

    /**
     * Embedding 供应商下拉选项。
     */
    @ApiOperation("Embedding 供应商下拉选项")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/embedding-options")
    public WebResponse<List<Option>> embeddingOptions() {
        return WebResponse.OK(modelCatalogService.getOptions("EMBEDDING"));
    }

    /**
     * 模型Options。
     */
    @ApiOperation("按能力获取供应商模型选项")
    @GetMapping("/models/options")
    public WebResponse<List<Option>> modelOptions(@RequestParam String capability) {
        return WebResponse.OK(modelCatalogService.getOptions(capability));
    }

    /**
     * 处理models。
     */
    @ApiOperation("查询模型目录")
    @GetMapping("/models")
    public WebResponse<List<ModelCatalog>> models(@RequestParam(required = false) String providerId) {
        return WebResponse.OK(modelCatalogService.list(Wrappers.lambdaQuery(ModelCatalog.class)
                .eq(StringUtils.isNotBlank(providerId), ModelCatalog::getProviderId, providerId)
                .eq(ModelCatalog::getDeleted, false).orderByAsc(ModelCatalog::getSortNum)));
    }

    /**
     * 从供应商读取可用模型列表。
     */
    @ApiOperation("从供应商读取可用模型列表")
    @GetMapping("/{id}/models/discover")
    public WebResponse<List<Option>> discoverModels(@PathVariable @NotBlank String id) {
        ModelProvider provider = modelProviderService.getById(id);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted()) || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.model.provider.not.found"));
        }
        try {
            // Alibaba Model Studio's OpenAI-compatible endpoint does not expose a stable
            // GET /v1/models contract. Return the maintained Qwen model IDs instead.
            if (StringUtils.containsIgnoreCase(provider.getApiBaseUrl(), "aliyuncs.com")) {
                return WebResponse.OK(qwenModelOptions());
            }
            String baseUrl = StringUtils.removeEnd(provider.getApiBaseUrl(), "/");
            String url = baseUrl.endsWith("/v1/models") ? baseUrl : (baseUrl.endsWith("/v1") ? baseUrl + "/models" : baseUrl + "/v1/models");
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(provider.getApiKey()))
                headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
            ResponseEntity<String> response = new RestTemplate().exchange(url, HttpMethod.GET, new HttpEntity<Void>(headers), String.class);
            JSONObject payload = JSON.parseObject(response.getBody());
            JSONArray models = payload == null ? null : payload.getJSONArray("data");
            List<Option> result = new java.util.ArrayList<>();
            if (models != null) for (int i = 0; i < models.size(); i++) {
                JSONObject model = models.getJSONObject(i);
                String name = model == null ? null : StringUtils.defaultIfBlank(model.getString("id"), model.getString("name"));
                if (StringUtils.isNotBlank(name)) result.add(new Option(name, name));
            }
            return WebResponse.OK(result);
        } catch (Exception e) {
            throw new ServerException(502, I18nUtils.getMessage("agent.model.provider.models.discover.fail"));
        }
    }

    /**
     * 处理qwen模型Options。
     */
    private List<Option> qwenModelOptions() {
        String[] models = {"qwen3.7-max", "qwen3.7-plus", "qwen3.6-flash", "qwen-max", "qwen-plus", "qwen-turbo", "qwen3-vl-plus", "qwen3-omni-flash", "text-embedding-v4", "qwen3-rerank"};
        List<Option> result = new java.util.ArrayList<>();
        for (String model : models) result.add(new Option(model, model));
        return result;
    }

    private ModelCatalog model(ModelCatalogRequest request) {
        ModelCatalog model = new ModelCatalog();
        if (request != null) {
            BeanUtils.copyProperties(request, model);
        }
        return model;
    }

    /**
     * 保存模型。
     */
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @ApiOperation("新增模型目录项")
    @PostMapping("/models")
    public WebResponse<String> saveModel(@RequestBody ModelCatalogRequest request) {
        ModelCatalog model = model(request);
        if (StringUtils.isBlank(model.getProviderId()) || StringUtils.isBlank(model.getName()) || StringUtils.isBlank(model.getCapabilities()))
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.required"));
        modelCatalogService.validateForSave(model);
        modelCatalogService.save(model);
        return WebResponse.OK(I18nUtils.getMessage("agent.model.catalog.create.success"), model.getId());
    }

    /**
     * 更新模型。
     */
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @ApiOperation("更新模型目录项")
    @PutMapping("/models/{id}")
    public WebResponse<Void> updateModel(@PathVariable String id, @RequestBody ModelCatalogRequest request) {
        ModelCatalog model = model(request);
        model.setId(id);
        modelCatalogService.validateForSave(model);
        modelCatalogService.updateById(model);
        return WebResponse.OK(I18nUtils.getMessage("agent.model.catalog.update.success"));
    }

    /**
     * 保存Models。
     */
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @ApiOperation("批量保存模型目录项")
    @PostMapping("/models/batch")
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Integer> saveModels(@RequestBody ModelCatalogBatch request) {
        List<ModelCatalog> models = request == null || request.getModels() == null ? null : request.getModels().stream().map(this::model).collect(Collectors.toList());
        if (models == null || models.isEmpty())
            throw new ServerException(400, I18nUtils.getMessage("agent.model.catalog.required"));
        models.forEach(modelCatalogService::validateForSave);
        modelCatalogService.saveBatch(models);
        return WebResponse.OK(I18nUtils.getMessage("agent.model.catalog.create.success"), models.size());
    }

    /**
     * 删除模型。
     */
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @ApiOperation("删除模型目录项")
    @DeleteMapping("/models/{id}")
    public WebResponse<Void> deleteModel(@PathVariable String id) {
        return WebResponse.OK(modelCatalogService.removeById(id) ? I18nUtils.getMessage("agent.model.catalog.delete.success") : I18nUtils.getMessage("agent.model.catalog.delete.fail"));
    }

    /**
     * 更新模型状态。
     */
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @ApiOperation("更新模型目录项状态")
    @PutMapping("/models/{id}/status")
    public WebResponse<Void> updateModelStatus(@PathVariable String id, @RequestBody Status model) {
        ModelCatalog update = new ModelCatalog();
        update.setId(id);
        update.setStatus(model.getStatus());
        return WebResponse.OK(modelCatalogService.updateById(update) ? I18nUtils.getMessage("agent.model.catalog.update.success") : I18nUtils.getMessage("agent.model.catalog.update.fail"));
    }

    /**
     * 详情当前请求。
     */
    @ApiOperation("模型供应商详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "供应商ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<ModelProviderVo> detail(@PathVariable @NotBlank String id) {
        ModelProvider provider = modelProviderService.getById(id);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.model.provider.not.found"));
        }
        ModelProviderVo vo = new ModelProviderVo();
        BeanUtils.copyProperties(provider, vo);
        vo.setApiKey(null);
        return WebResponse.OK(vo);
    }

    /**
     * 保存当前请求。
     */
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
        return WebResponse.OK(saved ? I18nUtils.getMessage("agent.model-provider.create.success") : I18nUtils.getMessage("agent.model-provider.create.fail"), provider.getId());
    }

    /**
     * 更新当前请求。
     */
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
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.model-provider.update.success") : I18nUtils.getMessage("agent.model-provider.update.fail"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "供应商ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = modelProviderService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("agent.model-provider.delete.success") : I18nUtils.getMessage("agent.model-provider.delete.fail"));
    }

    /**
     * 更新状态。
     */
    @ApiOperation("启用/禁用模型供应商")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/model-provider", type = Permission.Type.Write)
    @PutMapping("/{id}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String id, @RequestBody Status vo) {
        ModelProvider provider = new ModelProvider();
        provider.setId(id);
        provider.setStatus(vo.getStatus());
        boolean updated = modelProviderService.updateById(provider);
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.model-provider.status.update.success") : I18nUtils.getMessage("agent.model-provider.status.update.fail"));
    }

    /**
     * 测试Connection。
     */
    @ApiOperation("测试连接")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/{id}/test")
    public WebResponse<Map<String, Object>> testConnection(@PathVariable @NotBlank String id) {
        ModelProvider provider = modelProviderService.getById(id);
        if (provider == null || Boolean.TRUE.equals(provider.getDeleted()) || StringUtils.isBlank(provider.getApiBaseUrl())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.model.provider.not.found"));
        }
        long startedAt = System.currentTimeMillis();
        boolean connected = false;
        String error = null;
        try {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(5000);
            requestFactory.setReadTimeout(10000);
            RestTemplate client = new RestTemplate(requestFactory);
            HttpHeaders headers = new HttpHeaders();
            if (StringUtils.isNotBlank(provider.getApiKey()))
                headers.setBearerAuth(AesUtil.decrypt(provider.getApiKey()));
            client.exchange(provider.getApiBaseUrl(), HttpMethod.OPTIONS, new HttpEntity<Void>(headers), Void.class);
            connected = true;
        } catch (Exception e) {
            error = StringUtils.abbreviate(e.getMessage(), 240);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", connected);
        result.put("elapsedMs", System.currentTimeMillis() - startedAt);
        result.put("error", error);
        return WebResponse.OK(connected ? I18nUtils.getMessage("agent.model-provider.connection.test.success") : I18nUtils.getMessage("agent.model-provider.connection.test.fail"), result);
    }
}
