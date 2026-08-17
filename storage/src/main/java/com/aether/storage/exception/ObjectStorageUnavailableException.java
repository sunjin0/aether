package com.aether.storage.exception;

/**
 * Raised when the object store cannot serve a request.
 */
public class ObjectStorageUnavailableException extends RuntimeException {
    /**
     * 创建 {@code ObjectStorageUnavailableException} 实例。
     */
    public ObjectStorageUnavailableException(String operation, Throwable cause) {
        super("Object storage unavailable while " + operation, cause);
    }
}
