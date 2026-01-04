#!/bin/bash
# =============================================================================
# Quarkus 应用构建和部署脚本
# 使用方法:
#   ./build-and-deploy.sh [jvm|native] [--no-cache]
# =============================================================================

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 默认参数
BUILD_TYPE=${1:-jvm}
NO_CACHE=""

if [[ "$2" == "--no-cache" ]] || [[ "$1" == "--no-cache" ]]; then
    NO_CACHE="--no-cache"
fi

# 切换到项目根目录
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo -e "${BLUE}╔════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║         Quarkus 应用构建和部署工具                         ║${NC}"
echo -e "${BLUE}╚════════════════════════════════════════════════════════════╝${NC}"

echo -e "\n${YELLOW}📦 构建模式: ${BUILD_TYPE}${NC}"
echo -e "${YELLOW}📁 项目目录: ${PROJECT_ROOT}${NC}\n"

# 验证构建类型
if [[ "$BUILD_TYPE" != "jvm" && "$BUILD_TYPE" != "native" ]]; then
    echo -e "${RED}❌ 无效的构建类型: $BUILD_TYPE${NC}"
    echo "使用方法: ./build-and-deploy.sh [jvm|native]"
    exit 1
fi

cd "$PROJECT_ROOT"

# Step 1: 本地构建（可选，加速 Docker 构建）
echo -e "${GREEN}🔨 Step 1: 本地 Maven 构建...${NC}"
if [[ "$BUILD_TYPE" == "native" ]]; then
    echo -e "${YELLOW}⚠️  Native 构建将在 Docker 中进行...${NC}"
else
    ./mvnw package -DskipTests -q || {
        echo -e "${RED}❌ Maven 构建失败${NC}"
        exit 1
    }
fi

echo -e "${GREEN}✅ 本地构建完成${NC}\n"

# Step 2: Docker 镜像构建
echo -e "${GREEN}🐳 Step 2: 构建 Docker 镜像...${NC}"

if [[ "$BUILD_TYPE" == "jvm" ]]; then
    docker build \
        -f .devcontainer/Dockerfile.quarkus \
        --target jvm-runner \
        --build-arg BUILD_TYPE=jvm \
        -t admin-quarkus:latest \
        -t admin-quarkus:jvm \
        $NO_CACHE \
        . || {
            echo -e "${RED}❌ Docker 构建失败${NC}"
            exit 1
        }
else
    docker build \
        -f .devcontainer/Dockerfile.quarkus \
        --target native-runner \
        --build-arg BUILD_TYPE=native \
        -t admin-quarkus:latest \
        -t admin-quarkus:native \
        $NO_CACHE \
        . || {
            echo -e "${RED}❌ Docker 构建失败${NC}"
            exit 1
        }
fi

echo -e "${GREEN}✅ Docker 镜像构建完成${NC}\n"

# Step 3: 启动服务
echo -e "${GREEN}🚀 Step 3: 启动 Quarkus 服务...${NC}"

cd .devcontainer
docker-compose up -d quarkus-app

echo -e "\n${GREEN}═════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}✅ 部署完成！${NC}"
echo -e "${GREEN}═════════════════════════════════════════════════════════════${NC}"
echo -e ""
echo -e "📊 服务状态:"
docker-compose ps quarkus-app
echo -e ""
echo -e "🔗 访问地址:"
echo -e "   - API:        ${BLUE}http://localhost:8081${NC}"
echo -e "   - OpenAPI UI: ${BLUE}http://localhost:8081/q/swagger-ui${NC}"
echo -e "   - Health:     ${BLUE}http://localhost:8081/q/health${NC}"
echo -e "   - Metrics:    ${BLUE}http://localhost:8081/q/metrics${NC}"
echo -e ""
echo -e "📝 查看日志: ${YELLOW}docker-compose logs -f quarkus-app${NC}"
echo -e "🛑 停止服务: ${YELLOW}docker-compose stop quarkus-app${NC}"
