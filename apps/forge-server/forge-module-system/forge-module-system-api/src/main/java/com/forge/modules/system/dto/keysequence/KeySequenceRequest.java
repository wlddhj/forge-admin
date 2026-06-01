package com.forge.modules.system.dto.keysequence;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class KeySequenceRequest {

    private Long id;

    @NotBlank(message = "分类编码不能为空")
    private String keyCategory;

    private String keyPrefix;

    private String dateRule;

    @NotNull(message = "顺序号位数不能为空")
    private Long seqLength;

    private String remark;
}
