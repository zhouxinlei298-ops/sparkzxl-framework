package com.github.sparkzxl.datasource.listener;

import cn.hutool.core.util.ClassLoaderUtil;
import com.baomidou.dynamic.datasource.creator.DataSourceProperty;
import com.baomidou.dynamic.datasource.creator.druid.DruidConfig;
import com.baomidou.dynamic.datasource.creator.hikaricp.HikariCpConfig;
import com.baomidou.dynamic.datasource.enums.SeataMode;
import com.baomidou.dynamic.datasource.spring.boot.autoconfigure.DynamicDataSourceProperties;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * description: 动态数据源配置转化类
 *
 * @author zhouxinlei
 * @since 2025-08-22 08:52:22
 */
@SuppressWarnings(value = "all")
public class DynamicDsConverter {

    private static final Pattern MAP_KEY_PATTERN = Pattern.compile("(\\w+)\\[(\\w+)\\](\\.(.*))?");
    private static final String BASE_PREFIX = "spring.datasource.dynamic.";
    private static final String NACOS_CONFIG_SOURCE_NAME = "nacos-dynamic-ds";

    /**
     * 扁平Map转换为DynamicDataSourceProperties（使用三参数bind方法）
     */
    public static DynamicDataSourceProperties convert(Map<String, Object> flatConfigMap) {
        // 1. 扁平Map转换为嵌套Map（解析路径如"datasource[330324].url"）
        Map<String, Object> nestedMap = buildNestedMap(flatConfigMap);
        // 1. 初始化DynamicDataSourceProperties（使用默认构造函数）
        DynamicDataSourceProperties props = new DynamicDataSourceProperties();

        // 2. 设置顶层基础属性（primary、strict、p6spy等）
        setBasicProperties(nestedMap, props);

        // 3. 设置数据源集合（datasource: Map<String, DataSourceProperty>）
        setDataSources(nestedMap, props);

        // 4. 设置Druid配置（druid: DruidConfig）
        setDruidConfig(nestedMap, props);

        // 5. 如需其他子配置（hikari、beecp等），按相同逻辑处理
        setHikariConfig(nestedMap, props);
        // ...

        return props;
    }

