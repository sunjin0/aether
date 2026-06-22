package com.aether.agent.controller;

import com.aether.agent.entity.AgentToolCallLog;
import com.aether.agent.service.AgentToolCallLogService;
import com.aether.agent.vo.AgentToolCallLogVo;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
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
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 工具调用日志 Controller
 */
@Api(tags = "工具调用日志 API")
@Validated
@RestController
@Permission(path = "/agent/tool-call-log")
@RequestMapping("/api/agent/tool-call-log")
public class AgentToolCallLogController {

    private final AgentToolCallLogService agentToolCallLogService;

    @Autowired
    public AgentToolCallLogController(AgentToolCallLogService agentToolCallLogService) {
        this.agentToolCallLogService = agentToolCallLogService;
    }

    @ApiOperation("工具调用日志列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentToolCallLogVo>> list(@RequestBody AgentToolCallLogVo vo) {
        Page<AgentToolCallLog> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentToolCallLog> wrapper = Wrappers.lambdaQuery(AgentToolCallLog.class)
                .eq(StringUtils.isNotBlank(vo.getRunId()), AgentToolCallLog::getRunId, vo.getRunId())
                .eq(StringUtils.isNotBlank(vo.getToolId()), AgentToolCallLog::getToolId, vo.getToolId())
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentToolCallLog::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(vo.getStatus() != null, AgentToolCallLog::getStatus, vo.getStatus())
                .eq(AgentToolCallLog::getDeleted, false)
                .orderByDesc(AgentToolCallLog::getCreatedAt);
        Page<AgentToolCallLog> result = agentToolCallLogService.page(page, wrapper);
        List<AgentToolCallLogVo> list = result.getRecords().stream().map(item -> {
            AgentToolCallLogVo itemVo = new AgentToolCallLogVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("工具调用日志详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "日志ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentToolCallLogVo> detail(@PathVariable @NotBlank String id) {
        AgentToolCallLog log = agentToolCallLogService.getById(id);
        if (log == null || Boolean.TRUE.equals(log.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentToolCallLogVo vo = new AgentToolCallLogVo();
        BeanUtils.copyProperties(log, vo);
        return WebResponse.OK(vo);
    }
}
