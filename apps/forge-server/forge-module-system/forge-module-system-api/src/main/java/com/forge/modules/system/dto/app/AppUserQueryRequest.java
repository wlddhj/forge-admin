package com.forge.modules.system.dto.app;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * App用户查询请求（后台管理用）
 *
 * @author forge
 */
@Data
public class AppUserQueryRequest {
    /**
     * 当前页
     */
    private Long pageNum = 1L;

    /**
     * 每页大小
     */
    private Long pageSize = 10L;

    /**
     * 昵称（模糊查询）
     */
    private String nickname;

    /**
     * 手机号（模糊查询）
     */
    private String phone;

    /**
     * 微信OpenID（模糊查询）
     */
    private String openId;

    /**
     * 用户状态
     */
    private Integer status;

    /**
     * 创建时间起始
     */
    private LocalDateTime createTimeStart;

    /**
     * 创建时间截止
     */
    private LocalDateTime createTimeEnd;
}