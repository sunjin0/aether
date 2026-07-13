package com.aether.agent.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 工具
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("agent_tool")
@ApiModel(value = "AgentTool对象", description = "工具")
public class AgentTool extends BaseEntity {

    @ApiModelProperty(value = "工具名称")
    private String name;

    @ApiModelProperty(value = "工具编码（唯一）")
    private String code;

    @ApiModelProperty(value = "描述")
    private String description;

    @ApiModelProperty(value = "工具类型：http")
    private String type;

    /**
     * 内建工具的函数参数 Schema，不持久化到 agent_tool 表。
     */
    @TableField(exist = false)
    private String parametersSchema;

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

    @ApiModelProperty(value = "超时时间（毫秒），默认30000")
    private Integer timeoutMs;

    @ApiModelProperty(value = "缓存TTL（秒），默认0表示不缓存")
    private Integer cacheTtlSeconds;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    @ApiModelProperty(value = "排序号")
    private Integer sort;

    @ApiModelProperty(value = "备注")
    private String remark;
}
