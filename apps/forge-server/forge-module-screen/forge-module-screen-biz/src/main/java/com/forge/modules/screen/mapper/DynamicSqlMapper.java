package com.forge.modules.screen.mapper;

import com.forge.framework.mybatis.annotation.DataPermission;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 动态 SQL 执行 Mapper（C3 修复）。
 *
 * <p>大屏数据源允许用户配置自定义 SQL，必须走 MyBatis Plus 拦截器链
 * （{@code DataPermissionInterceptor} 等）以满足 spec §6.1/§6.3/§9.3 数据范围控制要求。
 * 该 Mapper 的 {@link #executeDynamicSql} 方法标注了 {@link DataPermission}，
 * 通过 {@code ${sql}} 注入已经过 {@code SqlSafetyGuard.guard()} deparse 与白名单校验的 SQL，
 * 其中 {@code ?} 占位符已由 {@code SqlParamBinder.toMybatisPlaceholders} 转换为
 * {@code #{p0}, #{p1}, ...}，由 MyBatis 标准参数绑定机制走 {@code PreparedStatement}。
 *
 * <h3>安全保证</h3>
 * <ul>
 *   <li>{@code ${sql}} 中的 SQL 文本已经过 AST deparse，注释/hint 已被 JSqlParser 剥离；</li>
 *   <li>所有外部值经 {@code #{pi}} 占位符绑定，绝不字符串拼接；</li>
 *   <li>{@code @DataPermission} 注解使 {@code DataPermissionInterceptor} 自动注入部门/用户过滤条件。</li>
 * </ul>
 *
 * @author standadmin
 */
@Mapper
public interface DynamicSqlMapper {

    /**
     * 执行经过安全闸门 deparse 后的 SQL。
     *
     * @param sql    含 {@code #{pi}} 占位符的 SQL 文本（已通过 {@code SqlSafetyGuard.guard()}）
     * @param params 按位置命名的参数 {@code {p0: v0, p1: v1, ...}}
     * @return 查询结果，每行一个 {@code LinkedHashMap}（保留列顺序）
     */
    @DataPermission(deptAlias = "d", userAlias = "u")
    List<Map<String, Object>> executeDynamicSql(@Param("sql") String sql,
                                                @Param("params") Map<String, Object> params);
}
