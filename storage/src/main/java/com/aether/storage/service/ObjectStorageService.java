package com.aether.storage.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 私有对象存储抽象，供知识库等业务模块复用。
 * 前端不得直接调用该接口或持有 MinIO 访问凭据。
 */
public interface ObjectStorageService {
    /** 上传一个对象；bucket 为存储桶名称，objectKey 为业务生成的对象键。 */
    String upload(String bucket, String objectKey, MultipartFile file);
    /** 生成指定有效期（秒）的 GET 临时访问 URL。 */
    String presignedGetUrl(String bucket, String objectKey, int expirySeconds);
    /** 读取对象的全部字节；仅供后端解析器使用。 */
    byte[] getObject(String bucket, String objectKey);
}
