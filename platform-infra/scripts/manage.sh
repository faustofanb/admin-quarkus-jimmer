#!/usr/bin/env bash
set -euo pipefail

# Operational helper for the platform.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(dirname "$SCRIPT_DIR")"

log() {
  echo "[$(date +'%Y-%m-%d %H:%M:%S')] $1"
}

status() {
  echo "=== Platform Status ==="
  echo "--- Nodes ---"
  kubectl get nodes -o wide
  echo ""
  echo "--- Core Namespaces ---"
  kubectl get pods -n argocd -o wide
  kubectl get pods -n platform -o wide
  kubectl get pods -n tekton-pipelines -o wide
  kubectl get pods -n middleware -o wide
}

wait_ready() {
  local timeout=${2:-300}
  log "Waiting for platform to be ready (timeout: ${timeout}s)..."
  
  namespaces=(argocd platform tekton-pipelines middleware)
  for ns in "${namespaces[@]}"; do
    log "Checking namespace: $ns"
    if kubectl get pods -n "$ns" 2>/dev/null | grep -q "No resources found"; then
       log "  [WARN] No resources in $ns, skipping..."
       continue
    fi
    kubectl wait --for=condition=Ready pods --all -n "$ns" --timeout="${timeout}s"
  done
  log "Platform is Ready."
}

diag() {
  echo "=== Diagnostics ==="
  echo "--- Failed Pods ---"
  kubectl get pods -A --field-selector=status.phase!=Running,status.phase!=Succeeded
  echo ""
  echo "--- PVC Status ---"
  kubectl get pvc -A | grep -v Bound || true
  echo ""
  echo "--- Events (Warning) ---"
  kubectl get events -A --field-selector type=Warning --sort-by='.lastTimestamp' | tail -n 20
}

usage() {
    echo "Usage: $0 {status|wait-ready [timeout]|diag}"
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
  *)
    usage
    ;;
esac
