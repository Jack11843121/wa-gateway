# 配置说明

wa-gateway 的配置分三层：

1. **`application.yml`**：本地/容器默认值，保证「无 Nacos 也能起」。
2. **`bootstrap.yml`**：Nacos 配置中心连接信息（`NACOS_*` 环境变量）。
3. **Nacos**：主体可调配置，按 data-id 拆分，支持热更新。

---

## 依赖资源角色

| 组件 | 作用 | 是否必需 | 丢失影响 |
| --- | --- | --- | --- |
| **MySQL** | 账号/号码/消息/事件/模板流水持久化 | 必需 | 无法落库、幂等失效 |
| **Redis** | 幂等去重、限流计数 | 必需 | 去重与限流降级 |
| **RocketMQ** | 消息投递、webhook 处理的异步削峰 | 必需 | 异步链路不可用（同步模式仍可发消息） |
| **Nacos** | 配置中心 + 服务注册发现 | 可选* | 用本地默认配置运行 |

> \* Nacos 配置服务不可用时，如启动阶段已成功拉取过一次，会沿用本地缓存继续运行；若从未拉到，则回退 `application.yml` 默认值。

### MQ 主题说明

| 主题 | 消费者组 | 标签 | 用途 |
| --- | --- | --- | --- |
| `WA_MESSAGE_SEND` | `CG_WA_MESSAGE_SEND` | `SEND_TAG` | 出站消息投递任务 |
| `WA_WEBHOOK_EVENT` | `CG_WA_WEBHOOK_EVENT` | `EVENT_TAG` | 入站 webhook 事件处理 |

生产建议**预创建主题**（分区数按并发等级，见 [RESOURCE-SIZING.md](RESOURCE-SIZING.md)），并关闭自动建主题。

> ⚠️ 主题/消费者组同时声明在代码注解 `MessageSendConsumer` / `WebhookEventConsumer` 中，
> 与 `gateway.mq.*` 配置必须一致；改名属于拓扑变更，需改代码 + 配置并重启。

---

## Nacos data-id（group 默认 `wa-gateway`）

| data-id | 类型 | 关注点 |
| --- | --- | --- |
| `wa-gateway-{profile}.yaml` | 主配置 | MySQL/Redis/RocketMQ/端口/actuator |
| `wa-gateway-common.yaml` | shared-configs | Graph API、webhook、回调、token 加密 |
| `wa-gateway-mq.yaml` | extension-configs | MQ 主题/组/标签 |
| `wa-gateway-threadpool.yaml` | extension-configs | 线程池 |
| `wa-gateway-ratelimit.yaml` | extension-configs | 限流 |

参考文件见 [`deploy/nacos/`](../deploy/nacos/)。导入时注意 **group 与 `NACOS_GROUP` 保持一致**。

---

## 环境变量清单

| 变量 | 说明 | 默认 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | 环境名（决定主 data-id 后缀） | `dev` |
| `NACOS_SERVER_ADDR` | Nacos 地址（逗号分隔集群） | `127.0.0.1:8848` |
| `NACOS_USERNAME` / `NACOS_PASSWORD` | Nacos 鉴权 | `nacos/nacos` |
| `NACOS_NAMESPACE` | 命名空间 id（环境隔离） | 空 |
| `NACOS_GROUP` | 配置/服务 group | `wa-gateway` |
| `MYSQL_URL` / `MYSQL_USER` / `MYSQL_PASSWORD` | MySQL | 本地默认 |
| `DB_POOL_MAX` / `DB_POOL_MIN` | Hikari 连接池 | `20 / 5` |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DB` | Redis | 本地默认 |
| `ROCKETMQ_NAME_SERVER` | NameServer 地址 | `127.0.0.1:9876` |
| `ROCKETMQ_PRODUCER_GROUP` | 生产者组 | `WA_GATEWAY_PRODUCER` |
| `ROCKETMQ_ACCESS_KEY` / `ROCKETMQ_SECRET_KEY` | ACL/云 RocketMQ 密钥 | 空 |
| `GATEWAY_API_KEY` | REST API 访问密钥 | `change-me-admin-key` |
| `TOKEN_ENCRYPT_SECRET` | token 落库 AES 密钥 | 占位（务必替换） |
| `GRAPH_BASE_URL` / `GRAPH_VERSION` | Graph API 根/版本 | `graph.facebook.com` / `v20.0` |
| `WEBHOOK_SIGNATURE_REQUIRED` | 是否强制 webhook 签名 | `true` |
| `WEBHOOK_VERIFY_TOKEN` | 应用级 webhook 验证令牌 | 空 |
| `OUTBOUND_PROXY_HOST` / `OUTBOUND_PROXY_PORT` | 出站固定代理（NAT 出口） | 空 / 0 |
| `RATE_LIMIT_ENABLED` / `RATE_LIMIT_SEND_PER_SECOND` | 限流开关/QPS | `true` / `50` |

---

## `gateway.*` 配置项（Nacos `wa-gateway-common.yaml` 可覆盖并热更新）

| 配置 | 说明 |
| --- | --- |
| `gateway.graph.*` | Graph API 地址/版本/超时/连接池 |
| `gateway.webhook.signature-required` | 强制签名校验 |
| `gateway.webhook.verify-token` | 应用级验证令牌 |
| `gateway.callback.*` | 下游回调超时/重试退避参数 |
| `gateway.idempotent.ttl-seconds` | 幂等键 TTL |
| `gateway.token.secret` | token 加密密钥 |
| `gateway.outbound.*` | NAT 出口代理解析 |
| `gateway.mq.*` | 主题/组/标签 |
| `gateway.rate-limit.*` | 限流开关与阈值 |
| `gateway.thread-pool.callback.*` | 回调 HTTP 线程池（热更新） |

> 线程池热更新：修改 Nacos → `RefreshScopeRefreshedEvent` → 实时调整核心/最大线程数，无需重启。
> 这是对「动态线程池」诉求的零依赖实现（对标 dynamic-tp 思路，但不上额外依赖）。

---

## 安全建议

1. 生产用 K8s Secret / 云 KMS / SealedSecret 注入 `GATEWAY_API_KEY`、`TOKEN_ENCRYPT_SECRET`、数据库与 MQ 密钥，勿提交明文。
2. `gateway.token.secret` 至少 32 字节随机；更换时需重新加密已存 token（或重新导入账号）。
3. `gateway.webhook.signature-required` 保持 `true`；每个账号配置独立 `appSecret`。
4. 出站 Graph API 流量建议收敛到固定 NAT 出口 IP（见 [NAT-EGRESS-IP-WHITELIST.md](NAT-EGRESS-IP-WHITELIST.md)）。