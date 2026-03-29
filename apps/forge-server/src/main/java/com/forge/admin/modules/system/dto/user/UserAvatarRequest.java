package com.forge.admin.modules.system.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
@Schema(description = "更新头像请求")
public class UserAvatarRequest {

    @Schema(description = "头像URL", required = true)
    @NotBlank(message = "头像不能为空")
    private String avatar;
}
