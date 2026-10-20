package com.github.sparkzxl.oss.listener;

import com.github.sparkzxl.oss.operation.OssOperationContextManager;

import org.aopalliance.intercept.MethodInvocation;

/**
 * 默认上传监听类
 *
 * @author zhouxinlei
 * @since 2022-09-27 09:42:10
 */
public class DefaultUploadListener implements UploadListener {

    /**
     * 校验上传前已建立显式操作域。
     *
     * @param invocation 待执行的客户端调用
     */
    @Override
    public void onListener(MethodInvocation invocation) {
        OssOperationContextManager.requiredHandle();
    }

    /**
     * 处理上传后通知，操作域由调用方关闭。
     *
     * @param invocation 待执行的客户端调用
     */
    @Override
    public void afterListener(MethodInvocation invocation) {
        // 操作域由调用方关闭，监听器不改变目标栈。
    }
}
