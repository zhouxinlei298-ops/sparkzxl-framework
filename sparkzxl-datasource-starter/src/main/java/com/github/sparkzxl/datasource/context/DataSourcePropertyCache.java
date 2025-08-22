package com.github.sparkzxl.datasource.context;

import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.google.common.collect.Maps;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * description: 数据源属性缓存
 *
 * @author zhouxinlei
 * @since 2025-08-22 09:52:59
 */
@Component
public class DataSourcePropertyCache {

    private final Map<String, DataSourceProperty> DATA_SOURCE_PROPERTY_MAP = Maps.newConcurrentMap();

    public Map<String, DataSourceProperty> getDataSourcePropertyMap() {
        return DATA_SOURCE_PROPERTY_MAP;
    }

    public void setDataSourcePropertyMap(Map<String, DataSourceProperty> dataSourcePropertyMap) {
        DATA_SOURCE_PROPERTY_MAP.clear();
        DATA_SOURCE_PROPERTY_MAP.putAll(dataSourcePropertyMap);
    }

    public void putAll(Map<String, DataSourceProperty> dataSourcePropertyMap) {
        DATA_SOURCE_PROPERTY_MAP.putAll(dataSourcePropertyMap);
    }

    public DataSourceProperty getDataSourceProperty(String key) {
        return DATA_SOURCE_PROPERTY_MAP.get(key);
    }

    public void put(String key, DataSourceProperty dataSourceProperty) {
        DATA_SOURCE_PROPERTY_MAP.put(key, dataSourceProperty);
    }

    public void remove(String key) {
        DATA_SOURCE_PROPERTY_MAP.remove(key);
    }

    public void clear() {
        DATA_SOURCE_PROPERTY_MAP.clear();
    }
}
