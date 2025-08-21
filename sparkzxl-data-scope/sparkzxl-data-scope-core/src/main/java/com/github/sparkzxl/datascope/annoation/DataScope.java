package com.github.sparkzxl.datascope.annoation;

import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.lang.annotation.*;

/**
 * description: 数据权限注解
 *
 * @author zhouxinlei
 * @since 2022-07-18 11:23:34
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = {ElementType.METHOD})
@Documented
public @interface DataScope {

    /**
     * 数据权限id {@link DataScopeConfProperties}
     */
    String value() default "";

    /**
     * 数据权限规则id(分组使用)
     */
    String ruleId() default "default";

    String ruleBeanName() default "";

    /**
     * support SPel expression 业务的key，用户动态判断
     *
     * @return KEY
     */
    String[] keys() default "";
}
