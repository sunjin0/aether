package com.aether.sys.controller;

import com.aether.permission.Permission;
import com.aether.sys.service.UserService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.sys.vo.ResourceVo;
import com.aether.sys.vo.UserVo;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@Permission(path = "/sys/admin")
@RequestMapping("/api/sys")
public class LoginController {
    @Resource
    private UserService userService;

    @ApiOperation("验证账号密码")
    @Permission(required = false)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "account", value = "账号", required = true),
            @ApiImplicitParam(name = "password", value = "密码", required = true)
    })
    @PostMapping("/verify")
    public WebResponse<Boolean> verify(@RequestBody
                                       @ValidEntity(fieldNames = {"account", "password"})
                                       UserVo user) throws ServerException {
        return WebResponse.OK(userService.verify(user));
    }

    @ApiOperation("验证帐户邮箱")
    @Permission(required = false)
    @PostMapping("/login")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "email", value = "邮箱", required = true),
            @ApiImplicitParam(name = "verificationCode", value = "验证码", required = true),
    })
    public WebResponse<UserVo> login(@RequestBody
                                        @ValidEntity(fieldNames = {"email", "verificationCode"})
                                       UserVo user) throws ServerException {
        return WebResponse.OK(userService.login(user));
    }

    @ApiOperation("重置密码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/resetPassword")
    @ApiImplicitParam(name = "Authorization", value = "token", required = true, paramType = "header")
    public WebResponse<Boolean> resetPassword(@RequestBody
                                              @ValidEntity(fieldNames = {"oldPassword", "password"})
                                              UserVo user) throws ServerException {
        return WebResponse.OK(userService.resetPassword(user));
    }


    @ApiOperation("登录用户信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<UserVo> info() {
        return WebResponse.OK(userService.detail());
    }

    @ApiOperation("路由")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/getRouters")
    @ApiImplicitParam(name = "Authorization", value = "token", required = true, paramType = "header")
    public WebResponse<List<ResourceVo>> getRouters() {
        return WebResponse.OK(userService.getRouters());
    }

    @ApiOperation("发送验证码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "email", value = "邮箱", required = true),
    })
    @Permission(required = false )
    @PostMapping("/send")
    public WebResponse<Boolean> sendVerificationCode(@RequestBody
                                                     @ValidEntity(fieldNames = {"email"})
                                                     UserVo user) throws ServerException {
        userService.sendVerificationCode(user.getEmail());
        return WebResponse.OK(I18nUtils.getMessage("send.success"));
    }

    @ApiOperation("退出登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/logout")
    public WebResponse<Boolean> logout() {
        boolean logout = userService.logout();
        return WebResponse.OK(I18nUtils.getMessage(logout?"logout.success":"logout.fail"));
    }
}
