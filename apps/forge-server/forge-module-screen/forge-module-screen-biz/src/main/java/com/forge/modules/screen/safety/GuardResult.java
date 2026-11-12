package com.forge.modules.screen.safety;

import net.sf.jsqlparser.statement.Statement;

import java.util.Set;

/**
 * SQL 安全闸门返回结果（闸C）。
 *
 * <p>承载两部分信息：
 * <ul>
 *   <li>{@link #statement()}：解析后的 AST。T11 执行层调用 {@code statement.toString()}
 *       验证 deparse 完整性；参数绑定仍基于原始模板（见 SqlDataSourceExecutor 说明）。</li>
 *   <li>{@link #limitParamNames()}：LIMIT/OFFSET 子句中以命名占位符（{@code :name}）
 *       形式出现的绑定参数名集合。静态闸无法确定其实参值，由 T11 运行时
 *       按该集合精确匹配实参并校验上限。</li>
 * </ul>
 *
 * @author standadmin
 */
public record GuardResult(Statement statement, Set<String> limitParamNames) {
}
