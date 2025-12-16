package com.github.sparkzxl.oss.executor;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.http.HttpUtil;
import com.aliyun.oss.ClientException;
import com.aliyun.oss.HttpMethod;
import com.aliyun.oss.OSSClient;
import com.aliyun.oss.OSSException;
import com.aliyun.oss.internal.Mimetypes;
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
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * description: aliYun 执行器
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:54:27
 */
@Slf4j
public class AliYunExecutor extends AbstractOssExecutor<OSSClient> {


    public AliYunExecutor(OssClient<OSSClient> client) {
        super(client);
    }

    @Override
    public void createBucket(String bucketName) {
        OSSClient ossClient = obtainClient();
        try {
            if (!ossClient.doesBucketExist(bucketName)) {
                ossClient.createBucket(bucketName);
            } else {
                log.info("bucket [{}] already exists.", bucketName);
            }
        } catch (OSSException e) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        OSSClient ossClient = obtainClient();
        try {
            ossClient.deleteBucket(bucketName);
        } catch (OSSException e) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        OSSClient ossClient = obtainClient();
        String objectUrl;
        try {
            DateTime expireDateTime = DateUtils.offsetSecond(new Date(), expire);
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET);
            req.setExpiration(expireDateTime);
            URL signedUrl = ossClient.generatePresignedUrl(req);
            objectUrl = signedUrl.toString();
        } catch (Exception e) {
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

    @Override
    public OssObject getObjectInfo(String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        try {
            OSSObject object = ossClient.getObject(bucketName, objectName);
            OssObject ossObject = new OssObject();
            ossObject.setObjectContent(object.getObjectContent());
            ossObject.setBucketName(object.getBucketName());
            ossObject.setKey(object.getKey());
            return ossObject;
        } catch (OSSException e) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        try {
            return ossClient.doesObjectExist(bucketName, objectName);
        } catch (OSSException e) {
            log.warn("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.OSS_ERROR);
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
        OSSClient ossClient = obtainClient();
        try {
            long size = multipartFile.getSize();
            String contentType = multipartFile.getContentType();
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(size);
            objectMetadata.setContentType(contentType);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, multipartFile.getInputStream(), objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(contentType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResult.getETag());
            return pushObjectResponse;
        } catch (OSSException e) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        uploadFileLimit(objectName);
        OSSClient ossClient = obtainClient();
        File tempFile = new File(filePath);
        BufferedInputStream tempInputStream = null;
        try {
            tempInputStream = FileUtil.getInputStream(tempFile);
            String mimeType = FileUtil.getType(tempFile);
            String finalMimeType = mimeType == null ? "application/octet-stream" : mimeType;
            long size = FileUtil.size(tempFile);
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(size);
            objectMetadata.setContentType(finalMimeType);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, tempInputStream, objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(finalMimeType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResult.getETag());
            return pushObjectResponse;
        } catch (OSSException e) {
            log.warn("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
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
        OSSClient ossClient = obtainClient();
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
            ObjectMetadata objectMetadata = new ObjectMetadata();
            objectMetadata.setContentLength(tempFile.length());
            objectMetadata.setContentType(finalMimeType);
            PutObjectRequest putObjectRequest = new PutObjectRequest(bucketName, objectName, tempInputStream, objectMetadata);
            PutObjectResult putObjectResult = ossClient.putObject(putObjectRequest);
            long totalTime = stopwatch.elapsed(TimeUnit.SECONDS);
            log.info("文件下载并上传完成，总耗时：[{}]", totalTime);
            OssPushObjectResponse pushObjectResponse = new OssPushObjectResponse();
            pushObjectResponse.setBucketName(bucketName);
            pushObjectResponse.setObjectName(objectName);
            pushObjectResponse.setSize(size);
            pushObjectResponse.setContentType(finalMimeType);
            pushObjectResponse.setUploadTime(LocalDateTime.now());
            String uploadFileUrl = getObjectUrl(bucketName, objectName);
            pushObjectResponse.setUrl(uploadFileUrl);
            log.info("文件上传成功，ETag: {}", putObjectResult.getETag());
            return pushObjectResponse;
        } catch (OSSException e) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{} Error Message:{} Request ID:{} Host ID:{}",
                    e.getErrorCode(),
                    e.getErrorMessage(),
                    e.getRequestId(),
                    e.getHostId());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
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
        OSSClient ossClient = obtainClient();
        try {
            long fileLength = multipartFile.getSize();
            InputStream inputStream = multipartFile.getInputStream();
            // 创建InitiateMultipartUploadRequest对象。
            InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);

            // 如果需要在初始化分片时设置请求头，请参考以下示例代码。
            ObjectMetadata metadata = new ObjectMetadata();
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard.toString());
            // 指定该Object的网页缓存行为。
            // metadata.setCacheControl("no-cache");
            // 指定该Object被下载时的名称。
            // metadata.setContentDisposition("attachment;filename=oss_MultipartUpload.txt");
            // 指定该Object的内容编码格式。
            // metadata.setContentEncoding(OSSConstants.DEFAULT_CHARSET_NAME);
            // 指定过期时间，单位为毫秒。
            // metadata.setHeader(HttpHeaders.EXPIRES, "1000");
            // 指定初始化分片上传时是否覆盖同名Object。此处设置为true，表示禁止覆盖同名Object。
            // metadata.setHeader("x-oss-forbid-overwrite", "true");
            // 指定上传该Object的每个part时使用的服务器端加密方式。
            // metadata.setHeader(OSSHeaders.OSS_SERVER_SIDE_ENCRYPTION, ObjectMetadata.KMS_SERVER_SIDE_ENCRYPTION);
            // 指定Object的加密算法。如果未指定此选项，表明Object使用AES256加密算法。
            // metadata.setHeader(OSSHeaders.OSS_SERVER_SIDE_DATA_ENCRYPTION, ObjectMetadata.KMS_SERVER_SIDE_ENCRYPTION);
            // 指定KMS托管的用户主密钥。
            // metadata.setHeader(OSSHeaders.OSS_SERVER_SIDE_ENCRYPTION_KEY_ID, "9468da86-3509-4f8d-a61e-6eab1eac****");
            // 指定Object的存储类型。
            // metadata.setHeader(OSSHeaders.OSS_STORAGE_CLASS, StorageClass.Standard);
            // 指定Object的对象标签，可同时设置多个标签。
            // metadata.setHeader(OSSHeaders.OSS_TAGGING, "a:1");
            // request.setObjectMetadata(metadata);
            if (metadata.getContentType() == null) {
                metadata.setContentType(Mimetypes.getInstance().getMimetype(multipartFile.getOriginalFilename(), objectName));
            }
            InitiateMultipartUploadResult upresult = ossClient.initiateMultipartUpload(request);
            // 返回uploadId，它是分片上传事件的唯一标识。您可以根据该uploadId发起相关的操作，例如取消分片上传、查询分片上传等。
            String uploadId = upresult.getUploadId();

            // partETags是PartETag的集合。PartETag由分片的ETag和分片号组成。
            List<PartETag> partTagList = new ArrayList<>();
            // 每个分片的大小1 MB，用于计算文件有多少个分片。单位为字节。
            final long partSize = 1024 * 1024L;
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
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(objectName);
                uploadPartRequest.setUploadId(uploadId);
                uploadPartRequest.setInputStream(inputStream);
                // 设置分片大小。除了最后一个分片没有大小限制，其他的分片最小为100 KB。
                uploadPartRequest.setPartSize(curPartSize);
                // 设置分片号。每一个上传的分片都有一个分片号，取值范围是1~10000，如果超出此范围，OSS将返回InvalidArgument错误码。
                uploadPartRequest.setPartNumber(i + 1);
                // 每个分片不需要按顺序上传，甚至可以在不同客户端上传，OSS会按照分片号排序组成完整的文件。
                UploadPartResult uploadPartResult = ossClient.uploadPart(uploadPartRequest);
                // 每次上传分片之后，OSS的返回结果包含PartETag。PartETag将被保存在partETags中。
                partTagList.add(uploadPartResult.getPartETag());
            }


            // 创建CompleteMultipartUploadRequest对象。
            // 在执行完成分片上传操作时，需要提供所有有效的partETags。OSS收到提交的partETags后，会逐一验证每个分片的有效性。当所有的数据分片验证通过后，OSS将把这些分片组合成一个完整的文件。
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, partTagList);

            // 如果需要在完成分片上传的同时设置文件访问权限，请参考以下示例代码。
            // completeMultipartUploadRequest.setObjectACL(CannedAccessControlList.Private);
            // 指定是否列举当前UploadId已上传的所有Part。如果通过服务端List分片数据来合并完整文件时，以上CompleteMultipartUploadRequest中的partETags可为null。
            // Map<String, String> headers = new HashMap<String, String>();
            // 如果指定了x-oss-complete-all:yes，则OSS会列举当前UploadId已上传的所有Part，然后按照PartNumber的序号排序并执行CompleteMultipartUpload操作。
            // 如果指定了x-oss-complete-all:yes，则不允许继续指定body，否则报错。
            // headers.put("x-oss-complete-all","yes");
            // completeMultipartUploadRequest.setHeaders(headers);
            CompleteMultipartUploadResult completeMultipartUploadResult = ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            log.info("分片上传结果：{}", completeMultipartUploadResult.getETag());
        } catch (OSSException oe) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{}\n"
                            + "Error Message:{}\n"
                            + "Request ID:{}\n"
                            + "Host ID:{}",
                    oe.getErrorCode(),
                    oe.getErrorMessage(),
                    oe.getRequestId(),
                    oe.getHostId());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), oe.getErrorMessage());
        } catch (ClientException ce) {
            log.warn("Caught an ClientException, which means the client encountered "
                            + "a serious internal problem while trying to communicate with OSS," +
                            "such as not being able to access the network.\n "
                            + "Error Message:{}",
                    ce.getMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), ce.getErrorMessage());
        } catch (IOException e) {
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        Integer chunkCount = fileUploadInfo.getChunkCount();
        String contentType = fileUploadInfo.getContentType();
        String uploadId = fileUploadInfo.getUploadId();
        log.info("文件<{}> - 分片<{}> 初始化分片上传数据 请求头 {}", objectName, chunkCount, contentType);
        UploadUrlsInfo uploadUrlsInfo = new UploadUrlsInfo();
        try {
            Map<String, String> headers = Maps.newHashMap();
            if (StringUtils.isEmpty(contentType)) {
                contentType = "application/octet-stream";
            }
            headers.put("Content-Type", contentType);
            // 如果初始化时有 uploadId，说明是断点续传，不能重新生成 uploadId
            if (StringUtils.isEmpty(fileUploadInfo.getUploadId())) {
                InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest(bucketName, objectName);
                ObjectMetadata objectMetadata = new ObjectMetadata();
                objectMetadata.setContentType(contentType);
                request.setObjectMetadata(objectMetadata);
                request.setHeaders(headers);
                InitiateMultipartUploadResult uploadResult = ossClient.initiateMultipartUpload(request);
                uploadId = uploadResult.getUploadId();
            }
            uploadUrlsInfo.setUploadId(uploadId);
            List<String> partList = new ArrayList<>();
            for (int i = 1; i <= chunkCount; i++) {
                Date expiration = new Date(new Date().getTime() + 3600 * 1000L);
                GeneratePresignedUrlRequest request = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
                // 设置过期时间。
                request.setExpiration(expiration);
                request.addQueryParameter("uploadId", uploadId);
                request.addQueryParameter("partNumber", String.valueOf(i));
                URL url = ossClient.generatePresignedUrl(request);
                String uploadUrl = url.toString();
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
        OSSClient ossClient = obtainClient();
        try {
            // 合并分片，与上传分片不在同一个系统。此时，您需要先列举分片，然后再合并分片。
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            PartListing partListing = ossClient.listParts(listPartsRequest);
            return partListing.getParts().stream()
                    .map(x -> {
                        PartData partData = new PartData();
                        partData.setPartNumber(x.getPartNumber());
                        partData.setEtag(x.getETag());
                        partData.setSize(x.getSize());
                        return partData;
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        OSSClient ossClient = obtainClient();
        try {
            // 合并分片，与上传分片不在同一个系统。此时，您需要先列举分片，然后再合并分片。
            ListPartsRequest listPartsRequest = new ListPartsRequest(bucketName, objectName, uploadId);
            PartListing partListing = ossClient.listParts(listPartsRequest);

            List<PartETag> parteTags = Lists.newArrayList();
            // 遍历分片，并填充partETags。
            for (PartSummary part : partListing.getParts()) {
                parteTags.add(new PartETag(part.getPartNumber(), part.getETag()));
            }
            CompleteMultipartUploadRequest completeMultipartUploadRequest =
                    new CompleteMultipartUploadRequest(bucketName, objectName, uploadId, parteTags);
            CompleteMultipartUploadResult completeMultipartUploadResult = ossClient.completeMultipartUpload(completeMultipartUploadRequest);
            log.info("合并分片成功，上传分片完成.uploadId：{}{}", uploadId, completeMultipartUploadResult.getETag());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
        return true;
    }

    @Override
    public void removeObject(String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException oe) {
            log.error("Caught an OSSException, which means your request made it to OSS, "
                            + "but was rejected with an error response for some reason.\n"
                            + "Error Code:{}\n"
                            + "Error Message:{}\n"
                            + "Request ID:{}\n"
                            + "Host ID:{}",
                    oe.getErrorCode(),
                    oe.getErrorMessage(),
                    oe.getRequestId(),
                    oe.getHostId());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), oe.getErrorMessage());
        } catch (ClientException ce) {
            log.error("Caught an ClientException, which means the client encountered "
                            + "a serious internal problem while trying to communicate with OSS," +
                            "such as not being able to access the network.\n "
                            + "Error Message:{}",
                    ce.getMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), ce.getErrorMessage());
        }
    }

    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        OSSClient ossClient = obtainClient();
        OSSObject ossObject = ossClient.getObject(bucketName, objectName);
        try (InputStream in = ossObject.getObjectContent()) {
            consumer.accept(in);
        } catch (IOException e) {
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
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
        OSSClient ossClient = obtainClient();
        GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
        generatePresignedUrlRequest.setQueryParameter(reqParams);
        generatePresignedUrlRequest.setHeaders(headers);
        // 设置1天有效期
        DateTime dateTime = DateUtils.offsetDay(new Date(), 1);
        generatePresignedUrlRequest.setExpiration(dateTime);
        try {
            String url = ossClient.generatePresignedUrl(generatePresignedUrlRequest).toString();
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    @Override
    public void setBucketPolicy(String bucket, BucketPolicyEnum policy) {

    }

    @Override
    public void showdown() {
        OSSClient ossClient = obtainClient();
        ossClient.shutdown();
    }
}
