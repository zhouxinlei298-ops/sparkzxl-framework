package com.github.sparkzxl.datasource.listener;

import cn.hutool.core.convert.Convert;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-08-21 15:16:30
 */
@Slf4j
public class DataSourceConfigOnChange implements OnChange {

    private static final String NACOS_CONFIG_SOURCE_NAME = "nacos-dynamic-ds-config";

    private final DataSourceUpdater dataSourceUpdater;

    public DataSourceConfigOnChange(DataSourceUpdater dataSourceUpdater) {
        this.dataSourceUpdater = dataSourceUpdater;
    }

    @Override
    public void change(boolean initialized, String configInfo) {
        log.info("接收到Nacos数据源配置变更，是否初始化加载:{}", initialized);
        if (configInfo == null || configInfo.trim().isEmpty()) {
            log.warn("配置为空，忽略解析");
            return;
        }
        if (initialized) {
            return;
        }

        try {
            // 1. 将配置字符串转换为Spring Resource
            Resource resource = new ByteArrayResource(configInfo.getBytes(StandardCharsets.UTF_8));

            // 2. 解析YAML配置为PropertySource
            YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
            List<org.springframework.core.env.PropertySource<?>> propertySources = loader.load(NACOS_CONFIG_SOURCE_NAME, resource);
            if (propertySources.isEmpty()) {
                log.warn("Nacos配置解析结果为空，忽略处理");
                return;
            }

            // 3. 提取配置Map（底层为Map<String, Object>）
            org.springframework.core.env.PropertySource<?> propertySource = propertySources.get(0);
            Object source = propertySource.getSource();
            if (!(source instanceof Map)) {
                log.error("Nacos配置格式错误，不是Map类型");
                return;
            }
            Map<String, Object> configMap = Convert.toMap(String.class, Object.class, source);

            // 4. 提取dynamic节点配置（兼容有无spring.datasource.dynamic前缀）
            Map<String, Object> dynamicDsMap = Convert.toMap(String.class, Object.class, configMap.get("spring.datasource.dynamic"));
            if (dynamicDsMap == null) {
                dynamicDsMap = configMap;
            }

            DynamicDataSourceProperties dynamicDataSourceProperties = DynamicDsConverter.convert(dynamicDsMap);
            // 7. 更新动态数据源
            dataSourceUpdater.updateDynamicDataSources(dynamicDataSourceProperties);
        } catch (IOException e) {
            log.error("Nacos配置解析失败", e);
        } catch (Exception e) {
            log.error("处理Nacos配置变更时发生异常", e);
        }
    }
}
