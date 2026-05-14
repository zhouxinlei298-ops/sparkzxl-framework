package com.github.sparkzxl.oss.client;

import com.github.sparkzxl.oss.properties.Configuration;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.AwsRequestOverrideConfiguration;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.http.HttpStatusCode;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URI;
import java.time.Duration;
import java.util.Map;

/**
 * description: Custom Rustfs Client
 *
 * @author zhouxinlei
 * @since 2025-03-08 20:28:38
 */
@Slf4j
@Getter
public class CustomRustfsClient {

    private final S3Client client;
    private final S3Presigner presigner;
    private final SdkHttpClient apacheHttpClient = ApacheHttpClient.builder()
            .maxConnections(100)
            .connectionTimeout(Duration.ofSeconds(15))
            .build();

    public CustomRustfsClient(Configuration configuration) {
        URI endpointUri = URI.create(configuration.getEndpoint());
        Region region = Region.US_EAST_1;
        StaticCredentialsProvider credentialsProvider = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(configuration.getAccessKey(),
                        configuration.getSecretKey())
        );
        this.client = S3Client.builder()
                // RustFS 地址
                .endpointOverride(endpointUri)
                // 2. 显式指定 Region（核心配置，双重保障）
                .region(region)
                .credentialsProvider(credentialsProvider)
                // 关键配置！RustFS 需启用 Path-Style
                .forcePathStyle(true)
                .httpClient(apacheHttpClient)
                .overrideConfiguration(
                        b -> b.apiCallTimeout(Duration.ofSeconds(5))
                                .apiCallAttemptTimeout(Duration.ofMillis(2000)))
                .build();
        // 创建 S3Presigner 实例，复用以提升性能
        this.presigner = S3Presigner.builder()
                .endpointOverride(endpointUri)
                .region(region)
                .credentialsProvider(credentialsProvider)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .build())
                .build();
    }

    /**
     * 初始化分片上传、获取 uploadId
     *
     * @param bucket      存储桶名称
     * @param object      文件名称
     * @param contentType contentType
     * @return String
     */
    public String initMultiPartUpload(String bucket, String object, String contentType) throws Exception {
        CreateMultipartUploadRequest createMultipartUploadRequest = CreateMultipartUploadRequest.builder()
                .bucket(bucket)
                .key(object)
                .contentType(contentType)
                .build();
        CreateMultipartUploadResponse multipartUpload = client.createMultipartUpload(createMultipartUploadRequest);
        return multipartUpload.uploadId();
    }

    /**
     * 合并分片
     *
     * @param bucketName 桶名称
     * @param objectName oss对象名称
     * @param uploadId   上传的 uploadId
     * @param parts      分片集合
     * @return ObjectWriteResponse
     */
    public CompleteMultipartUploadResponse mergeMultipartUpload(String bucketName, String objectName, String uploadId, CompletedPart[] parts) {
        CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                .parts(parts)
                .build();

        CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .uploadId(uploadId)
                .multipartUpload(completedUpload)
                .build();
        return client.completeMultipartUpload(completeRequest);
    }

    /**
     * 查询当前上传后的分片信息
     *
     * @param bucketName       String   桶名称
     * @param objectName       String   文件名称
     * @param partNumberMarker Integer  分片起始值
     * @param uploadId         String   上传的 uploadId
     * @return ListPartsResponse
     */
    public ListPartsResponse listMultipart(String bucketName,
                                           String objectName,
                                           Integer partNumberMarker,
                                           String uploadId) {
        ListPartsRequest listPartsRequest = ListPartsRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .partNumberMarker(partNumberMarker)
                .uploadId(uploadId)
                .build();
        return client.listParts(listPartsRequest);
    }

    /**
     * Do <a
     * href="https://docs.aws.amazon.com/AmazonS3/latest/API/API_AbortMultipartUpload.html">AbortMultipartUpload
     * S3 API</a>.
     *
     * @param bucketName Name of the bucket.
     * @param objectName Object name in the bucket.
     * @param uploadId   Upload ID.
     * @return AbortMultipartUploadResponse
     */
    public AbortMultipartUploadResponse abortMultipartUpload(
            String bucketName,
            String objectName,
            String uploadId) {
        AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .uploadId(uploadId)
                .build();
        return client.abortMultipartUpload(abortRequest);
    }

    public String createPresignedGetUrl(String bucketName, String keyName, Integer expire) {
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(keyName)
                .build();

        GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
                .signatureDuration(Duration.ofDays(expire))
                .getObjectRequest(objectRequest)
                .build();

        PresignedGetObjectRequest presignedRequest = presigner.presignGetObject(presignRequest);
        log.info("Presigned URL: [{}]", presignedRequest.url().toString());
        log.info("HTTP method: [{}]", presignedRequest.httpRequest().method());
        return presignedRequest.url().toExternalForm();
    }


    public String getPresignedObjectUrl(String bucketName, String objectName, Map<String, String> reqParams,Duration duration) {

        // 上传生成带有查询参数的预签名 URL
        AwsRequestOverrideConfiguration.Builder overrideConfigurationBuilder = AwsRequestOverrideConfiguration.builder();
        reqParams.forEach(overrideConfigurationBuilder::putRawQueryParameter);
        PutObjectRequest putRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .overrideConfiguration(overrideConfigurationBuilder.build())
                .build();
        PresignedPutObjectRequest presignedPut = presigner.presignPutObject(
                PutObjectPresignRequest.builder()
                        .putObjectRequest(putRequest)
                        .signatureDuration(duration)
                        .build()
        );
        log.info("Presigned URL: [{}]", presignedPut.url().toString());
        log.info("HTTP method: [{}]", presignedPut.httpRequest().method());
        return presignedPut.url().toExternalForm();
    }


    /**
     * Checks if the specified bucket exists. Amazon S3 buckets are named in a global namespace; use this method to
     * determine if a specified bucket name already exists, and therefore can't be used to create a new bucket.
     * <p>
     * Internally this method uses the <a
     * href="https://sdk.amazonaws.com/java/api/latest/software/amazon/awssdk/services/s3/S3Client.html#getBucketAcl(java.util.function.Consumer)">S3Client.getBucketAcl(String)</a>
     * operation to determine whether the bucket exists.
     * <p>
     * This method is equivalent to the AWS SDK for Java V1's <a
     * href="https://docs.aws.amazon.com/AWSJavaSDK/latest/javadoc/com/amazonaws/services/s3/AmazonS3Client.html#doesBucketExistV2-java.lang.String-">AmazonS3Client#doesBucketExistV2(String)</a>.
     *
     * @param bucketName The name of the bucket to check.
     * @return The value true if the specified bucket exists in Amazon S3; the value false if there is no bucket in
     * Amazon S3 with that name.
     */
    public boolean doesBucketExist(String bucketName) {
        try {
            client.getBucketAcl(r -> r.bucket(bucketName));
            return true;
        } catch (AwsServiceException ase) {
            // A redirect error or an AccessDenied exception means the bucket exists but it's not in this region
            // or we don't have permissions to it.
            if ((ase.statusCode() == HttpStatusCode.MOVED_PERMANENTLY) || "AccessDenied".equals(ase.awsErrorDetails().errorCode())) {
                return true;
            }
            if (ase.statusCode() == HttpStatusCode.NOT_FOUND) {
                return false;
            }
            throw ase;
        }
    }


    public void shutdown() {
        // 关闭 S3Presigner
        if (presigner != null) {
            try {
                presigner.close();
                log.debug("S3Presigner closed successfully");
            } catch (Exception e) {
                log.error("Error closing S3Presigner: {}", e.getMessage(), e);
            }
        }
        // 关闭 Apache5 HTTP 客户端
        if (apacheHttpClient != null) {
            try {
                apacheHttpClient.close();
                log.debug("Rustfs HTTP client closed successfully");
            } catch (Exception e) {
                log.error("Error closing Rustfs HTTP client: {}", e.getMessage(), e);
            }
        }
        // 关闭 S3Client
        if (client != null) {
            try {
                client.close();
                log.debug("S3Client closed successfully");
            } catch (Exception e) {
                log.error("Error closing S3Client: {}", e.getMessage(), e);
            }
        }

    }
}
