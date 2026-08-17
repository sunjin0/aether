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
 * 系统字典表
 * </p>
 *
 * @author sun
 * @since 2024-09-03
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_dict")
@ApiModel(value = "SysDict对象", description = "系统字典表")
public class Dict extends BaseEntity {


    @ApiModelProperty(value = "代码")
    private String code;

    @ApiModelProperty(value = "父类")
    private String parent;

    @ApiModelProperty(value = "默认名")
    private String name;

    @ApiModelProperty(value = "中文名")
    private String nameCn;

    @ApiModelProperty(value = "值")
    private String val;

    @ApiModelProperty(value = "备注")
    private String remark;

}
