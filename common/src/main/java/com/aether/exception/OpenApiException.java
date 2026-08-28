package com.aether.exception;

/** A stable, machine-readable failure returned only by the external OpenAPI surface. */
public class OpenApiException extends ServerException {
    private final String errorCode;

    public OpenApiException(int status, String errorCode) {
        super(status, errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() { return errorCode; }
}
