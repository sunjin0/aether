package com.aether.storage.service.impl;

import com.aether.storage.exception.ObjectNotFoundException;
import com.aether.storage.exception.ObjectStorageUnavailableException;
import com.aether.storage.service.ObjectStorageService;
import com.aliyun.oss.ClientBuilderConfiguration;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.GeneratePresignedUrlRequest;
import com.aliyun.oss.model.OSSObject;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.ResponseHeaderOverrides;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.Date;

/**
 * 阿里云 OSS 私有对象存储实现。
 *
 * <p>启用条件：{@code storage.provider=oss}。Bucket 需在阿里云控制台预先创建，
 * 防止应用运行时意外创建产生费用或使用错误地域。</p>
 */
@Service
@ConditionalOnProperty(name = "storage.provider", havingValue = "oss")
public class AliyunOssObjectStorageService implements ObjectStorageService {
    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";

    private final String endpoint;
    private final String publicEndpoint;
    private final String accessKeyId;
    private final String accessKeySecret;

    public AliyunOssObjectStorageService(@Value("${storage.oss.endpoint:${OSS_ENDPOINT:}}") String endpoint,
                                         @Value("${storage.oss.public-endpoint:${OSS_PUBLIC_ENDPOINT:}}") String publicEndpoint,
                                         @Value("${storage.oss.access-key-id:${OSS_ACCESS_KEY_ID:}}") String accessKeyId,
                                         @Value("${storage.oss.access-key-secret:${OSS_ACCESS_KEY_SECRET:}}") String accessKeySecret) {
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
        this.accessKeyId = accessKeyId;
        this.accessKeySecret = accessKeySecret;
    }

    @Override
    public String upload(String bucket, String objectKey, MultipartFile file) {
        try (InputStream input = file.getInputStream()) {
            putObject(bucket, objectKey, input, file.getSize(), file.getContentType());
            return objectKey;
        } catch (Exception e) {
            throw unavailable("uploading object", e);
        }
    }

    @Override
    public String upload(String bucket, String objectKey, byte[] content, String contentType) {
        try (InputStream input = new ByteArrayInputStream(content)) {
            putObject(bucket, objectKey, input, content.length, contentType);
            return objectKey;
        } catch (Exception e) {
            throw unavailable("uploading object", e);
        }
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds) {
        return presignedGetUrl(bucket, objectKey, expirySeconds, null);
    }

    @Override
    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds, String responseContentType) {
        OSS client = client(!blank(publicEndpoint));
        try {
            ensureBucketExists(client, bucket);
            GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucket, objectKey, HttpMethod.GET);
            request.setExpiration(new Date(System.currentTimeMillis() + expirySeconds * 1000L));
            if (!blank(responseContentType)) {
                ResponseHeaderOverrides headers = new ResponseHeaderOverrides();
                headers.setContentType(responseContentType);
                request.setResponseHeaders(headers);
            }
            URL url = client.generatePresignedUrl(request);
            return url.toString();
        } catch (Exception e) {
            throw unavailable("generating download URL", e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public byte[] getObject(String bucket, String objectKey) {
        OSS client = client(false);
        try (OSSObject object = client.getObject(bucket, objectKey);
             InputStream input = object.getObjectContent();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            for (int count; (count = input.read(buffer)) != -1; ) {
                output.write(buffer, 0, count);
            }
            return output.toByteArray();
        } catch (OSSException e) {
            if ("NoSuchKey".equals(e.getErrorCode()) || "NoSuchBucket".equals(e.getErrorCode())) {
                throw new ObjectNotFoundException(bucket, objectKey, e);
            }
            throw unavailable("reading object", e);
        } catch (Exception e) {
            throw unavailable("reading object", e);
        } finally {
            client.shutdown();
        }
    }

    @Override
    public void removeObject(String bucket, String objectKey) {
        OSS client = client(false);
        try {
            ensureBucketExists(client, bucket);
            client.deleteObject(bucket, objectKey);
        } catch (Exception e) {
            throw unavailable("removing object", e);
        } finally {
            client.shutdown();
        }
    }

    private void putObject(String bucket, String objectKey, InputStream input, long contentLength, String contentType) {
        OSS client = client(false);
        try {
            ensureBucketExists(client, bucket);
            ObjectMetadata metadata = new ObjectMetadata();
            metadata.setContentLength(contentLength);
            metadata.setContentType(blank(contentType) ? DEFAULT_CONTENT_TYPE : contentType);
            client.putObject(bucket, objectKey, input, metadata);
        } finally {
            client.shutdown();
        }
    }

    private void ensureBucketExists(OSS client, String bucket) {
        if (!client.doesBucketExist(bucket)) {
            throw new IllegalStateException("OSS bucket does not exist: " + bucket);
        }
    }

    private OSS client(boolean usePublicEndpoint) {
        String targetEndpoint = usePublicEndpoint ? publicEndpoint : endpoint;
        if (blank(targetEndpoint) || blank(accessKeyId) || blank(accessKeySecret)) {
            throw new IllegalStateException("Aliyun OSS is not configured");
        }
        ClientBuilderConfiguration configuration = new ClientBuilderConfiguration();
        if (usePublicEndpoint && !publicEndpoint.equals(endpoint)) {
            configuration.setSupportCname(true);
        }
        return new OSSClientBuilder().build(targetEndpoint, accessKeyId, accessKeySecret, configuration);
    }

    private ObjectStorageUnavailableException unavailable(String operation, Exception cause) {
        return new ObjectStorageUnavailableException(operation, cause);
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
