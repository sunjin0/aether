package com.aether.user.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("会员请求")
public final class MemberRequests {
    private MemberRequests() { }
    @Data @ApiModel("会员列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
    }
    @Data @ApiModel("会员保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "会员 ID", example = "1") private String id;
        @ApiModelProperty(value = "用户名", example = "member") private String username;
        @ApiModelProperty(value = "密码", example = "Password123!") private String password;
        @ApiModelProperty(value = "昵称", example = "Aether member") private String nickname;
        @ApiModelProperty(value = "邮箱", example = "member@example.com") private String email;
        @ApiModelProperty(value = "手机号", example = "13800138000") private String phone;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
}
