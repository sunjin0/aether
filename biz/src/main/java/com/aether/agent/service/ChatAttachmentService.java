package com.aether.agent.service;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.knowledge.service.impl.KnowledgeDocumentContentExtractor;
import com.aether.storage.service.ObjectStorageService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Parses and persists files uploaded with an agent chat message.
 */
@Service
public class ChatAttachmentService {
    private static final Logger log = LoggerFactory.getLogger(ChatAttachmentService.class);
    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<String>(Arrays.asList(
            "txt", "md", "pdf", "docx", "xlsx", "png", "jpg", "jpeg", "webp"));

    private final KnowledgeDocumentContentExtractor contentExtractor;
    private final ObjectStorageService objectStorageService;
    private final String bucket;
    private final long maxFileSize;
    private final int maxExtractedChars;

    /**
     * 创建 {@code ChatAttachmentService} 实例。
     */
    public ChatAttachmentService(KnowledgeDocumentContentExtractor contentExtractor,
                                 ObjectStorageService objectStorageService,
                                 @Value("${agent.chat.attachment.bucket:${MINIO_CHAT_ATTACHMENT_BUCKET:aether-chat}}") String bucket,
                                 @Value("${agent.chat.attachment.max-file-size:10485760}") long maxFileSize,
                                 @Value("${agent.chat.attachment.max-extracted-chars:100000}") int maxExtractedChars) {
        this.contentExtractor = contentExtractor;
        this.objectStorageService = objectStorageService;
        this.bucket = bucket;
        this.maxFileSize = maxFileSize;
        this.maxExtractedChars = maxExtractedChars;
    }

    /**
     * 处理当前请求。
     */
    public ChatAttachment process(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachment.required"));
        }
        if (file.getSize() > maxFileSize) {
            throw new ServerException(413, I18nUtils.getMessage("agent.chat.attachment.size.exceeded"));
        }
        String fileName = normalizeFileName(file.getOriginalFilename());
        if (!SUPPORTED_EXTENSIONS.contains(extension(fileName))) {
            throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachment.type.unsupported"));
        }
        try {
            long totalStart = System.currentTimeMillis();
            byte[] bytes = file.getBytes();
            long extractStart = System.currentTimeMillis();
            String content = StringUtils.trimToEmpty(contentExtractor.extractForChat(fileName, bytes));
            long extractMs = System.currentTimeMillis() - extractStart;
            if (StringUtils.isBlank(content)) {
                throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachment.text.unrecognized"));
            }
            if (content.length() > maxExtractedChars) {
                content = content.substring(0, maxExtractedChars) + "\n\n[文件内容因长度限制已截断]";
            }
            String tenantId = CurrentUser.getUser() == null ? null : CurrentUser.getUser().get("tenantId");
            String tenantPrefix = StringUtils.isBlank(tenantId) ? "" : tenantId + "/";
            String objectKey = "chat/" + tenantPrefix + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                    + "/" + UUID.randomUUID().toString().replace("-", "") + suffix(fileName);
            long uploadStart = System.currentTimeMillis();
            objectStorageService.upload(bucket, objectKey, file);
            long uploadMs = System.currentTimeMillis() - uploadStart;
            log.info("聊天附件处理完成: name={}, size={}B, extract={}ms, upload={}ms, total={}ms",
                    fileName, file.getSize(), extractMs, uploadMs, System.currentTimeMillis() - totalStart);
            return new ChatAttachment(fileName, StringUtils.defaultIfBlank(file.getContentType(), "application/octet-stream"),
                    file.getSize(), objectKey, content);
        } catch (ServerException e) {
            throw e;
        } catch (Exception e) {
            throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachment.parse.failed"));
        }
    }

    /**
     * 规范化文件Name。
     */
    private String normalizeFileName(String value) {
        String result = StringUtils.defaultIfBlank(value, "file").replace('\\', '/');
        result = result.substring(result.lastIndexOf('/') + 1).replaceAll("[\\r\\n\\\"]", "_");
        return StringUtils.defaultIfBlank(result, "file");
    }

    /**
     * 处理extension。
     */
    private String extension(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index < 1 ? "" : fileName.substring(index + 1).toLowerCase();
    }

    /**
     * 处理suffix。
     */
    private String suffix(String fileName) {
        String extension = extension(fileName);
        return StringUtils.isBlank(extension) ? "" : "." + extension;
    }

    /**
     * 表示对话Attachment。
     */
    public static class ChatAttachment {
        private final String fileName;
        private final String contentType;
        private final long size;
        private final String objectKey;
        private final String extractedContent;

        /**
         * 创建 {@code ChatAttachment} 实例。
         */
        public ChatAttachment(String fileName, String contentType, long size, String objectKey, String extractedContent) {
            this.fileName = fileName;
            this.contentType = contentType;
            this.size = size;
            this.objectKey = objectKey;
            this.extractedContent = extractedContent;
        }

        /**
         * 获取文件Name。
         */
        public String getFileName() {
            return fileName;
        }

        /**
         * 获取ContentType。
         */
        public String getContentType() {
            return contentType;
        }

        /**
         * 获取Size。
         */
        public long getSize() {
            return size;
        }

        /**
         * 获取ObjectKey。
         */
        public String getObjectKey() {
            return objectKey;
        }

        /**
         * 获取ExtractedContent。
         */
        public String getExtractedContent() {
            return extractedContent;
        }
    }
}
