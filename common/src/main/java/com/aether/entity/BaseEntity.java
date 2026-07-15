package com.aether.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import io.swagger.annotations.ApiModelProperty;
import lombok.Data;

import java.io.Serializable;

@Data
public class BaseEntity implements Serializable {
    @ApiModelProperty(value = "主键")
    @TableId(type = IdType.ASSIGN_ID)
    private String id;
    @ApiModelProperty(value = "创建时间")
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Long createdAt;

    @ApiModelProperty(value = "更新时间")
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Long updatedAt;


    @ApiModelProperty(value = "排序号")
    @TableField(value = "sort_num", fill = FieldFill.INSERT)
    private Integer sortNum;

    @ApiModelProperty(value = "删除状态")
    @TableField(value = "deleted", fill = FieldFill.INSERT)
    @TableLogic(value = "false", delval = "true")
    private Boolean deleted;
    /**
     * 默认为0
     */
    @ApiModelProperty(value = "状态 默认0")
    @TableField(fill = FieldFill.INSERT)
    private Integer state;
}
