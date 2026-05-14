package com.github.sparkzxl.log;

import com.github.sparkzxl.log.utils.ThrowableUtils;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * description: 日志告警全局信息
 *
 * @author zhoux
 */
public class AlarmLogContext {

    private static final List<Class<? extends Throwable>> DO_EXTEND_WARN_EXCEPTION_LIST = new ArrayList<>();
    @Getter
    private static Boolean printStackTrace = false;
    @Getter
    private static Boolean simpleWarnInfo = false;
    private static Boolean warnExceptionExtend = false;
    @Getter
    private static List<Class<? extends Throwable>> doWarnExceptionList = new ArrayList<>();

    public static void setPrintStackTrace(Boolean printStackTrace) {
        AlarmLogContext.printStackTrace = printStackTrace;
    }

    public static void setSimpleWarnInfo(Boolean simpleWarnInfo) {
        AlarmLogContext.simpleWarnInfo = simpleWarnInfo;
    }

    public static void setWarnExceptionExtend(Boolean warnExceptionExtend) {
        AlarmLogContext.warnExceptionExtend = warnExceptionExtend;
        if (warnExceptionExtend && !AlarmLogContext.doWarnExceptionList.isEmpty()) {
            genExtendWarnExceptionList();
        }
    }

    public static void setDoWarnExceptionList(List<Class<? extends Throwable>> doWarnExceptionList) {
        AlarmLogContext.doWarnExceptionList = doWarnExceptionList;
        if (AlarmLogContext.warnExceptionExtend) {
            genExtendWarnExceptionList();
        }
    }

    public static void addDoWarnExceptionList(List<Class<? extends Throwable>> doWarnExceptionList) {
        AlarmLogContext.doWarnExceptionList.addAll(doWarnExceptionList);
        if (AlarmLogContext.warnExceptionExtend) {
            genExtendWarnExceptionList(doWarnExceptionList);
        }
    }

    public static boolean doWarnException(Throwable warnExceptionClass) {
        return AlarmLogContext.warnExceptionExtend ? ThrowableUtils.doWarnExceptionExtend(warnExceptionClass, AlarmLogContext.DO_EXTEND_WARN_EXCEPTION_LIST) : ThrowableUtils.doWarnExceptionName(warnExceptionClass, AlarmLogContext.doWarnExceptionList);
    }

    public static boolean doWarnException(String warnExceptionClassName) {
        return AlarmLogContext.warnExceptionExtend ? ThrowableUtils.doWarnExceptionExtend(warnExceptionClassName, AlarmLogContext.DO_EXTEND_WARN_EXCEPTION_LIST) : ThrowableUtils.doWarnExceptionName(warnExceptionClassName, AlarmLogContext.doWarnExceptionList);
    }

    private static void genExtendWarnExceptionList() {
        AlarmLogContext.DO_EXTEND_WARN_EXCEPTION_LIST.addAll(AlarmLogContext.doWarnExceptionList);
    }

    private static void genExtendWarnExceptionList(List<Class<? extends Throwable>> doWarnExceptionList) {
        AlarmLogContext.DO_EXTEND_WARN_EXCEPTION_LIST.addAll(doWarnExceptionList);
    }

}
