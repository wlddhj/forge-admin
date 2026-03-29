package com.forge.admin.modules.system.dto.dict;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典类型请求
 */
@Data
public class DictTypeRequest {
    private Long id;

    @NotBlank(message = "字典名称不能为空")
    private String dictName;

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    private Integer status = 1;
    private String remark;
}
