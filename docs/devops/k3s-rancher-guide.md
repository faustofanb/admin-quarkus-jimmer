# K3s + Rancher 开发环境指南

本文档介绍如何在 DevContainer 中使用 K3s 和 Rancher 进行 Kubernetes 开发和学习。

## 📋 目录

- [架构概览](#架构概览)
- [快速开始](#快速开始)
- [服务说明](#服务说明)
- [常用命令](#常用命令)
- [部署应用](#部署应用)
- [Rancher 使用](#rancher-使用)
- [故障排除](#故障排除)

## 🏗️ 架构概览

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          DevContainer                                    │
│  ┌─────────────────────────────────────────────────────────────────────┐│
│  │                        Docker Compose                               ││
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐               ││
│  │  │   App   │  │   K3s   │  │ Rancher │  │ Grafana │               ││
│  │  │(DevEnv) │  │(K8s)    │  │  (Mgmt) │  │(Monitor)│               ││
│  │  └─────────┘  └────┬────┘  └─────────┘  └─────────┘               ││
│  │                    │                                                ││
│  │         ┌──────────┼──────────┐                                    ││
│  │         ▼          ▼          ▼                                    ││
│  │    ┌─────────┐ ┌─────────┐ ┌─────────┐                            ││
│  │    │PostgreSQL│ │  Redis  │ │admin-app│  ← K8s Pods               ││
│  │    └─────────┘ └─────────┘ └─────────┘                            ││
│  └─────────────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────────┘
```

## 🚀 快速开始

### 1. 启动 DevContainer

在 VS Code 中打开项目，选择 "Reopen in Container"。

### 2. 启动 K3s 集群

```bash
# 基础服务 (K3s) 默认启动
# 检查 K3s 状态
docker-compose ps

# 初始化 kubectl 配置
./scripts/k3s-setup.sh
```

### 3. 启动 Rancher (可选)

```bash
# 启动 Rancher
docker-compose --profile rancher up -d rancher

# 初始化 Rancher
./scripts/rancher-setup.sh
```

### 4. 部署应用到 K3s

```bash
# 部署 dev 环境
./scripts/deploy-to-k3s.sh dev

# 部署 prod 环境
./scripts/deploy-to-k3s.sh prod
```

## 📦 服务说明

### Docker Compose Profiles

| Profile | 服务 | 说明 |
|---------|------|------|
| (默认) | app, k3s | 开发容器 + K3s 集群 |
| `rancher` | rancher | Rancher 管理平台 |
| `dev` | postgresql, redis, quarkus-dev | 传统 Docker 开发模式 |
| `deploy` | quarkus-app | Docker 部署测试 |
| `monitoring` | grafana, prometheus | 监控服务 |

### 端口映射

| 端口 | 服务 | 说明 |
|------|------|------|
| 6443 | K3s API | Kubernetes API Server |
| 80 | Traefik | K8s Ingress HTTP |
| 443 | Traefik | K8s Ingress HTTPS |
| 8443 | Rancher | Rancher UI (HTTPS) |
| 30080 | NodePort | Admin App (K8s) |
| 5432 | PostgreSQL | 数据库 (Docker 模式) |
| 6379 | Redis | 缓存 (Docker 模式) |
| 3000 | Grafana | 监控仪表板 |
| 9090 | Prometheus | 指标收集 |

## 🔧 常用命令

### K3s / Kubernetes

```bash
# 查看集群信息
kubectl cluster-info

# 查看节点
kubectl get nodes -o wide

# 查看所有 Pod
kubectl get pods -A

# 查看 admin-system 命名空间
kubectl get all -n admin-system

# 查看 Pod 日志
kubectl logs -f -n admin-system -l app.kubernetes.io/name=admin-app

# 进入 Pod
kubectl exec -it -n admin-system deploy/admin-app -- sh

# 使用 k9s (推荐)
k9s -n admin-system
```

### Docker Compose

```bash
# 查看运行中的服务
docker-compose ps

# 启动特定 profile
docker-compose --profile rancher up -d
docker-compose --profile monitoring up -d

# 查看日志
docker-compose logs -f k3s
docker-compose logs -f rancher

# 停止所有服务
docker-compose down

# 停止并清理数据
docker-compose down -v
```

### Kustomize

```bash
# 预览 dev 配置
kubectl kustomize k8s/overlays/dev

# 预览 prod 配置
kubectl kustomize k8s/overlays/prod

# 应用配置
kubectl apply -k k8s/overlays/dev
```

## 🚢 部署应用

### 方式一：使用部署脚本 (推荐)

```bash
# 设置 GHCR 认证 (私有镜像需要)
export GHCR_USERNAME=your-github-username
export GHCR_TOKEN=your-github-pat

# 部署到 dev 环境
./scripts/deploy-to-k3s.sh dev

# 部署指定镜像标签
./scripts/deploy-to-k3s.sh dev sha-abc123

# 部署到 prod 环境
./scripts/deploy-to-k3s.sh prod v1.0.0
```

### 方式二：手动部署

```bash
# 创建命名空间
kubectl apply -f k8s/base/namespace.yaml

# 创建 Secret (GHCR 认证)
kubectl create secret docker-registry ghcr-pull-secret \
  --namespace=admin-system \
  --docker-server=ghcr.io \
  --docker-username=$GHCR_USERNAME \
  --docker-password=$GHCR_TOKEN

# 应用配置
kubectl apply -k k8s/overlays/dev

# 等待部署完成
kubectl rollout status deployment/admin-app -n admin-system
```

### 方式三：本地镜像 (开发测试)

```bash
# 构建本地镜像
docker build -t admin-quarkus:local -f .devcontainer/Dockerfile.quarkus --target jvm-runner .

# 导入到 K3s
docker save admin-quarkus:local | docker exec -i $(docker-compose ps -q k3s) ctr images import -

# 更新部署使用本地镜像
kubectl set image deployment/admin-app admin-app=admin-quarkus:local -n admin-system
```

## 🎯 Rancher 使用

### 首次登录

1. 访问 https://localhost:8443
2. 接受自签名证书警告
3. 使用 Bootstrap 密码登录 (运行 `./scripts/rancher-setup.sh` 获取)
4. 设置新的管理员密码
5. 配置 Rancher Server URL

### 导入 K3s 集群

1. 进入 **Cluster Management**
2. 点击 **Import Existing**
3. 选择 **Generic**
4. 复制生成的 kubectl 命令
5. 在终端执行该命令

### Fleet GitOps

1. 进入 **Continuous Delivery**
2. 创建 **Git Repo**
3. 填写仓库地址和路径 (`k8s/overlays/dev`)
4. 选择目标集群
5. Fleet 会自动同步配置

## 🐛 故障排除

### K3s 无法启动

```bash
# 检查 K3s 日志
docker-compose logs k3s

# 重启 K3s
docker-compose restart k3s

# 完全重建
docker-compose down -v
docker-compose up -d
```

### kubectl 无法连接

```bash
# 检查 kubeconfig
cat ~/.kube/config

# 重新配置
./scripts/k3s-setup.sh

# 手动配置
export KUBECONFIG=/root/.kube/config
kubectl config set-cluster default --server=https://k3s:6443 --insecure-skip-tls-verify=true
```

### Pod 无法拉取镜像

```bash
# 检查 Secret
kubectl get secret ghcr-pull-secret -n admin-system -o yaml

# 重新创建 Secret
kubectl delete secret ghcr-pull-secret -n admin-system
kubectl create secret docker-registry ghcr-pull-secret \
  --namespace=admin-system \
  --docker-server=ghcr.io \
  --docker-username=$GHCR_USERNAME \
  --docker-password=$GHCR_TOKEN
```

### Rancher 无法访问

```bash
# 检查 Rancher 日志
docker-compose logs rancher

# 获取 Bootstrap 密码
docker logs rancher 2>&1 | grep "Bootstrap Password:"

# 重启 Rancher
docker-compose --profile rancher restart rancher
```

### 应用无法启动

```bash
# 查看 Pod 状态
kubectl describe pod -n admin-system -l app.kubernetes.io/name=admin-app

# 查看应用日志
kubectl logs -n admin-system -l app.kubernetes.io/name=admin-app --tail=100

# 检查依赖服务
kubectl get pods -n admin-system
kubectl logs -n admin-system -l app.kubernetes.io/name=postgresql
kubectl logs -n admin-system -l app.kubernetes.io/name=redis
```

## 📚 参考资源

- [K3s 官方文档](https://docs.k3s.io/)
- [Rancher 官方文档](https://ranchermanager.docs.rancher.com/)
- [Kustomize 官方文档](https://kustomize.io/)
- [kubectl Cheat Sheet](https://kubernetes.io/docs/reference/kubectl/cheatsheet/)
