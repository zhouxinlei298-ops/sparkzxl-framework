package com.github.sparkzxl.oss.attachment;

import lombok.Getter;

/**
 * 附件签发结果值对象。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@Getter
public final class SignedAttachment {

    /**
     * 短期访问地址。
     */
    private final String url;

    /**
     * 到期时间（epoch millis）。
     */
    private final Long expiresAt;

    /**
     * 构建签发结果。
     *
     * @param url       短期访问地址
     * @param expiresAt 到期时间（epoch millis）
     */
    public SignedAttachment(String url, Long expiresAt) {
        this.url = url;
        this.expiresAt = expiresAt;
    }

}
