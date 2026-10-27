# 数据权限功能实现指南

## 概述

本文档记录了 forge-admin 系统数据权限 SQL 拦截功能的实现、测试和应用指南。

数据权限功能通过 MyBatis-Plus 拦截器实现，在 SQL 执行前自动添加数据权限过滤条件，确保用户只能查看其权限范围内的数据。

> ⚠️ **重要说明**：数据权限**默认关闭**，只有使用 `@DataPermission` 注解标注的方法才会启用数据权限过滤。

## 默认行为

| 场景 | 行为 |
|------|------|
| **没有 @DataPermission 注解** | 不进行任何数据权限过滤，返回所有数据 |
| **使用 @DataPermission 注解** | 启用数据权限过滤，根据用户权限范围过滤数据 |

## 架构升级 v2.0

### 核心改进

| 改进项 | 旧版本 | 新版本 v2.0 |
|-------|--------|------------|
| 返回类型 | String（SQL 字符串） | Expression（JSQLParser 对象） |
| 标准接口 | 自定义 InnerInterceptor | MyBatis-Plus `MultiDataPermissionHandler` |
| 规则管理 | 无 | 规则工厂（支持 include/exclude） |
| 上下文管理 | 无 | ThreadLocal 栈（支持嵌套） |
| AOP 支持 | 无 | `@DataPermission` 注解拦截 |
| 工具支持 | 无 | `MyBatisUtils` 表达式构建 |

**参考设计**: [shi9-boot](https://github.com/zhijiantianya/ruoyi-vue-pro) 数据权限实现

## 功能说明

### 支持的数据权限范围

| 范围代码 | 范围名称 | 说明 |
|---------|---------|------|
| 1 | 全部数据权限 | 查看所有数据（超级管理员） |
| 2 | 自定义数据权限 | 查看指定部门的数据 |
| 3 | 本部门数据权限 | 只能查看本部门的数据 |
| 4 | 本部门及以下数据权限 | 查看本部门及子部门的数据 |
| 5 | 仅本人数据权限 | 只能查看自己的数据 |

## 技术实现

### 核心组件

#### 1. 数据权限规则处理器（新架构核心）⭐

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DataPermissionRuleHandler.java`

实现 MyBatis-Plus 的 `MultiDataPermissionHandler` 接口，直接处理 Expression 对象：

```java
@Slf4j
@Component
public class DataPermissionRuleHandler implements MultiDataPermissionHandler {

    private final DataPermissionRuleFactory ruleFactory;

    @Override
    public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
        // ⚠️ 重要：默认关闭数据权限，只有使用 @DataPermission 注解时才启用
        var context = DataPermissionContextHolder.peek();
        if (context == null) {
            log.debug("[数据权限规则处理器] 未使用 @DataPermission 注解，跳过数据权限过滤");
            return null;
        }

        // 获取当前生效的规则（支持 include/exclude 过滤）
        var rules = ruleFactory.getEnabledRules(mappedStatementId);
        if (CollUtil.isEmpty(rules)) {
            return null;
        }

        String tableName = table.getName();
        Alias tableAlias = table.getAlias();

        Expression allExpression = null;

        for (var rule : rules) {
            if (!rule.getTableNames().contains(tableName)) {
                continue;
            }

            // ⚠️ 关键：直接传入 Alias 对象，而不是 String
            Expression oneExpression = rule.getExpression(tableName, tableAlias);
            if (oneExpression == null) {
                continue;
            }

            // 使用 AND 连接多个规则的表达式
            allExpression = allExpression == null ? oneExpression
                    : new AndExpression(allExpression, oneExpression);
        }

        // ⚠️ 关键修复：只返回数据权限条件，不与原 where 组合
        // MyBatis-Plus 会自动将返回值与原 where 条件组合
        return allExpression;
    }
}
```

**重要说明**：
- `getSqlSegment()` **只返回数据权限条件**，不与原 `where` 参数组合
- MyBatis-Plus 框架会自动将返回值与原始 WHERE 条件用 AND 连接
- 如果手动组合会导致重复条件，引发 "No value specified" 错误

#### 2. 规则工厂接口

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DataPermissionRuleFactory.java`

```java
public interface DataPermissionRuleFactory {
    Set<DataPermissionRule> getAllRules();
    Set<DataPermissionRule> getEnabledRules(String mappedStatementId);
    <T extends DataPermissionRule> T getRule(Class<T> ruleClass);
}
```

