package com.aether.sys.service;

import com.aether.sys.entity.OidcIdentityBinding;
import com.baomidou.mybatisplus.extension.service.IService;

/** Tenant-scoped OIDC subject binding operations. */
public interface OidcIdentityBindingService extends IService<OidcIdentityBinding> {
    OidcIdentityBinding find(String tenantId, String issuer, String subject);
    OidcIdentityBinding bind(String tenantId, String issuer, String subject, String userId, String email);
}
