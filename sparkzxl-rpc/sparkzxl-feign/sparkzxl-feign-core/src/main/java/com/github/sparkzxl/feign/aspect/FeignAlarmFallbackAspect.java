package com.github.sparkzxl.feign.aspect;

import cn.hutool.aop.aspects.SimpleAspect;
import cn.hutool.core.annotation.AnnotationUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.map.MapUtil;
import com.alibaba.fastjson.JSONArray;
import com.github.sparkzxl.alarm.annotation.Alarm;
import com.github.sparkzxl.alarm.annotation.AlarmParam;
import com.github.sparkzxl.alarm.entity.AlarmRequest;
import com.github.sparkzxl.alarm.enums.MessageSubType;
import com.github.sparkzxl.alarm.send.AlarmClient;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.entity.ExpressionTemplate;
import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.core.util.AopUtil;
import com.google.common.collect.Maps;
import feign.FeignException;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * description: Feign降级告警通知切面
 *
 * @author zhouxinlei
 * @since 2022-04-07 20:18:59
 */
@Slf4j
public class FeignAlarmFallbackAspect extends SimpleAspect {

    public static final String MARKDOWN_FALLBACK_TEMPLATE =
            "> · 应用： <font color=\"#1890ff\">#{[applicationName]}</font>\n" +
                    "> · 环境： <font color=\"#1890ff\">#{[environment]}</font>\n" +
                    "> · 租户ID：<font color=\"#1890ff\">#{[tenantId]}</font>\n" +
                    "> · 请求时间：#{[date]}\n" +
                    "> · 请求地址：#{[url]}\n" +
                    "> · 请求服务：<font color=\"#1890ff\">#{[serviceName]}</font>\n" +
                    "> · 请求方法名称：#{[classMethod]}\n" +
                    "> · 降级异常信息：\n```java\n#{[cause]}\n```\n" +
                    "> · 降级结果：\n```json\n#{[returnVal]}\n```";


    public static final String TEXT_FALLBACK_TEMPLATE =
            "应用： #{[applicationName]}" +
                    "环境： #{[environment]}\n" +
                    "租户ID：#{[tenantId]}\n" +
                    "请求时间：#{[date]}\n" +
                    "请求地址：#{[url]}\n" +
                    "请求服务：#{[serviceName]}\n" +
                    "请求方法名称：#{[classMethod]}\n" +
                    "降级异常信息：\n#{[cause]}\n" +
                    "降级结果：\n#{[returnVal]}\n";

    private final FeignException exception;
    private final AlarmClient alarmClient;
    private final String applicationName;
    private final String environment;

    public FeignAlarmFallbackAspect(FeignException exception) {
        this.exception = exception;
        this.alarmClient = SpringContextUtils.getBean(AlarmClient.class);
        this.applicationName = SpringContextUtils.getApplicationName();
        this.environment = SpringContextUtils.getEnvironment();
    }

    @Override
    public boolean after(Object target, Method method, Object[] args, Object returnVal) {
        if (alarmClient == null) {
            return true;
        }
        Alarm annotation = AnnotationUtil.getAnnotation(method, Alarm.class);
        if (annotation == null) {
            return true;
        }
        MessageSubType messageSubType = annotation.messageType();
        AlarmRequest alarmRequest = new AlarmRequest();
        alarmRequest.setTitle(annotation.name());
        Map<String, Object> alarmParamMap = Maps.newHashMap();
        alarmParamMap.put("title", annotation.name());
        Map<String, Object> paramMap = AopUtil.getParameterAnnotationMap(method, args, AlarmParam.class, "value");
        if (MapUtil.isNotEmpty(paramMap)) {
            alarmParamMap.putAll(paramMap);
        }
        String extractParams = annotation.extractParams();
        if (StringUtils.isNotEmpty(extractParams)) {
            String[] headerArray = StringUtils.split(extractParams, ",");
            for (String header : headerArray) {
                alarmParamMap.put(header, RequestLocalContextHolder.get(header));
            }
        }
        String expressionJson = annotation.expressionJson();
        if (StringUtils.isNotBlank(expressionJson)) {
            List<ExpressionTemplate> expressionTemplateList = JSONArray.parseArray(expressionJson, ExpressionTemplate.class);
            for (ExpressionTemplate expressionTemplate : expressionTemplateList) {
                String value = AopUtil.parseExpression(method, args, expressionTemplate.getExpression());
                alarmParamMap.put(expressionTemplate.getKey(), value);
            }
        }
        RequestTemplate requestTemplate = exception.request().requestTemplate();
        alarmParamMap.put("applicationName", applicationName);
        alarmParamMap.put("environment", environment);
        alarmParamMap.put("date", DateUtil.now());
        if (requestTemplate != null) {
            String name = requestTemplate.feignTarget().name();
            String serviceName = StringUtils.isEmpty(name) ? "unKnownServer" : name;
            String requestUrl = requestTemplate.url();
            String url = StringUtils.isEmpty(requestUrl) ? "" : requestUrl;
            String requestMethodName = requestTemplate.methodMetadata().method().getName();
            String methodName = StringUtils.isEmpty(requestMethodName) ? "" : requestMethodName;
            String requestClassName = requestTemplate.methodMetadata().targetType().getName();
            String className = StringUtils.isEmpty(requestClassName) ? "" : requestClassName;
            String classMethod = String.format("%s.%s", className, methodName);
            alarmParamMap.put("url", url);
            alarmParamMap.put("serviceName", serviceName);
            alarmParamMap.put("classMethod", classMethod);
            Optional<String> tenantIdOptional = requestTemplate.headers().get("tenantId").stream().findFirst();
            tenantIdOptional.ifPresent(s -> alarmParamMap.put("tenantId", s));
        }
        alarmParamMap.put("cause", ExceptionUtil.stacktraceToString(exception));
        alarmParamMap.put("returnVal", JsonUtils.getJson().toJson(returnVal));
        if (messageSubType.equals(MessageSubType.TEXT)) {
            alarmRequest.setContent(TEXT_FALLBACK_TEMPLATE);
        } else {
            alarmRequest.setContent(MARKDOWN_FALLBACK_TEMPLATE);
        }
        alarmRequest.setVariables(alarmParamMap);
        if (StringUtils.isNotBlank(annotation.robotId())) {
            alarmClient.designatedRobotSend(annotation.robotId(), messageSubType, alarmRequest);
        } else {
            alarmClient.send(messageSubType, alarmRequest);
        }
        return true;
    }

}
