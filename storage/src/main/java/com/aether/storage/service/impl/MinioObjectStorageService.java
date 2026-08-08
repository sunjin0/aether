package com.aether.storage.service.impl;

import com.aether.storage.service.ObjectStorageService;
import com.aether.storage.exception.ObjectNotFoundException;
import com.aether.storage.exception.ObjectStorageUnavailableException;
import io.minio.errors.ErrorResponseException;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@Service
/**
 * MinIO 私有对象存储实现。
 * 配置键：storage.minio.endpoint、access-key、secret-key；未配置时仅在实际存储操作时抛出异常，避免阻塞应用启动。
 */
public class MinioObjectStorageService implements ObjectStorageService {
    private final String endpoint, publicEndpoint, accessKey, secretKey;

    public MinioObjectStorageService(@Value("${storage.minio.endpoint:${MINIO_ENDPOINT:}}") String endpoint,
                                      @Value("${storage.minio.public-endpoint:${MINIO_PUBLIC_ENDPOINT:}}") String publicEndpoint,
                                      @Value("${storage.minio.access-key:${MINIO_ACCESS_KEY:}}") String accessKey,
                                      @Value("${storage.minio.secret-key:${MINIO_SECRET_KEY:}}") String secretKey) {
        this.endpoint = endpoint;
        this.publicEndpoint = publicEndpoint;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
    }

    /**
     * 上传文件流到指定私有 Bucket；调用方负责生成稳定且不包含敏感信息的对象键。
     */
    public String upload(String bucket, String objectKey, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            MinioClient client = client();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(in, file.getSize(), -1).contentType(blank(file.getContentType()) ? "application/octet-stream" : file.getContentType()).build());
            return objectKey;
        } catch (Exception e) {
            throw new ObjectStorageUnavailableException("uploading object", e);
        }
    }

    public String upload(String bucket, String objectKey, byte[] content, String contentType) {
        try (InputStream in = new java.io.ByteArrayInputStream(content)) {
            MinioClient client = client();
            if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            }
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(in, content.length, -1).contentType(blank(contentType) ? "application/octet-stream" : contentType).build());
            return objectKey;
        } catch (Exception e) {
            throw new ObjectStorageUnavailableException("uploading object", e);
        }
    }

    /**
     * 仅生成 GET 类型的临时签名 URL，知识库预览当前使用 600 秒。
     */
    public String presignedGetUrl(String bucket, String objectKey, int expirySeconds) {
        try {
            return client(blank(publicEndpoint) ? endpoint : publicEndpoint)
                    .getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().method(Method.GET).bucket(bucket).object(objectKey).expiry(expirySeconds).build());
        } catch (Exception e) {
            throw new ObjectStorageUnavailableException("generating download URL", e);
        }
    }

    /**
     * 下载对象内容，供 PDF/DOCX 等后端解析器读取。
     */
    public byte[] getObject(String bucket, String objectKey) {
        try (InputStream in = client().getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build()); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            for (int n; (n = in.read(b)) != -1; ) out.write(b, 0, n);
            return out.toByteArray();
        } catch (Exception e) {
            throw new ObjectStorageUnavailableException("reading object", e);
        }
    }

    public void removeObject(String bucket, String objectKey) {
        try {
            client().removeObject(RemoveObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (Exception e) {
            throw new ObjectStorageUnavailableException("removing object", e);
        }
    }

    private MinioClient client() {
        return client(endpoint);
    }

    private MinioClient client(String endpoint) {
        if (blank(endpoint) || blank(accessKey) || blank(secretKey))
            throw new IllegalStateException("MinIO is not configured");
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    private boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
