package com.aether.agent.model;

import lombok.Data;

/**
 * 原生传给模型的附件。文件链接必须是短时签名 URL，不能使用对象存储内部地址。
 */
@Data
public class ModelInputFile {
    private String fileName;
    private String contentType;
    private String fileUrl;

    public ModelInputFile(String fileName, String contentType, String fileUrl) {
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileUrl = fileUrl;
    }
}
