package com.github.sparkzxl.signature.utils;

import cn.hutool.core.util.IdUtil;
import com.alibaba.excel.util.StringUtils;
import com.github.sparkzxl.core.json.JsonUtils;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.bouncycastle.pqc.legacy.math.linearalgebra.ByteUtils;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * description: SM3SignUtil
 *
 * @author zhouxinlei
 * @since 2025-06-16 10:00:39
 */
public class SM3SignUtil {

    /**
     * 生成签名sign参数数据
     *
     * @param params 请求参数 。注意请求参数中不能包含key
     * @return String
     */
    private static String generateSignData(Map<String, Object> params, String secret) {
        // 第1步: 将所有参数（注意是所有参数，包括appKey,timestamp,nonce），除去sign本身,拼接成字符串
        String mapToString = SortUtils.mapToString(params, "", "");
        // 第2步: 将参数名和值的拼接
        System.out.println(mapToString);
        // 第2步: 在上面拼接得到的字符串前加上密钥secret
        return mapToString + secret;
    }

    public static String sign(Map<String, Object> params, String secret) {
        String signData = generateSignData(params, secret);
        return SM3Util.createSign(signData);
    }

    /**
     * @param str       明文
     * @param hexString 密文
     * @return 明文密文对比结果
     */
    public static boolean verify(String str, String hexString) {
        boolean flag = false;
        byte[] srcData = str.getBytes(StandardCharsets.UTF_8);
        byte[] sm3Hash = ByteUtils.fromHexString(hexString);
        byte[] hash = SM3Util.hash(srcData);
        if (Arrays.equals(hash, sm3Hash)) {
            flag = true;
        }
        return flag;
    }

    /**
     * @param params    明文参数
     * @param hexString 密文
     * @return 明文密文对比结果
     */
    public static boolean verify(Map<String, Object> params, String hexString, String secret) {
        String sign = sign(params, secret);
        return StringUtils.equals(sign, hexString);
    }


    public static void main(String[] args) {
        Map<String, Object> params = Maps.newHashMap();
        params.put("socialCreditCode", "24427218FX8FQTDMBE");
        params.put("enterpriseName", "测试企业");
        params.put("contactName", "茅全勇");
        params.put("contactPhone", "13202630638");
        List<Map<String, Object>> mapList = Lists.newArrayList();
        Map<String, Object> fileMap0 = Maps.newHashMap();
        fileMap0.put("fileId", 1809035302853783552L);
        fileMap0.put("fileName", "测试文件.pdf");
        fileMap0.put("fileUrl", "http://172.16.200.202:9000/nmg/dev/330300/2024/01/22/8e95100dab334115a6e7027c44393b99.pdf");
        mapList.add(fileMap0);
        Map<String, Object> fileMap1 = Maps.newHashMap();
        fileMap1.put("fileId", 1749318552873357313L);
        fileMap1.put("fileName", "cs.pdf");
        fileMap1.put("fileUrl", "http://172.16.200.202:9000/nmg/dev/330300/2024/01/22/26f2f9855c0141d5965df7cad62d24e5.pdf");
        mapList.add(fileMap1);
        params.put("files", mapList);
        Map<String, Object> finalParams = Maps.newHashMap();
        finalParams.put("appKey", "mbnfq0dwgzltcilw");
        finalParams.put("timestamp", System.currentTimeMillis());
        finalParams.put("nonce", IdUtil.fastSimpleUUID());
        finalParams.put("dataInfo", params);
        System.out.println("加签参数：" + JsonUtils.getJson().toJsonPretty(finalParams));
        String secret = IdUtil.fastSimpleUUID();
        String sign = sign(finalParams, secret);
        System.out.println("sign:" + sign);
        boolean verified = verify(finalParams, secret, sign);
        System.out.println("sign verify result:" + verified);
    }
}
