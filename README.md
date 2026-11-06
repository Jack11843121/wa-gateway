# wa-gateway

> 一个开源、可自部署的 **WhatsApp Cloud API 网关**，对接 Meta 官方 Graph API，把你的系统与 WhatsApp 社媒消息能力接起来。

**它是什么**：部署在你自己的服务器上，向上游业务方提供统一的 REST API（发消息、收回调、管账号/号码/模板/媒体），向下游对接 `graph.facebook.com`，中间负责**异步削峰、多账号路由、幂等、限流、签名校验、回调重投、可观测**等所有「脏活」。

**它不是什么**：
- ❌ 不是 WhatsApp 官方客户端，也不包含任何业务逻辑、计费、风控、渠道分发。
- ❌ 不是对 Meta 报文的二次建模——消息/模板结构**原样透传**，Meta 加了新消息类型你无需改网关。
- ✅ 是一个「纯网关」：标准化接入 + 可靠性 + 可运维，开箱即用。

---

## 为什么需要它

直接调 Meta Graph API，你需要自己处理一堆非业务问题；wa-gateway 帮你解决：

| 痛点 | wa-gateway 的做法 |
| --- | --- |
| Graph API 有吞吐与限流，同步等待易超时 | 落库即返回 + **RocketMQ** 削峰投递，天然背压与重试 |
| 一条 MQ/回调断了会丢消息 | 全程落库、幂等去重、状态回写、失败按 MQ 重投 |
| Meta 回调需要签名校验、防重放 | 内置 `X-Hub-Signature-256` 校验 + SHA-256 去重 |
| 多个号码/WABA 要分别路由 token | 多账号管理，按 `phone_number_id` 路由，token 加密落库 |
| 下游回调是否送达无从确认 | 事件转发可 HMAC 签名，失败重试，状态可查 |
| 参数要能调、能观测、能灰度 | Nacos 热更新线程池/限流/MQ，Prometheus + 全链路 traceId |

---

## 主要特性

**消息收发**

- 发送：文本 / 模板 / 图片 / 音视频 / 文档 / 位置 / 联系人 / 交互 / 表情，**默认异步入队**（`async=false` 可同步）
- 回执：接收 Meta `sent/delivered/read/failed/deleted`，回写本地并可选推下游
- 入站：用户消息、状态变更经 webhook 落库后转发到你的回调地址

**账号与号码**

- WABA 账号接入（access-token / app-secret **AES-GCM 加密落库**）
- 号码：注册 / 注销 / 请求验证码 / 校验验证码 / 两步验证 / 商业简介

**媒体与模板**

- 媒体：上传 / 取下载地址 / 下载 / 删除
- 模板：创建 / 删除 / 远端查询 / 同步本地缓存

**可靠与安全**

- 幂等：发送用 `X-Request-Id`（Redis + DB 唯一约束双层防重）；webhook 用事件 SHA-256 去重
- 限流：Redis 固定窗口按账号限流（QPS 可配热更新）
- 安全：API Key 鉴权、webhook 签名校验、token 加密存储、非 root 镜像
- 优雅停机、MySQL/Flyway 版本化建表

**可运维**

- Spring Boot Actuator + Prometheus 指标引擎
- 全链路 `X-Trace-Id`（读入/生成 → 日志 → 回写响应头）
- 动态线程池：Nacos 下发参数，刷新事件实时调整，无需重启
- 原生 K8s 清单：HPA、存活/就绪探针、Pod 反亲和、固定 NAT 出口

---

## 架构

```
       你的系统                          wa-gateway                          Meta
  ┌────────────┐   POST /v1/...   ┌─────────────────────────────┐
  │  业务方     │ ───────────────► │ REST API (Controller)        │
  └────────────┘   X-Api-Key      │  ├─ 鉴权/限流/幂等            │
        ▲                         │  ├─ 落库 wa_message(queued)  │
        │  回调 / 查询              │  └─ 生产 MQ ─────┐            │
        │                         └───────────────────┼───────────┘
        │                                             ▼
        │                              ┌──────────────────────────┐
        │                              │ RocketMQ (WA_MESSAGE_SEND)│
        │                              └──────────┬───────────────┘
        │                                         │ 消费者
        │                              ┌──────────▼───────────────┐
        │                              │ 读记录→解密token→Graph API │──► graph.facebook.com
        │                              │ 回写 message_id/status    │
        │                              └──────────────────────────┘
        │
   Meta 给网关推 webhook：
  ┌────────────┐  POST /v1/webhooks   ┌─────────────────────────────┐
  │    Meta     │ ───────────────────► │ 握手/签名校验/去重/落库/MQ   │
  └────────────┘                       │ 消费者:回写状态→转发 callback │──► 你的回调地址
                                       └─────────────────────────────┘

  依赖：MySQL(流水) · Redis(幂等/限流) · RocketMQ(异步) · Nacos(配置/注册)
```

两条关键链路：

1. **出站**：`受理 → 落库(queued) → MQ → 消费者调 Graph API → 回写 sent/message_id`
2. **入站**：`Meta webhook → 签名校验 → SHA-256 去重 → 落库 → MQ → 消费者回写状态 + 记录入站消息 + 转发下游 callback`

> 完整数据流与设计取舍见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

---

## 技术栈

