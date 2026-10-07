package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.oss.ConfigCache;
import com.github.sparkzxl.oss.OssClientType;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.creator.OssClientFactory;
import com.github.sparkzxl.oss.operation.OssOperationContext;
import com.github.sparkzxl.oss.operation.OssOperationContextManager;
import com.github.sparkzxl.oss.operation.OssOperationContextResolver;
import com.github.sparkzxl.oss.operation.OssOperationHandle;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.provider.OssConfigProvider;
import com.github.sparkzxl.spi.ExtensionLoader;

import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 维护显式客户端配置快照及在途操作引用，延迟关闭退休代际。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see java.lang.AutoCloseable
 */
public class OssExecutorFactoryContext implements ConfigCache, DisposableBean {

    private final OssConfigProvider provider;

    private final OssClientFactory clients;

    private Map<String, Generation> active = new HashMap<>();

    private Map<String, Configuration> configurationsSnapshot;

    private long snapshotVersion;

    private boolean stopped;

    private boolean snapshotRequired;

    /**
     * 注册供应商创建器与配置提供器，初始化独立客户端代际管理。
     *
     * @param provider 配置快照提供器
     * @param clients 已注册的客户端创建器
     */
    public OssExecutorFactoryContext(OssConfigProvider provider, OssClientFactory clients) {
        this.provider = provider;
        this.clients = clients;
    }

    /**
     * 使用指定配置提供器建立客户端代际工厂。
     *
     * @param type 目标供应商类型
     * @return 独立的客户端代际工厂
     */
    public static OssExecutorFactory newInstance(String type) {
        return ExtensionLoader.getExtensionLoader(OssExecutorFactory.class)
                .getJoin(OssClientType.of(type).value());
    }

    /** 要求宿主显式提供完整配置后才能建立操作域，禁止回退到配置提供器。 */
    public synchronized void requireConfigurationSnapshot() {
        if (configurationsSnapshot != null || !active.isEmpty()) {
            throw new IllegalStateException(
                    "Snapshot mode must be configured before opening OSS operations");
        }
        this.snapshotRequired = true;
    }

    /**
     * 按供应商类型创建执行器，创建失败时关闭已建立的客户端。
     *
     * @param config 已校验的客户端配置
     * @return 持有独立客户端的供应商执行器
     */
    protected OssExecutor buildExecutor(Configuration config) {
        OssExecutorFactory factory = newInstance(config.getClientType());
        if (factory == null) {
            throw new IllegalArgumentException("Unsupported OSS client type");
        }
        OssClient<?> client = clients.buildOssClient(config.copy());
        try {
            return factory.create(client);
        } catch (RuntimeException | Error e) {
            client.close();
            throw e;
        }
    }

