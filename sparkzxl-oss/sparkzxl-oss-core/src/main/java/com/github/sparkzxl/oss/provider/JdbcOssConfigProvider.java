package com.github.sparkzxl.oss.provider;

import com.github.sparkzxl.oss.properties.Configuration;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * description: jdbc 加载oss 配置信息，支持缓存刷新
 *
 * @author zhouxinlei
 * @since 2022-05-05 13:56:54
 */
@Slf4j
public class JdbcOssConfigProvider extends AbstractOssConfigProvider {

    private final Function<String, Configuration> function;
    private final Supplier<List<Configuration>> supplier;
    private final List<Configuration> configList;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // 缓存刷新时间间隔（毫秒），默认 5 分钟
    private long cacheRefreshIntervalMs = 5 * 60 * 1000L;
    private volatile long lastRefreshTime = System.currentTimeMillis();

    public JdbcOssConfigProvider(Function<String, Configuration> function,
            Supplier<List<Configuration>> supplier) {
        this.configList = Lists.newArrayList();
        this.function = function;
        this.supplier = supplier;
    }

    @Override
    public Configuration load(String clientId) {
        // 检查是否需要刷新缓存
        checkAndRefreshCacheIfNeeded();

        lock.readLock().lock();
        try {
            Optional<Configuration> optional = configList.stream().filter(config -> config.getClientId().equals(clientId)).findFirst();
            if (optional.isPresent()) {
                return optional.get();
            }
        } finally {
            lock.readLock().unlock();
        }

        // 不在缓存中，从数据库加载
        lock.writeLock().lock();
        try {
            // 双重检查
            Optional<Configuration> optional = configList.stream().filter(config -> config.getClientId().equals(clientId)).findFirst();
            if (optional.isPresent()) {
                return optional.get();
            }

            Configuration configuration = function.apply(clientId);
            configList.add(configuration);
            log.debug("Loaded OSS configuration from database for clientId: {}", clientId);
            return configuration;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    protected List<Configuration> list() {
        // 检查是否需要刷新缓存
        checkAndRefreshCacheIfNeeded();

        lock.readLock().lock();
        try {
            if (configList.isEmpty()) {
                // 缓存为空，从数据库加载
                return loadFromDatabase();
            }
            return configList;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * 刷新配置缓存
     * 清除当前缓存并从数据库重新加载所有配置
     */
    public void refreshCache() {
        lock.writeLock().lock();
        try {
            configList.clear();
            List<Configuration> configurations = supplier.get();
            if (configurations != null && !configurations.isEmpty()) {
                configList.addAll(configurations);
            }
            lastRefreshTime = System.currentTimeMillis();
            log.info("OSS configuration cache refreshed, loaded {} configurations", configList.size());
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 清除特定 clientId 的缓存
     *
     * @param clientId 客户端 ID
     */
    public void evictCache(String clientId) {
        lock.writeLock().lock();
        try {
            configList.removeIf(config -> config.getClientId().equals(clientId));
            log.debug("Evicted OSS configuration cache for clientId: {}", clientId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 设置缓存刷新间隔
     *
     * @param intervalMs 刷新间隔（毫秒）
     */
    public void setCacheRefreshInterval(long intervalMs) {
        if (intervalMs > 0) {
            this.cacheRefreshIntervalMs = intervalMs;
            log.info("OSS configuration cache refresh interval set to {} ms", intervalMs);
        }
    }

    /**
     * 检查并刷新缓存（如果需要）
     */
    private void checkAndRefreshCacheIfNeeded() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastRefreshTime > cacheRefreshIntervalMs) {
            // 使用 tryLock 避免阻塞
            if (lock.writeLock().tryLock()) {
                try {
                    // 再次检查时间（可能其他线程已经刷新了）
                    if (currentTime - lastRefreshTime > cacheRefreshIntervalMs) {
                        configList.clear();
                        List<Configuration> configurations = supplier.get();
                        if (configurations != null && !configurations.isEmpty()) {
                            configList.addAll(configurations);
                        }
                        lastRefreshTime = System.currentTimeMillis();
                        log.debug("Auto-refreshed OSS configuration cache, loaded {} configurations", configList.size());
                    }
                } finally {
                    lock.writeLock().unlock();
                }
            }
        }
    }

    /**
     * 从数据库加载配置
     *
     * @return 配置列表
     */
    private List<Configuration> loadFromDatabase() {
        lock.writeLock().lock();
        try {
            List<Configuration> configurations = supplier.get();
            if (configurations != null && !configurations.isEmpty()) {
                configList.addAll(configurations);
            }
            lastRefreshTime = System.currentTimeMillis();
            log.debug("Loaded OSS configurations from database, total: {}", configList.size());
            return configList;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
