package com.aether.sys.service.impl;

import com.aether.sys.dto.ServiceAccountCreateDto;
import com.aether.sys.dto.ServiceAccountTokenDto;
import com.aether.sys.dto.ServiceAccountUpdateDto;
import com.aether.sys.entity.Role;
import com.aether.sys.entity.ServiceAccount;
import com.aether.sys.entity.User;
import com.aether.sys.mapper.ServiceAccountMapper;
import com.aether.sys.mapper.UserMapper;
import com.aether.sys.mapper.UserRoleMapper;
import com.aether.sys.service.RoleService;
import com.aether.sys.service.ServiceAccountService;
import com.aether.sys.service.UserRoleService;
import com.aether.sys.service.UserService;
import com.aether.sys.vo.ServiceAccountSecretVo;
import com.aether.sys.vo.ServiceAccountTokenVo;
import com.aether.exception.ServerException;
import com.aether.utils.TokenUtils;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.*;

/**
 * 服务账号采用 client credentials，底层用户仅用于复用角色资源权限。
 */
@Service
public class ServiceAccountServiceImpl extends ServiceImpl<ServiceAccountMapper, ServiceAccount>
        implements ServiceAccountService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final UserService userService;
    private final UserRoleService userRoleService;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;
    private final RedisTemplate<String, Object> redisTemplate;
    private final UserMapper userMapper;
    private final UserRoleMapper userRoleMapper;
    private final int accessTokenSeconds;

    public ServiceAccountServiceImpl(UserService userService, UserRoleService userRoleService, RoleService roleService,
                                     PasswordEncoder passwordEncoder, RedisTemplate<String, Object> redisTemplate,
                                     UserMapper userMapper, UserRoleMapper userRoleMapper,
                                     @org.springframework.beans.factory.annotation.Value("${aether.service-account.access-token-seconds:900}") int accessTokenSeconds) {
        this.userService = userService;
        this.userRoleService = userRoleService;
        this.roleService = roleService;
        this.passwordEncoder = passwordEncoder;
        this.redisTemplate = redisTemplate;
        this.userMapper = userMapper;
        this.userRoleMapper = userRoleMapper;
        this.accessTokenSeconds = Math.max(60, Math.min(accessTokenSeconds, 3600));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceAccountSecretVo create(ServiceAccountCreateDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getName())) throw new ServerException(422, "服务账号名称不能为空");
        if (dto.getName().length() > 128) throw new ServerException(422, "服务账号名称长度不能超过 128");
        List<String> roleIds = dto.getRoleIds() == null ? Collections.<String>emptyList() : dto.getRoleIds();
        if (roleIds.isEmpty()) throw new ServerException(422, "服务账号至少需要一个角色");
        if (roleService.count(Wrappers.lambdaQuery(Role.class).in(Role::getId, roleIds).eq(Role::getDeleted, false)) != new HashSet<String>(roleIds).size())
            throw new ServerException(422, "包含不存在或已删除的角色");
        String clientId = StringUtils.defaultIfBlank(dto.getClientId(), "sa_" + randomToken(12));
        if (!clientId.matches("[A-Za-z][A-Za-z0-9_-]{2,63}")) throw new ServerException(422, "clientId 格式不合法");
        if (count(Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getClientId, clientId).eq(ServiceAccount::getDeleted, false)) > 0)
            throw new ServerException(409, "clientId 已存在");
        String secret = "sa_" + randomToken(32);
        User user = new User();
        user.setUsername("svc-" + clientId);
        user.setType("System_Role_Service");
        user.setSex("Gender_Type_Woman");
        user.setEmail("svc-" + clientId + "@service.local");
        user.setPhone("svc-"+UUID.randomUUID().toString().replace("-", ""));
        user.setAvatar("https://gw.alipayobjects.com/zos/antfincdn/XAosXuNZyF/BiazfanxmamNRoxxVxka.png");
        user.setPassword(passwordEncoder.encode(randomToken(32)));
        userService.save(user);
        userRoleService.saveUserRoleIds(user.getId(), new ArrayList<String>(new HashSet<String>(roleIds)));
        ServiceAccount account = new ServiceAccount();
        account.setUserId(user.getId());
        account.setName(dto.getName());
        account.setDescription(StringUtils.abbreviate(dto.getDescription(), 1024));
        account.setClientId(clientId);
        account.setSecretHash(passwordEncoder.encode(secret));
        account.setTokenVersion(1);
        account.setEnabled(true);
        List<String> allowed = dto.getAllowedWorkflowIds() == null ? Collections.<String>emptyList() : dto.getAllowedWorkflowIds();
        account.setAllowedWorkflowIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowed))));
        int maxStarts = dto.getMaxStartsPerHour() == null ? 0 : dto.getMaxStartsPerHour();
        if (maxStarts < 0 || maxStarts > 100000)
            throw new ServerException(422, "每小时启动额度必须在 0 到 100000 之间");
        account.setMaxStartsPerHour(maxStarts);
        save(account);
        return secretVo(account, secret);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(String id, ServiceAccountUpdateDto dto) {
        ServiceAccount account = required(id);
        if (dto == null || StringUtils.isBlank(dto.getName())) throw new ServerException(422, "服务账号名称不能为空");
        if (dto.getName().length() > 128) throw new ServerException(422, "服务账号名称长度不能超过 128");
        List<String> roleIds = dto.getRoleIds() == null ? Collections.<String>emptyList() : dto.getRoleIds();
        if (roleIds.isEmpty()) throw new ServerException(422, "服务账号至少需要一个角色");
        if (roleService.count(Wrappers.lambdaQuery(Role.class).in(Role::getId, roleIds).eq(Role::getDeleted, false)) != new HashSet<String>(roleIds).size())
            throw new ServerException(422, "包含不存在或已删除的角色");
        int maxStarts = dto.getMaxStartsPerHour() == null ? 0 : dto.getMaxStartsPerHour();
        if (maxStarts < 0 || maxStarts > 100000)
            throw new ServerException(422, "每小时启动额度必须在 0 到 100000 之间");
        List<String> allowed = dto.getAllowedWorkflowIds() == null ? Collections.<String>emptyList() : dto.getAllowedWorkflowIds();
        account.setName(dto.getName());
        account.setDescription(StringUtils.abbreviate(dto.getDescription(), 1024));
        account.setAllowedWorkflowIds(JSON.toJSONString(new ArrayList<String>(new LinkedHashSet<String>(allowed))));
        account.setMaxStartsPerHour(maxStarts);
        userRoleService.saveUserRoleIds(account.getUserId(), new ArrayList<String>(new LinkedHashSet<String>(roleIds)));
        boolean updated = updateById(account);
        if (updated) redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, account.getUserId());
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean delete(String id) {
        ServiceAccount account = required(id);
        redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, account.getUserId());
        userRoleMapper.physicalDeleteByUserId(account.getUserId());
        userMapper.physicalDeleteById(account.getUserId());
        return baseMapper.physicalDeleteById(id) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ServiceAccountSecretVo rotateSecret(String id) {
        ServiceAccount account = required(id);
        String secret = "sa_" + randomToken(32);
        account.setSecretHash(passwordEncoder.encode(secret));
        account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
        updateById(account);
        return secretVo(account, secret);
    }

    @Override
    public ServiceAccountTokenVo issueToken(ServiceAccountTokenDto dto) {
        if (dto == null || StringUtils.isBlank(dto.getClientId()) || StringUtils.isBlank(dto.getClientSecret()))
            throw new ServerException(401, "clientId 或 clientSecret 无效");
        ServiceAccount account = getOne(Wrappers.lambdaQuery(ServiceAccount.class).eq(ServiceAccount::getClientId, dto.getClientId())
                .eq(ServiceAccount::getDeleted, false));
        if (account == null || !Boolean.TRUE.equals(account.getEnabled()) || !passwordEncoder.matches(dto.getClientSecret(), account.getSecretHash()))
            throw new ServerException(401, "clientId 或 clientSecret 无效");
        Map<String, String> claims = new HashMap<String, String>();
        claims.put("userId", account.getUserId());
        claims.put("serviceAccountId", account.getId());
        claims.put("serviceTokenVersion", String.valueOf(account.getTokenVersion()));
        String accessToken = TokenUtils.createAccessToken(claims, accessTokenSeconds);
        redisTemplate.opsForHash().put(TokenUtils.TOKEN_KEY, account.getUserId(), userService.getPermissionMapByUserId(account.getUserId(), accessToken));
        account.setLastUsedAt(System.currentTimeMillis());
        updateById(account);
        ServiceAccountTokenVo result = new ServiceAccountTokenVo();
        result.setAccessToken(accessToken);
        result.setExpiresIn(accessTokenSeconds);
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean setEnabled(String id, boolean enabled) {
        ServiceAccount account = required(id);
        account.setEnabled(enabled);
        // 每次状态变更都推进版本；再次启用也不能恢复旧令牌。
        account.setTokenVersion((account.getTokenVersion() == null ? 0 : account.getTokenVersion()) + 1);
        boolean updated = updateById(account);
        if (updated) redisTemplate.opsForHash().delete(TokenUtils.TOKEN_KEY, account.getUserId());
        return updated;
    }

    @Override
    public boolean isActive(String serviceAccountId, String tokenVersion) {
        if (StringUtils.isBlank(serviceAccountId) || StringUtils.isBlank(tokenVersion)) return false;
        ServiceAccount account = getById(serviceAccountId);
        return account != null && !Boolean.TRUE.equals(account.getDeleted()) && Boolean.TRUE.equals(account.getEnabled())
                && StringUtils.equals(String.valueOf(account.getTokenVersion()), tokenVersion);
    }

    @Override
    public void assertWorkflowStartAllowed(String id, String workflowId) {
        ServiceAccount account = required(id);
        if (!Boolean.TRUE.equals(account.getEnabled())) throw new ServerException(403, "服务账号已停用");
        List<String> allowed = StringUtils.isBlank(account.getAllowedWorkflowIds()) ? Collections.<String>emptyList()
                : JSON.parseArray(account.getAllowedWorkflowIds(), String.class);
        if (allowed != null && !allowed.isEmpty() && !allowed.contains(workflowId))
            throw new ServerException(403, "服务账号无权启动该工作流");
        int limit = account.getMaxStartsPerHour() == null ? 0 : account.getMaxStartsPerHour();
        if (limit <= 0) return;
        Calendar calendar = Calendar.getInstance();
        String bucket = String.format(Locale.ROOT, "%1$tY%1$tm%1$td%1$tH", calendar);
        String key = "ServiceAccountWorkflowStarts:" + account.getId() + ":" + bucket;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) redisTemplate.expire(key, 2, java.util.concurrent.TimeUnit.HOURS);
        if (count != null && count > limit) throw new ServerException(429, "服务账号工作流启动额度已用尽");
    }

    private ServiceAccount required(String id) {
        ServiceAccount account = getById(id);
        if (account == null || Boolean.TRUE.equals(account.getDeleted()))
            throw new ServerException(404, "服务账号不存在");
        return account;
    }

    private ServiceAccountSecretVo secretVo(ServiceAccount account, String secret) {
        ServiceAccountSecretVo result = new ServiceAccountSecretVo();
        result.setId(account.getId());
        result.setName(account.getName());
        result.setClientId(account.getClientId());
        result.setClientSecret(secret);
        return result;
    }

    private String randomToken(int bytes) {
        byte[] value = new byte[bytes];
        RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
}
