package com.aether.sys.controller;

import com.aether.permission.Permission;
import com.aether.sys.service.UserService;
import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.validator.ValidEntity;
import com.aether.sys.vo.ResourceVo;
import com.aether.sys.vo.UserVo;
import com.aether.sys.dto.LoginRequests;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 提供Login相关的 REST 接口。
 */
@RestController
@Api(tags = "认证与会话 API")
@Permission(path = "/sys/admin")
@RequestMapping("/api/sys")
public class LoginController {
    @Resource
    private UserService userService;

    /**
     * 验证当前请求。
     */
    @ApiOperation("验证账号密码")
    @Permission(required = false)
    @ApiImplicitParams({
            @ApiImplicitParam(name = "account", value = "账号", required = true),
            @ApiImplicitParam(name = "password", value = "密码", required = true)
    })
    @PostMapping("/verify")
    public WebResponse<Boolean> verify(@RequestBody
                                       @ValidEntity(fieldNames = {"account", "password"})
                                        LoginRequests.VerifyRequest request) throws ServerException {
        UserVo user = new UserVo();
        user.setAccount(request.getAccount());
        user.setPassword(request.getPassword());
        return WebResponse.OK(userService.verify(user));
    }

    /**
     * 验证帐户邮箱。
     */
    @ApiOperation("验证帐户邮箱")
    @Permission(required = false)
    @PostMapping("/login")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "email", value = "邮箱", required = true),
            @ApiImplicitParam(name = "verificationCode", value = "验证码", required = true),
    })
    public WebResponse<UserVo> login(@RequestBody
                                     @ValidEntity(fieldNames = {"email", "verificationCode"})
                                      LoginRequests.LoginRequest request) throws ServerException {
        UserVo user = new UserVo();
        user.setEmail(request.getEmail());
        user.setVerificationCode(request.getVerificationCode());
        return WebResponse.OK(userService.login(user));
    }

    /**
     * 重置密码。
     */
    @ApiOperation("重置密码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @PostMapping("/resetPassword")
    @ApiImplicitParam(name = "Authorization", value = "token", required = true, paramType = "header")
    public WebResponse<Boolean> resetPassword(@RequestBody
                                              @ValidEntity(fieldNames = {"oldPassword", "password"})
                                               LoginRequests.ResetPasswordRequest request) throws ServerException {
        UserVo user = new UserVo();
        user.setOldPassword(request.getOldPassword());
        user.setPassword(request.getPassword());
        return WebResponse.OK(userService.resetPassword(user));
    }


    /**
     * 登录用户信息。
     */
    @ApiOperation("登录用户信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/info")
    public WebResponse<UserVo> info() {
        return WebResponse.OK(userService.detail());
    }

    /**
     * 获取Routers。
     */
    @ApiOperation("路由")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/getRouters")
    @ApiImplicitParam(name = "Authorization", value = "token", required = true, paramType = "header")
    public WebResponse<List<ResourceVo>> getRouters() {
        return WebResponse.OK(userService.getRouters());
    }

    /**
     * 发送VerificationCode。
     */
    @ApiOperation("发送验证码")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "email", value = "邮箱", required = true),
    })
    @Permission(required = false)
    @PostMapping("/send")
    public WebResponse<Boolean> sendVerificationCode(@RequestBody
                                                     @ValidEntity(fieldNames = {"email"})
                                                      LoginRequests.SendVerificationCodeRequest request) throws ServerException {
        userService.sendVerificationCode(request.getEmail());
        return WebResponse.OK(I18nUtils.getMessage("send.success"));
    }

    /**
     * 退出登录。
     */
    @ApiOperation("退出登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "Authorization", value = "访问令牌", required = true, dataType = "string", paramType = "header")
    })
    @GetMapping("/logout")
    public WebResponse<Boolean> logout() {
        boolean logout = userService.logout();
        return WebResponse.OK(I18nUtils.getMessage(logout ? "logout.success" : "logout.fail"));
    }
}
