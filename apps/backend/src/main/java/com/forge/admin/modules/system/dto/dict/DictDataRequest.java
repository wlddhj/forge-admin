package com.forge.admin.modules.system.dto.dict;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典数据请求
 */
@Data
public class DictDataRequest {
    private Long id;

    @NotBlank(message = "字典类型不能为空")
    private String dictType;

    @NotBlank(message = "字典标签不能为空")
    private String dictLabel;

    @NotBlank(message = "字典值不能为空")
    private String dictValue;

    private Integer dictSort = 0;
    private String cssClass;
    private String listClass;
    private Integer status = 1;
    private String remark;
}
