package com.github.sparkzxl.distributed.cloud.config;

import org.springframework.cloud.loadbalancer.annotation.LoadBalancerClients;
import org.springframework.context.annotation.Configuration;

/**
 * description: 负载均衡配置
 *
 * @author zhouxinlei
 * @since 2025-08-17 12:10:51
 */
@Configuration(proxyBeanMethods = false)
@LoadBalancerClients(defaultConfiguration = DefaultLoadBalancerConfiguration.class)
public class LoadBalancerAutoConfiguration {

}
