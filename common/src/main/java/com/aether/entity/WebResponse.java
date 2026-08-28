package com.aether.entity;

import com.aether.i18n.I18nUtils;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;

/**
 * code: 200 请求成功<br>
 * 500 请求失败<br>
 * 401 认证失败<br>
 * 403 拒绝访问<br>
 * 404 资源不存在<br>
 * 500 服务器错误<br>
 * 504 网关超时<br>
 * 503 服务不可用<br>
 * 400 请求参数错误<br>
 * message: 消息<br>
 * data: 数据<br>
 *
 * @author sun
 * @date 2024/08/15
 */
@Data
@AllArgsConstructor
public class WebResponse<T> implements Serializable {

    private Integer code;

    private String message;

    private T data;

    private Long total;

    /** Stable API error category. Null for ordinary admin/UI responses. */
    private String errorCode;

    public WebResponse(Integer code, String message, T data, Long total) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.total = total;
    }

    /**
     * 分页数据
     *
     * @param result 结果
     * @param total  总
     * @return {@link WebResponse }<{@link T }>
     */
    public static <T> WebResponse<T> Page(T result, Long total) {
        return new WebResponse<>(200, null, result, total);
    }

    /**
     * 分页查询当前请求。
     */
    public static <T> WebResponse<T> Page(String message, T result, Long total) {
        return new WebResponse<>(200, I18nUtils.getMessage("request.success"), result, total);
    }


    /**
     * code: 200 请求成功<br>
     *
     * @param result 结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> OK(T result) {
        return new WebResponse<>(200, null, result, 0L);
    }

    /**
     * code: 200 请求成功<br>
     *
     * @param message 消息
     * @param result  结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> OK(String message, T result) {
        return new WebResponse<>(200, message, result, 0L);
    }

    /**
     * code: 200 请求成功<br>
     *
     * @param message 消息
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> OK(String message) {
        return new WebResponse<>(200, message, null, 0L);
    }

    /**
     * @param code    代码
     * @param message 消息
     * @param result  结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> OK(Integer code, String message, T result) {
        return new WebResponse<>(code, message, result, 0L);
    }

    /**
     * 错误
     * code: 500 服务器错误<br>
     *
     * @param result 结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> Error(T result) {
        return new WebResponse<>(500, I18nUtils.getMessage("request.fail"), result, 0L);
    }

    /**
     * 错误
     * code: 500 服务器错误<br>
     *
     * @param message 消息
     * @param result  结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> Error(String message, T result) {
        return new WebResponse<>(500, message, result, 0L);
    }

    /**
     * 错误
     *
     * @param code    错误码
     * @param message 消息
     * @param result  结果
     * @return {@link WebResponse }
     */
    public static <T> WebResponse<T> Error(Integer code, String message, T result) {
        return new WebResponse<>(code, message, result, 0L);
    }

    /**
     * 处理Error。
     */
    public static WebResponse<String> Error(int code, String message) {
        return new WebResponse<>(code, message, null, 0L);
    }

    public static <T> WebResponse<T> OpenApiError(int status, String errorCode) {
        WebResponse<T> value = new WebResponse<>(status, errorCode, null, 0L);
        value.setErrorCode(errorCode);
        return value;
    }
}
