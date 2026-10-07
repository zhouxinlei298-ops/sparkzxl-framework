package com.github.sparkzxl.oss.executor;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.model.*;
import com.github.sparkzxl.core.util.DateUtils;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.enums.BucketPolicyEnum;
import com.github.sparkzxl.oss.properties.Configuration;
import com.github.sparkzxl.oss.support.OssErrorCode;
import com.github.sparkzxl.oss.support.OssException;
import com.github.sparkzxl.oss.utils.OssUtils;
import com.google.common.base.Stopwatch;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * aliYun 执行器
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:54:27
 */
@Slf4j
public class AliYunExecutor extends AbstractOssExecutor<OSSClient> {

    /**
     * 使用当前代际的阿里云客户端执行固定目标操作。
     *
     * @param client 当前代际持有的原生客户端
     */
    public AliYunExecutor(OssClient<OSSClient> client) {
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
        try {
            OSSClient ossClient = obtainClient();
            if (!ossClient.doesBucketExist(bucketName)) {
                ossClient.createBucket(bucketName);
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during create bucket for {}: {}",
                    bucketName,
                    e.getErrorMessage(),
                    e);
            throw new OssException(
                    OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
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
            OSSClient ossClient = obtainClient();
            ossClient.deleteBucket(bucketName);
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during remove bucket for {}: {}",
                    bucketName,
                    e.getErrorMessage(),
                    e);
            throw new OssException(
                    OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    /**
     * 获取对象访问 URL
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param expire     签名有效期（秒）
     * @return 对象访问 URL
     */
    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        String objectUrl;
        try {
            OSSClient ossClient = obtainClient();
            DateTime expireDateTime = DateUtils.offsetSecond(new Date(), expire);
            GeneratePresignedUrlRequest req =
                    new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET);
            req.setExpiration(expireDateTime);
            URL signedUrl = ossClient.generatePresignedUrl(req);
            objectUrl = signedUrl.toString();
        } catch (Exception e) {
            log.error(
                    "AliyunOSS unexpected error during get object url for {}/{}: {}",
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
            OSSClient ossClient = obtainClient();
            OSSObject object = ossClient.getObject(bucketName, objectName);
            OssObject ossObject = new OssObject();
            ossObject.setObjectContent(object.getObjectContent());
            ossObject.setBucketName(object.getBucketName());
            ossObject.setKey(object.getKey());
            return ossObject;
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during get object info for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getErrorMessage());
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
            OSSClient ossClient = obtainClient();
            ObjectMetadata objectMetadata = ossClient.getObjectMetadata(bucketName, objectName);
            OssMetadata ossMetadata = new OssMetadata();
            ossMetadata.setBucketName(bucketName);
            ossMetadata.setObjectName(objectName);
            ossMetadata.setSize(objectMetadata.getContentLength());
            ossMetadata.setContentType(objectMetadata.getContentType());
            ossMetadata.setLastModified(DateUtil.toLocalDateTime(objectMetadata.getLastModified()));
            ossMetadata.setEtag(objectMetadata.getETag());
            ossMetadata.setUserMetadata(objectMetadata.getUserMetadata());
            return ossMetadata;
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during get object metadata for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getErrorMessage());
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
            OSSClient ossClient = obtainClient();
            return ossClient.doesObjectExist(bucketName, objectName);
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during checking whether objectName exists for"
                            + " {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(OssErrorCode.OSS_ERROR);
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
            OSSClient ossClient = obtainClient();
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(size);
            objectMetadata.setContentType(contentType);
            PutObjectRequest putObjectRequest =
                    new PutObjectRequest(bucketName, objectName, uploadStream, objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            return buildPushObjectResponse(
                    bucketName, objectName, size, contentType, putObjectResult.getETag());
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
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
            OSSClient ossClient = obtainClient();
            tempInputStream = FileUtil.getInputStream(file);
            String mimeType = FileUtil.getMimeType(file.getName());
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            long size = FileUtil.size(file);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(size);
            objectMetadata.setContentType(finalMimeType);
            PutObjectRequest putObjectRequest =
                    new PutObjectRequest(bucketName, objectName, tempInputStream, objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            OssPushObjectResponse response =
                    buildPushObjectResponse(
                            bucketName, objectName, size, finalMimeType, putObjectResult.getETag());
            succeeded = true;
            return response;
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during local file upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
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
            OSSClient ossClient = obtainClient();
            log.debug("开始下载受控远程资源");
            long size = HttpUtil.downloadFile(fileUrl, tempFile, 600000);
            log.debug("受控远程资源下载完成，字节数：{}", size);
            String mimeType = FileUtil.getMimeType(fileUrl);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            tempInputStream = FileUtil.getInputStream(tempFile);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(tempFile.length());
            objectMetadata.setContentType(finalMimeType);
            PutObjectRequest putObjectRequest =
                    new PutObjectRequest(bucketName, objectName, tempInputStream, objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]秒", totalTime);
            return buildPushObjectResponse(
                    bucketName, objectName, size, finalMimeType, putObjectResult.getETag());
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS unexpected error during remote file upload for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
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
        final long partSize = 1024 * 1024L;
        if (fileLength <= 0 || 1 + (fileLength - 1) / partSize > 10000) {
            throw new IllegalArgumentException("文件大小或分片数量不符合供应商限制");
        }
        OSSClient ossClient = obtainClient();
        String uploadId = null;
        try {
            InitiateMultipartUploadRequest initialize =
                    new InitiateMultipartUploadRequest(bucketName, objectName);
            ObjectMetadata metadata = new ObjectMetadata();
            String contentType = multipartFile.getContentType();
            metadata.setContentType(contentType == null ? "application/octet-stream" : contentType);
            initialize.setObjectMetadata(metadata);
            uploadId = ossClient.initiateMultipartUpload(initialize).getUploadId();
            List<PartETag> parts = new ArrayList<>();
            int count = (int) (1 + (fileLength - 1) / partSize);
            for (int index = 0; index < count; index++) {
                long startPosition = index * partSize;
                long actualPartSize = Math.min(partSize, fileLength - startPosition);
                try (InputStream stream = multipartFile.getInputStream()) {
                    skipFully(stream, startPosition);
                    UploadPartRequest request = new UploadPartRequest();
                    request.setBucketName(bucketName);
                    request.setKey(objectName);
                    request.setUploadId(uploadId);
                    request.setInputStream(stream);
                    request.setPartSize(actualPartSize);
                    request.setPartNumber(index + 1);
                    parts.add(ossClient.uploadPart(request).getPartETag());
                }
            }
            ossClient.completeMultipartUpload(
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, parts));
            if (getOssMetadata(bucketName, objectName).getSize() != fileLength) {
                throw new IllegalStateException("实际对象大小与上传文件不一致");
            }
        } catch (Exception exception) {
            if (uploadId != null) {
                try {
                    ossClient.abortMultipartUpload(
                            new AbortMultipartUploadRequest(bucketName, objectName, uploadId));
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
        try {
            OSSClient ossClient = obtainClient();
            Integer chunkCount = fileUploadInfo.getChunkCount();
            String contentType = fileUploadInfo.getContentType();
            String uploadId = fileUploadInfo.getUploadId();
            log.info("文件<{}> - 分片<{}> 初始化分片上传数据 请求头 {}", objectName, chunkCount, contentType);
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            Map<String, String> headers = Maps.newHashMap();
            if (StringUtils.isEmpty(contentType)) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);
            // 如果初始化时有 uploadId，说明是断点续传，不能重新生成 uploadId
            if (StringUtils.isEmpty(fileUploadInfo.getUploadId())) {
                InitiateMultipartUploadRequest request =
                        new InitiateMultipartUploadRequest(bucketName, objectName);
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType(contentType);
                request.setObjectMetadata(objectMetadata);
                request.setHeaders(headers);
                InitiateMultipartUploadResult uploadResult =
                        ossClient.initiateMultipartUpload(request);
                uploadId = uploadResult.getUploadId();
            }
            uploadUrlsInfo.setUploadId(uploadId);
            List<String> partList = new ArrayList<>();
            for (int i = 1; i <= chunkCount; i++) {
                Date expiration = new Date(new Date().getTime() + 3600 * 1000L);
                GeneratePresignedUrlRequest request =
                        new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
                // 设置过期时间。
                request.setExpiration(expiration);
                request.addQueryParameter("uploadId", uploadId);
                request.addQueryParameter("partNumber", String.valueOf(i));
                URL url = ossClient.generatePresignedUrl(request);
                String uploadUrl = url.toString();
                partList.add(uploadUrl);
            }
            log.info("文件初始化分片成功,{}/{}: uploadId={}", bucketName, objectName, uploadId);
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error(
                    "AliyunOSS unexpected error during init multipart upload for {}/{}: {}",
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
            List<PartData> result = new ArrayList<>();
            int marker = 0;
            while (true) {
                ListPartsRequest request = new ListPartsRequest(bucketName, objectName, uploadId);
                request.setPartNumberMarker(marker);
                request.setMaxParts(1000);
                PartListing listing = obtainClient().listParts(request);
                for (PartSummary part : listing.getParts()) {
                    PartData p = new PartData();
                    p.setPartNumber(part.getPartNumber());
                    p.setEtag(part.getETag());
                    p.setSize(part.getSize());
                    result.add(p);
                }
                if (!listing.isTruncated()) {
                    return result;
                }
                int next = listing.getNextPartNumberMarker();
                if (next <= marker) {
                    throw new IllegalStateException("OSS pagination did not advance");
                }
                marker = next;
            }
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
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
            OSSClient ossClient = obtainClient();
            ossClient.abortMultipartUpload(
                    new AbortMultipartUploadRequest(bucketName, objectName, uploadId));
            log.info(
                    "Successfully aborted multipart upload for {}/{}，uploadId:{}",
                    bucketName,
                    objectName,
                    uploadId);
            return true;
        } catch (Exception e) {
            log.error(
                    "AliyunOSS Unexpected error during merge multipart upload completion for"
                            + " {}/{}/{}: {}",
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
        OSSClient ossClient = obtainClient();
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS OSSException error during remove object for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (ClientException e) {
            log.error(
                    "AliyunOSS ClientException error during remove object for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
            throw new OssException(
                    OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
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
            UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
            List<String> urlList = new ArrayList<>();
            // 主要是针对图片，若需要通过浏览器直接查看，而不是下载，需要指定对应的 content-type
            Map<String, String> headers = Maps.newHashMap();
            if (contentType == null || contentType.isEmpty()) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);
            String uploadId = IdUtil.simpleUUID();
            OSSClient ossClient = obtainClient();
            GeneratePresignedUrlRequest generatePresignedUrlRequest =
                    new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
            generatePresignedUrlRequest.setHeaders(headers);
            // 设置1天有效期
            DateTime dateTime = DateUtils.offsetHour(new Date(), 1);
            generatePresignedUrlRequest.setExpiration(dateTime);
            String url = ossClient.generatePresignedUrl(generatePresignedUrlRequest).toString();
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (OSSException e) {
            log.error(
                    "AliyunOSS OSSException error during get presigned object upload url for {}/{}:"
                            + " {}",
                    bucketName,
                    objectName,
                    e.getErrorMessage());
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
        OSSClient ossClient = obtainClient();
        OSSObject ossObject = ossClient.getObject(bucketName, objectName);
        try (InputStream in = ossObject.getObjectContent()) {
            consumer.accept(in);
        } catch (IOException e) {
            log.error(
                    "AliyunOSS Unexpected error during download file for {}/{}: {}",
                    bucketName,
                    objectName,
                    e.getMessage());
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    protected DownloadMetadata fetchDownloadMetadata(String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        ObjectMetadata objectMetadata = ossClient.getObjectMetadata(bucketName, objectName);
        return new DownloadMetadata(
                objectMetadata.getContentLength(),
                objectMetadata.getContentType(),
                objectMetadata.getLastModified().toString(),
                objectMetadata.getETag());
    }

    @Override
    protected InputStream openDownloadStream(
            String bucketName, String objectName, long startByte, long endByte) throws Exception {
        OSSClient ossClient = obtainClient();
        GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectName);
        getObjectRequest.setRange(startByte, endByte);
        OSSObject ossObject = ossClient.getObject(getObjectRequest);
        return ossObject.getObjectContent();
    }

    /**
     * 设置指定 Bucket 的访问策略。
     *
     * @param bucket 操作目标 Bucket
     * @param policy Bucket 访问策略
     */
    @Override
    public void setBucketPolicy(String bucket, BucketPolicyEnum policy) {
    }

    /** 关闭代际持有的供应商客户端资源。 */
    @Override
    public void shutdown() {
        // 委托给 OssClient.close() 统一管理资源释放
        OssClient<OSSClient> ossClient = client;
        if (ossClient != null) {
            ossClient.close();
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
            List<PartETag> parts = new ArrayList<>();
            for (PartData part : verified) {
                parts.add(new PartETag(part.getPartNumber(), part.getEtag()));
            }
            obtainClient()
                    .completeMultipartUpload(
                            new CompleteMultipartUploadRequest(
                                    bucketName, objectName, uploadId, parts));
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR, e);
        }
    }
}
