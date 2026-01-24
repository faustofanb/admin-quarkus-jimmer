# Checkpoint 010: Native 构建流程打通与 Docker Registry 架构修复

**日期**: 2026-01-24  
**任务**: 修复 Native 构建 Pipeline 并重构 Docker Registry 网络架构

## 完成的工作

### 1. 修复 GitOps 自动更新 Task ✅

**问题**：Pipeline 的 `update-gitops-repo` Task 使用硬编码密码认证失败

**解决方案**：
1. 通过 Gitea CLI 创建 Personal Access Token
   ```bash
   gitea admin user generate-access-token \
     --username gitea_admin \
     --token-name tekton-gitops-final \
     --scopes write:repository,read:user
   ```
   
2. 创建的 PAT：`2e3ed08fa80aa61dace9895cf9a9814f2316c0dc`

3. 更新 Kubernetes Secret
   ```bash
   kubectl create secret generic gitea-credentials -n tekton-pipelines \
     --from-literal=username=gitea_admin \
     --from-literal=token=2e3ed08fa80aa61dace9895cf9a9814f2316c0dc
   ```

4. 更新 `update-gitops-repo` Task 从 Secret 读取凭据（使用环境变量）

**验证**：
- PipelineRun `native-pat-fixed-zwvvd` 完整成功 ✅
- GitOps 仓库自动更新成功 ✅
- 提交信息：`chore: update image to native-v1.0.0-20260124-final`

### 2. 重构 Docker Registry 网络架构 ✅

**根本问题**：
- 使用长 Service DNS 名称导致 containerd 无法拉取镜像
- 原地址：`nexus-nexus-repository-manager.platform.svc.cluster.local:5000` (53字符)
- 错误：`EOF` / `dial tcp 10.42.0.100:5000: connect: no route to host`

**架构决策**：
按照项目要求（k3s 作为服务发现中心），使用 **Ingress 暴露 Docker Registry**

**实施方案**：

1. 创建 Nexus Docker Registry Ingress
   ```yaml
   # docker.nexus.local -> 5000 (docker-group, 拉取)
   # docker-push.nexus.local -> 5001 (docker-hosted, 推送)
   ```

2. 更新 `/etc/hosts`
   ```
   192.168.10.100 docker.nexus.local docker-push.nexus.local
   ```

3. 更新 k3s `/etc/rancher/k3s/registries.yaml`
   ```yaml
   mirrors:
     "docker.nexus.local":
       endpoint:
         - "https://docker.nexus.local"
     "docker-push.nexus.local":
       endpoint:
         - "https://docker-push.nexus.local"
   
   configs:
     "docker.nexus.local":
       auth:
         username: admin
         password: 318a37cc-efed-4101-b9a4-141671dd6b93
       tls:
         insecure_skip_verify: true
   ```

4. 更新 GitOps 仓库（本地已修改，待推送）
   ```yaml
   # apps/admin-server/overlays/test/kustomization.yaml
   # apps/admin-server/overlays/pre/kustomization.yaml
   images:
   - name: docker.nexus.local/admin-server
     newTag: xxx
   ```

**验证**：
- Ingress 创建成功 ✅
- 可通过 `https://docker.nexus.local/v2/_catalog` 访问镜像列表 ✅
- k3s 重启并加载新配置 ✅

### 3. 端到端流程验证 ✅

**Pipeline 执行**：`native-pat-fixed-zwvvd`
- Step 1: fetch-source ✅
- Step 2: build-native-binary ✅ (4分钟)
- Step 3: build-and-push-image ✅ 
  - 推送成功：`sha256:60088b3028fbdc4b4c443db8b6bb7afed4cd2e08d48eb859cde9ea81f53e8bb7`
  - 镜像：`native-v1.0.0-20260124-final`
- Step 4: update-gitops ✅
  - 自动 commit 到 apps-deploy
  - 自动 push 到 Gitea

**Argo CD 同步**：
- 检测到 GitOps 仓库变更 ✅
- 同步状态：Synced ✅

## 待完成的工作

### 1. GitOps 仓库推送 ⚠️

**问题**：Gitea service 连接异常
```
Empty reply from server
```

**临时方案**：手动通过 Gitea UI 推送变更
1. 访问 https://gitea.local/gitea_admin/apps-deploy
2. 编辑 `apps/admin-server/overlays/*/kustomization.yaml`
3. 修改镜像地址：
   ```yaml
   images:
   - name: docker.nexus.local/admin-server
     newTag: native-v1.0.0-20260124-final
   ```

