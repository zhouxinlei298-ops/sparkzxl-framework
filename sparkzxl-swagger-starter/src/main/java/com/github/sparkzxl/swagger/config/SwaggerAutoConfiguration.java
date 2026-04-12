package com.github.sparkzxl.swagger.config;

import cn.hutool.core.collection.CollUtil;
import com.github.sparkzxl.swagger.properties.SwaggerProperties;
import com.github.xiaoymin.knife4j.spring.filter.JakartaProductionSecurityFilter;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * description: swagger 包扫描配置
 *
 * @author zhouxinlei
 * @since 2025-08-09 15:24:26
 */
@Slf4j
@Configuration
@ConditionalOnProperty(prefix = "swagger", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SwaggerProperties.class)
public class SwaggerAutoConfiguration {

    private final SwaggerProperties properties;


    public SwaggerAutoConfiguration(SwaggerProperties properties) {
        this.properties = properties;
    }

    @Bean
    @ConditionalOnClass(SwaggerWebMvcConfigurer.class)
    public SwaggerWebMvcConfigurer getSwaggerWebMvcConfigurer() {
        return new SwaggerWebMvcConfigurer();
    }

    /**
     * 增强自定义配置
     *
     * @return MyKnife4jOpenApiCustomizer
     */
    @Bean
    public MyKnife4jOpenApiCustomizer knife4jOpenApiCustomizer(SwaggerProperties swaggerProperties) {
        return new MyKnife4jOpenApiCustomizer(swaggerProperties);
    }

    @Bean
    public GlobalOperationCustomizer customize2() {
        return (operation, handlerMethod) -> {
            List<SwaggerProperties.GlobalOperationParameter> globalOperationParameters = properties.getGlobalOperationParameters();

            if (CollUtil.isEmpty(globalOperationParameters)) {
                return operation;
            }
            for (SwaggerProperties.GlobalOperationParameter globalOperationParameter : globalOperationParameters) {
                operation = operation.addParametersItem(
                        new Parameter()
                                .in(globalOperationParameter.getParameterType())
                                .required(globalOperationParameter.getRequired())
                                .schema(new StringSchema())
                                .example(globalOperationParameter.getDefaultValue())
                                .description(globalOperationParameter.getDescription())
                                .allowEmptyValue(globalOperationParameter.getAllowEmptyValue())
                                .name(globalOperationParameter.getName())
                );
            }
            return operation;
        };
    }


    @Bean
    public OpenAPI customOpenApi() {
        Contact contact = new Contact().name(properties.getContact().getName())
                .url(properties.getContact().getUrl())
                .email(properties.getContact().getEmail());
        License license = new License()
                .name(properties.getLicense())
                .url(properties.getLicenseUrl());
        return new OpenAPI()
                .info(new Info()
                        .title(properties.getTitle())
                        .description(properties.getDescription())
                        .version(properties.getVersion())
                        .termsOfService(properties.getTermsOfServiceUrl())
                        .contact(contact)
                        .license(license));
    }

    @Bean
    @ConditionalOnMissingBean(JakartaProductionSecurityFilter.class)
    @ConditionalOnProperty(name = "swagger.production", havingValue = "true")
    public JakartaProductionSecurityFilter productionSecurityFilter() {
        return new JakartaProductionSecurityFilter(properties.isProduction());
    }


}
