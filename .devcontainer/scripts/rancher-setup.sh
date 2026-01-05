#!/bin/bash
# Rancher Setup Script
# 初始化 Rancher 并配置与 K3s 集群的连接

set -e

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

# 等待 Rancher 就绪
wait_for_rancher() {
    log_info "Waiting for Rancher to be ready..."
    local max_attempts=120  # Rancher 启动较慢
    local attempt=0
    
    while [ $attempt -lt $max_attempts ]; do
        if curl -sk https://localhost:8443/ping 2>/dev/null | grep -q "pong"; then
            log_success "Rancher is ready!"
            return 0
        fi
        attempt=$((attempt + 1))
        echo -n "."
        sleep 3
    done
    
    log_error "Rancher failed to start after ${max_attempts} attempts"
    return 1
}

# 获取 Rancher Bootstrap 密码
get_bootstrap_password() {
    log_info "Getting Rancher bootstrap password..."
    
    # 从 Docker 容器获取初始密码
    local password=""
    
    # 方法1: 从容器日志获取
    password=$(docker logs rancher 2>&1 | grep "Bootstrap Password:" | tail -1 | awk '{print $NF}' || true)
    
    # 方法2: 从容器内部获取
    if [ -z "$password" ]; then
        password=$(docker exec rancher cat /var/lib/rancher/bootstrap-password 2>/dev/null || true)
    fi
    
    if [ -n "$password" ]; then
        log_success "Bootstrap password found"
        echo ""
        echo "========================================"
        echo "  Rancher Bootstrap Password: $password"
        echo "========================================"
        echo ""
    else
        log_warn "Could not retrieve bootstrap password automatically"
        echo "Run the following command to get it:"
        echo "  docker logs rancher 2>&1 | grep 'Bootstrap Password:'"
    fi
}

# 显示 Rancher 信息
show_rancher_info() {
    log_info "Rancher Information:"
    echo ""
    echo "  URL:      https://localhost:8443"
    echo "  Username: admin"
    echo ""
    echo "First login steps:"
    echo "  1. Open https://localhost:8443 in your browser"
    echo "  2. Accept the self-signed certificate warning"
    echo "  3. Use the bootstrap password above"
    echo "  4. Set your new admin password"
    echo "  5. Configure the Rancher Server URL (use https://rancher:443 for internal access)"
    echo ""
    echo "Import the local K3s cluster:"
    echo "  1. Go to Cluster Management"
    echo "  2. Click 'Import Existing'"
    echo "  3. Choose 'Generic' and follow the instructions"
    echo ""
}

# 安装 Rancher CLI (可选)
install_rancher_cli() {
    if command -v rancher &>/dev/null; then
        log_info "Rancher CLI already installed"
        return 0
    fi
    
    log_info "Installing Rancher CLI..."
    
    local os=$(uname -s | tr '[:upper:]' '[:lower:]')
    local arch=$(uname -m)
    
    case $arch in
        x86_64) arch="amd64" ;;
        aarch64|arm64) arch="arm64" ;;
    esac
    
    local version="v2.8.0"
    local url="https://github.com/rancher/cli/releases/download/${version}/rancher-${os}-${arch}-${version}.tar.gz"
    
    curl -sL "$url" | tar xz -C /tmp
    mv /tmp/rancher-*/rancher /usr/local/bin/
    rm -rf /tmp/rancher-*
    
    log_success "Rancher CLI installed"
}

# 导入 K3s 集群到 Rancher (自动化，可选)
import_k3s_cluster() {
    log_info "To import K3s cluster to Rancher, follow the UI steps above"
    log_info "Or use the Rancher CLI after setting up authentication"
    
    # 自动导入需要 Rancher API token，首次设置后才能使用
    # rancher login https://localhost:8443 --token <token>
    # rancher cluster import <cluster-name>
}

# 主函数
main() {
    log_info "Starting Rancher setup..."
    
    wait_for_rancher
    get_bootstrap_password
    # install_rancher_cli  # 可选，取消注释启用
    show_rancher_info
    
    log_success "Rancher setup complete!"
}

main "$@"
