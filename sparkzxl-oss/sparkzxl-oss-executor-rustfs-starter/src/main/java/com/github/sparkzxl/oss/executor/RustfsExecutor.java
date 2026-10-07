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
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
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
 * Rustfs 执行器
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
    private static final String READ_ONLY =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "/*\"]}]}";

    /**
     * bucket权限-只读
     */
    private static final String WRITE_ONLY =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "/*\"]}]}";

    /**
     * bucket权限-读写
     */
    private static final String READ_WRITE =
            "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:DeleteObject\",\"s3:GetObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\"],\"Resource\":[\"arn:aws:s3:::"
                    + BUCKET_PARAM
                    + "/*\"]}]}";

    /**
     * 使用当前代际的 RustFS 客户端执行固定目标操作。
     *
     * @param client 当前代际持有的原生客户端
     */
    public RustfsExecutor(OssClient<CustomRustfsClient> client) {
        super(client);
    }

    /**
     * 准确跳过已处理字节，拒绝提前结束的上传流。
     *
     * @param stream   当前分片输入流
     * @param position 分片起始字节位置
     * @throws java.io.IOException 字节不足或读取失败
     */
    private static void skipFully(InputStream stream, long position) throws IOException {
        long skipped = 0;
        while (skipped < position) {
            long count = stream.skip(position - skipped);
            if (count > 0) {
                skipped += count;
            } else if (stream.read() >= 0) {
                skipped++;
            } else {
                throw new EOFException("上传流在分片起始位置前结束");
            }
        }
    }

    /**
     * 创建指定 Bucket；不得作为账号授权的替代。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void createBucket(String bucketName) {
        // 验证 bucketName
        validateBucketName(bucketName);
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            boolean bucketedExists = obtainClient().doesBucketExist(bucketName);
            if (!bucketedExists) {
                rustfsClient
                        .getClient()
                        .createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during create bucket for {}: {}",
                    bucketName,
                    e.getMessage(),
                    e);
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 删除指定 Bucket。
     *
     * @param bucketName Bucket 名称
     */
    @Override
    public void removeBucket(String bucketName) {
        S3Client rustfsClient = obtainClient().getClient();
        DeleteBucketRequest deleteBucketRequest =
                DeleteBucketRequest.builder().bucket(bucketName).build();
        try {
            rustfsClient.deleteBucket(deleteBucketRequest);
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during remove bucket for {}: {}",
                    bucketName,
                    e.getMessage(),
                    e);
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 获取对象访问 URL
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param expire 签名有效期（秒）
     * @return 对象访问 URL
     */
    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        String objectUrl;
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            objectUrl = rustfsClient.createPresignedGetUrl(bucketName, objectName, expire);
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during get object url for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

    /**
     * 获取对象下载流，调用方负责关闭
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @return 对象下载流，调用方负责关闭
     */
    @Override
    public OssObject getObjectInfo(String bucketName, String objectName) {
        S3Client rustfsClient = obtainClient().getClient();
        GetObjectRequest getObjectRequest =
                GetObjectRequest.builder().bucket(bucketName).key(objectName).build();
        try {
            ResponseInputStream<GetObjectResponse> responseInputStream =
                    rustfsClient.getObject(getObjectRequest, ResponseTransformer.toInputStream());
            OssObject ossObject = new OssObject();
            ossObject.setBucketName(bucketName);
            ossObject.setKey(objectName);
            ossObject.setObjectContent(responseInputStream);
            return ossObject;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during get object info for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 获取对象大小与供应商元信息
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @return 对象大小与供应商元信息
     */
    @Override
    public OssMetadata getOssMetadata(String bucketName, String objectName) {
        S3Client s3Client = obtainClient().getClient();
        try {
            HeadObjectResponse headResponse =
                    s3Client.headObject(
                            HeadObjectRequest.builder().bucket(bucketName).key(objectName).build());
            OssMetadata ossMetadata = new OssMetadata();
            ossMetadata.setBucketName(bucketName);
            ossMetadata.setObjectName(objectName);
            ossMetadata.setSize(headResponse.contentLength());
            ossMetadata.setContentType(headResponse.contentType());
            ossMetadata.setLastModified(
                    LocalDateTime.ofInstant(headResponse.lastModified(), ZoneId.systemDefault()));
            ossMetadata.setEtag(headResponse.eTag());
            return ossMetadata;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during get object metadata for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 通过对象元信息检查存在性，仅对象不存在时返回 false。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @return 对象存在返回 true；对象不存在返回 false，权限或网络异常继续抛出
     */
    @Override
    public boolean exists(String bucketName, String objectName) {
        try {
            obtainClient()
                    .getClient()
                    .headObject(
                            HeadObjectRequest.builder().bucket(bucketName).key(objectName).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (NoSuchBucketException e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        }
    }

    /**
     * 上传对象；只有上传成功后才按参数删除本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param multipartFile 待上传的请求文件
     * @return 供应商上传响应
     */
    @Override
    public OssPushObjectResponse putObject(
            String bucketName, String objectName, MultipartFile multipartFile) {
        objectNameValidate(objectName);
        CustomRustfsClient rustfsClient = obtainClient();
        try (InputStream uploadStream = multipartFile.getInputStream()) {
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(contentType)
                            .build();
            PutObjectResponse putObjectResponse =
                    rustfsClient
                            .getClient()
                            .putObject(
                                    putObjectRequest,
                                    RequestBody.fromInputStream(
                                            uploadStream, multipartFile.getSize()));
            return buildPushObjectResponse(
                    bucketName, objectName, size, contentType, putObjectResponse.eTag());
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 上传对象；只有上传成功后才按参数删除本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param filePath 本地文件路径
     * @return 供应商上传响应
     */
    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        objectNameValidate(objectName);
        File tempFile = new File(filePath);
        return putObject(bucketName, objectName, tempFile, true);
    }

    /**
     * 上传对象；只有上传成功后才按参数删除本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param file 待上传的本地文件
     * @param delete 上传成功后是否删除本地文件
     * @return 供应商上传响应
     */
    @Override
    public OssPushObjectResponse putObject(
            String bucketName, String objectName, File file, boolean delete) {
        boolean succeeded = false;
        objectNameValidate(objectName);
        CustomRustfsClient rustfsClient = obtainClient();
        BufferedInputStream tempInputStream = null;
        try {
            long size = FileUtil.size(file);
            tempInputStream = FileUtil.getInputStream(file);
            String mimeType = FileUtil.getMimeType(file.getName());
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(finalMimeType)
                            .build();
            PutObjectResponse putObjectResponse =
                    rustfsClient
                            .getClient()
                            .putObject(
                                    putObjectRequest,
                                    RequestBody.fromInputStream(tempInputStream, size));
            OssPushObjectResponse response =
                    buildPushObjectResponse(
                            bucketName, objectName, size, finalMimeType, putObjectResponse.eTag());
            succeeded = true;
            return response;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during local file upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            IoUtil.close(tempInputStream);
            if (delete && succeeded) {
                safeDeleteTempFile(file, "本地文件");
            }
        }
    }

    /**
     * 上传对象；只有上传成功后才按参数删除本地文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param url 待读取的受控资源地址
     * @return 供应商上传响应
     */
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
            log.debug("开始下载受控远程资源");
            long size = HttpUtil.downloadFile(fileUrl, tempFile, 600000);
            log.debug("受控远程资源下载完成，字节数：{}", size);
            String mimeType = FileUtil.getMimeType(fileUrl);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            tempInputStream = FileUtil.getInputStream(tempFile);
            PutObjectRequest putObjectRequest =
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(finalMimeType)
                            .build();
            PutObjectResponse putObjectResponse =
                    rustfsClient
                            .getClient()
                            .putObject(
                                    putObjectRequest,
                                    RequestBody.fromInputStream(tempInputStream, size));
            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]秒", totalTime);
            return buildPushObjectResponse(
                    bucketName, objectName, size, finalMimeType, putObjectResponse.eTag());
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during remote file upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        } finally {
            IoUtil.close(tempInputStream);
            safeDeleteTempFile(tempFile, "远程下载临时文件");
        }
    }

    /**
     * 上传指定对象的分片内容。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param multipartFile 待上传的请求文件
     */
    @Override
    public void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile) {
        objectNameValidate(objectName);
        long fileLength = multipartFile.getSize();
        final long partSize = 5 * 1024 * 1024L;
        if (fileLength <= 0 || 1 + (fileLength - 1) / partSize > 10000) {
            throw new IllegalArgumentException("文件大小或分片数量不符合供应商限制");
        }
        S3Client s3Client = obtainClient().getClient();
        String uploadId = null;
        try {
            String contentType = multipartFile.getContentType();
            CreateMultipartUploadRequest createRequest =
                    CreateMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .contentType(
                                    contentType == null ? "application/octet-stream" : contentType)
                            .build();
            uploadId = s3Client.createMultipartUpload(createRequest).uploadId();
            List<CompletedPart> completedParts = new ArrayList<>();
            int count = (int) (1 + (fileLength - 1) / partSize);
            for (int index = 0; index < count; index++) {
                long startPosition = index * partSize;
                long actualPartSize = Math.min(partSize, fileLength - startPosition);
                // 每片独立流必须准确定位；skip返回0时逐字节推进，提前EOF直接拒绝。
                try (InputStream stream = multipartFile.getInputStream()) {
                    skipFully(stream, startPosition);
                    UploadPartRequest request =
                            UploadPartRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectName)
                                    .uploadId(uploadId)
                                    .partNumber(index + 1)
                                    .build();
                    UploadPartResponse response =
                            s3Client.uploadPart(
                                    request, RequestBody.fromInputStream(stream, actualPartSize));
                    completedParts.add(
                            CompletedPart.builder()
                                    .partNumber(index + 1)
                                    .eTag(response.eTag())
                                    .build());
                }
            }
            s3Client.completeMultipartUpload(
                    CompleteMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(objectName)
                            .uploadId(uploadId)
                            .multipartUpload(
                                    CompletedMultipartUpload.builder()
                                            .parts(completedParts)
                                            .build())
                            .build());
            if (getOssMetadata(bucketName, objectName).getSize() != fileLength) {
                throw new IllegalStateException("实际对象大小与上传文件不一致");
            }
        } catch (Exception exception) {
            if (uploadId != null) {
                try {
                    s3Client.abortMultipartUpload(
                            AbortMultipartUploadRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectName)
                                    .uploadId(uploadId)
                                    .build());
                } catch (RuntimeException cleanupFailure) {
                    log.warn("分片失败后的取消未确认：{}", cleanupFailure.getClass().getSimpleName());
                }
            }
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), "分片上传失败");
        }
    }

    /**
     * 初始化供应商分片上传并返回上传标识。
     *
     * @param fileUploadInfo 本次上传信息
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @return 供应商分片上传标识
     */
    @Override
    public UploadUrlsInfo initMultiPartUpload(
            FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
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
                String uploadUrl =
                        rustfsClient.getPresignedObjectUrl(
                                bucketName, objectName, reqParams, Duration.ofHours(1));
                partList.add(uploadUrl);
            }
            log.info("文件初始化分片成功,{}/{}: uploadId={}", bucketName, objectName, uploadId);
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during init multipart upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 获取分页取得的真实分片清单
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param uploadId 供应商分片上传标识
     * @return 分页取得的真实分片清单
     */
    @Override
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        try {
            List<Part> parts = getParts(bucketName, objectName, uploadId);
            return parts.stream()
                    .map(
                            x -> {
                                PartData partData = new PartData();
                                partData.setPartNumber(x.partNumber());
                                partData.setEtag(x.eTag());
                                partData.setSize(x.size());
                                return partData;
                            })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during get parts for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 拒绝未提供完整校验清单的旧合并入口。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param uploadId 供应商分片上传标识
     * @return 此旧入口拒绝合并；必须改用带已验证分片清单的操作句柄
     */
    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        throw new IllegalStateException(
                "Multipart completion requires a validated session manifest and operation handle");
    }

    private List<Part> getParts(String bucketName, String objectName, String uploadId) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            int partNumberMarker = 0;
            boolean isTruncated = true;
            List<Part> parts = new ArrayList<>();
            while (isTruncated) {
                ListPartsResponse partResult =
                        rustfsClient.listMultipart(
                                bucketName, objectName, partNumberMarker, uploadId);
                parts.addAll(partResult.parts());
                // 检查是否还有更多分片
                isTruncated = partResult.isTruncated();
                if (isTruncated) {
                    // 更新partNumberMarker以获取下一页的分片数据
                    int nextMarker = partResult.nextPartNumberMarker();
                    if (nextMarker <= partNumberMarker) {
                        throw new IllegalStateException("OSS pagination did not advance");
                    }
                    partNumberMarker = nextMarker;
                }
            }
            return parts;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during get parts for {}/{} ,uploadId {}: {}",
                    bucketName,
                    objectName,
                    uploadId,
                    e.getMessage(),
                    e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 取消指定供应商分片上传。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param uploadId 供应商分片上传标识
     * @return 取消成功返回 true
     */
    @Override
    public boolean abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            rustfsClient.abortMultipartUpload(bucketName, objectName, uploadId);
            log.info(
                    "Successfully aborted multipart upload for {}/{}，uploadId:{}",
                    bucketName,
                    objectName,
                    uploadId);
            return true;
        } catch (Exception e) {
            log.error(
                    "Rustfs unexpected error during aborted multipart upload {}/{} ,uploadId {}:"
                            + " {}",
                    bucketName,
                    objectName,
                    uploadId,
                    e.getMessage(),
                    e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 删除指定目标对象。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     */
    @Override
    public void removeObject(String bucketName, String objectName) {
        CustomRustfsClient rustfsClient = obtainClient();
        S3Client s3Client = rustfsClient.getClient();
        DeleteObjectRequest deleteObjectRequest =
                DeleteObjectRequest.builder().bucket(bucketName).key(objectName).build();
        try {
            s3Client.deleteObject(deleteObjectRequest);
        } catch (Exception e) {
            log.error(
                    "Rustfs Unexpected error during remove object for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 获取限时对象上传签名 URL
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param contentType 对象媒体类型
     * @return 限时对象上传签名 URL
     */
    @Override
    public UploadUrlsInfo getPresignedObjectUploadUrl(
            String bucketName, String objectName, String contentType) {
        CustomRustfsClient rustfsClient = obtainClient();
        try {
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            List<String> urlList = new ArrayList<>();
            String uploadId = IdUtil.simpleUUID();
            Map<String, String> reqParams = new HashMap<>();
            String url =
                    rustfsClient.getPresignedObjectUrl(
                            bucketName, objectName, reqParams, Duration.ofHours(1));
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error(
                    "Rustfs Unexpected error during get presigned object upload url for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    /**
     * 在显式操作域内下载指定文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param consumer 同步读取回调，结束后关闭下载流
     */
    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        try (ResponseInputStream<GetObjectResponse> stream =
                     obtainClient()
                             .getClient()
                             .getObject(
                                     GetObjectRequest.builder()
                                             .key(objectName)
                                             .bucket(bucketName)
                                             .build(),
                                     ResponseTransformer.toInputStream())) {
            consumer.accept(stream);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    protected DownloadMetadata fetchDownloadMetadata(String bucketName, String objectName) {
        S3Client s3Client = obtainClient().getClient();
        HeadObjectResponse headResponse =
                s3Client.headObject(
                        HeadObjectRequest.builder().bucket(bucketName).key(objectName).build());
        return new DownloadMetadata(
                headResponse.contentLength(),
                headResponse.contentType(),
                headResponse.lastModified().toString(),
                headResponse.eTag());
    }

    @Override
    protected InputStream openDownloadStream(
            String bucketName, String objectName, long startByte, long endByte) throws Exception {
        S3Client s3Client = obtainClient().getClient();
        GetObjectRequest objectRequest =
                GetObjectRequest.builder()
                        .key(objectName)
                        .bucket(bucketName)
                        .range("bytes=" + startByte + "-" + endByte)
                        .build();
        return s3Client.getObject(objectRequest, ResponseTransformer.toInputStream());
    }

    /** 关闭代际持有的供应商客户端资源。 */
    @Override
    public void shutdown() {
        // 委托给 OssClient.close() 统一管理资源释放
        OssClient<CustomRustfsClient> ossClient = client;
        if (ossClient != null) {
            ossClient.close();
        }
    }

    /**
     * 设置指定 Bucket 的访问策略。
     *
     * @param bucketName Bucket 名称
     * @param policy Bucket 访问策略
     */
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
                    s3Client.putBucketPolicy(
                            PutBucketPolicyRequest.builder()
                                    .bucket(bucketName)
                                    .policy(policyConfig)
                                    .build());
                    break;
                case WRITE_ONLY:
                    policyConfig = WRITE_ONLY.replace(BUCKET_PARAM, bucketName);
                    s3Client.putBucketPolicy(
                            PutBucketPolicyRequest.builder()
                                    .bucket(bucketName)
                                    .policy(policyConfig)
                                    .build());
                    break;
                case READ_WRITE:
                    policyConfig = READ_WRITE.replace(BUCKET_PARAM, bucketName);
                    s3Client.putBucketPolicy(
                            PutBucketPolicyRequest.builder()
                                    .bucket(bucketName)
                                    .policy(policyConfig)
                                    .build());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error(
                    "Rustfs Unexpected error during set bucket policy for {}: {}",
                    bucketName,
                    e.getMessage());
            throw new OssException(
                    OssErrorCode.SET_BUCKET_POLICY_ERROR.getErrorCode(), e.getMessage());
        }
    }

    /**
     * 校验真实分片清单后合并，并回读对象大小。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param uploadId   供应商分片上传标识
     * @param verified   已验证且保留真实编号的分片清单
     */
    @Override
    public void completeMultipartUpload(
            String bucketName, String objectName, String uploadId, List<PartData> verified) {
        try {
            List<CompletedPart> parts = new ArrayList<>();
            for (PartData part : verified) {
                parts.add(
                        CompletedPart.builder()
                                .partNumber(part.getPartNumber())
                                .eTag(part.getEtag())
                                .build());
            }
            obtainClient()
                    .getClient()
                    .completeMultipartUpload(
                            CompleteMultipartUploadRequest.builder()
                                    .bucket(bucketName)
                                    .key(objectName)
                                    .uploadId(uploadId)
                                    .multipartUpload(
                                            CompletedMultipartUpload.builder().parts(parts).build())
                                    .build());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        }
    }
}
