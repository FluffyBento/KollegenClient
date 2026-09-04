Deploy package for Kollegen Client backend

Contents (server/deploy):
- kollegen.service           — systemd unit to run the Node backend
- Caddyfile                 — example Caddy config for kollegen.me reverse-proxy
- deploy.sh                 — helper script to install/update the service on an Ubuntu/Debian host
- README_DEPLOY.md          — this file

Quick notes
- The script assumes an installation path of /opt/kollegenclient and a data directory of /var/lib/kollegenclient/data. Adjust as needed.
- The systemd unit runs the backend as user "kollegen" by default. Create that user on the server and ensure it has access to /opt/kollegenclient and the data dir.
- The Caddyfile example sets up HTTPS for kollegen.me using automatic TLS (Let's Encrypt). Adjust the site and email as appropriate.

Security
- Do not commit real secrets (server API tokens) into the repo. Use environment variables or an admin-only UI on the server to provision tokens.

Usage (on the server)
1. Upload the repository or the server/ folder to the server (e.g. via git clone, scp or rsync).
2. Become root or use sudo for the next steps.
3. Create the install directories and a service user:
   sudo useradd --system --create-home --home-dir /var/lib/kollegenclient kollegen
   sudo mkdir -p /opt/kollegenclient
   sudo mkdir -p /var/lib/kollegenclient/data
   sudo chown -R kollegen:kollegen /opt/kollegenclient /var/lib/kollegenclient
4. Place the repo under /opt/kollegenclient (or adjust the service file's WorkingDirectory/ExecStart).
5. Copy the systemd unit and Caddyfile into place and enable the service (if you want the script to do this, run deploy.sh):
   sudo cp server/deploy/kollegen.service /etc/systemd/system/kollegen.service
   sudo systemctl daemon-reload
   sudo systemctl enable --now kollegen.service
6. Configure Caddy (example): copy server/deploy/Caddyfile to /etc/caddy/Caddyfile and reload Caddy.

If you prefer the helper script to perform these steps, run as root:
  sudo bash server/deploy/deploy.sh

Notes about tokens and publishing
- To allow clients to publish profiles to the server, first call POST /auth on the server with a valid Discord OAuth token from a user account. The server returns a token that the client may use to POST /profile.
- See server/index.js API comment block for details about endpoints.
