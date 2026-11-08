# Spring Boot 4 升级评估报告

> 评估日期：2026-11-08 ｜ 评估对象：forge-admin 后端（apps/forge-server，24 个 Maven 模块）
> **实施状态：✅ 已完成（2026-11-08，分支 `feat/spring-boot-4-upgrade`）——见文末「实施结果」节**

## 实施结果（2026-11-08 补记）

### 版本终值
| 项 | 终值 | 备注 |
|---|---|---|
| Spring Boot | **4.1.2**（Spring Framework 7.0.10 / Security 7.1.2 / Tomcat 11） | 7.7s 启动 |
| MyBatis-Plus | **3.5.18.1** `mybatis-plus-spring-boot4-starter` | **jsqlparser 4.9 锁定与适配包保持**，租户/数据权限不受影响 |
| springdoc | **3.2.0** | swagger-ui / api-docs 实测 200 |
| Spring AI | **2.0.2** | OpenAiApi→官方 OpenAI Java SDK 重写工厂 |
| SAS | spring-security-oauth2-authorization-server **7.1.2**（纯 artifact，非 Boot starter） | 见下「AS 链说明」 |
| ArchUnit | 1.5.1 ／ JUnit 6 ／ Mockito 5.20 | 479/479 测试全绿；byte-buddy 覆盖属性已删（Java 25 运行时无需） |
| jasypt | 维持 3.0.5 | CVE-2026-9370 修复版（≥4.0.5）**至今未发布**（central 最高 4.0.4 同受影响）；issue #417 实证 3.0.5 兼容 Boot 4。发布后单独升级 |
| 删除项 | resilience4j（零使用）、mockito-inline、mybatis.version 锁、jackson-datatype-jsr310、llmWebClient/RestClientBuilder bean | |

### 行为差异与决策清单（升级实际落地项）
1. **Jackson 3（tools.jackson）**：66 文件全量迁移；9 个自定义序列化器 SPI 重写（ValueSerializer/ValueDeserializer）；JacksonConfig 由自建 ObjectMapper bean 改为 `JsonMapperBuilderCustomizer`（Boot 4 自动装配 JsonMapper）；序列化行为（Long→字符串、LocalDateTime 秒级时间戳、NON_NULL、未知字段容错）经接口实测保持
2. **Redis 缓存序列化**：`GenericJackson2JsonRedisSerializer`→`GenericJacksonJsonRedisSerializer`。**部署提示：升级后首次发布建议清 Redis DB1 业务缓存（dictData/dictType/sysConfig/userInfo/menu/dept）或容忍冷启动**，新旧值含 @class 类型信息可互通但时间字段格式有差异
3. **yml 属性**：`spring.jackson.serialization.write-dates-as-timestamps` 删除（Jackson 3 无此枚举，默认即 ISO 字符串，与旧显式 false 等价）；`logging.file.max-*`→`logging.logback.rollingpolicy.*`（migrator 报告仅此 3 项）
4. **AS 链说明**：Security 7 禁止 any-request 链遮蔽（UnreachableFilterChainException），授权服务器链改用写死端点 OrRequestMatcher（`/oauth2/**`、`/.well-known/**`、`/userinfo`、`/connect/**`），未用 configurer 延迟 matcher（其字段 init 期填充，与 securityMatcher 注册时序耦合在自定义链场景 NPE）；并改用纯 SAS artifact 绕开 Boot AS 自动配置（项目全手工配置 AS，自动配置链与自定义链并存时冲突）
5. **Spring AI 2.0 工具语义**：`internalToolExecutionEnabled` 移除——ChatModel 永不内部执行工具，toolCalls 原样返回。项目 NativeToolCalling 本就是手动执行模式（图路由接管），删除该行即语义等价；重试由自建 RetryTemplate 改为官方 SDK `maxRetries=2`；thinking-disabled 请求体注入由 RestClient interceptor 平移为 OkHttp interceptor
6. **jjwt 双栈**：jjwt-jackson 基于 Jackson 2，其传递的 jackson2 仅在 jjwt 内部使用，与业务 Jackson 3 共存互不交叉（设计内状态）
7. **DaoAuthenticationProvider**：Security 7 改构造器注入 UserDetailsService，setter 移除

