package com.github.sparkzxl.feign.resilience4j.autoconfigure;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * description: 负载均衡配置
 *
 * @author zhouxinlei
 * @since 2025-08-02 10:46:45
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = DefaultLoadBalancerConfiguration.class)
public class LoadBalancerAutoConfiguration {

}
