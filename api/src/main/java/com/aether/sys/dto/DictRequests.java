package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@ApiModel("字典请求")
public final class DictRequests {
    private DictRequests() { }
    @Data @ApiModel("字典列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "字典 ID", example = "1") private String id;
        @ApiModelProperty(value = "编码", example = "gender") private String code;
        @ApiModelProperty(value = "名称", example = "Gender") private String name;
        @ApiModelProperty(value = "中文名称", example = "Gender") private String nameCn;
        @ApiModelProperty(value = "值", example = "male") private String val;
        @ApiModelProperty(value = "备注", example = "User gender") private String remark;
    }
    @Data @ApiModel("字典保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "编码", required = true, example = "gender") private String code;
        @ApiModelProperty(value = "父级编码", example = "user") private String parent;
        @ApiModelProperty(value = "名称", required = true, example = "Gender") private String name;
        @ApiModelProperty(value = "中文名称", required = true, example = "Gender") private String nameCn;
        @ApiModelProperty(value = "值", example = "male") private String val;
        @ApiModelProperty(value = "备注", example = "User gender") private String remark;
        @ApiModelProperty(value = "排序号", example = "1") private Integer sortNum;
        @ApiModelProperty(value = "状态", example = "0") private Integer state;
    }
    @Data @ApiModel("字典更新请求") public static class UpdateRequest extends SaveRequest {
        @ApiModelProperty(value = "字典 ID", required = true, example = "1") private String id;
    }
}
