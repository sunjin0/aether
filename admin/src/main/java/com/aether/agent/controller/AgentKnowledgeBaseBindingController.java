package com.aether.agent.controller;

import com.aether.knowledge.entity.KnowledgeBase;
import com.aether.agent.entity.AgentKnowledgeBaseBinding;
import com.aether.agent.service.AgentKnowledgeBaseBindingService;
import com.aether.knowledge.service.KnowledgeBaseService;
import com.aether.agent.vo.AgentKnowledgeBaseBindingVo;
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
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 提供智能体知识库BaseBinding相关的 REST 接口。
 */
@Api(tags = "Agent Knowledge Base Binding API")
@Validated
@RestController
@Permission(path = "/agent/definition")
@RequestMapping("/api/agent/knowledge-base-binding")
public class AgentKnowledgeBaseBindingController {

    private final AgentKnowledgeBaseBindingService bindingService;
    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * 创建 {@code AgentKnowledgeBaseBindingController} 实例。
     */
    public AgentKnowledgeBaseBindingController(AgentKnowledgeBaseBindingService bindingService,
                                               KnowledgeBaseService knowledgeBaseService) {
        this.bindingService = bindingService;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * Agent knowledge base binding list。
     */
    @ApiOperation("Agent knowledge base binding list")
    @PostMapping("/list")
    public WebResponse<List<AgentKnowledgeBaseBindingVo>> list(@RequestBody AgentKnowledgeBaseBindingVo vo) {
        Long current = vo.getCurrent();
        Long pageSize = vo.getPageSize();
        if (current == null || pageSize == null) {
            current = 1L;
            pageSize = 10L;
        }
        Page<AgentKnowledgeBaseBinding> page = new Page<>(current, pageSize);
        Wrapper<AgentKnowledgeBaseBinding> wrapper = Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentKnowledgeBaseBinding::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(StringUtils.isNotBlank(vo.getKnowledgeBaseId()), AgentKnowledgeBaseBinding::getKnowledgeBaseId, vo.getKnowledgeBaseId())
                .eq(vo.getStatus() != null, AgentKnowledgeBaseBinding::getStatus, vo.getStatus())
                .eq(AgentKnowledgeBaseBinding::getDeleted, false)
                .orderByDesc(AgentKnowledgeBaseBinding::getCreatedAt);
        Page<AgentKnowledgeBaseBinding> result = bindingService.page(page, wrapper);
        List<String> kbIds = result.getRecords().stream()
                .map(AgentKnowledgeBaseBinding::getKnowledgeBaseId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .collect(Collectors.toList());
        Map<String, KnowledgeBase> kbMap = kbIds.isEmpty()
                ? java.util.Collections.emptyMap()
                : knowledgeBaseService.listByIds(kbIds).stream()
                .collect(Collectors.toMap(KnowledgeBase::getId, item -> item, (a, b) -> a));
        List<AgentKnowledgeBaseBindingVo> list = result.getRecords().stream().map(item -> {
            AgentKnowledgeBaseBindingVo itemVo = new AgentKnowledgeBaseBindingVo();
            BeanUtils.copyProperties(item, itemVo);
            KnowledgeBase kb = kbMap.get(item.getKnowledgeBaseId());
            if (kb != null) {
                itemVo.setKnowledgeBaseName(kb.getName());
                itemVo.setScope(kb.getScope());
            }
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    /**
     * 保存当前请求。
     */
    @ApiOperation("Create Agent knowledge base binding")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @PostMapping
    public WebResponse<String> save(@RequestBody AgentKnowledgeBaseBindingVo vo) {
        if (StringUtils.isBlank(vo.getAgentDefinitionId()) || StringUtils.isBlank(vo.getKnowledgeBaseId())) {
            throw new ServerException(400, I18nUtils.getMessage("agent.knowledge.binding.required"));
        }
        validateKnowledgeBase(vo.getKnowledgeBaseId());
        boolean exists = bindingService.count(Wrappers.lambdaQuery(AgentKnowledgeBaseBinding.class)
                .eq(AgentKnowledgeBaseBinding::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(AgentKnowledgeBaseBinding::getKnowledgeBaseId, vo.getKnowledgeBaseId())
                .eq(AgentKnowledgeBaseBinding::getDeleted, false)) > 0;
        if (exists) {
            throw new ServerException(400, I18nUtils.getMessage("agent.knowledge.binding.exists"));
        }
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        BeanUtils.copyProperties(vo, binding);
        if (binding.getStatus() == null) {
            binding.setStatus(1);
        }
        boolean saved = bindingService.save(binding);
        return WebResponse.OK(saved ? I18nUtils.getMessage("agent.knowledge-binding.create.success") : I18nUtils.getMessage("agent.knowledge-binding.create.fail"), binding.getId());
    }

    /**
     * 更新状态。
     */
    @ApiOperation("Update Agent knowledge base binding status")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @PutMapping("/{id}/status")
    public WebResponse<Void> updateStatus(@PathVariable @NotBlank String id, @RequestBody AgentKnowledgeBaseBindingVo vo) {
        AgentKnowledgeBaseBinding binding = new AgentKnowledgeBaseBinding();
        binding.setId(id);
        binding.setStatus(vo.getStatus());
        boolean updated = bindingService.updateById(binding);
        return WebResponse.OK(updated ? I18nUtils.getMessage("agent.knowledge-binding.status.update.success") : I18nUtils.getMessage("agent.knowledge-binding.status.update.fail"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("Delete Agent knowledge base binding")
    @Permission(path = "/agent/definition", type = Permission.Type.Write)
    @DeleteMapping("/{id}")
    public WebResponse<Void> delete(@PathVariable @NotBlank String id) {
        boolean removed = bindingService.removeById(id);
        return WebResponse.OK(removed ? I18nUtils.getMessage("agent.knowledge-binding.delete.success") : I18nUtils.getMessage("agent.knowledge-binding.delete.fail"));
    }

    /**
     * 校验知识库Base。
     */
    private void validateKnowledgeBase(String knowledgeBaseId) {
        KnowledgeBase kb = knowledgeBaseService.getById(knowledgeBaseId);
        if (kb == null || Boolean.TRUE.equals(kb.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.knowledge.base.not.found"));
        }
    }
}
