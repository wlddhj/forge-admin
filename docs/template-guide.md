# forge-admin 模板使用指南

本文档介绍如何使用 forge-admin 作为模板快速创建新项目。

## 快速开始

### 1. 初始化新项目

在项目根目录执行：

```bash
pnpm run init <项目名称> "<项目描述>" <包名>
```

**参数说明：**

| 参数 | 说明 | 示例 |
|------|------|------|
| 项目名称 | 项目显示名称（中文或英文） | `my-admin` |
| 项目描述 | 项目简介（需用引号包裹） | `"我的管理系统"` |
| 包名 | Java 基础包名 | `com.mycompany` |

**示例：**

```bash
# 创建一个名为 "MyAdmin" 的项目
pnpm run init my-admin "我的管理系统" com.mycompany

# 创建一个企业内部系统
pnpm run init erp-system "企业资源管理系统" com.company.erp

# 创建一个电商后台
pnpm run init shop-admin "电商管理后台" com.shop.admin
```

### 2. 初始化后操作

初始化脚本执行完成后，需要进行以下步骤：

```bash
# 1. 创建数据库
mysql -u root -p < sql/init.sql

# 2. 配置环境变量（可选，用于生产环境）
cp .env.example .env
# 编辑 .env 文件

# 3. 启动后端
cd apps/my-admin-server
mvn spring-boot:run

# 4. 启动前端（新终端）
cd apps/my-admin-web
pnpm install
pnpm dev
```

## 创建新业务模块

项目支持通过脚本快速创建新的业务模块（如仓储管理、订单管理等），自动生成标准的三层 Maven 模块结构。

### 使用方法

```bash
pnpm run create-module <模块名> "<模块描述>"
```

**参数说明：**

| 参数 | 说明 | 格式要求 | 示例 |
|------|------|----------|------|
| 模块名 | 模块标识符 | kebab-case，全小写 | `wms`、`order-management` |
| 模块描述 | 模块中文名称 | 需用引号包裹 | `"仓储管理模块"` |

**示例：**

```bash
# 创建仓储管理模块
pnpm run create-module wms "仓储管理模块"

# 创建订单管理模块
pnpm run create-module order-management "订单管理模块"
```

### 生成的目录结构

以 `pnpm run create-module wms "仓储管理模块"` 为例，脚本会创建：

```
apps/forge-server/forge-module-wms/
├── pom.xml                                          # 聚合 POM (packaging=pom)
├── forge-module-wms-api/
│   ├── pom.xml
│   └── src/main/java/com/forge/modules/wms/
│       ├── entity/                                  # 实体类目录
│       └── dto/                                     # DTO 目录
└── forge-module-wms-biz/
    ├── pom.xml
    └── src/main/java/com/forge/modules/wms/
        ├── controller/                              # Controller 目录
        ├── mapper/                                  # Mapper 接口目录
        └── service/impl/                            # Service 接口和实现目录
    └── src/main/resources/mapper/wms/               # MyBatis XML 目录
```

### 自动修改的文件

脚本执行过程中会自动修改以下文件：

1. **根 POM**（`apps/forge-server/pom.xml`）— 添加 `<module>forge-module-{name}</module>` 声明
2. **启动模块 POM**（`apps/forge-server/forge-server/pom.xml`）— 添加 `forge-module-{name}-biz` 依赖
3. **初始化脚本**（`scripts/init-project.js`）— 添加新模块的替换规则和目录重命名规则，确保 `pnpm run init` 时能正确处理新模块

### 模块依赖关系

新模块默认依赖：
- `forge-common`（公共模块）
- `forge-module-system-api`（系统模块 API）
- `forge-spring-boot-starter-mybatis`（MyBatis 配置）
- `forge-spring-boot-starter-web`（Web 配置）
- `knife4j`（API 文档）、`lombok`、`mysql-connector-j`

### 创建后步骤

模块创建完成后，需要手动添加业务代码：

1. 在 `entity/` 中创建实体类
2. 在 `dto/` 中创建 Request / Response / QueryRequest
3. 在 `mapper/` 中创建 Mapper 接口
4. 在 `service/impl/` 中创建 Service 接口和实现类
5. 在 `controller/` 中创建 Controller
6. 在 `resources/mapper/{模块名}/` 中创建 MyBatis XML 映射文件
7. 编译验证：`cd apps/forge-server && mvn clean compile -pl forge-module-{name}`

