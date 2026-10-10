package com.forge.modules.system.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
@Schema(description = "批量分配角色请求")
public class UserRoleAssignRequest {

    @Schema(description = "用户ID列表", required = true)
    @NotEmpty(message = "请选择用户")
    private List<Long> userIds;

    @Schema(description = "角色ID列表（空数组表示清空角色）")
    private List<Long> roleIds;
}
