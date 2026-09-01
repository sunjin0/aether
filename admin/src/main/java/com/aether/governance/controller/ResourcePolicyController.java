package com.aether.governance.controller;

import com.aether.entity.WebResponse;
import com.aether.governance.entity.ResourcePolicyRule;
import com.aether.governance.service.ResourcePolicyService;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理 Agent/Tool 资源策略；策略接口本身不返回任何凭据内容。 */
@Api(tags = "Agent 资源策略 API")
@RestController
@Permission(path = "/agent/run")
@RequestMapping("/api/agent/governance/resource-policy")
public class ResourcePolicyController {
    private final ResourcePolicyService service;

    public ResourcePolicyController(ResourcePolicyService service) {
        this.service = service;
    }

    @ApiOperation("查询资源策略")
    @GetMapping
    public WebResponse<List<ResourcePolicyRule>> list(@RequestParam(required = false) String subjectType,
                                                       @RequestParam(required = false) String subjectId,
                                                       @RequestParam(required = false) String resourceType,
                                                       @RequestParam(required = false) String resourceId) {
        com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResourcePolicyRule> query =
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ResourcePolicyRule>()
                        .eq("deleted", false);
        if (StringUtils.isNotBlank(subjectType)) query.eq("subject_type", subjectType);
        if (StringUtils.isNotBlank(subjectId)) query.eq("subject_id", subjectId);
        if (StringUtils.isNotBlank(resourceType)) query.eq("resource_type", resourceType);
        if (StringUtils.isNotBlank(resourceId)) query.eq("resource_id", resourceId);
        return WebResponse.OK(service.list(query));
    }

    @ApiOperation("保存资源策略")
    @PostMapping
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    public WebResponse<String> save(@RequestBody ResourcePolicyRule request) {
        if (request == null || StringUtils.isAnyBlank(request.getSubjectType(), request.getSubjectId(),
                request.getResourceType(), request.getResourceId(), request.getAction(), request.getEffect()))
            return WebResponse.Error(400, "subject、resource、action 和 effect 不能为空");
        if (!"ALLOW".equalsIgnoreCase(request.getEffect()) && !"DENY".equalsIgnoreCase(request.getEffect()))
            return WebResponse.Error(400, "effect 必须为 ALLOW 或 DENY");
        if (request.getId() == null) service.save(request); else service.updateById(request);
        return WebResponse.OK(request.getId());
    }

    @ApiOperation("删除资源策略")
    @DeleteMapping("/{id}")
    @Permission(path = "/agent/run", type = Permission.Type.Write)
    public WebResponse<Boolean> delete(@PathVariable String id) {
        return WebResponse.OK(service.removeById(id));
    }
}
