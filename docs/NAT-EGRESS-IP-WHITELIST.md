# NAT 出口 IP 白名单

## 为什么需要固定出口 IP

1. **Meta 侧信任策略**：部分企业账号在 WhatsApp Manager 中配置「允许访问此应用的 IP 列表」，
   只允许白名单 IP 调用 Graph API / 接收 webhook。
2. **企业防火墙/风控**：内部往往要求对外出口 IP 固定，便于审计与放行。
3. **规避共享出口风险**：云 K8s 默认 NAT 出口 IP 动态/共享，可能被他人滥用连累 IP 信誉。

> 补充：Meta **入站** webhook 是从 Meta 服务器推送给你，你的入口应只放行 Meta 的
> 源 IP（Meta 官方会公布网段），与「出站白名单」是两件事。

---

## 方案总览

```
wa-gateway ──(http proxy)──▶ squid ──▶ Kubernetes LoadBalancer(固定 EIP) ──▶ internet ──▶ graph.facebook.com
```

网关只需配置一个出站代理，代理以固定公网 IP 出口，即可将该 IP 加入白名单。

### 配置开关

```yaml
gateway:
  outbound:
    proxy-host: "wa-gateway-egress-proxy.wa-gateway.svc.cluster.local"  # 或代理 EIP
    proxy-port: 3128
```

对应环境变量：`OUTBOUND_PROXY_HOST` / `OUTBOUND_PROXY_PORT`。
配置后，`RestTemplateConfig` 会为 Graph API 出站 `HttpClient` 挂上该代理。

---

## Kubernetes 落地步骤

1. 应用出站代理清单（已内置 Squid）：

```bash
kubectl apply -f deploy/k8s/07-egress-proxy.yaml
```

2. 为代理 Service 绑定固定公网 IP（任选，按云厂商）：

- **LoadBalancer 保留 EIP**：AWS NLB / 阿里云 SLB / 腾讯云 CLB 分配固定公网 IP，
  在 Service 注解中固定（见清单内注释）。
- **NAT 网关**：为 Pod 所在 VPC/子网配置 NAT 网关并绑定弹性 IP，则全部 Pod 统一出口，
  可跳过 squid（此时无需配 `gateway.outbound.proxy`，直接白名单 NAT 网关的 EIP）。

3. 将公网 IP 加入 Meta「应用允许列表」/ 企业防火墙白名单。

4. 验证出口 IP：

```bash
kubectl -n wa-gateway run curltest --rm -it --image=curlimages/curl -- sh
# 通过网关向 Graph API 发一次请求，或直接:
curl -x http://wa-gateway-egress-proxy.wa-gateway.svc.cluster.local:3128 https://api.ipify.org
```

> 直接探测：在网关 Pod 内 `curl -x $OUTBOUND_PROXY_HOST:$OUTBOUND_PROXY_PORT https://api.ipify.org`
> 得到的公网 IP 即需加白名单的 IP。

---

## 安全收口建议

- squid 默认 `allow all` 仅供内网使用；生产把 `acl` 收窄为仅网关网段（见清单注释）。
- 出站代理不要暴露公网入站；仅是 LoadBalancer 出口，不接 Ingress。
- webhook **入站**白名单单独配置：Meta 官方源网段（AS32934/AS54115 相关网段，以 Meta 最新公告为准）。
- 若使用云 NAT 网关方案，出站统一走 NAT EIP，无需 squid，运维更简单。