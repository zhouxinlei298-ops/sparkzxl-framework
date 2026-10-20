package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.oss.client.*;
import com.github.sparkzxl.oss.entity.PartData;
import com.github.sparkzxl.oss.properties.Configuration;
import io.minio.*;
import io.minio.messages.*;
import io.minio.errors.ErrorResponseException;
import okhttp3.Headers;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import java.io.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 验证 OSS 操作契约与资源安全边界。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 * @see java.lang.AutoCloseable
 */
public class MinioResourceContractTest {

    private static Configuration config() {
        Configuration c = new Configuration();
        c.setClientId("test");
        c.setClientType("minio");
        c.setEndpoint("http://localhost:9000");
        c.setBucketName("bucket");
        return c;
    }

    private static MinioExecutor executor(CustomMinioClient raw) {
        return new MinioExecutor(new OssClient<CustomMinioClient>() {

            /**
             * 获取当前代际持有的原生客户端
             * @return 当前代际持有的原生客户端
             */
            public CustomMinioClient getClient() {
                return raw;
            }

            /**
             * 获取已解析的配置快照
             * @return 已解析的配置快照
             */
            public Configuration getConfiguration() {
                return config();
            }

            /**
             * 关闭当前资源并释放持有的引用。
             */
            public void close() {
            }
        });
    }

    private static <T> CompletableFuture<T> failed(Throwable failure) {
        CompletableFuture<T> f = new CompletableFuture<>();
        f.completeExceptionally(failure);
        return f;
    }

    private static ErrorResponseException error(String code, int status) {
        okhttp3.Response response = new okhttp3.Response.Builder()
            .request(new okhttp3.Request.Builder().url("http://unit.invalid").build())
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(status)
            .message(code)
            .build();
        return new ErrorResponseException(new ErrorResponse(code, code, "bucket", "file", "/", "req", "host"), response,
                null);
    }

    /**
     * 验证对象缺失返回 false，权限与网络异常继续抛出。
     */
    @Test
    public void missingKeyIsFalseButPermissionAndNetworkAreErrors() throws Exception {
        CustomMinioClient raw = mock(CustomMinioClient.class);
        MinioExecutor e = executor(raw);
        when(raw.statObject(any(StatObjectArgs.class))).thenReturn(failed(error("NoSuchKey", 404)));
        assertFalse(e.exists("bucket", "file"));
        when(raw.statObject(any(StatObjectArgs.class))).thenReturn(failed(error("AccessDenied", 403)));
        try {
            e.exists("bucket", "file");
            fail();
        }
        catch (RuntimeException expected) {
        }
        when(raw.statObject(any(StatObjectArgs.class))).thenReturn(failed(new IOException("offline")));
        try {
            e.exists("bucket", "file");
            fail();
        }
        catch (RuntimeException expected) {
        }
        verify(raw, never()).close();
    }

    /**
     * 验证回调失败仍关闭下载流，保留共享客户端。
     */
    @Test
    public void downloadClosesStreamAfterCallbackFailureAndLeavesClientOpen() throws Exception {
        CustomMinioClient raw = mock(CustomMinioClient.class);
        final boolean[] closed = { false };
        InputStream stream = new ByteArrayInputStream(new byte[] { 1, 2 }) {

            /**
             * 关闭当前资源并释放持有的引用。
             */
            @Override
            public void close() {
                closed[0] = true;
            }
        };
        GetObjectResponse response = new GetObjectResponse(new Headers.Builder().build(), "bucket", "region", "file",
                stream);
        when(raw.getObject(any(GetObjectArgs.class))).thenReturn(CompletableFuture.completedFuture(response));
        try {
            executor(raw).downloadFile("bucket", "file", in -> {
                throw new IllegalStateException("consumer failure");
            });
            fail();
        }
        catch (RuntimeException expected) {
        }
        assertTrue(closed[0]);
        verify(raw, never()).close();
    }

    /**
     * 验证要求删除本地文件时，上传失败仍保留原文件。
     */
    @Test
    public void uploadFailureNeverDeletesCallerFileEvenWithDeleteTrue() throws Exception {
        CustomMinioClient raw = mock(CustomMinioClient.class);
        when(raw.putObject(any(PutObjectArgs.class))).thenReturn(failed(new IOException("offline")));
        File file = File.createTempFile("oss-failed-upload-", ".txt");
        try {
            java.nio.file.Files.write(file.toPath(), new byte[] { 1, 2, 3 });
            try {
                executor(raw).putObject("bucket", "file.txt", file, true);
                fail();
            }
            catch (RuntimeException expected) {
            }
            assertTrue(file.exists());
            verify(raw).putObject(any(PutObjectArgs.class));
            verify(raw, never()).close();
        }
        finally {
            file.delete();
        }
    }

    /**
     * 验证合并使用真实分片编号且不重新读取分片清单。
     */
    @Test
    public void completionKeepsActualPartNumbersAndDoesNotRelist() throws Exception {
        CustomMinioClient raw = mock(CustomMinioClient.class);
        List<PartData> parts = new ArrayList<>();
        for (int n : new int[] { 1, 3 }) {
            PartData p = new PartData();
            p.setPartNumber(n);
            p.setEtag("etag-" + n);
            p.setSize(1L);
            parts.add(p);
        }
        executor(raw).completeMultipartUpload("bucket", "file", "upload", parts);
        ArgumentCaptor<Part[]> capture = ArgumentCaptor.forClass(Part[].class);
        verify(raw).mergeMultipartUpload(eq("bucket"), isNull(), eq("file"), eq("upload"), capture.capture(), isNull(),
                isNull());
        assertEquals(3, capture.getValue()[1].partNumber());
        verify(raw, never()).close();
    }

}