## 替换规则说明

初始化脚本会自动替换以下内容：

### 后端替换

| 原值 | 替换为 | 示例 |
|------|--------|------|
| `com.forge` | `{包名}` | `com.mycompany` |
| `ForgeAdminApplication` | `{项目名PascalCase}Application` | `MyAdminApplication` |
| `forge-module-workflow-biz` | `{项目名-kebab}-module-workflow-biz` | `my-admin-module-workflow-biz` |
| `forge-module-workflow-api` | `{项目名-kebab}-module-workflow-api` | `my-admin-module-workflow-api` |
| `forge-module-system-biz` | `{项目名-kebab}-module-system-biz` | `my-admin-module-system-biz` |
| `forge-module-system-api` | `{项目名-kebab}-module-system-api` | `my-admin-module-system-api` |
| `forge-spring-boot-starter-*` | `{项目名-kebab}-spring-boot-starter-*` | `my-admin-spring-boot-starter-mybatis` |
| `forge-dependencies` | `{项目名-kebab}-dependencies` | `my-admin-dependencies` |
| `forge-framework` | `{项目名-kebab}-framework` | `my-admin-framework` |
| `forge-common` | `{项目名-kebab}-common` | `my-admin-common` |
| `forge-admin` | `{项目名-kebab}` | `my-admin` |
| `forge_admin` | `{项目名-snake}` | `my_admin` |
| `聚能后台管理系统` | `{项目描述}` | `我的管理系统` |
| `forge`（根 artifactId） | `{项目名-kebab}` | `my-admin` |

### 前端替换

| 原值 | 替换为 | 位置 |
|------|--------|------|
| `forge-admin` | `{项目名}` | 标题、Logo 旁 |
| `聚能后台管理系统` | `{项目描述}` | 登录页 |
| `forge_admin-page-config` | `{项目名-snake}-page-config` | localStorage key |
| `standadmin-tabs` | `{项目名-snake}-tabs` | localStorage key |

### 数据库替换

| 原值 | 替换为 |
|------|--------|
| `forge_admin` | `{项目名-snake}` |

### Java 包目录重命名

脚本会自动扫描所有 Maven 模块的 `src/main/java` 目录（共 10 个源码根），将 `com/forge/` 目录重命名为目标包名对应的目录结构。

### Maven 子模块目录重命名

脚本会由深到浅重命名后端所有子模块目录，包括：
- `forge-framework/` 下的 starter 和 common 子目录
- `forge-module-system/` 和 `forge-module-workflow/` 下的 api、biz 子目录
- `forge-dependencies/`、`forge-framework/`、`forge-module-*` 等中层目录
- `forge-server/` 内部启动模块目录

### Dockerfile 路径替换

脚本支持无扩展名文件（如 `Dockerfile`）的内容替换，确保 Docker 构建路径与重命名后的目录一致。

## 环境变量配置

### 开发环境

开发环境使用 `application.yml` 中的默认值，无需额外配置。

### 生产环境

1. 复制环境变量示例文件：

```bash
cp .env.example .env
```

2. 编辑 `.env` 文件：

```bash
# ========================================
# 项目基础配置
# ========================================
PROJECT_NAME=my-admin
SPRING_PROFILES_ACTIVE=prod

# ========================================
# 数据库配置
# ========================================
DB_HOST=your-db-host
DB_PORT=3306
DB_NAME=my_admin
DB_USERNAME=your-username
DB_PASSWORD=your-password

# ========================================
# Redis 配置
# ========================================
REDIS_HOST=your-redis-host
REDIS_PORT=6379
REDIS_PASSWORD=your-redis-password

# ========================================
# JWT 配置（重要！）
# ========================================
# 生产环境必须修改此密钥
JWT_SECRET=your-production-secret-key-at-least-256-bits

# ========================================
# 文件上传配置
# ========================================
FILE_UPLOAD_PATH=/data/uploads
FILE_BASE_URL=https://your-domain.com/api/uploads
```

## Docker 部署

### 使用 Docker Compose

1. 配置环境变量：

