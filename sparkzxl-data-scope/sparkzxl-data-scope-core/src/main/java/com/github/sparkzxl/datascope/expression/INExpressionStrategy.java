package com.github.sparkzxl.datascope.expression;

import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.ValueListExpression;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.commons.collections4.CollectionUtils;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;
import java.util.stream.Collectors;

/**
 * description: IN表达式条件构建
 *
 * @author zhouxinlei
 * @since 2024-01-29 14:57:41
 */
public class INExpressionStrategy implements ExpressionStrategy {

    @Override
    public Expression builderExpression(Table table, DataScopeConfProperties.DataColumn dataColumn) {
        List<String> valList = RequestLocalContextHolder.getList(dataColumn.getLoadKey());
        Column column = this.getAliasColumn(table, dataColumn.getColumn());
        if (CollectionUtils.isNotEmpty(valList)) {
            valList = valList.stream().distinct().collect(Collectors.toList());
            // 如果集合只有一个，使用=比较好
            if (valList.size() == 1) {
                return getEqualsTo(column, valList.get(0));
            } else {
                ValueListExpression valueListExpression = new ValueListExpression();
                List<Expression> inExpressionList = valList.stream().map(StringValue::new).collect(Collectors.toList());
                valueListExpression.setExpressionList(new ExpressionList(inExpressionList));
                InExpression inExpression = new InExpression();
                inExpression.setLeftExpression(column);
                inExpression.setRightExpression(valueListExpression);
                return inExpression;
            }
        } else {
            if (dataColumn.isForce()) {
                return getIsNullExpression(column);
            }
        }
        return null;
    }

    @Override
    public SqlCondition getSqlCondition() {
        return SqlCondition.IN;
    }
}
