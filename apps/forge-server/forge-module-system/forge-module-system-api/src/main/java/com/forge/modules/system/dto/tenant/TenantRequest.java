package com.forge.modules.system.dto.tenant;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 租户新增/修改请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "租户请求")
public class TenantRequest {

    @Schema(description = "租户ID（修改时必传）")
    private Long id;

    @Schema(description = "租户名称")
    @NotBlank(message = "租户名称不能为空")
    private String name;

    @Schema(description = "租户标识（登录用）")
    @NotBlank(message = "租户标识不能为空")
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

    @Schema(description = "租户管理员用户名（不传则默认 'admin'）")
    private String adminUsername;

    @Schema(description = "租户管理员初始密码（不传则自动生成16位强密码，首次登录强制改密）",
            hidden = true)  // 不暴露给前端（前端用自动生成的）
    private String adminPassword;
}
