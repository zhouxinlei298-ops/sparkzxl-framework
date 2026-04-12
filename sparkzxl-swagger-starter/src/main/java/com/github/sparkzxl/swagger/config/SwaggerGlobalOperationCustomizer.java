package com.github.sparkzxl.swagger.config;

import cn.hutool.core.collection.CollUtil;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.swagger.properties.SwaggerProperties;
import com.github.sparkzxl.web.annotation.IgnoreResponseWrap;
import com.github.sparkzxl.web.annotation.ResponseResult;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.web.method.HandlerMethod;

import java.util.List;

/**
 * description:
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-04-12 14:32:23
 */
public class SwaggerGlobalOperationCustomizer implements GlobalOperationCustomizer {

    private final SwaggerProperties properties;

    public SwaggerGlobalOperationCustomizer(SwaggerProperties properties) {
        this.properties = properties;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
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
        boolean needWrap = checkNeedWrap(handlerMethod);
        if (needWrap) {
            String desc = operation.getDescription() == null ? "" : operation.getDescription();
            String securityInfo = String.format("<div style='margin-top:10px;color:#e6a23c;'>" +
                    "<strong>接口响应参数示例：</strong>" +
                    "<br/>" +
                    "<code>%s</code>" +
                    "</div>", JsonUtils.getJson().toJson(R.success()));
            operation.setDescription(desc + securityInfo);
        }
        return operation;
    }

    private boolean checkNeedWrap(HandlerMethod handlerMethod) {
        // 检查方法上是否有 @IgnoreResponseWrap 注解
        if (handlerMethod.getMethodAnnotation(IgnoreResponseWrap.class) != null) {
            return false;
        }

        // 检查方法上是否有 @ResponseResult 注解
        if (handlerMethod.getMethodAnnotation(ResponseResult.class) != null) {
            return true;
        }

        // 检查类上是否有 @ResponseResult 注解
        if (handlerMethod.getBeanType().getAnnotation(ResponseResult.class) != null) {
            return true;
        }
        return false;
    }
}
