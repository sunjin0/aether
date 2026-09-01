package com.aether.observability;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** Loads the Admin OTel infrastructure when Trace or Logs is explicitly enabled. */
final class AdminOtelEnabledCondition implements Condition {
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        return Boolean.parseBoolean(context.getEnvironment().getProperty("aether.otel.admin.enabled", "false"))
                || Boolean.parseBoolean(context.getEnvironment().getProperty("aether.otel.admin.logs-enabled", "false"));
    }
}
