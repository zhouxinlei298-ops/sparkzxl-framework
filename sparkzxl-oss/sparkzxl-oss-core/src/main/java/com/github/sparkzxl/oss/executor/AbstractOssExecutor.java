package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.core.util.ArgumentAssert;
import com.github.sparkzxl.core.util.ListUtils;
import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.properties.Configuration;
import org.apache.commons.compress.utils.FileNameUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

/**
 * description: 抽象oss执行器
 *
 * @author zhouxinlei
 * @since 2022-05-07 15:27:13
 */
public abstract class AbstractOssExecutor<T> implements OssExecutor {

    // 64KB
    protected static final Integer BUFFER_SIZE = 1024 * 64;

    protected final OssClient<T> client;

    public AbstractOssExecutor(OssClient<T> client) {
        this.client = client;
    }

    /**
     * oss对象名称校验
     * @param objectName oss对象名称
     */
    public void objectNameValidate(String objectName) {
        // 验证 objectName 防止路径遍历攻击
        validateObjectName(objectName);

        String extension = FileNameUtils.getExtension(objectName);

        // 修复：无扩展名文件也视为不符合格式限制（除非配置允许所有格式）
        Configuration configuration = obtainConfigInfo();
        String fileFormat = configuration.getFileFormat();
        if (StringUtils.isEmpty(fileFormat)) {
            return;
        }

        // 如果文件没有扩展名，拒绝上传
        if (StringUtils.isEmpty(extension)) {
            throw new IllegalArgumentException(
                    String.format("上传文件格式限制：文件 [%s] 没有扩展名，不允许上传", objectName));
        }

        List<String> fileFormatList = ListUtils.stringToList(fileFormat);
        ArgumentAssert.isFalse(!fileFormatList.contains(extension),
                String.format("上传文件格式限制：不允许上传 [%s] 格式的文件", extension));
    }

    /**
     * 获取当前线程客户端
     *
     * @return T
     */
    protected T obtainClient() {
        return client.getClient();
    }

    /**
     * 获取当前线程配置信息
     *
     * @return OssConfigInfo
     */
    @Override
    public Configuration obtainConfigInfo() {
        return client.getConfiguration();
    }

    /**
     * 验证 bucketName 以防止非法输入和注入攻击
     * <p>
     * 根据 AWS S3 bucket 命名规范，bucket 名称只能包含：
     * - 小写字母 (a-z)
     * - 数字 (0-9)
     * - 连字符 (-)
     * - 点号 (.)
     * </p>
     * <p>
     * 附加规则：
     * - 长度必须在 3-63 个字符之间
     * - 不能以连字符或点号开头/结尾
     * - 不能包含连续的点号
     * </p>
     *
     * @param bucketName bucket 名称
     * @throws IllegalArgumentException 如果 bucketName 包含非法字符或不符合命名规范
     */
    protected void validateBucketName(String bucketName) {
        if (bucketName == null || bucketName.isEmpty()) {
            throw new IllegalArgumentException("Bucket name cannot be null or empty");
        }

        // 长度验证
        if (bucketName.length() < 3 || bucketName.length() > 63) {
            throw new IllegalArgumentException(
                    String.format("Invalid bucket name '%s'. Bucket name length must be between 3 and 63 characters", bucketName));
        }

        // 不能以连字符或点号开头/结尾
        if (bucketName.startsWith("-") || bucketName.endsWith("-") ||
                bucketName.startsWith(".") || bucketName.endsWith(".")) {
            throw new IllegalArgumentException(
                    String.format("Invalid bucket name '%s'. Bucket name cannot start or end with a hyphen (-) or dot (.)", bucketName));
        }

        // 不能包含连续的点号
        if (bucketName.contains("..")) {
            throw new IllegalArgumentException(
                    String.format("Invalid bucket name '%s'. Bucket name cannot contain consecutive dots (..)", bucketName));
        }

        // 只能包含小写字母、数字、连字符和点号（防止 JSON 注入）
        if (!bucketName.matches("^[a-z0-9.-]+$")) {
            throw new IllegalArgumentException(
                    String.format("Invalid bucket name '%s'. Bucket name can only contain lowercase letters, numbers, dots (.) and hyphens (-)", bucketName));
        }
    }

    /**
     * 验证 objectName 以防止路径遍历攻击
     * <p>
     * 防止恶意用户通过 ../ 或 ..\ 等路径遍历字符访问系统中的任意文件
     * </p>
     *
     * @param objectName oss对象名称
     * @throws IllegalArgumentException 如果 objectName 包含路径遍历字符
     */
    protected void validateObjectName(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            throw new IllegalArgumentException("Object name cannot be null or empty");
        }

        // 检查路径遍历攻击
        String normalizedObjectName = objectName.replace('\\', '/');

        // 检查是否包含 ../ 或 ./
        if (normalizedObjectName.contains("../") || normalizedObjectName.contains("./")) {
            throw new IllegalArgumentException(
                    String.format("Invalid object name '%s'. Object name cannot contain path traversal sequences (../ or ./)", objectName));
        }

        // 检查是否以 .. 或 . 开头
        if (normalizedObjectName.startsWith("../") || normalizedObjectName.startsWith("./") ||
            "..".equals(normalizedObjectName) || ".".equals(normalizedObjectName)) {
            throw new IllegalArgumentException(
                    String.format("Invalid object name '%s'. Object name cannot start with path traversal sequences", objectName));
        }

        // 检查是否包含绝对路径模式
        if (normalizedObjectName.startsWith("/")) {
            throw new IllegalArgumentException(
                    String.format("Invalid object name '%s'. Object name cannot be an absolute path (starts with /)", objectName));
        }

        // 检查长度限制（S3 对象键最大 1024 字节）
        if (objectName.length() > 1024) {
            throw new IllegalArgumentException(
                    String.format("Invalid object name '%s'. Object name length cannot exceed 1024 characters", objectName));
        }
    }

    /**
     * 从objectName中提取文件名
     * objectName格式如 dev/220200/2026/05/13/abc.pdf
     *
     * @param objectName OSS对象名称
     * @return 文件名
     */
    protected String extractFileName(String objectName) {
        int lastIndex = objectName.lastIndexOf('/');
        return lastIndex >= 0 ? objectName.substring(lastIndex + 1) : objectName;
    }
}
