# Stage 1: Build
FROM node:22-alpine AS builder

# 安装 pnpm（版本需与 apps/forge-web/pnpm-lock.yaml 的 lockfileVersion 9.0 匹配，即 pnpm >= 9）
RUN npm install -g pnpm@11.10.0

WORKDIR /build

# 复制 package.json 和 lock 文件
# 注意：本目录的 pnpm-lock.yaml 是独立于根 workspace 的锁文件（本 Docker 构建专用），
# 修改 forge-web 依赖后需同步刷新：
#   cd apps/forge-web && pnpm install --lockfile-only --ignore-workspace
COPY package.json pnpm-lock.yaml ./

# pnpm 11 已不读 package.json 的 pnpm 字段，构建脚本白名单须写入 pnpm-workspace.yaml
# （省略 packages 字段 = 仅根包；清单与根 pnpm-workspace.yaml 的 allowBuilds 保持一致）
RUN printf 'allowBuilds:\n  "@parcel/watcher": true\n  core-js: true\n  core-js-pure: true\n  esbuild: true\n  msw: true\n  vue-demi: true\n' > pnpm-workspace.yaml

# 安装依赖
RUN pnpm install --frozen-lockfile

# 复制源码
COPY . .

# 构建应用
RUN pnpm build

# Stage 2: Production
FROM nginx:alpine

# 复制自定义 Nginx 配置
COPY nginx.conf /etc/nginx/conf.d/default.conf

# 复制构建产物
COPY --from=builder /build/dist /usr/share/nginx/html

# 添加健康检查
RUN apk add --no-cache curl

# 暴露端口
EXPOSE 80

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=5s --retries=3 \
    CMD curl -f http://localhost/ || exit 1

# 启动 Nginx
CMD ["nginx", "-g", "daemon off;"]
