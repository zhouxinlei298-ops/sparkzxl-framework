package com.github.sparkzxl.sentinel.support;

import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.alibaba.csp.sentinel.slots.system.SystemBlockException;
import com.github.sparkzxl.core.support.code.ExceptionErrorCode;
import com.github.sparkzxl.core.support.ExceptionCodeResolver;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Sentinel 异常码注册
 *
 * @author zhouxinlei
 */
@Component
public class SentinelExceptionCodeRegistration implements InitializingBean {

    @Override
    public void afterPropertiesSet() {
        ExceptionCodeResolver.register(FlowException.class, ExceptionErrorCode.REQ_LIMIT);
        ExceptionCodeResolver.register(AuthorityException.class, ExceptionErrorCode.REQ_BLACKLIST);
        ExceptionCodeResolver.register(SystemBlockException.class, ExceptionErrorCode.SYSTEM_BLOCK);
        ExceptionCodeResolver.register(ParamFlowException.class, ExceptionErrorCode.PARAM_FLOW);
        ExceptionCodeResolver.register(DegradeException.class, ExceptionErrorCode.FALLBACK_EXCEPTION);
    }
}
