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

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2022-10-12 08:42:42
 */
@Slf4j
public class OssExecutorFactoryContext implements ConfigCache, DisposableBean {

    private final Map<String, OssExecutor> executorMap = new ConcurrentHashMap<>();
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
        return selectOssExecutor(configuration);
    }

    public OssExecutor create() {
        Configuration configuration = configProvider.loadConfigurationList().get(0);
        return selectOssExecutor(configuration);
    }

    private OssExecutor selectOssExecutor(Configuration configuration) {
        ArgumentAssert.notNull(configuration, "Oss Configuration is not available");
        String clientType = configuration.getClientType();
        String cacheKey = cacheKey(configuration.getClientType(), configuration.getClientId());
        return executorMap.computeIfAbsent(cacheKey, key -> {
            log.debug("create OssExecutor for cacheKey: {}", key);
            OssClient<?> ossClient = ossClientFactory.buildOssClient(configuration);
            OssExecutorFactory ossExecutorFactory = newInstance(clientType);
            return ossExecutorFactory.create(ossClient);
        });
    }

    @Override
    public String cacheKey(String clientType, String clientId) {
        return clientType.concat("-").concat(clientId);
    }

    @Override
    public void destroy() {
        log.info("OssExecutor 开始关闭 ....");
        executorMap.forEach((key, value) -> {
            try {
                value.shutdown();
            } catch (Exception e) {
                log.error("关闭 OssExecutor 异常 key: {}", key, e);
            }
        });
        executorMap.clear();
        log.info("OssExecutor 全部关闭成功，再见");
    }
}
