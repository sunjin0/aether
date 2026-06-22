package com.aether.sys.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <p>
 * 资源表
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_resource")
public class Resource extends BaseEntity {

    /**
     * 资源名称（英文）
     */
    private String name;

    /**
     * 资源名称（中文）
     */
    private String nameCn;

    /**
     * 资源路径
     */
    private String path;

    /**
     * 类型
     */
    private String type;

    /**
     * 图标
     */
    private String icon;

    /**
     * 父级资源id
     */
    private String parentId;
    /**
     * 叶子节点
     * true:叶子节点
     * false:非叶子节点
     */
    private Boolean leaf;
    /**
     * 描述
     */
    private String description;


}
