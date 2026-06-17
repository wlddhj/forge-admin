package com.forge.modules.ai.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新标题请求
 */
@Data
public class UpdateTitleRequest {

    @NotBlank(message = "标题不能为空")
    private String title;
}