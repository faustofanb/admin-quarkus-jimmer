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
| Nexus OSS | https://nexus.local/ | platform | Maven/OCI/Helm 制品仓库（UI/REST） |
| Nexus Docker Registry (OCI) | http(s)://nexus.local:5000/ (pull, docker-group)\nhttp(s)://nexus.local:5001/ (push, docker-hosted) | platform | 镜像 push/pull（端口已固定：group=5000, hosted=5001） |
| Gitea | https://gitea.local/ | platform | Git 代码仓库 |
| Kuboard v3 | https://kuboard.local/ | kuboard | 集群管理 UI |
| Tekton Dashboard | https://tekton.local/ | tekton-pipelines | Tekton UI（查看 Pipeline/TaskRun） |
| APP-Test | https://api-test.local/ | quarkus-test | 应用测试环境 - JVM构建 |
| APP-Pre  | https://api-pre.local/ | quarkus-pre | 应用预发布环境 - native构建|

### 1.1 /etc/hosts 示例

在需要访问的平台机器上添加（或用 `infra/scripts/setup.sh hosts` 生成托管段）：

```text
192.168.10.100  argocd.local nexus.local gitea.local kuboard.local tekton.local
```

> 说明：由于 `verge-mihomo` 占用 53 端口且不可改造，本平台暂不提供 dnsmasq。

## 2. 平台凭据（明文）

> ⚠️ 以下为内网开发环境凭据，仅供个人使用。
> 
> **重要**：根据项目要求，凭据必须以明文形式保存在文档中，禁止使用 SOPS 等加密方案。

### 2.1 Argo CD

- **URL**: https://argocd.local/
- **Username**: `admin`
- **Password**: `krdV05gYOArDsVeg`

获取/更新密码：
```bash
kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d
```

### 2.2 Nexus OSS

- **URL**: https://nexus.local/
- **Username**: `admin`
- **Password**: `318a37cc-efed-4101-b9a4-141671dd6b93`

获取初始密码：
```bash
kubectl -n platform exec -it deployment/nexus -- cat /nexus-data/admin.password
```

**Nexus Docker Registry**:
- Pull (docker-group): `http://nexus.local:5000/`
- Push (docker-hosted): `http://nexus.local:5001/`
- Username/Password: 同 Nexus UI 凭据

> OCI 镜像仓库策略：
> - HTTP registry 注意：Jib 默认不在 HTTP 连接上发送凭据，需要额外开启 `-DsendCredentialsOverHttp=true`（已在 Tekton Task 中配置）。
> - Pod 内访问应使用 k8s Service 域名：`nexus.platform.svc.cluster.local:5000/5001`

### 2.3 Gitea

- **URL**: https://gitea.local/
- **Username**: `gitea_admin`
- **Password**: `admin123`（已简化）
- **Personal Access Token**: `fcd484a9cb8bdae6d8add29cdfa8b621676a5ea7`（用于 Tekton GitOps）

重置密码：
```bash
kubectl -n platform exec deploy/gitea -- \
  gitea admin user change-password --username gitea_admin --password 'FanBiao@20000204'
# 取消强制修改密码标记（如需要）
kubectl exec -n middleware sts/postgresql -- psql -U postgres -d gitea -c \
  "UPDATE \"user\" SET must_change_password = false WHERE name = 'gitea_admin';"
```

### 2.4 Kuboard v3

- **URL**: https://kuboard.local/
- **Username**: `admin`
- **Password**: `Kuboard123`

### 2.5 Tekton Dashboard

- **URL**: https://tekton.local/
- **说明**: 无需登录，已配置为可编辑模式（可创建、重新运行 PipelineRun）

### 2.6 中间件（PostgreSQL / Redis）

**PostgreSQL**:
- Host (集群内): `postgresql.middleware.svc.cluster.local:5432`
- Username: `postgres`
- Password: `postgres123`
- Databases: `platform`, `gitea`

