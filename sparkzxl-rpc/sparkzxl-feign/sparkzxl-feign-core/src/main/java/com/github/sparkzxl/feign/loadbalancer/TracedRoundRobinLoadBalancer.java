package com.github.sparkzxl.feign.loadbalancer;

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
 * description: 改进负载均衡算法（移除CircuitBreaker相关逻辑）
 * <p>
 * 思路： 针对每次请求，记录：
 * 1. 本次请求已经调用过哪些实例 -> 请求调用过的实例缓存
 * 2. 随机将实例列表打乱，防止在指标相同时总是调用同一个实例
 * 3. 按照“未调用过的实例优先 -> 未调用过的网段优先”排序
 * 4. 取排序后的第一个实例作为本次负载均衡结果
 * <p>
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

    private ServiceInstanceListSupplier serviceInstanceListSupplier;
    private String serviceId;

    public TracedRoundRobinLoadBalancer(
            ServiceInstanceListSupplier serviceInstanceListSupplier,
            String serviceId) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
        this.serviceId = serviceId;
    }

    public void setServiceInstanceListSupplier(ServiceInstanceListSupplier serviceInstanceListSupplier) {
        this.serviceInstanceListSupplier = serviceInstanceListSupplier;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    @Override
    public Mono<Response<ServiceInstance>> choose(Request request) {
        return serviceInstanceListSupplier.get().next()
                .map(serviceInstances -> getInstanceResponse(serviceInstances, request));
    }

    private Response<ServiceInstance> getInstanceResponse(List<ServiceInstance> serviceInstances, Request request) {
        if (serviceInstances.isEmpty()) {
            log.warn("No servers available for service: " + this.serviceId);
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
     * @param request
     * @return
     */
    private String getTraceIdFromRequest(Request request) {
        // 示例：若Request是HttpRequest，可从header获取；此处简化为UUID
        RequestDataContext context = (RequestDataContext) request.getContext();
        if (ObjectUtils.isEmpty(context)) {
            return IdUtil.fastSimpleUUID();
        }

        String traceId = context.getClientRequest().getHeaders().getFirst(BaseContextConstants.TRACE_ID);
        if (StringUtils.isEmpty(traceId)) {
            return traceId = IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
        }
        return traceId;
    }

    public Response<ServiceInstance> getInstanceResponseByRoundRobin(String traceId, List<ServiceInstance> serviceInstances) {
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

    /**
     * 获取IP的网段前缀（如192.168.1.100 -> 192.168.1）
     */
    private String getIpPrefix(String host) {
        int lastDotIndex = host.lastIndexOf(".");
        return lastDotIndex > 0 ? host.substring(0, lastDotIndex) : host;
    }
}
