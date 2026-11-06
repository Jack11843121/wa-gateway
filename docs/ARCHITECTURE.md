# 架构设计

## 设计目标

- **纯网关**：不感知业务，只做可靠、可观测、可扩展的 WhatsApp Cloud API 接入层。
- **削峰异步**：接入层不等 Graph API，落库即返回，投递/回调走 MQ，天然背压与重试。
- **协议免维护**：消息/模板等复杂结构**透传 Meta 原文**，Meta 新增类型网关零改动。
- **多租号**：一套流水线承载多个 WABA，按 `wabaId/phone_number_id` 路由与隔离。

---

## 模块分层

```
controller ─> service ─> client (Graph API) ─> Meta
     │           │
     │           ├──> repository (MySQL)
     │           ├──> mq (RocketMQ)
     │           └──> redis (幂等/限流)
```

- `controller`：HTTP 入参/出参，薄。
- `service`：编排与事务边界，幂等、限流、状态流转。
- `client`：Graph API 薄封装，连接池 + 超时 + 出错归一化为 `GraphApiException`。
- `mq`：生产者/消费者，只传 ID，不传 token/报文（重新从 DB 载入，避免敏感信息入队）。
- `redis`：`IdempotencyService`（SET NX EX）、`RateLimiterService`（Lua 原子 INCR）。

---

## 出站消息异步流水线

```
POST /v1/accounts/{id}/messages
   │  1. 鉴权(API Key) + traceId
   │  2. 账号解析 + Redis 限流
   │  3. requestId 幂等（Redis + DB 唯一约束）
   │  4. 落库 wa_message(status=queued, payload=原文)
   │  5. 生产 MQ (WA_MESSAGE_SEND)
   ▼
返回 { messageRecordId, status:"queued" }
```

消费者（`MessageSendConsumer`）：

```
拉取 → 按 recordId 载入(bod/账号) → 解密 token
     → Graph API 发送 → 回写 message_id/status=sent
                    ↘ GraphApiException(业务错) → status=failed + error，不重试
                    ↘ 瞬时错(网络/5xx) → 抛出 → RocketMQ 重投
```

> 幂等性：消费者重复投递时，`status=sent` 直接跳过；正常失败只落 `failed`，不会重复扣费。

---

## 入站 Webhook 异步流水线

```
POST /v1/webhooks（Meta）
   │  1. 解析 wabaId → 定位账号
   │  2. X-Hub-Signature-256 校验（appSecret）
   │  3. body SHA-256 去重（Redis + DB 唯一）
   │  4. 落库 wa_webhook_event(status=1)
   │  5. 生产 MQ (WA_WEBHOOK_EVENT)
   ▼
200 OK（快速确认，避免 Meta 重试）
```

消费者（`WebhookEventConsumer`）：

```
载入事件 → 解析 changes
   ├── statuses[] → 回写 wa_message.status（sent/delivered/read/failed/deleted）
   ├── messages[] → 记录入站消息（按 wamid 幂等）
   └── 账号有 callbackUrl → CallbackService 转发（HMAC 签名）
         ├── 成功 → status=3
         └── 失败 → status=4, retryCount++ → 抛出 → RocketMQ 重投
```

---

## 关键设计点

### 1. 幂等
- 发送：`X-Request-Id` → Redis `SET NX EX` + `wa_message.uk_request_id` 唯一约束，双层防重。
- Webhook：请求体 `SHA-256` 去重键，Meta 重发同一事件只会处理一次。

### 2. 限流
- Redis 固定窗口（Lua 原子 `INCR`）按账号维度限流，阈值对齐 Meta 吞吐等级，可热调。
- 连接池（maxTotal/maxPerRoute）+ 读超时，防止单点慢调用拖垮全栈。

### 3. 动态线程池
- 回调 HTTP 之类阻塞 IO 走 `callbackExecutor`，参数由 Nacos 下发；
- `RefreshScopeRefreshedEvent` 触发 `ThreadPoolRefresher` 实时调整核心/最大线程数，不停机。

### 4. 可观测
- 全链路 `X-Trace-Id`（读入/生成 → MDC → 日志 → 回写响应头）。
- Actuator + Prometheus 指标，K8s 存活/就绪探针。
- 消息/事件全部落库，可追踪、可重放、可对账。

### 5. 安全
- REST：API Key 过滤器（webhook/health 免鉴权）。
- Webhook：HMAC-SHA256 常量时间比较。
- token：AES-GCM 落库，消费端按需解密，不进 MQ、不落日志。
- 镜像非 root 运行。

---

## 扩展点

- **On-Premises(Business API)**：在原仓库中与 Cloud 并列的 `onpremises` 模块，可用完全相同的
  `controller/service/mq/redis` 骨架替换 `client` 目标地址后接入，网关不感知。
- **更多通道/计费/风控**：在 MQ 消费者链路上追加消费者（打点、回调、审计）即可，无需改主链路。
- **多语言 SDK**：API 走纯 HTTP + JSON，任何语言可对接。