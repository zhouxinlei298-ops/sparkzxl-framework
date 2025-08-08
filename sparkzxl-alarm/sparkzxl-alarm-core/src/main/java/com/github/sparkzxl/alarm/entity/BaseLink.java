package com.github.sparkzxl.alarm.entity;

import lombok.Getter;
import lombok.Setter;

/**
 * description: Link类型实体对象
 *
 * @author zhouxinlei
 * @since 2022-05-19 10:38:05
 */
@Setter
@Getter
public class BaseLink {

    /**
     * 消息标题
     */
    private String title;
    /**
     * 消息内容。如果太长只会部分展示
     */
    private String text;
    /**
     * 点击消息跳转的URL
     */
    private String messageUrl;
    /**
     * 图片URL
     */
    private String picUrl;

    private BaseLink(String title, String text, String messageUrl, String picUrl) {
        this.title = title;
        this.text = text;
        this.messageUrl = messageUrl;
        this.picUrl = picUrl;
    }

    public static BaseLink instance(String title, String text, String messageUrl, String picUrl) {
        return new BaseLink(title, text, messageUrl, picUrl);
    }

}
