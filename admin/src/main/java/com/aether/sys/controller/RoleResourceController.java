package com.aether.sys.controller;



import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.aether.permission.Permission;
import com.aether.sys.service.RoleResourceService;
import  com.aether.entity.WebResponse;
import  com.aether.sys.entity.RoleResource;
import  com.aether.i18n.I18nUtils;
import  com.aether.validator.ValidEntity;
import  com.aether.sys.vo.RoleResourceVo;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 角色资源表 前端控制器
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Api(tags = "角色资源服务 API")
@RestController
@Permission(path = "/sys/role")
@RequestMapping("/api/sys/role-resource")
public class RoleResourceController {

    @Resource
    private RoleResourceService roleResourceService;

    @ApiOperation("根据角色ID查询权限资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/permission")
    public WebResponse<List<String>> getPermissionByRoleId(@RequestParam @NotBlank String roleId) {
        return WebResponse.OK(roleResourceService.getPermissionByRoleId(roleId));
    }

    @ApiOperation("根据角色ID添加或者修改权限资源")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/role", type = Permission.Type.Write)
    @PostMapping("/save")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"resourceIds", "roleId"})
                                     RoleResourceVo roleResourceVo) {
        List<RoleResource> roleResourceList = roleResourceVo.getResourceIds().stream().map(resourceId -> {
            // 删除角色资源
            if (roleResourceVo.getRoleId() != null) {
                roleResourceService.remove(Wrappers.lambdaQuery(RoleResource.class)
                        .eq(RoleResource::getRoleId, roleResourceVo.getRoleId()));
            }
            // 添加角色资源
            RoleResourceVo roleResource = new RoleResourceVo();
            roleResource.setRoleId(roleResourceVo.getRoleId());
            roleResource.setResourceId(resourceId);
            return roleResource;
        }).collect(Collectors.toList());

        boolean result = roleResourceService.saveBatch(roleResourceList);
        return WebResponse.OK(result ? I18nUtils.getMessage("system.authorize.success") : I18nUtils.getMessage("system.authorize.fail"));
    }
}
