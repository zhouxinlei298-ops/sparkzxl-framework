package com.github.sparkzxl.datasource.listener;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.DefaultDataSourceCreator;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import org.apache.commons.lang3.StringUtils;
import com.github.sparkzxl.datasource.context.DataSourcePropertyCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.sql.DataSource;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * description: 数据源更新
 *
 * @author zhouxinlei
 * @since 2025-08-22 08:43:52
 */
@Component
public class DataSourceUpdater {

    private static final Logger logger = LoggerFactory.getLogger(DataSourceUpdater.class);

    /**
     * 对比历史数据源和新数据源，实现差异化更新（新增、修改、删除）
     *
     * @param newProps 新配置
     */
    public void updateDynamicDataSources(DynamicDataSourceProperties newProps) {
        try {
            // 获取当前已存在的动态数据源和创建器
            DynamicRoutingDataSource dynamicDataSource = SpringContextUtils.getBean(DynamicRoutingDataSource.class);
            DataSourcePropertyCache dataSourcePropertyCache = SpringContextUtils.getBean(DataSourcePropertyCache.class);
            DefaultDataSourceCreator dataSourceCreator = SpringContextUtils.getBean(DefaultDataSourceCreator.class);

            // 1. 获取历史数据源信息（键：数据源名称，值：配置快照）
            // 现有数据源名称集合
            Set<String> existingDsKeys = dynamicDataSource.getDataSources().keySet();
            // 现有数据源配置快照
            Map<String, DataSourceProperty> existingDsProps = snapshotExistingDataSourceProps(dataSourcePropertyCache);

            // 2. 获取新数据源信息
            Map<String, DataSourceProperty> newDsProps = newProps.getDatasource();
            if (CollectionUtils.isEmpty(newDsProps)) {
                logger.warn("新数据源配置为空，不执行更新");
                return;
            }
            Set<String> newDsKeys = newDsProps.keySet();

            // 3. 计算需要执行的操作：新增、修改、删除
            // 新增：新有旧无
            Set<String> addKeys = newDsKeys.stream()
                    .filter(key -> !existingDsKeys.contains(key))
                    .collect(Collectors.toSet());

            // 修改：新旧都有但配置不同
            Set<String> updateKeys = newDsKeys.stream()
                    .filter(key -> existingDsKeys.contains(key)
                            && !isDataSourcePropertyEqual(existingDsProps.get(key), newDsProps.get(key)))
                    .collect(Collectors
                            .toSet());
            // 删除：旧有新无
            Set<String> deleteKeys = existingDsKeys.stream()
                    .filter(key -> !newDsKeys.contains(key))
                    .collect(Collectors.toSet());

            // 4. 执行删除操作（先删再增，避免同名冲突）
            for (String dsKey : deleteKeys) {
                dynamicDataSource.removeDataSource(dsKey);
                logger.info("已删除数据源: {}", dsKey);
            }

            // 5. 执行新增操作
            for (String dsKey : addKeys) {
                DataSourceProperty dsProperty = newDsProps.get(dsKey);
                DataSource dataSource = dataSourceCreator.createDataSource(dsProperty);
                dynamicDataSource.addDataSource(dsKey, dataSource);
                logger.info("已新增数据源: {}", dsKey);
            }

            // 6. 执行修改操作（先删旧的，再加新的）
            for (String dsKey : updateKeys) {
                // 移除旧数据源
                dynamicDataSource.removeDataSource(dsKey);
                DataSourceProperty newDsProperty = newDsProps.get(dsKey);
                DataSource newDataSource = dataSourceCreator.createDataSource(newDsProperty);
                // 添加新数据源
                dynamicDataSource.addDataSource(dsKey, newDataSource);
                logger.info("已更新数据源: {}", dsKey);
            }
            logger.info("动态数据源更新完成 | 新增: {} 个, 修改: {} 个, 删除: {} 个",
                    addKeys.size(), updateKeys.size(), deleteKeys.size());
            dataSourcePropertyCache.setDataSourcePropertyMap(newDsProps);
        } catch (Exception e) {
            logger.error("更新动态数据源失败", e);
            // 可选：出现异常时回滚操作（如恢复删除的数据源）
        }
    }

    /**
     * 快照现有数据源的配置（用于对比）
     * 注意：这里需要根据实际存储的配置获取，若框架未提供，可通过数据源元数据反向解析
     *
     * @param dataSourcePropertyCache 数据源属性缓存
     * @return Map<String, DataSourceProperty>
     */
    private Map<String, DataSourceProperty> snapshotExistingDataSourceProps(DataSourcePropertyCache dataSourcePropertyCache) {
        return dataSourcePropertyCache.getDataSourcePropertyMap();

    }

    /***
     * 对比两个数据源配置是否相同（仅对比关键字段）
     * @param oldProp 旧数据源配置
     * @param newProp 新数据源配置
     * @return boolean
     */
    private boolean isDataSourcePropertyEqual(DataSourceProperty oldProp, DataSourceProperty newProp) {
        if (oldProp == null && newProp == null) {
            return true;
        }
        if (oldProp == null || newProp == null) {
            return false;
        }
        // 对比关键配置字段（根据业务需求调整）
        return StringUtils.equals(oldProp.getUrl(), newProp.getUrl())
                && StringUtils.equals(oldProp.getUsername(), newProp.getUsername())
                && StringUtils.equals(oldProp.getPassword(), newProp.getPassword())
                && StringUtils.equals(oldProp.getDriverClassName(), newProp.getDriverClassName());
    }
}
