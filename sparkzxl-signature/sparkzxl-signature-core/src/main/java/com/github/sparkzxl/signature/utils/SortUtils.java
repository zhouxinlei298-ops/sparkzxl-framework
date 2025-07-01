package com.github.sparkzxl.signature.utils;

import cn.hutool.core.text.StrBuilder;
import com.github.sparkzxl.core.json.JsonUtils;
import org.apache.commons.lang3.ObjectUtils;

import java.util.*;

/**
 * description:
 *
 * @author zhouxinlei
 * @since 2025-07-01 09:48:08
 */
public class SortUtils {


    /**
     * 按照字母顺序进行升序排序
     *
     * @param params             请求参数 。注意请求参数中不能包含key
     * @param connectSymlinks    连接符号，连接两个key/value字段之间的符号，例如"&"、","号
     * @param assignmentSymlinks 赋值符号,例如"="号
     * @return 排序后结果
     */
    public static String mapToString(Map<String, Object> params, String connectSymlinks, String assignmentSymlinks) {
        List<String> sortedKeys = new ArrayList<>(params.keySet());
        Collections.sort(sortedKeys);

        StrBuilder content = StrBuilder.create();
        for (String key : sortedKeys) {
            Object value = params.get(key);
            if (ObjectUtils.isEmpty(value)) {
                continue;
            }

            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapValue = (Map<String, Object>) value;
                content.append(key)
                        .append(mapToString(mapValue, connectSymlinks, assignmentSymlinks));
            } else if (value instanceof List) {
                // 保留顶层列表的键名
                content.append(key);
                content.append(handleList((List<?>) value, connectSymlinks, assignmentSymlinks));
            } else {
                content.append(key)
                        .append(assignmentSymlinks)
                        .append(value.toString())
                        .append(connectSymlinks);
            }
        }

        if (!content.isEmpty() && !connectSymlinks.isEmpty()) {
            return content.subString(0, content.length() - connectSymlinks.length());
        }
        return content.toString();
    }

    /**
     * 处理集合属性
     *
     * @param list               集合对象
     * @param connectSymlinks    连接符号，连接两个key/value字段之间的符号，例如"&"、","号
     * @param assignmentSymlinks 赋值符号,例如"="号
     * @return String
     */
    private static String handleList(List<?> list, String connectSymlinks, String assignmentSymlinks) {
        StrBuilder result = StrBuilder.create();
        for (Object item : list) {
            if (ObjectUtils.isEmpty(item)) {
                continue;
            }

            if (item instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapItem = (Map<String, Object>) item;
                result.append(mapToString(mapItem, connectSymlinks, assignmentSymlinks));
            } else if (item instanceof List) {
                result.append(handleList((List<?>) item, connectSymlinks, assignmentSymlinks));
            } else {
                result.append(item.toString()).append(connectSymlinks);
            }
        }

        return result.toString();
    }

    public static void main(String[] args) {
        Map<String, Object> dataMap = JsonUtils.getJson().toMap("{\"name\":\"富思会\",\"id\":\"1\",\"resultList\":[{\"resultId\":\"0\",\"resultName\":\"刘平峰\"},{\"resultId\":\"1\",\"resultName\":\"徒雅枝\"},{\"resultId\":\"2\",\"resultName\":\"殴岩\"},{\"resultId\":\"3\",\"resultName\":\"幸友义\"},{\"resultId\":\"4\",\"resultName\":\"微峰广\"}]}");
        dataMap.put("mapObj", new HashMap<String, Object>() {{
            put("id", "1");
            put("username", "张三");
        }});
        System.out.println(JsonUtils.getJson().toJson(dataMap));
        System.out.println(SortUtils.mapToString(dataMap, "", ""));
    }
}
