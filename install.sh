#!/usr/bin/env bash
# =============================================================================
# wa-gateway 一键安装脚本（在客户的 Linux 主机上直接运行）
#   流程：装 Docker → 从腾讯云下载「预构建镜像包 + docker-compose.yml」
#         → docker load 加载镜像 → docker compose 启动整套网关
#   客户机上【无需】Java / Maven / Nacos，也无需编译源码。
#
# 用法：
#   sudo bash wa-gateway-install.sh
# 可覆盖：
#   VERSION            （默认 1.0.0）
#   BASE_URL           （默认 http://140.143.248.224:8000/releases）
#   MYSQL_ROOT_PASSWORD / GATEWAY_API_KEY / TOKEN_ENCRYPT_SECRET
#   DOCKER_MIRROR      （可选，国内拉取 MySQL/Redis/RocketMQ 依赖镜像的加速地址）
# =============================================================================
set -euo pipefail

VERSION="${VERSION:-1.0.0}"
BASE_URL="${BASE_URL:-http://140.143.248.224:8000/releases}"
MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD:-root123}"
GATEWAY_API_KEY="${GATEWAY_API_KEY:-change-me-admin-key}"
TOKEN_ENCRYPT_SECRET="${TOKEN_ENCRYPT_SECRET:-change-me-32-char-encrypt-secret}"

info() { echo -e "\033[36m==>\033[0m $*"; }
ok()   { echo -e "\033[32m>> $*\033[0m"; }
err()  { echo -e "\033[31m!! $*\033[0m" >&2; }

# 1) 安装/配置 Docker -----------------------------------------------------------
if ! command -v docker >/dev/null 2>&1; then
  info "未检测到 Docker，正在安装 ..."
  if command -v curl >/dev/null 2>&1; then
    # 国内镜像加速安装 Docker
    curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun
  elif command -v wget >/dev/null 2>&1; then
    wget -qO- https://get.docker.com | bash -s docker --mirror Aliyun
  else
    err "未找到 curl/wget，请先手动安装 Docker（见 https://docs.docker.com/engine/install/）"; exit 1
  fi
  systemctl enable --now docker 2>/dev/null || service docker start 2>/dev/null || true
fi

# compose 子命令：v2 优先，v1 回退
if docker compose version >/dev/null 2>&1; then
  COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
  COMPOSE="docker-compose"
else
  err "未找到 docker compose / docker-compose，请升级 Docker"; exit 1
fi

# 可选：给 Docker 配置国内镜像加速（拉 MySQL/Redis/RocketMQ 依赖镜像用）
if [ -n "${DOCKER_MIRROR:-}" ]; then
  mkdir -p /etc/docker
  echo "{ \"registry-mirrors\": [\"$DOCKER_MIRROR\"] }" > /etc/docker/daemon.json
  systemctl restart docker 2>/dev/null || service docker restart 2>/dev/null || true
fi

# 2) 从腾讯云下载必要资源 ------------------------------------------------------
cd "$(dirname "$0")"
info "从腾讯云下载 wa-gateway 预构建镜像包（v${VERSION}）与编排文件 ..."
curl -fSL --retry 3 "$BASE_URL/wa-gateway-${VERSION}.tar.gz" -o "wa-gateway-${VERSION}.tar.gz"
curl -fSL --retry 3 "$BASE_URL/docker-compose.yml" -o docker-compose.yml

# 3) 加载镜像 + 启动 -----------------------------------------------------------
info "加载 wa-gateway:${VERSION} 镜像 ..."
gunzip -c "wa-gateway-${VERSION}.tar.gz" | docker load

info "启动网关栈（MySQL + Redis + RocketMQ + 网关）..."
MYSQL_ROOT_PASSWORD="$MYSQL_ROOT_PASSWORD" \
GATEWAY_API_KEY="$GATEWAY_API_KEY" \
TOKEN_ENCRYPT_SECRET="$TOKEN_ENCRYPT_SECRET" \
$COMPOSE up -d

# 4) 等待健康 ------------------------------------------------------------------
info "等待服务就绪（首次需拉依赖镜像并初始化数据库，约 1~3 分钟）..."
for i in $(seq 1 60); do
  if curl -fsS "http://127.0.0.1:8080/v1/health" >/dev/null 2>&1; then
    ok "✅ wa-gateway 已启动并健康！"
    break
  fi
  sleep 3
done

cat <<EOF

========================================================
  🚀 wa-gateway 安装完成
--------------------------------------------------------
  健康检查       http://<服务器IP>:8080/v1/health
  API 文档       http://<服务器IP>:8080/swagger-ui/index.html
  Prometheus     http://<服务器IP>:8080/actuator/prometheus

  REST API Key   $GATEWAY_API_KEY

  下一步：接入 WABA 账号 → 发消息，见 README「快速开始」
========================================================
EOF

info "已下载的镜像包可删除：rm -f wa-gateway-${VERSION}.tar.gz"