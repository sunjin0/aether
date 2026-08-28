package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("配置请求")
public final class ConfigRequests {
    private ConfigRequests() { }
    @Data @ApiModel("配置列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "配置 ID", example = "1") private String id;
        @ApiModelProperty(value = "编码", example = "site.name") private String code;
        @ApiModelProperty(value = "名称", example = "Site name") private String name;
        @ApiModelProperty(value = "值", example = "Aether") private String value;
        @ApiModelProperty(value = "备注", example = "Displayed site name") private String remark;
    }
    @Data @ApiModel("配置保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "编码", required = true, example = "site.name") private String code;
        @ApiModelProperty(value = "父级编码", example = "site") private String parent;
        @ApiModelProperty(value = "名称", required = true, example = "Site name") private String name;
        @ApiModelProperty(value = "值", example = "Aether") private String value;
        @ApiModelProperty(value = "备注", example = "Displayed site name") private String remark;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
    @Data @ApiModel("配置更新请求") public static class UpdateRequest extends SaveRequest {
        @ApiModelProperty(value = "配置 ID", required = true, example = "1") private String id;
    }
}
