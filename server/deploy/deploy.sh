#!/usr/bin/env bash
set -euo pipefail

# Simple deploy helper for Kollegen Client backend (Ubuntu/Debian focused).
# Run on the target server as root (or via sudo).

DEST_DIR="/opt/kollegenclient"
DATA_DIR="/var/lib/kollegenclient/data"
SERVICE_NAME="kollegen"
SERVICE_FILE="/etc/systemd/system/${SERVICE_NAME}.service"
CADDYFILE_DEST="/etc/caddy/Caddyfile"

if [[ $EUID -ne 0 ]]; then
  echo "This script must be run as root. Use sudo." >&2
  exit 2
fi

echo "Creating install directories..."
mkdir -p "$DEST_DIR"
mkdir -p "$DATA_DIR"

# Create a system user if missing
if ! id -u kollegen >/dev/null 2>&1; then
  echo "Creating system user 'kollegen'..."
  useradd --system --create-home --home-dir /var/lib/kollegenclient kollegen || true
fi

echo "Setting ownership to kollegen:kollegen"
chown -R kollegen:kollegen "$DEST_DIR" "$DATA_DIR"
chmod 750 "$DEST_DIR" || true

# Copy files from current working tree into the destination
echo "Copying files to $DEST_DIR (preserving existing files)..."
rsync -a --delete --exclude='.git' ./ "$DEST_DIR/"
chown -R kollegen:kollegen "$DEST_DIR"

# Install Node.js if not present (Ubuntu/Debian). This step is best-effort.
if ! command -v node >/dev/null 2>&1; then
  echo "Node.js not found. Installing Node.js (Debian/Ubuntu)..."
  apt-get update
  apt-get install -y curl ca-certificates
  # Using NodeSource LTS installer
  curl -fsSL https://deb.nodesource.com/setup_lts.x | bash -
  apt-get install -y nodejs
fi

# Place systemd unit
if [[ -f "$DEST_DIR/server/deploy/kollegen.service" ]]; then
  echo "Installing systemd unit to $SERVICE_FILE"
  cp "$DEST_DIR/server/deploy/kollegen.service" "$SERVICE_FILE"
  systemctl daemon-reload
  systemctl enable --now "$SERVICE_NAME" || true
  systemctl restart "$SERVICE_NAME" || true
else
  echo "Warning: Service file not found in $DEST_DIR/server/deploy/kollegen.service" >&2
fi

# Optional: install Caddy config
if [[ -f "$DEST_DIR/server/deploy/Caddyfile" ]]; then
  echo "Installing Caddyfile to $CADDYFILE_DEST"
  cp "$DEST_DIR/server/deploy/Caddyfile" "$CADDYFILE_DEST"
  if systemctl is-enabled caddy >/dev/null 2>&1; then
    systemctl reload caddy || systemctl restart caddy || true
  fi
fi

echo "Deploy complete. Service: $SERVICE_NAME. Data dir: $DATA_DIR"

echo "Next steps:"
echo " - Ensure /opt/kollegenclient contains your built assets and server files."
echo " - If you use a firewall, allow PORT 8080 only from localhost and serve via Caddy."
echo " - Create API tokens via /auth when authenticating from a client (Discord OAuth required)."
