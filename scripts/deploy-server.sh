#!/usr/bin/env bash

set -euo pipefail

HOST="${1:?Usage: deploy-server.sh <host> [user]}"
USER="${2:-root}"
REMOTE_DIR="/opt/kollegen-backend"
KEY="$(dirname "$0")/../Kollegenserver"

if [ ! -f "$KEY" ]; then
  KEY="$(dirname "$0")/../Kollegenserver"
fi

SCP_OPTS=(-i "$KEY" -o StrictHostKeyChecking=no -o UserKnownHostsFile=/dev/null)
SSH_OPTS=("${SCP_OPTS[@]}")

echo "==> Kopiere server/ nach ${USER}@${HOST}:${REMOTE_DIR} (ohne data/)"
ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "mkdir -p ${REMOTE_DIR}/data"
ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "cp ${REMOTE_DIR}/data/store.json ${REMOTE_DIR}/data/store.json.bak-deploy 2>/dev/null || true"
tar -czf - --exclude=data -C "$(dirname "$0")/../server" . | ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "tar -xzf - -C ${REMOTE_DIR}/"

echo "==> Installiere systemd-Unit (benötigt root)"
scp "${SSH_OPTS[@]}" "$(dirname "$0")/../server/kollegen-backend.service" "${USER}@${HOST}:/tmp/kollegen-backend.service"
ssh "${SSH_OPTS[@]}" "${USER}@${HOST}" "
  set -e
  cp /tmp/kollegen-backend.service /etc/systemd/system/kollegen-backend.service
  sed -i 's/%i/${USER}/' /etc/systemd/system/kollegen-backend.service
  systemctl daemon-reload
  systemctl enable --now kollegen-backend
  systemctl restart kollegen-backend
  sleep 2
  curl -fsS http://127.0.0.1:8080/health || echo 'WARN: /health nicht erreichbar'
"

echo "==> Fertig. Backend läuft auf ${HOST}:8080"
echo "    Launcher-Einstellung KOLLEGEN_PRESENCE_BACKEND=basis-url des Servers"
