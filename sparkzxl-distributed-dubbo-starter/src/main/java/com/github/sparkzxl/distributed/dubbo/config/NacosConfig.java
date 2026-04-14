package com.github.sparkzxl.distributed.dubbo.config;

import cn.hutool.core.date.DateUtil;
import com.alibaba.cloud.nacos.NacosDiscoveryProperties;
import com.alibaba.cloud.nacos.NacosServiceManager;
import com.alibaba.cloud.nacos.discovery.NacosWatch;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2022-03-19 23:02:53
 */
@Configuration
public class NacosConfig {

    @Bean
    @ConditionalOnProperty(value = {"spring.cloud.nacos.discovery.watch.enabled"}, matchIfMissing = true)
    public NacosWatch nacosWatch(NacosServiceManager nacosServiceManager, NacosDiscoveryProperties nacosDiscoveryProperties) {
        //更改服务详情中的元数据，增加服务注册时间
        nacosDiscoveryProperties.getMetadata().put("startup.time", DateUtil.now());
        // 从环境变量 LOADBALANCE_ZONE 读取
        String zone = System.getenv("LOADBALANCE_ZONE");
        if (zone == null || zone.isEmpty()) {
            zone = "default";
        }
        nacosDiscoveryProperties.getMetadata().put("zone", zone);
        return new NacosWatch(nacosServiceManager, nacosDiscoveryProperties);
    }

}
