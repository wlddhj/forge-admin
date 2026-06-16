package com.forge.modules.system.dto.app;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * App用户查询请求（后台管理用）
 *
 * @author forge
 */
@Data
@Schema(description = "App用户查询请求（后台管理用）")
public class AppUserQueryRequest {
    /**
     * 当前页
     */
    @Schema(description = "当前页", defaultValue = "1")
    private Long pageNum = 1L;

    /**
     * 每页大小
     */
    @Schema(description = "每页大小", defaultValue = "10")
    private Long pageSize = 10L;

    /**
     * 昵称（模糊查询）
     */
    @Schema(description = "昵称（模糊查询）")
    private String nickname;

    /**
     * 手机号（模糊查询）
     */
    @Schema(description = "手机号（模糊查询）")
    private String phone;

    /**
     * 微信OpenID（模糊查询）
     */
    @Schema(description = "微信OpenID（模糊查询）")
    private String openId;

    /**
     * 用户状态
     */
    @Schema(description = "用户状态")
    private Integer status;

    /**
     * 创建时间起始
     */
    @Schema(description = "创建时间起始")
    private LocalDateTime createTimeStart;

    /**
     * 创建时间截止
     */
    @Schema(description = "创建时间截止")
    private LocalDateTime createTimeEnd;
}