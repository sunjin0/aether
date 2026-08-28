package com.aether.msg.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("邮件请求")
public final class EmailRequests {
    private EmailRequests() { }
    @Data @ApiModel("邮件列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "邮件 ID", example = "1") private String id;
        @ApiModelProperty(value = "邮箱地址", example = "user@example.com") private String email;
        @ApiModelProperty(value = "主题", example = "Welcome") private String subject;
        @ApiModelProperty(value = "正文", example = "Welcome to Aether") private String body;
        @ApiModelProperty(value = "类型", example = "notification") private String type;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
        @ApiModelProperty(value = "用户 ID", example = "1") private String userId;
    }
    @Data @ApiModel("邮件保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "邮件 ID", example = "1") private String id;
        @ApiModelProperty(value = "用户 ID", example = "1") private String userId;
        @ApiModelProperty(value = "邮箱地址", example = "user@example.com") private String email;
        @ApiModelProperty(value = "类型", example = "notification") private String type;
        @ApiModelProperty(value = "验证码", example = "123456") private Integer code;
        @ApiModelProperty(value = "主题", example = "Welcome") private String subject;
        @ApiModelProperty(value = "正文", example = "Welcome to Aether") private String body;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
}
