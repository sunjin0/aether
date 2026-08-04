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

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;

@Component
public class DoclingClient {

    private static final Logger log = LoggerFactory.getLogger(DoclingClient.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;
    private final DelegationTokenService delegationTokenService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DoclingClient(@Value("${docling.service.url:}") String serviceUrl,
                         DelegationTokenService delegationTokenService) {
        this.serviceUrl = serviceUrl;
        this.delegationTokenService = delegationTokenService;
        if (StringUtils.isNotBlank(serviceUrl)) {
            RequestConfig config = RequestConfig.custom()
                    .setConnectTimeout(30000)
                    .setSocketTimeout(120000)
                    .build();
            CloseableHttpClient httpClient = HttpClientBuilder.create()
                    .setDefaultRequestConfig(config)
                    .build();
            HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory(httpClient);
            this.restTemplate = new RestTemplate(factory);
        } else {
            this.restTemplate = null;
        }
    }

    public boolean isEnabled() {
        return restTemplate != null;
    }

    public String convert(String fileName, byte[] bytes) {
        return convert(fileName, bytes, "markdown", false);
    }

    /**
     * @param outputFormat "markdown", "json", or "both"
     */
    public String convert(String fileName, byte[] bytes, String outputFormat) {
        return convert(fileName, bytes, outputFormat, false);
    }

    /**
     * @param enableOcr whether Docling should perform OCR for image-based input
     */
    public String convert(String fileName, byte[] bytes, String outputFormat, boolean enableOcr) {
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
            body.add("output_format", outputFormat != null ? outputFormat : "markdown");
            body.add("ocr", String.valueOf(enableOcr));
            body.add("extract_tables", "true");

            HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> response = restTemplate.exchange(
                    serviceUrl + "/api/convert-file", org.springframework.http.HttpMethod.POST,
                    requestEntity, Map.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String fmt = outputFormat != null ? outputFormat : "markdown";
                if ("json".equals(fmt)) {
                    return extractJsonData(response.getBody());
                }
                Object markdown = response.getBody().get("markdown");
                if (markdown instanceof String) return (String) markdown;
                if ("both".equals(fmt)) {
                    return extractJsonData(response.getBody());
                }
            }
            return null;
        } catch (Exception e) {
            log.error("Docling service call failed for {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    private String extractJsonData(Map<String, Object> body) {
        Object jsonData = body.get("json_data");
        if (jsonData instanceof String) return (String) jsonData;
        if (jsonData instanceof Map) {
            try {
                return OBJECT_MAPPER.writeValueAsString(jsonData);
            } catch (Exception e) {
                log.warn("Failed to serialize json_data", e);
            }
        }
        return jsonData != null ? jsonData.toString() : null;
    }
}
