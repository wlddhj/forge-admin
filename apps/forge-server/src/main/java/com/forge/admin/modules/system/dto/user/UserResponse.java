package com.forge.admin.modules.system.dto.user;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 用户响应
 *
 * @author standadmin
 */
@Data
public class UserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String email;
    private String avatar;
    private Long deptId;
    private String deptName;
    private List<Long> positionIds;
    private List<String> positionNames;
    private Integer accountType;
    private Integer status;
    private LocalDateTime lastLoginTime;
    private String lastLoginIp;
    private LocalDateTime createTime;
    private List<Long> roleIds;
    private List<String> roleNames;
}
