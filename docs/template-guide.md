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
cd apps/forge-server
mvn spring-boot:run

# 4. 启动前端（新终端）
cd apps/forge-web
pnpm install
pnpm dev
```

## 替换规则说明

初始化脚本会自动替换以下内容：

### 后端替换

| 原值 | 替换为 | 示例 |
|------|--------|------|
| `com.forge.admin` | `{包名}` | `com.mycompany` |
| `forge-admin` | `{项目名-kebab}` | `my-admin` |
| `forge_admin` | `{项目名-snake}` | `my_admin` |
| `forge-admin` | `{项目名}` | `MyAdmin` |
| `聚能后台管理系统` | `{项目描述}` | `我的管理系统` |

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
cd apps/forge-server
docker build -t my-admin-backend .

# 构建前端镜像
cd apps/forge-web
docker build -t my-admin-frontend .
```

### 运行容器

```bash
# 运行后端
docker run -d \
  --name my-admin-backend \
  -p 8180:8180 \
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
│   ├── backend/                    # 后端应用
│   │   ├── src/main/java/com/mycompany/  # Java 包（已重命名）
│   │   ├── src/main/resources/
│   │   │   └── application.yml     # 配置文件（已更新）
│   │   └── Dockerfile
│   │
│   └── frontend/                   # 前端应用
│       ├── src/
│       │   ├── views/login/        # 登录页（已更新标题）
│       │   ├── layouts/            # 布局（已更新标题）
│       │   └── stores/             # 状态管理（已更新 keys）
│       └── Dockerfile
│
├── docker/
│   └── nginx.conf
│
├── scripts/
│   └── init-project.js             # 初始化脚本
│
├── sql/
│   └── init.sql                    # 数据库脚本（已更新数据库名）
│
├── .template/
│   └── template-config.yaml        # 模板配置
│
├── docker-compose.yml
├── .env.example
├── package.json
└── README.md
```

## 常见问题

### Q: 初始化后后端启动失败？

检查以下内容：
1. Java 包目录是否正确重命名
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
