package com.aether.agent.sandbox.dto;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

/**
 * Administrator request to publish a new immutable template policy version.
 */
@Data
@ApiModel("沙箱模板策略发布请求")
public class SandboxTemplateVersionPublishDto {
    @ApiModelProperty(value = "不可变的模板配置快照 JSON", required = true, example = "{\"image\":\"python:3.11\",\"timeoutSeconds\":60}")
    private String configSnapshot;
    @ApiModelProperty(value = "策略版本标签", required = true, example = "2026.01")
    private String policyVersion;
    @ApiModelProperty(value = "风险分类", required = true, example = "MEDIUM")
    private String riskLevel;
}
