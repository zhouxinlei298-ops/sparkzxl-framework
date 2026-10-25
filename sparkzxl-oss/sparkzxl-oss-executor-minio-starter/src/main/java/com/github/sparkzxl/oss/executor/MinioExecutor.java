package com.github.sparkzxl.oss.executor;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.github.sparkzxl.oss.client.CustomMinioClient;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.enums.BucketPolicyEnum;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.support.OssErrorCode;
import com.github.sparkzxl.oss.support.OssException;
import com.github.sparkzxl.oss.utils.OssUtils;
import com.google.common.base.Stopwatch;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Maps;
import io.minio.*;
import io.minio.errors.ErrorResponseException;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * minio 执行器
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:54:27
 */
@Slf4j
public class MinioExecutor extends AbstractOssExecutor<CustomMinioClient> {

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
     * 使用当前代际的 MinIO 客户端执行固定目标操作。
     *
     * @param client 当前代际持有的原生客户端
     */
    public MinioExecutor(OssClient<CustomMinioClient> client) {
        super(client);
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
        try {
            CustomMinioClient minioClient = obtainClient();
            CompletableFuture<Boolean> bucketedExists =
                    minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            Boolean found = bucketedExists.get(5, TimeUnit.SECONDS);
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during create bucket for {}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during remove bucket for {}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            objectUrl =
                    minioClient.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.GET)
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .expiry(expire)
                                    .build());
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during get object url for {}/{}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            CompletableFuture<GetObjectResponse> getObjectResponseCompletableFuture =
                    minioClient.getObject(
                            GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            GetObjectResponse minioClientObject = getObjectResponseCompletableFuture.get();
            if (minioClientObject == null) {
                throw new OssException(
                        OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(),
                        String.format(
                                "Object not found or response is null for bucket [%s], object [%s]",
                                bucketName, objectName));
            }
            OssObject ossObject = new OssObject();
            ossObject.setBucketName(minioClientObject.bucket());
            ossObject.setKey(minioClientObject.object());
            ossObject.setObjectContent(minioClientObject);
            return ossObject;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during get object info for {}/{}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            CompletableFuture<StatObjectResponse> getObjectResponseCompletableFuture =
                    minioClient.statObject(
                            StatObjectArgs.builder().bucket(bucketName).object(objectName).build());
            StatObjectResponse objectResponse = getObjectResponseCompletableFuture.get();
            if (objectResponse == null) {
                throw new OssException(
                        OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(),
                        String.format(
                                "Object metadata not found or response is null for bucket [%s],"
                                        + " object [%s]",
                                bucketName, objectName));
            }
            OssMetadata ossMetadata = new OssMetadata();
            ossMetadata.setBucketName(bucketName);
            ossMetadata.setObjectName(objectName);
            ossMetadata.setSize(objectResponse.size());
            ossMetadata.setContentType(objectResponse.contentType());
            ossMetadata.setLastModified(objectResponse.lastModified().toLocalDateTime());
            ossMetadata.setEtag(objectResponse.etag());
            ossMetadata.setUserMetadata(objectResponse.userMetadata());
            return ossMetadata;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during get object metadata for {}/{}: {}",
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
                    .statObject(
                            StatObjectArgs.builder().bucket(bucketName).object(objectName).build())
                    .get();
            return true;
        } catch (Exception e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ErrorResponseException) {
                    String code = ((ErrorResponseException) cause).errorResponse().code();
                    if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                        return false;
                    }
                }
                cause = cause.getCause();
            }
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
        try (InputStream uploadStream = multipartFile.getInputStream()) {
            CustomMinioClient minioClient = obtainClient();
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            PutObjectArgs putObjectArgs =
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    uploadStream, size, -1)
                            .contentType(contentType)
                            .build();
            CompletableFuture<ObjectWriteResponse> writeResponseCompletableFuture =
                    minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = writeResponseCompletableFuture.get();
            return buildPushObjectResponse(
                    bucketName, objectName, size, contentType, objectWriteResponse.etag());
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during multipartFile upload for {}/{}: {}",
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
        BufferedInputStream tempInputStream = null;
        try {
            CustomMinioClient minioClient = obtainClient();
            long size = FileUtil.size(file);
            tempInputStream = FileUtil.getInputStream(file);
            String mimeType = FileUtil.getMimeType(file.getName());
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            PutObjectArgs putObjectArgs =
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    tempInputStream, size, -1)
                            .contentType(finalMimeType)
                            .build();
            CompletableFuture<ObjectWriteResponse> writeResponseCompletableFuture =
                    minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = writeResponseCompletableFuture.get();
            OssPushObjectResponse response =
                    buildPushObjectResponse(
                            bucketName,
                            objectName,
                            size,
                            finalMimeType,
                            objectWriteResponse.etag());
            succeeded = true;
            return response;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during local file upload for {}/{}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            log.debug("开始下载受控远程资源");
            long size = HttpUtil.downloadFile(fileUrl, tempFile, 600000);
            log.debug("受控远程资源下载完成，字节数：{}", size);
            String mimeType = FileUtil.getMimeType(fileUrl);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            tempInputStream = FileUtil.getInputStream(tempFile);
            PutObjectArgs putObjectArgs =
                    PutObjectArgs.builder().bucket(bucketName).object(objectName).stream(
                                    tempInputStream, size, -1)
                            .contentType(finalMimeType)
                            .build();
            CompletableFuture<ObjectWriteResponse> completableFuture =
                    minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = completableFuture.get();
            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]秒", totalTime);
            return buildPushObjectResponse(
                    bucketName, objectName, size, finalMimeType, objectWriteResponse.etag());
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during remote file upload for {}/{}: {}",
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
        // MinIO SDK根据流大小执行真正的对象分片上传，不能把同名分片装入Snowball归档。
        putObject(bucketName, objectName, multipartFile);
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
        try {
            CustomMinioClient minioClient = obtainClient();
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
                uploadId =
                        minioClient.initMultiPartUpload(
                                bucketName, null, objectName, headers, null);
            }
            uploadUrlsInfo.setUploadId(uploadId);
            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= chunkCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl =
                        minioClient.getPresignedObjectUrl(
                                GetPresignedObjectUrlArgs.builder()
                                        .method(Method.PUT)
                                        .bucket(bucketName)
                                        .object(objectName)
                                        .expiry(1, TimeUnit.HOURS)
                                        .extraQueryParams(reqParams)
                                        .build());
                partList.add(uploadUrl);
            }
            log.info("文件初始化分片成功,{}/{}: uploadId={}", bucketName, objectName, uploadId);
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during init multipart upload for {}/{}: {}",
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
                                partData.setEtag(x.etag());
                                partData.setSize(x.partSize());
                                return partData;
                            })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during get parts for {}/{}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            int partNumberMarker = 0;
            boolean isTruncated = true;
            List<Part> parts = new ArrayList<>();
            while (isTruncated) {
                ListPartsResponse partResult =
                        minioClient.listMultipart(
                                bucketName,
                                null,
                                objectName,
                                1000,
                                partNumberMarker,
                                uploadId,
                                null,
                                null);
                parts.addAll(partResult.result().partList());
                // 检查是否还有更多分片
                isTruncated = partResult.result().isTruncated();
                if (isTruncated) {
                    // 更新partNumberMarker以获取下一页的分片数据
                    int nextMarker = partResult.result().nextPartNumberMarker();
                    if (nextMarker <= partNumberMarker) {
                        throw new IllegalStateException("OSS pagination did not advance");
                    }
                    partNumberMarker = nextMarker;
                }
            }
            return parts;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during get parts for {}/{} ,uploadId {}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            AbortMultipartUploadResponse abortMultipartUploadResponse =
                    minioClient.abortMultipartUpload(
                            bucketName, null, objectName, uploadId, null, null);
            log.info(
                    "Successfully aborted multipart upload for {}/{}，uploadId:{}",
                    bucketName,
                    objectName,
                    abortMultipartUploadResponse.uploadId());
            return true;
        } catch (Exception e) {
            log.error(
                    "MinIO unexpected error during aborted multipart upload {}/{} ,uploadId {}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            minioClient.removeObject(
                    RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            log.error(
                    "MinIO Unexpected error during remove object for {}/{}: {}",
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
        try {
            CustomMinioClient minioClient = obtainClient();
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            List<String> urlList = new ArrayList<>();
            // 主要是针对图片，若需要通过浏览器直接查看，而不是下载，需要指定对应的 content-type
            Map<String, String> headers = Maps.newHashMap();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);
            String uploadId = IdUtil.simpleUUID();
            Map<String, String> reqParams = new HashMap<>();
            String url =
                    minioClient.getPresignedObjectUrl(
                            GetPresignedObjectUrlArgs.builder()
                                    .method(Method.PUT)
                                    .bucket(bucketName)
                                    .object(objectName)
                                    .extraHeaders(headers)
                                    .extraQueryParams(reqParams)
                                    .expiry(1, TimeUnit.HOURS)
                                    .build());
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error(
                    "MinIO Unexpected error during get presigned object upload url for {}/{}: {}",
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
        try (GetObjectResponse stream =
                     obtainClient()
                             .getObject(
                                     GetObjectArgs.builder()
                                             .bucket(bucketName)
                                             .object(objectName)
                                             .build())
                             .get()) {
            if (stream == null) {
                throw new IllegalStateException("OSS object response is missing");
            }
            consumer.accept(stream);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    protected DownloadMetadata fetchDownloadMetadata(String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        try {
            StatObjectResponse objectResponse =
                    minioClient
                            .statObject(
                                    StatObjectArgs.builder()
                                            .bucket(bucketName)
                                            .object(objectName)
                                            .build())
                            .get();
            return new DownloadMetadata(
                    objectResponse.size(),
                    objectResponse.contentType(),
                    objectResponse.lastModified().toString(),
                    objectResponse.etag());
        } catch (Exception e) {
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    protected InputStream openDownloadStream(
            String bucketName, String objectName, long startByte, long endByte) throws Exception {
        CustomMinioClient minioClient = obtainClient();
        long contentLength = endByte - startByte + 1;
        return minioClient
                .getObject(
                        GetObjectArgs.builder()
                                .bucket(bucketName)
                                .object(objectName)
                                .offset(startByte)
                                .length(contentLength)
                                .build())
                .get();
    }

    /** 关闭代际持有的供应商客户端资源。 */
    @Override
    public void shutdown() {
        // 委托给 OssClient.close() 统一管理资源释放
        OssClient<CustomMinioClient> ossClient = client;
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
        try {
            CustomMinioClient minioClient = obtainClient();
            String policyConfig;
            switch (policy) {
                case READ_ONLY:
                    policyConfig = READ_ONLY.replace(BUCKET_PARAM, bucketName);
                    minioClient.setBucketPolicy(
                            SetBucketPolicyArgs.builder()
                                    .bucket(bucketName)
                                    .config(policyConfig)
                                    .build());
                    break;
                case WRITE_ONLY:
                    policyConfig = WRITE_ONLY.replace(BUCKET_PARAM, bucketName);
                    minioClient.setBucketPolicy(
                            SetBucketPolicyArgs.builder()
                                    .bucket(bucketName)
                                    .config(policyConfig)
                                    .build());
                    break;
                case READ_WRITE:
                    policyConfig = READ_WRITE.replace(BUCKET_PARAM, bucketName);
                    minioClient.setBucketPolicy(
                            SetBucketPolicyArgs.builder()
                                    .bucket(bucketName)
                                    .config(policyConfig)
                                    .build());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error(
                    "MinIO Unexpected error during set bucket policy for {}: {}",
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
            Part[] parts = new Part[verified.size()];
            for (int i = 0; i < verified.size(); i++) {
                PartData part = verified.get(i);
                parts[i] = new Part(part.getPartNumber(), part.getEtag());
            }
            obtainClient()
                    .mergeMultipartUpload(
                            bucketName, null, objectName, uploadId, parts, null, null);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        }
    }
}
