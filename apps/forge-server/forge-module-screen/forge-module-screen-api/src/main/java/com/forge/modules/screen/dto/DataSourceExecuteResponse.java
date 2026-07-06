package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 数据源执行响应
 *
 * @author standadmin
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "数据源执行响应")
public class DataSourceExecuteResponse {

    @Schema(description = "执行结果数据")
    private Object data;

    @Schema(description = "是否来自缓存")
    private boolean fromCache;

    @Schema(description = "执行时间")
    private LocalDateTime executedAt;
}
