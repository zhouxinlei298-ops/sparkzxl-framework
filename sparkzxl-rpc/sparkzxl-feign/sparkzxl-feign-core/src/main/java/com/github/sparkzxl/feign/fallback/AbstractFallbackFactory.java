package com.github.sparkzxl.feign.fallback;

import cn.hutool.aop.ProxyUtil;
import com.github.sparkzxl.feign.aspect.FeignAlarmFallbackAspect;
import feign.FeignException;
import lombok.Data;
import org.springframework.cloud.openfeign.FallbackFactory;

/**
 * description: 降级工厂抽象类
 *
 * @author zhouxinlei
 * @since 2025-08-14 09:34:12
 */
@Data
public abstract class AbstractFallbackFactory<T> implements FallbackFactory<T> {

    @Override
    public T create(Throwable cause) {
        FeignAlarmFallbackAspect fallbackAspect = new FeignAlarmFallbackAspect((FeignException) cause);
        return ProxyUtil.proxy(createFallback(cause), fallbackAspect);
    }

    public abstract T createFallback(Throwable cause);
}
