package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("资源请求")
public final class ResourceRequests {
    private ResourceRequests() { }

    @Data @ApiModel("资源列表请求")
    public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "英文名称", example = "System") private String name;
        @ApiModelProperty(value = "中文名称", example = "System management") private String nameCn;
        @ApiModelProperty(value = "资源路径", example = "/sys/admin") private String path;
        @ApiModelProperty(value = "资源类型", example = "route") private String type;
        @ApiModelProperty(value = "描述", example = "Manage administrators") private String description;
    }

    @Data @ApiModel("资源保存请求")
    public static class SaveRequest {
        @ApiModelProperty(value = "英文名称", required = true, example = "System") private String name;
        @ApiModelProperty(value = "中文名称", required = true, example = "System management") private String nameCn;
        @ApiModelProperty(value = "资源路径", example = "/sys/admin") private String path;
        @ApiModelProperty(value = "资源类型", required = true, example = "route") private String type;
        @ApiModelProperty(value = "图标", example = "settings") private String icon;
        @ApiModelProperty(value = "父资源 ID", example = "0") private String parentId;
        @ApiModelProperty(value = "是否为叶子资源", example = "false") private Boolean leaf;
        @ApiModelProperty(value = "描述", example = "Manage administrators") private String description;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }

    @Data @ApiModel("资源更新请求")
    public static class UpdateRequest extends SaveRequest {
        @ApiModelProperty(value = "资源 ID", required = true, example = "1") private String id;
    }
}
