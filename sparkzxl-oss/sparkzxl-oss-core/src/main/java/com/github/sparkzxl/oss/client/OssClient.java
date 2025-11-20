package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: Oss Client
 *
 * @author zhouxinlei
 * @since 2022-10-12 16:21:43
 */
public interface OssClient<T> {

    /**
     * 获取client
     *
     * @return T
     */
    T getClient();

    /**
     * 获取配置
     *
     * @return Configuration
     */
    Configuration getConfiguration();

}