    // 以下方法与之前一致：构建嵌套Map
    private static Map<String, Object> buildNestedMap(Map<String, Object> flatConfigMap) {
        Map<String, Object> nestedMap = new HashMap<>();
        for (Map.Entry<String, Object> entry : flatConfigMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.startsWith(BASE_PREFIX)) {
                continue;
            }
            String relativeKey = key.substring(BASE_PREFIX.length());
            parseAndPut(nestedMap, relativeKey, value);
        }
        return nestedMap;
    }

    private static void parseAndPut(Map<String, Object> currentMap, String relativeKey, Object value) {
        Matcher matcher = MAP_KEY_PATTERN.matcher(relativeKey);
        if (matcher.matches()) {
            String mapName = matcher.group(1);
            String mapKey = matcher.group(2);
            String remainingPath = matcher.group(4);
            Map<String, Object> subMap = (Map<String, Object>) currentMap.computeIfAbsent(mapName, k -> new HashMap<>());
            Map<String, Object> keyMap = (Map<String, Object>) subMap.computeIfAbsent(mapKey, k -> new HashMap<>());
            if (remainingPath == null) {
                subMap.put(mapKey, value);
            } else {
                parseAndPut(keyMap, remainingPath, value);
            }
            return;
        }

        String[] segments = relativeKey.split("\\.", 2);
        String firstSegment = segments[0];
        if (segments.length == 1) {
            currentMap.put(firstSegment, value);
        } else {
            Map<String, Object> subMap = (Map<String, Object>) currentMap.computeIfAbsent(firstSegment, k -> new HashMap<>());
            parseAndPut(subMap, segments[1], value);
        }
    }

    /**
     * 短横线命名转驼峰命名（核心工具方法）
     * 例：initial-size → initialSize，max-wait → maxWait
     */
    private static String kebabToCamel(String kebabCase) {
        if (kebabCase == null || !kebabCase.contains("-")) {
            return kebabCase; // 非短横线命名直接返回
        }
        StringBuilder camelCase = new StringBuilder();
        boolean nextUpper = false;
        for (char c : kebabCase.toCharArray()) {
            if (c == '-') {
                nextUpper = true;
            } else {
                if (nextUpper) {
                    camelCase.append(Character.toUpperCase(c));
                    nextUpper = false;
                } else {
                    camelCase.append(c);
                }
            }
        }
        return camelCase.toString();
    }

    /**
     * 设置顶层属性（支持短横线键，如"grace-destroy" → graceDestroy）
     */
    private static void setBasicProperties(Map<String, Object> nestedMap, DynamicDataSourceProperties props) {
        for (Map.Entry<String, Object> entry : nestedMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String camelKey = kebabToCamel(key); // 转换为驼峰键

            switch (camelKey) {
                case "primary":
                    props.setPrimary(String.valueOf(value));
                    break;
                case "strict":
                    props.setStrict(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "p6spy":
                    props.setP6spy(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "seata":
                    props.setSeata(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "lazy":
                    props.setLazy(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "graceDestroy": // 匹配"grace-destroy"转换后的值
                    props.setGraceDestroy(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                case "seataMode":
                    props.setSeataMode(SeataMode.valueOf(String.valueOf(value)));
                    break;
                case "publicKey":
                    props.setPublicKey(String.valueOf(value));
                    break;
                // 忽略datasource、druid等子节点（在专门的方法中处理）
                case "datasource":
                case "druid":
                case "hikari":
                    break;
                default:
                    // 忽略未知属性（或添加日志提示）
                    break;
            }
        }
    }

    /**
     * 设置数据源集合（支持"driver-class-name" → driverClassName等转换）
     */
    private static void setDataSources(Map<String, Object> nestedMap, DynamicDataSourceProperties props) {
        Object datasourceObj = nestedMap.get("datasource");
        if (!(datasourceObj instanceof Map)) {
            throw new RuntimeException("datasource节点格式错误，应为Map类型");
        }
        Map<String, Object> datasourceMap = (Map<String, Object>) datasourceObj;

        Map<String, DataSourceProperty> dataSources = new HashMap<>();
        for (Map.Entry<String, Object> entry : datasourceMap.entrySet()) {
            String dsKey = entry.getKey();
            Object dsConfigObj = entry.getValue();
            if (!(dsConfigObj instanceof Map)) {
                throw new RuntimeException("数据源" + dsKey + "配置格式错误");
            }
            Map<String, Object> dsConfigMap = (Map<String, Object>) dsConfigObj;

            DataSourceProperty dsProperty = new DataSourceProperty();
            for (Map.Entry<String, Object> dsEntry : dsConfigMap.entrySet()) {
                String key = dsEntry.getKey();
                Object value = dsEntry.getValue();
                String camelKey = kebabToCamel(key); // 转换为驼峰键

                switch (camelKey) {
                    case "url":
                        dsProperty.setUrl(String.valueOf(value));
                        break;
                    case "username":
                        dsProperty.setUsername(String.valueOf(value));
                        break;
                    case "password":
                        dsProperty.setPassword(String.valueOf(value));
                        break;
                    case "driverClassName": // 匹配"driver-class-name"转换后的值
                        dsProperty.setDriverClassName(String.valueOf(value));
                        break;
                    // 其他数据源属性（如type、schema等）
                    case "type":
                        Class<? extends DataSource> type = (Class<? extends DataSource>) ClassLoaderUtil.loadClass(String.valueOf(value));
                        dsProperty.setType(type);
                        break;
                    default:
                        // 忽略未知属性
                        break;
                }
            }
            dataSources.put(dsKey, dsProperty);
        }

        props.setDatasource(dataSources);
    }

    /**
     * 设置Druid配置（支持"initial-size" → initialSize等转换）
     */
    private static void setDruidConfig(Map<String, Object> nestedMap, DynamicDataSourceProperties props) {
        Object druidObj = nestedMap.get("druid");
        if (!(druidObj instanceof Map)) {
            return; // 使用默认配置
        }
        Map<String, Object> druidMap = (Map<String, Object>) druidObj;

        DruidConfig druidConfig = new DruidConfig();
        for (Map.Entry<String, Object> entry : druidMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            String camelKey = kebabToCamel(key); // 转换为驼峰键

            // 根据驼峰键设置对应字段（类型转换需严格匹配）
            switch (camelKey) {
                case "initialSize": // 匹配"initial-size"转换后的值
                    druidConfig.setInitialSize(Integer.parseInt(String.valueOf(value)));
                    break;
                case "minIdle": // 匹配"min-idle"转换后的值
                    druidConfig.setMinIdle(Integer.parseInt(String.valueOf(value)));
                    break;
                case "maxActive": // 匹配"max-active"转换后的值
                    druidConfig.setMaxActive(Integer.parseInt(String.valueOf(value)));
                    break;
                case "maxWait": // 匹配"max-wait"转换后的值
                    druidConfig.setMaxWait(Integer.valueOf(String.valueOf(value)));
                    break;
                case "timeBetweenEvictionRunsMillis": // 匹配"time-between-eviction-runs-millis"
                    druidConfig.setTimeBetweenEvictionRunsMillis(Long.parseLong(String.valueOf(value)));
                    break;
                case "validationQuery": // 匹配"validation-query"
                    druidConfig.setValidationQuery(String.valueOf(value));
                    break;
                case "testWhileIdle": // 匹配"test-while-idle"
                    druidConfig.setTestWhileIdle(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                // 其他Druid属性按需添加
                default:
                    // 忽略未知属性
                    break;
            }
        }

        props.setDruid(druidConfig);
    }

    /**
     * 设置Hikari连接池配置（支持短横线命名转驼峰，如"maximum-pool-size"→"maximumPoolSize"）
     */
    private static void setHikariConfig(Map<String, Object> nestedMap, DynamicDataSourceProperties props) {
        // 从嵌套Map中获取"hikari"节点（配置格式：hikari: {maximum-pool-size: 10, ...}）
        Object hikariObj = nestedMap.get("hikari");
        if (!(hikariObj instanceof Map)) {
            return; // 无Hikari配置，使用默认值（DynamicDataSourceProperties构造函数已初始化）
        }
        Map<String, Object> hikariMap = (Map<String, Object>) hikariObj;

        // 初始化Hikari配置对象
        HikariCpConfig hikariConfig = new HikariCpConfig();

        // 遍历Hikari配置项，转换命名并设置属性
        for (Map.Entry<String, Object> entry : hikariMap.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue; // 忽略null值
            }
            String camelKey = kebabToCamel(key); // 短横线转驼峰

            // 根据驼峰键设置对应字段（Hikari的核心配置项）
            switch (camelKey) {
                case "maximumPoolSize": // 匹配"maximum-pool-size"
                    hikariConfig.setMaximumPoolSize(Integer.parseInt(String.valueOf(value)));
                    break;
                case "minimumIdle": // 匹配"minimum-idle"
                    hikariConfig.setMinimumIdle(Integer.parseInt(String.valueOf(value)));
                    break;
                case "connectionTimeout": // 匹配"connection-timeout"
                    hikariConfig.setConnectionTimeout(Long.parseLong(String.valueOf(value)));
                    break;
                case "idleTimeout": // 匹配"idle-timeout"
                    hikariConfig.setIdleTimeout(Long.parseLong(String.valueOf(value)));
                    break;
                case "maxLifetime": // 匹配"max-lifetime"
                    hikariConfig.setMaxLifetime(Long.parseLong(String.valueOf(value)));
                    break;
                case "connectionTestQuery": // 匹配"connection-test-query"
                    hikariConfig.setConnectionTestQuery(String.valueOf(value));
                    break;
                case "autoCommit": // 匹配"auto-commit"
                    hikariConfig.setIsAutoCommit(Boolean.parseBoolean(String.valueOf(value)));
                    break;
                // 其他Hikari属性按需添加（参考HikariCpConfig类的字段）
                default:
                    // 忽略未知属性（可添加日志：log.warn("未知Hikari属性：{}", camelKey)）
                    break;
            }
        }

        // 将设置好的Hikari配置添加到DynamicDataSourceProperties
        props.setHikari(hikariConfig);
    }
}
