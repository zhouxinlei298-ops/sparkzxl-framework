package com.github.sparkzxl.oss.operation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.provider.JdbcOssConfigProvider;
import com.github.sparkzxl.oss.provider.OssConfigProvider;

import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 验证 OSS 操作契约与资源安全边界。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public class OssOperationRuntimeTest {

    static Configuration config(String id, String bucket, long revision) {
        Configuration c = new Configuration();
        c.setClientId(id);
        c.setClientType("minio");
        c.setEndpoint("https://storage.example");
        c.setBucketName(bucket);
        c.setAccessKey("test-access");
        c.setSecretKey("test-secret");
        c.setRevision(revision);
        c.setObjectPrefix("allowed/");
        return c;
    }

    /**
     * Fixture 的内部协作类型。
     *
     * @author zhouxinlei
     * @since 2026-10-06
     * @see OssOperationContextManager
     */
    static class Fixture extends OssExecutorFactoryContext {

        final Map<String, AtomicInteger> closed = new HashMap<>();

        final List<String> writes = Collections.synchronizedList(new ArrayList<>());

        final AtomicInteger builds = new AtomicInteger();

        boolean failBuild;

        Fixture(OssConfigProvider p) {
            super(p, null);
        }

        @Override
        protected OssExecutor buildExecutor(Configuration c) {
            if (failBuild) {
                throw new IllegalStateException("test failure");
            }
            builds.incrementAndGet();
            String key = c.getClientId() + ":" + c.getRevision();
            AtomicInteger stops = new AtomicInteger();
            closed.put(key, stops);
            return (OssExecutor)
                    Proxy.newProxyInstance(
                            OssExecutor.class.getClassLoader(),
                            new Class<?>[] {OssExecutor.class},
                            (proxy, method, args) -> {
                                if (method.getName().equals("shutdown")) {
                                    stops.incrementAndGet();
                                    return null;
                                }
                                if (method.getName().equals("obtainConfigInfo")) {
                                    return c;
                                }
                                if (method.getName().equals("exists")) {
                                    writes.add(c.getClientId() + ":" + args[0]);
                                    return true;
                                }
                                if (method.getName().equals("getObjectUrl")) {
                                    return c.getClientId() + ":" + args[0];
                                }
                                return null;
                            });
        }
    }

    /**
     * Provider 的内部协作类型。
     *
     * @author zhouxinlei
     * @since 2026-10-06
     * @see OssOperationContextManager
     */
    static class Provider implements OssConfigProvider {

        final Map<String, Configuration> data = new HashMap<>();

        final AtomicInteger loads = new AtomicInteger();

        Provider(Configuration... cs) {
            for (Configuration c : cs) {
                data.put(c.getClientId(), c);
            }
        }

        /**
         * 读取显式目标的防御性配置副本。
         *
         * @param id 显式客户端标识
         * @return 目标配置副本；未知标识返回 null
         */
        public Configuration load(String id) {
            loads.incrementAndGet();
            return data.get(id);
        }

        /**
         * 读取可用配置快照集合。
         *
         * @return 配置快照集合
         */
        public List<Configuration> loadConfigurationList() {
            return new ArrayList<>(data.values());
        }
    }

    private static void rejected(Runnable r) {
        try {
            r.run();
            fail("Expected operation rejection");
        } catch (IllegalStateException | IllegalArgumentException expected) {
        }
    }

    /** 验证一次解析及配置防御性复制。 */
    /** 验证显式快照模式在首次配置前不读取备用提供器，空快照移除目标。 */
    @Test
    public void explicitSnapshotsNeverFallBackToProvider() {
        OssExecutorFactoryContext factory =
                new OssExecutorFactoryContext(
                        new OssConfigProvider() {

                            @Override
                            public Configuration load(String clientId) {
                                throw new AssertionError(
                                        "Fallback configuration must not be loaded");
                            }

                            @Override
                            public List<Configuration> loadConfigurationList() {
                                throw new AssertionError(
                                        "Fallback configuration must not be loaded");
                            }
                        },
                        null);
        factory.requireConfigurationSnapshot();
        assertThrows(IllegalStateException.class, () -> factory.open("client-a"));
        assertTrue(factory.refresh(1, Collections.emptyList()));
        assertThrows(IllegalStateException.class, () -> factory.open("client-a"));
        assertFalse(factory.refresh(1, Collections.emptyList()));
        factory.destroy();
    }

    @Test
    public void resolvesOnceAndDefendsConfigurationMutations() {
        Configuration c = config("a", "bucket-a", 1);
        Provider p = new Provider(c);
        Fixture f = new Fixture(p);
        try (OssOperationContextScope scope = OssOperationContextManager.open(f, "a")) {
            OssOperationHandle h = scope.getHandle();
            c.setBucketName("wrong");
            c.setSecretKey("changed");
            h.executor().obtainConfigInfo().setBucketName("another");
            assertTrue(h.executor().exists("bucket-a", "allowed/file"));
            assertEquals("a:bucket-a", h.executor().getObjectUrl("bucket-a", "allowed/file"));
            assertEquals("bucket-a", h.getContext().getBucketName());
            try (OssOperationContextScope same = OssOperationContextManager.open(f, "a")) {
                assertSame(h, same.getHandle());
            }
            assertEquals(1, p.loads.get());
            assertEquals(1, f.builds.get());
        }
        rejected(OssOperationContextManager::requiredHandle);
        f.destroy();
    }

    /** 验证嵌套异常恢复外层，拒绝隐式替换目标。 */
    @Test
    public void nestedExceptionRestoresOuterAndRejectsImplicitReplacement() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1), config("b", "b", 1)));
        try (OssOperationContextScope outer = OssOperationContextManager.open(f, "a")) {
            rejected(() -> OssOperationContextManager.open(f, "b"));
            try (OssOperationContextScope inner =
                    OssOperationContextManager.openIndependent(f, "b")) {
                assertEquals(
                        "b",
                        OssOperationContextManager.requiredHandle().getContext().getClientId());
                throw new IllegalArgumentException("test callback");
            } catch (IllegalArgumentException expected) {
            }
            assertSame(outer.getHandle(), OssOperationContextManager.requiredHandle());
        }
        rejected(OssOperationContextManager::requiredHandle);
        f.destroy();
    }

    /** 验证错误关闭顺序不会破坏操作域栈。 */
    @Test
    public void wrongCloseOrderPreservesStack() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1), config("b", "b", 1)));
        OssOperationContextScope a = OssOperationContextManager.open(f, "a");
        OssOperationContextScope b = OssOperationContextManager.openIndependent(f, "b");
        rejected(a::close);
        assertSame(b.getHandle(), OssOperationContextManager.requiredHandle());
        b.close();
        a.close();
        a.close();
        f.destroy();
    }

    /** 验证相同标识不能复用另一工厂的客户端。 */
    @Test
    public void sameIdCannotReuseAnotherRuntime() {
        Fixture a = new Fixture(new Provider(config("a", "a", 1)));
        Fixture b = new Fixture(new Provider(config("a", "b", 1)));
        try (OssOperationContextScope scope = OssOperationContextManager.open(a, "a")) {
            rejected(() -> OssOperationContextManager.open(b, "a"));
        }
        a.destroy();
        b.destroy();
    }

    /** 验证刷新保留在途客户端，最后引用释放后关闭。 */
    @Test
    public void refreshKeepsInFlightClientAndRetiresAtLastRelease() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1)));
        OssOperationHandle old = f.open("a");
        assertTrue(f.refresh(1, Arrays.asList(config("a", "new", 2))));
        assertEquals(0, f.closed.get("a:1").get());
        assertTrue(old.executor().exists("a", "allowed/file"));
        try (OssOperationHandle next = f.open("a")) {
            assertEquals("new", next.getContext().getBucketName());
        }
        old.close();
        old.close();
        assertEquals(1, f.closed.get("a:1").get());
        f.destroy();
        assertEquals(1, f.closed.get("a:2").get());
    }

    /** 验证失败刷新不部分生效并拒绝版本内容冲突。 */
    @Test
    public void failedRefreshIsAtomicAndVersionConflictsFailClosed() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1)));
        f.refresh(2, Arrays.asList(config("a", "a", 1)));
        assertFalse(f.refresh(1, Arrays.asList(config("b", "b", 1))));
        assertFalse(f.refresh(2, Arrays.asList(config("a", "a", 1))));
        rejected(() -> f.refresh(2, Arrays.asList(config("a", "different", 1))));
        f.failBuild = true;
        rejected(() -> f.refresh(3, Arrays.asList(config("b", "b", 1))));
        try (OssOperationHandle h = f.open("a")) {
            assertEquals("a", h.getContext().getBucketName());
        }
        f.failBuild = false;
        f.refresh(4, Collections.emptyList());
        rejected(() -> f.open("a"));
        f.destroy();
    }

    /** 验证缺失目标、错误 Bucket 与路径穿越被拒绝。 */
    @Test
    public void missingTargetWrongBucketAndTraversalRejected() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1)));
        rejected(() -> f.open(null));
        rejected(() -> f.open("missing"));
        try (OssOperationHandle h = f.open("a")) {
            rejected(() -> h.executor().exists("wrong", "allowed/file"));
            for (String key :
                    Arrays.asList(
                            "other/file", "allowed/../file", "/allowed/file", "allowed//file")) {
                rejected(() -> h.executor().exists("a", key));
            }
        }
        assertTrue(f.writes.isEmpty());
        f.destroy();
    }

    /** 验证异步缺域、线程池复用与跨线程句柄访问边界。 */
    @Test
    public void asyncMissingScopeThreadReuseAndCrossThreadHandleRejected() throws Exception {
        Fixture f = new Fixture(new Provider(config("a", "a", 1)));
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try (OssOperationContextScope outer = OssOperationContextManager.open(f, "a")) {
            OssOperationHandle h = outer.getHandle();
            pool.submit(
                            () -> {
                                rejected(OssOperationContextManager::requiredHandle);
                                rejected(h::executor);
                            })
                    .get();
            pool.submit(
                            () -> {
                                try (OssOperationContextScope own =
                                        OssOperationContextManager.open(f, "a")) {
                                    assertEquals("a", own.getHandle().getContext().getClientId());
                                }
                            })
                    .get();
            pool.submit(() -> rejected(OssOperationContextManager::requiredHandle)).get();
        } finally {
            pool.shutdownNow();
            f.destroy();
        }
    }

    /** 验证双客户端并发使用各自固定 Bucket。 */
    @Test
    public void twoClientConcurrencyUsesActualBoundBuckets() throws Exception {
        Fixture f =
                new Fixture(new Provider(config("a", "bucket-a", 1), config("b", "bucket-b", 1)));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (String id : Arrays.asList("a", "b")) {
                tasks.add(
                        pool.submit(
                                () -> {
                                    for (int i = 0; i < 50; i++) {
                                        try (OssOperationHandle h = f.open(id)) {
                                            h.executor().exists("bucket-" + id, "allowed/file");
                                        }
                                    }
                                }));
            }
            for (Future<?> task : tasks) {
                task.get();
            }
            assertEquals(100, f.writes.size());
            for (String write : f.writes) {
                assertTrue(write.equals("a:bucket-a") || write.equals("b:bucket-b"));
            }
        } finally {
            pool.shutdownNow();
            f.destroy();
        }
    }

    /** 验证未知配置不缓存且无效刷新不污染缓存。 */
    @Test
    public void jdbcNeverCachesUnknownAndRejectsWrongIdOrInvalidRefresh() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new JdbcOssConfigProvider(null, Collections::emptyList));
        assertThrows(
                IllegalArgumentException.class, () -> new JdbcOssConfigProvider(id -> null, null));
        AtomicInteger queries = new AtomicInteger();
        JdbcOssConfigProvider p =
                new JdbcOssConfigProvider(
                        id -> {
                            queries.incrementAndGet();
                            return null;
                        },
                        Collections::emptyList);
        assertNull(p.load("unknown"));
        assertNull(p.load("unknown"));
        assertEquals(2, queries.get());
        JdbcOssConfigProvider mismatch =
                new JdbcOssConfigProvider(id -> config("other", "a", 1), Collections::emptyList);
        rejected(() -> mismatch.load("a"));
        Configuration c = config("a", "a", 1);
        JdbcOssConfigProvider valid = new JdbcOssConfigProvider(id -> c, () -> Arrays.asList(c, c));
        valid.load("a");
        rejected(valid::refreshCache);
        c.setBucketName("mutated");
        assertEquals("a", valid.load("a").getBucketName());
    }

    /** 验证销毁工厂后等待在途句柄释放再关闭客户端。 */
    @Test
    public void destroyDefersCloseUntilOutstandingHandleReleases() {
        Fixture f = new Fixture(new Provider(config("a", "a", 1)));
        OssOperationHandle h = f.open("a");
        f.destroy();
        f.destroy();
        assertEquals(0, f.closed.get("a:1").get());
        rejected(() -> f.open("a"));
        assertTrue(h.executor().exists("a", "allowed/file"));
        h.close();
        assertEquals(1, f.closed.get("a:1").get());
        rejected(h::executor);
    }
}
