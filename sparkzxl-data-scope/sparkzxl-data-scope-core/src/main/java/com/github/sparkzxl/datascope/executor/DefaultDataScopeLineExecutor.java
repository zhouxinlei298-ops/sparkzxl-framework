package com.github.sparkzxl.datascope.executor;

import com.alibaba.ttl.TransmittableThreadLocal;
import com.baomidou.mybatisplus.annotation.DbType;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.datascope.expression.*;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Parenthesis;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.conditional.OrExpression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.util.cnfexpression.MultiAndExpression;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.ibatis.mapping.SqlCommandType;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * description: 数据权限 行级处理器实现
 *
 * @author zhouxinlei
 * @since 2025-06-03 10:08:34
 */
@Slf4j
public class DefaultDataScopeLineExecutor implements DataScopeLineExecutor {

    private final ThreadLocal<List<DataScopeConfProperties.DataScopeConf>> confListThreadLocal = ThreadLocal.withInitial(Lists::newArrayList);

    private final ThreadLocal<Boolean> enableDataScopeThreadLocal = ThreadLocal.withInitial(() -> Boolean.FALSE);

    private final Map<SqlCondition, ExpressionStrategy> expressionStrategyMap = Maps.newConcurrentMap();

    public DefaultDataScopeLineExecutor(DbType dbType) {
        expressionStrategyMap.put(SqlCondition.EQ, new EQExpressionStrategy());
        expressionStrategyMap.put(SqlCondition.IN, new INExpressionStrategy());
        expressionStrategyMap.put(SqlCondition.LIKE, new LIKEExpressionStrategy());
        expressionStrategyMap.put(SqlCondition.LIKE_LEFT, new LIKELeftExpressionStrategy());
        expressionStrategyMap.put(SqlCondition.LIKE_RIGHT, new LIKERightExpressionStrategy());
        expressionStrategyMap.put(SqlCondition.LIST_IN, new ListInExpressionStrategy(dbType));
    }

    @Override
    public void create() {
        Boolean enableDataScope = RequestLocalContextHolder.get(BaseContextConstants.ENABLE_DATA_SCOPE, Boolean.class, Boolean.FALSE);
        enableDataScopeThreadLocal.set(enableDataScope);
        if (enableDataScope) {
            List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = RequestLocalContextHolder.getList(BaseContextConstants.DATA_SCOPE_CONF_LIST, DataScopeConfProperties.DataScopeConf.class);
            if (CollectionUtils.isNotEmpty(dataScopeConfList)) {
                confListThreadLocal.set(dataScopeConfList);
            }
        }
    }

    @Override
    public boolean ignoreTable(String tableColumn) {
        Boolean enableDataScope = enableDataScopeThreadLocal.get();
        if (enableDataScope) {
            List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = confListThreadLocal.get();
            if (CollectionUtils.isEmpty(dataScopeConfList)) {
                return Boolean.TRUE;
            } else {
                List<String> tableNameList = dataScopeConfList.stream().map(DataScopeConfProperties.DataScopeConf::getTableName).distinct().collect(Collectors.toList());
                return !tableNameList.contains(tableColumn);
            }
        } else {
            return Boolean.TRUE;
        }
    }

    @Override
    public boolean matchSqlCommandType(SqlCommandType commandType) {
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = confListThreadLocal.get();
        if (CollectionUtils.isEmpty(dataScopeConfList)) {
            return Boolean.FALSE;
        } else {
            List<SqlCommandType> commandTypeList = dataScopeConfList.stream().map(DataScopeConfProperties.DataScopeConf::getSqlCommandType).distinct().collect(Collectors.toList());
            return commandTypeList.contains(commandType);
        }
    }

    @Override
    public Expression builderExpression(Table table, Expression currentExpression) {
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = confListThreadLocal.get();
        if (CollectionUtils.isEmpty(dataScopeConfList)) {
            return null;
        }
        List<DataScopeConfProperties.DataColumn> dataColumnList = dataScopeConfList.stream().map(DataScopeConfProperties.DataScopeConf::getColumns).flatMap(Collection::stream).distinct().collect(Collectors.toList());
        List<Expression> expressionList = Lists.newArrayList();
        for (DataScopeConfProperties.DataColumn dataColumn : dataColumnList) {
            SqlCondition sqlCondition = dataColumn.getCondition();
            ExpressionStrategy expressionStrategy = expressionStrategyMap.get(sqlCondition);
            ArgumentAssert.notNull(expressionStrategy, "不支持的SQL条件类型");
            Expression expression = expressionStrategy.builderExpression(table, dataColumn);
            if (ObjectUtils.isNotEmpty(expression)) {
                expressionList.add(expression);
            }
        }
        if (CollectionUtils.isEmpty(expressionList)) {
            return null;
        }
        if (currentExpression == null) {
            return new MultiAndExpression(expressionList);
        }
        if (currentExpression instanceof OrExpression) {
            return new AndExpression(new Parenthesis(currentExpression), new MultiAndExpression(expressionList));
        } else {
            return new AndExpression(currentExpression, new MultiAndExpression(expressionList));
        }
    }

    @Override
    public void destroy() {
        confListThreadLocal.remove();
    }
}
