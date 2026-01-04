#!/bin/bash
# =============================================================================
# Quarkus 快速部署脚本（不使用 Docker 构建，直接部署本地 JAR）
# 适用于开发阶段的快速迭代测试
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

echo "🚀 Quick Deploy - 快速部署模式"
echo "📁 项目目录: $PROJECT_ROOT"

cd "$PROJECT_ROOT"

# 构建项目
echo "🔨 正在构建项目..."
./mvnw package -DskipTests -q

# 检查 JAR 是否存在
JAR_FILE="$PROJECT_ROOT/target/quarkus-app/quarkus-run.jar"
if [[ ! -f "$JAR_FILE" ]]; then
    echo "❌ 找不到 JAR 文件: $JAR_FILE"
    exit 1
fi

# 启动应用
echo "🚀 启动应用..."
echo "📊 访问地址:"
echo "   - API:        http://localhost:8080"
echo "   - OpenAPI UI: http://localhost:8080/q/swagger-ui"
echo "   - Health:     http://localhost:8080/q/health"
echo ""

cd "$PROJECT_ROOT"
java \
    -Dquarkus.http.host=0.0.0.0 \
    -Djava.util.logging.manager=org.jboss.logmanager.LogManager \
    -jar target/quarkus-app/quarkus-run.jar
