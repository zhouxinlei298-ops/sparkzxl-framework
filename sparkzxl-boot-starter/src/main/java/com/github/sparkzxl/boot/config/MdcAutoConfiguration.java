package com.github.sparkzxl.boot.config;

import com.github.sparkzxl.boot.filter.MdcFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * MDC 过滤器自动配置
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
@Configuration
@ConditionalOnWebApplication
public class MdcAutoConfiguration {

    @Bean
    public FilterRegistrationBean<MdcFilter> mdcFilter() {
        FilterRegistrationBean<MdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new MdcFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("mdcFilter");
        return registration;
    }
}
