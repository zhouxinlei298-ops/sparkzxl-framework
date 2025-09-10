package com.github.sparkzxl.datascope.interceptor;

import com.baomidou.mybatisplus.core.toolkit.ObjectUtils;
import com.baomidou.mybatisplus.core.toolkit.PluginUtils;
import com.baomidou.mybatisplus.extension.plugins.inner.BaseMultiTableInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.InnerInterceptor;
import com.github.sparkzxl.datascope.executor.DataScopeLineExecutor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.statement.delete.Delete;
import net.sf.jsqlparser.statement.insert.Insert;
import net.sf.jsqlparser.statement.update.Update;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;

import java.sql.Connection;

/**
 * description: 数据权限拦截器
 *
 * @author zhouxinlei
 * @since 2024-01-29 15:56:54
 */
@Slf4j
public class DataScopeInnerInterceptor extends BaseMultiTableInnerInterceptor implements InnerInterceptor {

    private final static String SELECT = "SELECT";
    private final DataScopeLineExecutor dataScopeLineExecutor;

    public DataScopeInnerInterceptor(DataScopeLineExecutor dataScopeLineExecutor) {
        this.dataScopeLineExecutor = dataScopeLineExecutor;
    }

    @Override
    public void beforePrepare(StatementHandler sh, Connection connection, Integer transactionTimeout) {
        PluginUtils.MPStatementHandler mpSh = PluginUtils.mpStatementHandler(sh);
        MappedStatement ms = mpSh.mappedStatement();
        SqlCommandType sct = ms.getSqlCommandType();
        if (sct == SqlCommandType.UPDATE || sct == SqlCommandType.DELETE || sct == SqlCommandType.SELECT) {
            // 数据权限开始处理
            dataScopeLineExecutor.create();
            PluginUtils.MPBoundSql mpBs = mpSh.mPBoundSql();
            mpBs.sql(parserMulti(mpBs.sql(), ms.getId()));
        }
    }

    @Override
    protected void processInsert(Insert insert, int index, String sql, Object obj) {
        logger.debug("insert 暂时不需要自动拼接条件");
    }

    /**
     * update 语句处理
     */
    @Override
    protected void processUpdate(Update update, int index, String sql, Object obj) {
        final Table table = update.getTable();
        if (dataScopeLineExecutor.ignoreTable(table.getName())) {
            return;
        }
        if (!dataScopeLineExecutor.matchSqlCommandType(SqlCommandType.UPDATE)) {
            // 过滤退出执行
            return;
        }
        Expression expression = dataScopeLineExecutor.builderExpression(table, update.getWhere());
        if (ObjectUtils.isNotEmpty(expression)) {
            update.setWhere(expression);
        }
        dataScopeLineExecutor.destroy();
    }

    /**
     * delete 语句处理
     */
    @Override
    protected void processDelete(Delete delete, int index, String sql, Object obj) {
        final Table table = delete.getTable();
        if (dataScopeLineExecutor.ignoreTable(delete.getTable().getName())) {
            // 过滤退出执行
            return;
        }
        if (!dataScopeLineExecutor.matchSqlCommandType(SqlCommandType.DELETE)) {
            // 过滤退出执行
            return;
        }
        Expression expression = dataScopeLineExecutor.builderExpression(table, delete.getWhere());
        if (ObjectUtils.isNotEmpty(expression)) {
            delete.setWhere(expression);
        }
        dataScopeLineExecutor.destroy();
    }

    @Override
    public Expression buildTableExpression(Table table, Expression where, String whereSegment) {
        if (dataScopeLineExecutor.ignoreTable(table.getName())) {
            return null;
        }
        return dataScopeLineExecutor.builderExpression(table, where);
    }

}
