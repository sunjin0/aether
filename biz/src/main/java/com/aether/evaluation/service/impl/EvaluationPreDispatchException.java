package com.aether.evaluation.service.impl;

/** A deterministic validation failure that occurs before an evaluation target is started. */
final class EvaluationPreDispatchException extends RuntimeException {
    private final String errorCode;

    EvaluationPreDispatchException(String errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    String getErrorCode() {
        return errorCode;
    }
}
