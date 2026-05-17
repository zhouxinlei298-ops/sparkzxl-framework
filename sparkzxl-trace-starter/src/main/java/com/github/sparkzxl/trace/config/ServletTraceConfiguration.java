package com.github.sparkzxl.trace.config;

import com.github.sparkzxl.trace.filter.ServletMdcFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
class ServletTraceConfiguration {

    @Bean
    public FilterRegistrationBean<ServletMdcFilter> servletMdcFilter() {
        FilterRegistrationBean<ServletMdcFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ServletMdcFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("servletMdcFilter");
        return registration;
    }
}
