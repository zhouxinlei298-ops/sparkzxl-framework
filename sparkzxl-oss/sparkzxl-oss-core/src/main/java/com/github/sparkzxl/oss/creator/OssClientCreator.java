package com.github.sparkzxl.oss.creator;

import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;

/**
 * description: OSS客户端创建器接口：每个OSS类型实现该接口，负责创建自身实例
 *
 * @author zhouxinlei
 * @since 2025-11-19 17:12:36
 */
public interface OssClientCreator {

    /**
     * 支持的clientType
     *
     * @return String
     */
    String supportClientType();

    /**
     * 创建新的客户端实例
     *
     * @return OssClient
     */
    OssClient<?> createClient(Configuration configuration);
}
