package com.forge.modules.system.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "是否处于登录锁定状态（以 Redis 登录闸口为准）")
    private Boolean locked;

    @Schema(description = "账号锁定截止时间（DB 快照，可能为历史值，展示以 locked 为准）")
    private LocalDateTime lockTime;

    @Schema(description = "连续登录失败次数")
    private Integer passwordErrorCount;

    @Schema(description = "锁定剩余秒数（未锁定时为 0）")
    private Long lockRemainSeconds;

    private LocalDateTime createTime;
    private List<Long> roleIds;
    private List<String> roleNames;
}
