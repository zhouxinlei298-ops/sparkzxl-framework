package com.github.sparkzxl.mybatis.support;

import cn.hutool.core.text.StrFormatter;
import cn.hutool.core.util.ReUtil;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import com.kingbase8.util.KSQLException;
import com.mysql.cj.jdbc.exceptions.MysqlDataTruncation;
import org.mybatis.spring.MyBatisSystemException;
import org.apache.ibatis.exceptions.TooManyResultsException;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.SQLSyntaxErrorException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 数据库异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class DatabaseExceptionCodeRegistration implements InitializingBean {

    private static final int DATABASE_ERROR_CODE = 1364;

    @Override
    public void afterPropertiesSet() {
        // ===== A 类：静态映射 =====
        ExceptionCodeResolver.register(TooManyResultsException.class, ExceptionErrorCode.SQL_MANY_RESULT_EX);
        ExceptionCodeResolver.register(MyBatisSystemException.class, ExceptionErrorCode.SQL_EX);
        ExceptionCodeResolver.register(SQLSyntaxErrorException.class, ExceptionErrorCode.SQL_EX);
        ExceptionCodeResolver.register(DuplicateKeyException.class, ExceptionErrorCode.SQL_EX);

        // ===== 消息提取器 =====
        ExceptionCodeResolver.registerMessageExtractor(BadSqlGrammarException.class,
                e -> {((BadSqlGrammarException) e).getSQLException();
                    return ((BadSqlGrammarException) e).getSQLException().getMessage();
                });

        // ===== C 类：消息谓词匹配 =====

        // MysqlDataTruncation
        ExceptionCodeResolver.register(MysqlDataTruncation.class,
                msg -> msg != null && msg.contains("Data too long for column"),
                ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION);
        ExceptionCodeResolver.register(MysqlDataTruncation.class,
                ExceptionErrorCode.SQL_EX);

        // BadSqlGrammarException
        ExceptionCodeResolver.register(BadSqlGrammarException.class,
                msg -> msg != null && msg.startsWith("Unknown database"),
                ExceptionErrorCode.UNKNOWN_DATABASE);
        ExceptionCodeResolver.register(BadSqlGrammarException.class,
                msg -> msg != null && ReUtil.isMatch("^Table.*doesn't exist$", msg),
                ExceptionErrorCode.UNKNOWN_TABLE);
        ExceptionCodeResolver.register(BadSqlGrammarException.class,
                msg -> msg != null && msg.startsWith("Unknown column"),
                ExceptionErrorCode.UNKNOWN_COLUMN);
        ExceptionCodeResolver.register(BadSqlGrammarException.class,
                ExceptionErrorCode.SQL_EX);

        // SQLIntegrityConstraintViolationException
        ExceptionCodeResolver.register(SQLIntegrityConstraintViolationException.class,
                msg -> msg != null && msg.startsWith("Duplicate entry") && msg.endsWith("for key 'PRIMARY'"),
                ExceptionErrorCode.PRIMARY_KEY_CONFLICT_EXCEPTION);
        ExceptionCodeResolver.register(SQLIntegrityConstraintViolationException.class,
                ExceptionErrorCode.SQL_EX);

        // ===== D 类：自定义解析函数 =====

        // SQLException — regex 提取字段名
        ExceptionCodeResolver.registerResolver(SQLException.class, this::resolveSqlException);

        // SQLIntegrityConstraintViolationException — D 类解析（违反唯一约束）
        ExceptionCodeResolver.registerResolver(SQLIntegrityConstraintViolationException.class, this::resolveSqlIntegrityConstraintViolation);

        // DataIntegrityViolationException — 消息匹配 + cause errorCode 判断
        ExceptionCodeResolver.registerResolver(DataIntegrityViolationException.class, this::resolveDataIntegrityViolation);

        // KSQLException — 4 种 regex 模式
        ExceptionCodeResolver.registerResolver(KSQLException.class, this::resolveKSQLException);
    }

    private ExceptionCodeResolver.ResolvedError resolveSqlException(Throwable ex) {
        String message = ex.getMessage();
        if (message != null) {
            Pattern pattern = Pattern.compile("Field\\s+'([^']+)' doesn't have a default value");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.SQL_EX.getErrorCode(),
                        "【" + matcher.group(1) + "】字段没有默认值！");
            }
        }
        return null;
    }

    private ExceptionCodeResolver.ResolvedError resolveSqlIntegrityConstraintViolation(Throwable ex) {
        SQLIntegrityConstraintViolationException e = (SQLIntegrityConstraintViolationException) ex;
        String message = e.getMessage();
        if (message != null) {
            Pattern pattern = Pattern.compile("Duplicate entry '(.*?)' for key '(.*?)'");
            Matcher matcher = pattern.matcher(message);
            if (matcher.find()) {
                String errorMsg = StrFormatter.format(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorMsg(),
                        matcher.group(2), matcher.group(1));
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorCode(),
                        errorMsg);
            }
        }
        return null;
    }

    private ExceptionCodeResolver.ResolvedError resolveDataIntegrityViolation(Throwable ex) {
        DataIntegrityViolationException e = (DataIntegrityViolationException) ex;
        String message = e.getMessage();
        if (message != null && message.contains("invalid input syntax for type numeric")) {
            return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.INVALID_INPUT_SYNTAX.getErrorCode(),
                    "数值类型输入格式错误: 请检查参数类型，确保传递的是数字而非对象");
        }
        if (message != null && message.startsWith("Data too long")) {
            return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorMsg());
        }
        Throwable cause = e.getCause();
        if (cause instanceof SQLException) {
            SQLException sqlException = (SQLException) cause;
            if (sqlException.getErrorCode() == DATABASE_ERROR_CODE) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.SQL_EX.getErrorCode(),
                        "数据操作异常,输入参数为空");
            }
        }
        return null;
    }

    private ExceptionCodeResolver.ResolvedError resolveKSQLException(Throwable ex) {
        String message = ex.getMessage();
        if (message != null && message.contains("value too long for type")) {
            return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorCode(),
                    ExceptionErrorCode.COLUMN_DATA_TO_LONG_EXCEPTION.getErrorMsg());
        }
        if (message != null) {
            Matcher nullMatcher = Pattern.compile("null value in column \"([^\"]+)\"").matcher(message);
            if (nullMatcher.find()) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.SQL_EX.getErrorCode(),
                        "【" + nullMatcher.group(1) + "】字段没有默认值！");
            }
            Matcher columnMatcher = Pattern.compile("column \"([^\"]+)\" of relation \"([^\"]+)\" does not exist").matcher(message);
            if (columnMatcher.find()) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.UNKNOWN_TABLE.getErrorCode(),
                        "【" + columnMatcher.group(2) + "】表字段【" + columnMatcher.group(1) + "】不存在，请联系管理员！");
            }
            Matcher tableMatcher = Pattern.compile("relation \"([^\"]+)\" does not exist").matcher(message);
            if (tableMatcher.find()) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.UNKNOWN_TABLE.getErrorCode(),
                        "【" + tableMatcher.group(1) + "】表不存在，请联系管理员！");
            }
            Matcher constraintMatcher = Pattern.compile("duplicate key value violates unique constraint \"([^\"]+)\"").matcher(message);
            if (constraintMatcher.find()) {
                return new ExceptionCodeResolver.ResolvedError(ExceptionErrorCode.VIOLATION_DATABASE_CONSTRAINT_EXCEPTION.getErrorCode(),
                        "违反数据库唯一约束键【" + constraintMatcher.group(1) + "】");
            }
        }
        return null;
    }
}
