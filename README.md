# forge-admin

企业级后台管理系统模板，基于 RBAC（基于角色的访问控制）权限模型，前后端分离，开箱即用。

## 项目简介

forge-admin 是一款现代化的企业级后台管理解决方案，采用前后端分离架构设计。后端基于 Spring Boot 3.2 构建，采用多模块 Maven 项目结构，使用 MyBatis Plus 简化数据操作，JWT 实现无状态认证；前端采用 Vue 3 + TypeScript + Element Plus + vxe-table 技术栈，提供流畅的用户体验、完善的类型支持和强大的表格功能。

系统内置完整的权限管理模块，支持用户、角色、菜单、部门的层级管理，并实现细粒度的数据权限控制（全部/本部门/本部门及以下/仅本人）。集成 OAuth2 授权服务器（基于 Spring Authorization Server），支持微信、钉钉等第三方登录（基于 JustAuth），允许第三方应用通过本系统进行用户认证。此外还集成了 Quartz 定时任务调度、在线 API 文档（Knife4j）、操作日志审计、登录日志等企业级功能。支持 Docker 容器化部署，提供项目模板化工具，可快速基于此项目创建新的管理系统。

## 项目截图

### 登录页面

![登录页面](./docs/screenshots/login.png)

### 仪表盘

![仪表盘](./docs/screenshots/dashboard.png)

### 用户管理

![用户管理](./docs/screenshots/user.png)

### 角色管理

![角色管理](./docs/screenshots/role.png)

### 菜单管理

![菜单管理](./docs/screenshots/menu.png)

### 定时任务

![定时任务](./docs/screenshots/job.png)

## 特性

- **权限管理**：完整的 RBAC 权限系统，支持用户、角色、菜单、部门管理
- **数据权限**：支持部门数据权限隔离（5 种范围类型）
- **OAuth2 授权服务器**：基于 Spring Authorization Server，支持授权码、客户端凭证等标准 OAuth2/OIDC 协议
- **第三方登录**：支持微信、钉钉扫码登录，基于 JustAuth 实现
- **字典管理**：灵活的数据字典配置，支持缓存刷新
- **定时任务**：基于 Quartz 的定时任务管理和日志
- **文件存储**：支持本地存储，可扩展 OSS 等
- **API 文档**：集成 Knife4j，提供在线 API 文档
- **移动端支持**：独立的 `/app-api` 端点，支持微信小程序授权登录
- **强大表格**：vxe-table 提供列自定义、导出、打印等功能

## 技术栈

### 后端

- Java 21
- Spring Boot 3.2.0
- MyBatis Plus 3.5.7
- MySQL 8.0+
- Redis 6.0+
- JWT 认证
- Spring Authorization Server (OAuth2/OIDC)
- JustAuth（第三方登录）
- Knife4j (Swagger)
- Quartz 定时任务
- Flowable 7.0.1（工作流引擎）

### 前端

- Vue 3.4
- TypeScript 5.3
- Element Plus 2.4
- vxe-table 4.9（强大表格组件）
- vxe-pc-ui 4.6
- Pinia 2.1
- Vite 5.0

### 移动端（小程序）

- uni-app + Vue 3
- 微信小程序授权登录
- Pinia 状态管理
- 独立的 app_user 用户表

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+（推荐 22.9.0）
- pnpm 8.15.4+
- MySQL 8.0+
- Redis 6.0+
- 微信开发者工具（小程序开发）

### 安装

1. **克隆项目**

```bash
git clone <repository-url>
cd forge-admin
```

2. **创建数据库**

```bash
mysql -u root -p < sql/init.sql
```

3. **启动后端**

```bash
cd apps/forge-server
mvn spring-boot:run -pl forge-server
```

后端服务运行在 http://localhost:8181

4. **启动前端**

```bash
cd apps/forge-web
pnpm install
pnpm dev
```

前端服务运行在 http://localhost:3003

5. **启动小程序**（可选）

