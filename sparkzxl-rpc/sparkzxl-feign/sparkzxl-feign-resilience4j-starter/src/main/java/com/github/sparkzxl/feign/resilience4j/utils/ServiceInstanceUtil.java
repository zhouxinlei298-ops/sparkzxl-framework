package com.github.sparkzxl.feign.resilience4j.utils;

import java.lang.reflect.Method;
import java.text.MessageFormat;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2022-12-01 11:04:58
 */
public class ServiceInstanceUtil {

    public static String getServiceInstance(String host, int port) {
        return MessageFormat.format("{0}:{1}", host, String.valueOf(port));
    }

    public static String getServiceInstanceMethodId(String host, int port, Method method) {
        return getServiceInstance(host, port) + ":" + MessageFormat.format("{0}:{1}",
                method.getDeclaringClass().getName(),
                method.getName());
    }

    public static String getServiceInstanceMethodId(String host, int port, String path) {
        return getServiceInstance(host, port) + path;
    }

}