#### 3. 规则工厂实现

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DataPermissionRuleFactoryImpl.java`

```java
@Slf4j
@Component
public class DataPermissionRuleFactoryImpl implements DataPermissionRuleFactory {

    private final Map<Class<?>, DataPermissionRule> rules = new ConcurrentHashMap<>();

    public DataPermissionRuleFactoryImpl(List<DataPermissionRule> initialRules) {
        if (initialRules != null) {
            for (var rule : initialRules) {
                rules.put(rule.getClass(), rule);
            }
        }
    }

    @Override
    public Set<DataPermissionRule> getEnabledRules(String mappedStatementId) {
        var context = DataPermissionContextHolder.peek();

        // 支持上下文栈过滤（嵌套调用）
        if (context == null || context.getIncludeRules().isEmpty()) {
            return getAllRules();
        }

        // include/exclude 过滤
        Set<DataPermissionRule> enabled = new LinkedHashSet<>();
        for (var ruleClass : context.getIncludeRules()) {
            var rule = rules.get(ruleClass);
            if (rule != null && !context.getExcludeRules().contains(ruleClass)) {
                enabled.add(rule);
            }
        }
        return enabled;
    }
}
```

#### 4. 上下文栈管理

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DataPermissionContextHolder.java`

```java
public class DataPermissionContextHolder {
    private static final ThreadLocal<Deque<DataPermissionContext>> STACK =
        ThreadLocal.withInitial(ArrayDeque::new);

    public static DataPermissionContext peek() {
        return STACK.get().peek();
    }

    public static DataPermissionContext push() {
        var context = new DataPermissionContext();
        STACK.get().push(context);
        return context;
    }

    public static void pop() {
        var stack = STACK.get();
        stack.pop();
        if (stack.isEmpty()) STACK.remove();
    }
}
```

#### 5. AOP 注解拦截器

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/aspect/DataPermissionAnnotationInterceptor.java`

```java
@Slf4j
@Aspect
@Component
@Order(1)
public class DataPermissionAnnotationInterceptor {

    @Around("@annotation(dataPermission)")
    public Object around(ProceedingJoinPoint point, DataPermission dataPermission) throws Throwable {
        if (!dataPermission.enable()) {
            return point.proceed();
        }

        // 推入上下文栈
        DataPermissionContext context = DataPermissionContextHolder.push();
        try {
            // 配置 include/exclude 规则
            for (var ruleClass : dataPermission.rules()) {
                context.addIncludeRule(ruleClass);
            }
            for (var ruleClass : dataPermission.excludeRules()) {
                context.addExcludeRule(ruleClass);
            }
            return point.proceed();
        } finally {
            // 弹出上下文栈
            DataPermissionContextHolder.pop();
        }
    }
}
```

#### 6. 数据权限规则接口（升级版）

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DataPermissionRule.java`

```java
public interface DataPermissionRule {
    Set<String> getTableNames();

    /**
     * 新方法：返回 Expression 对象（类型安全）
     *
     * @param tableName 表名
     * @param tableAlias 表别名（使用 JSQLParser 的 Alias 对象）
     * @return SQL 条件表达式，null 表示无过滤条件
     */
    Expression getExpression(String tableName, Alias tableAlias);

    /**
     * 旧方法：返回 SQL 字符串（兼容性保留）
     * @deprecated 使用 getExpression 替代
     */
    @Deprecated
    default String buildCondition(String tableName, String tableAlias) {
        // 将 String 转换为 Alias 对象调用新方法
        Alias alias = tableAlias != null ? new Alias(tableAlias) : null;
        Expression expr = getExpression(tableName, alias);
        return expr != null ? expr.toString() : null;
    }
}
```

**关键变化**：
- `tableAlias` 参数类型从 `String` 改为 `net.sf.jsqlparser.expression.Alias`
- 直接使用 JSQLParser 的类型系统，避免字符串转换

#### 7. MyBatis 工具类

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/utils/MyBatisUtils.java`

```java
@UtilityClass
public class MyBatisUtils {

    /**
     * 构建列引用（使用 Alias 对象）
     */
    public Column buildColumn(String tableName, Alias tableAlias, String columnName) {
        Column column = new Column();
        column.setColumnName(columnName);

        if (tableAlias != null) {
            Table table = new Table();
            table.setName(tableAlias.getName());
            column.setTable(table);
        }
        return column;
    }

