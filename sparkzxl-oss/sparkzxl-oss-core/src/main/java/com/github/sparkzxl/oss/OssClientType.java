package com.github.sparkzxl.oss;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Objects;

/**
 * OSS供应商类型值对象，内置类型与消费方扩展类型使用相同契约。
 *
 * <p>类型标识不代表已安装适配器；创建客户端时仍须注册对应实现。
 *
 * @author zhouxinlei
 * @since 2026-10-07
 */
public final class OssClientType {

    /**
     * MinIO供应商类型。
     */
    public static final OssClientType MINIO = of("minio", "MinIO");

    /**
     * 阿里云OSS供应商类型。
     */
    public static final OssClientType ALIYUN = of("aliyun", "阿里云OSS");

    /**
     * RustFS供应商类型。
     */
    public static final OssClientType RUSTFS = of("rustfs", "RustFS");

    /**
     * 配置与SPI共享的稳定标识。
     */
    private final String value;

    /**
     * 可信代码声明的展示名称。
     */
    private final String displayName;

    private OssClientType(String value, String displayName) {
        if (value == null || !value.matches("[a-z][a-z0-9_-]{0,15}")) {
            throw new IllegalArgumentException("Invalid OSS client type identifier");
        }
        if (displayName == null || displayName.trim().isEmpty()) {
            throw new IllegalArgumentException("OSS client type display name is required");
        }
        this.value = value;
        this.displayName = displayName.trim();
    }

    /**
     * 解析报文中的类型标识；未知类型保持其标识，不推断适配器。
     *
     * @param value 小写供应商标识，最长16字符
     * @return 内置或扩展类型
     */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static OssClientType of(String value) {
        if ("minio".equals(value)) {
            return MINIO;
        }
        if ("aliyun".equals(value)) {
            return ALIYUN;
        }
        if ("rustfs".equals(value)) {
            return RUSTFS;
        }
        return new OssClientType(value, value);
    }

    /**
     * 定义类型及展示名；同标识具有相同身份。
     *
     * @param value       供应商标识
     * @param displayName 可信代码声明的展示名
     * @return 不可变类型
     */
    public static OssClientType of(String value, String displayName) {
        return new OssClientType(value, displayName);
    }

    /**
     * @return 配置及SPI使用的稳定类型标识
     */
    @JsonValue
    public String value() {
        return value;
    }

    /**
     * @return 类型展示名
     */
    public String displayName() {
        return displayName;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object other) {
        return this == other
                || other instanceof OssClientType && value.equals(((OssClientType) other).value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return value;
    }
}
