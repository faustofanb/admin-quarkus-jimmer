# 下一个会话重点关注

## 核心任务
**目标**: 修复 Native 构建自动化的最后两步
1. ✅ Native 编译
2. ✅ 镜像构建  
3. ❌ 镜像推送到 Nexus（需验证）
4. ❌ GitOps 自动更新

## 必须阅读的文档

### 1. Session 状态文档
**位置**: `~/.copilot/session-state/dfa35c0c-b49d-43f7-8751-8b393735dd11/`

- **plan.md** - 当前任务列表和进度
- **checkpoints/009-native-build-integration.md** - 本次 Native 构建详细记录
- **files/platform-credentials.md** - 所有平台凭据（明文）
- **files/next-session-focus.md** - 本文件

### 2. 项目运维文档
**位置**: `/home/fausto/workplace/admin-quarkus-jimmer/docs/ops/`

- **README.md** - 平台访问入口、架构、使用说明
  - 第 8 节：Native 构建说明（新增）
  - 包含已知问题和临时解决方案
- **cicd-platform-plan.md** - 平台建设计划和决策记录
  - 包含版本锁定、架构选择

### 3. GitOps 仓库
**位置**: `/home/fausto/workplace/apps-deploy/`

- **apps/admin-server/overlays/test/** - JVM 版本（自动化完成）
- **apps/admin-server/overlays/pre/** - Native 版本（手动更新）
  - `kustomization.yaml` - 需要更新 `newTag` 字段

## 需要立即解决的问题

### 🔴 优先级 P0：Nexus Docker Registry 网络问题

**问题描述**:
```
dial tcp 10.42.0.100:5000: connect: no route to host
dial tcp 10.42.0.100:5001: connect: no route to host
```

**已知信息**:
- Kaniko 报告推送成功（sha256:d6c33...）
- 但 Pod 无法从 pre namespace 拉取镜像
- API 查询返回空结果

**排查步骤**:
1. 检查 NetworkPolicy 是否阻止跨 namespace 访问
   ```bash
   kubectl get networkpolicy -A
   ```

2. 验证 Nexus Service 配置
   ```bash
   kubectl get svc -n platform nexus-nexus-repository-manager -o yaml
   kubectl get endpoints -n platform nexus-nexus-repository-manager
   ```

3. 检查 Nexus Docker Registry 实际配置
   ```bash
   curl -u admin:318a37cc-efed-4101-b9a4-141671dd6b93 \
     http://nexus-nexus-repository-manager.platform.svc.cluster.local:8081/service/rest/v1/repositories
   ```

4. 测试从目标 namespace 访问
   ```bash
   kubectl run test-nexus -n quarkus-pre --rm -i --image=curlimages/curl -- \
     curl -v http://nexus-nexus-repository-manager.platform.svc.cluster.local:5000/v2/_catalog
   ```

**可能的修复方案**:
- 添加 NetworkPolicy 允许规则
- 修复 Nexus Service ClusterIP
- 或使用 Ingress 暴露 Nexus（通过域名访问）

### 🟡 优先级 P1：GitOps Push 认证

**问题描述**:
```
fatal: unable to access 'http://20000204@gitea-http...': URL rejected: Bad hostname
```

**原因**: 
- 密码 `FanBiao@20000204` 中的 `@` 导致 URL 解析失败
- 已尝试 URL 编码、credential helper，均失败
- 简化密码为 `admin123` 后推送仍失败

**已知可行的方案**:
1. **临时方案**（已在用）: 手动通过 Gitea UI 更新
   - 访问 https://gitea.local/gitea_admin/apps-deploy
   - 编辑 `apps/admin-server/overlays/pre/kustomization.yaml`
   - Argo CD 自动同步

2. **推荐方案**: 使用 Gitea Personal Access Token
   ```bash
   # 在 Gitea UI 创建 PAT
   # Settings → Applications → Generate Token
   
   # 在 Task 中使用 PAT
   git push http://gitea_admin:<PAT>@gitea-http.platform.svc.cluster.local:3000/...
   ```

3. **备选方案**: 改用 Kubernetes API
   ```bash
   # 直接 patch Deployment 而不是推送 Git
   kubectl set image deployment/admin-server ... -n quarkus-pre
   ```

### 🟢 优先级 P2：构建复用机制

**问题**: 每次重试都要重新编译（浪费 ~25 分钟）

**建议方案**:
1. 添加镜像存在性检查（when 条件）
2. 使用持久卷缓存 Maven 依赖和二进制
3. 分离构建和部署步骤

## 验证清单

在开始修复前，先验证基础设施状态：

```bash
# 1. 检查所有平台组件状态
kubectl get pods -n platform
kubectl get pods -n argocd
kubectl get pods -n tekton-pipelines

# 2. 验证 Nexus 可访问性
curl -u admin:318a37cc-efed-4101-b9a4-141671dd6b93 \
  http://nexus.local/service/rest/v1/status

# 3. 检查最近的 PipelineRun
kubectl get pipelinerun -n tekton-pipelines \
  --sort-by=.metadata.creationTimestamp | tail -5

# 4. 查看 Argo CD 应用状态
kubectl get application -n argocd

# 5. 检查 Gitea 仓库状态
curl -u gitea_admin:admin123 \
  https://gitea.local/api/v1/repos/gitea_admin/admin-quarkus-jimmer
```

## 关键凭据速查

| 服务 | 用户名 | 密码/Token |
|------|--------|-----------|
| Gitea | gitea_admin | admin123 |
| Nexus | admin | 318a37cc-efed-4101-b9a4-141671dd6b93 |
| Argo CD | admin | `kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' \| base64 -d` |
| PostgreSQL | postgres | postgres123 |
| Redis | - | redis123 |
| Kuboard | admin | Kuboard123 |

## 成功标准

新会话成功的标志：
1. ✅ Native 镜像可以从 pre namespace 成功拉取
2. ✅ GitOps 更新自动化（或至少简化为一键操作）
3. ✅ 完整流程：代码提交 → Native 构建 → 自动部署

## 快速启动命令

```bash
# 1. 进入项目目录
cd /home/fausto/workplace/admin-quarkus-jimmer

# 2. 读取会话状态
cat ~/.copilot/session-state/dfa35c0c-b49d-43f7-8751-8b393735dd11/plan.md
cat ~/.copilot/session-state/dfa35c0c-b49d-43f7-8751-8b393735dd11/checkpoints/009-native-build-integration.md

# 3. 查看平台文档
cat docs/ops/README.md | grep -A 50 "Native 构建"

# 4. 开始排查网络问题
kubectl get networkpolicy -A
kubectl describe svc -n platform nexus-nexus-repository-manager

# 5. 或直接手动触发一次完整构建测试
# （前提是上述问题已解决）
```

## 技术债务

记录下来待后续处理：

1. **Maven 依赖缓存** - 每次构建都重新下载（虽然有 Nexus 代理）
2. **PipelineRun 自动清理** - 完成的 PipelineRun 没有自动清理策略
3. **资源 requests/limits** - 大部分 Pod 未设置，可能影响调度
4. **监控告警** - 缺少构建失败通知机制
5. **文档自动化** - 平台凭据应该自动更新到文档

## 最后的建议

1. **不要急于重新构建** - 先修复基础设施问题
2. **一次只解决一个问题** - 避免多个变更干扰排查
3. **保持文档同步** - 修复后立即更新 checkpoint
4. **优先验证网络** - 这是当前的最大阻塞点

Good luck! 🚀

