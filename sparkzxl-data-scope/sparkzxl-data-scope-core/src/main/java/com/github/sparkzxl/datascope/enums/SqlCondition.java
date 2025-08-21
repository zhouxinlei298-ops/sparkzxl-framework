package com.github.sparkzxl.datascope.enums;

import lombok.AllArgsConstructor;

/**
 * description: SQL条件类型
 *
 * @author zhouxinlei
 * @since 2022-11-09 18:44:24
 */
@AllArgsConstructor
public enum SqlCondition {

    EQ("="),
    IN("IN"),
    LIKE("LIKE"),
    LIKE_LEFT("LIKE_LEFT"),
    LIKE_RIGHT("LIKE_RIGHT"),
    LIST_IN("LIST_IN"),
    ;

    private final String keyword;

    public String getSqlSegment() {
        return this.keyword;
    }

}
