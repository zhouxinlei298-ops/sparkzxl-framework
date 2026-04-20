package com.github.sparkzxl.distributed.dubbo.config;

import cn.hutool.core.date.DateUtil;
import com.alibaba.cloud.nacos.ConditionalOnNacosDiscoveryEnabled;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.github.sparkzxl.distributed.dubbo.environment.LoadBalancerEnvironmentProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ConditionalOnDiscoveryEnabled;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2022-03-19 23:02:53
 */
@Configuration
@ConditionalOnDiscoveryEnabled
@ConditionalOnNacosDiscoveryEnabled
public class DubboNacosLoadAutoConfig {

    @Autowired
    private NacosDiscoveryProperties nacosDiscoveryProperties;
    @Autowired
    private LoadBalancerEnvironmentProperties loadBalancerEnvironmentProperties;

    @PostConstruct
    public void init() {
        // 更改服务详情中的元数据，增加服务注册时间
        nacosDiscoveryProperties.getMetadata().put("startup.time", DateUtil.now());
        // 从环境变量 LOADBALANCE_ZONE 读取
        String zone = loadBalancerEnvironmentProperties.getZone();
        if (zone == null || zone.isEmpty()) {
            zone = "default";
        }
        nacosDiscoveryProperties.getMetadata().put("zone", zone);
    }

}
