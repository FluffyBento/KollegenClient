'use strict';

/**
 * Kollegen-Client Backend
 * =======================
 * Server für das Freundes-System und die Badges (Icon links neben dem Namen,
 * wie bei Feather/Essential/Tier-Tag). Reines Node.js (keine externen
 * Abhängigkeiten, kein `npm install`) – läuft mit `node index.js`.
 *
 * Erwartet (via Umgebung):
 *   PORT                 (default 8080)
 *   KOLLEGEN_DATA_DIR     Verzeichnis für store.json (default: ./data)
 *   PRESENCE_TTL_MS       Gültigkeit einer Presence in ms (default 90000)
 *   REQUIRE_HTTPS_NOTE    nur ein Hinweis – der Betreiber sollte HTTPS
 *                         (z. B. Caddy/Reverse-Proxy) davor setzen, damit das
 *                         Discord-Token nicht im Klartext übertragen wird.
 *
 * API-Vertrag (vom Launcher/Mod aufgerufen):
 *   POST   /auth              {discord_token, profile?}      -> {token}
 *   GET    /me                (Bearer)                       -> {id,name,uuid,code,accounts}
 *   GET    /friends           (Bearer)                       -> [{id,name,uuid,code,server,online}]
 *   POST   /friends           (Bearer) {code|target_id}      -> {ok} | {error}
 *   DELETE /friends           (Bearer) {target_id}           -> {ok} | {error}
 *   POST   /profile           (Bearer) {uuid,name,accounts?} -> {ok}
 *   PUT    /presence          (Bearer) {server,name,timestamp}
 *   DELETE /presence          (Bearer)
 *   GET    /presence?server=  (öffentlich)                   -> [name, ...]
 *   GET    /health
 */

const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = parseInt(process.env.PORT || '8080', 10);
const DATA_DIR = process.env.KOLLEGEN_DATA_DIR || path.join(__dirname, 'data');
const PRESENCE_TTL_MS = parseInt(process.env.PRESENCE_TTL_MS || '90000', 10);
const STORE_FILE = path.join(DATA_DIR, 'store.json');

// ── Persistenz ────────────────────────────────────────────────────────────
let store = { users: {}, sessions: {}, codes: {}, presence: {}, seq: 1 };
let saveTimer = null;

function loadStore() {
  try {
    if (fs.existsSync(STORE_FILE)) {
      const raw = fs.readFileSync(STORE_FILE, 'utf8');
      const parsed = JSON.parse(raw);
      if (parsed && typeof parsed === 'object') store = Object.assign(store, parsed);
    }
  } catch (e) {
    console.error('Konnte store.json nicht laden:', e.message);
  }
}

function saveStore() {
  if (saveTimer) return;
  saveTimer = setTimeout(() => {
    saveTimer = null;
    try {
      fs.mkdirSync(DATA_DIR, { recursive: true });
      fs.writeFileSync(STORE_FILE, JSON.stringify(store, null, 2));
    } catch (e) {
      console.error('Konnte store.json nicht schreiben:', e.message);
    }
  }, 500);
}

loadStore();

// ── Hilfsfunktionen ─────────────────────────────────────────────────────────
function sendJson(res, status, obj) {
  const body = JSON.stringify(obj);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Access-Control-Allow-Origin': '*',
    'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    'Access-Control-Allow-Methods': 'GET, POST, PUT, DELETE, OPTIONS',
  });
  res.end(body);
}

function readBody(req) {
  return new Promise((resolve) => {
    let data = '';
    req.on('data', (c) => (data += c));
    req.on('end', () => {
      if (!data) return resolve({});
      try {
        resolve(JSON.parse(data));
      } catch {
        resolve({});
      }
    });
  });
}

function bearerUser(req) {
  const h = req.headers['authorization'] || '';
  const m = h.match(/^Bearer\s+(.+)$/i);
  if (!m) return null;
  const token = m[1].trim();
  const discordId = store.sessions[token];
  if (!discordId) return null;
  return store.users[discordId] || null;
}

function genCode() {
  let code;
  do {
    code = crypto.randomBytes(5).toString('hex').toUpperCase();
  } while (store.codes[code]);
  return code;
}

function publicFriend(u) {
  if (!u) return null;
  const p = store.presence[u.discordId];
  const online = !!(p && Date.now() - (p.timestamp || 0) <= PRESENCE_TTL_MS);
  return {
    id: u.id,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    server: online && p ? p.server : null,
    online,
  };
}

// ── Discord-Token validieren ─────────────────────────────────────────────────
async function verifyDiscordToken(discordToken) {
  if (!discordToken) return null;
  try {
    const r = await fetch('https://discord.com/api/v10/users/@me', {
      headers: { Authorization: 'Bearer ' + discordToken },
    });
    if (!r.ok) return null;
    return await r.json();
  } catch {
    return null;
  }
}

