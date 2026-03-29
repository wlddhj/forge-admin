package com.forge.admin.modules.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通知公告实体
 */
@Data
@TableName("sys_notice")
public class SysNotice {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 公告标题 */
    private String noticeTitle;

    /** 公告类型(1:通知 2:公告) */
    private Integer noticeType;

    /** 公告内容 */
    private String noticeContent;

    /** 状态(0:关闭 1:正常) */
    private Integer status;

    /** 创建者ID */
    private Long createBy;

    /** 创建时间 */
    private LocalDateTime createTime;

    /** 更新时间 */
    private LocalDateTime updateTime;

    /** 备注 */
    private String remark;
}
