'use strict';



const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const { URL } = require('url');

const PORT = parseInt(process.env.PORT || '8080', 10);
const DATA_DIR = process.env.KOLLEGEN_DATA_DIR || path.join(__dirname, 'data');
const PRESENCE_TTL_MS = parseInt(process.env.PRESENCE_TTL_MS || '90000', 10);
const STORE_FILE = path.join(DATA_DIR, 'store.json');





const START_POINTS = 250;


const CATALOG = [
  
  { id: 'title_erkunder', category: 'title', name: 'Erkunder', desc: 'Zeig deinen Entdeckermut.', price: 120, rarity: 'common', data: { text: 'Erkunder' } },
  { id: 'title_veteran', category: 'title', name: 'Veteran', desc: 'Viele Stunden im Nether überlebt.', price: 450, rarity: 'rare', data: { text: 'Veteran' } },
  { id: 'title_champion', category: 'title', name: 'Champion', desc: 'Unbesiegt in deiner Arena.', price: 900, rarity: 'epic', data: { text: 'Champion' } },
  { id: 'title_legende', category: 'title', name: 'Legende', desc: 'Eine Legende unter den Kollegen.', price: 1600, rarity: 'legendary', featured: true, data: { text: 'Legende' } },
  
  { id: 'frame_bronze', category: 'avatar_frame', name: 'Bronzen', desc: 'Bronzefarbener Avatar-Rahmen.', price: 200, rarity: 'common', data: { color1: '#cd7f32', color2: '#7a5630' } },
  { id: 'frame_silber', category: 'avatar_frame', name: 'Silbern', desc: 'Silberner Avatar-Rahmen.', price: 500, rarity: 'rare', data: { color1: '#c0c0c0', color2: '#7f7f8a' } },
  { id: 'frame_gold', category: 'avatar_frame', name: 'Golden', desc: 'Goldener Avatar-Rahmen.', price: 1000, rarity: 'epic', data: { color1: '#ffd700', color2: '#b8860b' } },
  { id: 'frame_regenbogen', category: 'avatar_frame', name: 'Regenbogen', desc: 'Schimmernder Regenbogen-Rahmen.', price: 2500, rarity: 'legendary', featured: true, data: { color1: '#ff6b6b', color2: '#6b5bff' } },
  
  { id: 'theme_nebel', category: 'avatar_theme', name: 'Nebel', desc: 'Ruhiger Nebel-Hintergrund.', price: 250, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#3a4a6b,#1c1c28)' } },
  { id: 'theme_lava', category: 'avatar_theme', name: 'Lava', desc: 'Glühende Lava.', price: 600, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#ff8c00,#3a1212)' } },
  { id: 'theme_galaxie', category: 'avatar_theme', name: 'Galaxie', desc: 'Jenseits aller Welten.', price: 1200, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#2b1055,#7597de)' } },
  { id: 'theme_nether', category: 'avatar_theme', name: 'Nether', desc: 'Trotz der Hitze des Nethers.', price: 2000, rarity: 'legendary', data: { gradient: 'linear-gradient(135deg,#4a0e0e,#c92020)' } },
  
  { id: 'badge_stein', category: 'badge', name: 'Stein-Abzeichen', desc: 'Felsenfest im Kollegenkreis.', price: 100, rarity: 'common', data: { icon: '●', color: '#9aa5b1' } },
  { id: 'badge_diamant', category: 'badge', name: 'Diamant-Abzeichen', desc: 'Wertvoll wie ein Diamant.', price: 350, rarity: 'rare', data: { icon: '◆', color: '#4deeea' } },
  { id: 'badge_netherit', category: 'badge', name: 'Netherit-Abzeichen', desc: 'Unzerstörbar und dunkel.', price: 700, rarity: 'epic', data: { icon: '⬢', color: '#d4c9c0' } },
  { id: 'badge_drache', category: 'badge', name: 'Enderdrache-Abzeichen', desc: 'Bezwing den Drachen.', price: 1200, rarity: 'legendary', data: { icon: '★', color: '#c77dff' } },
  
  { id: 'pframe_emerald', category: 'profile_frame', name: 'Smaragd-Rahmen', desc: 'Grüner Glanz um dein Profil.', price: 500, rarity: 'rare', data: { color1: '#2ea043', color2: '#0f6b32' } },
  { id: 'pframe_ruby', category: 'profile_frame', name: 'Rubin-Rahmen', desc: 'Edler roter Rahmen.', price: 900, rarity: 'epic', data: { color1: '#f85149', color2: '#a32127' } },
  { id: 'pframe_royal', category: 'profile_frame', name: 'Königsblau-Rahmen', desc: 'Royal blau strahlend.', price: 1400, rarity: 'epic', data: { color1: '#3b82f6', color2: '#1e3a8a' } },
  { id: 'pframe_onyx', category: 'profile_frame', name: 'Onyx-Gold-Rahmen', desc: 'Schwarz mit Gold-Akzenten.', price: 2200, rarity: 'legendary', data: { color1: '#e6c96b', color2: '#374151' } },
  
  { id: 'pbg_dusk', category: 'profile_bg', name: 'Zwielicht', desc: 'Ruhiges, dunkles Dämmerlicht.', price: 300, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#1a1b2e,#0b0d14)' } },
  { id: 'pbg_lava', category: 'profile_bg', name: 'Lavastrom', desc: 'Glühende Lava unter deinem Profil.', price: 700, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#7a2200,#120404)' } },
  { id: 'pbg_aurora', category: 'profile_bg', name: 'Aurora', desc: 'Polarlichter über dunkler See.', price: 1400, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#0f2027,#203a43,#2c5364)' } },
  { id: 'pbg_ender', category: 'profile_bg', name: 'Das Ende', desc: 'Würde dem Enderdrachen gefallen.', price: 2400, rarity: 'legendary', featured: true, data: { gradient: 'linear-gradient(135deg,#0f0c29,#302b63,#24243e)' } },
  
  { id: 'banner_dawn', category: 'banner', name: 'Morgenröte', desc: 'Warme Töne für deinen Banner.', price: 400, rarity: 'common', data: { gradient: 'linear-gradient(135deg,#f6d365,#fda085)' } },
  { id: 'banner_ember', category: 'banner', name: 'Glut', desc: 'Feuer und Gold.', price: 800, rarity: 'rare', data: { gradient: 'linear-gradient(135deg,#f12711,#f5af19)' } },
  { id: 'banner_ocean', category: 'banner', name: 'Ozean', desc: 'Blau wie die offene See.', price: 1500, rarity: 'epic', data: { gradient: 'linear-gradient(135deg,#2193b0,#6dd5ed)' } },
  { id: 'banner_onyx', category: 'banner', name: 'Onyx Gold', desc: 'Elegant, dunkel, teuer.', price: 2400, rarity: 'legendary', featured: true, data: { gradient: 'linear-gradient(135deg,#232526,#414345,#b8860b)' } },
  
  { id: 'stil_klassisch', category: 'profil_stil', name: 'Klassisch Gold', desc: 'Warme Gold-Akzente, seri\u00f6s.', price: 350, rarity: 'common', data: { accent: '#D4AF37', font: 'Outfit' } },
  { id: 'stil_cyan', category: 'profil_stil', name: 'Eisblau', desc: 'Klare Cyan-Akzente mit Tech-Feeling.', price: 500, rarity: 'rare', data: { accent: '#4deeea', font: 'Inter' } },
  { id: 'stil_aura', category: 'profil_stil', name: 'Aura', desc: 'Sanftes Lila f\u00fcr mystische Profile.', price: 600, rarity: 'rare', data: { accent: '#c77dff', font: 'Outfit' } },
  { id: 'stil_wald', category: 'profil_stil', name: 'Wald', desc: 'Erdige Gr\u00fcnt\u00f6ne f\u00fcr Naturen.', price: 700, rarity: 'epic', data: { accent: '#7ee787', font: 'Outfit' } },
  { id: 'stil_gluth', category: 'profil_stil', name: 'Glut', desc: 'Feuriges Rot \u2013 hei\u00df und selbstbewusst.', price: 800, rarity: 'epic', data: { accent: '#f85149', font: 'Inter' } },
  { id: 'stil_ozean', category: 'profil_stil', name: 'Ozean', desc: 'K\u00f6nigsblau mit Tiefe.', price: 1000, rarity: 'epic', data: { accent: '#3b82f6', font: 'Outfit' } },
  { id: 'stil_onyx', category: 'profil_stil', name: 'Onyx Schwarzgold', desc: 'Dunkel mit edlem Gold-Glanz.', price: 2200, rarity: 'legendary', featured: true, data: { accent: '#e6c96b', font: 'Outfit' } },
  
  { id: 'name_bernstein', category: 'name_color', name: 'Bernstein', desc: 'Warm wie Bernstein.', price: 150, rarity: 'common', data: { accent: '#ffb454' } },
  { id: 'name_karmesin', category: 'name_color', name: 'Karmesin', desc: 'Feuriges Rot für deinen Namen.', price: 400, rarity: 'rare', data: { accent: '#ff5f56' } },
  { id: 'name_tuerkis', category: 'name_color', name: 'Türkis', desc: 'Frisch wie eine Lagune.', price: 700, rarity: 'epic', data: { accent: '#39d7ff' } },
  { id: 'name_violett', category: 'name_color', name: 'Neonviolett', desc: 'Kräftiges Violett mit Glow.', price: 1400, rarity: 'epic', data: { accent: '#c77dff' } },
  { id: 'name_onyxgold', category: 'name_color', name: 'Onyxgold', desc: 'Schwarz und Gold für den Namen.', price: 2200, rarity: 'legendary', featured: true, data: { accent: '#e6c96b' } },
  
  { id: 'sticker_herz', category: 'sticker', name: 'Herz-Sticker', desc: 'Zeig deine Zuneigung.', price: 120, rarity: 'common', data: { icon: '❤', color: '#ff6b6b' } },
  { id: 'sticker_glueck', category: 'sticker', name: 'Glücksklee', desc: 'Grün wie Freude.', price: 350, rarity: 'rare', data: { icon: '🍀', color: '#7ee787' } },
  { id: 'sticker_stern', category: 'sticker', name: 'Goldstern', desc: 'Strahl wie ein Star.', price: 650, rarity: 'epic', data: { icon: '⭐', color: '#ffd700' } },
  { id: 'sticker_blitz', category: 'sticker', name: 'Blitz', desc: 'Schnell und elektrisierend.', price: 800, rarity: 'epic', data: { icon: '⚡', color: '#ffd23f' } },
  { id: 'sticker_drache', category: 'sticker', name: 'Drachen-Sticker', desc: 'Der Enderdrache grüßt.', price: 1200, rarity: 'legendary', data: { icon: '🐉', color: '#c77dff' } },
  
  { id: 'font_rund', category: 'font', name: 'Rund und freundlich', desc: 'Weiche, freundliche Schrift.', price: 100, rarity: 'common', data: { font: '"Verdana","Segoe UI",sans-serif' } },
  { id: 'font_serif', category: 'font', name: 'Klassische Buchstaben', desc: 'Serifen für die Ewigkeit.', price: 250, rarity: 'rare', data: { font: '"Georgia","Times New Roman",serif' } },
  { id: 'font_typewriter', category: 'font', name: 'Typewriter', desc: 'Wie auf einer Schreibmaschine.', price: 600, rarity: 'epic', data: { font: '"Courier New",monospace' } },
  { id: 'font_banner', category: 'font', name: 'Banner-Bliter', desc: 'Groß, fett, auffällig.', price: 1600, rarity: 'legendary', data: { font: 'Impact,"Arial Narrow Bold",sans-serif' } },
];

function catById(id) {
  const i = typeof id === 'string' ? id : id && id.id ? id.id : null;
  if (!i) return null;
  return CATALOG.find((c) => c.id === i) || (store.catalog || []).find((c) => c.id === i);
}

function ensureUserExtras(u) {
  if (!u) return u;
  if (typeof u.points !== 'number') u.points = START_POINTS;
  if (typeof u.points_total !== 'number') u.points_total = typeof u.points === 'number' ? u.points : START_POINTS;
  if (!Array.isArray(u.cosmetics)) u.cosmetics = [];
  if (!u.equipped || typeof u.equipped !== 'object') u.equipped = {};
  if (!Array.isArray(u.friend_requests)) u.friend_requests = [];
  return u;
}

function levelOf(u) {
  const t = typeof u.points_total === 'number' ? u.points_total : START_POINTS;
  return 1 + Math.floor(t / 300);
}


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


let store = { users: {}, sessions: {}, codes: {}, presence: {}, dms: {}, groups: {}, calls: {}, seq: 1, catalog: CATALOG };
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
  const eq = u.equipped || {};
  const equippedData = {};
  for (const cat of Object.keys(eq)) {
    const it = eq[cat] ? catById(eq[cat]) : null;
    equippedData[cat] = it ? { id: it.id, category: it.category, name: it.name || null, data: it.data || null } : null;
  }
  return {
    id: u.id,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    server: online && p ? p.server : null,
    online,
    level: levelOf(u),
    equipped: equippedData,
    
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


function addFriendByCode(me, target) {
  
  me.friends = me.friends || [];
  target.friends = target.friends || [];
  target.friend_requests = target.friend_requests || [];
  me.friend_requests = me.friend_requests || [];
  if (me.friends.includes(target.discordId)) return { ok: true, already: true };
  const mutual = target.friend_requests.findIndex((r) => r && r.from === me.discordId);
  if (mutual >= 0) {
    target.friend_requests.splice(mutual, 1);
    if (!me.friends.includes(target.discordId)) me.friends.push(target.discordId);
    if (!target.friends.includes(me.discordId)) target.friends.push(me.discordId);
    saveStore();
    return { ok: true, accepted: true };
  }
  const mine = me.friend_requests.findIndex((r) => r && r.from === target.discordId);
  if (mine >= 0) return { ok: true, pending: true };
  target.friend_requests.push({ from: me.discordId, at: Date.now() });
  saveStore();
  return { ok: true, pending: true };
}

function acceptFriendRequest(me, fromId) {
  me.friends = me.friends || [];
  const from = fromId ? store.users[fromId] : null;
  if (!from) return { error: 'user_not_found' };
  const idx = (me.friend_requests || []).findIndex((r) => r && r.from === fromId);
  if (idx < 0) return { error: 'request_not_found' };
  me.friend_requests.splice(idx, 1);
  from.friends = from.friends || [];
  if (!me.friends.includes(fromId)) me.friends.push(fromId);
  if (!from.friends.includes(me.discordId)) from.friends.push(me.discordId);
  saveStore();
  return { ok: true };
}

function declineFriendRequest(me, fromId) {
  const idx = (me.friend_requests || []).findIndex((r) => r && r.from === fromId);
  if (idx >= 0) me.friend_requests.splice(idx, 1);
  saveStore();
  return { ok: true };
}

function incomingRequests(user) {
  if (!user) return [];
  const out = (user.friend_requests || [])
    .map((r) => {
      const from = r && r.from ? store.users[r.from] : null;
      if (!from) return null;
      return { request: { from: r.from, at: r.at || 0 }, user: publicFriend(from) };
    })
    .filter(Boolean)
    .sort((a, b) => (b.request.at || 0) - (a.request.at || 0));
  return out;
}


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


const server = http.createServer(async (req, res) => {
  if (req.method === 'OPTIONS') return sendJson(res, 204, {});

  const url = new URL(req.url, 'http://localhost');
  const pathname = url.pathname;
  const method = req.method;

  try {
    
    if (pathname === '/health' && method === 'GET') {
      return sendJson(res, 200, { ok: true });
    }

    
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

    
    if (pathname === '/profile' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      if (body.uuid) user.uuid = String(body.uuid);
      if (body.name) user.name = String(body.name);
      if (Array.isArray(body.accounts)) user.accounts = body.accounts;

      
      user.profile = user.profile || {};
      if (body.profile && typeof body.profile === 'object') {
        const p = body.profile;
        if (typeof p.bio === 'string') user.profile.bio = p.bio;
        if (typeof p.banner_data_url === 'string') user.profile.banner_data_url = p.banner_data_url;
        if (typeof p.avatar_data_url === 'string') user.profile.avatar_data_url = p.avatar_data_url;
        if (typeof p.avatar_choice === 'string') user.profile.avatar_choice = p.avatar_choice;
        if (typeof p.public === 'boolean') user.profile.public = p.public;
      }

      
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
      
      if (!user.equipped[item.category]) user.equipped[item.category] = itemId;
      saveStore();
      return sendJson(res, 200, {
        ok: true,
        points: user.points,
        item: { id: itemId, category: item.category, equipped: true },
        equipped: user.equipped,
      });
    }

    
    if (pathname === '/store/equip' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureUserExtras(user);
      const body = await readBody(req);
      const itemId = String(body.item_id || '');
      if (!itemId) {
        
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

    
    if (pathname === '/profiles' && method === 'GET') {
      
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

    
    if (pathname.startsWith('/profiles/') && method === 'GET') {
      const parts = pathname.split('/').filter(Boolean);
      
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

    




function internalAuthorized(req) {
  const expect = process.env.KOLLEGEN_INTERNAL_SECRET || '';
  if (!expect) return false;
  const h = req.headers['x-kollegen-internal'] || '';
  return h === expect;
}


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
  const r = addFriendByCode(user, target);
  if (r.error) return sendJson(res, 400, { error: r.error });
  return sendJson(res, 200, r);
}


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


if (pathname === '/internal/friend-requests' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const discordId = url.searchParams.get('discordId');
  const user = discordId ? store.users[discordId] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  return sendJson(res, 200, incomingRequests(user));
}


if (pathname === '/internal/friend-accept' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const user = body.discordId ? store.users[String(body.discordId)] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const r = acceptFriendRequest(user, String(body.from_id || ''));
  if (r.error) return sendJson(res, 400, { error: r.error });
  return sendJson(res, 200, r);
}


if (pathname === '/internal/friend-decline' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const user = body.discordId ? store.users[String(body.discordId)] : null;
  if (!user) return sendJson(res, 404, { error: 'user_not_found' });
  const r = declineFriendRequest(user, String(body.from_id || ''));
  return sendJson(res, 200, r);
}




if (pathname === '/internal/profile-view' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const code = (url.searchParams.get('code') || '').toUpperCase();
  const id = url.searchParams.get('id');
  const viewerId = url.searchParams.get('viewer_id');
  let u = null;
  if (code) u = store.users[store.codes[code]];
  else if (id) u = resolveUser(id);
  if (!u) return sendJson(res, 404, { error: 'not_found' });
  ensureUserExtras(u);
  const eq = u.equipped || {};
  const equippedData = {};
  for (const cat of Object.keys(eq)) {
    const it = eq[cat] ? catById(eq[cat]) : null;
    equippedData[cat] = it ? { id: it.id, category: it.category, name: it.name || null, data: it.data || null } : null;
  }
  const owned = (u.cosmetics || [])
    .map((c) => {
      const idStr = c && c.id ? String(c.id) : String(c);
      const it = catById(idStr);
      return { id: idStr, category: it ? it.category : 'title' };
    });
  const p = u.profile && typeof u.profile === 'object' ? u.profile : {};
  const isViewer = viewerId && String(viewerId) === String(u.discordId);
  const isFriend = viewerId && Array.isArray(u.friends) && u.friends.includes(String(viewerId));
  const showFull = isViewer || isFriend || !!p.public;
  const pr = store.presence[u.discordId];
  const online = !!(pr && Date.now() - (pr.timestamp || 0) <= PRESENCE_TTL_MS);
  return sendJson(res, 200, {
    id: u.id,
    discordId: u.discordId,
    name: u.name || u.discordName,
    uuid: u.uuid || null,
    code: u.code,
    online,
    server: online && pr ? pr.server : null,
    level: levelOf(u),
    equipped: equippedData,
    owned,
    bio: showFull ? p.bio || null : null,
    avatar_data_url: showFull ? p.avatar_data_url || null : null,
    banner_data_url: showFull ? p.banner_data_url || null : null,
    avatar_choice: p.avatar_choice || 'discord',
    isFriend,
    isViewer,
  });
}


function dmKey(a, b) {
  return [String(a), String(b)].sort().join(':');
}


if (pathname === '/internal/dm/send' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const fromId = String(body.from_id || '');
  const toId = String(body.to_id || '');
  const from = fromId ? store.users[fromId] : null;
  const to = toId ? store.users[toId] : null;
  if (!from || !to) return sendJson(res, 404, { error: 'user_not_found' });
  if (from.discordId === to.discordId) return sendJson(res, 400, { error: 'cannot_dm_self' });
  if (!Array.isArray(from.friends) || !from.friends.includes(to.discordId)) return sendJson(res, 403, { error: 'not_friends' });
  const text = String(body.text || '').trim().slice(0, 2000);
  if (!text) return sendJson(res, 400, { error: 'text_required' });
  store.dms = store.dms || {};
  const key = dmKey(from.discordId, to.discordId);
  store.dms[key] = store.dms[key] || [];
  const msg = { from: from.discordId, to: to.discordId, text, ts: Date.now() };
  store.dms[key].push(msg);
  if (store.dms[key].length > 500) store.dms[key] = store.dms[key].slice(-500);
  saveStore();
  return sendJson(res, 200, { ok: true, message: msg });
}


if (pathname === '/internal/dm/conversations' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const me = url.searchParams.get('discordId');
  const myUser = me ? store.users[me] : null;
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const out = [];
  for (const key of Object.keys(store.dms || {})) {
    const parts = key.split(':');
    if (!parts.includes(myUser.discordId)) continue;
    const otherDid = parts[0] === myUser.discordId ? parts[1] : parts[0];
    const other = store.users[otherDid];
    if (!other) continue;
    const msgs = store.dms[key];
    const last = msgs[msgs.length - 1];
    out.push({
      user: Object.assign(publicFriend(other), { discordId: other.discordId }),
      last: last || null,
    });
  }
  out.sort((a, b) => ((b.last && b.last.ts) || 0) - ((a.last && a.last.ts) || 0));
  return sendJson(res, 200, out);
}


if (pathname === '/internal/dm/messages' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const me = url.searchParams.get('me');
  const other = url.searchParams.get('other');
  const myUser = me ? store.users[me] : null;
  const otherUser = other ? store.users[other] : null;
  if (!myUser || !otherUser) return sendJson(res, 404, { error: 'user_not_found' });
  const key = dmKey(myUser.discordId, otherUser.discordId);
  const msgs = (store.dms[key] || []).slice(-100);
  return sendJson(res, 200, msgs);
}

// ── Internes API für die Website (Gruppen) ──────────────────────────────────

if (pathname === '/internal/groups' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const did = url.searchParams.get('discordId');
  const myUser = did ? store.users[did] : null;
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const myGroups = Object.values(store.groups || {}).filter(
    (g) => Array.isArray(g.members) && g.members.includes(myUser.discordId),
  );
  const out = myGroups.map((g) => ({
    id: g.id,
    name: g.name,
    memberCount: g.members.length,
    last: (g.messages || [])[g.messages.length - 1] || null,
  }));
  out.sort((a, b) => ((b.last && b.last.ts) || 0) - ((a.last && a.last.ts) || 0));
  return sendJson(res, 200, out);
}

if (pathname === '/internal/group/create' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const myUser = store.users[String(body.discordId || '')];
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const name = String(body.name || '').trim().slice(0, 60) || 'Gruppe';
  const members = [myUser.discordId];
  if (Array.isArray(body.memberIds)) {
    for (const raw of body.memberIds) {
      const u = resolveUser(raw);
      if (u && !members.includes(u.discordId)) members.push(u.discordId);
    }
  }
  const id = crypto.randomBytes(4).toString('hex').toUpperCase();
  store.groups = store.groups || {};
  store.groups[id] = { id, name, owner: myUser.discordId, members, messages: [], signals: [], created: Date.now() };
  saveStore();
  return sendJson(res, 200, { ok: true, data: { id, name } });
}

if (pathname === '/internal/group/view' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const did = url.searchParams.get('discordId');
  const gid = url.searchParams.get('groupId');
  const myUser = did ? store.users[did] : null;
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const g = (store.groups || {})[gid];
  if (!g || !g.members.includes(myUser.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
  return sendJson(res, 200, {
    id: g.id,
    name: g.name,
    owner: g.owner,
    members: g.members.map((dId) => Object.assign(publicFriend(store.users[dId]), { discordId: dId })).filter(Boolean),
  });
}

if (pathname === '/internal/group/poll' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const did = url.searchParams.get('discordId');
  const gid = url.searchParams.get('groupId');
  const myUser = did ? store.users[did] : null;
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const g = (store.groups || {})[gid];
  if (!g || !g.members.includes(myUser.discordId)) return sendJson(res, 200, { messages: [], signals: [] });
  const sinceMsg = parseInt(url.searchParams.get('sinceMsg') || '0', 10) || 0;
  const sinceSig = parseInt(url.searchParams.get('sinceSig') || '0', 10) || 0;
  return sendJson(res, 200, {
    messages: (g.messages || []).filter((m) => m.ts > sinceMsg),
    signals: (g.signals || []).filter((s) => s.ts > sinceSig && (s.to === myUser.discordId || s.to === '*')),
  });
}

if (pathname === '/internal/group/send' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const myUser = store.users[String(body.discordId || '')];
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const gid = String(body.groupId || '');
  const g = (store.groups || {})[gid];
  if (!g || !g.members.includes(myUser.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
  const text = String(body.text || '').trim().slice(0, 2000);
  if (!text) return sendJson(res, 400, { error: 'text_required' });
  const msg = { id: store.seq++, from: myUser.discordId, text, ts: Date.now() };
  g.messages = g.messages || [];
  g.messages.push(msg);
  if (g.messages.length > 1000) g.messages = g.messages.slice(-1000);
  saveStore();
  return sendJson(res, 200, { ok: true, message: msg });
}

if (pathname === '/internal/group/add' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const myUser = store.users[String(body.discordId || '')];
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const gid = String(body.groupId || '');
  const g = (store.groups || {})[gid];
  if (!g || !g.members.includes(myUser.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
  const u = resolveUser(body.memberId);
  if (!u) return sendJson(res, 404, { error: 'user_not_found' });
  if (!g.members.includes(u.discordId)) g.members.push(u.discordId);
  saveStore();
  return sendJson(res, 200, { ok: true });
}

if (pathname === '/internal/group/leave' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const myUser = store.users[String(body.discordId || '')];
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const gid = String(body.groupId || '');
  const g = (store.groups || {})[gid];
  if (!g || !g.members.includes(myUser.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
  g.members = g.members.filter((d) => d !== myUser.discordId);
  if (g.members.length === 0) delete store.groups[gid];
  saveStore();
  return sendJson(res, 200, { ok: true });
}

if (pathname === '/internal/group/delete' && method === 'POST') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const body = await readBody(req);
  const myUser = store.users[String(body.discordId || '')];
  if (!myUser) return sendJson(res, 404, { error: 'user_not_found' });
  const gid = String(body.groupId || '');
  const g = (store.groups || {})[gid];
  if (!g) return sendJson(res, 404, { error: 'group_not_found' });
  if (g.owner !== myUser.discordId) return sendJson(res, 403, { error: 'not_owner' });
  delete store.groups[gid];
  saveStore();
  return sendJson(res, 200, { ok: true });
}

if (pathname === '/internal/call/direct/active' && method === 'GET') {
  if (!internalAuthorized(req)) return sendJson(res, 403, { error: 'forbidden' });
  const did = url.searchParams.get('discordId');
  const myUser = did ? store.users[did] : null;
  if (!myUser) return sendJson(res, 200, []);
  ensureCalls();
  activeCallCleanup();
  const myCalls = Object.values(store.calls).filter(
    (c) => c.members.includes(myUser.discordId) && ((c.direct && c.members.length > 0) || (!c.direct && c.members.length > 1)),
  );
  return sendJson(res, 200, myCalls.map((c) => {
    const peerId = c.direct ? c.members.find((d) => d !== myUser.discordId) : null;
    return {
      callId: c.id,
      groupId: c.groupId || null,
      direct: !!c.direct,
      peer: peerId ? Object.assign(publicFriend(store.users[peerId]), { discordId: peerId }) : null,
      ts: c.ts,
    };
  }));
}


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

      const r = addFriendByCode(user, target);
      if (r.error) return sendJson(res, 400, { error: r.error });
      return sendJson(res, 200, r);
    }

    if (pathname === '/friend/requests' && method === 'GET') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      return sendJson(res, 200, incomingRequests(user));
    }

    if (pathname === '/friend/accept' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const r = acceptFriendRequest(user, String(body.from_id || ''));
      if (r.error) return sendJson(res, 400, { error: r.error });
      return sendJson(res, 200, r);
    }

    if (pathname === '/friend/decline' && method === 'POST') {
      const user = bearerUser(req);
      if (!user) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const r = declineFriendRequest(user, String(body.from_id || ''));
      return sendJson(res, 200, r);
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

    
    if (pathname === '/profile-view' && method === 'GET') {
      const viewer = bearerUser(req);
      const code = (url.searchParams.get('code') || '').toUpperCase();
      const target = code ? store.users[store.codes[code]] : null;
      if (!target) return sendJson(res, 404, { error: 'not_found' });
      ensureUserExtras(target);
      const eq = target.equipped || {};
      const equippedData = {};
      for (const cat of Object.keys(eq)) {
        const it = eq[cat] ? catById(eq[cat]) : null;
        equippedData[cat] = it ? { id: it.id, category: it.category, name: it.name || null, data: it.data || null } : null;
      }
      const p = target.profile && typeof target.profile === 'object' ? target.profile : {};
      const isViewer = viewer && String(viewer.discordId) === String(target.discordId);
      const isFriend = viewer && Array.isArray(target.friends) && target.friends.includes(String(viewer.discordId));
      const showFull = isViewer || isFriend || !!p.public;
      const pr = store.presence[target.discordId];
      const online = !!(pr && Date.now() - (pr.timestamp || 0) <= PRESENCE_TTL_MS);
      return sendJson(res, 200, {
        id: target.id,
        discordId: target.discordId,
        name: target.name || target.discordName,
        uuid: target.uuid || null,
        code: target.code,
        online,
        server: online && pr ? pr.server : null,
        level: levelOf(target),
        equipped: equippedData,
        owned: (target.cosmetics || []).map((c) => {
          const idStr = c && c.id ? String(c.id) : String(c);
          const it = catById(idStr);
          return { id: idStr, category: it ? it.category : 'title' };
        }),
        bio: showFull ? p.bio || null : null,
        avatar_data_url: showFull ? p.avatar_data_url || null : null,
        banner_data_url: showFull ? p.banner_data_url || null : null,
        avatar_choice: p.avatar_choice || 'discord',
        isFriend,
        isViewer,
      });
    }

    
    if (pathname === '/dm/conversations' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const out = [];
      for (const key of Object.keys(store.dms || {})) {
        const parts = key.split(':');
        if (!parts.includes(me.discordId)) continue;
        const otherDid = parts[0] === me.discordId ? parts[1] : parts[0];
        const other = store.users[otherDid];
        if (!other) continue;
        const msgs = store.dms[key];
        const last = msgs[msgs.length - 1];
        out.push({
          user: Object.assign(publicFriend(other), { discordId: other.discordId }),
          last: last || null,
        });
      }
      out.sort((a, b) => ((b.last && b.last.ts) || 0) - ((a.last && a.last.ts) || 0));
      return sendJson(res, 200, out);
    }

    if (pathname === '/dm/messages' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const other = url.searchParams.get('other');
      const otherUser = other ? store.users[String(other)] : null;
      if (!otherUser) return sendJson(res, 404, { error: 'user_not_found' });
      const key = dmKey(me.discordId, otherUser.discordId);
      return sendJson(res, 200, (store.dms[key] || []).slice(-100));
    }

    if (pathname === '/dm/send' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const toId = String(body.to_id || '');
      const to = toId ? store.users[toId] : null;
      if (!to) return sendJson(res, 404, { error: 'user_not_found' });
      if (me.discordId === to.discordId) return sendJson(res, 400, { error: 'cannot_dm_self' });
      if (!Array.isArray(me.friends) || !me.friends.includes(to.discordId)) return sendJson(res, 403, { error: 'not_friends' });
      const text = String(body.text || '').trim().slice(0, 2000);
      if (!text) return sendJson(res, 400, { error: 'text_required' });
      store.dms = store.dms || {};
      const key = dmKey(me.discordId, to.discordId);
      store.dms[key] = store.dms[key] || [];
      const msg = { from: me.discordId, to: to.discordId, text, ts: Date.now() };
      store.dms[key].push(msg);
      if (store.dms[key].length > 500) store.dms[key] = store.dms[key].slice(-500);
      saveStore();
      return sendJson(res, 200, { ok: true, message: msg });
    }

    
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

    // ── Gruppen ────────────────────────────────────────────────────────────
    if (pathname === '/groups' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const myGroups = Object.values(store.groups || {}).filter(
        (g) => Array.isArray(g.members) && g.members.includes(me.discordId),
      );
      const out = myGroups.map((g) => ({
        id: g.id,
        name: g.name,
        memberCount: g.members.length,
        last: (g.messages || [])[g.messages.length - 1] || null,
      }));
      out.sort((a, b) => ((b.last && b.last.ts) || 0) - ((a.last && a.last.ts) || 0));
      return sendJson(res, 200, out);
    }

    if (pathname === '/group/create' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const name = String(body.name || '').trim().slice(0, 60) || 'Gruppe';
      const members = [me.discordId];
      if (Array.isArray(body.memberIds)) {
        for (const raw of body.memberIds) {
          const u = resolveUser(raw);
          if (u && !members.includes(u.discordId)) members.push(u.discordId);
        }
      }
      const id = crypto.randomBytes(4).toString('hex').toUpperCase();
      store.groups = store.groups || {};
      store.groups[id] = {
        id,
        name,
        owner: me.discordId,
        members,
        messages: [],
        signals: [],
        created: Date.now(),
      };
      saveStore();
      return sendJson(res, 200, { id, name });
    }

    if (pathname === '/group/view' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const gid = String(url.searchParams.get('groupId') || '');
      const g = (store.groups || {})[gid];
      if (!g || !g.members.includes(me.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
      return sendJson(res, 200, {
        id: g.id,
        name: g.name,
        owner: g.owner,
        members: g.members.map((dId) => Object.assign(publicFriend(store.users[dId]), { discordId: dId })).filter(Boolean),
      });
    }

    if (pathname === '/group/add' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const gid = String(body.groupId || '');
      const g = (store.groups || {})[gid];
      if (!g || !g.members.includes(me.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
      const u = resolveUser(body.memberId);
      if (!u) return sendJson(res, 404, { error: 'user_not_found' });
      if (!g.members.includes(u.discordId)) g.members.push(u.discordId);
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/group/leave' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const gid = String(body.groupId || '');
      const g = (store.groups || {})[gid];
      if (!g || !g.members.includes(me.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
      g.members = g.members.filter((d) => d !== me.discordId);
      if (g.members.length === 0) delete store.groups[gid];
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/group/delete' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const gid = String(body.groupId || '');
      const g = (store.groups || {})[gid];
      if (!g) return sendJson(res, 404, { error: 'group_not_found' });
      if (g.owner !== me.discordId) return sendJson(res, 403, { error: 'not_owner' });
      delete store.groups[gid];
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/group/poll' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const gid = String(url.searchParams.get('groupId') || '');
      const g = (store.groups || {})[gid];
      if (!g || !g.members.includes(me.discordId)) return sendJson(res, 200, { messages: [], signals: [] });
      const sinceMsg = parseInt(url.searchParams.get('sinceMsg') || '0', 10) || 0;
      const sinceSig = parseInt(url.searchParams.get('sinceSig') || '0', 10) || 0;
      return sendJson(res, 200, {
        messages: (g.messages || []).filter((m) => m.ts > sinceMsg),
        signals: (g.signals || []).filter((s) => s.ts > sinceSig && (s.to === me.discordId || s.to === '*')),
      });
    }

    if (pathname === '/group/send' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      const body = await readBody(req);
      const gid = String(body.groupId || '');
      const g = (store.groups || {})[gid];
      if (!g || !g.members.includes(me.discordId)) return sendJson(res, 404, { error: 'group_not_found' });
      const text = String(body.text || '').trim().slice(0, 2000);
      if (!text) return sendJson(res, 400, { error: 'text_required' });
      const msg = { id: store.seq++, from: me.discordId, text, ts: Date.now() };
      g.messages = g.messages || [];
      g.messages.push(msg);
      if (g.messages.length > 1000) g.messages = g.messages.slice(-1000);
      saveStore();
      return sendJson(res, 200, { ok: true, message: msg });
    }

    // ── Anrufe (Gerüst) ────────────────────────────────────────────────────
    function ensureCalls() { store.calls = store.calls || {}; }
    function activeCallCleanup() {
      ensureCalls();
      const cutoff = Date.now() - 24 * 60 * 60 * 1000;
      for (const [cid, c] of Object.entries(store.calls)) {
        if (c.ts < cutoff || (Array.isArray(c.members) && c.members.length === 0)) delete store.calls[cid];
      }
    }

    if (pathname === '/call/open' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      activeCallCleanup();
      ensureCalls();
      const body = await readBody(req);
      const gid = String(body.groupId || '');
      const g = gid ? (store.groups || {})[gid] : null;
      if (gid && (!g || !g.members.includes(me.discordId))) return sendJson(res, 404, { error: 'group_not_found' });
      const id = crypto.randomBytes(4).toString('hex').toUpperCase();
      store.calls[id] = {
        id,
        groupId: gid || null,
        members: [me.discordId],
        signals: [],
        ts: Date.now(),
      };
      saveStore();
      return sendJson(res, 200, { callId: id, members: [me.discordId] });
    }

    if (pathname === '/call/join' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureCalls();
      const body = await readBody(req);
      const call = store.calls[String(body.callId || '')];
      if (!call) return sendJson(res, 404, { error: 'call_not_found' });
      if (!call.members.includes(me.discordId)) call.members.push(me.discordId);
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/call/leave' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureCalls();
      const body = await readBody(req);
      const call = store.calls[String(body.callId || '')];
      if (!call) return sendJson(res, 404, { error: 'call_not_found' });
      call.members = (call.members || []).filter((d) => d !== me.discordId);
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/call/signal' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureCalls();
      const body = await readBody(req);
      const call = store.calls[String(body.callId || '')];
      if (!call) return sendJson(res, 404, { error: 'call_not_found' });
      if (!call.members.includes(me.discordId)) return sendJson(res, 403, { error: 'not_in_call' });
      const sig = {
        id: store.seq++,
        from: me.discordId,
        to: body.to_id ? String(body.to_id) : '*',
        kind: String(body.kind || ''),
        data: body.data || null,
        ts: Date.now(),
      };
      call.signals = call.signals || [];
      call.signals.push(sig);
      if (call.signals.length > 200) call.signals = call.signals.slice(-200);
      saveStore();
      return sendJson(res, 200, { ok: true });
    }

    if (pathname === '/call/direct/open' && method === 'POST') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      activeCallCleanup();
      ensureCalls();
      const body = await readBody(req);
      const peerId = String(body.peerId || '');
      const peer = store.users[peerId];
      if (!peer) return sendJson(res, 404, { error: 'peer_not_found' });
      const key = dmKey(me.discordId, peer.discordId);
      let existing = Object.values(store.calls).find(
        (c) => c.directKey === key && c.direct && c.members.includes(me.discordId),
      );
      if (existing) return sendJson(res, 200, { callId: existing.id, peer: Object.assign(publicFriend(peer), { discordId: peer.discordId }) });
      const id = crypto.randomBytes(4).toString('hex').toUpperCase();
      store.calls[id] = {
        id,
        directKey: key,
        direct: true,
        members: [me.discordId, peer.discordId],
        signals: [],
        ts: Date.now(),
      };
      saveStore();
      return sendJson(res, 200, { callId: id, peer: Object.assign(publicFriend(peer), { discordId: peer.discordId }) });
    }

    if (pathname === '/call/direct/poll' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      ensureCalls();
      const cid = String(url.searchParams.get('callId') || '');
      const call = store.calls[cid];
      if (!call || !call.members.includes(me.discordId)) return sendJson(res, 200, { signals: [], members: [] });
      const sinceSig = parseInt(url.searchParams.get('sinceSig') || '0', 10) || 0;
      const sigs = (call.signals || []).filter(
        (s) => s.ts > sinceSig && (s.to === me.discordId || s.to === '*'),
      );
      return sendJson(res, 200, { signals: sigs, members: call.members });
    }

    if (pathname === '/call/direct/active' && method === 'GET') {
      const me = bearerUser(req);
      if (!me) return sendJson(res, 401, { error: 'not_authenticated' });
      activeCallCleanup();
      ensureCalls();
      const myCalls = Object.values(store.calls).filter(
        (c) => c.members.includes(me.discordId) && ((c.direct && c.members.length > 0) || (!c.direct && c.members.length > 1)),
      );
      return sendJson(res, 200, myCalls.map((c) => {
        const peerId = c.direct ? c.members.find((d) => d !== me.discordId) : null;
        return {
          callId: c.id,
          groupId: c.groupId || null,
          direct: !!c.direct,
          peer: peerId ? Object.assign(publicFriend(store.users[peerId]), { discordId: peerId }) : null,
          ts: c.ts,
        };
      }));
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
