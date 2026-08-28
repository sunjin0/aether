package com.aether.msg.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("短信请求")
public final class SmsRequests {
    private SmsRequests() { }
    @Data @ApiModel("短信列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "短信 ID", example = "1") private String id;
        @ApiModelProperty(value = "手机号", example = "13800138000") private String phone;
        @ApiModelProperty(value = "类型", example = "login") private String type;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
        @ApiModelProperty(value = "用户 ID", example = "1") private String userId;
    }
    @Data @ApiModel("短信保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "短信 ID", example = "1") private String id;
        @ApiModelProperty(value = "用户 ID", example = "1") private String userId;
        @ApiModelProperty(value = "手机号", example = "13800138000") private String phone;
        @ApiModelProperty(value = "验证码", example = "123456") private Integer code;
        @ApiModelProperty(value = "类型", example = "login") private String type;
        @ApiModelProperty(value = "主题", example = "Login verification") private String subject;
        @ApiModelProperty(value = "正文", example = "Your code is 123456") private String body;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
}