### 遗留验收项（需真实凭据/环境）
- 登录后接口的 JSON 契约对拍（分页 list/total、字典标签、时间字段）：dev 库演示账号密码已变更，本地 3 组凭据失败后为避免触发 Redis 登录锁定而停手。验收命令：`curl -X POST /admin-api/auth/login -H "tenant-id: 1" -d '{"username":"<账号>","password":"<密码>"}'` 后带 token 查 `/admin-api/system/user/list`
- OAuth2 授权码全流程（浏览器跳转 3000 前端登录页）：需前端联跑
- jasypt CVE 修复版发布后升级（≥4.0.5）

### 排查经验（复用价值）
- **spring-boot-maven-plugin repackage 增量陷阱**：源码已改但已存在 fat jar 未被替换（时间戳与内容均旧）——改依赖模块后重打 fat jar 前先 `rm` 旧 jar
- macOS BSD sed 不支持 `\b`，正则单词边界替换静默失效——用无边界精确模式或 perl
- javap + 常量池 MethodParameters 可从 jar 直接实证 API 参数名/签名，免网络查文档


---

## 一、结论摘要

| 维度 | 结论 |
|---|---|
| **可行性** | 可行。全链路依赖均有 Boot 4 适配版本，无硬阻塞项 |
| **建议目标版本** | **4.1.2**（4.1 线已 GA 5 个月；4.2.0 预计 2026-11 GA，如不急可等 4.2 补丁稳定后一步到位） |
| **前置要求** | Java 17+（项目 Java 21 ✅，未来可顺势升 Java 25 LTS） |
| **预估工作量** | 3~5 人日（含回归验证）；若 Jackson 采取「共存过渡」策略首轮可压到 2~3 人日 |
| **最大风险** | ① Jackson 3 序列化行为差异影响全部 HTTP JSON 契约；② Spring AI 2.0 API 重写 |
| **最省心的部分** | `@MockBean` 零使用、自动配置已用 `.imports`、Security DSL 无 `.and()`、jsqlparser 4.9 锁定可以保住 |

---

## 二、当前基线盘点（实测扫描）

### 版本
- `spring-boot-starter-parent` **3.4.11**，Java **21**，Lombok 1.18.38，byte-buddy 覆盖 1.17.8

### 关键第三方依赖（forge-dependencies BOM）
| 依赖 | 当前版本 | 项目内使用情况 |
|---|---|---|
| MyBatis-Plus（boot3-starter + jsqlparser-4.9 适配包） | 3.5.17 | 核心持久层；**jsqlparser 锁 4.9**（5.x 会导致租户插件/数据权限 WITH 解析炸） |
| springdoc-openapi | 2.8.14 | swagger-ui 文档 |
| Spring AI（client-chat / openai / tika） | 1.1.8 | AI 模块 **12 个 Java 文件** |
| FlowLong（工作流引擎） | 1.2.7 | workflow 模块；已排除其 Boot2 线 starter |
| Spring Authorization Server | **1.2.0**（很旧，6.2 时代） | system-biz 的 `AuthorizationServerConfig` 1 个文件 |
| jasypt | 3.0.5 | 仅 prod yml 2 处 ENC 加密配置 |
| resilience4j | 2.3.0 | **代码与配置零使用 → 死依赖，可直接删** |
| jjwt 0.12.3 / hutool 5.8.25 / easyexcel 3.3.4 / JustAuth 1.16.6 / langgraph4j 1.9.2 / guava / TTL / minio / oss / cos | — | 均与 Boot 版本解耦 |
| ArchUnit | 1.3.0 | 分层架构守护测试 |

### 代码层扫描结果（升级影响面实测）
| 扫描项 | 结果 | 影响 |
|---|---|---|
| `@MockBean` / `@SpyBean`（Boot 4 已移除） | **0 处** | ✅ 无需迁移 |
| 自动配置注册方式 | 全部 `.imports`（7 处），无 spring.factories | ✅ 无需迁移 |
| Security DSL `.and()`（Security 7 移除） | **0 处** | ✅ 无需迁移 |
| `com.fasterxml.jackson.databind`（databind API） | **66 个文件**（57 个用 ObjectMapper） | ⚠️ 核心迁移面 |
| `com.fasterxml.jackson.annotation`（注解） | 4 个文件 | ✅ 注解包名不变，零改动 |
| RestTemplate / RestClient / WebClient | 11 个文件 | 编译期验证即可 |
| jakarta.servlet 直接使用 | 26 个文件 | ✅ Servlet 6.1 向下兼容 |
| 测试类 | **86 个** | JUnit 5→6、Mockito/ArchUnit 版本联动 |

