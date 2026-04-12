package com.github.sparkzxl.swagger.config;

import cn.hutool.core.collection.CollUtil;
import com.github.sparkzxl.swagger.properties.SwaggerProperties;
import com.github.xiaoymin.knife4j.spring.filter.JakartaProductionSecurityFilter;
import com.google.common.collect.Maps;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.*;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;
import java.util.Map;

/**
 * description: swagger 包扫描配置
 *
 * @author zhouxinlei
 * @since 2025-08-09 15:24:26
 */
@Slf4j
@Configuration
@Import({Swagger2Configuration.class})
@ConditionalOnProperty(prefix = "swagger", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(SwaggerProperties.class)
public class SwaggerAutoConfiguration {

    private final SwaggerProperties properties;


    public SwaggerAutoConfiguration(SwaggerProperties properties) {
        this.properties = properties;
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

    /**
     * 全局响应配置
     * 为所有接口自动添加预定义的响应模板
     * 如果接口有 @ResponseResult 注解且没有 @IgnoreResponseWrap 注解，则将响应包装成 R<T> 格式
     * 200 响应从 operation.getResponses() 获取，其他状态码从 getResponseMessageMap() 获取
     *
     * @return GlobalOperationCustomizer
     */
    @Bean
    public GlobalOperationCustomizer globalResponseCustomizer() {
        return (operation, handlerMethod) -> {
            // 获取预定义的错误响应映射
            Map<String, ApiResponse> errorResponseMap = getResponseMessageMap();
            // 2. 添加错误响应（400, 401, 403, 404, 500）从 getResponseMessageMap() 获取
            errorResponseMap.forEach((key, val) -> {
                operation.getResponses().addApiResponse(key, val);
            });
            return operation;
        };
    }

    /**
     * 获取成功响应的 R<T> Schema 定义
     * 将原始 Schema 作为 result 字段
     *
     * @return R<T> Schema
     */
    private static Schema<?> getRschema() {
        return (Schema<?>) new Schema<>()
                .type("object")
                .description("统一响应格式")
                .addProperty("code", new IntegerSchema().description("响应编码").example(200))
                .addProperty("message", new StringSchema().description("响应信息").example("请求成功"))
                .addProperty("success", new BooleanSchema().description("是否成功").example(true))
                .addProperty("result", new ObjectSchema().description("响应数据"))
                .addProperty("errorCode", new StringSchema().description("异常码").nullable(true))
                .addProperty("errorMessage", new StringSchema().description("异常信息").nullable(true))
                .addProperty("extra", new Schema<>()
                        .type("object")
                        .description("附加数据")
                        .addProperty("traceId", new StringSchema().description("链路追踪ID").example("1234567890abcdef")));
    }

    /**
     * 获取响应消息映射
     *
     * @return 响应消息映射
     */
    private Map<String, ApiResponse> getResponseMessageMap() {
        Map<String, ApiResponse> responseMap = Maps.newHashMap();
        Schema<?> rschema = getRschema();
        // 400 - 请求参数错误
        ApiResponse badRequestResponse = new ApiResponse()
                .description("请求参数错误")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType()
                                        .schema(rschema)
                                        .example(Map.of(
                                                "code", 400,
                                                "message", "请求参数错误",
                                                "success", false,
                                                "errorCode", "A0415",
                                                "errorMessage", "参数校验失败",
                                                "extra", Map.of("traceId", "example-trace-id")
                                        ))));
        responseMap.put("400", badRequestResponse);

        // 401 - 未授权
        ApiResponse unauthorizedResponse = new ApiResponse()
                .description("用户登录已过期或未登录")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType()
                                        .schema(rschema)
                                        .example(Map.of(
                                                "code", 401,
                                                "message", "用户登录已过期",
                                                "success", false,
                                                "errorCode", "A0230",
                                                "errorMessage", "用户登陆已过期",
                                                "extra", Map.of("traceId", "example-trace-id")
                                        ))));
        responseMap.put("401", unauthorizedResponse);

        // 403 - 无权限
        ApiResponse forbiddenResponse = new ApiResponse()
                .description("用户访问未授权")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType()
                                        .schema(rschema)
                                        .example(Map.of(
                                                "code", 403,
                                                "message", "用户访问未授权",
                                                "success", false,
                                                "errorCode", "A0312",
                                                "errorMessage", "无权限使用 API",
                                                "extra", Map.of("traceId", "example-trace-id")
                                        ))));
        responseMap.put("403", forbiddenResponse);

        // 404 - 资源不存在
        ApiResponse notFoundResponse = new ApiResponse()
                .description("请求资源不存在")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType()
                                        .schema(rschema)
                                        .example(Map.of(
                                                "code", 404,
                                                "message", "接口不存在",
                                                "success", false,
                                                "errorCode", "C0113",
                                                "errorMessage", "接口不存在",
                                                "extra", Map.of("traceId", "example-trace-id")
                                        ))));
        responseMap.put("404", notFoundResponse);

        // 500 - 服务器内部错误
        ApiResponse internalServerErrorResponse = new ApiResponse()
                .description("服务器内部错误")
                .content(new Content()
                        .addMediaType("application/json",
                                new MediaType()
                                        .schema(rschema)
                                        .example(Map.of(
                                                "code", 500,
                                                "message", "系统执行出错",
                                                "success", false,
                                                "errorCode", "B0001",
                                                "errorMessage", "系统执行出错",
                                                "extra", Map.of("traceId", "example-trace-id")
                                        ))));
        responseMap.put("500", internalServerErrorResponse);

        return responseMap;
    }
    @Bean
    public OpenAPI customOpenApi() {
        // 配置 SpringDoc 以支持 R<T> 泛型类型的解析
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
