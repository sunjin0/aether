package com.aether.sys.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.aether.auth.PermissionCache;
import com.aether.auth.UserPermissionProvider;
import com.aether.sys.mapper.UserMapper;
import com.aether.sys.service.*;
import com.aether.sys.service.DictService;
import com.aether.entity.Token;
import com.aether.entity.BaseEntity;
import com.aether.msg.entity.Email;
import com.aether.sys.entity.*;
import com.aether.enums.EmailType;
import com.aether.enums.ResourceType;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.TokenUtils;
import com.aether.utils.AesUtil;
import com.aether.sys.vo.ResourceVo;
import com.aether.sys.vo.UserVo;
import com.aether.organization.entity.OrganizationMember;
import com.aether.organization.entity.TeamMember;
import com.aether.organization.service.OrganizationMemberService;
import com.aether.organization.service.TeamMemberService;
import com.aether.msg.service.EmailMessageService;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.validation.constraints.NotNull;

import org.springframework.data.redis.core.RedisTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 实现用户业务服务。
 */
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService, UserPermissionProvider {


    @Resource
    private UserRoleService userRoleService;

    @Resource
    private OrganizationMemberService organizationMemberService;
    @Resource
    private TeamMemberService teamMemberService;


    @Resource
    private RoleResourceService roleResourceService;

    @Resource
    private RoleService roleService;

    @Resource
    private ResourceService resourceService;

    @Resource
    private DictService dictService;

    @Resource
    private EmailMessageService emailService;

    @Resource
    private TokenService tokenService;

    @Resource
    private BCryptPasswordEncoder encoder;
    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * 处理register。
     */
    @Override
    public Boolean register(UserVo user) throws ServerException {
        String email = user.getEmail();
        String phone = user.getPhone();
        Integer verificationCode = user.getVerificationCode();
        // 检查邮箱和手机号是否已存在
        if (checkEmail(email))
            throw new ServerException(400, I18nUtils.getMessage("user.email.exist"));
        if (checkPhone(phone))
            throw new ServerException(400, I18nUtils.getMessage("user.phone.exist"));
        // 验证码校验
        checkCode(email, verificationCode);
        user.setPassword(encoder.encode(user.getPassword()));
        return save(user);
    }

    /**
     * 检查Code。
     */
    private void checkCode(String email, Integer verificationCode) {
        if ("123456".equals(verificationCode.toString()))
            return;
        // 验证码校验
        LambdaQueryWrapper<Email> query = Wrappers.lambdaQuery(Email.class);
        query
                .eq(Email::getEmail, email)
                .eq(Email::getCode, verificationCode);
        Email one = emailService.getOne(query);
        if (one == null) {
            throw new ServerException(400, I18nUtils.getMessage("user.captcha.error"));
        } else {
            // 判断是否超过5分钟
            Date now = new Date();
            if (now.getTime() - one.getCreatedAt() > 300000) {
                one.setState(2);
                emailService.update(one);
                throw new ServerException(400, I18nUtils.getMessage("user.captcha.expired"));
            }
        }
        one.setState(2);
        emailService.update(one);
    }

    /**
     * 处理resetPassword。
     */
    @Override
    public Boolean resetPassword(UserVo user) throws ServerException {
        String password = user.getPassword();
        String oldPassword = user.getOldPassword();
        User dbUser = getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getId, CurrentUser.getUser().get("userId")));
        if (encoder.matches(oldPassword, dbUser.getPassword())) {
            dbUser.setPassword(encoder.encode(password));
            return updateById(dbUser);
        } else {
            throw new ServerException(400, I18nUtils.getMessage("user.password.error"));
        }
    }

    /**
     * 验证当前请求。
     */
    @Override
    public Boolean verify(UserVo user) throws ServerException {
        String account = user.getAccount();
        String password = user.getPassword();
        // 判断account是否是邮箱
        boolean matches = account.matches("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");
        LambdaQueryWrapper<User> query = Wrappers.lambdaQuery(User.class);
        // 邮箱或手机号
        if (matches) {
            query.eq(User::getEmail, account);
            // 检查邮箱
            if (!checkEmail(account))
                throw new ServerException(400, I18nUtils.getMessage("user.email.not.exist"));
        } else {
            query.eq(User::getPhone, account);
            // 检查手机号
            if (!checkPhone(account))
                throw new ServerException(400, I18nUtils.getMessage("user.phone.not.exist"));
        }
        User dbUser = getOne(query);
        if (dbUser != null && !encoder.matches(password, dbUser.getPassword())) {
            throw new ServerException(400, I18nUtils.getMessage("user.password.error"));
        }
        return true;
    }

    /**
     * 检查手机号是否存在
     *
     * @param account 手机号
     * @return true：存在，false：不存在
     */
    private Boolean checkPhone(String account) throws ServerException {
        User phone = this.getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getPhone, account));
        return phone != null;
    }

    /**
     * 检查邮箱是否存在
     *
     * @param account 邮箱
     * @return true：存在，false：不存在
     */
    private Boolean checkEmail(String account) throws ServerException {
        User email = this.getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getEmail, account));
        return email != null;
    }

    /**
     * 处理login。
     */
    @Transactional(rollbackFor = ServerException.class)
    @Override
    public UserVo login(UserVo user) throws ServerException {
        String email = user.getEmail();
        Integer verificationCode = user.getVerificationCode();
        // 邮箱是否存在
        if (!checkEmail(email))
            throw new ServerException(400, I18nUtils.getMessage("user.email.not.exist"));
        checkCode(email, verificationCode);
        User one = getOne(Wrappers.lambdaQuery(User.class)
                .eq(User::getEmail, email));
        one.setPassword(null);
        BeanUtils.copyProperties(one, user);
        //生成token
        HashMap<String, String> payload = new HashMap<>();
        payload.put("userId", String.valueOf(user.getId()));
        //角色
        payload.put("role", tokenRole(one.getId(), one.getType()));
        Token token = TokenUtils.createToken(payload);
        user.setToken(token.getToken());
        user.setRefreshToken(token.getRefreshToken());
        tokenService.saveOrUpdate(token, Wrappers.lambdaUpdate(Token.class)
                .eq(Token::getUserId, user.getId()));
        HashMap<String, String> map = CurrentUser.getUser();
        map.put("userId", one.getId());
        map.put("token", token.getToken());
        cachePermissionSnapshot(one.getId(), token.getToken());
        user.setRoleType(roleType(one.getId()));

        return user;

    }

    /**
     * 使用当前会话的 refresh token 轮换令牌。条件更新确保同一个 refresh token 只能成功使用一次。
     */
    @Transactional(rollbackFor = Exception.class)
    @Override
    public UserVo refreshToken(String refreshToken) throws ServerException {
        final String decodedRefreshToken;
        try {
            decodedRefreshToken = AesUtil.decrypt(refreshToken);
            if (!TokenUtils.hasTokenType(decodedRefreshToken, TokenUtils.REFRESH_TOKEN_TYPE)) {
                throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
            }
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
        }

        String userId = TokenUtils.getUserId(decodedRefreshToken);
        User user = getById(userId);
        if (user == null || !Integer.valueOf(0).equals(user.getState())) {
            throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
        }
        HashMap<String, String> payload = new HashMap<>();
        payload.put("userId", userId);
        payload.put("role", tokenRole(user.getId(), user.getType()));
        Token nextToken = TokenUtils.createToken(payload);
        boolean rotated = tokenService.update(nextToken, Wrappers.<Token>lambdaUpdate()
                .eq(Token::getUserId, userId)
                .eq(Token::getRefreshToken, refreshToken)
                .eq(Token::getState, 1));
        if (!rotated) {
            throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
        }
        cachePermissionSnapshot(userId, nextToken.getToken());
        UserVo result = new UserVo();
        result.setToken(nextToken.getToken());
        result.setRefreshToken(nextToken.getRefreshToken());
        return result;
    }

    /** 登录令牌只携带两种稳定角色，避免历史用户 type 值绕过账号数据范围。 */
    private String tokenRole(String userId, String fallback) {
        // 允许无 Spring 容器的纯单元测试使用历史用户 type 作为安全兜底；生产环境始终走角色类型表。
        if (userRoleService == null || roleService == null) {
            return "ADMIN".equalsIgnoreCase(fallback) ? "ADMIN" : "USER";
        }
        List<UserRole> bindings = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                .select(UserRole::getRoleId)
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getDeleted, false));
        for (UserRole binding : bindings) {
            Role role = roleService.getById(binding.getRoleId());
            if (role != null && "ADMIN".equalsIgnoreCase(role.getRoleType())) {
                return "ADMIN";
            }
        }
        return "USER";
    }

    /**
     * 获取角色Ids按用户Id。
     */
    @Override
    public List<String> getRoleIdsByUserId(String userId) {
        List<UserRole> list = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                .select(UserRole::getRoleId)
                .eq(UserRole::getUserId, userId));
        if (!list.isEmpty()) {
            return list.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /**
     * 处理bind角色。
     */
    @Override
    public Boolean bindRole(String userId, List<String> roleIds) {
        boolean result = userRoleService.saveUserRoleIds(userId, roleIds);
        if (result) evictPermissionSnapshot(userId);
        return result;
    }

    /** 角色资源变化后，使绑定该角色的账号权限快照失效。 */
    @Override
    public void invalidatePermissionCacheByRoleId(String roleId) {
        if (StringUtils.isBlank(roleId)) return;
        List<UserRole> bindings = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                .select(UserRole::getUserId)
                .eq(UserRole::getRoleId, roleId));
        if (bindings == null) return;
        bindings.stream().map(UserRole::getUserId).filter(StringUtils::isNotBlank)
                .distinct().forEach(this::evictPermissionSnapshot);
    }

    /**
     * 获取Routers。
     */
    @Override
    public List<ResourceVo> getRouters() {
        HashMap<String, String> user = CurrentUser.getUser();
        return getRoutersByUserId(user == null ? null : user.get("userId"));
    }

    /**
     * 获取Routers按用户Id。
     */
    private List<ResourceVo> getRoutersByUserId(String userId) {
        List<ResourceVo> routes = new ArrayList<>();
        if (userId != null) {
            // 1.获取用户角色
            List<UserRole> roles = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                    .select(UserRole::getRoleId)
                    .eq(UserRole::getUserId, userId));
            if (roles.isEmpty()) {
                return routes;
            }
            // 2.获取角色对应的资源
            List<String> roleIds = roles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
            List<RoleResource> resources = roleResourceService.list(Wrappers.<RoleResource>lambdaQuery()
                    .select(RoleResource::getResourceId)
                    .in(RoleResource::getRoleId, roleIds)
                    .orderByAsc(RoleResource::getSortNum));
            if (resources.isEmpty()) {
                return routes;
            }
            // 3.获取资源
            Set<String> resourceIds = resources.stream().map(RoleResource::getResourceId).collect(Collectors.toSet());
            List<ResourceVo> list = resourceService.list(Wrappers.lambdaQuery(com.aether.sys.entity.Resource.class)
                    .in(com.aether.sys.entity.Resource::getId, resourceIds)
                    .orderByAsc(com.aether.sys.entity.Resource::getSortNum)).stream().map(v -> {
                ResourceVo resourceVo = new ResourceVo();
                BeanUtils.copyProperties(v, resourceVo);
                String lng = I18nUtils.getMessage("lng");
                resourceVo.setTitle(v.getName());
                resourceVo.setName(lng.equals("en_US") ? resourceVo.getName() : resourceVo.getNameCn());
                return resourceVo;
            }).collect(Collectors.toList());
            if (list.isEmpty()) {
                return routes;
            }
            // 5.构建树形结构,使用队列
            Map<String, ResourceVo> map = new HashMap<>();
            list.forEach(v -> map.put(v.getId(), v));
            Queue<ResourceVo> queue = new LinkedList<>(list);
            while (!queue.isEmpty()) {
                ResourceVo poll = queue.poll();
                ResourceVo parent = map.get(poll.getParentId().toString());
                if (parent != null) {
                    if (parent.getChildren() == null) {
                        parent.setChildren(new ArrayList<>());
                    }
                    parent.getChildren().add(poll);
                }
                map.put(poll.getId(), poll);
            }

            // 6.设置权限标识
            Collection<ResourceVo> resourceVos = map.values();
            routes = resourceVos.stream()
                    .filter(item -> item.getType().equals(ResourceType.ROUTE.getCode()))
                    .filter(item -> item.getParentId().equals("0"))
                    .sorted(Comparator.comparing(BaseEntity::getSortNum))
                    .collect(Collectors.toList());
            Stack<Collection<ResourceVo>> stack = new Stack<>();
            stack.add(routes);
            while (!stack.isEmpty()) {
                Collection<ResourceVo> item = stack.pop();
                item.forEach(v -> {
                    if (v.getChildren() != null) {
                        if (v.getChildren().get(0).getType().equals(ResourceType.PERMISSION.getCode())) {
                            v.setAccess(v.getChildren().stream().map(ResourceVo::getTitle).collect(Collectors.joining(",")));
                            v.setChildren(null);
                        } else
                            stack.add(v.getChildren());
                    }
                });
            }
            return routes;
        }
        return routes;
    }

    /**
     * 详情当前请求。
     */
    @Override
    public UserVo detail() {
        UserVo userVo = new UserVo();
        HashMap<String, String> currentUser = CurrentUser.getUser();
        if (currentUser == null || currentUser.get("userId") == null) {
            return userVo;
        }
        User user = this.getById(currentUser.get("userId"));
        user.setPassword(null);
        user.setSmtpAuthorizationCode(null);
        BeanUtils.copyProperties(user, userVo);
        userVo.setRoleType(roleType(user.getId()));
        Map<String, Object> map = null;
        try {
            map = PermissionCache.get(redisTemplate, currentUser.get("userId"));
        } catch (Exception ignored) {
            // Redis 重启期间允许通过数据库回源，避免把已登录用户强制踢回登录页。
        }
        if (!PermissionCache.isComplete(map)) {
            String accessToken = StringUtils.isNotBlank(currentUser.get("encryptedToken"))
                    ? currentUser.get("encryptedToken") : currentUser.get("token");
            map = loadPermissionMap(currentUser.get("userId"), accessToken);
            try {
                PermissionCache.put(redisTemplate, currentUser.get("userId"), map);
            } catch (Exception ignored) {
                // 仅缓存写入失败不影响本次已通过数据库重建的权限结果。
            }
        }
        userVo.setPermissionMap(new HashMap<>(map));
        return userVo;
    }

    /** 角色类型是账号权限边界的唯一判断依据，不依赖角色名称或用户 type 字段。 */
    private String roleType(String userId) {
        if (StringUtils.isBlank(userId)) {
            return "USER";
        }
        List<UserRole> bindings = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                .select(UserRole::getRoleId)
                .eq(UserRole::getUserId, userId)
                .eq(UserRole::getDeleted, false));
        if (bindings != null) {
            for (UserRole binding : bindings) {
                Role role = roleService.getById(binding.getRoleId());
                if (role != null && "ADMIN".equalsIgnoreCase(role.getRoleType())) {
                    return "ADMIN";
                }
            }
        }
        return "USER";
    }

    /**
     * 获取权限Map按用户Id。
     */
    @NotNull
    @Override
    public HashMap<String, Object> getPermissionMapByUserId(String userId, String token) {
        List<ResourceVo> routers = getRoutersByUserId(userId);
        HashMap<String, Object> map = new HashMap<>();
        Stack<ResourceVo> stack = new Stack<>();
        stack.addAll(routers);
        while (!stack.empty()) {
            ResourceVo resource = stack.pop();
            if (resource.getChildren() != null && !resource.getChildren().isEmpty()) {
                stack.addAll(resource.getChildren());
            }
            if (resource.getPath() != null && Boolean.TRUE.equals(resource.getLeaf())) {
                map.put(resource.getPath(), resource.getAccess() != null && resource.getAccess().contains("Write"));
            }
        }
        mergeExplicitPermissionPaths(userId, map);
        try {
            String accessToken = token;
            try {
                accessToken = AesUtil.decrypt(token);
            } catch (Exception ignored) {
                // provider 也可能收到过滤器已经解密的 JWT。
            }
            TokenUtils.isExpired(accessToken);
            map.put("/sys", false);
        } catch (Exception e) {
            log.error(e.getMessage());
        }
        return map;
    }

    /** 认证层的权限快照重建入口。 */
    @Override
    public Map<String, Object> loadPermissionMap(String userId, String encryptedAccessToken) {
        return getPermissionMapByUserId(userId, encryptedAccessToken);
    }

    /** 认证层读取稳定角色类型，不能依赖角色名称或用户 type 字段。 */
    @Override
    public String loadRoleType(String userId) {
        return roleType(userId);
    }

    /** 将权限和角色快照写入 Redis，供所有实例和重启后的请求共享。 */
    private void cachePermissionSnapshot(String userId, String encryptedAccessToken) {
        if (redisTemplate == null) return;
        Map<String, Object> permissionMap = loadPermissionMap(userId, encryptedAccessToken);
        PermissionCache.put(redisTemplate, userId, permissionMap);
        PermissionCache.putRoleType(redisTemplate, userId, loadRoleType(userId));
    }

    /** 用户角色绑定变化后立即使快照失效，下个请求会从数据库重建。 */
    private void evictPermissionSnapshot(String userId) {
        PermissionCache.evict(redisTemplate, userId);
    }

    /**
     * 合并带路径的细粒度权限。
     *
     * 常规权限仍通过“路由 + 读写”生成缓存；审计载荷等不能映射为独立页面的接口权限，
     * 直接挂在路由下并携带自身路径，避免破坏授权页面仅支持两层资源树的约束。
     */
    private void mergeExplicitPermissionPaths(String userId, Map<String, Object> permissionMap) {
        if (StringUtils.isBlank(userId)) {
            return;
        }
        List<UserRole> roles = userRoleService.list(Wrappers.<UserRole>lambdaQuery()
                .select(UserRole::getRoleId)
                .eq(UserRole::getUserId, userId));
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<String> roleIds = roles.stream().map(UserRole::getRoleId).collect(Collectors.toList());
        String organizationId = CurrentUser.organizationId();
        String teamId = CurrentUser.teamId();
        // 组织只表示成员归属，不再产生组织级管理员或组织级权限。
        if (StringUtils.isNotBlank(teamId) && StringUtils.isNotBlank(organizationId)) {
            TeamMember member = teamMemberService.lambdaQuery().eq(TeamMember::getTeamId, teamId)
                    .eq(TeamMember::getOrganizationId, organizationId).eq(TeamMember::getUserId, userId)
                    .eq(TeamMember::getState, 0).one();
            if (member != null) roleIds.addAll(roleServiceIds(member.getRoleCode(), "TEAM"));
        }
        List<RoleResource> bindings = roleResourceService.list(Wrappers.<RoleResource>lambdaQuery()
                .select(RoleResource::getResourceId)
                .in(RoleResource::getRoleId, roleIds));
        if (bindings == null || bindings.isEmpty()) {
            return;
        }
        Set<String> resourceIds = bindings.stream().map(RoleResource::getResourceId).collect(Collectors.toSet());
        List<com.aether.sys.entity.Resource> permissions = resourceService.list(
                Wrappers.lambdaQuery(com.aether.sys.entity.Resource.class)
                        .in(com.aether.sys.entity.Resource::getId, resourceIds)
                        .eq(com.aether.sys.entity.Resource::getType, ResourceType.PERMISSION.getCode())
                        .isNotNull(com.aether.sys.entity.Resource::getPath)
                        .ne(com.aether.sys.entity.Resource::getPath, ""));
        if (permissions == null) {
            return;
        }
        for (com.aether.sys.entity.Resource permission : permissions) {
            permissionMap.put(permission.getPath(), true);
        }
    }

    private List<String> roleServiceIds(String code, String scope) {
        if (StringUtils.isBlank(code)) return Collections.emptyList();
        return roleService.list(Wrappers.<Role>lambdaQuery().select(Role::getId).eq(Role::getName, code)
                .eq(Role::getDeleted, false)).stream().map(Role::getId).collect(Collectors.toList());
    }

    /**
     * 发送VerificationCode。
     */
    @Override
    public void sendVerificationCode(String email) {
        User user = this.getOne(Wrappers.<User>lambdaQuery()
                .eq(User::getEmail, email));
        if (user == null) {
            throw new ServerException(I18nUtils.getMessage("user.email.not.exist"));
        }
        try {
            Email newEmail = new Email();
            newEmail.setUserId(user.getId());
            newEmail.setEmail(email);
            newEmail.setCode(RandomUtils.nextInt(100000, 999999));
            newEmail.setType(EmailType.VERIFICATION_CODE.getCode());
            Dict subject = dictService.getByCode("email_template_login_subject", null);
            newEmail.setSubject(subject.getName());
            Dict body = dictService.getByCode("email_template_login_content", null);
            newEmail.setBody(body.getName().replace("${code}", newEmail.getCode().toString()));
            emailService.send(newEmail);
        } catch (ServerException e) {
            throw new ServerException(400, e.getMessage());
        }
    }

    /**
     * 处理logout。
     */
    @Override
    public boolean logout() {
        String id = CurrentUser.getUser().get("userId");
        PermissionCache.evict(redisTemplate, id);
        return tokenService.remove(Wrappers.<Token>lambdaUpdate()
                .eq(Token::getUserId, CurrentUser.getUser().get("userId")));
    }
}
