package com.github.sparkzxl.datascope.expression;

import com.baomidou.mybatisplus.core.toolkit.StringPool;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.EqualsTo;
import net.sf.jsqlparser.expression.operators.relational.IsNullExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

public interface ExpressionStrategy {

    /**
     * 构建条件表达式
     *
     * @param table      表
     * @param dataColumn 数据列
     * @return Expression
     */
    Expression builderExpression(Table table, DataScopeConfProperties.DataColumn dataColumn);

    default Column getAliasColumn(Table table, String columnName) {
        StringBuilder column = new StringBuilder();
        if (table.getAlias() != null) {
            column.append(table.getAlias().getName()).append(StringPool.DOT);
        }
        column.append(columnName);
        return new Column(column.toString());
    }

    /**
     * 获取equal表达式
     *
     * @param column 列
     * @param val    数据值
     * @return Expression
     */
    default Expression getEqualsTo(Column column, String val) {
        EqualsTo equalsExpression = new EqualsTo();
        equalsExpression.setLeftExpression(column);
        equalsExpression.setRightExpression(new StringValue(val));
        return equalsExpression;
    }

    /**
     * 获取为空{column is null}表达式
     *
     * @param column 数据列
     * @return Expression
     */
    default Expression getIsNullExpression(Column column) {
        IsNullExpression isNullExpression = new IsNullExpression();
        isNullExpression.setLeftExpression(column);
        return isNullExpression;
    }

    SqlCondition getSqlCondition();
}
