# CI/CD 流水线指南

本文档介绍 GitHub Actions CI/CD 流水线的配置和使用。

## 📋 目录

- [流水线概览](#流水线概览)
- [CI 流水线](#ci-流水线)
- [CD 流水线](#cd-流水线)
- [Release 流水线](#release-流水线)
- [配置说明](#配置说明)
- [最佳实践](#最佳实践)

## 🏗️ 流水线概览

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              Git Push / PR                                   │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CI Pipeline (ci.yml)                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐     │
│  │ Checkout │─▶│  Build   │─▶│   Test   │─▶│ Security │─▶│  Docker  │     │
│  │   Code   │  │  Maven   │  │  JUnit   │  │   Scan   │  │   Push   │     │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──────────┘     │
└────────────────────────────────────┬────────────────────────────────────────┘
                                     ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           CD Pipeline (cd.yml)                               │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │  Update  │─▶│   Dev    │─▶│ Staging  │─▶│   Prod   │                    │
│  │ Manifest │  │  Deploy  │  │  Deploy  │  │  Deploy  │                    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘                    │
└─────────────────────────────────────────────────────────────────────────────┘
                                     │
                                     ▼ Tag Release
┌─────────────────────────────────────────────────────────────────────────────┐
│                        Release Pipeline (release.yml)                        │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐                    │
│  │   JVM    │  │  Native  │  │ Artifacts│  │  Notify  │                    │
│  │  Build   │  │  Build   │  │  Upload  │  │          │                    │
│  └──────────┘  └──────────┘  └──────────┘  └──────────┘                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

## 🔄 CI 流水线

### 触发条件

- Push 到 `main`, `develop`, `feature/**` 分支
- 创建 Pull Request 到 `main`, `develop`
- 创建 Tag `v*`

### 流程阶段

#### 1. Build & Test

```yaml
jobs:
  build:
    steps:
      - Checkout Code
      - Setup JDK 21
      - Cache Maven Dependencies
      - Build with Maven
      - Run Tests (with PostgreSQL & Redis services)
      - Upload Test Results
      - Upload Build Artifacts
```

#### 2. Code Quality

```yaml
jobs:
  code-quality:
    steps:
      - Checkout Code
      - Setup JDK
      - Check Code Style (Checkstyle)
      # - Run Spotbugs (可选)
```

#### 3. Security Scan

```yaml
jobs:
  security-scan:
    steps:
      - Checkout Code
      - Trivy Filesystem Scan
```

#### 4. Docker Build & Push

```yaml
jobs:
  docker:
    steps:
      - Checkout Code
      - Build Application
      - Setup Docker Buildx
      - Login to GHCR
      - Build & Push Docker Image
      - Trivy Image Scan
```

### 镜像标签策略

| 事件 | 标签示例 |
|------|----------|
| Push to main | `latest`, `main`, `sha-abc1234` |
| Push to develop | `develop`, `sha-abc1234` |
| Pull Request | `pr-123` |
| Tag v1.0.0 | `v1.0.0`, `1.0` |

## 🚀 CD 流水线

### 触发条件

- CI 流水线完成后自动触发
- 手动触发 (workflow_dispatch)

### GitOps 工作流

```
1. CI 构建完成，镜像推送到 GHCR
                    ▼
2. CD 更新 k8s/overlays/{env}/kustomization.yaml 中的镜像标签
                    ▼
3. Git commit & push 配置变更
                    ▼
4. Rancher Fleet / ArgoCD 检测到变更
                    ▼
5. 自动同步配置到 K8s 集群
```

### 手动部署

```bash
# 在 GitHub Actions 页面手动触发
# 选择环境和镜像标签
```

### 环境配置

| 环境 | 自动部署 | 需要审批 |
|------|----------|----------|
| dev | ✅ | ❌ |
| staging | ❌ | ✅ |
| prod | ❌ | ✅ |

## 📦 Release 流水线

### 触发条件

- 发布 GitHub Release
- 手动触发

### 流程

1. **Build JVM Image**: 构建标准 JVM 镜像
2. **Build Native Image** (可选): 构建 GraalVM Native 镜像
3. **Create Release Artifacts**: 上传 JAR 到 Release
4. **Notify**: 发送通知

### 创建 Release

```bash
# 方式一：GitHub UI
1. 进入 Releases 页面
2. 点击 "Draft a new release"
3. 创建 Tag (如 v1.0.0)
4. 填写 Release Notes
5. 发布

# 方式二：Git CLI
git tag -a v1.0.0 -m "Release v1.0.0"
git push origin v1.0.0
# 然后在 GitHub 创建 Release
```

## ⚙️ 配置说明

### GitHub Secrets

在仓库 Settings > Secrets and variables > Actions 中配置：

| Secret | 说明 | 必需 |
|--------|------|------|
| `GITHUB_TOKEN` | 自动提供 | ✅ |
| `GHCR_TOKEN` | GitHub PAT (packages:write) | 自动使用 GITHUB_TOKEN |
| `RANCHER_API_TOKEN` | Rancher API Token | 可选 |
| `RANCHER_API_URL` | Rancher API URL | 可选 |
| `SLACK_WEBHOOK_URL` | Slack 通知 Webhook | 可选 |

### 创建 GitHub PAT

1. 进入 GitHub Settings > Developer settings > Personal access tokens
2. 创建 Token，选择权限：
   - `write:packages` - 推送容器镜像
   - `read:packages` - 拉取容器镜像
   - `repo` - 用于 GitOps 更新

### 配置 Environment

1. 进入仓库 Settings > Environments
2. 创建环境：`dev`, `staging`, `production`
3. 为 `staging` 和 `production` 添加:
   - Required reviewers (需要审批)
   - Deployment branches (限制分支)

## 📋 最佳实践

### 分支策略

```
main (生产)
  ├── develop (开发)
  │     ├── feature/xxx
  │     ├── feature/yyy
  │     └── ...
  ├── hotfix/xxx
  └── release/v1.0.0
```

### Commit 规范

```
type(scope): subject

类型:
- feat: 新功能
- fix: Bug 修复
- docs: 文档更新
- style: 代码格式
- refactor: 重构
- test: 测试
- chore: 构建/工具

示例:
feat(auth): add JWT token refresh
fix(api): handle null pointer in user service
chore(k8s): update image tag to v1.0.1
```

### 版本号规范

使用语义化版本 (SemVer):

```
v{MAJOR}.{MINOR}.{PATCH}

MAJOR: 不兼容的 API 变更
MINOR: 向后兼容的功能新增
PATCH: 向后兼容的 Bug 修复

示例:
v1.0.0 - 首次正式发布
v1.1.0 - 新增功能
v1.1.1 - Bug 修复
v2.0.0 - 重大架构变更
```

### 安全最佳实践

1. **不要在代码中硬编码密钥**
2. **使用 GitHub Secrets 管理敏感信息**
3. **定期运行安全扫描**
4. **保持依赖更新**
5. **使用最小权限原则**

## 🔗 相关文档

- [GitHub Actions 文档](https://docs.github.com/en/actions)
- [Docker Build Push Action](https://github.com/docker/build-push-action)
- [Kustomize 文档](https://kustomize.io/)
- [Rancher Fleet 文档](https://fleet.rancher.io/)
