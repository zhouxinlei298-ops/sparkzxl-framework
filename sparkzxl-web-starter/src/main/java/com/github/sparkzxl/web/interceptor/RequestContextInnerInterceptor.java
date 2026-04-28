package com.github.sparkzxl.web.interceptor;

import cn.hutool.core.convert.Convert;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.HttpRequestUtils;
import com.github.sparkzxl.spi.Join;
import com.github.sparkzxl.web.annotation.ResponseResult;
import com.google.common.collect.Lists;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.lang.reflect.Method;
import java.util.List;

/**
 * description: 请求上下文拦截器
 *
 * @author zhouxinlei
 * @since 2022-12-09 09:07:20
 */
@Join
public class RequestContextInnerInterceptor extends AbstractInnerInterceptor {

    public static final List<String> THREAD_LOCAL_ATTRIBUTE = Lists.newArrayList(
            BaseContextConstants.TENANT_ID,
            BaseContextConstants.JWT_KEY_USER_ID,
            BaseContextConstants.JWT_KEY_ACCOUNT,
            BaseContextConstants.JWT_KEY_NAME,
            BaseContextConstants.VERSION
    );

    @Override
    public void doPreHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod)) {
            return;
        }
        final HandlerMethod handlerMethod = (HandlerMethod) handler;
        //设置当前请求线程全局信息
        THREAD_LOCAL_ATTRIBUTE.forEach(header -> RequestLocalContextHolder.set(header, HttpRequestUtils.getHeader(request, header)));
        Boolean feign = Convert.toBool(request.getHeader(BaseContextConstants.REMOTE_CALL), Boolean.FALSE);
        if (feign) {
            return;
        }
        final Class<?> classz = handlerMethod.getBeanType();
        final Method method = handlerMethod.getMethod();
        if (classz.isAnnotationPresent(ResponseResult.class)) {
            request.setAttribute(BaseContextConstants.RESPONSE_RESULT_ANN, classz.getAnnotation(ResponseResult.class));
        } else if (method.isAnnotationPresent(ResponseResult.class)) {
            request.setAttribute(BaseContextConstants.RESPONSE_RESULT_ANN, method.getAnnotation(ResponseResult.class));
        }
    }

    @Override
    public void doPostHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView)
            throws Exception {

    }

    @Override
    public String named() {
        return "context";
    }

    @Override
    public int getOrder() {
        return -99;
    }
}
