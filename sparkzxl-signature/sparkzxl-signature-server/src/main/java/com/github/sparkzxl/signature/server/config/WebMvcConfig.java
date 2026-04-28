package com.github.sparkzxl.signature.server.config;

import com.github.sparkzxl.core.constant.Constant;
import com.github.sparkzxl.signature.server.interceptor.CachedBodyRequestFilter;
import com.github.sparkzxl.signature.server.interceptor.SignAuthInterceptor;
import com.github.sparkzxl.signature.server.properties.SignatureServerProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static com.github.sparkzxl.signature.server.properties.SignatureServerProperties.CONFIG_PREFIX;

/**
 * description: WebConfig全局配置
 *
 * @author zhouxinlei
 * @link @ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")代表有spring-mvc依赖
 */
@Order(1)
@Configuration
@ConditionalOnClass(name = "org.springframework.web.servlet.DispatcherServlet")
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired
    private SignatureServerProperties signatureServerProperties;

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
    public SignAuthInterceptor signAuthInterceptor() {
        return new SignAuthInterceptor();
    }

    @Bean
    @ConditionalOnProperty(prefix = CONFIG_PREFIX, name = "enabled", havingValue = "true")
    public FilterRegistrationBean<CachedBodyRequestFilter> cachedBodyRequestFilter() {
        FilterRegistrationBean<CachedBodyRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new CachedBodyRequestFilter());
        registration.addUrlPatterns("/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("cachedBodyRequestFilter");
        return registration;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        boolean enabled = signatureServerProperties.isEnabled();
        if (enabled) {
            List<String> excludePatterns = signatureServerProperties.getExcludePatterns();
            excludePatterns.addAll(Constant.EXCLUDE_STATIC_PATTERNS);
            registry.addInterceptor(signAuthInterceptor())
                    .order(Ordered.HIGHEST_PRECEDENCE + 2)
                    .addPathPatterns(signatureServerProperties.getIncludePatterns())
                    .excludePathPatterns(excludePatterns);
        }
    }
}
