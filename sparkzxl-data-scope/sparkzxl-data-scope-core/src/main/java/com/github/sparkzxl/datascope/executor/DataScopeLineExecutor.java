package com.github.sparkzxl.datascope.executor;

import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import org.apache.ibatis.mapping.SqlCommandType;

/**
 * description: 数据权限 行级处理器
 *
 * @author zhouxinlei
 * @since 2023-12-20 10:41:21
 */
public interface DataScopeLineExecutor {

    void create();

    /**
     * 根据表名判断是否忽略拼接scope条件
     * <p>
     * 默认不进行解析并拼接scope条件
     *
     * @param tableName 表名
     * @return 是否忽略, true:表示忽略，false:需要解析并拼接scope条件
     */
    default boolean ignoreTable(String tableName) {
        return Boolean.TRUE;
    }

    /**
     * 匹配sql命令类型
     *
     * @return boolean
     */
    default boolean matchSqlCommandType(SqlCommandType commandType) {
        return Boolean.FALSE;
    }

    /**
     * 构建表达式
     *
     * @param currentExpression 当前表达式
     * @param table             表名
     * @return Expression
     */
    Expression builderExpression(Table table, Expression currentExpression);

    void destroy();

}