    /**
     * 建立显式操作域；同工厂同目标嵌套复用在途句柄。
     *
     * @param clientId 固定客户端标识
     * @return 持有固定配置与客户端代际的可关闭操作域
     */
    public synchronized OssOperationHandle open(String clientId) {
        if (stopped) {
            throw new IllegalStateException("OSS runtime is stopped");
        }
        if (snapshotRequired && configurationsSnapshot == null) {
            throw new IllegalStateException("No OSS configuration snapshot");
        }
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalStateException("Explicit OSS clientId is required");
        }
        Configuration loaded =
                configurationsSnapshot == null
                        ? provider.load(clientId)
                        : configurationsSnapshot.get(clientId);
        if (loaded == null) {
            throw new IllegalStateException("OSS target not configured");
        }
        Configuration config = loaded.copy();
        OssOperationContext context = OssOperationContextResolver.resolve(clientId, config);
        Generation old = active.get(clientId);
        if (old != null && config.getRevision() < old.config.getRevision()) {
            throw new IllegalStateException("OSS configuration revision regressed");
        }
        if (old != null
                && config.getRevision() == old.config.getRevision()
                && !config.equals(old.config)) {
            throw new IllegalStateException("OSS configuration changed without a new revision");
        }
        Generation generation = old;
        if (old == null || config.getRevision() != old.config.getRevision()) {
            generation = new Generation(config, buildExecutor(config));
            active.put(clientId, generation);
            if (old != null) {
                retire(old);
            }
        }
        generation.leases++;
        Generation leased = generation;
        return new OssOperationHandle(
                context, generation.config, generation.executor, () -> release(leased));
    }

    /**
     * 原子刷新完整客户端配置，保留在途客户端至操作引用释放。 空集合将移除全部目标；宿主负责配置来源、凭据解析与访问授权。
     *
     * @param version 单调递增的配置快照版本
     * @param configurations 待原子替换的完整配置集合
     * @return 新版本已生效返回 true；重复或旧版本返回 false
     */
    public synchronized boolean refresh(long version, List<Configuration> configurations) {
        if (stopped) {
            throw new IllegalStateException("OSS runtime is stopped");
        }
        if (version <= 0 || configurations == null) {
            throw new IllegalArgumentException("Invalid OSS configuration snapshot");
        }
        if (version < snapshotVersion) {
            return false;
        }
        Map<String, Configuration> next = new LinkedHashMap<>();
        for (Configuration source : configurations) {
            if (source == null) {
                throw new IllegalArgumentException("Null OSS configuration");
            }
            Configuration c = source.copy();
            OssOperationContextResolver.resolve(c.getClientId(), c);
            if (next.put(c.getClientId(), c) != null) {
                throw new IllegalArgumentException("Duplicate OSS clientId");
            }
        }
        if (version == snapshotVersion) {
            if (!next.equals(configurationsSnapshot)) {
                throw new IllegalStateException("Conflicting OSS configuration snapshot version");
            }
            return false;
        }
        Map<String, Generation> candidates = new HashMap<>();
        List<Generation> created = new ArrayList<>();
        try {
            for (Configuration c : next.values()) {
                Generation old = active.get(c.getClientId());
                if (old != null && c.getRevision() < old.config.getRevision()) {
                    throw new IllegalStateException("OSS revision regressed");
                }
                if (old != null && c.getRevision() == old.config.getRevision()) {
                    if (!c.equals(old.config)) {
                        throw new IllegalStateException("OSS revision content conflicts");
                    }
                    candidates.put(c.getClientId(), old);
                } else {
                    Generation g = new Generation(c, buildExecutor(c));
                    created.add(g);
                    candidates.put(c.getClientId(), g);
                }
            }
        } catch (RuntimeException | Error e) {
            for (Generation g : created) {
                retire(g);
            }
            throw e;
        }
        Map<String, Generation> previous = active;
        active = candidates;
        configurationsSnapshot = Collections.unmodifiableMap(next);
        snapshotVersion = version;
        for (Generation g : previous.values()) {
            if (!candidates.containsValue(g)) {
                retire(g);
            }
        }
        return true;
    }

    private synchronized void release(Generation g) {
        if (g.leases <= 0) {
            throw new IllegalStateException("Invalid OSS lease count");
        }
        g.leases--;
        if (g.retired && g.leases == 0) {
            closeRetired(g);
        }
    }

    private void retire(Generation g) {
        g.retired = true;
        if (g.leases == 0) {
            closeRetired(g);
        }
    }

    private void closeRetired(Generation g) {
        if (g.closed) {
            return;
        }
        g.closed = true;
        try {
            g.executor.shutdown();
        } catch (RuntimeException e) {
            LoggerFactory.getLogger(OssExecutorFactoryContext.class)
                    .warn("OSS retired client close failed: {}", e.getClass().getSimpleName());
        }
    }

    /**
     * 兼容入口只能消费已绑定handle，不能返回无租约执行器。
     *
     * @param clientId 固定客户端标识
     * @return 当前显式操作域持有的固定执行器
     */
    @Deprecated
    public OssExecutor create(String clientId) {
        OssOperationHandle h = OssOperationContextManager.requiredHandle();
        if (!h.getContext().getClientId().equals(clientId)) {
            throw new IllegalStateException("OSS target conflicts");
        }
        return h.executor();
    }

    /**
     * 获取显式绑定目标的执行器；缺少目标时拒绝访问。
     *
     * @return 当前显式操作域持有的固定执行器
     */
    @Deprecated
    public OssExecutor create() {
        throw new IllegalStateException("Explicit OSS clientId is required");
    }

    /**
     * 构造显式客户端类型与标识组成的缓存键。
     *
     * @param type 目标供应商类型
     * @param clientId 固定客户端标识
     * @return 客户端类型与固定标识构成的缓存键
     */
    @Override
    public String cacheKey(String type, String clientId) {
        return type + ":" + clientId;
    }

    /** 停止建立新操作并退休所有客户端代际。 */
    @Override
    public synchronized void destroy() {
        if (stopped) {
            return;
        }
        stopped = true;
        for (Generation g : active.values()) {
            retire(g);
        }
        active.clear();
    }

    /**
     * 固定配置及执行器的代际状态，跟踪活动租约与退役释放。
     *
     * @author zhouxinlei
     * @see java.lang.AutoCloseable
     * @since 2026-10-06
     */
    private static final class Generation {

        final Configuration config;

        final OssExecutor executor;

        int leases;

        boolean retired;

        boolean closed;

        Generation(Configuration config, OssExecutor executor) {
            this.config = config;
            this.executor = executor;
        }
    }
}
