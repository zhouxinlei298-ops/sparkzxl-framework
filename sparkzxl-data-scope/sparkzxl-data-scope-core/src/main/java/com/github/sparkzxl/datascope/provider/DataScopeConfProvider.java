package com.github.sparkzxl.datascope.provider;

import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;

/**
 * description: 数据权限配置提供类
 *
 * @author zhouxinlei
 * @since 2023-12-20 11:11:13
 */
public interface DataScopeConfProvider {

    /**
     * 加载数据权限配置
     *
     * @param scopeId 数据权限ID
     * @return DataScopeConfig
     */
    DataScopeConfProperties.DataScopeConf loadByScopeId(String scopeId);

    /**
     * 加载数据权限配置列表
     *
     * @param scopeIdList 数据权限ID列表
     * @return List<DataScopeConf>
     */
    List<DataScopeConfProperties.DataScopeConf> loadByScopeIdList(List<String> scopeIdList);

    /**
     * 加载数据权限配置列表
     *
     * @return List<DataScopeConf>
     */
    List<DataScopeConfProperties.DataScopeConf> loadAll();
}
