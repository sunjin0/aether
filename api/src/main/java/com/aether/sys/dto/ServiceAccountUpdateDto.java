package com.aether.sys.dto;

import lombok.Data;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.util.List;

/**
 * 服务账号可维护字段；客户端 ID 与密钥不可通过编辑接口变更。
 */
@Data
@ApiModel("服务账号更新请求")
public class ServiceAccountUpdateDto {
    @ApiModelProperty(value = "应用 ID", required = true, example = "app-1")
    private String applicationId;
    @ApiModelProperty(value = "服务账号名称", required = true, example = "Production integration")
    private String name;
    @ApiModelProperty(value = "描述", example = "Used by the production integration")
    private String description;
    @ApiModelProperty(value = "允许的产品 ID", example = "[\"product-1\"]")
    private List<String> allowedProductIds;
    @ApiModelProperty(value = "每小时最大工作流启动次数", example = "100")
    private Integer maxStartsPerHour;
    @ApiModelProperty(value = "每小时最大智能体调用次数", example = "1000")
    private Integer maxAgentCallsPerHour;
}
