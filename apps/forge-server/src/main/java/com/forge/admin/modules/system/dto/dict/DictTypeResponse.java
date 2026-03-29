package com.forge.admin.modules.system.dto.dict;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典类型响应
 */
@Data
public class DictTypeResponse {
    private Long id;
    private String dictName;
    private String dictType;
    private Integer status;
    private Integer isSystem;
    private String remark;
    private LocalDateTime createTime;
}
