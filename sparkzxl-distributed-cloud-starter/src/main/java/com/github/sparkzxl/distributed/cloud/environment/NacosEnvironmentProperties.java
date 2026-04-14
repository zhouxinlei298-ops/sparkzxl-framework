package com.github.sparkzxl.distributed.cloud.environment;

import jakarta.annotation.PostConstruct;
import lombok.Data;
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
        // Spring Boot 3.x 中可以通过 Environment 获取所有属性
        System.out.println("NacosProperties initialized:");
        System.out.println("  URL: " + url);
        System.out.println("  Namespace: " + namespace);
        System.out.println("  Group: " + group);
        System.out.println("  Username: " + username);
        System.out.println("  Password: " + (password != null && !password.isEmpty() ? "***" : "empty"));
    }
}
