package com.github.sparkzxl.log.aop;

import com.github.sparkzxl.log.annotation.HttpRequestLog;
import lombok.NonNull;
import org.aopalliance.aop.Advice;
import org.springframework.aop.Pointcut;
import org.springframework.aop.support.AbstractPointcutAdvisor;
import org.springframework.aop.support.annotation.AnnotationMatchingPointcut;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;

/**
 * HTTP 请求日志 AOP 通知
 *
 * @author zhouxinlei
 */
public class HttpRequestLogAnnotationAdvisor extends AbstractPointcutAdvisor implements BeanFactoryAware {

    private final Advice advice;

    private final Pointcut pointcut;

    public HttpRequestLogAnnotationAdvisor(@NonNull HttpRequestLogInterceptor interceptor, int order) {
        this.advice = interceptor;
        this.pointcut = AnnotationMatchingPointcut.forMethodAnnotation(HttpRequestLog.class);
        setOrder(order);
    }

    @Override
    public Pointcut getPointcut() {
        return pointcut;
    }

    @Override
    public Advice getAdvice() {
        return advice;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        if (this.advice instanceof BeanFactoryAware) {
            ((BeanFactoryAware) this.advice).setBeanFactory(beanFactory);
        }
    }
}
