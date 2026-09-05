const invoke = window.__TAURI__.core.invoke;

const $ = (id) => document.getElementById(id);

let availableVersions = [];
let instancesCache = [];

async function loadVersions() {
  try {
    availableVersions = await invoke("get_available_versions");
    const vSelect = $("iVersion");
    vSelect.innerHTML = "";
    for (const v of availableVersions) {
      const opt = document.createElement("option");
      opt.value = v;
      opt.textContent = v;
      vSelect.append(opt);
    }
    if (availableVersions.length > 0) {
      vSelect.value = availableVersions[0];
    }
    updateLoaderVersions();
  } catch (e) {
    console.error("Failed to load versions:", e);
  }
}

async function updateLoaderVersions() {
  const version = $("iVersion").value;
  const loader = $("iLoader").value;
  const lvSelect = $("iLoaderVer");
  lvSelect.innerHTML = '<option value="">(Standard / Neueste)</option>';

  if (loader === "vanilla" || !version) return;

  try {
    const loadersData = await invoke("get_loaders_for_version", { version });
    const list = loadersData[loader] || [];
    for (const item of list) {
      const opt = document.createElement("option");
      const verStr = item.version || item.loader?.version || item.id || item;
      opt.value = verStr;
      opt.textContent = verStr;
      lvSelect.append(opt);
    }
  } catch (e) {
    console.error("Failed to load loader versions:", e);
  }
}

async function refreshInstances() {
  try {
    const instances = await invoke("get_instances");
    instancesCache = instances;
    if (document.body.classList.contains("console-mode")) {
      renderConsoleHome();
      return;
    }
    const list = $("instanceList");
    list.innerHTML = "";
    if (instances.length === 0) {
      list.innerHTML = "<li style='color: #888; justify-content: center;'>Keine Instanzen vorhanden</li>";
      return;
    }
    for (const inst of instances) {
      const li = document.createElement("li");
      const label = document.createElement("span");
      label.textContent = `${inst.name} — ${inst.version} (${inst.loader})`;
      const actions = document.createElement("span");

      const launch = document.createElement("button");
      launch.textContent = "Starten";
      launch.onclick = () => launchGame(inst.name);

      const manage = document.createElement("button");
      manage.textContent = "Verwalten";
      manage.onclick = () => openManage(inst);

      let opt = null;
      if (inst.loader === "fabric" || inst.loader === "quilt") {
        opt = document.createElement("button");
        opt.textContent = "Optimize";
        opt.title = "Performance-Modpack installieren";
        opt.onclick = async () => {
          opt.disabled = true;
          opt.textContent = "…";
          try {
            const msg = await invoke("optimize_instance", { name: inst.name });
            alert(msg || "Performance-Modpack verarbeitet.");
          } catch (e) {
            alert("Optimierung fehlgeschlagen: " + e);
          }
          opt.disabled = false;
          opt.textContent = "Optimize";
        };
      }

      const del = document.createElement("button");
      del.textContent = "Löschen";
      del.onclick = () => deleteInstance(inst.name, inst.id);

      actions.append(launch, manage, ...(opt ? [opt] : []), del);
      li.append(label, actions);
      list.append(li);
    }
  } catch (e) {
    console.error(e);
  }
}

let activeInstance = null;
let conflictHandled = false;

