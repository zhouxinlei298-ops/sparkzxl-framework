package com.github.sparkzxl.dubbo.fallback;

import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

/**
 * Dubbo服务降级处理器函数式接口
 * <p>
 * 用于定义Dubbo服务调用失败时的自定义降级逻辑。
 * 支持服务级降级策略配置。
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@FunctionalInterface
public interface DubboFallbackHandler {

    /**
     * 处理Dubbo服务调用失败时的降级逻辑
     *
     * @param invoker    服务调用器，包含服务接口信息
     * @param invocation 调用信息，包含方法名和参数
     * @param exception  原始RpcException异常
     * @param clientName 客户端名称
     * @return Result 降级后的调用结果，可以返回默认值、缓存值或空结果
     */
    Result handle(Invoker<?> invoker, Invocation invocation, RpcException exception, String clientName);
}
