# 贡献指南 (Contributing Guide)

## 分支策略 (Branch Strategy)

本项目采用 **Git Flow** 简化版分支策略：

```
main (生产分支)
  │
  ├── develop (开发分支)
  │     │
  │     ├── feature/xxx (功能分支)
  │     ├── fix/xxx (修复分支)
  │     └── refactor/xxx (重构分支)
  │
  └── release/x.x.x (发布分支)
        │
        └── hotfix/xxx (热修复分支)
```

### 分支说明

| 分支类型 | 命名规范 | 来源 | 合并目标 | 说明 |
|---------|---------|------|---------|------|
| `main` | main | - | - | 生产环境代码，只接受 release/hotfix 合并 |
| `develop` | develop | main | main | 开发主干，集成所有功能 |
| `feature/*` | feature/模块-功能 | develop | develop | 新功能开发 |
| `fix/*` | fix/issue编号-描述 | develop | develop | Bug 修复 |
| `refactor/*` | refactor/模块-描述 | develop | develop | 代码重构 |
| `release/*` | release/x.x.x | develop | main, develop | 版本发布准备 |
| `hotfix/*` | hotfix/issue编号-描述 | main | main, develop | 生产环境紧急修复 |

### 分支命名示例

```bash
# 功能分支
feature/auth-jwt-login
feature/cache-redis-integration
feature/ops-cicd-pipeline

# 修复分支
fix/123-login-token-expired
fix/456-cache-null-pointer

# 重构分支
refactor/security-module-cleanup
refactor/cache-strategy-optimization

# 发布分支
release/1.0.0
release/1.1.0

# 热修复分支
hotfix/789-production-db-connection
```

---

## 提交规范 (Commit Convention)


本项目采用 **Conventional Commits** 规范。

> ⚠️ **重要提示**：所有提交信息（Subject 和 Body）**必须使用中文**编写。


### 提交格式

```
<type>(<scope>): <subject>

[optional body]

[optional footer(s)]
```

### Type 类型

| Type | 说明 | 示例 |
|------|------|------|
| `feat` | 新功能 | `feat(auth): 添加 JWT 登录接口` |
| `fix` | Bug 修复 | `fix(cache): 修复缓存穿透问题` |
| `docs` | 文档更新 | `docs(readme): 更新部署说明` |
| `style` | 代码格式（不影响逻辑） | `style(api): 统一代码缩进` |
| `refactor` | 重构（非新功能/非修复） | `refactor(security): 抽取通用鉴权逻辑` |
| `perf` | 性能优化 | `perf(query): 优化列表查询 N+1 问题` |
| `test` | 测试相关 | `test(auth): 添加登录接口单元测试` |
| `build` | 构建/依赖变更 | `build(deps): 升级 Quarkus 到 3.x` |
| `ci` | CI/CD 配置 | `ci(zadig): 添加 Native 构建流水线` |
| `chore` | 其他杂项 | `chore: 更新 .gitignore` |
| `revert` | 回滚提交 | `revert: 回滚 feat(auth) 提交` |

### Scope 范围

根据项目模块定义：

| Scope | 说明 |
|-------|------|
| `auth` | 认证授权模块 |
| `cache` | 缓存模块 |
| `distributed` | 分布式锁/ID/幂等 |
| `messaging` | 消息模块 |
| `availability` | 限流/熔断/重试 |
| `observable` | 可观测性模块 |
| `api` | REST API |
| `db` | 数据库/迁移 |
| `ops` | 运维/CI/CD |
| `deps` | 依赖管理 |

### 提交示例

