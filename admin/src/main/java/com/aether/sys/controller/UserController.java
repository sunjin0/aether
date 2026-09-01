package com.aether.sys.controller;


import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.aether.permission.Permission;
import com.aether.sys.service.UserService;
import com.aether.sys.service.RoleService;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.Role;
import com.aether.entity.WebResponse;
import com.aether.entity.Option;
import com.aether.sys.entity.User;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.utils.AesUtil;
import com.aether.sys.vo.UserVo;
import com.aether.sys.dto.UserRequests;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotBlank;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 提供用户相关的 REST 接口。
 */
@Api(tags = "系统用户服务 API")
@Validated
@RestController
@Permission(path = "/sys/admin")
@RequestMapping("/api/sys/admin")
public class UserController {

    private final UserService userService;

    private final PasswordEncoder encoder;
    private final RoleService roleService;


    /**
     * 创建 {@code UserController} 实例。
     */
    @Autowired
    public UserController(UserService userService, org.springframework.security.crypto.password.PasswordEncoder encoder, RoleService roleService) {
        this.userService = userService;
        this.encoder = encoder;
        this.roleService = roleService;
    }


    /**
     * 管理员列表。
     */
    @ApiOperation("管理员列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<UserVo>> list(@RequestBody UserRequests.ListRequest user) throws ServerException {
        Page<User> userPage = new Page<>(user.getCurrent(), user.getPageSize());
        Wrapper<User> queryWrapper = Wrappers.lambdaQuery(User.class)
                .eq(StringUtils.isNotBlank(currentTenantId()), User::getTenantId, currentTenantId())
                .like(StringUtils.isNotBlank(user.getSex()), User::getSex, user.getSex())
                .like(StringUtils.isNotBlank(user.getType()), User::getType, user.getType())
                .like(StringUtils.isNotBlank(user.getUsername()), User::getUsername, user.getUsername())
                .like(StringUtils.isNotBlank(user.getPhone()), User::getPhone, user.getPhone())
                .like(StringUtils.isNotBlank(user.getEmail()), User::getEmail, user.getEmail())
                .orderByDesc(User::getCreatedAt);
        Page<User> page = userService.page(userPage, queryWrapper);
        List<UserVo> userVos = page.getRecords().stream().map(item -> {
            UserVo userVo = new UserVo();
            item.setPassword(null);
            item.setSmtpAuthorizationCode(null);
            BeanUtils.copyProperties(item, userVo);
            userVo.setRoleIds(userService.getRoleIdsByUserId(item.getId()));
            return userVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(userVos, page.getTotal());
    }

    /**
     * 查询管理员下拉选项，仅返回用户名和用户 ID，不返回密码等账户字段。
     */
    @ApiOperation("管理员下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options() {
        com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<User> optionsQuery = Wrappers.lambdaQuery(User.class)
                        .eq(User::getDeleted, false);
        if (StringUtils.isNotBlank(currentTenantId())) optionsQuery.eq(User::getTenantId, currentTenantId());
        List<Option> options = userService.list(optionsQuery.orderByAsc(User::getUsername))
                .stream().map(item -> new Option(item.getUsername(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }


    /**
     * 详情当前请求。
     */
    @ApiOperation("管理员详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "管理员ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<User> detail(@RequestParam @NotBlank String id) throws ServerException {
        User user = userService.getById(id);
        user.setPassword(null);
        user.setSmtpAuthorizationCode(null);
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        userVo.setRoleIds(userService.getRoleIdsByUserId(user.getId()));
        return WebResponse.OK(userVo);
    }

    /**
     * 保存当前请求。
     */
    @ApiOperation("管理员保存")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/add")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"username", "phone", "email", "avatar"})
                                      UserRequests.SaveRequest request) throws ServerException {
        UserVo User = new UserVo();
        BeanUtils.copyProperties(request, User);
        User.setTenantId(currentTenantId());
        // 密码加密
        if (StringUtils.isNotEmpty(User.getPassword())) {
            User.setPassword(encoder.encode(User.getPassword()));
        }
        encryptSmtpAuthorizationCode(User, null);
        if (User.getRoleIds() != null) {
            validateRoleTenants(User.getRoleIds());
            userService.bindRole(User.getId(), User.getRoleIds());
        }
        boolean saved = userService.save(User);
        return WebResponse.OK(saved ? I18nUtils.getMessage("system.admin.create.success") : I18nUtils.getMessage("system.admin.create.fail"), saved);
    }

    /**
     * 更新当前请求。
     */
    @ApiOperation("管理员修改")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody
                                       @ValidEntity(fieldNames = {"username", "phone", "email", "avatar"})
                                        UserRequests.SaveRequest request) throws ServerException {
        UserVo User = new UserVo();
        BeanUtils.copyProperties(request, User);
        // 密码加密
        if (StringUtils.isNotEmpty(User.getPassword())) {
            User.setPassword(encoder.encode(User.getPassword()));
        }
        User existing = userService.getById(User.getId());
        User.setTenantId(existing == null ? currentTenantId() : existing.getTenantId());
        encryptSmtpAuthorizationCode(User, existing);
        if (User.getRoleIds() != null) {
            validateRoleTenants(User.getRoleIds());
            userService.bindRole(User.getId(), User.getRoleIds());
        }
        boolean update = userService.updateById(User);
        return WebResponse.OK(update ? I18nUtils.getMessage("system.admin.update.success") : I18nUtils.getMessage("system.admin.update.fail"), update);
    }

    private String currentTenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    private void validateRoleTenants(List<String> roleIds) {
        String tenantId = currentTenantId();
        if (StringUtils.isBlank(tenantId)) return;
        for (String roleId : roleIds) {
            Role role = roleService.getById(roleId);
            if (role == null || !tenantId.equals(role.getTenantId()))
                throw new ServerException(403, "角色不属于当前租户");
        }
    }

    /**
     * 删除当前请求。
     */
    @ApiOperation("管理员删除")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "管理员ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @DeleteMapping("/delete")
    public WebResponse<Boolean> delete(@RequestParam @NotBlank String id) throws ServerException {
        boolean update = userService.remove(Wrappers.lambdaUpdate(User.class)
                .eq(User::getId, id));
        return WebResponse.OK(update ? I18nUtils.getMessage("system.admin.delete.success") : I18nUtils.getMessage("system.admin.delete.fail"), update);
    }

    /** 加密新授权码；编辑时空值保留旧配置，所有读取接口均清空该字段。 */
    private void encryptSmtpAuthorizationCode(User user, User existing) {
        if (StringUtils.isNotBlank(user.getSmtpAuthorizationCode())) {
            user.setSmtpAuthorizationCode(AesUtil.encrypt(user.getSmtpAuthorizationCode()));
        } else if (existing != null) {
            user.setSmtpAuthorizationCode(existing.getSmtpAuthorizationCode());
        }
    }

}
