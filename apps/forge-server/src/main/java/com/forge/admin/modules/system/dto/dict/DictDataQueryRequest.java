package com.forge.admin.modules.system.dto.dict;

import lombok.Data;

/**
 * 字典数据查询请求
 */
@Data
public class DictDataQueryRequest {
    private String dictType;
    private String dictLabel;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