```bash
cd apps/forge-miniapp
pnpm install
pnpm dev:mp-weixin
```

小程序开发工具导入 `dist/dev/mp-weixin` 目录

6. **访问系统**

- 前端地址：http://localhost:3003
- API 文档：http://localhost:8181/doc.html
- 默认账号：`admin` / `password`
- OAuth2 使用文档：[docs/oauth2-guide.md](docs/oauth2-guide.md)

## 开发命令

### 前端（在 `apps/forge-web` 目录下）

```bash
pnpm install    # 安装依赖
pnpm dev        # 启动开发服务器（端口 3003）
pnpm build      # 生产构建（含类型检查）
pnpm preview    # 预览生产构建
pnpm lint       # 运行 ESLint
pnpm test       # 运行 vitest 单元测试
```

### 小程序（在 `apps/forge-miniapp` 目录下）

```bash
pnpm install    # 安装依赖
pnpm dev:mp-weixin  # 微信小程序开发模式
pnpm build:mp-weixin # 微信小程序生产构建
```

### 后端（在 `apps/forge-server` 目录下）

```bash
mvn spring-boot:run -pl forge-server    # 启动开发服务器
mvn clean compile                       # 仅编译
mvn clean package -DskipTests           # 打包 JAR（跳过测试）
mvn test                                # 运行所有测试
mvn test -Dtest=ClassName -pl <module>  # 运行指定模块的单个测试类
```

## 目录结构

```
forge-admin/
├── apps/
│   ├── forge-server/                    # 后端（多模块 Maven 项目）
│   │   ├── pom.xml                      # 根聚合 POM
│   │   ├── forge-dependencies/          # BOM 版本管理
│   │   ├── forge-framework/             # 框架层
│   │   │   ├── forge-common/            # 公共模块（注解、异常、响应、工具类）
│   │   │   ├── forge-spring-boot-starter-mybatis/   # MyBatis + 数据权限
│   │   │   ├── forge-spring-boot-starter-redis/     # Redis 配置
│   │   │   ├── forge-spring-boot-starter-security/  # JWT + OAuth2
│   │   │   └── forge-spring-boot-starter-web/       # Web + WebSocket
│   │   ├── forge-module-system/         # 系统模块
│   │   │   ├── forge-module-system-api/ # API 接口 + 实体 + DTO
│   │   │   └── forge-module-system-biz/ # 业务实现（含 auth、quartz）
│   │   ├── forge-module-workflow/       # 工作流模块
│   │   │   ├── forge-module-workflow-api/
│   │   │   └── forge-module-workflow-biz/
│   │   └── forge-server/               # Spring Boot 启动入口
│   │
│   ├── forge-web/                       # 前端应用
│   │   ├── src/
│   │   │   ├── api/                     # API 接口定义
│   │   │   ├── components/              # 公共组件
│   │   │   ├── composables/             # 组合式函数（useTableHeight、useTableSeq 等）
│   │   │   ├── layouts/                 # 布局组件
│   │   │   ├── plugins/                 # vxe-table 全局配置
│   │   │   ├── router/                  # 路由配置（动态路由）
│   │   │   ├── stores/                  # Pinia 状态管理
│   │   │   ├── styles/                  # 样式文件
│   │   │   ├── types/                   # TypeScript 类型定义
│   │   │   ├── utils/                   # 工具函数
│   │   │   └── views/                   # 页面组件
│   │   └── package.json
│   │
│   └── forge-miniapp/                   # 小程序应用
│       ├── src/
│       │   ├── api/                     # API 接口定义
│       │   ├── pages/                   # 页面组件
│       │   │   ├── login/               # 登录页
│       │   │   └── profile/             # 个人中心
│       │   ├── stores/                  # Pinia 状态管理
│       │   ├── static/                  # 静态资源（logo、头像）
│       │   └── composables/             # 组合式函数
│       └── package.json
│
├── docker/                              # Docker 配置
├── scripts/                             # 项目初始化脚本
├── sql/                                 # 数据库脚本
└── .template/                           # 模板配置
```

