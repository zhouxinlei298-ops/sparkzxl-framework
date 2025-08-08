package com.github.sparkzxl.alarm.wetalk.entity;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * description: 企业微信-消息类型-markdown类型
 *
 * @author zhouxinlei
 * @since 2022-05-19 10:16:43
 */
@Setter
@Getter
public class WeMarkdown extends WeTalkMessage {

    private Markdown markdown;

    public WeMarkdown(Markdown markdown) {
        setMsgtype(WeTalkMsgType.MARKDOWN.type());
        this.markdown = markdown;
    }

    @Override
    public void transfer(Map<String, Object> params) {
        this.markdown.content = replaceContent(this.markdown.content, params);
    }

    @Setter
    @Getter
    public static class Markdown implements Serializable {

        /**
         * markdown内容，最长不超过4096个字节，必须是utf8编码
         */
        private String content;

        public Markdown() {
        }

        public Markdown(String content) {
            this.content = content;
        }

    }
}
