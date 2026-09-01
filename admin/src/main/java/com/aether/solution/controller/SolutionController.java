package com.aether.solution.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.permission.Permission;
import com.aether.solution.entity.Solution;
import com.aether.solution.entity.SolutionInstallation;
import com.aether.solution.service.SolutionInstallationService;
import com.aether.agent.application.entity.AgentApplication;
import com.aether.agent.application.service.AgentApplicationService;
import com.aether.solution.service.SolutionService;
import com.aether.agent.service.AgentMcpServerService;
import com.aether.agent.skill.entity.AgentSkill;
import com.aether.agent.skill.service.AgentSkillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.aether.local.CurrentUser;
import com.aether.workflow.service.AgentWorkflowService;
import com.aether.knowledge.service.KnowledgeBaseService;

import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Autowired;

@Api(tags = "Solution 管理 API")
@RestController
@Permission(path = "/agent/application")
@RequestMapping("/api/agent/solution")
public class SolutionController {
    private final SolutionService service;
    private final SolutionInstallationService installationService;
    private final AgentApplicationService applicationService;
    private final AgentMcpServerService mcpServerService;
    private final AgentSkillService skillService;
    @Autowired(required = false)
    private AgentWorkflowService workflowService;
    @Autowired(required = false)
    private KnowledgeBaseService knowledgeBaseService;

    public SolutionController(SolutionService service, SolutionInstallationService installationService,
                              AgentApplicationService applicationService, AgentMcpServerService mcpServerService,
                              AgentSkillService skillService) {
        this.service = service;
        this.installationService = installationService;
        this.applicationService = applicationService;
        this.mcpServerService = mcpServerService;
        this.skillService = skillService;
    }

    @ApiOperation("Solution 列表")
    @GetMapping
    public WebResponse<List<Solution>> list(@RequestParam(defaultValue = "1") long current,
                                            @RequestParam(defaultValue = "20") long pageSize,
                                            @RequestParam(required = false) String name,
                                            @RequestParam(required = false) String code,
                                            @RequestParam(required = false) Integer status) {
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Solution> page =
                new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(Math.max(1, current), Math.min(Math.max(1, pageSize), 100));
        com.baomidou.mybatisplus.extension.plugins.pagination.Page<Solution> result = service.page(page,
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Solution>()
                        .eq("deleted", false)
                        .and(StringUtils.isNotBlank(currentTenantId()), q -> q.eq("tenant_id", currentTenantId()).or().isNull("tenant_id"))
                        .like(StringUtils.isNotBlank(name), "name", name)
                        .eq(StringUtils.isNotBlank(code), "code", code)
                        .eq(status != null, "status", status)
                        .orderByDesc("created_at"));
        return WebResponse.Page(result.getRecords(), result.getTotal());
    }

    @ApiOperation("Solution 详情")
    @GetMapping("/{id}")
    public WebResponse<Solution> detail(@PathVariable String id) {
        Solution solution = service.getById(id);
        requireTenant(solution);
        if (solution == null || Boolean.TRUE.equals(solution.getDeleted())) throw new ServerException(404, "Solution 不存在");
        return WebResponse.OK(solution);
    }

    @ApiOperation("查询 Application 的 Solution 安装记录")
    @GetMapping("/installations")
    public WebResponse<List<SolutionInstallation>> installations(@RequestParam String applicationId,
                                                                  @RequestParam(defaultValue = "false") boolean history) {
        AgentApplication application = applicationService.getById(applicationId);
        if (application == null || Boolean.TRUE.equals(application.getDeleted())
                || (StringUtils.isNotBlank(currentTenantId()) && !currentTenantId().equals(application.getTenantId())))
            throw new ServerException(404, "目标 Application 不存在");
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation>()
                        .eq("application_id", applicationId).eq("deleted", false);
        if (!history) query.eq("status", 1);
        query.eq(StringUtils.isNotBlank(currentTenantId()), "tenant_id", currentTenantId());
        return WebResponse.OK(installationService.list(query.orderByDesc("created_at")));
    }

