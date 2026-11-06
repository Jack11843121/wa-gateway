# 部署指南

## 前置资源

打包运行只需 JDK 17 + Maven 3.9+。运行依赖：MySQL 8、Redis 7、RocketMQ 5、Nacos 2.3（可选）。

---

## 一、本地开发（docker compose）

```bash
cd deploy
docker compose up -d
docker compose ps            # 等待全部 healthy
```

启动后：

1. 打开 Nacos `http://localhost:8848/nacos`（用户/密码 `nacos/nacos`）。
2. 新建命名空间（可选）→ 新建配置，group 填 `wa-gateway`。
3. 依次导入 `deploy/nacos/` 下 `wa-gateway-common.yaml`（shared）、`wa-gateway-mq.yaml`、`wa-gateway-threadpool.yaml`、`wa-gateway-ratelimit.yaml`（extension）、`wa-gateway-dev.yaml`（主配置，data-id 即文件名）。
4. 构建运行：

```bash
cd ..
mvn clean package -DskipTests
java -jar target/wa-gateway.jar
```

> 首次启动 Flyway 自动建表，无需手动执行 SQL。

---

## 二、Kubernetes 部署

### 1. 构建并推送镜像

```bash
docker build -t your-registry/wa-gateway:1.0.0 .
docker push your-registry/wa-gateway:1.0.0
```

### 2. 修改并应用配置

编辑 [`deploy/k8s/`](../deploy/k8s/)：

- `01-configmap.yaml`：Nacos 地址、RocketMQ 地址、Graph 版本
- `02-secret.yaml`：API Key、token 加密密钥、Nacos/MySQL/Redis 凭据

### 3. 安装依赖组件（如尚未自建）

Nacos / RocketMQ / MySQL / Redis 建议用官方 Helm：

```bash
helm repo add nacos https://nacos.group/repo/helm 2>/dev/null || true
helm repo add bitnami https://charts.bitnami.com/bitnami

# 以自建 RocketMQ + MySQL + Redis 为例
helm upgrade --install rocketmq bitnami/rocketmq --namespace mq --create-namespace
helm upgrade --install mysql bitnami/mysql --namespace db --create-namespace
helm upgrade --install redis bitnami/redis --namespace cache --create-namespace
# Nacos 可用官方 helm 或单机 docker 形式部署
```

> 也可用云托管：腾讯云 TDMQ-RocketMQ、阿里云 Message Queue、云 MySQL、云 Redis 等，
> 只需在 Nacos/Secret 中填对应地址与 ACL 密钥，无需改代码。

### 4. 应用网关清单

```bash
kubectl apply -f deploy/k8s/
kubectl -n wa-gateway get pods -w
```

### 5. 校验

```bash
# 健康
curl https://wa-gateway.example.com/v1/health
# 接入账号 + 发消息（见 README「快速开始」）
```

### 6. 配置 Meta webhook

- 回调地址：`https://webhook.example.com/v1/webhooks`（建议独立域名/Ingress）
- 验证令牌：与账号 `verifyToken`（或 `gateway.webhook.verify-token`）一致

### 7. 扩容

```bash
kubectl -n wa-gateway scale deployment wa-gateway --replicas=5
# 或依赖 HPA（06-hpa.yaml 已按 CPU/内存自动伸缩）
```

---

## 三、NAT 出口 IP 白名单（可选但推荐）

部分企业/Meta 场景要求出站固定 IP。部署 `07-egress-proxy.yaml` 并将
`gateway.outbound.proxy-host/port` 指向代理后，所有 Graph API 出站流量从固定 EIP 出口。
详见 [NAT-EGRESS-IP-WHITELIST.md](NAT-EGRESS-IP-WHITELIST.md)。

---

## 四、升级与回滚

```bash
docker build -t your-registry/wa-gateway:1.0.1 .
kubectl -n wa-gateway set image deployment/wa-gateway \
  wa-gateway=your-registry/wa-gateway:1.0.1 --record

# 回滚
kubectl -n wa-gateway rollout undo deployment/wa-gateway
```

Flyway 迁移采用「新增版本」策略，向下兼容，回滚无需改库。