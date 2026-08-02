package com.github.sparkzxl.boot.config;

import com.github.sparkzxl.boot.filter.BootContextFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Boot request-context filter auto configuration.
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
@Configuration
@ConditionalOnWebApplication
public class BootAutoConfiguration {

    @Bean
    public FilterRegistrationBean<BootContextFilter> bootContextFilter() {
        FilterRegistrationBean<BootContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new BootContextFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("bootContextFilter");
        return registration;
    }
}
