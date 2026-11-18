#!/usr/bin/env bash
# =============================================================================
# wa-gateway 一键安装脚本（Linux）
#   sudo bash install.sh
# 会：1) 检查/安装 Docker  2) 构建并启动整套网关栈  3) 打印访问方式
# 支持 Ubuntu / Debian / CentOS / RHEL 系；需要能访问外网（拉镜像 + 构建）。
# =============================================================================
set -euo pipefail

info() { echo -e "\033[36m==>\033[0m $*"; }
ok()   { echo -e "\033[32m>> $*\033[0m"; }
err()  { echo -e "\033[31m!! $*\033[0m" >&2; }

command -v docker >/dev/null 2>&1 || {
  info "未检测到 Docker，开始安装 Docker ..."
  if command -v curl >/dev/null 2>&1; then
    curl -fsSL https://get.docker.com | sh
  elif command -v wget >/dev/null 2>&1; then
    wget -qO- https://get.docker.com | sh
  else
    err "未找到 curl/wget，无法自动安装 Docker；请先手动安装 Docker"; exit 1
  fi
  systemctl enable --now docker 2>/dev/null || service docker start 2>/dev/null || true
}

docker compose version >/dev/null 2>&1 || {
  info "当前 Docker 为旧版（无 compose 子命令），升级后重试，或使用 docker-compose"
  if command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
  else
    err "未找到 docker compose / docker-compose"; exit 1
  fi
}
COMPOSE="${COMPOSE:-docker compose}"

cd "$(dirname "$0")"

info "开始构建并启动 wa-gateway 全套（MySQL + Redis + RocketMQ + 网关）..."
$COMPOSE up -d --build

info "等待服务就绪（首次需拉镜像/初始化数据库，约 1~3 分钟）..."
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
  健康检查      http://<服务器IP>:8080/v1/health
  API 文档(Swagger) http://<服务器IP>:8080/swagger-ui/index.html
  Prometheus    http://<服务器IP>:8080/actuator/prometheus

  REST API Key  ${GATEWAY_API_KEY:-change-me-admin-key}
                 （请用环境变量 GATEWAY_API_KEY 修改）

  下一步：接入 WABA 账号 → 发消息，见 README「快速开始」
========================================================
EOF

info "注：RocketMQ 主题会随首次使用自动创建；生产环境请预创建并关闭自动建主题（见 docs/RESOURCE-SIZING.md）"