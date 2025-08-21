package com.github.sparkzxl.datascope.autoconfigure;

import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.github.sparkzxl.datascope.annoation.DataScopes;
import com.github.sparkzxl.datascope.aop.DataScopeAnnotationAdvisor;
import com.github.sparkzxl.datascope.aop.DataScopeInterceptor;
import com.github.sparkzxl.datascope.executor.DataScopeLineExecutor;
import com.github.sparkzxl.datascope.executor.DefaultDataScopeLineExecutor;
import com.github.sparkzxl.datascope.interceptor.DataScopeInnerInterceptor;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;
import com.github.sparkzxl.datascope.provider.DataScopeConfProvider;
import com.github.sparkzxl.datascope.provider.YamlDataScopeConfProvider;
import com.github.sparkzxl.datascope.rule.DataScopeRule;
import com.github.sparkzxl.datascope.rule.DefaultDataScopeRule;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * description: 数据权限自动装配
 *
 * @author zhouxinlei
 * @since 2024-01-29 09:00:11
 */
@Configuration
@EnableConfigurationProperties(value = DataScopeConfProperties.class)
@ConditionalOnProperty(prefix = "spring.datasource.data-scope", name = "enabled", havingValue = "true")
public class DataScopeAutoConfigure {

    @Bean
    public DataScopeInterceptor dataScopeInterceptor() {
        return new DataScopeInterceptor();
    }

    @Bean
    public DataScopeAnnotationAdvisor dataScopeAnnotationAdvisor(DataScopeInterceptor dataScopeInterceptor) {
        return new DataScopeAnnotationAdvisor(dataScopeInterceptor, DataScopes.class, 1);
    }

    @Bean
    @ConditionalOnMissingBean
    public DataScopeConfProvider dataScopeConfProvider(DataScopeConfProperties dataScopeConfProperties) {
        return new YamlDataScopeConfProvider(dataScopeConfProperties.getConfigList());
    }

    @Bean(name = "defaultDataScopeRule")
    public DataScopeRule dataScopeRule() {
        return new DefaultDataScopeRule();
    }

    @Bean
    public DataScopeLineExecutor dataScopeLineExecutor(DataScopeConfProperties dataScopeConfProperties) {
        return new DefaultDataScopeLineExecutor(dataScopeConfProperties.getDbType());
    }

    @Bean(value = "dataScopeInnerInterceptor")
    public InnerInterceptor dataScopeInnerInterceptor(DataScopeLineExecutor dataScopeLineExecutor) {
        return new DataScopeInnerInterceptor(dataScopeLineExecutor);
    }
}
