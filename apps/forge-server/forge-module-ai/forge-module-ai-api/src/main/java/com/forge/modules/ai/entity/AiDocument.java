package com.forge.modules.ai.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 文档实体
 */
@Data
@TableName("ai_document")
public class AiDocument {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long attachmentId;  // 新增：关联附件ID

    private String fileName;    // 保留：文件名用于显示

    private String content;

    private String summary;

    private String modelName;

    private Integer status;

    private String errorMessage;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}