    /**
     * 构建 IN 表达式（支持 Alias 对象）
     */
    public Expression buildInExpression(String tableName, Alias tableAlias,
                                       String columnName, Long... values) {
        if (values == null || values.length == 0) return null;

        ExpressionList<Expression> list = new ExpressionList<>();
        for (var v : values) {
            list.add(new LongValue(v.toString()));
        }

        return new InExpression(
            buildColumn(tableName, tableAlias, columnName),
            list
        );
    }

    /**
     * 构建 IN 表达式（String 兼容版本）
     */
    public Expression buildInExpression(String tableName, String tableAlias,
                                       String columnName, Long... values) {
        return buildInExpression(tableName,
            tableAlias != null ? new Alias(tableAlias) : null,
            columnName, values);
    }

    /**
     * 构建 = 表达式（支持 Alias 对象）
     */
    public Expression buildEqualsExpression(String tableName, Alias tableAlias,
                                           String columnName, Long value) {
        return new EqualsTo(
            buildColumn(tableName, tableAlias, columnName),
            new LongValue(value.toString())
        );
    }

    /**
     * 构建 = 表达式（String 兼容版本）
     */
    public Expression buildEqualsExpression(String tableName, String tableAlias,
                                           String columnName, Long value) {
        return buildEqualsExpression(tableName,
            tableAlias != null ? new Alias(tableAlias) : null,
            columnName, value);
    }

    /**
     * 构建 OR 表达式
     */
    public Expression buildOrExpression(Expression... expressions) {
        if (expressions == null || expressions.length == 0) return null;
        if (expressions.length == 1) return expressions[0];

        Expression result = expressions[0];
        for (int i = 1; i < expressions.length; i++) {
            if (expressions[i] != null) {
                result = new OrExpression(result, expressions[i]);
            }
        }
        return result;
    }

    /**
     * 构建 "1=0" 表达式（无权限）
     */
    public Expression buildNoPermissionExpression() {
        return new EqualsTo(new LongValue("1"), new LongValue("0"));
    }
}
```

**关键特性**：
- 提供支持 `Alias` 对象的重载方法
- 保留 `String` 版本作为兼容层
- 统一使用 JSQLParser 类型系统

#### 8. 部门数据权限规则（新实现）

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/DeptDataPermissionRule.java`

```java
@Slf4j
@Component
public class DeptDataPermissionRule implements DataPermissionRule {

    @Override
    public Set<String> getTableNames() {
        // ⚠️ 注意：sys_job 表没有 dept_id 字段，已被移除
        return Set.of("sys_user", "sys_position");
    }

    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        UserContext context = UserContext.get();
        if (context == null) {
            return MyBatisUtils.buildNoPermissionExpression();
        }

        if (context.isAdmin()) {
            return null;
        }

        // 获取缓存的数据权限信息
        DeptDataScopeInfo dataScopeInfo = context.getContext(CONTEXT_KEY, DeptDataScopeInfo.class);
        if (dataScopeInfo == null) {
            dataScopeInfo = calculateDataScopeInfo(context);
            context.setContext(CONTEXT_KEY, dataScopeInfo);
        }

        // 获取表别名（优先使用 Alias 对象的名称）
        String alias = tableAlias != null ? tableAlias.getName() : tableName;

        return buildExpression(alias, dataScopeInfo, context);
    }

    private Expression buildExpression(String alias, DeptDataScopeInfo info, UserContext context) {
        if (info.isAll()) {
            return null;
        }

        List<Expression> conditions = new ArrayList<>();

        // 部门权限
        if (info.getDeptIds() != null && !info.getDeptIds().isEmpty()) {
            // 使用 Alias 对象构建表达式
            conditions.add(MyBatisUtils.buildInExpression(
                null, new Alias(alias), "dept_id",
                info.getDeptIds().toArray(new Long[0])
            ));
        }

        // 仅本人权限
        if (info.isSelf()) {
            conditions.add(MyBatisUtils.buildEqualsExpression(
                null, new Alias(alias), "id", context.getUserId()
            ));
        }

        // 组合条件：OR 逻辑
        if (conditions.isEmpty()) {
            return MyBatisUtils.buildNoPermissionExpression();
        } else if (conditions.size() == 1) {
            return conditions.get(0);
        } else {
            return MyBatisUtils.buildOrExpression(conditions.toArray(new Expression[0]));
        }
    }
}
```

