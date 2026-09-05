// Kollegen.me – Profil-Modul (Website)
// ====================================
// Ergänzt die Website um:
//   GET  /profil            – self-contained Profil-Seite (HTML)
//   GET  /api/profil/me     – eigene Profil-/MC-Daten ({ user, mcName, uuid, code, profile })
//   GET  /api/profil/uuid?name=… – MC-Name → UUID Proxy (CORS-sauber)
//   POST /api/profil/save   – Profil + MC-Identität ins Backend-Bridge schreiben
// und injiziert in der SPA rechts oben ein "Profil"-Icon mit Avatar
// (Standard: eigener Kopf vom Skin, sonst Discord-Avatar, sonst verborgen).
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
  // Fallback für pm2/Webserver: Secret aus Datei lesen (Berechtigung 600)
  try {
    const raw = fs.readFileSync('/etc/kollegen_internal.env', 'utf8');
    const m = /^KOLLEGEN_INTERNAL_SECRET\s*=\s*(.+)\s*$/m.exec(raw);
    if (m) return m[1].trim();
  } catch (_) {}
  return '';
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

  // ── Profilseite (self-contained) ──
  const PAGE = buildPage();

  // ── SPA: Avatar-Icon oben rechts injizieren ──
  let cachedIndex = null;
  function injectedIndex() {
    if (cachedIndex) return cachedIndex;
    let html;
    try {
      html = fs.readFileSync(INDEX_PATH, 'utf8');
    } catch (_) {
      return null;
    }
    const fab =
      '<style>' +
      '#kmProfFab{position:fixed;top:14px;right:18px;z-index:9999;display:flex;align-items:center;gap:8px;' +
      'background:rgba(10,10,15,.55);border:1px solid rgba(212,175,55,.45);border-radius:999px;padding:5px 14px 5px 6px;' +
      'color:#fff;font:600 13px/1 Inter,Outfit,sans-serif;cursor:pointer;backdrop-filter:blur(6px);text-decoration:none;transition:background .2s;}' +
      '#kmProfFab:hover{background:rgba(212,175,55,.25);}' +
      '#kmProfImg{width:26px;height:26px;border-radius:50%;object-fit:cover;background:#222;}' +
      '@media (max-width:640px){#kmProfFab #kmProfLabel{display:none;}#kmProfFab{padding:6px;}}' +
      '</style>' +
      '<a id="kmProfFab" href="/profil">' +
      '<img id="kmProfImg" alt="" />' +
      '<span id="kmProfLabel">Profil</span>' +
      '</a>' +
      '<script>' +
      '(function(){' +
      'function setHref(){' +
      'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
      'var fab=document.getElementById("kmProfFab");var img=document.getElementById("kmProfImg");' +
      'if(!fab||fab.dataset.done)return;fab.dataset.done="1";' +
      'if(!j||!j.user){fab.style.display="none";return;}' +
      'fetch("/api/profil/me").then(function(r){return r.json();}).then(function(p){' +
      'var head=null;' +
      'if(p&&p.mcName){head="https://mc-heads.net/avatar/"+encodeURIComponent(p.mcName).replace(/%20/g,"_")+"/128";}' +
      'else if(j.user&&j.user.avatarUrl){head=j.user.avatarUrl;}' +
      'if(head&&img){img.src=head;img.style.display="";}' +
      '}).catch(function(){});' +
      '}).catch(function(){});' +
      '}' +
      'if(document.readyState!=="loading")setHref();else document.addEventListener("DOMContentLoaded",setHref);' +
      '})();' +
      '</' + 'script>';
    html = html.replace('</body>', fab + '</body>');
    cachedIndex = html;
    return html;
  }

  // HTML-Navigationen abfangen, Assets (css/js/img) durchreichen
  app.use((req, res, next) => {
    if (req.method !== 'GET') return next();
    const urlPath = (req.url || '').split('?')[0];

    if (urlPath === '/profil') {
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      return res.send(PAGE);
    }

    const ext = path.extname(urlPath).toLowerCase();
    if (ext && ext !== '.html') return next();
    if (urlPath.startsWith('/api/') || urlPath === '/profil') return next();
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

function buildPage() {
  const css =
    'body{background:#0a0a0f;color:#f8fafc;font-family:Inter,"Segoe UI",Arial,sans-serif;margin:0;padding:2rem 1rem;display:flex;justify-content:center;}' +
    '#wrap{max-width:720px;width:100%;}' +
    'h1{font-family:Outfit,Inter,sans-serif;color:#D4AF37;margin-bottom:.25rem;}' +
    '.card{background:#14141c;border:1px solid #262633;border-radius:14px;padding:1.2rem;margin-top:1rem;}' +
    'label{display:block;color:#a7a7b5;font-size:.85rem;margin:1rem 0 .3rem;}' +
    'input,textarea,select{width:100%;box-sizing:border-box;background:#0f0f16;border:1px solid #30303d;color:#f8fafc;padding:.6rem;border-radius:8px;font-size:.95rem;}' +
    'textarea{resize:vertical;min-height:70px;}' +
    'button{background:#D4AF37;color:#0a0a0f;font-weight:700;border:0;padding:.65rem 1.3rem;border-radius:8px;cursor:pointer;margin-top:1rem;font-size:.95rem;}' +
    'button.secondary{background:#262633;color:#f8fafc;margin-left:.5rem;}' +
    '.row{display:flex;gap:.5rem;align-items:center;flex-wrap:wrap;}' +
    '.head{width:64px;height:64px;border-radius:12px;object-fit:cover;background:#222;}' +
    '.muted{color:#8b8b9a;font-size:.85rem;} .good{color:#7ee787;} .bad{color:#f85149;}' +
    'a,button{cursor:pointer;}';
  const html =
    '<!DOCTYPE html><html lang="de"><head><meta charset="utf-8"/>' +
    '<meta name="viewport" content="width=device-width,initial-scale=1"/>' +
    '<title>Profil · kollegen.me</title><style>' + css + '</style></head><body><div id="wrap">' +
    '<h1>Profil &amp; Minecraft</h1>' +
    '<div class="card" id="loginCard" style="display:none;">' +
    '<p style="margin:0 0 .4rem;">Melde dich mit Discord an – dein Minecraft-Profil wird automatisch übernommen, sobald du im Kollegen-Launcher angemeldet bist.</p>' +
    '<a href="/api/auth/discord/login"><button type="button">Mit Discord anmelden</button></a>' +
    '</div>' +
    '<div class="card" id="meCard" style="display:none;">' +
    '<div class="row"><img id="headImg" class="head" alt=""/><div>' +
    '<div id="meName" style="font-weight:700;font-size:1.1rem;"></div>' +
    '<div class="muted" id="meDiscord"></div>' +
    '<div class="muted" id="meCode"></div></div></div>' +
    '<label for="mcName">Minecraft-Name (verknüpfen)</label>' +
    '<div class="row"><input id="mcName" placeholder="z. B. FluffyBento" style="flex:1;width:auto;"/><button type="button" class="secondary" id="lookupBtn">Skin laden</button></div>' +
    '<div id="skinPreview" style="margin-top:.6rem;"></div>' +
    '<label for="avatarChoice">Avatar</label>' +
    '<select id="avatarChoice"><option value="discord">Discord-Avatar</option><option value="minecraft">Minecraft-Head</option></select>' +
    '<label for="bioValue">Bio</label><textarea id="bioValue" placeholder="Beschreibe dich in ein paar Worten..."></textarea>' +
    '<label for="bannerValue">Banner-URL (optional)</label><input id="bannerValue" placeholder="https://…/banner.png"/>' +
    '<label style="display:flex;align-items:center;gap:.5rem;"><input type="checkbox" id="pubToggle" style="width:auto;"/> Öffentliches Profil (für andere sichtbar)</label>' +
    '<div class="muted" id="saveHint" style="margin-top:.6rem;"></div>' +
    '<div class="row"><button type="button" id="saveBtn">Speichern</button></div>' +
    '</div>' +
    '<p class="muted" style="margin-top:1rem;">Hinweis: Der direkte Minecraft-Login (Microsoft/OAuth) benötigt eine eigene App-Registrierung – bis dahin wird dein Skin über den Namen geladen (ganz ohne Passwort). Gemeinsame Freunde im Launcher übernehmen den Skin automatisch.</p>' +
    '</div><script>' +
    '(function(){' +
    'function $(i){return document.getElementById(i);}' +
    'var me=$("meCard"),login=$("loginCard");' +
    'fetch("/api/auth/me").then(function(r){return r.json();}).then(function(j){' +
    'if(!j||!j.user){login.style.display="block";return;}' +
    'var u=j.user;' +
    '$("meName").textContent=u.global_name||u.username;' +
    '$("meDiscord").textContent=u.username+" (Discord)";' +
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
    '$("saveHint").textContent="Noch kein Minecraft-Profil verkn\u00fcpft. Tipp: Melde dich im Kollegen-Launcher mit demselben Discord-Konto an und starte einmal das Spiel – dann erscheint dein Name + Skin hier automatisch.";' +
    '}' +
    'if(p&&p.profile){var pr=p.profile;' +
    'if(pr.bio)$("bioValue").value=pr.bio;' +
    'if(pr.banner_data_url)$("bannerValue").value=pr.banner_data_url;' +
    'if(pr.avatar_choice)$("avatarChoice").value=pr.avatar_choice;' +
    'if(pr.public)$("pubToggle").checked=true;' +
    '}' +
    '}).catch(function(){});' +
    '}).catch(function(){login.style.display="block";});' +
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
    '$("saveHint").textContent="Lade Skin…";' +
    'fetch("/api/profil/uuid?name="+encodeURIComponent(nm)).then(function(r){return r.json();}).then(function(j){' +
    'if(j.error){$("saveHint").textContent="Name nicht gefunden.";return;}' +
    'window._mcName=j.name||nm;window._mcUuid=j.uuid;' +
    '$("headImg").src="https://mc-heads.net/head/"+encodeURIComponent(window._mcName).replace(/%20/g,"_")+"/256";' +
    'loadBody(window._mcName);' +
    '$("saveHint").textContent="Skin von "+window._mcName+" geladen – speichern, um zu übernehmen.";' +
    '}).catch(function(){ $("saveHint").textContent="Fehler beim Skin-Laden."; });' +
    '}' +
    '$("saveBtn").addEventListener("click",function(){' +
    'var body={mcName:window._mcName||$("mcName").value.trim(),uuid:window._mcUuid||null,' +
    'bio:$("bioValue").value.trim(),avatar_choice:$("avatarChoice").value,' +
    'banner_data_url:$("bannerValue").value.trim(),public:$("pubToggle").checked};' +
    'var head=$("headImg").src;' +
    'if(body.avatar_choice==="minecraft"){body.avatar_data_url=head||"";}' +
    'else{body.avatar_data_url="";}' +
    '$("saveBtn").disabled=true;$("saveBtn").textContent="Speichern…";' +
    'fetch("/api/profil/save",{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify(body)})' +
    '.then(function(r){return r.json();}).then(function(j){' +
    'if(j&&j.ok){$("saveHint").textContent="Gespeichert ✓";}' +
    'else{$("saveHint").textContent="Fehler: "+((j&&j.error)||"unbekannt");}' +
    '$("saveBtn").disabled=false;$("saveBtn").textContent="Speichern";' +
    '}).catch(function(){ $("saveHint").textContent="Fehler beim Speichern.";$("saveBtn").disabled=false;$("saveBtn").textContent="Speichern"; });' +
    '});' +
    '})();' +
    '</script></body></html>';
  return html;
}