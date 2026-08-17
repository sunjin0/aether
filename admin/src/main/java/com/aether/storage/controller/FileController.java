package com.aether.storage.controller;

import com.aether.entity.WebResponse;
import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.permission.Permission;
import com.aether.storage.exception.ObjectNotFoundException;
import com.aether.storage.exception.ObjectStorageUnavailableException;
import com.aether.storage.model.FileUploadResult;
import com.aether.storage.service.ObjectStorageService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 提供文件相关的 REST 接口。
 */
@Api(tags = "通用文件 API")
@RestController
@RequestMapping("/api/file")
public class FileController {
    private static final MediaType DEFAULT_MEDIA_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private final ObjectStorageService objectStorageService;
    private final String bucket;
    private final String chatAttachmentBucket;
    private final long maxFileSize;

    /**
     * 创建 {@code FileController} 实例。
     */
    public FileController(ObjectStorageService objectStorageService,
                          @Value("${storage.file.bucket:${MINIO_FILE_BUCKET:aether}}") String bucket,
                          @Value("${agent.chat.attachment.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String chatAttachmentBucket,
                          @Value("${storage.file.max-size:52428800}") long maxFileSize) {
        this.objectStorageService = objectStorageService;
        this.bucket = bucket;
        this.chatAttachmentBucket = chatAttachmentBucket;
        this.maxFileSize = maxFileSize;
    }

    /**
     * 上传当前请求。
     */
    @ApiOperation("上传文件")
    @PostMapping("/upload")
    public WebResponse<FileUploadResult> upload(@RequestParam("file") MultipartFile file) {
        validateFile(file);
        String fileName = normalizeFileName(file.getOriginalFilename());
        String objectKey = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                + "/" + UUID.randomUUID().toString().replace("-", "") + extension(fileName);
        objectStorageService.upload(bucket, objectKey, file);

        String baseUrl = ServletUriComponentsBuilder.fromCurrentContextPath().path("/api/file").build().toUriString();
        FileUploadResult result = new FileUploadResult();
        result.setObjectKey(objectKey);
        result.setFileName(fileName);
        result.setContentType(StringUtils.defaultIfBlank(file.getContentType(), DEFAULT_MEDIA_TYPE.toString()));
        result.setSize(file.getSize());
        result.setPreviewUrl(ServletUriComponentsBuilder.fromHttpUrl(baseUrl).path("/preview")
                .queryParam("objectKey", objectKey).queryParam("fileName", fileName).build().encode().toUriString());
        result.setDownloadUrl(ServletUriComponentsBuilder.fromHttpUrl(baseUrl).path("/download")
                .queryParam("objectKey", objectKey).queryParam("fileName", fileName).build().encode().toUriString());
        return WebResponse.OK(result);
    }

    /**
     * 预览文件。
     */
    @ApiOperation("预览文件")
    @GetMapping("/preview")
    public ResponseEntity<byte[]> preview(@RequestParam String objectKey,
                                          @RequestParam(required = false) String fileName,
                                          @RequestParam(required = false) String contentType) {
        return fileResponse(objectKey, fileName, contentType, true);
    }

    /**
     * 下载文件。
     */
    @ApiOperation("下载文件")
    @GetMapping("/download")
    public ResponseEntity<byte[]> download(@RequestParam String objectKey,
                                           @RequestParam(required = false) String fileName,
                                           @RequestParam(required = false) String contentType) {
        return fileResponse(objectKey, fileName, contentType, false);
    }

    /**
     * 预览对话Attachment。
     */
    @ApiOperation("预览聊天附件")
    @GetMapping("/chat/preview")
    public ResponseEntity<byte[]> previewChatAttachment(@RequestParam String objectKey,
                                                        @RequestParam(required = false) String fileName,
                                                        @RequestParam(required = false) String contentType) {
        validateChatObjectKey(objectKey);
        return fileResponse(chatAttachmentBucket, objectKey, fileName, contentType, true);
    }

    /**
     * 下载对话Attachment。
     */
    @ApiOperation("下载聊天附件")
    @GetMapping("/chat/download")
    public ResponseEntity<byte[]> downloadChatAttachment(@RequestParam String objectKey,
                                                         @RequestParam(required = false) String fileName,
                                                         @RequestParam(required = false) String contentType) {
        validateChatObjectKey(objectKey);
        return fileResponse(chatAttachmentBucket, objectKey, fileName, contentType, false);
    }

    /**
     * 文件Response。
     */
    private ResponseEntity<byte[]> fileResponse(String objectKey, String fileName, String contentType, boolean inline) {
        validateObjectKey(objectKey);
        return fileResponse(bucket, objectKey, fileName, contentType, inline);
    }

    /**
     * 文件Response。
     */
    private ResponseEntity<byte[]> fileResponse(String bucket, String objectKey, String fileName, String contentType, boolean inline) {
        String outputName = normalizeFileName(StringUtils.defaultIfBlank(fileName, objectKey.substring(objectKey.lastIndexOf('/') + 1)));
        byte[] content;
        try {
            content = objectStorageService.getObject(bucket, objectKey);
        } catch (ObjectNotFoundException e) {
            throw new ServerException(404, I18nUtils.getMessage("file.not.found"));
        } catch (ObjectStorageUnavailableException e) {
            throw new ServerException(503, I18nUtils.getMessage("file.storage.unavailable"));
        }
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(outputName, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(parseMediaType(contentType, outputName))
                .contentLength(content.length)
                .body(content);
    }

    /**
     * 校验文件。
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new ServerException(422, I18nUtils.getMessage("file.required"));
        if (file.getSize() > maxFileSize) throw new ServerException(413, I18nUtils.getMessage("file.size.exceeded"));
    }

    /**
     * 校验ObjectKey。
     */
    private void validateObjectKey(String objectKey) {
        if (StringUtils.isBlank(objectKey) || objectKey.startsWith("/") || objectKey.contains("..") || objectKey.contains("\\")) {
            throw new ServerException(400, I18nUtils.getMessage("file.identifier.invalid"));
        }
    }

    /**
     * 校验对话ObjectKey。
     */
    private void validateChatObjectKey(String objectKey) {
        validateObjectKey(objectKey);
        if (!objectKey.startsWith("chat/")) {
            throw new ServerException(400, I18nUtils.getMessage("file.identifier.invalid"));
        }
    }

    /**
     * 规范化文件Name。
     */
    private String normalizeFileName(String fileName) {
        String normalized = StringUtils.defaultIfBlank(fileName, "file").replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_");
        return StringUtils.defaultIfBlank(normalized, "file");
    }

    /**
     * 处理extension。
     */
    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index <= 0 || index == fileName.length() - 1) return "";
        String extension = fileName.substring(index).toLowerCase();
        return extension.matches("\\.[a-z0-9]{1,10}") ? extension : "";
    }

    /**
     * 解析MediaType。
     */
    private MediaType parseMediaType(String contentType, String fileName) {
        try {
            if (StringUtils.isNotBlank(contentType)) return MediaType.parseMediaType(contentType);
            MediaType detected = MediaTypeFactory.getMediaType(fileName).orElse(DEFAULT_MEDIA_TYPE);
            return detected;
        } catch (Exception ignored) {
            return DEFAULT_MEDIA_TYPE;
        }
    }
}