**重要更新**：
- 参数类型使用 `Alias` 而不是 `String`
- `sys_job` 已从表名列表中移除（无 `dept_id` 字段）
- 从 `Alias` 对象提取表别名：`tableAlias.getName()`

#### 9. 数据权限注解（增强版）

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/annotation/DataPermission.java`

```java
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface DataPermission {
    String deptAlias() default "d";
    String userAlias() default "u";
    String permission() default "";
    boolean enable() default true;
    Class<? extends DataPermissionRule>[] rules() default {};
    Class<? extends DataPermissionRule>[] excludeRules() default {}; // 新增
}
```

#### 10. MyBatis-Plus 配置

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/config/MybatisPlusConfig.java`

```java
@Configuration
@RequiredArgsConstructor
public class MybatisPlusConfig {

    private final DataPermissionRuleHandler dataPermissionRuleHandler;

    // 旧版拦截器保留作为降级方案
    private final DataPermissionInterceptorJSQLParser dataPermissionInterceptorJSQLParser;
    private final DataPermissionInterceptor dataPermissionInterceptor;

    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 使用 MyBatis-Plus 标准数据权限插件
        interceptor.addInnerInterceptor(new DataPermissionInterceptor(dataPermissionRuleHandler));
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        return interceptor;
    }
}
```

### 架构对比

| 特性 | 旧版本 | 新版本 v2.0 |
|------|--------|------------|
| SQL 解析准确性 | ✅ 100% 准确 | ✅ 100% 准确 |
| 复杂 JOIN 支持 | ✅ 完整支持 | ✅ 完整支持 |
| 类型安全 | ❌ String 字符串 | ✅ Expression 对象 |
| 规则过滤 | ❌ 不支持 | ✅ include/exclude |
| 嵌套调用 | ❌ 不支持 | ✅ ThreadLocal 栈 |
| 二次解析 | ❌ 需要解析字符串 | ✅ 直接使用 Expression |

### 依赖配置

**文件**: `apps/forge-server/pom.xml`

```xml
<!-- MyBatis-Plus -->
<dependency>
    <groupId>com.baomidou</groupId>
    <artifactId>mybatis-plus-spring-boot3-starter</artifactId>
    <version>3.5.7</version>
</dependency>

<!-- JSQLParser - SQL解析器 -->
<dependency>
    <groupId>com.github.jsqlparser</groupId>
    <artifactId>jsqlparser</artifactId>
    <version>4.9</version>
</dependency>
```

**版本说明**：
- **MyBatis-Plus 3.5.7**：完整支持 JSQLParser 4.9 的 `Alias` 对象
- **JSQLParser 4.9**：`Alias` 类位于 `net.sf.jsqlparser.expression.Alias` 包

## 使用指南

> ⚠️ **重要**：数据权限**默认关闭**，需要显式使用 `@DataPermission` 注解才会启用。

### 方式一：使用 @DataPermission 注解（推荐）

在 Mapper 方法上添加 `@DataPermission` 注解，拦截器会自动处理数据权限。

#### 默认行为（不启用数据权限）

```java
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    // ⚠️ 没有注解，不启用数据权限，返回所有数据
    @Select("SELECT * FROM sys_user")
    List<SysUser> selectAllUsers();
}
```

#### 基本用法（启用数据权限）

```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {

    // 启用数据权限过滤
    @DataPermission(userAlias = "x", deptAlias = "d")
    IPage<Xxx> selectXxxPageWithPermission(
        Page<Xxx> page,
        @Param("name") String name
    );
}
```

#### 不启用数据权限（默认行为）

```java
@Mapper
public interface XxxMapper extends BaseMapper<Xxx> {

    // 没有注解，不进行数据权限过滤，返回所有数据
    IPage<Xxx> selectXxxPage(
        Page<Xxx> page,
        @Param("name") String name
    );

    // 或者显式禁用
    @DataPermission(enable = false)
    IPage<Xxx> selectXxxPageWithoutPermission(
        Page<Xxx> page,
        @Param("name") String name
    );
}
```

#### 高级用法（规则过滤）

