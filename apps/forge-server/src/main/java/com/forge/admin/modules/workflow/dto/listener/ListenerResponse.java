package com.forge.admin.modules.workflow.dto.listener;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ListenerResponse {

    private Long id;
    private String name;
    private Integer status;
    private String type;
    private String event;
    private String valueType;
    private String value;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
