package com.aether.observability;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Conditional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminOpenTelemetryConfigurationTest {
    @Test
    void tracingOrLogsConfigurationIsExplicitlyOptIn() {
        Conditional condition = AdminOpenTelemetryConfiguration.class
                .getAnnotation(Conditional.class);
        assertNotNull(condition);
        assertEquals(AdminOtelEnabledCondition.class, condition.value()[0]);
    }
}
