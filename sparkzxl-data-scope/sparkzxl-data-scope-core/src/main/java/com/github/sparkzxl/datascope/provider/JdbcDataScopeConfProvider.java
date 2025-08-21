package com.github.sparkzxl.datascope.provider;

import cn.hutool.core.text.StrFormatter;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.core.util.ListUtils;
import com.google.common.collect.Lists;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;
import java.util.function.Function;

/**
 * description: 数据库加载数据源实现类
 *
 * @author zhouxinlei
 */
@RequiredArgsConstructor
public class JdbcDataScopeConfProvider extends BaseDataScopeConfProvider {

    private final Function<String, DataScopeConfProperties.DataScopeConf> function;
    private final Function<List<String>, List<DataScopeConfProperties.DataScopeConf>> functionList;

    @Override
    public DataScopeConfProperties.DataScopeConf loadByScopeId(String scopeId) {
        DataScopeConfProperties.DataScopeConf dataScopeConf = function.apply(scopeId);
        ArgumentAssert.notNull(dataScopeConf, StrFormatter.format("数据权限scopeId[{}]未配置，请联系管理员", scopeId));
        return dataScopeConf;
    }

    @Override
    public List<DataScopeConfProperties.DataScopeConf> loadByScopeIdList(List<String> scopeIdList) {
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = functionList.apply(scopeIdList);
        ArgumentAssert.notNull(dataScopeConfList, StrFormatter.format("数据权限scopeId[{}]未配置，请联系管理员", ListUtils.listToString(scopeIdList)));
        return dataScopeConfList;
    }

    @Override
    public List<DataScopeConfProperties.DataScopeConf> loadAll() {
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = functionList.apply(null);
        if (CollectionUtils.isNotEmpty(dataScopeConfList)) {
            return dataScopeConfList;
        } else {
            return Lists.newArrayList();
        }
    }
}
