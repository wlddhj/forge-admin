package com.forge.admin.modules.system.dto.dict;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 字典数据响应
 */
@Data
public class DictDataResponse {
    private Long id;
    private String dictType;
    private String dictLabel;
    private String dictValue;
    private Integer dictSort;
    private String cssClass;
    private String listClass;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
}
