package com.github.sparkzxl.web.interceptor;

import com.github.sparkzxl.core.support.LoginExpireException;
import io.vavr.control.Try;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.AsyncHandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

/**
 * description: web request interceptor
 *
 * @author zhouxinlei
 * @since 2022-12-08 19:24:09
 */
@Slf4j
@Getter
public class HttpRequestInterceptor implements AsyncHandlerInterceptor {

    private volatile List<InnerInterceptor> innerInterceptorList;

    public HttpRequestInterceptor(InnerInterceptorFactory innerInterceptorFactory) {
        this.innerInterceptorList = innerInterceptorFactory.loadInterceptors();
    }

    public void reloadInterceptors(List<InnerInterceptor> interceptors) {
        log.info("InnerInterceptor拦截器已刷新加载");
        this.innerInterceptorList = interceptors;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        for (InnerInterceptor innerInterceptor : innerInterceptorList) {
            Try.run(() -> innerInterceptor.preHandle(request, response, handler))
                    .getOrElseThrow(throwable -> {
                        if (throwable instanceof LoginExpireException) {
                            return (LoginExpireException) throwable;
                        }
                        return new RuntimeException(throwable);
                    });
        }
        return true;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        for (InnerInterceptor innerInterceptor : innerInterceptorList) {
            Try.run(() -> innerInterceptor.postHandle(request, response, handler, modelAndView))
                    .getOrElseThrow(throwable -> {
                        if (throwable instanceof LoginExpireException) {
                            return (LoginExpireException) throwable;
                        }
                        return new RuntimeException(throwable);
                    });
        }
    }
}
