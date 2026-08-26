package com.aether.sys.controller;

import com.aether.agent.entity.AgentDefinition;
import com.aether.agent.entity.AgentRun;
import com.aether.agent.service.AgentDefinitionService;
import com.aether.agent.service.AgentRunService;
import com.aether.entity.WebResponse;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.dto.ServiceAccountUpdateDto;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.aether.sys.vo.ServiceAccountUsageItemVo;
import com.aether.sys.vo.ServiceAccountUsageVo;
import com.aether.sys.vo.ServiceAccountVo;
import com.aether.workflow.entity.AgentWorkflow;
import com.aether.workflow.entity.AgentWorkflowInstance;
import com.aether.workflow.service.AgentWorkflowInstanceService;
import com.aether.workflow.service.AgentWorkflowService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.alibaba.fastjson2.JSON;

/**
 * 服务账号管理与 client credentials 令牌签发。
 */
@Api(tags = "服务账号 API")
@RestController
public class ServiceAccountController {
    private final ServiceAccountService serviceAccountService;
    private final AgentRunService agentRunService;
    private final AgentDefinitionService agentDefinitionService;
    private final AgentWorkflowInstanceService workflowInstanceService;
    private final AgentWorkflowService workflowService;

    /**
     * 创建 {@code ServiceAccountController} 实例。
     */
    public ServiceAccountController(ServiceAccountService serviceAccountService,
                                    AgentRunService agentRunService,
                                    AgentDefinitionService agentDefinitionService,
                                    AgentWorkflowInstanceService workflowInstanceService,
                                    AgentWorkflowService workflowService) {
        this.serviceAccountService = serviceAccountService;
        this.agentRunService = agentRunService;
        this.agentDefinitionService = agentDefinitionService;
        this.workflowInstanceService = workflowInstanceService;
        this.workflowService = workflowService;
    }

    /**
     * 令牌当前请求。
     */
    @ApiOperation("签发服务账号访问令牌")
    @PostMapping("/api/auth/service-account/token")
    public WebResponse<ServiceAccountTokenVo> token(@RequestBody ServiceAccountTokenDto dto, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(I18nUtils.getMessage("service-account.token.issue.success"), serviceAccountService.issueToken(dto));
    }

