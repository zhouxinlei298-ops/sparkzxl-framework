package com.github.sparkzxl.oss.attachment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件短期地址解析默认实现：有效期判断与失败语义由框架承担，签发动作委托宿主查表实现。
 *
 * <p>「有值才生成，无值不生成」：模型已携带未过期地址时原样返回；无稳定引用的模型
 * 不签发；身份上下文缺失时不签发也不清除既有地址；签发失败时不保留旧地址，
 * 避免把过期地址或非本主体的地址继续交给前端。异常消息不进入日志，避免泄漏配置内容。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public class DefaultAttachmentUrlResolver implements AttachmentUrlResolver {

    /** 解析器日志。 */
    private static final Logger log = LoggerFactory.getLogger(DefaultAttachmentUrlResolver.class);

    /** 可信身份提供者。 */
    private final AttachmentIdentityProvider identityProvider;

    /** 附件查表签发端口。 */
    private final AttachmentSigner signer;

    /** 时间来源，便于测试固定有效期判断。 */
    private final Clock clock;

    /**
     * 构建默认解析器。
     *
     * @param identityProvider 可信身份提供者
     * @param signer 附件查表签发端口
     */
    public DefaultAttachmentUrlResolver(AttachmentIdentityProvider identityProvider, AttachmentSigner signer) {
        this(identityProvider, signer, Clock.systemUTC());
    }

    /**
     * 构建默认解析器并指定时间来源。
     *
     * @param identityProvider 可信身份提供者
     * @param signer 附件查表签发端口
     * @param clock 时间来源
     */
    public DefaultAttachmentUrlResolver(
            AttachmentIdentityProvider identityProvider, AttachmentSigner signer, Clock clock) {
        this.identityProvider = identityProvider;
        this.signer = signer;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public UploadFileModel resolve(UploadFileModel model) {
        if (model == null) {
            return null;
        }
        if (model.hasUsableFileUrl(clock.millis(), SAFETY_MARGIN_MILLIS)) {
            return model;
        }
        Long attachmentId = model.attachmentId();
        if (attachmentId == null) {
            return model;
        }
        AttachmentIdentity identity = identityProvider.current();
        if (identity == null
                || isBlank(identity.getTenantId())
                || isBlank(identity.getOwnerSubjectId())) {
            log.warn("附件短期地址签发跳过：身份上下文不可用，attachmentId 不校验不签发");
            return model;
        }
        try {
            SignedAttachment signed = signer.sign(identity.getTenantId(), identity.getOwnerSubjectId(), attachmentId);
            if (signed == null || signed.getUrl() == null || signed.getUrl().trim().isEmpty()) {
                return model.withFileUrl(null, null);
            }
            return model.withFileUrl(signed.getUrl(), signed.getExpiresAt());
        } catch (Exception e) {
            log.warn(
                    "附件短期地址签发失败，按地址不可用处理：attachmentId={}, errorType={}",
                    attachmentId,
                    e.getClass().getSimpleName());
            return model.withFileUrl(null, null);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<UploadFileModel> resolveAll(List<UploadFileModel> models) {
        List<UploadFileModel> result = new ArrayList<>();
        if (models == null) {
            return result;
        }
        for (UploadFileModel model : models) {
            result.add(resolve(model));
        }
        return result;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
