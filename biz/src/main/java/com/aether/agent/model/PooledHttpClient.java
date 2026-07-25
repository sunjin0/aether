package com.aether.agent.model;

import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.conn.PoolingHttpClientConnectionManager;
import org.apache.http.util.EntityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;

/**
 * 连接池HTTP客户端（降低首字延迟）。
 * 复用TCP/TLS连接，避免每次请求都握手。
 */
@Component
public class PooledHttpClient {

    private static final Logger log = LoggerFactory.getLogger(PooledHttpClient.class);

    private CloseableHttpClient httpClient;

    @PostConstruct
    public void init() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(64);             // 最大连接数
        cm.setDefaultMaxPerRoute(32);   // 每个主机最大连接数

        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectTimeout(10_000)     // 连接超时10s
                .setSocketTimeout(300_000)     // 读超时5分钟（推理模型需要）
                .setConnectionRequestTimeout(5_000) // 从池获取连接超时5s
                .build();

        httpClient = HttpClients.custom()
                .setConnectionManager(cm)
                .setDefaultRequestConfig(requestConfig)
                .disableAutomaticRetries()
                .build();

        log.info("HTTP连接池初始化完成: maxTotal=64, maxPerRoute=32");
    }

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

        HttpStreamResult(CloseableHttpResponse response, InputStream inputStream) {
            this.response = response;
            this.inputStream = inputStream;
        }

        public InputStream getInputStream() {
            return inputStream;
        }

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
