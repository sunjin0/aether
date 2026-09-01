package com.aether.sys.service;

import com.aether.sys.config.OidcIdentityProperties;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies OIDC ID tokens with provider JWKS; decoded-but-unverified claims are never trusted. */
@Component
public class OidcIdTokenVerifier {
    private final OidcIdentityProperties properties;
    private final AtomicReference<JwtDecoder> decoder = new AtomicReference<JwtDecoder>();
    public OidcIdTokenVerifier(OidcIdentityProperties properties) { this.properties = properties; }

    public Jwt verify(String rawToken, String expectedAudience, String expectedNonce) {
        if (!properties.isEnabled() || rawToken == null || rawToken.trim().isEmpty()) return null;
        JwtDecoder current = decoder.get();
        if (current == null) {
            current = NimbusJwtDecoder.withJwkSetUri(properties.getJwksUri()).build();
            decoder.compareAndSet(null, current);
            current = decoder.get();
        }
        Jwt jwt = current.decode(rawToken);
        if (jwt.getIssuer() == null || !properties.getIssuerUri().equals(jwt.getIssuer().toString())) throw new IllegalArgumentException("OIDC issuer mismatch");
        if (expectedAudience == null || !jwt.getAudience().contains(expectedAudience)) throw new IllegalArgumentException("OIDC audience mismatch");
        if (expectedNonce != null && !expectedNonce.equals(jwt.getClaimAsString("nonce"))) throw new IllegalArgumentException("OIDC nonce mismatch");
        return jwt;
    }
}
