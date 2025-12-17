package com.github.sparkzxl.oss.executor;

import cn.hutool.core.exceptions.ExceptionUtil;
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
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
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
        CustomMinioClient minioClient = obtainClient();
        try {
            CompletableFuture<Boolean> bucketedExists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucketName).build());
            Boolean found = bucketedExists.get(5, TimeUnit.SECONDS);
            if (!found) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucketName).build());
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (Exception e) {
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        CustomMinioClient minioClient = obtainClient();
        try {
            minioClient.removeBucket(RemoveBucketArgs.builder().bucket(bucketName).build());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        CustomMinioClient minioClient = obtainClient();
        String objectUrl;
        try {
            objectUrl = minioClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder().bucket(bucketName).object(objectName).expiry(expire).build());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

    @Override
    public OssObject getObjectInfo(String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        try {
            CompletableFuture<GetObjectResponse> getObjectResponseCompletableFuture = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            GetObjectResponse minioClientObject = getObjectResponseCompletableFuture.get();
            OssObject ossObject = new OssObject();
            ossObject.setBucketName(minioClientObject.bucket());
            ossObject.setKey(minioClientObject.object());
            ossObject.setObjectContent(minioClientObject);
            return ossObject;
        } catch (Exception e) {
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }
    @Override
    public OssMetadata getOssMetadata(String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        try {
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
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        try {
            CompletableFuture<StatObjectResponse> completableFuture = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(objectName)
                            .build());
            StatObjectResponse stat = completableFuture.get();
            return stat != null && stat.lastModified() != null;
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
        CustomMinioClient minioClient = obtainClient();
        try {
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
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        uploadFileLimit(objectName);
        CustomMinioClient minioClient = obtainClient();
        File tempFile = new File(filePath);
        BufferedInputStream tempInputStream = null;
        try {
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
        CustomMinioClient minioClient = obtainClient();
        Stopwatch stopwatch = Stopwatch.createStarted();
        String fileUrl = url.toString();
        File tempFile = FileUtil.createTempFile();
        // 注册JVM退出时自动删除临时文件（双重保障）
        tempFile.deleteOnExit();
        InputStream tempInputStream = null;
        try {
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
        CustomMinioClient minioClient = obtainClient();
        try {
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
            log.error("上传minio失败：{}", ExceptionUtil.stacktraceToString(e));
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        Integer chunkCount = fileUploadInfo.getChunkCount();
        String contentType = fileUploadInfo.getContentType();
        String uploadId = fileUploadInfo.getUploadId();
        log.info("文件<{}> - 分片<{}> 初始化分片上传数据 请求头 {}", objectName, chunkCount, contentType);
        UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
        try {
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
            log.info("文件初始化分片成功");
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("初始化分片上传失败: {}", e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        List<Part> parts;
        try {
            parts = getParts(bucketName, objectName, uploadId);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
        return parts.stream()
                .map(x -> {
                    PartData partData = new PartData();
                    partData.setPartNumber(x.partNumber());
                    partData.setEtag(x.etag());
                    partData.setSize(x.partSize());
                    return partData;
                })
                .collect(Collectors.toList());
    }

    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        CustomMinioClient minioClient = obtainClient();
        log.info("通过 <{}-{}-{}> 合并<分片上传>数据", objectName, uploadId, bucketName);
        // 获取所有分片
        try {
            List<Part> partsList = getParts(bucketName, objectName, uploadId);
            Part[] parts = new Part[partsList.size()];
            int partNumber = 1;
            for (Part part : partsList) {
                parts[partNumber - 1] = new Part(partNumber, part.etag());
                partNumber++;
            }
            // 合并分片
            minioClient.mergeMultipartUpload(bucketName, null, objectName, uploadId, parts, null, null);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
        return true;
    }

    @NotNull
    private List<Part> getParts(String bucketName, String objectName, String uploadId) throws Exception {
        CustomMinioClient minioClient = obtainClient();
        int partNumberMarker = 0;
        boolean isTruncated = true;
        List<Part> parts = new ArrayList<>();
        while (isTruncated) {
            ListPartsResponse partResult = minioClient.listMultipart(bucketName, null, objectName, 1000, partNumberMarker, uploadId, null, null);
            parts.addAll(partResult.result().partList());
            // 检查是否还有更多分片
            isTruncated = partResult.result().isTruncated();
            if (isTruncated) {
                // 更新partNumberMarker以获取下一页的分片数据
                partNumberMarker = partResult.result().nextPartNumberMarker();
            }
        }
        return parts;
    }


    @Override
    public void removeObject(String bucketName, String objectName) {
        CustomMinioClient minioClient = obtainClient();
        try {
            minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucketName).object(objectName).build());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType) {
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
        CustomMinioClient minioClient = obtainClient();
        try {
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
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        CustomMinioClient minioClient = obtainClient();
        try {
            CompletableFuture<GetObjectResponse> responseCompletableFuture = minioClient.getObject(GetObjectArgs.builder().bucket(bucketName).object(objectName).build());
            GetObjectResponse objectResponse = responseCompletableFuture.get();
            consumer.accept(objectResponse);
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    public ResponseEntity<byte[]> downloadMultipartFile(String bucketName, String objectName, String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        CustomMinioClient minioClient = obtainClient();
        try {
            String range = request.getHeader("Range");
            log.info("下载文件的 bucket <{}>，object <{}>", bucketName, objectName);
            StatObjectResponse objectResponse = minioClient.statObject(StatObjectArgs.builder().bucket(bucketName).object(objectName).build()).get();
            // 开始下载位置
            long startByte = 0;
            long fileSize = objectResponse.size();
            // 结束下载位置
            long endByte = fileSize - 1;
            log.info("文件总长度：{}，当前 range：{}", fileSize, range);

            // 存在 range，需要根据前端下载长度进行下载，即分段下载
            // 例如：range=bytes=0-52428800
            if (range != null && range.contains("bytes=") && range.contains("-")) {
                // 0-52428800
                range = range.substring(range.lastIndexOf("=") + 1).trim();
                String[] ranges = range.split("-");
                // 判断range的类型
                if (ranges.length == 1) {
                    // 类型一：bytes=-2343 后端转换为 0-2343
                    if (range.startsWith("-")) endByte = Long.parseLong(ranges[0]);
                    // 类型二：bytes=2343- 后端转换为 2343-最后
                    if (range.endsWith("-")) startByte = Long.parseLong(ranges[0]);
                } else if (ranges.length == 2) {
                    // 类型三：bytes=22-2343
                    startByte = Long.parseLong(ranges[0]);
                    endByte = Long.parseLong(ranges[1]);
                }
            }
            // 要下载的长度
            // 确保返回的 contentLength 不会超过文件的实际剩余大小
            long contentLength = Math.min(endByte - startByte + 1, fileSize - startByte);
            // 文件类型
            String contentType = request.getServletContext().getMimeType(fileName);

            // 解决下载文件时文件名乱码问题
            byte[] fileNameBytes = fileName.getBytes(StandardCharsets.UTF_8);
            fileName = new String(fileNameBytes, 0, fileNameBytes.length, StandardCharsets.ISO_8859_1);
            // 响应头设置---------------------------------------------------------------------------------------------
            // 断点续传，获取部分字节内容：
            response.setHeader("Accept-Ranges", "bytes");
            // http状态码要为206：表示获取部分内容,SC_PARTIAL_CONTENT,若部分浏览器不支持，改成 SC_OK
            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
            response.setContentType(contentType);
            response.setHeader("Last-Modified", objectResponse.lastModified().toString());
            response.setHeader("Content-Disposition", "attachment;filename=" + fileName);
            response.setHeader("Content-Length", String.valueOf(contentLength));
            // Content-Range，格式为：[要下载的开始位置]-[结束位置]/[文件总大小]
            response.setHeader("Content-Range", "bytes " + startByte + "-" + endByte + "/" + objectResponse.size());
            response.setHeader("ETag", "\"".concat(objectResponse.etag()).concat("\""));
            response.setContentType("application/octet-stream;charset=UTF-8");


            BufferedOutputStream os = null;
            GetObjectResponse stream = null;
            try {
                // 获取文件流
                stream = minioClient.getObject(GetObjectArgs.builder()
                        .bucket(bucketName)
                        .object(objectName)
                        .offset(startByte)
                        .length(contentLength)
                        .build()).get();
                os = new BufferedOutputStream(response.getOutputStream());
                // 将读取的文件写入到 OutputStream
                byte[] bytes = new byte[BUFFER_SIZE];
                long bytesWritten = 0;
                int bytesRead = -1;
                while ((bytesRead = stream.read(bytes)) != -1) {
                    if (bytesWritten + bytesRead >= contentLength) {
                        os.write(bytes, 0, (int) (contentLength - bytesWritten));
                        break;
                    } else {
                        os.write(bytes, 0, bytesRead);
                        bytesWritten += bytesRead;
                    }
                }
                os.flush();
                response.flushBuffer();
                // 返回对应http状态
                return new ResponseEntity<>(bytes, HttpStatus.OK);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
            } finally {
                if (os != null) {
                    os.close();
                }
                if (stream != null) {
                    stream.close();
                }
            }
        } catch (Exception e) {
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
        return null;
    }

    @Override
    public void setBucketPolicy(String bucket, BucketPolicyEnum policy) {
        CustomMinioClient minioClient = obtainClient();
        try {
            switch (policy) {
                case READ_ONLY:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(READ_ONLY.replace(BUCKET_PARAM, bucket)).build());
                    break;
                case WRITE_ONLY:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(WRITE_ONLY.replace(BUCKET_PARAM, bucket)).build());
                    break;
                case READ_WRITE:
                    minioClient.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config(READ_WRITE.replace(BUCKET_PARAM, bucket)).build());
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            throw new OssException(OssErrorCode.SET_BUCKET_POLICY_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void showdown() {
    }
}
