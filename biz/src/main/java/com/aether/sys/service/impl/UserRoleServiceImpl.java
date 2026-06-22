package com.aether.sys.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.sys.mapper.UserRoleMapper;
import com.aether.sys.service.UserRoleService;
import com.aether.sys.entity.UserRole;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>
 * 用户角色表 服务实现类
 * </p>
 *
 * @author sun
 * @since 2024-11-12
 */
@Service
public class UserRoleServiceImpl extends ServiceImpl<UserRoleMapper, UserRole> implements UserRoleService {

    @Override
    public boolean saveUserRoleIds(String userId, List<String> roleIds) {
        // 删除该用户所有角色
        super.remove(Wrappers.lambdaQuery(UserRole.class).eq(UserRole::getUserId, userId));
        // 添加角色资源
        List<UserRole> userRoles = roleIds.stream().map(roleId -> {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            return userRole;
        }).collect(Collectors.toList());
        return super.saveBatch(userRoles);
    }
}
