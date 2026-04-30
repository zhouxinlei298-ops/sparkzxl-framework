package com.github.sparkzxl.core.constant.enums;

import lombok.Getter;

/**
 * description: The Order Of Bean
 *
 * @author zhouxinlei
 * @since 2025-04-27 09:29:34
 */
@Getter
public enum BeanOrderEnum {

    REGISTRY_FEIGN_FILTER(50),
    CACHE_EXCEPTION_ORDER(Integer.MIN_VALUE + 1),
    IDEMPOTENT_EXCEPTION_ORDER(Integer.MIN_VALUE + 2),
    SENTINEL_EXCEPTION_ORDER(Integer.MIN_VALUE + 3),
    ZOOKEEPER_EXCEPTION_ORDER(Integer.MIN_VALUE + 4),
    FEIGN_EXCEPTION_ORDER(Integer.MIN_VALUE + 5),
    ALARM_EXCEPTION_ORDER(Integer.MIN_VALUE + 6),
    APPLICATION_LOG_ORDER(Integer.MIN_VALUE + 7),
    OSS_EXCEPTION_ORDER(-1),
    DATABASE_EXCEPTION_HANDLER_ORDER(0),
    DATASOURCE_EXCEPTION_ORDER(1),
    DUBBO_EXCEPTION_ORDER(2),
    BASE_EXCEPTION_ORDER(Integer.MAX_VALUE)
    ;

    private final int order;

    BeanOrderEnum(int order) {
        this.order = order;
    }

    public static void main(String[] args) {
        System.out.println(Integer.MIN_VALUE + 99);
    }

}
