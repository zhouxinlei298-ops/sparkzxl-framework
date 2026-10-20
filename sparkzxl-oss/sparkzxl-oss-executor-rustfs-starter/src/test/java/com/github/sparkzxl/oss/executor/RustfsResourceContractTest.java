package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.oss.client.CustomRustfsClient;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.entity.PartData;
import com.github.sparkzxl.oss.properties.Configuration;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.AbortableInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * RustFS原生适配的存在性、流释放与真实分片提交契约测试。
 *
 * @author zhouxinlei
 * @since 2026-10-06
 */
public class RustfsResourceContractTest {

    private static RustfsExecutor executor(CustomRustfsClient raw) {
        return new RustfsExecutor(new OssClient<CustomRustfsClient>() {
            @Override
            public CustomRustfsClient getClient() {
                return raw;
            }

            @Override
            public Configuration getConfiguration() {
                Configuration configuration = new Configuration();
                configuration.setClientId("test");
                configuration.setClientType("rustfs");
                configuration.setBucketName("test-bucket");
                configuration.setEndpoint("http://127.0.0.1:9000");
                return configuration;
            }

            @Override
            public void close() {
            }
        });
    }

    /**
     * 验证拒绝权限和 Bucket 缺失不会被误判为对象不存在。
     */
    @Test
    public void existsDoesNotTreatDeniedOrMissingBucketAsMissingKey() {
        CustomRustfsClient raw = mock(CustomRustfsClient.class);
        S3Client s3 = mock(S3Client.class);
        when(raw.getClient()).thenReturn(s3);
        when(s3.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchKeyException.builder().statusCode(404).build());
        assertFalse(executor(raw).exists("test-bucket", "file"));
        when(s3.headObject(any(HeadObjectRequest.class)))
            .thenThrow(NoSuchBucketException.builder().statusCode(404).build());
        rejected(() -> executor(raw).exists("test-bucket", "file"));
        when(s3.headObject(any(HeadObjectRequest.class)))
            .thenThrow(S3Exception.builder().statusCode(403).message("denied").build());
        rejected(() -> executor(raw).exists("test-bucket", "file"));
        verify(raw, never()).shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void callbackFailureStillClosesSupplierDownloadStream() throws IOException {
        CustomRustfsClient raw = mock(CustomRustfsClient.class);
        S3Client s3 = mock(S3Client.class);
        when(raw.getClient()).thenReturn(s3);
        AtomicInteger closed = new AtomicInteger();
        InputStream bytes = new ByteArrayInputStream(new byte[] { 1, 2 }) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        };
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                AbortableInputStream.create(bytes));
        when(s3.getObject(any(GetObjectRequest.class), any(ResponseTransformer.class))).thenReturn(stream);
        rejected(() -> executor(raw).downloadFile("test-bucket", "file", input -> {
            throw new IllegalStateException("Controlled callback failure");
        }));
        assertEquals(1, closed.get());
        verify(raw, never()).shutdown();
    }

    /**
     * 验证真实分片编号保留且分页标记必须前进。
     */
    @Test
    public void listPartsPreservesSupplierNumbersAndRequiresAdvancingPagination() {
        CustomRustfsClient raw = mock(CustomRustfsClient.class);
        when(raw.listMultipart("test-bucket", "file", 0, "upload")).thenReturn(ListPartsResponse.builder()
            .isTruncated(true)
            .nextPartNumberMarker(3)
            .parts(Part.builder().partNumber(3).eTag("e3").size(5L).build())
            .build());
        when(raw.listMultipart("test-bucket", "file", 3, "upload")).thenReturn(ListPartsResponse.builder()
            .isTruncated(false)
            .parts(Part.builder().partNumber(7).eTag("e7").size(2L).build())
            .build());
        List<PartData> parts = executor(raw).getListParts("test-bucket", "file", "upload");
        assertEquals(Integer.valueOf(3), parts.get(0).getPartNumber());
        assertEquals(Integer.valueOf(7), parts.get(1).getPartNumber());
        when(raw.listMultipart("test-bucket", "file", 0, "upload"))
            .thenReturn(ListPartsResponse.builder().isTruncated(true).nextPartNumberMarker(0).build());
        rejected(() -> executor(raw).getListParts("test-bucket", "file", "upload"));
    }

    /**
     * 验证每个分片流关闭且供应商收到完整字节。
     */
    @Test
    public void eachMultipartStreamClosesAndProviderReceivesCompleteBytes() throws Exception {
        CustomRustfsClient raw = mock(CustomRustfsClient.class);
        S3Client s3 = mock(S3Client.class);
        when(raw.getClient()).thenReturn(s3);
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload").build());
        int length = 5 * 1024 * 1024 + 3;
        byte[] content = new byte[length];
        content[length - 1] = 91;
        AtomicInteger closed = new AtomicInteger();
        AtomicInteger consumed = new AtomicInteger();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn((long) length);
        when(file.getInputStream()).thenAnswer(invocation -> new ByteArrayInputStream(content) {
            @Override
            public long skip(long size) {
                // Force skipFully's safe fallback; progress must remain exact.
                return 0;
            }

            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        });
        when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class))).thenAnswer(invocation -> {
            UploadPartRequest request = invocation.getArgument(0);
            RequestBody body = invocation.getArgument(1);
            long partSize = body.optionalContentLength().get();
            byte[] buffer = new byte[8192];
            try (InputStream input = body.contentStreamProvider().newStream()) {
                long remaining = partSize;
                int last = -1;
                while (remaining > 0) {
                    int count = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                    if (count < 0)
                        throw new EOFException();
                    consumed.addAndGet(count);
                    remaining -= count;
                    last = buffer[count - 1];
                }
                if (request.partNumber() == 2)
                    assertEquals(91, last);
            }
            return UploadPartResponse.builder().eTag("etag-" + request.partNumber()).build();
        });
        when(s3.headObject(any(HeadObjectRequest.class))).thenReturn(HeadObjectResponse.builder()
            .contentLength((long) length)
            .lastModified(Instant.EPOCH)
            .eTag("complete")
            .build());
        executor(raw).multipartUpload("test-bucket", "file", file);
        assertEquals(length, consumed.get());
        assertTrue(closed.get() >= 2);
        ArgumentCaptor<CompleteMultipartUploadRequest> request = ArgumentCaptor
            .forClass(CompleteMultipartUploadRequest.class);
        verify(s3).completeMultipartUpload(request.capture());
        assertEquals(Integer.valueOf(1), request.getValue().multipartUpload().parts().get(0).partNumber());
        assertEquals(Integer.valueOf(2), request.getValue().multipartUpload().parts().get(1).partNumber());
        verify(s3, never()).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
    }

    /**
     * 验证分片失败关闭输入流并取消供应商上传。
     */
    @Test
    public void failedPartClosesStreamAndCancelsSupplierUpload() throws Exception {
        CustomRustfsClient raw = mock(CustomRustfsClient.class);
        S3Client s3 = mock(S3Client.class);
        when(raw.getClient()).thenReturn(s3);
        when(s3.createMultipartUpload(any(CreateMultipartUploadRequest.class)))
            .thenReturn(CreateMultipartUploadResponse.builder().uploadId("upload").build());
        when(s3.uploadPart(any(UploadPartRequest.class), any(RequestBody.class)))
            .thenThrow(new IllegalStateException("offline"));
        AtomicInteger closed = new AtomicInteger();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[] { 1 }) {
            @Override
            public void close() throws IOException {
                closed.incrementAndGet();
                super.close();
            }
        });
        rejected(() -> executor(raw).multipartUpload("test-bucket", "file", file));
        assertEquals(1, closed.get());
        verify(s3).abortMultipartUpload(any(AbortMultipartUploadRequest.class));
        verify(s3, never()).completeMultipartUpload(any(CompleteMultipartUploadRequest.class));
    }

    private static void rejected(Runnable action) {
        try {
            action.run();
            fail("Expected supplier failure");
        }
        catch (RuntimeException expected) {
        }
    }

}
