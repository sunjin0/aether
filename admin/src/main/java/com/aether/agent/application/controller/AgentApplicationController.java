package com.aether.agent.application.controller;

import com.aether.agent.application.dto.AgentApplicationDto;
import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.agent.application.vo.AgentApplicationVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/** 面向外部业务系统的 Agent 应用空间管理。 */
@RestController
@RequestMapping("/api/agent/application")
public class AgentApplicationController {
    private final AgentApplicationService applicationService;

    public AgentApplicationController(AgentApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/list")
    @Permission(path = "/service-account/manage")
    public WebResponse<List<AgentApplicationVo>> list(@RequestBody(required = false) AgentApplicationVo query) {
        long current = query == null || query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query == null || query.getPageSize() == null ? 20L : Math.min(100L, query.getPageSize());
        Page<AgentApplication> page = applicationService.page(new Page<AgentApplication>(current, pageSize),
                Wrappers.lambdaQuery(AgentApplication.class).eq(AgentApplication::getDeleted, false)
                        .orderByDesc(AgentApplication::getCreatedAt));
        return WebResponse.Page(page.getRecords().stream().map(this::vo).collect(Collectors.toList()), page.getTotal());
    }

    @PostMapping
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    public WebResponse<Void> create(@RequestBody AgentApplicationDto dto) {
        validate(dto);
        if (applicationService.count(Wrappers.lambdaQuery(AgentApplication.class).eq(AgentApplication::getCode, dto.getCode())
                .eq(AgentApplication::getDeleted, false)) > 0) throw new ServerException(422, "业务应用编码已存在");
        AgentApplication entity = new AgentApplication();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) entity.setStatus(1);
        applicationService.save(entity);
        return WebResponse.OK("创建成功");
    }

    @PutMapping("/{id}")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    public WebResponse<Void> update(@PathVariable String id, @RequestBody AgentApplicationDto dto) {
        AgentApplication entity = applicationService.getById(id);
        if (entity == null || Boolean.TRUE.equals(entity.getDeleted())) throw new ServerException(404, "业务应用空间不存在");
        validate(dto);
        if (!entity.getCode().equals(dto.getCode()) && applicationService.count(Wrappers.lambdaQuery(AgentApplication.class)
                .eq(AgentApplication::getCode, dto.getCode()).eq(AgentApplication::getDeleted, false)) > 0)
            throw new ServerException(422, "业务应用编码已存在");
        BeanUtils.copyProperties(dto, entity);
        applicationService.updateById(entity);
        return WebResponse.OK("更新成功");
    }

    private void validate(AgentApplicationDto dto) {
        if (dto == null || !StringUtils.hasText(dto.getCode()) || !StringUtils.hasText(dto.getName()))
            throw new ServerException(422, "应用编码和名称不能为空");
        if (!dto.getCode().matches("[A-Za-z0-9_-]{2,64}")) throw new ServerException(422, "应用编码仅支持字母、数字、下划线和短横线");
        if ((dto.getMaxAgentCallsPerHour() != null && (dto.getMaxAgentCallsPerHour() < 0 || dto.getMaxAgentCallsPerHour() > 100000))
                || (dto.getMaxWorkflowStartsPerHour() != null && (dto.getMaxWorkflowStartsPerHour() < 0 || dto.getMaxWorkflowStartsPerHour() > 100000)))
            throw new ServerException(422, "应用配额必须在 0 到 100000 之间");
    }

    private AgentApplicationVo vo(AgentApplication source) {
        AgentApplicationVo result = new AgentApplicationVo();
        BeanUtils.copyProperties(source, result);
        return result;
    }
}
