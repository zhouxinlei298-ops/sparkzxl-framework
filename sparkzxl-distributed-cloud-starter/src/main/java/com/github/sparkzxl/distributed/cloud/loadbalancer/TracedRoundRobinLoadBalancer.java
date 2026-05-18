package com.github.sparkzxl.distributed.cloud.loadbalancer;

import cn.hutool.core.util.IdUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.sparkzxl.core.constant.BaseContextConstants;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.client.loadbalancer.*;
import org.springframework.cloud.loadbalancer.core.ReactorServiceInstanceLoadBalancer;
import org.springframework.cloud.loadbalancer.core.ServiceInstanceListSupplier;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于请求链路追踪的负载均衡器，配合 {@link SameZoneOnlyServiceInstanceListSupplier} 实现同 Zone 智能路由。
 * <p>
 * 核心思路：以 traceId 为维度追踪每个请求已调用的 IP 和网段，在重试场景下优先选择未调用过的实例，
 * 实现同一请求的多次调用尽可能分散到不同机器和网段，提高容灾能力。
 * <p>
 * 算法流程：
 * 1. 从请求 Header 中提取 traceId，无则生成唯一标识
 * 2. 随机打乱实例列表，避免固定顺序导致的热点问题
 * 3. 按”未调用过的 IP 优先 → 未调用过的网段优先”两级排序
 * 4. 选择排序后的第一个实例，并记录其 IP 和网段到 Caffeine 缓存（3 分钟 TTL）
 * 5. 使用 traceId 级别的细粒度锁（{@link #traceLocks}），确保同一请求的排序+选择+记录是原子操作
 *
 * @author zhouxinlei
 * @since 2022-12-01 10:10:16
 */
@Slf4j
@SuppressWarnings("ALL")
public class TracedRoundRobinLoadBalancer implements ReactorServiceInstanceLoadBalancer {

    /**
     * 每次请求算上重试不会超过3分钟，超过3分钟的请求不重试
     */
    // 记录每个请求的调用位置（兼容原有逻辑）
    private final LoadingCache<String, AtomicInteger> positionCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
            // 随机初始值，防止每次从第一个实例开始调用
            .build(k -> new AtomicInteger(ThreadLocalRandom.current().nextInt(0, 1000)));

    // 记录每个请求已调用过的IP网段（用于排序过滤）
    private final LoadingCache<String, Set<String>> calledIpPrefixes = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> Sets.newConcurrentHashSet());

    // 记录每个请求已调用过的IP（用于排序过滤）
    private final LoadingCache<String, Set<String>> calledIps = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> Sets.newConcurrentHashSet());

    // 每个 traceId 的锁，确保同一请求的排序+选择+记录是原子操作
    private final LoadingCache<String, Object> traceLocks = Caffeine.newBuilder()
            .expireAfterAccess(3, TimeUnit.MINUTES)
            .build(k -> new Object());

    private final ServiceInstanceListSupplier serviceInstanceListSupplier;
    private final String serviceId;

    public TracedRoundRobinLoadBalancer(
            ServiceInstanceListSupplier serviceInstanceListSupplier,
            String serviceId) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
        this.serviceId = serviceId;
    }


    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return serviceInstanceListSupplier.get().next()
                .map(serviceInstances -> getInstanceResponse(serviceInstances, request));
    }

    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> serviceInstances, Request request) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: {}", this.serviceId);
            return new EmptyResponse();
        }
        // 去重实例列表
        serviceInstances = serviceInstances.stream().distinct().collect(Collectors.toList());
        // 获取请求唯一标识（替代原traceId逻辑，可根据实际需求调整）
        String traceId = getTraceIdFromRequest(request);
        return getInstanceResponseByRoundRobin(traceId, serviceInstances);
    }

    /**
     * 从请求中获取唯一标识traceId
     *
     * @param request request
     * @return  String
     */
    private String getTraceIdFromRequest(Request request) {
        // 示例：若Request是HttpRequest，可从header获取；此处简化为UUID
        RequestDataContext context = (RequestDataContext) request.getContext();
        if (ObjectUtils.isEmpty(context)) {
            return IdUtil.fastSimpleUUID();
        }

        String traceId = context.getClientRequest().getHeaders().getFirst(BaseContextConstants.TRACE_ID_HEADER);
        if (StringUtils.isEmpty(traceId)) {
            return IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
        }
        return traceId;
    }

    public Response<ServiceInstance> getInstanceResponseByRoundRobin(String traceId, List<ServiceInstance> serviceInstances) {
        // 使用 traceId 级别的锁，确保同一请求的排序+选择+记录是原子操作
        // 不同 traceId 之间互不影响，不会成为全局瓶颈
        Object lock = traceLocks.get(traceId);
        synchronized (lock != null ? lock : new Object()) {
            // 随机打乱实例列表，避免固定顺序
            Collections.shuffle(serviceInstances);

            // 缓存排序参数，避免比较器多次计算
            Map<ServiceInstance, Integer> usedFlags = Maps.newHashMap();

            // 排序逻辑：未调用过的IP优先 -> 未调用过的网段优先
            List<ServiceInstance> sortedInstances = serviceInstances.stream()
                    .sorted(Comparator
                            // 已调用过的IP排后面（0：未调用，1：已调用）
                            .<ServiceInstance>comparingInt(instance ->
                                    usedFlags.computeIfAbsent(instance, k ->
                                            calledIps.get(traceId).contains(instance.getHost()) ? 1 : 0))
                            // 已调用过的网段排后面（0：未调用，1：已调用）
                            .thenComparingInt(instance ->
                                    usedFlags.computeIfAbsent(instance, k -> {
                                        String ipPrefix = getIpPrefix(instance.getHost());
                                        return calledIpPrefixes.get(traceId).contains(ipPrefix) ? 1 : 0;
                                    }))
                    ).collect(Collectors.toList());

            if (sortedInstances.isEmpty()) {
                log.warn("No available instances for service: " + serviceId);
                return new EmptyResponse();
            }

            // 选择排序后的第一个实例
            ServiceInstance selectedInstance = sortedInstances.get(0);
            log.info("Selected instance for request [{}]: {}:{}", traceId, selectedInstance.getHost(), selectedInstance.getPort());

            // 记录本次调用的IP和网段（用于后续请求排序）
            calledIps.get(traceId).add(selectedInstance.getHost());
            calledIpPrefixes.get(traceId).add(getIpPrefix(selectedInstance.getHost()));

            // 兼容原有计数逻辑
            positionCache.get(traceId).getAndIncrement();

            return new DefaultResponse(selectedInstance);
        }
    }

    /**
     * 获取IP的网段前缀（如192.168.1.100 -> 192.168.1）
     */
    private String getIpPrefix(String host) {
        int lastDotIndex = host.lastIndexOf(".");
        return lastDotIndex > 0 ? host.substring(0, lastDotIndex) : host;
    }
}
