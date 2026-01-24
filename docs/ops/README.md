# 平台运维文档 (Platform Operations)

本文档详细说明了 CI/CD 平台的架构设计、关键机制（服务发现、配置中心）以及运维操作指南。

## 1. 核心架构与机制

### 1.1 服务注册与发现 (Service Discovery)

本平台采用 **Kubernetes 原生服务发现机制 (Native K8s Service Discovery)**。

*   **实现方式**：
    *   所有应用组件（Gitea, Nexus, Postgres, Redis, Microservices）都通过 Kubernetes `Service` 暴露。
    *   集群内置 DNS (CoreDNS) 自动为每个 Service 分配域名。
*   **如何使用**：
    *   **集群内访问**：使用 `<service-name>.<namespace>.svc.cluster.local` 格式。
        *   例：Gitea 访问数据库 -> `postgresql.middleware.svc.cluster.local`
        *   例：Tekton 推送镜像 -> `nexus-nexus-repository-manager.platform.svc.cluster.local`
    *   **集群外访问**：通过 Ingress Controller (Nginx) 暴露的 HTTP/HTTPS 路由。
        *   例：用户访问 Argo CD -> `https://argocd.local/` (映射到 Ingress LB IP)

### 1.2 配置中心 (Configuration Center)

本平台采用 **GitOps** 模式，将 Git 仓库作为单一事实来源（Single Source of Truth），即配置中心。

*   **实现方式**：
    *   **存储**：所有配置文件（ConfigMap, Secret, Deployment YAML）托管在 Gitea 的配置仓库中。
        *   基础设施配置：`admin-quarkus-jimmer.git` -> `infra/` 目录
        *   应用配置：`admin-quarkus-jimmer.git` -> `gitops/` 目录 (原 `apps-deploy`)
    *   **分发**：Argo CD 定时轮询 Git 仓库，检测到变更后自动同步到 Kubernetes 集群。
    *   **注入**：Kubernetes 通过 Volume Mount (文件) 或 Environment Variables (环境变量) 将配置注入 Pod。
*   **如何使用**：
    *   **修改配置**：开发/运维人员直接修改 Git 仓库中的 YAML 文件 (如 `configmap.yaml`, `values.yaml`) 并提交。
    *   **生效**：Argo CD 自动同步（或手动点击 Sync），应用 Pod 通常配置为自动重载或通过 Argo CD 触发滚动更新。
    *   **敏感信息**：Secrets 虽然也在 Git 中（通常加密或仅存引用），但在本内部演示环境中暂以 Kubernetes Secret 对象形式手动/脚本创建，Git 中仅保留 Secret 定义框架。

## 2. 平台组件入口

| 组件 | URL | 账号 | 密码 |
|---|---|---|---|
| **Argo CD** | https://argocd.local | admin | krdV05gYOArDsVeg (或命令获取) |
| **Gitea** | https://gitea.local | gitea_admin | FanBiao@20000204 |
| **Nexus** | https://nexus.local | admin | 318a37cc... (命令获取) |
| **Kuboard** | https://kuboard.local | admin | Kuboard123 |
| **Tekton** | https://tekton.local | - | - |

> 提示：密码均可通过 `infra/scripts/manage.sh shell <service>` 进入容器或 kubectl 查看 secret 获取。

## 3. 运维操作指南 (`manage.sh`)

平台提供了统一的运维脚本 `infra/scripts/manage.sh`，封装了常用 kubectl 操作。

```bash
# 查看平台整体状态 (Pods, Nodes, Services)
./infra/scripts/manage.sh status

# 重启特定服务 (支持 gitea, nexus, argocd, 或 deployment 名称)
./infra/scripts/manage.sh restart gitea

# 查看服务实时日志
./infra/scripts/manage.sh logs nexus -f

# 进入服务容器终端 (Shell)
./infra/scripts/manage.sh shell admin-server

# [危险] 停止整个平台 (停止 K3s 服务)
./infra/scripts/manage.sh stop-all

# 启动整个平台 (启动 K3s 并等待就绪)
./infra/scripts/manage.sh start-all
```

## 4. CI/CD 流水线

*   **代码仓库 (Monorepo)**: `admin-quarkus-jimmer`
    *   包含源代码 (`src/`) 和 部署清单 (`gitops/`)。
*   **流程**：
    1.  代码提交 -> 触发 Tekton Pipeline.
    2.  Check Changes -> (可选) 跳过未变更构建。
    3.  Maven Build -> Native Build -> Docker Image Build.
    4.  推送镜像到 Nexus (Hosted Repo).
    5.  更新 `gitops/` 目录下的 Kustomization 文件 (Git Push).
    6.  Argo CD 检测到 Git 变更 -> 同步应用到 Test/Pre 环境。

## 5. 故障排查 (Troubleshooting)

*   **Pod ImagePullBackOff**: 
    *   通常是因为镜像构建失败导致 Nexus 中缺镜像，或者 K3s 节点未配置 Nexus 认证（已在 `infra/bootstrap/k3s-registries.yaml` 修复）。
    *   **修复**：手动触发一次 PipelineRun (`quarkus-native-deploy-fix`) 生成镜像。
*   **Service 503/404**:
    *   检查 Ingress: `kubectl get ingress -A`
    *   检查 Service Endpoints: `kubectl get endpoints -n <ns> <svc>`
    *   检查 Pod 日志: `./manage.sh logs <svc>`
