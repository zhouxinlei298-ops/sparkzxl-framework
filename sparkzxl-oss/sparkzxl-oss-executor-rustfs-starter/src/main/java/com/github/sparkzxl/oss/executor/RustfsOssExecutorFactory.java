package com.github.sparkzxl.oss.executor;

import com.github.sparkzxl.oss.client.OssClient;
import com.github.sparkzxl.spi.Join;

/**
 * description: Rustfs oss 执行器工厂
 *
 * @author zhouxinlei
 * @since 2022-10-12 08:47:30
 */
@Join
public class RustfsOssExecutorFactory implements OssExecutorFactory {


    @Override
    public OssExecutor create(OssClient ossClient) {
        return new RustfsExecutor(ossClient);
    }
}
