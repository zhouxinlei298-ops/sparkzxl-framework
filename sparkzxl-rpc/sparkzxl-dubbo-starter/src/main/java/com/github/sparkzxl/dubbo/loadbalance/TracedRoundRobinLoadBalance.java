package com.github.sparkzxl.dubbo.loadbalance;

import cn.hutool.core.util.IdUtil;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.common.URL;
import org.apache.dubbo.rpc.Invocation;
import org.apache.dubbo.rpc.Invoker;
import org.apache.dubbo.rpc.cluster.loadbalance.AbstractLoadBalance;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 基于请求链路追踪的 Dubbo 负载均衡，复刻 Spring Cloud 侧 {@code TracedRoundRobinLoadBalancer} 的核心算法。
 * <p>
 * 以 traceId 为维度追踪每个请求已调用的 IP 和网段，在重试场景下优先选择未调用过的实例，
 * 实现同一请求的多次调用尽可能分散到不同机器和网段，提高容灾能力。
 * <p>
 * 算法流程：
 * 1. 从 {@link RequestLocalContextHolder} 提取 traceId（已由 RequestContextFilter 通过 Dubbo attachments 传播）
 * 2. 随机打乱 invoker 列表，避免固定顺序导致的热点问题
 * 3. 按"未调用过的 IP 优先 → 未调用过的网段优先"两级排序
 * 4. 选择排序后的第一个 invoker，并记录其 IP 和网段到 Caffeine 缓存（3 分钟 TTL）
 * 5. 使用 traceId 级别的细粒度锁（{@link #traceLocks}），确保同一请求的排序+选择+记录是原子操作
 *
 * @author zhouxinlei
 */
@Slf4j
@SuppressWarnings("ALL")
public class TracedRoundRobinLoadBalance extends AbstractLoadBalance {

    private static final String NAME = "tracedRoundRobin";

    // 记录每个请求的调用位置（兼容原有逻辑）
    private final LoadingCache<String, AtomicInteger> positionCache = Caffeine.newBuilder()
            .expireAfterWrite(3, TimeUnit.MINUTES)
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

    @Override
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        if (invokers.size() == 1) {
            return invokers.get(0);
        }

        // 去重
        List<Invoker<T>> distinctInvokers = invokers.stream().distinct().collect(Collectors.toList());
        String traceId = getTraceId();

        return selectByTraceId(traceId, distinctInvokers);
    }

    private String getTraceId() {
        String traceId = RequestLocalContextHolder.traceId();
        if (StringUtils.isEmpty(traceId) || "N/A".equals(traceId)) {
            return IdUtil.fastSimpleUUID() + "." + IdUtil.getSnowflakeNextId();
        }
        return traceId;
    }

    private <T> Invoker<T> selectByTraceId(String traceId, List<Invoker<T>> invokers) {
        Object lock = traceLocks.get(traceId);
        synchronized (lock) {
            Collections.shuffle(invokers);

            Map<Invoker<T>, Integer> usedFlags = Maps.newHashMap();

            List<Invoker<T>> sortedInvokers = invokers.stream()
                    .sorted(Comparator
                            .<Invoker<T>>comparingInt(invoker ->
                                    usedFlags.computeIfAbsent(invoker, k ->
                                            calledIps.get(traceId).contains(invoker.getUrl().getHost()) ? 1 : 0))
                            .thenComparingInt(invoker ->
                                    usedFlags.computeIfAbsent(invoker, k -> {
                                        String ipPrefix = getIpPrefix(invoker.getUrl().getHost());
                                        return calledIpPrefixes.get(traceId).contains(ipPrefix) ? 1 : 0;
                                    }))
                    ).collect(Collectors.toList());

            if (sortedInvokers.isEmpty()) {
                log.warn("No available invokers for traceId: {}", traceId);
                return invokers.get(0);
            }

            Invoker<T> selected = sortedInvokers.get(0);
            String host = selected.getUrl().getHost();
            log.info("Selected invoker for request [{}]: {}:{}", traceId, host, selected.getUrl().getPort());

            calledIps.get(traceId).add(host);
            calledIpPrefixes.get(traceId).add(getIpPrefix(host));
            positionCache.get(traceId).getAndIncrement();

            return selected;
        }
    }

    private String getIpPrefix(String host) {
        int lastDotIndex = host.lastIndexOf(".");
        return lastDotIndex > 0 ? host.substring(0, lastDotIndex) : host;
    }
}
