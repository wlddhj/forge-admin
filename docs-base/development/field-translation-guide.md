# 字段翻译与字典渲染指南

> 定位：列表/详情接口「ID → 名称」类字段翻译的**三条链路分工与落地写法**——新模块开发时按第 0 节选型表直接对号入座。
>
> 适用范围：后端响应 DTO 设计（easy-trans 基础表翻译）、后端字典 label 组装（`DictLabelUtils`）、前端字典渲染（`useDict`）。
>
> 配套文档：[新模块开发向导](./module-development.md)、[后端开发规范](./backend-development-specification.md)、[前端开发规范](./frontend-development-specification.md)（第 8 节数据字典）。

---

## 0. 三条链路选型表

| 场景 | 用哪条链路 | 示例 |
|------|-----------|------|
| 基础表外键 → 名称（部门/分类/表单等**一条数据库记录的字段**） | easy-trans `@Trans(type = TransType.SIMPLE)`，见第 2 节 | `deptId → deptName`、`categoryId → categoryName` |
| 字典码值 → label，**前端渲染** | 前端 `useDict` + `<dict-value>`（见 [前端开发规范第 8 节](./frontend-development-specification.md#8-数据字典使用指南)） | 状态列、下拉框 |
| 字典码值 → label，**后端组装**（导出、打印、业务单据视图、IM/推送文案、给第三方的数据） | `DictLabelUtils.resolve / resolveMap`，见第 4 节 | `status → "审批中"` |
| 字典码值 → label，**响应 DTO 直接出 label 字段**（前端免翻译直接展示、接口对外自带文案） | easy-trans `@Trans(type = TransType.DICTIONARY)`，见第 3 节 | `approveStatus → approveStatusDictLabel` |

**一句话记忆：跨表查名称用 SIMPLE；字典码值前端渲染用 useDict、后端组装用 DictLabelUtils、响应 DTO 要现成 label 字段用 DICTIONARY——三选一按数据流向定，同一字段不要双链路重复输出。**

---

## 1. easy-trans 接入架构（现状盘点）

### 1.1 依赖与版本

版本统一由 `forge-dependencies` 管理：`easy-trans.version = 4.0.2`，坐标为 **`org.dromara`**（easy-trans 迁入 dromara 后的新包名，注解在 `org.dromara.core.trans.*`，**不是**老文档常见的 `com.fhs.trans.*`）。

| 依赖 | 引入位置 | 作用 |
|------|---------|------|
| `easy-trans-anno` | `forge-common`（默认引入） | 提供 `@Trans`/`TransPojo` 等注解——**任何模块的 api/biz 包直接可用，无需再加依赖** |
| `easy-trans-spring-boot-starter` | `forge-spring-boot-starter-mybatis` | 核心服务（`TransService`、全局响应拦截、字典/枚举服务） |
| `easy-trans-mybatis-plus-extend` | `forge-spring-boot-starter-mybatis` | SIMPLE 类型的 MP 驱动（`MybatisPlusSimpleTransDiver`） |

业务模块通过依赖 `forge-spring-boot-starter-mybatis` 自动获得全部能力，**零配置接入**。

### 1.2 配置（`forge-server/src/main/resources/application.yml`）

```yaml
easy-trans:
  is-enable-redis: true       # 翻译缓存接入 Redis
  is-enable-global: true      # 全局翻译：拦截所有 ResponseBody 自动翻译（主通道）
  is-enable-tile: true        # 平铺模式：翻译结果写回 VO 字段
  dict-use-redis: true        # 字典翻译缓存放 Redis
  is-enable-map-result: true  # Map 类型结果也参与翻译
  mp-new: false               # ⚠️ 注释称 MP≥3.5.3.2 应设 true，当前 MP 3.5.17 + false 实测可用；升级 easy-trans/MP 时优先核对此项
```

### 1.3 全局生效链路

```
Controller 返回 Result<PageResult<XxxResponse>>
  → EasyTransResponseBodyAdvice（is-enable-global 注册的 ResponseBodyAdvice）
  → TransUtil.transOne 递归遍历对象树（Map / Collection / POJO 字段都下钻，
     自动穿过 Result 与 PageResult 包装，找出所有 TransPojo 实例）
  → SimpleTransService 按 @Trans 声明分组、同目标实体的 id 去重合并
  → MybatisPlusSimpleTransDiver：SqlHelper.sqlSession() + QueryWrapper.in(主键, ids)
     经 BaseMapper.selectList 查询目标实体
  → 平铺模式回填：DTO 有接收字段则 setter 写入；没有则动态补充字段
```

关键行为：

- **批量合并**：同一页 50 行的 50 个 `deptId` 合并为一条 `WHERE id IN (...)`，不产生逐行查询。
- **异常静默降级**：翻译抛错只记日志（「翻译错误」），不影响接口响应——也因此翻译配置错误**不会报错，只会字段为 null**，排查见第 7 节。
- **方法级开关**：Controller 方法标 `@IgnoreTrans` 跳过翻译；标 `@TransSett(include/exclude)` 控制只翻译/排除指定字段。

### 1.4 项目自有封装

`forge-spring-boot-starter-mybatis` 的 `com.forge.framework.mybatis.trans` 包：

- `MyTranslateAutoConfiguration`：启动时把 easy-trans 的 `TransService` 注入静态工具。
- `TranslateUtils.translate(List<T extends VO>)`：**手动触发翻译**，用于全局拦截够不到的场景（异步线程、导出、定时任务组装数据）。当前项目内暂无调用方，属备用通道。

---

## 2. 基础表字段翻译（`TransType.SIMPLE`）——新模块标准用法

### 2.1 响应 DTO 写法（四要素）

以现有实例为准（`forge-module-system` 的 `UserResponse`）：

```java
@Data
public class UserResponse implements TransPojo {   // ① 必须 implements TransPojo

    @Trans(type = TransType.SIMPLE,                // ② 外键字段标 @Trans
           target = SysDept.class,                 //    目标实体类
           fields = {"deptName"},                  //    要取目标实体的哪些字段
           refs = {"deptName"})                    //    依序回填到本 DTO 的哪些字段
    private Long deptId;

    private String deptName;                       // ③ 预留接收字段（推荐显式声明）
    // ...
}
```

- import 均来自 `org.dromara.core.trans.*`（`anno.Trans`、`constant.TransType`、`vo.TransPojo`）。
- **多字段对位**：`fields` 与 `refs` 数组按**下标一一对应**。工作流模块 `ModelResponse` 的实例：

```java
@Trans(type = TransType.SIMPLE, target = WfCategory.class,
       fields = {"categoryCode", "categoryName"},   // 目标字段
       refs = {"category", "categoryName"})         // 本类字段，顺序对位
private Long categoryId;
private String category;
private String categoryName;

@Trans(type = TransType.SIMPLE, target = WfForm.class,
       fields = "name", ref = "formName")           // 单字段用单数 ref
private Long formId;
private String formName;
```

- **④ 接收字段建议显式预留**（项目现有风格）：类型明确、前端契约清晰。不预留时平铺模式会动态补充字段，但 DTO 上看不见契约，不利于维护。

现有实例清单（照抄即可）：

| DTO | 翻译 | 文件 |
|-----|------|------|
| `UserResponse` | `deptId → SysDept.deptName` | `forge-module-system-api` `dto/user/UserResponse.java` |
| `ModelResponse` | `categoryId → WfCategory{category, categoryName}`、`formId → WfForm.name` | `forge-module-workflow-api` `dto/model/ModelResponse.java` |
| `ProcessDefinitionResponse` | `categoryId → WfCategory.categoryName` | `forge-module-workflow-api` `dto/definition/ProcessDefinitionResponse.java` |

### 2.2 目标实体的硬性要求

被 `target` 指向的实体（如 `SysDept`、`WfCategory`、`WfForm`）必须同时满足：

1. **`implements TransPojo`**（`SysDept.java:16` 等）；
2. **标准 MP 实体**：`@TableName` + `@TableId` 主键（驱动经 MP `TableInfo` 解析主键列）；
3. **存在已注册的 `BaseMapper`**（如 `SysDeptMapper`）——驱动通过 `SqlSession.getMapper` 取 Mapper 执行查询，实体没有 Mapper 则翻译**静默不生效**；
4. 主键类型与 DTO 外键字段类型匹配（Long 对 Long）；类型不匹配时可用 `@Trans(pkeyClassType = ...)` 声明。

### 2.3 查询语义（租户与逻辑删除自动生效）

翻译查询走 `BaseMapper.selectList` + `QueryWrapper.in(主键列, ids)`，即**完整经过 MyBatis-Plus 拦截器链**：

- 目标实体含 `@TableLogic` 字段 → 自动过滤已删除记录（`SysDept`/`WfCategory`/`WfForm` 均有）；
- 目标表含 `tenant_id` 列 → 租户插件自动追加隔离条件（如 `sys_dept`）；全局表（如 `wf_category` 无 tenant_id）则不过滤；
- 全局翻译发生在**请求线程**的响应序列化阶段，`TenantContextHolder` 有值，租户语义正确。

### 2.4 缓存行为

- 目标实体**未标 `@AutoTrans`**（项目现状）→ 无跨请求缓存，每个响应独立查询（同响应内去重合并）。
- 基础表数据量小、按主键 IN 查询，一般无需缓存；确有热点再评估 `@AutoTrans(useCache = true)`（注意引入缓存失效问题，需同步刷新策略）。
- easy-trans 自身的 auto 模式（`easy-trans.is-enable-auto`，扫描 `*.service.impl` 注册 `@AutoTrans`）**未开启**，勿与 SIMPLE 混淆。

---

## 3. 字典注解翻译链路：`TransType.DICTIONARY`

字典写路径刷新钩子（`DictTransCacheRefresher`，2026-11-18 补齐）落地后，easy-trans 的字典翻译已**正式可用**——适合「响应 DTO 想直接携带 label 字段」的场景：前端免翻译直接展示、接口对外自带文案。

### 3.1 写法（demo 模块实例）

```java
@Data
public class ProjectResponse implements TransPojo {

    /** 字典类型常量定义在 DTO：@Trans(key=) 要求编译期常量，且消除 DTO 对 Service 的反向引用 */
    public static final String STATUS_DICT_TYPE = "demo_project_status";

    @Trans(type = TransType.DICTIONARY, key = STATUS_DICT_TYPE, ref = "approveStatusDictLabel")
    private String approveStatus;                 // 码值字段（源）

    private String approveStatusDictLabel;        // ref 指向的接收字段（响应阶段被填充）
}
```

要点：

- 注解标在**码值字段**上，`key` = 字典类型，`ref` = 本 DTO 接收 label 的字段名；
- import 同样来自 `org.dromara.core.trans.*`；DTO 须 `implements TransPojo`；
- 翻译只查**启用项**（与 `useDict` 数据源同口径）；未命中（字典未配置/全停用/码值不存在）label 字段为 **null**；
- 同一码值不要同时上 SIMPLE 与 DICTIONARY 双注解，一条链路足够。

### 3.2 刷新机制（为什么现在可用）

- **唯一刷新出口**：`forge-module-system-biz` 的 `com.forge.common.cache.DictTransCacheRefresher`——字典数据增/改/删/停启/批量保存（`SysDictDataServiceImpl`）与类型删除、编码变更（`SysDictTypeServiceImpl`）全部挂钩；
- **时序**：事务 **afterCommit** 后刷新（对齐仓库失效钩子纪律，杜绝「事务未提交其他请求已回填旧值」）；刷新经 `SysDictDataService` 代理重查（`@CacheEvict` 已清 → 重查回填 Redis）；
- **多实例一致性**：框架内建 Redis pub/sub（`refreshCacheAndNoticeOtherService` / `removeDictGroupAndNoticeOtherService`），本实例刷新后广播其他实例；Redis 故障时降级为仅本实例生效（warn 日志），其余实例陈旧至下次刷新；
- **启动加载**：`MyApplicationListener`（`ApplicationReadyEvent`）调 `refreshAll()` 全量重建；
- **手动刷新**：管理页「刷新字典缓存」（`DELETE /system/dict/type/cache`）同时全量重建翻译缓存；
- **双层缓存分工不变**：`DictLabelUtils`（§4）与 easy-trans 字典缓存是两套独立缓存，字典写路径**两条都刷**（前者 evict、后者重建），互不影响。

### 3.3 与 `DictLabelUtils` 的语义差异（选型依据）

| | `@Trans(DICTIONARY)` | `DictLabelUtils` |
|---|---|---|
| 生效时机 | 响应序列化阶段自动填充 | 业务代码手动调用 |
| 未命中语义 | label 字段 null | `resolve` null / `resolveOrDefault` 回退 |
| 适用 | 返回给前端的响应 DTO | 导出/打印/推送/单据视图等后端组装 |
| 缓存刷新 | 写路径 afterCommit 重建 | 写路径即时 evict + 60s TTL 收敛 |

demo 模块 `ProjectResponse` 同时保留 `approveStatusName`（手动组装，码值回退）与 `approveStatusDictLabel`（注解翻译，未命中 null）两字段并存示范，对照两种语义。

---

## 4. 后端字典组装：`DictLabelUtils`（唯一出口）

`forge-module-system-biz` 的 `com.forge.modules.system.util.DictLabelUtils`——后端任何模块需要「码值 → label」时统一走它，**禁止在业务代码里各自写 switch/if 硬编码映射**。

三级缓存链：本地 Guava（60s TTL，O(1)）→ Redis（`@Cacheable("dictData")`，1h）→ DB；并发未命中按 dictType in-flight 去重；只含启用项；字典侧异常降级为未命中不外抛。

| API | 用途 |
|-----|------|
| `resolve(dictType, value)` | 单值解析，未命中返回 `null` |
| `resolveOrDefault(dictType, value, default)` | 单值解析带回退 |
| `resolveMap(dictType)` | 整字典 `value → label` 映射——**列表页组装首选**，一页一次，行内查 Map |

标准范式（`forge-module-demo` 的 `ProjectApprovalServiceImpl`，分页列表组装 `statusLabel`）：

```java
// 页级一次字典解析（resolveMap），行内查 Map 免逐行查询
Map<String, String> statusLabels = DictLabelUtils.resolveMap(STATUS_DICT_TYPE);
result.setRecords(page.getRecords().stream()
        .map(p -> toResponse(p, statusLabels))
        .toList());
```

**写路径失效义务**：`DictLabelUtils` 本地缓存依赖字典写路径调用 `DictLabelUtils.evictLocalCache()` 失效。现有写入口（增删改/状态切换/批量保存/手动刷新）均已接入 `SysDictDataServiceImpl`；若**新增字典写入口**（如新的批量导入、SQL 修正脚本之外的代码路径），必须同步调用 `evictLocalCache()`（多实例部署下其余实例靠 60s TTL 收敛）。

---

## 5. 前端渲染：`useDict`

页面上的字典渲染（表格状态列、下拉框、标签）统一走前端 `useDict` composable + `<dict-value>` 组件，写法与缓存机制见 [前端开发规范第 8 节](./frontend-development-specification.md#8-数据字典使用指南)。要点：

- 模块级共享缓存 + in-flight 去重，表格 N 行只有 1 次请求；
- **新增字典写入口须调 `reloadDictCache(type)`**（与后端 `evictLocalCache` 对应的前端义务）；
- 后端 DTO **默认**不需要为前端渲染准备字典 label 字段——前端拿码值自己翻；仅当接口确需自带文案（§3 场景）时才用 `@Trans(DICTIONARY)` 出 label 字段，这是与 easy-trans SIMPLE 翻译（后端出 `xxxName` 字段）的分工边界。

---

## 6. 新模块接入 checklist

设计响应 DTO 时逐条核对：

- [ ] 外键 → 基础表名称：DTO `implements TransPojo` + 外键字段标 `@Trans(SIMPLE)` + 显式预留接收字段（§2.1）
- [ ] 目标实体满足四要件：`TransPojo` + `@TableName`/`@TableId` + 已注册 `BaseMapper` + 主键类型匹配（§2.2）
- [ ] 字典枚举字段：默认前端渲染走 `useDict`，不为此在 DTO 加 label 字段（§5）
- [ ] 响应 DTO 需要**现成 label 字段**（对外接口自带文案/前端免翻译）→ 码值字段标 `@Trans(DICTIONARY)` + `ref` 接收字段（§3.1）
- [ ] 后端需要组装字典 label（导出/打印/推送/单据视图）→ `DictLabelUtils.resolveMap`（§4）
- [ ] 同一码值字段勿双链路重复输出（useDict 渲染 + DTO label 字段二选一；SIMPLE/DICTIONARY 不叠加）（§3.3）
- [ ] 非常规场景（异步线程/定时任务/MQ 消费者组装数据给前端）→ 手动 `TranslateUtils.translate(list)`，且先确认租户上下文已传递（§7）

---

## 7. 踩坑与注意事项

1. **翻译失败是静默的**：字段 null 不报错。排查顺序：DTO 是否 `implements TransPojo` → `@Trans` 参数拼写 → 目标实体有无 `BaseMapper` → 目标表数据是否被租户/逻辑删除条件滤掉。
2. **`fields`/`refs` 按下标对位**，错位会张冠李戴（code 填进 name 字段），code review 重点看。
3. **异步线程手动翻译的租户上下文**：`TranslateUtils.translate` 在非请求线程（定时任务、MQ 消费者、`@Async`）调用时，若线程内没有租户上下文，目标表含 `tenant_id` 时查不到数据（或需按 IM 通知模块的既有做法 `@TenantIgnore` + 显式 `tenant_id` 处理，参见 `workflow-im-notify` 模块的异步实践）。
4. **SSE / 流式接口不走全局翻译**：`EasyTransResponseBodyAdvice` 只拦普通 ResponseBody；SSE 逐帧推送的数据需要发送前手动 `TranslateUtils.translate`。
5. **`mp-new: false` 与 MP 3.5.17**：按 easy-trans 官方注释，MP ≥ 3.5.3.2 建议 `true`；当前组合实测可用，**升级 easy-trans 或 MP 时优先回归用户/流程模型列表的翻译字段**。
6. **导出/对外接口的字典文案**：优先 `DictLabelUtils` 而非 `@Trans(DICTIONARY)`——导出文件/推送文案在业务线程组装，走 DictLabelUtils 才有「回退码值」语义且不依赖响应拦截。
7. **跨模块引用目标实体**：`target = SysDept.class` 需要 api 层依赖——模块间依赖走 `xxx-module-api`（如工作流模块引用 system-api 的实体），biz 层不得跨模块引 biz。
8. **DICTIONARY 的 `key` 必须是编译期常量**：字典类型字符串定义在 DTO 常量（参考 `ProjectResponse.STATUS_DICT_TYPE`），不要引用 Service 的常量（反向依赖）。
9. **DICTIONARY 未命中是 null 不是回退**：需要「字典缺失时显示码值本身」的场景用 `DictLabelUtils.resolveOrDefault`（§3.3 对照表）。
10. **多实例部署的字典翻译一致性依赖 Redis pub/sub**：Redis 故障窗口内其他实例翻译陈旧（warn 日志可见「刷新失败」），恢复后手动「刷新字典缓存」即可收敛。
