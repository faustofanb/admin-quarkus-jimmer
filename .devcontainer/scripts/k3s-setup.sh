#!/bin/bash
# K3s Setup Script for DevContainer
# 初始化 K3s 集群并配置 GHCR 访问

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
K3S_CONFIG_DIR="${SCRIPT_DIR}/../k3s"

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

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

# 等待 K3s 就绪
wait_for_k3s() {
    log_info "Waiting for K3s to be ready..."
    local max_attempts=60
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if kubectl get nodes &>/dev/null; then
            log_success "K3s is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    
    log_error "K3s failed to start after ${max_attempts} attempts"
    return 1
}

# 配置 GHCR 认证
setup_ghcr_auth() {
    log_info "Setting up GHCR authentication..."
    
    if [ -z "$GHCR_USERNAME" ] || [ -z "$GHCR_TOKEN" ]; then
        log_warn "GHCR_USERNAME or GHCR_TOKEN not set"
        log_info "To pull private images, set these environment variables:"
        echo "  export GHCR_USERNAME=your-github-username"
        echo "  export GHCR_TOKEN=your-github-token"
        return 0
    fi
    
    # 创建 registries.yaml
    mkdir -p /etc/rancher/k3s
    cat > /etc/rancher/k3s/registries.yaml <<EOF
mirrors:
  "ghcr.io":
    endpoint:
      - "https://ghcr.io"

configs:
  "ghcr.io":
    auth:
      username: "${GHCR_USERNAME}"
      password: "${GHCR_TOKEN}"
EOF
    
    log_success "GHCR authentication configured"
}

# 配置 kubectl
setup_kubectl() {
    log_info "Setting up kubectl..."
    
    # 等待 K3s 通过共享卷生成 kubeconfig
    # 注意：docker-compose 将卷挂载到了 /root/.kube，普通用户需 sudo 访问
    local kube_config_src="/root/.kube/kubeconfig.yaml"
    local kube_config_dest="$HOME/.kube/config"
    local max_attempts=60
    local attempt=0
    
    log_info "Waiting for kubeconfig at ${kube_config_src}..."
    
    # 使用 sudo 检查文件是否存在
    while ! sudo test -f "$kube_config_src" && [ $attempt -lt $max_attempts ]; do
        attempt=$((attempt + 1))
        echo -n "."
        sleep 2
    done
    echo ""
    
    if sudo test -f "$kube_config_src"; then
        mkdir -p "$HOME/.kube"
        
        # 使用 sudo 复制，然后修改所有者为当前用户
        sudo cp "$kube_config_src" "$kube_config_dest"
        sudo chown $(id -u):$(id -g) "$kube_config_dest"
        chmod 600 "$kube_config_dest"
        
        # 替换 localhost 为 k3s 服务名
        if command -v sed &>/dev/null; then
            sed -i 's/127.0.0.1/k3s/g' "$kube_config_dest" 2>/dev/null || true
            sed -i 's/localhost/k3s/g' "$kube_config_dest" 2>/dev/null || true
        fi
        
        # 设置环境变量以覆盖 docker-compose 中错误的默认值
        export KUBECONFIG="$kube_config_dest"
        if ! grep -q "export KUBECONFIG=$kube_config_dest" ~/.zshrc; then
             echo "export KUBECONFIG=$kube_config_dest" >> ~/.zshrc
        fi
        
        log_success "kubectl configured at ${kube_config_dest}"
    else
        log_error "kubeconfig not found at ${kube_config_src}."
        log_info "Check if K3s is running: docker-compose --profile k8s ps"
        return 1
    fi
}

# 安装常用工具
install_tools() {
    log_info "Installing K8s tools..."
    
    # 检查是否已安装
    if command -v kubectl &>/dev/null; then
        log_info "kubectl already installed: $(kubectl version --client --short 2>/dev/null || kubectl version --client)"
    fi
    
    # 安装 kustomize (如果需要)
    if ! command -v kustomize &>/dev/null; then
        log_info "Installing kustomize..."
        curl -s "https://raw.githubusercontent.com/kubernetes-sigs/kustomize/master/hack/install_kustomize.sh" | bash
        mv kustomize /usr/local/bin/
    fi
    
    # 安装 k9s (可选)
    if ! command -v k9s &>/dev/null; then
        log_info "Installing k9s..."
        curl -sS https://webinstall.dev/k9s | bash 2>/dev/null || log_warn "k9s installation skipped"
    fi
    
    log_success "Tools installation complete"
}

# 显示集群信息
show_cluster_info() {
    log_info "Cluster Information:"
    echo ""
    kubectl cluster-info
    echo ""
    kubectl get nodes -o wide
    echo ""
    log_success "K3s setup complete!"
    echo ""
    echo "Next steps:"
    echo "  1. Deploy the application: ./deploy-to-k3s.sh"
    echo "  2. Access Rancher: https://localhost:8443"
    echo "  3. Use k9s for cluster management: k9s"
}

# 主函数
main() {
    log_info "Starting K3s setup..."
    
    setup_ghcr_auth
    setup_kubectl
    wait_for_k3s
    install_tools
    show_cluster_info
}

main "$@"
