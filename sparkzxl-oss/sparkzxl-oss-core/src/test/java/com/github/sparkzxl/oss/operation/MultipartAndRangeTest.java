package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.entity.PartData;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

/**
 * 验证 OSS 操作契约与资源安全边界。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public class MultipartAndRangeTest {

    private static PartData part(int number, long size) {
        PartData p = new PartData();
        p.setPartNumber(number);
        p.setSize(size);
        p.setEtag("etag-" + number);
        return p;
    }

    private static void invalid(Runnable action) {
        try {
            action.run();
            fail("Expected validation failure");
        }
        catch (IllegalArgumentException expected) {
        }
    }

    /**
     * 验证真实分片编号排序及防御性复制。
     */
    @Test
    public void preservesNumberingSortsAndCopiesVerifiedParts() {
        PartData two = part(2, 2), one = part(1, 5);
        List<PartData> verified = MultipartPartsValidator.validate(Arrays.asList(two, one), 7, 5, 2);
        two.setPartNumber(99);
        assertEquals(Integer.valueOf(2), verified.get(1).getPartNumber());
        assertEquals(Integer.valueOf(1), verified.get(0).getPartNumber());
    }

    /**
     * 验证拒绝缺片、重复编号、大小错误和伪造总大小。
     */
    @Test
    public void rejectsMissingDuplicateNumberWrongSizeAndForgedTotal() {
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 5)), 7, 5, 2));
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 5), part(1, 2)), 7, 5, 2));
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 5), part(3, 2)), 7, 5, 2));
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 4), part(2, 3)), 7, 5, 2));
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 5), part(2, 2)), 8, 5, 2));
    }

    /**
     * 验证单分片也要求实际对象与精确大小。
     */
    @Test
    public void singlePartStillRequiresRealProviderEntryAndExactSize() {
        invalid(() -> MultipartPartsValidator.validate(Collections.emptyList(), 3, 5, 1));
        invalid(() -> MultipartPartsValidator.validate(Arrays.asList(part(1, 2)), 3, 5, 1));
        assertEquals(1, MultipartPartsValidator.validate(Arrays.asList(part(1, 3)), 3, 5, 1).size());
    }

    /**
     * 验证字节范围边界、后缀范围及开放结束范围。
     */
    @Test
    public void rangeClampsEndAndSupportsSuffixAndOpenEnd() {
        OssByteRange r = OssByteRange.parse("bytes=2-999", 10);
        assertEquals(2, r.getStart());
        assertEquals(9, r.getEnd());
        assertEquals(8, r.getLength());
        r = OssByteRange.parse("bytes=-3", 10);
        assertEquals(7, r.getStart());
        assertEquals(3, r.getLength());
        assertEquals(6, OssByteRange.parse("bytes=4-", 10).getLength());
        assertEquals(10, OssByteRange.parse("bytes=-100", 10).getLength());
    }

    /**
     * 验证非法或多段范围不会降级为完整下载。
     */
    @Test
    public void invalidOrMultipleRangesNeverBecomeFullDownload() {
        for (String h : Arrays.asList("bytes=1-2,4-5", "items=0-1", "bytes=-", "bytes=-0", "bytes=10-", "bytes=9-2",
                "bytes=abc-", "bytes=999999999999999999999999-")) {
            invalid(() -> OssByteRange.parse(h, 10));
        }
        invalid(() -> OssByteRange.parse("bytes=0-", 0));
        assertEquals(0, OssByteRange.parse(null, 0).getLength());
    }

}
