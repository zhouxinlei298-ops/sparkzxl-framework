package com.github.sparkzxl.mybatis.plugins;

import cn.hutool.core.exceptions.ExceptionUtil;
import com.github.sparkzxl.core.constant.enums.EnvironmentEnum;
import com.github.sparkzxl.core.thread.ThreadPoolExecutorFactory;
import com.github.sparkzxl.mybatis.send.SendNoticeService;
import com.github.sparkzxl.mybatis.send.SqlMonitorMessage;
import com.google.common.base.Stopwatch;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.type.TypeHandlerRegistry;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * description: 拦截执行时间过长的sql语句并发送钉钉消息
 *
 * @author zhouxinlei
 * @since 2025-11-21 14:14:47
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update", args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query", args = {MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class})
})
@Slf4j
@Component
public class SlowSqlMonitorInterceptor implements Interceptor, DisposableBean {

    // ==================== 常量定义 ====================
    /**
     * 默认慢SQL阈值(毫秒)
     */
    private static final long DEFAULT_SLOW_SQL_THRESHOLD = 3000L;

    /**
     * 测试环境慢SQL阈值(毫秒)
     */
    private static final long TEST_ENV_SLOW_SQL_THRESHOLD = 6000L;

    /**
     * 堆栈跟踪最大行数
     */
    private static final int MAX_STACK_TRACE_LINES = 10;

    /**
     * 需要过滤的框架包前缀
     */
    private static final String[] FRAMEWORK_PACKAGE_PREFIXES = {
            "org.apache.ibatis",
            "com.baomidou.mybatisplus",
            "org.springframework",
            "java.lang.reflect",
            "sun.reflect",
            "com.sun.proxy"
    };

    // ==================== 配置属性 ====================
    @Value("${database.slow-sql.threshold-ms:#{null}}")
    private Long configuredSlowSqlThreshold;

    @Value("${database.monitor.enabled:true}")
    private boolean monitorEnabled;

    // ==================== 依赖注入 ====================
    private SendNoticeService sendNoticeService;

    @Autowired
    private ApplicationContext applicationContext;

    private long slowSqlThreshold = DEFAULT_SLOW_SQL_THRESHOLD;

    // ==================== 线程池 ====================
    private ExecutorService executorService;

    private String activeProfile;

    /**
     * 构造函数
     * 初始配置使用默认值，实际值会在init()方法中从配置中加载
     */
    public SlowSqlMonitorInterceptor() {
        initThreadPool();
    }

