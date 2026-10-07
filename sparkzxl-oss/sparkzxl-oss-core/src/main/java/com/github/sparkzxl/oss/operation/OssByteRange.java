package com.github.sparkzxl.oss.operation;

import lombok.Getter;

/**
 * 解析单段 HTTP 字节范围，拒绝非法或多段请求。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class OssByteRange {

    /** 范围起始位置（字节）。 */
    @Getter private final long start;

    /** 范围结束位置（字节，含）。 */
    @Getter private final long end;

    private OssByteRange(long start, long end) {
        this.start = start;
        this.end = end;
    }

    /**
     * 获取范围长度（字节）
     *
     * @return 范围长度（字节）
     */
    public long getLength() {
        return end - start + 1;
    }

    /**
     * 解析单段字节范围并按对象大小约束边界。
     *
     * @param header HTTP Range 请求头
     * @param size 对象预期总大小（字节）
     * @return 已约束至实际对象边界的单段范围
     */
    public static OssByteRange parse(String header, long size) {
        if (size < 0) {
            throw new IllegalArgumentException("Invalid object size");
        }
        if (header == null) {
            return new OssByteRange(0, size - 1);
        }
        if (size == 0 || !header.matches("bytes=[0-9]*-[0-9]*")) {
            throw new IllegalArgumentException("Unsatisfiable Range");
        }
        String[] range = header.substring(6).split("-", -1);
        try {
            long start, end = size - 1;
            if (range[0].isEmpty()) {
                if (range[1].isEmpty()) {
                    throw new IllegalArgumentException("Empty Range");
                }
                long suffix = Long.parseLong(range[1]);
                if (suffix <= 0) {
                    throw new IllegalArgumentException("Invalid suffix");
                }
                start = Math.max(0, size - suffix);
            } else {
                start = Long.parseLong(range[0]);
                if (!range[1].isEmpty()) {
                    end = Math.min(end, Long.parseLong(range[1]));
                }
            }
            if (start < 0 || start >= size || end < start) {
                throw new IllegalArgumentException("Unsatisfiable Range");
            }
            return new OssByteRange(start, end);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid Range number", e);
        }
    }
}
