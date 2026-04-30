package com.github.sparkzxl.mybatis.support;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.springframework.core.Ordered;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;

/**
 * description: 数据库全局异常处理
 *
 * @author zhoux
 */
@Slf4j
@RestControllerAdvice
public class DataBaseExceptionHandler implements Ordered {

    @ExceptionHandler(SQLSyntaxErrorException.class)
    public R<?> handleSqlSyntaxErrorException(SQLSyntaxErrorException e) {
        log.error("SQL异常：", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(TooManyResultsException.class)
    public R<?> handleTooManyResultsException(TooManyResultsException e) {
        log.error("SQL异常：", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public R<?> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("SQL异常：", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public R<?> handleSqlIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e) {
        log.error("SQL完整性约束违反异常：", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<?> handlerDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据重复输入: ", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(PersistenceException.class)
    public R<?> handlePersistenceException(PersistenceException e) {
        Throwable rootCause = ExceptionUtil.getRootCause(e);
        if (rootCause != null && rootCause != e) {
            return R.failDetail(ExceptionCodeResolver.resolve(rootCause));
        }
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(SQLException.class)
    public R<?> handleSqlException(SQLException e) {
        log.error("SQL异常：", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<?> handlerDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("数据库操作异常:", e);
        return R.failDetail(ExceptionCodeResolver.resolve(e));
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.DATABASE_EXCEPTION_HANDLER_ORDER.getOrder();
    }
}
