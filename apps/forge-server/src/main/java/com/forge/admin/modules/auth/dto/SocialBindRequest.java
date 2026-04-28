package com.forge.admin.modules.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 社交账号绑定请求
 */
@Data
public class SocialBindRequest {

    /** 临时token（未绑定社交账号时生成，用于关联社交用户信息） */
    @NotBlank(message = "临时令牌不能为空")
    private String tempToken;
}
