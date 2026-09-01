package com.aether.observability;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Conditional;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.LoggerFactory;
import ch.qos.logback.classic.Logger;

import javax.annotation.PreDestroy;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/** Default-off standard OpenTelemetry tracing for the Admin HTTP surface. */
@Configuration
@Conditional(AdminOtelEnabledCondition.class)
public class AdminOpenTelemetryConfiguration {
    private static final AttributeKey<String> HTTP_METHOD = AttributeKey.stringKey("http.request.method");
    private static final AttributeKey<String> URL_PATH = AttributeKey.stringKey("url.path");
    private static final AttributeKey<Long> HTTP_STATUS = AttributeKey.longKey("http.response.status_code");
    private final OpenTelemetrySdk sdk;
    private final SdkLoggerProvider loggerProvider;
    private OtelLogbackAppender logbackAppender;

    public AdminOpenTelemetryConfiguration(
            @Value("${aether.otel.admin.endpoint:http://127.0.0.1:4318/v1/traces}") String endpoint,
            @Value("${aether.otel.admin.service-name:aether-admin}") String serviceName,
            @Value("${aether.otel.admin.logs-enabled:false}") boolean logsEnabled,
            @Value("${aether.otel.admin.logs-endpoint:http://127.0.0.1:4318/v1/logs}") String logsEndpoint) {
        OtlpHttpSpanExporter exporter = OtlpHttpSpanExporter.builder().setEndpoint(endpoint).build();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName)))
                .addSpanProcessor(BatchSpanProcessor.builder(exporter).build())
                .build();
        this.sdk = OpenTelemetrySdk.builder().setTracerProvider(provider).build();
        if (logsEnabled) {
            OtlpHttpLogRecordExporter logExporter = OtlpHttpLogRecordExporter.builder().setEndpoint(logsEndpoint).build();
            this.loggerProvider = SdkLoggerProvider.builder().setResource(Resource.create(Attributes.of(AttributeKey.stringKey("service.name"), serviceName)))
                    .addLogRecordProcessor(BatchLogRecordProcessor.builder(logExporter).build()).build();
        } else this.loggerProvider = null;
    }

    @Bean
    public OpenTelemetry adminOpenTelemetry() { return sdk; }

    @javax.annotation.PostConstruct
    public void attachLogbackAppender() {
        if (loggerProvider == null) return;
        logbackAppender = new OtelLogbackAppender(loggerProvider);
        logbackAppender.setContext(((ch.qos.logback.classic.LoggerContext) LoggerFactory.getILoggerFactory()));
        logbackAppender.start();
        ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).addAppender(logbackAppender);
    }

    @Bean
    public FilterRegistrationBean<OncePerRequestFilter> adminOpenTelemetryFilter(OpenTelemetry telemetry) {
        FilterRegistrationBean<OncePerRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceFilter(telemetry));
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.LOWEST_PRECEDENCE - 20);
        return registration;
    }

    @PreDestroy
    public void shutdown() {
        if (logbackAppender != null) {
            ((Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).detachAppender(logbackAppender);
            logbackAppender.stop();
        }
        if (loggerProvider != null) loggerProvider.shutdown();
        sdk.getSdkTracerProvider().shutdown();
    }

    private static final class TraceFilter extends OncePerRequestFilter {
        private final Tracer tracer;
        private final io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator propagator =
                io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator.getInstance();

        private TraceFilter(OpenTelemetry telemetry) { this.tracer = telemetry.getTracer("com.aether.admin.http"); }

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            Context parent = propagator.extract(Context.current(), request, new TextMapGetter<HttpServletRequest>() {
                @Override public Iterable<String> keys(HttpServletRequest carrier) { return Collections.singleton("traceparent"); }
                @Override public String get(HttpServletRequest carrier, String key) { return carrier.getHeader(key); }
            });
            String normalizedPath = normalizePath(request.getRequestURI());
            Span span = tracer.spanBuilder(request.getMethod() + " " + normalizedPath)
                    .setParent(parent).setSpanKind(SpanKind.SERVER)
                    .setAttribute(HTTP_METHOD, request.getMethod())
                    .setAttribute(URL_PATH, normalizedPath).startSpan();
            try (io.opentelemetry.context.Scope ignored = span.makeCurrent()) {
                chain.doFilter(request, response);
                span.setAttribute(HTTP_STATUS, (long) response.getStatus());
                if (response.getStatus() >= 500) span.setStatus(StatusCode.ERROR);
            } catch (IOException | ServletException | RuntimeException ex) {
                span.setStatus(StatusCode.ERROR);
                throw ex;
            } finally { span.end(); }
        }

        private String normalizePath(String path) {
            if (path == null || path.isEmpty()) return "/";
            return path.replaceAll("/[0-9]+(?=/|$)", "/:id")
                    .replaceAll("/[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,}(?=/|$)", "/:id")
                    .replaceAll("/[A-Za-z0-9_-]{24,}(?=/|$)", "/:id");
        }
    }
}