async function refreshLogs() {
  try {
    const logs = await invoke("get_logs");
    const logsEl = $("logs");
    // Only touch the DOM when the Logs panel is actually visible – otherwise
    // we'd keep allocating huge strings in the renderer every poll and the
    // webview (Edge/WebView2) memory would grow without bound.
    const visible =
      logsEl && (logsEl.offsetParent !== null || logsEl.style.display !== "none");
    let glogText = "";
    if (activeInstance) {
      try {
        glogText = await invoke("get_game_log", { instanceName: activeInstance });
      } catch (e) {
        /* ignore missing game log */
      }
    }

    // Auto-detect and resolve Fabric mod incompatibilities in the game log
    if (
      activeInstance &&
      !conflictHandled &&
      glogText.includes("Incompatible mods found")
    ) {
      conflictHandled = true;
      try {
        const msg = await invoke("auto_resolve_conflict", {
          instanceName: activeInstance,
        });
        alert(
          "Mod-Konflikt erkannt und automatisch behoben:\n\n" +
            msg +
            "\n\nDer Launcher wird neu gestartet…"
        );
        launchGame(activeInstance, true);
      } catch (e) {
        alert("Konflikt konnte nicht automatisch behoben werden: " + e);
      }
    }

    if (visible && logsEl) {
      logsEl.textContent =
        logs.join("\n") + (glogText ? "\n" + glogText : "");
    }
  } catch (e) {
    console.error(e);
  }
}

  async function refreshAuth() {
    try {
      const status = await invoke("auth_check_status");
      const statusEl = $("authStatus");
      statusEl.textContent = status.state === "done"
        ? `Angemeldet: ${status.username || ""}`
        : `Status: ${status.state} ${status.msg ? '(' + status.msg + ')' : ''}`;
      if (status.state === "done") {
        $("loginInfo").style.display = "none";
        $("authBtn").style.display = "none";
        const qrModal = $("loginQrModal");
        if (qrModal && qrModal.style.display !== "none") {
          qrModal.style.display = "none";
          if (consoleNavActive && typeof refreshConsoleFocusables === "function") refreshConsoleFocusables();
        }
      } else {
        $("authBtn").style.display = "";
      }
    } catch (e) {
      console.error(e);
    }
  }

  async function refreshDiscord() {
    try {
      // `discord_social` combines OAuth login + RPC presence + friends into a
      // single source of truth, so the header and the Socials tab never show
      // contradictory "verbunden" / "nicht verbunden" states.
      const s = await invoke("discord_social");
      const acct = $("discordAccountName");
      const avatar = $("discordAvatar");
      const statusEl = $("discordStatus");
      const serverEl = $("discordCurrentServer");

      const connected = s.oauth_logged_in || s.rpc_connected;
      const u = s.user;
      if (connected && u) {
        const name = u.global_name || u.username;
        acct.textContent = `Discord: ${name}`;
        if (u.avatar_url) {
          avatar.src = u.avatar_url;
          avatar.style.display = "";
        } else {
          avatar.style.display = "none";
        }
        statusEl.textContent = `Verbunden als ${name}`;
        statusEl.style.color = "var(--muted)";
      } else {
        acct.textContent = "Discord";
        avatar.style.display = "none";
        statusEl.textContent = "Nicht verbunden mit Discord";
        statusEl.style.color = "var(--danger)";
      }

      // Current server (auto-detected from the game log). Shown as a button
      // that copies the IP so it can be shared with friends.
      serverEl.textContent = "";
      if (s.current_server) {
        const btn = document.createElement("button");
        btn.className = "server-btn";
        btn.textContent = `Aktueller Server: ${s.current_server}`;
        btn.title = "Server-IP kopieren";
        btn.onclick = () => copyText(s.current_server);
        serverEl.append(btn);
      }

      renderDiscordFriends(s.friends || [], connected);
      renderDiscordInvites(s.invites || [], connected);
    } catch (e) {
      console.error(e);
    }
  }

  function statusLabel(status) {
    return (
      { online: "Online", idle: "Idle", dnd: "Beschäftigt", offline: "Offline" }[
        status
      ] || status
    );
  }

  // Renders the Discord friends and wires the "Freund beitreten" action that
  // lets the user join a friend's game (instance picker filtered by version).
  // Friends who run the Kollegen Client (detected via rich presence) are
  // highlighted with a badge and sorted to the top.
  function renderDiscordFriends(friends, connected) {
    const list = $("discordFriends");
    const hint = $("friendsHint");
    if (!list) return;
    list.innerHTML = "";

    const online = friends.filter((f) =>
      f.presence_known ? (f.status && f.status !== "offline") : true
    );
    if (!friends.length) {
      hint.style.display = "";
      hint.textContent = connected
        ? "Noch keine Freunde geladen."
        : "Verbinde dich mit Discord, um deine Freunde zu sehen.";
      return;
    }
    if (!online.length) {
      hint.style.display = "";
      hint.textContent = "Keine Freunde gerade online.";
      return;
    }
    hint.style.display = "none";

    // Kollegen-Nutzer zuerst anzeigen.
    online.sort((a, b) => (!!b.kollegen || false) - (!!a.kollegen || false));

    for (const f of online) {
      const li = document.createElement("li");
      li.classList.toggle("friend-kollegen", !!f.kollegen);

      if (f.avatar_url) {
        const img = document.createElement("img");
        img.className = "friend-avatar";
        img.src = f.avatar_url;
        img.alt = "";
        li.append(img);
      }

      const meta = document.createElement("div");
      meta.className = "friend-meta";
      const name = document.createElement("div");
      name.className = "friend-name";
      name.textContent = f.global_name || f.username;
      if (f.kollegen) {
        const badge = document.createElement("span");
        badge.className = "friend-badge";
        badge.textContent = "⚡ Kollegen Client";
        badge.title = "Nutzt den Kollegen Client – Freunden kann direkt beigetreten werden.";
        name.append(" ", badge);
      }
      const sub = document.createElement("div");
      sub.className = "friend-sub";
      let subText;
      if (f.game) {
        subText = f.version ? `${f.game} (${f.version})` : f.game;
      } else if (f.presence_known) {
        subText = statusLabel(f.status);
      } else {
        subText = "Freund";
      }
      sub.textContent = subText;
      meta.append(name, sub);
      li.append(meta);

      if (f.join_secret) {
        const join = document.createElement("button");
        join.textContent = f.kollegen ? "Freund beitreten" : "Beitreten";
        join.title = "Instanz wählen und dem Server beitreten";
        join.onclick = () => openJoinFriend(f);
        li.append(join);
      }

      if (f.mutual_guilds && f.mutual_guilds.length) {
        for (const g of f.mutual_guilds) {
          const srv = document.createElement("button");
          srv.className = "friend-server-btn";
          srv.textContent = g.name ? `Server: ${g.name}` : "Server öffnen";
          srv.title = "Server in Discord öffnen";
          srv.onclick = () => invoke("open_url", {
            url: `discord://discord.com/channels/${g.id}`,
          });
          li.append(srv);
        }
      }

      list.append(li);
    }
  }

  // Social-System (Discord-verifiziert, Freundes-Codes): eigenes Profil +
  // Freundesliste. Beides wird auch als ~/.kollegen/social.json für die Mod
  // geschrieben. Freunde werden ausschließlich über den Freundes-Code hinzugefügt.
  let socialMe = null;
  let socialFriends = [];
  let socialRequests = [];
  let lastSocialFetch = 0;
  const SOCIAL_STALE_MS = 30000;

  function renderSocialAll() {
    renderProfileWidget();
    renderFriendsWidget();
    renderSocialPanel();
  }

  async function refreshSocial(force) {
    // Bei frischen Daten sofort aus dem Cache rendern statt erneut zu laden –
    // dann öffnet der Socials-Tab ohne Netz-Wartezeit (kein Lag/Blinken).
    if (
      !force &&
      socialMe !== null &&
      lastSocialFetch &&
      Date.now() - lastSocialFetch < SOCIAL_STALE_MS
    ) {
      renderSocialAll();
      return;
    }
    try {
      const [me, friends, requests] = await Promise.all([
        invoke("kollegen_me"),
        invoke("kollegen_friends"),
        invoke("kollegen_friend_requests").catch(() => ({ error: true })),
      ]);
      socialMe = me && !me.error ? normalizeProfile(me) : null;
      socialFriends = (friends && !friends.error ? friends : []).map(normalizeFriend);
      socialRequests = requests && !requests.error ? requests : [];
      lastSocialFetch = Date.now();
      renderSocialAll();
    } catch (e) {
      console.error(e);
    }
  }

  // Das Backend liefert /me als {id,name,uuid,code,accounts} und /friends als
  // [{id,name,uuid,code,server,online}]. Die Anzeige erwartet mc_name /
  // friend_code / global_name / username / avatar – wir normalisieren hier zentral.
  function normalizeProfile(me) {
    if (!me) return me;
    const accts = Array.isArray(me.accounts) ? me.accounts : [];
    const disc = accts.find((a) => (a.type || "").toLowerCase().indexOf("discord") >= 0) || null;
    const avatar = (disc && (disc.avatar_url || disc.avatar)) || me.avatar || "";
    const name = me.name || me.mc_name || me.username || (disc && (disc.global_name || disc.username)) || "";
    return {
      id: me.id || (disc && disc.id) || "",
      mc_name: name,
      username: name,
      global_name: name,
      friend_code: me.code || me.friend_code || "",
      uuid: me.uuid || null,
      avatar,
      accounts: accts,
    };
  }

  function normalizeFriend(f) {
    if (!f) return f;
    const accts = Array.isArray(f.accounts) ? f.accounts : [];
    const disc = accts.find((a) => (a.type || "").toLowerCase().indexOf("discord") >= 0) || null;
    const avatar = (disc && (disc.avatar_url || disc.avatar)) || f.avatar || "";
    const name = f.name || f.mc_name || f.global_name || f.username || "";
    return {
      id: f.id || "",
      mc_name: name,
      username: name,
      global_name: name,
      uuid: f.uuid || null,
      friend_code: f.code || f.friend_code || "",
      code: f.code || f.friend_code || "",
      server: f.server || null,
      online: !!f.online,
      level: typeof f.level === "number" ? f.level : null,
      equipped: f.equipped && typeof f.equipped === "object" ? f.equipped : {},
      avatar,
      accounts: accts,
      // Profil-Zusammenfassung (kommt seit v1.11.5 vom Backend mit).
      bio: (f.profile && f.profile.bio) || null,
      avatar_data_url: (f.profile && f.profile.avatar_data_url) || null,
      banner_data_url: (f.profile && f.profile.banner_data_url) || null,
      avatar_choice: (f.profile && f.profile.avatar_choice) || "discord",
      profile_public: !!(f.profile && f.profile.public),
    };
  }

  function avatarUrl(u) {
    if (!u) return "";
    if (u.avatar && /^https?:\/\//.test(u.avatar)) return u.avatar;
    if (u.avatar && u.id) return `https://cdn.discordapp.com/avatars/${u.id}/${u.avatar}.png`;
    return "";
  }

  function renderProfileWidget() {
    const w = $("profileWidget");
    if (w) {
      if (!socialMe) {
        $("pwAvatar").style.display = "none";
        $("pwName").textContent = "nicht verbunden";
        $("pwDiscord").textContent = "";
      } else {
        const url = avatarUrl(socialMe);
        if (url) { $("pwAvatar").src = url; $("pwAvatar").style.display = ""; }
        $("pwName").textContent = socialMe.mc_name || socialMe.username || "—";
        $("pwDiscord").textContent = socialMe.global_name || socialMe.username || "";
      }
    }
    // Profil-Block im Sozial-Panel (einziger Social-Hub, kein doppeltes HUD).
    const spName = $("spName");
    const spDiscord = $("spDiscord");
    const spAvatar = $("spAvatar");
    if (spName) {
      if (!socialMe) {
        spName.textContent = "nicht verbunden";
        if (spDiscord) spDiscord.textContent = "";
        if (spAvatar) spAvatar.style.display = "none";
      } else {
        spName.textContent = socialMe.mc_name || "—";
        if (spDiscord) spDiscord.textContent = socialMe.global_name || socialMe.username || "";
        if (spAvatar) {
          const url = avatarUrl(socialMe);
          if (url) { spAvatar.src = url; spAvatar.style.display = ""; }
          else spAvatar.style.display = "none";
        }
      }
    }
  }

// Wire profile UI controls (save/edit/view)
(function wireProfileUI(){
  const editBtn = $("profileEditToggle");
  const saveBtn = $("profileSaveBtn");
  const cancelBtn = $("profileCancelBtn");
  const bannerInput = $("profileBannerInput");
  const avatarChoice = $("profileAvatarChoice");
  const avatarUpload = $("profileAvatarUpload");
  const publishBtn = $("profilePublishBtn");

  if (editBtn) editBtn.onclick = () => { setProfileEditMode(true); };
  if (saveBtn) saveBtn.onclick = () => saveProfileCustomization();
  if (cancelBtn) cancelBtn.onclick = () => cancelProfileEdits();
  if (publishBtn) publishBtn.onclick = () => publishProfileToServer();

  if (bannerInput) bannerInput.onchange = (e) => {
    const f = e.target.files && e.target.files[0];
    if (!f) return; const r = new FileReader(); r.onload = (ev) => { $("profileBannerPreview").src = ev.target.result; $("profileBannerPreview").style.display = ""; }; r.readAsDataURL(f);
  };

  if (avatarChoice) avatarChoice.onchange = () => {
    const v = avatarChoice.value;
    if (v === 'upload') { avatarUpload.style.display = ''; } else { avatarUpload.style.display = 'none'; }
    if (v === 'minecraft') {
      // Standard-Avatar: eigener Kopf aus dem aktuellen Skin.
      const nm = (socialMe && socialMe.mc_name) || "";
      if (nm) {
        const head = `https://mc-heads.net/avatar/${encodeURIComponent(nm).replace(/%20/g, "_")}/128`;
        const av = $("profileAvatarPreview");
        if (av) { av.src = head; av.style.display = ""; }
      }
    }
  };

  if (avatarUpload) avatarUpload.onchange = (e) => {
    const f = e.target.files && e.target.files[0]; if (!f) return; const r = new FileReader(); r.onload = (ev) => { $("profileAvatarPreview").src = ev.target.result; $("profileAvatarPreview").style.display = ""; }; r.readAsDataURL(f);
  };

  const viewBtn = $("viewProfileBtn"); if (viewBtn) viewBtn.onclick = () => viewProfileByName();
  const viewInput = $("viewProfileName"); if (viewInput) viewInput.onkeyup = (e) => { if (e.key === 'Enter') viewProfileByName(); };

  const browseBtn = $("browseProfileBtn");
  const browseInput = $("browseProfileSearch");
  const doBrowse = () => browsePublicProfiles((browseInput && browseInput.value.trim()) || "");
  if (browseBtn) browseBtn.onclick = doBrowse;
  if (browseInput) browseInput.onkeyup = (e) => { if (e.key === 'Enter') doBrowse(); };
})();

  function renderFriendsWidget() {
    renderFriendsList("fwList", socialFriends, false);
  }

  function renderSocialPanel() {
    renderKollegenRequests();
    renderFriendsList("kollegenFriends", socialFriends, true);
  }

  // Eingehende Freundesanfragen (Anfrage → Annehmen/Ablehnen), neueste zuerst.
  function renderKollegenRequests() {
    const wrap = $("kollegenRequestsWrap");
    const list = $("kollegenRequests");
    if (!wrap || !list) return;
    const arr = Array.isArray(socialRequests) ? socialRequests : [];
    if (!arr.length) { wrap.style.display = "none"; return; }
    wrap.style.display = "";
    list.innerHTML = "";
    for (const it of arr) {
      const req = it.request || {};
      const u = normalizeFriend(it.user || {});
      const fromId = req.from || u.id || "";
      const li = document.createElement("li");
      const img = document.createElement("img");
      img.className = "friend-avatar";
      const url = avatarUrl(u);
      if (url) img.src = url; else img.style.display = "none";
      li.append(img);
      const meta = document.createElement("div");
      meta.className = "friend-meta";
      const name = document.createElement("div");
      name.className = "friend-name";
      name.textContent = u.mc_name || u.global_name || u.username || "—";
      const sub = document.createElement("div");
      sub.className = "friend-sub";
      sub.textContent = u.friend_code ? "Code: " + u.friend_code : "Anfrage offen";
      meta.append(name, sub);
      li.append(meta);
      const acc = document.createElement("button");
      acc.textContent = "Annehmen";
      acc.onclick = async () => {
        acc.disabled = true;
        await invoke("kollegen_friend_accept", { fromId }).catch(() => null);
        refreshSocial(true);
      };
      li.append(acc);
      const dec = document.createElement("button");
      dec.textContent = "Ablehnen";
      dec.onclick = async () => {
        dec.disabled = true;
        await invoke("kollegen_friend_decline", { fromId }).catch(() => null);
        refreshSocial(true);
      };
      li.append(dec);
      list.append(li);
    }
  }

  function renderFriendsList(elId, arr, withRemove) {
    const list = $(elId);
    if (!list) return;
    list.innerHTML = "";
    if (!arr || !arr.length) {
      list.innerHTML = `<li style="color:#888;">Keine Freunde – füge welche über deinen Code hinzu.</li>`;
      return;
    }
    for (const u of arr) {
      const li = document.createElement("li");
      const img = document.createElement("img");
      img.className = "friend-avatar";
      const url = avatarUrl(u);
      if (url) img.src = url; else img.style.display = "none";
      li.append(img);
      const meta = document.createElement("div");
      meta.className = "friend-meta";
      const name = document.createElement("div");
      name.className = "friend-name";
      const ti = (window.kmTitleOf && window.kmTitleOf(u)) || null;
      const visible = u.mc_name || u.global_name || u.username || "—";
      if (ti && ti.data && ti.data.text) name.append(ti.data.text + " \u00b7 ");
      name.append(visible);
      const dot = document.createElement("span");
      dot.className = "status-dot " + (u.online ? "online" : "offline");
      dot.title = u.online ? "Online" : "Offline";
      name.append(" ", dot);
      const sub = document.createElement("div");
      sub.className = "friend-sub";
      const onlineTxt = u.online
        ? (u.server ? `Online auf ${u.server}` : "Online")
        : ("Offline" + (u.server ? ` \u00b7 zuletzt ${u.server}` : ""));
      const bb = (window.kmBadge && window.kmBadge(u)) || null;
      let subHtml = escapeHtml(onlineTxt) + (u.level ? ` \u00b7 Level ${u.level}` : "");
      if (bb && bb.data) subHtml += ` <span style="color:${escapeHtml(bb.data.color)}">${escapeHtml(bb.data.icon)}</span>`;
      sub.innerHTML = subHtml;
      meta.append(name, sub);
      li.append(meta);
      if (withRemove) {
        const pbtn = document.createElement("button");
        pbtn.textContent = "Profil";
        pbtn.onclick = () => (window.kmShowProfile ? window.kmShowProfile(u.friend_code) : showFriendProfile(u));
        li.append(pbtn);
        const dmbtn = document.createElement("button");
        dmbtn.textContent = "Nachricht";
        dmbtn.onclick = () => window.kmOpenDm && window.kmOpenDm(u.friend_code);
        li.append(dmbtn);
        const btn = document.createElement("button");
        btn.textContent = "Entfernen";
        btn.onclick = async () => {
          await invoke("kollegen_friend_remove", { targetId: u.id });
          refreshSocial(true);
        };
        li.append(btn);
      }
      list.append(li);
    }
  }

  // Freundes-Profil im Viewer anzeigen (Daten kommen mit /friends mit).
  function showFriendProfile(u) {
    const wrap = $("viewProfileResult");
    if (!wrap) return;
    wrap.innerHTML = "";
    const card = document.createElement("div");
    card.className = "view-profile-result";
    const hdr = document.createElement("div");
    hdr.style.display = "flex"; hdr.style.alignItems = "center"; hdr.style.gap = "0.7rem";
    const head = document.createElement("img");
    head.style.width = "56px"; head.style.height = "56px"; head.style.borderRadius = "10px"; head.style.objectFit = "cover"; head.style.background = "#222";
    const nm = u.mc_name || u.global_name || u.username || "";
    if (nm) head.src = `https://mc-heads.net/head/${encodeURIComponent(nm).replace(/%20/g, "_")}/256`;
    else if (u.avatar) head.src = u.avatar;
    else head.style.display = "none";
    head.onerror = () => { head.style.display = "none"; };
    hdr.append(head);
    const info = document.createElement("div");
    info.style.flex = "1"; info.style.minWidth = "0";
    let html = `<div style="font-weight:700;font-size:1.05rem;">${escapeHtml(nm || "—")} <span class="status-dot ${u.online ? "online" : "offline"}"></span></div>`;
    html += `<div style="color:var(--muted);font-size:.85rem;">${u.online ? (u.server ? `Online auf ${escapeHtml(u.server)}` : "Online") : "Offline"}</div>`;
    if (u.friend_code) html += `<div style="color:var(--muted);font-size:.85rem;">Code: ${escapeHtml(u.friend_code)}</div>`;
    if (u.uuid) html += `<div style="color:var(--muted);font-size:.75rem;">UUID ${escapeHtml(String(u.uuid).slice(0, 8))}…</div>`;
    if (u.banner_data_url) html += `<div style="margin-top:.5rem;"><img src="${escapeHtml(u.banner_data_url)}" style="max-width:100%;border-radius:8px;"/></div>`;
    if (u.bio) html += `<div style="margin-top:.5rem;">${escapeHtml(u.bio)}</div>`;
    info.innerHTML = html;
    hdr.append(info);
    card.append(hdr);
    wrap.append(card);
  }

  function openProfileModal() {
    const m = $("profileModal");
    if (!m) return;
    m.style.display = "flex";
    // Skin + Capes laden – das funktioniert unabhängig von socialMe
    // (Microsoft-Konto reicht), darf also NICHT hinter dem socialMe-Guard hängen.
    loadSkinChanger();

    // Load profile customization from settings
    try {
      invoke("get_settings").then(s => {
        const prof = (s && s.profile) ? s.profile : {};
        $("profileBio").value = prof.bio || "";
        if (prof.banner_data_url) {
          $("profileBannerPreview").src = prof.banner_data_url; $("profileBannerPreview").style.display = "";
        } else { $("profileBannerPreview").style.display = "none"; }
        $("profileAvatarChoice").value = prof.avatar_choice || "discord";
        if (prof.avatar_data_url) { $("profileAvatarPreview").src = prof.avatar_data_url; $("profileAvatarPreview").style.display = ""; }
        $("profilePublicToggle").checked = !!prof.public;
        $("profileServerUrl").value = prof.server_url || 'https://kollegen.me';
        $("profileServerToken").value = prof.server_token || '';
        // initial state: editing off
        setProfileEditMode(false);
      }).catch(()=>{});
    } catch (e) {}

    if (!socialMe) return;
    $("pmName").textContent = socialMe.mc_name || socialMe.username || "—";
    $("pmDiscord").textContent = "Discord: " + (socialMe.global_name || socialMe.username || "—");
    $("pmCode").textContent = socialMe.friend_code || "—";
    const acc = $("pmAccounts");
    acc.innerHTML = "";
    (socialMe.accounts || []).forEach((a) => {
      const d = document.createElement("div");
      d.className = "account-chip";
      d.textContent = (a.type === "discord" ? "Discord: " : "") + (a.name || a.id);
      acc.append(d);
    });
    if (socialMe.mc_name) showSkinFromName(socialMe.mc_name);
  }

  // Profile editing helpers
  function setProfileEditMode(on) {
    const editBtn = $("profileEditToggle");
    const saveBtn = $("profileSaveBtn");
    const cancelBtn = $("profileCancelBtn");
    const inputs = ["profileBio","profileBannerInput","profileAvatarChoice","profileAvatarUpload","profilePublicToggle","viewProfileName","viewProfileBtn"];
    if (on) {
      editBtn.textContent = "Bearbeiten (An)";
      saveBtn.style.display = "inline-block";
      cancelBtn.style.display = "inline-block";
      inputs.forEach(i => { const el=$(i); if (el) el.removeAttribute('disabled'); });
    } else {
      editBtn.textContent = "Bearbeiten";
      saveBtn.style.display = "none";
      cancelBtn.style.display = "none";
      inputs.forEach(i => { const el=$(i); if (el) el.setAttribute('disabled', 'disabled'); });
    }
  }

  // Save profile customization to settings
  async function saveProfileCustomization() {
    try {
      const s = await invoke('get_settings');
      const profile = (s && s.profile) ? s.profile : {};
      profile.bio = $("profileBio").value.trim();
      profile.public = !!$("profilePublicToggle").checked;
      profile.avatar_choice = $("profileAvatarChoice").value;
      if ($("profileBannerPreview").src) profile.banner_data_url = $("profileBannerPreview").src;
      if ($("profileAvatarPreview").src) profile.avatar_data_url = $("profileAvatarPreview").src;
      profile.server_url = $("profileServerUrl").value.trim() || "";
      profile.server_token = $("profileServerToken").value.trim() || "";
      s.profile = profile;
      await invoke('save_settings', { settings: s });
      toast('Profil gespeichert', 'ok');
      setProfileEditMode(false);
    } catch (e) { toast('Speichern fehlgeschlagen: '+e, 'error'); }
  }

  // Publish profile to the Kollegen-Cloud (default) or a custom backend.
  async function publishProfileToServer() {
    const serverUrl = $("profileServerUrl").value.trim();
    const token = $("profileServerToken").value.trim();

    const profile = {
      uuid: socialMe && socialMe.uuid ? socialMe.uuid : null,
      name: socialMe && socialMe.mc_name ? socialMe.mc_name : (socialMe && socialMe.username ? socialMe.username : null),
      accounts: socialMe && socialMe.accounts ? socialMe.accounts : [],
      profile: {
        bio: $("profileBio").value.trim(),
        banner_data_url: $("profileBannerPreview").src || null,
        avatar_data_url: $("profileAvatarPreview").src || null,
        avatar_choice: $("profileAvatarChoice").value,
        public: !!$("profilePublicToggle").checked,
      }
    };

    // Kein eigenes Backend/Token hinterlegt → Kollegen-Cloud via Launcher-
    // Discord-Session (kein manuelles Token nötig).
    if (!serverUrl || !token) {
      const j = await invoke("kollegen_publish_profile", { profile });
      if (j && j.ok) {
        toast('Profil veröffentlicht (Kollegen-Cloud)', 'ok');
        refreshSocial(true);
      } else {
        toast('Publish fehlgeschlagen: ' + ((j && j.error) || 'unbekannt'), 'error');
      }
      return;
    }

    try {
      const res = await fetch(serverUrl.replace(/\/$/, '') + '/profile', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + token,
        },
        body: JSON.stringify(profile),
      });
      const j = await res.json();
      if (res.ok) {
        toast('Profil veröffentlicht', 'ok');
      } else {
        toast('Publish fehlgeschlagen: ' + (j.error || res.status), 'error');
      }
    } catch (e) { toast('Verbindung zum Server fehlgeschlagen: ' + e, 'error'); }
  }

  // Browse public profiles on the provided server
  async function browsePublicProfiles(search) {
    const serverUrl = $("profileServerUrl").value.trim();
    const wrap = $("viewProfileResult");
    wrap.innerHTML = "";
    if (!serverUrl) {
      // Default: Kollegen-Cloud (https://kollegen.me) via Launcher-Backend.
      const j = await invoke("kollegen_browse_profiles", { search: search || "" });
      if (j && j.error) return alert('Fehler beim Laden: ' + j.error);
      renderProfileItems(j && j.items ? j.items : [], wrap, true);
      return;
    }
    const q = new URL(serverUrl.replace(/\/$/, '') + '/profiles');
    if (search) q.searchParams.set('search', search);
    try {
      const res = await fetch(q.toString());
      const j = await res.json();
      if (!res.ok) return alert('Fehler beim Laden: ' + (j.error || res.status));
      renderProfileItems(j.items || [], wrap, false);
    } catch (e) { alert('Fehler: ' + e); }
  }

  function renderProfileItems(items, wrap, isCloud) {
    if (!items.length) { wrap.textContent = 'Keine Profile gefunden.'; return; }
    for (const p of items) {
      const el = document.createElement('div'); el.className = 'view-profile-result';
      const hdr = document.createElement('div'); hdr.style.display = 'flex'; hdr.style.alignItems = 'center'; hdr.style.gap = '0.6rem';
      const img = document.createElement('img');
      const nm = p.name || p.mc_name || '';
      img.src = p.avatar_data_url || (nm ? `https://mc-heads.net/head/${encodeURIComponent(nm).replace(/%20/g, "_")}/256` : '');
      img.style.width = '48px'; img.style.height = '48px'; img.style.borderRadius = '8px'; img.style.background = '#222'; img.onerror = () => { img.style.display = 'none'; };
      const name = document.createElement('div'); name.textContent = p.name || '—'; name.style.fontWeight = '700';
      hdr.append(img, name);
      const bio = document.createElement('div'); bio.textContent = p.bio || ''; bio.style.marginTop = '0.25rem'; bio.style.color = 'var(--muted)';
      el.append(hdr, bio);
      const btns = document.createElement('div'); btns.style.marginTop = '0.4rem'; btns.style.display = 'flex'; btns.style.gap = '0.4rem';
      const viewBtn = document.createElement('button');
      viewBtn.textContent = 'Öffnen';
      if (isCloud) {
        // In der App öffnen statt im Browser tab
        viewBtn.onclick = () => showCloudProfile(p);
      } else {
        const b = $("profileServerUrl").value.trim().replace(/\/$/, '');
        viewBtn.onclick = () => { window.open(b + '/profiles/' + p.id, '_blank'); };
      }
      btns.append(viewBtn);
      el.append(btns);
      wrap.append(el);
    }
  }

  // Öffentliches Cloud-Profil im Viewer öffnen (egal ob in Browse-Liste).
  function showCloudProfile(p) {
    const wrap = $("viewProfileResult");
    if (!wrap) return;
    wrap.innerHTML = "";
    const card = document.createElement("div");
    card.className = "view-profile-result";
    const hdr = document.createElement("div");
    hdr.style.display = "flex"; hdr.style.alignItems = "center"; hdr.style.gap = "0.7rem";
    const head = document.createElement("img");
    head.style.width = "56px"; head.style.height = "56px"; head.style.borderRadius = "10px"; head.style.objectFit = "cover"; head.style.background = "#222";
    const nm = p.name || p.mc_name || "";
    if (nm) head.src = `https://mc-heads.net/head/${encodeURIComponent(nm).replace(/%20/g, "_")}/256`;
    else if (p.avatar_data_url) head.src = p.avatar_data_url;
    else head.style.display = "none";
    head.onerror = () => { head.style.display = "none"; };
    hdr.append(head);
    const info = document.createElement("div");
    info.style.flex = "1"; info.style.minWidth = "0";
    let html = `<div style="font-weight:700;font-size:1.05rem;">${escapeHtml(nm || p.name || "—")}</div>`;
    if (p.code) html += `<div style="color:var(--muted);font-size:.85rem;">Code: ${escapeHtml(p.code)}</div>`;
    if (p.banner_data_url) html += `<div style="margin-top:.5rem;"><img src="${escapeHtml(p.banner_data_url)}" style="max-width:100%;border-radius:8px;"/></div>`;
    if (p.bio) html += `<div style="margin-top:.5rem;">${escapeHtml(p.bio)}</div>`;
    info.innerHTML = html;
    hdr.append(info);
    card.append(hdr);
    wrap.append(card);
  }

  // Cancel edits: reload from settings
  function cancelProfileEdits() {
    invoke('get_settings').then(s => {
      const prof = (s && s.profile) ? s.profile : {};
      $("profileBio").value = prof.bio || "";
      if (prof.banner_data_url) { $("profileBannerPreview").src = prof.banner_data_url; $("profileBannerPreview").style.display = ""; } else { $("profileBannerPreview").style.display = "none"; }
      $("profileAvatarChoice").value = prof.avatar_choice || "discord";
      if (prof.avatar_data_url) { $("profileAvatarPreview").src = prof.avatar_data_url; $("profileAvatarPreview").style.display = ""; }
      $("profilePublicToggle").checked = !!prof.public;
      setProfileEditMode(false);
    }).catch(()=>{});
  }

  // View other profile by name (uses new Tauri command)
  async function viewProfileByName() {
    const name = $("viewProfileName").value.trim();
    if (!name) return;
    const resWrap = $("viewProfileResult");
    resWrap.textContent = 'Lade…';
    try {
      const res = await invoke('get_minecraft_profile_by_name', { name });
      if (res && res.error) { resWrap.textContent = 'Fehler: ' + res.error; return; }
      // show basic info + skin preview
      resWrap.innerHTML = '';
      const title = document.createElement('div'); title.textContent = `${res.name} · ${res.uuid}`; resWrap.append(title);
      if (res.skin_data_url) {
        const img = document.createElement('img'); img.src = res.skin_data_url; img.style.maxWidth = '140px'; img.style.borderRadius='6px'; resWrap.append(img);
        showSkin(res.skin_data_url);
      } else if (res.skin_url) {
        const img = document.createElement('img'); img.src = res.skin_url; img.style.maxWidth = '140px'; img.style.borderRadius='6px'; resWrap.append(img);
        showSkin(res.skin_url);
      }
      if (res.cape_data_url) { const c = document.createElement('img'); c.src = res.cape_data_url; c.style.maxWidth='160px'; resWrap.append(c); }
      const raw = document.createElement('pre'); raw.style.maxHeight='8rem'; raw.style.overflow='auto'; raw.textContent = JSON.stringify(res.raw_textures || {}, null, 2); resWrap.append(raw);
    } catch (e) { resWrap.textContent = 'Fehler: ' + e; }
  }

  let skinViewer = null;
  let currentSkinUrl = null;
  let currentCapeUrl = null;

  // Lädt (sofern vorhanden) das aktive Cape auf den 3D-Viewer.
  function applyCapeToViewer() {
    if (!skinViewer || !currentCapeUrl) return;
    try {
      const p = skinViewer.loadCape(currentCapeUrl);
      if (p && typeof p.catch === "function") p.catch(() => {});
    } catch (_) {}
  }

  function showSkin(url) {
    const canvas = $("skinCanvas");
    if (!canvas || !url) return;
    currentSkinUrl = url;
    const sv3d = window.skinview3d;
    // WebGL-Verfügbarkeit prüfen (SteamDeck/WebKit ohne GPU-Kontext → direkt
    // auf das 2D-Fallback-Bild gehen statt auf einen leeren Canvas zu warten).
    let webglOk = false;
    try {
      const probeCanvas = document.createElement("canvas");
      const probe = probeCanvas.getContext("webgl2") || probeCanvas.getContext("webgl");
      webglOk = !!(probe && probe.getParameter);
    } catch (_) {}
    if (sv3d && sv3d.SkinViewer && sv3d.IdleAnimation && webglOk) {
      try {
        if (skinViewer) { try { skinViewer.dispose && skinViewer.dispose(); } catch (_) {} skinViewer = null; }
        canvas.style.display = "";
        const fb = canvas.parentElement.querySelector("img.skin-fallback");
        if (fb) fb.remove();
        skinViewer = new sv3d.SkinViewer({ canvas: canvas, width: 300, height: 600 });
        // Dieser skinview3d-Build wertet die `skin`-Option nicht aus – die
        // Textur muss explizit via loadSkin() geladen werden.
        const p = skinViewer.loadSkin(url);
        if (p && typeof p.then === "function") {
          p.then(() => applyCapeToViewer()).catch(() => applyCapeToViewer());
        }
        skinViewer.animation = new sv3d.IdleAnimation();
        applyCapeToViewer();
        return;
      } catch (e) {
        console.error("3D-Skin fehlgeschlagen, Fallback-Bild:", e);
      }
    }
    const wrap = canvas.parentElement;
    let fb = wrap.querySelector("img.skin-fallback");
    if (!fb) { fb = new Image(); fb.className = "skin-fallback"; wrap.append(fb); }
    fb.src = url;
    canvas.style.display = "none";
  }
  function showSkinFromName(name) {
    if (name) showSkin(`https://mc-heads.net/skin/${encodeURIComponent(name)}`);
  }

  // ── Skin-Bibliothek + Cape-Wechsler im Profil ──
  function loadSkinChanger() {
    // Capes IMMER laden – unabhängig von der Skin-Bibliothek, damit sie auch
    // dann erscheinen, wenn `skin_list` fehlschlägt oder leer ist.
    invoke("skin_mc_profile").then(renderCapes).catch(() => renderCapes({}));

    invoke("skin_list").then(list => {
      renderSkinLibrary(list);
      const active = (list.skins || []).find(s => s.name === list.active);
      if (active && active.url) {
        showSkin(active.url);
        return;
      }
      // Kein lokaler aktiver Skin: automatisch vom Minecraft-Namen ziehen
      // (funktioniert ohne Microsoft-Login über das öffentliche Mojang-Profil).
      const fromMcProfile = (prof) => {
        const skins = (prof && prof.skins) || [];
        const s = skins.find(x => x.state === "ACTIVE") || skins[0];
        if (s && s.url) { showSkin(s.url); return; }
        if (socialMe && socialMe.mc_name) showSkinFromName(socialMe.mc_name);
      };
      const fromName = () => {
        if (socialMe && socialMe.mc_name) {
          invoke("get_minecraft_profile_by_name", { name: socialMe.mc_name }).then(res => {
            if (res && !res.error && (res.skin_data_url || res.skin_url)) showSkin(res.skin_data_url || res.skin_url);
            else invoke("skin_mc_profile").then(fromMcProfile).catch(() => showSkinFromName(socialMe.mc_name));
          }).catch(() => {
            invoke("skin_mc_profile").then(fromMcProfile).catch(() => showSkinFromName(socialMe.mc_name));
          });
        } else {
          invoke("skin_mc_profile").then(fromMcProfile).catch(() => {});
        }
      };
      fromName();
    }).catch(() => {});
  }

  function renderSkinLibrary(list) {
    const lib = $("skinLibrary");
    if (!lib) return;
    lib.innerHTML = "";
    (list.skins || []).forEach(s => {
      const el = document.createElement("button");
      el.className = "skin-lib-item" + (s.name === list.active ? " active" : "");
      const img = document.createElement("img");
      img.src = s.url;
      img.alt = s.name;
      el.append(img);
      el.title = s.name + " (wechseln)";
      el.onclick = () => switchSkin(s);
      lib.append(el);
    });
    if (!(list.skins || []).length) {
      const hint = document.createElement("div");
      hint.className = "socials-hint";
      hint.textContent = "Noch keine Skins – lade deinen aktuellen herunter oder lade eine Datei hoch.";
      lib.append(hint);
    }
  }

  function switchSkin(s) {
    showSkin(s.url);
    invoke("skin_set_active", { name: s.name }).catch(() => {});
    invoke("skin_upload", { name: s.name, data: s.url, variant: "classic" }).then(r => {
      if (r && r.mc_uploaded) toast("Skin gewechselt", "ok");
      else if (r && r.ok) toast("Lokal gespeichert (kein MC-Upload möglich)", "ok");
      else if (r && r.error) toast(r.error, "error");
      loadSkinChanger();
    }).catch(e => toast("Skin wechseln fehlgeschlagen: " + e, "error"));
  }

  function renderCapes(prof) {
    const list = $("capeList");
    if (!list) return;
    list.innerHTML = "";
    if (!prof || prof.error) {
      list.innerHTML = '<div class="socials-hint">Mit Microsoft-Konto anmelden, um Capes zu sehen &amp; auszurüsten.</div>';
      return;
    }
    const capes = (prof && prof.capes) ? prof.capes : [];
    if (!capes.length) {
      list.innerHTML = '<div class="socials-hint">Keine Capes verfügbar.</div>';
      return;
    }
    capes.forEach(c => {
      const el = document.createElement("div");
      el.className = "cape-item" + (c.state === "ACTIVE" ? " active" : "");
      const img = document.createElement("img");
      img.src = c.url;
      const capName = c.alias || c.capeName || c.name || "Cape";
      img.alt = capName;
      img.loading = "lazy";
      // Bricht das Bild fehl (z. B. CORS), zeigen wir nur den Namen, damit
      // die Capes weiterhin unterscheidbar bleiben.
      img.onerror = () => { img.style.visibility = "hidden"; };
      const name = document.createElement("span");
      name.textContent = capName;
      const btn = document.createElement("button");
      btn.textContent = c.state === "ACTIVE" ? "Aktiv" : "Ausrüsten";
      btn.disabled = c.state === "ACTIVE";
      btn.onclick = () => equipCape(c.id, c.url);
      el.append(img, name, btn);
      list.append(el);
      if (c.state === "ACTIVE") {
        currentCapeUrl = c.url;
        applyCapeToViewer();
      }
    });
  }

  function equipCape(id, url) {
    invoke("cape_equip", { capeId: id }).then(r => {
      if (r && r.ok) {
        toast("Cape ausgerüstet", "ok");
        if (url) { currentCapeUrl = url; applyCapeToViewer(); }
        loadSkinChanger();
      }
      else toast((r && r.error) || "Fehler", "error");
    }).catch(e => toast("Cape fehlgeschlagen: " + e, "error"));
  }

  let joinFriendTarget = null;

  // Opens the instance picker for joining a friend. Only instances with the
  // same Minecraft version as the friend are offered (if the version could be
  // detected from their rich presence); otherwise all instances are shown.
  function openJoinFriend(friend) {
    joinFriendTarget = friend;
    const list = $("joinFriendInstances");
    const hint = $("joinFriendHint");
    list.innerHTML = "";

    const all = instancesCache || [];
    let candidates = all;
    if (friend.version) {
      candidates = all.filter((i) => i.version === friend.version);
    }

    if (!candidates.length) {
      hint.textContent = friend.version
        ? `Keine Instanz mit Version ${friend.version} gefunden – zeige alle Instanzen.`
        : "Wähle eine Instanz, um deinem Freund beizutreten.";
      candidates = all;
    } else {
      hint.textContent = friend.version
        ? `Instanzen mit Version ${friend.version}:`
        : "Wähle eine Instanz, um deinem Freund beizutreten.";
    }

    if (!candidates.length) {
      const li = document.createElement("li");
      li.textContent = "Keine Instanzen vorhanden.";
      list.append(li);
      $("joinFriendModal").style.display = "flex";
      return;
    }

    for (const inst of candidates) {
      const li = document.createElement("li");
      const label = document.createElement("span");
      label.textContent = `${inst.name} (${inst.version}${
        inst.loader ? " · " + inst.loader : ""
      })`;
      const btn = document.createElement("button");
      btn.textContent = "Beitreten";
      btn.onclick = () => selectJoinInstance(inst.name);
      li.append(label, btn);
      list.append(li);
    }
    $("joinFriendModal").style.display = "flex";
  }

  async function selectJoinInstance(name) {
    if (!joinFriendTarget || !joinFriendTarget.join_secret) return;
    const secret = joinFriendTarget.join_secret;
    $("joinFriendModal").style.display = "none";
    try {
      const res = await invoke("discord_join", {
        instanceName: name,
        server: secret,
      });
      alert(res);
      refreshDiscord();
    } catch (e) {
      alert("Beitreten fehlgeschlagen: " + e);
    }
  }

  // ── Discord OAuth (browser login) ──
  async function discordOauthStart() {
    try {
      await invoke("discord_oauth_start");
      renderDiscordLogin({ state: "waiting" });
    } catch (e) {
      renderDiscordLogin({ state: "error", message: String(e) });
    }
  }

  async function discordOauthLogout() {
    try {
      await invoke("discord_oauth_logout");
    } catch (e) {
      console.error(e);
    }
    renderDiscordLogin({ state: "idle" });
  }

  function renderDiscordLogin(info) {
    const box = $("discordLoginStatus");
    const btn = $("discordLoginBtn");
    box.textContent = "";
    if (info.state === "waiting") {
      btn.style.display = "none";
      box.textContent = "Browser wurde geöffnet – bitte bei Discord anmelden…";
    } else if (info.state === "done") {
      // Verbunden-Status zeigt #discordStatus an; "Abmelden" ist bewusst nur in
      // den Einstellungen (Verbindungen → Discord abmelden) zu finden.
      btn.style.display = "none";
    } else if (info.state === "error") {
      btn.style.display = "";
      box.textContent = `Fehler: ${info.message}`;
    } else {
      btn.style.display = "";
    }
  }

  async function refreshDiscordLogin() {
    try {
      const s = await invoke("discord_oauth_status");
      if (s.logged_in && s.user) {
        renderDiscordLogin({ state: "done", user: s.user });
      } else {
        renderDiscordLogin({ state: "idle" });
      }
    } catch (e) {
      console.error(e);
    }
  }

  function renderDiscordInvites(invites, connected) {
    const list = $("discordInvites");
    list.innerHTML = "";
    if (!invites.length) {
      const msg = connected
        ? "Keine Einladungen."
        : "Keine Einladungen – verbinde dich mit Discord, um Server-Einladungen von Freunden zu erhalten.";
      list.innerHTML = `<li style='color:#888;'>${msg}</li>`;
      return;
    }
    for (const inv of invites) {
      const li = document.createElement("li");
      const label = document.createElement("span");
      label.textContent = inv.secret;
      const join = document.createElement("button");
      join.textContent = "Beitreten";
      join.onclick = () => joinDiscordInvite(inv.secret);
      const copy = document.createElement("button");
      copy.textContent = "Kopieren";
      copy.onclick = () => copyText(inv.secret);
      const del = document.createElement("button");
      del.textContent = "Verwerfen";
      del.onclick = () => dismissInvite(inv.secret);
      const actions = document.createElement("span");
      actions.append(join, copy, del);
      li.append(label, actions);
      list.append(li);
    }
  }

  async function joinDiscordInvite(secret) {
    const instName = activeInstance || (instancesCache[0] && instancesCache[0].name);
    if (!instName) return alert("Keine Instanz zum Beitreten vorhanden.");
    try {
      const res = await invoke("discord_join", { instanceName: instName, server: secret });
      alert(res);
      await invoke("discord_dismiss_invite", { secret });
      refreshDiscord();
    } catch (e) {
      alert("Beitreten fehlgeschlagen: " + e);
    }
  }

  async function dismissInvite(secret) {
    try {
      await invoke("discord_dismiss_invite", { secret });
      refreshDiscord();
    } catch (e) {
      console.error(e);
    }
  }

