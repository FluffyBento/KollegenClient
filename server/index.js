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
 *   GET    /store                (optional Bearer)        -> {catalog, points?, equipped?}
 *   POST   /store/buy            (Bearer) {item_id}       -> {ok, points, equipped}
 *   POST   /store/equip          (Bearer) {item_id|category}
 *   Interne Bridge (Header X-Kollegen-Internal):
 *   GET    /internal/user, /internal/friends
 *   POST   /internal/profile, /internal/store-buy,
 *          /internal/store-equip, /internal/friend-add, /internal/friend-remove
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

// ── Kollegen-Points & Cosmetics (Steam-inspiriert) ─────────────────────────
// Startguthaben für neue Nutzer. Das Guthaben ist die "custom currency"
// (Kollegen-Points). Ein späterer Tausch (redeem) ist eingeplant – der
// Gesamtverdienst steckt in points_total und wird schon jetzt getrackt.
const START_POINTS = 250;

// Kosmetik-Katalog. Kategorien: title, avatar_frame, avatar_theme, badge.
const CATALOG = [
  // Titel (werden vor dem Namen angezeigt)
  { id: 'title_erkunder', category: 'title', name: 'Erkunder', desc: 'Zeig deinen Entdeckermut.', price: 120, rarity: 'common', data: { text: 'Erkunder' } },
  { id: 'title_veteran', category: 'title', name: 'Veteran', desc: 'Viele Stunden im Nether überlebt.', price: 450, rarity: 'rare', data: { text: 'Veteran' } },
  { id: 'title_champion', category: 'title', name: 'Champion', desc: 'Unbesiegt in deiner Arena.', price: 900, rarity: 'epic', data: { text: 'Champion' } },
  { id: 'title_legende', category: 'title', name: 'Legende', desc: 'Eine Legende unter den Kollegen.', price: 1600, rarity: 'legendary', featured: true, data: { text: 'Legende' } },
  // Avatar-Rahmen
  { id: 'frame_bronze', category: 'avatar_frame', name: 'Bronzen', desc: 'Bronzefarbener Avatar-Rahmen.', price: 200, rarity: 'common', data: { color1: '#cd7f32', color2: '#7a5630' } },
  { id: 'frame_silber', category: 'avatar_frame', name: 'Silbern', desc: 'Silberner Avatar-Rahmen.', price: 500, rarity: 'rare', data: { color1: '#c0c0c0', color2: '#7f7f8a' } },
  { id: 'frame_gold', category: 'avatar_frame', name: 'Golden', desc: 'Goldener Avatar-Rahmen.', price: 1000, rarity: 'epic', data: { color1: '#ffd700', color2: '#b8860b' } },
  { id: 'frame_regenbogen', category: 'avatar_frame', name: 'Regenbogen', desc: 'Schimmernder Regenbogen-Rahmen.', price: 2500, rarity: 'legendary', featured: true, data: { color1: '#ff6b6b', color2: '#6b5bff' } },
  // Avatar-Hintergrund (Theme)
  { id: 'theme_nebel', category: 'avatar_theme', name: 'Nebel', desc: 'Ruhiger Nebel-Hintergrund.', price: 250, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#3a4a6b,#1c1c28)' } },
  { id: 'theme_lava', category: 'avatar_theme', name: 'Lava', desc: 'Glühende Lava.', price: 600, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#ff8c00,#3a1212)' } },
  { id: 'theme_galaxie', category: 'avatar_theme', name: 'Galaxie', desc: 'Jenseits aller Welten.', price: 1200, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#2b1055,#7597de)' } },
  { id: 'theme_nether', category: 'avatar_theme', name: 'Nether', desc: 'Trotz der Hitze des Nethers.', price: 2000, rarity: 'legendary', data: { gradient: 'linear-gradient(135deg,#4a0e0e,#c92020)' } },
  // Badges (werden neben dem Namen angezeigt)
  { id: 'badge_stein', category: 'badge', name: 'Stein-Abzeichen', desc: 'Felsenfest im Kollegenkreis.', price: 100, rarity: 'common', data: { icon: '●', color: '#9aa5b1' } },
  { id: 'badge_diamant', category: 'badge', name: 'Diamant-Abzeichen', desc: 'Wertvoll wie ein Diamant.', price: 350, rarity: 'rare', data: { icon: '◆', color: '#4deeea' } },
  { id: 'badge_netherit', category: 'badge', name: 'Netherit-Abzeichen', desc: 'Unzerstörbar und dunkel.', price: 700, rarity: 'epic', data: { icon: '⬢', color: '#d4c9c0' } },
  { id: 'badge_drache', category: 'badge', name: 'Enderdrache-Abzeichen', desc: 'Bezwing den Drachen.', price: 1200, rarity: 'legendary', data: { icon: '★', color: '#c77dff' } },
  // Profil-Rahmen (um deine Profil-Karte, Steam-style)
  { id: 'pframe_emerald', category: 'profile_frame', name: 'Smaragd-Rahmen', desc: 'Grüner Glanz um dein Profil.', price: 500, rarity: 'rare', data: { color1: '#2ea043', color2: '#0f6b32' } },
  { id: 'pframe_ruby', category: 'profile_frame', name: 'Rubin-Rahmen', desc: 'Edler roter Rahmen.', price: 900, rarity: 'epic', data: { color1: '#f85149', color2: '#a32127' } },
  { id: 'pframe_royal', category: 'profile_frame', name: 'Königsblau-Rahmen', desc: 'Royal blau strahlend.', price: 1400, rarity: 'epic', data: { color1: '#3b82f6', color2: '#1e3a8a' } },
  { id: 'pframe_onyx', category: 'profile_frame', name: 'Onyx-Gold-Rahmen', desc: 'Schwarz mit Gold-Akzenten.', price: 2200, rarity: 'legendary', data: { color1: '#e6c96b', color2: '#374151' } },
  // Profil-Hintergrund (Seiten-Hintergrund deiner Profilseite)
  { id: 'pbg_dusk', category: 'profile_bg', name: 'Zwielicht', desc: 'Ruhiges, dunkles Dämmerlicht.', price: 300, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#1a1b2e,#0b0d14)' } },
  { id: 'pbg_lava', category: 'profile_bg', name: 'Lavastrom', desc: 'Glühende Lava unter deinem Profil.', price: 700, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#7a2200,#120404)' } },
  { id: 'pbg_aurora', category: 'profile_bg', name: 'Aurora', desc: 'Polarlichter über dunkler See.', price: 1400, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#0f2027,#203a43,#2c5364)' } },
  { id: 'pbg_ender', category: 'profile_bg', name: 'Das Ende', desc: 'Würde dem Enderdrachen gefallen.', price: 2400, rarity: 'legendary', featured: true, data: { gradient: 'linear-gradient(135deg,#0f0c29,#302b63,#24243e)' } },
  // Profil-Banner (Zierleiste oben auf der Profilseite, Steam-style)
  { id: 'banner_dawn', category: 'banner', name: 'Morgenröte', desc: 'Warme Töne für deinen Banner.', price: 400, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#f6d365,#fda085)' } },
  { id: 'banner_ember', category: 'banner', name: 'Glut', desc: 'Feuer und Gold.', price: 800, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#f12711,#f5af19)' } },
  { id: 'banner_ocean', category: 'banner', name: 'Ozean', desc: 'Blau wie die offene See.', price: 1500, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#2193b0,#6dd5ed)' } },
  { id: 'banner_onyx', category: 'banner', name: 'Onyx Gold', desc: 'Elegant, dunkel, teuer.', price: 2400, rarity: 'legendary', featured: true, data: { gradient: 'linear-gradient(135deg,#232526,#414345,#b8860b)' } },
];

function catById(id) {
  return CATALOG.find((c) => c.id === id) || (store.catalog || []).find((c) => c.id === id);
}

function ensureUserExtras(u) {
  if (!u) return u;
  if (typeof u.points !== 'number') u.points = START_POINTS;
  if (typeof u.points_total !== 'number') u.points_total = typeof u.points === 'number' ? u.points : START_POINTS;
  if (!Array.isArray(u.cosmetics)) u.cosmetics = [];
  if (!u.equipped || typeof u.equipped !== 'object') u.equipped = {};
  return u;
}

function levelOf(u) {
  const t = typeof u.points_total === 'number' ? u.points_total : START_POINTS;
  return 1 + Math.floor(t / 300);
}

// Volles Sozial-Bild eines Users (Punkte, Kosmetik, Equip, Presence).
function socialView(u) {
  ensureUserExtras(u);
  const p = store.presence[u.discordId];
  const online = !!(p && Date.now() - (p.timestamp || 0) <= PRESENCE_TTL_MS);
  return {
    id: u.id,
    discordId: u.discordId,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    online,
    server: online && p ? p.server : null,
    points: u.points,
    points_total: u.points_total,
    level: levelOf(u),
    cosmetics: (u.cosmetics || []).map((c) => (typeof c === 'object' && c && c.id ? c : { id: String(c), boughtAt: null })),
    equipped: u.equipped || {},
    profile: u.profile || null,
  };
}

// ── Persistenz ────────────────────────────────────────────────────────────
let store = { users: {}, sessions: {}, codes: {}, presence: {}, seq: 1, catalog: CATALOG };
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
  // Migration: neue Felder (Punkte/Kosmetik) für Bestandsnutzer ergänzen.
  // Katalog-Seed: neue Items per id in den bereits persistierten Katalog mergen,
  // damit Katalog-Erweiterungen auch bei Bestandesinstallationen ankommen.
  const curCat = Array.isArray(store.catalog) ? store.catalog : [];
  const byId = {};
  for (const c of curCat) if (c && c.id) byId[c.id] = c;
  store.catalog = CATALOG.map((c) => Object.assign({}, byId[c.id] || {}, c));
  for (const key of Object.keys(store.users || {})) {
    const u = store.users[key];
    if (u && typeof u === 'object') ensureUserExtras(u);
  }
  saveStore();
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

// User robust auflösen: per discordId, numerischer id oder Freundes-Code.
function resolveUser(x) {
  if (!x) return null;
  const s = String(x);
  if (store.users[s]) return store.users[s];
  const hit = Object.values(store.users || {}).find((u) => u && (u.id === s || u.code === s.toUpperCase()));
  return hit || null;
}

function publicFriend(u) {
  if (!u) return null;
  ensureUserExtras(u);
  const p = store.presence[u.discordId];
  const online = !!(p && Date.now() - (p.timestamp || 0) <= PRESENCE_TTL_MS);
  return {
    id: u.id,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    server: online && p ? p.server : null,
    online,
    level: levelOf(u),
    equipped: u.equipped || {},
    // Profil-Zusammenfassung (nur für Freunde sichtbar, egal ob public).
    profile:
      u.profile && typeof u.profile === 'object'
        ? {
            bio: u.profile.bio || null,
            avatar_data_url: u.profile.avatar_data_url || null,
            banner_data_url: u.profile.banner_data_url || null,
            avatar_choice: u.profile.avatar_choice || 'discord',
            public: !!u.profile.public,
          }
        : null,
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
      ensureUserExtras(user);

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

      // Optional: profile customizations for public profile directory
      user.profile = user.profile || {};
      if (body.profile && typeof body.profile === 'object') {
        const p = body.profile;
        if (typeof p.bio === 'string') user.profile.bio = p.bio;
        if (typeof p.banner_data_url === 'string') user.profile.banner_data_url = p.banner_data_url;
        if (typeof p.avatar_data_url === 'string') user.profile.avatar_data_url = p.avatar_data_url;
        if (typeof p.avatar_choice === 'string') user.profile.avatar_choice = p.avatar_choice;
        if (typeof p.public === 'boolean') user.profile.public = p.public;
      }

      // Optional: Kosmetik anlegen/ausrüsten (z.B. via Website/Client).
      if (Array.isArray(body.cosmetics)) {
        for (const c of body.cosmetics) {
          const itemId = typeof c === 'object' && c ? String(c.id) : String(c);
          if (itemId && !user.cosmetics.some((x) => x && x.id === itemId)) {
            user.cosmetics.push({ id: itemId, boughtAt: Date.now() });
          }
        }
      }
      if (body.equipped && typeof body.equipped === 'object') {
        const next = {};
        for (const cat of Object.keys(body.equipped)) {
          const itemId = String(body.equipped[cat] || '').trim();
          if (!itemId) { next[cat] = ''; continue; }
          const item = catById(itemId);
          if (item && item.category === cat && user.cosmetics.some((x) => x && x.id === itemId)) {
            next[cat] = itemId;
          }
        }
        user.equipped = next;
      }

      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    // ── Eigene Profil-Daten ──
    if (pathname === '/me' && method === 'GET') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const sv = socialView(user);
      return sendJson(res, 200, {
        id: user.id,
        name: user.name || user.discordName,
        uuid: user.uuid,
        code: user.code,
        accounts: user.accounts,
        profile: user.profile || null,
        points: sv.points,
        points_total: sv.points_total,
        level: sv.level,
        cosmetics: sv.cosmetics,
        equipped: sv.equipped,
      });
    }

    // ── Store: Katalog (öffentlich, mit Owned/Equipped wenn eingeloggt) ──
    if (pathname === '/store' && method === 'GET') {
      let user = bearerUser(req);
      if (!user) {
        const dId = url.searchParams.get('discordId');
        if (dId && internalAuthorized(req)) user = store.users[dId] || null;
      }
      const hasUser = !!user;
      if (user) ensureUserExtras(user);
      const items = (store.catalog || CATALOG)
        .map((c) => Object.assign({}, c, {
          owned: hasUser ? user.cosmetics.some((x) => x && x.id === c.id) : false,
          equippedCategory: hasUser ? user.equipped[c.category] === c.id : false,
        }));
      return sendJson(res, 200, hasUser
        ? { catalog: items, points: user.points, points_total: user.points_total, level: levelOf(user), equipped: user.equipped }
        : { catalog: items, needsAuth: true });
    }

    // ── Store: Kaufen (Kollegen-Points) ──
    if (pathname === '/store/buy' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureUserExtras(user);
      const body = await readBody(req);
      const itemId = String(body.item_id || '').trim();
      const item = catById(itemId);
      if (!item) return sendJson(res, 404, { error: 'item_not_found' });
      if (user.cosmetics.some((x) => x && x.id === itemId)) return sendJson(res, 400, { error: 'already_owned' });
      if (user.points < item.price) return sendJson(res, 400, { error: 'not_enough_points', points: user.points, price: item.price });
      user.points -= item.price;
      user.cosmetics.push({ id: itemId, boughtAt: Date.now() });
      // Komfort: leere Kategorie-Slots automatisch ausrüsten.
      if (!user.equipped[item.category]) user.equipped[item.category] = itemId;
      saveStore();
      return sendJson(res, 200, {
        ok: true,
        points: user.points,
        item: { id: itemId, category: item.category, equipped: true },
        equipped: user.equipped,
      });
    }

    // ── Store: Ausrüsten / Ablegen ──
    if (pathname === '/store/equip' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureUserExtras(user);
      const body = await readBody(req);
      const itemId = String(body.item_id || '');
      if (!itemId) {
        // Ablegen: { category: "avatar_frame" } ohne item_id → Slot leeren.
        if (!user.equipped || typeof user.equipped !== 'object') user.equipped = {};
        const cat = String(body.category || '');
        if (cat) user.equipped[cat] = '';
        else if (body.all) user.equipped = {};
        saveStore();
        return sendJson(res, 200, { ok: true, equipped: user.equipped });
      }
      const item = catById(itemId);
      if (!item) return sendJson(res, 404, { error: 'item_not_found' });
      if (!user.cosmetics.some((x) => x && x.id === itemId)) return sendJson(res, 403, { error: 'not_owned' });
      user.equipped = user.equipped || {};
      user.equipped[item.category] = itemId;
      saveStore();
      return sendJson(res, 200, { ok: true, equipped: user.equipped });
    }

    // ── Öffentliches Profil-Listing / Suche ──
    if (pathname === '/profiles' && method === 'GET') {
      // query: search, limit, offset
      const search = (url.searchParams.get('search') || '').toLowerCase();
      const limit = parseInt(url.searchParams.get('limit') || '50', 10);
      const offset = parseInt(url.searchParams.get('offset') || '0', 10);

      const all = Object.values(store.users || {})
        .filter(u => u && u.profile && u.profile.public)
        .map(u => ({
          id: u.id,
          name: u.name || u.discordName,
          uuid: u.uuid || null,
          code: u.code,
          avatar_data_url: (u.profile && u.profile.avatar_data_url) || null,
          banner_data_url: (u.profile && u.profile.banner_data_url) || null,
          bio: (u.profile && u.profile.bio) || null,
          level: levelOf(u),
          equipped: ensureUserExtras(u).equipped || {},
        }));

      const filtered = search
        ? all.filter(p => (p.name || '').toLowerCase().includes(search) || (p.code || '').toLowerCase().includes(search))
        : all;

      const slice = filtered.slice(offset, offset + Math.min(limit, 200));
      return sendJson(res, 200, { total: filtered.length, items: slice });
    }

    // ── Einzelnes öffentliches Profil ──
    if (pathname.startsWith('/profiles/') && method === 'GET') {
      const parts = pathname.split('/').filter(Boolean);
      // /profiles/:id
      if (parts.length === 2) {
        const id = parts[1];
        const u = Object.values(store.users || {}).find(x => x && (x.id === id || x.code === id));
        if (!u || !u.profile || !u.profile.public) return sendJson(res, 404, { error: 'not_found' });
        return sendJson(res, 200, {
          id: u.id,
          name: u.name || u.discordName,
          uuid: u.uuid || null,
          code: u.code,
          avatar_data_url: (u.profile && u.profile.avatar_data_url) || null,
          banner_data_url: (u.profile && u.profile.banner_data_url) || null,
          bio: (u.profile && u.profile.bio) || null,
          level: levelOf(u),
          equipped: ensureUserExtras(u).equipped || {},
        });
      }
    }

    // ── Interne Bridge (nur Website-Server → Backend) ─────────────────────────
// Authentifiziert über das Shared Secret in KOLLEGEN_INTERNAL_SECRET
// (Umgebung/EnvironmentFile). Die Website (kollegen.me server.js) ruft diese
// Endpoints mit demselben Secret auf, um Profil + MC-Identität abzugleichen –
// ohne dass Nutzereingaben durch die Website-Sessions hindurchtoken müssen.
function internalAuthorized(req) {
  const expect = process.env.KOLLEGEN_INTERNAL_SECRET || '';
  if (!expect) return false;
  const h = req.headers['x-kollegen-internal'] || '';
  return h === expect;
}

// GET /internal/user?discordId=... | ?id=... → öffentliche Profildaten des Users
if (pathname === '/internal/user' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const discordId = url.searchParams.get('discordId');
  const id = url.searchParams.get('id');
  let u = null;
  if (discordId) u = store.users[discordId];
  else if (id) u = Object.values(store.users || {}).find(x => x && x.id === String(id)) || null;
  if (!u) return sendJson(res, 404, { error: 'not_found' });
  ensureUserExtras(u);
  return sendJson(res, 200, {
    id: u.id,
    discordId: u.discordId,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    profile: u.profile || null,
    mc_name: u.name || null,
    points: u.points,
    points_total: u.points_total,
    level: levelOf(u),
    cosmetics: u.cosmetics,
    equipped: u.equipped || {},
  });
}

// POST /internal/profile → Legt Profil + MC-Identität an/aktualisiert (Server-to-Server)
if (pathname === '/internal/profile' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const discordId = String(body.discordId || '');
  if (!discordId) return sendJson(res, 400, { error: 'discordId_required' });
  const user = store.users[discordId] || (store.users[discordId] = {
    discordId,
    discordName: String(body.discordName || 'Discord-Nutzer'),
    uuid: null,
    name: null,
    accounts: [],
    code: genCode(),
    friends: [],
    id: String(store.seq++),
  });
  ensureUserExtras(user);
  if (body.mcName) user.name = String(body.mcName);
  if (body.uuid) user.uuid = String(body.uuid);
  if (body.discordName) user.discordName = String(body.discordName);
  if (body.profile && typeof body.profile === 'object') {
    user.profile = user.profile || {};
    const p = body.profile;
    if (typeof p.bio === 'string') user.profile.bio = p.bio;
    if (typeof p.banner_data_url === 'string') user.profile.banner_data_url = p.banner_data_url;
    if (typeof p.avatar_data_url === 'string') user.profile.avatar_data_url = p.avatar_data_url;
    if (typeof p.avatar_choice === 'string') user.profile.avatar_choice = p.avatar_choice;
    if (typeof p.public === 'boolean') user.profile.public = p.public;
  }
  if (body.equipped && typeof body.equipped === 'object') {
    const next = {};
    for (const cat of Object.keys(body.equipped)) {
      const itemId = String(body.equipped[cat] || '').trim();
      if (!itemId) { next[cat] = ''; continue; }
      const item = catById(itemId);
      if (item && item.category === cat && user.cosmetics.some((x) => x && x.id === itemId)) {
        next[cat] = itemId;
      }
    }
    user.equipped = next;
  }
  store.codes[user.code] = user.discordId;
  saveStore();
  return sendJson(res, 200, { ok: true, user: { id: user.id, code: user.code } });
}

// GET /internal/store-buy → Kauf für einen User (Server-to-Server, ohne Bearer)
if (pathname === '/internal/store-buy' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const discordId = String(body.discordId || '');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  ensureUserExtras(user);
  const itemId = String(body.item_id || '').trim();
  const item = catById(itemId);
  if (!item) return sendJson(res, 404, { error: 'item_not_found' });
  if (user.cosmetics.some((x) => x && x.id === itemId)) return sendJson(res, 400, { error: 'already_owned' });
  if (user.points < item.price) return sendJson(res, 400, { error: 'not_enough_points', points: user.points, price: item.price });
  user.points -= item.price;
  user.cosmetics.push({ id: itemId, boughtAt: Date.now() });
  if (!user.equipped[item.category]) user.equipped[item.category] = itemId;
  saveStore();
  return sendJson(res, 200, { ok: true, points: user.points, item: { id: itemId, category: item.category, equipped: true }, equipped: user.equipped });
}

// POST /internal/store-equip → Ausrüsten/Ablegen (Server-to-Server)
if (pathname === '/internal/store-equip' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const discordId = String(body.discordId || '');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  ensureUserExtras(user);
  const itemId = String(body.item_id || '');
  if (!itemId) {
    const cat = String(body.category || '');
    if (cat) user.equipped[cat] = '';
    else if (body.all) user.equipped = {};
    saveStore();
    return sendJson(res, 200, { ok: true, equipped: user.equipped });
  }
  const item = catById(itemId);
  if (!item) return sendJson(res, 404, { error: 'item_not_found' });
  if (!user.cosmetics.some((x) => x && x.id === itemId)) return sendJson(res, 403, { error: 'not_owned' });
  user.equipped[item.category] = itemId;
  saveStore();
  return sendJson(res, 200, { ok: true, equipped: user.equipped });
}

// GET /internal/friends?discordId=... → Freundesliste (Server-to-Server)
if (pathname === '/internal/friends' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const discordId = url.searchParams.get('discordId');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const list = (user.friends || [])
    .map((id) => store.users[id])
    .filter(Boolean)
    .map(publicFriend);
  return sendJson(res, 200, list);
}

// POST /internal/friend-add {discordId, code} → Freund hinzufügen (Server-to-Server)
if (pathname === '/internal/friend-add' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const discordId = String(body.discordId || '');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const targetId = body.code ? store.codes[String(body.code).toUpperCase()] : (body.target_id ? String(body.target_id) : null);
  const target = targetId ? resolveUser(targetId) : null;
  if (!target) return sendJson(res, 404, { error: 'target_not_found' });
  if (target.discordId === user.discordId) return sendJson(res, 400, { error: 'cannot_friend_self' });
  user.friends = user.friends || [];
  target.friends = target.friends || [];
  if (!user.friends.includes(target.discordId)) user.friends.push(target.discordId);
  if (!target.friends.includes(user.discordId)) target.friends.push(user.discordId);
  saveStore();
  return sendJson(res, 200, { ok: true });
}

// POST /internal/friend-remove {discordId, target_id} → Freund entfernen (Server-to-Server)
if (pathname === '/internal/friend-remove' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const discordId = String(body.discordId || '');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const targetId = body.target_id ? String(body.target_id) : null;
  const target = targetId ? resolveUser(targetId) : null;
  if (target) {
    user.friends = (user.friends || []).filter((x) => x !== target.discordId);
    target.friends = (target.friends || []).filter((x) => x !== user.discordId);
  }
  saveStore();
  return sendJson(res, 200, { ok: true });
}

// GET /internal/users?search= → Admin: Nutzerliste mit Punkten/Kosmetik/Level
if (pathname === '/internal/users' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const search = (url.searchParams.get('search') || '').toLowerCase();
  const all = Object.values(store.users || {})
    .filter(Boolean)
    .filter((u) => {
      if (!search) return true;
      const h = [u.name, u.discordName, u.code, u.id, u.discordId, u.uuid]
        .map((x) => String(x || '').toLowerCase())
        .join(' ');
      return h.includes(search);
    })
    .map(socialView)
    .sort((a, b) => (a.name || '').toLowerCase().localeCompare((b.name || '').toLowerCase()) || String(a.discordId).localeCompare(String(b.discordId)));
  return sendJson(res, 200, all);
}

// POST /internal/points {discordId, delta} → Punkte geben/abziehen (Admin)
if (pathname === '/internal/points' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const user = resolveUser(body.discordId);
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  ensureUserExtras(user);
  const delta = Math.round(Number(body.delta) || 0);
  user.points = Math.max(0, user.points + delta);
  user.points_total = Math.max(0, user.points_total + delta);
  saveStore();
  return sendJson(res, 200, { ok: true, points: user.points, points_total: user.points_total, level: levelOf(user) });
}

// POST /internal/grant {discordId, item_id} → Kosmetik kostenlos schenken (Admin)
if (pathname === '/internal/grant' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const user = resolveUser(body.discordId);
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const itemId = String(body.item_id || '').trim();
  const item = catById(itemId);
  if (!item) return sendJson(res, 404, { error: 'item_not_found' });
  ensureUserExtras(user);
  if (!user.cosmetics.some((x) => x && x.id === itemId)) {
    user.cosmetics.push({ id: itemId, boughtAt: Date.now(), granted: true });
  }
  if (body.equip !== false && !user.equipped[item.category]) user.equipped[item.category] = itemId;
  saveStore();
  return sendJson(res, 200, { ok: true, items: user.cosmetics.length, equipped: user.equipped });
}

// POST /internal/reset {discordId} → Punkte/Level zurücksetzen, Equip leeren (Admin)
if (pathname === '/internal/reset' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const user = resolveUser(body.discordId);
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  ensureUserExtras(user);
  user.points = START_POINTS;
  user.points_total = START_POINTS;
  user.equipped = {};
  saveStore();
  return sendJson(res, 200, { ok: true, points: user.points, level: levelOf(user) });
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

      const target = targetId ? resolveUser(targetId) : null;
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
      const target = targetId ? resolveUser(targetId) : null;
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
