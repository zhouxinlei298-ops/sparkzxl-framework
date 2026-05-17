package com.github.sparkzxl.trace.provider;

import cn.hutool.core.util.IdUtil;
import com.github.sparkzxl.core.trace.TraceIdContext;
import com.github.sparkzxl.core.trace.TraceTypeEnum;

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
