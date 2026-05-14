package com.github.sparkzxl.oss.context;

import cn.hutool.http.ContentType;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.core.util.StrPool;
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
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * description: 文件上传下载
 *
 * @author zhouxinlei
 * @since 2024-11-15 15:34:39
 */
@Slf4j
@Component
public class FileUploadContext {

    private static final String AGENT_FIREFOX = "firefox";

    @Value("${spring.profiles.active}")
    private String environment;
    @Resource
    private OssTemplate ossTemplate;

    /**
     * 获取当前执行器
     *
     * @return OssExecutor
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
     * @return ObjectNameGenerator
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
     * @return OssPushObjectResponse
     */
    public OssPushObjectResponse uploadFile(MultipartFile multipartFile) {
        return uploadFile(multipartFile, null);
    }


    /**
     * 文件上传
     *
     * @param multipartFile       文件对象
     * @param objectNameGenerator OSS对象名称生成器
     * @return OssPushObjectResponse
     */
    public OssPushObjectResponse uploadFile(MultipartFile multipartFile, ObjectNameGenerator objectNameGenerator) {
        ArgumentAssert.notNull(multipartFile, "上传的文件为空");
        String originalFileName = multipartFile.getOriginalFilename();
        String bucketName = obtainBucketName();
        String tenantId = RequestLocalContextHolder.getTenantId();
        log.info("OSS Configuration tenantId: {},clientId: {},bucketName: {}",
                tenantId,
                obtainExecutor().obtainConfigInfo().getClientId(),
                bucketName);
        String objectName = loadObjectNameGenerator(objectNameGenerator).generator(environment, tenantId, originalFileName);
        OssPushObjectResponse ossPushObjectResponse = obtainExecutor().putObject(bucketName, objectName, multipartFile);
        ArgumentAssert.notNull(ossPushObjectResponse, "文件上传结果为空");
        return ossPushObjectResponse;
    }

    /**
     * URL文件上传
     *
     * @param originalFileName 文件名
     * @param url              文件地址
     * @return UploadFileModel
     */
    public OssPushObjectResponse uploadUrlFile(String originalFileName, URL url) {
        return uploadUrlFile(originalFileName, url, null);
    }


    /**
     * URL文件上传
     *
     * @param originalFileName    原始文件名
     * @param url                 文件地址
     * @param objectNameGenerator OSS对象名称生成器
     * @return UploadFileModel
     */
    public OssPushObjectResponse uploadUrlFile(String originalFileName, URL url, ObjectNameGenerator objectNameGenerator) {
        ArgumentAssert.notEmpty(originalFileName, "文件名不能为空");
        ArgumentAssert.notNull(url, "远程文件地址为空，无法上传");
        String bucketName = obtainBucketName();
        String objectName = loadObjectNameGenerator(objectNameGenerator).generator(environment, RequestLocalContextHolder.getTenantId(), originalFileName);
        OssPushObjectResponse ossPushObjectResponse = obtainExecutor().putObject(bucketName, objectName, url);
        ArgumentAssert.notNull(ossPushObjectResponse, "文件上传结果为空");
        return ossPushObjectResponse;
    }

    /**
     * 上传本地文件
     *
     * @param originalFileName 原始文件名
     * @param file             本地文件
     * @return OssPushObjectResponse
     */
    public OssPushObjectResponse uploadLocalFile(String originalFileName, File file) {
        return uploadLocalFile(originalFileName, file, null);
    }

    /**
     * 上传本地文件
     *
     * @param originalFileName    原始文件名
     * @param file                本地文件
     * @param objectNameGenerator OSS对象名称生成器
     * @return OssPushObjectResponse
     */
    public OssPushObjectResponse uploadLocalFile(String originalFileName, File file, ObjectNameGenerator objectNameGenerator) {
        ArgumentAssert.notEmpty(originalFileName, "文件名不能为空");
        ArgumentAssert.notNull(file, "文件为空，无法上传");
        String bucketName = obtainBucketName();
        String objectName = loadObjectNameGenerator(objectNameGenerator).generator(environment, RequestLocalContextHolder.getTenantId(), originalFileName);
        OssPushObjectResponse ossPushObjectResponse = obtainExecutor().putObject(bucketName, objectName, file, true);
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
        obtainBucketName();
        obtainExecutor().removeObject(bucketName, objectName);
    }

    /**
     * 获取文件信息
     *
     * @param originalFilename 原始文件名
     * @return FileResult
     */
    public ObjectResult getObjectResult(String originalFilename) {
        return getObjectResult(originalFilename, null);
    }


    /**
     * 获取文件信息
     *
     * @param originalFilename    原始文件名
     * @param objectNameGenerator OSS对象名称生成器
     * @return FileResult
     */
    public ObjectResult getObjectResult(String originalFilename,
                                        ObjectNameGenerator objectNameGenerator) {
        String bucketName = obtainBucketName();
        ObjectNameGenerator actualGenerator = loadObjectNameGenerator(objectNameGenerator);
        String fileName = actualGenerator.fileNameGenerator().generator(originalFilename);
        String objectName = actualGenerator.generator(environment, RequestLocalContextHolder.getTenantId(), originalFilename);
        String fileUrl = obtainExecutor().getObjectUrl(bucketName, objectName);
        return new ObjectResult(bucketName, objectName, fileName, fileUrl);
    }


