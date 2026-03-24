package com.forge.admin.modules.system.dto.notice;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NoticeResponse {
    private Long id;
    private String noticeTitle;
    private Integer noticeType;
    private String noticeContent;
    private Integer status;
    private String createByName;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private String remark;
}
