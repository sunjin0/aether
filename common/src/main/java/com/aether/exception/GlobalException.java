package com.aether.exception;

import com.auth0.jwt.exceptions.TokenExpiredException;
import com.aether.entity.WebResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.validation.ValidationException;

/**
 * 全局异常处理
 *
 * @author sun
 * @date 2024/08/30
 */
@RestControllerAdvice
public class GlobalException {
    private static final Logger log = LoggerFactory.getLogger(GlobalException.class);

    /**
     * 处理服务器异常
     *
     * @param e e
     */
    @ExceptionHandler(value = ServerException.class)
    public WebResponse<String> handleServerException(ServerException e) {
        String message = e.getMessage();
        log.error("服务器异常：", e);
        // 处理自定义异常
        String[] strings = message.split(":");
        if (strings.length > 1) {
            try {
                // 转换成数字,可能发生数字转换异常
                int code = Integer.parseInt(strings[0]);
                String messages = strings[1];
                return WebResponse.Error(code, messages, null);
            } catch (Exception ex) {
                log.error("转换异常：{}", ex.getMessage());
                return WebResponse.Error(message);
            }
        }

        return WebResponse.Error(message);
    }

    /**
     * 处理其他异常
     *
     * @param e e
     * @return {@link WebResponse }<{@link String }>
     */
    @ExceptionHandler(value = Exception.class)
    public WebResponse<String> handleOtherException(Exception e) {
        if (e instanceof ValidationException) {
            log.error("参数异常", e);
            return WebResponse.Error(400, e.getMessage(), null);
        }
        if (e instanceof TokenExpiredException) {
            log.error("token过期", e);
            return WebResponse.Error(401, e.getMessage(), null);
        }

        log.error("其他异常", e);
        return WebResponse.Error(e.getMessage());
    }
}
