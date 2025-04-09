package com.github.sparkzxl.log.event;


import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.log.entity.OptRecordLog;
import com.github.sparkzxl.log.utils.BizPointLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Optional;
import java.util.function.Consumer;


/***
 * description: 异步监听请求日志
 *
 * @author charles.zhou
 */
@Slf4j
@RequiredArgsConstructor
public class OptLogListener {

    private final Consumer<OptRecordLog> consumer;

    @Async
    @EventListener(OptLogEvent.class)
    public void saveRequestLog(OptLogEvent event) {
        OptRecordLog optRecordLog = (OptRecordLog) event.getSource();
        if (ObjectUtils.isEmpty(optRecordLog)) {
            log.warn("忽略操作日志记录");
            return;
        }
        Optional.ofNullable(optRecordLog.getTenantId()).ifPresent(RequestLocalContextHolder::setTenantId);
        if (log.isDebugEnabled()) {
            log.debug("用户行为记录：租户：【{}】 请求接口：【{}】 操作人【{}】 业务类型：【{}】 业务日志：【{}】",
                    optRecordLog.getTenantId(), optRecordLog.getRequestUrl(), optRecordLog.getOperator(),
                    optRecordLog.getCategory(), optRecordLog.getDetail());
        }
        BizPointLog.log(optRecordLog);
        consumer.accept(optRecordLog);
    }

}
