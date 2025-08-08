package com.github.sparkzxl.alarm.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * description: 告警图文类型实体
 *
 * @author zhouxinlei
 * @since 2022-05-19 10:19:05
 */
@Setter
@Getter
public class BaseImageText {

    /**
     * 标题
     */
    private String title;
    /**
     * 描述-仅限Wetalk
     */
    private String description;
    /**
     * title点击链接地址
     */
    private String url;
    /**
     * 图片地址
     */
    private String picUrl;

    private BaseImageText(String title, String url, String picUrl) {
        this.title = title;
        this.url = url;
        this.picUrl = picUrl;
    }

    private BaseImageText(String title, String description, String url, String picUrl) {
        this(title, url, picUrl);
        this.description = description;
    }

    public static BaseImageText instance(String title, String url, String picUrl) {
        return new BaseImageText(title, url, picUrl);
    }

    public static BaseImageText instance(String title, String description, String url, String picUrl) {
        return new BaseImageText(title, description, url, picUrl);
    }

}
