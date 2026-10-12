# 新模块开发向导

> 定位：新建业务模块的**端到端流程入口**——按顺序走完本文步骤即可落地一个新模块。
>
> 代码细节规范不在本文重复展开，按需查阅：

| 文档 | 覆盖内容 |
|------|---------|
| [后端开发规范](./backend-development-specification.md) | 模块结构、Maven、Entity/Mapper/DTO/Service/Controller 模板、横切关注点、迁移与菜单 SQL |
| [前端开发规范](./frontend-development-specification.md) | API 文件、页面模板、vxe-table、弹窗/抽屉、样式、数据字典、检查清单 |
| [数据库设计规范](./database-design-specification.md) | 表/字段/索引设计、迁移脚本、菜单与字典数据规范 |

---

## 1. 开发流程总览

```
创建模块结构 → Maven 注册 → 后端开发（实体→DTO→Mapper→Service→Controller）
→ 数据库迁移脚本 → 菜单与权限 SQL → 编译验证 → 前端开发（API→页面→弹窗）
```

模块结构较复杂时，优先使用脚手架脚本（见下节）生成骨架，再按规范填充业务代码。

---

## 2. 创建模块结构

### 2.1 脚手架方式（推荐）

```bash
node scripts/create-module.js <模块名称> "<模块描述>"
# 示例：node scripts/create-module.js order "订单管理模块"
```

自动创建模块目录结构（api/biz）、pom.xml 配置、基础类模板（Entity、DTO、Controller、Service、Mapper）、数据库迁移脚本模板。

### 2.2 手工方式

按 [后端开发规范](./backend-development-specification.md) 第 1 章创建 `forge-module-{module}` 双模块目录（api 层放实体与 DTO，biz 层放 Controller/Service/Mapper）。

删除模块：`./scripts/remove-module.sh <模块名称>`（自动处理 pom 引用与迁移脚本，交互确认）。

---

## 3. Maven 注册

手工创建的模块需完成四步注册（模板见 [后端开发规范](./backend-development-specification.md) 第 2 章）：

1. 根聚合 POM：`apps/forge-server/pom.xml` 的 `<modules>` 添加模块
2. `forge-dependencies/pom.xml` 的 `<dependencyManagement>` 添加 api/biz 依赖声明
3. `forge-server/pom.xml` 的 `<dependencies>` 添加 biz 依赖
4. 运行 `mvn clean compile` 验证编译

---

## 4. 后端开发

按以下顺序编写，各层模板与要点见 [后端开发规范](./backend-development-specification.md) 第 3 章：

1. **Entity**（api 模块）：实体类对应数据库表
2. **DTO**（api 模块）：Request / QueryRequest / Response
3. **Mapper**（biz 模块）：接口 + XML（如有复杂 SQL）
4. **Service**（biz 模块）：接口 + 实现，写操作加 `@Transactional`
5. **Controller**（biz 模块）：后台管理放 `controller/admin/`，移动端放 `controller/app/`

横切关注点（操作日志、数据权限、限流、缓存）按需叠加，见后端规范第 4 章。

多租户注意：新增业务表需加 `tenant_id` 列 + 索引，业务实体继承 `TenantBaseDO`，详见项目 CLAUDE.md「多租户架构」。

---

## 5. 数据库迁移脚本

- 位置：模块自己的 `forge-module-{module}-biz/src/main/resources/db/migration/`
- 命名：`V{YYYYMMDD}{seq}__<description>.sql`
- 项目未引入 Flyway，脚本需手工按版本号顺序执行
- 表结构模板与规范见 [后端开发规范](./backend-development-specification.md) 第 5 章；字段/索引设计细节见 [数据库设计规范](./database-design-specification.md)

---

## 6. 菜单与权限 SQL

- 按「目录 → 菜单 → 按钮」三层插入 `sys_menu`，并给管理员角色分配 `sys_role_menu`
- SQL 模板见 [后端开发规范](./backend-development-specification.md) 第 6 章
- 菜单 component 与前端页面路径对应：`/views/{module}/{entity}/index`

---

## 7. 前端开发

按以下顺序编写，各部分模板与要点见 [前端开发规范](./frontend-development-specification.md)：

1. **API 定义**（`src/api/{module}/{entity}.ts`）：接口定义 + 类型 + 请求函数
2. **页面组件**（`src/views/{module}/{entity}/index.vue`）：搜索 + 列表 + 弹窗编排
3. **弹窗/抽屉**（`src/views/{module}/{entity}/components/`）：表单弹窗、详情抽屉等独立组件
4. **数据字典**：枚举字段先插入字典 SQL，再在 `constants/dict.ts` 注册常量
5. **路由**：通常由菜单自动生成，无需手工配置

---

## 8. 新模块开发检查清单

创建新模块时，确保完成以下步骤：

### 后端

- [ ] 创建模块目录结构（api + biz）
- [ ] 配置 Maven pom.xml（父模块、api、biz、dependencies、forge-server）
- [ ] 创建实体类（api 模块）
- [ ] 创建请求/响应 DTO（api 模块）
- [ ] 创建 Mapper 接口（biz 模块）
- [ ] 创建 Service 接口和实现（biz 模块）
- [ ] 创建 Controller（biz 模块，放在 `controller.admin` 或 `controller.app` 包下）
- [ ] 运行 `mvn clean compile` 验证编译

### 数据库

- [ ] 编写数据库迁移脚本
- [ ] 插入菜单和权限数据
- [ ] 插入字典数据（如有枚举字段）

### 前端

- [ ] 创建前端 API 定义
- [ ] 创建前端页面组件
- [ ] 创建弹窗/抽屉独立组件
- [ ] 配置前端路由（通常由菜单自动生成）

---

## 9. 常见问题

页面与表格类问题（工具栏自定义列、序号不连续、树形展开、列宽调整、移动端选中）见 [前端开发规范](./frontend-development-specification.md) 第 12 章。

---

*文档版本：v4.0*
*最后更新：2026-10-12*
