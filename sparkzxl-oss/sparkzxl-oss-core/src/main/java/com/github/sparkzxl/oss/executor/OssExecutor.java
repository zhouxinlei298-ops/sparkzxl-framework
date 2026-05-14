package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.core.support.ArgumentException;
import com.github.sparkzxl.oss.entity.*;
import com.github.sparkzxl.oss.enums.BucketPolicyEnum;
import com.github.sparkzxl.oss.properties.Configuration;
import org.apache.commons.lang3.StringUtils;
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
 * description: oss 执行器
 *
 * @author zhouxinlei
 * @since 2022-05-03 16:19:41
 */
public interface OssExecutor {

    /**
     * 创建bucket
     *
     * @param bucketName bucket名称
     */
    void createBucket(String bucketName);

    /**
     * 移除bucket
     *
     * @param bucketName bucket名称
     */
    void removeBucket(String bucketName);

    /**
     * 设置bucket策略
     *
     * @param bucket bucket名称
     * @param policy 桶策略
     */
    void setBucketPolicy(String bucket, BucketPolicyEnum policy);

    /**
     * 获取文件外链
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param expires    过期时间 <=7
     * @return url
     */
    String getObjectUrl(String bucketName, String objectName, Integer expires);


    /**
     * 获取文件访问路径
     *
     * @param bucketName bucket名称
     * @param objectName 文件名称
     * @return String
     */
    default String getObjectUrl(String bucketName, String objectName) {
        Configuration configInfo = obtainConfigInfo();
        if (configInfo == null) {
            throw new ArgumentException(
                    String.format("Cannot get object URL: OSS configuration not found for bucket [%s], object [%s]", bucketName, objectName));
        }
        String domainName = configInfo.getDomain();
        if (StringUtils.isEmpty(domainName)) {
            domainName = configInfo.getEndpoint();
        }
        if (StringUtils.isEmpty(domainName)) {
            throw new ArgumentException(
                    String.format("Cannot get object URL: Both domain and endpoint are empty in configuration for bucket [%s]", bucketName));
        }
        // 优化：使用字符串连接代替 StringJoiner，避免重复创建对象
        // 确保 domainName 末尾没有 /，bucketName 和 objectName 开头没有 /
        StringBuilder url = new StringBuilder(domainName);
        if (!domainName.endsWith("/")) {
            url.append('/');
        }
        url.append(bucketName).append('/').append(objectName);
        return url.toString();
    }

    /**
     * 获取文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return OssObject 二进制流
     */
    OssObject getObjectInfo(String bucketName, String objectName);

    /**
     * 获取文件内容和元信息
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return OssMetadata
     */
    OssMetadata getOssMetadata(String bucketName, String objectName);

    /**
     * 判断文件是否存在
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @return boolean
     */
    boolean exists(String bucketName, String objectName);

    /**
     * 上传文件
     *
     * @param bucketName    bucket名称
     * @param objectName    文件名称
     * @param multipartFile 文件
     * @return OssPushObjectResponse
     */
    OssPushObjectResponse putObject(String bucketName, String objectName, MultipartFile multipartFile);

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param filePath   文件路径
     * @return OssPushObjectResponse
     */
    OssPushObjectResponse putObject(String bucketName, String objectName, String filePath);

    /**
     * 上传本地文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param file   本地文件
     * @param delete   上传成功是否删除文件
     * @return OssPushObjectResponse
     */
    OssPushObjectResponse putObject(String bucketName, String objectName, File file, boolean delete);

    /**
     * 上传文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param url        文件地址
     * @return OssPushObjectResponse
     */
    OssPushObjectResponse putObject(String bucketName, String objectName, URL url);

    /**
     * 分段上传
     *
     * @param bucketName    bucket名称
     * @param objectName    oss对象名称
     * @param multipartFile 上传文件
     */
    void multipartUpload(String bucketName, String objectName, MultipartFile multipartFile);

    /**
     * 初始化文件分片上传
     *
     * @param fileUploadInfo 文件上传信息
     * @param bucketName     bucket名称
     * @param objectName     oss对象名称
     * @return UploadUrlsInfo
     */
    UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String bucketName, String objectName);

    /**
     * 获取OSS中已经上传的分片文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     * @return List<PartData>
     */
    List<PartData> getListParts(String bucketName, String objectName, String uploadId);

    /**
     * 合并文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     */
    boolean mergeMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 中止分片上传
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param uploadId   上传标识ID
     */
    boolean abortMultipartUpload(String bucketName, String objectName, String uploadId);

    /**
     * 删除文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     */
    void removeObject(String bucketName, String objectName);

    /**
     * 获取文件上传地址
     *
     * @param bucketName  bucket名称
     * @param objectName  oss对象名称
     * @param contentType contentType
     * @return UploadUrlsInfo
     */
    UploadUrlsInfo getPresignedObjectUploadUrl(String bucketName, String objectName, String contentType);

    /**
     * 下载文件
     *
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param consumer   消费
     */
    void downloadFile(String bucketName, String objectName, Consumer<InputStream> consumer);

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
    void downloadMultipartFile(String bucketName,
                               String objectName,
                               String fileName,
                               HttpServletRequest request,
                               HttpServletResponse response) throws IOException;

    /**
     * 销毁
     */
    void shutdown();

    default Configuration obtainConfigInfo() {
        return null;
    }

}
