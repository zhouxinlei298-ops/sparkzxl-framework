package com.github.sparkzxl.web.config;

import com.github.sparkzxl.web.interceptor.DefaultInnerInterceptorFactory;
import com.github.sparkzxl.web.interceptor.HttpRequestInterceptor;
import com.github.sparkzxl.web.interceptor.InnerInterceptor;
import com.github.sparkzxl.web.interceptor.InnerInterceptorFactory;
import com.github.sparkzxl.web.properties.WebProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

import java.util.List;

/**
 * Spring Cloud 环境下通过 SmartApplicationListener 监听 EnvironmentChangeEvent 实现 Nacos 配置热更新。
 * <p>
 * 收到事件后手动从 Environment 重新绑定 WebProperties，确保读取最新配置再 reload 拦截器。
 * <p>
 * 注意：@RefreshScope 在 Nacos 自动推送场景下不会销毁重建 Bean（实测验证），
 * 因此采用事件监听 + 手动 Binder 方案。
 * <p>
 * 非Spring Cloud环境下此类不会被加载（@ConditionalOnClass 条件不满足）
 *
 * @author zhouxinlei
 * @since 2026-04-24
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
@EnableConfigurationProperties(WebProperties.class)
public class CloudRefreshInterceptorAutoConfiguration {

    private static final String WEB_CONFIG_PREFIX = "spring.web";

    @Bean
    @ConditionalOnMissingBean(InnerInterceptorFactory.class)
    public InnerInterceptorFactory innerInterceptorFactory(WebProperties webProperties) {
        return new DefaultInnerInterceptorFactory(webProperties);
    }

    @Bean
    @ConditionalOnMissingBean(HttpRequestInterceptor.class)
    public HttpRequestInterceptor httpRequestInterceptor(InnerInterceptorFactory factory) {
        return new HttpRequestInterceptor(factory);
    }

    /**
     * 监听环境变更事件，手动从 Environment 重新绑定 WebProperties 后 reload 拦截器。
     * Environment 在事件发布前已更新，因此手动绑定一定拿到最新值。
     */
    @Bean
    public SmartApplicationListener interceptorRefreshListener(
            InnerInterceptorFactory factory,
            HttpRequestInterceptor httpRequestInterceptor,
            WebProperties webProperties, Environment environment) {
        return new SmartApplicationListener() {
            @Override
            public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
                return EnvironmentChangeEvent.class.isAssignableFrom(eventType);
            }

            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                EnvironmentChangeEvent envEvent = (EnvironmentChangeEvent) event;
                boolean affected = envEvent.getKeys().stream()
                        .anyMatch(key -> key.startsWith(WEB_CONFIG_PREFIX));
                if (affected) {
                    log.info("检测到 spring.web 配置变更，重新绑定 WebProperties 并加载拦截器");
                    Binder.get(environment).bind(WEB_CONFIG_PREFIX, Bindable.ofInstance(webProperties));
                    List<InnerInterceptor> reloaded = factory.loadInterceptors();
                    httpRequestInterceptor.reloadInterceptors(reloaded);
                }
            }

            @Override
            public int getOrder() {
                return Ordered.LOWEST_PRECEDENCE - 100;
            }
        };
    }
}
