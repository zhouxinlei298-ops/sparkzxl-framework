package com.github.sparkzxl.oss.attachment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.sparkzxl.core.entity.UploadFileModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * 附件交互模型的 JSON 编解码，用于把附件集合以字符串形式持久化到业务表。
 *
 * <p>写入时只保留稳定字段：短期访问地址与到期时间不入库、不落快照，读取后由
 * {@link AttachmentUrlResolver} 按稳定引用重新签发。读取时忽略未知字段，兼容后续
 * 模型字段演进。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public final class AttachmentModelJson {

    /**
     * 编解码器，忽略未知字段以兼容模型演进。
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * 附件集合类型。
     */
    private static final TypeReference<List<UploadFileModel>> MODEL_LIST =
            new TypeReference<List<UploadFileModel>>() {
            };

    /**
     * 工具类禁止实例化。
     */
    private AttachmentModelJson() {
    }

    /**
     * 序列化为只含稳定字段的 JSON 字符串。
     *
     * @param models 附件集合；为 null 时按空集合处理
     * @return JSON 字符串
     * @throws IllegalStateException 序列化失败；不携带模型内容
     */
    public static String write(List<UploadFileModel> models) {
        List<UploadFileModel> stable = new ArrayList<UploadFileModel>();
        if (models != null) {
            for (UploadFileModel model : models) {
                stable.add(model == null ? null : model.withFileUrl(null, null));
            }
        }
        try {
            return MAPPER.writeValueAsString(stable);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("attachment json invalid", e);
        }
    }

    /**
     * 反序列化附件集合。
     *
     * @param json JSON 字符串；空白时返回空集合
     * @return 附件集合
     * @throws IllegalStateException 反序列化失败；不携带原文
     */
    public static List<UploadFileModel> read(String json) {
        if (json == null || json.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<UploadFileModel> models = MAPPER.readValue(json, MODEL_LIST);
            return models == null ? new ArrayList<>() : models;
        } catch (IOException e) {
            throw new IllegalStateException("attachment json invalid", e);
        }
    }
}
