package com.aether.storage.exception;

/** Raised when a requested object does not exist in its configured bucket. */
public class ObjectNotFoundException extends RuntimeException {
    public ObjectNotFoundException(String bucket, String objectKey, Throwable cause) {
        super("Object not found: " + bucket + "/" + objectKey, cause);
    }
}
