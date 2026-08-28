package com.aether.sys.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import java.math.BigDecimal;

@ApiModel("管理员偏好请求")
public final class AdminPreferenceRequests {
    private AdminPreferenceRequests() { }
    @Data @ApiModel("管理员偏好列表请求") public static class ListRequest {
        @ApiModelProperty(value = "页码", required = true, example = "1") private Long current;
        @ApiModelProperty(value = "每页数量", required = true, example = "20") private Long pageSize;
        @ApiModelProperty(value = "分类", example = "language") private String category;
        @ApiModelProperty(value = "偏好键", example = "response_language") private String keyName;
        @ApiModelProperty(value = "偏好值", example = "English") private String value;
        @ApiModelProperty(value = "状态", example = "1") private Integer status;
    }
    @Data @ApiModel("管理员偏好保存请求") public static class SaveRequest {
        @ApiModelProperty(value = "分类", required = true, example = "language") private String category;
        @ApiModelProperty(value = "偏好键", required = true, example = "response_language") private String keyName;
        @ApiModelProperty(value = "偏好值", required = true, example = "English") private String value;
        @ApiModelProperty(value = "描述", example = "Preferred response language") private String description;
        @ApiModelProperty(value = "优先级", example = "10") private Integer priority;
        @ApiModelProperty(value = "作用域", example = "global") private String scope;
        @ApiModelProperty(value = "作用域详情", example = "") private String scopeDetail;
        @ApiModelProperty(value = "来源", example = "explicit") private String source;
        @ApiModelProperty(value = "置信度", example = "1.00") private BigDecimal confidence;
        @ApiModelProperty(value = "使用次数", example = "0") private Integer usageCount;
        @ApiModelProperty(value = "最后使用时间（毫秒）", example = "1735689600000") private Long lastUsedAt;
        @ApiModelProperty(value = "过期时间（毫秒）", example = "1735689600000") private Long expiresAt;
        @ApiModelProperty(value = "衰减率", example = "0.10") private BigDecimal decayRate;
        @ApiModelProperty(value = "有效分数", example = "0.90") private BigDecimal effectiveScore;
        @ApiModelProperty(value = "状态", example = "1") private Integer status;
    }
    @Data @ApiModel("管理员偏好状态请求") public static class StatusRequest {
        @ApiModelProperty(value = "状态", required = true, example = "1") private Integer status;
    }
    @Data @ApiModel("管理员偏好覆盖请求") public static class OverrideRequest {
        @ApiModelProperty(value = "偏好值", required = true, example = "English") private String value;
    }
}
