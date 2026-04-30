package com.github.sparkzxl.datasource.support;

import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * description: 数据源全局异常处理
 *
 * @author zhouxinlei
 */
@Slf4j
@RestControllerAdvice
public class DataSourceExceptionHandler implements Ordered {

    @ExceptionHandler(TenantException.class)
    public R<?> handleTenantException(TenantException e) {
        log.warn("TenantException异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(CannotFindDataSourceException.class)
    public R<?> handleCannotFindDataSourceException(CannotFindDataSourceException e) {
        log.warn("CannotFindDataSourceException异常:{}", e.getMessage());
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.DATASOURCE_EXCEPTION_ORDER.getOrder();
    }
}
