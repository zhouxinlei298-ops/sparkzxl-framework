package com.github.sparkzxl.oss.provider;

import com.github.sparkzxl.oss.operation.OssOperationContextResolver;
import com.github.sparkzxl.oss.properties.Configuration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * 通过业务查询函数读取与刷新配置缓存，拒绝缺失或标识不一致的目标。
 *
 * <p>查询和事务由消费方管理，本类型不直接访问JDBC或开启数据库事务。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 */
public class JdbcOssConfigProvider extends AbstractOssConfigProvider {

    private final Function<String, Configuration> function;

    private final Supplier<List<Configuration>> supplier;

    private Map<String, Configuration> cache = new LinkedHashMap<>();

    private boolean initialized;

    /**
     * 注册显式客户端配置读取函数，建立可整体替换的数据库配置缓存。
     *
     * @param function 按客户端标识读取配置的业务查询函数
     * @param supplier 读取全部配置的业务查询函数
     */
    public JdbcOssConfigProvider(
            Function<String, Configuration> function, Supplier<List<Configuration>> supplier) {
        if (function == null || supplier == null) {
            throw new IllegalArgumentException("OSS configuration query callbacks are required");
        }
        this.function = function;
        this.supplier = supplier;
    }

    /**
     * 读取显式目标的防御性配置副本。
     *
     * @param clientId 固定客户端标识
     * @return 目标配置副本；未知标识返回 null
     */
    @Override
    public synchronized Configuration load(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Explicit OSS clientId is required");
        }
        Configuration c = cache.get(clientId);
        if (c == null) {
            c = function.apply(clientId);
            if (c == null) {
                return null;
            }
            c = c.copy();
            OssOperationContextResolver.resolve(clientId, c);
            cache.put(clientId, c);
        }
        return c.copy();
    }

    @Override
    protected synchronized List<Configuration> list() {
        if (!initialized) {
            refreshCache();
        }
        List<Configuration> result = new ArrayList<>();
        for (Configuration c : cache.values()) {
            result.add(c.copy());
        }
        return result;
    }

    /**
     * 完整校验数据库配置后替换缓存，失败保留原缓存。
     */
    public synchronized void refreshCache() {
        List<Configuration> loaded = supplier.get();
        if (loaded == null) {
            throw new IllegalStateException("OSS provider returned no snapshot");
        }
        Map<String, Configuration> next = new LinkedHashMap<>();
        for (Configuration source : loaded) {
            if (source == null) {
                throw new IllegalArgumentException("Null OSS configuration");
            }
            Configuration c = source.copy();
            OssOperationContextResolver.resolve(c.getClientId(), c);
            if (next.put(c.getClientId(), c) != null) {
                throw new IllegalArgumentException("Duplicate OSS clientId");
            }
        }
        cache = next;
        initialized = true;
    }
}
