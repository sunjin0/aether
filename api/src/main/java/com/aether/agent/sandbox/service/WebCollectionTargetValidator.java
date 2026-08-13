package com.aether.agent.sandbox.service;

import java.util.Map;

/** Validates user-proposed web targets against a frozen collection template. */
public interface WebCollectionTargetValidator {
    void validate(Map<String, Object> input, Map<String, Object> config);
}
