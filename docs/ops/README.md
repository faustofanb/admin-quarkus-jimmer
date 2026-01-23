# Ops / CI-CD Platform

本文档是“家庭 CI/CD 平台”的**使用说明入口**：包含各服务访问地址、凭据获取方式（不在仓库内写明文密码）、整体架构与 CI/CD 流程。

> 安全约束：我**不能**把真实账号/密码写入仓库文件（等同于提交 secrets）。
> 但我已为你生成一个**本地私有凭据文件**用于记录：
> `/home/fausto/.copilot/session-state/dfa35c0c-b49d-43f7-8751-8b393735dd11/files/platform-credentials.md`

## 1. 平台入口（HTTPS）

前提：本机 `/etc/hosts` 已把以下域名指向 Ingress LB（当前为 `192.168.10.100`）。

| 服务 | URL | Namespace | 说明 |
|---|---|---|---|
| Argo CD | https://argocd.local/ | argocd | GitOps/CD 控制面 |
| Nexus OSS | https://nexus.local/ | platform | Maven/OCI/Helm 制品仓库 |
| Gitea | https://gitea.local/ | platform | Git 代码仓库 |
| Kuboard v3 | https://kuboard.local/ | kuboard | 集群管理 UI |
| Tekton Pipelines | （无 UI） | tekton-pipelines | CI 执行引擎（可选后续再装 Dashboard） |

### 1.1 /etc/hosts 示例

在需要访问的平台机器上添加（或用 `platform-infra/scripts/setup.sh hosts` 生成托管段）：

```text
192.168.10.100  argocd.local nexus.local gitea.local kuboard.local
```

> 说明：由于 `verge-mihomo` 占用 53 端口且不可改造，本平台暂不提供 dnsmasq。

## 2. 凭据与密码获取方式（不入库）

以下命令会输出敏感信息，请在可信终端执行并妥善保管。

### 2.1 Argo CD

获取初始 admin 密码：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl -n argocd get secret argocd-initial-admin-secret \
  -o jsonpath='{.data.password}' | base64 -d; echo
```

### 2.2 Nexus OSS

Nexus 初始密码通常在 Pod 内（首次启动生成）：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl -n platform exec deploy/nexus-nexus-repository-manager -- \
  cat /nexus-data/admin.password; echo
```

建议：首次登录后立即在 UI 中修改 admin 密码。

### 2.3 Gitea

本次 bootstrap 期间创建了管理员账号（用户名固定），密码**不要写入仓库**；如忘记密码，建议通过 kubectl 查找 chart 生成的 secret 或直接在 Pod 内重置。

查看管理员账号是否存在：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl -n platform exec deploy/gitea -- gitea admin user list
```

重置管理员密码（示例，将 <NEW_PASSWORD> 替换为新密码）：

```bash
export KUBECONFIG=/etc/rancher/k3s/k3s.yaml
kubectl -n platform exec deploy/gitea -- \
  gitea admin user change-password --username gitea_admin --password '<NEW_PASSWORD>'
```

### 2.4 Kuboard v3

Kuboard 默认会创建 admin 用户（默认密码请按 Kuboard 文档或 UI 提示设置/查看），建议首次登录后立即修改。

## 3. 整体架构（当前落地）

```text
                     ┌─────────────────────────────┐
LAN / 本机浏览器  →  │ ingress-nginx (LB: 192.168.10.100)
                     └──────────────┬──────────────┘
                                    │ HTTPS (local CA)
      ┌─────────────────────────────┼─────────────────────────────┐
      │                             │                             │
┌─────▼─────┐                 ┌─────▼─────┐                 ┌─────▼─────┐
│  Argo CD  │                 │   Gitea   │                 │  Nexus OSS │
│  (argocd) │                 │ (platform)│                 │ (platform) │
└─────┬─────┘                 └─────┬─────┘                 └─────┬─────┘
      │ GitOps sync                  │ Git repo                     │ Maven/OCI/Helm
      │                              │ Webhook (后续)               │
      │                              ▼                               │
      │                        ┌──────────────┐                       │
      │                        │ Tekton (CI)   │  build/push image      │
      │                        │ (tekton-*)    ├───────────────────────┘
      │                        └──────┬───────┘
      │                               │
      ▼                               ▼
apps-deploy (Git)               K8s Deployments
(base/overlays)                 (test/pre namespaces)

Middleware:
- PostgreSQL (middleware)
- Redis (middleware)
```

## 4. CI/CD 流程（目标形态）

1) Developer push 代码到 **Gitea**（业务仓库）
2) Gitea Webhook → **Tekton Triggers**（待安装）
3) Tekton Pipeline：
   - clone 代码
   - Maven 构建（通过 `maven-settings` 强制走 **Nexus Maven group**）
   - 构建 OCI 镜像（kaniko，待补齐）并推送到 **Nexus docker group/hosted**
   - 更新 `apps-deploy` 中对应 overlay 的镜像 tag 并提交
4) **Argo CD** 监听 `apps-deploy`：
   - test 环境自动同步
   - pre 环境手动批准（可选）

## 5. GitOps 仓库

- `platform-infra`：平台基础设施与组件清单（bootstrap/platform/scripts/versions）
- `apps-deploy`：应用部署清单（Kustomize base/overlays）

## 6. 相关文档

- `cicd-platform-plan.md`：搭建计划与阶段性 checklist
- `versions.env.example`：版本锁定示例
