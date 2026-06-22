package com.aether.sys.service;

import com.aether.sys.entity.RoleResource;

import com.baomidou.mybatisplus.extension.service.IService;


import java.util.List;

/**
 * <p>
 * 角色资源表 服务类
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
public interface RoleResourceService extends IService<RoleResource> {

    List<String> getPermissionByRoleId(String roleId);
}
