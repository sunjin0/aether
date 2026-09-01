package com.aether.sys.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.saml2.core.Saml2X509Credential;
import org.springframework.security.saml2.provider.service.registration.InMemoryRelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistration;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrationRepository;
import org.springframework.security.saml2.provider.service.registration.RelyingPartyRegistrations;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import org.springframework.util.StringUtils;

/** Builds the standard Spring Security SAML relying-party contract when explicitly enabled. */
@Configuration
@ConditionalOnProperty(prefix = "aether.identity.saml", name = "enabled", havingValue = "true")
public class SamlRelyingPartyConfiguration {
    @Bean
    public RelyingPartyRegistrationRepository samlRelyingPartyRegistrationRepository(SamlIdentityProperties properties) {
        if (properties.isMetadataDriven()) {
            RelyingPartyRegistration registration = RelyingPartyRegistrations.fromMetadataLocation(properties.getMetadataUri())
                    .registrationId("aether")
                    .entityId(properties.getEntityId())
                    .assertionConsumerServiceLocation(properties.getRedirectUri())
                    .build();
            // NameID 映射服务复用注册中经过 metadata 解析的 IdP entityId，避免 metadata 模式仍依赖手工配置。
            if (!StringUtils.hasText(registration.getRemoteIdpEntityId())) {
                throw new IllegalStateException("SAML metadata does not contain a valid IdP entityId");
            }
            properties.setIdpEntityId(registration.getRemoteIdpEntityId());
            return new InMemoryRelyingPartyRegistrationRepository(registration);
        }
        X509Certificate certificate = certificate(properties.getCertificate());
        RelyingPartyRegistration registration = RelyingPartyRegistration.withRegistrationId("aether")
                .entityId(properties.getEntityId())
                .assertionConsumerServiceLocation(properties.getRedirectUri())
                .assertingPartyDetails(details -> details
                        .entityId(properties.getIdpEntityId())
                        .singleSignOnServiceLocation(properties.getSsoUrl())
                        .verificationX509Credentials(credentials -> credentials.add(Saml2X509Credential.verification(certificate)))
                        .wantAuthnRequestsSigned(false))
                .build();
        return new InMemoryRelyingPartyRegistrationRepository(registration);
    }

    private X509Certificate certificate(String value) {
        try {
            String normalized = value.replace("-----BEGIN CERTIFICATE-----", "")
                    .replace("-----END CERTIFICATE-----", "")
                    .replaceAll("\\s", "");
            byte[] encoded = Base64.getDecoder().decode(normalized.getBytes(StandardCharsets.US_ASCII));
            return (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(new ByteArrayInputStream(encoded));
        } catch (Exception ex) {
            throw new IllegalStateException("SAML IdP certificate is invalid", ex);
        }
    }
}
