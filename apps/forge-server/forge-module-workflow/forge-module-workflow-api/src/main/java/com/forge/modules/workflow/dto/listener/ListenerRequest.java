package com.forge.modules.workflow.dto.listener;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ListenerRequest {

    private Long id;

    @NotBlank(message = "监听器名称不能为空")
    private String name;

    private Integer status;

    @NotBlank(message = "监听类型不能为空")
    private String type;

    @NotBlank(message = "监听事件不能为空")
    private String event;

    @NotBlank(message = "值类型不能为空")
    private String valueType;

    @NotBlank(message = "监听器值不能为空")
    private String value;

    private String remark;
}
