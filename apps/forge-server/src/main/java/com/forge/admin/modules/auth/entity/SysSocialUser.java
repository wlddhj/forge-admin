package com.forge.admin.modules.auth.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 社交账号绑定实体
 */
@Data
@TableName("sys_social_user")
public class SysSocialUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 系统用户ID */
    private Long userId;

    /** 平台标识: wechat/dingtalk */
    private String source;

    /** 第三方open_id */
    private String openId;

    /** union_id(微信多应用绑定) */
    private String unionId;

    /** 加密存储的access_token */
    private String accessToken;

    /** 加密存储的refresh_token */
    private String refreshToken;

    /** token过期时间 */
    private LocalDateTime tokenExpireTime;

    /** 第三方昵称 */
    private String nickname;

    /** 第三方头像URL */
    private String avatar;

    /** 原始用户信息JSON */
    private String rawUserInfo;

    /** 状态(0:禁用 1:启用) */
    private Integer status;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    /** 创建者 */
    private String createBy;

    /** 更新者 */
    private String updateBy;

    /** 逻辑删除 */
    @TableLogic
    private Integer deleted;

    /** 备注 */
    private String remark;
}