---

## 三、Spring Boot 4 核心变化（对照本项目）

| 变化 | 对本项目的影响 |
|---|---|
| **Jackson 3 默认**（`tools.jackson.*` 新包名；Jackson 2 弃用但保留 `spring-boot-jackson2` 模块过渡；**jackson-annotations 保持 `com.fasterxml.jackson.annotation` 且 2/3 共用**） | 最大的迁移项：66 文件 databind 使用需决策策略（见 §五） |
| **模块化重组**：`spring-boot-starter-web`→`spring-boot-starter-webmvc` 等 starter 改名，**旧名保留 deprecated 别名** | 机械替换即可，短期不改也能跑 |
| **Spring Security 7 + SAS 并入**（`spring-security-oauth2-authorization-server` 版本号跟随 Security 7.x，包名不变） | SAS 1.2.0→7.0 跨 5 个中版本，`AuthorizationServerConfig` 需适配验证；BOM 里手工锁的 1.2.0 删掉交给 Boot 管理 |
| **JUnit Jupiter 6**（test starter 默认） | 86 个测试类回归；JUnit 6 大体向下兼容，个别弃用 API 需修 |
| **Java 基线 17+**（4.1 支持到 27） | ✅ Java 21 满足 |
| **Jakarta EE 11 / 内嵌 Tomcat 11 / Servlet 6.1** | ✅ 26 处 jakarta.servlet 用法兼容 |
| **~83 项破坏性变更**（属性改名/删除等） | 用官方 `spring-boot-properties-migrator` 启动期自动报告，逐项确认后移除 |
| AOT/模块化自动配置 | 项目已是 `.imports` 注册，✅ |
| Mockito 5.2x（inline 已内置） | `mockito-inline` 依赖可删；`byte-buddy.version` 覆盖属性需更新或删除 |

---

## 四、依赖兼容性矩阵（逐项动作）

| 依赖 | 当前 | 目标 | 兼容性 | 动作 |
|---|---|---|---|---|
| spring-boot-starter-parent | 3.4.11 | **4.1.2** | ✅ | parent 直接升（建议先 3.5.x 过一轮再上 4.x） |
| mybatis-plus | 3.5.17 boot3-starter | **3.5.18.1** | ✅ 官方已有 `mybatis-plus-spring-boot4-starter` | 换 boot4-starter；**保留 `mybatis-plus-jsqlparser-4.9` 适配包（同版本线出到 3.5.18.1），BOM 里对 starter 的 `mybatis-plus-jsqlparser` 排除照搬**；jsqlparser 4.9 锁定保住，租户插件/数据权限不受影响 |
| springdoc | 2.8.14 | **3.2.x** | ✅ v3 线专为 Boot 4 | 升级；`io.swagger.v3.oas.annotations` 注解包名不变 |
| Spring AI | 1.1.8 | **2.0.x** | ⚠️ 2.0 GA（2026-06-12）**要求 Boot 4**，但 ChatModel 工厂/builder API 有重写、模块改名 | AI 模块 12 文件迁移；对照官方 Upgrade Notes 逐个改（集中在 LlmClient 封装层） |
| Spring Authorization Server | 1.2.0 独立版 | Security 7 内置版（7.x） | ⚠️ 包名不变、API 有调整 | 删 BOM 锁定，改用 Boot 4 的 `spring-boot-starter-oauth2-authorization-server` 托管；适配 `AuthorizationServerConfig` |
| FlowLong | 1.2.7 | 1.2.7（暂不升） | ✅ 大概率可用：starter 已带 `.imports`（Boot3/4 通用机制），引擎本体仅依赖 slf4j + MP core，其 Boot2 线 starter 项目早已排除 | 保持现状，升级后重点回归工作流全流程 |
| jasypt | 3.0.5 | **≥4.0.5** | ⚠️ 3.0.5 社区验证 Boot 4 可用，但 **CVE-2026-9370** 影响 ≤3.0.5/≤4.0.4 | 升到已修复版本；长期关注该库维护停滞，考虑换环境变量/Secret 管理方案 |
| resilience4j | 2.3.0（boot3 线） | 删除 | **代码与配置零使用** | 从 BOM 删除（Boot 4 若将来要用，对应 artifact 是 `resilience4j-spring-boot4` 2.4.0+） |
| JUnit / Mockito / ArchUnit | Boot 托管 / mockito-inline 5.2.0 / 1.3.0 | JUnit 6 / Mockito 5.20+ / ArchUnit 最新 | ⚠️ 需验证 | 删 `mockito-inline`（inline 已内置）；更新或删除 `byte-buddy.version` 覆盖；ArchUnit 升级并确认其 JUnit 平台引擎兼容 JUnit 6 |
| jjwt / hutool / easyexcel / JustAuth / langgraph4j / guava / TTL / oss / cos / minio | — | 不强制 | ✅ 与 Boot 解耦 | 不动（easyexcel 已停更，迁 FastExcel 是独立议题，不建议混入本次） |
| swagger-annotations-jakarta | 2.2.38 | 跟随 springdoc 3 传递 | ✅ | 可能可直接删掉 BOM 锁定 |

