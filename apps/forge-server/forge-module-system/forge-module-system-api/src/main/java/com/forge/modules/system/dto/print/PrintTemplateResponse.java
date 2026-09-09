package com.forge.modules.system.dto.print;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 打印模板响应
 */
@Data
public class PrintTemplateResponse {
    private Long id;
    private String templateCode;
    private String templateName;
    private String contents;
    private Integer version;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
