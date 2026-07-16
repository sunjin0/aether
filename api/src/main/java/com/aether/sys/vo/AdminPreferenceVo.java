package com.aether.sys.vo;

import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
public class AdminPreferenceVo extends BaseEntity {

    @ApiModelProperty(value = "用户ID")
    private String adminId;

    @ApiModelProperty(value = "偏好分类")
    private String category;

    @ApiModelProperty(value = "偏好内容")
    private String content;

    @ApiModelProperty(value = "来源会话ID")
    private String sourceConversationId;

    @ApiModelProperty(value = "来源消息ID")
    private String sourceMessageId;

    @ApiModelProperty(value = "置信度")
    private BigDecimal confidence;

    @ApiModelProperty(value = "状态：0-禁用，1-启用")
    private Integer status;

    private Long current;

    private Long pageSize;
}
