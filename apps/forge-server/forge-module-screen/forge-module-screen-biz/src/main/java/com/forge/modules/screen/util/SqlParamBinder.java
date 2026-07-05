package com.forge.modules.screen.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * SQL 命名参数绑定器：把 {@code :name} 形式的命名占位符转换为 JDBC 的 {@code ?}
 * 占位符，并按 SQL 中出现的顺序返回参数值列表。
 *
 * <p>这是 T11 {@code SqlDataSourceExecutor} 的核心组件，确保所有外部值都通过
 * {@code PreparedStatement} 的参数绑定机制传入，绝不进行字符串拼接（C3 防御）。
 *
 * <h3>设计约束</h3>
 * <ul>
 *   <li>仅识别 {@code :name} 形式（{@code [a-zA-Z_][a-zA-Z0-9_]*}），不识别
 *       JDBC 原生 {@code ?} 或 MyBatis 风格 {@code #{name}}；后者由调用方在使用前
 *       自行转换为 {@code :name}。</li>
 *   <li>遇到未提供的命名参数立即抛出 {@link IllegalArgumentException}（fail-closed），
 *       避免 JDBC 层因参数不足抛出更模糊的错误。</li>
 *   <li>重复出现的同名参数按出现顺序多次写入参数列表，与 JDBC 的位置绑定语义一致。</li>
 * </ul>
 *
 * <p>非线程安全（静态方法不可变，但传入的 Map 与返回的 List 由调用方负责）。
 *
 * @author standadmin
 */
public final class SqlParamBinder {

    /**
     * 命名参数正则：以 {@code :} 开头，后接标识符。
     * 不捕获引号内的 {@code :xxx}（例如字符串字面量），但当前实现为简化版本，
     * 调用方应在传入前已通过 AST 校验，正常白名单 SQL 不会在字面量中包含
     * 形似参数的字符串。
     */
    private static final Pattern NAMED_PARAM = Pattern.compile(":([a-zA-Z_][a-zA-Z0-9_]*)");

    private SqlParamBinder() {
    }

    /**
     * 把 {@code :name} 占位符转换为 {@code ?}，并按出现顺序返回参数值列表。
     *
     * @param sqlTemplate 含 {@code :name} 占位符的 SQL 模板
     * @param params      参数名 -> 参数值映射（可为 null，等同空映射）
     * @return 转换后的 SQL 与有序参数值列表
     * @throws IllegalArgumentException 当 SQL 引用了未提供的参数时
     */
    public static PreparedSql convert(String sqlTemplate, Map<String, Object> params) {
        if (sqlTemplate == null) {
            throw new IllegalArgumentException("sqlTemplate 不能为空");
        }
        Map<String, Object> safe = params == null ? Map.of() : params;
        Matcher m = NAMED_PARAM.matcher(sqlTemplate);
        StringBuilder sb = new StringBuilder();
        List<Object> ordered = new ArrayList<>();
        while (m.find()) {
            String name = m.group(1);
            if (!safe.containsKey(name)) {
                throw new IllegalArgumentException("缺少参数: " + name);
            }
            // appendReplacement 把 \ 与 $ 当作特殊字符；参数名只允许字母数字下划线，
            // 替换字符串固定为 "?" 不会触发反向引用解析。
            m.appendReplacement(sb, "?");
            ordered.add(safe.get(name));
        }
        m.appendTail(sb);
        return new PreparedSql(sb.toString(), ordered);
    }

    /**
     * 已转换的 SQL 与对应参数值列表。
     *
     * @param sql    仅含 {@code ?} 占位符的 SQL
     * @param params 与 {@code ?} 出现顺序对齐的参数值列表
     */
    public record PreparedSql(String sql, List<Object> params) {
    }
}
