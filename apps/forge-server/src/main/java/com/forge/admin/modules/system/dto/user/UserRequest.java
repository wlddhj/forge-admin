package com.forge.admin.modules.system.dto.user;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

/**
 * 用户请求
 *
 * @author standadmin
 */
@Schema(description = "用户信息请求")
@Data
public class UserRequest {

    @Schema(description = "用户ID（更新时必填）", example = "1")
    private Long id;

    @Schema(description = "用户名（登录账号）", example = "zhangsan", required = true)
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Schema(description = "用户昵称", example = "张三", required = true)
    @NotBlank(message = "昵称不能为空")
    private String nickname;

    @Schema(description = "密码（新增时必填）", example = "123456")
    private String password;

    @Schema(description = "手机号", example = "13800138000")
    private String phone;

    @Schema(description = "邮箱", example = "zhangsan@example.com")
    private String email;

    @Schema(description = "头像URL", example = "/uploads/avatar/default.png")
    private String avatar;

    @Schema(description = "所属部门ID", example = "100")
    private Long deptId;

    @Schema(description = "所属岗位ID", example = "10")
    private Long positionId;

    @Schema(description = "账户类型（0:普通用户 1:管理员）", example = "0")
    private Integer accountType;

    @Schema(description = "状态（0:禁用 1:启用）", example = "1")
    private Integer status;

    @Schema(description = "角色ID列表", example = "[1, 2]")
    private List<Long> roleIds;
}
