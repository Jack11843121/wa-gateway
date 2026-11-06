# wa-gateway API 文档

基础地址：`http://<host>:8080`。除 Webhook 与健康检查外，所有接口需携带请求头：

```
X-Api-Key: <网关 API Key>
X-Request-Id: <可选，幂等键，发送消息时强烈建议>
```

OpenAPI 界面：`/swagger-ui.html`（或 `/swagger-ui/index.html`）。

---

## 通用约定

### 响应结构

```json
{ "code": 0, "message": "ok", "data": { ... }, "traceId": "..." }
```

`code = 0` 表示成功；非 0 为错误码。

### 错误码

| code | 含义 |
| --- | --- |
| 40000 | 参数错误 |
| 40100 | 未鉴权（API Key 缺失/错误） |
| 42900 | 触发限流 |
| 41001 | 账号不存在 |
| 41002 | 账号已停用 |
| 41008 | webhook 签名校验失败 |
| 51001 | Graph API 错误（详见返回 message） |
| 51002 | MQ 发送失败 |

---

## 1. 账号（WABA）管理

### 1.1 接入账号 `POST /v1/accounts`

请求体（`appId/wabaId/accessToken` 必填）：

```json
{
  "appId": "125000000000000",
  "wabaId": "104000000000000",
  "name": "主账号",
  "accessToken": "EAAT...",
  "appSecret": "your-app-secret",
  "verifyToken": "my-verify-token",
  "callbackUrl": "https://your-biz.example.com/wa/events",
  "status": 1,
  "graphVersion": "v20.0",
  "timezone": "Asia/Shanghai",
  "currency": "CNY"
}
```

响应 `data` 为账号视图，`data.id` 即后续所有接口的 `accountId`。
（`accessToken` 与 `appSecret` 以 AES-GCM 加密落库，响应不返回明文）

### 1.2 账号列表 `GET /v1/accounts`

### 1.3 账号详情 `GET /v1/accounts/{accountId}`

### 1.4 更新账号 `PUT /v1/accounts/{accountId}`

字段同 1.1，均可选；`accessToken` 非空时自动重新加密覆盖。

### 1.5 停用账号 `DELETE /v1/accounts/{accountId}`

---

## 2. 消息

### 2.1 发送消息 `POST /v1/accounts/{accountId}/messages`

- Query：
  - `phoneNumberId`（可选）：指定发送号码 ID；为空时取该账号下第一个在线号码
  - `async`（可选，默认 `true`）：`true` 异步入队；`false` 同步直连返回 Meta 结果
- Header：`X-Request-Id`（可选，幂等）

请求体为 **Meta 原生消息体**（透传，不二次建模）：

文本：
```json
{ "messaging_product": "whatsapp", "to": "8613800138000", "type": "text", "text": { "preview_url": false, "body": "hi" } }
```

模板：
```json
{ "messaging_product": "whatsapp", "to": "8613800138000", "type": "template",
  "template": { "name": "hello_world", "language": { "code": "en_US" },
                "components": [ { "type": "body", "parameters": [ { "type": "text", "text": "Jack" } ] } ] } }
```

图片（`id` 为上传后媒体 ID）：
```json
{ "messaging_product": "whatsapp", "to": "8613800138000", "type": "image",
  "image": { "id": "1765..." } }
```

交互式按钮：
```json
{ "messaging_product": "whatsapp", "to": "8613800138000", "type": "interactive",
  "interactive": { "type": "button", "body": { "text": "选一个" },
    "action": { "buttons": [ { "type": "reply", "reply": { "id": "yes", "title": "是" } } ] } } }
```

响应（异步受理）：
```json
{ "code": 0, "data": { "requestId": "req-0001", "messageRecordId": 1, "messageId": null,
  "status": "queued", "accountId": 1, "phoneNumberId": "..." } }
```

> 同类型更多字段（位置/联系人/表情/文档/音频/视频等）与 Meta 官方一致，直接透传。

### 2.2 查询消息（按 wamid）`GET /v1/messages/{messageId}`

返回本地消息流水（含 `status`：`queued/sent/delivered/read/failed`）。

### 2.3 查询消息（按幂等键）`GET /v1/messages/request/{requestId}`

