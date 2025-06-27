package com.github.sparkzxl.signature.utils;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SM3Digest;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.*;

/**
 * description: SM3密码学哈希算法工具类，支持密钥生成和消息哈希计算
 *
 * @author zhouxinlei
 * @since 2025-06-16 09:50:01
 */
@Slf4j
public class SM3Util {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * 生成 SM3 算法使用的随机密钥（十六进制字符串形式）
     *
     * @param keyLength 密钥长度（字节），通常为 16、24 或 32 字节
     * @return 随机生成的密钥（十六进制字符串）
     */
    public static String generateKey(int keyLength) {
        byte[] key = new byte[keyLength];
        SECURE_RANDOM.nextBytes(key);
        return bytesToHexString(key);
    }

    /**
     * 计算 SM3 哈希值
     *
     * @param srcData 待哈希的数据
     * @return 哈希结果（字节数组）
     */
    public static byte[] hash(byte[] srcData) {
        SM3Digest digest = new SM3Digest();
        digest.update(srcData, 0, srcData.length);
        byte[] hash = new byte[digest.getDigestSize()];
        digest.doFinal(hash, 0);
        return hash;
    }

    /**
     * 创建签名（十六进制字符串形式）
     *
     * @param sortParam 排序后的参数字符串
     * @return 签名结果（十六进制字符串）
     */
    public static String createSign(String sortParam) {
        byte[] signHash = hash(sortParam.getBytes(StandardCharsets.UTF_8));
        return bytesToHexString(signHash);
    }

    /**
     * 生成签名数据
     *
     * @param params 请求参数（不含key）
     * @param secret 密钥（十六进制字符串形式）
     * @return 用于签名的字符串
     */
    private static String generateSignData(Map<String, Object> params, String secret) {
        // 第1步: 将所有参数（注意是所有参数，包括appKey,timestamp,nonce），除去sign本身,拼接成字符串
        String mapToString = SortUtils.mapToString(params, "&", "=");
        // 第2步: 将参数名和值的拼接
        String signData = mapToString.replaceAll("&", "").replaceAll("=", "");
        String sign = signData + secret;
        System.out.println(signData);
        log.debug("签名数据排序：{}", signData);
        // 第2步: 在上面拼接得到的字符串前加上密钥secret
        return sign;
    }

    /**
     * 生成签名
     *
     * @param params 请求参数
     * @param secret 密钥（十六进制字符串形式）
     * @return 签名结果（十六进制字符串）
     */
    public static String sign(Map<String, Object> params, String secret) {
        String signData = generateSignData(params, secret);
        return createSign(signData);
    }

    /**
     * 验证签名
     *
     * @param str       原始字符串
     * @param hexString 待验证的签名（十六进制字符串形式）
     * @return 验证结果
     */
    public static boolean verify(String str, String hexString) {
        String computedSign = createSign(str);
        return computedSign.equals(hexString);
    }

    /**
     * 验证参数签名
     *
     * @param params    请求参数
     * @param hexString 待验证的签名（十六进制字符串形式）
     * @param secret    密钥（十六进制字符串形式）
     * @return 验证结果
     */
    public static boolean verify(Map<String, Object> params, String hexString, String secret) {
        String computedSign = sign(params, secret);
        return computedSign.equals(hexString);
    }

    /**
     * 字节数组转十六进制字符串
     */
    private static String bytesToHexString(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }

    public static void main(String[] args) {
        // 构建请求参数
        Map<String, Object> params = new HashMap<>();
        params.put("socialCreditCode", "24427218FX8FQTDMBE");
        params.put("enterpriseName", "测试企业");
        params.put("contactName", "茅全勇");
        params.put("contactPhone", "13202630638");

        // 构建文件列表
        List<Map<String, Object>> fileList = new ArrayList<>();
        Map<String, Object> fileMap0 = new HashMap<>();
        fileMap0.put("fileId", 1809035302853783552L);
        fileMap0.put("fileName", "测试文件.pdf");
        fileMap0.put("fileUrl", "http://172.16.200.202:9000/nmg/dev/330300/2024/01/22/8e95100dab334115a6e7027c44393b99.pdf");
        fileList.add(fileMap0);

        Map<String, Object> fileMap1 = new HashMap<>();
        fileMap1.put("fileId", 1749318552873357313L);
        fileMap1.put("fileName", "cs.pdf");
        fileMap1.put("fileUrl", "http://172.16.200.202:9000/nmg/dev/330300/2024/01/22/26f2f9855c0141d5965df7cad62d24e5.pdf");
        fileList.add(fileMap1);

        params.put("files", fileList);

        // 构建最终参数
        Map<String, Object> finalParams = new HashMap<>();
        finalParams.put("appKey", "330300");
        finalParams.put("timestamp", System.currentTimeMillis());
        finalParams.put("nonce", UUID.randomUUID().toString().replace("-", ""));
        finalParams.put("level", 4);

        // 打印参数
        System.out.println("请求参数: " + finalParams);

        // 生成密钥（正确方式）
        String secret = "8dffe576976c0502b1838ab2f069cd5c";
        System.out.println("生成的密钥（十六进制）: " + secret);

        // 生成签名
        String sign = sign(finalParams, secret);
        System.out.println("生成的签名: " + sign);

        // 验证签名
        boolean isValid = verify(finalParams, sign, secret);
        System.out.println("签名验证结果: " + isValid);
    }
}
