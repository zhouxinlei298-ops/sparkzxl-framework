package com.github.sparkzxl.oss.attachment;

import lombok.Getter;

/**
 * 当前可信主体身份，由宿主侧从请求上下文提取。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@Getter
public final class AttachmentIdentity {

    /** 可信租户标识。 */
    private final String tenantId;

    /** 可信主体标识。 */
    private final String ownerSubjectId;

    /**
     * 构建身份值对象。
     *
     * @param tenantId 可信租户标识
     * @param ownerSubjectId 可信主体标识
     */
    public AttachmentIdentity(String tenantId, String ownerSubjectId) {
        this.tenantId = tenantId;
        this.ownerSubjectId = ownerSubjectId;
    }

}
