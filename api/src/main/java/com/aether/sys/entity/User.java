package com.aether.sys.entity;


import com.aether.entity.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 系统用户表
 * </p>
 *
 * @author sun
 * @since 2024-09-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_user")
@ApiModel(value = "SysUser对象", description = "系统用户表")
public class User extends BaseEntity {


    @ApiModelProperty(value = "用户名")
    private String username;

    @ApiModelProperty(value = "性别")
    private String sex;

    @ApiModelProperty(value = "身份类型")
    private String type;

    @ApiModelProperty(value = "邮箱")
    private String email;

    @ApiModelProperty(value = "电话")
    private String phone;

    @ApiModelProperty(value = "密码")
    private String password;

    @ApiModelProperty(value = "头像")
    private String avatar;

    @ApiModelProperty(value = "发信 SMTP 主机")
    private String smtpHost;

    @ApiModelProperty(value = "发信 SMTP 端口")
    private Integer smtpPort;

    @ApiModelProperty(value = "发信 SMTP 加密方式：ssl/starttls")
    private String smtpSecurity;

    /** 已 AES 加密；任何接口均不得回传明文或密文。 */
    @ApiModelProperty(value = "SMTP 授权码（仅写入）")
    private String smtpAuthorizationCode;

}
