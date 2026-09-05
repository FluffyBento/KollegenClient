// Kollegen.me – Profil-/Store-/Freunde-Modul (Website)
// =====================================================
// Ergänzt die Website um:
//   GET  /profil             – self-contained Profil-Seite (HTML, Steam-inspiriert)
//   GET  /store              – self-contained Kosmetik-Store (Kollegen-Points)
//   GET  /freunde            – self-contained Freunde-Seite
//   GET  /api/profil/me      – eigene Profil-/MC-Daten ({ user, mcName, uuid, code, profile, points, level, equipped, cosmetics })
//   GET  /api/profil/store   – Katalog + eigener Kontostand/Owned/Equipped
//   POST /api/profil/buy     – Kosmetik kaufen (Points abziehen)
//   POST /api/profil/equip   – Kosmetik ausrüsten/ablegen
//   GET  /api/profil/friends – eigene Freundesliste
//   POST /api/profil/friend-add, /api/profil/friend-remove
//   GET  /api/profil/uuid?name=… – MC-Name → UUID Proxy (CORS-sauber)
//   POST /api/profil/save    – Profil + MC-Identität ins Backend-Bridge schreiben
// und injiziert in der SPA oben eine Steam-artige Navigations-Leiste
// (kollegen.me · Profil · Store · Freunde) inklusive Points-Chip.
//
// Einbindung in server.js VOR den statischen/SPA-Fallbacks:
//   require(path.join(__dirname, 'profil.js'))(app, getSession);
// (getSession ist die bestehende Website-Session-Funktion aus server.js.)
//
// Auth zum Backend läuft Server-zu-Server über KOLLEGEN_INTERNAL_SECRET
// (muss per Umgebung/EnvironmentFile auf Website- UND Backend-Prozess stehen).

'use strict';

const fs = require('fs');
const path = require('path');

function loadInternalSecret() {
  if (process.env.KOLLEGEN_INTERNAL_SECRET) return process.env.KOLLEGEN_INTERNAL_SECRET;
  try {
    const raw = fs.readFileSync('/etc/kollegen_internal.env', 'utf8');
    const m = /^KOLLEGEN_INTERNAL_SECRET\s*=\s*(.+)\s*$/m.exec(raw);
    if (m) return m[1].trim();
  } catch (_) {}
  return '';
}

// ── Gestaltung (Steam-angelehnt) ───────────────────────────────────────────
const NAV_CSS =
  '.km-nav{position:fixed;top:14px;left:50%;transform:translateX(-50%);z-index:9999;display:flex;' +
  'align-items:center;gap:4px;background:rgba(11,13,20,.85);border:1px solid rgba(212,175,55,.35);' +
  'border-radius:999px;padding:5px 8px;backdrop-filter:blur(8px);box-shadow:0 6px 24px rgba(0,0,0,.55);' +
  'white-space:nowrap;max-width:94vw;overflow-x:auto;scrollbar-width:none;}' +
  '.km-nav::-webkit-scrollbar{display:none;}' +
  '.km-nav a{color:#f2f3f5;text-decoration:none;font:600 13px/1 Inter,Outfit,sans-serif;' +
  'padding:7px 12px;border-radius:999px;transition:background .15s,color .15s;flex:none;}' +
  '.km-nav a.on{background:rgba(212,175,55,.24);color:#ffd75f;}' +
  '.km-nav a:hover{background:rgba(255,255,255,.09);}' +
  '.km-nav .km-avatar{width:26px;height:26px;border-radius:50%;object-fit:cover;background:#222;' +
  'vertical-align:middle;margin-right:4px;}' +
  '.km-nav .km-pts{display:inline-flex;align-items:center;gap:6px;color:#ffd75f;' +
  'font:800 13px/1 Inter,sans-serif;padding:7px 12px;flex:none;border-left:1px solid rgba(255,255,255,.12);}' +
  '@media (max-width:640px){.km-nav a span.km-lbl{display:none;}}';

function navHtml(current) {
  function a(href, label, active, extra) {
    const on = active ? ' on' : '';
    return '<a class="km-link' + on + '" href="' + href + '"' + (extra || '') + '>' + label + '</a>';
  }
  return (
    '<nav class="km-nav" id="kmNav">' +
    a('/', '&#9656; kollegen.me', current === '/') +
    a('/profil', '<img class="km-avatar" id="kmNavAvatar" alt="" />' + '<span class="km-lbl">Profil</span>', current === '/profil') +
    a('/store', '<span class="km-lbl">Store</span>', current === '/store') +
    a('/freunde', '<span class="km-lbl">Freunde</span>', current === '/freunde') +
    '<span class="km-pts" id="kmNavPts" style="display:none;">&#9733; <span id="kmNavPtsVal">0</span></span>' +
    '</nav>'
  );
}

const NAV_SCRIPT =
  '(function(){' +
  'function kmReady(fn){if(document.readyState!=="loading")fn();else document.addEventListener("DOMContentLoaded",fn);}' +
  'kmReady(function(){' +
  'var pts=document.getElementById("kmNavPts");var pv=document.getElementById("kmNavPtsVal");' +
  'var av=document.getElementById("kmNavAvatar");' +
  'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
  'if(!j||!j.user){if(av)av.style.display="none";return;}' +
  'if(pts)pts.style.display="";' +
  'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
  'if(!p)return;' +
  'if(pv&&typeof p.points==="number")pv.textContent=p.points;' +
  'if(av){var head=null;' +
  'if(p.mcName){head="https://mc-heads.net/avatar/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/128";}' +
  'else if(j.user&&j.user.avatarUrl){head=j.user.avatarUrl;}' +
  'if(head){av.src=head;}else{av.style.display="none";}' +
  '}' +
  '}).catch(function(){});' +
  '}).catch(function(){});' +
  '});' +
  '})();';

