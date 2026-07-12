package com.forge.common.response;

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
    DEPT_HAS_USERS(5402, "部门下存在用户，不允许删除"),

    // 社交登录错误 55xx
    SOCIAL_USER_NOT_BOUND(5501, "该第三方账号未绑定系统账号"),
    SOCIAL_USER_ALREADY_BOUND(5502, "该第三方账号已被绑定"),
    SOCIAL_LOGIN_FAILED(5504, "第三方登录失败"),

    // 短信相关 56xx
    SMS_COOLDOWN(5601, "验证码发送冷却中，请稍后再试"),
    SMS_DAILY_EXCEEDED(5602, "今日验证码发送次数已达上限"),
    SMS_CODE_NOT_FOUND(5603, "验证码不存在或已过期"),
    SMS_CODE_ERROR(5604, "验证码错误"),
    SMS_CODE_LOCKED(5605, "验证码错误次数过多，请重新获取"),

    // 手机号相关 57xx
    PHONE_ALREADY_BOUND(5701, "该手机号已被其他用户绑定"),

    // 附件相关 58xx
    ATTACHMENT_TYPE_INVALID(5801, "文件类型不支持"),
    ATTACHMENT_SIZE_EXCEEDED(5802, "文件大小超过限制"),

    // 租户相关 59xx
    TENANT_NOT_EXISTS(5901, "租户不存在"),
    TENANT_DISABLED(5902, "租户已被禁用"),
    TENANT_EXPIRED(5903, "租户已过期"),

    // 用户状态相关
    USER_DEACTIVATED(5106, "账号已注销");

    private final Integer code;
    private final String message;
}
