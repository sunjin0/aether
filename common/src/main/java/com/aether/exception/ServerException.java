package com.aether.exception;

/**
 * 服务器异常
 *
 * @author sun
 * @date 2024/08/30
 */
public class ServerException extends RuntimeException {

    /**
     * 创建 {@code ServerException} 实例。
     */
    public ServerException() {
        super();
    }

    /**
     * 创建 {@code ServerException} 实例。
     */
    public ServerException(String message) {
        super(message);
    }

    /**
     * 创建 {@code ServerException} 实例。
     */
    public ServerException(int code, String message) {
        super(code + ":" + message);
    }

    /**
     * 创建 {@code ServerException} 实例。
     */
    public ServerException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * 创建 {@code ServerException} 实例。
     */
    public ServerException(Throwable cause) {
        super(cause);
    }

    /**
     * 创建 {@code ServerException} 实例。
     */
    protected ServerException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
