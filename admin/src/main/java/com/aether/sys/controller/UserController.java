package com.aether.sys.controller;



import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import  com.aether.permission.Permission;
import  com.aether.sys.service.UserService;
import  com.aether.entity.WebResponse;
import  com.aether.entity.Option;
import  com.aether.sys.entity.User;
import  com.aether.exception.ServerException;
import  com.aether.i18n.I18nUtils;
import  com.aether.validator.ValidEntity;
import  com.aether.sys.vo.UserVo;
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

@Api(tags = "系统用户服务 API")
@Validated
@RestController
@Permission(path = "/sys/admin")
@RequestMapping("/api/sys/admin")
public class UserController {

    private final UserService userService;

    private final PasswordEncoder encoder;


    @Autowired
    public UserController(UserService userService, org.springframework.security.crypto.password.PasswordEncoder encoder) {
        this.userService = userService;
        this.encoder = encoder;
    }


    @ApiOperation("管理员列表")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/list")
    public WebResponse<List<UserVo>> list(@RequestBody UserVo user) throws ServerException {
        Page<User> userPage = new Page<>(user.getCurrent(), user.getPageSize());
        Wrapper<User> queryWrapper = Wrappers.lambdaQuery(User.class)
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
            BeanUtils.copyProperties(item, userVo);
            userVo.setRoleIds(userService.getRoleIdsByUserId(item.getId()));
            return userVo;
        }).collect(Collectors.toList());
        return WebResponse.Page(userVos, page.getTotal());
    }

    @ApiOperation("管理员下拉选项")
    @Permission(required = false)
    @GetMapping("/options")
    public WebResponse<List<Option>> options() {
        List<Option> options = userService.list(Wrappers.lambdaQuery(User.class)
                        .eq(User::getDeleted, false).orderByAsc(User::getUsername))
                .stream().map(item -> new Option(item.getUsername(), item.getId())).collect(Collectors.toList());
        return WebResponse.OK(options);
    }


    @ApiOperation("管理员详情")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "id", value = "管理员ID", required = true),
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<User> detail(@RequestParam @NotBlank String id) throws ServerException {
        User user = userService.getById(id);
        user.setPassword(null);
        UserVo userVo = new UserVo();
        BeanUtils.copyProperties(user, userVo);
        userVo.setRoleIds(userService.getRoleIdsByUserId(user.getId()));
        return WebResponse.OK(userVo);
    }

    @ApiOperation("管理员保存")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/add")
    public WebResponse<Boolean> save(@RequestBody
                                     @ValidEntity(fieldNames = {"username", "phone", "email", "avatar"})
                                     UserVo User) throws ServerException {
        // 密码加密
        if (StringUtils.isNotEmpty(User.getPassword())) {
            User.setPassword(encoder.encode(User.getPassword()));
        }
        if (User.getRoleIds() != null) {
            userService.bindRole(User.getId(), User.getRoleIds());
        }
        boolean saved = userService.save(User);
            return WebResponse.OK(saved ? I18nUtils.getMessage("add.success") : I18nUtils.getMessage("add.fail"), true);
    }
    @ApiOperation("管理员修改")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @Permission(path = "/sys/admin", type = Permission.Type.Write)
    @Transactional(rollbackFor = Exception.class)
    @PostMapping("/update")
    public WebResponse<Boolean> update(@RequestBody
                                       @ValidEntity(fieldNames = {"username", "phone", "email", "avatar"})
                                       UserVo User)throws ServerException {
        // 密码加密
        if (StringUtils.isNotEmpty(User.getPassword())) {
            User.setPassword(encoder.encode(User.getPassword()));
        }
        if (User.getRoleIds() != null) {
            userService.bindRole(User.getId(), User.getRoleIds());
        }
        boolean update = userService.updateById(User);
        return WebResponse.OK(update ? I18nUtils.getMessage("update.success") : I18nUtils.getMessage("update.fail"), true);
    }

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
        return WebResponse.OK(update ? I18nUtils.getMessage("delete.success") : I18nUtils.getMessage("delete.fail"), update);
    }

}
