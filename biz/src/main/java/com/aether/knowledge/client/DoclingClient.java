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

import java.util.List;
import java.util.Map;

/**
 * 表示DoclingClient。
 */
@Component
public class DoclingClient {

    private static final Logger log = LoggerFactory.getLogger(DoclingClient.class);

    private final RestTemplate restTemplate;
    private final String serviceUrl;
    private final DelegationTokenService delegationTokenService;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 创建 {@code DoclingClient} 实例。
     */
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

    /**
     * 判断是否为Enabled。
     */
    public boolean isEnabled() {
        return restTemplate != null;
    }

    /**
     * 转换当前请求。
     */
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
                /**
                 * 获取Filename。
                 */
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
                if (markdown instanceof String) return appendImageChunks((String) markdown, response.getBody());
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

    /**
     * 处理extractJsonData。
     */
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

    /**
     * 将 MCP 对文档内嵌图片生成的语义块并入正文，复用既有分块和向量化流程入库。
     */
    private String appendImageChunks(String markdown, Map<String, Object> body) {
        Object imageChunks = body.get("image_chunks");
        if (!(imageChunks instanceof List)) return markdown;
        StringBuilder result = new StringBuilder(markdown == null ? "" : markdown.trim());
        for (Object item : (List<?>) imageChunks) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> chunk = (Map<?, ?>) item;
            Object text = chunk.get("text");
            if (!(text instanceof String) || StringUtils.isBlank((String) text)) continue;
            if (result.length() > 0) result.append("\n\n");
            result.append("## 文档内嵌图片语义");
            Object page = chunk.get("page");
            if (page != null) result.append("（第 ").append(page).append(" 页）");
            result.append("\n\n").append(((String) text).trim());
        }
        return result.toString();
    }
}
