package com.github.sparkzxl.oss.client;

import com.google.common.collect.Multimap;
import io.minio.*;
import io.minio.messages.Part;

/**
 * description: Custom Minio Client
 *
 * @author zhouxinlei
 * @since 2025-03-08 20:28:38
 */
public class CustomMinioClient extends MinioAsyncClient {

    /**
     * 继承父类
     *
     * @param client 客户端
     */
    public CustomMinioClient(MinioAsyncClient client) {
        super(client);
    }


    /**
     * 初始化分片上传、获取 uploadId
     *
     * @param bucket           String  存储桶名称
     * @param region           String
     * @param object           String   文件名称
     * @param headers          Multimap<String, String> 请求头
     * @param extraQueryParams Multimap<String, String>
     * @return String
     */
    public String initMultiPartUpload(String bucket, String region, String object, Multimap<String, String> headers, Multimap<String, String> extraQueryParams) throws Exception {
        CreateMultipartUploadResponse response = super.createMultipartUploadAsync(bucket, region, object, headers, extraQueryParams).get();
        return response.result().uploadId();
    }

    /**
     * 合并分片
     *
     * @param bucketName       桶名称
     * @param region           区域
     * @param objectName       oss对象名称
     * @param uploadId         上传的 uploadId
     * @param parts            分片集合
     * @param extraHeaders     Multimap<String, String>
     * @param extraQueryParams Multimap<String, String>
     * @return ObjectWriteResponse
     */
    public ObjectWriteResponse mergeMultipartUpload(String bucketName, String region, String objectName, String uploadId, Part[] parts, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws Exception {
        return super.completeMultipartUploadAsync(bucketName, region, objectName, uploadId, parts, extraHeaders, extraQueryParams).get();
    }

    /**
     * 查询当前上传后的分片信息
     *
     * @param bucketName       桶名称
     * @param region           区域
     * @param objectName       oss对象名称
     * @param maxParts         分片数量
     * @param partNumberMarker 分片起始值
     * @param uploadId         上传的 uploadId
     * @param extraHeaders     Multimap<String, String>
     * @param extraQueryParams Multimap<String, String>
     * @return ListPartsResponse
     */
    public ListPartsResponse listMultipart(String bucketName, String region, String objectName, Integer maxParts, Integer partNumberMarker, String uploadId, Multimap<String, String> extraHeaders, Multimap<String, String> extraQueryParams) throws Exception {
        return super.listPartsAsync(bucketName, region, objectName, maxParts, partNumberMarker, uploadId, extraHeaders, extraQueryParams).get();
    }

    /**
     * Do <a
     * href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html">AbortMultipartUpload
     * S3 API</a>.
     *
     * @param bucketName       Name of the bucket.
     * @param region           Region of the bucket.
     * @param objectName       Object name in the bucket.
     * @param uploadId         Upload ID.
     * @param extraHeaders     Extra headers (Optional).
     * @param extraQueryParams Extra query parameters (Optional).
     * @return AbortMultipartUploadResponse
     */
    @Override
    public AbortMultipartUploadResponse abortMultipartUpload(
            String bucketName,
            String region,
            String objectName,
            String uploadId,
            Multimap<String, String> extraHeaders,
            Multimap<String, String> extraQueryParams) {
        try {
            return abortMultipartUploadAsync(
                    bucketName, region, objectName, uploadId, extraHeaders, extraQueryParams)
                    .get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

}
