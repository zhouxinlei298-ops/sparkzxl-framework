package com.github.sparkzxl.oss.attachment;

import com.github.sparkzxl.core.entity.UploadFileModel;

import java.util.ArrayList;
import java.util.List;

/**
 * 附件短期地址解析契约：把持久化的附件模型转换为可交给前端的带有效地址模型。
 *
 * <p>语义为「有值才生成，无值不生成」：已携带未过期地址的模型原样保留；只有稳定引用、
 * 地址缺失或即将过期的模型才重新签发；无稳定引用的模型不生成任何地址。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public interface AttachmentUrlResolver {

    /** 剩余可用时间安全余量（毫秒），小于该余量时提前重新签发。 */
    long SAFETY_MARGIN_MILLIS = 60_000L;

    /**
     * 刷新单个附件模型的短期地址。
     *
     * @param model 附件模型；为 null 时返回 null
     * @return 带有效短期地址的附件模型
     */
    UploadFileModel resolve(UploadFileModel model);

    /**
     * 按顺序刷新附件模型集合的短期地址。
     *
     * @param models 附件模型集合；为 null 时返回空集合
     * @return 与入参顺序一致的新集合
     */
    List<UploadFileModel> resolveAll(List<UploadFileModel> models);
}
