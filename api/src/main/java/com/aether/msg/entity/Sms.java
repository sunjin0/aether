package com.aether.msg.entity;

import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 短信消息发送记录
 * </p>
 *
 * @author sun
 * @since 2024-09-11
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("msg_sms")
@ApiModel(value = "MsgSms对象", description = "短信消息发送记录")
public class Sms extends BaseEntity {

    @ApiModelProperty(value = "用户id")
    private String userId;
    @ApiModelProperty(value = "电话")
    private String phone;

    @ApiModelProperty(value = "验证码")
    private Integer code;

    @ApiModelProperty(value = "类型：字典")
    private String type;

    @ApiModelProperty(value = "主题")
    private String subject;

    @ApiModelProperty(value = "内容")
    private String body;

}