```java
@DataPermission(
    userAlias = "o",
    deptAlias = "d",
    enable = true,
    rules = {OrderDataPermissionRule.class},           // 只使用指定规则
    excludeRules = {DeptDataPermissionRule.class}      // 排除指定规则
)
IPage<SysOrder> selectOrderPageWithPermission(...);
```

### 方式二：创建自定义数据权限规则

#### 完整示例：订单数据权限

**步骤 1: 创建自定义规则类**

**文件**: `apps/forge-server/src/main/java/com/standadmin/common/permission/OrderDataPermissionRule.java`

```java
package com.forge.admin.common.permission;

import com.forge.admin.common.utils.MyBatisUtils;
import com.forge.admin.common.utils.UserContext;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.Alias;
import org.springframework.stereotype.Component;

import java.util.Set;

@Slf4j
@Component
public class OrderDataPermissionRule implements DataPermissionRule {

    @Override
    public Set<String> getTableNames() {
        return Set.of("sys_order", "sys_order_item");
    }

    @Override
    public Expression getExpression(String tableName, Alias tableAlias) {
        UserContext context = UserContext.get();
        if (context == null) {
            return MyBatisUtils.buildNoPermissionExpression();
        }

        if (context.isAdmin()) {
            return null;
        }

        // 根据数据权限范围构建 Expression
        switch (context.getMaxDataScope()) {
            case ALL:
                return null;
            case DEPT:
            case DEPT_AND_CHILD:
                if (context.getDeptId() != null) {
                    return MyBatisUtils.buildEqualsExpression(
                        null, tableAlias, "dept_id", context.getDeptId()
                    );
                }
                return MyBatisUtils.buildNoPermissionExpression();
            case SELF:
            default:
                return MyBatisUtils.buildEqualsExpression(
                    null, tableAlias, "creator_id", context.getUserId()
                );
        }
    }
}
```

**关键点**：
- 参数类型使用 `Alias tableAlias`
- 直接将 `tableAlias` 传递给 `MyBatisUtils` 方法
- 无需手动转换，类型安全

**步骤 2: 在 Mapper 中使用**

```java
@Mapper
public interface SysOrderMapper extends BaseMapper<SysOrder> {

    @DataPermission(
        userAlias = "o",
        deptAlias = "d",
        rules = {OrderDataPermissionRule.class}
    )
    IPage<SysOrder> selectOrderPageWithPermission(
        Page<SysOrder> page,
        @Param("orderNo") String orderNo
    );
}
```

**步骤 3: 编写 Mapper XML**

```xml
<select id="selectOrderPageWithPermission" resultType="...SysOrder">
    SELECT o.id, o.order_no, o.dept_id, o.creator_id
    FROM sys_order o
    LEFT JOIN sys_dept d ON o.dept_id = d.id
    <where>
        <if test="orderNo != null and orderNo != ''">
            AND o.order_no LIKE CONCAT('%', #{orderNo}, '%')
        </if>
    </where>
    ORDER BY o.create_time DESC
</select>
```

## 已完成模块

### 用户管理（SysUser）

**状态**: ✅ 已完成

**相关文件**:
- Mapper: `SysUserMapper.java` - 添加 `selectUserPageWithPermission` 方法
- XML: `SysUserMapper.xml` - 带数据权限的 SQL 查询
- Service: `SysUserServiceImpl.java` - 使用新的 Mapper 方法

**SQL 示例**:
```sql
-- 原始 SQL
SELECT u.id, u.username, u.dept_id FROM sys_user u
LEFT JOIN sys_dept d ON u.dept_id = d.id

-- 修改后 SQL（研发部用户，MyBatis-Plus 自动添加）
SELECT u.id, u.username, u.dept_id FROM sys_user u
LEFT JOIN sys_dept d ON u.dept_id = d.id
WHERE u.dept_id = 101
```

### 岗位管理（SysPosition）

**状态**: ✅ 已完成

**相关文件**:
- Mapper: `SysPositionMapper.java` - 添加数据权限方法
- XML: `SysPositionMapper.xml` - 带数据权限的 SQL 查询
- Service: `SysPositionServiceImpl.java` - 使用新的 Mapper 方法

## 测试验证

### 单元测试

**文件**: `apps/forge-server/src/test/java/com/standadmin/common/config/DataPermissionInterceptorJSQLParserTest.java`

```bash
# 运行测试
mvn test -Dtest=DataPermissionInterceptorJSQLParserTest
```

