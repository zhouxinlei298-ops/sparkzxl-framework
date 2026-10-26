package com.github.sparkzxl.oss.attachment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.github.sparkzxl.core.entity.UploadFileModel;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * 附件模型引用契约与默认解析器「有值才生成」语义回归。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public class AttachmentUrlResolverTest {

    private static final long NOW = Instant.parse("2026-10-25T00:00:00Z").toEpochMilli();

    private Clock clock() {
        return Clock.fixed(Instant.ofEpochMilli(NOW), ZoneOffset.UTC);
    }

    @Test
    public void referenceContractRoundTrips() {
        UploadFileModel model = UploadFileModel.of(12L, "a.png", "p/a.png", 3L, "https://oss/signed", NOW + 300_000L);
        assertEquals("attachment:12", model.getAttachmentReference());
        assertTrue(UploadFileModel.isAttachmentReference("attachment:12"));
        assertFalse(UploadFileModel.isAttachmentReference("attachment:abc"));
        assertFalse(UploadFileModel.isAttachmentReference("https://oss/signed"));
        assertEquals(Long.valueOf(12L), model.attachmentId());
        assertEquals(Long.valueOf(9L), UploadFileModel.of(null, null, null, null, null, null)
                .setAttachmentReference("attachment:9")
                .attachmentId());
        assertNull(new UploadFileModel().attachmentId());
    }

    @Test
    public void usableUrlRespectsExpiryAndMargin() {
        UploadFileModel fresh = new UploadFileModel().setFileUrl("https://oss/signed").setFileUrlExpiresAt(NOW + 120_000L);
        assertTrue(fresh.hasUsableFileUrl(NOW, 60_000L));
        UploadFileModel nearExpiry = new UploadFileModel().setFileUrl("https://oss/signed").setFileUrlExpiresAt(NOW + 30_000L);
        assertFalse(nearExpiry.hasUsableFileUrl(NOW, 60_000L));
        UploadFileModel reference = new UploadFileModel().setFileUrl("attachment:1").setFileUrlExpiresAt(NOW + 120_000L);
        assertFalse(reference.hasUsableFileUrl(NOW, 60_000L));
        UploadFileModel unknownExpiry = new UploadFileModel().setFileUrl("https://oss/signed");
        assertFalse(unknownExpiry.hasUsableFileUrl(NOW, 60_000L));
    }

    @Test
    public void resolveKeepsFreshUrlWithoutResigning() {
        UploadFileModel model = new UploadFileModel()
                .setFileId(7L)
                .setFileUrl("https://oss/signed")
                .setFileUrlExpiresAt(NOW + 600_000L);
        DefaultAttachmentUrlResolver resolver = new DefaultAttachmentUrlResolver(
                () -> new AttachmentIdentity("t", "s"),
                (tenant, subject, id) -> {
                    throw new AssertionError("fresh url must not resign");
                },
                clock());
        assertEquals("https://oss/signed", resolver.resolve(model).getFileUrl());
    }

    @Test
    public void resolveSignsStableReferenceOnly() {
        UploadFileModel model = UploadFileModel.of(7L, "a.png", "p/a.png", 1L, null, null);
        DefaultAttachmentUrlResolver resolver = new DefaultAttachmentUrlResolver(
                () -> new AttachmentIdentity("tenant-a", "subject-1"),
                (tenant, subject, id) -> {
                    assertEquals("tenant-a", tenant);
                    assertEquals("subject-1", subject);
                    assertEquals(7L, id);
                    return new SignedAttachment("https://oss/signed-7", NOW + 300_000L);
                },
                clock());
        UploadFileModel resolved = resolver.resolve(model);
        assertEquals("https://oss/signed-7", resolved.getFileUrl());
        assertEquals(Long.valueOf(NOW + 300_000L), resolved.getFileUrlExpiresAt());
        // 无引用模型不生成地址
        UploadFileModel noReference = new UploadFileModel().setOriginalFileName("b.png");
        assertNull(resolver.resolve(noReference).getFileUrl());
        assertNull(resolver.resolve(noReference).getFileUrlExpiresAt());
    }

    @Test
    public void resolveClearsUrlWhenSigningFailsOrIdentityMissing() {
        UploadFileModel model = UploadFileModel.of(7L, "a.png", "p/a.png", 1L, "https://oss/stale", NOW - 1L);
        DefaultAttachmentUrlResolver failing = new DefaultAttachmentUrlResolver(
                () -> new AttachmentIdentity("t", "s"),
                (tenant, subject, id) -> {
                    throw new IllegalStateException("boom " + "https://oss/secret");
                },
                clock());
        UploadFileModel resolved = failing.resolve(model);
        assertNull(resolved.getFileUrl());
        assertNull(resolved.getFileUrlExpiresAt());

        DefaultAttachmentUrlResolver identityMissing = new DefaultAttachmentUrlResolver(
                () -> null,
                (tenant, subject, id) -> new SignedAttachment("https://oss/should-not-happen", NOW),
                clock());
        UploadFileModel kept = identityMissing.resolve(model);
        assertEquals("https://oss/stale", kept.getFileUrl());
    }

    @Test
    public void resolveAllPreservesOrderAndNullEntries() {
        UploadFileModel first = UploadFileModel.of(1L, "a", "o", 1L, null, null);
        DefaultAttachmentUrlResolver resolver = new DefaultAttachmentUrlResolver(
                () -> new AttachmentIdentity("t", "s"),
                (tenant, subject, id) -> new SignedAttachment("https://oss/" + id, NOW + 1000L),
                clock());
        List<UploadFileModel> resolved = resolver.resolveAll(Arrays.asList(first, null));
        assertEquals(2, resolved.size());
        assertEquals("https://oss/1", resolved.get(0).getFileUrl());
        assertNull(resolved.get(1));
        assertTrue(resolver.resolveAll(null).isEmpty());
    }

    @Test
    public void jsonWriteStripsEphemeralFieldsAndReadRoundTrips() {
        UploadFileModel model = UploadFileModel.of(5L, "a.txt", "p/a.txt", 9L, "https://oss/signed", NOW);
        String json = AttachmentModelJson.write(Arrays.asList(model, null));
        assertFalse(json.contains("https://oss/signed"));
        List<UploadFileModel> parsed = AttachmentModelJson.read(json);
        assertEquals(2, parsed.size());
        assertEquals("attachment:5", parsed.get(0).getAttachmentReference());
        assertNull(parsed.get(0).getFileUrl());
        assertTrue(AttachmentModelJson.read(" ").isEmpty());
    }
}