| 层 | 选型 |
| --- | --- |
| 语言/框架 | Java 17 · Spring Boot 3.2 |
| 配置/注册 | Spring Cloud Alibaba · **Nacos** |
| 异步消息 | Apache **RocketMQ** 5 |
| 存储 | **MySQL** 8（Flyway 建表 + JPA）· **Redis**（幂等/限流） |
| HTTP | httpclient5 连接池（出站 Graph API） |
| 文档/观测 | springdoc-openapi · Micrometer · Prometheus |

---

## 快速开始

**前置**：JDK 17 + Maven 3.9+ + Docker（跑依赖）。

```bash
# 1. 启动依赖（MySQL / Redis / RocketMQ / Nacos）
cd deploy
docker compose up -d

# 2. Nacos 导入配置（group 统一填 wa-gateway）
#    控制台 http://localhost:8848/nacos ，导入 deploy/nacos/*.yaml 共 5 份
#    详见 docs/DEPLOYMENT.md

# 3. 构建运行
cd ..
mvn clean package -DskipTests
java -jar target/wa-gateway.jar
```

> 无 Nacos 也能启动：`application.yml` 内置了可用默认值。

```bash
# 4. 接入一个 WABA 账号（响应里的 id 即 accountId）
curl -X POST http://localhost:8080/v1/accounts \
  -H 'X-Api-Key: change-me-admin-key' -H 'Content-Type: application/json' \
  -d '{"appId":"1250...","wabaId":"1040...","accessToken":"EAAT...",
       "appSecret":"...","verifyToken":"my-verify-token",
       "callbackUrl":"https://your-biz.example.com/wa/events"}'

# 5. 发一条文本消息（默认异步，X-Request-Id 幂等）
curl -X POST http://localhost:8080/v1/accounts/1/messages \
  -H 'X-Api-Key: change-me-admin-key' -H 'Content-Type: application/json' \
  -H 'X-Request-Id: req-0001' \
  -d '{"messaging_product":"whatsapp","to":"8613800138000",
       "type":"text","text":{"body":"hello from wa-gateway"}}'
```

```json
{"code":0,"message":"ok","data":{"requestId":"req-0001","messageRecordId":1,"status":"queued","accountId":1,"phoneNumberId":"..."}}
```

```bash
# 6. Meta 应用 → WhatsApp → 配置 webhook
#    回调地址 https://your-domain/v1/webhooks ，验证令牌 my-verify-token
```

---

## 目录结构

```
wa-gateway/
├── src/main/java/com/wagateway/
│   ├── controller/     # REST 接口
│   ├── service/        # 编排：消息/媒体/模板/号码/账号/webhook/回调
│   ├── client/         # Graph API 客户端（连接池 + 超时 + 错误归一）
│   ├── mq/             # RocketMQ 生产者/消费者
│   ├── model/          # JPA 实体
│   ├── repository/     # 数据访问
│   ├── redis/          # 幂等、限流
│   ├── config/         # 配置/线程池/安全/OpenAPI
│   ├── security/       # TraceId、API Key 过滤器
│   └── util/           # JSON/HMAC 签名/AES
├── src/main/resources/
│   ├── application.yml / bootstrap.yml
│   └── db/migration/V1__init.sql
├── deploy/
│   ├── k8s/            # K8s 清单（含固定 NAT 出站代理）
│   ├── nacos/          # Nacos 导入配置（按关注点拆分）
│   └── docker-compose.yml
└── docs/               # 全部文档
```

## 文档导航

| 文档 | 内容 |
| --- | --- |
| [docs/API.md](docs/API.md) | 完整 API 说明与报文示例 |
| [docs/DEPLOYMENT.md](docs/DEPLOYMENT.md) | K8s / Docker Compose 部署 |
| [docs/CONFIGURATION.md](docs/CONFIGURATION.md) | 全部配置、Nacos data-id、环境变量、依赖角色 |
| [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) | 架构、异步流水线、设计取舍、扩展点 |
| [docs/NAT-EGRESS-IP-WHITELIST.md](docs/NAT-EGRESS-IP-WHITELIST.md) | NAT 出口 IP 白名单方案 |
| [docs/RESOURCE-SIZING.md](docs/RESOURCE-SIZING.md) | 资源规格与容量规划 |

## 当前状态与边界

- ✅ 已实现：Meta **Cloud API**（官方 Graph API）完整网关链路。
- ⏳ 未实现：On-Premises（自建 Business API）适配；与 Cloud 使用同一套
  `controller/service/mq/redis` 骨架，仅替换 client 目标即可扩展。
- 消息/模板等结构**透传 Meta 原文**，因此 Meta 新增消息类型时网关无需升级。

## 常见问题

- **MQ / Nacos 各做什么？** 发送消息与 webhook 回调的异步削峰用 RocketMQ；配置下发与热更新用 Nacos。详见 [docs/CONFIGURATION.md](docs/CONFIGURATION.md)。
- **为什么默认异步？** Graph API 有吞吐与限流，`受理即返回 + MQ 削峰` 才能不丢消息、扛住峰值。
- **改了 MQ 主题没生效？** 主题同时声明在代码注解与 `gateway.mq.*`，需一并修改并重启（见配置文档）。
- **要不要 whitelist IP？** Meta 侧企业账号/风控可能要求固定出口 IP，方案见 [docs/NAT-EGRESS-IP-WHITELIST.md](docs/NAT-EGRESS-IP-WHITELIST.md)。

## License

[Apache License 2.0](LICENSE)