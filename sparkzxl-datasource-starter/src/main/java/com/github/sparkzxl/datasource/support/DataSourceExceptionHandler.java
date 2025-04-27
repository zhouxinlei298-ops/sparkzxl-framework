package com.github.sparkzxl.datasource.support;

import com.baomidou.dynamic.datasource.exception.CannotFindDataSourceException;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RestControllerAdvice;


/**
 * description: 数据源全局异常处理
 *
 * @author zhouxinlei
 */
@Slf4j
@RestControllerAdvice
@RestController
public class DataSourceExceptionHandler implements Ordered {

    @ExceptionHandler(TenantException.class)
    public R<?> handleTenantException(TenantException e) {
        log.warn("TenantException异常:", e);
        return R.failDetail(ExceptionErrorCode.TENANT_EXCEPTION.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(CannotFindDataSourceException.class)
    public R<?> handleCannotFindDataSourceException(CannotFindDataSourceException e) {
        log.warn("CannotFindDataSourceException异常:", e);
        return R.failDetail(ExceptionErrorCode.CAN_NOT_FIND_DATASOURCE_EXCEPTION.getErrorCode(), e.getMessage());
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.DATASOURCE_EXCEPTION_ORDER.getOrder();
    }
}
