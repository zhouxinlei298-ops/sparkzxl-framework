package com.github.sparkzxl.log.task;

import cn.hutool.cron.task.Task;
import com.github.sparkzxl.alarm.enums.MessageSubType;
import com.github.sparkzxl.alarm.send.AlarmClient;
import com.github.sparkzxl.core.spring.SpringContextUtils;
import com.github.sparkzxl.log.queue.AlarmTaskInfo;
import com.github.sparkzxl.log.queue.AlarmTaskQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * description: 告警任务
 *
 * @author zhouxinlei
 * @since 2022-12-26 13:30:23
 */
@Slf4j
public class AlarmTask implements Task {

    private static final int MAX_BATCH_SIZE = 50;

    private volatile AlarmClient alarmClient;

    @Override
    public void execute() {
        AlarmClient client = getAlarmClient();
        if (client == null) {
            return;
        }
        AlarmTaskQueue queue = AlarmTaskQueue.getQueue();
        AlarmTaskInfo alarmTask;
        int count = 0;
        while ((alarmTask = queue.consume()) != null && count < MAX_BATCH_SIZE) {
            try {
                doSend(client, alarmTask);
            } catch (Exception e) {
                log.error("告警发送失败: {}", e.getMessage(), e);
            }
            count++;
        }
    }

    private void doSend(AlarmClient alarmClient, AlarmTaskInfo alarmTask) {
        if (StringUtils.isBlank(alarmTask.getRobotId())) {
            alarmClient.send(MessageSubType.MARKDOWN, alarmTask.getAlarmRequest());
        } else {
            alarmClient.designatedRobotSend(alarmTask.getRobotId(), MessageSubType.MARKDOWN, alarmTask.getAlarmRequest());
        }
    }

    private AlarmClient getAlarmClient() {
        if (alarmClient == null) {
            try {
                alarmClient = SpringContextUtils.getBean(AlarmClient.class);
            } catch (Exception ignored) {
            }
        }
        return alarmClient;
    }
}