async function createInstance() {
  const name = $("iName").value.trim();
  const version = $("iVersion").value;
  const loader = $("iLoader").value;
  const loaderVersion = $("iLoaderVer").value || null;
  if (!name || !version) return alert("Name und Version erforderlich");
  
  try {
    $("createBtn").disabled = true;
    $("createBtn").textContent = "Erstelle...";
    
    // 1. Create instance in json and show immediately
    await invoke("create_instance", { name, version, loader, loaderVersion });
    await refreshInstances();

    // 2. Install files
    $("createBtn").textContent = "Installiere...";
    await invoke("install_instance", { name, version, loader, loaderVersion });
    
    alert("Instanz erfolgreich erstellt und installiert!");
    $("iName").value = "";
    await refreshInstances();
  } catch (e) {
    alert("Fehler bei Instanz-Installation: " + e);
  } finally {
    $("createBtn").disabled = false;
    $("createBtn").textContent = "Erstellen";
  }
}

async function launchGame(name, isAuto) {
  if (!isAuto) conflictHandled = false;
  activeInstance = name;

  const inst = instancesCache.find((i) => i.name === name);
    if (inst) {
      try {
        // The actual server is detected later from the game log (see
        // backend). We only publish the version/loader here; the server
        // field is filled in automatically once you join a server.
        await invoke("set_discord_presence", {
          details: "Spielt Minecraft",
          stateStr: `${inst.version} · ${inst.loader}`,
          largeText: inst.name,
          server: null,
          players: null,
        });
      } catch (e) {
        console.error("Discord RPC fehlgeschlagen:", e);
      }
    }

  try {
    const result = await invoke("launch_game", { instanceName: name, server: null });
    alert(result);
  } catch (e) {
    alert("Launch fehlgeschlagen: " + e);
  }
  refreshLogs();
}

async function deleteInstance(name, id) {
  if (!confirm(`Instanz '${name}' wirklich löschen?`)) return;
  await invoke("delete_instance", { name, id: id || null });
  await refreshInstances();
}

$("iVersion").onchange = updateLoaderVersions;
$("iLoader").onchange = updateLoaderVersions;

function qrDataUrl(text) {
  try {
    if (typeof qrcodegen === "undefined" || !qrcodegen.QrCode) return "";
    const qr = qrcodegen.QrCode.encodeText(text, qrcodegen.QrCode.Ecc.MEDIUM);
    const border = 4;
    const scale = 8;
    const n = qr.size + 2 * border;
    const canvas = document.createElement("canvas");
    canvas.width = n * scale;
    canvas.height = n * scale;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = "#000000";
    for (let y = 0; y < qr.size; y++) {
      for (let x = 0; x < qr.size; x++) {
        if (qr.getModule(x, y)) ctx.fillRect((x + border) * scale, (y + border) * scale, scale, scale);
      }
    }
    return canvas.toDataURL("image/png");
  } catch (e) {
    return "";
  }
}

