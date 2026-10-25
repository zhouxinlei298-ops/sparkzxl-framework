package com.github.sparkzxl.oss.attachment;

/**
 * 附件查表签发端口：由宿主实现，按归属校验后签发短期访问地址。
 *
 * <p>宿主实现负责附件归属、租户与主体的可信校验（查表），并通过受控 OSS 运行时签发
 * 短期地址；框架默认解析器只负责有效期判断与失败语义，不承担任何归属判断。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public interface AttachmentSigner {

    /**
     * 为单个附件签发短期访问地址。
     *
     * @param tenantId 可信租户标识
     * @param ownerSubjectId 可信主体标识
     * @param attachmentId 附件主键
     * @return 签发结果
     * @throws Exception 附件不存在、越权或签发失败时抛出；实现不得在消息中携带地址或凭据
     */
    SignedAttachment sign(String tenantId, String ownerSubjectId, long attachmentId) throws Exception;
}
