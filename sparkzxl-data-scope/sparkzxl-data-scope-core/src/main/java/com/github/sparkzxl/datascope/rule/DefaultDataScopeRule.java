package com.github.sparkzxl.datascope.rule;

import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;

/**
 * description: 数据权限规则
 *
 * @author zhouxinlei
 * @since 2024-01-29 11:14:12
 */
public class DefaultDataScopeRule implements DataScopeRule {

    @Override
    public List<DataScopeConfProperties.DataScopeConf> chooseConfList(List<DataScopeConfProperties.DataScopeConf> dataScopeConfList) {
        if (CollectionUtils.isNotEmpty(dataScopeConfList)) {
            return dataScopeConfList;
        }
        return Lists.newArrayList();
    }

    @Override
    public String getType() {
        return "defaultRule";
    }
}
