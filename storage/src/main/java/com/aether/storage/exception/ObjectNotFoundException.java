package com.aether.storage.exception;

/**
 * Raised when a requested object does not exist in its configured bucket.
 */
public class ObjectNotFoundException extends RuntimeException {
    /**
     * 创建 {@code ObjectNotFoundException} 实例。
     */
    public ObjectNotFoundException(String bucket, String objectKey, Throwable cause) {
        super("Object not found: " + bucket + "/" + objectKey, cause);
    }
}