    /**
     * 获取预签名上传地址
     *
     * @param objectName  对象名称
     * @param contentType 文件Content-Type
     * @return UploadUrlsInfo
     */
    public UploadUrlsInfo getUploadObjectUrl(String objectName, String contentType) {
        String bucketName = obtainBucketName();
        return obtainExecutor().getPresignedObjectUploadUrl(bucketName, objectName, contentType);
    }

    /**
     * 初始化分片上传
     *
     * @param fileUploadInfo 文件上传信息
     * @param objectName     对象名称
     * @return UploadUrlsInfo 分片上传地址信息
     */
    public UploadUrlsInfo initMultiPartUpload(FileUploadInfo fileUploadInfo, String objectName) {
        String bucketName = obtainBucketName();
        return obtainExecutor().initMultiPartUpload(fileUploadInfo, bucketName, objectName);
    }

    /**
     * 合并分片上传
     *
     * @param objectName 对象名称
     * @param uploadId   上传Id
     * @return 是否合并成功
     */
    public boolean mergeMultipartUpload(String objectName, String uploadId) {
        String bucketName = obtainBucketName();
        return obtainExecutor().mergeMultipartUpload(bucketName, objectName, uploadId);
    }

    /**
     * 获取分片数据
     *
     * @param objectName 对象名称
     * @param uploadId   上传Id
     * @return List<PartData>
     */
    public List<PartData> getListParts(String objectName, String uploadId) {
        String bucketName = obtainBucketName();
        return obtainExecutor().getListParts(bucketName, objectName, uploadId);
    }

    /**
     * 文件下载
     * @param request request
     * @param response response
     * @param bucketName bucket名称
     * @param objectName oss对象名称
     * @param fileName 文件名称
     * @throws Exception 异常
     */
    public void download(HttpServletRequest request, HttpServletResponse response, String bucketName,String objectName,String fileName) throws Exception {
        HttpURLConnection connection;
        OssExecutor ossExecutor = obtainExecutor();
        try {
            // 默认3600秒、即一小时有效
            String objectUrl = ossExecutor.getObjectUrl(bucketName, objectName, 3600);
            connection = getConnection(objectUrl);
            response.setContentType(ContentType.OCTET_STREAM.getValue());
            // 设置response的Header
            response.setCharacterEncoding(StandardCharsets.UTF_8.displayName());
            String downloadFileName;
            String agent = request.getHeader("USER-AGENT");
            if (agent != null && agent.toLowerCase().indexOf(AGENT_FIREFOX) > 0) {
                downloadFileName = "=?UTF-8?B?" + (new String(Base64.encodeBase64((fileName).getBytes(StandardCharsets.UTF_8)))) + "?=";
            } else {
                //~ \ / |:"<>?   这些字符不能被替换，因为系统允许文件名有这些字符！！
                downloadFileName = URLEncoder.encode(fileName, "UTF-8")
                        .replaceAll("\\+", "%20").replaceAll("%28", "\\(")
                        .replaceAll("%29", "\\)")
                        .replaceAll("%3B", StrPool.SEMICOLON)
                        .replaceAll("%40", StrPool.AT).replaceAll("%23", "\\#")
                        .replaceAll("%26", "\\&").replaceAll("%2C", "\\,")
                        .replaceAll("%2B", StrPool.PLUS).replaceAll("%25", StrPool.PERCENT)
                        .replaceAll("%21", StrPool.EXCLAMATION_MARK).replaceAll("%5E", StrPool.HAT)
                        .replaceAll("%24", "\\$").replaceAll("%7E", StrPool.TILDA)
                        .replaceAll("%60", StrPool.BACKTICK).replaceAll("%5B", StrPool.LEFT_SQ_BRACKET)
                        .replaceAll("%3D", StrPool.EQUALS)
                        .replaceAll("%5D", StrPool.RIGHT_SQ_BRACKET).replaceAll("%5C", "\\\\")
                        .replaceAll("%27", StrPool.SINGLE_QUOTE).replaceAll("%2F", StrPool.SLASH)
                        .replaceAll("%7B", StrPool.LEFT_BRACE).replaceAll("%7D", StrPool.RIGHT_BRACE)
                        .replaceAll("%7C", "\\|").replaceAll("%3A", "\\:")
                        .replaceAll("%22", "\\\"").replaceAll("%3C", "\\<")
                        .replaceAll("%3E", "\\>").replaceAll("%3F", "\\?");
            }
            response.addHeader("content-disposition",
                    "attachment;filename=" + downloadFileName);
            ServletOutputStream out = response.getOutputStream();
            downloadFile(connection.getInputStream(), out);
        } catch (Exception e) {
            log.error("download file exception：{}", e.getMessage());
        }

    }

    private static void downloadFile(InputStream is, OutputStream out) throws Exception {
        try {
            byte[] b = new byte[2048];
            int length;
            while ((length = is.read(b)) > 0) {
                out.write(b, 0, length);
            }
        } finally {
            if (out != null) {
                out.close();
            }
            if (is != null) {
                is.close();
            }
        }
    }

    private static HttpURLConnection getConnection(String url) throws Exception {
        log.info("url={}", url);
        URL conUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) conUrl.openConnection();
        connection.connect();
        return connection;
    }

    @Getter
    @Setter
    public static class ObjectResult implements Serializable {

        private static final long serialVersionUID = -4643773241040725253L;
        private final String bucketName;
        private final String objectName;
        private final String fileName;
        private final String fileUrl;

        public ObjectResult(String bucketName,
                            String objectName,
                            String fileName,
                            String fileUrl) {
            this.bucketName = bucketName;
            this.objectName = objectName;
            this.fileName = fileName;
            this.fileUrl = fileUrl;
        }
    }

}
