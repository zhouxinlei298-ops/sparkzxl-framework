package com.github.sparkzxl.distributed.cloud.environment;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * description: Nacos 配置属性
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-14 16:41:59
 */
@Data
@Slf4j
@Component
@ConfigurationProperties(prefix = "nacos")
public class NacosEnvironmentProperties {

    private String url;
    private String namespace;
    private String group = "DEFAULT_GROUP";
    private String username;
    private String password;

    @PostConstruct
    public void init() {
        // 环境变量已在 NacosEnvironmentPostProcessor 中注入到 Environment
        // 此时可以通过 @ConfigurationProperties 直接读取到环境变量的值
        log.info("NacosProperties initialized:");
        log.info("  URL: {}", url);
        log.info("  Namespace: {}", namespace);
        log.info("  Group: {}", group);
        log.info("  Username: {}", username);
        log.info("  Password: {}", (password != null && !password.isEmpty() ? "***" : "empty"));
    }
}
