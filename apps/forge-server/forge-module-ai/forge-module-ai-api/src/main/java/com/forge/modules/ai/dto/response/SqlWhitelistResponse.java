package com.forge.modules.ai.dto.response;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * SQL 白名单响应
 */
@Data
public class SqlWhitelistResponse {

    private Long id;

    private String tableName;

    private String tableComment;

    private Integer enabled;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