async function showLoginQr(url) {
  const local = qrDataUrl(url);
  const src = local || "https://api.qrserver.com/v1/create-qr-code/?size=460x460&margin=8&data=" + encodeURIComponent(url);
  const modal = $("loginQrModal");
  const modalImg = $("loginQrModalImg");
  // Im Konsolenmodus ist der Header (wo sonst der QR sitzt) ausgeblendet:
  // den QR als großes Popup anzeigen, damit man ihn vom Sofa scannen kann.
  if (consoleNavActive && modal && modalImg) {
    // Darunterliegendes Modal (z. B. Einstellungen) schließen, damit B/SELECT
    // später das QR-Popup schließt und der Fokus dort landet.
    if (typeof consoleCloseModal === "function") consoleCloseModal();
    modalImg.src = src;
    const code = (url.match(/[?&]otc=([^&]+)/) || [])[1] || "";
    const codeEl = $("loginQrCode");
    if (codeEl) codeEl.textContent = code;
    modal.style.display = "flex";
    return;
  }
  const wrap = $("loginQrWrap");
  const img = $("loginQr");
  if (!wrap || !img) return;
  img.src = src;
  wrap.style.display = "flex";
}

$("loginQrClose").onclick = () => {
  const modal = $("loginQrModal");
  if (modal) modal.style.display = "none";
  if (consoleNavActive && typeof refreshConsoleFocusables === "function") refreshConsoleFocusables();
};

$("authBtn").onclick = async () => {
  try {
    const res = await invoke("auth_start");
    if (res.user_code && res.verification_uri) {
      const loginUrl = `${res.verification_uri}?otc=${res.user_code}`;
      if (consoleNavActive) {
        showLoginQr(loginUrl);
        refreshConsoleFocusables();
      } else {
        $("loginUrl").value = loginUrl;
        $("loginInfo").style.display = "flex";
        try { window.open(loginUrl, '_blank'); } catch (e) {}
        copyText(loginUrl);
        await showLoginQr(loginUrl);
        alert(`Microsoft Login:\n\nBrowser wurde geöffnet.\nDu kannst den QR-Code auch mit dem Handy scannen.`);
      }
    }
    refreshAuth();
  } catch (e) {
    alert("Login fehlgeschlagen: " + e);
  }
  };


async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text);
  } catch (e) {
    const ta = document.createElement("textarea");
    ta.value = text;
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand("copy"); } catch (_) {}
    document.body.removeChild(ta);
  }
}

$("copyUrlBtn").onclick = () => {
  copyText($("loginUrl").value);
  $("copyUrlBtn").textContent = "Kopiert!";
  setTimeout(() => ($("copyUrlBtn").textContent = "Kopieren"), 1500);
};

// Vollbild umschalten mit F11 (kein extra Button mehr).
document.addEventListener("keydown", async (e) => {
  if (e.key === "F11") {
    e.preventDefault();
    try {
      await invoke("toggle_fullscreen");
    } catch (err) {
      console.error("Vollbild fehlgeschlagen:", err);
    }
  }
});

$("createBtn").onclick = createInstance;

$("discordLoginBtn").onclick = discordOauthStart;

// ─=== Akzentfarbe (wird auch in die Theme-Datei für den In-Game-Mod geschrieben) ===
function readCssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

// Schreibt die aktuellen Launcher-Farben in eine Datei, damit der Kollegen-Client
// In-Game-Mod sein Menü in exakt derselben Farbe rendern kann (auch wenn die
// Launcher-Farbe zur Laufzeit geändert wird).
function pushTheme() {
  const theme = {
    bg: readCssVar("--bg") || "#0d0d12",
    panel: readCssVar("--panel") || "#1a1a24",
    panel2: readCssVar("--panel-2") || "#21212e",
    accent: readCssVar("--accent") || "#f5a623",
    accent2: readCssVar("--accent2") || "#ff7a00",
    text: readCssVar("--text") || "#f3e9d8",
    muted: readCssVar("--muted") || "#b9a98c",
    border: readCssVar("--border") || "#34303a",
    danger: readCssVar("--danger") || "#ff5b6e",
  };
  invoke("write_theme_file", { json: JSON.stringify(theme) }).catch(() => {});
}

// Einladen zum Launcher: Text + Link zum GitHub-Repo.
$("inviteLauncherBtn").onclick = () => {
  const url = "https://github.com/FluffyBento/KollegenClient";
  copyText(url);
  invoke("open_url", { url }).catch(() => window.open(url, "_blank"));
};

// Beim Start die aktuelle Launcher-Theme an den Mod weitergeben.
pushTheme();

// ─=== Settings (Verbindungen / Themes / Importieren) ===

// Farbschemata je Theme (reine Farbpaletten, kein Hell/Dunkel-Modus mehr).
// `head` = Minecraft-Kopf (PNG in dist/heads) links neben dem Theme.
const THEMES = {
  Kollegen:    { bg:"#0a0c10", panel:"#12141d", panel2:"#161922", accent:"#ffaa00", accent2:"#f5c518", text:"#ededed", muted:"#9ca3af", border:"#282d3d", danger:"#ff5b6e", head:"Kollegen.png" },
  Limit_Los:   { bg:"#140a0a", panel:"#1d0f0f", panel2:"#271414", accent:"#FF0000", accent2:"#cc0000", text:"#f3e9e9", muted:"#c39b9b", border:"#600000", danger:"#ff5b6e", head:"heads/Limit_Los.png" },
  FluffyBento: { bg:"#0d0912", panel:"#160f1e", panel2:"#1e1524", accent:"#b054d8", accent2:"#7c2fa3", accent3:"#86e14a", text:"#f6ecfa", muted:"#c2a8d4", border:"#332050", danger:"#ff6b9d", head:"heads/FluffyBento.png" },
  Annanastv:   { bg:"#1a1605", panel:"#221d08", panel2:"#2b250c", accent:"#f1c40f", accent2:"#d4ac0d", text:"#fbf7e6", muted:"#cabf8e", border:"#3a3211", danger:"#ff7a59", head:"heads/Annanastv_.png" },
  T_son_:      { bg:"#0c1410", panel:"#112019", panel2:"#16271e", accent:"#2ecc71", accent2:"#239b56", text:"#e8f5ee", muted:"#9bc2ac", border:"#244234", danger:"#ff5b6e", head:"heads/T_son_.png" },
  zSpicyyy:    { bg:"#0a1218", panel:"#0f1a22", panel2:"#14222c", accent:"#3498db", accent2:"#2471a3", text:"#e6f1f8", muted:"#9bbccc", border:"#223a48", danger:"#ff7a59", head:"heads/zSpicyy.png" },
  Irongirl:    { bg:"#14171a", panel:"#1c2024", panel2:"#24292e", accent:"#bdc3c7", accent2:"#95a5a6", text:"#f0f3f5", muted:"#aab4ba", border:"#2e343a", danger:"#ff5b6e", head:"heads/Irongirl_.png" },

  Machtarchiv: { bg:"#1a0000", panel:"#260a0a", panel2:"#300f0f", accent:"#e22626", accent2:"#ff8a8a", accent3:"#ffffff", text:"#ffffff", muted:"#d9b3b3", border:"#4a1414", danger:"#ff5252", head:"heads/Machtarchiv.png" },
  Zerocraft77: { bg:"#050505", panel:"#0a0a0a", panel2:"#101010", accent:"#b0b0b0", accent2:"#6e6e6e", text:"#e6e6e6", muted:"#8a8a8a", border:"#1f1f1f", danger:"#ff5252", head:"heads/Zerocraft77.png" },
  Erhaltunq:   { bg:"#1c1610", panel:"#241c14", panel2:"#2c2118", accent:"#d4b482", accent2:"#a98c55", text:"#f3ecdf", muted:"#c9b79a", border:"#3a2d1e", danger:"#ff6b5e", head:"heads/Erhaltunq.png" },
};

let currentSettings = null;

async function loadSettingsOnce() {
  if (currentSettings) return currentSettings;
  try {
    currentSettings = await invoke("get_settings");
  } catch (e) {
    currentSettings = { theme: "Kollegen", theme_mode: "dark" };
  }
  return currentSettings;
}

function applyTheme(name) {
  const pal = THEMES[name] || THEMES.Kollegen;
  const r = document.documentElement.style;
  r.setProperty("--bg", pal.bg);
  r.setProperty("--panel", pal.panel);
  r.setProperty("--panel-2", pal.panel2);
  r.setProperty("--accent", pal.accent);
  r.setProperty("--accent2", pal.accent2);
  r.setProperty("--accent3", pal.accent3 || pal.accent2 || pal.accent);
  r.setProperty("--text", pal.text);
  r.setProperty("--muted", pal.muted);
  r.setProperty("--border", pal.border);
  r.setProperty("--danger", pal.danger);
  document.documentElement.setAttribute("data-theme", name);
  pushTheme();
}

async function applySavedTheme() {
  const s = await loadSettingsOnce();
  const name = THEMES[s.theme] ? s.theme : "Kollegen";
  applyTheme(name);
}

async function saveTheme(name) {
  const s = await loadSettingsOnce();
  s.theme = name;
  try {
    await invoke("save_settings", { settings: s });
  } catch (e) {}
  applyTheme(name);
}

// ─=== Settings panel wiring ===

// Öffnet ein Settings-Panel deterministisch: Tab-Active-Klasse setzen, nur das
// passende Panel anzeigen und dessen Inhalt (neu) rendern. Ein einziger Pfad für
// Öffnen und Tab-Klick verhindert, dass Panels leer/unsichtbar bleiben, bis man
// einmal weg- und wieder zurückwechselt (bekannter "leeres Panel"-Bug).
function showSettingsTab(t) {
  document.querySelectorAll(".settings-tab").forEach((el) => {
    el.classList.toggle("active", el.dataset.tab === t);
  });
  $("settingsConnections").style.display = t === "connections" ? "" : "none";
  $("settingsThemes").style.display = t === "themes" ? "" : "none";
  $("settingsImport").style.display = t === "import" ? "" : "none";
  $("settingsUpdates").style.display = t === "updates" ? "" : "none";
  $("settingsSteamDeck").style.display = t === "steamdeck" ? "" : "none";
  if (t === "themes") { renderThemeList(); renderLayoutOptions(); }
  if (t === "connections") { refreshSettingsAccounts(); }
  if (t === "steamdeck") { initSteamDeckTab(); }
}

const settingsModalEl = $("settingsModal");
$("settingsBtn").onclick = async () => {
  settingsModalEl.style.display = "flex";
  await loadSettingsOnce();
  const cmt = $("companionModToggle");
  if (cmt && currentSettings) cmt.checked = !!currentSettings.companion_mod;
  const pmt = $("perfModsToggle");
  if (pmt && currentSettings) pmt.checked = currentSettings.perf_mods !== false;
  // Immer auf den Verbindungen-Tab zurücksetzen, damit der Startzustand
  // reproduzierbar ist und alle Panels korrekt (neu) gerendert werden.
  showSettingsTab("connections");
  if (consoleNavActive) setConsoleFocusFirstNonIcon();
};

const companionModToggle = $("companionModToggle");
if (companionModToggle) {
  companionModToggle.onchange = async () => {
    const s = await loadSettingsOnce();
    s.companion_mod = companionModToggle.checked;
    try { await invoke("save_settings", { settings: s }); } catch (e) {}
  };
}
const perfModsToggle = $("perfModsToggle");
if (perfModsToggle) {
  perfModsToggle.onchange = async () => {
    const s = await loadSettingsOnce();
    s.perf_mods = perfModsToggle.checked;
    try { await invoke("save_settings", { settings: s }); } catch (e) {}
  };
}
// ─ SteamDeck / Konsolen-Modus ─
// Schaltet den TV/Controller-optimierten "Konsolen"-Look um und sorgt dafür,
// dass der Controller-Modus im Kollegen-Begleit-Mod automatisch aktiviert wird.
const steamdeckToggle = $("steamdeckToggle");
function initSteamDeckTab() {
  if (!steamdeckToggle) return;
  const s = currentSettings || {};
  steamdeckToggle.checked = !!s.steamdeck_mode;
}
function restoreDesktopHome() {
  document.body.classList.remove("ps5-ready");
  const dock = document.getElementById("ps5Dock");
  if (dock) dock.remove();
  const hint = document.getElementById("ps5Hint");
  if (hint) hint.remove();
  const panel = $("homePanel");
  if (panel) {
    panel.innerHTML = '<h2>Home</h2><ul id="instanceList"></ul>';
  }
}

async function applyConsoleMode() {
  const s = await loadSettingsOnce();
  const on = !!s.steamdeck_mode;
  document.body.classList.toggle("console-mode", on);
  setupConsoleNavigation(on);
  // Controller-Modus im Begleit-Mod sofort (ohne Neustart) aktivieren.
  try { await invoke("set_console_mode", { on }); } catch (e) {}
  if (on) {
    renderConsoleHome();
  } else {
    restoreDesktopHome();
    refreshInstances();
  }
}
if (steamdeckToggle) {
  steamdeckToggle.onchange = async () => {
    const s = await loadSettingsOnce();
    s.steamdeck_mode = steamdeckToggle.checked;
    try { await invoke("save_settings", { settings: s }); } catch (e) {}
    await applyConsoleMode();
  };
}
// Rendert das Konsolen-Startmenü (PS5-artig, dunkel/schlank): ein großes
// horizontales Hero oben, darunter horizontale Reihen runder Kacheln und eine
// eigene Icon-Dock-Leiste unten. Die Theme-Farben werden weiterhin genutzt.
function ensureConsoleDock() {
  if (document.getElementById("ps5Dock")) return;
  const dock = document.createElement("div");
  dock.id = "ps5Dock";
  dock.className = "ps5-dock";
  const mk = (tab, ico, label) => {
    const b = document.createElement("button");
    b.className = "ps5-dock-btn";
    b.dataset.tab = tab;
    const i = document.createElement("span");
    i.className = "ps5-dock-ico";
    i.textContent = ico;
    b.append(i);
    if (label) {
      const l = document.createElement("span");
      l.className = "ps5-dock-label";
      l.textContent = label;
      b.append(l);
    }
    b.onclick = (e) => {
      e.preventDefault();
      if (tab === "settings") {
        $("settingsBtn").click();
      } else {
        switchTab(tab);
      }
      if (consoleNavActive) refreshConsoleFocusables();
    };
    return b;
  };
  dock.append(
    mk("home", "▦", "Home"),
    mk("create", "＋", "Erstellen"),
    mk("socials", "⌂", "Sozial"),
    mk("logs", "☰", "Logs"),
    mk("settings", "⚙", "Einst.")
  );
  document.body.appendChild(dock);
  const hint = document.createElement("div");
  hint.id = "ps5Hint";
  hint.className = "ps5-hint";
  hint.textContent = "A Starten · X Verwalten · Y Erstellen · B Zurück · Start Einstellungen · Select Zurück";
  document.body.appendChild(hint);
  setTimeout(() => document.body.classList.add("ps5-ready"), 30);
}

function renderConsoleHome() {
  ensureConsoleDock();
  const panel = $("homePanel");
  if (!panel) return;
  panel.innerHTML = "";

  // Kopfzeile mit Logo (Switch-Hauptmenü-Stil: Marke links oben)
  const topbar = document.createElement("div");
  topbar.className = "ps5-topbar";
  const logo = document.createElement("img");
  logo.className = "ps5-logo";
  logo.src = "Kollegen.png";
  logo.alt = "Kollegen Client";
  const brand = document.createElement("div");
  brand.className = "ps5-brand";
  brand.textContent = "Kollegen Client";
  topbar.append(logo, brand);
  panel.append(topbar);

  // Horizontale Reihe grosser Hero-Karten (eine pro Instanz), wie Spiele-Tiles
  // im Switch-Hauptmenü. Links/Rechts (D-Pad/Stick/WASD) wählt die Karte.
  const instances = instancesCache || [];
  const heroRow = document.createElement("div");
  heroRow.className = "ps5-hero-row";
  if (instances.length === 0) {
    const empty = document.createElement("div");
    empty.className = "ps5-empty";
    empty.textContent = "Keine Instanzen. Drücke Y, um eine neue zu erstellen.";
    heroRow.append(empty);
  } else {
    instances.forEach((inst, idx) => {
      const card = document.createElement("div");
      card.className = "ps5-hcard" + (idx === 0 ? " ps5-hcard-first" : "");
      card.dataset.inst = inst.name;
      card.tabIndex = 0;
      card.setAttribute("role", "button");
      const cicon = document.createElement("div");
      cicon.className = "ps5-hcard-icon";
      cicon.textContent = (inst.name || "?").charAt(0).toUpperCase();
      const cbody = document.createElement("div");
      cbody.className = "ps5-hcard-body";
      const ctitle = document.createElement("div");
      ctitle.className = "ps5-hcard-title";
      ctitle.textContent = inst.name;
      const cmeta = document.createElement("div");
      cmeta.className = "ps5-hcard-meta";
      cmeta.textContent = `${inst.version} · ${inst.loader}`;
      const chint = document.createElement("div");
      chint.className = "ps5-hcard-hint";
      chint.textContent = "A Starten · X Verwalten";
      cbody.append(ctitle, cmeta, chint);
      card.append(cicon, cbody);
      heroRow.append(card);
    });
  }
  panel.append(heroRow);

  const dock = document.getElementById("ps5Dock");
  if (dock) {
    dock.querySelectorAll(".ps5-dock-btn").forEach((b) => {
      b.classList.toggle("active", b.dataset.tab === "home");
    });
  }
}
// ─ Konsolen-/Controller-Navigation ─
// Im Konsolen-Modus kannst du per Pfeiltasten, WASD und Gamepad (D-Pad/Stick +
// A) durch den Launcher navigieren. "A" aktiviert, "B"/Esc schließt Modals.
let consoleFocusables = [];
let consoleFocusIndex = -1;
let consoleNavActive = false;

