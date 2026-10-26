package com.github.sparkzxl.core.entity;

import lombok.Getter;

import java.io.Serializable;

/**
 * 附件交互模型，承载稳定引用、短期访问地址与地址有效期。
 *
 * <p>持久化值只能是稳定引用（{@code attachment:<附件主键>}）；{@code fileUrl} 是短期地址，
 * 禁止入库、落快照或长期保存。读取侧由 {@code AttachmentUrlResolver} 按稳定引用重新签发，
 * 「有值才生成」：已携带未过期地址的模型原样保留，不重复签发。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@Getter
public class UploadFileModel implements Serializable {

    private static final long serialVersionUID = -8075067456922455607L;
    /**
     * 稳定引用前缀。
     */
    public static final String REFERENCE_PREFIX = "attachment:";

    /**
     * 附件主键，租户内唯一。
     */
    private Long fileId;

    /**
     * 稳定附件引用，格式固定为 attachment:<附件主键>。
     */
    private String attachmentReference;

    /**
     * 原始文件名。
     */
    private String originalFileName;

    /**
     * 短期访问地址；业务持久化使用 attachmentReference。
     */
    private String fileUrl;

    /**
     * 短期访问地址到期时间（epoch millis）；未知时为 null，视为需要重新签发。
     */
    private Long fileUrlExpiresAt;

    /**
     * 服务端对象键。
     */
    private String objectName;

    /**
     * 字节数。
     */
    private Long size;

    /**
     * 文件摘要。
     */
    private String md5;

    /**
     * 按上传结果创建附件模型，同时派生稳定引用。
     *
     * @param fileId           附件主键
     * @param originalFileName 原始文件名
     * @param objectName       服务端对象键
     * @param size             字节数
     * @param fileUrl          短期访问地址
     * @param fileUrlExpiresAt 短期访问地址到期时间（epoch millis）
     * @return 附件交互模型
     */
    public static UploadFileModel of(
            Long fileId,
            String originalFileName,
            String objectName,
            Long size,
            String fileUrl,
            Long fileUrlExpiresAt) {
        return new UploadFileModel()
                .setFileId(fileId)
                .setAttachmentReference(referenceOf(fileId))
                .setOriginalFileName(originalFileName)
                .setObjectName(objectName)
                .setSize(size)
                .setFileUrl(fileUrl)
                .setFileUrlExpiresAt(fileUrlExpiresAt);
    }

    /**
     * 生成稳定附件引用。
     *
     * @param fileId 附件主键
     * @return 稳定引用；附件主键为空时返回 null
     */
    public static String referenceOf(Long fileId) {
        return fileId == null ? null : REFERENCE_PREFIX + fileId;
    }

    /**
     * 判断值是否为稳定附件引用。
     *
     * @param value 待判断值
     * @return 形如 attachment:<十进制主键> 时返回 true
     */
    public static boolean isAttachmentReference(String value) {
        return referenceId(value) != null;
    }

    /**
     * 提取稳定引用中的附件主键文本。
     *
     * @param value 待解析值
     * @return 附件主键文本；不是稳定引用时返回 null
     */
    public static String referenceId(String value) {
        String text = value == null ? "" : value.trim();
        if (!text.startsWith(REFERENCE_PREFIX)) {
            return null;
        }
        String id = text.substring(REFERENCE_PREFIX.length());
        if (id.isEmpty()) {
            return null;
        }
        for (int index = 0; index < id.length(); index++) {
            if (!Character.isDigit(id.charAt(index))) {
                return null;
            }
        }
        return id;
    }

    /**
     * 返回可解析的附件主键，优先使用显式主键。
     *
     * @return 附件主键；无法解析时返回 null
     */
    public Long attachmentId() {
        if (fileId != null) {
            return fileId;
        }
        String id = referenceId(attachmentReference);
        if (id == null) {
            return null;
        }
        try {
            return Long.parseLong(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断当前地址在安全余量内是否仍可用。
     *
     * @param nowMillis          当前时间（epoch millis）
     * @param safetyMarginMillis 安全余量（毫秒），剩余时间不足时视为不可用
     * @return 地址存在、非稳定引用且未临近过期时返回 true
     */
    public boolean hasUsableFileUrl(long nowMillis, long safetyMarginMillis) {
        if (fileUrl == null || fileUrl.trim().isEmpty() || isAttachmentReference(fileUrl)) {
            return false;
        }
        if (fileUrlExpiresAt == null) {
            return false;
        }
        return fileUrlExpiresAt - safetyMarginMillis > nowMillis;
    }

    /**
     * 返回替换短期地址后的副本，稳定字段保持不变。
     *
     * @param url       新的短期访问地址；不可用时为 null
     * @param expiresAt 新的到期时间（epoch millis）；不可用时为 null
     * @return 附件交互模型副本
     */
    public UploadFileModel withFileUrl(String url, Long expiresAt) {
        return new UploadFileModel()
                .setFileId(fileId)
                .setAttachmentReference(attachmentReference)
                .setOriginalFileName(originalFileName)
                .setObjectName(objectName)
                .setSize(size)
                .setMd5(md5)
                .setFileUrl(url)
                .setFileUrlExpiresAt(expiresAt);
    }

    public UploadFileModel setFileId(Long fileId) {
        this.fileId = fileId;
        return this;
    }

    public UploadFileModel setAttachmentReference(String attachmentReference) {
        this.attachmentReference = attachmentReference;
        return this;
    }

    public UploadFileModel setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
        return this;
    }

    public UploadFileModel setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
        return this;
    }

    public UploadFileModel setFileUrlExpiresAt(Long fileUrlExpiresAt) {
        this.fileUrlExpiresAt = fileUrlExpiresAt;
        return this;
    }

    public UploadFileModel setObjectName(String objectName) {
        this.objectName = objectName;
        return this;
    }

    public UploadFileModel setSize(Long size) {
        this.size = size;
        return this;
    }

    public UploadFileModel setMd5(String md5) {
        this.md5 = md5;
        return this;
    }
}
