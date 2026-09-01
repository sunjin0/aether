package com.aether.governance.controller;

import com.aether.entity.WebResponse;
import com.aether.governance.service.SecretProvider;
import com.aether.local.CurrentUser;
import com.aether.permission.Permission;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.net.URI;

/** 连接器凭据管理入口。接口永不回显或查询明文，只返回引用和操作结果。 */
@Api(tags = "Connector Credential API")
@RestController
@Permission(path = "/agent/mcp-server")
@RequestMapping("/api/agent/governance/connector-credentials")
public class ConnectorCredentialController {
    private final SecretProvider secretProvider;

    public ConnectorCredentialController(SecretProvider secretProvider) {
        this.secretProvider = secretProvider;
    }

    @ApiOperation("保存连接器凭据")
    @PostMapping
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    public WebResponse<String> put(@RequestBody CredentialRequest request) {
        String tenantId = tenantId();
        if (StringUtils.isBlank(tenantId) || request == null
                || StringUtils.isBlank(request.credentialRef) || request.values == null || request.values.isEmpty()
                || !request.credentialRef.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")
                || request.values.size() > 20 || request.values.entrySet().stream()
                .anyMatch(entry -> StringUtils.isBlank(entry.getKey()) || entry.getKey().length() > 64
                        || entry.getValue() == null || entry.getValue().length() > 8192
                        || ("endpoint".equalsIgnoreCase(entry.getKey()) && !isHttpsEndpoint(entry.getValue())))) {
            return WebResponse.Error(400, "凭据引用或凭据内容无效");
        }
        secretProvider.put(request.credentialRef, "tenant", tenantId, request.values);
        return WebResponse.OK(request.credentialRef);
    }

    private boolean isHttpsEndpoint(String value) {
        if (StringUtils.isBlank(value) || value.length() > 2048) return false;
        try {
            URI uri = new URI(value);
            return "https".equalsIgnoreCase(uri.getScheme())
                    && StringUtils.isNotBlank(uri.getHost())
                    && uri.getUserInfo() == null
                    && uri.getFragment() == null;
        } catch (Exception ex) {
            return false;
        }
    }

    @ApiOperation("撤销连接器凭据")
    @DeleteMapping("/{credentialRef}")
    @Permission(path = "/agent/mcp-server", type = Permission.Type.Write)
    public WebResponse<String> revoke(@PathVariable String credentialRef) {
        String tenantId = tenantId();
        if (StringUtils.isBlank(tenantId) || StringUtils.isBlank(credentialRef)
                || !credentialRef.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            return WebResponse.Error(400, "凭据引用无效");
        }
        secretProvider.revoke(credentialRef, "tenant", tenantId);
        return WebResponse.OK("true");
    }

    private String tenantId() {
        return CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
    }

    public static class CredentialRequest {
        public String credentialRef;
        public Map<String, String> values;
    }
}