// Backend-Gamepad (gilrs) → Konsolenaktion. WebKitGTK kann auf Linux die
// JS-Gamepad-API praktisch nicht, deshalb liest der Rust-Thread den Controller
// und sendet `console-input`-Events. Diese Funktion mappt sie auf die gleichen
// Aktionen wie die Tastatur (siehe consoleNavKey / Gamepad-Polling weiter unten).
function consoleInputAction(action) {
  if (!consoleNavActive) return;
  // v1.10.9: kein 280ms-Richtungs-Cooldown mehr – das Backend (gilrs) liefert
  // pro Drücker genau ein Event und regelt die Wiederholung beim Halten selbst.
  // Der alte Cooldown hier hat schnell aufeinanderfolgende D-Pad-Drucke
  // verschluckt (man musste bei jedem zweiten Schritt doppelt drücken).
  switch (action) {
    case "A": consoleDoStart(); break;
    case "X": consoleDoManage(); break;
    case "Y": try { switchTab("create"); } catch (e) {} break;
    case "B":
    case "SELECT": consoleCloseModal(); break;
    case "START": consoleOpenSettings(); break;
    case "UP": moveConsoleFocus(0, -1); break;
    case "DOWN": moveConsoleFocus(0, 1); break;
    case "LEFT": moveConsoleFocus(-1, 0); break;
    case "RIGHT": moveConsoleFocus(1, 0); break;
  }
}
if (window.__TAURI__?.event?.listen && !window.__consoleNavTauriSub) {
  window.__consoleNavTauriSub = true;
  window.__TAURI__.event.listen("console-input", (e) => {
    consoleInputAction(String(e?.payload || ""));
  });
}


function computeConsoleFocusables() {
  const list = [];
  // Sichtbares Modal zuerst (sonst Sidebar/Panel)
  const modals = Array.from(document.querySelectorAll(".modal"));
  const openModal = modals.find((m) => m.style.display && m.style.display !== "none");
  const root = openModal || document.body;
  const sel = "a.sidebar-link, button, select, input[type=checkbox], input[type=text], " +
    "input:not([type]), textarea, [tabindex], label.settings-check";
  root.querySelectorAll(sel).forEach((el) => {
    const r = el.getBoundingClientRect();
    if (r.width === 0 && r.height === 0) return;
    const cs = getComputedStyle(el);
    if (cs.visibility === "hidden" || cs.display === "none") return;
    if (el.disabled) return;
    // Checkboxen in "Settings-Check"-Zeilen überspringen: das umgebende Label
    // ist der Fokuspunkt (sonst doppelte Einträge → nervige Navigation).
    if (el.type === "checkbox" && el.closest && el.closest("label.settings-check")) return;
    if (!openModal && !isVisibleAtAll(el)) return;
    list.push(el);
  });
  return list;
}

function isVisibleAtAll(el) {
  let n = el;
  while (n && n !== document.body) {
    const stale = n.style && n.style.display;
    if (stale === "none") return false;
    n = n.parentElement;
  }
  return true;
}

function refreshConsoleFocusables() {
  consoleFocusables = computeConsoleFocusables();
  consoleFocusables.forEach((el, i) => el.classList.toggle("cfocus", i === consoleFocusIndex));
}

// Fokus nach Tab-/Modal-Wechsel auf das erste "echte" Bedienelement setzen
// (überspringt Kopf-Schließ-Buttons .icon-btn, damit nicht der ✕ am Anfang landet).
function setConsoleFocusFirstNonIcon() {
  refreshConsoleFocusables();
  let i = consoleFocusables.findIndex((el) => !el.classList.contains("icon-btn"));
  if (i < 0) i = 0;
  setConsoleFocus(i);
}

function setConsoleFocus(index) {
  if (!consoleFocusables.length) return;
  if (index < 0) index = consoleFocusables.length - 1;
  if (index >= consoleFocusables.length) index = 0;
  consoleFocusIndex = index;
  consoleFocusables.forEach((el, i) => el.classList.toggle("cfocus", i === index));
  const el = consoleFocusables[index];
  try { el.scrollIntoView({ block: "nearest" }); } catch (e) {}
  const r = el.getBoundingClientRect();
  let cx = r.left + r.width / 2;
  let cy = r.top + r.height / 2;
  if (typeof window.__consoleNavCursor === "function") window.__consoleNavCursor(cx, cy);
}

function moveConsoleFocus(dx, dy) {
  if (!consoleFocusables.length) return;
  // Im Konsolenmodus ändert ←/→ direkt die Auswahl eines Dropdowns; ↑/↓ bleibt
  // Fokus-Navigation.
  if (dx !== 0 && changeConsoleSelect(dx)) return;
  const cur = consoleFocusables[Math.max(0, consoleFocusIndex)];
  const c = cur ? cur.getBoundingClientRect() : { left: 0, top: 0, width: 0, height: 0 };
  const ccx = c.left + c.width / 2;
  const ccy = c.top + c.height / 2;
  let best = null;
  let bestScore = Infinity;
  const allowed = (dx !== 0 ? dx > 0 ? 1 : -1 : 0);
  const allowedY = (dy !== 0 ? dy > 0 ? 1 : -1 : 0);
  consoleFocusables.forEach((el, i) => {
    if (i === consoleFocusIndex) return;
    const r = el.getBoundingClientRect();
    const ex = r.left + r.width / 2;
    const ey = r.top + r.height / 2;
    let relX = ex - ccx;
    let relY = ey - ccy;
    if (dx !== 0 && Math.sign(relX) !== allowed) return;
    if (dy !== 0 && Math.sign(relY) !== allowedY) return;
    const dist = Math.hypot(relX, relY);
    // Bevorzuge gleichgerichtete Bewegungen stark
    const penal = (dx !== 0 ? Math.abs(relY) * 1.6 : 0) + (dy !== 0 ? Math.abs(relX) * 1.6 : 0);
    const score = dist + penal;
    if (score < bestScore) { bestScore = score; best = i; }
  });
  if (best != null) setConsoleFocus(best);
}

function activateConsoleFocus() {
  const el = consoleFocusables[consoleFocusIndex];
  if (!el) return;
  if (el.type === "checkbox") {
    el.checked = !el.checked;
    el.dispatchEvent(new Event("change"));
  } else if (el.tagName === "SELECT") {
    openConsolePicker(el);
  } else if (el.tagName === "A" || el.tagName === "BUTTON" || el.tagName === "LABEL") {
    // LABEL: die Settings-Checkboxen sind <label class=settings-check> mit
    // verstecktem <input> – ein Klick auf das Label schaltet sie um.
    el.click();
  } else if (el.tagName === "INPUT" || el.tagName === "TEXTAREA") {
    el.focus();
  }
}

function consoleCloseModal() {
  if (closeConsolePicker()) return true;
  const modals = Array.from(document.querySelectorAll(".modal"));
  const open = modals.find((m) => m.style.display && m.style.display !== "none");
  if (!open) return false;
  open.style.display = "none";
  refreshConsoleFocusables();
  return true;
}

// ── Gamepad-/Tastatur-Shortcuts (statt nur Auswahl über Buttons) ──
// Standard-Gamepad-Mapping (Web Gamepad API): 0=A 1=B 2=X 3=Y 8=Select 9=Start
// A = Aktivieren, B = Instanz starten, Y = Instanz verwalten, X = Menü-Aktion,
// Start = Einstellungen, Select = Zurück/Modal schließen.
function consoleFocusedEl() {
  return consoleFocusables[consoleFocusIndex] || null;
}

function consoleFocusedInstance() {
  const el = consoleFocusedEl();
  if (!el) return null;
  let n = el.closest && el.closest("[data-inst]");
  if (!n && el.parentElement) n = el.parentElement.closest && el.parentElement.closest("[data-inst]");
  if (!n || !(instancesCache)) return null;
  const name = n.dataset.inst;
  return instancesCache.find((i) => i.name === name) || null;
}

let consolePickerTarget = null;

// Durchblättern eines fokussierten <select> per ←/→: Das native WebKitGTK-Dropdown
// ist im Konsolenmodus nicht mit dem D-Pad bedienbar, deshalb ändern wir die
// Auswahl direkt und feuern `change` (kaskadiert z. B. Loader-Versionen).
function changeConsoleSelect(step) {
  const el = consoleFocusedEl();
  if (!el || el.tagName !== "SELECT") return false;
  const opts = Array.from(el.options).filter((o) => !o.disabled);
  if (!opts.length) return true;
  const sel = el.selectedOptions[0];
  let idx = opts.indexOf(sel);
  if (idx < 0) idx = 0;
  idx = (idx + step + opts.length) % opts.length;
  try { el.value = opts[idx].value; el.selectedIndex = opts[idx].index; } catch (e) { return true; }
  el.dispatchEvent(new Event("change", { bubbles: true }));
  return true;
}

// Auswahl-Popup für Dropdowns: A öffnet eine echte Liste; D-Pad scrollt, A wählt.
function openConsolePicker(select) {
  const modal = $("pickModal");
  const list = $("pickList");
  if (!modal || !list) { try { select.click(); } catch (e) {} return; }
  const opts = Array.from(select.options).filter((o) => !o.disabled);
  if (!opts.length) return;
  consolePickerTarget = select;
  $("pickTitle").textContent = select.getAttribute("aria-label") || select.title || "Auswahl";
  list.innerHTML = "";
  let startIdx = 0;
  opts.forEach((o, i) => {
    if (o.selected) startIdx = i;
    const b = document.createElement("button");
    b.type = "button";
    b.className = "picker-option" + (o.selected ? " picked" : "");
    b.textContent = o.textContent.trim();
    b.onclick = () => pickConsoleOption(select, o);
    list.append(b);
  });
  modal.style.display = "flex";
  if (consoleNavActive) {
    refreshConsoleFocusables();
    // Der ✕-Header-Button steht in der Fokusliste vor den Optionen – daher den
    // Listen-Versatz ermitteln, statt stumpf Index = Option zu setzen.
    const optsStart = consoleFocusables.findIndex((el) => el.classList.contains("picker-option"));
    const i = optsStart >= 0 ? optsStart + startIdx : startIdx;
    setConsoleFocus(Math.min(consoleFocusables.length - 1, i));
  }
}

function pickConsoleOption(select, option) {
  try { select.value = option.value; select.selectedIndex = option.index; } catch (e) {}
  select.dispatchEvent(new Event("change", { bubbles: true }));
  closeConsolePicker();
}

function closeConsolePicker() {
  const modal = $("pickModal");
  if (!modal || modal.style.display === "none") return false;
  modal.style.display = "none";
  if (consoleNavActive) {
    refreshConsoleFocusables();
    const i = consoleFocusables.indexOf(consolePickerTarget);
    if (i >= 0) setConsoleFocus(i);
  }
  consolePickerTarget = null;
  return true;
}

function consoleDoStart() {
  // Zuerst das fokussierte, auslösbare Element aktivieren – v. a. wenn ein Modal
  // offen ist: "A" muss Buttons/Checkboxen darin auslösen, nicht das Modal schließen.
  const el = consoleFocusedEl();
  if (el) {
    const tag = el.tagName;
    if (el.type === "checkbox") { el.checked = !el.checked; el.dispatchEvent(new Event("change")); return; }
    if (tag === "SELECT") { openConsolePicker(el); return; }
    if (tag === "BUTTON" || tag === "A" || tag === "LABEL") { el.click(); return; }
  }
  const inst = consoleFocusedInstance();
  if (inst) { launchGame(inst.name); return; }
  // Nichts Sinnvolles fokussiert → offenes Modal schließen ("A" als Abbrechen).
  if (consoleCloseModal()) return;
}

function consoleDoManage() {
  const inst = consoleFocusedInstance();
  if (inst) { openManage(inst); return; }
}

function consoleOpenSettings() {
  closeConsolePicker();
  const s = $("settingsBtn");
  if (s) s.click();
  if (consoleNavActive) refreshConsoleFocusables();
}


function consoleNavKey(e) {
  const k = e.key;
  const arrows = k === "ArrowUp" || k === "ArrowDown" || k === "ArrowLeft" || k === "ArrowRight";
  const wasd = k === "w" || k === "s" || k === "a" || k === "d";
  if (arrows || wasd) {
    const tag = (e.target && e.target.tagName) || "";
    const isField = /INPUT|TEXTAREA/.test(tag);
    // In Textfeldern typst du normal: WASD soll nicht kapern; nur mit den
    // Pfeiltasten wechselst du zum nächsten Feld.
    if (isField && wasd) return;
    e.preventDefault();
    const dx = (k === "ArrowRight" || k === "d") ? 1 : (k === "ArrowLeft" || k === "a") ? -1 : 0;
    const dy = (k === "ArrowDown" || k === "s") ? 1 : (k === "ArrowUp" || k === "w") ? -1 : 0;
    moveConsoleFocus(dx, dy);
    return;
  }
  if (k === "Enter") { e.preventDefault(); consoleDoStart(); return; }
  if (k === "Escape") { e.preventDefault(); consoleCloseModal(); return; }
  const tag = (e.target && e.target.tagName) || "";
  const isField = /INPUT|TEXTAREA/.test(tag);
  if (!isField) {
    if (k === "x" || k === "X") { e.preventDefault(); consoleDoManage(); return; }
    if (k === "y" || k === "Y") { e.preventDefault(); try { switchTab("create"); } catch (err) {} return; }
  }
}

function setupConsoleNavigation(on) {
  consoleNavActive = on;
  document.body.classList.toggle("console-nav", on);
  if (!on) {
    consoleFocusables.forEach((el) => el.classList.remove("cfocus", "console-nav"));
    document.onkeydown = null;
    if (window.__consoleNavLoop) { clearInterval(window.__consoleNavLoop); window.__consoleNavLoop = null; }
    return;
  }
  // Tastatur
  document.onkeydown = consoleNavKey;
  // Ersten Fokus setzen
  refreshConsoleFocusables();
  setConsoleFocus(0);
  // Gamepad-Polling (D-Pad/Stick + A). Seit v1.10.6 ist das Backend (gilrs) die
  // zuverlässige, alleinige Eingabequelle — WebKitGTK kann die Web-Gamepad-API
  // auf Linux praktisch nicht, und ein aktives Web-Polling würde doppelt
  // navigieren (→ mehrere Tabs bei kurzer Bewegung). Daher hier nur ein Pflicht-
  // leerer Guard: die eigentliche Steuerung kommt als `console-input`-Events aus
  // dem Rust-Gamepad-Thread (siehe consoleInputAction).
  window.__consoleNavLoop = setInterval(() => {
    if (window.__TAURI__) return; // Backend übernimmt die Steuerung
    const gp = navigator.getGamepads && navigator.getGamepads();
    if (!gp) return;
    const pad = Array.from(gp).find((p) => p && p.connected);
    if (!pad) return;
    const ax = pad.axes ? pad.axes[0] || 0 : 0;
    const ay = pad.axes ? pad.axes[1] || 0 : 0;
    const dpad = (pad.buttons && pad.buttons.length) ? [
      pad.buttons[12]?.value, // up
      pad.buttons[13]?.value, // down
      pad.buttons[14]?.value, // left
      pad.buttons[15]?.value  // right
    ] : null;
    const repeat = (window.__consoleNavLastDir === (JSON.stringify([ax, ay, dpad]))) ? 1 : 0;
    const now = Date.now() - (window.__consoleNavLastT || 0);
    if (now < (repeat ? 320 : 0)) return;
    const up = (dpad && dpad[0] > 0.5) || ay < -0.5;
    const down = (dpad && dpad[1] > 0.5) || ay > 0.5;
    const left = (dpad && dpad[2] > 0.5) || ax < -0.5;
    const right = (dpad && dpad[3] > 0.5) || ax > 0.5;
    let dx = right ? 1 : left ? -1 : 0;
    let dy = down ? 1 : up ? -1 : 0;
    if (dx || dy) {
      moveConsoleFocus(dx, dy);
      window.__consoleNavLastT = Date.now();
      window.__consoleNavLastDir = JSON.stringify([ax, ay, dpad]);
      return;
    }
    // Edge-Trigger für Aktionen (A/B/X/Y/Start/Select) mit Entprellung.
    const btns = pad.buttons || [];
    if (!window.__consoleNavBtns) window.__consoleNavBtns = {};
    const map = { 0: "A", 1: "B", 2: "X", 3: "Y", 8: "SELECT", 9: "START" };
    for (const idx in map) {
      const down = btns[idx] ? btns[idx].value > 0.5 : false;
      const was = !!window.__consoleNavBtns[map[idx]];
      if (down && !was) {
        window.__consoleNavBtns[map[idx]] = true;
        if (map[idx] === "A") { consoleDoStart(); return; }
        if (map[idx] === "X") { consoleDoManage(); return; }
        if (map[idx] === "Y") { try { switchTab("create"); } catch (e) {} return; }
        if (map[idx] === "B") { consoleCloseModal(); return; }
        if (map[idx] === "SELECT") { consoleCloseModal(); return; }
        if (map[idx] === "START") { consoleOpenSettings(); return; }
      }
      if (!down && was) window.__consoleNavBtns[map[idx]] = false;
    }
  }, 80);
  // Fokusliste nach UI-Änderungen aktualisieren
  if (!window.__consoleNavRescan) {
    window.__consoleNavRescan = setInterval(() => {
      if (document.body.classList.contains("console-mode")) refreshConsoleFocusables();
    }, 500);
  }
}

$("pickClose").onclick = () => closeConsolePicker();

