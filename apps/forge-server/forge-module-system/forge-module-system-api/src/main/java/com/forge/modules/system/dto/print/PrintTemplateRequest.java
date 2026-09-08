package com.forge.modules.system.dto.print;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 打印模板新增/修改请求
 */
@Data
public class PrintTemplateRequest {

    private Long id;

    @NotBlank(message = "模板编号不能为空")
    private String templateCode;

    @NotBlank(message = "模板名称不能为空")
    private String templateName;

    private String contents;
    private Integer version = 1;
    private Integer status = 1;
    private String remark;
}
