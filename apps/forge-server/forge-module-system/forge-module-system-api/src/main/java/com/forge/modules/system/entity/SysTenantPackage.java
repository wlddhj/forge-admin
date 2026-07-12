package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户套餐实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_tenant_package")
public class SysTenantPackage {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "套餐名称")
    private String name;

    @Schema(description = "套餐编码")
    private String code;

    @Schema(description = "状态（0禁用 1启用）")
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