**Redis**:
- Host (集群内): `redis.middleware.svc.cluster.local:6379`
- Password: `redis123`

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
   - 构建 OCI 镜像（Quarkus + Jib，无 Docker）并推送到 **Nexus docker-hosted:5001**（拉取走 docker-group:5000）
   - 更新 `apps-deploy` 中对应 overlay 的镜像 tag 并提交
4) **Argo CD** 监听 `apps-deploy`：
   - test 环境自动同步
   - pre 环境手动批准（可选）

## 5. GitOps 仓库

- `infra`：平台基础设施与组件清单（bootstrap/platform/scripts/versions）
- `apps-deploy`：应用部署清单（Kustomize base/overlays）

## 6. 相关文档

- `cicd-platform-plan.md`：搭建计划与阶段性 checklist
- `versions.env.example`：版本锁定示例

## 8. Native 构建说明

### 8.1 Native vs JVM

| 特性 | JVM 模式 | Native 模式 |
|------|----------|-------------|
| 启动时间 | ~2-3秒 | ~50ms |
| 内存占用 | ~200-300MB | ~50-100MB |
| 镜像大小 | ~300MB | ~150MB |
| 构建时间 | ~30秒 | ~4分钟 |
| 热部署 | 支持 | 不支持 |
| 适用场景 | 开发/测试 | 生产环境 |

### 8.2 触发 Native 构建

**方式 1: 手动触发 PipelineRun**
```bash
kubectl create -f - <<YAML
apiVersion: tekton.dev/v1
kind: PipelineRun
metadata:
  generateName: native-build-
  namespace: tekton-pipelines
spec:
  pipelineRef:
    name: quarkus-native-cicd-pipeline
  params:
  - name: git-url
    value: http://gitea-http.platform.svc.cluster.local:3000/gitea_admin/admin-quarkus-jimmer.git
  - name: git-revision
    value: main
  - name: image-name
    value: nexus.default.svc.cluster.local:5001/admin-server
  - name: image-tag
    value: native-v1.0.0-$(date +%Y%m%d)
  - name: overlay-path
    value: apps/admin-server/overlays/pre
  workspaces:
  - name: shared-data
    volumeClaimTemplate:
      spec:
        accessModes: [ReadWriteOnce]
        resources:
          requests:
            storage: 10Gi
  - name: maven-settings
    configMap:
      name: maven-settings
  - name: gitops-workspace
    emptyDir: {}
  timeouts:
    pipeline: "2h"
YAML
```

**方式 2: 通过 Tekton Dashboard**
1. 访问 https://tekton.local/
2. 选择 `tekton-pipelines` namespace
3. 点击 `Pipelines` → `quarkus-native-cicd-pipeline`
4. 点击 `Create PipelineRun`
5. 填写参数并提交

### 8.3 监控构建进度

```bash
# 查看最新的 PipelineRun
kubectl get pipelinerun -n tekton-pipelines --sort-by=.metadata.creationTimestamp | tail -5

# 实时查看日志
kubectl logs -n tekton-pipelines -l tekton.dev/pipelineRun=<name>,tekton.dev/pipelineTask=build-native-binary -f
```

或在 Tekton Dashboard 中查看可视化进度。

### 8.4 已知问题和解决方案

#### 问题 1: GitOps 更新失败
**现象**: Pipeline 的 `update-gitops` 步骤失败，显示认证错误

**临时方案**: 手动更新 GitOps 仓库
1. 访问 https://gitea.local/gitea_admin/apps-deploy
2. 导航到 `apps/admin-server/overlays/pre/kustomization.yaml`
3. 点击编辑按钮
4. 修改 `newTag` 为构建的镜像标签（如 `native-v1.0.0-20260124`）
5. 提交更改
6. Argo CD 将在 30 秒内自动同步

**永久方案**（待实现）:
- 使用 Gitea Personal Access Token 替代密码认证
- 或改用 Kubernetes API 直接更新

