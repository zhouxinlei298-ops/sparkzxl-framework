package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.entity.PartData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * 校验真实分片编号、完整性、分片大小及对象总大小。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class MultipartPartsValidator {

    private MultipartPartsValidator() {}

    /**
     * 校验编号连续性、重复分片、分片大小及总大小。
     *
     * @param parts 远端真实分片清单
     * @param size 对象预期总大小（字节）
     * @param partSize 标准分片大小（字节）
     * @param count 预期分片数
     * @return 按真实编号排序的完整分片副本
     */
    public static List<PartData> validate(
            List<PartData> parts, long size, long partSize, int count) {
        if (size <= 0
                || partSize <= 0
                || count <= 0
                || count > 10000
                || 1 + (size - 1) / partSize != count
                || parts == null
                || parts.size() != count) {
            throw new IllegalArgumentException("Invalid or incomplete multipart manifest");
        }
        List<PartData> result = new ArrayList<>();
        for (PartData part : parts) {
            if (part == null
                    || part.getPartNumber() == null
                    || part.getSize() == null
                    || part.getEtag() == null
                    || part.getEtag().trim().isEmpty()) {
                throw new IllegalArgumentException("Invalid provider multipart entry");
            }
            PartData copy = new PartData();
            copy.setPartNumber(part.getPartNumber());
            copy.setSize(part.getSize());
            copy.setEtag(part.getEtag());
            result.add(copy);
        }
        result.sort(Comparator.comparingInt(PartData::getPartNumber));
        long total = 0;
        for (int i = 0; i < count; i++) {
            PartData part = result.get(i);
            long expected = i == count - 1 ? size - partSize * i : partSize;
            if (part.getPartNumber() != i + 1 || part.getSize() != expected) {
                throw new IllegalArgumentException(
                        "Multipart numbering or size does not match manifest");
            }
            total = Math.addExact(total, part.getSize());
        }
        if (total != size) {
            throw new IllegalArgumentException("Multipart total size mismatch");
        }
        return Collections.unmodifiableList(result);
    }
}
