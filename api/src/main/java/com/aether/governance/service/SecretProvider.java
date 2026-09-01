package com.aether.governance.service;

import java.util.Map;

/**
 * Secret provider boundary. Runtime components exchange only credentialRef;
 * implementations must never expose secret values through DTOs, logs or traces.
 */
public interface SecretProvider {
    Map<String, String> resolve(String credentialRef, String subjectType, String subjectId);

    String put(String credentialRef, String subjectType, String subjectId, Map<String, String> values);

    void revoke(String credentialRef, String subjectType, String subjectId);
}
