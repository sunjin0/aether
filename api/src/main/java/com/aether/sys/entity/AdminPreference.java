package com.aether.sys.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.math.BigDecimal;

/**
 * Long-term user preference extracted from conversations.
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_admin_preference")
@ApiModel(value = "AdminPreference", description = "后台用户长期偏好")
public class AdminPreference extends BaseEntity {

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
}
