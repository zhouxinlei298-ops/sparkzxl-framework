package com.github.sparkzxl.log;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * description: 日志告警全局信息
 *
 * @author zhoux
 */
@Getter
@Setter
public class AlarmLogContext {

    @Getter
    @Setter
    private static Boolean printStackTrace = false;
    @Getter
    @Setter
    private static Boolean simpleWarnInfo = false;
    @Getter
    private static List<Class<? extends Throwable>> warnExceptionList = new CopyOnWriteArrayList<>();

    public static void addWarnExceptionList(List<Class<? extends Throwable>> warnExceptionList) {
        AlarmLogContext.warnExceptionList.addAll(warnExceptionList);
    }

    public static boolean match(Throwable throwable) {
        return match(throwable.getClass());
    }

    public static boolean match(String className) {
        if (warnExceptionList.isEmpty()) {
            return true;
        }
        return warnExceptionList.stream().anyMatch(x -> x.getName().equals(className));
    }

    private static boolean match(Class<?> actualClass) {
        if (warnExceptionList.isEmpty()) {
            return true;
        }
        for (Class<?> configured : warnExceptionList) {
            if (configured.isAssignableFrom(actualClass)) {
                return true;
            }
        }
        return false;
    }

}
