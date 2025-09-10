package com.github.sparkzxl.datasource.properties;

import lombok.Data;
import com.github.sparkzxl.datasource.enums.DynamicDataSourceListenerEnum;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * description: 动态数据源配置类
 *
 * @author zhouxinlei
 */
@Data
@ConfigurationProperties(
        prefix = com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties.PREFIX
)
public class DynamicDataProperties {

    private boolean enabled = true;

    @NestedConfigurationProperty
    private Listener listener;

    /**
     * description: 数据源监听
     *
     * @author zhouxinlei
     * @since 2025-08-21 14:53:34
     */
    @Data
    public static class Listener {
        /**
         * 监听类型
         */
        private DynamicDataSourceListenerEnum type = DynamicDataSourceListenerEnum.NACOS;

        /**
         * Nacos监听配置
         */
        @NestedConfigurationProperty
        private NacosConsumerProperties nacos;

    }

}
