package com.github.sparkzxl.dubbo.support;


import com.google.common.collect.Lists;

import java.util.List;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-09-05 11:45:32
 */
public class ExceptionHandlerLoad {

    private final static List<Class<?>> EXCEPTION_HANDLERS = Lists.newArrayList();

    public static void load(Class<?> exceptionClass) {
        EXCEPTION_HANDLERS.add(exceptionClass);
    }

    public static boolean contains(Class<?> exceptionClass){
        return EXCEPTION_HANDLERS.contains(exceptionClass);
    }

}
