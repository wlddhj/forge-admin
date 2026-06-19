package com.forge.modules.system.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@Schema(description = "修改密码请求")
public class UserPasswordRequest {

    @Schema(description = "当前密码", required = true)
    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    @Schema(description = "新密码（8-32位，需含大小写字母、数字、特殊字符）", required = true)
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度为8-32位")
    private String newPassword;
}
