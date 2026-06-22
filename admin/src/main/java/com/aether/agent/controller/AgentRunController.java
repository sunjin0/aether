package com.aether.agent.controller;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.aether.agent.vo.AgentRunVo;
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
 * 运行审计 Controller
 */
@Api(tags = "运行审计 API")
@Validated
@RestController
@Permission(path = "/agent/run")
@RequestMapping("/api/agent/run")
public class AgentRunController {

    private final AgentRunService agentRunService;

    @Autowired
    public AgentRunController(AgentRunService agentRunService) {
        this.agentRunService = agentRunService;
    }

    @ApiOperation("运行记录列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentRunVo>> list(@RequestBody AgentRunVo vo) {
        Page<AgentRun> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentRun> wrapper = Wrappers.lambdaQuery(AgentRun.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentRun::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(StringUtils.isNotBlank(vo.getUserId()), AgentRun::getUserId, vo.getUserId())
                .eq(vo.getStatus() != null, AgentRun::getStatus, vo.getStatus())
                .eq(AgentRun::getDeleted, false)
                .orderByDesc(AgentRun::getCreatedAt);
        Page<AgentRun> result = agentRunService.page(page, wrapper);
        List<AgentRunVo> list = result.getRecords().stream().map(item -> {
            AgentRunVo itemVo = new AgentRunVo();
            BeanUtils.copyProperties(item, itemVo);
            return itemVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(list, result.getTotal());
    }

    @ApiOperation("运行记录详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "运行记录ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentRunVo> detail(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("resource.not.found"));
        }
        AgentRunVo vo = new AgentRunVo();
        BeanUtils.copyProperties(run, vo);
        return WebResponse.OK(vo);
    }

    @ApiOperation("运行统计")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/statistics")
    public WebResponse<AgentRunStatisticsVo> statistics(@RequestParam(required = false) String agentId,
                                                        @RequestParam(required = false) Long startTime,
                                                        @RequestParam(required = false) Long endTime) {
        // TODO: V0.6 完善统计逻辑
        AgentRunStatisticsVo vo = new AgentRunStatisticsVo();
        vo.setAgentDefinitionId(agentId);
        vo.setTotalCalls(0L);
        vo.setSuccessCalls(0L);
        vo.setFailedCalls(0L);
        vo.setTimeoutCalls(0L);
        vo.setTotalPromptTokens(0L);
        vo.setTotalCompletionTokens(0L);
        vo.setTotalTokens(0L);
        vo.setAvgLatencyMs(0L);
        vo.setErrorRate(0.0);
        return WebResponse.OK(vo);
    }
}
