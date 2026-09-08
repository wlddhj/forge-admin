package com.forge.modules.system.dto.print;

import lombok.Data;

/**
 * 打印模板查询请求
 */
@Data
public class PrintTemplateQueryRequest {
    private String templateCode;
    private String templateName;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
