package com.forge.admin.modules.system.dto.user;

import lombok.Data;

@Data
public class UserPasswordRequest {
    private String oldPassword;
    private String newPassword;
}
