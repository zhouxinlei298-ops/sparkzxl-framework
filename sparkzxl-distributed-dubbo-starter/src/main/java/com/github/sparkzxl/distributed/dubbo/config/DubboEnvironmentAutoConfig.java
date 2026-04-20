package com.github.sparkzxl.distributed.dubbo.config;

import com.github.sparkzxl.distributed.dubbo.environment.LoadBalancerEnvironmentProperties;
import com.github.sparkzxl.distributed.dubbo.environment.NacosEnvironmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * description: dubbo环境自动装配
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-14 16:49:52
 */
@Configuration
@EnableConfigurationProperties({NacosEnvironmentProperties.class, LoadBalancerEnvironmentProperties.class})
public class DubboEnvironmentAutoConfig {
}
