package com.aether.agent.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 工具 VO
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AgentToolVo extends BaseEntity {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "工具类型：http")
    private String type;

    @ApiModelProperty(value = "HTTP方法：GET、POST")
    private String httpMethod;

    @ApiModelProperty(value = "HTTP请求地址")
    private String httpUrl;

    @ApiModelProperty(value = "请求头模板（JSON格式）")
    private String httpHeaders;

    @ApiModelProperty(value = "请求体模板（支持占位符）")
    private String httpBodyTemplate;

    @ApiModelProperty(value = "响应提取规则（JSONPath或正则）")
    private String responseExtractRule;

    @ApiModelProperty(value = "超时时间（毫秒）")
    private Integer timeoutMs;

    @ApiModelProperty(value = "缓存TTL（秒）")
    private Integer cacheTtlSeconds;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;

    private Long current;
    private Long pageSize;
}