#### 问题 2: 镜像拉取失败
**现象**: Pod 无法从 Nexus 拉取 Native 镜像

**排查步骤**:
```bash
# 1. 验证镜像是否存在
curl -u admin:318a37cc-efed-4101-b9a4-141671dd6b93 \
  "http://nexus.platform.svc.cluster.local:8081/service/rest/v1/search?repository=docker-hosted&name=admin-server"

# 2. 检查 Nexus Service 端口
kubectl get svc -n platform nexus -o yaml

# 3. 测试从 Pod 内访问
kubectl run test-registry --rm -i --image=curlimages/curl -- \
  curl -v http://nexus.platform.svc.cluster.local:5000/v2/_catalog
```

**可能原因**:
- NetworkPolicy 阻止跨 namespace 访问
- Nexus Docker Registry 端口未正确暴露
- 镜像推送实际未成功

#### 问题 3: 构建时间过长
**现象**: Native 构建耗时 20-30 分钟

**优化方案**:
1. 调整 GraalVM 内存配置（当前 12GB）
2. 使用 Maven 本地仓库缓存
3. 考虑使用更快的构建节点

### 8.5 构建资源需求

**Native 构建 Pod**:
- CPU: 4 cores (request), 8 cores (limit)
- 内存: 8GB (request), 16GB (limit)
- 存储: 10GB PVC（临时，构建完成后可删除）

**建议**:
- 确保节点有足够可用资源
- 避免同时运行多个 Native 构建

### 8.6 镜像仓库配置

**推送端口**: 5001 (docker-hosted)
- 地址: `nexus.platform.svc.cluster.local:5001`
- 用途: Kaniko 推送镜像

**拉取端口**: 5000 (docker-group，聚合)
- 地址: `nexus.platform.svc.cluster.local:5000`
- 用途: Kubernetes 拉取镜像
- 包含: docker-hosted + docker-proxy (Docker Hub)

## 7. 平台运维 (Operations)

所有的基础设施配置代码化存储在 `infra` 目录中。

### 7.1 自动化脚本 (`setup.sh`)

位于 `infra/scripts/setup.sh`，支持幂等操作：

```bash
# 1. 配置 /etc/hosts 映射 (argocd.local, nexus.local 等)
./setup.sh hosts

# 2. 部署基础组件 (Ingress, MetalLB, Cert-Manager, Argo CD) - 幂等
./setup.sh bootstrap

# 3. 安装平台层 (Nexus, Gitea, Tekton, Middleware) - 移交 Argo CD
./setup.sh install

# 4. 验证环境健康状态 (DNS, Pods, Nexus 端口)
./setup.sh verify
```

### 7.2 运维辅助脚本 (`manage.sh`)

位于 `infra/scripts/manage.sh`：

```bash
# 查看整体状态
./manage.sh status

# 等待平台就绪
./manage.sh wait-ready

# 诊断问题 (失败 Pod, PVC, Warning 事件)
./manage.sh diag
```

### 7.3 关键自动化机制

1.  **Nexus 初始化**:
    - **机制**: K8s Job (`nexus-init-job`) + Groovy 脚本
    - **触发**: Argo CD 同步 `infra/platform/nexus` 时自动运行
    - **功能**: 自动创建 Maven (hosted/proxy/group) 和 Docker (hosted/proxy/group) 仓库，无需人工登录 UI 配置。

2.  **K3s 镜像加速**:
    - **配置文件**: `infra/bootstrap/k3s-registries.yaml`
    - **效果**: 强制 K3s 节点拉取 `docker.io`, `quay.io`, `gcr.io` 等镜像时，代理到 `nexus.local:5000`。

3.  **Maven 统一配置**:
    - **配置文件**: `infra/platform/tekton/config/maven-settings.yaml`
    - **效果**: Tekton Pipeline 全局挂载此 ConfigMap，强制所有构建流量走 Nexus `maven-public` 组。

