package com.forge.admin.modules.system.dto.user;

import lombok.Data;

@Data
public class UserProfileRequest {
    private String nickname;
    private String phone;
    private String email;
}
