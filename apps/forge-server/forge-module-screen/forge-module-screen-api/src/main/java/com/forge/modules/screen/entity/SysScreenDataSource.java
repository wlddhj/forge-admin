package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏数据源实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_screen_data_source")
public class SysScreenDataSource {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 数据源编码
     */
    private String code;

    /**
     * 数据源名称
     */
    private String name;

    /**
     * 类型(HTTP/SQL)
     */
    private String type;

    /**
     * 配置（JSON 字符串）。
     *
     * <p>包含 SQL 原文 / HTTP URL 等敏感信息。列表接口通过 SELECT 排除此字段，
     * 详情接口正常返回以供编辑回显。
     */
    private String config;

    /**
     * 缓存秒数
     */
    private Integer cacheSeconds;

    /**
     * 是否启用(0:禁用 1:启用)
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /**
     * 创建人
     */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /**
     * 更新人
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /**
     * 删除标记
     */
    @TableLogic
    private Integer deleted;

    /**
     * 备注
     */
    private String remark;
}
