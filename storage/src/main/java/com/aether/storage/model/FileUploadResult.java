package com.aether.storage.model;

import lombok.Getter;
import lombok.Setter;

/** 通用文件上传结果。对象键由服务端生成，可用于后续预览和下载。 */
@Setter
@Getter
public class FileUploadResult {
    private String objectKey;
    private String fileName;
    private String contentType;
    private long size;
    private String previewUrl;
    private String downloadUrl;

}
