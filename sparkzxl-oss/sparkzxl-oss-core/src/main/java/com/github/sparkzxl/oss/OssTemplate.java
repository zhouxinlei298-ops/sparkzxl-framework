package com.github.sparkzxl.oss;

import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.executor.OssExecutorFactoryContext;
import com.github.sparkzxl.oss.operation.OssOperationContextManager;
import com.github.sparkzxl.oss.operation.OssOperationContextScope;
import com.github.sparkzxl.oss.properties.OssProperties;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.util.Assert;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.List;
import java.util.function.Consumer;

/**
 * 文件操作模板类
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:17:36
 */
@Slf4j
@NoArgsConstructor
public class OssTemplate implements InitializingBean {

    @Setter
    private OssProperties ossProperties;

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
     * @param expires 过期时间 <=7
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
     * @see <a href="http://docs.aws.amazon.com/goto/WebAPI/s3-2006-03-01/GetObject">API
     *     Documentation</a>
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
     * @return 对象大小及供应商元信息
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
     * @return 本次操作是否成功
     */
    public boolean exists(String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.exists(bucketName, objectName);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param multipartFile 文件
     * @return 供应商上传响应
     */
    public OssPushObjectResponse putObject(
            String bucketName, String objectName, MultipartFile multipartFile) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, multipartFile);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param filePath 文件地址
     * @return 供应商上传响应
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
     * @param file 本地文件
     * @param delete 上传成功是否删除文件
     * @return 包含实际对象位置与元信息的上传响应
     */
    public OssPushObjectResponse putObject(
            String bucketName, String objectName, File file, boolean delete) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, file, delete);
    }

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param url 文件地址
     * @return 供应商上传响应
     */
    public OssPushObjectResponse putObject(String bucketName, String objectName, URL url) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.putObject(bucketName, objectName, url);
    }

    /**
     * 分片上传
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
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
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return 供应商上传标识及各分片的限时签名信息
     */
    public UploadUrlsInfo initMultiPartUpload(
            FileUploadInfo fileUploadInfo, String bucketName, String objectName) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.initMultiPartUpload(fileUploadInfo, bucketName, objectName);
    }

    /**
     * 获取OSS中已经上传的分片文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param uploadId 上传标识ID
     * @return 供应商已接收的真实分片编号集合
     */
    public List<PartData> getListParts(String bucketName, String objectName, String uploadId) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getListParts(bucketName, objectName, uploadId);
    }

    /**
     * 合并文件
     *
     * @param objectName oss对象名称
     * @param uploadId 上传标识ID
     * @param bucketName Bucket 名称
     * @return 此旧入口拒绝合并；必须改用带已验证分片清单的操作句柄
     */
    public boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.mergeMultipartUpload(bucketName, objectName, uploadId);
    }

    /**
     * 中止分片上传
     *
     * @param objectName oss对象名称
     * @param uploadId 上传标识ID
     * @param bucketName Bucket 名称
     * @return 取消成功返回 true
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
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param contentType 对象媒体类型
     * @return 供应商上传标识及各分片的限时签名信息
     */
    public UploadUrlsInfo getPresignedObjectUploadUrl(
            String bucketName, String objectName, String contentType) {
        OssExecutor ossExecutor = obtainExecutor();
        return ossExecutor.getPresignedObjectUploadUrl(bucketName, objectName, contentType);
    }

    /**
     * 在显式操作域内下载指定文件。
     *
     * @param bucketName Bucket 名称
     * @param objectName 对象键
     * @param consumer   同步读取回调，结束后关闭下载流
     */
    public void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer) {
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.downloadFile(bucketName, objectName, consumer);
    }

    /**
     * 分片下载文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param fileName 文件名
     * @param request HTTP请求
     * @param response HTTP响应
     * @throws IOException IO异常
     */
    public void downloadMultipartFile(
            String bucketName,
            String objectName,
            String fileName,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException {
        log.info("下载文件的 object <{}>", objectName);
        OssExecutor ossExecutor = obtainExecutor();
        ossExecutor.downloadMultipartFile(bucketName, objectName, fileName, request, response);
    }

    /**
     * 为显式客户端建立可关闭的操作域。
     *
     * @param clientId 固定客户端标识
     * @return 固定显式目标的可关闭操作域
     */
    public OssOperationContextScope openOperation(String clientId) {
        return OssOperationContextManager.open(ossExecutorFactoryContext, clientId);
    }

    /**
     * 为异步任务独立建立可关闭的操作域。
     *
     * @param clientId 固定客户端标识
     * @return 独立解析目标的可关闭操作域
     */
    public OssOperationContextScope openIndependentOperation(String clientId) {
        return OssOperationContextManager.openIndependent(ossExecutorFactoryContext, clientId);
    }

    /**
     * 取得当前显式操作域的固定执行器。
     *
     * @return 当前操作域固定的执行器
     */
    public OssExecutor obtainExecutor() {
        return OssOperationContextManager.requiredHandle().executor();
    }

    /** 校验启动配置并完成 OSS 组件初始化。 */
    @Override
    public void afterPropertiesSet() {
        Assert.notNull(ossProperties.getRegister(), "Register mode must be not null");
        Assert.notNull(ossExecutorFactoryContext, "OSS executor factory is required");
    }
}
