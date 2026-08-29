package com.aether.workflow.runtime;

import com.aether.exception.ServerException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * HTTP 节点的受控客户端。默认拒绝所有主机，避免工作流定义成为 SSRF 入口。
 */
@Component
public class WorkflowHttpNodeExecutor {
    private final Set<String> allowedHosts;
    private final int connectTimeoutMillis;
    private final int readTimeoutMillis;

    public WorkflowHttpNodeExecutor(@Value("${aether.workflow.http.allowed-hosts:}") String hosts,
                                    @Value("${aether.workflow.http.connect-timeout-ms:3000}") int connectTimeoutMillis,
                                    @Value("${aether.workflow.http.read-timeout-ms:10000}") int readTimeoutMillis) {
        this.allowedHosts = new LinkedHashSet<String>();
        if (StringUtils.isNotBlank(hosts)) {
            for (String host : hosts.split(",")) {
                String normalized = StringUtils.trimToNull(host);
                if (normalized != null) this.allowedHosts.add(normalized.toLowerCase());
            }
        }
        this.connectTimeoutMillis = Math.max(100, connectTimeoutMillis);
        this.readTimeoutMillis = Math.max(100, readTimeoutMillis);
    }

    public String execute(String method, String url, HttpHeaders headers, String body) {
        URI target;
        try {
            target = URI.create(url);
        } catch (Exception ex) {
            throw new ServerException(422, "HTTP 节点 URL 不合法");
        }
        String scheme = target.getScheme();
        String host = target.getHost();
        if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) || StringUtils.isBlank(host))
            throw new ServerException(422, "HTTP 节点仅支持 http/https URL");
        if (!allowedHosts.contains(host.toLowerCase()))
            throw new ServerException(422, "HTTP 节点目标主机不在 aether.workflow.http.allowed-hosts 白名单中");
        HttpMethod httpMethod;
        try {
            httpMethod = HttpMethod.valueOf(StringUtils.defaultIfBlank(method, "POST").toUpperCase());
        } catch (Exception ex) {
            throw new ServerException(422, "HTTP 节点请求方法不支持");
        }
        if (headers.getContentType() == null && StringUtils.isNotBlank(body)) headers.setContentType(MediaType.APPLICATION_JSON);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMillis);
        factory.setReadTimeout(readTimeoutMillis);
        ResponseEntity<String> response = new RestTemplate(factory).exchange(target, httpMethod, new HttpEntity<String>(body, headers), String.class);
        if (!response.getStatusCode().is2xxSuccessful())
            throw new ServerException(502, "HTTP 节点响应异常：" + response.getStatusCodeValue());
        return response.getBody() == null ? "" : response.getBody();
    }
}
