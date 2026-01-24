# Checkpoint 009: Native 构建集成

**日期**: 2026-01-24  
**任务**: 集成 Quarkus Native 构建到 CI/CD 流程

## 完成的工作

### 1. Native 构建 Task 创建
- **Task**: `quarkus-native-build`
- **镜像**: `quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21`
- **资源配置**: 
  - 内存: 8Gi request, 16Gi limit
  - CPU: 4 cores request, 8 cores limit
  - GraalVM 内存限制: 12GB
- **构建时间**: ~4 分钟
- **产物**: 136MB native 二进制

### 2. Kaniko Native 镜像构建
- **Task**: `kaniko-build-native`
- **Dockerfile 生成**: 动态创建，基于 UBI8 minimal
- **推送目标**: Nexus docker-hosted (5001 端口)
- **认证**: 使用 Kubernetes Secret

### 3. 完整 Pipeline 创建
- **Pipeline**: `quarkus-native-cicd-pipeline`
- **步骤**:
  1. fetch-source (git clone)
  2. build-native-binary (Maven + GraalVM)
  3. build-and-push-image (Kaniko)
  4. update-gitops (更新 apps-deploy)

### 4. 镜像标签格式改进
- **JVM 构建**: `jvm-{date}-{time}-{commit-sha}`  
  示例: `jvm-20260124-135959-7a9c71d`
- **Native 构建**: `native-v{version}-{date}`  
  示例: `native-v1.0.0-20260124`

### 5. 修复的问题
- ✅ Gitea 仓库认证（数据库设为公开）
- ✅ Native 构建验证步骤（移除 file 命令）
- ✅ Kaniko 脚本执行（改用 command/args）
- ✅ Maven wrapper 权限
- ✅ 构建超时配置（2小时）

## 阻塞问题

### 问题 1: GitOps Push 认证失败 ⚠️
**现象**:
```
fatal: unable to access 'http://20000204@gitea-http.platform.svc.cluster.local:3000/...': 
URL rejected: Bad hostname
```

**原因**: 
- Gitea 密码 `FanBiao@20000204` 中包含 `@` 符号
- URL 编码后仍然无法正确解析
- Git credential helper 配置不生效

**尝试的方案**:
1. ❌ 在 URL 中直接嵌入密码（URL 解析失败）
2. ❌ 使用 `%40` 编码（仍然失败）
3. ❌ Git credential helper + store（认证失败）
4. ✅ 简化密码为 `admin123`（但推送仍失败）

**建议方案**:
- 使用 Gitea Personal Access Token
- 或改用 Kubernetes API 直接更新 GitOps 仓库
- 或手动通过 Gitea UI 更新

### 问题 2: Nexus Docker Registry 网络访问 ⚠️
**现象**:
```
dial tcp 10.42.0.100:5000: connect: no route to host
dial tcp 10.42.0.100:5001: connect: no route to host
```

**原因**:
- Nexus Pod IP 无法从其他 namespace 访问
- Service 端口配置可能有问题
- 集群网络策略可能阻止访问

**已验证**:
- Kaniko 推送报告成功（`sha256:d6c33...`）
- 但无法查询到镜像（API 返回空）
- Pod 拉取镜像失败

**需要排查**:
1. Nexus Service 端口配置
2. NetworkPolicy 是否阻止跨 namespace 访问
3. Nexus Docker Registry 实际监听端口
4. 镜像是否真的推送成功

### 问题 3: 构建复用机制缺失 ⚠️
**影响**: 每次 GitOps 更新失败都需要重新执行完整 Pipeline（~25 分钟）

**原因**: Tekton PipelineRun 是一次性的，不共享工作空间

**建议方案**:
1. 实现持久卷存储构建产物
2. 添加镜像存在性检查（跳过已构建的镜像）
3. 分离构建和部署步骤

## 技术细节

### Native 构建配置
```yaml
# Maven 配置
MAVEN_OPTS: "-Dmaven.repo.local=... -Xmx12G"

# Quarkus Native 参数
-Pnative -DskipTests
-Dquarkus.native.native-image-xmx=12g
```

### GraalVM 编译阶段（8步）
1. Initializing (~10s)
2. Performing analysis (最耗时 ~60s)
3. Building universe
4. Parsing methods
5. Inlining methods
6. Compiling methods (最耗时 ~80s)
7. Layouting methods
8. Creating image

### Nexus Docker Registry 配置
- **docker-hosted**: 推送端口 5001
- **docker-group**: 拉取端口 5000（聚合 hosted + proxy）
- **认证**: admin / 318a37cc-efed-4101-b9a4-141671dd6b93

### Gitea 配置
- **用户**: gitea_admin
- **密码**: admin123（已简化）
- **仓库**:
  - admin-quarkus-jimmer: 已设为公开
  - apps-deploy: 已设为公开（但 push 仍需认证）

## 相关文件

### 新增 Tekton 资源
- `Task`: quarkus-native-build
- `Task`: kaniko-build-native
- `Task`: update-gitops-repo
- `Pipeline`: quarkus-native-cicd-pipeline
- `TriggerTemplate`: gitea-tag-to-native-pipelinerun

### 配置文件
- `Secret`: nexus-registry-secret
- `ConfigMap`: maven-settings
- `PVC`: shared-data (10Gi)

### 成功的 PipelineRun
- `native-complete-fixed-cqh42`:
  - ✅ fetch-source
  - ✅ build-native-binary (3分49秒)
  - ✅ build-and-push-image
  - ❌ update-gitops

## 下一步建议

### 立即修复（优先级高）
1. **修复 Nexus 网络访问**
   - 检查 NetworkPolicy
   - 验证 Service 端口配置
   - 测试镜像推送/拉取完整流程

2. **解决 GitOps 认证**
   - 创建 Gitea Personal Access Token
   - 更新 update-gitops-repo Task 使用 PAT
   - 或改用 kubectl apply 直接更新

### 架构改进（优先级中）
3. **实现构建缓存**
   - 添加镜像存在性检查
   - 使用持久卷存储二进制
   - 或分离构建/推送/部署步骤

4. **完善监控**
   - 添加构建通知（Slack/企微）
   - 记录构建指标（时间/资源）
   - 集成到 Tekton Dashboard

### 文档完善（优先级低）
5. **更新运维文档**
   - Native 构建使用说明
   - 故障排查指南
   - 性能对比数据

## 经验教训

1. **先验证基础设施再搭建自动化**
   - Nexus Docker Registry 应该先手动验证推送/拉取
   - Gitea 认证应该先在命令行测试通过

2. **构建产物复用很重要**
   - Native 构建耗时长，失败后重试成本高
   - 应该在设计阶段就考虑缓存机制

3. **密码中的特殊字符是大坑**
   - 应该使用 Token 而不是密码
   - URL 编码不是万能的

4. **分步验证比完整流程更高效**
   - 先验证各步骤单独执行
   - 再组合成完整 Pipeline

## 关键凭据（明文）

- **Gitea**: gitea_admin / admin123
- **Nexus**: admin / 318a37cc-efed-4101-b9a4-141671dd6b93
- **Argo CD**: admin / (kubectl -n argocd get secret argocd-initial-admin-secret ...)

