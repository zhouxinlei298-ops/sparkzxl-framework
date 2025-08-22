package com.github.sparkzxl.datasource.autoconfigure;

import com.alibaba.nacos.api.exception.NacosException;
import com.github.sparkzxl.datasource.listener.DataSourceConfigOnChange;
import com.github.sparkzxl.datasource.listener.DataSourceUpdater;
import com.github.sparkzxl.datasource.listener.NacosDynamicDataSourceConfigListener;
import com.github.sparkzxl.datasource.properties.DynamicDataProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: Nacos监听数据源配置
 *
 * @author zhouxinlei
 * @since 2025-08-21 15:22:42
 */
@Configuration
@ConditionalOnProperty(name = "spring.datasource.dynamic.listener.type", havingValue = "nacos")
public class DynamicDataSourceNacosListenerAutoConfig {

    @Bean
    public DataSourceUpdater dataSourceUpdater() {
        return new DataSourceUpdater();
    }

    @Bean
    public DataSourceConfigOnChange dataSourceConfigOnChange(DataSourceUpdater dataSourceUpdater) {
        return new DataSourceConfigOnChange(dataSourceUpdater);
    }

    @Bean
    public NacosDynamicDataSourceConfigListener nacosConfigListener(DynamicDataProperties dynamicDataProperties,
                                                                    DataSourceConfigOnChange dataSourceConfigOnChange) {
        try {
            return new NacosDynamicDataSourceConfigListener(dynamicDataProperties, dataSourceConfigOnChange);
        } catch (NacosException e) {
            throw new RuntimeException("初始化Nacos监听配置失败:" + e.getMessage());
        }
    }
}
