package com.aether.sys.controller;

import com.aether.entity.WebResponse;
import com.aether.permission.Permission;
import com.aether.sys.config.OidcIdentityProperties;
import com.aether.sys.config.ScimIdentityProperties;
import com.aether.sys.config.SamlIdentityProperties;
import com.aether.sys.service.OidcAuthorizationCodeClient;
import com.aether.sys.service.OidcIdTokenVerifier;
import com.aether.sys.service.OidcIdentityBindingService;
import com.aether.sys.service.UserService;
import com.aether.sys.entity.OidcIdentityBinding;
import com.aether.sys.vo.UserVo;
import com.aether.local.CurrentUser;
import org.springframework.security.oauth2.jwt.Jwt;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

/** 企业身份集成的脱敏元数据接口。 */
@Api(tags = "企业身份 API")
@RestController
public class IdentityController {
    private final OidcIdentityProperties oidc;
    private final ScimIdentityProperties scim;
    private final SamlIdentityProperties saml;
    private final StringRedisTemplate redis;
    private final OidcAuthorizationCodeClient codeClient;
    private final OidcIdTokenVerifier tokenVerifier;
    private final OidcIdentityBindingService bindingService;
    private final UserService userService;

    public IdentityController(OidcIdentityProperties oidc, ScimIdentityProperties scim, StringRedisTemplate redis,
                              OidcAuthorizationCodeClient codeClient, OidcIdTokenVerifier tokenVerifier,
                              OidcIdentityBindingService bindingService, UserService userService) {
        this(oidc, scim, new SamlIdentityProperties(), redis, codeClient, tokenVerifier, bindingService, userService);
    }

    @Autowired
    public IdentityController(OidcIdentityProperties oidc, ScimIdentityProperties scim, SamlIdentityProperties saml, StringRedisTemplate redis,
                              OidcAuthorizationCodeClient codeClient, OidcIdTokenVerifier tokenVerifier,
                              OidcIdentityBindingService bindingService, UserService userService) {
        this.oidc = oidc;
        this.scim = scim;
        this.saml = saml;
        this.redis = redis;
        this.codeClient = codeClient;
        this.tokenVerifier = tokenVerifier;
        this.bindingService = bindingService;
        this.userService = userService;
    }

    @ApiOperation("SAML 联邦登录配置")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/saml")
    public WebResponse<Map<String, Object>> saml() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", saml.isEnabled());
        result.put("entityId", saml.getEntityId());
        result.put("idpEntityId", saml.getIdpEntityId());
        result.put("metadataUri", saml.getMetadataUri());
        result.put("ssoUrl", saml.getSsoUrl());
        result.put("redirectUri", saml.getRedirectUri());
        result.put("protocol", "SAML 2.0");
        return WebResponse.OK(result);
    }

    @ApiOperation("SCIM 集成配置")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/scim")
    public WebResponse<Map<String, Object>> scim() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", scim.isEnabled());
        result.put("protocol", "SCIM 2.0");
        result.put("basePath", scim.getBasePath());
        return WebResponse.OK(result);
    }

    @ApiOperation("开始 SAML 联邦登录")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/saml/authorize")
    public ResponseEntity<Void> samlAuthorize(@org.springframework.web.bind.annotation.RequestParam String tenantId) {
        if (!saml.isEnabled() || tenantId == null
                || !tenantId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) return ResponseEntity.notFound().build();
        String state = UUID.randomUUID().toString().replace("-", "");
        redis.opsForValue().set("saml:login:state:" + state, tenantId, 5, TimeUnit.MINUTES);
        String location = "/saml2/authenticate/aether?RelayState=" + state;
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    @ApiOperation("OIDC 登录配置")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/oidc")
    public WebResponse<Map<String, Object>> oidc() {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("enabled", oidc.isEnabled());
        result.put("issuerUri", oidc.getIssuerUri());
        result.put("authorizationUri", oidc.getAuthorizationUri());
        result.put("tokenUri", oidc.getTokenUri());
        result.put("jwksUri", oidc.getJwksUri());
        result.put("clientId", oidc.getClientId());
        result.put("redirectUri", oidc.getRedirectUri());
        result.put("autoProvision", oidc.isAutoProvision());
        result.put("scopes", oidc.getScopes());
        result.put("pkce", "S256");
        result.put("scimEnabled", scim.isEnabled());
        return WebResponse.OK(result);
    }

    @ApiOperation("开始 OIDC 授权码登录")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/oidc/authorize")
    public ResponseEntity<Void> authorize(@org.springframework.web.bind.annotation.RequestParam(required = false) String tenantId) {
        if (!oidc.isEnabled()) return ResponseEntity.notFound().build();
        String sessionTenant = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
        if (tenantId == null || tenantId.trim().isEmpty()) tenantId = sessionTenant;
        if (tenantId == null || tenantId.trim().isEmpty()) return ResponseEntity.badRequest().build();
        String state = UUID.randomUUID().toString().replace("-", "");
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String verifier = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(sha256(verifier));
        redis.opsForValue().set("oidc:login:state:" + state, tenantId + ":" + nonce + ":" + verifier, 5, TimeUnit.MINUTES);
        String location = UriComponentsBuilder.fromUriString(oidc.getAuthorizationUri())
                .queryParam("response_type", "code")
                .queryParam("client_id", oidc.getClientId())
                .queryParam("redirect_uri", oidc.getRedirectUri())
                .queryParam("scope", String.join(" ", oidc.getScopes()))
                .queryParam("state", state)
                .queryParam("nonce", nonce)
                .queryParam("code_challenge", challenge)
                .queryParam("code_challenge_method", "S256").build().encode().toUriString();
        return ResponseEntity.status(302).header(HttpHeaders.LOCATION, location).build();
    }

    @ApiOperation("OIDC 授权码回调")
    @Permission(required = false)
    @GetMapping("/api/sys/identity/oidc/callback")
    public WebResponse<UserVo> callback(@org.springframework.web.bind.annotation.RequestParam String code,
                                        @org.springframework.web.bind.annotation.RequestParam String state,
                                        @org.springframework.web.bind.annotation.RequestParam String tenantId) {
        if (!oidc.isEnabled()) return WebResponse.Error(404, "OIDC 未启用", null);
        String stateValue = redis.opsForValue().get("oidc:login:state:" + state);
        if (stateValue == null) return WebResponse.Error(401, "OIDC state 无效或已过期", null);
        redis.delete("oidc:login:state:" + state);
        String[] stateParts = stateValue.split(":", 3);
        if (stateParts.length != 3 || !tenantId.equals(stateParts[0])) return WebResponse.Error(401, "OIDC state 无效", null);
        Map<String, Object> tokenResponse = codeClient.exchange(code, stateParts[2]);
        Jwt jwt = tokenVerifier.verify(String.valueOf(tokenResponse.get("id_token")), oidc.getClientId(), stateParts[1]);
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        OidcIdentityBinding binding = bindingService.find(tenantId, issuer, jwt.getSubject());
        if (binding == null) return WebResponse.Error(403, "OIDC 身份尚未绑定本地用户", null);
        return WebResponse.OK(userService.loginByIdentity(binding.getUserId()));
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.US_ASCII));
        } catch (Exception ex) {
            throw new IllegalStateException("无法生成 OIDC PKCE challenge", ex);
        }
    }
}
