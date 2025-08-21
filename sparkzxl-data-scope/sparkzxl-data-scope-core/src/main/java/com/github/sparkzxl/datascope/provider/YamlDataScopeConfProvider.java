package com.github.sparkzxl.datascope.provider;

import cn.hutool.core.text.StrFormatter;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * description: 从yaml读取数据权限配置
 *
 * @author zhouxinlei
 * @since 2023-12-20 11:22:11
 */
public class YamlDataScopeConfProvider extends BaseDataScopeConfProvider {

    private final Map<String, DataScopeConfProperties.DataScopeConf> configMap = Maps.newHashMap();

    public YamlDataScopeConfProvider(List<DataScopeConfProperties.DataScopeConf> configs) {
        if (CollectionUtils.isNotEmpty(configs)) {
            Map<String, DataScopeConfProperties.DataScopeConf> confMap = configs.stream().collect(Collectors.toMap(DataScopeConfProperties.DataScopeConf::getScopeId, k -> k));
            configMap.putAll(confMap);
        }
    }

    @Override
    public DataScopeConfProperties.DataScopeConf loadByScopeId(String scopeId) {
        DataScopeConfProperties.DataScopeConf dataScopeConf = configMap.get(scopeId);
        ArgumentAssert.notNull(dataScopeConf, StrFormatter.format("数据权限scopeId[{}]未配置，请联系管理员", scopeId));
        return dataScopeConf;
    }

    @Override
    public List<DataScopeConfProperties.DataScopeConf> loadByScopeIdList(List<String> scopeIdList) {
        List<DataScopeConfProperties.DataScopeConf> dataScopeConfList = Lists.newArrayList();
        for (String scopeId : scopeIdList) {
            DataScopeConfProperties.DataScopeConf scopeConf = loadByScopeId(scopeId);
            dataScopeConfList.add(scopeConf);
        }
        return dataScopeConfList;
    }

    @Override
    public List<DataScopeConfProperties.DataScopeConf> loadAll() {
        return new ArrayList<>(configMap.values());
    }
}
