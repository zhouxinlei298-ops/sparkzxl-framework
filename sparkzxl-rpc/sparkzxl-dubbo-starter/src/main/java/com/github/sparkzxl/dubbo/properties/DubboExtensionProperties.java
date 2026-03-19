package com.github.sparkzxl.dubbo.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.logging.LogLevel;

/**
 * description: dubbo扩展配置
 *
 * @author zhouxinlei
 * @since 2022-08-06 14:23:34
 */
@Data
@ConfigurationProperties(prefix = "dubbo.extension")
public class DubboExtensionProperties {

    private boolean requestLog;

    private LogLevel level = LogLevel.INFO;

}
