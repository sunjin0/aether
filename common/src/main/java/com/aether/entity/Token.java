package com.aether.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * SYS 令牌
 *
 * @author sun
 * @since 2024/09/19
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_token")
@ApiModel(value = "SysToken对象", description = "令牌表")
public class Token extends BaseEntity {

    @ApiModelProperty(value = "用户id")
    private String userId;

    @ApiModelProperty(value = "令牌")
    private String token;

    @ApiModelProperty(value = "令牌类型")
    private String refreshToken;
}
