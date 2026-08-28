package com.aether.agent.controller;

import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentRunService;
import com.aether.agent.service.AgentRunStepService;
import com.aether.agent.service.DeepAgentSigningClient;
import com.aether.agent.dto.DeepAgentConfig;
import com.aether.agent.vo.AgentRunStatisticsVo;
import com.aether.agent.vo.AgentRunStepVo;
import com.aether.agent.vo.AgentRunVo;
import com.aether.agent.dto.AgentControllerRequests.RunList;
import com.aether.agent.vo.AgentRunPlanVo;
import com.aether.agent.service.AgentRunPlanService;
import com.aether.agent.service.DeepAgentRunService;
import com.aether.local.CurrentUser;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    private static final Logger log = LoggerFactory.getLogger(AgentRunController.class);

    private final AgentRunService agentRunService;
    private final AgentRunStepService agentRunStepService;
    private final DeepAgentSigningClient signingClient;
    private final DeepAgentConfig deepAgentConfig;
    private final AgentRunPlanService planService;
    private final DeepAgentRunService deepAgentRunService;

    /**
     * 创建 {@code AgentRunController} 实例。
     */
    @Autowired
    public AgentRunController(AgentRunService agentRunService,
                              AgentRunStepService agentRunStepService,
                              DeepAgentSigningClient signingClient,
                              DeepAgentConfig deepAgentConfig, AgentRunPlanService planService, DeepAgentRunService deepAgentRunService) {
        this.agentRunService = agentRunService;
        this.agentRunStepService = agentRunStepService;
        this.signingClient = signingClient;
        this.deepAgentConfig = deepAgentConfig;
        this.planService = planService;
        this.deepAgentRunService = deepAgentRunService;
    }

    /**
     * 运行记录列表。
     */
    @ApiOperation("运行记录列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<AgentRunVo>> list(@RequestBody RunList vo) {
        Page<AgentRun> page = new Page<>(vo.getCurrent(), vo.getPageSize());
        Wrapper<AgentRun> wrapper = Wrappers.lambdaQuery(AgentRun.class)
                .eq(StringUtils.isNotBlank(vo.getAgentDefinitionId()), AgentRun::getAgentDefinitionId, vo.getAgentDefinitionId())
                .eq(StringUtils.isNotBlank(vo.getUserId()), AgentRun::getUserId, vo.getUserId())
                .eq(vo.getStatus() != null, AgentRun::getStatus, vo.getStatus())
                .ge(vo.getStartTime() != null, AgentRun::getCreatedAt, vo.getStartTime())
                .le(vo.getEndTime() != null, AgentRun::getCreatedAt, vo.getEndTime())
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

    /**
     * 详情当前请求。
     */
    @ApiOperation("运行记录详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "运行记录ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}")
    public WebResponse<AgentRunVo> detail(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        }
        AgentRunVo vo = new AgentRunVo();
        BeanUtils.copyProperties(run, vo);
        return WebResponse.OK(vo);
    }

    /**
     * 运行统计。
     */
    @ApiOperation("运行统计")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/statistics")
    public WebResponse<AgentRunStatisticsVo> statistics(@RequestParam(value = "agentDefinitionId", required = false) String agentDefinitionId,
                                                        @RequestParam(required = false) Long startTime,
                                                        @RequestParam(required = false) Long endTime) {
        return WebResponse.OK(agentRunService.statistics(agentDefinitionId, startTime, endTime));
    }

    /**
     * 运行步骤列表。
     */
    @ApiOperation("运行步骤列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/{id}/steps")
    public WebResponse<List<AgentRunStepVo>> steps(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        }
        List<AgentRunStepVo> steps = agentRunStepService.listByRunId(id).stream()
                // 兼容历史记录：聊天文本分片不是执行步骤，不在执行记录中展示。
                .filter(item -> !"message.delta".equals(item.getEventType())).map(item -> {
                    AgentRunStepVo vo = new AgentRunStepVo();
                    BeanUtils.copyProperties(item, vo);
                    return vo;
                }).collect(Collectors.toList());
        return WebResponse.OK(steps);
    }

    /**
     * 取消当前请求。
     */
    @ApiOperation("取消 Deep Agent 运行")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    @PostMapping("/{id}/cancel")
    public WebResponse<Void> cancel(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        }
        if (!"DEEP".equals(run.getExecutionMode())) {
            throw new ServerException(422, I18nUtils.getMessage("agent.deep.run.cancel.unsupported"));
        }
        try {
            Map<String, String> cancelBody = new HashMap<>();
            cancelBody.put("run_id", id);
            signingClient.signedPost("/v1/runs/" + id + "/cancel", cancelBody);
        } catch (Exception e) {
            log.warn("取消 Deep Agent 运行请求失败: runId={}", id, e);
            throw new ServerException(502, I18nUtils.getMessage("agent.deep.run.cancel.failed"));
        }
        return WebResponse.OK(I18nUtils.getMessage("agent.deep.run.cancel.success"));
    }

    /**
     * 处理plan。
     */
    @ApiOperation("查询 Agent 运行计划")
    @GetMapping("/{id}/plan")
    public WebResponse<AgentRunPlanVo> plan(@PathVariable @NotBlank String id) {
        AgentRun run = agentRunService.getById(id);
        if (run == null || Boolean.TRUE.equals(run.getDeleted())) {
            throw new ServerException(404, I18nUtils.getMessage("agent.run.not-found"));
        }
        AgentRunPlanVo plan = planService.detail(id);
        if (plan == null && StringUtils.isNotBlank(run.getTaskId())) {
            plan = planService.detailByTaskId(run.getTaskId());
        }
        // Deep Agent 已受理但尚未完成首次规划时，前端应保持等待状态而不是展示 404。
        if (plan == null) {
            plan = new AgentRunPlanVo();
            plan.setRunId(id);
            plan.setStatus("PENDING");
            plan.setVersions(java.util.Collections.emptyList());
        }
        return WebResponse.OK(plan);
    }

    /**
     * 处理pause。
     */
    @ApiOperation("暂停 Agent 运行")
    @PostMapping("/{id}/pause")
    public WebResponse<Void> pause(@PathVariable @NotBlank String id) {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) throw new ServerException(401, "未登录");
        deepAgentRunService.pause(id, userId);
        planService.markPaused(id, "用户暂停");
        return WebResponse.OK("运行已暂停");
    }

    /**
     * 处理resume。
     */
    @ApiOperation("恢复 Agent 运行")
    @PostMapping("/{id}/resume")
    public WebResponse<Void> resume(@PathVariable @NotBlank String id) {
        String userId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("userId");
        if (StringUtils.isBlank(userId)) throw new ServerException(401, "未登录");
        deepAgentRunService.resume(id, userId);
        planService.markRunning(id);
        return WebResponse.OK("运行已继续");
    }
}
