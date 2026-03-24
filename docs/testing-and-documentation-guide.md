# 测试与文档说明

> **生成日期**: 2026-03-04
> **项目版本**: v1.0

---

## 一、测试说明

### 1.1 测试框架

本项目使用 **JUnit 5** 作为测试框架，配合 **Spring Boot Test** 进行单元测试和集成测试。

### 1.2 已实现的测试

| 测试类 | 测试内容 | 状态 |
|--------|----------|------|
| `DataPermissionRuleTest` | 数据权限规则测试 | ✅ |
| `JwtTokenProviderTest` | JWT 令牌生成和验证测试 | ✅ |
| `DataScopeUtilsTest` | 数据权限 SQL 构建测试 | ✅ |

### 1.3 运行测试

```bash
# 运行所有测试
mvn test

# 运行指定测试类
mvn test -Dtest=DataPermissionRuleTest

# 运行指定测试方法
mvn test -Dtest=JwtTokenProviderTest#testGenerateToken
```

### 1.4 测试覆盖

**核心功能测试覆盖：**

- ✅ 数据权限规则（5种权限类型）
- ✅ JWT Token 生成和验证
- ✅ 用户上下文管理
- ✅ 数据权限 SQL 构建

**待添加测试：**

- ⏳ Controller 层测试
- ⏳ Service 层测试
- ⏳ Mapper 层测试
- ⏳ 集成测试

---

## 二、API 文档说明

### 2.1 Knife4j 配置

项目使用 **Knife4j 4.5.0** 作为 API 文档工具。

**访问地址：**
- 开发环境: http://localhost:8180/api/doc.html
- 生产环境: 需在配置中关闭

### 2.2 文档注解规范

#### Controller 层注解

```java
@Tag(name = "用户管理")  // 模块名称
@RestController
@RequestMapping("/system/user")
public class SysUserController {

    @Operation(summary = "分页查询用户")  // 接口说明
    @GetMapping("/list")
    public Result<PageResult<UserResponse>> list(UserQueryRequest request) {
        // ...
    }
}
```

#### DTO 层注解

```java
@Schema(description = "用户信息请求")  // 类说明
@Data
public class UserRequest {

    @Schema(description = "用户名", example = "admin", required = true)
    @NotBlank(message = "用户名不能为空")
    private String username;
}
```

#### 参数注解

```java
@GetMapping("/{id}")
public Result<UserResponse> getInfo(
    @Parameter(description = "用户ID", example = "1", required = true)
    @PathVariable Long id
) {
    // ...
}
```

### 2.3 接口分组

| 分组 | 路径前缀 | 说明 |
|------|----------|------|
| 认证管理 | `/auth` | 登录、登出、获取用户信息 |
| 用户管理 | `/system/user` | 用户 CRUD、密码重置 |
| 角色管理 | `/system/role` | 角色 CRUD、权限分配 |
| 菜单管理 | `/system/menu` | 菜单树管理 |
| 部门管理 | `/system/dept` | 部门树管理 |
| 岗位管理 | `/system/position` | 岗位 CRUD |
| 字典管理 | `/system/dict` | 字典类型和数据管理 |
| 系统配置 | `/system/config` | 系统参数配置 |
| 定时任务 | `/system/job` | Quartz 任务管理 |

---

## 三、测试编写指南

### 3.1 单元测试模板

```java
@DisplayName("模块名称测试")
class XxxServiceTest {

    private XxxService xxxService;

    @BeforeEach
    void setUp() {
        // 初始化测试数据
    }

    @Test
    @DisplayName("测试功能描述")
    void testFeature() {
        // Given
        // 准备测试数据

        // When
        // 执行测试方法

        // Then
        // 验证结果
        assertEquals(expected, actual);
    }
}
```

### 3.2 数据权限测试示例

