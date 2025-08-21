package com.github.sparkzxl.datascope.expression;

import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.commons.lang3.StringUtils;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

/**
 * description: 等值表达式条件构建
 *
 * @author zhouxinlei
 * @since 2024-01-29 14:57:41
 */
public class EQExpressionStrategy implements ExpressionStrategy {

    @Override
    public Expression builderExpression(Table table, DataScopeConfProperties.DataColumn dataColumn) {
        String val = RequestLocalContextHolder.get(dataColumn.getLoadKey());
        Column column = getAliasColumn(table, dataColumn.getColumn());
        if (StringUtils.isNotEmpty(val)) {
            return getEqualsTo(column, val);
        } else {
            if (dataColumn.isForce()) {
                return getIsNullExpression(column);
            }
        }
        return null;
    }

    @Override
    public SqlCondition getSqlCondition() {
        return SqlCondition.EQ;
    }
}