```bash
cp .env.example .env
# 编辑 .env，配置外部数据库和 Redis 连接信息
```

2. 启动服务：

```bash
docker-compose up -d
```

3. 查看日志：

```bash
docker-compose logs -f
```

4. 停止服务：

```bash
docker-compose down
```

### 单独构建镜像

```bash
# 构建后端镜像
cd apps/my-admin-server
docker build -t my-admin-backend .

# 构建前端镜像
cd apps/my-admin-web
docker build -t my-admin-frontend .
```

### 运行容器

```bash
# 运行后端
docker run -d \
  --name my-admin-backend \
  -p 8181:8181 \
  -e DB_HOST=host.docker.internal \
  -e DB_PASSWORD=your-password \
  -e REDIS_HOST=host.docker.internal \
  -e JWT_SECRET=your-secret \
  my-admin-backend

# 运行前端
docker run -d \
  --name my-admin-frontend \
  -p 80:80 \
  my-admin-frontend
```

## 目录结构说明

初始化后的项目结构：

```
my-admin/
├── apps/
│   ├── my-admin-server/                            # 后端（多模块 Maven 项目）
│   │   ├── pom.xml                                # 根聚合 POM
│   │   ├── my-admin-dependencies/                 # BOM 版本管理
│   │   ├── my-admin-framework/                    # 框架层
│   │   │   ├── my-admin-common/                   # 公共模块
│   │   │   ├── my-admin-spring-boot-starter-mybatis/
│   │   │   ├── my-admin-spring-boot-starter-redis/
│   │   │   ├── my-admin-spring-boot-starter-security/
│   │   │   └── my-admin-spring-boot-starter-web/
│   │   ├── my-admin-module-system/                # 系统模块
│   │   │   ├── my-admin-module-system-api/        # API 接口 + 实体 + DTO
│   │   │   └── my-admin-module-system-biz/        # 业务实现
│   │   ├── my-admin-module-workflow/              # 工作流模块
│   │   │   ├── my-admin-module-workflow-api/
│   │   │   └── my-admin-module-workflow-biz/
│   │   └── my-admin-server/                       # Spring Boot 启动入口
│   │       └── src/main/java/com/mycompany/       # Java 包（已重命名）
│   │           └── MyAdminApplication.java
│   │
│   └── my-admin-web/                              # 前端应用
│       ├── src/
│       │   ├── views/login/                       # 登录页（已更新标题）
│       │   ├── layouts/                           # 布局（已更新标题）
│       │   └── stores/                            # 状态管理（已更新 keys）
│       └── Dockerfile
│
├── docker/
│   └── nginx.conf
│
├── scripts/
│   ├── init-project.js                            # 项目初始化脚本
│   └── create-module.js                           # 创建新业务模块脚本
│
├── sql/
│   └── init.sql                                   # 数据库脚本（已更新数据库名）
│
├── .template/
│   └── template-config.yaml                       # 模板配置
│
├── docker-compose.yml
├── .env.example
├── package.json
└── README.md
```

## 常见问题

### Q: 初始化后后端启动失败？

检查以下内容：
1. Java 包目录是否正确重命名（所有 10 个模块的 `com/forge/` 都应被替换）
2. `pom.xml` 中的 groupId 是否正确
3. 数据库是否已创建

### Q: 前端 localStorage 数据冲突？

初始化脚本会更新 localStorage keys，如果之前有 forge-admin 的数据，需要清除浏览器缓存。

### Q: 如何自定义替换规则？

编辑 `.template/template-config.yaml` 文件，添加自定义的替换规则。

### Q: Docker 构建失败？

检查以下内容：
1. 确保 `apps/forge-web/nginx.conf` 文件存在
2. 确保有网络连接下载依赖
3. 检查 Dockerfile 语法

## 附录

### 包名命名规范

Java 包名应遵循以下规范：
- 全小写字母
- 以公司/组织的反向域名开头
- 例如：`com.company.project`

### 项目标识符格式

初始化脚本会自动生成三种格式的标识符：

| 格式 | 说明 | 示例 |
|------|------|------|
| kebab-case | URL、包名用 | `my-admin` |
| snake_case | 数据库名用 | `my_admin` |
| PascalCase | 显示名称 | `MyAdmin` |
