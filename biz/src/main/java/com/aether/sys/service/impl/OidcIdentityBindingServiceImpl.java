package com.aether.sys.service.impl;

import com.aether.sys.entity.OidcIdentityBinding;
import com.aether.sys.mapper.OidcIdentityBindingMapper;
import com.aether.sys.service.OidcIdentityBindingService;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists explicit OIDC subject-to-user bindings without email inference. */
@Service
public class OidcIdentityBindingServiceImpl extends ServiceImpl<OidcIdentityBindingMapper, OidcIdentityBinding>
        implements OidcIdentityBindingService {
    @Override
    public OidcIdentityBinding find(String tenantId, String issuer, String subject) {
        if (StringUtils.isAnyBlank(tenantId, issuer, subject)) return null;
        return getOne(Wrappers.lambdaQuery(OidcIdentityBinding.class)
                .eq(OidcIdentityBinding::getTenantId, tenantId)
                .eq(OidcIdentityBinding::getIssuer, issuer)
                .eq(OidcIdentityBinding::getSubject, subject)
                .eq(OidcIdentityBinding::getDeleted, false).last("LIMIT 1"));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public OidcIdentityBinding bind(String tenantId, String issuer, String subject, String userId, String email) {
        if (StringUtils.isAnyBlank(tenantId, issuer, subject, userId))
            throw new IllegalArgumentException("OIDC binding requires tenant, issuer, subject and user");
        OidcIdentityBinding existing = find(tenantId, issuer, subject);
        if (existing != null && !userId.equals(existing.getUserId()))
            throw new IllegalStateException("OIDC subject is already bound to another user");
        if (existing != null) {
            existing.setEmailSnapshot(email);
            existing.setLastLoginAt(System.currentTimeMillis());
            updateById(existing);
            return existing;
        }
        OidcIdentityBinding value = new OidcIdentityBinding();
        value.setTenantId(tenantId);
        value.setIssuer(issuer);
        value.setSubject(subject);
        value.setUserId(userId);
        value.setEmailSnapshot(email);
        value.setLastLoginAt(System.currentTimeMillis());
        try {
            save(value);
            return value;
        } catch (DuplicateKeyException ex) {
            OidcIdentityBinding raced = find(tenantId, issuer, subject);
            if (raced != null) return raced;
            throw ex;
        }
    }
}
