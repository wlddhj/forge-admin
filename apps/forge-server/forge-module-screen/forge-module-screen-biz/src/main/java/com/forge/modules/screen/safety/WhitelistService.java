package com.forge.modules.screen.safety;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forge.modules.screen.entity.SysScreenSqlWhitelist;
import com.forge.modules.screen.mapper.SysScreenSqlWhitelistMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * SQL 白名单查询与列级控制服务。
 *
 * <p>负责：
 * <ul>
 *   <li>{@link #checkTableAllowed(String, String)}：根据 schema/table 查询白名单条目；</li>
 *   <li>{@link #checkColumnsAllowed(String, String, Set)}：校验请求列是否全部命中白名单，
 *       若有禁用列则抛出 {@link SqlSafetyException}。</li>
 * </ul>
 *
 * <p>列名比较大小写不敏感（双方统一小写）。{@code column_list} 为空或 null 表示
 * <b>fail-closed</b>（拒绝所有请求列，I6 修复），强制管理员显式声明允许的列；
 * JSON 解析失败同样按"无列"处理（拒绝所有请求列）。
 *
 * @author standadmin
 */
@Service
@RequiredArgsConstructor
public class WhitelistService {

    private final SysScreenSqlWhitelistMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 查询表是否在白名单中。
     *
     * @param schema Schema 名称
     * @param table  表名
     * @return 启用的白名单条目；不存在或未启用时返回 {@code null}
     */
    public SysScreenSqlWhitelist checkTableAllowed(String schema, String table) {
        return mapper.findByTable(schema, table);
    }

    /**
     * 校验请求列是否全部允许。
     *
     * @param schema    Schema 名称
     * @param table     表名
     * @param requested 请求查询的列集合
     * @throws SqlSafetyException 表不在白名单；列白名单为空（fail-closed，I6 修复）；
     *                             或任一请求列不在白名单中
     */
    public void checkColumnsAllowed(String schema, String table, Set<String> requested) {
        SysScreenSqlWhitelist wl = checkTableAllowed(schema, table);
        if (wl == null) {
            throw new SqlSafetyException("表不在白名单: " + schema + "." + table);
        }
        // I6 修复（fail-closed）：column_list 为空不再"默认放行全部列"，
        // 而是拒绝所有请求列，强制管理员显式声明允许的列。spec §6.3 第 2 条
        // "等保二级要求最小授权"要求"列级控制必须显式"。
        if (wl.getColumnList() == null || wl.getColumnList().isBlank()) {
            throw new SqlSafetyException(
                "白名单条目未声明 column_list（fail-closed 拒绝全部请求列）: "
                    + schema + "." + table);
        }

        Set<String> allowed = parseColumns(wl.getColumnList());
        for (String col : requested) {
            if (!allowed.contains(col.toLowerCase())) {
                throw new SqlSafetyException(
                    "列不在白名单: " + schema + "." + table + "." + col);
            }
        }
    }

    /**
     * 解析 {@code column_list} JSON 数组为小写列名集合。
     * 解析失败返回空集（保守拒绝）。
     */
    private Set<String> parseColumns(String json) {
        try {
            List<String> list = objectMapper.readValue(json, new TypeReference<List<String>>() {});
            return list.stream().map(String::toLowerCase).collect(Collectors.toSet());
        } catch (Exception e) {
            // JSON 异常时按"无允许列"处理，使后续 check 全部拒绝
            return Collections.emptySet();
        }
    }
}
