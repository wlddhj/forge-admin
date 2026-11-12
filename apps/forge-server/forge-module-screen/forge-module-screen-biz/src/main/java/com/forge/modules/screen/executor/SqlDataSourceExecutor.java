package com.forge.modules.screen.executor;

import com.forge.modules.screen.constant.ScreenConstants;
import com.forge.modules.screen.mapper.DynamicSqlMapper;
import com.forge.modules.screen.safety.GuardResult;
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
import java.util.Set;

/**
 * 大屏 SQL 数据源执行器：通过 MyBatis {@link DynamicSqlMapper} 走 MyBatis Plus 拦截器链
 * （含 {@code DataPermissionInterceptor}）的参数化执行。
 *
 * <h3>职责</h3>
 * <ul>
 *   <li><b>安全闸门</b>（C4 闭合）：先调用 {@link SqlSafetyGuard#guard}，得到
 *       {@link GuardResult}（注释硬拒 + AST 校验 + 全树白名单已在此完成）。
 *       {@code statement.toString()} 仅用于验证 deparse 完整性；<b>真正执行的 SQL
 *       基于原始模板生成</b>（{@code :name} → {@code #{pi}}），避免 JSqlParser
 *       deparse 把 {@code :name} 规范化为 {@code ?} 导致漏绑定。</li>
 *   <li><b>参数绑定</b>（C3 闭合）：通过 {@link SqlParamBinder} 把 {@code :name}
 *       转换为 JDBC 占位符，所有外部值经 {@code PreparedStatement} 绑定，绝不字符串拼接。</li>
 *   <li><b>数据权限</b>（C3 修复 / spec §6.1/§6.3/§9.3）：通过调用 {@link DynamicSqlMapper#executeDynamicSql}
 *       走 MyBatis Plus 拦截器链，{@code @DataPermission} 注解触发
 *       {@code DataPermissionInterceptor} 自动注入部门/用户过滤条件。</li>
 *   <li><b>运行时 LIMIT/OFFSET 上限</b>（C5 闭合）：静态闸放行的占位符形式
 *       {@code LIMIT :n} 由本执行器按 {@link GuardResult#limitParamNames()} 精确匹配
 *       实参并校验不超过 {@link ScreenConstants#SQL_MAX_ROWS}；字面量 LIMIT/OFFSET
 *       已在静态闸（SqlSafetyValidator）校验。</li>
 *   <li><b>结果行数硬上限兜底</b>：无论 SQL 形态如何，返回行数统一截断到
 *       {@link ScreenConstants#SQL_MAX_ROWS}（setMaxRows 等价兜底，见
 *       {@link #capToMaxRows}）。</li>
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
     * @return 查询结果，每行一个 {@code Map<String,Object>}，行数不超过 {@link ScreenConstants#SQL_MAX_ROWS}
     * @throws SqlSafetyException 当安全闸门失败或 LIMIT/OFFSET 实参超过上限时
     */
    public List<Map<String, Object>> execute(String sqlTemplate, Map<String, Object> params, int timeoutMs) {
        // ① 安全闸门（闸A/B/C）：注释硬拒 + 解析 + AST 校验 + 全树白名单。
        //    返回 GuardResult：statement 用于 deparse 验证；limitParamNames 用于运行时限额校验。
        GuardResult guardResult = safetyGuard.guard(sqlTemplate, Map.of());
        Statement stmt = guardResult.statement();

        // ② C4 关键：调用 stmt.toString() 触发 deparse，确保 AST 完整解析通过
        //    （若 SQL 含注释/hint 在解析阶段被剥离，deparse 不含这些构造）。
        //    我们不直接用 deparsed SQL 执行，而是验证后用原始模板做参数绑定。
        String deparsedForValidation = stmt.toString();
        if (deparsedForValidation == null || deparsedForValidation.isBlank()) {
            throw new SqlSafetyException("安全闸门 deparsed SQL 为空");
        }

        // ③ C5 闭合：对 limitParamNames 精确匹配的 LIMIT/OFFSET 实参做运行时上限校验。
        enforceBoundLimit(params, guardResult.limitParamNames());

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
        //    执行结果统一按 SQL_MAX_ROWS 截断兜底，保证任何返回行数不超硬上限。
        return capToMaxRows(dynamicSqlMapper.executeDynamicSql(mybatisSql, positional));
    }

    /**
     * 运行时 LIMIT/OFFSET 上限校验（C5 闭合，闸C 配套）。
     *
     * <p>T8 {@code SqlSafetyValidator} 的 {@code assertLimitWithinMax} 仅能校验
     * AST 中可静态识别为 {@link net.sf.jsqlparser.expression.LongValue} 的 LIMIT/OFFSET，
     * 占位符形式（{@code LIMIT ?} / {@code LIMIT :n}）放行，交由本方法处理。
     *
     * <p>策略（精确匹配）：只校验 {@link GuardResult#limitParamNames()} 中登记的
     * 参数名（即真正出现在 LIMIT/OFFSET 子句中的命名占位符）。仅"名字含
     * limit/offset 字样"但未出现在 LIMIT 子句的参数不受影响（旧版宽松包含匹配
     * 既会误伤同名业务参数，也会漏掉任意命名的 LIMIT 参数）。
     *
     * @param params          实参映射
     * @param limitParamNames guard 从 AST 收集的 LIMIT/OFFSET 绑定参数名
     * @throws SqlSafetyException 任一登记参数为数值且大于 {@link ScreenConstants#SQL_MAX_ROWS}
     */
    private void enforceBoundLimit(Map<String, Object> params, Set<String> limitParamNames) {
        if (params == null || params.isEmpty() || limitParamNames.isEmpty()) {
            return;
        }
        for (String name : limitParamNames) {
            // 模板引用了未提供的参数由 SqlParamBinder fail-closed 拦截，此处不重复处理
            if (!params.containsKey(name)) {
                continue;
            }
            if (params.get(name) instanceof Number n && n.longValue() > ScreenConstants.SQL_MAX_ROWS) {
                throw new SqlSafetyException(
                    "参数 " + name + " 超过上限 " + ScreenConstants.SQL_MAX_ROWS
                        + "，当前: " + n.longValue());
            }
        }
    }

    /**
     * 结果行数硬上限兜底（验收 §8：任何执行结果行数 ≤ SQL_MAX_ROWS）。
     *
     * <p>本执行器经 MyBatis Mapper 执行，拿不到 {@code java.sql.Statement} 句柄，
     * 无法调用 {@code statement.setMaxRows(SQL_MAX_ROWS)}；以结果截断实现同等
     * 防护语义——即使静态/运行时 LIMIT 校验均被绕过（如子查询形态），返回给
     * 调用方的数据量也被钳制在硬上限内。
     */
    private List<Map<String, Object>> capToMaxRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.size() <= ScreenConstants.SQL_MAX_ROWS) {
            return rows;
        }
        return rows.subList(0, ScreenConstants.SQL_MAX_ROWS);
    }
}
