package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.properties.Configuration;

import lombok.Getter;

/**
 * 不含客户端与凭据的不可变操作目标。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class OssOperationContext {

    /** 固定客户端标识。 */
    @Getter private final String clientId;

    /** 存储供应商类型。 */
    @Getter private final String clientType;

    /** Bucket 名称。 */
    @Getter private final String bucketName;

    /** 允许的对象键前缀。 */
    @Getter private final String objectPrefix;

    /** 配置版本。 */
    @Getter private final long revision;

    /** 存储位置版本。 */
    @Getter private final long locationRevision;

    OssOperationContext(Configuration c) {
        clientId = c.getClientId();
        clientType = c.getClientType();
        bucketName = c.getBucketName();
        objectPrefix = c.getObjectPrefix();
        revision = c.getRevision();
        locationRevision = c.getLocationRevision();
    }

    /**
     * 校验对象所属 Bucket 与前缀，拒绝跨目标访问。
     *
     * @param bucket 操作目标 Bucket
     * @param object 目标前缀内的对象键
     */
    public void assertObject(String bucket, String object) {
        if (!bucketName.equals(bucket)) {
            throw new IllegalArgumentException("OSS bucket conflicts with operation target");
        }
        if (object == null
                || object.isEmpty()
                || object.length() > 1024
                || object.startsWith("/")
                || object.indexOf('\\') >= 0
                || !object.startsWith(objectPrefix)) {
            throw new IllegalArgumentException("OSS object is outside the allowed prefix");
        }
        for (String segment : object.split("/", -1)) {
            if (segment.equals(".") || segment.equals("..") || segment.isEmpty()) {
                throw new IllegalArgumentException("Invalid OSS object key");
            }
        }
    }
}
