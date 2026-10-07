package com.github.sparkzxl.oss.creator;

import com.github.sparkzxl.oss.OssClientType;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;
import com.google.common.collect.Maps;

import org.apache.commons.collections4.CollectionUtils;

import java.util.List;
import java.util.Map;

/**
 * OssClientFactory
 *
 * @author zhouxinlei
 * @since 2025-11-20 08:34:23
 */
public class OssClientFactory {

    // 缓存：clientType -> 对应的创建器（初始化时加载）
    private final Map<OssClientType, OssClientCreator> creatorMap = Maps.newConcurrentMap();

    /**
     * 注册当前消费方提供的客户端创建器。
     *
     * @param ossClientCreators 创建器集合；同类型不能由不同实例重复注册
     */
    public OssClientFactory(List<OssClientCreator> ossClientCreators) {
        if (CollectionUtils.isNotEmpty(ossClientCreators)) {
            for (OssClientCreator ossClientCreator : ossClientCreators) {
                registerCreator(ossClientCreator);
            }
        }
    }

    /**
     * 手动注册创建器；同一实例可重复注册，冲突实例必须拒绝。
     *
     * @param creator 供应商客户端创建器
     */
    public void registerCreator(OssClientCreator creator) {
        if (creator == null) {
            throw new IllegalArgumentException("OSS creator is required");
        }
        OssClientType type = creator.clientType();
        if (type == null || !type.equals(OssClientType.of(creator.supportClientType()))) {
            throw new IllegalArgumentException("OSS creator type must match its SPI identifier");
        }
        synchronized (creatorMap) {
            OssClientCreator previous = creatorMap.get(type);
            if (previous != null && previous != creator) {
                throw new IllegalStateException("Duplicate OSS client creator: " + type.value());
            }
            creatorMap.put(type, creator);
        }
    }

    /**
     * 根据配置创建对应供应商的新客户端。
     *
     * @param configuration 固定操作配置
     * @return 对应供应商的新客户端
     */
    public OssClient<?> buildOssClient(Configuration configuration) {
        // 1. 校验配置
        if (configuration == null) {
            throw new IllegalArgumentException("OSS配置不能为空");
        }
        OssClientType clientType = OssClientType.of(configuration.getClientType());
        // 2. 找到对应的创建器
        OssClientCreator creator = creatorMap.get(clientType);
        if (creator == null) {
            throw new UnsupportedOperationException(
                    "不支持的OSS客户端类型：" + clientType + "，已支持类型：" + creatorMap.keySet());
        }
        configuration.validateClientConfiguration();
        // 3. 创建新实例
        return creator.createClient(configuration);
    }
}
