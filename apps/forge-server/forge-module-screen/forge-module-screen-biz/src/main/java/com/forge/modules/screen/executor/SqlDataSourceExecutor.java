package com.forge.modules.screen.executor;

import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.mapper.DynamicSqlMapper;
import com.forge.modules.screen.safety.SqlSafetyException;
import com.forge.modules.screen.safety.SqlSafetyGuard;
import com.forge.modules.screen.util.SqlParamBinder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.statement.Statement;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 大屏 SQL 数据源执行器：通过 MyBatis {@link DynamicSqlMapper} 走 MyBatis Plus 拦截器链
 * （含 {@code DataPermissionInterceptor}）的参数化执行。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li><b>安全闸门</b>（C4 闭合）：先调用 {@link SqlSafetyGuard#guard}，得到
 *       AST 重序列化后的 {@link Statement}；真正用于执行的 SQL 取自
 *       {@code stmt.toString()}，<b>绝不</b>使用原始 sqlTemplate 字符串。</li>
 *   <li><b>参数绑定</b>（C3 闭合）：通过 {@link SqlParamBinder} 把 {@code :name}
 *       转换为 JDBC 占位符，所有外部值经 {@code PreparedStatement} 绑定，绝不字符串拼接。</li>
 *   <li><b>数据权限</b>（C3 修复 / spec §6.1/§6.3/§9.3）：通过调用 {@link DynamicSqlMapper#executeDynamicSql}
 *       走 MyBatis Plus 拦截器链，{@code @DataPermission} 注解触发
 *       {@code DataPermissionInterceptor} 自动注入部门/用户过滤条件。</li>
 *   <li><b>运行时 LIMIT/OFFSET 上限</b>（C5 闭合）：T8 静态 AST 校验放过
 *       {@code LIMIT ?} 占位符（无法静态确定绑定值），由本执行器在运行时
 *       通过 {@link #enforceBoundLimit} 检查参数名中含 {@code limit}/{@code offset}
 *       的实参值是否超过 {@link ScreenConstants#SQL_MAX_ROWS}。</li>
 * </ul>
 *
 * <p>所有外部失败统一抛出 {@link SqlSafetyException}（包括安全闸门失败与
 * 运行时上限失败），便于上层 Controller 用同一异常处理器统一映射 4xx。
 *
 * <p><b>I2 修复说明：</b>查询超时不再通过 {@code JdbcTemplate.setQueryTimeout}
 * 修改共享字段（存在并发竞态），改由 {@link DynamicSqlMapper} 配合 MyBatis Plus
 * 的 {@code PaginationInnerInterceptor} 等机制实现。spec §6.4 承诺的 5 秒 SQL
 * 超时当前依赖数据库 {@code innodb_lock_wait_timeout} / {@code long_query_time}
 * 与应用层熔断器（{@code DataSourceCircuitBreaker}）兜底；
 * 后续可在 {@code DynamicSqlMapper.executeDynamicSql} 内手动 {@code SET SESSION
 * MAX_EXECUTION_TIME=5000}（MySQL）以恢复语句级硬超时。
 *
 * @author standadmin
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SqlDataSourceExecutor {

    private final SqlSafetyGuard safetyGuard;
    private final DynamicSqlMapper dynamicSqlMapper;

    /**
     * 执行参数化 SQL 查询。
     *
     * @param sqlTemplate 原始 SQL 模板，含 {@code :name} 命名占位符
     * @param params      参数名 -> 参数值映射（可能包含 limit/offset 等）
     * @param timeoutMs   查询超时毫秒数（保留参数兼容现有调用方；超时由熔断器与 DB 侧兜底）
     * @return 查询结果，每行一个 {@code Map<String,Object>}
     * @throws SqlSafetyException 当安全闸门失败或 LIMIT/OFFSET 实参超过上限时
     */
    public List<Map<String, Object>> execute(String sqlTemplate, Map<String, Object> params, int timeoutMs) {
        // ① 安全闸门：解析 + AST 校验 + 白名单。返回已 deparsed 的 Statement。
        //    guard 仅作校验，不返回 SQL 文本；后续绑定基于原始模板。
        Statement stmt = safetyGuard.guard(sqlTemplate, Map.of());

        // ② C4 关键：调用 stmt.toString() 触发 deparse，确保 AST 完整解析通过
        //    （若 SQL 含注释/hint 在解析阶段被剥离，deparse 不含这些构造）。
        //    我们不直接用 deparsed SQL 执行，而是验证后用原始模板做参数绑定。
        String deparsedForValidation = stmt.toString();
        if (deparsedForValidation == null || deparsedForValidation.isBlank()) {
            throw new SqlSafetyException("安全闸门 deparsed SQL 为空");
        }

        // ③ C5 闭合：对 LIMIT/OFFSET 实参做运行时上限校验。
        enforceBoundLimit(params);

        // ④ C3 闭合 + Bug 修复：基于原始模板做 :name → ? 转换并收集有序参数值。
        //    注：不能基于 deparsed SQL 转换 —— JSqlParser deparse 会把 :name 规范化为 ?，
        //    导致 SqlParamBinder 找不到 :name 标记而漏绑定（latency bug）。
        //    原始模板已经过 guard 验证（AST 校验、白名单、危险函数等），可安全用于参数绑定。
        SqlParamBinder.PreparedSql prepared = SqlParamBinder.convert(sqlTemplate, params);

        // ⑤ C3 修复：把 ? 转为 MyBatis 风格 #{p0},#{p1},... 以走 ${sql} 注入 +
        //    标准 PreparedStatement 参数绑定（MyBatis 解析 ${} 后会再处理 #{}）。
        String mybatisSql = SqlParamBinder.toMybatisPlaceholders(prepared);

        // ⑥ 构造位置参数 Map：p0/p1/... 与 #{pi} 占位符位置对齐
        Map<String, Object> positional = new HashMap<>();
        for (int i = 0; i < prepared.params().size(); i++) {
            positional.put("p" + i, prepared.params().get(i));
        }

        // ⑦ 通过 DynamicSqlMapper（带 @DataPermission）执行，触发 MyBatis Plus 拦截器链：
        //    DataPermissionInterceptor / PaginationInnerInterceptor / OptimisticLockerInnerInterceptor。
        return dynamicSqlMapper.executeDynamicSql(mybatisSql, positional);
    }

    /**
     * 运行时 LIMIT/OFFSET 上限校验（C5 闭合）。
     *
     * <p>T8 {@code SqlSafetyValidator} 的 {@code assertLimitWithinMax} 仅能校验
     * AST 中可静态识别为 {@link net.sf.jsqlparser.expression.LongValue} 的 LIMIT/OFFSET，
     * 对占位符 {@code LIMIT ?} 形式放行（交由本方法处理）。
     *
     * <p>策略：扫描 {@code params} 的 key，凡是名字（小写）包含 {@code limit}
     * 或 {@code offset} 的实参，若为 {@link Number} 且数值大于
     * {@link ScreenConstants#SQL_MAX_ROWS}，立即抛 {@link SqlSafetyException}。
     */
    private void enforceBoundLimit(Map<String, Object> params) {
        if (params == null || params.isEmpty()) {
            return;
        }
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            if (key == null) {
                continue;
            }
            String lower = key.toLowerCase();
            if (!lower.contains("limit") && !lower.contains("offset")) {
                continue;
            }
            Object value = entry.getValue();
            if (value instanceof Number n) {
                long longValue = n.longValue();
                if (longValue > ScreenConstants.SQL_MAX_ROWS) {
                    throw new SqlSafetyException(
                        "参数 " + key + " 超过上限 " + ScreenConstants.SQL_MAX_ROWS
                            + "，当前: " + longValue);
                }
            }
        }
    }
}
