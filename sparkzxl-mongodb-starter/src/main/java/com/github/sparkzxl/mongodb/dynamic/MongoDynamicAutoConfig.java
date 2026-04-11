package com.github.sparkzxl.mongodb.dynamic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Map;

/**
 * description: MongoDB多数据源管理-> 用于动态切库
 *
 * @author zhouxinlei
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DynamicMongoProperties.class)
@ConditionalOnProperty(prefix = DynamicMongoProperties.DYNAMIC_MONGO_PREFIX, name = "enabled", havingValue = "true")
@EnableMongoAuditing
@Slf4j
public class MongoDynamicAutoConfig {

    private final DynamicMongoProperties dynamicMongoProperties;

    public MongoDynamicAutoConfig(DynamicMongoProperties dynamicMongoProperties) {
        this.dynamicMongoProperties = dynamicMongoProperties;
        log.info("Mongodb动态数据源正在加载");
    }

    @Bean
    @ConditionalOnMissingBean
    public DynamicMongoDatabaseFactoryProvider dynamicMongoDatabaseFactoryProvider() {
        Map<String, DynamicMongoProperties.MongoDatabaseProperty> databasePropertyMap = dynamicMongoProperties.getProvider();
        return new YamlMongoDatabaseFactoryProvider(databasePropertyMap);
    }

    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactoryContext mongoDatabaseFactoryContext,
                                                       MongoMappingContext context,
                                                       BeanFactory beanFactory) {
        // 创建 DbRefResolver 对象
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(mongoDatabaseFactoryContext.determineMongoDatabaseFactory());
        // 创建 MappingMongoConverter 对象
        MappingMongoConverter mappingConverter = new MappingMongoConverter(dbRefResolver, context);
        // 设置 conversions 属性
        try {
            mappingConverter.setCustomConversions(beanFactory.getBean(MongoCustomConversions.class));
        } catch (NoSuchBeanDefinitionException ignore) {
        }
        // 设置 typeMapper 属性，从而移除 _class field 。
        mappingConverter.setTypeMapper(new DefaultMongoTypeMapper(null));
        return mappingConverter;
    }


    @Bean
    @ConditionalOnMissingBean
    public MongoDatabaseFactoryContext mongoDatabaseFactoryContext(
            DynamicMongoDatabaseFactoryProvider dynamicMongoDatabaseFactoryProvider) {
        String primary = dynamicMongoProperties.getPrimary();
        MongoDatabaseFactoryContext mongoDatabaseFactoryContext = new MongoDatabaseFactoryContext(dynamicMongoDatabaseFactoryProvider);
        mongoDatabaseFactoryContext.setPrimary(primary);
        return mongoDatabaseFactoryContext;
    }

    @Bean
    @ConditionalOnMissingBean
    public MongoTemplate mongoTemplate(MongoDatabaseFactoryContext mongoDatabaseFactoryContext) {
        return new DynamicMongoTemplate(mongoDatabaseFactoryContext.determinePrimaryMongoDatabaseFactory(), mongoDatabaseFactoryContext,
                dynamicMongoProperties.isRemoveClass());
    }

    @Bean
    public PlatformTransactionManager mongoTransactionManager(MongoDatabaseFactoryContext mongoDatabaseFactoryContext) {
        return new DynamicMongoTransactionManager(mongoDatabaseFactoryContext.determinePrimaryMongoDatabaseFactory(),
                mongoDatabaseFactoryContext);
    }

}
