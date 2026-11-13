# 插件模块（forge-module-plugin + forge-spring-boot-starter-plugin）使用手册

> 适用范围：`apps/forge-server/forge-framework/forge-spring-boot-starter-plugin`（契约层）、`apps/forge-server/forge-module-plugin`（管理模块）、`apps/forge-web/src/views/system/plugin`（前端）、`tools/license-cli`（离线签发 CLI）。
>
> 形态：**编译期模块插件**——插件是普通 Maven 模块随底座打包，启动时扫描 `PluginDescriptor` Bean 注册；「启停」控制的是**访问权**（菜单过滤 + 请求闸 + 数据门面），不是代码加载。未来热插拔的演进位：Descriptor/Guard/Resolver/DataApi 四契约不变，替换装配层即可。

## 目录

- [1. 模块概述](#1-模块概述)
- [2. 架构设计](#2-架构设计)
- [3. 前端页面](#3-前端页面)
- [4. API 端点](#4-api-端点)
- [5. 配置项](#5-配置项)
- [6. 数据库表结构](#6-数据库表结构)
- [7. License 体系](#7-license-体系)
- [8. 插件开发指南](#8-插件开发指南)
- [9. 安全注意事项](#9-安全注意事项)
- [10. 故障排查](#10-故障排查)

---

## 1. 模块概述

| 能力 | 说明 |
|------|------|
| 插件注册 | 启动时扫描全部 `PluginDescriptor` Bean，元数据 upsert `sys_plugin`；版本变化自动记操作日志（无人工确认环节） |
| 菜单同步 | 插件声明菜单树，按 `pluginMenuKey` 幂等 diff 导入 `sys_menu`（保留菜单 id 不断角色关联，自动授予内置超管角色） |
| 租户级启停 | 启用硬闸=有效 License；停用=菜单隐藏+接口拦截，**业务数据全量保留**，重新启用即恢复 |
| 平台总闸 | 一键禁用某插件对全部租户的访问（fail-closed，秒级生效） |
| License 校验 | 三要素：签名（RSA2048-SHA256）+ 租户 code + 时间窗；外加 scope ⊆ 插件声明集结构校验；每日 02:30 Quartz 巡检到期自动停用 |
| 在线签发（双模式） | 配置 `forge.plugin.license.private-key` → 页面直接签发（落库生效或下载 .lic）；不配置 → 仅导入（私有化交付姿势） |
| 数据门面 | `PluginDataApi`：员工/组织读默认放行；写须插件声明 scope 且被授予（管理端勾选 ∪ License 内置，越界裁剪回声明集） |
| 独立升级 | 插件独立 Maven artifact 独立版本号；底座触点仅 BOM 一行 |

## 2. 架构设计

```
forge-framework/forge-spring-boot-starter-plugin   ← 契约层（无 DB 依赖）
  PluginDescriptor        插件描述符 SPI（编码/版本/菜单树/scope 声明）
  PluginMenuNode/ScopeDefinition   声明模型
  @PluginGuard + Aspect   请求闸（fail-closed：无租户上下文/未启用即拒绝）
  PluginStateResolver     状态 SPI（切面消费）
  PluginMenuFilter        菜单可见性 SPI（system 菜单/权限过滤消费）
  PluginDataApi + DTO     数据门面契约
  LicenseVerifier/LicenseIssuer   验签/在线签发（canonical JSON，与 CLI 同规则）

apps/forge-server/forge-module-plugin              ← 管理模块（单模块形态）
  registry/   PluginRegistryRunner（启动注册+版本日志）+ PluginMenuSyncService（diff 同步）
  license/    PluginLicenseService（导入/签发/撤销/巡检）+ PluginLicenseCheckTask（Quartz）
  state/      PluginStateServiceImpl（Resolver+Filter 实现，Redis TTL 300s）+ PluginCacheService
  data/       PluginDataApiImpl + PluginScopeChecker（三路合并+裁剪）
  service/    PluginManageService（启停事务/总闸）
  controller/ 三套管理接口
```

**菜单可见性合成**（复用套餐过滤模式，递归 CTE 零改动）：

```
用户可见 = 角色菜单 ∩ 租户套餐 ∩ 插件启用过滤（plugin_code 非空 → 该租户已启用）
权限码 = sys_menu.permission 链路叠加 NOT IN 排除集（SysUserMapper foreach）
请求期 = @PluginGuard 切面 → Redis 状态缓存 → sys_plugin(总闸) × sys_plugin_tenant(租户开关)
```

**依赖方向**：`system-biz → starter-plugin`（仅 PluginMenuFilter 契约，Java 层过滤）；`forge-module-plugin → system-biz`（菜单/用户/部门服务委托）；插件模块 → starter-plugin。

## 3. 前端页面

系统管理 → 插件管理（菜单 id 2600，权限点 `plugin:list`）：

| 页面/组件 | 说明 |
|-----------|------|
| `views/system/plugin/index.vue` | 插件列表（vxe）：编码/运行版本/提供方/最近变更；操作=租户授权/License 管理/平台总闸 |
| `components/TenantAuthDrawer.vue` | 授权抽屉：**全部启用租户** LEFT JOIN 启停状态（无记录=开关关）；License 到期标签（7 天预警）；展开行 scope 勾选 |
| `components/ScopeCheckGroup.vue` | scope 勾选组：只展示插件声明集，读类默认项不可取消，License 内置只读，勾选=授予/取消=回收实时生效 |
| `components/LicenseDrawer.vue` | License 列表（有效期按天）+ 导入（一码多插件原子导入）+ 在线签发入口（能力探测，未配私钥隐藏） |
| `components/IssueLicenseDialog.vue` | 签发表单：租户/插件多选/被授权方/有效期（按天，到期日收口 23:59:59）/scope 勾选/编号自动生成；签发生效或下载 .lic |

## 4. API 端点

| 方法 | 路径 | 权限 | 说明 |
|------|------|------|------|
| GET | `/admin-api/plugin/page`、`/plugin/list` | plugin:list | 注册表分页/全量 |
| GET | `/admin-api/plugin/{code}/tenants` | plugin:list | 全部启用租户的启停/授权/到期（未操作行 enabled=0） |
| POST | `/admin-api/plugin/{code}/tenants/{tenantId}/enable` | plugin:enable | 启用（无有效 License 报 LICENSE_REQUIRED） |
| POST | `/admin-api/plugin/{code}/tenants/{tenantId}/disable` | plugin:disable | 停用（菜单隐藏数据保留） |
| PUT | `/admin-api/plugin/{code}/global-status` | plugin:global | 平台总闸 |
| GET | `/admin-api/plugin/my` | 登录 | 本租户启用插件（租户视角只读） |
| GET | `/admin-api/plugin/license/page` | plugin:list | License 记录分页 |
| POST | `/admin-api/plugin/license/import` | plugin:license:import | 上传 .lic 导入（@RepeatSubmit） |
| POST | `/admin-api/plugin/license/{id}/revoke` | plugin:license:revoke | 撤销并联动停用 |
| GET | `/admin-api/plugin/license/issue/form-data` | 登录 | 签发表单元数据+能力开关 |
| POST | `/admin-api/plugin/license/issue` | plugin:license:issue | 在线签发（activate 落库/仅下载） |
| GET | `/admin-api/plugin/scope/list` | plugin:list | 声明集+授予态+来源 |
| POST | `/admin-api/plugin/scope/grant`\|`revoke` | plugin:scope:grant | 勾选授予/回收 |

错误码段 `60xx`（ResultCode）：PLUGIN_NOT_FOUND/PLUGIN_GLOBAL_DISABLED/PLUGIN_DISABLED/LICENSE_REQUIRED/LICENSE_INVALID/LICENSE_TENANT_MISMATCH/LICENSE_PLUGIN_UNKNOWN/LICENSE_EXPIRED/LICENSE_NOT_YET_VALID/LICENSE_DUPLICATED/LICENSE_SCOPE_NOT_DECLARED/SCOPE_DENIED/SCOPE_NOT_DECLARED。

## 5. 配置项

| 配置 | 默认 | 说明 |
|------|------|------|
| `forge.plugin.license.public-key` | classpath:plugin-license-public.key | 验签公钥（Base64 X.509） |
| `forge.plugin.license.private-key` | 无 | **在线签发开关**：配置即启用页面签发（建议 jasypt `ENC(...)`）；私有化交付绝不配置 |
| `forge.tenant.ignore-tables` | 已含 sys_plugin×4 | 插件四表为全局共享表，tenant_id 显式列 |

dev profile 已内置开发私钥（env `PLUGIN_LICENSE_PRIVATE_KEY` 可覆盖）。Redis 缓存键：`plugin:state:{tenantId}:{code}`、`plugin:scope:{tenantId}:{code}`、`plugin:codes:{tenantId}`（TTL 300s，变更主动 evict；菜单缓存全清 + userAuth 全清）。

## 6. 数据库表结构

| 表 | 说明 | 关键约束 |
|----|------|---------|
| `sys_plugin` | 注册表（启动维护） | uk(plugin_code,deleted)；running_version/global_status 总闸/menus_synced |
| `sys_plugin_tenant` | 租户启停 | uk(tenant_id,plugin_code,deleted)；enabled/disabled_reason(手动/license_expired/license_revoked) |
| `sys_plugin_license` | License 记录（一码多插件拆行） | uk(license_key,plugin_code,deleted)；status 1有效/0失效/2已替换；scopes CSV |
| `sys_plugin_scope` | 管理端勾选授予（License 内置不落表动态合并） | uk(tenant_id,plugin_code,scope,deleted) |
| `sys_menu` +2 列 | plugin_code/plugin_menu_key 归属标记 | idx 普通索引，同步逻辑幂等 |

墓碑列均为 `BIGINT + delval="id"`（uk 含 deleted 列）。升级 SQL：`sql/upgrade-20261113-plugin-module.sql`（存量库手工执行）。

## 7. License 体系

**文件格式**（与 CLI/在线签发完全互通）：

```json
{ "payload": "<Base64(canonical JSON)>", "signature": "<Base64(RSA2048-SHA256)>" }
```

payload 必填：licenseKey/pluginCodes[]/tenantCode/licensee/issuedAt/notBefore/notAfter（ISO-8601 含时区）；可选 scopes[]（⊆ 声明集）、extra{}。**不做任何版本约束**（卡底座版本=循环校验零防御力，卡插件小版本=升级要换发）。

**两种签发姿势**：

| 姿势 | 操作 | 私钥位置 |
|------|------|---------|
| 在线签发（自营） | 页面表单，签发生效/下载 | 服务端 yml（jasypt ENC） |
| 离线 CLI（私有化交付） | `tools/license-cli`：`java KeyGen.java` 生成密钥对 → `java IssueLicense.java payload.json private-key.b64 out.lic` | 运营方离线保管（如 ~/.forge-license-dev/，永不入仓库） |

**动线**：签发/导入 License → 插件管理·租户授权 → 打开启用开关 → 菜单出现（套餐需勾选插件菜单租户才可见）。到期前 7 天列表预警；到期巡检（sys_job id=3，02:30）自动停用并清缓存。

## 8. 插件开发指南

> 📖 **手把手完整教程见 [plugin-development-guide.md](./plugin-development-guide.md)**（含可复制的全量示例代码、验证清单与排错表），本节仅速览四步。

以 demo（`forge-module-demo`）为标准范例，四步接入：

1. **建模块**：Maven 模块依赖 `forge-spring-boot-starter-plugin`（+ 所需业务 starter），加入根 pom/BOM/forge-server 依赖三处聚合。
2. **写描述符**：实现 `PluginDescriptor` 为 `@Component`：pluginCode（kebab-case 唯一）/name/version（SemVer）/menus()（PluginMenuNode 树，parentKey 挂声明内节点，根节点 parentKey=null）/declaredScopes()（ScopeDefinition.readOnly=默认放行、writable=须授予）。
3. **加请求闸**：插件 Controller 类上 `@PluginGuard("你的编码")`；读写底座数据一律注入 `PluginDataApi`（禁止直连 sys_user/sys_dept Mapper）。
4. **前端页面**：放 `views/<插件>/` 下，componentPath 与菜单声明一致（随 forge-web 主包编译，停用仅隐藏路由）。

启动即自动注册+菜单导入+超管授权；租户可见性=套餐勾选 ∩ 插件启用。升级=改模块版本重新打包（版本变化自动记操作日志），插件 SQL 放模块内 `db/migration/` + 根 `sql/upgrade-*.sql`。

## 9. 安全注意事项

- **威胁模型**：防客户侧篡改载荷/越权使用/到期不续费；**防不住持有私钥者**——私钥离线保管，永不入仓库/部署包。
- 三道闸正交：`@PluginGuard`（租户×插件）→ `@PreAuthorize` RBAC（人×功能）→ scope 门面（插件×数据）。
- scope 四层收口：勾选界面只渲染声明集 → grant 接口验声明集 → License 导入验声明集（原子） → 运行期合并后再裁剪回声明集。
- 在线签发私钥建议 jasypt 加密；轮换=全部存量 License 失效，慎重。

## 10. 故障排查

| 现象 | 排查 |
|------|------|
| 签发了 License 仍看不到菜单 | 签发≠启用：插件管理→租户授权→打开开关；确认租户套餐已勾选插件菜单 |
| 租户授权抽屉无数据 | 已修复（0bf66332）：现为全部启用租户 LEFT JOIN；仍空则查 sys_tenant status=1 是否有租户 |
| 菜单同步报「声明无效」 | 描述符 parentKey 不在声明集 / menuKey 重复 / 父子成环，看启动日志 ERROR |
| 手工标记的插件菜单被删 | pluginMenuKey 与描述符声明不一致被当孤儿清理——菜单管理页可补标记，键必须与声明一致 |
| 启用报「无有效授权」 | 查 sys_plugin_license：status=1 且 not_before≤now<not_after |
| 插件接口 403 PLUGIN_DISABLED | 切面拦截：租户未启用/总闸禁用/异步线程无租户上下文 |
| 在线签发按钮不出现 | 未配置 forge.plugin.license.private-key（能力探测 enabled=false，属预期） |
