package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户响应
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户响应")
public class TenantResponse {

    @Schema(description = "租户ID")
    private Long id;

    @Schema(description = "租户名称")
    private String name;

    @Schema(description = "租户标识")
    private String code;

    @Schema(description = "联系人")
    private String contactName;

    @Schema(description = "联系电话")
    private String contactPhone;

    @Schema(description = "状态（0禁用 1启用）")
    private Integer status;

    @Schema(description = "套餐ID")
    private Long packageId;

    @Schema(description = "套餐名称")
    private String packageName;

    @Schema(description = "到期时间")
    private LocalDateTime expireTime;

    @Schema(description = "租户官网")
    private String website;

    @Schema(description = "备注")
    private String remark;

    @Schema(description = "创建时间")
    private LocalDateTime createTime;

    /**
     * 仅 addTenant 首次返回的初始管理员密码（前端展示给用户保存，之后不再返回）
     */
    @Schema(description = "初始管理员密码（仅创建时返回）")
    private String initialAdminPassword;
}
