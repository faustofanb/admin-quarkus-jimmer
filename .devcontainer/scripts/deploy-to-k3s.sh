#!/bin/bash
# Deploy to K3s Script
# 将应用部署到本地 K3s 集群

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="${SCRIPT_DIR}/../.."
K8S_DIR="${PROJECT_ROOT}/k8s"

# 默认值
ENVIRONMENT="${1:-dev}"
IMAGE_TAG="${2:-latest}"
NAMESPACE="admin-system"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

log_warn() {
    echo -e "${YELLOW}[WARN]${NC} $1"
}

log_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 显示使用说明
usage() {
    echo "Usage: $0 [ENVIRONMENT] [IMAGE_TAG]"
    echo ""
    echo "Arguments:"
    echo "  ENVIRONMENT   dev or prod (default: dev)"
    echo "  IMAGE_TAG     Docker image tag (default: latest)"
    echo ""
    echo "Examples:"
    echo "  $0              # Deploy dev with latest tag"
    echo "  $0 dev sha-abc  # Deploy dev with specific tag"
    echo "  $0 prod v1.0.0  # Deploy prod with version tag"
    echo ""
    echo "Environment variables:"
    echo "  GHCR_USERNAME   GitHub username for GHCR"
    echo "  GHCR_TOKEN      GitHub PAT for GHCR"
}

# 检查前置条件
check_prerequisites() {
    log_info "Checking prerequisites..."
    
    # 检查 kubectl
    if ! command -v kubectl &>/dev/null; then
        log_error "kubectl not found. Run k3s-setup.sh first."
        exit 1
    fi
    
    # 检查集群连接
    if ! kubectl cluster-info &>/dev/null; then
        log_error "Cannot connect to Kubernetes cluster"
        log_info "Make sure K3s is running and kubectl is configured"
        exit 1
    fi
    
    # 检查 kustomize
    if ! command -v kustomize &>/dev/null; then
        log_warn "kustomize not found, using kubectl kustomize"
        KUSTOMIZE_CMD="kubectl kustomize"
    else
        KUSTOMIZE_CMD="kustomize build"
    fi
    
    log_success "Prerequisites check passed"
}

# 创建 GHCR Pull Secret
create_pull_secret() {
    log_info "Creating GHCR pull secret..."
    
    if [ -z "$GHCR_USERNAME" ] || [ -z "$GHCR_TOKEN" ]; then
        log_warn "GHCR credentials not set, skipping pull secret creation"
        log_info "For private images, set GHCR_USERNAME and GHCR_TOKEN"
        return 0
    fi
    
    # 确保 namespace 存在
    kubectl create namespace "$NAMESPACE" --dry-run=client -o yaml | kubectl apply -f -
    
    # 创建或更新 pull secret
    kubectl create secret docker-registry ghcr-pull-secret \
        --namespace="$NAMESPACE" \
        --docker-server=ghcr.io \
        --docker-username="$GHCR_USERNAME" \
        --docker-password="$GHCR_TOKEN" \
        --dry-run=client -o yaml | kubectl apply -f -
    
    log_success "GHCR pull secret created/updated"
}

# 部署应用
deploy_app() {
    log_info "Deploying application (env: $ENVIRONMENT, tag: $IMAGE_TAG)..."
    
    local overlay_dir="${K8S_DIR}/overlays/${ENVIRONMENT}"
    
    if [ ! -d "$overlay_dir" ]; then
        log_error "Overlay directory not found: $overlay_dir"
        exit 1
    fi
    
    # 更新镜像标签
    cd "$overlay_dir"
    
    # 使用 kustomize 设置镜像
    if command -v kustomize &>/dev/null; then
        kustomize edit set image "admin-quarkus=ghcr.io/faustofan/admin-quarkus-jimmer:${IMAGE_TAG}"
    fi
    
    # 应用配置
    log_info "Applying Kubernetes manifests..."
    $KUSTOMIZE_CMD "$overlay_dir" | kubectl apply -f -
    
    cd - >/dev/null
    
    log_success "Manifests applied"
}

# 等待部署完成
wait_for_rollout() {
    log_info "Waiting for deployment rollout..."
    
    # 等待 PostgreSQL
    log_info "Waiting for PostgreSQL..."
    kubectl rollout status statefulset/postgresql -n "$NAMESPACE" --timeout=300s || {
        log_warn "PostgreSQL rollout timeout, checking status..."
        kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=postgresql
    }
    
    # 等待 Redis
    log_info "Waiting for Redis..."
    kubectl rollout status deployment/redis -n "$NAMESPACE" --timeout=120s || {
        log_warn "Redis rollout timeout, checking status..."
        kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=redis
    }
    
    # 等待应用
    log_info "Waiting for Admin App..."
    kubectl rollout status deployment/admin-app -n "$NAMESPACE" --timeout=300s || {
        log_warn "Admin App rollout timeout, checking status..."
        kubectl get pods -n "$NAMESPACE" -l app.kubernetes.io/name=admin-app
    }
    
    log_success "Rollout complete"
}

# 显示部署状态
show_status() {
    log_info "Deployment Status:"
    echo ""
    
    echo "=== Pods ==="
    kubectl get pods -n "$NAMESPACE" -o wide
    echo ""
    
    echo "=== Services ==="
    kubectl get svc -n "$NAMESPACE"
    echo ""
    
    echo "=== Ingress ==="
    kubectl get ingress -n "$NAMESPACE"
    echo ""
    
    # 获取访问地址
    local nodeport=$(kubectl get svc admin-app-nodeport -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}' 2>/dev/null || echo "30080")
    
    log_success "Deployment complete!"
    echo ""
    echo "Access the application:"
    echo "  - NodePort: http://localhost:${nodeport}"
    echo "  - Ingress:  http://admin.localhost (需要配置 /etc/hosts)"
    echo "  - API Docs: http://localhost:${nodeport}/q/swagger-ui"
    echo "  - Health:   http://localhost:${nodeport}/q/health"
    echo ""
    echo "Useful commands:"
    echo "  kubectl logs -f -n $NAMESPACE -l app.kubernetes.io/name=admin-app"
    echo "  kubectl exec -it -n $NAMESPACE deploy/admin-app -- sh"
    echo "  k9s -n $NAMESPACE"
}

# 主函数
main() {
    case "$1" in
        -h|--help)
            usage
            exit 0
            ;;
    esac
    
    log_info "Starting deployment to K3s..."
    echo "  Environment: $ENVIRONMENT"
    echo "  Image Tag:   $IMAGE_TAG"
    echo ""
    
    check_prerequisites
    create_pull_secret
    deploy_app
    wait_for_rollout
    show_status
}

main "$@"
