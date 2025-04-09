package com.github.sparkzxl.log.utils;

import com.github.sparkzxl.core.json.JsonUtils;
import com.github.sparkzxl.log.entity.OptRecordLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * description: 埋点日志
 *
 * @author zhouxinlei
 * @since 2025-04-06 13:30:11
 */
public class BizPointLog {

    private static final Logger BIZ_LOGGER = LoggerFactory.getLogger("BIZ_POINT_LOGGER");

    public BizPointLog() {
    }

    public static void log(OptRecordLog optRecordLog) {
        BIZ_LOGGER.info(JsonUtils.getJson().toJson(optRecordLog));
    }

}
