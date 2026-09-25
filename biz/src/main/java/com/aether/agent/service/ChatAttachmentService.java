package com.aether.agent.service;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.knowledge.service.impl.KnowledgeDocumentContentExtractor;
import com.aether.storage.service.ObjectStorageService;
import com.aether.agent.model.ModelInputFile;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import java.util.ArrayList;
import java.util.List;

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
            // Persist first so native model file inputs can be used even when local
            // OCR/text extraction is unavailable or fails.
            String accountId = CurrentUser.userId();
            String accountPrefix = StringUtils.isBlank(accountId) ? "" : accountId + "/";
            String objectKey = "chat/" + accountPrefix + LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))
                    + "/" + UUID.randomUUID().toString().replace("-", "") + suffix(fileName);
            long uploadStart = System.currentTimeMillis();
            objectStorageService.upload(bucket, objectKey, file);
            long uploadMs = System.currentTimeMillis() - uploadStart;
            long extractStart = System.currentTimeMillis();
            String content;
            try {
                content = StringUtils.trimToEmpty(contentExtractor.extractForChat(fileName, bytes));
            } catch (Exception extractionFailure) {
                log.info("聊天附件本地文本识别失败，保留原文件供模型读取: name={}", fileName);
                content = "";
            }
            long extractMs = System.currentTimeMillis() - extractStart;
            if (content.length() > maxExtractedChars) {
                content = content.substring(0, maxExtractedChars) + "\n\n[文件内容因长度限制已截断]";
            }
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
     * 读取聊天附件原始字节，供业务前端回显已上传的文件。
     *
     * <p>objectKey 是客户端传回来的值，因此这里沿用 {@link #resolveNativeFiles} 的同一道前缀校验：
     * 只允许落在当前账号的 {@code chat/} 前缀下，防止拿它当任意对象存储的读取口子。
     * 聊天附件没有落库的归属台账（它只随一次对话请求存在），所以前缀是当前可用的边界。</p>
     */
    public byte[] readOwnedAttachment(String objectKey) {
        String key = StringUtils.trimToNull(objectKey);
        if (key == null) throw new ServerException(422, I18nUtils.getMessage("agent.chat.attachment.required"));
        String accountId = CurrentUser.userId();
        String allowedPrefix = "chat/" + (StringUtils.isBlank(accountId) ? "" : accountId + "/");
        // 只看前缀还不够："chat/account-1/../account-2/x" 也是以它开头的，必须把上跳段一并挡掉。
        if (!key.startsWith(allowedPrefix) || hasTraversalSegment(key))
            throw new ServerException(404, I18nUtils.getMessage("agent.chat.attachment.not-found"));
        return objectStorageService.getObject(bucket, key);
    }

    /** 对象键里出现 "." / ".." 段就说明调用方在试图越出自己那一层目录。 */
    private boolean hasTraversalSegment(String key) {
        for (String segment : key.split("/")) {
            if (".".equals(segment) || "..".equals(segment)) return true;
        }
        return false;
    }

    /**
     * 将客户端在上传接口获得的附件元数据还原为模型原生文件输入。
     * objectKey 只由服务端用于签发短时 URL，客户端不能注入任意外部链接。
     */
    public List<ModelInputFile> resolveNativeFiles(String attachments) {
        List<ModelInputFile> files = new ArrayList<>();
        if (StringUtils.isBlank(attachments)) return files;
        try {
            JSONArray values = JSONArray.parseArray(attachments);
            if (values == null || values.size() > 3) return files;
            String accountId = CurrentUser.userId();
            String allowedPrefix = "chat/" + (StringUtils.isBlank(accountId) ? "" : accountId + "/");
            for (int i = 0; i < values.size(); i++) {
                JSONObject value = values.getJSONObject(i);
                if (value == null) continue;
                String objectKey = StringUtils.trimToNull(value.getString("objectKey"));
                String fileName = normalizeFileName(value.getString("fileName"));
                if (objectKey == null || !objectKey.startsWith(allowedPrefix)) continue;
                files.add(new ModelInputFile(fileName,
                        StringUtils.defaultIfBlank(value.getString("contentType"), "application/octet-stream"),
                        objectStorageService.presignedGetUrl(bucket, objectKey, 300)));
            }
        } catch (Exception e) {
            log.warn("聊天附件原生模型链接准备失败，将继续使用本地识别结果", e);
        }
        return files;
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
