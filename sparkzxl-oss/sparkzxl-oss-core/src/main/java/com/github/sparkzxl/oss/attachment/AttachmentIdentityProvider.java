package com.github.sparkzxl.oss.attachment;

/**
 * 可信身份提供者：由宿主从请求上下文提取当前租户与主体。
 *
 * <p>框架不感知宿主的会话与租户机制；无身份上下文（如协议端点、后台线程）时返回
 * {@code null}，由调用方决定跳过刷新还是失败关闭。
 *
 * @author zhouxinlei
 * @since 2026-10-25
 */
public interface AttachmentIdentityProvider {

    /**
     * 读取当前请求的可信身份。
     *
     * @return 当前身份；无上下文时返回 null
     */
    AttachmentIdentity current();
}
