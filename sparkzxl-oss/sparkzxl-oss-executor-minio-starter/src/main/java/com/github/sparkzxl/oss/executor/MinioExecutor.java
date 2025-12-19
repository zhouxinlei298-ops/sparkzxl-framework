package com.github.sparkzxl.oss.executor;

import cn.hutool.core.io.FileUtil;
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
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * description: minio 执行器
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
    private static final String READ_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-只读
     */
    private static final String WRITE_ONLY = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:AbortMultipartUpload\",\"s3:DeleteObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";
    /**
     * bucket权限-读写
     */
    private static final String READ_WRITE = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:GetBucketLocation\",\"s3:ListBucket\",\"s3:ListBucketMultipartUploads\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "\"]},{\"Effect\":\"Allow\",\"Principal\":{\"AWS\":[\"*\"]},\"Action\":[\"s3:DeleteObject\",\"s3:GetObject\",\"s3:ListMultipartUploadParts\",\"s3:PutObject\",\"s3:AbortMultipartUpload\"],\"Resource\":[\"arn:aws:s3:::" + BUCKET_PARAM + "/*\"]}]}";

    public MinioExecutor(OssClient<CustomMinioClient> client) {
        super(client);
    }

    @Override
    public void createBucket(String bucketName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            CompletableFuture<Boolean> bucketedExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            Boolean found = bucketedExists.get(5, TimeUnit.SECONDS);
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (Exception e) {
            log.error("MinIO unexpected error during create bucket for {}: {}",
                    bucketName, e.getMessage(), e);
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            log.error("MinIO unexpected error during remove bucket for {}: {}",
                    bucketName, e.getMessage(), e);
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        String objectUrl;
        try (CustomMinioClient minioClient = obtainClient()) {
            objectUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName).expiry(expire).build());
        } catch (Exception e) {
            log.error("MinIO unexpected error during get object url for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

    @Override
    public OssObject getObjectInfo(String bucketName, String objectName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            CompletableFuture<GetObjectResponse> getObjectResponseCompletableFuture = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            GetObjectResponse minioClientObject = getObjectResponseCompletableFuture.get();
            OssObject ossObject = new OssObject();
            ossObject.setBucketName(minioClientObject.bucket());
            ossObject.setKey(minioClientObject.object());
            ossObject.setObjectContent(minioClientObject);
            return ossObject;
        } catch (Exception e) {
            log.error("MinIO unexpected error during get object info for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssMetadata getOssMetadata(String bucketName, String objectName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            CompletableFuture<StatObjectResponse> getObjectResponseCompletableFuture = minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
            StatObjectResponse objectResponse = getObjectResponseCompletableFuture.get();
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
            log.error("MinIO unexpected error during get object metadata for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            CompletableFuture<StatObjectResponse> completableFuture = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            StatObjectResponse stat = completableFuture.get();
            return stat != null && stat.lastModified() != null;
        } catch (Exception e) {
            log.error("MinIO unexpected error during checking whether objectName exists for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
        try (CustomMinioClient minioClient = obtainClient()) {
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(multipartFile.getInputStream(), size, -1)
                    .contentType(contentType
                    ).build();
            CompletableFuture<ObjectWriteResponse> writeResponseCompletableFuture = minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = writeResponseCompletableFuture.get();
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(contentType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", objectWriteResponse.etag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("MinIO unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        uploadFileLimit(objectName);
        File tempFile = new File(filePath);
        BufferedInputStream tempInputStream = null;
        try (CustomMinioClient minioClient = obtainClient()) {
            long size = FileUtil.size(tempFile);
            tempInputStream = FileUtil.getInputStream(tempFile);
            String mimeType = FileUtil.getType(tempFile);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName).stream(tempInputStream, size, -1)
                    .contentType(finalMimeType)
                    .build();
            CompletableFuture<ObjectWriteResponse> writeResponseCompletableFuture = minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = writeResponseCompletableFuture.get();
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(finalMimeType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", objectWriteResponse.etag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("MinIO unexpected error during local file upload for {}/{}: {}",
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
            if (!tempFile.delete()) {
                log.warn("临时文件删除失败，文件路径：{}", tempFile.getAbsolutePath());
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
        try (CustomMinioClient minioClient = obtainClient()) {
            log.info("HTTP下载文件[{}]:开始======", fileUrl);
            long size = HttpUtil.downloadFile(fileUrl, tempFile, 600000);
            log.info("HTTP下载文件[{}]:结束,文件大小：{}======", fileUrl, size);
            String mimeType = FileUtil.getMimeType(fileUrl);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            tempInputStream = FileUtil.getInputStream(tempFile);
            // 4. 上传到MinIO
            tempInputStream = FileUtil.getInputStream(tempFile);
            PutObjectArgs putObjectArgs = PutObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .stream(tempInputStream, size, -1)
                    .contentType(finalMimeType).build();
            CompletableFuture<ObjectWriteResponse> completableFuture = minioClient.putObject(putObjectArgs);
            ObjectWriteResponse objectWriteResponse = completableFuture.get();
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
            log.info("文件上传成功，ETag: {}", objectWriteResponse.etag());
            return pushObjectResponse;
        } catch (Exception e) {
            log.error("MinIO unexpected error during remote file upload for {}/{}: {}",
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
            // 删除临时文件（双重保障：主动删除+JVM退出删除）
            if (tempFile != null && !tempFile.delete()) {
                log.warn("临时文件删除失败，文件路径：{}", tempFile.getAbsolutePath());
            }
        }
    }

    @Override
    public void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
        try (CustomMinioClient minioClient = obtainClient()) {
            List<SnowballObject> snowballObjects = new ArrayList<>();
            InputStream inputStream = multipartFile.getInputStream();
            // 计算文件有多少个分片。
            // 1MB
            final long partSize = 1024 * 1024L;
            long fileLength = multipartFile.getSize();
            int partCount = (int) (fileLength / partSize);
            if (fileLength % partSize != 0) {
                partCount++;
            }
            // 遍历分片上传。
            for (int i = 0; i < partCount; i++) {
                long startPos = i * partSize;
                long curPartSize = (i + 1 == partCount) ? (fileLength - startPos) : partSize;
                // 跳过已经上传的分片。
                inputStream.skip(startPos);
                SnowballObject snowballObject = new SnowballObject(objectName, inputStream, curPartSize, null);
                snowballObjects.add(snowballObject);
            }
            log.info("objectName [{}] upload started", objectName);
            minioClient.uploadSnowballObjects(UploadSnowballObjectsArgs.builder().bucket(bucketName).object(objectName).objects(snowballObjects).build());
            log.info("objectName [{}] upload complete", objectName);
        } catch (Exception e) {
            log.error("MinIO unexpected error during multipart file chunk upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        try (CustomMinioClient minioClient = obtainClient()) {
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
                uploadId = minioClient.initMultiPartUpload(bucketName, null, objectName, headers, null);
            }
            uploadUrlsInfo.setUploadId(uploadId);

            List<String> partList = new ArrayList<>();
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            for (int i = 1; i <= chunkCount; i++) {
                reqParams.put("partNumber", String.valueOf(i));
                String uploadUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                        .method(Method.PUT)
                        .bucket(bucketName)
                        .object(objectName)
                        .expiry(1, TimeUnit.HOURS)
                        .extraQueryParams(reqParams)
                        .build());
                partList.add(uploadUrl);
            }
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("MinIO unexpected error during init multipart upload for {}/{}: {}",
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
                        partData.setEtag(x.etag());
                        partData.setSize(x.partSize());
                        return partData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("MinIO unexpected error during get parts for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }

    }

    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        try (CustomMinioClient minioClient = obtainClient()) {
            // 获取所有分片
            log.info("通过 <{}-{}-{}> 合并<分片上传>数据", objectName, uploadId, bucketName);
            List<Part> partsList = getParts(bucketName, objectName, uploadId);
            Part[] parts = new Part[partsList.size()];
            int partNumber = 1;
            for (Part part : partsList) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            // 合并分片
            ObjectWriteResponse writeResponse = minioClient.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts, null, null);
            log.info("合并分片成功，上传分片完成.uploadId：{}{}", uploadId, writeResponse.etag());
            return true;
        } catch (Exception e) {
            log.error("MinIO Unexpected error during merge multipart upload completion for {}/{}/{}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    private List<Part> getParts(String bucketName, String objectName, String uploadId) {
        try (CustomMinioClient minioClient = obtainClient()) {
            int partNumberMarker = 0;
            boolean isTruncated = true;
            List<Part> parts = new ArrayList<>();
            while (isTruncated) {
                ListPartsResponse partResult = minioClient.listMultipart(bucketName, null, objectName, 10000, partNumberMarker, uploadId, null, null);
                parts.addAll(partResult.result().partList());
                // 检查是否还有更多分片
                isTruncated = partResult.result().isTruncated();
                if (isTruncated) {
                    // 更新partNumberMarker以获取下一页的分片数据
                    partNumberMarker = partResult.result().nextPartNumberMarker();
                }
            }
            return parts;
        } catch (Exception e) {
            log.error("MinIO unexpected error during get parts for {}/{} ,uploadId {}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }

    }


    @Override
    public void removeObject(String bucketName, String objectName) {
        try (CustomMinioClient minioClient = obtainClient()) {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .build());
        } catch (Exception e) {
            log.error("MinIO Unexpected error during remove object for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType) {
        try (CustomMinioClient minioClient = obtainClient()) {
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
            reqParams.put("uploadId", uploadId);
            String url = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.PUT)
                    .bucket(bucketName)
                    .object(objectName)
                    .extraHeaders(headers)
                    .extraQueryParams(reqParams)
                    .expiry(1, TimeUnit.DAYS)
                    .build());
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("MinIO Unexpected error during get presigned object upload url for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        try (CustomMinioClient minioClient = obtainClient()) {
            CompletableFuture<GetObjectResponse> responseCompletableFuture = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            GetObjectResponse objectResponse = responseCompletableFuture.get();
            consumer.accept(objectResponse);
        } catch (Exception e) {
            log.error("MinIO Unexpected error during download file for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    public void downloadMultipartFile(String bucketName, String objectName, String fileName, HttpServletRequest request, HttpServletResponse response) {
        CustomMinioClient minioClient = obtainClient();
        InputStream stream = null;
        BufferedOutputStream os = null;

        try {
            // 1. 获取文件元数据
            StatObjectResponse objectResponse = minioClient.statObject(
                    StatObjectArgs.builder().bucket(bucketName).object(objectName).build()
            ).get();
            long fileSize = objectResponse.size();

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
                contentType = objectResponse.contentType();
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

            response.setHeader("Last-Modified", objectResponse.lastModified().toString());
            response.setHeader("Content-Length", String.valueOf(contentLength));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            response.setHeader("ETag", "\"" + objectResponse.etag() + "\"");

            // 5. 获取 MinIO 流
            // MinIO 的 getObject 已经支持 offset 和 length，返回的流就是精确的片段
            stream = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(bucketName)
                    .object(objectName)
                    .offset(startByte)
                    .length(contentLength)
                    .build()).get();

            // 6. 写出数据
            os = new BufferedOutputStream(response.getOutputStream());

            // 使用 Spring 工具类直接拷贝流，无需手动循环
            StreamUtils.copy(stream, os);

            os.flush();
            response.flushBuffer();

        } catch (Exception e) {
            log.error("MinIO Unexpected error during download multipart file for {}/{}: {}",
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
    public void setBucketPolicy(String bucketName, BucketPolicyEnum policy) {
        try (CustomMinioClient minioClient = obtainClient()) {
            switch (policy) {
                case READ_ONLY:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(READ_ONLY.replace(BUCKET_PARAM, bucketName))
                            .build());
                    break;
                case WRITE_ONLY:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(WRITE_ONLY.replace(BUCKET_PARAM, bucketName))
                            .build());
                    break;
                case READ_WRITE:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                            .bucket(bucketName)
                            .config(READ_WRITE.replace(BUCKET_PARAM, bucketName))
                            .build());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("MinIO Unexpected error during set bucket policy for {}: {}",
                    bucketName, e.getMessage());
            throw new OssException(OssErrorCode.SET_BUCKET_POLICY_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void showdown() {
    }
}
