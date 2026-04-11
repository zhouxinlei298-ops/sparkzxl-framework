package com.github.sparkzxl.mongodb.event;

import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.IdUtil;
import com.github.sparkzxl.core.context.RequestLocalContextHolder;
import com.github.sparkzxl.mongodb.entity.Entity;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.data.mongodb.core.mapping.event.AbstractMongoEventListener;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertEvent;

import java.time.LocalDateTime;


/**
 * description: mongodb 插入时间监听
 *
 * @author zhouxinlei
 */
public class MongoInsertEventListener extends AbstractMongoEventListener<Entity> {

    private final Snowflake snowflake = IdUtil.getSnowflake(0, 10);

    @Override
    public void onBeforeConvert(BeforeConvertEvent<Entity> event) {
        Entity entity = event.getSource();
        String name = RequestLocalContextHolder.getName();
        String userId = RequestLocalContextHolder.getUserId(String.class);
        // 判断 id 为空
        LocalDateTime localDateTime = LocalDateTime.now();
        if (entity.getId() == null) {
            Number id = snowflake.nextId();
            // noinspection unchecked
            entity.setId(id);
        }
        if (ObjectUtils.isEmpty(entity.getCreatedAt())){
            entity.setCreatedAt(localDateTime);
            entity.setCreatedBy(userId);
            entity.setCreateName(name);
        }
        if (ObjectUtils.isEmpty(entity.getUpdatedAt())){
            entity.setUpdatedAt(localDateTime);
            entity.setUpdatedBy(userId);
            entity.setUpdateName(name);
        }
    }

}
