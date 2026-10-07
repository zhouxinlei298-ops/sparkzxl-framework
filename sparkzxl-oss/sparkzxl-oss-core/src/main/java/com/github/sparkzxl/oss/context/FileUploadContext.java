package com.github.sparkzxl.oss.context;

import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.oss.OssTemplate;
import com.github.sparkzxl.oss.entity.FileUploadInfo;
import com.github.sparkzxl.oss.entity.OssPushObjectResponse;
import com.github.sparkzxl.oss.entity.PartData;
import com.github.sparkzxl.oss.entity.UploadUrlsInfo;
import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.generator.ObjectNameGenerator;
import com.github.sparkzxl.oss.properties.Configuration;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.Serializable;
import java.net.URL;
import java.util.List;

/**
 * 文件上传下载
 *
 * @author zhouxinlei
 * @since 2024-11-15 15:34:39
 */
@Slf4j
public class FileUploadContext {

    @Value("${spring.profiles.active}")
    private String environment;

    @Resource
    private OssTemplate ossTemplate;

    /**
     * 获取当前执行器
     *
     * @return 当前显式操作域持有的固定执行器
     */
    public OssExecutor obtainExecutor() {
        return ossTemplate.obtainExecutor();
    }

    /**
     * 获取当前桶名称
     *
     * @return 桶名称
     */
    private String obtainBucketName() {
        Configuration configuration = obtainExecutor().obtainConfigInfo();
        ArgumentAssert.notNull(configuration, "未加载文件存储配置信息，请联系管理员");
        return configuration.getBucketName();
    }

    /**
     * 加载OSS对象名称生成器，为空时返回默认生成器
     *
     * @param objectNameGenerator OSS对象名称生成器
     * @return 本次操作使用的对象键生成器
     */
    private ObjectNameGenerator loadObjectNameGenerator(ObjectNameGenerator objectNameGenerator) {
        if (ObjectUtils.isNotEmpty(objectNameGenerator)) {
            return objectNameGenerator;
        } else {
            return ObjectNameGenerator.DEFAULT_OBJECT_NAME_GENERATOR;
        }
    }

    /**
     * 文件上传
     *
     * @param multipartFile 文件对象
     * @return 包含实际对象位置与元信息的上传响应
     */
    public OssPushObjectResponse uploadFile(MultipartFile multipartFile) {
        return uploadFile(multipartFile, null);
    }

    /**
     * 文件上传
     *
     * @param multipartFile 文件对象
     * @param objectNameGenerator OSS对象名称生成器
     * @return 包含实际对象位置与元信息的上传响应
     */
    public OssPushObjectResponse uploadFile(
            MultipartFile multipartFile, ObjectNameGenerator objectNameGenerator) {
        OssExecutor executor = obtainExecutor();
        ArgumentAssert.notNull(multipartFile, "上传的文件为空");
        String originalFileName = multipartFile.getOriginalFilename();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        String tenantId = RequestLocalContextHolder.getTenantId();
        log.info(
                "OSS Configuration tenantId: {},clientId: {},bucketName: {}",
                tenantId,
                executor.obtainConfigInfo().getClientId(),
                bucketName);
        String objectName =
                executor.obtainConfigInfo().getObjectPrefix()
                        + loadObjectNameGenerator(objectNameGenerator)
                        .generator(environment, tenantId, originalFileName);
        OssPushObjectResponse ossPushObjectResponse =
                executor.putObject(bucketName, objectName, multipartFile);
        ArgumentAssert.notNull(ossPushObjectResponse, "文件上传结果为空");
        return ossPushObjectResponse;
    }

    /**
     * URL文件上传
     *
     * @param originalFileName 文件名
     * @param url 文件地址
     * @return 上传信息及固定目标对象地址
     */
    public OssPushObjectResponse uploadUrlFile(String originalFileName, URL url) {
        return uploadUrlFile(originalFileName, url, null);
    }

    /**
     * URL文件上传
     *
     * @param originalFileName 原始文件名
     * @param url 文件地址
     * @param objectNameGenerator OSS对象名称生成器
     * @return 上传信息及固定目标对象地址
     */
    public OssPushObjectResponse uploadUrlFile(
            String originalFileName, URL url, ObjectNameGenerator objectNameGenerator) {
        OssExecutor executor = obtainExecutor();
        ArgumentAssert.notEmpty(originalFileName, "文件名不能为空");
        ArgumentAssert.notNull(url, "远程文件地址为空，无法上传");
        String bucketName = executor.obtainConfigInfo().getBucketName();
        String objectName =
                executor.obtainConfigInfo().getObjectPrefix()
                        + loadObjectNameGenerator(objectNameGenerator)
                        .generator(
                                environment,
                                RequestLocalContextHolder.getTenantId(),
                                originalFileName);
        OssPushObjectResponse ossPushObjectResponse =
                executor.putObject(bucketName, objectName, url);
        ArgumentAssert.notNull(ossPushObjectResponse, "文件上传结果为空");
        return ossPushObjectResponse;
    }

    /**
     * 上传本地文件
     *
     * @param originalFileName 原始文件名
     * @param file 本地文件
     * @return 包含实际对象位置与元信息的上传响应
     */
    public OssPushObjectResponse uploadLocalFile(String originalFileName, File file) {
        return uploadLocalFile(originalFileName, file, null);
    }

