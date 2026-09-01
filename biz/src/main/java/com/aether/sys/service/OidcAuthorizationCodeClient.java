package com.aether.sys.service;

import com.aether.sys.config.OidcIdentityProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/** Exchanges an authorization code; returned claims must still pass OIDC signature verification. */
@Component
public class OidcAuthorizationCodeClient {
    private final OidcIdentityProperties properties;
    private final RestTemplate restTemplate = new RestTemplate();

    public OidcAuthorizationCodeClient(OidcIdentityProperties properties) {
        this.properties = properties;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> exchange(String code, String codeVerifier) {
        if (!properties.isEnabled() || code == null || code.trim().isEmpty()
                || codeVerifier == null || codeVerifier.trim().isEmpty()) {
            throw new IllegalArgumentException("OIDC authorization code exchange requires enabled configuration, code and PKCE verifier");
        }
        MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.getRedirectUri());
        form.add("client_id", properties.getClientId());
        form.add("client_secret", properties.getClientSecret());
        form.add("code_verifier", codeVerifier);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        Map<String, Object> response = restTemplate.postForObject(properties.getTokenUri(), new HttpEntity<MultiValueMap<String, String>>(form, headers), Map.class);
        if (response == null || response.get("id_token") == null) throw new IllegalArgumentException("OIDC token response missing id_token");
        return response;
    }
}
