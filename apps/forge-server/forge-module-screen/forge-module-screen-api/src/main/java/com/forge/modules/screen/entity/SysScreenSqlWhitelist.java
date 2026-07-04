package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 大屏 SQL 白名单实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_screen_sql_whitelist")
public class SysScreenSqlWhitelist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * Schema 名称
     */
    private String schemaName;

    /**
     * 表名
     */
    private String tableName;

    /**
     * 允许查询的列（逗号分隔），* 表示全部
     */
    private String columnList;

    /**
     * 风险等级
     */
    private Integer riskLevel;

    /**
     * 是否启用(0:禁用 1:启用)
     */
    private Integer enabled;

    /**
     * 备注
     */
    private String remark;
}
