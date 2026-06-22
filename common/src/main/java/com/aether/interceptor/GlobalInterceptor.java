package com.aether.interceptor;

import com.aether.exception.ServerException;
import com.aether.i18n.I18nUtils;
import com.aether.local.CurrentUser;
import com.aether.utils.AesUtil;
import com.aether.utils.TokenUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;

/**
 * 全局拦截器
 *
 * @author sun
 * @since 2024/09/20
 */
@Component
public class GlobalInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(GlobalInterceptor.class);

    /**
     * 预处理器，在请求处理之前进行调用，只有返回true才会继续向下执行，
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @return boolean
     * @throws Exception 例外
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String authorization = request.getHeader("Authorization");
        HashMap<String, String> payload = new HashMap<>();
        if (authorization != null) {
            authorization = AesUtil.decrypt(authorization.replace("Bearer ", ""));
            // 判断token是否过期
            try {
                TokenUtils.isExpired(authorization);
            } catch (Exception e) {
                throw new ServerException(401, I18nUtils.getMessage("error.token.expired"));
            }
            String userId = TokenUtils.getUserId(authorization);
            //设置当前用户
            payload.put("userId", userId);
            payload.put("token", authorization);
        }
        payload.put("startTime", System.currentTimeMillis() + "");
        CurrentUser.set(payload);
        return HandlerInterceptor.super.preHandle(request, response, handler);
    }

    /**
     * 处理器执行完成之后，视图渲染之前，
     *
     * @param request      请求
     * @param response     响应
     * @param handler      处理器
     * @param modelAndView 模型和视图
     * @throws Exception 例外
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) throws Exception {
        HandlerInterceptor.super.postHandle(request, response, handler, modelAndView);
    }

    /**
     * 完成请求处理后，视图渲染完成之后，
     *
     * @param request  请求
     * @param response 响应
     * @param handler  处理器
     * @param ex       前任
     * @throws Exception 例外
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        long startTime = Long.parseLong(CurrentUser.getUser().get("startTime"));
        long endTime = System.currentTimeMillis();
        //记录日志
        log.info("用户id:{},请求地址：{}，请求耗时：{}ms，请求参数：{}，请求结果：{}", CurrentUser.getUser().get("userId"), request.getRequestURI(), endTime - startTime, request.getQueryString(), response.getStatus());
        CurrentUser.remove();
        HandlerInterceptor.super.afterCompletion(request, response, handler, ex);
    }
}
