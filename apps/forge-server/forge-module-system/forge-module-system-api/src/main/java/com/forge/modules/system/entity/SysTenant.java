package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户实体
 *
 * @author standadmin
 */
@Data
@TableName("sys_tenant")
public class SysTenant {

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户标识（登录用）")
    private String code;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;

    @Schema(description = "套餐ID")
    private Long packageId;

    @Schema(description = "到期时间（NULL=永久）")
    private LocalDateTime expireTime;

    @Schema(description = "租户官网")
    private String website;

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
