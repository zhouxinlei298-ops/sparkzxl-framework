package com.github.sparkzxl.datasource.autoconfigure;

import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.github.sparkzxl.datasource.listener.DataSourceUpdater;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.context.environment.EnvironmentChangeEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.Environment;

/**
 * Spring Cloud 环境下通过 SmartApplicationListener 监听 EnvironmentChangeEvent 实现数据源配置热更新。
 * <p>
 * 收到事件后手动从 Environment 重新绑定 DynamicDataSourceProperties，再通过 DataSourceUpdater 执行差异化更新。
 * <p>
 * 注意：@RefreshScope 在 Nacos 自动推送场景下不会销毁重建 Bean（实测验证），
 * 因此采用事件监听 + 手动 Binder 方案。
 * <p>
 * 非 Spring Cloud 环境下此类不会被加载（@ConditionalOnClass 条件不满足）
 *
 * @author zhouxinlei
 * @since 2026-04-27
 */
@Slf4j
@Configuration
@ConditionalOnClass(name = "org.springframework.cloud.context.config.annotation.RefreshScope")
@ConditionalOnProperty(prefix = DynamicDataSourceProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class CloudRefreshDataSourceAutoConfiguration {

    private static final String DS_CONFIG_PREFIX = DynamicDataSourceProperties.PREFIX;

    @Bean
    @ConditionalOnMissingBean
    public DataSourceUpdater dataSourceUpdater() {
        return new DataSourceUpdater();
    }

    /**
     * 监听环境变更事件，手动从 Environment 重新绑定 DynamicDataSourceProperties 后刷新数据源。
     * Environment 在事件发布前已更新，因此手动绑定一定拿到最新值。
     */
    @Bean
    public SmartApplicationListener dataSourceRefreshListener(
            DataSourceUpdater dataSourceUpdater,
            Environment environment) {
        return new SmartApplicationListener() {
            @Override
            public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
                return EnvironmentChangeEvent.class.isAssignableFrom(eventType);
            }

            @Override
            public void onApplicationEvent(ApplicationEvent event) {
                EnvironmentChangeEvent envEvent = (EnvironmentChangeEvent) event;
                boolean affected = envEvent.getKeys().stream()
                        .anyMatch(key -> key.startsWith(DS_CONFIG_PREFIX));
                if (affected) {
                    log.info("检测到 {} 配置变更，重新绑定 DynamicDataSourceProperties 并刷新数据源", DS_CONFIG_PREFIX);
                    // 创建全新实例作为配置快照传入更新器，不影响 Spring 容器中的 Bean
                    DynamicDataSourceProperties newProps = new DynamicDataSourceProperties();
                    Binder.get(environment).bind(DS_CONFIG_PREFIX, Bindable.ofInstance(newProps));
                    dataSourceUpdater.updateDynamicDataSources(newProps);
                }
            }

            @Override
            public int getOrder() {
                return Ordered.LOWEST_PRECEDENCE - 100;
            }
        };
    }
}