```bash
# 功能提交
feat(auth): 实现 JWT 访问令牌生成

- 使用 SmallRye JWT 库
- 支持自定义 claims
- 配置 30 分钟过期时间

Closes #12

# 修复提交
fix(cache): 修复二级缓存 TTL 不生效问题

根因：Redis TTL 单位计算错误
方案：统一使用 Duration 进行时间计算

Fixes #45

# 运维提交
ci(ops): 添加 K3s + Zadig CI/CD 基础设施

- 初始化 ops-platform 目录结构
- 创建自动化部署脚本
- 配置 Gitea/Zadig/Kuboard Helm Values

# 依赖更新
build(deps): 升级 Quarkus 至 3.17.0

BREAKING CHANGE: 最低 JDK 版本要求提升至 21
```

### Footer 说明

| Footer | 说明 |
|--------|------|
| `Closes #issue` | 关闭相关 Issue |
| `Fixes #issue` | 修复相关 Issue |
| `Refs #issue` | 引用相关 Issue |
| `BREAKING CHANGE: xxx` | 破坏性变更说明 |
| `Co-authored-by: name <email>` | 协作者 |

---

## 工作流程 (Workflow)

### 1. 开发新功能

```bash
# 1. 从 develop 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/auth-jwt-login

# 2. 开发并提交
git add .
git commit -m "feat(auth): 实现 JWT 登录接口"

# 3. 推送并创建 PR
git push origin feature/auth-jwt-login
# 在 Gitea 创建 Pull Request -> develop

# 4. Code Review 通过后合并
# 5. 删除功能分支
git branch -d feature/auth-jwt-login
```

### 2. 发布版本

```bash
# 1. 从 develop 创建发布分支
git checkout develop
git checkout -b release/1.0.0

# 2. 版本号更新、文档完善
git commit -m "chore(release): 准备 1.0.0 版本发布"

# 3. 合并到 main 并打 tag
git checkout main
git merge release/1.0.0
git tag -a v1.0.0 -m "Release 1.0.0"
git push origin main --tags

# 4. 合并回 develop
git checkout develop
git merge release/1.0.0
git push origin develop

# 5. 删除发布分支
git branch -d release/1.0.0
```

### 3. 生产热修复

```bash
# 1. 从 main 创建热修复分支
git checkout main
git checkout -b hotfix/critical-bug

# 2. 修复并提交
git commit -m "fix(api): 紧急修复生产环境 NPE"

# 3. 合并到 main 并打 tag
git checkout main
git merge hotfix/critical-bug
git tag -a v1.0.1 -m "Hotfix 1.0.1"
git push origin main --tags

# 4. 合并回 develop
git checkout develop
git merge hotfix/critical-bug
git push origin develop

# 5. 删除热修复分支
git branch -d hotfix/critical-bug
```

---

## 代码审查 (Code Review)

### PR 检查清单

- [ ] 代码符合项目规范
- [ ] 提交信息符合 Conventional Commits
- [ ] 单元测试覆盖新功能
- [ ] 文档已更新（如需要）
- [ ] 无敏感信息泄露
- [ ] CI 流水线通过

### 合并策略

- **功能分支 → develop**: Squash Merge（合并为单个提交）
- **release/hotfix → main**: Merge Commit（保留完整历史）
- **release/hotfix → develop**: Merge Commit

---

## 版本号规范 (Versioning)

采用 **语义化版本 (Semantic Versioning)**：

```
MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
```

| 版本号 | 说明 | 示例 |
|--------|------|------|
| MAJOR | 破坏性变更 | 2.0.0 |
| MINOR | 新功能（向后兼容） | 1.1.0 |
| PATCH | Bug 修复（向后兼容） | 1.0.1 |
| PRERELEASE | 预发布版本 | 1.0.0-alpha.1 |
| BUILD | 构建元数据 | 1.0.0+20240118 |

### 版本号示例

```
1.0.0-SNAPSHOT    # 开发中
1.0.0-alpha.1     # 内测版本
1.0.0-beta.1      # 公测版本
1.0.0-rc.1        # 候选版本
1.0.0             # 正式版本
1.0.1             # 补丁版本
1.1.0             # 新功能版本
2.0.0             # 大版本更新
```
