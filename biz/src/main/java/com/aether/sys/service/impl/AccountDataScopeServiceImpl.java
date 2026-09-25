package com.aether.sys.service.impl;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.sys.entity.Role;
import com.aether.sys.entity.User;
import com.aether.sys.entity.UserRole;
import com.aether.sys.mapper.RoleMapper;
import com.aether.sys.mapper.UserMapper;
import com.aether.sys.mapper.UserRoleMapper;
import com.aether.sys.service.AccountDataScopeService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/** 账号级资源范围：管理员全量，普通用户仅本人。 */
@Service
public class AccountDataScopeServiceImpl implements AccountDataScopeService {
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final RoleMapper roleMapper;

    public AccountDataScopeServiceImpl(UserMapper userMapper, UserRoleMapper userRoleMapper, RoleMapper roleMapper) {
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.roleMapper = roleMapper;
    }

    @Override
    public String currentUserId() {
        return CurrentUser.userId();
    }

    @Override
    public boolean isAdministrator() {
        String userId = currentUserId();
        if (StringUtils.isBlank(userId)) return false;
        List<String> roleIds = userRoleMapper.selectList(Wrappers.lambdaQuery(UserRole.class)
                        .select(UserRole::getRoleId)
                        .eq(UserRole::getUserId, userId)
                        .eq(UserRole::getDeleted, false))
                .stream().map(UserRole::getRoleId).filter(StringUtils::isNotBlank).collect(Collectors.toList());
        if (roleIds.isEmpty()) return false;
        return roleMapper.selectCount(Wrappers.lambdaQuery(Role.class)
                .in(Role::getId, roleIds)
                .eq(Role::getRoleType, "ADMIN")
                .eq(Role::getDeleted, false)) > 0;
    }

    @Override
    public List<String> readableCreatorIds(String requestedCreatorId) {
        String current = currentUserId();
        if (StringUtils.isBlank(current)) throw new ServerException(401, I18nUtils.getMessage("auth.error.no.permission"));
        if (!isAdministrator()) {
            if (StringUtils.isNotBlank(requestedCreatorId) && !StringUtils.equals(current, requestedCreatorId)) {
                throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
            }
            return Collections.singletonList(current);
        }
        if (StringUtils.isNotBlank(requestedCreatorId)) {
            User user = userMapper.selectOne(Wrappers.lambdaQuery(User.class)
                    .eq(User::getId, requestedCreatorId));
            if (user == null) throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
            return Collections.singletonList(requestedCreatorId);
        }
        return userMapper.selectList(Wrappers.lambdaQuery(User.class)
                        .select(User::getId))
                .stream().map(User::getId).collect(Collectors.toList());
    }

    @Override
    public void assertReadable(String createdBy) {
        if (isAdministrator()) return;
        if (!StringUtils.equals(currentUserId(), createdBy)) {
            throw new ServerException(404, I18nUtils.getMessage("auth.error.no.permission"));
        }
    }

    @Override
    public void assertWritable(String createdBy) {
        if (isAdministrator()) return;
        if (!StringUtils.equals(currentUserId(), createdBy)) {
            throw new ServerException(403, I18nUtils.getMessage("auth.error.no.permission"));
        }
    }
}