$("settingsClose").onclick = () => {
  settingsModalEl.style.display = "none";
  if (consoleNavActive) refreshConsoleFocusables();
};

document.querySelectorAll(".settings-tab").forEach((tab) => {
  tab.onclick = () => {
    showSettingsTab(tab.dataset.tab);
    if (consoleNavActive) setConsoleFocusFirstNonIcon();
  };
});

// ─ Updates (manual check + install) ─
let pendingCanInstall = false;
let pendingInstallFormat = "deb-rpm";
const UPDATE_RELEASE_URL = "https://github.com/FluffyBento/KollegenClient/releases/latest";
$("updateCheckBtn").onclick = async () => {
  const status = $("updateStatus");
  const installBtn = $("updateInstallBtn");
  status.textContent = "Prüfe auf Updates…";
  installBtn.style.display = "none";
  try {
    const update = await invoke("check_app_update");
    if (update) {
      pendingCanInstall = !!update.can_install;
      pendingInstallFormat = update.format || "deb-rpm";
      if (pendingCanInstall) {
        status.textContent = "Update verfügbar: Version " + update.version + (update.notes ? "\n" + update.notes : "");
        installBtn.textContent = "Update installieren";
      } else if (pendingInstallFormat === "flatpak") {
        status.textContent = "Update verfügbar: Version " + update.version + "\n\nDiese Installation ist ein Flatpak. Zum Aktualisieren bitte die neue Version herunterladen und installieren:\n\n   flatpak install --user ./dev.kollegen.Client.flatpak\n\n(falls ein Fehler 'bereits installiert' erscheint: zuerst 'flatpak uninstall dev.kollegen.client'.)" + (update.notes ? "\n\n" + update.notes : "");
        installBtn.textContent = "Flatpak-Download öffnen";
      } else {
        status.textContent = "Update verfügbar: Version " + update.version + " – kann aus diesem Installationsformat (.deb/.rpm) nicht direkt installiert werden. Bitte manuell von GitHub laden." + (update.notes ? "\n" + update.notes : "");
        installBtn.textContent = "Download auf GitHub öffnen";
      }
      installBtn.style.display = "";
    } else {
      status.textContent = "Du verwendest bereits die aktuellste Version.";
    }
  } catch (e) {
    status.textContent = "Update-Prüfung fehlgeschlagen: " + e;
  }
};
$("updateInstallBtn").onclick = async () => {
  const status = $("updateStatus");
  if (pendingCanInstall) {
    status.textContent = "Update wird heruntergeladen und installiert…";
    try {
      await invoke("install_app_update");
      // App restarts itself after install; this line is only reached on failure.
    } catch (e) {
      status.textContent = "Installation fehlgeschlagen: " + e;
    }
  } else {
    await invoke("open_url", { url: UPDATE_RELEASE_URL });
  }
};

// ─ Verbindungen (Microsoft / Discord) ─
async function refreshSettingsAccounts() {
  const list = $("msAccountList");
  list.innerHTML = "";
  let accounts = [];
  try {
    accounts = await invoke("get_accounts");
  } catch (e) {}
  if (accounts.length === 0) {
    list.innerHTML = "<li style='color:#888;'>Keine Microsoft-Accounts verbunden.</li>";
    return;
  }
  accounts.forEach((acc, i) => {
    const li = document.createElement("li");
    const info = document.createElement("div");
    info.className = "acc-info";
    const nm = document.createElement("span");
    nm.className = "acc-name";
    nm.textContent = acc.username || acc.uuid;
    info.append(nm);
    if (i === 0) {
      const badge = document.createElement("span");
      badge.className = "acc-badge";
      badge.textContent = "Aktiv";
      info.append(badge);
    }
    const actions = document.createElement("div");
    actions.className = "acc-actions";
    if (i !== 0) {
      const sw = document.createElement("button");
      sw.textContent = "Wechseln";
      sw.onclick = async () => {
        try {
          await invoke("ms_switch_account", { uuid: acc.uuid });
          await refreshSettingsAccounts();
          refreshAuth();
        } catch (e) {
          alert("Wechseln fehlgeschlagen: " + e);
        }
      };
      actions.append(sw);
    }
    const rm = document.createElement("button");
    rm.textContent = "Entfernen";
    rm.onclick = async () => {
      if (!confirm(`Account '${acc.username || acc.uuid}' wirklich entfernen?`)) return;
      try {
        await invoke("ms_remove_account", { uuid: acc.uuid });
        await refreshSettingsAccounts();
        refreshAuth();
      } catch (e) {
        alert("Entfernen fehlgeschlagen: " + e);
      }
    };
    actions.append(rm);
    li.append(info, actions);
    list.append(li);
  });
}

$("msAddBtn").onclick = async () => {
  try {
    const res = await invoke("auth_start");
    if (res.user_code && res.verification_uri) {
      const loginUrl = `${res.verification_uri}?otc=${res.user_code}`;
      if (consoleNavActive) {
        await showLoginQr(loginUrl);
        refreshConsoleFocusables();
      } else {
        try { window.open(loginUrl, "_blank"); } catch (e) {}
        copyText(loginUrl);
        await showLoginQr(loginUrl);
        $("loginInfo").style.display = "flex";
        alert("Microsoft Login:\n\nBrowser wurde geöffnet. Du kannst den QR-Code auch mit dem Handy scannen.");
      }
    }
    refreshAuth();
    await refreshSettingsAccounts();
  } catch (e) {
    alert("Login fehlgeschlagen: " + e);
  }
};

$("discordLogoutBtn").onclick = async () => {
  try {
    await invoke("discord_oauth_logout");
    refreshDiscord();
    refreshDiscordLogin();
  } catch (e) {
    alert("Discord Abmeldung fehlgeschlagen: " + e);
  }
};

// ─ Themes (reine Farbschemata) ─
function renderThemeList() {
  const list = $("themeList");
  list.innerHTML = "";
  const s = currentSettings || { theme: "Kollegen" };
  for (const [name, pal] of Object.entries(THEMES)) {
    const card = document.createElement("button");
    card.className = "theme-card" + (name === s.theme ? " active" : "");
    const head = document.createElement("img");
    head.className = "theme-head";
    head.src = pal.head || "";
    head.alt = "";
    const label = document.createElement("span");
    label.textContent = name;
    card.append(head, label);
    card.onclick = () => saveTheme(name);
    list.append(card);
  }
}

// ─ Layout-/Darstellungsoptionen ─
function applyLayout() {
  const s = currentSettings || { density: "comfortable", sidebar_visible: true, animations: true };
  document.body.classList.toggle("density-compact", s.density === "compact");
  document.body.classList.toggle("hide-sidebar", !s.sidebar_visible);
  document.body.classList.toggle("reduce-motion", !s.animations);
}

async function saveLayout(patch) {
  const s = await loadSettingsOnce();
  Object.assign(s, patch);
  try { await invoke("save_settings", { settings: s }); } catch (e) {}
  applyLayout();
}

function segButton(label, active, onClick) {
  const b = document.createElement("button");
  b.className = "mode-btn" + (active ? " active" : "");
  b.textContent = label;
  b.onclick = onClick;
  return b;
}

function renderLayoutOptions() {
  const wrap = $("layoutOptions");
  if (!wrap) return;
  wrap.innerHTML = "";
  const s = currentSettings || { density: "comfortable", sidebar_visible: true, animations: true };

  const density = document.createElement("div");
  density.className = "layout-row";
  density.innerHTML = "<span class='layout-label'>Dichte</span>";
  const densSeg = document.createElement("div");
  densSeg.className = "mode-toggle";
  densSeg.append(
    segButton("Komfortabel", s.density !== "compact", () => { saveLayout({ density: "comfortable" }); renderLayoutOptions(); }),
    segButton("Kompakt", s.density === "compact", () => { saveLayout({ density: "compact" }); renderLayoutOptions(); })
  );
  density.append(densSeg);

  const sidebar = document.createElement("div");
  sidebar.className = "layout-row";
  sidebar.innerHTML = "<span class='layout-label'>Sidebar</span>";
  const sideSeg = document.createElement("div");
  sideSeg.className = "mode-toggle";
  sideSeg.append(
    segButton("Anzeigen", s.sidebar_visible, () => { saveLayout({ sidebar_visible: true }); renderLayoutOptions(); }),
    segButton("Ausblenden", !s.sidebar_visible, () => { saveLayout({ sidebar_visible: false }); renderLayoutOptions(); })
  );
  sidebar.append(sideSeg);

  const anim = document.createElement("div");
  anim.className = "layout-row";
  anim.innerHTML = "<span class='layout-label'>Animationen</span>";
  const animSeg = document.createElement("div");
  animSeg.className = "mode-toggle";
  animSeg.append(
    segButton("An", s.animations, () => { saveLayout({ animations: true }); renderLayoutOptions(); }),
    segButton("Aus", !s.animations, () => { saveLayout({ animations: false }); renderLayoutOptions(); })
  );
  anim.append(animSeg);

  wrap.append(density, sidebar, anim);
}

// ─ Importieren (andere Launcher) ─
$("importScanBtn").onclick = async () => {
  const list = $("importLauncherList");
  list.innerHTML = "";
  $("importInstanceList").innerHTML = "";
  $("importStatus").textContent = "Suche nach installierten Launchern...";
  try {
    const launchers = await invoke("detect_launchers");
    $("importStatus").textContent = "";
    if (!launchers.length) {
      $("importStatus").textContent = "Keine anderen Launcher gefunden.";
      return;
    }
    for (const l of launchers) {
      const li = document.createElement("li");
      const info = document.createElement("div");
      const nm = document.createElement("span");
      nm.className = "imp-name";
      nm.textContent = l.name;
      const meta = document.createElement("span");
      meta.className = "imp-meta";
      meta.textContent = l.path;
      info.append(nm, document.createElement("br"), meta);
      const btn = document.createElement("button");
      btn.textContent = "Instanzen anzeigen";
      btn.onclick = () => showLauncherInstances(l.id);
      li.append(info, btn);
      list.append(li);
    }
  } catch (e) {
    $("importStatus").textContent = "Fehler: " + e;
  }
};

$("importPackBtn").onclick = async () => {
  const status = $("importStatus");
  status.textContent = "Wähle eine .mrpack- oder .zip-Datei…";
  const open = window.__TAURI__.dialog?.open || window.__TAURI__.pluginDialog?.open;
  if (!open) {
    status.textContent = "Datei-Dialog nicht verfügbar.";
    return;
  }
  let path;
  try {
    path = await open({
      title: "Modpack importieren",
      multiple: false,
      filters: [{ name: "Modpack", extensions: ["mrpack", "zip"] }],
    });
  } catch (e) {
    status.textContent = "Fehler: " + e;
    return;
  }
  if (!path) {
    status.textContent = "";
    return;
  }
  status.textContent = "Importiere Modpack…";
  try {
    const inst = await invoke("import_pack", { path });
    status.textContent = `Modpack '${inst.name}' importiert.`;
    refreshInstances();
  } catch (e) {
    status.textContent = "Fehler: " + e;
  }
};

async function showLauncherInstances(launcherId) {
  const list = $("importInstanceList");
  list.innerHTML = "";
  $("importStatus").textContent = "Lade Instanzen...";
  try {
    const instances = await invoke("list_launcher_instances", { launcherId });
    $("importStatus").textContent = "";
    if (!instances.length) {
      $("importStatus").textContent = "Keine Instanzen in diesem Launcher gefunden.";
      return;
    }
    for (const inst of instances) {
      const li = document.createElement("li");
      const info = document.createElement("div");
      const nm = document.createElement("span");
      nm.className = "imp-name";
      nm.textContent = inst.name;
      const meta = document.createElement("span");
      meta.className = "imp-meta";
      meta.textContent = `${inst.version || "?"} (${inst.loader})`;
      info.append(nm, document.createElement("br"), meta);
      const btn = document.createElement("button");
      btn.textContent = "Importieren";
      btn.onclick = () => doImport(launcherId, inst.dir_name, inst.name);
      li.append(info, btn);
      list.append(li);
    }
  } catch (e) {
    $("importStatus").textContent = "Fehler: " + e;
  }
}

async function doImport(launcherId, dirName, dispName) {
  if (!confirm(`Instanz '${dispName}' importieren?`)) return;
  $("importStatus").textContent = `Importiere '${dispName}'...`;
  try {
    const inst = await invoke("import_instance", { launcherId, instanceName: dirName });
    $("importStatus").textContent = `Importiert: ${inst.name}`;
    refreshInstances();
  } catch (e) {
    $("importStatus").textContent = "Import fehlgeschlagen: " + e;
  }
}

// Beim Start das gespeicherte Theme + Layout anwenden. Danach den SteamDeck-/
// Konsolen-Modus anwenden (aktiviert die Konsolen-UI und den Controller-Modus).
applySavedTheme()
  .then(applyLayout)
  .then(applyConsoleMode);

  // Tab-System (Sidebar: Home / Erstellen / Soziales).
  function switchTab(name) {
    document.querySelectorAll(".sidebar-link[data-tab]").forEach((l) => {
      l.classList.toggle("active", l.dataset.tab === name);
    });
    document.querySelectorAll(".tab-panel[data-tab]").forEach((p) => {
      p.classList.toggle("active", p.dataset.tab === name);
    });
    if (document.body.classList.contains("console-mode")) {
      const dock = document.getElementById("ps5Dock");
      if (dock) {
        dock.querySelectorAll(".ps5-dock-btn").forEach((b) => {
          b.classList.toggle("active", b.dataset.tab === name);
        });
      }
    }
    if (name === "socials") {
      renderSocialAll();
      refreshSocial();
    }
    if (name === "home" && document.body.classList.contains("console-mode")) {
      renderConsoleHome();
    }
    if (consoleNavActive) setConsoleFocusFirstNonIcon();
  }
  document.querySelectorAll(".sidebar-link[data-tab]").forEach((l) => {
    l.addEventListener("click", (e) => {
      e.preventDefault();
      switchTab(l.dataset.tab);
    });
  });
$("profileWidget").onclick = () => openProfileModal();
const openProfileBtn = $("openProfileBtn");
if (openProfileBtn) openProfileBtn.onclick = () => openProfileModal();
$("profileClose").onclick = () => { $("profileModal").style.display = "none"; };
$("pmCopy").onclick = () => { if (socialMe) copyText(socialMe.friend_code); };

// Skin/Cape-Changer
const skinFileInput = $("skinFileInput");
if ($("skinDownloadBtn")) {
  $("skinDownloadBtn").onclick = () => {
    invoke("skin_download_current").then(r => {
      if (r && r.state) {
        renderSkinLibrary(r.state);
        const a = (r.state.skins || []).find(s => s.name === r.state.active);
        if (a) showSkin(a.url);
        toast("Skin heruntergeladen", "ok");
      } else {
        toast((r && r.error) || "Download fehlgeschlagen", "error");
      }
    }).catch(e => toast("Download fehlgeschlagen: " + e, "error"));
  };
}
if ($("skinUploadBtn") && skinFileInput) {
  $("skinUploadBtn").onclick = () => skinFileInput.click();
  skinFileInput.onchange = (e) => {
    const file = e.target.files && e.target.files[0];
    if (!file) return;
    const reader = new FileReader();
    reader.onload = () => {
      const dataUrl = reader.result;
      const name = (file.name || "upload").replace(/\.[^.]+$/, "") || "upload";
      invoke("skin_upload", { name, data: dataUrl, variant: "classic" }).then(r => {
        if (r && r.state) {
          renderSkinLibrary(r.state);
          const a = (r.state.skins || []).find(s => s.name === r.state.active);
          if (a) showSkin(a.url);
        }
        if (r && r.error) toast(r.error, "error");
        else toast(r && r.mc_uploaded ? "Skin hochgeladen & gewechselt" : "Skin lokal gespeichert", "ok");
      }).catch(err => toast("Upload fehlgeschlagen: " + err, "error"));
    };
    reader.readAsDataURL(file);
    e.target.value = "";
  };
}
$("friendCodeAdd").onclick = async () => {
  const code = ($("friendCodeInput").value || "").trim().toUpperCase();
  if (!code) return;
  try {
    const res = await invoke("kollegen_friend_add", { targetId: code });
    $("friendCodeInput").value = "";
    if (res && res.error) toast("Fehler: " + res.error, "error");
    else if (res && res.pending) toast("Anfrage gesendet – wartet auf Bestätigung", "ok");
    else if (res && res.accepted) toast("Ihr seid jetzt Freunde!", "ok");
    else if (res && res.already) toast("Bereits Freunde", "ok");
    else toast("Freund hinzugefügt", "ok");
    refreshSocial(true);
  } catch (e) {
    toast("Freund konnte nicht hinzugefügt werden: " + e, "error");
  }
};

let toastTimer = null;
function toast(msg, kind) {
  const el = $("toast");
  if (!el) return;
  el.textContent = msg;
  el.className = "toast show" + (kind ? " toast-" + kind : "");
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => { el.className = "toast"; }, 3200);
}
  $("socialsClose").onclick = () => switchTab("home");

  const logsEl = $("logs");
  if ($("refreshLogsBtn")) $("refreshLogsBtn").onclick = () => refreshLogs();
  if ($("copyLogsBtn"))
    $("copyLogsBtn").onclick = async () => {
      try {
        await navigator.clipboard.writeText(logsEl.textContent || "");
        toast("Logs in Zwischenablage kopiert");
      } catch (e) {
        toast("Kopieren fehlgeschlagen: " + e);
      }
    };
  if ($("openLogFolderBtn"))
    $("openLogFolderBtn").onclick = () => invoke("open_logs_folder");

  // Update-Status: Version anzeigen + Launcher-Update prüfen
  (async () => {
    try {
      const v = await invoke("get_version");
      const uv = $("updateVersion");
      if (uv) uv.textContent = v;
    } catch (e) {}
  })();
  if ($("checkUpdateBtn")) {
    $("checkUpdateBtn").onclick = async () => {
      const res = $("updateResult");
      if (res) res.textContent = "prüfe…";
      try {
        const info = await invoke("check_app_update");
        if (!info) {
          if (res) res.textContent = "Kein Update verfügbar – du bist aktuell.";
        } else {
          const cur = await invoke("get_version");
          if (res)
            res.textContent =
              "Update verfügbar: v" + info.version + " (aktuell v" + cur + ")";
          if (info.can_install) {
            if (confirm("Update auf v" + info.version + " installieren?")) {
              await invoke("install_app_update");
            }
          } else if (res) {
            res.textContent += " (bitte manuell via GitHub installieren)";
          }
        }
      } catch (e) {
        if (res) res.textContent = "Fehler: " + e;
      }
    };
  }
