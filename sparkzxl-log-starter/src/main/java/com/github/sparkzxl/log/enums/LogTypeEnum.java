package com.github.sparkzxl.log.enums;

import lombok.Getter;

/**
 * description: LogTypeEnum
 *
 * @author zhouxinlei
 * @since 2025-07-31 17:12:58
 */
@Getter
public enum LogTypeEnum {
    REDIS("redis"),
    KAFKA("kafka"),
    ;

    private final String type;

    LogTypeEnum(String type) {
        this.type = type;
    }
}
