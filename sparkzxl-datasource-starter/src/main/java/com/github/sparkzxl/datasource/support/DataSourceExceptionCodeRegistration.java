package com.github.sparkzxl.datasource.support;

import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * 数据源异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class DataSourceExceptionCodeRegistration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        ExceptionCodeResolver.registerResolver(TenantException.class, ex ->
                new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.TENANT_EXCEPTION.getErrorCode(), ex.getMessage()));
        ExceptionCodeResolver.registerResolver(CannotFindDataSourceException.class, ex ->
                new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.CAN_NOT_FIND_DATASOURCE_EXCEPTION.getErrorCode(), ex.getMessage()));
    }
}
