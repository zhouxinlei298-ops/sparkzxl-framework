package com.github.sparkzxl.datascope.rule;

import com.github.sparkzxl.datascope.properties.DataScopeConfProperties;

import java.util.List;

/**
 * description: 数据权限规则
 *
 * @author zhouxinlei
 * @since 2024-01-29 11:14:12
 */
public interface DataScopeRule {

    /**
     * 查询配置列表
     *
     * @param dataScopeConfList 数据权限配置列表
     * @return List<DataScopeConf>
     */
    List<DataScopeConfProperties.DataScopeConf> chooseConfList(List<DataScopeConfProperties.DataScopeConf> dataScopeConfList);

    /**
     * 规则类型
     *
     * @return String
     */
    String getType();


}
