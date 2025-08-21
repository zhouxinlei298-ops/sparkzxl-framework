package com.github.sparkzxl.datascope.expression;

import com.baomidou.mybatisplus.annotation.DbType;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.ListUtils;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Function;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.schema.Column;
import net.sf.jsqlparser.schema.Table;
import org.apache.commons.collections4.CollectionUtils;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;
import java.util.stream.Collectors;

/**
 * description: LIST_IN表达式条件构建
 *
 * @author zhouxinlei
 * @since 2024-01-29 14:57:41
 */
@Slf4j
public class ListInExpressionStrategy implements ExpressionStrategy {

    private final DbType dbType;

    public ListInExpressionStrategy(DbType dbType) {
        this.dbType = dbType;
    }

    @Override
    public Expression builderExpression(Table table, DataScopeConfProperties.DataColumn dataColumn) {
        List<String> valList = RequestLocalContextHolder.getList(dataColumn.getLoadKey());
        Column column = this.getAliasColumn(table, dataColumn.getColumn());
        if (CollectionUtils.isNotEmpty(valList)) {
            valList = valList.stream().distinct().collect(Collectors.toList());
            String functionName = "find_in_set_multiple";
            log.debug("Check whether the [find_in_set_multiple] function is available in {}", dbType.getDb());
            Function function = new Function();
            // 设置函数名
            function.setName(functionName);
            // 创建参数表达式
            ExpressionList expressionListCount = new ExpressionList();
            expressionListCount.setExpressions(
                    Lists.newArrayList(new StringValue(ListUtils.listToString(valList)), column));
            // 设置参数
            function.setParameters(expressionListCount);
            return function;
        } else {
            if (dataColumn.isForce()) {
                return getIsNullExpression(column);
            }
        }
        return null;
    }

    @Override
    public SqlCondition getSqlCondition() {
        return SqlCondition.LIST_IN;
    }
}
