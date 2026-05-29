package com.forge.modules.system.dto.notice;

import lombok.Data;

@Data
public class NoticeQueryRequest {
    private String noticeTitle;
    private Integer noticeType;
    private Integer status;
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
