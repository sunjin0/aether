package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("角色请求")
public final class RoleRequests {
    private RoleRequests() { }
    @Data @ApiModel("角色列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "角色名称", example = "Administrator") private String name;
        @ApiModelProperty(value = "描述", example = "Full access") private String description;
    }
    @Data @ApiModel("角色保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "角色名称", required = true, example = "Administrator") private String name;
        @ApiModelProperty(value = "描述", example = "Full access") private String description;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
    @Data @ApiModel("角色更新请求") public static class UpdateRequest extends SaveRequest {
        @ApiModelProperty(value = "角色 ID", required = true, example = "1") private String id;
    }
}
