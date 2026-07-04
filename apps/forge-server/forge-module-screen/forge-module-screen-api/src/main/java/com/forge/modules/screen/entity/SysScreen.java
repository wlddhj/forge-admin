package com.forge.modules.screen.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 大屏实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_screen")
public class SysScreen {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 大屏编码
     */
    private String code;

    /**
     * 大屏名称
     */
    private String name;

    /**
     * 描述
     */
    private String description;

    /**
     * 已发布配置（JSON 字符串）
     */
    private String config;

    /**
     * 草稿配置（JSON 字符串）
     */
    private String configDraft;

    /**
     * 主题
     */
    private String theme;

    /**
     * 状态(0:草稿 1:已发布)
     */
    private Integer status;

    @Version
    private Integer version;

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
