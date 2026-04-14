package com.github.sparkzxl.distributed.cloud.environment;

import jakarta.annotation.PostConstruct;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * description: LoadBalancerEnvironment 配置属性
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-14 16:41:59
 */
@Data
@Component
@ConfigurationProperties(prefix = "loadbalancer")
public class LoadBalancerEnvironmentProperties {

    private String zone;

    @PostConstruct
    public void init() {
        // Spring Boot 3.x 中可以通过 Environment 获取所有属性
        System.out.println("LoadBalancerEnvironmentProperties initialized:");
        System.out.println("  zone: " + zone);
    }
}
