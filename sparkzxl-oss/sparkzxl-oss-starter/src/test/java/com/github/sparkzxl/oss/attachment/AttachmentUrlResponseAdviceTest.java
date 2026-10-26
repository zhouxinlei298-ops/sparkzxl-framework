package com.github.sparkzxl.oss.attachment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.github.sparkzxl.core.entity.UploadFileModel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/**
 * 响应刷新增强的图下探与刷新语义回归（不起 Spring 容器）。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public class AttachmentUrlResponseAdviceTest {

    private static final long NOW = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();

    @RefreshAttachmentUrls
    public static class LicenseVO {
        private UploadFileModel license;
        private List<UploadFileModel> annexes;
        private String name;

        public UploadFileModel getLicense() {
            return license;
        }

        public void setLicense(UploadFileModel license) {
            this.license = license;
        }

        public List<UploadFileModel> getAnnexes() {
            return annexes;
        }

        public void setAnnexes(List<UploadFileModel> annexes) {
            this.annexes = annexes;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    /** 宿主包装对象（未标注）：字段下探进入。 */
    public static class Wrapper {
        private Object data;

        public Object getData() {
            return data;
        }

        public void setData(Object data) {
            this.data = data;
        }
    }

    public static class Plain {
        private UploadFileModel model;

        public UploadFileModel getModel() {
            return model;
        }

        public void setModel(UploadFileModel model) {
            this.model = model;
        }
    }

    private DefaultAttachmentUrlResolver resolver() {
        return new DefaultAttachmentUrlResolver(
                () -> new AttachmentIdentity("t", "s"),
                (tenant, subject, id) -> new SignedAttachment("https://oss/" + id, NOW + 300_000L),
                Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC));
    }

    private static Map<String, Object> response(Object data) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("code", 200);
        envelope.put("data", data);
        return envelope;
    }

    @Test
    public void refreshesAnnotatedFieldsInsideWrapperMapAndOptional() {
        AttachmentUrlResponseAdvice advice = new AttachmentUrlResponseAdvice(resolver());
        LicenseVO vo = new LicenseVO();
        vo.setLicense(UploadFileModel.of(3L, "a.png", "p", 1L, null, null));
        vo.setAnnexes(Arrays.asList(
                UploadFileModel.of(4L, "b.pdf", "p", 1L, null, null),
                UploadFileModel.of(5L, "c.doc", "p", 1L, "https://oss/stale", NOW - 1L)));
        vo.setName("n");

        Wrapper wrapper = new Wrapper();
        wrapper.setData(response(Optional.of(vo)));
        Object refreshed = advice.beforeBodyWrite(wrapper, null, null, null, null, null);

        LicenseVO result = (LicenseVO) ((Optional<?>) ((Map<?, ?>) ((Wrapper) refreshed).getData()).get("data")).orElse(null);
        assertEquals("https://oss/3", result.getLicense().getFileUrl());
        assertEquals("https://oss/4", result.getAnnexes().get(0).getFileUrl());
        // 过期地址按稳定引用重签，而不是保留旧地址
        assertEquals("https://oss/5", result.getAnnexes().get(1).getFileUrl());
        assertEquals(Long.valueOf(NOW + 300_000L), result.getAnnexes().get(1).getFileUrlExpiresAt());
    }

    @Test
    public void skipsUnannotatedInstances() {
        AttachmentUrlResponseAdvice advice = new AttachmentUrlResponseAdvice(
                new DefaultAttachmentUrlResolver(
                        () -> new AttachmentIdentity("t", "s"),
                        (tenant, subject, id) -> {
                            throw new AssertionError("unannotated must not resolve");
                        },
                        Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)));
        Plain plain = new Plain();
        plain.setModel(UploadFileModel.of(9L, "a", "p", 1L, null, null));
        advice.beforeBodyWrite(plain, null, null, null, null, null);
        assertNull(plain.getModel().getFileUrl());
    }

    @Test
    public void keepsFreshUrlsWithoutResigning() {
        AttachmentUrlResponseAdvice advice = new AttachmentUrlResponseAdvice(
                new DefaultAttachmentUrlResolver(
                        () -> new AttachmentIdentity("t", "s"),
                        (tenant, subject, id) -> {
                            throw new AssertionError("fresh url must not resign");
                        },
                        Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC)));
        LicenseVO vo = new LicenseVO();
        vo.setLicense(UploadFileModel.of(3L, "a.png", "p", 1L, "https://oss/fresh", NOW + 600_000L));
        advice.beforeBodyWrite(vo, null, null, null, null, null);
        assertEquals("https://oss/fresh", vo.getLicense().getFileUrl());
    }

    @Test
    public void nullBodyPassesThrough() {
        AttachmentUrlResponseAdvice advice = new AttachmentUrlResponseAdvice(resolver());
        assertNull(advice.beforeBodyWrite(null, null, null, null, null, null));
    }
}
