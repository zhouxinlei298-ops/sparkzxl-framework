package com.github.sparkzxl.data.sync.admin.listener.websocket;

import cn.hutool.core.convert.Convert;
import com.google.common.collect.Maps;

import java.util.Map;

/**
 * description: The interface for the websocket ThreadLocal Context
 *
 * @author zhouxinlei
 * @since 2022-08-25 11:00:16
 */
public class WebSocketThreadLocalContext {

    private static final ThreadLocal<Map<String, Object>> THREAD_CONTEXT = new ThreadLocal<>();

    public static void put(final String key, final Object value) {
        getLocalMap().put(key, value);
    }

    /**
     * remove thread variable.
     *
     * @param key remove key
     */
    public static void remove(final String key) {
        getLocalMap().remove(key);
    }

    /**
     * get thread variables.
     *
     * @param key get key
     * @return the Object
     */
    public static <T> T get(final String key, Class<T> type) {
        Object o = getLocalMap().get(key);
        return Convert.convert(type, o);
    }

    public static Map<String, Object> getLocalMap() {
        Map<String, Object> map = THREAD_CONTEXT.get();
        if (map == null) {
            map = Maps.newHashMap();
            THREAD_CONTEXT.set(map);
        }
        return map;
    }

    /**
     * remove all variables.
     */
    public static void clear() {
        THREAD_CONTEXT.remove();
    }


}
