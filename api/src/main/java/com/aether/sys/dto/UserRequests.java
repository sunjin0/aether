package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.util.List;

@ApiModel("系统用户请求")
public final class UserRequests {
    private UserRequests() { }
    @Data @ApiModel("系统用户列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "性别", example = "male") private String sex;
        @ApiModelProperty(value = "用户类型", example = "admin") private String type;
        @ApiModelProperty(value = "用户名", example = "admin") private String username;
        @ApiModelProperty(value = "手机号", example = "13800138000") private String phone;
        @ApiModelProperty(value = "邮箱", example = "admin@example.com") private String email;
    }
    @Data @ApiModel("系统用户保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "用户 ID", example = "1") private String id;
        @ApiModelProperty(value = "用户名", required = true, example = "admin") private String username;
        @ApiModelProperty(value = "性别", example = "male") private String sex;
        @ApiModelProperty(value = "用户类型", example = "admin") private String type;
        @ApiModelProperty(value = "邮箱", required = true, example = "admin@example.com") private String email;
        @ApiModelProperty(value = "手机号", required = true, example = "13800138000") private String phone;
        @ApiModelProperty(value = "密码", example = "Password123!") private String password;
        @ApiModelProperty(value = "头像 URL", required = true, example = "https://example.com/avatar.png") private String avatar;
        @ApiModelProperty(value = "SMTP 主机", example = "smtp.example.com") private String smtpHost;
        @ApiModelProperty(value = "SMTP 端口", example = "587") private Integer smtpPort;
        @ApiModelProperty(value = "SMTP 安全方式", example = "starttls") private String smtpSecurity;
        @ApiModelProperty(value = "SMTP 授权码", example = "app-password") private String smtpAuthorizationCode;
        @ApiModelProperty(value = "角色 ID 列表", example = "[\"1\", \"2\"]") private List<String> roleIds;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
}
