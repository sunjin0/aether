package com.aether.sys.controller;

import com.aether.entity.WebResponse;
import com.aether.permission.Permission;
import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.service.UserService;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.aether.sys.vo.ServiceAccountVo;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;

import java.util.List;
import java.util.stream.Collectors;
import com.alibaba.fastjson2.JSON;

/** 服务账号管理与 client credentials 令牌签发。 */
@Api(tags = "服务账号 API")
@RestController
public class ServiceAccountController {
    private final ServiceAccountService serviceAccountService;
    private final UserService userService;

    public ServiceAccountController(ServiceAccountService serviceAccountService, UserService userService) {
        this.serviceAccountService = serviceAccountService;
        this.userService = userService;
    }

    @ApiOperation("签发服务账号访问令牌")
    @PostMapping("/api/auth/service-account/token")
    public WebResponse<ServiceAccountTokenVo> token(@RequestBody ServiceAccountTokenDto dto, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(serviceAccountService.issueToken(dto));
    }

    @ApiOperation("服务账号列表")
    @Permission(path = "/sys/admin")
    @PostMapping("/api/sys/service-account/list")
    public WebResponse<List<ServiceAccountVo>> list(@RequestBody(required = false) ServiceAccountVo query) {
        long current = query == null || query.getCurrent() == null ? 1L : query.getCurrent();
        long pageSize = query == null || query.getPageSize() == null ? 20L : Math.min(query.getPageSize(), 100L);
        Page<ServiceAccount> page = serviceAccountService.page(new Page<ServiceAccount>(current, pageSize),
                Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getDeleted, false).orderByDesc(ServiceAccount::getCreatedAt));
        List<ServiceAccountVo> rows = page.getRecords().stream().map(this::vo).collect(Collectors.toList());
        return WebResponse.Page(rows, page.getTotal());
    }

    @ApiOperation("创建服务账号；明文密钥只在本次响应中返回")
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account")
    public WebResponse<ServiceAccountSecretVo> create(@RequestBody ServiceAccountCreateDto dto, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(serviceAccountService.create(dto));
    }

    @ApiOperation("轮换服务账号密钥；旧令牌立即失效")
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account/{id}/rotate-secret")
    public WebResponse<ServiceAccountSecretVo> rotateSecret(@PathVariable String id, HttpServletResponse response) {
        noStore(response);
        return WebResponse.OK(serviceAccountService.rotateSecret(id));
    }

    @ApiOperation("启用或禁用服务账号；状态变更后旧令牌立即失效")
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @PostMapping("/api/sys/service-account/{id}/enabled")
    public WebResponse<Void> enabled(@PathVariable String id, @RequestParam boolean enabled) {
        serviceAccountService.setEnabled(id, enabled);
        return WebResponse.OK((Void) null);
    }

    private ServiceAccountVo vo(ServiceAccount account) {
        ServiceAccountVo result = new ServiceAccountVo(); BeanUtils.copyProperties(account, result);
        result.setRoleIds(userService.getRoleIdsByUserId(account.getUserId()));
        result.setAllowedWorkflowIds(account.getAllowedWorkflowIds() == null ? java.util.Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedWorkflowIds(), String.class));
        return result;
    }

    private void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
    }
}
