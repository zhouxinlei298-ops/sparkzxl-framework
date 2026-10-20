package com.github.sparkzxl.oss.operation;

import com.github.sparkzxl.oss.entity.OssMetadata;
import com.github.sparkzxl.oss.entity.PartData;
import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.properties.Configuration;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 固定配置与客户端代际的操作句柄，关闭时释放在途引用。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see OssOperationContextManager
 */
public final class OssOperationHandle implements AutoCloseable {

    private final OssOperationContext context;

    private final Configuration configuration;

    private final Runnable release;

    private final Thread owner = Thread.currentThread();

    private final OssExecutor view;

    private boolean closed;

    /**
     * 持有不可变目标、配置副本及执行器代际；关闭时调用引用释放回调。
     *
     * @param context 不可变操作目标
     * @param configuration 已解析的配置快照
     * @param executor 固定代际执行器
     * @param release 释放客户端引用的回调
     */
    public OssOperationHandle(
            OssOperationContext context,
            Configuration configuration,
            OssExecutor executor,
            Runnable release) {
        this.context = context;
        this.configuration = configuration.copy();
        this.release = release;
        this.view =
                (OssExecutor)
                        Proxy.newProxyInstance(
                                OssExecutor.class.getClassLoader(),
                                new Class<?>[] {OssExecutor.class},
                                (proxy, method, args) -> {
                                    assertOpen();
                                    String name = method.getName();
                                    if (name.equals("toString")) {
                                        return "OssOperationExecutor";
                                    }
                                    if (name.equals("hashCode")) {
                                        return System.identityHashCode(proxy);
                                    }
                                    if (name.equals("equals")) {
                                        return proxy == args[0];
                                    }
                                    boolean write =
                                            name.equals("putObject")
                                                    || name.equals("multipartUpload")
                                                    || name.equals("initMultiPartUpload")
                                                    || name.equals("getListParts")
                                                    || name.equals("completeMultipartUpload")
                                                    || name.equals("mergeMultipartUpload")
                                                    || name.equals("abortMultipartUpload")
                                                    || name.equals("removeObject")
                                                    || name.equals("getPresignedObjectUploadUrl");
                                    if (write && !this.configuration.isWriteEnabled()) {
                                        throw new IllegalStateException("OSS writes are disabled");
                                    }
                                    if (!write
                                            && (name.equals("getObjectUrl")
                                                    || name.equals("getObjectInfo")
                                                    || name.equals("getOssMetadata")
                                                    || name.equals("exists")
                                                    || name.equals("downloadFile")
                                                    || name.equals("downloadMultipartFile"))
                                            && !this.configuration.isReadEnabled()) {
                                        throw new IllegalStateException("OSS reads are disabled");
                                    }
                                    if (name.equals("getObjectInfo")) {
                                        throw new UnsupportedOperationException(
                                                "Use synchronous downloadFile callback within the"
                                                        + " handle");
                                    }
                                    if (name.equals("obtainConfigInfo")) {
                                        return this.configuration.copy();
                                    }
                                    if (name.equals("shutdown")
                                            || name.equals("createBucket")
                                            || name.equals("removeBucket")
                                            || name.equals("setBucketPolicy")) {
                                        throw new UnsupportedOperationException(
                                                "Management operation requires a separate"
                                                        + " capability");
                                    }
                                    if (args != null
                                            && args.length >= 2
                                            && args[0] instanceof String
                                            && args[1] instanceof String) {
                                        context.assertObject((String) args[0], (String) args[1]);
                                    } else if (name.equals("initMultiPartUpload")) {
                                        context.assertObject((String) args[1], (String) args[2]);
                                    }
                                    try {
                                        return method.invoke(executor, args);
                                    } catch (InvocationTargetException e) {
                                        throw e.getCause();
                                    }
                                });
    }

    /**
     * 获取不可变操作目标
     *
     * @return 不可变操作目标
     */
    public OssOperationContext getContext() {
        assertOpen();
        return context;
    }

    /**
     * 获取受目标约束的执行器视图。
     *
     * @return 禁止越过固定 Bucket 和前缀的执行器视图
     */
    public OssExecutor executor() {
        assertOpen();
        return view;
    }

    /**
     * 校验真实分片清单后合并，并回读对象大小。
     *
     * @param object 目标前缀内的对象键
     * @param uploadId 供应商分片上传标识
     * @param expectedSize 对象预期大小（字节）
     * @param partSize 标准分片大小（字节）
     * @param count 预期分片数
     * @return 合并后回读得到的实际对象元信息
     */
    public OssMetadata completeMultipartUpload(
            String object, String uploadId, long expectedSize, long partSize, int count) {
        assertOpen();
        if (uploadId == null || uploadId.trim().isEmpty()) {
            throw new IllegalArgumentException("Upload ID required");
        }
        String bucket = context.getBucketName();
        context.assertObject(bucket, object);
        List<PartData> verified =
                MultipartPartsValidator.validate(
                        view.getListParts(bucket, object, uploadId), expectedSize, partSize, count);
        view.completeMultipartUpload(bucket, object, uploadId, verified);
        return verifyObject(object, expectedSize);
    }

    /**
     * 读取实际对象元信息并校验预期大小。
     *
     * @param object 目标前缀内的对象键
     * @param expectedSize 对象预期大小（字节）
     * @return 大小校验通过的实际对象元信息
     */
    public OssMetadata verifyObject(String object, long expectedSize) {
        assertOpen();
        if (expectedSize <= 0) {
            throw new IllegalArgumentException("Expected object size must be positive");
        }
        OssMetadata metadata = view.getOssMetadata(context.getBucketName(), object);
        if (metadata == null || metadata.getSize() != expectedSize) {
            throw new IllegalStateException("OSS actual object size mismatch");
        }
        return metadata;
    }

    /** 校验句柄未关闭且由创建线程使用。 */
    public void assertOpen() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("OSS handles cannot cross threads");
        }
        if (closed) {
            throw new IllegalStateException("OSS operation handle is closed");
        }
    }

    /** 关闭当前资源并释放持有的引用。 */
    @Override
    public void close() {
        if (Thread.currentThread() != owner) {
            throw new IllegalStateException("OSS handle closed by another thread");
        }
        if (!closed) {
            closed = true;
            release.run();
        }
    }
}