$("joinFriendClose").onclick = () => {
  $("joinFriendModal").style.display = "none";
};

// ─=== Instance content management (Modrinth) ===

let manageInst = null;
let manageKind = "mod";

// Close the version picker when clicking anywhere outside of it.
document.addEventListener("click", () => closeVersionMenu());

function setManageTab(kind) {
  manageKind = kind;
  document.querySelectorAll(".tab").forEach((t) =>
    t.classList.toggle("active", t.dataset.kind === kind)
  );
  manageSearch(true);
  manageLoadInstalled();
}

function openManage(inst) {
  manageInst = inst;
  $("manageTitle").textContent = `Verwalten: ${inst.name}`;
  $("manageQuery").value = "";
  $("manageModal").style.display = "flex";
  stopBackgroundIntervals();
  setManageTab(inst.loader === "vanilla" ? "resourcepack" : "mod");
}

$("manageClose").onclick = () => {
  closeVersionMenu();
  closeChangeMenu();
  $("manageModal").style.display = "none";
  startBackgroundIntervals();
};
document.querySelectorAll(".tab").forEach((t) => {
  t.onclick = () => setManageTab(t.dataset.kind);
});
$("manageSearchBtn").onclick = () => manageSearch(true);

function renderCard(p) {
  const card = document.createElement("div");
  card.className = "card";
  card.dataset.pid = p.id;
  if (p.icon_url) {
    const img = document.createElement("img");
    img.src = p.icon_url;
    img.className = "card-icon";
    img.loading = "lazy";
    img.decoding = "async";
    img.onerror = () => img.remove();
    card.append(img);
  } else {
    const icon = document.createElement("div");
    icon.className = "card-icon placeholder";
    icon.textContent = (p.title || "?").trim().charAt(0).toUpperCase();
    card.append(icon);
  }
  const body = document.createElement("div");
  body.className = "card-body";
  const title = document.createElement("div");
  title.className = "card-title";
  title.textContent = p.title;
  const desc = document.createElement("div");
  desc.className = "card-desc";
  desc.textContent = p.description;
  const meta = document.createElement("div");
  meta.className = "card-meta";
  meta.textContent = `${(p.downloads || 0).toLocaleString()} Downloads`;
  // Install button + version dropdown arrow. A plain click installs the newest
  // compatible version; the arrow opens a menu to pick a specific version.
  const installGroup = document.createElement("div");
  installGroup.className = "install-group";
  installGroup.style.cssText = "position:relative; display:inline-flex; gap:6px;";
  const btn = document.createElement("button");
  btn.textContent = "Installieren";
  btn.onclick = () => manageInstall(p.id, p.title, null);
  const arrow = document.createElement("button");
  arrow.textContent = "▾";
  arrow.className = "ver-arrow";
  arrow.title = "Bestimmte Version wählen";
  arrow.onclick = (e) => {
    e.stopPropagation();
    toggleVersionMenu(p, arrow);
  };
  installGroup.append(btn, arrow);
  const viewBtn = document.createElement("button");
  viewBtn.textContent = "Ansehen";
  viewBtn.className = "view-btn";
  viewBtn.onclick = () => openExternal(p);
  body.append(title, desc, meta, installGroup, viewBtn);
  card.append(body);
  return card;
}

// ─=== Version picker ===
let activeVersionMenu = null;

function closeVersionMenu() {
  if (activeVersionMenu) {
    activeVersionMenu.remove();
    activeVersionMenu = null;
  }
}

async function toggleVersionMenu(p, arrow) {
  if (activeVersionMenu && activeVersionMenu._pid === p.id) {
    closeVersionMenu();
    return;
  }
  closeVersionMenu();
  const menu = document.createElement("div");
  menu.className = "version-menu";
  menu._pid = p.id;
  const rect = arrow.getBoundingClientRect();
  let top = rect.bottom + 4;
  menu.style.cssText =
    "position:fixed; top:" + top + "px; left:" + rect.left + "px; z-index:9999; background:#1e1e24; border:1px solid #333; border-radius:8px; padding:6px; min-width:210px; max-height:240px; overflow:auto; box-shadow:0 6px 20px rgba(0,0,0,.45);";
  menu.innerHTML =
    "<div style='padding:8px; color:#aaa;'>Lade Versionen…</div>";
  // Portaled to <body> so ancestors like `.card { content-visibility:auto }`
  // or `.modal-content { overflow:auto }` can't clip the dropdown.
  document.body.appendChild(menu);
  activeVersionMenu = menu;
  const mh = menu.getBoundingClientRect().height;
  if (top + mh > window.innerHeight && rect.top - 4 - mh > 0) {
    menu.style.top = (rect.top - 4 - mh) + "px";
  }
  if (!window.__vmOutsideBound) {
    window.__vmOutsideBound = true;
    document.addEventListener("click", (e) => {
      if (activeVersionMenu && !activeVersionMenu.contains(e.target)) closeVersionMenu();
    });
  }
  try {
    const versions = await invoke("modrinth_versions", {
      projectId: p.id,
      mcVersion: manageInst.version,
      loader: manageInst.loader,
    });
    menu.innerHTML = "";
    if (!versions || versions.length === 0) {
      menu.innerHTML = "<div style='padding:8px; color:#aaa;'>Keine Versionen</div>";
    } else {
      for (const v of versions) {
        const item = document.createElement("div");
        item.style.cssText =
          "padding:8px 10px; cursor:pointer; border-radius:6px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;";
        item.textContent = v.version_number || v.name;
        item.title = v.name + "  (" + (v.game_versions || []).join(", ") + ")";
        item.onmouseenter = () => (item.style.background = "#2c2c34");
        item.onmouseleave = () => (item.style.background = "transparent");
        item.onclick = (ev) => {
          ev.stopPropagation();
          closeVersionMenu();
          manageInstall(p.id, p.title, v.id);
        };
        menu.append(item);
      }
    }
  } catch (e) {
    menu.innerHTML = `<div style='padding:8px; color:#e88;'>Fehler: ${e}</div>`;
  }
}

// ─=== Paginated, install-filtered browsing ===
// We walk the Modrinth result stream, skip already-installed projects, and
// buffer the remaining hits. Each visible page is then filled from that buffer,
// pulling extra API pages as needed so gaps left by installed items are filled
// by subsequent results (which then don't reappear on later pages).
const MANAGE_PAGE = 24;
let manageAllHits = [];
let manageApiOffset = 0;
let manageExhausted = false;
let manageCurrentPage = 0;
let currentInstalled = new Set();

function resetManageSearch() {
  manageAllHits = [];
  manageApiOffset = 0;
  manageExhausted = false;
  manageCurrentPage = 0;
}

async function refreshInstalledSet() {
  currentInstalled = new Set();
  try {
    const ids = await invoke("installed_project_ids", { instanceName: manageInst.name });
    (ids[manageKind] || []).forEach((id) => currentInstalled.add(id));
  } catch (e) {
    console.warn("installed_project_ids failed:", e);
  }
}

async function fetchNextApiPage() {
  if (manageExhausted) return;
  const hits = await invoke("modrinth_search", {
    kind: manageKind,
    query: $("manageQuery").value,
    mcVersion: manageInst.version,
    loader: manageInst.loader,
    offset: manageApiOffset,
  });
  const arr = hits || [];
  for (const p of arr) {
    if (!currentInstalled.has(p.id)) manageAllHits.push(p);
  }
  manageApiOffset += MANAGE_PAGE;
  if (arr.length < MANAGE_PAGE) manageExhausted = true;
}

async function ensureHits(page) {
  const needed = (page + 1) * MANAGE_PAGE;
  while (manageAllHits.length < needed && !manageExhausted) {
    await fetchNextApiPage();
  }
}

function renderPage() {
  const results = $("manageResults");
  const start = manageCurrentPage * MANAGE_PAGE;
  const pageHits = manageAllHits.slice(start, start + MANAGE_PAGE);
  results.innerHTML = "";
  if (pageHits.length === 0) {
    results.innerHTML = "<div class='loading'>Keine Ergebnisse.</div>";
    return;
  }
  for (const p of pageHits) results.append(renderCard(p));
  renderPager();
}

async function manageSearch(reset) {
  if (reset) resetManageSearch();
  const results = $("manageResults");
  results.innerHTML = "<div class='loading'>Lade...</div>";
  if (manageKind === "mod" && manageInst.loader === "vanilla") {
    results.innerHTML =
      "<div class='loading'>Mods sind nur für Fabric/Forge/NeoForge Instanzen verfügbar.</div>";
    return;
  }
  await refreshInstalledSet();
  await ensureHits(manageCurrentPage);
  renderPage();
}

function renderPager() {
  const results = $("manageResults");
  const pager = document.createElement("div");
  pager.className = "pager";
  pager.style.cssText = "display:flex; gap:12px; justify-content:center; align-items:center; padding:14px;";

  const prev = document.createElement("button");
  prev.textContent = "← Zurück";
  prev.disabled = manageCurrentPage === 0;
  prev.onclick = () => {
    if (manageCurrentPage > 0) {
      manageCurrentPage--;
      renderPage();
    }
  };

  const info = document.createElement("span");
  info.className = "pager-info";
  info.textContent = `Seite ${manageCurrentPage + 1}`;

  const next = document.createElement("button");
  next.textContent = "Weiter →";
  const hasMoreLoaded = manageAllHits.length > (manageCurrentPage + 1) * MANAGE_PAGE;
  next.disabled = manageExhausted && !hasMoreLoaded;
  next.onclick = async () => {
    manageCurrentPage++;
    $("manageResults").innerHTML = "<div class='loading'>Lade...</div>";
    await ensureHits(manageCurrentPage);
    renderPage();
  };

  pager.append(prev, info, next);
  results.append(pager);
}

async function manageInstall(projectId, title, versionId) {
  try {
    await invoke("install_content", {
      instanceName: manageInst.name,
      kind: manageKind,
      projectId,
      mcVersion: manageInst.version,
      loader: manageInst.loader,
      versionId: versionId ?? null,
    });
    // Remove the freshly installed project from the buffered list and
    // re-render the current page (freed slot is filled by the next hit).
    const idx = manageAllHits.findIndex((x) => x.id === projectId);
    if (idx >= 0) manageAllHits.splice(idx, 1);
    if (
      manageCurrentPage > 0 &&
      manageAllHits.slice(manageCurrentPage * MANAGE_PAGE).length === 0
    ) {
      manageCurrentPage--;
    }
    renderPage();
    manageLoadInstalled();
  } catch (e) {
    alert("Installation fehlgeschlagen: " + e);
  }
}

async function manageLoadInstalled() {
  const list = $("manageInstalled");
  list.innerHTML = "";
  try {
    const res = await invoke("list_content", { instanceName: manageInst.name });
    const items = res[manageKind] || [];
    if (items.length === 0) {
      list.innerHTML = "<li style='color:#888;'>Keine installiert</li>";
      return;
    }
    for (const it of items) {
      const name = it.name || it;
      const li = document.createElement("li");
      const span = document.createElement("span");
      span.textContent = name;
      const del = document.createElement("button");
      del.textContent = "Entfernen";
      del.onclick = () => manageDelete(name);
      li.append(span, del);
      if (it.project_id) {
        const chg = document.createElement("button");
        chg.textContent = "Version ändern";
        chg.className = "ver-change-btn";
        chg.onclick = (e) => {
          e.stopPropagation();
          openChangeMenu(name, it.project_id, chg);
        };
        li.append(chg);
      }
      list.append(li);
    }
  } catch (e) {
    list.innerHTML = `<li style='color:#888;'>Fehler: ${e}</li>`;
  }
}

async function manageDelete(filename) {
  if (!confirm(`'${filename}' entfernen?`)) return;
  try {
    await invoke("delete_content", {
      instanceName: manageInst.name,
      kind: manageKind,
      filename,
    });
    manageLoadInstalled();
  } catch (e) {
    alert("Fehler: " + e);
  }
}

// ─=== Change version of an already-installed mod/pack ===
async function changeInstalledVersion(filename, projectId, versionId) {
  try {
    await invoke("change_content_version", {
      instanceName: manageInst.name,
      kind: manageKind,
      filename,
      versionId,
    });
    manageLoadInstalled();
  } catch (e) {
    alert("Version ändern fehlgeschlagen: " + e);
  }
}

let activeChangeMenu = null;
function closeChangeMenu() {
  if (activeChangeMenu) {
    activeChangeMenu.remove();
    activeChangeMenu = null;
  }
}

async function openChangeMenu(filename, projectId, btn) {
  if (activeChangeMenu && activeChangeMenu._fn === filename) {
    closeChangeMenu();
    return;
  }
  closeVersionMenu();
  closeChangeMenu();
  const menu = document.createElement("div");
  menu.className = "version-menu";
  menu._fn = filename;
  const rect = btn.getBoundingClientRect();
  let top = rect.bottom + 4;
  menu.style.cssText =
    "position:fixed; top:" + top + "px; left:" + rect.left + "px; z-index:9999; background:#1e1e24; border:1px solid #333; border-radius:8px; padding:6px; min-width:210px; max-height:240px; overflow:auto; box-shadow:0 6px 20px rgba(0,0,0,.45);";
  menu.innerHTML = "<div style='padding:8px; color:#aaa;'>Lade Versionen…</div>";
  document.body.appendChild(menu);
  activeChangeMenu = menu;
  const mh = menu.getBoundingClientRect().height;
  if (top + mh > window.innerHeight && rect.top - 4 - mh > 0) {
    menu.style.top = (rect.top - 4 - mh) + "px";
  }
  if (!window.__cmOutsideBound) {
    window.__cmOutsideBound = true;
    document.addEventListener("click", (e) => {
      if (activeChangeMenu && !activeChangeMenu.contains(e.target)) closeChangeMenu();
    });
  }
  try {
    const versions = await invoke("modrinth_versions", {
      projectId,
      mcVersion: manageInst.version,
      loader: manageInst.loader,
    });
    menu.innerHTML = "";
    if (!versions || versions.length === 0) {
      menu.innerHTML = "<div style='padding:8px; color:#aaa;'>Keine Versionen</div>";
    } else {
      for (const v of versions) {
        const item = document.createElement("div");
        item.style.cssText =
          "padding:8px 10px; cursor:pointer; border-radius:6px; white-space:nowrap; overflow:hidden; text-overflow:ellipsis;";
        item.textContent = v.version_number || v.name;
        item.title = v.name + "  (" + (v.game_versions || []).join(", ") + ")";
        item.onmouseenter = () => (item.style.background = "#2c2c34");
        item.onmouseleave = () => (item.style.background = "transparent");
        item.onclick = (ev) => {
          ev.stopPropagation();
          closeChangeMenu();
          changeInstalledVersion(filename, projectId, v.id);
        };
        menu.append(item);
      }
    }
  } catch (e) {
    menu.innerHTML = `<div style='padding:8px; color:#e88;'>Fehler: ${e}</div>`;
  }
}

// ─=== In-client detail view ("Ansehen") ===

function escapeHtml(s) {
  return String(s == null ? "" : s)
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function stripHtml(s) {
  const tmp = document.createElement("div");
  tmp.innerHTML = s;
  return tmp.textContent || "";
}

// "Ansehen" opens the project in the system's default browser (a separate,
// GPU-accelerated window) instead of rendering a heavy in-app overlay. This
// keeps the content browser smooth.
async function openExternal(p) {
  const link = `https://modrinth.com/project/${encodeURIComponent(p.id)}`;
  try {
    await invoke("open_url", { url: link });
  } catch (e) {
    console.warn("open_url failed, falling back to window.open:", e);
    window.open(link, "_blank");
  }
}

// ─=== Background intervals (paused while the content browser is open) ===
let bgIntervals = [];
function startBackgroundIntervals() {
  stopBackgroundIntervals();
  bgIntervals.push(setInterval(refreshLogs, 5000));
  bgIntervals.push(setInterval(refreshAuth, 5000));
  bgIntervals.push(setInterval(refreshDiscord, 5000));
  bgIntervals.push(setInterval(refreshDiscordLogin, 5000));
}
function stopBackgroundIntervals() {
  bgIntervals.forEach((id) => clearInterval(id));
  bgIntervals = [];
}

// Initiale Discord Rich Presence ("Im Launcher")
try {
  invoke("set_discord_presence", {
    details: "Kollegen Client",
    stateStr: "Im Launcher",
    largeText: "Kollegen Client",
    server: null,
    players: null,
  }).catch(() => {});
} catch (e) {}

loadVersions();
// Defensive: alle Overlays beim Start schließen, damit nach Update/Neuinstall
// nicht versehentlich mehrere Menüs gleichzeitig offen sind.
function closeAllOverlays() {
  ["settingsModal", "manageModal", "joinFriendModal", "profileModal", "dmModal"].forEach((id) => {
    const el = $(id);
    if (el) el.style.display = "none";
  });
  const sp = $("socialsPanel");
  if (sp) sp.classList.remove("open");
}
closeAllOverlays();

// Globales Schließen von Modals: Klick auf den Backdrop oder ESC schließt alles,
// damit nie ein Overlay hängen bleibt (robust gegen einzelne fehlende Close-Buttons).
document.addEventListener("click", (e) => {
  if (e.target && e.target.classList && e.target.classList.contains("modal")) {
    e.target.style.display = "none";
  }
});
document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") {
    document.querySelectorAll(".modal").forEach((m) => (m.style.display = "none"));
  }
});