### 功能测试

```bash
# 1. 登录获取 Token
TOKEN=$(curl -s -X POST "http://localhost:8180/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"123456"}' | jq -r '.data.accessToken')

# 2. 查询数据
curl -s "http://localhost:8180/api/system/user/list?pageNum=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN" | jq '.data.list'
```

## 常见问题

### 1. "No value specified for parameter 2" 错误

**原因**：`getSqlSegment()` 方法错误地将数据权限条件与原始 WHERE 条件组合。

**错误实现**：
```java
// ❌ 错误：手动组合导致重复条件
@Override
public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
    Expression result = where;  // 错误：以 where 为基础
    // ...
    result = new AndExpression(result, dataPermissionExpr);
    return result;
}
```

**正确实现**：
```java
// ✅ 正确：只返回数据权限条件
@Override
public Expression getSqlSegment(Table table, Expression where, String mappedStatementId) {
    // ...
    Expression allExpression = buildDataPermissionExpression();
    return allExpression;  // 只返回数据权限条件
    // MyBatis-Plus 会自动与 where 条件组合
}
```

### 2. 返回 Expression 对象的优势

**旧版本（String）**：
```java
// 返回 SQL 字符串，需要二次解析
String condition = "u.dept_id IN (101, 102)";
// 拦截器需要再次解析为 Expression
Expression expr = CCJSqlParserUtil.parseCondExpression(condition);
```

**新版本（Expression）**：
```java
// 直接返回 Expression 对象，类型安全
Expression expr = MyBatisUtils.buildInExpression(
    null, new Alias("u"), "dept_id", 101L, 102L
);
// 拦截器直接使用，无需二次解析
```

### 3. Alias 对象 vs String

**使用 Alias 对象的优势**：
- 类型安全，编译时检查
- 避免 SQL 注入风险
- 与 JSQLParser 类型系统一致

```java
// 旧方式（String）
Expression getExpression(String tableName, String tableAlias) {
    // 需要手动处理空值和引用
    String alias = tableAlias != null ? tableAlias : tableName;
}

// 新方式（Alias 对象）
Expression getExpression(String tableName, Alias tableAlias) {
    // 直接使用 JSQLParser 类型
    String alias = tableAlias != null ? tableAlias.getName() : tableName;
}
```

### 4. sys_job 表权限错误

**错误**：`Unknown column 'dept_id' in 'where clause'`

**原因**：`sys_job` 表没有 `dept_id` 字段

**解决**：从 `DeptDataPermissionRule` 的表名列表中移除 `sys_job`

```java
@Override
public Set<String> getTableNames() {
    // ❌ 错误：sys_job 没有 dept_id 字段
    // return Set.of("sys_user", "sys_position", "sys_job");

    // ✅ 正确
    return Set.of("sys_user", "sys_position");
}
```

### 5. 规则过滤的使用场景

```java
// 场景：订单列表只使用订单规则，不使用部门规则
@DataPermission(
    rules = {OrderDataPermissionRule.class},
    excludeRules = {DeptDataPermissionRule.class}
)
IPage<SysOrder> selectOrderPage(...);

// 场景：同时使用多个规则
@DataPermission(
    rules = {OrderDataPermissionRule.class, StatusDataPermissionRule.class}
)
IPage<SysOrder> selectOrderPage(...);
```

### 3. 嵌套调用支持

```java
// 外层方法使用部门规则
@DataPermission(rules = {DeptDataPermissionRule.class})
public void outerMethod() {
    // 内层方法使用订单规则，不会影响外层
    innerMethod();
}

@DataPermission(rules = {OrderDataPermissionRule.class})
public void innerMethod() {
    // 只使用订单规则
}
```

## 性能优化建议

1. **缓存数据权限信息**: 在 `UserContext` 中缓存计算结果
2. **使用 Expression 对象**: 避免 SQL 字符串二次解析
3. **数据库索引**: 确保 `dept_id` 等过滤字段有索引

```sql
-- 添加索引
CREATE INDEX idx_dept_id ON sys_user(dept_id);
CREATE INDEX idx_dept_id ON sys_position(dept_id);
```

## 附录

### 相关文件清单

