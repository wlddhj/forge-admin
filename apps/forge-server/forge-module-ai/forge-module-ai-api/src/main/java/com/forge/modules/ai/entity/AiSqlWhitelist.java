package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI Agent SQL 工具表白名单实体（跨租户共享配置，物理删除）
 */
@Data
@TableName("ai_sql_whitelist")
public class AiSqlWhitelist {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 表名（小写）
     */
    private String tableName;

    /**
     * 表注释（展示给 Agent 的表清单）
     */
    private String tableComment;

    /**
     * 状态(0:禁用 1:启用)
     */
    private Integer enabled;

    private String remark;

    private Long createBy;

    private Long updateBy;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
