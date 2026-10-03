# 服务部署指南

本文档介绍基于 forge-admin 模板创建的项目的服务部署，包括环境变量配置与 Docker 容器化部署。项目初始化与模块管理见 [template-guide.md](../template-guide.md)。

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

### 使用 Docker Compose（推荐）

1. 配置环境变量：

```bash
cp .env.example .env
# 编辑 .env，填写必填密钥（MySQL/Redis/JWT/AES，参考文件内注释）
```

MySQL 与 Redis 由 compose 内置编排（数据卷持久化），无需外部数据库；首次启动自动按序执行 `sql/` 下的初始化脚本。

2. 启动服务：

```bash
docker compose up -d      # 旧版环境使用 docker-compose up -d
```

3. 查看日志：

```bash
docker compose logs -f
```

4. 停止服务：

```bash
docker compose down       # 不带 -v 时数据卷保留；-v 会清空数据库，仅全新重装时使用
```

### 升级

```bash
# 备份数据库兜底
docker exec forge-admin-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases $MYSQL_DATABASE' > backup-$(date +%Y%m%d%H%M).sql

git pull
docker compose build      # 重建前后端镜像，数据卷自动保留
docker compose up -d

# 对比升级前后 db/migration/ 目录，按版本号顺序手工执行新增的增量迁移脚本
```

完整步骤与注意事项见根目录 README 的「Docker 部署 - 升级」章节。

### 单独构建镜像

```bash
# 构建后端镜像
cd apps/my-admin-server
docker build -t my-admin-backend .

# 构建前端镜像
cd apps/my-admin-web
docker build -t my-admin-frontend .
```

### 运行容器（不走 Compose 时）

```bash
# 运行后端（连接宿主机自建的 MySQL/Redis）
docker run -d \
  --name my-admin-backend \
  -p 8181:8181 \
  -e DB_HOST=host.docker.internal \
  -e DB_PASSWORD=your-password \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PASSWORD=your-redis-password \
  -e JWT_SECRET=your-jwt-secret \
  -e APP_AES_KEY=your-aes-key \
  my-admin-backend

# 运行前端
docker run -d \
  --name my-admin-frontend \
  -p 80:80 \
  my-admin-frontend
```

## 常见问题

### Q: Docker 构建失败？

检查以下内容：
1. 确保 `apps/forge-web/nginx.conf` 文件存在
2. 确保有网络连接下载依赖
3. 检查 Dockerfile 语法
