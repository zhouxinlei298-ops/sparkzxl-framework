package com.github.sparkzxl.datascope.properties;

import com.baomidou.mybatisplus.annotation.DbType;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.ibatis.mapping.SqlCommandType;
import com.github.sparkzxl.datascope.enums.SqlCondition;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.io.Serializable;
import java.util.List;

/**
 * description: 使用数据权限
 *
 * @author zhouxinlei
 * @since 2023-12-20 11:29:10
 */
@Data
@ConfigurationProperties(prefix = "spring.datasource.data-scope")
public class DataScopeConfProperties {

    /**
     * 使用数据权限
     */
    private boolean enabled;

    /**
     * 全局数据权限规则bean name
     */
    private String globalRule = "defaultDataScopeRule";

    /**
     * 数据库类型
     */
    private DbType dbType = DbType.MYSQL;

    private List<DataScopeConf> configList;


    /**
     * description: 数据权限配置信息
     *
     * @author zhouxinlei
     * @since 2022-12-08 09:11:53
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataScopeConf implements Serializable {

        private static final long serialVersionUID = -6446831012888624449L;
        /**
         * 数据权限id标识
         */
        private String scopeId;
        /**
         * 数据权限规则id标识
         */
        private String ruleId;
        /**
         * 表名
         */
        private String tableName;
        /**
         * SQL类型
         */
        private SqlCommandType sqlCommandType;

        /**
         * 数据权限列
         */
        List<DataColumn> columns;

    }

    /**
     * description: 数据权限列
     *
     * @author zhouxinlei
     * @since 2023-12-20 11:05:32
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DataColumn implements Serializable {

        private static final long serialVersionUID = 30082524422583630L;
        /**
         * 数据权限字段
         */
        private String column;
        /**
         * 条件类型
         */
        private SqlCondition condition;
        /**
         * 是否强制拼接条件
         */
        private boolean force;
        /**
         * 查询key值
         */
        private String loadKey;

    }

}
