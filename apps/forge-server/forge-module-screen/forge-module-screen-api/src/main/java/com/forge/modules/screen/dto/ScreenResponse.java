package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 大屏响应
 *
 * @author standadmin
 */
@Data
@Schema(description = "大屏响应")
public class ScreenResponse {

    private Long id;

    private String code;

    private String name;

    private String description;

    private String config;

    private String configDraft;

    private String theme;

    private Integer status;

    private Integer isPublic;

    private Integer accessType;

    /** 已授权角色 ID 列表（accessType=1 时使用） */
    private List<Long> roleIds;

    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private Long createBy;

    private String remark;
}
