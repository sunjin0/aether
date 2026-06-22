package com.aether.msg.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 邮箱消息发送记录
 * </p>
 *
 * @author sun
 * @since 2024-09-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("msg_email")
@ApiModel(value = "MsgEmail对象", description = "邮箱消息发送记录")
public class Email extends BaseEntity {
    @ApiModelProperty(value = "用户id")
    private String userId;
    @ApiModelProperty(value = "邮箱")
    private String email;

    @ApiModelProperty(value = "类型：字典")
    private String type;

    @ApiModelProperty(value = "验证码")
    private Integer code;

    @ApiModelProperty(value = "主题")
    private String subject;

    @ApiModelProperty(value = "内容")
    private String body;

}
