package com.forge.modules.system.dto.keysequence;

import lombok.Data;

@Data
public class KeySequenceQueryRequest {

    private String keyCategory;

    private Integer pageNum = 1;

    private Integer pageSize = 10;
}
