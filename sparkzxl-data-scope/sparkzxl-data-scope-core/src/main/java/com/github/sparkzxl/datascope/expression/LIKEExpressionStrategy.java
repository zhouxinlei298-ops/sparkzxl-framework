package com.github.sparkzxl.datascope.expression;

import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.LikeExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.commons.lang3.StringUtils;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

/**
 * description: IN表达式条件构建
 *
 * @author zhouxinlei
 * @since 2024-01-29 14:57:41
 */
public class LIKEExpressionStrategy implements ExpressionStrategy {

    @Override
    public Expression builderExpression(Table table, DataScopeConfProperties.DataColumn dataColumn) {
        String val = RequestLocalContextHolder.get(dataColumn.getLoadKey());
        Column column = this.getAliasColumn(table, dataColumn.getColumn());
        if (StringUtils.isNotEmpty(val)) {
            LikeExpression likeExpression = new LikeExpression();
            likeExpression.setLeftExpression(column);
            likeExpression.setRightExpression(new StringValue("%".concat(val).concat("%")));
            return likeExpression;
        } else {
            if (dataColumn.isForce()) {
                return getIsNullExpression(column);
            }
        }
        return null;
    }

    @Override
    public SqlCondition getSqlCondition() {
        return SqlCondition.LIKE;
    }
}
