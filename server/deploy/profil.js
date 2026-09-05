// Kollegen.me – Profil-/Store-/Freunde-Modul (Website)
// =====================================================
// Ergänzt die Website um:
//   GET  /profil             – self-contained Profil-Seite (HTML, Steam-inspiriert)
//   GET  /store              – self-contained Kosmetik-Store (Kollegen-Points)
//   GET  /freunde            – self-contained Freunde-Seite
//   GET  /api/profil/me      – eigene Profil-/MC-Daten
//   GET  /api/profil/store   – Katalog + eigener Kontostand/Owned/Equipped
//   POST /api/profil/buy     – Kosmetik kaufen (Points abziehen)
//   POST /api/profil/equip   – Kosmetik ausrüsten/ablegen
//   GET  /api/profil/friends, POST /api/profil/friend-add/-remove
//   GET  /api/profil/uuid?name=… – MC-Name → UUID Proxy
//   POST /api/profil/save    – Profil + MC-Identität ins Backend-Bridge schreiben
//   Admin (nur session.isAdmin):
//   GET  /api/profil/admin/users, POST /api/profil/admin/points,
//        /api/profil/admin/grant, /api/profil/admin/reset
//
// In der SPA wird die alte (React-)Toolbar ausgeblendet und durch eine
// Steam-artige Topbar ersetzt (beide Link-Sets), mit Points-Chip, Avatar,
// Discord/Anmelden (ausgeloggt) bzw. Abmelden (eingeloggt).
//
// Einbindung in server.js VOR den statischen/SPA-Fallbacks:
//   require(path.join(__dirname, 'profil.js'))(app, getSession);

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

// ── Topbar (Steam-angelehnt) ────────────────────────────────────────────────
const DISCORD_INVITE = 'https://discord.gg/P5kzdms8bx';

const KM_TOP_CSS =
  '.km-topbar{position:sticky;top:0;z-index:9999;display:flex;align-items:center;gap:6px;width:100%;box-sizing:border-box;' +
  'background:linear-gradient(180deg,#191f31 0%,#10141f 55%,#0a0d16 100%);' +
  'border-bottom:1px solid rgba(212,175,55,.28);' +
  'box-shadow:0 6px 28px rgba(0,0,0,.55), inset 0 1px 0 rgba(255,255,255,.04);' +
  'padding:10px 18px;overflow-x:auto;white-space:nowrap;scrollbar-width:none;position:relative;}' +
  '.km-topbar::-webkit-scrollbar{display:none;}' +
  '.km-topbar::after{content:"";position:absolute;left:0;right:0;bottom:-1px;height:2px;' +
  'background:linear-gradient(90deg,transparent,#D4AF37 30%,#ffd75f 50%,#D4AF37 70%,transparent);' +
  'background-size:200% 100%;animation:kmShine 6s linear infinite;}' +
  '@keyframes kmShine{0%{background-position:200% 0}100%{background-position:-200% 0}}' +
  '.km-topbar .km-brand{display:flex;align-items:center;gap:10px;color:#ffd75f;' +
  'font:800 15px/1 Outfit,Inter,sans-serif;text-decoration:none;letter-spacing:.03em;flex:none;margin-right:12px;' +
  'text-shadow:0 0 14px rgba(212,175,55,.35);}' +
  '.km-topbar .km-brand img{width:32px;height:32px;border-radius:9px;object-fit:contain;' +
  'animation:kmPulse 3s ease-in-out infinite;}' +
  '@keyframes kmPulse{0%,100%{filter:drop-shadow(0 2px 6px rgba(212,175,55,.35));transform:scale(1)}' +
  '50%{filter:drop-shadow(0 2px 12px rgba(255,215,95,.7));transform:scale(1.05)}}' +
  '.km-topbar a.km-link{color:#c7cfdd;text-decoration:none;font:600 12.5px/1 Inter,sans-serif;letter-spacing:.02em;' +
  'padding:9px 12px;border-radius:9px;flex:none;transition:background .18s,color .18s,transform .18s,box-shadow .18s;}' +
  '.km-topbar a.km-link:hover{background:rgba(255,255,255,.08);color:#fff;transform:translateY(-1px);' +
  'box-shadow:0 4px 12px rgba(0,0,0,.35);}' +
  '.km-topbar a.km-link.on{color:#0a0d13;background:linear-gradient(135deg,#D4AF37,#ffd75f);font-weight:800;' +
  'box-shadow:0 2px 12px rgba(212,175,55,.45), inset 0 1px 0 rgba(255,255,255,.4);}' +
  '.km-topbar .km-right{margin-left:auto;display:flex;align-items:center;gap:8px;flex:none;}' +
  '.km-topbar .km-avatar{width:30px;height:30px;border-radius:50%;object-fit:cover;background:#222;flex:none;' +
  'border:2px solid rgba(212,175,55,.65);box-shadow:0 0 8px rgba(212,175,55,.3);}' +
  '.km-topbar .km-pts{display:inline-flex;align-items:center;gap:6px;color:#ffd75f;font:800 13px/1 Inter,sans-serif;' +
  'padding:8px 12px;border:1px solid #5a4d22;border-radius:999px;background:linear-gradient(180deg,#1d1a10,#141208);' +
  'box-shadow:inset 0 1px 0 rgba(255,255,255,.06);}' +
  '.km-topbar .km-pts .km-star{animation:kmPulse 2.4s ease-in-out infinite;}' +
  '.km-topbar a.km-login{color:#ffd75f;font:700 13px/1 Inter,sans-serif;padding:9px 14px;border-radius:9px;' +
  'border:1px solid rgba(212,175,55,.55);text-decoration:none;flex:none;transition:background .15s;}' +
  '.km-topbar a.km-login:hover{background:rgba(212,175,55,.14);}' +
  '.km-topbar a.km-discord{background:linear-gradient(135deg,#6a76f5,#5865F2);color:#fff;' +
  'font:700 13px/1 Inter,sans-serif;padding:9px 14px;border-radius:9px;text-decoration:none;flex:none;' +
  'box-shadow:0 2px 10px rgba(88,101,242,.4);transition:filter .15s,transform .15s;}' +
  '.km-topbar a.km-discord:hover{filter:brightness(1.12);transform:translateY(-1px);}' +
  '.km-topbar a.km-logout{color:#c7cfdd;font:600 12px/1 Inter,sans-serif;padding:8px 10px;text-decoration:none;' +
  'flex:none;opacity:.8;transition:opacity .15s,color .15s;}' +
  '.km-topbar a.km-logout:hover{opacity:1;color:#fff;}' +
  '.km-topbar .km-group{display:inline-flex;align-items:center;gap:2px;flex:none;}' +
  '.km-topbar .km-group-label{font:800 9.5px/1 Outfit,sans-serif;color:#77809a;letter-spacing:.16em;' +
  'text-transform:uppercase;margin-right:5px;padding-left:2px;text-shadow:0 1px 2px rgba(0,0,0,.5);}' +
  '.km-topbar .km-gsep{width:1px;height:24px;background:linear-gradient(180deg,transparent,rgba(255,255,255,.22),transparent);' +
  'margin:0 10px;flex:none;}' +
  '@media (max-width:860px){.km-topbar a.km-link.km-hide-sm{display:none;}.km-topbar .km-group-label{display:none;}}';

function topBarHtml(current) {
  function a(href, label, active, hideSm) {
    const on = active === href ? ' on' : '';
    const hs = hideSm ? ' km-hide-sm' : '';
    return '<a class="km-link' + on + hs + '" href="' + href + '">' + label + '</a>';
  }
  // Gruppen-Navigation: „Spielen" · „Community" · „Deine Welt"
  const groups = [
    { label: 'Spielen', pages: [
      { href: '/minecraft', label: 'Minecraft' },
      { href: '/clicker', label: 'Clicker' },
      { href: '/chat', label: 'Chat', badge: true },
    ] },
    { label: 'Community', pages: [
      { href: '/kollegenawards', label: 'Awards', hideSm: true },
      { href: '/ueber-uns', label: '\u00dcber uns', hideSm: true },
      { href: '/projekte', label: 'Projekte', hideSm: true },
    ] },
    { label: 'Deine Welt', pages: [
      { href: '/profil', label: 'Profil' },
      { href: '/store', label: 'Store' },
      { href: '/freunde', label: 'Freunde' },
    ] },
  ];
  let links = '';
  for (let gi = 0; gi < groups.length; gi++) {
    if (gi > 0) links += '<span class="km-gsep"></span>';
    const g = groups[gi];
    links += '<span class="km-group"><span class="km-group-label">' + g.label + '</span>';
    for (const p of g.pages) links += a(p.href, p.label, current, p.hideSm);
    links += '</span>';
  }
  return (
    '<header class="km-topbar" id="kmTopbar">' +
    '<a class="km-brand" href="/"><img src="/images/logo.png" alt=""/>kollegen.me</a>' +
    links +
    '<div class="km-right">' +
    '<span class="km-pts" id="kmNavPts" style="display:none;"><span class="km-star">&#9733;</span> <span id="kmNavPtsVal">0</span></span>' +
    '<img class="km-avatar" id="kmNavAvatar" alt="" style="display:none;"/>' +
    '<a class="km-login" id="kmLogin" href="/api/auth/discord/login" style="display:none;">Anmelden</a>' +
    '<a class="km-discord" id="kmDiscord" href="' + DISCORD_INVITE + '" target="_blank" rel="noopener">Discord</a>' +
    '<a class="km-logout" id="kmLogout" href="#" style="display:none;">Abmelden</a>' +
    '</div>' +
    '</header>'
  );
}

