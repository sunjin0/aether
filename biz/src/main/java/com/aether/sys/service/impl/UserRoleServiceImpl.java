package com.aether.sys.service.impl;


import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.sys.mapper.UserRoleMapper;
import com.aether.sys.service.UserRoleService;
import com.aether.sys.entity.UserRole;
import com.aether.sys.entity.Role;
import com.aether.sys.mapper.RoleMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.aether.local.CurrentUser;

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
    @Autowired
    private RoleMapper roleMapper;

    /**
 * 保存用户角色Ids。
 */
@Override
    public boolean saveUserRoleIds(String userId, List<String> roleIds) {
        String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (tenantId != null && !tenantId.trim().isEmpty() && roleIds != null) {
            for (String roleId : roleIds) {
                Role role = roleMapper.selectById(roleId);
                if (role == null || !tenantId.equals(role.getTenantId())) {
                    throw new com.aether.exception.ServerException(403, "角色不属于当前租户");
                }
            }
        }
        // 删除该用户所有角色
        super.remove(Wrappers.lambdaQuery(UserRole.class).eq(UserRole::getUserId, userId));
        // 添加角色资源
        List<UserRole> userRoles = roleIds.stream().map(roleId -> {
            UserRole userRole = new UserRole();
            if (CurrentUser.getUser() != null) userRole.setTenantId(CurrentUser.getUser().get("tenantId"));
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            return userRole;
        }).collect(Collectors.toList());
        return super.saveBatch(userRoles);
    }
}
