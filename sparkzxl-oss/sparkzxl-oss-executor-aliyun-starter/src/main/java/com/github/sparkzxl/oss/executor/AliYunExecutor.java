package com.github.sparkzxl.oss.executor;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
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
import org.springframework.util.StreamUtils;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
            log.error("AliyunOSS unexpected error during create bucket for {}: {}",
                    bucketName, e.getErrorMessage(), e);
            throw new OssException(OssErrorCode.CREATE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public void removeBucket(String bucketName) {
        try {
            OSSClient ossClient = obtainClient();
            ossClient.deleteBucket(bucketName);
        } catch (OSSException e) {
            log.error("AliyunOSS unexpected error during remove bucket for {}: {}",
                    bucketName, e.getErrorMessage(), e);
            throw new OssException(OssErrorCode.DELETE_BUCKET_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public String getObjectUrl(String bucketName, String objectName, Integer expire) {
        String objectUrl;
        try {
            OSSClient ossClient = obtainClient();
            DateTime expireDateTime = DateUtils.offsetSecond(new Date(), expire);
            GeneratePresignedUrlRequest req = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.GET);
            req.setExpiration(expireDateTime);
            URL signedUrl = ossClient.generatePresignedUrl(req);
            objectUrl = signedUrl.toString();
        } catch (Exception e) {
            log.error("AliyunOSS unexpected error during get object url for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getMessage());
        }
        Configuration configInfo = obtainConfigInfo();
        return OssUtils.replaceHttpDomain(objectUrl, configInfo.getDomain());
    }

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
            log.error("AliyunOSS unexpected error during get object info for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

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
            log.error("AliyunOSS unexpected error during get object metadata for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.GET_OBJECT_INFO_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public boolean exists(String bucketName, String objectName) {
        try {
            OSSClient ossClient = obtainClient();
            return ossClient.doesObjectExist(bucketName, objectName);
        } catch (OSSException e) {
            log.error("AliyunOSS unexpected error during checking whether objectName exists for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.OSS_ERROR);
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        uploadFileLimit(objectName);
        try {
            OSSClient ossClient = obtainClient();
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
            log.error("AliyunOSS unexpected error during multipartFile upload for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.PUT_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (Exception e) {
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        uploadFileLimit(objectName);
        File tempFile = new File(filePath);
        BufferedInputStream tempInputStream = null;
        try {
            OSSClient ossClient = obtainClient();
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
            log.error("AliyunOSS unexpected error during local file upload for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
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
        try {
            OSSClient ossClient = obtainClient();
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
            log.error("AliyunOSS unexpected error during remote file upload for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
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
        try {
            OSSClient ossClient = obtainClient();
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
                UploadPartRequest uploadPartRequest = new UploadPartRequest();
                uploadPartRequest.setBucketName(bucketName);
                uploadPartRequest.setKey(objectName);
                uploadPartRequest.setUploadId(uploadId);
                uploadPartRequest.setInputStream(partInputStream);
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
        } catch (OSSException e) {
            log.error("AliyunOSS OSSException error during multipart file chunk upload for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (ClientException e) {
            log.error("AliyunOSS ClientException error during multipart file chunk upload for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (IOException e) {
            log.error("AliyunOSS IOException error during multipart file chunk upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.MULTIPART_UPLOAD_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
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
            log.info("文件初始化分片成功,{}/{}: uploadId={}", bucketName, objectName, uploadId);
            uploadUrlsInfo.setUrls(partList);
            return uploadUrlsInfo;
        } catch (Exception e) {
            log.error("AliyunOSS unexpected error during init multipart upload for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        try {
            OSSClient ossClient = obtainClient();
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
            log.error("AliyunOSS unexpected error during get parts for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            OSSClient ossClient = obtainClient();
            log.info("start Merge MultipartUpload start. {}/{}，uploadId:{}", bucketName, objectName, uploadId);
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
            log.info("Merge MultipartUpload was successful. uploadId:{}，etag:{}", uploadId, completeMultipartUploadResult.getETag());
            return true;
        } catch (Exception e) {
            log.error("AliyunOSS Unexpected error during merge multipart upload completion for {}/{}/{}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public boolean abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        try {
            OSSClient ossClient = obtainClient();
            ossClient.abortMultipartUpload(new AbortMultipartUploadRequest(bucketName, objectName, uploadId));
            log.info("Successfully aborted multipart upload for {}/{}，uploadId:{}", bucketName, objectName, uploadId);
            return true;
        } catch (Exception e) {
            log.error("AliyunOSS Unexpected error during merge multipart upload completion for {}/{}/{}: {}",
                    bucketName, objectName, uploadId, e.getMessage(), e);
            throw new OssException(OssErrorCode.OSS_ERROR.getErrorCode(), e.getMessage());
        }
    }

    @Override
    public void removeObject(String bucketName, String objectName) {
        OSSClient ossClient = obtainClient();
        try {
            ossClient.deleteObject(bucketName, objectName);
        } catch (OSSException e) {
            log.error("AliyunOSS OSSException error during remove object for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        } catch (ClientException e) {
            log.error("AliyunOSS ClientException error during remove object for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.DELETE_OBJECT_ERROR.getErrorCode(), e.getErrorMessage());
        }
    }

    @Override
    public UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType) {
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
            Map<String, String> reqParams = new HashMap<>();
            reqParams.put("uploadId", uploadId);
            OSSClient ossClient = obtainClient();
            GeneratePresignedUrlRequest generatePresignedUrlRequest = new GeneratePresignedUrlRequest(bucketName, objectName, HttpMethod.PUT);
            generatePresignedUrlRequest.setQueryParameter(reqParams);
            generatePresignedUrlRequest.setHeaders(headers);
            // 设置1天有效期
            DateTime dateTime = DateUtils.offsetDay(new Date(), 1);
            generatePresignedUrlRequest.setExpiration(dateTime);
            String url = ossClient.generatePresignedUrl(generatePresignedUrlRequest).toString();
            urlList.add(url);
            uploadUrlsInfo.setUploadId(uploadId).setUrls(urlList);
            return uploadUrlsInfo;
        } catch (OSSException e) {
            log.error("AliyunOSS OSSException error during get presigned object upload url for {}/{}: {}",
                    bucketName, objectName, e.getErrorMessage());
            throw new OssException(OssErrorCode.GET_PRESIGNED_OBJECT_URL_ERROR, e);
        }
    }

    @Override
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        OSSClient ossClient = obtainClient();
        OSSObject ossObject = ossClient.getObject(bucketName, objectName);
        try (InputStream in = ossObject.getObjectContent()) {
            consumer.accept(in);
        } catch (IOException e) {
            log.error("AliyunOSS Unexpected error during download file for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
        }
    }

    @Override
    public void downloadMultipartFile(String bucketName, String objectName, String fileName, HttpServletRequest request, HttpServletResponse response) {
        InputStream stream = null;
        BufferedOutputStream os = null;
        OSSObject ossObject = null;
        try {
            OSSClient ossClient = obtainClient();
            // 1. 获取文件元数据
            ObjectMetadata objectMetadata = ossClient.getObjectMetadata(bucketName, objectName);
            long fileSize = objectMetadata.getContentLength();

            // 文件大小为0的异常处理
            if (fileSize <= 0) {
                response.setStatus(HttpServletResponse.SC_NO_CONTENT);
                return;
            }

            long startByte = 0;
            long endByte = fileSize - 1;

            String range = request.getHeader("Range");
            log.info("下载请求 bucket={}, object={}, range={}", bucketName, objectName, range);

            // 2. 解析 Range 头 (处理 bytes=0-500, bytes=-500, bytes=500- 等情况)
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
                contentType = objectMetadata.getContentType();
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

            response.setHeader("Last-Modified", objectMetadata.getLastModified().toString());
            response.setHeader("Content-Length", String.valueOf(contentLength));
            response.setHeader("Content-Disposition", "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName);
            // ETag 是 HTTP 标准缓存头，OSS通常会提供
            if (objectMetadata.getETag() != null) {
                response.setHeader("ETag", "\"" + objectMetadata.getETag() + "\"");
            }

            // 5. 获取 OSS 数据流
            GetObjectRequest getObjectRequest = new GetObjectRequest(bucketName, objectName);
            // 关键修复：阿里云 setRange 参数含义是 (start, end)，是闭区间，不是 (start, length)！
            getObjectRequest.setRange(startByte, endByte);

            ossObject = ossClient.getObject(getObjectRequest);
            stream = ossObject.getObjectContent();

            // 6. 写出数据
            os = new BufferedOutputStream(response.getOutputStream());

            // 使用 Spring 工具类直接拷贝流，无需手动循环
            StreamUtils.copy(stream, os);

            os.flush();
            response.flushBuffer();

        } catch (Exception e) {
            log.error("AliyunOSS Unexpected error during download multipart file for {}/{}: {}",
                    bucketName, objectName, e.getMessage());
            // 如果还没有写入响应，可以抛出异常给全局异常处理器
            if (!response.isCommitted()) {
                throw new OssException(OssErrorCode.DOWNLOAD_OBJECT_ERROR, e);
            }
        } finally {
            // 7. 资源关闭
            if (stream != null) {
                try {
                    stream.close();
                } catch (IOException e) { /* ignore */ }
            }
            if (ossObject != null) {
                try {
                    ossObject.close();
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
    public void setBucketPolicy(String bucket, BucketPolicyEnum policy) {

    }

    @Override
    public void shutdown() {
        // 委托给 OssClient.close() 统一管理资源释放
        OssClient<OSSClient> ossClient = client;
        if (ossClient != null) {
            ossClient.close();
        }
    }
}