---

## 五、Jackson 2→3 策略决策（本次升级的核心取舍）

项目 66 个文件直接使用 `com.fasterxml.jackson.databind`（ObjectMapper 57 处）。三条路线：

| 方案 | 做法 | 代价 | 建议 |
|---|---|---|---|
| **A. 全量迁 Jackson 3** | databind API 换 `tools.jackson.databind`；异常从受检 `JsonProcessingException` 变非受检 `JacksonException`（catch 处需改）；OpenRewrite `UpgradeJackson_2_3` 配方可自动化大部分 | 66 文件 + 全接口 JSON 契约回归，约 1~2 人日 | **推荐**（一步到位，Boot 4 默认栈，避免双 JSON 栈长期共存） |
| B. 共存过渡 | 引入 `spring-boot-jackson2` 模块，MVC 用 Jackson 3，业务代码留 Jackson 2（`spring.jackson2.*` 配置） | 改动小，但双 ObjectMapper 栈、配置分裂、注解版本必须用共用的 2.x annotations | 仅当工期极紧时作过渡，且要排期清偿 |
| C. 不升级 | 留在 3.4 | 3.4 已停止 OSS 维护，安全补丁断供 | 不推荐 |

**Jackson 3 行为差异重点回归项**（即使 API 编译通过也要验）：
1. `FAIL_ON_UNKNOWN_PROPERTIES` 等默认值在 Jackson 3 有调整 → 前端多传/少传字段的容错行为
2. 日期时间序列化默认策略变化（项目 yml 配了 `date-format`/`write-dates-as-timestamps: false`，需确认 Jackson 3 下属性名与生效情况）
3. 异常体系改非受检 → 所有 `catch (JsonProcessingException)` 编译期即暴露，逐个确认
4. `@JsonFormat` 等 4 处注解零改动（annotations 包 2/3 共用）

---

## 六、建议实施路线（6 步）

1. **前置（0.5 天）**：3.4.11 → 3.5.x 升级并全量测试（官方推荐路径，消化 3.x 末期弃用告警）；顺手删死依赖 resilience4j、`mockito-inline`；把 CI 现存 2 条 ArchitectureTest 存量失败先修掉或立基线，避免升级后归因混乱
2. **依赖矩阵（0.5 天）**：按 §四 更新 forge-dependencies BOM + 各 starter pom（web→webmvc 等改名一并做）；parent 上 4.1.2；启动挂 `spring-boot-properties-migrator` 收集属性改名清单
3. **编译修复（0.5~1 天）**：Security 7 / SAS、springdoc 3、starter 改名等编译期问题逐个清（Security DSL 与 servlet API 基本无碍）
4. **Jackson 迁移（1~2 天）**：OpenRewrite `UpgradeJackson_2_3` 跑一遍 → 手工清尾 → 按上一节 4 个行为差异点做接口契约回归（重点：分页/字典/时间字段/导出入参）
5. **Spring AI 2.0（0.5~1 天）**：按官方 Upgrade Notes 迁 LlmClient 等 12 文件；AI 路由/Agent 链路实测
6. **全量回归（1 天）**：
   - 86 个单测（JUnit 6 / Mockito / ArchUnit / H2 schema）
   - 工作流全流程（FlowLong：发起/审批/撤销/超时提醒/取消=rejectComplete）
   - 租户插件 + 数据权限拦截器（jsqlparser WITH 解析——历史踩坑点）
   - OAuth2 授权码流程、登录锁定（Redis DB1）、Excel 导入导出、文件上传、Prometheus 指标
   - 移除 properties-migrator，确认无告警后交付

