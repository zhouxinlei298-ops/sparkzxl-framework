package com.github.sparkzxl.oss.provider;

import com.github.sparkzxl.oss.properties.Configuration;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * description: jdbc 加载oss 配置信息
 * <p>
 * 配置在应用初始化时加载，后续不再变更
 * </p>
 *
 * @author zhouxinlei
 * @since 2022-05-05 13:56:54
 */
@Slf4j
public class JdbcOssConfigProvider extends AbstractOssConfigProvider {

    private final Function<String, Configuration> function;
    private final Supplier<List<Configuration>> supplier;
    private final List<Configuration> configList;

    public JdbcOssConfigProvider(Function<String, Configuration> function,
            Supplier<List<Configuration>> supplier) {
        this.configList = Lists.newArrayList();
        this.function = function;
        this.supplier = supplier;
    }

    @Override
    public Configuration load(String clientId) {
        // 从缓存中查找
        Optional<Configuration> optional = configList.stream()
                .filter(config -> config.getClientId().equals(clientId))
                .findFirst();
        if (optional.isPresent()) {
            return optional.get();
        }

        // 缓存未命中，从数据库加载
        Configuration configuration = function.apply(clientId);
        configList.add(configuration);
        log.debug("Loaded OSS configuration from database for clientId: {}", clientId);
        return configuration;
    }

    @Override
    protected List<Configuration> list() {
        if (configList.isEmpty()) {
            // 从数据库加载
            List<Configuration> configurations = supplier.get();
            if (configurations != null && !configurations.isEmpty()) {
                configList.addAll(configurations);
            }
            log.debug("Loaded OSS configurations from database, total: {}", configList.size());
        }
        return configList;
    }

    /**
     * 手动刷新配置缓存（如果需要）
     */
    public void refreshCache() {
        configList.clear();
        List<Configuration> configurations = supplier.get();
        if (configurations != null && !configurations.isEmpty()) {
            configList.addAll(configurations);
        }
        log.info("OSS configuration cache refreshed, loaded {} configurations", configList.size());
    }
}
