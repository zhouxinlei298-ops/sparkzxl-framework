package com.github.sparkzxl.datascope.annoation;

import java.lang.annotation.*;

/**
 * description: 数据权限集合注解
 * // @DataScopes{
 * //       @DataScope{value="sys_user_data_permission"},
 * //       @DataScope{value="sys_user_dept_permission"}
 * }
 *
 * @author zhouxinlei
 * @since 2022-07-18 11:23:34
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface DataScopes {

    String ruleBeanName() default "";

    /**
     * support SPel expression 业务的key，用户动态判断
     * 仅支持#p0.key,#p1.key这种定义
     *
     * @return String[]
     */
    String[] keys() default "";

    DataScope[] value();

}
