package com.forge.modules.screen.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.util.Map;

/**
 * 数据源执行请求
 *
 * @author standadmin
 */
@Data
@Schema(description = "数据源执行请求")
public class DataSourceExecuteRequest {

    @Schema(description = "参数映射（SQL 占位符 / HTTP query）")
    private Map<String, Object> params;
}
