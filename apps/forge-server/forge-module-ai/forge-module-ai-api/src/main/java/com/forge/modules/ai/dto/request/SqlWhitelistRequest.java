package com.forge.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * SQL 白名单新增/修改请求（新增时 id 为空，修改时必传）
 */
@Data
public class SqlWhitelistRequest {

    private Long id;

    @NotBlank(message = "表名不能为空")
    @Size(max = 64, message = "表名长度不能超过 64")
    @Pattern(regexp = "^[a-z][a-z0-9_]*$", message = "表名须为小写字母开头的下划线格式")
    private String tableName;

    @Size(max = 255, message = "表注释长度不能超过 255")
    private String tableComment;

    @NotNull(message = "状态不能为空")
    private Integer enabled;

    @Size(max = 255, message = "备注长度不能超过 255")
    private String remark;
}
