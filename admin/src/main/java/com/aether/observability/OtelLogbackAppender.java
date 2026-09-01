package com.aether.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import io.opentelemetry.api.logs.Logger;
import io.opentelemetry.api.logs.LoggerProvider;
import io.opentelemetry.api.logs.Severity;
import io.opentelemetry.context.Context;

import java.util.Locale;
import java.util.regex.Pattern;

/** Logback bridge that emits only redacted, formatted log text to OTel Logs. */
final class OtelLogbackAppender extends AppenderBase<ILoggingEvent> {
    private static final Pattern SECRET = Pattern.compile("(?i)(password|secret|token|api[-_ ]?key|authorization)(\\s*[:=]\\s*)[^,; ]+");
    private static final ThreadLocal<Boolean> EMITTING = new ThreadLocal<>();
    private final Logger logger;

    OtelLogbackAppender(LoggerProvider provider) { this.logger = provider.get("com.aether.admin.logback"); }

    @Override
    protected void append(ILoggingEvent event) {
        if (Boolean.TRUE.equals(EMITTING.get())) return;
        EMITTING.set(Boolean.TRUE);
        try {
        String body = redact(event.getFormattedMessage());
        Severity severity;
        switch (event.getLevel().toString().toUpperCase(Locale.ROOT)) {
            case "ERROR": severity = Severity.ERROR; break;
            case "WARN": severity = Severity.WARN; break;
            case "DEBUG": severity = Severity.DEBUG; break;
            case "TRACE": severity = Severity.TRACE; break;
            default: severity = Severity.INFO;
        }
        logger.logRecordBuilder().setContext(Context.current()).setSeverity(severity)
                .setSeverityText(event.getLevel().toString()).setBody(body).emit();
        } finally {
            EMITTING.remove();
        }
    }

    static String redact(String message) {
        return SECRET.matcher(message == null ? "" : message).replaceAll("$1$2[REDACTED]");
    }
}
