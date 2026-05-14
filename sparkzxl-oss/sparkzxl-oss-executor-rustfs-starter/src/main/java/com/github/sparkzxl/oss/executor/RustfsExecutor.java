package com.github.sparkzxl.oss.executor;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.github.sparkzxl.oss.client.CustomRustfsClient;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.enums.BucketPolicyEnum;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.support.OssErrorCode;
import com.github.sparkzxl.oss.support.OssException;
import com.github.sparkzxl.oss.utils.OssUtils;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * description: Rustfs 执行器
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:54:27
 */
@Slf4j
public class RustfsExecutor extends AbstractOssExecutor<CustomRustfsClient> {

    /**
     * 桶占位符
     */
    private static final String BUCKET_PARAM = "${bucket}";
    /**
     * bucket权限-只读
     */
    private static final String READ_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-只读
     */
    private static final String WRITE_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-读写
     */
    private static final String READ_WRITE = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:DeleteObject\",\"s3:GetObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";

    public RustfsExecutor(OssClient<CustomRustfsClient> client) {
        super(client);
    }

    @Override
    public void createBucket(String bucketName) {
        // 验证 bucketName
        validateBucketName(bucketName);
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            boolean bucketedExists = obtainClient().doesBucketExist(bucketName);
            if (!bucketedExists) {
                rustfsClient.getClient().createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error("Rustfs unexpected error during create bucket for {}: {}",
                    bucketName, e.getMessage(), e);
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        S3Client rustfsClient = obtainClient().getClient();
        DeleteBucketRequest deleteBucketRequest = DeleteBucketRequest.builder().bucket(bucketName).build();
        try {
            rustfsClient.deleteBucket(deleteBucketRequest);
        } catch (Exception e) {
            log.error("Rustfs unexpected error during remove bucket for {}: {}",
                    bucketName, e.getMessage(), e);
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        String objectUrl;
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            objectUrl = rustfsClient.createPresignedGetUrl(bucketName, objectName, expire);
        } catch (Exception e) {
            log.error("Rustfs unexpected error during get object url for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

    @Override
    public OssObject getObjectInfo(String bucketName, String objectName) {
        S3Client rustfsClient = obtainClient().getClient();
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build();
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream = rustfsClient.getObject(getObjectRequest, ResponseTransformer.toInputStream());
            OssObject ossObject = new OssObject();
            ossObject.setBucketName(bucketName);
            ossObject.setKey(objectName);
            ossObject.setObjectContent(responseInputStream);
            return ossObject;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during get object info for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssMetadata getOssMetadata(String bucketName, String objectName) {
        S3Client s3Client = obtainClient().getClient();
        try {
            HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            OssMetadata ossMetadata = new OssMetadata();
            ossMetadata.setBucketName(bucketName);
            ossMetadata.setObjectName(objectName);
            ossMetadata.setSize(headResponse.contentLength());
            ossMetadata.setContentType(headResponse.contentType());
            ossMetadata.setLastModified(LocalDateTime.ofInstant(headResponse.lastModified(), ZoneId.systemDefault()));
            ossMetadata.setEtag(headResponse.eTag());
            return ossMetadata;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during get object metadata for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        S3Client rustfsClient = obtainClient().getClient();
        try {
            rustfsClient.getObjectAcl(GetObjectAclRequest.builder().bucket(bucketName).key(objectName).build());
            return true;
        } catch (NoSuchKeyException e) {
            // 对象不存在，这是正常情况
            log.debug("Object does not exist for bucket [{}/object [{}]", bucketName, objectName);
            return false;
        } catch (NoSuchBucketException e) {
            // bucket 不存在，记录警告但返回 false
            log.warn("Bucket [{}] does not exist when checking object [{}]", bucketName, objectName);
            return false;
        } catch (AwsServiceException e) {
            // 区分处理其他 AWS 服务异常
            if (e.statusCode() == 404 || e.awsErrorDetails() != null && "NotFound".equals(e.awsErrorDetails().errorCode())) {
                // 明确的 404 错误
                log.debug("Object not found (404) for bucket [{}/object [{}]", bucketName, objectName);
                return false;
            }
            // 其他 AWS 服务异常（权限错误等）应该抛出
            log.error("Rustfs AWS service error during checking whether objectName exists for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(),
                    String.format("Failed to check object existence for [%s]/[%s]: %s", bucketName, objectName, e.getMessage()), e);
        } catch (Exception e) {
            // 其他异常（网络错误等）应该抛出，而不是吞掉
            log.error("Rustfs unexpected error during checking whether objectName exists for {}/{}: {}",
                    bucketName, objectName, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(),
                    String.format("Failed to check object existence for [%s]/[%s]: %s", bucketName, objectName, e.getMessage()), e);
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        objectNameValidate(objectName);
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(contentType).build();
            PutObjectResponse putObjectResponse = rustfsClient.getClient().putObject(putObjectRequest,
                    RequestBody.fromInputStream(multipartFile.getInputStream(), multipartFile.getSize()));
            return buildPushObjectResponse(bucketName, objectName, size, contentType, putObjectResponse.eTag());
        } catch (Exception e) {
            log.error("Rustfs unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        objectNameValidate(objectName);
        File tempFile = new File(filePath);
        tempFile.deleteOnExit();
        return putObject(bucketName, objectName, tempFile, true);
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, File file, boolean delete) {
        objectNameValidate(objectName);
        CustomRustfsClient rustfsClient = obtainClient();
        BufferedInputStream tempInputStream = null;
        try {
            long size = FileUtil.size(file);
            tempInputStream = FileUtil.getInputStream(file);
            String mimeType = FileUtil.getType(file);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(finalMimeType).build();

            PutObjectResponse putObjectResponse = rustfsClient.getClient().putObject(putObjectRequest,
                    RequestBody.fromInputStream(tempInputStream, size));
            return buildPushObjectResponse(bucketName, objectName, size, finalMimeType, putObjectResponse.eTag());
        } catch (Exception e) {
            log.error("Rustfs unexpected error during local file upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            IoUtil.close(tempInputStream);
            if (delete) {
                safeDeleteTempFile(file, "本地文件");
            }
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, URL url) {
        objectNameValidate(objectName);
        Stopwatch stopwatch = Stopwatch.createStarted();
        String fileUrl = url.toString();
        File tempFile = FileUtil.createTempFile();
        // 注册JVM退出时自动删除临时文件（双重保障）
        tempFile.deleteOnExit();
        InputStream tempInputStream = null;
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            log.info("HTTP下载文件[{}]:开始======", fileUrl);
            long size = HttpUtil.downloadFile(fileUrl, tempFile, 600000);
            log.info("HTTP下载文件[{}]:结束,文件大小：{}======", fileUrl, size);
            String mimeType = FileUtil.getMimeType(fileUrl);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            tempInputStream = FileUtil.getInputStream(tempFile);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(finalMimeType).build();

            PutObjectResponse putObjectResponse = rustfsClient.getClient().putObject(putObjectRequest,
                    RequestBody.fromInputStream(tempInputStream, size));

            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]秒", totalTime);
            return buildPushObjectResponse(bucketName, objectName, size, finalMimeType, putObjectResponse.eTag());
        } catch (Exception e) {
            log.error("Rustfs unexpected error during remote file upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            IoUtil.close(tempInputStream);
            safeDeleteTempFile(tempFile, "远程下载临时文件");
        }
    }

    @Override
    public void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile) {
        objectNameValidate(objectName);
        CustomRustfsClient rustfsClient = obtainClient();

        S3Client s3Client = rustfsClient.getClient();
        try {

            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build();
            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            String uploadId = createResponse.uploadId();

            List<CompletedPart> completedParts = new ArrayList<>();
            InputStream inputStream = multipartFile.getInputStream();
            // 计算文件有多少个分片。
            // 1MB
            final long partSize = 5 * 1024 * 1024L;
            long fileLength = multipartFile.getSize();
            int partCount = (int) (fileLength / partSize);
            if (fileLength % partSize != 0) {
                partCount++;
            }
            // 遍历分片上传。
            for (int i = 0; i < partCount; i++) {
                long startPos = i * partSize;
                long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : partSize;
                // 每次重新获取 InputStream，避免 skip 位置错误
                InputStream partInputStream = multipartFile.getInputStream();
                // 跳过已处理的字节（从文件开头定位到当前分片起始位置）
                long skipped = 0;
                while (skipped < startPos) {
                    long n = partInputStream.skip(startPos - skipped);
                    if (n <= 0) {
                        break;
                    }
                    skipped += n;
                }
                // 分片编号从 1 开始（AWS S3 规范要求）
                int partNumber = i + 1;
                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(objectName)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .build();

                UploadPartResponse uploadPartResponse = s3Client.uploadPart(uploadPartRequest, RequestBody.fromInputStream(partInputStream, curPartSize));
                completedParts.add(
                        CompletedPart.builder()
                                .partNumber(partNumber)
                                .eTag(uploadPartResponse.eTag())
                                .build()
                );
            }
            log.info("objectName [{}] upload started", objectName);

            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
            log.info("objectName [{}] upload complete", objectName);
        } catch (Exception e) {
            log.error("Rustfs unexpected error during multipart file chunk upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            Integer chunkCount = fileUploadInfo.getChunkCount();
            String contentType = fileUploadInfo.getContentType();
            String uploadId = fileUploadInfo.getUploadId();
            log.info("文件<{}> - 分片<{}> 初始化分片上传数据 请求头 {}", objectName, chunkCount, contentType);
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            HashMultimap<String, String> headers = HashMultimap.create();
            if (StringUtils.isEmpty(contentType)) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);
            // 如果初始化时有 uploadId，说明是断点续传，不能重新生成 uploadId
            if (StringUtils.isEmpty(fileUploadInfo.getUploadId())) {
                uploadId = rustfsClient.initMultiPartUpload(bucketName, objectName, contentType);
            }
            uploadUrlsInfo.setUploadId(uploadId);

            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= chunkCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl = rustfsClient.getPresignedObjectUrl(bucketName, objectName, reqParams, Duration.ofHours(1));
                partList.add(uploadUrl);
            }
            log.info("文件初始化分片成功,{}/{}: uploadId={}", bucketName, objectName, uploadId);
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during init multipart upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        try {
            List<Part> parts = getParts(bucketName, objectName, uploadId);
            return parts.stream()
                    .map(x -> {
                        PartData partData = new PartData();
                        partData.setPartNumber(x.partNumber());
                        partData.setEtag(x.eTag());
                        partData.setSize(x.size());
                        return partData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("Rustfs unexpected error during get parts for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }

    }

    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        CustomRustfsClient rustfsClient = obtainClient();
        S3Client s3Client = rustfsClient.getClient();
        try {
            // 获取所有分片
            log.info("start Merge MultipartUpload start. {}/{}，uploadId:{}", bucketName, objectName, uploadId);
            List<Part> partsList = getParts(bucketName, objectName, uploadId);
            CompletedPart[] parts = new CompletedPart[partsList.size()];
            int partNumber = 1;
            for (Part part : partsList) {
                parts[partNumber - 1] = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(part.eTag())
                        .build();
                partNumber++;
            }
            // 合并分片
            CompletedMultipartUpload completedUpload = CompletedMultipartUpload.builder()
                    .parts(parts)
                    .build();
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .uploadId(uploadId)
                    .multipartUpload(completedUpload)
                    .build();
            CompleteMultipartUploadResponse multipartUploadResponse = s3Client.completeMultipartUpload(completeRequest);
            log.info("Merge MultipartUpload was successful. uploadId:{}，etag:{}", uploadId, multipartUploadResponse.eTag());
            return true;
        } catch (Exception e) {
            log.error("Rustfs Unexpected error during merge multipart upload completion for {}/{}/{}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    private List<Part> getParts(String bucketName, String objectName, String uploadId) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            int partNumberMarker = 0;
            boolean isTruncated = true;
            List<Part> parts = new ArrayList<>();
            while (isTruncated) {
                ListPartsResponse partResult = rustfsClient.listMultipart(bucketName, objectName, partNumberMarker, uploadId);
                parts.addAll(partResult.parts());
                // 检查是否还有更多分片
                isTruncated = partResult.isTruncated();
                if (isTruncated) {
                    // 更新partNumberMarker以获取下一页的分片数据
                    partNumberMarker = partResult.nextPartNumberMarker();
                }
            }
            return parts;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during get parts for {}/{} ,uploadId {}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            rustfsClient.abortMultipartUpload(bucketName, objectName, uploadId);
            log.info("Successfully aborted multipart upload for {}/{}，uploadId:{}", bucketName, objectName, uploadId);
            return true;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during aborted multipart upload {}/{} ,uploadId {}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void removeObject(String bucketName, String objectName) {
        CustomRustfsClient rustfsClient = obtainClient();
        S3Client s3Client = rustfsClient.getClient();
        DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build();
        try {
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error("Rustfs Unexpected error during remove object for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            List<String> urlList = new ArrayList<>();
            String uploadId = IdUtil.simpleUUID();
            Map<String, String> reqParams = new HashMap<>();
            String url = rustfsClient.getPresignedObjectUrl(bucketName, objectName, reqParams, Duration.ofHours(1));
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("Rustfs Unexpected error during get presigned object upload url for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        CustomRustfsClient rustfsClient = obtainClient();
        S3Client s3Client = rustfsClient.getClient();
        try {
            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .key(objectName)
                    .bucket(bucketName)
                    .build();
            ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(objectRequest, ResponseTransformer.toInputStream());
            consumer.accept(responseInputStream);
        } catch (Exception e) {
            log.error("Rustfs Unexpected error during download file for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    protected DownloadMetadata fetchDownloadMetadata(String bucketName, String objectName) {
        S3Client s3Client = obtainClient().getClient();
        HeadObjectResponse headResponse = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build());
        return new DownloadMetadata(
                headResponse.contentLength(),
                headResponse.contentType(),
                headResponse.lastModified().toString(),
                headResponse.eTag()
        );
    }

    @Override
    protected InputStream openDownloadStream(String bucketName, String objectName, long startByte, long endByte) throws Exception {
        S3Client s3Client = obtainClient().getClient();
        GetObjectRequest objectRequest = GetObjectRequest.builder()
                .key(objectName)
                .bucket(bucketName)
                .range("bytes=" + startByte + "-" + endByte)
                .build();
        return s3Client.getObject(objectRequest, ResponseTransformer.toInputStream());
    }

    @Override
    public void shutdown() {
        // 委托给 OssClient.close() 统一管理资源释放
        OssClient<CustomRustfsClient> ossClient = client;
        if (ossClient != null) {
            ossClient.close();
        }
    }

    @Override
    public void setBucketPolicy(String bucketName, BucketPolicyEnum policy) {
        // 验证 bucketName 以防止 JSON 注入
        validateBucketName(bucketName);
        CustomRustfsClient rustfsClient = obtainClient();
        S3Client s3Client = rustfsClient.getClient();
        try {
            String policyConfig;
            switch (policy) {
                case READ_ONLY:
                    policyConfig = READ_ONLY.replace(BUCKET_PARAM, bucketName);
                    s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                            .bucket(bucketName)
                            .policy(policyConfig)
                            .build());
                    break;
                case WRITE_ONLY:
                    policyConfig = WRITE_ONLY.replace(BUCKET_PARAM, bucketName);
                    s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                            .bucket(bucketName)
                            .policy(policyConfig)
                            .build());
                    break;
                case READ_WRITE:
                    policyConfig = READ_WRITE.replace(BUCKET_PARAM, bucketName);
                    s3Client.putBucketPolicy(PutBucketPolicyRequest.builder()
                            .bucket(bucketName)
                            .policy(policyConfig)
                            .build());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Rustfs Unexpected error during set bucket policy for {}: {}",
                    bucketName, e.getMessage());
            throw new OssException(OssErrorCode.SET_BUCKET_POLICY_ERROR.getErrorCode(), e.getMessage());
        }
    }
}
