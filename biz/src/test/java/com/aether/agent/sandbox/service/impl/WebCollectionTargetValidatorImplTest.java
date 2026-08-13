package com.aether.agent.sandbox.service.impl;

import com.aether.exception.ServerException;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class WebCollectionTargetValidatorImplTest {
    private final WebCollectionTargetValidatorImpl validator = new WebCollectionTargetValidatorImpl();
    private Map<String, Object> config() { Map<String, Object> result = new HashMap<>(); result.put("executionMode", "WEB_COLLECTION"); result.put("allowedDomains", Collections.singletonList("example.com")); result.put("allowSubdomains", true); result.put("maxRequests", 20); result.put("maxPageDepth", 1); return result; }
    private Map<String, Object> input(String url) { Map<String, Object> result = new HashMap<>(); result.put("targetUrl", url); result.put("purpose", "Extract public documentation"); result.put("estimatedRequests", 3); return result; }

    @Test void acceptsOnlyAllowlistedHttpsDomain() { assertDoesNotThrow(() -> validator.validate(input("https://docs.example.com/path"), config())); }
    @Test void rejectsPrivateOrUnauthorizedTargets() {
        assertThrows(ServerException.class, () -> validator.validate(input("http://example.com"), config()));
        assertThrows(ServerException.class, () -> validator.validate(input("https://127.0.0.1"), config()));
        assertThrows(ServerException.class, () -> validator.validate(input("https://evil.example.org"), config()));
        assertThrows(ServerException.class, () -> validator.validate(input("https://example.com:8443"), config()));
    }
    @Test void rejectsMissingPurposeOrRequestQuotaBypass() { Map<String, Object> missingPurpose = input("https://example.com"); missingPurpose.remove("purpose"); assertThrows(ServerException.class, () -> validator.validate(missingPurpose, config())); Map<String, Object> excessive = input("https://example.com"); excessive.put("estimatedRequests", 21); assertThrows(ServerException.class, () -> validator.validate(excessive, config())); }
}
