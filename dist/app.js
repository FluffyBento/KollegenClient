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
        $("authBtn").style.display = "none";
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

  // Renders the online Discord friends and wires the "Beitreten" action that
  // lets the user join a friend's game (instance picker filtered by version).
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

// Vollständige Farbpaletten je Theme + Modus. Schlüssel = Theme-Name.
const THEMES = {
  Limit_Los: {
    dark:  { bg:"#1a0d0d", panel:"#241313", panel2:"#2e1717", accent:"#e6332a", accent2:"#b71c1c", text:"#f3e9e9", muted:"#c9a9a9", border:"#3a2222", danger:"#ff5b6e" },
    light: { bg:"#fbeeee", panel:"#f3dcdc", panel2:"#ecd0d0", accent:"#e6332a", accent2:"#b71c1c", text:"#2b1414", muted:"#7a4f4f", border:"#e0b8b8", danger:"#d83a4c" },
  },
  FluffyBento: {
    dark:  { bg:"#0f1410", panel:"#16201a", panel2:"#1d2a22", accent:"#3ba55d", accent2:"#b65cff", text:"#eaf7ee", muted:"#9fc4ab", border:"#29402f", danger:"#ff7aa2" },
    light: { bg:"#eef6ee", panel:"#dcefe0", panel2:"#cfe9d4", accent:"#2d8049", accent2:"#9b3fd1", text:"#142117", muted:"#5a7d63", border:"#bfe0c6", danger:"#d83a4c" },
  },
  Annanastv: {
    dark:  { bg:"#1a1605", panel:"#221d08", panel2:"#2b250c", accent:"#f1c40f", accent2:"#d4ac0d", text:"#fbf7e6", muted:"#cabf8e", border:"#3a3211", danger:"#ff7a59" },
    light: { bg:"#fdf8e3", panel:"#fbf0c4", panel2:"#f6e8a8", accent:"#d4ac0d", accent2:"#b7950b", text:"#2b2410", muted:"#7a6e3a", border:"#efe0a0", danger:"#d83a4c" },
  },
  T_son_: {
    dark:  { bg:"#0c1410", panel:"#112019", panel2:"#16271e", accent:"#2ecc71", accent2:"#239b56", text:"#e8f5ee", muted:"#9bc2ac", border:"#244234", danger:"#ff5b6e" },
    light: { bg:"#e9f5ee", panel:"#d6ecdd", panel2:"#c4e3cd", accent:"#239b56", accent2:"#1e8449", text:"#11231a", muted:"#5a7d68", border:"#bce0c8", danger:"#d83a4c" },
  },
  zSpicyyy: {
    dark:  { bg:"#0a1218", panel:"#0f1a22", panel2:"#14222c", accent:"#3498db", accent2:"#2471a3", text:"#e6f1f8", muted:"#9bbccc", border:"#223a48", danger:"#ff7a59" },
    light: { bg:"#e6f2f9", panel:"#cfe6f2", panel2:"#bcdced", accent:"#2471a3", accent2:"#1f618d", text:"#0e1f2a", muted:"#4f788f", border:"#aed4e6", danger:"#d83a4c" },
  },
  Irongirl: {
    dark:  { bg:"#14171a", panel:"#1c2024", panel2:"#24292e", accent:"#bdc3c7", accent2:"#95a5a6", text:"#f0f3f5", muted:"#aab4ba", border:"#2e343a", danger:"#ff5b6e" },
    light: { bg:"#ffffff", panel:"#f4f6f7", panel2:"#e9edef", accent:"#95a5a6", accent2:"#7f8c8d", text:"#1c2127", muted:"#6b777e", border:"#dde1e3", danger:"#d83a4c" },
  },
  Notschie_: {
    dark:  { bg:"#0c0810", panel:"#140e1a", panel2:"#1c1426", accent:"#9b59b6", accent2:"#71368a", text:"#f1eaf6", muted:"#b69cc6", border:"#2c2040", danger:"#ff6b9d" },
    light: { bg:"#efe7f5", panel:"#e2d4ee", panel2:"#d4c1e6", accent:"#71368a", accent2:"#5b2c70", text:"#1c1226", muted:"#6a517e", border:"#cbb3e0", danger:"#d83a4c" },
  },
  SMPNico: {
    dark:  { bg:"#081418", panel:"#0d1c20", panel2:"#122a2f", accent:"#1abc9c", accent2:"#138d75", text:"#e3f4f1", muted:"#8fc2b8", border:"#20434a", danger:"#ff7a59" },
    light: { bg:"#e0f4f1", panel:"#c9eae4", panel2:"#b3e1d8", accent:"#138d75", accent2:"#0e6e5c", text:"#0c1f1c", muted:"#4f8278", border:"#a6d8cf", danger:"#d83a4c" },
  },
  LetsLennyy: {
    dark:  { bg:"#140a0a", panel:"#1d0f0f", panel2:"#271414", accent:"#FF0000", accent2:"#cc0000", text:"#f3e9e9", muted:"#c39b9b", border:"#600000", danger:"#ff5b6e" },
    light: { bg:"#f6e6e4", panel:"#eccfcc", panel2:"#e0bcb8", accent:"#FF0000", accent2:"#cc0000", text:"#261110", muted:"#7a4f4a", border:"#600000", danger:"#d83a4c" },
  },
};

