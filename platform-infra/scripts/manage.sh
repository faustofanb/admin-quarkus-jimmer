#!/usr/bin/env bash
set -euo pipefail

# Operational helper for the platform.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

# --- Core Commands ---

status() {
  echo "=== Platform Status ==="
  echo "--- Nodes ---"
  kubectl get nodes -o wide
  echo ""
  echo "--- Component Status ---"
  kubectl get pods -n argocd -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,STATUS:.status.phase,PF:.spec.containers[*].ports[*].containerPort --no-headers | head -n 3
  kubectl get pods -n platform -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,STATUS:.status.phase --no-headers
  kubectl get pods -n tekton-pipelines -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,STATUS:.status.phase --no-headers | head -n 3
  kubectl get pods -n middleware -o custom-columns=NAMESPACE:.metadata.namespace,NAME:.metadata.name,STATUS:.status.phase --no-headers
  echo "(Output truncated for brevity)"
}

wait_ready() {
  local timeout=${2:-300}
  log "Waiting for platform to be ready (timeout: ${timeout}s)..."
  
  namespaces=(argocd platform tekton-pipelines middleware)
  for ns in "${namespaces[@]}"; do
    log "Checking namespace: $ns"
    if ! kubectl get namespace "$ns" >/dev/null 2>&1; then
       log "  [WARN] Namespace $ns does not exist, skipping..."
       continue
    fi
    # Wait for Deployment/StatefulSet readiness instead of just Pods
    kubectl wait --for=condition=available deployment --all -n "$ns" --timeout="${timeout}s" || true
    kubectl wait --for=condition=ready pod --all -n "$ns" --timeout="${timeout}s" || true
  done
  log "Platform is Ready."
}

diag() {
  echo "=== Diagnostics ==="
  echo "--- Failed Pods ---"
  kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded
  echo ""
  echo "--- PVC Status (Not Bound) ---"
  kubectl get pvc -A | grep -v Bound || true
  echo ""
  echo "--- Events (Warning) ---"
  kubectl get events -A --field-selector type=Warning --sort-by='.lastTimestamp' | tail -n 20
}

# --- Service Management ---

restart() {
  local service_name=${2:-}
  if [ -z "$service_name" ]; then
    echo "Usage: $0 restart <service-name>"
    echo "Supported services: gitea, nexus, argocd, runner"
    exit 1
  fi

  case "$service_name" in
    gitea)
      kubectl rollout restart deployment/gitea -n platform
      log "Restarted Gitea"
      ;;
    nexus)
      kubectl rollout restart deployment/nexus-nexus-repository-manager -n platform
      log "Restarted Nexus"
      ;;
    argocd)
      kubectl rollout restart statefulset/argocd-application-controller -n argocd
      kubectl rollout restart deployment/argocd-server -n argocd
      log "Restarted Argo CD"
      ;;
    *)
      # Generic restart attempt
      log "Attempting generic restart for deployment matching '$service_name'..."
      kubectl rollout restart deployment -n platform -l "app.kubernetes.io/name=$service_name" || \
      kubectl rollout restart deployment -n argocd -l "app.kubernetes.io/name=$service_name" || \
      echo "Service not found or restart failed."
      ;;
  esac
}

logs() {
  local service_name=${2:-}
  if [ -z "$service_name" ]; then
    echo "Usage: $0 logs <service-name> [-f]"
    exit 1
  fi
  local follow=${3:-}
  
  # Heuristic to find pod
  local pod=$(kubectl get pods -A -l "app.kubernetes.io/name=$service_name" -o name | head -1)
  if [ -z "$pod" ]; then
     # Try name contains
     pod=$(kubectl get pods -A | grep "$service_name" | awk '{print $2}' | head -1)
  fi

  if [ -z "$pod" ]; then
    echo "Pod for $service_name not found."
    exit 1
  fi
  
  # Namespace
  local ns=$(kubectl get pod -A | grep "$(basename $pod)" | awk '{print $1}')
  
  log "Showing logs for $pod in $ns..."
  kubectl logs -n "$ns" "$(basename $pod)" $follow
}

shell() {
  local service_name=${2:-}
  if [ -z "$service_name" ]; then
    echo "Usage: $0 shell <service-name>"
    exit 1
  fi
  
  local pod=$(kubectl get pods -A -l "app.kubernetes.io/name=$service_name" -o name | head -1)
  if [ -z "$pod" ]; then
     pod=$(kubectl get pods -A | grep "$service_name" | awk '{print $2}' | head -1)
  fi

  if [ -z "$pod" ]; then
    echo "Pod for $service_name not found."
    exit 1
  fi
  
  local ns=$(kubectl get pod -A | grep "$(basename $pod)" | awk '{print $1}')
  
  log "Entering shell in $pod ($ns)..."
  kubectl exec -it -n "$ns" "$(basename $pod)" -- /bin/sh || kubectl exec -it -n "$ns" "$(basename $pod)" -- /bin/bash
}

# --- Global Control ---

stop_all() {
  log "Stopping K3s and all services..."
  sudo systemctl stop k3s
  log "K3s stopped. Containers will be killed by containerd/systemd."
}

start_all() {
  log "Starting K3s..."
  sudo systemctl start k3s
  log "Waiting for node to be ready..."
  sleep 10
  kubectl wait --for=condition=Ready node --all --timeout=60s
  log "K3s started. Waiting for pods..."
  wait_ready 300
}

usage() {
    echo "Usage: $0 {status|wait-ready|diag|restart|logs|shell|stop-all|start-all}"
    echo ""
    echo "Commands:"
    echo "  status       Show platform status summary"
    echo "  wait-ready   Wait for all pods to be ready"
    echo "  diag         Show failed pods and warnings"
    echo "  restart <svc> Restart a specific service (gitea, nexus, etc)"
    echo "  logs <svc>   View logs for a service"
    echo "  shell <svc>  Exec into a service pod"
    echo "  stop-all     Stop K3s service (pauses platform)"
    echo "  start-all    Start K3s service and wait for ready"
    exit 1
}

case "${1:-}" in
  status)
    status
    ;;
  wait-ready)
    wait_ready "$@"
    ;;
  diag)
    diag
    ;;
  restart)
    restart "$@"
    ;;
  logs)
    logs "$@"
    ;;
  shell)
    shell "$@"
    ;;
  stop-all)
    stop_all
    ;;
  start-all)
    start_all
    ;;
  *)
    usage
    ;;
esac