    @ApiOperation("保存 Solution")
    @PostMapping
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody Solution request) {
        if (request == null || StringUtils.isAnyBlank(request.getName(), request.getCode(), request.getVersion()))
            return WebResponse.Error(400, "name、code 和 version 不能为空");
        if (request.getStatus() == null) request.setStatus(1);
        String tenantId = currentTenantId();
        QueryWrapper<Solution> duplicate = new QueryWrapper<Solution>()
                .eq("code", request.getCode()).eq("version", request.getVersion()).eq("deleted", false)
                .isNull(StringUtils.isBlank(tenantId), "tenant_id")
                .eq(StringUtils.isNotBlank(tenantId), "tenant_id", tenantId);
        if (request.getId() != null) duplicate.ne("id", request.getId());
        if (service.getOne(duplicate, false) != null)
            return WebResponse.Error(409, "同租户下 Solution 编码和版本已存在");
        if (request.getId() == null) {
            if (StringUtils.isNotBlank(tenantId)) request.setTenantId(tenantId);
            service.save(request);
        } else {
            Solution existing = service.getById(request.getId());
            requireTenant(existing);
            if (existing == null || Boolean.TRUE.equals(existing.getDeleted())) return WebResponse.Error(404, "Solution 不存在");
            if (StringUtils.isNotBlank(currentTenantId()) && StringUtils.isBlank(existing.getTenantId()))
                return WebResponse.Error(403, "全局 Solution 仅允许平台管理员修改");
            request.setTenantId(existing.getTenantId());
            service.updateById(request);
        }
        return WebResponse.OK(request.getId());
    }

    @ApiOperation("删除 Solution")
    @DeleteMapping("/{id}")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    public WebResponse<Boolean> delete(@PathVariable String id) {
        Solution solution = service.getById(id);
        requireTenant(solution);
        if (solution == null || Boolean.TRUE.equals(solution.getDeleted())) return WebResponse.OK(false);
        if (StringUtils.isNotBlank(currentTenantId()) && StringUtils.isBlank(solution.getTenantId()))
            throw new ServerException(403, "全局 Solution 仅允许平台管理员删除");
        return WebResponse.OK(service.removeById(id));
    }

    @ApiOperation("安装 Solution 到 Application")
    @PostMapping("/{id}/install")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<String> install(@PathVariable String id, @RequestParam String applicationId) {
        Solution solution = service.getById(id);
        requireTenant(solution);
        if (solution == null || Boolean.TRUE.equals(solution.getDeleted()) || !Integer.valueOf(1).equals(solution.getStatus()))
            return WebResponse.Error(404, "Solution 不存在或已禁用");
        AgentApplication application = applicationService.getById(applicationId);
        if (application == null || Boolean.TRUE.equals(application.getDeleted()) || !Integer.valueOf(1).equals(application.getStatus()))
            return WebResponse.Error(404, "目标 Application 不存在或已禁用");
        if (StringUtils.isNotBlank(currentTenantId()) && !currentTenantId().equals(application.getTenantId()))
            return WebResponse.Error(404, "目标 Application 不存在或已禁用");
        if (!validManifest(solution.getManifestJson())) return WebResponse.Error(422, "Solution Manifest 格式无效");
        if (!dependenciesAvailable(solution.getManifestJson(), id)) return WebResponse.Error(422, "Solution 依赖不存在或已禁用");
        SolutionInstallation existing = installationService.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation>()
                .eq("solution_id", id).eq("application_id", applicationId).eq("solution_version", solution.getVersion()).eq("deleted", false)
                .eq(StringUtils.isNotBlank(currentTenantId()), "tenant_id", currentTenantId()), false);
        if (existing != null) {
            existing.setStatus(1);
            installationService.updateById(existing);
            return WebResponse.OK(existing.getId());
        }
        List<SolutionInstallation> activeVersions = installationService.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation>()
                .eq("solution_id", id).eq("application_id", applicationId).eq("status", 1).eq("deleted", false)
                .eq(StringUtils.isNotBlank(currentTenantId()), "tenant_id", currentTenantId()));
        for (SolutionInstallation active : activeVersions) {
            active.setStatus(0);
            installationService.updateById(active);
        }
        SolutionInstallation installation = new SolutionInstallation();
        installation.setSolutionId(id);
        installation.setTenantId(currentTenantId());
        installation.setApplicationId(applicationId);
        installation.setSolutionVersion(solution.getVersion());
        installation.setStatus(1);
        installationService.save(installation);
        return WebResponse.OK(installation.getId());
    }

    private boolean validManifest(String manifest) {
        if (StringUtils.isBlank(manifest)) return true;
        try {
            JSONObject json = JSON.parseObject(manifest);
            Object dependencies = json.get("dependencies");
            if (dependencies != null && !(dependencies instanceof JSONArray)) return false;
            if (dependencies instanceof JSONArray) {
                Set<String> dependencyKeys = new HashSet<>();
                for (Object dependency : (JSONArray) dependencies) {
                if (!(dependency instanceof JSONObject)) return false;
                JSONObject item = (JSONObject) dependency;
                if (StringUtils.isBlank(item.getString("type")) || StringUtils.isBlank(item.getString("code"))) return false;
                String type = StringUtils.lowerCase(item.getString("type"));
                if (!Arrays.asList("solution", "connector", "skill").contains(type)) return false;
                String version = item.getString("version");
                if (StringUtils.isNotBlank(version) && !version.matches("[0-9A-Za-z][0-9A-Za-z._-]{0,31}")) return false;
                if (!dependencyKeys.add(type + ":" + item.getString("code"))) return false;
                }
            }
            return validConfiguration(json);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    /** AI SRE 等方案的关键能力必须有明确的配置门禁，避免安装后生成不可执行的半成品。 */
    private boolean validConfiguration(JSONObject manifest) {
        JSONArray capabilities = manifest.getJSONArray("capabilities");
        JSONObject configuration = manifest.getJSONObject("configuration");
        if (capabilities == null) return true;
        if (capabilities.contains("alert-webhook") && !requiredConfiguration(configuration, "alertWebhook")) return false;
        if (capabilities.contains("human-approval") && !requiredConfiguration(configuration, "approval")) return false;
        if (capabilities.contains("diagnosis-workflow") && !codedConfiguration(configuration, "diagnosisWorkflow")) return false;
        if (capabilities.contains("knowledge-retrieval") && !codedConfiguration(configuration, "knowledgeBase")) return false;
        return true;
    }

    private boolean requiredConfiguration(JSONObject configuration, String key) {
        if (configuration == null || !(configuration.get(key) instanceof JSONObject)) return false;
        return Boolean.TRUE.equals(configuration.getJSONObject(key).getBoolean("required"));
    }

    private boolean codedConfiguration(JSONObject configuration, String key) {
        return configuration != null && configuration.get(key) instanceof JSONObject
                && StringUtils.isNotBlank(configuration.getJSONObject(key).getString("code"))
                && configuration.getJSONObject(key).getString("code").matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,63}");
    }

    /** 解析当前目录内可解析的 Solution 依赖；其他 Connector/Skill 依赖交由对应安装器处理。 */
    private boolean dependenciesAvailable(String manifest, String selfId) {
        if (StringUtils.isBlank(manifest)) return true;
        JSONArray dependencies = JSON.parseObject(manifest).getJSONArray("dependencies");
        if (dependencies == null) return true;
        for (Object raw : dependencies) {
            JSONObject dependency = (JSONObject) raw;
            String type = StringUtils.lowerCase(dependency.getString("type"));
            if ("connector".equals(type) && !connectorAvailable(dependency)) return false;
            if ("skill".equals(type) && !skillAvailable(dependency.getString("code"))) return false;
            if (!"solution".equals(type)) continue;
            String code = dependency.getString("code");
            QueryWrapper<Solution> query = new QueryWrapper<Solution>().eq("code", code)
                    .eq("status", 1).eq("deleted", false);
            query.and(StringUtils.isNotBlank(currentTenantId()), q -> q.eq("tenant_id", currentTenantId()).or().isNull("tenant_id"));
            if (selfId != null) query.ne("id", selfId);
            Solution resolved = service.getOne(query, false);
            if (resolved == null) return false;
            String requiredVersion = dependency.getString("version");
            if (StringUtils.isNotBlank(requiredVersion) && !requiredVersion.equals(resolved.getVersion())) return false;
        }
        JSONObject configuration = JSON.parseObject(manifest).getJSONObject("configuration");
        if (configuration != null && configuration.getJSONObject("diagnosisWorkflow") != null
                && !workflowAvailable(configuration.getJSONObject("diagnosisWorkflow").getString("code"))) return false;
        if (configuration != null && configuration.getJSONObject("knowledgeBase") != null
                && !knowledgeBaseAvailable(configuration.getJSONObject("knowledgeBase").getString("code"))) return false;
        return true;
    }

    private boolean workflowAvailable(String code) {
        if (workflowService == null || StringUtils.isBlank(code)) return false;
        QueryWrapper<com.aether.workflow.entity.AgentWorkflow> query = new QueryWrapper<com.aether.workflow.entity.AgentWorkflow>()
                .eq("code", code).eq("status", 1).eq("deleted", false);
        if (StringUtils.isNotBlank(currentTenantId()))
            query.and(q -> q.eq("tenant_id", currentTenantId()).or().isNull("tenant_id"));
        return workflowService.getOne(query, false) != null;
    }

    /** 现有知识库模型以 name 作为方案引用标识，仍执行租户/公共范围隔离。 */
    private boolean knowledgeBaseAvailable(String code) {
        if (knowledgeBaseService == null || StringUtils.isBlank(code)) return false;
        QueryWrapper<com.aether.knowledge.entity.KnowledgeBase> query = new QueryWrapper<com.aether.knowledge.entity.KnowledgeBase>()
                .eq("name", code).eq("deleted", false);
        if (StringUtils.isNotBlank(currentTenantId()))
            query.and(q -> q.eq("tenant_id", currentTenantId()).or().isNull("tenant_id"));
        return knowledgeBaseService.getOne(query, false) != null;
    }

    private boolean connectorAvailable(JSONObject dependency) {
        if (mcpServerService == null) return false;
        String code = dependency.getString("code");
        QueryWrapper<com.aether.agent.entity.AgentMcpServer> query = new QueryWrapper<com.aether.agent.entity.AgentMcpServer>()
                .eq("code", code).eq("status", 1).eq("deleted", false);
        if (StringUtils.isNotBlank(currentTenantId()))
            query.and(q -> q.eq("tenant_id", currentTenantId()).or().isNull("tenant_id"));
        com.aether.agent.entity.AgentMcpServer connector = mcpServerService.getOne(query, false);
        if (connector == null) return false;
        String requiredVersion = dependency.getString("version");
        return StringUtils.isBlank(requiredVersion) || compatibleVersion(requiredVersion, connector.getVersion());
    }

    /** Manifest 可声明精确版本，也可声明主版本（例如 1 匹配 1.0.0）。 */
    private boolean compatibleVersion(String required, String actual) {
        if (StringUtils.isBlank(actual)) return false;
        return required.equals(actual) || actual.startsWith(required + ".");
    }

    private boolean skillAvailable(String code) {
        if (skillService == null) return false;
        return skillService.getOne(new QueryWrapper<AgentSkill>().eq("code", code).eq("status", 1).eq("deleted", false), false) != null;
    }

    @ApiOperation("卸载 Solution")
    @PostMapping("/{id}/uninstall")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> uninstall(@PathVariable String id, @RequestParam String applicationId) {
        SolutionInstallation installation = installationService.getOne(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation>()
                .eq("solution_id", id).eq("application_id", applicationId).eq("status", 1).eq("deleted", false)
                .eq(StringUtils.isNotBlank(currentTenantId()), "tenant_id", currentTenantId()), false);
        if (installation == null) return WebResponse.OK(false);
        installation.setStatus(0);
        return WebResponse.OK(installationService.updateById(installation));
    }

    @ApiOperation("回滚 Solution 到历史版本")
    @PostMapping("/installations/{installationId}/rollback")
    @Permission(path = "/agent/application", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    public WebResponse<Boolean> rollback(@PathVariable String installationId) {
        SolutionInstallation target = installationService.getById(installationId);
        if (target != null && !tenantMatches(target.getTenantId())) target = null;
        if (target == null || Boolean.TRUE.equals(target.getDeleted())) throw new ServerException(404, "安装记录不存在");
        List<SolutionInstallation> active = installationService.list(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<SolutionInstallation>()
                .eq("solution_id", target.getSolutionId()).eq("application_id", target.getApplicationId())
                .eq("status", 1).eq("deleted", false)
                .eq(StringUtils.isNotBlank(currentTenantId()), "tenant_id", currentTenantId()));
        for (SolutionInstallation item : active) {
            item.setStatus(0);
            installationService.updateById(item);
        }
        target.setStatus(1);
        return WebResponse.OK(installationService.updateById(target));
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    private boolean tenantMatches(String tenantId) {
        String current = currentTenantId();
        return StringUtils.isBlank(current) || StringUtils.isBlank(tenantId) || current.equals(tenantId);
    }

    private void requireTenant(Solution solution) {
        if (solution != null && !tenantMatches(solution.getTenantId()))
            throw new ServerException(404, "Solution 不存在");
    }
}
