package com.forge.modules.system.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

@Data
@TableName("sys_key_sequence")
@Accessors(chain = true)
public class KeySequence {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String keyCategory;

    private String keyPrefix;

    private String dateRule;

    private Long maxValue;

    private Long seqLength;

    private String lastDateVal;

    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    @TableLogic
    private Integer deleted;
}
