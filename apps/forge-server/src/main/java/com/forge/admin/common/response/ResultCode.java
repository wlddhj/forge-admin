package com.forge.admin.common.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 响应状态码
 *
 * @author standadmin
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "操作成功"),
    FAILED(500, "操作失败"),

    // 参数错误 4xx
    VALIDATE_FAILED(400, "参数校验失败"),
    UNAUTHORIZED(401, "未登录或登录已过期"),
    FORBIDDEN(403, "没有相关权限"),
    NOT_FOUND(404, "资源不存在"),

    // 业务错误 5xx
    DATA_NOT_FOUND(5000, "数据不存在"),
    PARAM_ERROR(5001, "参数错误"),
    DATA_EXISTS(5002, "数据已存在"),
    USER_NOT_FOUND(5101, "用户不存在"),
    USER_PASSWORD_ERROR(5102, "密码错误"),
    USER_DISABLED(5103, "用户已被禁用"),
    USER_EXISTS(5104, "用户名已存在"),
    ROLE_EXISTS(5201, "角色已存在"),
    MENU_HAS_CHILDREN(5301, "存在子菜单，不允许删除"),
    DEPT_HAS_CHILDREN(5401, "存在子部门，不允许删除"),
    DEPT_HAS_USERS(5402, "部门下存在用户，不允许删除");

    private final Integer code;
    private final String message;
}