// ── Routing ────────────────────────────────────────────────────────────────
const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return sendJson(res, 204, {});

  const url = new URL(req.url, 'http://localhost');
  const pathname = url.pathname;
  const method = req.method;

  try {
    // ── Health ──
    if (pathname === '/health' && method === 'GET') {
      return sendJson(res, 200, { ok: true });
    }

    // ── Auth (Discord-Token -> Session) ──
    if (pathname === '/auth' && method === 'POST') {
      const body = await readBody(req);
      const discord = await verifyDiscordToken(body.discord_token);
      if (!discord || !discord.id) return sendJson(res, 401, { error: 'invalid_discord_token' });

      let user = store.users[discord.id];
      if (!user) {
        user = {
          discordId: discord.id,
          discordName: discord.global_name || discord.username,
          uuid: null,
          name: null,
          accounts: [],
          code: genCode(),
          friends: [],
          id: String(store.seq++),
        };
        store.users[discord.id] = user;
        store.codes[user.code] = discord.id;
      } else {
        user.discordName = discord.global_name || discord.username;
      }

      if (body.profile && typeof body.profile === 'object') {
        if (body.profile.uuid) user.uuid = String(body.profile.uuid);
        if (body.profile.name) user.name = String(body.profile.name);
        if (Array.isArray(body.profile.accounts)) user.accounts = body.profile.accounts;
      }

      const token = crypto.randomBytes(32).toString('hex');
      store.sessions[token] = discord.id;

      saveStore();
      return sendJson(res, 200, { token });
    }

    // ── Profil registrieren/aktualisieren ──
    if (pathname === '/profile' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      if (body.uuid) user.uuid = String(body.uuid);
      if (body.name) user.name = String(body.name);
      if (Array.isArray(body.accounts)) user.accounts = body.accounts;
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    // ── Eigene Profil-Daten ──
    if (pathname === '/me' && method === 'GET') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      return sendJson(res, 200, {
        id: user.id,
        name: user.name || user.discordName,
        uuid: user.uuid,
        code: user.code,
        accounts: user.accounts,
      });
    }

    // ── Freunde ──
    if (pathname === '/friends' && method === 'GET') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const list = (user.friends || [])
        .map((id) => store.users[id])
        .filter(Boolean)
        .map(publicFriend);
      return sendJson(res, 200, list);
    }

    if (pathname === '/friends' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);

      let targetId = null;
      if (body.code) targetId = store.codes[String(body.code).toUpperCase()];
      else if (body.target_id) targetId = String(body.target_id);

      const target = targetId ? store.users[targetId] : null;
      if (!target) return sendJson(res, 404, { error: 'target_not_found' });
      if (target.discordId === user.discordId) return sendJson(res, 400, { error: 'cannot_friend_self' });

      user.friends = user.friends || [];
      target.friends = target.friends || [];
      if (!user.friends.includes(target.discordId)) user.friends.push(target.discordId);
      if (!target.friends.includes(user.discordId)) target.friends.push(user.discordId);

      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/friends' && method === 'DELETE') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const targetId = body.target_id ? String(body.target_id) : null;
      const target = targetId ? store.users[targetId] : null;
      if (target) {
        user.friends = (user.friends || []).filter((x) => x !== target.discordId);
        target.friends = (target.friends || []).filter((x) => x !== user.discordId);
      }
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    // ── Presence ──
    if (pathname === '/presence' && method === 'PUT') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      if (!body.server || !body.name) return sendJson(res, 400, { error: 'server_and_name_required' });
      store.presence[user.discordId] = {
        server: String(body.server),
        name: String(body.name),
        timestamp: typeof body.timestamp === 'number' ? body.timestamp : Date.now(),
      };
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/presence' && method === 'DELETE') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      delete store.presence[user.discordId];
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/presence' && method === 'GET') {
      const server = url.searchParams.get('server');
      if (!server) return sendJson(res, 400, { error: 'server_required' });
      const now = Date.now();
      const names = [];
      for (const dId of Object.keys(store.presence)) {
        const p = store.presence[dId];
        if (!p) continue;
        if (p.server !== server) continue;
        if (now - (p.timestamp || 0) > PRESENCE_TTL_MS) continue;
        if (p.name) names.push(p.name);
      }
      return sendJson(res, 200, names);
    }

    return sendJson(res, 404, { error: 'not_found' });
  } catch (e) {
    console.error('Fehler:', e);
    return sendJson(res, 500, { error: 'internal_error' });
  }
});

server.listen(PORT, '0.0.0.0', () => {
  console.log(`Kollegen-Backend läuft auf Port ${PORT} (Daten: ${DATA_DIR})`);
});
