package com.github.sparkzxl.oss;

import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.oss.executor.OssExecutor;
import com.github.sparkzxl.oss.executor.OssExecutorFactory;
import com.github.sparkzxl.spi.Join;

/**
 * 消费方扩展类型端到端测试夹具；不是框架内置供应商定义。
 *
 * @author zhouxinlei
 * @since 2026-10-07
 */
@Join
public class PartnerS3OssExecutorFactory implements OssExecutorFactory {

    /**
     * @return 测试专用执行器；这里只验证SPI解析，不执行远端操作
     */
    @Override
    public OssExecutor create(OssClient ossClient) {
        return null;
    }
}
