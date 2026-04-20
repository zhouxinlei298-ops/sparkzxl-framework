package com.github.sparkzxl.distributed.cloud.config;

import com.github.sparkzxl.distributed.cloud.environment.LoadBalancerEnvironmentProperties;
import com.github.sparkzxl.distributed.cloud.environment.NacosEnvironmentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * description: 环境自动装配
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-14 16:49:52
 */
@Configuration
@EnableConfigurationProperties({NacosEnvironmentProperties.class, LoadBalancerEnvironmentProperties.class})
public class EnvironmentAutoConfig {
}
