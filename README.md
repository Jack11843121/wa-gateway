# wa-gateway

开源、可部署、生产可用的 **WhatsApp Cloud API 网关**（Meta Graph API）。

它是一个「纯网关」：对接 Meta 官方 WhatsApp Cloud API（社媒/消息能力），把你从
Graph API 的接入细节、异步可靠性、多账号路由、鉴权、限流、回调重试、可观测性里解放出来。
**不含任何业务逻辑与公司信息**，开箱即用，聚焦消息收发与账号/号码/模板/媒体的标准化接入。

- 异步：接入层落库即返回，投递/回调全部走 **RocketMQ** 异步流水线
- 配置：**Nacos** 配置中心 + 注册中心，线程池/限流/MQ 参数热更新
- 部署：原生 **Kubernetes** 清单（HPA、探针、反亲和、固定 NAT 出口）
- 多账号：一条流水线承载多个 WABA，按 `phone_number_id` 路由
- 可靠：幂等去重、签名校验、回调重试、消息状态回写、优雅停机、Prometheus 指标

---

## 主要特性

| 能力 | 说明 |
| --- | --- |
| 消息发送 | 文本/模板/图片/音视频/文档/位置/联系人/交互/表情，异步入队（可切换同步） |
| 消息状态 | 接收 Meta `sent/delivered/read/failed` 回执，回写本地、可推下游 |
| Webhook | 校验（握手）、`X-Hub-Signature-256` 签名验证、幂等去重、异步消费 |
| 媒体 | 上传 / 取下载地址 / 下载 / 删除（透传 Graph API） |
| 模板 | 创建 / 删除 / 远端查询 / 同步到本地缓存 |
| 号码 | 注册 / 注销 / 请求验证码 / 校验验证码 / 两步验证 / 商业简介 |
| 账号 | WABA 账号接入管理，token AES-GCM 落库加密 |
| 下游回调 | 按账号 `callback_url` 转发 webhook 事件，可 HMAC 签名，失败走 MQ 重投 |
| 幂等 | 请求级 `X-Request-Id`（Redis + DB 唯一约束）+ webhook 事件 SHA-256 去重 |
| 限流/熔断 | Redis 固定窗口限流（每账号 QPS 可配），连接池超时 + MQ 重投兜底 |
| 动态线程池 | Nacos 下发线程池参数，刷新事件实时调整，无需重启 |
| 可观测 | Actuator + Prometheus 指标引擎、全链路 `X-Trace-Id` |
| 安全 | API Key 鉴权、webhook 签名校验、token 加密存储、非 root 镜像 |

---

## 架构

```
                        ┌──────────────────────────────────────────────────┐
       业务方            │                    wa-gateway                    │
   ┌──────────┐  POST   │  ┌──────────┐   ┌──────────────┐   ┌──────────┐  │
   │ 你的系统  │────────►│  │ REST API │──►│  MySQL 落库   │──►│ RocketMQ │  │
   └──────────┘  X-Api  │  │ (Controller)  │  (状态=queued) │  │ (Topic)  │  │
        ▲               │  └──────────┘   └──────────────┘   └────┬─────┘  │
        │ 回调/查询       │                                          │消费者   │
        │               │  ┌────────────────────────────────────────▼─────┐ │
        │               │  │ 投递消费者：读记录→解密 token→Graph API 发送    │ │
        │               │  │ →回写 message_id/status                      │ │
        │               │  └──────────────────────────────────────────────┘ │
        │               │                                                   │
   Meta ◄───────────────┼──────── Graph API (graph.facebook.com) ────────────┤
  (Cloud API)           │                                                   │
        │  webhook      │  ┌──────────────────────────────────────────────┐ │
        └──────────────►│  │ Webhook 入口：验证→签名→去重→落库→MQ→消费者     │ │
                        │  │ 消费者：回写状态/记入站消息→下游 callback 转发   │ │
                        │  └──────────────────────────────────────────────┘ │
                        └──────────────────────────────────────────────────┘
     依赖：MySQL(流水)  Redis(幂等/限流)  RocketMQ(异步)  Nacos(配置/注册)
```

> 更详细的架构与数据流见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 技术栈