const KM_TOP_SCRIPT =
  '(function(){' +
  'function kmReady(fn){if(document.readyState!=="loading")fn();else document.addEventListener("DOMContentLoaded",fn);}' +
  'kmReady(function(){' +
  'var pts=document.getElementById("kmNavPts");var pv=document.getElementById("kmNavPtsVal");' +
  'var av=document.getElementById("kmNavAvatar");' +
  'var lg=document.getElementById("kmLogin");var dg=document.getElementById("kmDiscord");' +
  'var lo=document.getElementById("kmLogout");' +
  'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
  'if(j&&j.user){' +
  'if(lg)lg.style.display="none";' +
  'if(pts)pts.style.display="";if(av)av.style.display="";' +
  'if(lo){lo.style.display="";lo.addEventListener("click",function(e){e.preventDefault();' +
  'fetch("/api/auth/logout",{method:"POST"}).then(function(){location.href="/";}).catch(function(){location.href="/";});});}' +
  'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
  'if(!p)return;' +
  'if(pv&&typeof p.points==="number")pv.textContent=p.points;' +
  'if(av){var head=null;' +
  'if(p.mcName){head="https://mc-heads.net/avatar/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/128";}' +
  'else if(j.user&&j.user.avatarUrl){head=j.user.avatarUrl;}' +
  'if(head){av.src=head;}else{av.style.display="none";}' +
  '}' +
  '}).catch(function(){});' +
  '}else{' +
  'if(lo)lo.style.display="none";if(av)av.style.display="none";if(pts)pts.style.display="none";' +
  'if(lg)lg.style.display="";if(dg)dg.style.display="";' +
  '}' +
  '}).catch(function(){if(lo)lo.style.display="none";if(av)av.style.display="none";if(pts)pts.style.display="none";' +
  'if(lg)lg.style.display="";if(dg)dg.style.display="";});' +
  '});' +
  '})();';

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

  function isAdmin(session) {
    return !!session && session.isAdmin === true;
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
    let r = await backendInternal('GET', '/internal/user?discordId=' + encodeURIComponent(String(session.id)));
    // Neu-Registrierung: Ein Discord-Login legt den Backend-User erst an, wenn er
    // im Profil-Editor gespeichert wird → bis dahin 404 + keine Starter-Points.
    // Hier legen wir den User deshalb beim ersten Zugriff automatisch an.
    if (!r.ok && /404/.test(r.error || '')) {
      const reg = await backendInternal('POST', '/internal/profile', {
        discordId: String(session.id),
        discordName: session.global_name || session.username || 'Discord-Nutzer',
      });
      if (reg.ok) r = await backendInternal('GET', '/internal/user?discordId=' + encodeURIComponent(String(session.id)));
    }
    const data = r.ok && r.data ? r.data : {};
    return res.json({
      user: {
        id: session.id,
        username: session.username || '',
        global_name: session.global_name || session.username || '',
        avatarUrl: session.avatarUrl || '',
        isAdmin: isAdmin(session),
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

  // Freund per Code hinzufügen (legt eine Anfrage an, Gegner bestätigt)
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

  // Eingehende Freundesanfragen
  app.get('/api/profil/friend-requests', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const r = await backendInternal('GET', '/internal/friend-requests?discordId=' + encodeURIComponent(String(session.id)));
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(Array.isArray(r.data) ? r.data : []);
  });

  // Freundesanfrage annehmen
  app.post('/api/profil/friend-accept', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/friend-accept', {
      discordId: String(session.id),
      from_id: String(body.from_id || ''),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // Freundesanfrage ablehnen
  app.post('/api/profil/friend-decline', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/friend-decline', {
      discordId: String(session.id),
      from_id: String(body.from_id || ''),
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

  // Profil eines Kollegen ansehen (öffentlich / Freund / eigene)
  app.get('/api/profil/profile-view', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    const viewer = session ? String(session.id) : '';
    const code = String(req.query.code || '').toUpperCase();
    const qs = '?viewer_id=' + encodeURIComponent(viewer) + '&code=' + encodeURIComponent(code);
    const r = await backendInternal('GET', '/internal/profile-view' + qs);
    if (!r.ok) return res.status(r.error === 'not_found' ? 404 : 500).json({ error: r.error });
    return res.json(r.data);
  });

  // ── DMs (privater Chat zwischen Freunden) ──
  app.get('/api/profil/dm/conversations', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const r = await backendInternal('GET', '/internal/dm/conversations?discordId=' + encodeURIComponent(String(session.id)));
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(Array.isArray(r.data) ? r.data : []);
  });

  app.get('/api/profil/dm/messages', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const other = String(req.query.other || '');
    if (!other) return res.status(400).json({ error: 'other_required' });
    const qs = '?me=' + encodeURIComponent(String(session.id)) + '&other=' + encodeURIComponent(other);
    const r = await backendInternal('GET', '/internal/dm/messages' + qs);
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(Array.isArray(r.data) ? r.data : []);
  });

  app.post('/api/profil/dm/send', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!session) return res.status(401).json({ error: 'not_authenticated' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/dm/send', {
      from_id: String(session.id),
      to_id: String(body.other || ''),
      text: String(body.text || ''),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // ── Admin (nur für Admins) ──
  app.get('/api/profil/admin/users', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!isAdmin(session)) return res.status(403).json({ error: 'forbidden' });
    const search = String(req.query.search || '');
    const r = await backendInternal('GET', '/internal/users?search=' + encodeURIComponent(search));
    if (!r.ok) return res.status(500).json({ error: r.error });
    return res.json(r.data);
  });

  app.post('/api/profil/admin/points', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!isAdmin(session)) return res.status(403).json({ error: 'forbidden' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/points', {
      discordId: String(body.discordId || ''),
      delta: Math.round(Number(body.delta) || 0),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  app.post('/api/profil/admin/grant', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!isAdmin(session)) return res.status(403).json({ error: 'forbidden' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/grant', {
      discordId: String(body.discordId || ''),
      item_id: String(body.item_id || ''),
      equip: body.equip !== false,
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  app.post('/api/profil/admin/reset', async (req, res) => {
    const session = (typeof getSession === 'function') ? getSession(req) : null;
    if (!isAdmin(session)) return res.status(403).json({ error: 'forbidden' });
    const body = req.body && typeof req.body === 'object' ? req.body : {};
    const r = await backendInternal('POST', '/internal/reset', {
      discordId: String(body.discordId || ''),
    });
    if (!r.ok) return res.status(400).json({ error: r.error });
    return res.json(r.data);
  });

  // ── Seiten ──
  const PAGES = {
    '/profil': buildProfilPage(),
    '/store': buildStorePage(),
    '/freunde': buildFreundePage(),
    '/dm': buildDmPage(),
  };

  // ── SPA: alte Toolbar ausblenden + neue Topbar injizieren ──
  // Route-abhängig: /chat bekommt zusätzlich das Social-Widget (Freunde + DMs).
  const indexCache = {};
  let baseIndexHtml = null;
  function injectedIndex(route) {
    if (indexCache[route]) return indexCache[route];
    if (baseIndexHtml === null) {
      try {
        baseIndexHtml = fs.readFileSync(INDEX_PATH, 'utf8');
      } catch (_) {
        return null;
      }
    }
    let html = baseIndexHtml;
    let top =
      '<style>' + KM_TOP_CSS +
      // Alte React-Toolbar (fixed top-0 z-50) unsichtbar machen.
      'nav[class*="top-0"][class*="z-50"]{display:none !important;}' +
      '</style>' +
      topBarHtml(route) +
      '<script>' + KM_TOP_SCRIPT + '</' + 'script>';
    if (route === '/chat') top += CHAT_WIDGET_HTML;
    html = html.replace(/<body[^>]*>/, function (m) { return m + top; });
    indexCache[route] = html;
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

    // Öffentliche Kollegen-Profile: /u/<Code>
    const um = /^\/u\/([A-Za-z0-9]{1,20})$/.exec(urlPath);
    if (um) {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      return res.send(buildUserPage(String(um[1]).toUpperCase()));
    }

    const ext = path.extname(urlPath).toLowerCase();
    if (ext && ext !== '.html') return next();
    if (urlPath.startsWith('/api/')) return next();
    const acceptsHtml = (req.headers.accept || '').includes('text/html');
    if (urlPath === '/' || ext === '.html' || acceptsHtml) {
      const injected = injectedIndex(urlPath);
      if (injected) {
        res.setHeader('Content-Type', 'text/html; charset=utf-8');
        return res.send(injected);
      }
    }
    return next();
  });
};

// ── Gemeinsame Basis ────────────────────────────────────────────────────────
const SHARED_CSS =
  'body{background:#0a0d13;color:#f2f3f5;font-family:Inter,"Segoe UI",Arial,sans-serif;' +
  'margin:0;padding:0;display:flex;flex-direction:column;align-items:center;min-height:100vh;box-sizing:border-box;}' +
  '#wrap{max-width:880px;width:100%;box-sizing:border-box;margin:1.6rem auto 3rem;padding:1.4rem;}' +
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
  '.btn-sm{padding:.45rem .9rem;font-size:.8rem;margin:0;}' +
  '.row{display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;}' +
  '.muted{color:#8f9aab;font-size:.85rem;} .good{color:#7ee787;} .bad{color:#f85149;}' +
  'a{color:#8ab4ff;text-decoration:none;} a:hover{text-decoration:underline;}' +
  '.badge-medal{font:800 11px/1 Inter,sans-serif;letter-spacing:.04em;border-radius:999px;padding:3px 9px;}' +
  '.loginCard{display:none;}' +
  '.loginCard button{background:#5865F2;}' +
  '.ptsChip{display:inline-flex;align-items:center;gap:6px;background:linear-gradient(135deg,#3a3020,#241c10);' +
  'border:1px solid #6b5627;color:#ffd75f;font:800 14px/1 Inter,sans-serif;padding:8px 14px;border-radius:999px;}' +
  '.lvlChip{display:inline-flex;align-items:center;gap:6px;background:#1a2333;border:1px solid #2f3d55;' +
  'color:#c9d4e3;font:700 13px/1 Inter,sans-serif;padding:8px 12px;border-radius:999px;}' +
  '.bar{height:8px;border-radius:999px;background:#1a2333;overflow:hidden;}' +
  '.bar>div{height:100%;background:linear-gradient(90deg,#D4AF37,#ffd75f);border-radius:999px;}';

function pageShell(title, cssExtra, bodyInner) {
  return (
    '<!DOCTYPE html><html lang="de"><head><meta charset="utf-8"/>' +
    '<meta name="viewport" content="width=device-width,initial-scale=1"/>' +
    '<title>' + title + ' &middot; kollegen.me</title>' +
    '<style>' + KM_TOP_CSS + SHARED_CSS + cssExtra + '</style></head>' +
    '<body>' + topBarHtml(pagePathForTitle(title)) + bodyInner + '</body></html>'
  );
}

function pagePathForTitle(title) {
  if (title === 'Profil') return '/profil';
  if (title === 'Store') return '/store';
  if (title === 'Freunde') return '/freunde';
  return '';
}

// ── Profilseite ─────────────────────────────────────────────────────────────
function buildProfilPage() {
  const css =
    '#wrap{border:1px solid rgba(255,255,255,.06);border-radius:18px;background:rgba(9,11,18,.66);' +
    'box-shadow:0 12px 44px rgba(0,0,0,.45);}' +
    '.bannerStrip{display:none;height:96px;border-radius:14px;margin-bottom:1.1rem;position:relative;overflow:hidden;' +
    'background-size:cover;background-position:center;}' +
    '.bannerStrip .bl{position:absolute;left:12px;bottom:10px;color:#0a0d13;font:800 13px/1 Outfit,Inter,sans-serif;' +
    'background:rgba(255,255,255,.72);padding:4px 10px;border-radius:999px;}' +
    '.profileRow{display:flex;gap:1rem;align-items:center;}' +
    '.headImg{width:84px;height:84px;border-radius:16px;object-fit:cover;background:#222;flex:none;}' +
    '.equipList{display:flex;flex-wrap:wrap;gap:.6rem;margin-top:.3rem;}' +
    '.equipItem{display:flex;align-items:center;gap:.5rem;background:#0d1420;border:1px solid #26344a;' +
    'border-radius:10px;padding:.45rem .7rem;font-size:.85rem;color:#e3e9f2;}' +
    '.equipItem .swatch{width:18px;height:18px;border-radius:5px;flex:none;text-align:center;line-height:18px;font-size:11px;}' +
    '.progressLabel{display:flex;justify-content:space-between;font-size:.75rem;color:#8f9aab;margin-top:.25rem;}' +
    // Profil-Editor
    '.editPreview{display:flex;align-items:center;gap:.9rem;padding:.8rem;border:1px dashed #2c3b57;border-radius:12px;' +
    'margin-top:.4rem;background:#0d1420;}' +
    '.editPreview img{width:64px;height:64px;border-radius:12px;object-fit:cover;background:#151d2b;flex:none;}' +
    '.editPreview .epMeta{flex:1;min-width:0;}' +
    '.editPreview .epName{font-weight:800;color:#ffd75f;font-family:Outfit,sans-serif;font-size:1.05rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}' +
    '.editPreview .epSub{color:#8f9aab;font-size:.78rem;}' +
    '.pubPreview{margin-top:1rem;}' +
    '.pubPreview .ppHead{display:flex;align-items:center;justify-content:space-between;gap:.6rem;margin-bottom:.5rem;}' +
    '.pubPreview .ppHead>span{color:#8f9aab;font-size:.8rem;letter-spacing:.04em;font-weight:600;text-transform:uppercase;}' +
    '.pubPreview .pvFrame{width:100%;height:340px;border:1px solid #26354f;border-radius:12px;background:#0a0d13;}' +
    '.editGroup{margin-top:1rem;}' +
    '.editGroup .egTitle{font:800 11px/1 Outfit,sans-serif;color:#77809a;letter-spacing:.12em;text-transform:uppercase;margin-bottom:.5rem;}' +
    '.editChips{display:flex;flex-wrap:wrap;gap:.45rem;}' +
    '.editChip{border:1px solid #2c3b57;background:#16203a;color:#c7cfdd;border-radius:999px;padding:.4rem .7rem;font-size:.8rem;' +
    'cursor:pointer;transition:all .15s;display:inline-flex;align-items:center;gap:.4rem;user-select:none;}' +
    '.editChip:hover{border-color:#D4AF37;color:#fff;}' +
    '.editChip.eq{border-color:#ffd75f;background:linear-gradient(135deg,rgba(212,175,55,.28),rgba(255,215,95,.14));color:#ffd75f;font-weight:700;}' +
    '.editChip .sw{width:15px;height:15px;border-radius:4px;background:#333;flex:none;border:1px solid #000;' +
    'text-align:center;line-height:15px;font-size:10px;}';

  const html =
    '<div id="wrap">' +
    '<div class="bannerStrip" id="bannerStrip"><span class="bl" id="bannerLabel"></span></div>' +
    '<h1>Profil &amp; Minecraft</h1>' +
    '<div class="sub">Dein \u00f6ffentliches Kollegen-Profil \u2013 mit Sternen-Store, Skins und Kosmetik.</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an \u2013 dein Minecraft-Profil wird automatisch \u00fcbernommen, sobald du im Kollegen-Launcher angemeldet bist.</p>' +
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
    '<label for="mcName">Minecraft-Name (verkn\u00fcpfen)</label>' +
    '<div class="row"><input id="mcName" placeholder="z. B. FluffyBento" style="flex:1;width:auto;"/><button type="button" class="secondary" id="lookupBtn">Skin laden</button></div>' +
    '<div id="skinPreview" style="margin-top:.6rem;"></div>' +
    '<label for="avatarChoice">Avatar</label>' +
    '<select id="avatarChoice"><option value="discord">Discord-Avatar</option><option value="minecraft">Minecraft-Head</option></select>' +
    '<label for="bioValue">Bio</label><textarea id="bioValue" placeholder="Beschreibe dich in ein paar Worten..."></textarea>' +
    '<label for="bannerValue">Banner-Bild-URL (optional)</label><input id="bannerValue" placeholder="https://\u2026/banner.png"/>' +
    '<label style="display:flex;align-items:center;gap:.5rem;"><input type="checkbox" id="pubToggle" style="width:auto;"/> \u00d6ffentliches Profil (f\u00fcr andere sichtbar)</label>' +
    '<div class="muted" id="saveHint" style="margin-top:.6rem;"></div>' +
    '<div class="row"><button type="button" id="saveBtn">Speichern</button>' +
    '<a href="/store"><button type="button" class="ghost">Kosmetik &amp; Store</button></a></div>' +
    '</div>' +
    '<div class="card" id="equipCard" style="display:none;">' +
    '<h1 style="font-size:1.1rem;">Ausger\u00fcstete Kosmetik</h1>' +
    '<p class="muted">Diese Items siehst du in Profilen und der Freunde-Liste. Ausr\u00fcsten/ablegen kannst du im Store.</p>' +
    '<div class="equipList" id="equipList"></div>' +
    '</div>' +
    '<div class="card" id="editCard" style="display:none;">' +
    '<h1 style="font-size:1.1rem;">Profil anpassen</h1>' +
    '<p class="muted">W\u00e4hle deine eigenen Kosmetik-Items \u2013 klick auf einen Chip, um es auszur\u00fcsten. Neue Items gibt\u2019s im Store.</p>' +
    '<div class="editPreview" id="editPreview"></div>' +
    '<div class="pubPreview" id="pubPreview" style="display:none;"></div>' +
    '<div class="editGroups" id="editGroups"></div>' +
    '<div class="muted" id="editHint" style="margin-top:.6rem;"></div>' +
    '<a href="/store"><button type="button" class="ghost">Mehr im Store</button></a>' +
    '</div>' +
    '<p class="sub" style="margin-top:1.2rem;">Hinweis: Der direkte Minecraft-Login (Microsoft/OAuth) ben\u00f6tigt eine eigene App-Registrierung \u2013 bis dahin wird dein Skin \u00fcber den Namen geladen. Gemeinsame Freunde im Launcher \u00fcbernehmen den Skin automatisch.</p>' +
    '</div>' +
    '<script>' + KM_TOP_SCRIPT +
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
    'fetch("/api/profil/store").then(function(r){return r.json();}).then(function(st){' +
    'window._kmCat=(st&&st.catalog)||[];' +
    'if(p&&typeof p.points==="number"){renderPts(p);}' +
    'applyCosmetics(p);' +
    '}).catch(function(){ if(p&&typeof p.points==="number"){renderPts(p);} });' +
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
    '}).catch(function(){});' +
    '}).catch(function(){login.style.display="block";});' +
    'function byId(id){var c=window._kmCat||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function applyCosmetics(p){' +
    'var eq=p.equipped||{};' +
    'var pb=byId(eq.profile_bg);' +
    'if(pb&&pb.data&&pb.data.gradient){document.body.style.background=pb.data.gradient+" fixed";}' +
    'var bn=byId(eq.banner);var strip=$("bannerStrip"),lab=$("bannerLabel");' +
    'if(bn&&bn.data&&bn.data.gradient){strip.style.background=bn.data.gradient;strip.style.display="block";if(lab)lab.textContent=bn.name;}' +
    'else if(p.profile&&p.profile.banner_data_url){strip.style.backgroundImage="url("+p.profile.banner_data_url+")";strip.style.display="block";if(lab)lab.textContent="Profil-Banner";}' +
    'else{strip.style.display="none";}' +
    'var pf=byId(eq.profile_frame);var wr=$("wrap");' +
    'if(pf&&pf.data&&pf.data.color1){wr.style.border="2px solid "+pf.data.color1;wr.style.boxShadow="0 0 30px "+pf.data.color1+"44, 0 12px 44px rgba(0,0,0,.45)";}' +
    'var st=byId(eq.profil_stil);var hs=document.querySelectorAll("h1");' +
    'if(st&&st.data&&st.data.accent){for(var h=0;h<hs.length;h++){hs[h].style.color=st.data.accent;}}' +
    'else{for(var h2=0;h2<hs.length;h2++){hs[h2].style.color="";}}' +
    '}' +
    'function renderPts(p){' +
    'var w=$("ptsWrap");w.innerHTML="";' +
    'var c=document.createElement("div");c.className="ptsChip";c.textContent="\u2605 "+p.points;' +
    'var l=document.createElement("div");l.className="lvlChip";l.textContent="Level "+p.level;' +
    'var t=(typeof p.points_total==="number")?p.points_total:0;var next=300-((t-1)%300)-1;' +
    'var prog=document.createElement("div");' +
    'prog.innerHTML="<div class=\\"bar\\"><div style=\\"width:"+(((t-1)%300)+1)+"%;\\"></div></div>"+"<div class=\\"progressLabel\\"><span>"+p.level+" \u2192 "+(p.level+1)+"</span><span>"+next+" Punkte bis Level "+(p.level+1)+"</span></div>";' +
    'w.append(c,l,prog);' +
    'renderEquip(p);' +
    'renderEditor(p);' +
    '}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'function renderEditor(p){' +
    'var card=$("editCard");if(!card)return;' +
    'card.style.display="block";' +
    'var eq=p.equipped||{};' +
    'var prev=$("editPreview");prev.innerHTML="";' +
    'var head=document.createElement("img");' +
    'if(p.mcName){head.src="https://mc-heads.net/avatar/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/128";}' +
    'else if(p.user&&p.user.avatarUrl){head.src=p.user.avatarUrl;}else{head.style.display="none";}' +
    'head.alt="";head.onerror=function(){head.style.display="none";};' +
    'var meta=document.createElement("div");meta.className="epMeta";' +
    'var ti=byId(eq.title);var titleTxt=(ti&&ti.data)?ti.data.text+" ":"";' +
    'var ba=byId(eq.badge);var baHtml="";' +
    'if(ba&&ba.data){baHtml="<span style=\\"color:"+esc(ba.data.color)+"\\">"+esc(ba.data.icon)+"</span> ";}' +
    'var nm=esc(p.mcName||((p.user&&(p.user.global_name||p.user.username))||"Du"));' +
    'meta.innerHTML="<div class=\\"epName\\">"+baHtml+esc(titleTxt)+nm+"</div><div class=\\"epSub\\">Level "+esc(p.level)+"</div>";' +
    'prev.append(head,meta);' +
    'var pv=$("pubPreview");' +
    'if(pv){' +
    'if(p.code){' +
    'pv.style.display="block";' +
    'pv.innerHTML="<div class=\\"ppHead\\"><span>So sehen Andere dein Profil</span><a class=\\"btn-sm linkBtn\\" target=\\"_blank\\" rel=\\"noopener\\" href=\\"/u/"+encodeURIComponent(p.code)+"\\">Profil \u00f6ffnen</a></div>"+"<iframe class=\\"pvFrame\\" src=\\"/u/"+encodeURIComponent(p.code)+"\\" loading=\\"lazy\\"></iframe>";' +
    '}else{pv.style.display="none";}' +
    '}' +
    'var groups=$("editGroups");groups.innerHTML="";' +
    'var order=["title","badge","avatar_theme","avatar_frame","profile_bg","profile_frame","banner","sticker","name_color","font","profil_stil"];' +
    'var catNames={title:"Titel",badge:"Abzeichen",avatar_theme:"Avatar-Hintergrund",avatar_frame:"Avatar-Rahmen",profile_bg:"Profil-Hintergrund",profile_frame:"Profil-Rahmen",banner:"Banner",sticker:"Aufkleber",name_color:"Namensfarbe",font:"Schriftart",profil_stil:"Profilstil"};' +
    'var owned=p.cosmetics||[];' +
    'for(var oi=0;oi<order.length;oi++){' +
    'var cat=order[oi];' +
    'var mine=owned.filter(function(c){var it=byId(c&&c.id)?byId(c.id):(typeof c==="string"?byId(c):null);return it&&it.category===cat;});' +
    'if(!mine.length)continue;' +
    'var g=document.createElement("div");g.className="editGroup";' +
    'g.innerHTML="<div class=\\"egTitle\\">"+catNames[cat]+"</div><div class=\\"editChips\\"></div>";' +
    'var wrap=g.querySelector(".editChips");' +
    'var none=document.createElement("span");none.className="editChip"+(eq[cat]?"":" eq");none.textContent="Keins";' +
    'none.addEventListener("click",function(){doEquip(cat,null);});' +
    'wrap.append(none);' +
    'mine.forEach(function(c){' +
    'var it=byId(c&&c.id?c.id:c);if(!it)return;' +
    'var ch=document.createElement("span");ch.className="editChip"+(eq[cat]===it.id?" eq":"");' +
    'var sw=document.createElement("span");sw.className="sw";' +
    'if(it.data&&it.data.color1){sw.style.background=it.data.color1;}' +
    'else if(it.data&&it.data.gradient){sw.style.background=it.data.gradient;}' +
    'else if(it.data&&it.data.accent){sw.style.background=it.data.accent;}' +
    'else if(it.data&&it.data.text){sw.textContent=it.data.text;sw.style.background="#3a3f4e";}' +
    'else if(it.data&&it.data.font){sw.textContent="Aa";sw.style.background="#3a3f4e";}' +
    'else if(it.data&&it.data.icon){sw.textContent=it.data.icon;sw.style.color=it.data.color;}' +
    'ch.append(sw);ch.append(document.createTextNode(it.name));' +
    'ch.addEventListener("click",function(){doEquip(cat,it.id);});' +
    'wrap.append(ch);' +
    '});' +
    'groups.append(g);' +
    '}' +
    'function doEquip(cat,id){' +
    'fetch("/api/profil/equip",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({category:cat,all:false,item_id:id})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){location.reload();}' +
    'else{$("editHint").textContent="Fehler: "+((j&&j.error)||"?");}' +
    '}).catch(function(){$("editHint").textContent="Netzwerkfehler";});' +
    '}' +
    '}' +
    'function renderEquip(p){' +
    'var list=$("equipList");if(!list)return;' +
    'var eq=p.equipped||{};' +
    'for(var k in eq){if(!eq[k])continue;var it=byId(eq[k]);if(it){' +
    'var el=document.createElement("div");el.className="equipItem";' +
    'var sw=document.createElement("span");sw.className="swatch";' +
    'if(it.data&&it.data.color1){sw.style.background=it.data.color1;}' +
    'else if(it.data&&it.data.gradient){sw.style.background=it.data.gradient;}' +
    'else if(it.data&&it.data.accent){sw.style.background=it.data.accent;}' +
    'else if(it.category==="title"&&it.data){sw.textContent=it.data.text;sw.style.background="#3a3f4e";}' +
    'else if(it.category==="badge"&&it.data){sw.textContent=it.data.icon;sw.style.background="#0d1420";sw.style.color=it.data.color;}' +
    'else if(it.category==="sticker"&&it.data){sw.textContent=it.data.icon;sw.style.background="#0d1420";sw.style.color=it.data.color;}' +
    'else if(it.category==="font"&&it.data){sw.textContent="Aa";sw.style.background="#3a3f4e";}' +
    'el.append(sw);el.append(document.createTextNode(it.name));' +
    'list.append(el);' +
    '}'+
    '}'+
    '$("equipCard").style.display="block";' +
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

// ── Store-Seite (Steam-inspiriert + Admin-Panel) ────────────────────────────
function buildStorePage() {
  const css = SHARED_CSS +
    '#wrap{border:0;background:transparent;box-shadow:none;}' +
    '.storeHead{display:flex;align-items:center;gap:.8rem;flex-wrap:wrap;justify-content:space-between;}' +
    // Showcase (Steam-Profil-Vorschau)
    '.showCard{margin-top:1rem;overflow:hidden;padding:0;}' +
    '.showBanner{height:72px;background-size:cover;background-position:center;}' +
    '.showRow{display:flex;gap:1rem;align-items:center;padding:1rem 1.2rem 1.1rem;}' +
    '.showAvWrap{position:relative;flex:none;}' +
    '.showAv{width:74px;height:74px;border-radius:16px;object-fit:cover;background:#151d2b;}' +
    '.showInfo{flex:1;min-width:0;}' +
    '.showName{font:800 16px/1 Outfit,Inter,sans-serif;color:#ffd75f;}' +
    '.showSub{color:#9aa3af;font-size:.83rem;margin:.25rem 0 .55rem;}' +
    '.showCollTxt{display:flex;justify-content:space-between;font-size:.75rem;color:#8f9aab;margin-bottom:.25rem;}' +
    // Filter + Sortierung
    '.toolRow{display:flex;gap:.6rem;align-items:center;flex-wrap:wrap;margin:1rem 0 .3rem;}' +
    '.rarChips{display:flex;gap:.35rem;flex-wrap:wrap;}' +
    '.chip{background:#121826;border:1px solid #26334a;color:#b8c2d0;font:600 12px/1 Inter,sans-serif;' +
    'padding:6px 12px;border-radius:999px;cursor:pointer;transition:all .15s;margin:0;}' +
    '.chip.on{background:#D4AF37;border-color:#D4AF37;color:#0a0d13;}' +
    '.sortSel{width:auto;background:#0d1420;border:1px solid #2a3749;color:#b8c2d0;border-radius:9px;padding:.45rem .6rem;font-size:.82rem;}' +
    // Tabs
    '.tabs{display:flex;gap:.4rem;flex-wrap:wrap;margin:.6rem 0 .4rem;}' +
    '.tab{background:#121826;border:1px solid #26334a;color:#c6cfdb;font:600 13px/1 Inter,sans-serif;' +
    'padding:7px 14px;border-radius:999px;cursor:pointer;transition:all .15s;margin:0;}' +
    '.tab.on{background:#D4AF37;border-color:#D4AF37;color:#0a0d13;}' +
    // Grid + Karten
    '.grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(176px,1fr));gap:1rem;margin-top:.9rem;}' +
    '.card{display:flex;flex-direction:column;margin:0;position:relative;transition:transform .15s,border-color .15s;cursor:pointer;}' +
    '.card:hover{transform:translateY(-3px);border-color:#3c4a63;box-shadow:0 8px 22px rgba(0,0,0,.35);}' +
    '.card.equipped{border-color:#6b5627;}' +
    '.card.owned{opacity:.92;}' +
    '.ribbon{position:absolute;top:10px;left:-2px;z-index:3;background:linear-gradient(90deg,#f7b733,#fc4a1a);' +
    'color:#fff;font:800 10px/1 Inter,sans-serif;letter-spacing:.05em;padding:4px 10px 4px 8px;border-radius:0 8px 8px 0;' +
    'box-shadow:0 2px 8px rgba(0,0,0,.4);}' +
    '.pv{height:112px;border-radius:10px;display:flex;align-items:center;justify-content:center;' +
    'background:#0d1420;border:1px solid #1c2636;overflow:hidden;}' +
    '.pvHead{width:64px;height:64px;border-radius:14px;object-fit:cover;background:#151d2b;}' +
    '.pvTitle{font:800 15px/1 Outfit,Inter,sans-serif;color:#ffd75f;}' +
    '.pvBadgeTxt{color:#0a0d13;font-weight:800;}' +
    '.pvMini{width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;' +
    'color:#c9d4e3;font:700 11px/1 Inter,sans-serif;}' +
    '.rar{position:absolute;top:12px;right:10px;z-index:3;}' +
    '.r-common{background:#2b3547;color:#b8c2d0;}.r-rare{background:#123a5c;color:#5aa9ff;}' +
    '.r-epic{background:#3b1e5c;color:#c96bff;}.r-legendary{background:#5c3e12;color:#ffb64d;}' +
    '.cardBody{padding:.7rem .2rem 0;flex:1;display:flex;flex-direction:column;}' +
    '.cardTitle{font-weight:700;font-size:.98rem;}' +
    '.cardDesc{color:#8f9aab;font-size:.8rem;margin:.25rem 0 .5rem;flex:1;}' +
    '.price{display:flex;align-items:center;gap:5px;color:#ffd75f;font-weight:800;}' +
    '.cardBtn{width:100%;text-align:center;margin-top:.55rem;padding:.5rem;}' +
    // Admin
    '.adminCard{border:1px dashed #6b5627;background:#15131B;}' +
    '.adminCard>div:first-child{margin-top:0;}' +
    '.adminUsers{max-height:220px;overflow:auto;border:1px solid #26344a;border-radius:10px;margin-top:.4rem;}' +
    '.adminU{display:flex;align-items:center;gap:.6rem;padding:.45rem .7rem;cursor:pointer;border-bottom:1px solid #1c2636;transition:background .15s;}' +
    '.adminU:hover{background:rgba(255,255,255,.05);}' +
    '.adminU.on{background:rgba(212,175,55,.14);}' +
    '.adminU .auName{font-weight:700;font-size:.88rem;flex:1;min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;}' +
    '.adminU .auPts{color:#ffd75f;font-weight:800;font-size:.8rem;}' +
    '.adminTarget{border:1px solid #2e3a52;border-radius:12px;padding:.8rem;margin-top:.6rem;background:#0d1420;}' +
    '.adminNote{color:#8f9aab;font-size:.78rem;margin-top:.5rem;}' +
    // Modal
    '.kmModal{position:fixed;inset:0;z-index:10000;background:rgba(4,6,10,.72);display:flex;align-items:center;justify-content:center;padding:1rem;}' +
    '.kmModalCard{background:#121826;border:1px solid #3a4a63;border-radius:16px;max-width:440px;width:100%;position:relative;padding:1.2rem;' +
    'box-shadow:0 18px 60px rgba(0,0,0,.6),0 0 30px rgba(212,175,55,.12);}' +
    '.kmModalX{position:absolute;top:10px;right:14px;font:600 22px/1 Inter;color:#8f9aab;cursor:pointer;background:none;border:0;padding:4px;margin:0;}' +
    '.kmModalX:hover{color:#fff;}' +
    '.kmModalCard .pv{height:190px;margin-top:1rem;border-radius:12px;}' +
    '.mmTitle{font:800 19px/1 Outfit,Inter,sans-serif;color:#ffd75f;margin-top:.9rem;}' +
    '.mmRar{display:inline-block;margin-top:.5rem;}' +
    '.mmDesc{color:#a9b3c0;font-size:.9rem;margin-top:.6rem;}' +
    '.mmPrice{margin-top:.6rem;}' +
    '.note{display:flex;gap:.5rem;align-items:flex-start;margin:1rem 0 0;}' +
    '.backLink{margin-top:1.4rem;display:inline-block;font-size:.85rem;}';

  const html =
    '<div id="wrap">' +
    '<div class="storeHead">' +
    '<div><h1>Kosmetik-Store</h1>' +
    '<div class="sub">Austattung f\u00fcr dein Profil \u2013 gekauft mit Kollegen-Points (sp\u00e4ter eintauschbar).</div></div>' +
    '<div class="row" id="walletRow"><span class="ptsChip" id="walletPts" style="display:none;">&#9733; 0</span>' +
    '<span class="lvlChip" id="walletLvl" style="display:none;">Level 1</span></div>' +
    '</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich an, um Kosmetik zu kaufen, auszur\u00fcsten und deine Sammlung zu sehen. Bereits im Launcher angemeldet? Dann bist du automatisch auch hier angemeldet.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="card showCard" id="showCard" style="display:none;">' +
    '<div class="showBanner" id="showBanner"></div>' +
    '<div class="showRow">' +
    '<div class="showAvWrap"><img id="showAv" class="showAv" alt=""/></div>' +
    '<div class="showInfo">' +
    '<div class="showName" id="showName"></div>' +
    '<div class="showSub" id="showSub"></div>' +
    '<div class="bar"><div id="showCollBar" style="width:0%;"></div></div>' +
    '<div class="showCollTxt"><span id="showCollTxt">0 gesammelt</span><span id="showCollPct">0%</span></div>' +
    '</div></div>' +
    '</div>' +
    '<div class="toolRow">' +
    '<div class="rarChips" id="rarChips">' +
    '<button type="button" class="chip on" data-r="alle">Alle Rarit\u00e4ten</button>' +
    '<button type="button" class="chip" data-r="common">Gew\u00f6hnlich</button>' +
    '<button type="button" class="chip" data-r="rare">Selten</button>' +
    '<button type="button" class="chip" data-r="epic">Episch</button>' +
    '<button type="button" class="chip" data-r="legendary">Legend\u00e4r</button>' +
    '</div>' +
    '<select class="sortSel" id="sortSel">' +
    '<option value="price-asc">Preis \u2191</option>' +
    '<option value="price-desc">Preis \u2193</option>' +
    '<option value="rarity">Seltenheit</option>' +
    '<option value="name">Name</option>' +
    '</select>' +
    '</div>' +
    '<div class="tabs" id="tabs">' +
    '<button type="button" class="tab on" data-f="alle">Alle</button>' +
    '<button type="button" class="tab" data-f="title">Titel</button>' +
    '<button type="button" class="tab" data-f="avatar_frame">Avatar-Rahmen</button>' +
    '<button type="button" class="tab" data-f="avatar_theme">Avatar-Hintergrund</button>' +
    '<button type="button" class="tab" data-f="badge">Abzeichen</button>' +
    '<button type="button" class="tab" data-f="profile_frame">Profil-Rahmen</button>' +
    '<button type="button" class="tab" data-f="profile_bg">Profil-Hintergrund</button>' +
    '<button type="button" class="tab" data-f="banner">Banner</button>' +
    '<button type="button" class="tab" data-f="profil_stil">Profilstil</button>' +
    '</div>' +
    '<div class="grid" id="grid"><div class="muted" id="gridMsg">Lade Store\u2026</div></div>' +
    '<div class="note card"><span style="font-size:1.2rem;">&#9432;</span>' +
    '<span class="muted">Kollegen-Points bekommst du als Startguthaben und sp\u00e4ter durch Aktivit\u00e4t dazu. Der Punkte-Tausch (Redeem) in echte Vorteile ist geplant \u2013 aktuell kannst du mit deinen Punkten ausschlie\u00dflich Kosmetik kaufen.</span></div>' +
    '<div class="card adminCard" id="adminCard" style="display:none;">' +
    '<div style="display:flex;justify-content:space-between;align-items:center;margin-top:0;">' +
    '<strong style="color:#ffd75f;">ADMIN \u00b7 Store-Management</strong>' +
    '<button type="button" class="ghost btn-sm" id="adminToggle">Einklappen</button></div>' +
    '<div id="adminBody">' +
    '<label>Nutzer suchen (Name / Code / ID)</label>' +
    '<input id="adminSearch" placeholder="z. B. FluffyBento oder C8B99EC051"/>' +
    '<div class="adminUsers" id="adminUsers"></div>' +
    '<div class="adminTarget" id="adminTarget" style="display:none;">' +
    '<div class="row"><strong id="adminTgtName"></strong><span class="muted" id="adminTgtCode"></span></div>' +
    '<div class="row" style="margin-top:.4rem;"><span class="ptsChip" id="adminTgtPts"></span><span class="lvlChip" id="adminTgtLvl"></span></div>' +
    '<div class="muted" id="adminTgtOwned" style="margin-top:.4rem;"></div>' +
    '<label>Punkte ver\u00e4ndern</label>' +
    '<div class="row">' +
    '<button type="button" class="secondary btn-sm" data-pts="100">+100</button>' +
    '<button type="button" class="secondary btn-sm" data-pts="500">+500</button>' +
    '<button type="button" class="secondary btn-sm" data-pts="1000">+1000</button>' +
    '<button type="button" class="secondary btn-sm" data-pts="-100">-100</button>' +
    '<input id="adminDelta" placeholder="+/- Punkte" style="flex:1;width:auto;min-width:90px;"/>' +
    '<button type="button" class="secondary btn-sm" id="adminDeltaBtn">Anwenden</button>' +
    '<button type="button" class="ghost btn-sm" id="adminResetBtn">Reset (250)</button>' +
    '</div>' +
    '<label>Kosmetik schenken</label>' +
    '<div class="row">' +
    '<select id="adminItem" style="flex:1;width:auto;"></select>' +
    '<button type="button" class="secondary btn-sm" id="adminGrantBtn">Schenken &amp; ausr\u00fcsten</button>' +
    '</div>' +
    '<div class="adminNote">Tipp: Klick auf einen Nutzer w\u00e4hlt ihn als Ziel. \u201eReset\u201c setzt Punkte auf 250 und leert die Ausr\u00fcstung.</div>' +
    '</div>' +
    '</div>' +
    '</div>' +
    '<a class="backLink" href="/">\u2190 Zur Startseite</a>' +
    '</div>' +
    '<div class="kmModal" id="kmModal" style="display:none;">' +
    '<div class="kmModalCard">' +
    '<button type="button" class="kmModalX" id="kmModalX">&times;</button>' +
    '<div id="kmModalBody"></div>' +
    '</div></div>' +
    '<script>' + KM_TOP_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var st=null,filter="alle",rar="alle",sort="price-asc";' +
    'var myDid=null;var admin=null,adminTarget=null;' +
    'var RAR={common:["Gew\u00f6hnlich","r-common"],rare:["Selten","r-rare"],epic:["Episch","r-epic"],legendary:["Legend\u00e4r","r-legendary"]};' +
    'var RANK={common:0,rare:1,epic:2,legendary:3};' +
    'function byId(id){var c=(st&&st.catalog)||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'function avUrlPlain(name){return "https://mc-heads.net/avatar/"+encodeURIComponent(name||"MHF_Steve").replace(/%20/g,"_")+"/96";}' +
    'function headUrlPlain(name){return "https://mc-heads.net/head/"+encodeURIComponent(name||"MHF_Steve").replace(/%20/g,"_")+"/128";}' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'window._av="https://mc-heads.net/avatar/MHF_Steve/64";' +
    'if(j&&j.user){' +
    'myDid=j.user.id;' +
    'if(j.user.isAdmin){initAdmin();}' +
    'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
    'if(p&&p.mcName){window._av=avUrlPlain(p.mcName);window._head=headUrlPlain(p.mcName);}' +
    'else if(j.user&&j.user.avatarUrl){window._av=j.user.avatarUrl;window._head=j.user.avatarUrl;}' +
    'load();' +
    '}).catch(function(){load();});}' +
    'else{load();}' +
    '}).catch(function(){load();});' +
    'function load(){' +
    'fetch("/api/profil/store").then(function(r){return r.json();}).then(function(d){' +
    'if(d.error){$("gridMsg").textContent="Store-Tempor\u00e4r nicht erreichbar. Bitte gleich nochmal versuchen.";return;}' +
    'st=d;' +
    'if(d.needsAuth){$("loginCard").style.display="block";$("showCard").style.display="none";}' +
    'else{' +
    '$("walletPts").style.display="";$("walletPts").textContent="\u2605 "+d.points;' +
    'if(typeof d.level==="number"){$("walletLvl").style.display="";$("walletLvl").textContent="Level "+d.level;}' +
    'var pv=document.getElementById("kmNavPtsVal");if(pv)pv.textContent=d.points;' +
    'renderShowcase();' +
    '}' +
    'fillAdminItems();' +
    'render();' +
    '}).catch(function(){ $("gridMsg").textContent="Store-Tempor\u00e4r nicht erreichbar."; });' +
    '}' +
    'function renderShowcase(){' +
    'var eq=st.equipped||{};var owned=(st.catalog||[]).filter(function(c){return c.owned;}).length;' +
    'var total=(st.catalog||[]).length;' +
    'var bn=byId(eq.banner);' +
    'var banner=$("showBanner");banner.style.background=bn&&bn.data&&bn.data.gradient?bn.data.gradient:"linear-gradient(90deg,#1a2234,#0d1420)";' +
    'var av=$("showAv");' +
    'var fr=byId(eq.avatar_frame),th=byId(eq.avatar_theme);' +
    'if(fr&&fr.data&&fr.data.color1){av.style.border="3px solid "+fr.data.color1;av.style.boxShadow="0 0 14px "+fr.data.color1+"66";}' +
    'else{av.style.border="0";av.style.boxShadow="none";}' +
    'if(th&&th.data&&th.data.gradient){av.style.background=th.data.gradient;}' +
    'av.src=window._head||window._av||"https://mc-heads.net/avatar/MHF_Steve/96";' +
    'var ti=byId(eq.title),bd=byId(eq.badge);' +
    'var name="Dein Profil";if(ti&&ti.data)name=ti.data.text+" \u00b7 "+(st.name||"Dein Profil");' +
    '$("showName").textContent=name;' +
    'var sub="Level "+st.level;' +
    'if(bd&&bd.data)sub+=" \u00b7 Abzeichen "+bd.data.icon;' +
    'sub+=" \u00b7 Code "+(st.code||"-");' +
    '$("showSub").textContent=sub;' +
    'var pct=total?Math.round(owned/total*100):0;' +
    '$("showCollBar").style.width=pct+"%";' +
    '$("showCollTxt").textContent=owned+" von "+total+" gesammelt";' +
    '$("showCollPct").textContent=pct+"%";' +
    '$("showCard").style.display="block";' +
    '}' +
    'function filtered(){' +
    'var items=(st.catalog||[]).filter(function(i){return (filter==="alle"||i.category===filter)&&(rar==="alle"||i.rarity===rar);});' +
    'var rk=RANK||{};' +
    'items.sort(function(a,b){' +
    'if(sort==="price-asc")return a.price-b.price;' +
    'if(sort==="price-desc")return b.price-a.price;' +
    'if(sort==="rarity"){var d=(rk[b.rarity]||-1)-(rk[a.rarity]||-1);return d||a.price-b.price;}' +
    'if(sort==="name")return (a.name||"").localeCompare(b.name||"");' +
    'return 0;});' +
    'return items;' +
    '}' +
    'function preview(item){' +
    'var av=window._av||"https://mc-heads.net/avatar/MHF_Steve/64";' +
    'if(item.category==="title"){return "<span class=\\"pvTitle\\">"+esc(item.data.text)+"</span>";}' +
    'if(item.category==="badge"){return "<span class=\\"pvBadgeTxt\\" style=\\"font-size:30px;color:"+esc(item.data.color)+"\\">"+esc(item.data.icon)+"</span>";}' +
    'if(item.category==="avatar_theme"){return "<div style=\\"width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:"+esc(item.data.gradient)+"\\"><img class=\\"pvHead\\" src=\\""+av+"\\"/></div>";}' +
    'if(item.category==="banner"){return "<div style=\\"width:88%;height:58px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#0a0d13;font:800 12px/1 Outfit,sans-serif;background:"+esc(item.data.gradient)+"\\">Banner</div>";}' +
    'if(item.category==="profile_bg"){return "<div style=\\"width:88%;height:78px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#c9d4e3;font:700 11px/1 Inter,sans-serif;background:"+esc(item.data.gradient)+"\\"><img class=\\"pvHead\\" style=\\"width:34px;height:34px;\\" src=\\""+av+"\\"/></div>";}' +
    'if(item.category==="profile_frame"){return "<div class=\\"pvMini\\" style=\\"border:4px solid "+esc(item.data.color1)+";box-shadow:0 0 12px "+esc(item.data.color1)+"44;background:#0d1420;\\"><img class=\\"pvHead\\" style=\\"width:40px;height:40px;\\" src=\\""+av+"\\"/></div>";}' +
    'if(item.category==="profil_stil"){return "<div class=\\"pvMini\\" style=\\"border:2px solid "+esc(item.data.accent)+";background:#0d1420;\\"><span style=\\"color:"+esc(item.data.accent)+";font:800 22px/1 Outfit,sans-serif;\\">Aa</span> <span style=\\"color:#c9d4e3;font-size:11px;font-weight:600;\\">"+esc(item.data.font)+"</span></div>";}' +
    'var border="",shadow="";' +
    'if(item.category==="avatar_frame"&&item.data.color1){border="border:"+(item.data.width||3)+"px solid "+item.data.color1+";";shadow="box-shadow:0 0 14px "+item.data.color1+"55;";}' +
    'return "<img class=\\"pvHead\\" style=\\""+border+shadow+"border-radius:14px;\\" src=\\""+av+"\\"/>";' +
    '}' +
    'function render(){' +
    'var grid=$("grid");grid.innerHTML="";' +
    'var items=filtered();' +
    'if(!items.length){grid.innerHTML="<div class=\\"muted\\">Keine Items in dieser Auswahl.</div>";return;}' +
    'var logged=!st.needsAuth;' +
    'items.forEach(function(item){' +
    'var card=document.createElement("div");card.className="card";' +
    'var rarArr=RAR[item.rarity]||["","r-common"];' +
    'var owned=logged&&item.owned?1:0;' +
    'var equipped=logged&&item.equippedCategory?1:0;' +
    'if(equipped)card.className+=" equipped";if(owned)card.className+=" owned";' +
    'var ribbon=item.featured?"<span class=\\"ribbon\\">&#9733; Highlight</span>":"";' +
    'var html="";' +
    'html+=ribbon;' +
    'html+="<div class=\\"pv\\">"+preview(item)+"</div>";' +
    'html+="<span class=\\"badge-medal rar "+rarArr[1]+"\\">"+rarArr[0]+"</span>";' +
    'html+="<div class=\\"cardBody\\"><div class=\\"cardTitle\\">"+esc(item.name)+"</div>";' +
    'html+="<div class=\\"cardDesc\\">"+esc(item.desc)+"</div>";' +
    'html+="<div class=\\"price\\">&#9733; "+item.price+"</div>";' +
    'card.innerHTML=html;' +
    'var btn=document.createElement("button");btn.className="cardBtn";' +
    'if(equipped){btn.textContent="Ausger\u00fcstet \u2713";btn.disabled=true;}' +
    'else if(owned){btn.textContent="Ausr\u00fcsten";btn.className+=" secondary";btn.setAttribute("data-act","equip");btn.setAttribute("data-item",item.id);}' +
    'else if(!logged){btn.textContent="Anmelden zum Kaufen";btn.className+=" ghost";btn.disabled=true;}' +
    'else{btn.textContent="Kaufen \u00b7 \u2605 "+item.price;if(item.price>st.points){btn.className+=" ghost";btn.disabled=true;}btn.setAttribute("data-act","buy");btn.setAttribute("data-item",item.id);}' +
    'btn.addEventListener("click",function(e){e.stopPropagation();var act=btn.getAttribute("data-act");var it=btn.getAttribute("data-item");if(act==="buy"){buy(it);}else if(act==="equip"){equip(it);}});' +
    'card.append(btn);' +
    'card.addEventListener("click",function(){openModal(item);});' +
    'grid.append(card);' +
    '});' +
    '}' +
    'function openModal(item){' +
    'var logged=!st.needsAuth;' +
    'var rarArr=RAR[item.rarity]||["","r-common"];' +
    'var h="";' +
    'if(item.featured)h+="<span class=\\"ribbon\\">&#9733; Highlight</span>";' +
    'h+="<div class=\\"pv\\">"+preview(item)+"</div>";' +
    'h+="<div class=\\"mmRar\\"><span class=\\"badge-medal "+rarArr[1]+"\\">"+rarArr[0]+"</span></div>";' +
    'h+="<div class=\\"mmTitle\\">"+esc(item.name)+"</div>";' +
    'h+="<div class=\\"mmDesc\\">"+esc(item.desc)+"</div>";' +
    'h+="<div class=\\"mmPrice price\\">&#9733; "+item.price+"</div>";' +
    'var owned=logged&&item.owned?1:0;var equipped=logged&&item.equippedCategory?1:0;' +
    'var btn="";' +
    'if(equipped){btn="<button class=\\"cardBtn\\" disabled>Ausger\u00fcstet \u2713</button>";}' +
    'else if(owned){btn="<button class=\\"cardBtn secondary\\" id=\\"mmBtn\\" data-mact=\\"equip\\">Ausr\u00fcsten</button>";}' +
    'else if(!logged){btn="<button class=\\"cardBtn ghost\\" disabled>Anmelden zum Kaufen</button>";}' +
    'else{btn="<button class=\\"cardBtn\\" id=\\"mmBtn\\" data-mact=\\"buy\\">Kaufen \u00b7 &#9733; "+item.price+"</button>";' +
    'if(item.price>st.points){btn="<button class=\\"cardBtn ghost\\" id=\\"mmBtn\\" disabled>Nicht genug &#9733; ("+st.points+")</button>";}}' +
    '$("kmModalBody").innerHTML=h+btn;' +
    'var mb=document.getElementById("mmBtn");' +
    'if(mb&&!mb.disabled){mb.addEventListener("click",function(){' +
    'var act=mb.getAttribute("data-mact");' +
    'if(act==="buy")buy(item.id);else if(act==="equip")equip(item.id);' +
    '});}' +
    '$("kmModal").style.display="flex";' +
    '}' +
    'function closeModal(){$("kmModal").style.display="none";}' +
    'function doAct(act,id){' +
    'var url=act==="buy"?"/api/profil/buy":"/api/profil/equip";' +
    'var body={};if(act==="buy"){body.item_id=id;}else{body.item_id=id;}' +
    'return fetch(url,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)}).then(function(r){return r.json();});' +
    '}' +
    'function buy(id){doAct("buy",id).then(function(j){' +
    'if(j&&j.ok){$("walletPts").textContent="\u2605 "+j.points;' +
    'closeModal();load();}' +
    'else{alert("Kauf fehlgeschlagen: "+((j&&j.error)||"unbekannt"));}' +
    '}).catch(function(){alert("Netzwerkfehler beim Kauf.");});' +
    '}' +
    'function equip(id){doAct("equip",id).then(function(j){' +
    'if(j&&j.ok){closeModal();load();}' +
    'else{alert("Ausr\u00fcsten fehlgeschlagen: "+((j&&j.error)||"unbekannt"));}' +
    '}).catch(function(){alert("Netzwerkfehler.");});' +
    '}' +
    '$("tabs").addEventListener("click",function(e){' +
    'var t=e.target.closest(".tab");if(!t)return;' +
    'document.querySelectorAll("#tabs .tab").forEach(function(x){x.classList.remove("on");});' +
    't.classList.add("on");filter=t.getAttribute("data-f");render();' +
    '});' +
    '$("rarChips").addEventListener("click",function(e){' +
    'var c=e.target.closest(".chip");if(!c)return;' +
    'document.querySelectorAll("#rarChips .chip").forEach(function(x){x.classList.remove("on");});' +
    'c.classList.add("on");rar=c.getAttribute("data-r");render();' +
    '});' +
    '$("sortSel").addEventListener("change",function(){sort=$("sortSel").value;render();});' +
    '$("kmModalX").addEventListener("click",closeModal);' +
    '$("kmModal").addEventListener("click",function(e){if(e.target===$("kmModal"))closeModal();});' +
    'document.addEventListener("keydown",function(e){if(e.key==="Escape")closeModal();});' +
    'function initAdmin(){fetch("/api/profil/admin/users").then(function(r){return r.json();}).then(function(u){' +
    '$("adminCard").style.display="block";admin=Array.isArray(u)?u:[];renderAdmin();fillAdminItems();' +
    '}).catch(function(){});}' +
    'function fillAdminItems(){' +
    'var sel=$("adminItem");if(!sel||!st)return;' +
    'sel.innerHTML="";' +
    '(st.catalog||[]).forEach(function(item){' +
    'var o=document.createElement("option");o.value=item.id;o.textContent=item.name+" ("+item.category+")";sel.append(o);' +
    '});' +
    '}' +
    'function renderAdmin(){' +
    'var q=($("adminSearch").value||"").toLowerCase();' +
    'var wrap=$("adminUsers");wrap.innerHTML="";' +
    'var list=(admin||[]).filter(function(u){' +
    'if(!q)return true;' +
    'var h=[u.name,u.discordName,u.code,u.id].map(function(x){return String(x||"").toLowerCase();}).join(" ");return h.indexOf(q)>=0;' +
    '});' +
    'if(!list.length){wrap.innerHTML="<div class=\\"adminNote\\">Keine Treffer.</div>";return;}' +
    'list.forEach(function(u){' +
    'var r=document.createElement("div");r.className="adminU";if(adminTarget===u.discordId)r.className+=" on";' +
    'var n=document.createElement("span");n.className="auName";n.textContent=(u.name||u.discordName||("User #"+u.id))+(u.discordId===myDid?" (du)":"");' +
    'var c=document.createElement("span");c.className="muted";c.textContent=u.code;' +
    'var p=document.createElement("span");p.className="auPts";p.textContent="\u2605 "+u.points;' +
    'r.append(n,c,p);' +
    'r.addEventListener("click",function(){"use strict"?"":"";selectTarget(u.discordId);});' +
    'wrap.append(r);' +
    '});' +
    'if(adminTarget){showTarget();}' +
    '}' +
    'function selectTarget(did){adminTarget=did;renderAdmin();showTarget();}' +
    'function showTarget(){' +
    'var u=null;for(var i=0;admin&&i<admin.length;i++){if(admin[i].discordId===adminTarget){u=admin[i];break;}}' +
    'if(!u){$("adminTarget").style.display="none";return;}' +
    '$("adminTarget").style.display="block";' +
    '$("adminTgtName").textContent=(u.name||u.discordName)+((u.discordId===myDid)?" (du)":"");' +
    '$("adminTgtCode").textContent="Code "+u.code;' +
    '$("adminTgtPts").textContent="\u2605 "+u.points;' +
    '$("adminTgtLvl").textContent="Level "+u.level;' +
    '$("adminTgtOwned").textContent=(u.cosmetics?u.cosmetics.length:0)+" Items im Inventar";' +
    '}' +
    '$("adminSearch").addEventListener("input",renderAdmin);' +
    '$("adminToggle").addEventListener("click",function(){' +
    'var b=$("adminBody");var hidden=b.style.display==="none";' +
    'b.style.display=hidden?"block":"none";' +
    '$("adminToggle").textContent=hidden?"Einklappen":"Aufklappen";' +
    '});' +
    '$("adminUsers").addEventListener("click",function(e){' +
    'var r=e.target.closest(".adminU");if(!r)return;' +
    'var i=Array.prototype.indexOf.call(r.parentNode.children,r);' +
    'var q=($("adminSearch").value||"").toLowerCase();var list=(admin||[]).filter(function(u){' +
    'if(!q)return true;var h=[u.name,u.discordName,u.code,u.id].map(function(x){return String(x||"").toLowerCase();}).join(" ");return h.indexOf(q)>=0;});' +
    'var u=list[i];' +
    'if(u){if(u.discordId===adminTarget)adminTarget=null;else selectTarget(u.discordId);}' +
    '});' +
    'function applyPts(delta){' +
    'if(!adminTarget)return;fetch("/api/profil/admin/points",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({discordId:adminTarget,delta:delta})})' +
    '.then(function(r){return r.json();}).then(function(j){if(j&&j.ok){refreshAdmin();load();}else{alert("Fehler: "+((j&&j.error)||"?"));}}).catch(function(){alert("Netzwerkfehler");});' +
    '}' +
    'document.querySelectorAll(".adminTarget [data-pts]").forEach(function(btn){' +
    'btn.addEventListener("click",function(){applyPts(Number(btn.getAttribute("data-pts")));});' +
    '});' +
    '$("adminDeltaBtn").addEventListener("click",function(){' +
    'var d=Number($("adminDelta").value);if(!(d&&isFinite(d)))return;applyPts(Math.round(d));$("adminDelta").value="";' +
    '});' +
    '$("adminResetBtn").addEventListener("click",function(){' +
    'if(!adminTarget)return;if(!confirm("Punkte auf 250 setzen und Ausr\u00fcstung leeren?"))return;' +
    'fetch("/api/profil/admin/reset",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({discordId:adminTarget})})' +
    '.then(function(r){return r.json();}).then(function(j){if(j&&j.ok){refreshAdmin();load();}else{alert("Fehler: "+((j&&j.error)||"?"));}}).catch(function(){alert("Netzwerkfehler");});' +
    '});' +
    '$("adminGrantBtn").addEventListener("click",function(){' +
    'if(!adminTarget)return;var itemId=$("adminItem").value;if(!itemId)return;' +
    'fetch("/api/profil/admin/grant",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({discordId:adminTarget,item_id:itemId})})' +
    '.then(function(r){return r.json();}).then(function(j){if(j&&j.ok){refreshAdmin();load();}else{alert("Fehler: "+((j&&j.error)||"?"));}}).catch(function(){alert("Netzwerkfehler");});' +
    '});' +
    'function refreshAdmin(){fetch("/api/profil/admin/users").then(function(r){return r.json();}).then(function(u){admin=Array.isArray(u)?u:[];renderAdmin();}).catch(function(){});}' +
    '})();' +
    '</script>';

  return pageShell('Store', css, html);
}

// ── Freunde-Seite ───────────────────────────────────────────────────────────
function buildFreundePage() {
  const css = SHARED_CSS +
    '#wrap{border:0;background:transparent;box-shadow:none;}' +
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
    '.badgeGlyph{margin-right:5px;font-weight:800;}' +
    '.frBtns{display:flex;gap:.4rem;flex:none;align-items:center;}' +
    '.frBtns .btn-sm{margin:0;font-size:.78rem;padding:.42rem .7rem;}' +
    '.frBtns a.linkBtn{display:inline-flex;align-items:center;text-decoration:none;background:#1c2740;color:#c7cfdd;' +
    'border:1px solid #2c3b57;border-radius:8px;font-weight:600;transition:background .15s,color .15s;}' +
    '.frBtns a.linkBtn:hover{background:#26355a;color:#fff;}';

  const html =
    '<div id="wrap">' +
    '<h1>Freunde</h1>' +
    '<div class="sub">Dein Kollegen-Netzwerk \u2013 online auf einem Joint-Server, in Steam-Profil-Optik.</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an, um Freunde zu sehen und hinzuzuf\u00fcgen.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="card" id="meCard" style="display:none;">' +
    '<label>Dein Freundes-Code</label>' +
    '<div class="codeBox"><span class="code" id="myCode">\u2013</span>' +
    '<button type="button" class="secondary btn-sm" id="copyBtn">Kopieren</button></div>' +
    '<label>Freund hinzuf\u00fcgen (per Code)</label>' +
    '<div class="row"><input id="addCode" placeholder="z. B. C8B99EC051" style="flex:1;width:auto;text-transform:uppercase;"/>' +
    '<button type="button" class="secondary" id="addBtn">Hinzuf\u00fcgen</button></div>' +
    '<div class="muted" id="addHint" style="margin-top:.5rem;"></div>' +
    '</div>' +
    '<div class="card" id="reqCard" style="display:none;">' +
    '<strong style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.55rem;">' +
    'Freundesanfragen <span class="muted" id="reqCount" style="font-size:.8rem;"></span></strong>' +
    '<div id="reqWrap"></div>' +
    '</div>' +
    '<div class="card" id="listCard" style="display:none;">' +
    '<div id="listHeader" style="display:flex;justify-content:space-between;align-items:center;margin-bottom:.4rem;">' +
    '<strong id="listTitle"></strong><span class="muted" id="listCount"></span></div>' +
    '<div id="listWrap"><div class="empty">Lade Freunde\u2026</div></div>' +
    '</div>' +
    '<a class="backLink" href="/">\u2190 Zur Startseite</a>' +
    '</div>' +
    '<script>' + KM_TOP_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var me=$("meCard"),login=$("loginCard"),listCard=$("listCard");' +
    'var cat=null;' +
    'function byId(id){var c=cat||[];for(var i=0;i<c.length;i++){if(c[i].id===id)return c[i];}return null;}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'function avFor(f){return "https://mc-heads.net/head/"+encodeURIComponent(f.name||"MHF_Steve").replace(/%20/g,"_")+"/128";}' +
    'function enc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
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
    'loadRequests();' +
    'fetch("/api/profil/friends").then(function(r){return r.json();}).then(function(list){' +
    'listCard.style.display="block";' +
    '$("listTitle").textContent="Deine Kollegen";' +
    '$("listCount").textContent=list.length+" Freund"+(list.length===1?"":"e");' +
    'var wrap=$("listWrap");wrap.innerHTML="";' +
    'if(!list.length){wrap.innerHTML="<div class=\\"empty\\">Noch keine Freunde. Teile deinen Code (oben) oder f\u00fcge einen Code hinzu.</div>";return;}' +
    'list.forEach(function(f){' +
    'var row=document.createElement("div");row.className="frRow";' +
    'var av=document.createElement("img");av.className="frAv";av.src=avFor(f);av.alt="";av.onerror=function(){av.src="https://mc-heads.net/head/MHF_Steve/128";};' +
    'var eq=f.equipped||{};' +
    'var frame=byId(eq.avatar_frame);' +
    'if(frame&&frame.data&&frame.data.color1){av.style.border="3px solid "+frame.data.color1;av.style.boxShadow="0 0 12px "+frame.data.color1+"66";}' +
    'var info=document.createElement("div");info.className="frInfo";' +
    'var nm=document.createElement("div");nm.className="frName";' +
    'var titleTxt="";var ti=byId(eq.title);if(ti&&ti.data)titleTxt=ti.data.text+" \u00b7 ";' +
    'nm.textContent=titleTxt+(f.name||("User #"+f.id));' +
    'var gl="";' +
    'if(eq.badge){var b=byId(eq.badge);if(b)gl="<span class=\\"badgeGlyph\\" style=\\"color:"+esc(b.data.color||"#fff")+"\\">"+esc(b.data.icon)+"</span>";}' +
    'var sub=document.createElement("div");sub.className="frSub";' +
    'sub.innerHTML="<span class=\\"dot "+(f.online?"on":"off")+"\\"></span>"+gl+"Level "+esc(f.level)+"\\u00b7 "+esc(f.server||"Offline")+" \\u00b7 Code "+esc(f.code);' +
    'info.append(nm,sub);' +
    'var btns=document.createElement("div");btns.className="frBtns";' +
    'btns.innerHTML="<a class=\\"btn-sm linkBtn\\" href=\\"/u/"+encodeURIComponent(f.code||"")+"\\">Profil</a>"+' +
    '"<a class=\\"btn-sm linkBtn\\" href=\\"/dm?to="+encodeURIComponent(f.code||"")+"\\">Nachricht</a>";' +
    'var rem=document.createElement("button");rem.className="secondary btn-sm";rem.textContent="Entfernen";' +
    'rem.addEventListener("click",function(){' +
    'if(!confirm("Freund entfernen?"))return;' +
    'fetch("/api/profil/friend-remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({target_id:f.id})})' +
    '.then(function(r){return r.json();}).then(function(x){load();}).catch(function(){});' +
    '});' +
    'row.append(av,info,btns,rem);' +
    'wrap.append(row);' +
    '});' +
    '}).catch(function(){ $("listWrap").innerHTML="<div class=\\"empty\\">Freunde-Liste tempor\u00e4r nicht erreichbar.</div>"; });' +
    '}' +
    'function loadRequests(){' +
    'var card=$("reqCard");if(!card)return;' +
    'fetch("/api/profil/friend-requests").then(function(r){return r.json();}).then(function(reqs){' +
    'reqs=Array.isArray(reqs)?reqs:[];' +
    'var wrap=$("reqWrap");wrap.innerHTML="";' +
    'if(!reqs.length){card.style.display="none";return;}' +
    'card.style.display="block";' +
    '$("reqCount").textContent=reqs.length+" neu"+(reqs.length===1?"":"e");' +
    'reqs.forEach(function(it){' +
    'var f=it.user||{};' +
    'var row=document.createElement("div");row.className="frRow";' +
    'var av=document.createElement("img");av.className="frAv";av.src=avFor(f);av.alt="";av.onerror=function(){av.src="https://mc-heads.net/head/MHF_Steve/128";};' +
    'var info=document.createElement("div");info.className="frInfo";' +
    'var nm=document.createElement("div");nm.className="frName";nm.textContent=f.name||("User #"+(f.id||""));' +
    'var sub=document.createElement("div");sub.className="frSub";' +
    'sub.innerHTML="<span class=\\"dot "+(f.online?"on":"off")+"\\"></span>"+esc(f.server||(f.online?"Online":"Offline"))+" \\u00b7 Code "+esc(f.code||"");' +
    'info.append(nm,sub);' +
    'var btns=document.createElement("div");btns.className="frBtns";' +
    'var ok=document.createElement("button");ok.className="btn-sm";ok.textContent="Annehmen";' +
    'ok.addEventListener("click",function(){' +
    'fetch("/api/profil/friend-accept",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({from_id:it.request.from})})' +
    '.then(function(r){return r.json();}).then(function(x){if(x&&x.ok){load();}else{sub.textContent="Fehler: "+((x&&x.error)||"?");}}).catch(function(){sub.textContent="Netzwerkfehler.";});' +
    '});' +
    'var no=document.createElement("button");no.className="secondary btn-sm";no.textContent="Ablehnen";' +
    'no.addEventListener("click",function(){' +
    'fetch("/api/profil/friend-decline",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({from_id:it.request.from})})' +
    '.then(function(r){return r.json();}).then(function(){load();}).catch(function(){sub.textContent="Netzwerkfehler.";});' +
    '});' +
    'btns.append(ok,no);' +
    'row.append(av,info,btns);' +
    'wrap.append(row);' +
    '});' +
    '}).catch(function(){});' +
    '}' +
    '$("copyBtn").addEventListener("click",function(){' +
    'var c=$("myCode").textContent;if(!c||c==="\u2013")return;' +
    'if(navigator.clipboard&&navigator.clipboard.writeText){navigator.clipboard.writeText(c).catch(function(){});}' +
    '$("addHint").textContent="Code kopiert!";setTimeout(function(){$("addHint").textContent="";},1500);' +
    '});' +
    '$("addBtn").addEventListener("click",function(){' +
    'var code=$("addCode").value.trim().toUpperCase();' +
    'if(!code){$("addHint").textContent="Bitte Code eingeben.";return;}' +
    '$("addHint").textContent="F\u00fcge hinzu\u2026";' +
    'fetch("/api/profil/friend-add",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({code:code})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){' +
    'if(j.accepted){$("addHint").textContent="Ihr seid jetzt Freunde! (Gr\u00fcn = online)";}' +
    'else{$("addHint").textContent="Anfrage gesendet \u2013 wartet auf Best\u00e4tigung.";}' +
    '$("addCode").value="";load();}' +
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

// ── Öffentliches Kollegen-Profil: /u/<Code> ─────────────────────────────────
function buildUserPage(code) {
  const css =
    '#wrap{border:1px solid rgba(255,255,255,.06);border-radius:18px;background:rgba(9,11,18,.66);box-shadow:0 12px 44px rgba(0,0,0,.45);}' +
    '.ubBanner{display:none;height:120px;border-radius:14px;margin-bottom:1.1rem;position:relative;overflow:hidden;background-size:cover;background-position:center;}' +
    '.ubBanner .bl{position:absolute;left:12px;bottom:10px;color:#0a0d13;font:800 13px/1 Outfit,Inter,sans-serif;background:rgba(255,255,255,.72);padding:4px 10px;border-radius:999px;}' +
    '.ubRow{display:flex;gap:1.1rem;align-items:flex-start;flex-wrap:wrap;}' +
    '.ubAvatar{width:110px;height:110px;border-radius:22px;object-fit:cover;background:#151d2b;flex:none;}' +
    '.ubMeta{flex:1;min-width:220px;}' +
    '.ubName{font:800 32px/1.1 Outfit,Inter,sans-serif;color:#ffd75f;letter-spacing:.01em;}' +
    '.ubBadge{font-size:22px;margin-right:6px;vertical-align:1px;}' +
    '.ubChips{display:flex;gap:.5rem;flex-wrap:wrap;margin-top:.7rem;}' +
    '.ubChips>span{border-color:color-mix(in srgb,var(--ubAccent,#ffd75f) 32%,transparent)!important;}' +
    '.ubBio{margin-top:1rem;padding:1rem;border-radius:12px;background:#0d1420;border:1px solid #1c2636;border-left:3px solid color-mix(in srgb,var(--ubAccent,#ffd75f) 60%,transparent);color:#c9d4e3;line-height:1.5;}' +
    '.ubActions button{border-color:color-mix(in srgb,var(--ubAccent,#ffd75f) 60%,transparent);}' +
    '#ubTitle{color:var(--ubAccent,#D4AF37);}' +
    '.ubEquips{display:grid;grid-template-columns:1fr 1fr;gap:.55rem;margin-top:1.1rem;}@media(max-width:560px){.ubEquips{grid-template-columns:1fr;}}' +
    '.ueTile{border-radius:9px;overflow:hidden;min-width:0;border:1px solid rgba(255,255,255,.06);}' +
    '.ubActions{display:flex;gap:.5rem;flex-wrap:wrap;margin-top:.9rem;}' +
    '.ubActions a,.ubActions button{margin:0;text-decoration:none;}' +
    '.ubP{color:#8f9aab;font-size:.82rem;margin-top:1rem;}' +
    '.errBig{padding:2.5rem 0;text-align:center;color:#8f9aab;font-size:1.1rem;}';

  const html =
    '<div id="wrap">' +
    '<div class="ubBanner" id="ubBanner"><span class="bl" id="ubBannerLabel"></span></div>' +
    '<h1 id="ubTitle">Kollegen-Profil</h1>' +
    '<div class="sub">Lade Profil f\u00fcr Code ' + code + '\u2026</div>' +
    '<div class="card"><div class="ubRow">' +
    '<img class="ubAvatar" id="ubAvatar" alt=""/>' +
    '<div class="ubMeta">' +
    '<div class="ubName" id="ubName"></div>' +
    '<div class="ubChips" id="ubChips"></div>' +
    '<div class="muted" id="ubSub"></div>' +
    '<div class="ubBio" id="ubBio" style="display:none;"></div>' +
    '<div class="ubEquips" id="ubEquips" style="display:none;"></div>' +
    '<div class="ubActions" id="ubActions"></div>' +
    '<div class="ubP" id="ubP"></div>' +
    '</div></div></div>' +
    '<a class="backLink" href="/">\u2190 Zur Startseite</a>' +
    '</div>' +
    '<script>' + KM_TOP_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'var CODE="' + code + '";var CURRENT=null;' +
    'function headUrl(u,d){' +
    'if(d&&d.avatar_data_url){return d.avatar_data_url;}' +
    'if(u){return "https://mc-heads.net/head/"+u+"/256";}' +
    'return "https://mc-heads.net/head/MHF_Steve/256";}' +
    'function load(){' +
    'fetch("/api/profil/profile-view?code="+encodeURIComponent(CODE)).then(function(r){' +
    'if(r.status===404){$("ubTitle").textContent="Nicht gefunden";' +
    '$("ubTitle").parentElement.querySelector(".sub").innerHTML="<div class=\\"errBig\\">Kein Kollege mit diesem Code. Code pr\u00fcfen oder in Steam befreunden.</div>";return;}return r.json();' +
    '}).then(function(p){' +
    'if(!p)return;CURRENT=p;chips(p);' +
    '}).catch(function(){$("ubTitle").textContent="Fehler";$("ubTitle").parentElement.querySelector(".sub").textContent="Profil tempor\u00e4r nicht erreichbar.";});' +
    '}' +
'function chips(p){' +
    'var eq=p.equipped||{};' +
    'var all=p.owned||[];' +
    'function it(id){for(var k in eq){var e=eq[k];if(e&&e.id===id)return {id:id,data:(e.data||null)};}for(var i=0;i<all.length;i++){if(all[i].id===id)return {id:id,data:(all[i].data||null)};}return null;}' +
    'var fr=it(eq.avatar_frame),th=it(eq.avatar_theme),bd=it(eq.badge),ti=it(eq.title),st=it(eq.profil_stil),nc=it(eq.name_color),sk=it(eq.sticker),fn=it(eq.font);' +
    'var av=$("ubAvatar");' +
    'var bdCss="",sh="",bg="";' +
    'if(fr&&fr.data&&fr.data.color1){bdCss="border:"+((fr.data.width||4))+"px solid "+fr.data.color1+";";sh="box-shadow:0 0 22px "+fr.data.color1+"66;";}' +
    'if(th&&th.data&&th.data.gradient){bg="background:"+th.data.gradient+";";}' +
    'av.style.cssText=bdCss+sh+bg+"border-radius:22px;";' +
    'av.src=headUrl(p.uuid,p);' +
    'av.alt="";av.onerror=function(){av.src="https://mc-heads.net/head/MHF_Steve/256";};' +
    'var nameCmp=(ti&&ti.data)?ti.data.text+" \\u00b7 ":" ";' +
    'var badge=(bd&&bd.data)?"<span class=\\"ubBadge\\" style=\\"color:"+esc(bd.data.color)+"\\">"+esc(bd.data.icon)+"</span>":"";' +
    'var stick=(sk&&sk.data)?"<span class=\\"ubBadge\\" style=\\"color:"+esc(sk.data.color)+"\\">"+esc(sk.data.icon)+"</span>":"";' +
    'var accent=nc&&nc.data&&nc.data.accent?nc.data.accent:(st&&st.data&&st.data.accent?st.data.accent:"#ffd75f");' +
    'var nmEl=$("ubName");' +
    'nmEl.innerHTML=badge+"<span style=\\"color:"+esc(accent)+"\\">"+esc(nameCmp)+"</span>"+esc(p.name||("User #"+p.id))+stick;' +
    'if(fn&&fn.data&&fn.data.font){nmEl.style.fontFamily=fn.data.font;}' +
    'var cw=$("ubChips");cw.innerHTML="";' +
    'function chip(cls,txt){var d=document.createElement("span");d.className=cls;d.textContent=txt;cw.append(d);}' +
    'chip("ptsChip","\\u2605 Level "+p.level);' +
    'var sp=p.online?p.server:null;' +
    'chip("lvlChip","<dot>");cw.lastChild.innerHTML="<span style=\\"color:"+(p.online?"#3fb950":"#4b5563")+";\\">\\u25cf</span> "+(p.online?"Online \\u00b7 "+esc(sp||"Server"):"Offline");' +
    'chip("lvlChip","Code "+esc(p.code));' +
    'if(p.isFriend)chip("lvlChip","\\u2605 Freund");' +
    '$("ubSub").textContent="Freundes-Code "+esc(p.code)+(p.isFriend?" \\u00b7 Du bist mit ihm befreundet":"");' +
    'initActions(p);' +
    'initCosmetics(p);' +
    'renderEquips(p);' +
    '}' +
    'function renderEquips(p){' +
    'var box=$("ubEquips");box.innerHTML="";' +
    'var cs=p.equipped||{};' +
    'var CATS=[["title","Titel"],["badge","Abzeichen"],["avatar_theme","Avatar-Hintergrund"],["avatar_frame","Avatar-Rahmen"],["profile_bg","Profil-Hintergrund"],["profile_frame","Profil-Rahmen"],["banner","Banner"],["sticker","Sticker"],["name_color","Namensfarbe"],["font","Schrift"],["profil_stil","Profil-Stil"]];' +
    'var shown=0;' +
    'for(var i=0;i<CATS.length;i++){' +
    'var e=cs[CATS[i][0]];if(!e)continue;var d=e.data||{};' +
    'var nm=(e.name&&e.name.concat?e.name:"")||(d.text||"");if(!nm)continue;' +
    'var sw="",ic="",icst="";' +
    'if(d.gradient){sw="background:"+d.gradient+";";}' +
    'else if(d.color1){sw="background:linear-gradient(135deg,"+esc(d.color1)+","+esc(d.color2||d.color1)+");";}' +
    'else if(d.accent){sw="background:"+d.accent+";";}' +
    'else if(d.icon){ic=esc(d.icon);icst="color:"+esc(d.color)+";";}' +
    'else if(d.font){ic="Aa";icst="font-family:"+esc(d.font)+";";}' +
    'var band=ic?"<div style=\\"height:36px;background:rgba(255,255,255,.07);display:flex;align-items:center;justify-content:center;font-size:16px;"+icst+"\\">"+ic+"</div>":"<div style=\\"height:36px;"+(sw||"background:rgba(255,255,255,.07);")+"\\"></div>";' +
    'var tile=document.createElement("div");tile.className="ueTile";tile.innerHTML=band+' +
    '"<div style=\\"display:flex;align-items:center;gap:.4rem;padding:.4rem .5rem;background:rgba(255,255,255,.03);\\">"+"<span style=\\"color:#8f9aab;font-size:.62rem;letter-spacing:.04em;flex:none;text-transform:uppercase;\\">"+esc(CATS[i][1])+"</span>"+"<span style=\\"color:#e6ecf5;font-weight:600;font-size:.74rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;\\">"+esc(nm)+"</span>"+"</div>";' +
    'box.append(tile);shown++;' +
    '}' +
    'box.style.display=shown?"grid":"none";' +
    '}' +
    'function stc(){var p=CURRENT||{};var cs=p.equipped||{};var it=p.owned||[];' +
    'function find(id){for(var k in cs){var e=cs[k];if(e&&e.id===id)return e.data||null;}for(var i=0;i<it.length;i++){if(it[i].id===id)return it[i].data||null;}return null;}' +
    'var st=find(cs.profil_stil);' +
    'document.documentElement.style.setProperty("--ubAccent",(st&&st.accent)||"#ffd75f");' +
    'var hs=document.querySelectorAll("h1");for(var i=0;i<hs.length;i++){hs[i].style.color=(st&&st.accent)||"#D4AF37";}' +
    'var bg=find(cs.profile_bg);if(bg&&bg.gradient){document.body.style.background=bg.gradient+" fixed";}' +
    'var bn=find(cs.banner);var strip=$("ubBanner");' +
    'if(bn&&bn.gradient){strip.style.background=bn.gradient;strip.style.display="block";$("ubBannerLabel").textContent="Banner";}' +
    'else if(p.banner_data_url){strip.style.backgroundImage="url("+p.banner_data_url+")";strip.style.display="block";$("ubBannerLabel").textContent="Profil-Banner";}' +
    'else{strip.style.display="none";}' +
    'var pf=find(cs.profile_frame);var wr=$("wrap");' +
    'if(pf&&pf.color1){wr.style.border="2px solid "+pf.color1;wr.style.boxShadow="0 0 30px "+pf.color1+"44,0 12px 44px rgba(0,0,0,.45)";}' +
    '}' +
    'function initCosmetics(p){stc();' +
    'if(p.bio){$("ubBio").style.display="block";$("ubBio").textContent=p.bio;}' +
    'else if(!p.isViewer){$("ubP").textContent="Dieses Profil ist privat \u2013 Bio nur f\u00fcr Freunde sichtbar.";}' +
    '}' +
    'function initActions(p){' +
    'var box=$("ubActions");box.innerHTML="";' +
    'if(p.isViewer){' +
    'var a=document.createElement("a");a.href="/profil";a.innerHTML="<button type=\\"button\\">Zu deinem Profil</button>";box.append(a);return;}' +
    'if(logged()){' +
    'if(p.isFriend){' +
    'var dm=document.createElement("a");dm.href="/dm?to="+encodeURIComponent(p.code)+"&n="+encodeURIComponent(p.name);dm.innerHTML="<button type=\\"button\\">Nachricht senden</button>";box.append(dm);' +
    'var rm=document.createElement("button");rm.type="button";rm.className="secondary";rm.textContent="Freund entfernen";' +
    'rm.addEventListener("click",function(){' +
    'if(!confirm("Freund entfernen?"))return;' +
    'fetch("/api/profil/friend-remove",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({target_id:p.id})})' +
    '.then(function(r){return r.json();}).then(function(){load();}).catch(function(){});});box.append(rm);' +
    '}' +
    'else{' +
    'var ad=document.createElement("button");ad.type="button";ad.textContent="Freund hinzuf\u00fcgen";' +
    'ad.addEventListener("click",function(){' +
    'ad.disabled=true;ad.textContent="Sende\u2026";' +
    'fetch("/api/profil/friend-add",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({code:p.code})})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){if(j.accepted){ad.textContent="Ihr seid jetzt Freunde!";setTimeout(load,1200);}else{ad.textContent="Anfrage gesendet \u2713";}}' +
    'else{ad.disabled=false;ad.textContent="Freund hinzuf\u00fcgen";alert("Fehler: "+((j&&j.error)||"?"));}' +
    '}).catch(function(){ad.disabled=false;ad.textContent="Freund hinzuf\u00fcgen";alert("Netzwerkfehler");});});box.append(ad);' +
    '}}' +
    'else{' +
    'var l=document.createElement("a");l.href="/api/auth/discord/login";l.innerHTML="<button type=\\"button\\">Anmelden zum Befreunden</button>";box.append(l);' +
    '}' +
    '}' +
    'function logged(){var el=document.getElementById("kmLogin");return el&&el.style.display==="none";}' +
    'load();' +
    '})();' +
    '</script>';

  return pageShell('Profil von ' + code, css, html);
}

// ── Nachrichten (DMs): /dm ───────────────────────────────────────────────────
function buildDmPage() {
  const css =
    '#wrap{border:0;background:transparent;box-shadow:none;}' +
    '.dmCols{display:grid;grid-template-columns:260px 1fr;gap:1rem;align-items:start;margin-top:.6rem;}' +
    '@media(max-width:760px){.dmCols{grid-template-columns:1fr;}}' +
    '.convCard{padding:0;overflow:hidden;}' +
    '.convHead{padding:.8rem 1rem;border-bottom:1px solid #1c2636;font-weight:800;color:#ffd75f;}' +
    '.conv{padding:.6rem .9rem;border-bottom:1px solid #141c2a;cursor:pointer;display:flex;gap:.7rem;align-items:center;transition:background .15s;}' +
    '.conv:hover{background:#16203a;}' +
    '.conv.on{background:#1c2740;}' +
    '.convAv{width:40px;height:40px;border-radius:10px;object-fit:cover;background:#151d2b;flex:none;}' +
    '.convInfo{flex:1;min-width:0;}' +
    '.convName{font-weight:700;font-size:.9rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}' +
    '.convLast{color:#8f9aab;font-size:.75rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;margin-top:2px;}' +
    '.dmMsgs{display:flex;flex-direction:column;gap:.5rem;padding:1rem;max-height:460px;overflow-y:auto;min-height:260px;}' +
    '.dmB{max-width:75%;padding:.6rem .9rem;border-radius:14px;font-size:.9rem;line-height:1.45;word-break:break-word;}' +
    '.dmB.me{background:linear-gradient(135deg,#D4AF37,#b8860b);color:#0a0d13;align-self:flex-end;border-bottom-right-radius:4px;}' +
    '.dmB.oth{background:#1c2740;color:#e3e9f2;align-self:flex-start;border-bottom-left-radius:4px;}' +
    '.dmT{font-size:.7rem;color:#8f9aab;margin:.35rem 0 0;text-align:right;}' +
    '.dmInput{display:flex;gap:.5rem;padding:.8rem;border-top:1px solid #141c2a;}' +
    '.dmInput input{flex:1;width:auto;}' +
    '.dmInput button{margin:0;}' +
    '.dmEmpty{text-align:center;color:#8f9aab;padding:2.5rem 1rem;}' +
    '.dmThreadHead{display:flex;align-items:center;gap:.7rem;padding:.8rem 1rem;border-bottom:1px solid #1c2636;}' +
    '.dmThreadHead img{width:36px;height:36px;border-radius:9px;object-fit:cover;background:#151d2b;}' +
    '.dmThreadHead .tname{font-weight:800;flex:1;min-width:0;}' +
    '.dmThreadHead a{font-size:.78rem;font-weight:600;}';

  const html =
    '<div id="wrap">' +
    '<h1>Nachrichten</h1>' +
    '<div class="sub">Privater Chat mit deinen Freunden\u2026</div>' +
    '<div class="card loginCard" id="loginCard">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an, um Nachrichten zu lesen und zu schreiben.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="dmCols" id="dmCols" style="display:none;">' +
    '<div class="card convCard">' +
    '<div class="convHead">Chats</div>' +
    '<div id="convList"><div class="dmEmpty">Keine Chats.</div></div>' +
    '<div style="padding:.8rem 1rem;border-top:1px solid #141c2a;">' +
    '<div class="muted" style="margin-bottom:.3rem;">Ziel-Code \u00f6ffnen</div>' +
    '<input id="convCode" placeholder="z. B. C8B99EC051" style="text-transform:uppercase;"/>' +
    '</div></div>' +
    '<div class="card" id="threadCard">' +
    '<div class="dmThreadHead">' +
    '<img id="thAv" alt=""/>' +
    '<div class="tname" id="thName">W\u00e4hle einen Chat</div>' +
    '<a id="thProf" href="#" style="display:none;">Profil</a>' +
    '</div>' +
    '<div class="dmMsgs" id="dmMsgs"><div class="dmEmpty">Kein Chat ausgew\u00e4hlt. Tipp: \u00d6ffne das Profil eines Freundes oder f\u00fcge oben links rechts einen Code ein.</div></div>' +
    '<div class="dmInput" id="dmInputBox" style="display:none;">' +
    '<input id="dmText" placeholder="Nachricht \u2026" maxlength="2000"/>' +
    '<button type="button" id="dmSend">Senden</button>' +
    '</div>' +
    '</div>' +
    '</div>' +
    '</div>' +
    '<script>' + KM_TOP_SCRIPT +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'function esc(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
    'var me=null,meDid="",current=null,pollTimer=null;' +
    'function maybe(obj){return obj||{};}' +
    'function tstr(ts){if(!ts)return "";var d=new Date(ts);' +
    'var pad=function(n){return((""+n).length<2?"0"+n:""+n);};' +
    'return pad(d.getHours())+":"+pad(d.getMinutes());}' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'if(!j||!j.user){$("loginCard").style.display="block";return;}' +
    'me=j.user;meDid=String(me.id);' +
    '$("dmCols").style.display="grid";' +
    'loadConvs();' +
    'var to=new URLSearchParams(location.search).get("to");' +
    'if(to){openByCode(String(to).toUpperCase());}' +
    '}).catch(function(){ $("loginCard").style.display="block"; });' +
    'function loadConvs(){' +
    'fetch("/api/profil/dm/conversations").then(function(r){return r.json();}).then(function(list){' +
    'var wrap=$("convList");wrap.innerHTML="";' +
    'if(!list||!list.length){wrap.innerHTML="<div class=\\"dmEmpty\\">Keine Chats. Schreib einem Freund: \u00d6ffne sein Profil und klick \u201eNachricht senden\u201c.</div>";return;}' +
    'list.forEach(function(c){' +
    'var u=c.user||{};' +
    'var row=document.createElement("div");row.className="conv";' +
    'if(current&&current===u.discordId)row.className+=" on";' +
    'var av=document.createElement("img");av.className="convAv";' +
    'av.src=u.profile&&u.profile.avatar_data_url?u.profile.avatar_data_url:"https://mc-heads.net/head/MHF_Steve/128";' +
    'av.alt="";av.onerror=function(){av.src="https://mc-heads.net/head/MHF_Steve/128";};' +
    'var ui=document.createElement("div");ui.className="convInfo";' +
    'var nm=document.createElement("div");nm.className="convName";' +
    'nm.innerHTML="<span style=\\"color:"+(u.online?"#3fb950":"#4b5563")+";\\">\u25cf</span> "+esc(u.name||("User "+u.id));' +
    'var last=document.createElement("div");last.className="convLast";' +
    'var l=c.last||{};' +
    'last.textContent=(l.text?((l.from===meDid?"Du: ":"")+l.text):"")+" "+(l.ts?"\u00b7 "+tstr(l.ts):"");' +
    'ui.append(nm,last);' +
    'row.append(av,ui);' +
    'row.addEventListener("click",function(){openOther(u.discordId,u.name||("User "+u.id));});' +
    'wrap.append(row);' +
    '});' +
    '}).catch(function(){});' +
    '}' +
    'function openByCode(code){' +
    'fetch("/api/profil/profile-view?code="+encodeURIComponent(code)).then(function(r){return r.json();}).then(function(p){' +
    'if(!p){return;}' +
    'if(!p.isFriend){$("dmMsgs").innerHTML="<div class=\\"dmEmpty\\">Du bist mit \\""+esc(p.name)+"\\" noch nicht befreundet. F\u00fcge den Code im Freunde-Tab hinzu.</div>";return;}' +
    'openOther(p.discordId,p.name);' +
    '}).catch(function(){});' +
    '}' +
    'function openOther(did,name){' +
    'current=did;' +
    '$("thName").textContent=name;' +
    '$("thAv").src="https://mc-heads.net/head/MHF_Steve/128";' +
    'var q=document.querySelectorAll(".conv.on");for(var i=0;i<q.length;i++)q[i].classList.remove("on");' +
    'loadConvs();' +
    '$("dmInputBox").style.display="flex";' +
    'loadMsgs();' +
    'if(pollTimer)clearInterval(pollTimer);' +
    'pollTimer=setInterval(function(){if(current)loadMsgs(true);},4000);' +
    '}' +
    'function loadMsgs(silent){' +
    'fetch("/api/profil/dm/messages?other="+encodeURIComponent(current)).then(function(r){return r.json();}).then(function(list){' +
    'var pb=$("dmMsgs");' +
    'var wasBottom=(pb.scrollHeight-pb.scrollTop-pb.clientHeight<40);' +
    'pb.innerHTML="";' +
    'if(!list||!list.length){pb.innerHTML="<div class=\\"dmEmpty\\">Noch keine Nachrichten. Starte den Chat!</div>";return;}' +
    'list.forEach(function(m){' +
    'var d=document.createElement("div");d.className="dmB "+(m.from===meDid?"me":"oth");' +
    'd.textContent=m.text;' +
    'var t=document.createElement("div");t.className="dmT";t.textContent=tstr(m.ts);' +
    'var w=document.createElement("div");' +
    'w.append(d,t);' +
    'pb.append(w);' +
    '});' +
    'if(list.length&&wasBottom)pb.scrollTop=pb.scrollHeight;' +
    '}).catch(function(){});' +
    '}' +
    '$("dmSend").addEventListener("click",send);' +
    '$("dmText").addEventListener("keydown",function(e){if(e.key==="Enter")send();});' +
    'function send(){' +
    'var t=$("dmText").value.trim();' +
    'if(!t||!current)return;' +
    '$("dmText").value="";' +
    'fetch("/api/profil/dm/send",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({other:current,text:t})})' +
    '.then(function(r){return r.json();}).then(function(j){if(!j||!j.ok){$("dmText").value=t;alert("Fehler: "+((j&&j.error)||"?"));}else{loadMsgs();loadConvs();}})' +
    '.catch(function(){$("dmText").value=t;alert("Netzwerkfehler");});' +
    '}' +
    '$("convCode").addEventListener("keydown",function(e){if(e.key==="Enter"){var c=$("convCode").value.trim().toUpperCase();if(c){$("convCode").value="";openByCode(c);}}});' +
    'window.addEventListener("beforeunload",function(){if(pollTimer)clearInterval(pollTimer);});' +
    '})();' +
    '</script>';

  return pageShell('Nachrichten', css, html);
}

// ── Chat-Widget für die SPA (/chat): Freunde & DMs ──────────────────────────
const CHAT_WIDGET_HTML =
  '<div id="kmSocial">' +
  '<style>' +
  '#kmSocial{position:fixed;right:16px;bottom:16px;z-index:10001;font-family:Inter,"Segoe UI",Arial,sans-serif;}' +
  '#kmSocial .kmSbtn{display:flex;align-items:center;gap:8px;background:linear-gradient(135deg,#D4AF37,#b8860b);color:#0a0d13;' +
  'font:800 13px/1 Inter,sans-serif;border:0;padding:12px 16px;border-radius:999px;cursor:pointer;' +
  'box-shadow:0 6px 22px rgba(0,0,0,.45);transition:transform .15s,filter .15s;}' +
  '#kmSocial .kmSbtn:hover{transform:translateY(-2px);filter:brightness(1.08);}' +
  '#kmSocial .kmSpin{width:360px;max-width:calc(100vw - 40px);background:#0f1522;border:1px solid #26334a;border-radius:16px;' +
  'box-shadow:0 18px 50px rgba(0,0,0,.6);margin-bottom:10px;overflow:hidden;display:none;}' +
  '#kmSocial .kmSpin.on{display:block;}' +
  '#kmSocial .kmShead{display:flex;justify-content:space-between;align-items:center;padding:.7rem 1rem;border-bottom:1px solid #1c2636;' +
  'font:800 13px/1 Outfit,sans-serif;color:#ffd75f;}' +
  '#kmSocial .kmShead a{color:#8ab4ff;font-size:.75rem;font-weight:600;text-decoration:none;}' +
  '#kmSocial .kmSbody{max-height:400px;overflow-y:auto;padding:.5rem;}' +
  '#kmSocial .kmEdit{display:flex;gap:.4rem;padding:.6rem;border-top:1px solid #1c2636;}' +
  '#kmSocial .kmEdit input{flex:1;width:auto;background:#0d1420;border:1px solid #2a3749;color:#f2f3f5;padding:.5rem;border-radius:8px;font-size:.8rem;}' +
  '#kmSocial .kmEdit button{margin:0;padding:.5rem .8rem;font-size:.78rem;}' +
  '#kmSocial .kmFr{display:flex;gap:.6rem;align-items:center;padding:.5rem;border-radius:10px;}' +
  '#kmSocial .kmFr:hover{background:#16203a;}' +
  '#kmSocial .kmFr img{width:38px;height:38px;border-radius:9px;object-fit:cover;background:#151d2b;flex:none;}' +
  '#kmSocial .kmFr .kmFi{flex:1;min-width:0;}' +
  '#kmSocial .kmFr .kmFn{font-weight:700;font-size:.85rem;white-space:nowrap;overflow:hidden;text-overflow:ellipsis;}' +
  '#kmSocial .kmFr .kmFs{color:#8f9aab;font-size:.72rem;}' +
  '#kmSocial .kmFr a{font-size:.75rem;font-weight:700;color:#ffd75f;text-decoration:none;}' +
  '#kmSocial .kmEmpty{padding:1.5rem 1rem;text-align:center;color:#8f9aab;font-size:.85rem;}' +
  '</style>' +
  '<div class="kmSpin" id="kmSspin"><div class="kmShead">Freunde &amp; DMs <a href="/dm">Alle Nachrichten \u2192</a></div>' +
  '<div class="kmSbody"><div id="kmReqs"></div><div id="kmFlist"></div></div>' +
  '<div class="kmEdit"><input id="kmAddCode" placeholder="Freundes-Code"/>' +
  '<button type="button" id="kmAddBtn">Hinzuf\u00fcgen</button></div></div>' +
  '<button type="button" class="kmSbtn" id="kmSbtn">&#9993; Freunde &amp; DMs</button>' +
  '</div>' +
  '<scr' + 'ipt>' +
  '(function(){' +
  'function es(s){return String(s==null?"":s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");}' +
  'var open=false;' +
  'var btn=document.getElementById("kmSbtn");var spin=document.getElementById("kmSspin");' +
  'if(!btn)return;' +
  'btn.addEventListener("click",function(){open=!open;spin.classList.toggle("on",open);if(open)load();});' +
  'function av(u){' +
  'if(u&&u.profile&&u.profile.avatar_data_url)return u.profile.avatar_data_url;' +
  'return "https://mc-heads.net/head/MHF_Steve/96";}' +
  'function renderReqs(rq,reqs){' +
  'if(!rq)return;rq.innerHTML="";reqs=Array.isArray(reqs)?reqs:[];' +
  'if(!reqs.length)return;' +
  'var h="<div style=\\"padding:.45rem .6rem;font:800 11px/1 Outfit,sans-serif;color:#ffd75f;text-transform:uppercase;letter-spacing:.06em;\\">Freundesanfragen ("+reqs.length+")</div>";' +
  'reqs.forEach(function(it){var f=it.user||{};' +
  'h+="<div class=\\"kmFr\\"><img src=\\""+av(f)+"\\" data-fb=\\"https://mc-heads.net/head/MHF_Steve/96\\" onerror=\\"if(this.dataset.fb)this.src=this.dataset.fb;\\" alt=\\"\\"/>";' +
  'h+="<div class=\\"kmFi\\"><div class=\\"kmFn\\">"+es(f.name||("User "+(f.id||"")))+"</div>";' +
  'h+="<div class=\\"kmFs\\">"+es(f.code||"")+"</div></div>";' +
  'h+="<button type=\\"button\\" data-a=\\""+encodeURIComponent(it.request.from)+"\\" title=\\"Annehmen\\">\u2713</button>";' +
  'h+="<button type=\\"button\\" data-d=\\""+encodeURIComponent(it.request.from)+"\\" title=\\"Ablehnen\\" style=\\"opacity:.65\\">\u2715</button>";' +
  'h+="</div>";' +
  '});' +
  'rq.innerHTML=h;' +
  'rq.querySelectorAll("[data-a]").forEach(function(b){' +
  'b.addEventListener("click",function(){' +
  'fetch("/api/profil/friend-accept",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({from_id:decodeURIComponent(b.getAttribute("data-a"))})})' +
  '.then(function(r){return r.json();}).then(function(){load();}).catch(function(){});});});' +
  'rq.querySelectorAll("[data-d]").forEach(function(b){' +
  'b.addEventListener("click",function(){' +
  'fetch("/api/profil/friend-decline",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({from_id:decodeURIComponent(b.getAttribute("data-d"))})})' +
  '.then(function(r){return r.json();}).then(function(){load();}).catch(function(){});});});' +
  '}' +
  'function load(){' +
  'var fl=document.getElementById("kmFlist");var rq=document.getElementById("kmReqs");' +
  'if(fl)fl.innerHTML="";renderReqs(rq,[]);' +
  'fetch("/api/profil/friend-requests").then(function(r){return r.json();}).then(function(reqs){renderReqs(rq,reqs);}).catch(function(){});' +
  'fetch("/api/profil/friends").then(function(r){return r.json();}).then(function(list){' +
  'if(!fl)return;' +
  'if(!list||!list.length){fl.innerHTML="<div class=\\"kmEmpty\\">Noch keine Freunde. F\u00fcge unten einen Code hinzu oder teile deinen.<br/>Freunde-Code: siehst du im <a href=\\"/profil\\" style=\\"color:#ffd75f\\">Profil</a>.</div>";return;}' +
  'list.forEach(function(f){' +
  'var eq=f.equipped||{};var ti=(eq.title)?null:null;' +
  'var row=document.createElement("div");row.className="kmFr";' +
  'var img=document.createElement("img");img.src=av(f);img.alt="";img.onerror=function(){img.src="https://mc-heads.net/head/MHF_Steve/96";};' +
  'var info=document.createElement("div");info.className="kmFi";' +
  'var n=document.createElement("div");n.className="kmFn";n.textContent=f.name||("User "+f.id);' +
  'var s=document.createElement("div");s.className="kmFs";' +
  's.innerHTML="<span style=\\"color:"+(f.online?"#3fb950":"#4b5563")+";\\">\u25cf</span> Level "+es(f.level)+" \u00b7 "+es(f.server||"Offline");'; +
  'info.append(n,s);' +
  'var pr=document.createElement("a");pr.href="/u/"+encodeURIComponent(f.code||"");pr.textContent="Profil";pr.target="_blank";' +
  'var dm=document.createElement("a");dm.href="/dm?to="+encodeURIComponent(f.code||"");dm.textContent="\u2709";dm.style.marginLeft=".5rem";' +
  'row.append(img,info,pr,dm);' +
  'fl.append(row);' +
  '});' +
  '}).catch(function(){if(fl)fl.innerHTML="<div class=\\"kmEmpty\\">Nicht erreichbar.</div>";});' +
  '}' +
  'document.getElementById("kmAddBtn").addEventListener("click",function(){' +
  'var code=document.getElementById("kmAddCode").value.trim().toUpperCase();' +
  'if(!code)return;' +
  'fetch("/api/profil/friend-add",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({code:code})})' +
  '.then(function(r){return r.json();}).then(function(j){' +
  'if(j&&j.ok){document.getElementById("kmAddCode").value="";' +
  'if(j.accepted){alert("Ihr seid jetzt Freunde!");}else{alert("Anfrage gesendet \u2013 wartet auf Best\u00e4tigung.");}' +
  'load();}else{alert("Fehler: "+((j&&j.error)||"?"));}}).catch(function(){alert("Netzwerkfehler");});' +
  '});' +
  '})();' +
  '</scr' + 'ipt>';