```
apps/forge-server/src/main/java/com/standadmin/
├── common/
│   ├── annotation/
│   │   └── DataPermission.java                      # 数据权限注解（增强版）
│   ├── aspect/
│   │   └── DataPermissionAnnotationInterceptor.java # AOP 拦截器 ⭐ 新增
│   ├── config/
│   │   ├── MybatisPlusConfig.java                   # MyBatis-Plus 配置
│   ├── permission/
│   │   ├── DataPermissionRule.java                  # 规则接口（升级版，使用 Alias）
│   │   ├── DataPermissionRuleFactory.java           # 规则工厂接口 ⭐ 新增
│   │   ├── DataPermissionRuleFactoryImpl.java       # 规则工厂实现 ⭐ 新增
│   │   ├── DataPermissionRuleHandler.java           # 核心处理器 ⭐ 新增
│   │   ├── DataPermissionContext.java               # 上下文数据类 ⭐ 新增
│   │   ├── DataPermissionContextHolder.java         # 上下文栈管理 ⭐ 新增
│   │   └── DeptDataPermissionRule.java              # 部门规则（新版实现）
│   └── utils/
│       ├── MyBatisUtils.java                        # Expression 工具类 ⭐ 新增
│       └── UserContext.java                         # 用户上下文
```

**⭐ 新增文件（7个）**：
1. `DataPermissionRuleHandler.java` - 实现 MyBatis-Plus `MultiDataPermissionHandler`
2. `DataPermissionRuleFactory.java` - 规则工厂接口
3. `DataPermissionRuleFactoryImpl.java` - 规则工厂实现
4. `DataPermissionContext.java` - 上下文数据类
5. `DataPermissionContextHolder.java` - ThreadLocal 栈管理
6. `DataPermissionAnnotationInterceptor.java` - AOP 注解拦截器
7. `MyBatisUtils.java` - JSQLParser Expression 构建工具

**修改文件（4个）**：
1. `DataPermissionRule.java` - 添加 `getExpression(String, Alias)` 方法
2. `DeptDataPermissionRule.java` - 实现 Alias 版本接口
3. `DataPermission.java` - 添加 `excludeRules` 属性
4. `MybatisPlusConfig.java` - 使用新的 `DataPermissionRuleHandler`

### 架构流程图

```
┌─────────────────────────────────────────────────────────────────────┐
│                          数据权限处理流程                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  @DataPermission 注解                                               │
│       │                                                            │
│       ▼                                                            │
│  DataPermissionAnnotationInterceptor (AOP)                          │
│       │                                                            │
│       ▼                                                            │
│  DataPermissionContextHolder.push()                                 │
│       │                                                            │
│       ▼                                                            │
│  执行 Mapper 方法                                                   │
│       │                                                            │
│       ▼                                                            │
│  MyBatis-Plus DataPermissionInterceptor                             │
│       │                                                            │
│       ▼                                                            │
│  DataPermissionRuleHandler.getSqlSegment()                          │
│       │                                                            │
│       ▼                                                            │
│  DataPermissionRuleFactory.getEnabledRules()                        │
│       │                                                            │
│       ▼                                                            │
│  DataPermissionRule.getExpression() ──► MyBatisUtils                │
│       │                                                            │
│       ▼                                                            │
│  返回 Expression 对象                                                │
│       │                                                            │
│       ▼                                                            │
│  MyBatis-Plus 自动合并到 WHERE 子句                                   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

**文档版本**: v2.3
**更新日期**: 2026-03-05
**维护者**: forge-admin Team

## 更新历史

| 版本 | 日期 | 更新内容 |
|------|------|----------|
| v2.3 | 2026-03-05 | **重要变更**：数据权限默认关闭，只有使用 @DataPermission 注解时才启用 |
| v2.2 | 2026-03-04 | 修复 getSqlSegment() 返回值逻辑；使用 Alias 对象代替 String；更新版本要求为 MyBatis-Plus 3.5.7 |
| v2.1 | 2026-03-04 | 架构升级 v2.0 - 采用 Expression 对象和标准接口 |
| v1.0 | 2026-03-03 | 初始版本 - 数据权限基础实现 |

## 技术栈

| 组件 | 版本 | 说明 |
|------|------|------|
| MyBatis-Plus | 3.5.7 | 支持 MultiDataPermissionHandler 和 JSQLParser 4.9 |
| JSQLParser | 4.9 | Alias 类位于 `net.sf.jsqlparser.expression.Alias` |
| Spring Boot | 3.x | 使用 spring-boot3-starter |