    /**
     * 初始化线程池
     */
    private void initThreadPool() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        this.executorService = ThreadPoolExecutorFactory.getThreadPoolExecutor(
                3,
                6,
                200,
                "slow-sql-monitor-pool",
                (r, executor) -> {
                    log.warn("Slow SQL monitor thread pool is saturated. Task rejected. " +
                                    "Pool size: {}, Active threads: {}, Queue size: {}",
                            executor.getPoolSize(), executor.getActiveCount(), executor.getQueue().size());
                    // 不执行拒绝策略，防止影响主业务流程
                });
    }

    /**
     * 初始化方法，根据环境调整慢SQL阈值
     */
    @PostConstruct
    public void init() {
        this.activeProfile = StringUtils.defaultIfEmpty(
                applicationContext.getEnvironment().getProperty("spring.profiles.active"), "dev");
        sendNoticeService = applicationContext.getBean(SendNoticeService.class);
        // 优先使用配置的阈值，如果没有配置，则根据环境设置默认值
        if (configuredSlowSqlThreshold != null && configuredSlowSqlThreshold > 0) {
            this.slowSqlThreshold = configuredSlowSqlThreshold;
        } else if (isProductionEnvironment()) {
            this.slowSqlThreshold = DEFAULT_SLOW_SQL_THRESHOLD;
        } else {
            this.slowSqlThreshold = TEST_ENV_SLOW_SQL_THRESHOLD;
        }

        log.info("Slow SQL monitor initialized. Environment: {}, Threshold: {}ms, Enabled: {}",
                activeProfile, slowSqlThreshold, monitorEnabled);
    }

    /**
     * 判断是否为生产环境
     */
    private boolean isProductionEnvironment() {
        return StringUtils.equalsIgnoreCase(activeProfile, EnvironmentEnum.PRE.name()) ||
                StringUtils.equalsIgnoreCase(activeProfile, EnvironmentEnum.PROD.name());
    }

    /**
     * 将对象转换为SQL中的参数字符串
     *
     * @param obj 对象
     * @return SQL参数字符串
     */
    private static String getParameterValue(Object obj) {
        if (obj == null) {
            return "NULL";
        }
        if (obj instanceof String) {
            // 对字符串中的单引号进行转义，防止SQL注入风险（仅用于日志显示）
            return "'" + escapeSqlString((String) obj) + "'";
        }
        if (obj instanceof Date) {
            DateFormat formatter = DateFormat.getDateTimeInstance(
                    DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.getDefault());
            return "'" + formatter.format(obj) + "'";
        }
        if (obj instanceof Calendar) {
            return getParameterValue(((Calendar) obj).getTime());
        }
        if (obj instanceof Enum) {
            // 枚举类型使用其名称
            return "'" + ((Enum<?>) obj).name() + "'";
        }
        if (obj instanceof Number || obj instanceof Boolean) {
            return obj.toString();
        }
        // 其他类型，返回简化的对象表示
        return getSafeObjectRepresentation(obj);
    }

    /**
     * 安全转义SQL字符串，避免日志注入
     */
    private static String escapeSqlString(String str) {
        if (str == null) {
            return null;
        }
        // 限制字符串长度，避免过长的日志
        String truncated = StringUtils.abbreviate(str, 200);
        // 转义单引号
        return truncated.replace("'", "''");
    }

    /**
     * 获取对象的安全表示，避免敏感信息泄露和过长内容
     */
    private static String getSafeObjectRepresentation(Object obj) {
        if (obj == null) {
            return "null";
        }

        String str = obj.toString();
        // 敏感字段检查和脱敏
        if (isSensitiveField(str)) {
            return "[REDACTED]";
        }

        // 限制字符串长度
        return StringUtils.abbreviate(str, 100);
    }

    /**
     * 检查是否为敏感字段
     */
    private static boolean isSensitiveField(String value) {
        if (StringUtils.isBlank(value)) {
            return false;
        }

        // 简单的敏感信息检测，可以根据需要扩展
        String lowerValue = value.toLowerCase();
        return lowerValue.contains("password") ||
                lowerValue.contains("secret") ||
                lowerValue.contains("token") ||
                lowerValue.contains("key");
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 如果监控被禁用，直接执行并返回
        if (!monitorEnabled) {
            return invocation.proceed();
        }

        Stopwatch dbStopwatch = Stopwatch.createStarted();
        boolean success = true;
        try {
            // 执行原始方法
            return invocation.proceed();
        } catch (Exception e) {
            success = false;
            // 异步处理SQL异常
            if (shouldMonitorException(e)) {
                Throwable rootCause = ExceptionUtil.getRootCause(e);
                handleSqlExceptionAsync(invocation, rootCause);
            }
            throw e;
        } finally {
            // 无论成功失败，都记录执行时间
            long executeTime = dbStopwatch.elapsed(TimeUnit.MILLISECONDS);
            if (success && executeTime >= slowSqlThreshold) {
                // 异步处理慢SQL
                handleSlowSqlAsync(invocation, executeTime);
            }
        }
    }

    /**
     * 判断是否应该监控该异常
     */
    private boolean shouldMonitorException(Exception e) {
        // 可以在这里添加需要忽略的异常类型
        String exceptionClassName = e.getClass().getName();
        return !exceptionClassName.contains("TimeoutException") &&
                !exceptionClassName.contains("InterruptedException");
    }

    /**
     * 异步处理慢SQL
     */
    private void handleSlowSqlAsync(Invocation invocation, long executeTime) {
        if (!monitorEnabled) {
            return;
        }

        // 使用Future限制执行时间，避免通知服务异常影响主流程
        CompletableFuture.runAsync(() -> processSlowSqlNotification(invocation, executeTime), executorService)
                .exceptionally(ex -> {
                    log.error("Slow SQL notification processing timed out or failed", ex);
                    return null;
                });
    }

    /**
     * 实际处理慢SQL通知
     */
    private void processSlowSqlNotification(Invocation invocation, long executeTime) {
        try {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;

            String sqlId = mappedStatement.getId();
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            Configuration configuration = mappedStatement.getConfiguration();

            // 解析SQL
            String sql = parseSql(configuration, boundSql);
            // 构建并发送消息
            SqlMonitorMessage message = buildSlowSqlMessage(sqlId, sql, executeTime);
            sendNoticeService.send(message);
        } catch (Exception e) {
            log.error("Failed to process slow SQL notification", e);
        }
    }

    /**
     * 异步处理SQL异常
     */
    private void handleSqlExceptionAsync(Invocation invocation, Throwable throwable) {
        if (!monitorEnabled) {
            return;
        }

        CompletableFuture.runAsync(() -> processSqlExceptionNotification(invocation, throwable), executorService)
                .exceptionally(ex -> {
                    log.error("SQL exception notification processing timed out or failed", ex);
                    return null;
                });
    }

    /**
     * 实际处理SQL异常通知
     */
    private void processSqlExceptionNotification(Invocation invocation, Throwable throwable) {
        try {
            MappedStatement mappedStatement = (MappedStatement) invocation.getArgs()[0];
            Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;
            String sqlId = mappedStatement.getId();
            BoundSql boundSql = mappedStatement.getBoundSql(parameter);
            Configuration configuration = mappedStatement.getConfiguration();
            // 解析SQL
            String sql = parseSql(configuration, boundSql);
            // 构建并发送消息
            SqlMonitorMessage message = buildExceptionMessage(sqlId, sql, throwable);
            sendNoticeService.send(message);
        } catch (Exception ex) {
            log.error("Failed to process SQL exception notification", ex);
        }
    }

    /**
     * 构建慢SQL消息
     */
    private SqlMonitorMessage buildSlowSqlMessage(String sqlId, String sql, long executeTime) {
        SqlMonitorMessage message = new SqlMonitorMessage();
        message.setType(Type.SLOW_SQL);
        message.setSqlId(sqlId);
        message.setSql(sql);
        message.setExecuteTime(executeTime);
        message.setStackTrace(getRelevantStackTrace(null));
        return message;
    }

    /**
     * 构建异常消息
     */
    private SqlMonitorMessage buildExceptionMessage(String sqlId, String sql, Throwable throwable) {
        SqlMonitorMessage message = new SqlMonitorMessage();
        message.setType(Type.SQL_EXCEPTION);
        message.setSqlId(sqlId);
        message.setSql(sql);
        String exceptionMsg = ExceptionUtil.getMessage(throwable);
        message.setExceptionMsg(StringUtils.defaultString(exceptionMsg, "Unknown SQL exception"));
        message.setStackTrace(getRelevantStackTrace(throwable));
        return message;
    }

    /**
     * 高效解析SQL，替换占位符为实际参数值
     *
     * @param configuration mybatis配置信息
     * @param boundSql      mybatis存放sql信息的对象
     * @return 实际执行的sql语句
     */
    private String parseSql(Configuration configuration, BoundSql boundSql) {
        try {
            String sql = boundSql.getSql().replaceAll("[\\s]+", " ").trim();
            Object parameterObject = boundSql.getParameterObject();
            List<ParameterMapping> parameterMappings = boundSql.getParameterMappings();

            if (parameterObject == null || CollectionUtils.isEmpty(parameterMappings)) {
                return sql;
            }

            // 收集所有参数值
            List<String> parameterValues = extractParameterValues(configuration, boundSql, parameterObject, parameterMappings);

            // 替换占位符
            return replacePlaceholders(sql, parameterValues);
        } catch (Exception e) {
            log.error("Error parsing SQL", e);
            return "Error parsing SQL: " + e.getMessage();
        }
    }

    /**
     * 提取参数值
     */
    private List<String> extractParameterValues(Configuration configuration,
                                                BoundSql boundSql,
                                                Object parameterObject,
                                                List<ParameterMapping> parameterMappings) {
        List<String> parameterValues = new ArrayList<>(parameterMappings.size());
        TypeHandlerRegistry typeHandlerRegistry = configuration.getTypeHandlerRegistry();

        if (typeHandlerRegistry.hasTypeHandler(parameterObject.getClass())) {
            parameterValues.add(getParameterValue(parameterObject));
        } else {
            MetaObject metaObject = configuration.newMetaObject(parameterObject);
            for (ParameterMapping pm : parameterMappings) {
                String propertyName = pm.getProperty();
                Object value = extractParameterValue(metaObject, boundSql, propertyName);
                parameterValues.add(getParameterValue(value));
            }
        }
        return parameterValues;
    }

    /**
     * 提取单个参数值
     */
    private Object extractParameterValue(MetaObject metaObject, BoundSql boundSql, String propertyName) {
        if (metaObject.hasGetter(propertyName)) {
            return metaObject.getValue(propertyName);
        } else if (boundSql.hasAdditionalParameter(propertyName)) {
            return boundSql.getAdditionalParameter(propertyName);
        } else {
            return null;
        }
    }

    /**
     * 替换SQL中的占位符
     *
     * @param sql             sql
     * @param parameterValues 参数值列表
     * @return String
     */
    private String replacePlaceholders(String sql, List<String> parameterValues) {
        // 添加边界检查
        if (StringUtils.isBlank(sql) || CollectionUtils.isEmpty(parameterValues)) {
            return sql;
        }

        // 预估容量，避免频繁扩容
        StringBuilder sb = new StringBuilder(sql.length() + parameterValues.size() * 16);
        int index = 0;
        int start = 0;

        // 遍历SQL，替换占位符
        for (int i = 0; i < sql.length(); i++) {
            if (sql.charAt(i) == '?') {
                if (index < parameterValues.size()) {
                    // 添加 ? 之前的字符串
                    sb.append(sql, start, i);
                    // 添加替换值
                    sb.append(parameterValues.get(index++));
                    // 更新下次复制的起始位置
                    start = i + 1;
                }
                // 如果参数不够用了，后面的 ? 就保持原样或者可以根据需求处理
            }
        }

        // 添加最后一个 ? 之后剩余的 SQL 部分
        sb.append(sql, start, sql.length());

        return sb.toString();
    }

    /**
     * 获取相关的堆栈信息，过滤掉框架调用
     */
    private String getRelevantStackTrace(Throwable throwable) {
        if (throwable == null) {
            return StringUtils.EMPTY;
        }
        StackTraceElement[] elements = throwable.getStackTrace();
        if (ArrayUtils.isEmpty(elements)) {
            return StringUtils.EMPTY;
        }

        List<StackTraceElement> relevantElements = new ArrayList<>(MAX_STACK_TRACE_LINES);
        // 从索引3开始，跳过getStackTrace()、当前方法和调用方法
        for (int i = 3; i < elements.length && relevantElements.size() < MAX_STACK_TRACE_LINES; i++) {
            StackTraceElement element = elements[i];
            if (isRelevantStackElement(element)) {
                relevantElements.add(element);
            }
        }

        if (relevantElements.isEmpty()) {
            return "No relevant stack trace found";
        }

        StringBuilder sb = new StringBuilder("Relevant Stack Trace:\n");
        for (StackTraceElement element : relevantElements) {
            sb.append("\tat ").append(element).append("\n");
        }

        if (relevantElements.size() == MAX_STACK_TRACE_LINES && elements.length > 3 + relevantElements.size()) {
            sb.append("\t... (more stack frames omitted)\n");
        }

        return sb.toString();
    }

    /**
     * 判断堆栈元素是否相关（是否业务代码）
     */
    private boolean isRelevantStackElement(StackTraceElement element) {
        String className = element.getClassName();

        // 过滤框架包
        for (String prefix : FRAMEWORK_PACKAGE_PREFIXES) {
            if (className.startsWith(prefix)) {
                return false;
            }
        }
        // 过滤当前方法
        return !className.equals(SlowSqlMonitorInterceptor.class.getName());
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof Executor) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        // 保留扩展性，可以根据需要添加配置
        String thresholdStr = properties.getProperty("slowSqlThreshold");
        if (StringUtils.isNotEmpty(thresholdStr)) {
            try {
                long threshold = Long.parseLong(thresholdStr);
                if (threshold > 0) {
                    this.slowSqlThreshold = threshold;
                }
            } catch (NumberFormatException e) {
                log.warn("Invalid slowSqlThreshold value: {}", thresholdStr);
            }
        }

        String enabledStr = properties.getProperty("enabled");
        if (StringUtils.isNotEmpty(enabledStr)) {
            this.monitorEnabled = Boolean.parseBoolean(enabledStr);
        }
    }

    /**
     * 应用关闭时优雅停止线程池
     */
    @Override
    public void destroy() throws Exception {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
            log.info("Slow SQL monitor thread pool has been shutdown gracefully");
        }
    }

    /**
     * SQL监控类型枚举
     */
    public enum Type {
        /**
         * 慢sql
         */
        SLOW_SQL,
        /**
         * SQL异常
         */
        SQL_EXCEPTION
    }
}
