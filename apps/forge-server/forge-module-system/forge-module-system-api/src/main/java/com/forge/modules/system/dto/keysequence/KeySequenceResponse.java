package com.forge.modules.system.dto.keysequence;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KeySequenceResponse {

    private Long id;

    private String keyCategory;

    private String keyPrefix;

    private String dateRule;

    private Long maxValue;

    private Long seqLength;

    private String lastDateVal;

    private String remark;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