## 架构要点

### 后端多模块架构

后端采用 14 模块的 Maven 多项目结构，各模块职责清晰：

```
forge-dependencies        → BOM 版本管理
forge-framework (5模块)   → 框架层，可独立版本管理
forge-module-system (2)   → 系统模块，api/biz 分离
forge-module-workflow (2) → 工作流模块，api/biz 分离
forge-server              → Spring Boot 启动入口
```

每个业务模块遵循分层结构：`controller/admin/` + `controller/app/` → `service/` → `mapper/`，实体和 DTO 放在 `api` 子模块供跨模块引用。

**双端点架构：**
- `/admin-api/**` — 后台管理端点，使用 `sys_user` 表 + `@PreAuthorize` 权限控制
- `/app-api/**` — 移动端端点，使用独立的 `app_user` 表 + 微信授权登录

框架通过 `WebMvcConfigurer.configurePathMatch()` 根据 Controller 包名（`controller.admin` / `controller.app`）自动注入路径前缀，Controller 代码无需手动指定前缀。

**模块依赖关系：**
- `forge-server` ← `system-biz`、`workflow-biz`
- `workflow-biz` ← `workflow-api`、`system-api`、`starters`、`flowable`
- `system-biz` ← `system-api`、`starters`、`quartz`

**横切关注点：**
- `@OperationLog` — 基于 AOP 的审计日志
- `@DataPermission` — SQL 级数据范围过滤
- `@RateLimiter` — 基于 Redis 令牌桶的限流
- `@Cacheable/@CacheEvict` — Redis 缓存

### 前端表格组件（vxe-table）

所有列表页面统一使用 vxe-table，提供：
- 列自定义（显示/隐藏、排序、固定）
- 数据导出（CSV、HTML、XML、TXT）
- 打印功能
- 虚拟滚动（大数据量优化）
- 树形表格（菜单、部门）

**表格命名约定：** `sys{Module}Table`（如 `sysUserTable`、`sysRoleTable`）

### 动态路由

后端返回菜单树 → 转换为 Vue Router 配置 → 通过 `import.meta.glob` 解析组件 → `router.addRoute()` 添加路由。

### 权限控制

- **后端**：`@PreAuthorize("hasAuthority('system:user:list')")`
- **前端模板**：`v-permission="'system:user:add'"`
- **前端脚本**：`hasPermission('system:user:add')`

## 基于模板创建新项目

```bash
pnpm run init <项目名称> "<项目描述>" <包名>
# 示例：pnpm run init my-admin "我的管理系统" com.mycompany
```

自动扫描所有模块的 Java 源码目录，重命名包名、更新配置文件、前端标题和数据库名。详见 [docs/template-guide.md](docs/template-guide.md)。

## 模块管理

### 创建新模块

```bash
node scripts/create-module.js <模块名称> "<模块描述>"
# 示例：node scripts/create-module.js order "订单管理模块"
```

自动创建模块目录结构、pom.xml、基础类模板。

### 删除模块

```bash
./scripts/remove-module.sh <模块名称>
# 示例：./scripts/remove-module.sh workflow
```

自动从 pom.xml 移除模块引用、删除依赖关系、清理迁移脚本和模块目录。

## Docker 部署

```bash
cp .env.example .env
docker-compose up -d
```

访问：http://localhost（前端）、http://localhost/doc.html（API 文档）

## 配置说明

### 后端配置（application.yml）

| 配置项 | 默认值 |
|--------|--------|
| 服务端口 | 8181 |
| 数据库 | localhost:3306/forge_admin |
| Redis | localhost:6379 |
| JWT 密钥 | （生产环境请修改） |

### 前端环境变量

| 变量 | 开发环境 | 生产环境 |
|------|----------|----------|
| 后台 API 地址 | http://localhost:8181/admin-api | /admin-api |
| 移动端 API 地址 | http://localhost:8181/app-api | /app-api |

## 许可证

MIT License
