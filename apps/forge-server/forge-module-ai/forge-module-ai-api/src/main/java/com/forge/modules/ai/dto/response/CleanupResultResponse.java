package com.forge.modules.ai.dto.response;

import lombok.Data;

/**
 * Agent 运行数据清理结果统计
 */
@Data
public class CleanupResultResponse {

    private long runDeleted;

    private long stepDeleted;

    private long todoDeleted;

    private long checkpointDeleted;

    private long durationMs;
}
