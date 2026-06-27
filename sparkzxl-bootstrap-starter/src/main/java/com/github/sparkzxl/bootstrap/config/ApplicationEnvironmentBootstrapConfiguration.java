package com.github.sparkzxl.bootstrap.config;

import com.github.sparkzxl.bootstrap.environment.ApplicationEnvironmentPropertySourceLocator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Cloud Bootstrap 配置
 * <p>
 * 在 bootstrap 阶段注册 {@link ApplicationEnvironmentPropertySourceLocator}，
 * 使系统环境变量在 bootstrap-test.yml 等配置加载之后注入，优先级更高。
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-15
 */
@Configuration
public class ApplicationEnvironmentBootstrapConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ApplicationEnvironmentPropertySourceLocator applicationEnvironmentPropertySourceLocator() {
        return new ApplicationEnvironmentPropertySourceLocator();
    }
}
