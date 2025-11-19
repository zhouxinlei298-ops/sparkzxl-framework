package com.github.sparkzxl.oss;

/**
 * description: 配置缓存
 *
 * @author zhouxinlei
 * @since 2025-11-19 14:37:38
 */
public interface ConfigCache {

    String cacheKey(String clientType, String clientId);
}
