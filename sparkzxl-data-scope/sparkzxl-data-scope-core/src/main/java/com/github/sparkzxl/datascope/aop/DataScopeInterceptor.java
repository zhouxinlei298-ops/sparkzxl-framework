package com.github.sparkzxl.datascope.aop;

import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.sparkzxl.datascope.annoation.DataScope;
import com.github.sparkzxl.datascope.annoation.DataScopes;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;
import com.github.sparkzxl.datascope.provider.DataScopeConfProvider;
import com.github.sparkzxl.datascope.rule.DataScopeRule;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.lang.NonNull;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * description: 多列数据权限aop处理器
 *
 * @author zhouxinlei
 * @since 2022-05-27 12:30:03
 */
@Slf4j
public class DataScopeInterceptor implements MethodInterceptor {

    @Autowired
    private DataScopeConfProperties dataScopeConfProperties;

    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();

    private static final ExpressionParser PARSER = new SpelExpressionParser();

    @Override
    public Object invoke(@NonNull MethodInvocation invocation) throws Throwable {
        //fix 使用其他aop组件时,aop切了两次.
        Class<?> cls = AopProxyUtils.ultimateTargetClass(Objects.requireNonNull(invocation.getThis()));
        if (!cls.equals(invocation.getThis().getClass())) {
            return invocation.proceed();
        }
        Method method = invocation.getMethod();
        DataScopes dataScopes = method.getAnnotation(DataScopes.class);
        DataScopeConfProvider dataScopeConfProvider = SpringContextUtils.getBean(DataScopeConfProvider.class);
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = Lists.newArrayList();
        String ruleBeanName;
        String[] definitionKeys;
        if (ObjectUtils.isNotEmpty(dataScopes)) {
            DataScope[] value = dataScopes.value();
            ArgumentAssert.notNull(value, "DataScopes注解必须包含DataScope注解");
            List<DataScope> dataScopeList = Lists.newArrayList(value);
            Map<String, String> dataScopeMap = dataScopeList.stream().collect(Collectors.toMap(DataScope::value, DataScope::ruleId));
            List<DataScopeConfProperties.DataScopeConf> confList = dataScopeConfProvider.loadByScopeIdList(Lists.newArrayList(dataScopeMap.keySet()));
            for (DataScopeConfProperties.DataScopeConf dataScopeConf : confList) {
                String ruleId = dataScopeMap.get(dataScopeConf.getScopeId());
                dataScopeConf.setRuleId(ruleId);
            }
            dataScopeConfList.addAll(confList);
            ruleBeanName = dataScopes.ruleBeanName();
            definitionKeys = dataScopes.keys();
        } else {
            DataScope dataScope = method.getAnnotation(DataScope.class);
            DataScopeConfProperties.DataScopeConf dataScopeConf = dataScopeConfProvider.loadByScopeId(dataScope.value());
            dataScopeConf.setRuleId(dataScope.ruleId());
            dataScopeConfList.add(dataScopeConf);
            ruleBeanName = dataScope.ruleBeanName();
            definitionKeys = dataScope.keys();
        }
        if (definitionKeys.length > 1 || !"".equals(definitionKeys[0])) {
            Map<String, Object> definitionKeyMap = getSpElDefinitionKey(definitionKeys, method, invocation.getArguments());
            for (String key : definitionKeyMap.keySet()) {
                RequestLocalContextHolder.set(key, definitionKeyMap.get(key));
            }
        }
        if (StringUtils.isEmpty(ruleBeanName)) {
            ruleBeanName = dataScopeConfProperties.getGlobalRule();
        }
        DataScopeRule dataScopeRule = SpringContextUtils.getBean(ruleBeanName);
        List<DataScopeConfProperties.DataScopeConf> chooseConfList = dataScopeRule.chooseConfList(dataScopeConfList);
        RequestLocalContextHolder.set(BaseContextConstants.DATA_SCOPE_CONF_LIST, chooseConfList);
        RequestLocalContextHolder.set(BaseContextConstants.ENABLE_DATA_SCOPE, Boolean.TRUE);
        return invocation.proceed();
    }


    protected Map<String, Object> getSpElDefinitionKey(String[] definitionKeys, Method method, Object[] parameterValues) {
        EvaluationContext context = new MethodBasedEvaluationContext(new Object(), method, parameterValues,
                NAME_DISCOVERER);
        Map<String, Object> definitionKeyMap = Maps.newHashMap();
        for (String definitionKey : definitionKeys) {
            if (definitionKey != null && !definitionKey.isEmpty()) {
                String value = PARSER.parseExpression(definitionKey).getValue(context, String.class);
                String[] definitionKeyArray = StringUtils.split(definitionKey, ".");
                if (definitionKeyArray != null && definitionKeyArray.length > 0) {
                    definitionKeyMap.put(definitionKeyArray[1], value);
                }
                definitionKeyMap.put(definitionKey, value);
            }
        }
        return definitionKeyMap;
    }
}
