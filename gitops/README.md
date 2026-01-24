# apps-deploy

应用部署 GitOps 清单仓库（Kustomize）

## 目录结构

```
apps-deploy/
└── apps/
    └── admin-server/
        ├── base/              # 基础清单（共享配置）
        │   ├── deployment.yaml
        │   ├── service.yaml
        │   └── kustomization.yaml
        └── overlays/          # 环境特定配置
            ├── test/          # 测试环境
            │   └── kustomization.yaml
            └── pre/           # 预发布环境
                └── kustomization.yaml
```

## 使用说明

### 工作流程
1. Tekton Pipeline 构建镜像并推送到 Nexus
2. Pipeline 更新 `overlays/{test|pre}/kustomization.yaml` 中的镜像 tag
3. 提交到 Git 触发 Argo CD 同步
4. Argo CD 自动部署到对应环境

### 分支策略
- `main`: 生产配置（pre 环境）
- `develop`: 开发配置（test 环境）

### 环境差异
- **test**: 副本数1，资源限制较低，自动同步
- **pre**: 副本数2+，资源限制较高，手动 approve
