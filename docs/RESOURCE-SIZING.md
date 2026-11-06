# 资源规格与容量规划

## 应用本身（wa-gateway）

网关是 **I/O 密集型**：大量等待 Graph API / 下游回调网络 IO，CPU 消耗相对低，内存吃连接与队列。

| 规模 | 副本 | CPU request/limit | 内存 request/limit | 说明 |
| --- | --- | --- | --- | --- |
| 起步 | 2 | 500m / 2000m | 512Mi / 2Gi | 单副本数十 QPS 发送 |
| 中等 | 5~10 | 500m / 2000m | 512Mi / 2Gi | 并行发送 + 大量 webhook 回调 |
| 大流量 | 10~20 | 1000m / 3000m | 1Gi / 3Gi | 依赖 HPA 自动扩缩 |

> 已内置 HPA：CPU 70% / 内存 80% 触发，`min=2, max=20`。发送瓶颈通常不在网关，
> 而在 **Meta 每号码吞吐等级**（message tier），网关再多并行也受限于 Meta 侧配额。

### 关键参数对应资源

| 参数 | 默认 | 调优方向 |
| --- | --- | --- |
| `gateway.graph.max-total / max-per-route` | 200/100 | 并发连接上限，与副本数联动 |
| `gateway.thread-pool.callback.*` | 16/64/2000 | 回调 IO 线程，按下游并发调 |
| `DB_POOL_MAX` | 20（K8s 50） | 每副本 DB 连接数，注意副本数 × 池上限 ≤ MySQL max_connections |
| RocketMQ 消费者线程 | 由 consumeThreadMin/Max 决定 | 投递/回调消费并行度 |

---

## 依赖资源规格建议

### MySQL
- 存储：消息流水体量大（每消息一行 + payload MEDIUMTEXT），按日吞吐估算：
  `行数 ≈ 日发送量 + 日 webhook 事件数`，预留 3~6 个月，建议 **100GB 起，可扩容 SSD**。
- 实例：起步 2C4G；大流量 4C8G~8C16G，开启主从 + 备份。

### Redis
- 只存幂等键/限流计数，**内存占用低**（幂等键 TTL 默认 1 天）。
- 起步 1G~2G 内存即可；生产开主从 + 持久化（AOF）。

### RocketMQ
- 主题：`WA_MESSAGE_SEND`、`WA_WEBHOOK_EVENT` 两个主题。
  - 分区数按并发投递等级；每分区 ≈ 1k~2k TPS 参考，起步 `8~16` 分区，大流量 `32+`。
- 实例：起步 3 副本（1 master + 2 slave 同步）；云厂商按 TPS 选购 Standard/Pro。
- 消费组：`CG_WA_MESSAGE_SEND`、`CG_WA_WEBHOOK_EVENT`，预留 2 套（测试/生产隔离）。

### Nacos
- 生产建议 3 节点集群（依赖可插 MySQL 持久化），配置量小、负载极低，1C1G/节点足够。

---

## 容量经验公式（粗略）

```
发送 QPS = 并发投递消费者线程数 / 平均 Graph API 单次耗时(ms) × 1000
峰值 TPS ≈ 各号码 messaging_limit_tier 之和（受 Meta 限制）
webhook 流量 ≈ 发送量 × 平均状态数(约 3~4 条/message)
```

**结论**：先保证 Meta 号码吞吐等级最大化，再按 webhook 回调负载确定副本与 MQ 分区数，
最后按「副本数 × DB 池上限」校验 MySQL 连接上限即可。