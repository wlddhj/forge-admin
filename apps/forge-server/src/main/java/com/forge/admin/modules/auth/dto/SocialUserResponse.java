package com.forge.admin.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 社交账号绑定信息响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SocialUserResponse {

    /** 绑定记录ID */
    private Long id;

    /** 平台标识 */
    private String source;

    /** 平台显示名称 */
    private String sourceName;

    /** 第三方昵称 */
    private String nickname;

    /** 第三方头像 */
    private String avatar;

    /** 绑定时间 */
    private String bindTime;
}
