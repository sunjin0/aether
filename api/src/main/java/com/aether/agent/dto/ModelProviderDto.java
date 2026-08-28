package com.aether.agent.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;

/**
 * 模型供应商 DTO
 */
@Data
@ApiModel("模型供应商创建或更新请求")
public class ModelProviderDto {

    @ApiModelProperty(value = "供应商名称")
    private String name;

    @ApiModelProperty(value = "供应商类型：openai、local、local-openai-compatible、azure、azure-openai、anthropic、qwen-compatible")
    private String type;

    @ApiModelProperty(value = "API基础地址")
    private String apiBaseUrl;

    @ApiModelProperty(value = "API Key")
    private String apiKey;

    /**
     * Legacy field is retained only for schema compatibility; new runtime selections use ModelCatalog.
     */
    @ApiModelProperty(value = "历史默认模型名称（新配置不使用）")
    private String defaultModel;

    @ApiModelProperty(value = "模型上下文窗口大小（token），默认32768")
    @Min(value = 4096, message = "contextWindow不能小于4096")
    @Max(value = 2000000, message = "contextWindow不能大于2000000")
    private Integer contextWindow;

    @ApiModelProperty(value = "是否允许用于会话摘要/记忆压缩出站")
    private Boolean compressionOutboundAllowed;

    @ApiModelProperty(value = "模型处理区域，如 CN/US/EU/GLOBAL")
    private String processingRegion;

    @ApiModelProperty(value = "数据处理许可说明")
    private String dataProcessingPolicy;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;
}
