#!/usr/bin/env bash
set -euo pipefail

# Minimal bootstrap helper for this homelab CI/CD platform.
# Idempotent and non-interactive (requires sudo NOPASSWD or root).

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"
source "$ROOT_DIR/versions.env"

LB_IP="${LB_IP:-192.168.10.100}"
HOSTS_DOMAINS=(argocd.local nexus.local gitea.local tekton.local kuboard.local api-test.local api-pre.local)

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

ensure_hosts_mapping() {
  local begin='# cicd-platform (managed) BEGIN'
  local end='# cicd-platform (managed) END'
  local line="$LB_IP ${HOSTS_DOMAINS[*]}"

  log "Updating /etc/hosts with $line"
  
  sudo -n true
  sudo cp -a /etc/hosts "/etc/hosts.bak.$(date +%Y%m%d%H%M%S)"

  # Remove previous managed block if present
  if grep -qF "$begin" /etc/hosts; then
    sudo awk -v b="$begin" -v e="$end" 'BEGIN{skip=0} $0==b{skip=1;next} $0==e{skip=0;next} skip==0{print}' /etc/hosts | sudo tee /tmp/hosts.new >/dev/null
  else
    sudo cat /etc/hosts | sudo tee /tmp/hosts.new >/dev/null
  fi

  {
    echo "$begin"
    echo "$line"
    echo "$end"
  } | sudo tee -a /tmp/hosts.new >/dev/null

  sudo mv /tmp/hosts.new /etc/hosts
  sudo chmod 644 /etc/hosts
}

add_helm_repos() {
  log "Adding Helm repositories..."
  helm repo add metallb https://metallb.github.io/metallb
  helm repo add ingress-nginx https://kubernetes.github.io/ingress-nginx
  helm repo add jetstack https://charts.jetstack.io
  helm repo add argo https://argoproj.github.io/argo-helm
  helm repo add sonatype https://sonatype.github.io/helm3-charts/
  helm repo add gitea https://dl.gitea.io/charts/
  helm repo update
}

bootstrap() {
  log "Starting bootstrap..."
  add_helm_repos

  # 1. MetalLB
  log "Deploying MetalLB $METALLB_VERSION..."
  helm upgrade --install metallb metallb/metallb \
    --namespace metallb-system --create-namespace \
    --version "$METALLB_VERSION" \
    -f "$ROOT_DIR/bootstrap/metallb/values.yaml"
  
  # Apply IP Pool (manifest)
  if [ -f "$ROOT_DIR/bootstrap/metallb/config.yaml" ]; then
    kubectl apply -f "$ROOT_DIR/bootstrap/metallb/config.yaml"
  fi

  # 2. Ingress Nginx
  log "Deploying Ingress Nginx $INGRESS_NGINX_CHART_VERSION..."
  helm upgrade --install ingress-nginx ingress-nginx/ingress-nginx \
    --namespace ingress-nginx --create-namespace \
    --version "$INGRESS_NGINX_CHART_VERSION" \
    -f "$ROOT_DIR/bootstrap/ingress-nginx/values.yaml"

  # 3. Cert Manager
  log "Deploying Cert Manager $CERT_MANAGER_VERSION..."
  helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager --create-namespace \
    --version "$CERT_MANAGER_VERSION" \
    --set crds.enabled=true \
    -f "$ROOT_DIR/bootstrap/cert-manager/values.yaml"
  
  # Apply ClusterIssuer
  if [ -f "$ROOT_DIR/bootstrap/cert-manager/cluster-issuer.yaml" ]; then
    log "Waiting for cert-manager webhook..."
    kubectl -n cert-manager wait --for=condition=Available deployment/cert-manager-webhook --timeout=60s || true
    kubectl apply -f "$ROOT_DIR/bootstrap/cert-manager/cluster-issuer.yaml"
  fi

  # 4. Argo CD
  log "Deploying Argo CD $ARGOCD_CHART_VERSION..."
  helm upgrade --install argocd argo/argo-cd \
    --namespace argocd --create-namespace \
    --version "$ARGOCD_CHART_VERSION" \
    -f "$ROOT_DIR/bootstrap/argocd/values.yaml"
  
  log "Bootstrap finished. Argo CD password:"
  kubectl -n argocd get secret argocd-initial-admin-secret -o jsonpath='{.data.password}' | base64 -d && echo
}

install() {
  log "Starting installation (Argo CD Handoff)..."
  
  # Ensure we are bootstrapped first? (Users responsibility or Idempotent checks)
  
  log "Applying Argo CD 'App of Apps'..."
  # 1. Platform Components
  kubectl apply -f "$ROOT_DIR/bootstrap/argo-cd/applications/platform.yaml"
  
  # 2. Business Apps
  kubectl apply -f "$ROOT_DIR/bootstrap/argo-cd/applications/apps.yaml"
  
  log "Waiting for Argo CD to pick up changes..."
  sleep 5
  kubectl get application -n argocd
  
  log "Installation triggers submitted. Check Argo CD UI for sync status."
}

verify() {
  log "Verifying platform status..."
  
  # 1. DNS
  log "Checking DNS resolution..."
  for domain in "${HOSTS_DOMAINS[@]}"; do
    if getent hosts "$domain" | grep -q "$LB_IP"; then
      echo "  [OK] $domain -> $LB_IP"
    else
      echo "  [FAIL] $domain resolution failed"
    fi
  done
  
  # 2. Key Components
  log "Checking key components..."
  local namespaces=(argocd platform ingress-nginx cert-manager metallb-system)
  for ns in "${namespaces[@]}"; do
    local not_ready=$(kubectl get pods -n "$ns" --field-selector=status.phase!=Running,status.phase!=Succeeded --no-headers 2>/dev/null | wc -l)
    if [ "$not_ready" -eq 0 ]; then
      echo "  [OK] Namespace $ns: All pods running/succeeded"
    else
      echo "  [WARN] Namespace $ns: $not_ready pods not ready"
    fi
  done
  
  # 3. Nexus TCP Ports
  log "Checking Nexus TCP ports (5000/5001)..."
  if curl -s -m 2 http://nexus.local:5000/v2/ >/dev/null 2>&1 || [ $? -eq 56 ] || [ $? -eq 7 ]; then
     # Code 56/7 might be connect failure, but let's be strict.
     # Actually we faced 401 Unauthorized which is GOOD (means port is open)
     local code=$(curl -s -o /dev/null -w "%{http_code}" http://nexus.local:5000/v2/ || echo "000")
     if [ "$code" -eq 401 ]; then
        echo "  [OK] Nexus Registry (Port 5000): Reachable (401 Unauthorized)"
     else
        echo "  [FAIL] Nexus Registry (Port 5000): Code $code"
     fi
  else
     echo "  [FAIL] Nexus Registry (Port 5000): Connection failed"
  fi
}

case "${1:-}" in
  hosts)
    ensure_hosts_mapping
    getent hosts "${HOSTS_DOMAINS[@]}" || true
    ;;
  bootstrap)
    ensure_hosts_mapping
    bootstrap
    ;;
  install)
    install
    ;;
  verify)
    verify
    ;;
  all)
    ensure_hosts_mapping
    bootstrap
    install
    verify
    ;;
  *)
    echo "Usage: $0 {hosts|bootstrap|install|verify|all}" >&2
    exit 2
    ;;
esac
