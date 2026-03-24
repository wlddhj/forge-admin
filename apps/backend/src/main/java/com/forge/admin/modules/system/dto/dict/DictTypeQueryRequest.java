package com.forge.admin.modules.system.dto.dict;

import lombok.Data;

/**
 * 字典类型查询请求
 */
@Data
public class DictTypeQueryRequest {
    private String dictName;
    private String dictType;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
