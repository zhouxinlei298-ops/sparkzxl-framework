package com.github.sparkzxl.oss.executor;

import cn.hutool.core.io.FileUtil;
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
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
        S3Client rustfsClient = obtainClient().getClient();
        try {
            GetObjectAttributesResponse getObjectAttributesResponse = rustfsClient.getObjectAttributes(GetObjectAttributesRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .build());
            OssMetadata ossMetadata = new OssMetadata();
            ossMetadata.setBucketName(bucketName);
            ossMetadata.setObjectName(objectName);
            ossMetadata.setSize(getObjectAttributesResponse.objectSize());
            ossMetadata.setContentType("");
            ossMetadata.setLastModified(LocalDateTime.ofInstant(getObjectAttributesResponse.lastModified(), ZoneId.systemDefault()));
            ossMetadata.setEtag(getObjectAttributesResponse.eTag());
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
        } catch (Exception e) {
            log.error("Rustfs unexpected error during checking whether objectName exists for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            return false;
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
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
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(contentType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResponse.eTag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        uploadFileLimit(objectName);
        File tempFile = new File(filePath);
        CustomRustfsClient rustfsClient = obtainClient();
        BufferedInputStream tempInputStream = null;
        try {
            long size = FileUtil.size(tempFile);
            tempInputStream = FileUtil.getInputStream(tempFile);
            String mimeType = FileUtil.getType(tempFile);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(finalMimeType).build();

            PutObjectResponse putObjectResponse = rustfsClient.getClient().putObject(putObjectRequest,
                    RequestBody.fromInputStream(tempInputStream, size));

            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(finalMimeType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResponse.eTag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during local file upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            if (tempInputStream != null) {
                try {
                    tempInputStream.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败：{}", e.getMessage());
                }
            }
            // 删除临时文件
            if (tempFile != null && tempFile.exists()) {
                try {
                    boolean deleted = FileUtil.del(tempFile);
                    if (!deleted) {
                        log.warn("临时文件删除失败，文件路径：{}", tempFile.getAbsolutePath());
                        tempFile.deleteOnExit();
                    }
                } catch (Exception e) {
                    log.error("删除临时文件时发生异常，文件路径：{}，错误信息：{}", tempFile.getAbsolutePath(), e.getMessage());
                    tempFile.deleteOnExit();
                }
            }
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, URL url) {
        uploadFileLimit(objectName);
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
            // 4. 上传到Rustfs
            tempInputStream = FileUtil.getInputStream(tempFile);

            PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectName)
                    .contentType(finalMimeType).build();

            PutObjectResponse putObjectResponse = rustfsClient.getClient().putObject(putObjectRequest,
                    RequestBody.fromInputStream(tempInputStream, size));

            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]秒", totalTime);
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(finalMimeType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResponse.eTag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("Rustfs unexpected error during remote file upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            if (tempInputStream != null) {
                try {
                    tempInputStream.close();
                } catch (IOException e) {
                    log.error("关闭文件流失败：{}", e.getMessage());
                }
            }
            // 删除临时文件（使用 Hutool 的 FileUtil.del 提供更可靠的删除机制）
            if (tempFile != null && tempFile.exists()) {
                try {
                    boolean deleted = FileUtil.del(tempFile);
                    if (!deleted) {
                        log.warn("临时文件删除失败，文件路径：{}", tempFile.getAbsolutePath());
                        // 尝试使用 JVM 退出时删除作为最后的保障
                        tempFile.deleteOnExit();
                    }
                } catch (Exception e) {
                    log.error("删除临时文件时发生异常，文件路径：{}，错误信息：{}", tempFile.getAbsolutePath(), e.getMessage());
                    tempFile.deleteOnExit();
                }
            }
        }
    }

    @Override
    public void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
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
                String uploadUrl = rustfsClient.getPresignedObjectUrl(bucketName, objectName, reqParams);
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
                ListPartsResponse partResult = rustfsClient.listMultipart(bucketName, objectName, 10000, partNumberMarker, uploadId);
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
            reqParams.put("uploadId", uploadId);
            String url = rustfsClient.getPresignedObjectUrl(bucketName, objectName, reqParams);
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
    public void downloadMultipartFile(String bucketName, String objectName, String fileName, HttpServletRequest request, HttpServletResponse response) {
        CustomRustfsClient rustfsClient = obtainClient();
        InputStream stream = null;
        BufferedOutputStream os = null;
        S3Client s3Client = rustfsClient.getClient();

        HeadObjectRequest headObjectRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(objectName)
                .build();
        try {
            // 1. 获取文件元数据
            HeadObjectResponse headObjectResponse = s3Client.headObject(headObjectRequest);
            long fileSize = headObjectResponse.contentLength();

            // 文件大小为0的异常处理
            if (fileSize <= 0) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            long startByte = 0;
            long endByte = fileSize - 1;
            String range = request.getHeader("Range");
            log.info("下载请求 bucket={}, object={}, range={}", bucketName, objectName, range);


            // 2. 解析 Range 头 (断点续传/视频拖动)
            if (range != null && range.contains("bytes=") && range.contains("-")) {
                range = range.substring(range.lastIndexOf("=") + 1).trim();
                String[] ranges = range.split("-");

                if (ranges.length == 1) {
                    // 情况 A: bytes=-500 (最后500字节)
                    if (range.startsWith("-")) {
                        long lastBytes = Long.parseLong(ranges[1]);
                        startByte = fileSize - lastBytes;
                    }
                    // 情况 B: bytes=500- (从500字节到结束)
                    else if (range.endsWith("-")) {
                        startByte = Long.parseLong(ranges[0]);
                    }
                } else if (ranges.length == 2) {
                    // 情况 C: bytes=500-1000
                    startByte = Long.parseLong(ranges[0]);
                    endByte = Long.parseLong(ranges[1]);
                }
            }

            // 3. 计算实际要下载的长度 & 严格的Range边界校验（关键！）
            if (startByte > endByte || startByte >= fileSize || endByte < 0) {
                response.setStatus(HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                response.setHeader("Content-Range", "bytes */" + fileSize);
                return;
            }

            long contentLength = endByte - startByte + 1;

            // 4. 设置响应头
            String contentType = request.getServletContext().getMimeType(fileName);
            if (contentType == null) {
                contentType = headObjectResponse.contentType();
            }
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            // 文件名编码
            String encodedFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8.toString()).replaceAll("\\+", "%20");

            response.setContentType(contentType);
            response.setHeader("Accept-Ranges", "bytes");

            // 根据是否有 Range 决定返回 206 还是 200
            if (request.getHeader("Range") != null) {
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + fileSize);
            } else {
                response.setStatus(HttpServletResponse.SC_OK);
            }

            response.setHeader("Last-Modified", headObjectResponse.lastModified().toString());
            response.setHeader("Content-Length", String.valueOf(contentLength));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("ETag", "\"" + headObjectResponse.eTag() + "\"");

            // 5. 获取 Rustfs 流
            // Rustfs 的 getObject 已经支持 offset 和 length，返回的流就是精确的片段

            GetObjectRequest objectRequest = GetObjectRequest.builder()
                    .key(objectName)
                    .bucket(bucketName)
                    .build();

            stream = s3Client.getObject(objectRequest, ResponseTransformer.toInputStream());

            // 6. 写出数据
            os = new BufferedOutputStream(response.getOutputStream());

            // 使用 Spring 工具类直接拷贝流，无需手动循环
            StreamUtils.copy(stream, os);

            os.flush();
            response.flushBuffer();

        } catch (Exception e) {
            log.error("Rustfs Unexpected error during download multipart file for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            // 如果还没有写入响应，可以抛出异常给全局异常处理器
            if (!response.isCommitted()) {
                throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
            }
        } finally {
            // 安全关闭资源
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) { /* ignore */ }
            }
            if (os != null) {
                try {
                    os.close();
                } catch (IOException e) { /* ignore */ }
            }
        }
    }

    @Override
    public void shutdown() {
        CustomRustfsClient rustfsClient = obtainClient();
        rustfsClient.shutdown();
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