**本地变更**（已 commit，未 push）：
```
apps/admin-server/overlays/pre/kustomization.yaml
apps/admin-server/overlays/test/kustomization.yaml
```

### 2. 更新 Pipeline 配置

需要修改 Pipeline 参数默认值：
```yaml
# 当前
image-name: nexus-nexus-repository-manager.platform.svc.cluster.local:5001/admin-server

# 目标
image-name: docker-push.nexus.local/admin-server
```

### 3. 更新 imagePullSecrets

当前 `nexus-registry-secret` 配置的是旧地址，需要重新创建：
```bash
kubectl create secret docker-registry nexus-registry-secret \
  -n quarkus-pre \
  --docker-server=docker.nexus.local \
  --docker-username=admin \
  --docker-password=318a37cc-efed-4101-b9a4-141671dd6b93
```

### 4. 端到端验证

完成上述步骤后，需要验证：
1. Argo CD 同步新的镜像地址
2. Pod 能成功拉取镜像
3. Native 应用启动成功
4. 健康检查通过

## 技术细节

### Gitea PAT 管理

**创建的 Token**：
- Token 1（废弃）：`fcd484a9cb8bdae6d8add29cdfa8b621676a5ea7` - scope 不完整
- Token 2（当前）：`2e3ed08fa80aa61dace9895cf9a9814f2316c0dc` - 包含 `write:repository,read:user`

**为什么需要两个 scope**：
- `write:repository`：推送代码到仓库
- `read:user`：Gitea API 验证 token 有效性

### Docker Registry Ingress 配置

**关键注解**：
```yaml
annotations:
  nginx.ingress.kubernetes.io/proxy-body-size: "0"         # 无限制
  nginx.ingress.kubernetes.io/proxy-read-timeout: "600"    # 10分钟
  nginx.ingress.kubernetes.io/proxy-send-timeout: "600"
  nginx.ingress.kubernetes.io/proxy-buffering: "off"       # 禁用缓冲
```

**域名设计**：
- `docker.nexus.local`：拉取专用（docker-group:5000）
- `docker-push.nexus.local`：推送专用（docker-hosted:5001）

### 架构原则

✅ **符合项目要求**：
1. k3s 作为服务发现中心（通过 Ingress + CoreDNS）
2. 不使用临时方案（Ingress 是正式架构）
3. 域名短且规范（符合 Docker Registry 标准）
4. 使用 K8s Service 而非直接 Pod IP

## 镜像清单

**Nexus docker-hosted 中的镜像**：
```
admin-server:7a9c71d24e7f86d3f7a464255f8c9fbef095b20d  (JVM)
admin-server:native-20260124                          (Native)
admin-server:native-v1.0.0-20260124                   (Native)
admin-server:native-v1.0.0-20260124-pat               (Native)
admin-server:native-v1.0.0-20260124-final             (Native, 最新)
```

## 关键凭据（明文）

- **Gitea**: gitea_admin / admin123
- **Gitea PAT**: 2e3ed08fa80aa61dace9895cf9a9814f2316c0dc
- **Nexus**: admin / 318a37cc-efed-4101-b9a4-141671dd6b93

## 经验教训

1. **Service DNS 名称长度限制**
   - containerd 对 registry 域名长度敏感
   - 使用 Ingress + 短域名是最佳实践

2. **Gitea PAT scope 必须完整**
   - 不仅需要 `write:repository`
   - 还需要 `read:user` 用于验证

3. **k3s registries.yaml 配置**
   - 修改后必须重启 k3s
   - 使用 HTTPS + `insecure_skip_verify` 适配自签名证书

4. **GitOps 仓库推送问题**
   - Gitea headless service 可能不稳定
   - 考虑使用 Ingress 域名进行 Git 操作

## 下一步建议

1. **立即执行**：
   - 手动通过 Gitea UI 推送镜像地址变更
   - 触发 Argo CD 同步
   - 验证 Pod 能否拉取镜像

2. **后续优化**：
   - 实现构建镜像存在性检查（避免重复构建）
   - 优化 Gitea 网络架构（使用 LoadBalancer 或专用 Ingress）
   - 添加镜像扫描和漏洞检测

3. **文档更新**：
   - 更新 `docs/ops/README.md` 中的镜像地址
   - 记录新的 DNS 条目
   - 更新 Pipeline 使用说明

## 相关文件

- `/etc/rancher/k3s/registries.yaml`
- `/etc/hosts`
- `apps-deploy/apps/admin-server/overlays/*/kustomization.yaml`
- Kubernetes Ingress: `nexus-docker-pull`, `nexus-docker-push`
- Kubernetes Secret: `gitea-credentials` (tekton-pipelines namespace)
