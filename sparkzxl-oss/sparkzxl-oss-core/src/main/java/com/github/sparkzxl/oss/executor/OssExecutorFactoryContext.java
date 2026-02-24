package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.oss.ConfigCache;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.creator.OssClientFactory;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.provider.OssConfigProvider;
import com.github.sparkzxl.spi.ExtensionLoader;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * description: OssExecutor 工厂上下文，支持缓存管理
 *
 * @author zhouxinlei
 * @since 2022-10-12 08:42:42
 */
@Slf4j
public class OssExecutorFactoryContext implements ConfigCache, DisposableBean {

    /**
     * 缓存条目包装类，支持过期时间检查
     */
    private static class CacheEntry {
        private final OssExecutor executor;
        private final long createTime;
        private volatile long lastAccessTime;

        CacheEntry(OssExecutor executor) {
            this.executor = executor;
            this.createTime = System.currentTimeMillis();
            this.lastAccessTime = this.createTime;
        }

        OssExecutor getExecutor() {
            this.lastAccessTime = System.currentTimeMillis();
            return executor;
        }

        long getCreateTime() {
            return createTime;
        }

        long getLastAccessTime() {
            return lastAccessTime;
        }
    }

    // 缓存最大空闲时间（毫秒），默认 30 分钟
    private static final long MAX_IDLE_TIME_MS = 30 * 60 * 1000L;

    private final Map<String, CacheEntry> executorMap = new ConcurrentHashMap<>();
    private final OssConfigProvider configProvider;
    private final OssClientFactory ossClientFactory;

    public OssExecutorFactoryContext(OssConfigProvider configProvider, OssClientFactory ossClientFactory) {
        this.configProvider = configProvider;
        this.ossClientFactory = ossClientFactory;
    }

    public static OssExecutorFactory newInstance(final String ossType) {
        return ExtensionLoader.getExtensionLoader(OssExecutorFactory.class).getJoin(ossType);
    }

    public OssExecutor create(String clientId) {
        Configuration configuration = configProvider.load(clientId);
        if (configuration == null) {
            throw new IllegalStateException(
                    String.format("OSS configuration not found for clientId [%s]. " +
                            "Please check your configuration (yaml/file/database) to ensure the client is properly configured.", clientId));
        }
        return selectOssExecutor(configuration);
    }

    public OssExecutor create() {
        List<Configuration> configurations = configProvider.loadConfigurationList();
        if (configurations == null || configurations.isEmpty()) {
            throw new IllegalStateException(
                    "No OSS configuration found. " +
                    "Please check your configuration (yaml/file/database) to ensure at least one OSS client is properly configured.");
        }
        Configuration configuration = configurations.get(0);
        return selectOssExecutor(configuration);
    }

    private OssExecutor selectOssExecutor(Configuration configuration) {
        ArgumentAssert.notNull(configuration, "Oss Configuration is not available");
        String clientType = configuration.getClientType();
        String cacheKey = cacheKey(configuration.getClientType(), configuration.getClientId());

        // 先检查缓存，避免重复创建
        CacheEntry cacheEntry = executorMap.get(cacheKey);
        if (cacheEntry != null && !isEntryExpired(cacheEntry)) {
            return cacheEntry.getExecutor();
        }

        // 如果条目已过期，移除它
        if (cacheEntry != null) {
            executorMap.remove(cacheKey);
            try {
                cacheEntry.getExecutor().shutdown();
                log.debug("Removed expired OssExecutor cache entry for cacheKey: {}", cacheKey);
            } catch (Exception e) {
                log.warn("Error while shutting down expired executor for cacheKey: {}", cacheKey, e);
            }
        }

        // 使用 synchronized 确保只有一个线程执行创建逻辑
        synchronized (this) {
            // 双重检查锁定
            cacheEntry = executorMap.get(cacheKey);
            if (cacheEntry != null && !isEntryExpired(cacheEntry)) {
                return cacheEntry.getExecutor();
            }

            log.debug("create OssExecutor for clientId: {}, clientType: {}, endpoint: {}",
                    configuration.getClientId(),
                    configuration.getClientType(),
                    maskSensitiveUrl(configuration.getEndpoint()));
            OssClient<?> ossClient = null;
            try {
                // 步骤1: 创建 OssClient
                ossClient = ossClientFactory.buildOssClient(configuration);

                // 步骤2: 获取 ExecutorFactory
                OssExecutorFactory ossExecutorFactory = newInstance(clientType);
                if (ossExecutorFactory == null) {
                    throw new IllegalStateException(
                            String.format("Cannot find OssExecutorFactory for clientType [%s]", clientType));
                }

                // 步骤3: 创建 Executor
                OssExecutor executor = ossExecutorFactory.create(ossClient);

                // 步骤4: 成功后放入缓存
                executorMap.put(cacheKey, new CacheEntry(executor));
                return executor;

            } catch (Exception e) {
                // 创建失败时，清理已创建的资源
                if (ossClient != null) {
                    try {
                        ossClient.close();
                        log.warn("Failed to create OssExecutor, closed OssClient for clientId: {}", configuration.getClientId());
                    } catch (Exception closeException) {
                        log.error("Error while closing OssClient after creation failure for clientId: {}", configuration.getClientId(), closeException);
                    }
                }
                log.error("Failed to create OssExecutor for clientId: {}, clientType: {}, error: {}",
                        configuration.getClientId(), configuration.getClientType(), e.getMessage());
                throw new IllegalStateException(
                        String.format("Failed to create OssExecutor for cacheKey [%s], clientType [%s]", cacheKey, clientType), e);
            }
        }
    }

    /**
     * 检查缓存条目是否过期
     *
     * @param entry 缓存条目
     * @return true if expired, false otherwise
     */
    private boolean isEntryExpired(CacheEntry entry) {
        long idleTime = System.currentTimeMillis() - entry.getLastAccessTime();
        return idleTime > MAX_IDLE_TIME_MS;
    }

    @Override
    public String cacheKey(String clientType, String clientId) {
        return clientType.concat("-").concat(clientId);
    }

    /**
     * 对敏感 URL 进行脱敏处理
     *
     * @param url 原始 URL
     * @return 脱敏后的 URL
     */
    private String maskSensitiveUrl(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        // 如果 URL 包含 accessKey 或 secretKey 参数，进行脱敏
        int urlLength = url.length();
        if (urlLength > 100) {
            return url.substring(0, 50) + "..." + url.substring(urlLength - 30);
        }
        return url;
    }

    @Override
    public void destroy() {
        log.info("OssExecutor 开始关闭 ....");
        executorMap.forEach((key, entry) -> {
            try {
                entry.getExecutor().shutdown();
            } catch (Exception e) {
                log.error("关闭 OssExecutor 异常 key: {}", key, e);
            }
        });
        executorMap.clear();
        log.info("OssExecutor 全部关闭成功，再见");
    }
}

