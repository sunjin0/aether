package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("登录请求")
public final class LoginRequests {
    private LoginRequests() { }
    @Data @ApiModel("账号验证请求") public static class VerifyRequest {
        @ApiModelProperty(value = "账号", required = true, example = "admin") private String account;
        @ApiModelProperty(value = "密码", required = true, example = "Password123!") private String password;
    }
    @Data @ApiModel("邮箱登录请求") public static class LoginRequest {
        @ApiModelProperty(value = "邮箱", required = true, example = "admin@example.com") private String email;
        @ApiModelProperty(value = "验证码", required = true, example = "123456") private Integer verificationCode;
    }
    @Data @ApiModel("刷新令牌请求") public static class RefreshTokenRequest {
        @ApiModelProperty(value = "登录时获得的刷新令牌", required = true) private String refreshToken;
    }
    @Data @ApiModel("密码重置请求") public static class ResetPasswordRequest {
        @ApiModelProperty(value = "当前密码", required = true, example = "OldPassword123!") private String oldPassword;
        @ApiModelProperty(value = "新密码", required = true, example = "NewPassword123!") private String password;
    }
    @Data @ApiModel("验证码请求") public static class SendVerificationCodeRequest {
        @ApiModelProperty(value = "邮箱", required = true, example = "admin@example.com") private String email;
    }
}