refreshInstances();
refreshLogs();
refreshAuth();
refreshDiscord();
refreshDiscordLogin();
refreshSocial();
startBackgroundIntervals();

// ─── Kollegen-Sozial-Modul v2: Kosmetik-Editor, Profil-Viewer, DMs ───────────
(function () {
  let kmCat = null;
  let kmState = null;
  let kmMe = null;
  let dmCurrent = null;
  let dmPoll = null;

  function byId(id) {
    if (!kmCat || !id) return null;
    for (const it of kmCat) if (it.id === id) return it;
    return null;
  }
  function esc(s) {
    return String(s == null ? "" : s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  window.kmTitleOf = (u) => byId(u && u.equipped && u.equipped.title);
  window.kmBadge = (u) => byId(u && u.equipped && u.equipped.badge);
  window.kmCat = null;

  async function loadStore() {
    if (!kmCat) {
      try {
        const d = await invoke("kollegen_store");
        if (d && Array.isArray(d.catalog)) {
          kmCat = d.catalog;
          window.kmCat = kmCat;
          kmState = d;
        }
      } catch (e) {}
    }
    if (!kmMe) {
      try { kmMe = await invoke("kollegen_me"); } catch (e) {}
    }
    return kmState;
  }

  function previewAvatar() {
    const nm = (kmMe && (kmMe.mc_name || kmMe.name)) || "";
    if (nm) return `https://mc-heads.net/avatar/${encodeURIComponent(nm).replace(/%20/g, "_")}/96`;
    if (kmMe && kmMe.avatar && /^https?:\/\//.test(kmMe.avatar)) return kmMe.avatar;
    return "";
  }

  // ── Kosmetik-Editor (im Profil-Modal) ──
  const ORDER = ["title", "badge", "avatar_theme", "avatar_frame", "profile_bg", "profile_frame", "banner", "profil_stil"];
  const CATNAMES = { title: "Titel", badge: "Abzeichen", avatar_theme: "Avatar-Hintergrund", avatar_frame: "Avatar-Rahmen", profile_bg: "Profil-Hintergrund", profile_frame: "Profil-Rahmen", banner: "Banner", profil_stil: "Profilstil" };

  function renderKosmet() {
    const groups = $("kosmetGroups");
    const save = $("kosmetPreview");
    const pts = $("kosmetPts");
    const hint = $("kosmetHint");
    if (!groups || !kmCat) return;
    const st = kmState || {};
    const eq = st.equipped || {};
    groups.innerHTML = "";
    save.innerHTML = "";
    const av = document.createElement("img");
    const au = previewAvatar();
    if (au) av.src = au;
    else av.style.display = "none";
    const mt = document.createElement("div");
    mt.className = "kp-meta";
    const ti = byId(eq.title);
    const bd = byId(eq.badge);
    const nm = (kmMe && (kmMe.mc_name || kmMe.global_name || kmMe.username)) || "Du";
    let nameHtml = esc(nm);
    if (ti && ti.data && ti.data.text) nameHtml = esc(ti.data.text) + " \u00b7 " + nameHtml;
    if (bd && bd.data) nameHtml = `<span style="color:${esc(bd.data.color)}">${esc(bd.data.icon)}</span> ` + nameHtml;
    mt.innerHTML = `<div class="kp-name">${nameHtml}</div><div class="kp-sub">Level ${esc(st.level || 1)}</div>`;
    save.append(av, mt);
    if (typeof st.points === "number") pts.textContent = "\u2605 " + st.points + " Kollegen-Points";
    else pts.textContent = "Nicht angemeldet – keine Kollegen-Points sichtbar.";

    const owned = kmCat.filter((it) => it.owned);
    let anyGroup = false;
    for (const cat of ORDER) {
      const mine = owned.filter((it) => it.category === cat);
      if (!mine.length) continue;
      anyGroup = true;
      const g = document.createElement("div");
      g.className = "kosmet-group";
      g.innerHTML = `<div class="kg-title">${CATNAMES[cat] || cat}</div><div class="kosmet-chips"></div>`;
      const chips = g.querySelector(".kosmet-chips");
      const none = document.createElement("span");
      none.className = "kosmet-chip" + (eq[cat] ? "" : " eq");
      none.textContent = "Keins";
      none.onclick = () => doEquip(cat, "");
      chips.append(none);
      for (const it of mine) {
        const ch = document.createElement("span");
        ch.className = "kosmet-chip" + (eq[cat] === it.id ? " eq" : "");
        const sw = document.createElement("span");
        sw.className = "sw";
        if (it.data && it.data.color1) sw.style.background = it.data.color1;
        else if (it.data && it.data.gradient) sw.style.background = it.data.gradient;
        else if (it.data && it.data.accent) sw.style.background = it.data.accent;
        else if (it.data && it.data.text) { sw.textContent = it.data.text; sw.style.background = "#3a3f4e"; }
        else if (it.data && it.data.icon) { sw.textContent = it.data.icon; sw.style.color = it.data.color; }
        ch.append(sw);
        ch.append(" " + it.name);
        ch.onclick = () => doEquip(cat, it.id);
        chips.append(ch);
      }
      groups.append(g);
    }
    if (!anyGroup) hint.textContent = "Noch keine Kosmetik. Nimm im Store teil – Items triffst du unter kollegen.me/store.";
    else hint.textContent = "Klick auf einen Chip, um es auszur\u00fcsten. Store: kollegen.me/store";
  }

  async function doEquip(cat, itemId) {
    const hint = $("kosmetHint");
    try {
      const r = await invoke("kollegen_store_equip", { itemId, category: cat });
      if (r && (r.ok || r.equipped)) {
        kmState.equipped = r.equipped || kmState.equipped;
        renderKosmet();
      } else {
        hint.textContent = "Fehler: " + ((r && r.error) || "?");
      }
    } catch (e) {
      hint.textContent = "Fehler: " + e;
    }
  }

  // ── Profil-Viewer (kollegen.me/u/<Code> als Modal) ──
  window.kmShowProfile = async function (code) {
    if (!code) return;
    const res = $("viewProfileResult");
    const pm = $("profileModal");
    if (pm) pm.style.display = "flex";
    if (!res) return;
    res.innerHTML = `<div style="color:var(--muted);padding:0.5rem 0;">Lade Profil…</div>`;
    let p;
    try { p = await invoke("kollegen_profile_view", { code }); } catch (e) { p = { error: String(e) }; }
    if (!p || p.error) {
      res.innerHTML = `<div style="color:#f85149;padding:0.5rem 0;">Profil nicht gefunden: ${esc((p && p.error) || "?")}</div>`;
      return;
    }
    const eq = p.equipped || {};
    const find = (id) => {
      if (!id) return null;
      if (kmCat) for (const it of kmCat) if (it.id === id) return it.data || null;
      for (const it of (p.owned || [])) if (it.id === id) return it.data || null;
      return null;
    };
    const banner = find(eq.banner);
    const frame = find(eq.profile_frame);
    const bg = find(eq.profile_bg);
    const frameData = find(eq.avatar_frame);
    const themeData = find(eq.avatar_theme);
    const ti = find(eq.title);
    const bd = find(eq.badge);
    const stil = find(eq.profil_stil);
    const accent = (stil && stil.accent) || "#f1c40f";
    const head = (() => {
      if (p.uuid) return `https://mc-heads.net/head/${p.uuid}/256`;
      if (p.avatar_data_url) return p.avatar_data_url;
      return "https://mc-heads.net/head/MHF_Steve/256";
    })();
    const name = (ti && ti.text ? ti.text + " \u00b7 " : "") + (p.name || "User " + p.id);
    const status = p.online ? `<span style="color:#3fb950;">\u25cf</span> ${esc(p.server || "Online")}` : `<span style="color:#555;">\u25cf</span> Offline`;
    let h = "";
    if (banner && banner.gradient) h += `<div style="height:70px;border-radius:10px;margin-bottom:0.6rem;background:${esc(banner.gradient)};"></div>`;
    else if (p.banner_data_url) h += `<div style="height:70px;border-radius:10px;margin-bottom:0.6rem;background:url(${esc(p.banner_data_url)}) center/cover;"></div>`;
    let frm = "";
    if (frameData && frameData.color1) frm = `border:${(frameData.width || 3)}px solid ${esc(frameData.color1)};box-shadow:0 0 16px ${esc(frameData.color1)}66;`;
    let thm = "";
    if (themeData && themeData.gradient) thm = `background:${esc(themeData.gradient)};`;
    h += `<div style="display:flex;gap:0.8rem;align-items:center;">`;
    h += `<img src="${esc(head)}" style="width:64px;height:64px;border-radius:14px;object-fit:cover;${frm}${thm}" onerror="this.style.display='none';" alt=""/>`;
    h += `<div style="flex:1;min-width:0;">`;
    h += `<div style="font-weight:800;font-size:1.05rem;color:${esc(accent)}">${bd && bd.icon ? `<span style="color:${esc(bd.color)}">${esc(bd.icon)}</span> ` : ""}${esc(name)}</div>`;
    h += `<div style="color:var(--muted);font-size:0.8rem;">${status} \u00b7 Level ${esc(p.level)} \u00b7 Code ${esc(p.code)}</div>`;
    h += `</div></div>`;
    if (p.bio) h += `<div style="margin-top:0.6rem;padding:0.6rem;background:rgba(255,255,255,0.05);border-radius:10px;font-size:0.85rem;">${esc(p.bio)}</div>`;
    h += `<div style="margin-top:0.7rem;display:flex;gap:0.5rem;flex-wrap:wrap;">`;
    if (p.isViewer) h += `<span style="color:var(--muted);font-size:0.8rem;">Das ist dein eigenes Profil.</span>`;
    else {
      if (p.isFriend) h += `<button id="kmProfilDm">Nachricht senden</button>`;
      else h += `<button id="kmProfilAdd">Freund hinzuf\u00fcgen</button>`;
    }
    h += `</div>`;
    if (frame && frame.color1) res.style.border = `2px solid ${esc(frame.color1)}`;
    else res.style.border = "";
    if (bg && bg.gradient) res.style.background = esc(bg.gradient);
    else res.style.background = "";
    res.style.padding = "0.8rem";
    res.style.borderRadius = "12px";
    res.innerHTML = h;
    const add = $("kmProfilAdd");
    if (add) add.onclick = async () => {
      try {
        const r = await invoke("kollegen_friend_add", { targetId: p.code });
        let msg;
        if (r && r.pending) msg = "Anfrage gesendet – wartet auf Bestätigung ✓";
        else if (r && r.accepted) msg = "Ihr seid jetzt Freunde! ✓";
        else if (r && r.already) msg = "Bereits Freunde";
        else if (r && r.ok) msg = "Freund hinzugefügt ✓";
        else msg = `Fehler: ${esc((r && r.error) || "?")}`;
        res.innerHTML = r && r.ok ? `<div style="color:#7ee787;padding:0.4rem 0;">${esc(msg)}</div>` : `<div style="color:#f85149;padding:0.4rem 0;">${esc(msg)}</div>`;
      } catch (e) { res.innerHTML = `<div style="color:#f85149;">Fehler: ${esc(e)}</div>`; }
    };
    const dm = $("kmProfilDm");
    if (dm) dm.onclick = () => window.kmOpenDm && window.kmOpenDm(p.code);
  };

  // ── DM-Modal ──
  function closeDm() {
    if (dmPoll) clearInterval(dmPoll);
    dmPoll = null;
    dmCurrent = null;
  }
  $("dmClose").onclick = () => { closeDm(); $("dmModal").style.display = "none"; };
  $("dmSend").onclick = () => sendDm();
  $("dmText").addEventListener("keydown", (e) => { if (e.key === "Enter") sendDm(); });
  $("dmConvCode").addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      const c = $("dmConvCode").value.trim().toUpperCase();
      if (c) { $("dmConvCode").value = ""; openDmByCode(c); }
    }
  });
  new MutationObserver(() => {
    const m = $("dmModal");
    if (m && m.style.display === "none") closeDm();
  }).observe($("dmModal"), { attributes: true, attributeFilter: ["style"] });

  function timeStr(ts) {
    if (!ts) return "";
    const d = new Date(ts);
    const pad = (n) => (("" + n).length < 2 ? "0" + n : "" + n);
    return pad(d.getHours()) + ":" + pad(d.getMinutes());
  }

  async function loadConvs() {
    const listEl = $("dmConvList");
    let list;
    try { list = await invoke("kollegen_dm_conversations"); } catch (e) { list = []; }
    list = Array.isArray(list) ? list : [];
    listEl.innerHTML = "";
    if (!list.length) {
      listEl.innerHTML = `<div class="dm-empty">Keine Chats. \u00d6ffne das Profil eines Freundes und klick auf \u201eNachricht\u201c.</div>`;
      return;
    }
    for (const c of list) {
      const u = c.user || {};
      const row = document.createElement("div");
      row.className = "dm-conv" + (dmCurrent && dmCurrent === u.discordId ? " on" : "");
      const img = document.createElement("img");
      img.src = (u.profile && u.profile.avatar_data_url) || (u.uuid ? `https://mc-heads.net/head/${u.uuid}/96` : "https://mc-heads.net/head/MHF_Steve/96");
      img.onerror = () => { img.src = "https://mc-heads.net/head/MHF_Steve/96"; };
      const info = document.createElement("div");
      info.className = "dm-ci";
      const n = document.createElement("div");
      n.className = "dm-cn";
      n.textContent = u.name || "User " + u.id;
      const last = document.createElement("div");
      last.className = "dm-cl";
      const l = c.last || {};
      last.textContent = (l.from === dmMeId ? "Du: " : "") + (l.text || "") + (l.ts ? " \u00b7 " + timeStr(l.ts) : "");
      info.append(n, last);
      row.append(img, info);
      row.onclick = () => openOther(u.discordId, u.name || "User " + u.id);
      listEl.append(row);
    }
  }

  window.kmOpenDm = openDmByCode;
  async function openDmByCode(code) {
    const m = $("dmModal");
    if (m) m.style.display = "flex";
    try {
      const p = await invoke("kollegen_profile_view", { code });
      if (!p || p.error) { alert("Kein Kollege mit diesem Code."); return; }
      if (!p.isFriend) { alert("Du bist mit \u201e" + (p.name || "ihm") + "\u201c noch nicht befreundet. F\u00fcge den Code zuerst hinzu."); return; }
      openOther(p.discordId, p.name);
      loadConvs();
    } catch (e) { alert("Fehler: " + e); }
  }

  function openOther(did, name) {
    dmCurrent = did;
    const head = $("dmThreadHead");
    if (head) head.textContent = name;
    $("dmInputBox").style.display = "flex";
    document.querySelectorAll(".dm-conv.on").forEach((x) => x.classList.remove("on"));
    loadConvs();
    loadMsgs();
    if (dmPoll) clearInterval(dmPoll);
    dmPoll = setInterval(() => { if (dmCurrent) loadMsgs(true); }, 4000);
  }

  async function loadMsgs(silent) {
    if (!dmCurrent) return;
    const box = $("dmMsgList");
    let list;
    try { list = await invoke("kollegen_dm_messages", { other: dmCurrent }); } catch (e) { list = []; }
    list = Array.isArray(list) ? list : [];
    const wasBottom = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
    box.innerHTML = "";
    if (!list.length) { box.innerHTML = `<div class="dm-empty">Noch keine Nachrichten. Starte den Chat!</div>`; return; }
    for (const m of list) {
      const me = m.from === dmMeId;
      const b = document.createElement("div");
      b.className = "dm-b" + (me ? "" : " oth");
      b.textContent = m.text;
      const t = document.createElement("div");
      t.className = "dm-t";
      t.textContent = timeStr(m.ts);
      b.append(t);
      box.append(b);
    }
    if (!silent || wasBottom) box.scrollTop = box.scrollHeight;
  }

  async function sendDm() {
    const inp = $("dmText");
    const t = (inp.value || "").trim();
    if (!t || !dmCurrent) return;
    inp.value = "";
    try {
      const r = await invoke("kollegen_dm_send", { toId: dmCurrent, text: t });
      if (r && r.ok !== false) { loadMsgs(); loadConvs(); }
      else inp.value = t;
    } catch (e) { inp.value = t; alert("Fehler: " + e); }
  }

  let dmMeId = "";
  (async function init() {
    await loadStore();
    try {
      const me = await invoke("kollegen_me");
      const accts = (me && me.accounts) || [];
      const disc = accts.find((a) => (a.type || "").toLowerCase().indexOf("discord") >= 0);
      if (disc && disc.id) dmMeId = String(disc.id);
    } catch (e) {}
    const pm = $("profileModal");
    if (pm) {
      new MutationObserver(() => {
        if (pm.style.display !== "none") renderKosmet();
      }).observe(pm, { attributes: true, attributeFilter: ["style"] });
      renderKosmet();
    }
    // Freundes-Zeilen aktualisieren, falls schon gerendert.
    if (window.refreshSocial) { try { window.refreshSocial(true); } catch (e) {} }
  })();
})();
