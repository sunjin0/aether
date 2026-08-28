package com.aether.agent.product.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

@Data
@ApiModel("智能体产品档案列表查询")
public class AgentProductProfileQueryDto {
    @ApiModelProperty(value = "按应用 ID 筛选", example = "app-support")
    private String applicationId;
    @ApiModelProperty(value = "按产品名称筛选", example = "Support")
    private String name;
    @ApiModelProperty(value = "按产品类型筛选", example = "AGENT")
    private String productType;
    @ApiModelProperty(value = "按状态筛选：0-禁用，1-启用", example = "1")
    private Integer status;
    @ApiModelProperty(value = "从 1 开始的页码；省略时使用默认值", example = "1")
    private Long current;
    @ApiModelProperty(value = "每页数量；省略时使用默认值", example = "20")
    private Long pageSize;
}
