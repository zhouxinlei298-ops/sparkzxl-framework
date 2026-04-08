package com.github.sparkzxl.oss;

import com.github.sparkzxl.oss.context.OssClientContextHolder;
import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;
import com.github.sparkzxl.oss.properties.OssProperties;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * description:  文件操作模板类
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:17:36
 */
@Slf4j
@NoArgsConstructor
public class OssTemplate implements InitializingBean {

    @Setter
    private OssProperties ossProperties;
    private OssExecutor primaryExecutor;
    @Setter
    private OssExecutorFactoryContext ossExecutorFactoryContext;

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    public void createBucket(String bucketName) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.createBucket(bucketName);
    }

    /**
     * 移除bucket
     *
     * @param bucketName bucket名称
     */
    public void removeBucket(String bucketName) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.removeBucket(bucketName);
    }

    /**
     * 获取文件外链
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param expires    过期时间 <=7
     * @return url
     */
    public String getObjectUrl(String bucketName, String objectName, Integer expires) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getObjectUrl(bucketName, objectName, expires);
    }

    /**
     * 获取文件外链
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return url
     */
    public String getObjectUrl(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getObjectUrl(bucketName, objectName);
    }

    /**
     * 获取文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return 二进制流
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">API Documentation</a>
     */
    public OssObject getObjectInfo(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getObjectInfo(bucketName, objectName);
    }

    /**
     * 获取文件内容和元信息
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return OssMetadata
     */
    public OssMetadata getOssMetadata(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getOssMetadata(bucketName, objectName);
    }

    /**
     * 文件是否存在
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return boolean
     */
    public boolean exists(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.exists(bucketName, objectName);
    }

    /**
     * 上传文件
     *
     * @param bucketName    bucket名称
     * @param objectName    文件名称
     * @param multipartFile 文件
     */
    public OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, multipartFile);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param filePath   文件地址
     */
    public OssPushObjectResponse putObject(String bucketName, String objectName, String filePath) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, filePath);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param url        文件地址
     */
    public OssPushObjectResponse putObject(String bucketName, String objectName, URL url) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, url);
    }

    /**
     * 分片上传
     *
     * @param bucketName    bucket名称
     * @param objectName    文件名称
     * @param multipartFile 上传文件
     */
    public void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.multipartUpload(bucketName, objectName, multipartFile);
    }

    /**
     * 初始化文件分片上传
     *
     * @param fileUploadInfo 文件上传信息
     * @param bucketName     bucket名称
     * @param objectName     文件名称
     * @return UploadUrlsInfo
     */
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.initMultiPartUpload(fileUploadInfo, bucketName, objectName);
    }

    /**
     * 获取OSS中已经上传的分片文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     * @return List<Integer>
     */
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getListParts(bucketName, objectName, uploadId);
    }

    /**
     * 合并文件
     *
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     */
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.mergeMultipartUpload(bucketName, objectName, uploadId);
    }

    /**
     * 中止分片上传
     *
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     */
    public boolean abortMultipartUpload(String bucketName, String objectName, String uploadId) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.abortMultipartUpload(bucketName, objectName, uploadId);
    }


    /**
     * 删除文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     */
    public void removeObject(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.removeObject(bucketName, objectName);
    }

    /**
     * 获取文件上传地址
     *
     * @param bucketName  bucket名称
     * @param objectName  文件名称
     * @param contentType contentType
     * @return UploadUrlsInfo
     */
    public UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getPresignedObjectUploadUrl(bucketName, objectName, contentType);
    }

    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.downloadFile(bucketName, objectName, consumer);
    }

    /**
     * 分片下载文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param fileName   文件名
     * @param request    HTTP请求
     * @param response   HTTP响应
     * @throws IOException IO异常
     */
    public void downloadMultipartFile(String bucketName, String objectName, String fileName, HttpServletRequest request, HttpServletResponse response) throws IOException {
        log.info("下载文件的 object <{}>", objectName);
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.downloadMultipartFile(bucketName, objectName, fileName, request, response);
    }

    public OssExecutor obtainExecutor() {
        String clientId = OssClientContextHolder.peek();
        if (clientId == null) {
            Assert.notNull(primaryExecutor, "Primary OssExecutor not initialized. Please check the OSS configuration.");
            return primaryExecutor;
        }
        final OssExecutor ossExecutor = ossExecutorFactoryContext.create(clientId);
        Assert.notNull(ossExecutor, String.format("Cannot get OssExecutor for clientId [%s]", clientId));
        return ossExecutor;
    }

    @Override
    public void afterPropertiesSet() {
        Assert.notNull(ossProperties.getRegister(), "Register mode must be not null");
        this.primaryExecutor = ossExecutorFactoryContext.create();
    }

}
