package com.github.sparkzxl.dubbo.fallback;

import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.Result;
import org.apache.dubbo.rpc.RpcException;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dubbo服务降级处理器注册中心
 * <p>
 * 管理服务级降级处理器。
 * </p>
 * <p>
 * 数据结构：Map&lt;接口名, 降级处理器&gt;
 * </p>
 * <p>
 * clientName 用于标识服务名称，方便日志输出和问题排查，不影响降级匹配逻辑。
 * </p>
 *
 * @author zhouxinlei
 * @since 2026-03-20
 */
@Slf4j
public class DubboFallbackRegistry {

    /**
     * 服务级降级处理器缓存
     * Key: 服务接口全限定名
     * Value: 降级处理器
     */
    private static final Map<String, FallbackHandlerHolder> SERVICE_FALLBACK_MAP = new ConcurrentHashMap<>();

    private DubboFallbackRegistry() {
        // 工具类禁止实例化
    }

    /**
     * 注册服务级降级处理器
     *
     * @param interfaceName 服务接口全限定名
     * @param clientName    客户端名称（用于标识和日志）
     * @param handler       降级处理器
     */
    public static void registerService(String interfaceName, String clientName, DubboFallbackHandler handler) {
        if (interfaceName == null || interfaceName.isEmpty()) {
            throw new IllegalArgumentException("interfaceName cannot be null or empty");
        }
        if (clientName == null || clientName.isEmpty()) {
            throw new IllegalArgumentException("clientName cannot be null or empty");
        }
        if (handler == null) {
            throw new IllegalArgumentException("handler cannot be null");
        }

        SERVICE_FALLBACK_MAP.put(interfaceName, new FallbackHandlerHolder(handler, clientName));

        log.info("[Dubbo降级] 注册服务级降级处理器: interface={}, client={}", interfaceName, clientName);
    }

    /**
     * 获取降级处理器
     * <p>
     * 通过接口名匹配降级处理器，clientName 用于日志追踪。
     * </p>
     *
     * @param invoker    服务调用器
     * @return 降级处理器，如果没有配置则返回默认降级处理器
     */
    public static FallbackHandlerHolder getFallbackHandler(Invoker<?> invoker) {
        String interfaceName = invoker.getInterface().getName();
        FallbackHandlerHolder holder = SERVICE_FALLBACK_MAP.get(interfaceName);

        if (holder == null) {
            // 没有配置降级处理器，使用全局默认
            log.debug("[Dubbo降级] 使用全局默认降级: interface={}", interfaceName);
            return new FallbackHandlerHolder(DefaultDubboFallbackHandler.INSTANCE, "");
        }

        log.debug("[Dubbo降级] 使用配置的降级处理器: interface={}, client={}", interfaceName, holder.getClientName());
        return holder;
    }

    /**
     * 执行降级处理
     *
     * @param invoker    服务调用器
     * @param invocation 调用信息
     * @param exception  原始异常
     * @return Result 降级后的调用结果
     */
    public static Result executeFallback(Invoker<?> invoker, Invocation invocation, RpcException exception) {
        FallbackHandlerHolder holder = getFallbackHandler(invoker);
        DubboFallbackHandler handler = holder.getHandler();
        String registeredClientName = holder.getClientName();

        try {
            // 使用注册时配置的 clientName（来自 @DubboFallback 的 value）
            return handler.handle(invoker, invocation, exception, registeredClientName);
        } catch (Exception e) {
            // 自定义降级处理器执行失败，回退到默认降级
            log.error("[Dubbo降级] 自定义降级处理器执行异常，回退到默认降级: interface={}, client={}, error={}",
                    invoker.getInterface().getName(), registeredClientName, e.getMessage(), e);
            return DefaultDubboFallbackHandler.INSTANCE.handle(invoker, invocation, exception, registeredClientName);
        }
    }

    /**
     * 检查是否存在服务级降级处理器
     *
     * @param interfaceName 服务接口全限定名
     * @return 是否存在降级处理器
     */
    public static boolean hasServiceFallback(String interfaceName) {
        return SERVICE_FALLBACK_MAP.containsKey(interfaceName);
    }
}
