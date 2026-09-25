package com.aether.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/** 账号级资源的统一归属和修改审计字段。 */
@Data
@EqualsAndHashCode(callSuper = true)
public abstract class AccountOwnedEntity extends BaseEntity {
    @ApiModelProperty(value = "创建账号 ID")
    @TableField(value = "created_by", fill = FieldFill.INSERT)
    private String createdBy;

    @ApiModelProperty(value = "最后修改账号 ID")
    @TableField(value = "updated_by", fill = FieldFill.INSERT_UPDATE)
    private String updatedBy;
}
