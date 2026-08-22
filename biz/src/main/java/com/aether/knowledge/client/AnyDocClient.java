package com.aether.knowledge.client;

import com.aether.agent.service.DelegationTokenService;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * AnyDoc document-to-Markdown conversion client.
 */
@Component
public class AnyDocClient {
    private static final Logger log = LoggerFactory.getLogger(AnyDocClient.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;
    private final DelegationTokenService delegationTokenService;

    public AnyDocClient(@Value("${anydoc.service.url:}") String serviceUrl,
                        @Value("${anydoc.service.socket-timeout:600000}") int socketTimeout,
                        DelegationTokenService delegationTokenService) {
        this.serviceUrl = serviceUrl;
        this.delegationTokenService = delegationTokenService;
        if (StringUtils.isNotBlank(serviceUrl)) {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(30000)
                    .setSocketTimeout(socketTimeout)
                    .build();
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .build();
            this.restTemplate = new RestTemplate(new HttpComponentsClientHttpRequestFactory(httpClient));
        } else {
            this.restTemplate = null;
        }
    }

    public boolean isEnabled() {
        return restTemplate != null;
    }

    /**
     * Converts a source document to Markdown. A null result means AnyDoc could
     * not convert the document, so callers may use a local fallback.
     */
    public String convertToMarkdown(String fileName, byte[] bytes) {
        if (!isEnabled()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.MULTIPART_FORM_DATA);
            headers.setBearerAuth(delegationTokenService.createDocumentProcessingToken());

            MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
            body.add("file", new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return fileName;
                }
            });
            body.add("output_format", "markdown");

            ResponseEntity<Map> response = restTemplate.exchange(
                    serviceUrl + "/api/convert-file", org.springframework.http.HttpMethod.POST,
                    new HttpEntity<>(body, headers), Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Object markdown = response.getBody().get("markdown");
                if (markdown instanceof String && StringUtils.isNotBlank((String) markdown)) {
                    return (String) markdown;
                }
            }
        } catch (Exception e) {
            log.warn("AnyDoc conversion failed for {}; falling back to local parser", fileName, e);
        }
        return null;
    }
}
