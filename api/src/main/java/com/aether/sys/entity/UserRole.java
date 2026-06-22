package com.aether.sys.entity;


import com.baomidou.mybatisplus.annotation.TableName;
import com.aether.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;


/**
 * <p>
 * 用户角色表
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("sys_user_role")
public class UserRole extends BaseEntity {

    /**
     * 用户id
     */
    private String userId;

    /**
     * 角色id
     */
    private String roleId;


}
