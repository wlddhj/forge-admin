package com.forge.admin.modules.workflow.dto.candidate;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 候选人策略响应
 *
 * @author forge-admin
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CandidateStrategyResponse {

    /**
     * 策略代码
     */
    private Integer code;

    /**
     * 策略描述
     */
    private String description;
}