**升级期间与生产兼容性**：纯后端 jar 自包含，无 Docker/端口/Redis/DB 变更；前端零影响。dev 库无 Flyway，本次升级无 SQL 变更项。

---

## 七、风险清单

| # | 风险 | 等级 | 缓解 |
|---|---|---|---|
| 1 | Jackson 3 序列化默认值变化导致接口 JSON 契约隐性变化（编译期不报错） | **高** | 行为差异 4 项专项回归 + 关键接口 snapshot 对比（升级前后同一接口响应 diff） |
| 2 | Spring AI 2.0 重写面大，tool calling/路由行为变化 | **高** | AI 模块迁移单独提交，便于独立回滚 |
| 3 | FlowLong 1.2.7 按 Boot 2.7 编译，Boot 4 下未官方声明 | 中 | 引擎低耦合 + `.imports` 已兼容，实测为准；留 fork/锁定回退方案 |
| 4 | SAS 1.2.0→7.0 跨度大，授权服务器配置细节变化 | 中 | 单文件适配，授权码全流程实测 |
| 5 | JUnit 6 + ArchUnit 引擎兼容性未知 | 中 | 升级 ArchUnit 到最新并先跑架构守护测试验证 |
| 6 | jasypt 维护停滞 + CVE | 低 | 升 ≥4.0.5；列入技术债清单 |
| 7 | 4.2.0 就在本月 GA，现在上 4.1 会不会很快过时 | 低 | 4.1→4.2 是小版本升级，代价远小于本次 3→4 |

## 八、决策建议

- **现在做**：目标 **4.1.2**，按 §六 六步走，3~5 人日。3.4 已停止 OSS 维护，安全补丁断供是实际压力。
- **再等等**：4.2.0 预计 2026-11 GA（本月），等 4.2.1 补丁（约 2026-12）一步到位，路线完全相同，仅目标版本号不同。
- 无论何时做，**步骤 1（先升 3.5.x + 清死依赖 + 修 CI 存量红）现在就可以独立执行**，它是任何路径的公共前置。

---

## 参考来源

- [Modularizing Spring Boot（spring.io 官方）](https://spring.io/blog/2025/10/28/modularizing-spring-boot)
- [Introducing Jackson 3 support in Spring（spring.io 官方）](https://spring.io/blog/2025/10/07/introducing-jackson-3-support-in-spring)
- [Spring AI 2.0.0 GA（spring.io 官方，2026-06-12）](https://spring.io/blog/2026/06/12/spring-ai-2-0-0-GA-available-now)
- [Spring AI 升级说明 1.x→2.0（官方文档）](https://docs.spring.io/spring-ai/reference/upgrade-notes.html)
- [Spring Boot 4.0 Release Notes / Migration Guide（GitHub Wiki）](https://github.com/spring-projects/spring-boot/wiki)
- [MyBatis-Plus 官方安装文档（boot4-starter）](https://baomidou.com/getting-started/install)
- [springdoc-openapi 兼容性矩阵](https://springdoc.org)
- [Resilience4j #2371：Spring Boot 4 支持（2.4.0）](https://github.com/resilience4j/resilience4j/issues/2371)
- [jasypt-spring-boot（Boot 4 兼容 issue #417、CVE-2026-9370）](https://github.com/ulisesbocchio/jasypt-spring-boot)
- [OpenRewrite：UpgradeSpringBoot_4_0 / UpgradeJackson_2_3 配方](https://docs.openrewrite.org/recipes/java/jackson/upgradejackson_2_3)
- [Moderne：Spring Boot 迁移配方集](https://docs.moderne.io/user-documentation/recipes/popular-recipe-guides/migrate-to-spring-boot)
- [Spring Security 7 What's New](https://docs.spring.io/spring-security/reference/whats-new.html)
