package com.aether.sys.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * <p>
 * 角色资源表
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_role_resource")
public class RoleResource extends BaseEntity {



    /**
     * 角色id
     */
    private String roleId;

    /**
     * 资源id
     */
    private String resourceId;


}
