package com.github.sparkzxl.datasource.autoconfigure;

import cn.hutool.core.map.MapUtil;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ClassUtils;
import com.github.sparkzxl.datasource.context.DataSourcePropertyCache;
import com.github.sparkzxl.datasource.interceptor.DynamicDataSourceInterceptor;
import com.github.sparkzxl.datasource.properties.DynamicDataProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Optional;

/**
 * description: 动态数据源全局配置
 *
 * @author zhouxinlei
 */
@Configuration
@EnableConfigurationProperties({DynamicDataProperties.class})
@Slf4j
@ConditionalOnProperty(prefix = DynamicDataSourceProperties.PREFIX, name = "enabled", havingValue = "true", matchIfMissing = true)
public class DynamicDataSourceAutoConfig implements WebMvcConfigurer {

    private DynamicDataProperties dynamicDataProperties;

    @Autowired
    public void setDynamicDataProperties(DynamicDataProperties dynamicDataProperties) {
        this.dynamicDataProperties = dynamicDataProperties;
    }

    @Bean
    public DataSourcePropertyCache dataSourcePropertyCache(DynamicDataSourceProperties dynamicDataSourceProperties) {
        DataSourcePropertyCache dataSourcePropertyCache = new DataSourcePropertyCache();
        if (MapUtil.isNotEmpty(dynamicDataSourceProperties.getDatasource())) {
            dataSourcePropertyCache.putAll(dynamicDataSourceProperties.getDatasource());
        }
        return dataSourcePropertyCache;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (dynamicDataProperties.isEnabled()) {
            DynamicDataSourceInterceptor dynamicDataSourceInterceptor = new DynamicDataSourceInterceptor();
            Optional.of(dynamicDataSourceInterceptor).ifPresent(interceptor -> {
                registry.addInterceptor(interceptor).order(Ordered.HIGHEST_PRECEDENCE + 1);
                log.info("已加载拦截器：[{}]", ClassUtils.getName(interceptor));
            });
        }
    }
}
