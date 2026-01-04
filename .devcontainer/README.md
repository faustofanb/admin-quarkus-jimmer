# Quarkus DevContainer 部署指南

本指南介绍如何在 DevContainer 环境中打包、部署和测试 Quarkus 应用。

## 📁 目录结构

```
.devcontainer/
├── Dockerfile                 # DevContainer 基础镜像
├── Dockerfile.quarkus         # Quarkus 应用多阶段构建镜像
├── docker-compose.yml         # Docker Compose 配置
├── devcontainer.json          # DevContainer 配置
├── init/                      # PostgreSQL 初始化脚本
└── scripts/
    ├── build-and-deploy.sh    # 完整构建部署脚本
    └── quick-deploy.sh        # 快速本地部署脚本
```

## 🚀 部署方式

### 方式一：在 DevContainer 中直接开发（推荐日常开发）

直接在 DevContainer 终端运行：

```bash
# 开发模式（热重载）
./mvnw quarkus:dev

# 访问地址
# API:        http://localhost:8080
# OpenAPI UI: http://localhost:8080/q/swagger-ui
# Health:     http://localhost:8080/q/health
```

### 方式二：Docker 容器化部署测试

使用构建脚本打包应用到 Docker 容器：

```bash
# JVM 模式构建和部署
cd .devcontainer
./scripts/build-and-deploy.sh jvm

# Native 模式构建和部署（需要更多时间和资源）
./scripts/build-and-deploy.sh native

# 使用 --no-cache 强制重新构建
./scripts/build-and-deploy.sh jvm --no-cache
```

**访问地址：**
- API: http://localhost:8081
- OpenAPI UI: http://localhost:8081/q/swagger-ui
- Health: http://localhost:8081/q/health
- Metrics: http://localhost:8081/q/metrics

### 方式三：使用 Docker Compose Profile

```bash
cd .devcontainer

# 启动部署模式（打包好的应用）
docker-compose --profile deploy up -d quarkus-app

# 启动开发模式（热重载）
docker-compose --profile dev up -d quarkus-dev

# 停止服务
docker-compose --profile deploy stop quarkus-app
docker-compose --profile dev stop quarkus-dev
```

### 方式四：快速本地部署（不使用 Docker）

```bash
cd .devcontainer/scripts
./quick-deploy.sh
```

## 🔧 端口规划

| 端口 | 服务 | 说明 |
|------|------|------|
| 8080 | Quarkus Dev | 本地开发模式 |
| 8081 | quarkus-app | Docker 部署测试 |
| 8082 | quarkus-dev | Docker 开发模式 |
| 5005 | Debug | 远程调试端口 |
| 5432 | PostgreSQL | 数据库 |
| 6379 | Redis | 缓存 |
| 3000 | Grafana | 监控仪表板 |
| 9090 | Prometheus | 指标收集 |

## 📦 构建类型说明

### JVM 模式（推荐）
- 构建快速（约 1-2 分钟）
- 启动时间较长（约 2-5 秒）
- 内存占用较高
- 适合开发和测试环境

### Native 模式
- 构建缓慢（约 3-10 分钟）
- 启动时间极快（约 10-50 毫秒）
- 内存占用极低
- 适合生产环境

## 🛠️ 常用命令

```bash
# 查看应用日志
docker-compose logs -f quarkus-app

# 进入容器
docker exec -it $(docker-compose ps -q quarkus-app) sh

# 重启应用
docker-compose restart quarkus-app

# 完全停止并清理
docker-compose --profile deploy down

# 只构建镜像不启动
docker-compose build quarkus-app

# 查看健康状态
curl http://localhost:8081/q/health
```

## ⚙️ 环境变量配置

可在 `docker-compose.yml` 中修改以下环境变量：

```yaml
environment:
  # 数据库
  QUARKUS_DATASOURCE_JDBC_URL: jdbc:postgresql://postgresql:5432/admin
  QUARKUS_DATASOURCE_USERNAME: admin
  QUARKUS_DATASOURCE_PASSWORD: password
  
  # Redis
  QUARKUS_REDIS_HOSTS: redis://redis:6379
  
  # 日志
  QUARKUS_LOG_LEVEL: INFO
  
  # JVM 调优
  JAVA_OPTS: "-Xms256m -Xmx512m -XX:+UseG1GC"
```

## 🔍 调试

### 远程调试（quarkus-dev 模式）

1. 启动 quarkus-dev 服务：
   ```bash
   docker-compose --profile dev up -d quarkus-dev
   ```

2. 在 IDE 中创建远程调试配置：
   - Host: localhost
   - Port: 5005

### 查看指标

访问 Prometheus metrics：
```bash
curl http://localhost:8081/q/metrics
```

在 Grafana 中可视化（http://localhost:3000，密码：password）。

## 📝 注意事项

1. **首次构建**：首次 Docker 构建会下载所有 Maven 依赖，时间较长
2. **Maven 缓存**：`maven-repo` volume 会持久化依赖，加速后续构建
3. **端口冲突**：确保 8081、8082 端口未被占用
4. **健康检查**：`quarkus-app` 配置了健康检查，启动后会自动探测
