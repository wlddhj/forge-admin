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

### 升级与发布

#### 发布前准备（通用）

```bash
# 本地：推送待发布提交（确认 git status 干净后再推）
git push origin main

# 服务器：拉取代码，并备份数据库兜底
git pull
docker exec forge-admin-mysql sh -c 'mysqldump -uroot -p"$MYSQL_ROOT_PASSWORD" --databases $MYSQL_DATABASE' > backup-$(date +%Y%m%d%H%M).sql
```

#### 仅发布前端

前端改动（页面/组件/样式等）只需重建 `frontend` 服务，不影响 mysql/redis/backend：

```bash
git pull
docker compose up -d --no-deps --build frontend

# 验证（端口取 .env 的 FRONTEND_PORT，默认 80）
docker compose ps frontend
curl -I http://localhost/
```

说明：

- 镜像内执行 `vite build`（跳过 vue-tsc 类型检查，存量类型错误不阻塞构建；类型治理应在本地/CI 进行）
- 仅当改过 `apps/forge-web/package.json` 依赖时，需先在本地刷新 Docker 专用锁文件并提交：`cd apps/forge-web && pnpm install --lockfile-only --ignore-workspace`
- js/css 产物带内容 hash 且 nginx 缓存 1 年（immutable），`index.html` 不缓存——发布后用户正常刷新即可拿到新版
- 海外服务器构建慢可用国内镜像源（默认 npmmirror），或反向覆盖：`docker compose build --build-arg NPM_REGISTRY=https://registry.npmjs.org frontend`

#### 仅发布后端

后端改动（接口/Service/依赖等）只需重建 `backend` 服务；镜像内多阶段 Maven 打包（`mvn clean package -DskipTests`），服务器无需安装 JDK/Maven：

```bash
git pull
docker compose up -d --no-deps --build backend

# 观察启动日志（healthcheck start-period 120s，等待健康后再收尾）
docker compose logs -f backend
```

说明：

- **backend 重启期间前端 API 会短暂 502**（frontend 的 nginx 直转 `backend:8181`），选择低峰窗口发布
- **对比升级前后各模块 `db/migration/` 目录，按版本号顺序手工执行新增的增量迁移脚本**（项目未引入 Flyway，迁移不自动执行）
- 后端镜像重建耗时较长（Maven 依赖层有 Docker 缓存，改 POM 才会重下依赖）

#### 全量发布（前后端一起）

```bash
git pull
docker compose build      # 重建前后端镜像，数据卷自动保留
docker compose up -d      # 按依赖顺序滚动重启（mysql/redis 健康后起 backend，backend 健康后起 frontend）

# 同样需要检查并手工执行新增的 db/migration/ 增量脚本
```

#### 发布排障

| 现象 | 处理 |
|------|------|
| 镜像构建异常（疑似缓存脏） | `docker compose build --no-cache frontend`（或 backend）强制重建 |
| 前端页面空白/回滚 | `docker compose logs frontend` 查 nginx 报错；确认产物正常后强刷浏览器 |
| backend 起不来 | `docker compose logs backend` 看启动栈；确认 `.env` 密钥与数据库迁移是否先执行 |
| 误执行 `down -v` | 数据卷被清空，用最近备份 `backup-*.sql` 恢复 |

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