```java
@Test
@DisplayName("超级管理员应返回 null（无过滤条件）")
void testAdminDataScope() {
    // Given
    UserContext adminContext = createAdminContext();

    // When
    String filter = DataScopeUtils.buildDataScopeFilter(adminContext, "sys_user", "u");

    // Then
    assertNull(filter, "超级管理员应返回 null");
}
```

### 3.3 JWT 测试示例

```java
@Test
@DisplayName("应成功生成有效的 JWT Token")
void testGenerateToken() {
    // Given
    String username = "testuser";

    // When
    String token = jwtTokenProvider.generateToken(username);

    // Then
    assertNotNull(token);
    assertFalse(token.isEmpty());
}
```

---

## 四、文档维护指南

### 4.1 接口变更时

1. **新增接口**：添加 `@Operation` 注解说明
2. **修改接口**：更新 `@Operation` 和相关注解
3. **废弃接口**：添加 `@Deprecated` 注解
4. **修改参数**：更新 `@Parameter` 或 `@Schema` 注解

### 4.2 DTO 变更时

1. **新增字段**：添加 `@Schema` 注解，包含 description 和 example
2. **修改字段**：更新 example 值
3. **必填字段**：添加 `required = true`

### 4.3 注解规范

| 注解 | 使用位置 | 必填属性 | 可选属性 |
|------|----------|----------|----------|
| `@Tag` | Controller 类 | name | description |
| `@Operation` | Controller 方法 | summary | description |
| `@Schema` | DTO 类/字段 | description | example, required |
| `@Parameter` | 方法参数 | description | example, required |

---

## 五、测试最佳实践

### 5.1 命名规范

- 测试类：`XxxTest.java`
- 测试方法：`testXxx()` 或使用 `@DisplayName`

### 5.2 测试结构

使用 **Given-When-Then** 模式：

```java
@Test
void testSomething() {
    // Given - 准备测试数据
    String input = "test";

    // When - 执行被测试的方法
    String result = service.process(input);

    // Then - 验证结果
    assertEquals("expected", result);
}
```

### 5.3 断言选择

| 方法 | 用途 |
|------|------|
| `assertEquals()` | 验证相等 |
| `assertNotEquals()` | 验证不相等 |
| `assertTrue()` | 验证为真 |
| `assertFalse()` | 验证为假 |
| `assertNull()` | 验证为空 |
| `assertNotNull()` | 验证非空 |
| `assertThrows()` | 验证抛出异常 |

### 5.4 Mock 使用

对于需要 Mock 的测试，使用 Mockito：

```java
@ExtendWith(MockitoExtension.class)
class XxxServiceTest {

    @Mock
    private XxxMapper xxxMapper;

    @InjectMocks
    private XxxService xxxService;

    @Test
    void testFeature() {
        // Given
        when(xxxMapper.selectById(1L)).thenReturn(mockEntity);

        // When
        XxxEntity result = xxxService.getById(1L);

        // Then
        assertNotNull(result);
        verify(xxxMapper).selectById(1L);
    }
}
```

---

## 六、常见问题

### Q1: 测试运行失败，提示找不到类？

**A**: 确保在 `pom.xml` 中添加了测试依赖：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

### Q2: Knife4j 文档打不开？

**A**: 检查以下几点：

1. 确认配置文件中 `knife4j.enable=true`
2. 检查 Spring Security 是否放行了文档路径
3. 确认后端服务已启动

### Q3: API 文档没有显示接口说明？

**A**: 确保添加了正确的注解：

- Controller 类上添加 `@Tag`
- 方法上添加 `@Operation`
- DTO 类上添加 `@Schema`

---

## 七、下一步计划

- [ ] 添加 Service 层单元测试
- [ ] 添加 Controller 层集成测试
- [ ] 完善所有 DTO 的 `@Schema` 注解
- [ ] 添加接口参数的 `@Parameter` 注解
- [ ] 添加测试覆盖率报告
- [ ] 添加 API 文档的示例值

---

**文档维护者**: forge-admin Team
**最后更新**: 2026-03-04
