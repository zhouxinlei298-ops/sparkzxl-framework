package com.github.sparkzxl.dubbo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * description: dubbo消费端配置
 *
 * @author zhouxinlei
 * @since 2022-08-06 14:23:34
 */
@Data
@ConfigurationProperties(prefix = "dubbo.consumer")
public class DubboConsumerProperties {

    private boolean fallback;

}