function pageShell(title, cssExtra, bodyInner) {
  return (
    '<!DOCTYPE html><html lang="de"><head><meta charset="utf-8"/>' +
    '<meta name="viewport" content="width=device-width,initial-scale=1"/>' +
    '<title>' + title + ' &middot; kollegen.me</title>' +
    '<style>' + NAV_CSS + cssExtra + '</style></head><body>' + bodyInner + '</body></html>'
  );
}

module.exports = function registerProfilModule(app, getSession) {
  const BACKEND = process.env.KOLLEGEN_BACKEND_URL || 'http://127.0.0.1:8080';
  const SECRET = loadInternalSecret();
  const INDEX_PATH = path.join(__dirname, 'dist', 'index.html');

  async function backendInternal(method, urlPath, body) {
    if (!SECRET) return { ok: false, error: 'internal secret missing (KOLLEGEN_INTERNAL_SECRET)' };
    const headers = { 'X-Kollegen-Internal': SECRET };
    let res;
    try {
      if (method === 'GET') {
        res = await fetch(BACKEND + urlPath, { headers });
      } else {
        headers['Content-Type'] = 'application/json';
        res = await fetch(BACKEND + urlPath, { method, headers, body: JSON.stringify(body || {}) });
      }
    } catch (e) {
      return { ok: false, error: 'backend unreachable: ' + e.message };
    }
    if (!res.ok) {
      const txt = await res.text();
      return { ok: false, error: 'backend ' + res.status + ': ' + txt.slice(0, 300) };
    }
    try {
      return { ok: true, data: await res.json() };
    } catch (_) {
      return { ok: true, data: null };
    }
  }

  // MC-Name → UUID (Browser-Proxy)
  app.get('/api/profil/uuid', async (req, res) => {
    const name = String(req.query.name || '').trim();
    if (!name || !/^[A-Za-z0-9_]{1,16}$/.test(name)) {
      return res.json({ error: 'invalid_name' });
    }
    try {
      const r = await fetch('https://api.mojang.com/users/profiles/minecraft/' + encodeURIComponent(name));
      if (!r.ok) return res.json({ error: 'not_found' });
      const j = await r.json();
      if (!j || !j.id) return res.json({ error: 'not_found' });
      return res.json({ uuid: j.id, name: j.name || name });
    } catch (_) {
      return res.json({ error: 'lookup_failed' });
    }
  });

  // Eigene Profil-/MC-Daten (über getSession aus server.js)
  app.get('/api/profil/me', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.json({ user: null });
    const r = await backendInternal('GET', '/internal/user?discordId=' + encodeURIComponent(String(session.id)));
    const data = r.ok && r.data ? r.data : {};
    return res.json({
      user: {
        id: session.id,
        username: session.username || '',
        global_name: session.global_name || session.username || '',
        avatarUrl: session.avatarUrl || '',
      },
      discordId: String(session.id),
      mcName: data.mc_name || null,
      uuid: data.uuid || null,
      code: data.code || null,
      profile: data.profile || null,
      points: typeof data.points === 'number' ? data.points : null,
      points_total: typeof data.points_total === 'number' ? data.points_total : null,
      level: typeof data.level === 'number' ? data.level : null,
      equipped: (data.equipped && typeof data.equipped === 'object') ? data.equipped : {},
      cosmetics: Array.isArray(data.cosmetics) ? data.cosmetics : [],
    });
  });

  // Profil speichern (Website-Session → Backend-Bridge)
  app.post('/api/profil/save', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const mcName = String(body.mcName || '').trim();
    const r = await backendInternal('POST', '/internal/profile', {
      discordId: String(session.id),
      discordName: session.global_name || session.username,
      mcName: mcName || undefined,
      uuid: body.uuid ? String(body.uuid) : undefined,
      profile: {
        bio: typeof body.bio === 'string' ? body.bio : '',
        avatar_data_url: typeof body.avatar_data_url === 'string' ? body.avatar_data_url : '',
        banner_data_url: typeof body.banner_data_url === 'string' ? body.banner_data_url : '',
        avatar_choice: typeof body.avatar_choice === 'string' ? body.avatar_choice : 'discord',
        public: body.public === true,
        server_url: '',
        server_token: '',
      },
    });
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json({ ok: true });
  });

  // Store: Katalog + eigener Kontostand (über discordId + Secret)
  app.get('/api/profil/store', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    const q = session ? '?discordId=' + encodeURIComponent(String(session.id)) : '';
    const r = await backendInternal('GET', '/store' + q);
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(r.data);
  });

  // Kosmetik kaufen
  app.post('/api/profil/buy', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const itemId = String(body.item_id || '').trim();
    if (!itemId) return res.status(400).json({ error: 'item_id_required' });
    const r = await backendInternal('POST', '/internal/store-buy', {
      discordId: String(session.id),
      item_id: itemId,
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // Kosmetik ausrüsten/ablegen
  app.post('/api/profil/equip', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const payload = { discordId: String(session.id) };
    if (body.item_id) payload.item_id = String(body.item_id);
    else if (body.category) payload.category = String(body.category);
    else if (body.all) payload.all = true;
    const r = await backendInternal('POST', '/internal/store-equip', payload);
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // Freundesliste
  app.get('/api/profil/friends', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const r = await backendInternal('GET', '/internal/friends?discordId=' + encodeURIComponent(String(session.id)));
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(Array.isArray(r.data) ? r.data : []);
  });

  // Freund per Code hinzufügen
  app.post('/api/profil/friend-add', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/friend-add', {
      discordId: String(session.id),
      code: String(body.code || '').trim().toUpperCase(),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // Freund entfernen (target = id | code | discordId)
  app.post('/api/profil/friend-remove', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/friend-remove', {
      discordId: String(session.id),
      target_id: String(body.target_id || ''),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // ── Seiten ──
  const PAGES = {
    '/profil': buildProfilPage(),
    '/store': buildStorePage(),
    '/freunde': buildFreundePage(),
  };

  // ── SPA: Navigations-Leiste injizieren ──
  let cachedIndex = null;
  function injectedIndex() {
    if (cachedIndex) return cachedIndex;
    let html;
    try {
      html = fs.readFileSync(INDEX_PATH, 'utf8');
    } catch (_) {
      return null;
    }
    const nav =
      '<style>' + NAV_CSS + '</style>' + navHtml('') +
      '<script>' + NAV_SCRIPT + '</' + 'script>';
    html = html.replace('</body>', nav + '</body>');
    cachedIndex = html;
    return html;
  }

  // HTML-Navigationen abfangen, Assets (css/js/img) durchreichen
  app.use((req, res, next) => {
    if (req.method !== 'GET') return next();
    const urlPath = (req.url || '').split('?')[0];

    if (PAGES[urlPath]) {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      return res.send(PAGES[urlPath]);
    }

    const ext = path.extname(urlPath).toLowerCase();
    if (ext && ext !== '.html') return next();
    if (urlPath.startsWith('/api/')) return next();
    const acceptsHtml = (req.headers.accept || '').includes('text/html');
    if (urlPath === '/' || ext === '.html' || acceptsHtml) {
      const injected = injectedIndex();
      if (injected) {
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        return res.send(injected);
      }
    }
    return next();
  });
};

// ── Gemeinsame Hilfen für die inline-Seiten-Skripte ─────────────────────────
const SHARED_CSS =
  'body{background:#0a0d13;color:#f2f3f5;font-family:Inter,"Segoe UI",Arial,sans-serif;' +
  'margin:0;padding:5.2rem 1rem 3rem;display:flex;justify-content:center;min-height:100vh;box-sizing:border-box;}' +
  '#wrap{max-width:860px;width:100%;}' +
  'h1{font-family:Outfit,Inter,sans-serif;color:#D4AF37;margin:0 0 .2rem;font-size:1.6rem;}' +
  '.sub{color:#9aa3af;font-size:.9rem;margin-bottom:1.2rem;}' +
  '.card{background:#121826;border:1px solid #232e42;border-radius:14px;padding:1.2rem;margin-top:1rem;}' +
  'label{display:block;color:#a9b3c0;font-size:.85rem;margin:1rem 0 .3rem;}' +
  'input,textarea,select{width:100%;box-sizing:border-box;background:#0d1420;border:1px solid #2a3749;' +
  'color:#f2f3f5;padding:.6rem;border-radius:8px;font-size:.95rem;}' +
  'textarea{resize:vertical;min-height:70px;}' +
  'button{background:#D4AF37;color:#0a0d13;font-weight:700;border:0;padding:.65rem 1.3rem;border-radius:8px;' +
  'cursor:pointer;margin-top:1rem;font-size:.95rem;transition:filter .15s;}' +
  'button:hover{filter:brightness(1.1);}' +
  'button.secondary{background:#2a3749;color:#f2f3f5;margin-left:.5rem;}' +
  'button.ghost{background:transparent;border:1px solid #3c4a60;color:#d7dee8;}' +
  '.row{display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;}' +
  '.muted{color:#8f9aab;font-size:.85rem;} .good{color:#7ee787;} .bad{color:#f85149;}' +
  'a{color:#8ab4ff;text-decoration:none;} a:hover{text-decoration:underline;}' +
  '.badge-medal{font:800 11px/1 Inter,sans-serif;letter-spacing:.04em;border-radius:999px;padding:3px 9px;}' +
  '.loginCard{display:none;}' +
  '.loginCard button{background:#5865F2;}' +
  '.ptsChip{display:inline-flex;align-items:center;gap:6px;background:linear-gradient(135deg,#3a3020,#241c10);' +
  'border:1px solid #6b5627;color:#ffd75f;font:800 14px/1 Inter,sans-serif;padding:8px 14px;border-radius:999px;}' +
  '.lvlChip{display:inline-flex;align-items:center;gap:6px;background:#1a2333;border:1px solid #2f3d55;' +
  'color:#c9d4e3;font:700 13px/1 Inter,sans-serif;padding:8px 12px;border-radius:999px;}';

// ── Profilseite ─────────────────────────────────────────────────────────────
function buildProfilPage() {
  const css = SHARED_CSS +
    '.profileRow{display:flex;gap:1rem;align-items:center;}' +
    '.headImg{width:84px;height:84px;border-radius:16px;object-fit:cover;background:#222;flex:none;}' +
    '.equipList{display:flex;flex-wrap:wrap;gap:.6rem;margin-top:.3rem;}' +
    '.equipItem{display:flex;align-items:center;gap:.5rem;background:#0d1420;border:1px solid #26344a;' +
    'border-radius:10px;padding:.45rem .7rem;font-size:.85rem;color:#e3e9f2;}' +
    '.equipItem .swatch{width:18px;height:18px;border-radius:5px;flex:none;}' +
    '.bar{height:8px;border-radius:999px;background:#1a2333;overflow:hidden;margin-top:.4rem;}' +
    '.bar > div{height:100%;background:linear-gradient(90deg,#D4AF37,#ffd75f);border-radius:999px;}' +
    '.progressLabel{display:flex;justify-content:space-between;font-size:.75rem;color:#8f9aab;margin-top:.25rem;}';

  const html =
    '<nav class="km-nav" id="kmNav">' +
    '<a class="km-link" href="/">&#9656; kollegen.me</a>' +
    '<a class="km-link on" href="/profil"><img class="km-avatar" id="kmNavAvatar" alt="" /><span class="km-lbl">Profil</span></a>' +
    '<a class="km-link" href="/store"><span class="km-lbl">Store</span></a>' +
    '<a class="km-link" href="/freunde"><span class="km-lbl">Freunde</span></a>' +
    '<span class="km-pts" id="kmNavPts" style="display:none;">&#9733; <span id="kmNavPtsVal">0</span></span>' +
    '</nav>' +
    '<div id="wrap">' +
    '<h1>Profil &amp; Minecraft</h1>' +
    '<div class="sub">Dein öffentliches Kollegen-Profil – mit Sternen-Store und Skins.</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an – dein Minecraft-Profil wird automatisch übernommen, sobald du im Kollegen-Launcher angemeldet bist.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="card" id="meCard" style="display:none;">' +
    '<div class="profileRow">' +
    '<img id="headImg" class="headImg" alt=""/>' +
    '<div style="flex:1;min-width:140px;">' +
    '<div id="meName" style="font-weight:700;font-size:1.15rem;"></div>' +
    '<div class="muted" id="meDiscord"></div>' +
    '<div class="muted" id="meCode"></div>' +
    '</div>' +
    '<div id="ptsWrap" style="display:flex;flex-direction:column;gap:.5rem;align-items:flex-end;"></div>' +
    '</div>' +
    '<label for="mcName">Minecraft-Name (verknüpfen)</label>' +
    '<div class="row"><input id="mcName" placeholder="z. B. FluffyBento" style="flex:1;width:auto;"/><button type="button" class="secondary" id="lookupBtn">Skin laden</button></div>' +
    '<div id="skinPreview" style="margin-top:.6rem;"></div>' +
    '<label for="avatarChoice">Avatar</label>' +
    '<select id="avatarChoice"><option value="discord">Discord-Avatar</option><option value="minecraft">Minecraft-Head</option></select>' +
    '<label for="bioValue">Bio</label><textarea id="bioValue" placeholder="Beschreibe dich in ein paar Worten..."></textarea>' +
    '<label for="bannerValue">Banner-URL (optional)</label><input id="bannerValue" placeholder="https://…/banner.png"/>' +
    '<label style="display:flex;align-items:center;gap:.5rem;"><input type="checkbox" id="pubToggle" style="width:auto;"/> Öffentliches Profil (für andere sichtbar)</label>' +
    '<div class="muted" id="saveHint" style="margin-top:.6rem;"></div>' +
    '<div class="row"><button type="button" id="saveBtn">Speichern</button>' +
    '<a href="/store"><button type="button" class="ghost">Zum Kosmetik-Store</button></a></div>' +
    '</div>' +
    '<div class="card" id="equipCard" style="display:none;">' +
    '<h1 style="font-size:1.1rem;">Ausgerüstete Kosmetik</h1>' +
    '<p class="muted">Diese Items siehst du in Profilen und der Freunde-Liste. Ausrüsten/ablegen kannst du im Store.</p>' +
    '<div class="equipList" id="equipList"></div>' +
    '<a href="/store"><button type="button" class="ghost">Im Store anpassen</button></a>' +
    '</div>' +
    '<p class="sub" style="margin-top:1.2rem;">Hinweis: Der direkte Minecraft-Login (Microsoft/OAuth) benötigt eine eigene App-Registrierung – bis dahin wird dein Skin über den Namen geladen. Gemeinsame Freunde im Launcher übernehmen den Skin automatisch.</p>' +
    '</div>' +
    '<script>' + NAV_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var me=$("meCard"),login=$("loginCard");' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'if(!j||!j.user){login.style.display="block";return;}' +
    'var u=j.user;' +
    '$("meName").textContent=u.global_name||u.username;' +
    '$("meDiscord").textContent="@"+u.username+" (Discord)";' +
    'me.style.display="block";' +
    'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
    'if(p&&p.code){$("meCode").textContent="Freundes-Code: "+p.code;}' +
    'if(p&&p.mcName){' +
    '$("mcName").value=p.mcName;window._mcName=p.mcName;window._mcUuid=p.uuid||null;' +
    '$("headImg").src="https://mc-heads.net/head/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/256";' +
    'loadBody(p.mcName);' +
    'window._skipHeadAvatar=true;' +
    '}' +
    'else if(u.avatarUrl){$("headImg").src=u.avatarUrl;}' +
    'if(p&&!p.mcName){' +
    '$("saveHint").textContent="Noch kein Minecraft-Profil verkn\u00fcpft. Tipp: Melde dich im Kollegen-Launcher mit demselben Discord-Konto an und starte einmal das Spiel \u2013 dann erscheint dein Name + Skin hier automatisch.";' +
    '}' +
    'if(p&&p.profile){var pr=p.profile;' +
    'if(pr.bio)$("bioValue").value=pr.bio;' +
    'if(pr.banner_data_url)$("bannerValue").value=pr.banner_data_url;' +
    'if(pr.avatar_choice)$("avatarChoice").value=pr.avatar_choice;' +
    'if(pr.public)$("pubToggle").checked=true;' +
    '}' +
    'if(p&&typeof p.points==="number"){renderPts(p);}' +
    '}).catch(function(){});' +
    '}).catch(function(){login.style.display="block";});' +
    'function renderPts(p){' +
    'var w=$("ptsWrap");w.innerHTML="";' +
    'var c=document.createElement("div");c.className="ptsChip";c.textContent="\u2605 "+p.points;' +
    'var l=document.createElement("div");l.className="lvlChip";l.textContent="Level "+p.level;' +
    'var t=(typeof p.points_total==="number")?p.points_total:0;var next=300-((t-1)%300)-1;' +
    'var prog=document.createElement("div");' +
    'prog.innerHTML="<div class=\\"bar\\"><div style=\\"width:"+(((t-1)%300)+1)+"%;\\"></div></div>"+' +
    '"<div class=\\"progressLabel\\"><span>"+p.level+" \u2192 "+(p.level+1)+"</span><span>"+next+" Punkte bis Level "+(p.level+1)+"</span></div>";' +
    'w.append(c,l,prog);' +
    'renderEquip(p);' +
    '}' +
    'function byId(id){var c=window._kmCat||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function renderEquip(p){' +
    'var list=$("equipList");if(!list)return;' +
    'fetch("/api/profil/store").then(function(r){return r.json();}).then(function(st){' +
    'window._kmCat=st.catalog||[];' +
    'var eq=p.equipped||{};var names=[];' +
    'for(var k in eq){if(!eq[k])continue;var it=byId(eq[k]);if(it){' +
    'var el=document.createElement("div");el.className="equipItem";' +
    'var sw=document.createElement("span");sw.className="swatch";' +
    'if(it.category==="avatar_frame"&&it.data.color1){sw.style.background=it.data.color1;}' +
    'else if(it.category==="title"){sw.style.background="#3a3f4e";sw.textContent=it.data.text;}else if(it.category==="avatar_theme"){sw.style.background="linear-gradient(135deg,#3a4a6b,#1c1c28)";}' +
    'el.append(sw);el.append(document.createTextNode(it.name));' +
    'list.append(el);' +
    '}'+
    '}'+
    '$("equipCard").style.display="block";' +
    '}).catch(function(){});' +
    '}' +
    'function loadBody(nm){' +
    'var wrap=$("skinPreview");wrap.innerHTML="";' +
    'var im=document.createElement("img");' +
    'im.alt="Skin";im.style.maxWidth="180px";' +
    'im.src="https://mc-heads.net/body/"+encodeURIComponent(nm).replace(/%20/g,"_")+"/full.png";' +
    'im.onerror=function(){im.style.display="none";};' +
    'wrap.append(im);' +
    '}' +
    '$("lookupBtn").addEventListener("click",doLookup);' +
    '$("mcName").addEventListener("keydown",function(e){if(e.key==="Enter")doLookup();});' +
    'function doLookup(){' +
    'var nm=$("mcName").value.trim();if(!nm)return;' +
    '$("saveHint").textContent="Lade Skin\u2026";' +
    'fetch("/api/profil/uuid?name="+encodeURIComponent(nm)).then(function(r){return r.json();}).then(function(j){' +
    'if(j.error){$("saveHint").textContent="Name nicht gefunden.";return;}' +
    'window._mcName=j.name||nm;window._mcUuid=j.uuid;' +
    '$("headImg").src="https://mc-heads.net/head/"+encodeURIComponent(window._mcName).replace(/%20/g,"_")+"/256";' +
    'loadBody(window._mcName);' +
    '$("saveHint").textContent="Skin von "+window._mcName+" geladen \u2013 speichern, um zu \u00fcbernehmen.";' +
    '}).catch(function(){ $("saveHint").textContent="Fehler beim Skin-Laden."; });' +
    '}' +
    '$("saveBtn").addEventListener("click",function(){' +
    'var body={mcName:window._mcName||$("mcName").value.trim(),uuid:window._mcUuid||null,' +
    'bio:$("bioValue").value.trim(),avatar_choice:$("avatarChoice").value,' +
    'banner_data_url:$("bannerValue").value.trim(),public:$("pubToggle").checked};' +
    'var head=$("headImg").src;' +
    'if(body.avatar_choice==="minecraft"){body.avatar_data_url=head||"";}' +
    'else{body.avatar_data_url="";}' +
    '$("saveBtn").disabled=true;$("saveBtn").textContent="Speichern\u2026";' +
    'fetch("/api/profil/save",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){$("saveHint").textContent="Gespeichert \u2713";}' +
    'else{$("saveHint").textContent="Fehler: "+((j&&j.error)||"unbekannt");}' +
    '$("saveBtn").disabled=false;$("saveBtn").textContent="Speichern";' +
    '}).catch(function(){ $("saveHint").textContent="Fehler beim Speichern.";$("saveBtn").disabled=false;$("saveBtn").textContent="Speichern"; });' +
    '});' +
    '})();' +
    '</script>';

  return pageShell('Profil', css, html);
}

// ── Store-Seite (Kollegen-Points) ───────────────────────────────────────────
function buildStorePage() {
  const css = SHARED_CSS +
    '.storeHead{display:flex;align-items:center;gap:.8rem;flex-wrap:wrap;justify-content:space-between;}' +
    '.tabs{display:flex;gap:.4rem;flex-wrap:wrap;margin:1rem 0 .4rem;}' +
    '.tab{background:#121826;border:1px solid #26334a;color:#c6cfdb;font:600 13px/1 Inter,sans-serif;' +
    'padding:7px 14px;border-radius:999px;cursor:pointer;transition:all .15s;}' +
    '.tab.on{background:#D4AF37;border-color:#D4AF37;color:#0a0d13;}' +
    '.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(180px,1fr));gap:1rem;margin-top:1rem;}' +
    '.card{display:flex;flex-direction:column;margin:0;position:relative;transition:transform .15s,border-color .15s;}' +
    '.card:hover{transform:translateY(-2px);border-color:#3c4a63;}' +
    '.card.equipped{border-color:#6b5627;}' +
    '.card.owned{opacity:.92;}' +
    '.pv{height:112px;border-radius:10px;display:flex;align-items:center;justify-content:center;' +
    'background:#0d1420;border:1px solid #1c2636;overflow:hidden;}' +
    '.pvHead{width:64px;height:64px;border-radius:14px;object-fit:cover;background:#151d2b;}' +
    '.pvTitle{font:800 15px/1 Outfit,Inter,sans-serif;color:#ffd75f;}' +
    '.pvBadgeTxt{color:#0a0d13;font-weight:800;}' +
    '.rar{position:absolute;top:10px;right:10px;}' +
    '.r-common{background:#2b3547;color:#b8c2d0;}.r-rare{background:#123a5c;color:#5aa9ff;}' +
    '.r-epic{background:#3b1e5c;color:#c96bff;}.r-legendary{background:#5c3e12;color:#ffb64d;}' +
    '.cardBody{padding:.7rem .2rem 0;flex:1;display:flex;flex-direction:column;}' +
    '.cardTitle{font-weight:700;font-size:.98rem;}' +
    '.cardDesc{color:#8f9aab;font-size:.8rem;margin:.25rem 0 .5rem;flex:1;}' +
    '.price{display:flex;align-items:center;gap:5px;color:#ffd75f;font-weight:800;}' +
    '.cardBtn{width:100%;text-align:center;margin-top:.55rem;padding:.5rem;}' +
    '.ownedTag{color:#7ee787;font-weight:800;font-size:.82rem;text-align:center;margin-top:.55rem;}' +
    '.note{display:flex;gap:.5rem;align-items:flex-start;margin:1rem 0 0;}' +
    '.backLink{margin-top:1.4rem;display:inline-block;font-size:.85rem;}';

  const html =
    '<nav class="km-nav" id="kmNav">' +
    '<a class="km-link" href="/">&#9656; kollegen.me</a>' +
    '<a class="km-link" href="/profil"><img class="km-avatar" id="kmNavAvatar" alt="" /><span class="km-lbl">Profil</span></a>' +
    '<a class="km-link on" href="/store"><span class="km-lbl">Store</span></a>' +
    '<a class="km-link" href="/freunde"><span class="km-lbl">Freunde</span></a>' +
    '<span class="km-pts" id="kmNavPts" style="display:none;">&#9733; <span id="kmNavPtsVal">0</span></span>' +
    '</nav>' +
    '<div id="wrap">' +
    '<div class="storeHead">' +
    '<div><h1>Kosmetik-Store</h1>' +
    '<div class="sub">Austattung für dein Profil – gekauft mit Kollegen-Points (später eintauschbar).</div></div>' +
    '<div class="row" id="walletRow"><span class="ptsChip" id="walletPts" style="display:none;">&#9733; 0</span>' +
    '<span class="lvlChip" id="walletLvl" style="display:none;">Level 1</span></div>' +
    '</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich an, um Kosmetik zu kaufen und auszurüsten. Bereits im Launcher angemeldet? Dann bist du automatisch auch hier angemeldet.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="tabs" id="tabs">' +
    '<button type="button" class="tab on" data-f="alle">Alle</button>' +
    '<button type="button" class="tab" data-f="title">Titel</button>' +
    '<button type="button" class="tab" data-f="avatar_frame">Rahmen</button>' +
    '<button type="button" class="tab" data-f="avatar_theme">Hintergründe</button>' +
    '<button type="button" class="tab" data-f="badge">Abzeichen</button>' +
    '</div>' +
    '<div class="grid" id="grid"><div class="muted" id="gridMsg">Lade Store…</div></div>' +
    '<div class="note card"><span style="font-size:1.2rem;">&#9432;</span>' +
    '<span class="muted">Kollegen-Points bekommst du als Startguthaben und später durch Aktivität dazu. Der Punkte-Tausch (Redeem) in echte Vorteile ist geplant – aktuell kannst du mit deinen Punkten ausschließlich Kosmetik kaufen.</span></div>' +
    '<a class="backLink" href="/">&#8592; Zur Startseite</a>' +
    '</div>' +
    '<script>' + NAV_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var st=null,filter="alle";' +
    'var RAR={common:["Gew\u00f6hnlich","r-common"],rare:["Selten","r-rare"],epic:["Episch","r-epic"],legendary:["Legend\u00e4r","r-legendary"]};' +
    'function byId(id){var c=(st&&st.catalog)||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'window._av="https://mc-heads.net/avatar/MHF_Steve/64";' +
    'if(j&&j.user){fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
    'if(p&&p.mcName){window._av="https://mc-heads.net/avatar/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/96";}' +
    'else if(j.user&&j.user.avatarUrl){window._av=j.user.avatarUrl;}' +
    'load();' +
    '}).catch(function(){load();});}' +
    'else{load();}' +
    '}).catch(function(){load();});' +
    'function load(){' +
    'fetch("/api/profil/store").then(function(r){return r.json();}).then(function(d){' +
    'if(d.error){$("gridMsg").textContent="Store-Tempor\u00e4r nicht erreichbar. Bitte gleich nochmal versuchen.";return;}' +
    'st=d;' +
    'if(d.needsAuth){$("loginCard").style.display="block";}' +
    'else{$("walletPts").style.display="";$("walletPts").textContent="\u2605 "+d.points;' +
    'if(typeof d.level==="number"){$("walletLvl").style.display="";$("walletLvl").textContent="Level "+d.level;}}' +
    'render();' +
    '}).catch(function(){ $("gridMsg").textContent="Store-Tempor\u00e4r nicht erreichbar."; });' +
    '}' +
    'function preview(item){' +
    'var av=window._av||"https://mc-heads.net/avatar/MHF_Steve/64";' +
    'if(item.category==="title"){return "<span class=\\"pvTitle\\">"+esc(item.data.text)+"</span>";}' +
    'if(item.category==="badge"){return "<span class=\\"pvBadgeTxt\\" style=\\"font-size:30px;color:"+esc(item.data.color)+"\\">"+esc(item.data.icon)+"</span>";}' +
    'if(item.category==="avatar_theme"){return "<div style=\\"width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:"+esc(item.data.gradient)+"\\"><img class=\\"pvHead\\" src=\\""+av+"\\"/></div>";}' +
    'var border="",shadow="";' +
    'if(item.category==="avatar_frame"&&item.data.color1){border="border:"+(item.data.width||3)+"px solid "+item.data.color1+";";shadow="box-shadow:0 0 14px "+item.data.color1+"55;";}' +
    'return "<img class=\\"pvHead\\" style=\\""+border+shadow+"border-radius:14px;\\" src=\\""+av+"\\"/>";' +
    '}' +
    'function render(){' +
    'var grid=$("grid");grid.innerHTML="";' +
    'var items=(st.catalog||[]).filter(function(i){return filter==="alle"||i.category===filter;});' +
    'if(!items.length){grid.innerHTML="<div class=\\"muted\\">Keine Items in dieser Kategorie.</div>";return;}' +
    'var logged=!st.needsAuth;' +
    'items.forEach(function(item){' +
    'var card=document.createElement("div");card.className="card";' +
    'var rar=RAR[item.rarity]||["","r-common"];' +
    'var owned=logged&&item.owned?1:0;' +
    'var equipped=logged&&item.equippedCategory?1:0;' +
    'if(equipped)card.className+=" equipped";if(owned)card.className+=" owned";' +
    'var html="";' +
    'html+="<div class=\\"pv\\">"+preview(item)+"</div>";' +
    'html+="<span class=\\"badge-medal rar "+rar[1]+"\\">"+rar[0]+"</span>";' +
    'html+="<div class=\\"cardBody\\"><div class=\\"cardTitle\\">"+esc(item.name)+"</div>";' +
    'html+="<div class=\\"cardDesc\\">"+esc(item.desc)+"</div>";' +
    'html+="<div class=\\"price\\">&#9733; "+item.price+"</div>";' +
    'card.innerHTML=html;' +
    'var btn=document.createElement("button");btn.className="cardBtn";' +
    'if(equipped){btn.textContent="Ausger\u00fcstet \u2713";btn.disabled=true;}' +
    'else if(owned){btn.textContent="Ausr\u00fcsten";btn.className+=" secondary";btn.setAttribute("data-act","equip");btn.setAttribute("data-item",item.id);}' +
    'else if(!logged){btn.textContent="Anmelden zum Kaufen";btn.className+=" ghost";btn.disabled=true;}' +
    'else{btn.textContent="Kaufen \u00b7 \u2605 "+item.price;if(item.price>st.points){btn.className+=" ghost";btn.disabled=true;}btn.setAttribute("data-act","buy");btn.setAttribute("data-item",item.id);}' +
    'card.append(btn);' +
    'grid.append(card);' +
    '});' +
    '}' +
    '$("tabs").addEventListener("click",function(e){' +
    'var t=e.target.closest(".tab");if(!t)return;' +
    'document.querySelectorAll("#tabs .tab").forEach(function(x){x.classList.remove("on");});' +
    't.classList.add("on");filter=t.getAttribute("data-f");render();' +
    '});' +
    '$("grid").addEventListener("click",function(e){' +
    'var b=e.target.closest("[data-act]");if(!b)return;' +
    'var id=b.getAttribute("data-item");var act=b.getAttribute("data-act");' +
    'b.disabled=true;' +
    'if(act==="buy"){' +
    'fetch("/api/profil/buy",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({item_id:id})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){$("walletPts").textContent="\u2605 "+j.points;load();}' +
    'else{b.disabled=false;alert("Kauf fehlgeschlagen: "+((j&&j.error)||"unbekannt"));}' +
    '}).catch(function(){b.disabled=false;alert("Netzwerkfehler beim Kauf.");});' +
    '}' +
    'else if(act==="equip"){' +
    'fetch("/api/profil/equip",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({item_id:id})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){load();}' +
    'else{b.disabled=false;alert("Ausr\u00fcsten fehlgeschlagen: "+((j&&j.error)||"unbekannt"));}' +
    '}).catch(function(){b.disabled=false;alert("Netzwerkfehler.");});' +
    '}' +
    '});' +
    '})();' +
    '</script>';

  return pageShell('Store', css, html);
}

// ── Freunde-Seite ───────────────────────────────────────────────────────────
function buildFreundePage() {
  const css = SHARED_CSS +
    '.frRow{display:flex;align-items:center;gap:.9rem;padding:.75rem .2rem;border-bottom:1px solid #1c2636;}' +
    '.frRow:last-child{border-bottom:0;}' +
    '.frAv{width:48px;height:48px;border-radius:12px;object-fit:cover;background:#151d2b;flex:none;}' +
    '.frInfo{flex:1;min-width:0;}' +
    '.frName{font-weight:700;font-size:.95rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}' +
    '.frSub{color:#8f9aab;font-size:.8rem;}' +
    '.dot{display:inline-block;width:9px;height:9px;border-radius:50%;margin-right:5px;}' +
    '.on{background:#3fb950;box-shadow:0 0 8px #3fb950;} .off{background:#4b5563;}' +
    '.empty{padding:2rem 0;text-align:center;color:#8f9aab;}' +
    '.codeBox{display:flex;align-items:center;gap:.6rem;flex-wrap:wrap;}' +
    '.codeBox .code{font:800 18px/1 "Courier New",monospace;color:#ffd75f;letter-spacing:.12em;}' +
    '.btn-sm{padding:.4rem .9rem;font-size:.8rem;margin:0;}' +
    '.badgeGlyph{margin-right:5px;font-weight:800;}';

  const html =
    '<nav class="km-nav" id="kmNav">' +
    '<a class="km-link" href="/">&#9656; kollegen.me</a>' +
    '<a class="km-link" href="/profil"><img class="km-avatar" id="kmNavAvatar" alt="" /><span class="km-lbl">Profil</span></a>' +
    '<a class="km-link" href="/store"><span class="km-lbl">Store</span></a>' +
    '<a class="km-link on" href="/freunde"><span class="km-lbl">Freunde</span></a>' +
    '<span class="km-pts" id="kmNavPts" style="display:none;">&#9733; <span id="kmNavPtsVal">0</span></span>' +
    '</nav>' +
    '<div id="wrap">' +
    '<h1>Freunde</h1>' +
    '<div class="sub">Dein Kollegen-Netzwerk – online auf einem Joint-Server, in Steam-Profil-Optik.</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an, um Freunde zu sehen und hinzuzufügen.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="card" id="meCard" style="display:none;">' +
    '<label>Dein Freundes-Code</label>' +
    '<div class="codeBox"><span class="code" id="myCode">–</span>' +
    '<button type="button" class="secondary btn-sm" id="copyBtn">Kopieren</button></div>' +
    '<label>Freund hinzufügen (per Code)</label>' +
    '<div class="row"><input id="addCode" placeholder="z. B. C8B99EC051" style="flex:1;width:auto;text-transform:uppercase;"/>' +
    '<button type="button" class="secondary" id="addBtn">Hinzufügen</button></div>' +
    '<div class="muted" id="addHint" style="margin-top:.5rem;"></div>' +
    '</div>' +
    '<div class="card" id="listCard" style="display:none;">' +
    '<div id="listHeader" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.4rem;">' +
    '<strong id="listTitle"></strong><span class="muted" id="listCount"></span></div>' +
    '<div id="listWrap"><div class="empty">Lade Freunde…</div></div>' +
    '</div>' +
    '<a class="backLink" href="/">&#8592; Zur Startseite</a>' +
    '</div>' +
    '<script>' + NAV_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var me=$("meCard"),login=$("loginCard"),listCard=$("listCard");' +
    'var cat=null;' +
    'function byId(id){var c=cat||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'function avFor(f){return "https://mc-heads.net/head/"+encodeURIComponent(f.name||"MHF_Steve").replace(/%20/g,"_")+"/128";}' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'if(!j||!j.user){login.style.display="block";return;}' +
    'me.style.display="block";' +
    'fetch("/api/profil/store").then(function(r){return r.json();}).then(function(d){cat=(d&&d.catalog)||[];}).catch(function(){});' +
    'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
    'if(p&&p.code){$("myCode").textContent=p.code;}' +
    '}).catch(function(){});' +
    'load();' +
    '}).catch(function(){login.style.display="block";});' +
    'function load(){' +
    'fetch("/api/profil/friends").then(function(r){return r.json();}).then(function(list){' +
    'listCard.style.display="block";' +
    '$("listTitle").textContent="Deine Kollegen";' +
    '$("listCount").textContent=list.length+" Freund"+(list.length===1?"":"e");' +
    'var wrap=$("listWrap");wrap.innerHTML="";' +
    'if(!list.length){wrap.innerHTML="<div class=\\"empty\\">Noch keine Freunde. Teile deinen Code (oben) oder füge einen Code hinzu.</div>";return;}' +
    'list.forEach(function(f){' +
    'var row=document.createElement("div");row.className="frRow";' +
    'var av=document.createElement("img");av.className="frAv";av.src=avFor(f);av.alt="";av.onerror=function(){av.src="https://mc-heads.net/head/MHF_Steve/128";};' +
    'var info=document.createElement("div");info.className="frInfo";' +
    'var nm=document.createElement("div");nm.className="frName";nm.textContent=f.name||("User #"+f.id);' +
    'var eq=f.equipped||{};var gl="";' +
    'if(eq.badge){var b=byId(eq.badge);if(b)gl="<span class=\\"badgeGlyph\\" style=\\"color:"+esc(b.data.color||"#fff")+"\\">"+esc(b.data.icon)+"</span>";}' +
    'var sub=document.createElement("div");sub.className="frSub";' +
    'sub.innerHTML="<span class=\\"dot "+(f.online?"on":"off")+"\\"></span>"+gl+"Level "+esc(f.level)+"\\u00b7 "+esc(f.server||"Offline")+" \\u00b7 Code "+esc(f.code);' +
    'info.append(nm,sub);' +
    'var rem=document.createElement("button");rem.className="secondary btn-sm";rem.textContent="Entfernen";' +
    'rem.addEventListener("click",function(){' +
    'if(!confirm("Freund entfernen?"))return;' +
    'fetch("/api/profil/friend-remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({target_id:f.id})})' +
    '.then(function(r){return r.json();}).then(function(x){load();}).catch(function(){});' +
    '});' +
    'row.append(av,info,rem);' +
    'wrap.append(row);' +
    '});' +
    '}).catch(function(){ $("listWrap").innerHTML="<div class=\\"empty\\">Freunde-Liste temporär nicht erreichbar.</div>"; });' +
    '}' +
    '$("copyBtn").addEventListener("click",function(){' +
    'var c=$("myCode").textContent;if(!c||c==="\u2013")return;' +
    'if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(c).catch(function(){});}' +
    '$("addHint").textContent="Code kopiert!";setTimeout(function(){$("addHint").textContent="";},1500);' +
    '});' +
    '$("addBtn").addEventListener("click",function(){' +
    'var code=$("addCode").value.trim().toUpperCase();' +
    'if(!code){$("addHint").textContent="Bitte Code eingeben.";return;}' +
    '$("addHint").textContent="Füge hinzu…";' +
    'fetch("/api/profil/friend-add",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({code:code})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){$("addHint").textContent="Freund hinzugefügt! (Grün = online)";$("addCode").value="";load();}' +
    'else{var m=(j&&j.error)||"unbekannt";' +
    'if(m==="cannot_friend_self")m="Das bist du selbst.";' +
    'else if(m==="target_not_found")m="Kein Kollege mit diesem Code.";' +
    '$("addHint").textContent="Fehler: "+m;}' +
    '}).catch(function(){ $("addHint").textContent="Netzwerkfehler."; });' +
    '});' +
    '$("addCode").addEventListener("keydown",function(e){if(e.key==="Enter")$("addBtn").click();});' +
    '})();' +
    '</script>';

  return pageShell('Freunde', css, html);
}