package com.aether.agent.model;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.pool.PoolStats;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 连接池HTTP客户端（降低首字延迟）。
 * 复用TCP/TLS连接，避免每次请求都握手。
 */
@Component
public class PooledHttpClient {

    private static final Logger log = LoggerFactory.getLogger(PooledHttpClient.class);

    private CloseableHttpClient httpClient;
    private PoolingHttpClientConnectionManager connectionManager;

    /**
     * 初始化所需资源。
     */
    @PostConstruct
    public void init() {
        connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(64);
        connectionManager.setDefaultMaxPerRoute(32);
        connectionManager.setValidateAfterInactivity(5_000);

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10_000)
                .setSocketTimeout(300_000)
                .setConnectionRequestTimeout(5_000)
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .setConnectionTimeToLive(60, TimeUnit.SECONDS)
                .evictExpiredConnections()
                .evictIdleConnections(30, TimeUnit.SECONDS)
                .disableAutomaticRetries()
                .build();

        log.info("HTTP连接池初始化完成: maxTotal=64, maxPerRoute=32, validateAfterInactivity=5s, connTTL=60s");
    }

    /**
     * 释放所持有的资源。
     */
    @PreDestroy
    public void destroy() {
        try {
            httpClient.close();
        } catch (IOException e) {
            log.warn("关闭HTTP连接池失败", e);
        }
    }

    /**
     * 发送POST请求并返回响应流（用于SSE）。
     * 调用方必须在使用完InputStream后关闭它。
     */
    public HttpStreamResult postStream(String url, String jsonBody, String authorization) {
        HttpPost post = new HttpPost(url);
        post.setHeader("Content-Type", "application/json");
        if (authorization != null) {
            post.setHeader("Authorization", authorization);
        }
        post.setEntity(new StringEntity(jsonBody, "UTF-8"));

        try {
            PoolStats before = connectionManager == null ? null : connectionManager.getTotalStats();
            if (before != null && before.getPending() > 0) {
                log.warn("模型HTTP连接池存在等待: pending={}, leased={}, available={}, max={}",
                        before.getPending(), before.getLeased(), before.getAvailable(), before.getMax());
            }
            CloseableHttpResponse response = httpClient.execute(post);
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode < 200 || statusCode >= 300) {
                HttpEntity entity = response.getEntity();
                String body = entity != null ? EntityUtils.toString(entity, "UTF-8") : "(no body)";
                response.close();
                throw new RuntimeException("模型调用失败, status=" + statusCode + ", body=" + body);
            }
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                response.close();
                throw new RuntimeException("模型调用返回空响应体, status=" + statusCode);
            }
            InputStream inputStream = entity.getContent();
            return new HttpStreamResult(response, inputStream);
        } catch (IOException e) {
            post.abort();
            throw new RuntimeException("模型调用IO异常", e);
        }
    }

    /**
     * 流式响应结果，使用方用完后必须调用close()。
     */
    public static class HttpStreamResult implements AutoCloseable {
        private final CloseableHttpResponse response;
        private final InputStream inputStream;

        /**
         * 创建 {@code HttpStreamResult} 实例。
         */
        HttpStreamResult(CloseableHttpResponse response, InputStream inputStream) {
            this.response = response;
            this.inputStream = inputStream;
        }

        /**
         * 获取InputStream。
         */
        public InputStream getInputStream() {
            return inputStream;
        }

        /**
         * 关闭当前资源。
         */
        @Override
        public void close() {
            try {
                inputStream.close();
            } catch (IOException e) {
                log.warn("关闭流式响应输入流失败", e);
            }
            try {
                response.close();
            } catch (IOException e) {
                log.warn("关闭流式HTTP响应失败", e);
            }
        }
    }
}
