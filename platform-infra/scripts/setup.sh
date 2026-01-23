#!/usr/bin/env bash
set -euo pipefail

# Minimal bootstrap helper for this homelab CI/CD platform.
# Idempotent and non-interactive (requires sudo NOPASSWD or root).

LB_IP="${LB_IP:-192.168.10.100}"
HOSTS_DOMAINS=(argocd.local nexus.local gitea.local tekton.local kuboard.local)

ensure_hosts_mapping() {
  local begin='# cicd-platform (managed) BEGIN'
  local end='# cicd-platform (managed) END'
  local line="$LB_IP ${HOSTS_DOMAINS[*]}"

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

case "${1:-}" in
  hosts)
    ensure_hosts_mapping
    getent hosts "${HOSTS_DOMAINS[@]}" || true
    ;;
  *)
    echo "Usage: $0 hosts" >&2
    exit 2
    ;;
esac