    /**
     * 服务账号列表。
     */
    @ApiOperation("服务账号列表")
    @Permission(path = "/service-account/manage")
    @PostMapping("/api/sys/service-account/list")
    public WebResponse<List<ServiceAccountVo>> list(@RequestBody(required = false) ServiceAccountVo query) {
        long current = query == null || query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query == null || query.getPageSize() == null ? 20L : Math.min(query.getPageSize(), 100L);
        Page<ServiceAccount> page = serviceAccountService.page(new Page<ServiceAccount>(current, pageSize),
                Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getDeleted, false)
                        .eq(query != null && org.apache.commons.lang3.StringUtils.isNotBlank(query.getApplicationId()), ServiceAccount::getApplicationId, query == null ? null : query.getApplicationId())
                        .orderByDesc(ServiceAccount::getCreatedAt));
        List<ServiceAccountVo> rows = page.getRecords().stream().map(this::vo).collect(Collectors.toList());
        return WebResponse.Page(rows, page.getTotal());
    }

    /**
     * 创建当前请求。
     */
    @ApiOperation("创建服务账号；明文密钥只在本次响应中返回")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account")
    public WebResponse<ServiceAccountSecretVo> create(@RequestBody ServiceAccountCreateDto dto, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(I18nUtils.getMessage("service-account.create.success"), serviceAccountService.create(dto));
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("编辑服务账号；客户端 ID 与密钥不可直接修改")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    @PutMapping("/api/sys/service-account/{id}")
    public WebResponse<Void> update(@PathVariable String id, @RequestBody ServiceAccountUpdateDto dto) {
        serviceAccountService.update(id, dto);
        return WebResponse.OK(I18nUtils.getMessage("service-account.update.success"));
    }

    /**
     * 轮换服务账号密钥；旧令牌立即失效。
     */
    @ApiOperation("轮换服务账号密钥；旧令牌立即失效")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account/{id}/rotate-secret")
    public WebResponse<ServiceAccountSecretVo> rotateSecret(@PathVariable String id, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(I18nUtils.getMessage("service-account.secret.rotate.success"), serviceAccountService.rotateSecret(id));
    }

    /**
     * 启用或禁用服务账号；状态变更后旧令牌立即失效。
     */
    @ApiOperation("启用或禁用服务账号；状态变更后旧令牌立即失效")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account/{id}/enabled")
    public WebResponse<Void> enabled(@PathVariable String id, @RequestParam boolean enabled) {
        serviceAccountService.setEnabled(id, enabled);
        return WebResponse.OK(I18nUtils.getMessage("service-account.status.update.success"));
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("删除服务账号；已签发令牌立即失效")
    @Permission(path = "/service-account/manage", type = Permission.Type.Write)
    @DeleteMapping("/api/sys/service-account/{id}")
    public WebResponse<Void> delete(@PathVariable String id) {
        serviceAccountService.delete(id);
        return WebResponse.OK(I18nUtils.getMessage("service-account.delete.success"));
    }

    /**
     * 服务账号外部接入使用情况。
     */
    @ApiOperation("服务账号外部接入使用情况")
    @Permission(path = "/service-account/monitor")
    @GetMapping("/api/sys/service-account/usage")
    public WebResponse<ServiceAccountUsageVo> usage(@RequestParam(required = false, defaultValue = "7") Integer days) {
        int rangeDays = normalizeDays(days);
        long now = System.currentTimeMillis();
        long rangeMillis = rangeDays * 24L * 60L * 60L * 1000L;
        long rangeStart = now - rangeMillis;
        long previousRangeStart = rangeStart - rangeMillis;
        long last24HoursStart = now - 24L * 60L * 60L * 1000L;
        long previous24HoursStart = last24HoursStart - 24L * 60L * 60L * 1000L;
        List<ServiceAccount> accounts = serviceAccountService.list(Wrappers.lambdaQuery(ServiceAccount.class)
                .eq(ServiceAccount::getDeleted, false));
        Map<String, ServiceAccount> accountByPrincipal = new HashMap<String, ServiceAccount>();
        for (ServiceAccount account : accounts) accountByPrincipal.put("sa:" + account.getId(), account);
        List<AgentRun> runs = accountByPrincipal.isEmpty() ? new ArrayList<AgentRun>() : agentRunService.list(
                Wrappers.lambdaQuery(AgentRun.class).in(AgentRun::getUserId, accountByPrincipal.keySet())
                        .ge(AgentRun::getCreatedAt, rangeStart)
                        .eq(AgentRun::getDeleted, false));
        List<AgentWorkflowInstance> instances = accountByPrincipal.isEmpty() ? new ArrayList<AgentWorkflowInstance>() : workflowInstanceService.list(
                Wrappers.lambdaQuery(AgentWorkflowInstance.class).in(AgentWorkflowInstance::getUserId, accountByPrincipal.keySet())
                        .ge(AgentWorkflowInstance::getCreatedAt, rangeStart)
                        .eq(AgentWorkflowInstance::getDeleted, false));
        List<AgentRun> previousRuns = accountByPrincipal.isEmpty() ? new ArrayList<AgentRun>() : agentRunService.list(
                Wrappers.lambdaQuery(AgentRun.class).in(AgentRun::getUserId, accountByPrincipal.keySet())
                        .ge(AgentRun::getCreatedAt, previousRangeStart)
                        .lt(AgentRun::getCreatedAt, rangeStart)
                        .eq(AgentRun::getDeleted, false));
        List<AgentWorkflowInstance> previousInstances = accountByPrincipal.isEmpty() ? new ArrayList<AgentWorkflowInstance>() : workflowInstanceService.list(
                Wrappers.lambdaQuery(AgentWorkflowInstance.class).in(AgentWorkflowInstance::getUserId, accountByPrincipal.keySet())
                        .ge(AgentWorkflowInstance::getCreatedAt, previousRangeStart)
                        .lt(AgentWorkflowInstance::getCreatedAt, rangeStart)
                        .eq(AgentWorkflowInstance::getDeleted, false));
        List<AgentRun> previous24Runs = accountByPrincipal.isEmpty() ? new ArrayList<AgentRun>() : agentRunService.list(
                Wrappers.lambdaQuery(AgentRun.class).in(AgentRun::getUserId, accountByPrincipal.keySet())
                        .ge(AgentRun::getCreatedAt, previous24HoursStart)
                        .lt(AgentRun::getCreatedAt, last24HoursStart)
                        .eq(AgentRun::getDeleted, false));
        List<AgentWorkflowInstance> previous24Instances = accountByPrincipal.isEmpty() ? new ArrayList<AgentWorkflowInstance>() : workflowInstanceService.list(
                Wrappers.lambdaQuery(AgentWorkflowInstance.class).in(AgentWorkflowInstance::getUserId, accountByPrincipal.keySet())
                        .ge(AgentWorkflowInstance::getCreatedAt, previous24HoursStart)
                        .lt(AgentWorkflowInstance::getCreatedAt, last24HoursStart)
                        .eq(AgentWorkflowInstance::getDeleted, false));
        ServiceAccountUsageVo usage = new ServiceAccountUsageVo();
        long previousServiceAccountCount = accounts.stream()
                .filter(account -> account.getCreatedAt() != null && account.getCreatedAt() < rangeStart).count();
        long previousCalls = previousRuns.size() + previousInstances.size();
        long previousTokens = previousRuns.stream().mapToLong(run -> run.getTotalTokens() == null ? 0L : run.getTotalTokens()).sum();
        long previous24Calls = previous24Runs.size() + previous24Instances.size();
        usage.setRangeDays(rangeDays);
        usage.setServiceAccountCount((long) accounts.size());
        usage.setAgentCalls((long) runs.size());
        usage.setWorkflowStarts((long) instances.size());
        usage.setTotalCalls(usage.getAgentCalls() + usage.getWorkflowStarts());
        usage.setTotalTokens(runs.stream().mapToLong(run -> run.getTotalTokens() == null ? 0L : run.getTotalTokens()).sum());
        usage.setLast24HoursCalls(last24HoursCalls(runs, instances, last24HoursStart));
        usage.setServiceAccountDelta(usage.getServiceAccountCount() - previousServiceAccountCount);
        usage.setServiceAccountGrowthRate(growthRate(usage.getServiceAccountDelta(), previousServiceAccountCount));
        usage.setTotalCallsDelta(usage.getTotalCalls() - previousCalls);
        usage.setTotalCallsGrowthRate(growthRate(usage.getTotalCallsDelta(), previousCalls));
        usage.setTotalTokensDelta(usage.getTotalTokens() - previousTokens);
        usage.setTotalTokensGrowthRate(growthRate(usage.getTotalTokensDelta(), previousTokens));
        usage.setLast24HoursCallsDelta(usage.getLast24HoursCalls() - previous24Calls);
        usage.setLast24HoursCallsGrowthRate(growthRate(usage.getLast24HoursCallsDelta(), previous24Calls));
        usage.setAccountTotal((long) accounts.size());
        usage.setAgentTotal(runs.stream().filter(run -> run.getAgentDefinitionId() != null)
                .map(AgentRun::getAgentDefinitionId).distinct().count());
        usage.setWorkflowTotal(instances.stream().filter(instance -> instance.getWorkflowId() != null)
                .map(AgentWorkflowInstance::getWorkflowId).distinct().count());
        usage.setAccounts(accountUsage(accounts, runs, instances, usage.getTotalCalls()));
        usage.setAgents(agentUsage(runs, usage.getAgentCalls()));
        usage.setWorkflows(workflowUsage(instances, usage.getWorkflowStarts()));
        return WebResponse.OK(usage);
    }

    /**
     * VO当前请求。
     */
    private ServiceAccountVo vo(ServiceAccount account) {
        ServiceAccountVo result = new ServiceAccountVo();
        BeanUtils.copyProperties(account, result);
        result.setAllowedWorkflowIds(account.getAllowedWorkflowIds() == null ? java.util.Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedWorkflowIds(), String.class));
        result.setAllowedAgentIds(account.getAllowedAgentIds() == null ? java.util.Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedAgentIds(), String.class));
        return result;
    }

    private List<ServiceAccountUsageItemVo> accountUsage(List<ServiceAccount> accounts, List<AgentRun> runs,
                                                         List<AgentWorkflowInstance> instances, long totalCalls) {
        List<ServiceAccountUsageItemVo> result = new ArrayList<ServiceAccountUsageItemVo>();
        for (ServiceAccount account : accounts) {
            String principal = "sa:" + account.getId();
            long agentCalls = runs.stream().filter(run -> principal.equals(run.getUserId())).count();
            long workflowStarts = instances.stream().filter(item -> principal.equals(item.getUserId())).count();
            long tokens = runs.stream().filter(run -> principal.equals(run.getUserId()))
                    .mapToLong(run -> run.getTotalTokens() == null ? 0L : run.getTotalTokens()).sum();
            ServiceAccountUsageItemVo item = new ServiceAccountUsageItemVo();
            item.setId(account.getId());
            item.setName(account.getName());
            item.setCalls(agentCalls + workflowStarts);
            item.setTokens(tokens);
            item.setPercent(percent(item.getCalls(), totalCalls));
            result.add(item);
        }
        result.sort((left, right) -> Long.compare(right.getCalls(), left.getCalls()));
        return result;
    }

    private List<ServiceAccountUsageItemVo> agentUsage(List<AgentRun> runs, long totalCalls) {
        Map<String, ServiceAccountUsageItemVo> map = new HashMap<String, ServiceAccountUsageItemVo>();
        for (AgentRun run : runs) {
            if (run.getAgentDefinitionId() == null) continue;
            ServiceAccountUsageItemVo item = map.computeIfAbsent(run.getAgentDefinitionId(), id -> {
                ServiceAccountUsageItemVo value = new ServiceAccountUsageItemVo();
                value.setId(id);
                AgentDefinition agent = agentDefinitionService.getById(id);
                value.setName(agent == null ? id : agent.getName());
                value.setCalls(0L);
                value.setTokens(0L);
                return value;
            });
            item.setCalls(item.getCalls() + 1);
            item.setTokens(item.getTokens() + (run.getTotalTokens() == null ? 0L : run.getTotalTokens()));
        }
        applyPercent(map.values(), totalCalls);
        return sortedUsage(map);
    }

    private List<ServiceAccountUsageItemVo> workflowUsage(List<AgentWorkflowInstance> instances, long totalCalls) {
        Map<String, ServiceAccountUsageItemVo> map = new HashMap<String, ServiceAccountUsageItemVo>();
        for (AgentWorkflowInstance instance : instances) {
            if (instance.getWorkflowId() == null) continue;
            ServiceAccountUsageItemVo item = map.computeIfAbsent(instance.getWorkflowId(), id -> {
                ServiceAccountUsageItemVo value = new ServiceAccountUsageItemVo();
                value.setId(id);
                AgentWorkflow workflow = workflowService.getById(id);
                value.setName(workflow == null ? id : workflow.getName());
                value.setCalls(0L);
                value.setTokens(0L);
                return value;
            });
            item.setCalls(item.getCalls() + 1);
        }
        applyPercent(map.values(), totalCalls);
        return sortedUsage(map);
    }

    private List<ServiceAccountUsageItemVo> sortedUsage(Map<String, ServiceAccountUsageItemVo> map) {
        List<ServiceAccountUsageItemVo> result = new ArrayList<ServiceAccountUsageItemVo>(map.values());
        result.sort((left, right) -> Long.compare(right.getCalls(), left.getCalls()));
        return result.stream().limit(10).collect(Collectors.toList());
    }

    private long last24HoursCalls(List<AgentRun> runs, List<AgentWorkflowInstance> instances, long last24HoursStart) {
        long agentCalls = runs.stream().filter(run -> run.getCreatedAt() != null && run.getCreatedAt() >= last24HoursStart).count();
        long workflowStarts = instances.stream()
                .filter(instance -> instance.getCreatedAt() != null && instance.getCreatedAt() >= last24HoursStart).count();
        return agentCalls + workflowStarts;
    }

    private void applyPercent(Collection<ServiceAccountUsageItemVo> items, long totalCalls) {
        for (ServiceAccountUsageItemVo item : items) item.setPercent(percent(item.getCalls(), totalCalls));
    }

    private double percent(long value, long total) {
        if (total <= 0L) return 0D;
        return Math.round(value * 10000D / total) / 100D;
    }

    private double growthRate(long delta, long previous) {
        if (previous <= 0L) return 0D;
        return Math.round(delta * 10000D / previous) / 100D;
    }

    private int normalizeDays(Integer days) {
        if (days == null) return 7;
        return Math.max(1, Math.min(days, 90));
    }

    /**
     * 处理noStore。
     */
    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
