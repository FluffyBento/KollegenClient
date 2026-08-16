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

      const del = document.createElement("button");
      del.textContent = "Löschen";
      del.onclick = () => deleteInstance(inst.name);

      actions.append(launch, manage, del);
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
    let text = logs.join("\n");
    if (activeInstance) {
      try {
        const glog = await invoke("get_game_log", { instanceName: activeInstance });
        if (glog) text += "\n" + glog;
      } catch (e) {
        /* ignore missing game log */
      }
    }
    $("logs").textContent = text;

    // Auto-detect and resolve Fabric mod incompatibilities in the game log
    if (
      activeInstance &&
      !conflictHandled &&
      text.includes("Incompatible mods found")
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

      renderDiscordFriends(s.friends || []);
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

  // Renders the online Discord friends and wires the "Beitreten" action that
  // lets the user join a friend's game (instance picker filtered by version).
  function renderDiscordFriends(friends) {
    const list = $("discordFriends");
    const hint = $("friendsHint");
    if (!list) return;
    list.innerHTML = "";

    const online = friends.filter((f) => f.status && f.status !== "offline");
    if (!friends.length) {
      hint.style.display = "";
      hint.textContent =
        "Noch keine Freunde geladen – verbinde dich mit Discord, um sie zu sehen.";
      return;
    }
    if (!online.length) {
      hint.style.display = "";
      hint.textContent = "Keine Freunde gerade online.";
      return;
    }
    hint.style.display = "none";

    for (const f of online) {
      const li = document.createElement("li");

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
      const sub = document.createElement("div");
      sub.className = "friend-sub";
      sub.textContent = f.game
        ? f.version
          ? `${f.game} (${f.version})`
          : f.game
        : statusLabel(f.status);
      meta.append(name, sub);
      li.append(meta);

      if (f.join_secret) {
        const join = document.createElement("button");
        join.textContent = "Beitreten";
        join.title = "Instanz wählen und beitreten";
        join.onclick = () => openJoinFriend(f);
        li.append(join);
      }

      list.append(li);
    }
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
      // The connection state itself is shown by #discordStatus ("Verbunden als
      // …"), so here we only surface the logout action to avoid duplication.
      btn.style.display = "none";
      const out = document.createElement("button");
      out.textContent = "Trennen";
      out.onclick = discordOauthLogout;
      box.append(out);
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

async function deleteInstance(name) {
  if (!confirm(`Instanz '${name}' wirklich löschen?`)) return;
  await invoke("delete_instance", { name });
  await refreshInstances();
}

$("iVersion").onchange = updateLoaderVersions;
$("iLoader").onchange = updateLoaderVersions;

$("authBtn").onclick = async () => {
  try {
    const res = await invoke("auth_start");
    if (res.user_code && res.verification_uri) {
      const loginUrl = `${res.verification_uri}?otc=${res.user_code}`;
      $("loginUrl").value = loginUrl;
      $("loginInfo").style.display = "flex";
      window.open(loginUrl, '_blank');
      copyText(loginUrl);
      alert(`Microsoft Login:\n\nBrowser wurde geöffnet.\nDer Link steht oben zum Kopieren bereit.`);
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

$("fullscreenBtn").onclick = async () => {
  try {
    await invoke("toggle_fullscreen");
  } catch (e) {
    console.error("Vollbild fehlgeschlagen:", e);
  }
};

try {
  const win = window.__TAURI__.window.getCurrentWebviewWindow();
  win.onFullscreenChanged(({ payload }) => {
    $("fullscreenBtn").textContent = payload ? "Fenster" : "Vollbild";
  });
} catch (e) {}

$("createBtn").onclick = createInstance;

$("discordLoginBtn").onclick = discordOauthStart;

// Socials drawer (left sidebar) toggle.
$("socialsBtn").onclick = () => {
  $("socialsPanel").classList.toggle("open");
};
$("socialsClose").onclick = () => {
  $("socialsPanel").classList.remove("open");
};
$("joinFriendClose").onclick = () => {
  $("joinFriendModal").style.display = "none";
};

$("jreBtn").onclick = async () => {
  $("jreBtn").disabled = true;
  $("jreBtn").textContent = "Lade JRE...";
  try {
    const res = await invoke("download_jre_command", { version: 21 });
    if (res.ok) {
      alert("JRE erfolgreich heruntergeladen und eingerichtet!");
    } else {
      alert("JRE Download Fehler: " + (res.error || "Unbekannt"));
    }
  } catch (e) {
    alert("JRE Download fehlgeschlagen: " + e);
  } finally {
    $("jreBtn").disabled = false;
    $("jreBtn").textContent = "JRE herunterladen";
  }
};

// ─=== Instance content management (Modrinth) ===

let manageInst = null;
let manageKind = "mod";

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
  const btn = document.createElement("button");
  btn.textContent = "Installieren";
  btn.onclick = () => manageInstall(p.id, p.title);
  const viewBtn = document.createElement("button");
  viewBtn.textContent = "Ansehen";
  viewBtn.className = "view-btn";
  viewBtn.onclick = () => openExternal(p);
  body.append(title, desc, meta, btn, viewBtn);
  card.append(body);
  return card;
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

async function manageInstall(projectId, title) {
  try {
    await invoke("install_content", {
      instanceName: manageInst.name,
      kind: manageKind,
      projectId,
      mcVersion: manageInst.version,
      loader: manageInst.loader,
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
    for (const name of items) {
      const li = document.createElement("li");
      const span = document.createElement("span");
      span.textContent = name;
      const del = document.createElement("button");
      del.textContent = "Entfernen";
      del.onclick = () => manageDelete(name);
      li.append(span, del);
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
  bgIntervals.push(setInterval(refreshLogs, 3000));
  bgIntervals.push(setInterval(refreshAuth, 2000));
  bgIntervals.push(setInterval(refreshDiscord, 2000));
  bgIntervals.push(setInterval(refreshDiscordLogin, 2000));
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
refreshInstances();
refreshLogs();
refreshAuth();
refreshDiscord();
refreshDiscordLogin();
startBackgroundIntervals();
