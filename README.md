# forge-admin

企业级后台管理系统模板，基于 RBAC（基于角色的访问控制）权限模型，前后端分离，开箱即用。

## 项目简介

forge-admin 是一款现代化的企业级后台管理解决方案，采用前后端分离架构设计。后端基于 Spring Boot 3.2 构建，使用 MyBatis Plus 简化数据操作，JWT 实现无状态认证；前端采用 Vue 3 + TypeScript + Element Plus + vxe-table 技术栈，提供流畅的用户体验、完善的类型支持和强大的表格功能。

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

## 特性

- **权限管理**：完整的 RBAC 权限系统，支持用户、角色、菜单、部门管理
- **数据权限**：支持部门数据权限隔离（5 种范围类型）
- **OAuth2 授权服务器**：基于 Spring Authorization Server，支持授权码、客户端凭证等标准 OAuth2/OIDC 协议
- **第三方登录**：支持微信、钉钉扫码登录，基于 JustAuth 实现
- **字典管理**：灵活的数据字典配置，支持缓存刷新
- **定时任务**：基于 Quartz 的定时任务管理和日志
- **文件存储**：支持本地存储，可扩展 OSS 等
- **API 文档**：集成 Knife4j，提供在线 API 文档
- **响应式设计**：支持桌面端和移动端
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

### 前端

- Vue 3.4
- TypeScript 5.3
- Element Plus 2.4
- vxe-table 4.9（强大表格组件）
- vxe-pc-ui 4.6
- Pinia 2.1
- Vite 5.0

## 快速开始

### 环境要求

- JDK 21+
- Node.js 18+（推荐 22.9.0）
- pnpm 8.15.4+
- MySQL 8.0+
- Redis 6.0+

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
mvn spring-boot:run
```

后端服务运行在 http://localhost:8181

4. **启动前端**

```bash
cd apps/forge-web
pnpm install
pnpm dev
```

前端服务运行在 http://localhost:3003

5. **访问系统**

- 前端地址：http://localhost:3003
- API 文档：http://localhost:8181/api/doc.html
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

### 后端（在 `apps/forge-server` 目录下）

```bash
mvn spring-boot:run              # 启动开发服务器
mvn clean compile                # 仅编译
mvn clean package -DskipTests    # 打包 JAR（跳过测试）
mvn test                         # 运行所有测试
mvn test -Dtest=ClassName       # 运行单个测试类
mvn test -Dtest=ClassName#methodName  # 运行单个测试方法
```

## 目录结构

```
forge-admin/
├── apps/
│   ├── forge-server/           # 后端应用
│   │   ├── src/main/java/com/forge/admin/
│   │   │   ├── common/         # 通用模块（注解、切面、配置、异常、工具）
│   │   │   └── modules/        # 业务模块（auth、system、quartz）
│   │   └── src/main/resources/
│   │       ├── application.yml # 配置文件
│   │       └── db/migration/   # 数据库迁移脚本
│   │
│   └── forge-web/              # 前端应用
│       ├── src/
│       │   ├── api/            # API 接口定义
│       │   ├── components/     # 公共组件
│       │   ├── composables/    # 组合式函数（useTableHeight、useTableSeq 等）
│       │   ├── layouts/        # 布局组件
│       │   ├── plugins/        # vxe-table 全局配置
│       │   ├── router/         # 路由配置（动态路由）
│       │   ├── stores/         # Pinia 状态管理
│       │   ├── styles/         # 样式文件
│       │   ├── types/          # TypeScript 类型定义
│       │   ├── utils/          # 工具函数
│       │   └── views/          # 页面组件
│       └── package.json
│
├── docker/                     # Docker 配置
├── scripts/                    # 项目初始化脚本
├── sql/                        # 数据库脚本
└── .template/                  # 模板配置
```

## 架构要点

### 后端模块模式

每个业务模块遵循分层结构：`controller/` → `service/` → `mapper/` → `entity/`，`dto/` 存放请求/响应对象。

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

自动重命名 Java 包名、更新配置文件、前端标题和数据库名。

## Docker 部署

```bash
cp .env.example .env
docker-compose up -d
```

访问：http://localhost（前端）、http://localhost/api/doc.html（API 文档）

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
| API 地址 | http://localhost:8181/api | /api |

## 许可证

MIT License
