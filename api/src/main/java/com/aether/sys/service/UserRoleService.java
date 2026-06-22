package com.aether.sys.service;

import com.aether.sys.entity.UserRole;

import com.baomidou.mybatisplus.extension.service.IService;


import java.util.List;

/**
 * <p>
 * 用户角色表 服务类
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
public interface UserRoleService extends IService<UserRole> {
    /**
     * 用户绑定角色
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID
     * @return boolean
     */
    boolean saveUserRoleIds(String userId, List<String> roleIds);
}