    /**
     * 上传本地文件
     *
     * @param originalFileName 原始文件名
     * @param file 本地文件
     * @param objectNameGenerator OSS对象名称生成器
     * @return 包含实际对象位置与元信息的上传响应
     */
    public OssPushObjectResponse uploadLocalFile(
            String originalFileName, File file, ObjectNameGenerator objectNameGenerator) {
        OssExecutor executor = obtainExecutor();
        ArgumentAssert.notEmpty(originalFileName, "文件名不能为空");
        ArgumentAssert.notNull(file, "文件为空，无法上传");
        String bucketName = executor.obtainConfigInfo().getBucketName();
        String objectName =
                executor.obtainConfigInfo().getObjectPrefix()
                        + loadObjectNameGenerator(objectNameGenerator)
                        .generator(
                                environment,
                                RequestLocalContextHolder.getTenantId(),
                                originalFileName);
        OssPushObjectResponse ossPushObjectResponse =
                executor.putObject(bucketName, objectName, file, true);
        ArgumentAssert.notNull(ossPushObjectResponse, "文件上传结果为空");
        return ossPushObjectResponse;
    }

    /**
     * 删除文件
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     */
    public void deleteFile(String bucketName, String objectName) {
        OssExecutor executor = obtainExecutor();
        executor.obtainConfigInfo().getBucketName();
        executor.removeObject(bucketName, objectName);
    }

    /**
     * 获取文件信息
     *
     * @param originalFilename 原始文件名
     * @return 文件上传结果及固定对象位置
     */
    public ObjectResult getObjectResult(String originalFilename) {
        return getObjectResult(originalFilename, null);
    }

    /**
     * 获取文件信息
     *
     * @param originalFilename 原始文件名
     * @param objectNameGenerator OSS对象名称生成器
     * @return 文件上传结果及固定对象位置
     */
    public ObjectResult getObjectResult(
            String originalFilename, ObjectNameGenerator objectNameGenerator) {
        OssExecutor executor = obtainExecutor();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        ObjectNameGenerator actualGenerator = loadObjectNameGenerator(objectNameGenerator);
        String fileName = actualGenerator.fileNameGenerator().generator(originalFilename);
        String objectName =
                executor.obtainConfigInfo().getObjectPrefix()
                        + actualGenerator.generator(
                        environment,
                        RequestLocalContextHolder.getTenantId(),
                        originalFilename);
        String fileUrl = executor.getObjectUrl(bucketName, objectName);
        return new ObjectResult(bucketName, objectName, fileName, fileUrl);
    }

    /**
     * 获取OSS文件地址
     *
     * @param bucketName 桶名称
     * @param objectName 对象名称
     * @return 按当前目标生成的字符串结果
     */
    public String getObjectUrl(String bucketName, String objectName) {
        OssExecutor executor = obtainExecutor();
        OssExecutor ossExecutor = executor;
        return ossExecutor.getObjectUrl(bucketName, objectName);
    }

    /**
     * 获取预签名上传地址
     *
     * @param objectName 对象名称
     * @param contentType 文件Content-Type
     * @return 供应商上传标识及各分片的限时签名信息
     */
    public UploadUrlsInfo getUploadObjectUrl(String objectName, String contentType) {
        OssExecutor executor = obtainExecutor();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        return executor.getPresignedObjectUploadUrl(bucketName, objectName, contentType);
    }

    /**
     * 初始化分片上传
     *
     * @param fileUploadInfo 文件上传信息
     * @param objectName 对象名称
     * @return UploadUrlsInfo 分片上传地址信息
     */
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String objectName) {
        OssExecutor executor = obtainExecutor();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        return executor.initMultiPartUpload(fileUploadInfo, bucketName, objectName);
    }

    /**
     * 合并分片上传
     *
     * @param objectName 对象名称
     * @param uploadId 上传Id
     * @return 是否合并成功
     */
    public boolean mergeMultipartUpload(String objectName, String uploadId) {
        OssExecutor executor = obtainExecutor();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        return executor.mergeMultipartUpload(bucketName, objectName, uploadId);
    }

    /**
     * 获取分片数据
     *
     * @param objectName 对象名称
     * @param uploadId 上传Id
     * @return 包含真实分片编号、大小和 ETag 的清单
     */
    public List<PartData> getListParts(String objectName, String uploadId) {
        OssExecutor executor = obtainExecutor();
        String bucketName = executor.obtainConfigInfo().getBucketName();
        return executor.getListParts(bucketName, objectName, uploadId);
    }

    /**
     * 文件下载
     *
     * @param request HTTP 下载请求
     * @param response HTTP 下载响应
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param fileName 文件名称
     * @throws Exception 异常
     */
    public void download(
            HttpServletRequest request,
            HttpServletResponse response,
            String bucketName,
            String objectName,
            String fileName)
            throws Exception {
        OssExecutor executor = obtainExecutor();
        executor.downloadMultipartFile(bucketName, objectName, fileName, request, response);
    }

    /**
     * ObjectResult 的 OSS 存储适配实现。
     *
     * @author zhouxinlei
     * @see java.lang.AutoCloseable
     * @since 2026-10-06
     */
    @Getter
    @Setter
    public static class ObjectResult implements Serializable {

        private static final long serialVersionUID = -4643773241040725253L;

        private final String bucketName;

        private final String objectName;

        private final String fileName;

        private final String fileUrl;

        /**
         * 保存对象位置和访问地址，用于本次上传结果。
         *
         * @param bucketName Bucket 名称
         * @param objectName 对象键
         * @param fileName   文件名称
         * @param fileUrl    受控文件访问地址
         */
        public ObjectResult(String bucketName, String objectName, String fileName, String fileUrl) {
            this.bucketName = bucketName;
            this.objectName = objectName;
            this.fileName = fileName;
            this.fileUrl = fileUrl;
        }
    }
}
