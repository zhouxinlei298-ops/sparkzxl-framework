package com.github.sparkzxl.dubbo.filter;

import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.dubbo.fallback.DubboFallbackRegistry;
import com.github.sparkzxl.dubbo.properties.DubboConsumerProperties;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.extension.Activate;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;
import org.apache.dubbo.rpc.cluster.filter.ClusterFilter;

/**
 * description: RPC异常将降级处理过滤器
 *
 * @author zhouxinlei
 * @version 1.0
 * @since 2026-03-19 14:57:48
 */
@Slf4j
@Getter
@Setter
@Activate(group = CommonConstants.CONSUMER, order = -10000)
public class RpcFallbackFilter implements ClusterFilter {

    private DubboConsumerProperties consumerProperties;

    @Override
    public Result invoke(Invoker<?> invoker, Invocation invocation) throws RpcException {
        if (ObjectUtils.isEmpty(consumerProperties)) {
            DubboConsumerProperties consumerProperties = SpringContextUtils.getBean(DubboConsumerProperties.class);
            setConsumerProperties(consumerProperties);
        }
        try {
            return invoker.invoke(invocation);
        } catch (RpcException e) {
            if (consumerProperties.isFallback()) {
                return DubboFallbackRegistry.executeFallback(invoker, invocation, e);
            } else {
                throw e;
            }
        }
    }
}