### 2.4 标记已读 `PUT /v1/accounts/{accountId}/messages/read`

```json
{ "messaging_product": "whatsapp", "status": "read", "message_id": "wamid.HBg..." }
```

---

## 3. 媒体

### 3.1 上传 `POST /v1/accounts/{accountId}/media`（multipart/form-data）

表单字段：`file`（必填）；Query：`phoneNumberId`（可选）。

响应 `data` 为 Meta 媒体响应，含 `id`（媒体 ID）。

### 3.2 取下载地址 `GET /v1/accounts/{accountId}/media/{mediaId}/url`

```json
{ "code": 0, "data": { "url": "https://lookaside.fbsbx.com/..." } }
```

### 3.3 下载 `GET /v1/accounts/{accountId}/media/{mediaId}`

返回二进制流（网关用 token 替业务方下载）。

### 3.4 删除 `DELETE /v1/accounts/{accountId}/media/{mediaId}`

---

## 4. 模板

### 4.1 远端模板列表 `GET /v1/accounts/{accountId}/templates`

### 4.2 本地缓存模板 `GET /v1/accounts/{accountId}/templates/cached`

### 4.3 创建模板 `POST /v1/accounts/{accountId}/templates`

请求体为 Meta `message_templates` 结构（`name/language/category/components`）。

### 4.4 同步到本地缓存 `POST /v1/accounts/{accountId}/templates/sync`

### 4.5 删除模板（按名称） `DELETE /v1/accounts/{accountId}/templates/{name}`

---

## 5. 号码

基础路径 `/v1/accounts/{accountId}/phone-numbers`。

| 方法 & 路径 | 说明 |
| --- | --- |
| `GET /` | 号码列表（同时同步到本地缓存） |
| `GET /{phoneNumberId}` | 号码详情（远端） |
| `POST /{phoneNumberId}/register` | 注册（body：`{"messaging_product":"whatsapp","pin":"?"}` 等，透传） |
| `POST /{phoneNumberId}/deregister` | 注销 |
| `POST /{phoneNumberId}/request-code` | 请求验证码（body：`{"code_method":"SMS","language":"zh_CN"}`） |
| `POST /{phoneNumberId}/verify-code` | 校验验证码（body：`{"code":"123456"}`） |
| `POST /{phoneNumberId}` | 通用更新 / 两步验证（body：`{"messaging_product":"whatsapp","pin":"123456"}`） |
| `GET /{phoneNumberId}/business-profile` | 获取商业简介 |
| `POST /{phoneNumberId}/business-profile` | 更新商业简介（body：`{"messaging_product":"whatsapp","about":"...","address":"...","description":"...","email":"...","websites":[...],"vertical":"..."}`） |

---

## 6. Webhook（Meta → 网关）

### 6.1 验证（握手） `GET /v1/webhooks`

Meta 会请求：
```
GET /v1/webhooks?hub.mode=subscribe&hub.verify_token=<token>&hub.challenge=<challenge>
```
网关校验 `verify_token` 后原样返回 `challenge`（纯文本 `200`）。

### 6.2 事件接收 `POST /v1/webhooks`

- 网关校验 `X-Hub-Signature-256`（HMAC-SHA256，密钥为账号 `appSecret`）
- 按请求体 SHA-256 去重，落库后异步入队，快速返回 `200`
- 消费端回写消息状态、记录入站消息，并按账号 `callbackUrl` 转发

### 6.3 下游回调格式（网关 → 业务方）

网关将 Meta 原始事件 JSON 原样 `POST` 到账号配置的 `callbackUrl`，并附带：

```
X-Wa-Gateway-Event: whatsapp_webhook
X-Wa-Gateway-Signature: sha256=<HMAC-SHA256(body, appSecret)>   # 配置了 appSecret 时
```

业务方可用 `appSecret` 校验 `X-Wa-Gateway-Signature` 保证来源可信。

---

## 7. 健康检查

| 方法 & 路径 | 说明 |
| --- | --- |
| `GET /v1/health/live` | 存活探针 |
| `GET /v1/health` | 简单健康 |
| `GET /actuator/health/readiness` | K8s 就绪探针 |
| `GET /actuator/health/liveness` | K8s 存活探针 |
| `GET /actuator/prometheus` | Prometheus 指标 |