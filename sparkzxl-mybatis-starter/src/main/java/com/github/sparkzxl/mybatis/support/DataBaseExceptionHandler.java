package com.github.sparkzxl.mybatis.support;

import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import com.github.sparkzxl.core.base.result.R;
import com.github.sparkzxl.core.constant.enums.BeanOrderEnum;
import com.github.sparkzxl.core.support.BizException;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.kingbase8.util.KSQLException;
import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * description: 数据库全局异常处理
 *
 * @author zhoux
 */
@Slf4j
@RestControllerAdvice
public class DataBaseExceptionHandler implements Ordered {

    private final static String DATABASE_PREFIX = "Unknown database";
    private final static String TABLE_PREFIX = "^Table.*doesn't exist$";
    private final static String COLUMN_PREFIX = "Unknown column";
    private final static String VIOLATION_DATABASE_REGEX = "Duplicate entry '(.*?)' for key '(.*?)'";
    private final static int DATABASE_ERROR_CODE = 1364;

    @ExceptionHandler(MysqlDataTruncation.class)
    public R<?> handleMysqlDataTruncation(MysqlDataTruncation e) {
        log.error("SQL异常：", e);
        String message = e.getMessage();
        String prefix = "Data too long for column";
        if (message.contains(prefix)) {
            return R.failDetail(ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorMsg());
        }
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @ExceptionHandler(SQLSyntaxErrorException.class)
    public R<?> handleSqlSyntaxErrorException(SQLSyntaxErrorException e) {
        log.error("SQL异常：", e);
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @ExceptionHandler(TooManyResultsException.class)
    public R<?> handleTooManyResultsException(TooManyResultsException e) {
        log.error("SQL异常：", e);
        return R.failDetail(
                ExceptionErrorCode.SQL_MANY_RESULT_EX.getErrorCode(), ExceptionErrorCode.SQL_MANY_RESULT_EX.getErrorMsg());
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public R<?> handleBadSqlGrammarException(BadSqlGrammarException e) {
        log.error("SQL异常：", e);
        String message = e.getSQLException().getMessage();
        if (message.startsWith(DATABASE_PREFIX)) {
            return R.failDetail(
                    ExceptionErrorCode.UNKNOWN_DATABASE.getErrorCode(), ExceptionErrorCode.UNKNOWN_DATABASE.getErrorMsg());
        }
        if (ReUtil.isMatch(TABLE_PREFIX, message)) {
            return R.failDetail(
                    ExceptionErrorCode.UNKNOWN_TABLE.getErrorCode(), ExceptionErrorCode.UNKNOWN_TABLE.getErrorMsg());
        }
        if (message.startsWith(COLUMN_PREFIX)) {
            return R.failDetail(
                    ExceptionErrorCode.UNKNOWN_COLUMN.getErrorCode(), ExceptionErrorCode.UNKNOWN_COLUMN.getErrorMsg());
        }
        return R.failDetail(ExceptionErrorCode.FAILURE.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(SQLIntegrityConstraintViolationException.class)
    public R<?> handleSqlIntegrityConstraintViolationException(SQLIntegrityConstraintViolationException e) {
        log.error("SQL完整性约束违反异常：", e);
        String message = e.getMessage();
        if (message.startsWith("Duplicate entry") && message.endsWith("for key 'PRIMARY'")) {
            return R.failDetail(ExceptionErrorCode.PRIMARY_KEY_CONFLICT_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.PRIMARY_KEY_CONFLICT_EXCEPTION.getErrorMsg());
        }
        Pattern pattern = Pattern.compile(VIOLATION_DATABASE_REGEX);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            System.out.println("Found: " + matcher.group(1) + " | " + matcher.group(2));
            String errorMsg = StrFormatter.format(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorMsg(),
                    matcher.group(2), matcher.group(1));
            return R.failDetail(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorCode(), errorMsg);
        }
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @ExceptionHandler(DuplicateKeyException.class)
    public R<?> handlerDuplicateKeyException(DuplicateKeyException e) {
        log.error("数据重复输入: ", e);
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), "数据重复冲突异常");
    }

    @ExceptionHandler(PersistenceException.class)
    public R<?> handlePersistenceException(PersistenceException e) {
        Throwable rootCause = ExceptionUtil.getRootCause(e);
        if (rootCause instanceof SQLIntegrityConstraintViolationException) {
            return handleSqlIntegrityConstraintViolationException((SQLIntegrityConstraintViolationException) rootCause);
        }
        if (rootCause instanceof BizException) {
            BizException cause = (BizException) rootCause;
            log.error("数据库异常：", e);
            return R.failDetail(cause.getErrorCode(), cause.getMessage());
        } else if (rootCause instanceof MysqlDataTruncation) {
            MysqlDataTruncation cause = (MysqlDataTruncation) rootCause;
            return handleMysqlDataTruncation(cause);
        } else if (rootCause instanceof SQLException) {
            SQLException cause = (SQLException) rootCause;
            return handleSqlException(cause);
        }
        log.error("数据库异常：", e);
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @ExceptionHandler(SQLException.class)
    public R<?> handleSqlException(SQLException e) {
        log.error("SQL异常：", e);
        String message = e.getMessage();
        String regex = "Field\\s+'([^']+)' doesn't have a default value";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String columnName = matcher.group(1);
            String errorMessage = "【" + columnName + "】字段没有默认值！";
            return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), errorMessage);
        }
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), e.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public R<?> handlerDataIntegrityViolationException(DataIntegrityViolationException e) {
        log.error("数据库操作异常:", e);
        String message = e.getMessage();

        // 1. 优先处理特定错误消息（精确匹配关键特征）
        if (message != null && message.contains("invalid input syntax for type numeric")) {
            return R.failDetail(
                    ExceptionErrorCode.INVALID_INPUT_SYNTAX.getErrorCode(),
                    "数值类型输入格式错误: 请检查参数类型，确保传递的是数字而非对象"
            );
        }

        // 2. 处理其他常见数据库错误
        if (message != null && message.startsWith("Data too long")) {
            return R.failDetail(ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorMsg());
        }
        Throwable cause = e.getCause();
        if (cause instanceof SQLException) {
            SQLException sqlException = (SQLException) cause;
            int errorCode = sqlException.getErrorCode();
            if (errorCode == DATABASE_ERROR_CODE) {
                return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), "数据操作异常,输入参数为空");
            }
        }
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @ExceptionHandler(KSQLException.class)
    public R<?> handleKSQLException(KSQLException e) {
        log.error("SQL异常：", e);
        String message = e.getMessage();
        String prefix = "value too long for type character";
        if (message.contains(prefix)) {
            return R.failDetail(ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorMsg());
        }
        String regex = "null value in column \"([^\"]+)\"";
        Pattern nullPattern = Pattern.compile(regex);
        Matcher nullMatcher = nullPattern.matcher(message);
        if (nullMatcher.find()) {
            String columnName = nullMatcher.group(1);
            String errorMessage = "【" + columnName + "】字段没有默认值！";
            return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), errorMessage);
        }
        String columnRegex = "column \"([^\"]+)\" of relation \"([^\"]+)\" does not exist";
        Pattern columnPattern = Pattern.compile(columnRegex);
        Matcher columnMatcher = columnPattern.matcher(message);
        if (columnMatcher.find()) {
            String columnName = columnMatcher.group(1);
            String tableName = columnMatcher.group(2);
            String errorMessage = "【" + tableName + "】表字段【" + columnName + "】不存在，请联系管理员！";
            return R.failDetail(ExceptionErrorCode.UNKNOWN_TABLE.getErrorCode(), errorMessage);
        }
        String tableRegex = "relation \"([^\"]+)\" does not exist";
        Pattern tablePattern = Pattern.compile(tableRegex);
        Matcher tableMatcher = tablePattern.matcher(message);
        if (tableMatcher.find()) {
            String tableName = tableMatcher.group(1);
            String errorMessage = "【" + tableName + "】表不存在，请联系管理员！";
            return R.failDetail(ExceptionErrorCode.UNKNOWN_TABLE.getErrorCode(), errorMessage);
        }
        Pattern pattern = Pattern.compile("duplicate key value violates unique constraint \"([^\"]+)\"");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            String constraintName = matcher.group(1);
            String errorMessage = "违反数据库唯一约束键【" + constraintName + "】";
            return R.failDetail(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorCode(), errorMessage);
        }
        return R.failDetail(ExceptionErrorCode.SQL_EX.getErrorCode(), ExceptionErrorCode.SQL_EX.getErrorMsg());
    }

    @Override
    public int getOrder() {
        return BeanOrderEnum.DATABASE_EXCEPTION_HANDLER_ORDER.getOrder();
    }
}
