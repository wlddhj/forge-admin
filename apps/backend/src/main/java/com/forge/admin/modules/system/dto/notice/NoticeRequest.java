package com.forge.admin.modules.system.dto.notice;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class NoticeRequest {
    private Long id;

    @NotBlank(message = "公告标题不能为空")
    private String noticeTitle;

    @NotNull(message = "公告类型不能为空")
    private Integer noticeType;

    @NotBlank(message = "公告内容不能为空")
    private String noticeContent;

    private Integer status = 1;

    private String remark;
}