let currentSettings = null;

async function loadSettingsOnce() {
  if (currentSettings) return currentSettings;
  try {
    currentSettings = await invoke("get_settings");
  } catch (e) {
    currentSettings = { theme: "Limit_Los", theme_mode: "dark" };
  }
  return currentSettings;
}

function applyTheme(name, mode) {
  const theme = THEMES[name];
  const pal = theme ? (theme[mode] || theme.dark) : THEMES.Limit_Los.dark;
  const r = document.documentElement.style;
  r.setProperty("--bg", pal.bg);
  r.setProperty("--panel", pal.panel);
  r.setProperty("--panel-2", pal.panel2);
  r.setProperty("--accent", pal.accent);
  r.setProperty("--accent2", pal.accent2);
  r.setProperty("--text", pal.text);
  r.setProperty("--muted", pal.muted);
  r.setProperty("--border", pal.border);
  r.setProperty("--danger", pal.danger);
  document.documentElement.setAttribute("data-theme", name);
  document.documentElement.setAttribute("data-mode", mode);
  pushTheme();
}

async function applySavedTheme() {
  const s = await loadSettingsOnce();
  const name = THEMES[s.theme] ? s.theme : "Limit_Los";
  const mode = s.theme_mode === "light" ? "light" : "dark";
  applyTheme(name, mode);
}

async function saveTheme(name, mode) {
  const s = await loadSettingsOnce();
  s.theme = name;
  s.theme_mode = mode;
  try {
    await invoke("save_settings", { settings: s });
  } catch (e) {}
  applyTheme(name, mode);
}

// ─=== Settings panel wiring ===
$("settingsBtn").onclick = async () => {
  $("settingsModal").style.display = "flex";
  await loadSettingsOnce();
  await refreshSettingsAccounts();
  renderThemeList();
  syncModeButtons();
};
$("settingsClose").onclick = () => {
  $("settingsModal").style.display = "none";
};

document.querySelectorAll(".settings-tab").forEach((tab) => {
  tab.onclick = () => {
    document.querySelectorAll(".settings-tab").forEach((t) => t.classList.remove("active"));
    tab.classList.add("active");
    const t = tab.dataset.tab;
    $("settingsConnections").style.display = t === "connections" ? "" : "none";
    $("settingsThemes").style.display = t === "themes" ? "" : "none";
    $("settingsImport").style.display = t === "import" ? "" : "none";
    $("settingsUpdates").style.display = t === "updates" ? "" : "none";
  };
});

// ─ Updates (manual check + install) ─
$("updateCheckBtn").onclick = async () => {
  const status = $("updateStatus");
  const installBtn = $("updateInstallBtn");
  status.textContent = "Prüfe auf Updates…";
  installBtn.style.display = "none";
  try {
    const update = await invoke("check_app_update");
    if (update) {
      status.textContent = "Update verfügbar: Version " + update.version + (update.notes ? "\n" + update.notes : "");
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
  status.textContent = "Update wird heruntergeladen und installiert…";
  try {
    await invoke("install_app_update");
    // App restarts itself after install; this line is only reached on failure.
  } catch (e) {
    status.textContent = "Installation fehlgeschlagen: " + e;
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
      window.open(loginUrl, "_blank");
      copyText(loginUrl);
      alert("Microsoft Login:\n\nBrowser wurde geöffnet. Der Link steht in der Zwischenablage.");
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

// ─ Themes ─
function renderThemeList() {
  const list = $("themeList");
  list.innerHTML = "";
  const s = currentSettings || { theme: "Limit_Los", theme_mode: "dark" };
  for (const [name, modes] of Object.entries(THEMES)) {
    const card = document.createElement("button");
    card.className = "theme-card" + (name === s.theme ? " active" : "");
    const dot = document.createElement("span");
    dot.className = "theme-dot";
    dot.style.background = modes.dark.accent;
    const label = document.createElement("span");
    label.textContent = name;
    card.append(dot, label);
    card.onclick = () => saveTheme(name, s.theme_mode === "light" ? "light" : "dark");
    list.append(card);
  }
}

function syncModeButtons() {
  const mode = currentSettings && currentSettings.theme_mode === "light" ? "light" : "dark";
  $("modeDark").classList.toggle("active", mode === "dark");
  $("modeLight").classList.toggle("active", mode === "light");
}

$("modeDark").onclick = () => {
  const name = currentSettings ? currentSettings.theme : "Limit_Los";
  saveTheme(name, "dark");
  syncModeButtons();
  renderThemeList();
};
$("modeLight").onclick = () => {
  const name = currentSettings ? currentSettings.theme : "Limit_Los";
  saveTheme(name, "light");
  syncModeButtons();
  renderThemeList();
};

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

// Beim Start das gespeicherte Theme anwenden.
applySavedTheme();

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
