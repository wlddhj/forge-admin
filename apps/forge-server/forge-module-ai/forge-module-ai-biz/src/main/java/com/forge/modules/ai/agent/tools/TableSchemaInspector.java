package com.forge.modules.ai.agent.tools;

import com.forge.modules.ai.entity.AiSqlWhitelist;
import com.forge.modules.ai.service.AiSqlWhitelistService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据库表清单探查：展示 SQL 白名单内启用的表（含注释），为 Agent 决策提示词提供真实表名。
 * 表清单与 sql_query 可查范围保持一致，避免模型臆查非白名单表。
 */
@Component
@RequiredArgsConstructor
public class TableSchemaInspector {

    private final AiSqlWhitelistService whitelistService;

    public String describeTables() {
        List<AiSqlWhitelist> tables = whitelistService.getEnabledTables();
        if (tables.isEmpty()) {
            return "（暂无可用表，SQL 白名单为空）";
        }
        StringBuilder sb = new StringBuilder();
        for (AiSqlWhitelist table : tables) {
            sb.append("- ").append(table.getTableName());
            if (table.getTableComment() != null && !table.getTableComment().isBlank()) {
                sb.append("（").append(table.getTableComment()).append("）");
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
