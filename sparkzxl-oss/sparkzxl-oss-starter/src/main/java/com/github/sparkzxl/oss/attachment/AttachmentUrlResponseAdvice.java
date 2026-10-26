package com.github.sparkzxl.oss.attachment;

import com.github.sparkzxl.core.entity.UploadFileModel;
import org.apache.commons.lang3.ObjectUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 响应序列化前刷新标注 {@link RefreshAttachmentUrls} 的实体实例中的附件短期地址。
 *
 * <p>在响应图内按有限深度下探（集合、数组、Map、Optional 与宿主包装对象的字段），
 * 对命中标注类的实例刷新其 {@link UploadFileModel} 与 {@code List<UploadFileModel>}
 * 字段；刷新语义（有值才生成、失败清空）由 {@link AttachmentUrlResolver} 决定。
 * 解析器抛出的异常不改变响应状态，只把对应地址置空并告警，避免把已成功的查询改写成错误。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
@RestControllerAdvice
public class AttachmentUrlResponseAdvice implements ResponseBodyAdvice<Object> {

    /**
     * 响应结构最大下探深度，避免深图反射。
     */
    public static final int MAX_DEPTH = 3;

    /**
     * 刷新日志。
     */
    private static final Logger log = LoggerFactory.getLogger(AttachmentUrlResponseAdvice.class);

    /**
     * 标注类判定缓存。
     */
    private static final Map<Class<?>, Boolean> ANNOTATED = new ConcurrentHashMap<>();

    /**
     * 附件字段缓存：仅记录 UploadFileModel 与 List 型附件字段。
     */
    private static final Map<Class<?>, List<Field>> ATTACHMENT_FIELDS = new ConcurrentHashMap<>();

    /**
     * 附件短期地址解析契约。
     */
    private final AttachmentUrlResolver resolver;

    /**
     * 构造响应刷新增强。
     *
     * @param resolver 附件短期地址解析契约
     */
    public AttachmentUrlResponseAdvice(AttachmentUrlResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * 所有响应都进入下探判断；实际开销由深度限制与类级缓存控制。
     *
     * @param returnType    处理器返回类型
     * @param converterType 消息转换器类型
     * @return 始终执行刷新判断
     */
    @Override
    public boolean supports(MethodParameter returnType,
                            Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    /**
     * 在序列化前刷新响应体里的附件模型。
     *
     * @param body                  响应体
     * @param returnType            处理器返回类型
     * @param selectedContentType   内容类型
     * @param selectedConverterType 消息转换器类型
     * @param request               当前请求
     * @param response              当前响应
     * @return 已刷新的响应体
     */
    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType,
                                  MediaType selectedContentType, Class<? extends HttpMessageConverter<?>> selectedConverterType,
                                  ServerHttpRequest request, ServerHttpResponse response) {
        if (ObjectUtils.isEmpty(body)) {
            return body;
        }
        try {
            refreshGraph(body, 0, new IdentityHashMap<>());
        } catch (RuntimeException e) {
            log.warn("附件地址刷新中止，错误类型={}", e.getClass().getSimpleName());
        }
        return body;
    }

    private void refreshGraph(Object node, int depth, Map<Object, Object> seen) {
        if (node == null || depth > MAX_DEPTH || seen.containsKey(node)) {
            return;
        }
        if (node instanceof UploadFileModel || node instanceof CharSequence
                || node instanceof Number || node instanceof Boolean) {
            return;
        }
        seen.put(node, Boolean.TRUE);
        if (isAnnotated(node.getClass())) {
            refreshAnnotatedInstance(node);
        }
        if (node instanceof Collection) {
            for (Object item : (Collection<?>) node) {
                refreshGraph(item, depth + 1, seen);
            }
            return;
        }
        if (node instanceof Map) {
            for (Object value : ((Map<?, ?>) node).values()) {
                refreshGraph(value, depth + 1, seen);
            }
            return;
        }
        if (node instanceof Optional) {
            refreshGraph(((Optional<?>) node).orElse(null), depth + 1, seen);
            return;
        }
        if (node.getClass().isArray()) {
            if (node instanceof Object[]) {
                for (Object item : (Object[]) node) {
                    refreshGraph(item, depth + 1, seen);
                }
            }
            return;
        }
        if (node.getClass().getName().startsWith("java.") || node.getClass().getName().startsWith("javax.")) {
            return;
        }
        for (Field field : instanceFields(node.getClass())) {
            if (Modifier.isStatic(field.getModifiers())) {
                continue;
            }
            field.setAccessible(true);
            try {
                refreshGraph(field.get(node), depth + 1, seen);
            } catch (IllegalAccessException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void refreshAnnotatedInstance(Object instance) {
        for (Field field : attachmentFields(instance.getClass())) {
            field.setAccessible(true);
            try {
                Object value = field.get(instance);
                if (value == null) {
                    continue;
                }
                if (value instanceof UploadFileModel) {
                    UploadFileModel resolved = resolver.resolve((UploadFileModel) value);
                    if (resolved != null) {
                        field.set(instance, resolved);
                    }
                } else if (value instanceof List && !((List<?>) value).isEmpty()
                        && ((List<?>) value).get(0) instanceof UploadFileModel) {
                    @SuppressWarnings("unchecked")
                    List<UploadFileModel> models = (List<UploadFileModel>) value;
                    field.set(instance, resolver.resolveAll(models));
                }
            } catch (IllegalAccessException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static boolean isAnnotated(Class<?> type) {
        return ANNOTATED.computeIfAbsent(type, key -> key.isAnnotationPresent(RefreshAttachmentUrls.class));
    }

    private static List<Field> instanceFields(Class<?> type) {
        return FIELDS.computeIfAbsent(type, AttachmentUrlResponseAdvice::collectFields);
    }

    private static List<Field> attachmentFields(Class<?> type) {
        return ATTACHMENT_FIELDS.computeIfAbsent(type, key -> {
            List<Field> result = new java.util.ArrayList<>();
            for (Field field : collectFields(key)) {
                if (field.getType() == UploadFileModel.class || field.getType() == List.class) {
                    result.add(field);
                }
            }
            return result;
        });
    }

    private static List<Field> collectFields(Class<?> type) {
        List<Field> fields = new java.util.ArrayList<>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            for (Field field : current.getDeclaredFields()) {
                if (!Modifier.isStatic(field.getModifiers()) && !field.isSynthetic()) {
                    fields.add(field);
                }
            }
        }
        return fields;
    }

    /**
     * 类级字段缓存。
     */
    private static final Map<Class<?>, List<Field>> FIELDS = new ConcurrentHashMap<>();
}
