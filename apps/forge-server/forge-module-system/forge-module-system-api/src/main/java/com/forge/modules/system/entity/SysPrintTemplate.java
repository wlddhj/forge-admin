package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打印模板配置实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_print_template")
public class SysPrintTemplate {

    @Schema(description = "主键ID")
    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "模板编号（同租户内唯一）")
    private String templateCode;

    @Schema(description = "模板名称")
    private String templateName;

    @Schema(description = "模板内容（hiprint JSON）")
    private String contents;

    @Schema(description = "版本号")
    private Integer version;

    @Schema(description = "状态（0:禁用 1:启用）")
    private Integer status;

    @Schema(description = "备注")
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    private Long createBy;

    private Long updateBy;

    @TableLogic
    private Integer deleted;
}
