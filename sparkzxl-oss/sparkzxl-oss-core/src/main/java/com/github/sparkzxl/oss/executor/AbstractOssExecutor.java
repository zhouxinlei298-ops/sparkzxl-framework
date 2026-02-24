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

    public void uploadFileLimit(String fileName) {
        String extension = FileNameUtils.getExtension(fileName);
        Configuration configuration = obtainConfigInfo();
        String fileFormat = configuration.getFileFormat();
        if (StringUtils.isEmpty(fileFormat)) {
            return;
        }
        List<String> fileFormatList = ListUtils.stringToList(fileFormat);
        ArgumentAssert.isFalse(!fileFormatList.contains(extension), "上传文件格式限制，不允许上传");
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
}
