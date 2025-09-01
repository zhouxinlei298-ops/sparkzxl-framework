package com.github.sparkzxl.core.context;

import cn.hutool.core.util.IdUtil;
import com.github.sparkzxl.core.constant.enums.TraceTypeEnum;

/**
 * description: 本地 traceId 获取
 *
 * @author zhouxinlei
 * @since 2025-08-30 11:09:43
 */
public class LocalTraceIdContext implements TraceIdContext {

    @Override
    public String getTraceId() {
        return IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
    }

    @Override
    public String type() {
        return TraceTypeEnum.LOCAL.name();
    }
}