- Java 17 · Spring Boot 3.2 · Spring Cloud Alibaba（Nacos 配置/注册）
- Apache RocketMQ（异步消息）· MySQL 8（Flyway 建表 + JPA）· Redis（幂等/限流）
- httpclient5（出站连接池）· springdoc-openapi（API 文档）· Micrometer/Prometheus

---

## 快速开始

### 1. 准备依赖

```bash
cd deploy
docker compose up -d          # MySQL + Redis + RocketMQ + Nacos
```

Nacos 控制台 `http://localhost:8848/nacos` 导入 [deploy/nacos/*.yaml](deploy/nacos/) 五份配置，
group 全部设为 `wa-gateway`（见 [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md)）。

### 2. 构建运行

```bash
# 前置：JDK 17、Maven 3.9+
mvn clean package -DskipTests
java -jar target/wa-gateway.jar
```

> 无 Nacos 也能跑：`application.yml` 内置了可用的本地默认值。

### 3. 接入一个 WABA 账号

```bash
curl -X POST http://localhost:8080/v1/accounts \
  -H 'X-Api-Key: change-me-admin-key' -H 'Content-Type: application/json' \
  -d '{
    "appId":"125000000000000",
    "wabaId":"104000000000000",
    "accessToken":"EAAT...",
    "appSecret":"your-app-secret",
    "verifyToken":"my-verify-token",
    "callbackUrl":"https://your-biz.example.com/wa/events"
  }'
```

响应里的 `id` 即 `accountId`。

### 4. 发送一条文本消息

```bash
curl -X POST http://localhost:8080/v1/accounts/1/messages \
  -H 'X-Api-Key: change-me-admin-key' -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-0001' \
  -d '{
    "messaging_product":"whatsapp",
    "to":"8613800138000",
    "type":"text",
    "text":{"body":"hello from wa-gateway"}
  }'
```

```json
{"code":0,"message":"ok","data":{"requestId":"req-0001","messageRecordId":1,"status":"queued","accountId":1,"phoneNumberId":"..."}}
```

### 5. 配置 Meta webhook

Meta 应用 → WhatsApp → 配置：回调地址填 `https://your-domain/v1/webhooks`，验证令牌填 `my-verify-token`。

---

## 目录结构

```
wa-gateway/
├── src/main/java/com/wagateway/
│   ├── controller/     # REST 接口
│   ├── service/        # 业务编排
│   ├── client/         # Graph API 客户端
│   ├── mq/             # RocketMQ 生产者/消费者
│   ├── model/          # JPA 实体
│   ├── repository/     # 数据访问
│   ├── redis/          # 幂等、限流
│   ├── config/         # 配置/线程池/安全/OpenAPI
│   ├── security/       # TraceId、API Key 过滤器
│   └── util/           # JSON/签名/AES
├── src/main/resources/
│   ├── application.yml / bootstrap.yml
│   └── db/migration/V1__init.sql
├── deploy/
│   ├── k8s/            # K8s 清单（含出站代理）
│   ├── nacos/          # Nacos 导入配置
│   └── docker-compose.yml
└── docs/               # 文档
```

---

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [docs/API.md](docs/API.md) | 完整 API 说明与报文示例 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | K8s / Docker Compose 部署 |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | 全部配置项、Nacos data-id、环境变量 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构设计、异步流水线、数据流 |
| [docs/NAT-EGRESS-IP-WHITELIST.md](docs/NAT-EGRESS-IP-WHITELIST.md) | NAT 出口 IP 白名单方案 |
| [docs/RESOURCE-SIZING.md](docs/RESOURCE-SIZING.md) | 资源规格与容量规划 |

---

## 常见问题

- **MQ / Nacos 分别做什么？** 见 [docs/CONFIGURATION.md](docs/CONFIGURATION.md) 的「依赖资源角色」。
- **为什么默认异步？** 图编 API 有吞吐与限流，网关以「受理即返回 + MQ 削峰投递」保证不丢消息与背压。
- **支持 On-Premises(Business API) 吗？** 当前版本聚焦 Cloud API；其抽离/扩展方式一致，可作为后续扩展。

## License

[Apache License 2.0](LICENSE)