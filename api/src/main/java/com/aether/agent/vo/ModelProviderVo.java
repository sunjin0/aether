package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 模型供应商 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ModelProviderVo extends BaseEntity {

    @ApiModelProperty(value = "供应商名称")
    private String name;

    @ApiModelProperty(value = "供应商类型：openai、azure、anthropic、local")
    private String type;

    @ApiModelProperty(value = "API基础地址")
    private String apiBaseUrl;

    @ApiModelProperty(value = "API Key")
    private String apiKey;

    @ApiModelProperty(value = "默认模型名称")
    private String defaultModel;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;

    private Long current;
    private Long pageSize;
}
