const invoke = window.__TAURI__.core.invoke;
window.__kginvoke = (cmd, args) => invoke(cmd, args);

const $ = (id) => document.getElementById(id);

const HOME_PANEL_HTML = $("homePanel") ? $("homePanel").innerHTML : "";

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

const LOADER_COLORS = { vanilla: "#6abe30", fabric: "#dbb69b", forge: "#5b8def", neoforge: "#f08a3c", quilt: "#a66bf0" };

const UI_ICONS = {
  play: '<path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z" fill="currentColor" stroke="none"/>',
  manage: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  folder: '<path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z"/>',
  bolt: '<path d="M13 2 4 14h7l-1 8 9-12h-7z"/>',
  trash: '<path d="M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"/>',
  image: '<rect x="3" y="4" width="18" height="16" rx="2"/><circle cx="8.5" cy="9.5" r="1.8"/><path d="m4 17 5-5 4 4 2.5-2.5L20 18"/>',
  imageWide: '<rect x="2" y="6" width="20" height="12" rx="2"/><circle cx="7.5" cy="10.5" r="1.5"/><path d="m3 16 4.5-4.5L11 15"/>',
  imageOff: '<path d="M3 3l18 18"/><path d="M5 6.5A2 2 0 0 1 7 5h10a2 2 0 0 1 2 2v9.5M3.5 8.5V18a2 2 0 0 0 2 2H17"/><path d="m3.5 16.5 4-4 3.5 3.5"/>',
  plus: '<path d="M12 5v14M5 12h14"/>'
};

function uiIcon(name) {
  return `<svg class="ui-ico" viewBox="0 0 24 24" aria-hidden="true">${UI_ICONS[name] || ""}</svg>`;
}

function loaderColor(loader) {
  return LOADER_COLORS[String(loader || "vanilla").toLowerCase()] || LOADER_COLORS.vanilla;
}

function loaderRgb(loader) {
  return (hexRgb(loaderColor(loader)) || [106, 190, 48]).join(", ");
}

function relativeTime(iso) {
  const t = Date.parse(iso || "");
  if (!Number.isFinite(t)) return "Noch nie gespielt";
  const sec = Math.max(0, Math.floor((Date.now() - t) / 1000));
  if (sec < 60) return "Gerade eben";
  const min = Math.floor(sec / 60);
  if (min < 60) return `Vor ${min} Min.`;
  const hrs = Math.floor(min / 60);
  if (hrs < 24) return `Vor ${hrs} Std.`;
  const days = Math.floor(hrs / 24);
  if (days === 1) return "Gestern";
  if (days < 30) return `Vor ${days} Tagen`;
  const months = Math.floor(days / 30);
  if (months < 12) return months === 1 ? "Vor 1 Monat" : `Vor ${months} Monaten`;
  return "Vor über einem Jahr";
}

function sortedInstances(list) {
  return [...list].sort((a, b) => (Date.parse(b.last_played || "") || 0) - (Date.parse(a.last_played || "") || 0));
}

function makeChip(text, cls) {
  const c = document.createElement("span");
  c.className = "chip-tag" + (cls ? " " + cls : "");
  c.textContent = text;
  return c;
}

function makeIconButton(iconName, title, cls, onclick) {
  const b = document.createElement("button");
  b.className = "icon-act" + (cls ? " " + cls : "");
  b.title = title;
  b.setAttribute("aria-label", title);
  b.innerHTML = uiIcon(iconName);
  b.onclick = onclick;
  return b;
}

function makePlayButton(label, cls, onclick) {
  const b = document.createElement("button");
  b.className = cls;
  b.innerHTML = uiIcon("play");
  b.append(document.createTextNode(label));
  b.onclick = onclick;
  return b;
}

function openInstanceFolder(inst) {
  invoke("open_instance_folder", { instanceName: inst.name }).catch((e) => alert("Fehler: " + e));
}

async function optimizeInstance(inst, btn) {
  btn.disabled = true;
  btn.classList.add("busy");
  try {
    const msg = await invoke("optimize_instance", { name: inst.name });
    alert(msg || "Performance-Modpack verarbeitet.");
  } catch (e) {
    alert("Optimierung fehlgeschlagen: " + e);
  }
  btn.disabled = false;
  btn.classList.remove("busy");
}

const instanceImageCache = new Map();

function cacheKeyForImage(inst, kind) {
  return `${inst.name}|${kind}`;
}

async function instanceImage(inst, kind) {
  const key = cacheKeyForImage(inst, kind);
  if (instanceImageCache.has(key)) return instanceImageCache.get(key);
  let b64 = null;
  try {
    b64 = await invoke("get_instance_image", { name: inst.name, kind });
  } catch (e) {
    b64 = null;
  }
  const url = b64 ? `data:image/png;base64,${b64}` : "";
  instanceImageCache.set(key, url);
  return url;
}

function pickImageFile(title) {
  const open = window.__TAURI__.dialog?.open || window.__TAURI__.pluginDialog?.open;
  if (!open) {
    alert("Datei-Dialog nicht verfügbar.");
    return Promise.resolve(null);
  }
  return open({
    title,
    multiple: false,
    filters: [{ name: "Bild", extensions: ["png", "jpg", "jpeg", "webp"] }],
  }).then((p) => (Array.isArray(p) ? p[0] : p)).catch(() => null);
}

async function changeInstanceImage(inst, kind) {
  const isIcon = kind === "icon";
  const path = await pickImageFile(isIcon ? "Icon für die Instanz wählen" : "Banner für die Instanz wählen");
  if (!path) return;
  try {
    await invoke("set_instance_image", { name: inst.name, kind, path });
    instanceImageCache.delete(cacheKeyForImage(inst, kind));
    refreshInstances();
    toast(isIcon ? "Icon gespeichert" : "Banner gespeichert", "ok");
  } catch (e) {
    alert("Bild konnte nicht gespeichert werden: " + e);
  }
}

async function resetInstanceImage(inst, kind) {
  try {
    await invoke("clear_instance_image", { name: inst.name, kind });
    instanceImageCache.delete(cacheKeyForImage(inst, kind));
    refreshInstances();
  } catch (e) {
    alert("Zurücksetzen fehlgeschlagen: " + e);
  }
}

function buildInstanceCard(inst, index) {
  const card = document.createElement("div");
  card.className = "instance-card";
  card.setAttribute("role", "listitem");
  card.style.setProperty("--inst-rgb", loaderRgb(inst.loader));
  card.style.setProperty("--i", String(Math.min(index, 12)));

  const banner = document.createElement("div");
  banner.className = "instance-card-banner";
  const played = document.createElement("span");
  played.className = "instance-card-played";
  played.textContent = relativeTime(inst.last_played);
  const tools = document.createElement("div");
  tools.className = "instance-card-tools";
  const iconBtn = makeIconButton("image", "Icon ändern", "", () => changeInstanceImage(inst, "icon"));
  const bannerBtn = makeIconButton("imageWide", "Banner ändern", "", () => changeInstanceImage(inst, "banner"));
  tools.append(iconBtn, bannerBtn);
  if (inst.loader === "fabric" || inst.loader === "quilt") {
    const opt = makeIconButton("bolt", "Performance-Modpack installieren", "", () => optimizeInstance(inst, opt));
    tools.append(opt);
  }
  tools.append(makeIconButton("trash", "Instanz löschen", "danger", () => deleteInstance(inst.name, inst.id)));
  banner.append(played, tools);

  instanceImage(inst, "banner").then((url) => {
    if (url) banner.style.backgroundImage = `url("${url}")`;
  });

  const top = document.createElement("div");
  top.className = "instance-card-top";
  const icon = document.createElement("div");
  icon.className = "inst-icon";
  icon.innerHTML = instIconSVG(inst.name, inst.loader);
  instanceImage(inst, "icon").then((url) => {
    if (!url) return;
    icon.innerHTML = "";
    const img = document.createElement("img");
    img.src = url;
    img.alt = "";
    icon.classList.add("has-img");
    icon.append(img);
  });
  const info = document.createElement("div");
  info.className = "instance-card-info";
  const name = document.createElement("div");
  name.className = "instance-card-name";
  name.textContent = inst.name;
  name.title = inst.name;
  const meta = document.createElement("div");
  meta.className = "instance-card-meta";
  const badge = document.createElement("span");
  badge.className = "loader-badge";
  badge.textContent = inst.loader || "?";
  meta.append(document.createTextNode(inst.version || "?"), badge);
  info.append(name, meta);
  top.append(icon, info);

  const chips = document.createElement("div");
  chips.className = "instance-card-chips";
  const modCount = Array.isArray(inst.mods) ? inst.mods.length : 0;
  if (modCount > 0) chips.append(makeChip(`${modCount} Mods`));
  if (inst.memory_max) chips.append(makeChip(`RAM ${inst.memory_max}`));
  if (inst.server) chips.append(makeChip(inst.server, "server"));

  const actions = document.createElement("div");
  actions.className = "instance-card-actions";
  const launch = makePlayButton("Starten", "primary", () => launchGame(inst.name));
  const manage = makeIconButton("manage", "Verwalten", "", () => openManage(inst));
  const folder = makeIconButton("folder", "Instanz-Ordner öffnen", "", () => openInstanceFolder(inst));
  actions.append(launch, manage, folder);

  const body = document.createElement("div");
  body.className = "instance-card-body";
  body.append(top);
  if (chips.childElementCount > 0) body.append(chips);
  body.append(actions);

  card.append(banner, body);
  return card;
}

function renderHomeHero(instances) {
  const hero = $("homeHero");
  if (!hero) return;
  hero.innerHTML = "";
  const copy = document.createElement("div");
  copy.className = "hero-copy";
  const eyebrow = document.createElement("div");
  eyebrow.className = "hero-eyebrow";
  const title = document.createElement("h3");
  title.className = "hero-title";
  const actions = document.createElement("div");
  actions.className = "hero-actions";
  const art = document.createElement("div");
  art.className = "hero-art";

  if (!instances.length) {
    hero.style.setProperty("--hero-rgb", "var(--accent-rgb)");
    eyebrow.textContent = "Willkommen";
    title.textContent = "Bereit für dein erstes Abenteuer?";
    const sub = document.createElement("p");
    sub.className = "hero-sub";
    sub.textContent = "Erstelle deine erste Instanz und leg mit den Kollegen los.";
    const create = makePlayButton("Instanz erstellen", "btn-play", () => switchTab("create"));
    create.querySelector("svg").outerHTML = uiIcon("plus");
    actions.append(create);
    copy.append(eyebrow, title, sub, actions);
    art.innerHTML = instIconSVG("", "vanilla");
    hero.append(copy, art);
    return;
  }

  const inst = sortedInstances(instances)[0];
  hero.style.setProperty("--hero-rgb", loaderRgb(inst.loader));
  eyebrow.textContent = inst.last_played ? "Weiter spielen" : "Bereit zum Start";
  title.textContent = inst.name;
  title.title = inst.name;
  const meta = document.createElement("div");
  meta.className = "hero-meta";
  meta.append(makeChip(inst.version || "?"), makeChip(inst.loader || "?", "loader"), makeChip(relativeTime(inst.last_played)));
  if (inst.server) meta.append(makeChip(inst.server, "server"));
  actions.append(
    makePlayButton("Spielen", "btn-play", () => launchGame(inst.name)),
    (() => {
      const b = document.createElement("button");
      b.className = "btn-glass";
      b.innerHTML = uiIcon("manage");
      b.append(document.createTextNode("Verwalten"));
      b.onclick = () => openManage(inst);
      return b;
    })()
  );
  copy.append(eyebrow, title, meta, actions);
  art.innerHTML = instIconSVG(inst.name, inst.loader);
  hero.append(copy, art);
}

function renderInstanceList() {
  const list = $("instanceList");
  if (!list) return;
  const all = instancesCache || [];
  const search = $("instanceSearch");
  const q = search ? search.value.trim().toLowerCase() : "";
  const shown = sortedInstances(all).filter((i) => !q || `${i.name} ${i.version} ${i.loader}`.toLowerCase().includes(q));
  const count = $("homeCount");
  if (count) count.textContent = q ? `${shown.length} von ${all.length}` : String(all.length);
  list.innerHTML = "";
  if (all.length === 0) {
    const empty = document.createElement("div");
    empty.className = "instance-empty";
    empty.textContent = "Noch keine Instanzen vorhanden.";
    list.append(empty);
    return;
  }
  if (shown.length === 0) {
    const empty = document.createElement("div");
    empty.className = "instance-empty";
    empty.textContent = "Keine Treffer.";
    list.append(empty);
    return;
  }
  shown.forEach((inst, idx) => list.append(buildInstanceCard(inst, idx)));
}

function bindHomeControls() {
  const search = $("instanceSearch");
  if (search) search.oninput = renderInstanceList;
  const create = $("homeCreateBtn");
  if (create) create.onclick = () => switchTab("create");
  const skinBtn = $("homeSkinOpen");
  if (skinBtn) skinBtn.onclick = () => switchTab("skins");
  const capeBtn = $("homeCapeOpen");
  if (capeBtn) capeBtn.onclick = () => switchTab("capes");
  const inviteBtn = $("homeInviteBtn");
  if (inviteBtn) {
    inviteBtn.onclick = async () => {
      const user = (socialMe && (socialMe.mc_name || socialMe.username || socialMe.global_name)) || "Jemand";
      const text = `${user} nutzt den **Kollegen Client** und findet DU solltest es auch!\n## [DOWNLOAD](https://kollegen.me/minecraft)`;
      try {
        if (navigator.share) {
          await navigator.share({ title: "Kollegen Client", text });
          return;
        }
      } catch (e) {}
      try { await copyText(text); toast("Einladungstext kopiert", "ok"); } catch (e) { toast("Kopieren fehlgeschlagen", "error"); }
    };
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
    renderHomeHero(instances);
    renderInstanceList();
    if (typeof renderHomeExtras === "function") renderHomeExtras();
  } catch (e) {
    console.error(e);
  }
}

function instIconSVG(name, loader) {
  const l = String(loader || "vanilla").toLowerCase();
  const top = l === "fabric" ? "#7ddc4f" : l === "forge" ? "#8fd14f" : l === "neoforge" ? "#a3e635" : "#6abe30";
  return `<svg viewBox="0 0 64 64" aria-hidden="true">`
    + `<polygon points="32,6 58,20 32,34 6,20" fill="${top}"/>`
    + `<polygon points="20,13 46,27 32,33 14,23" fill="#86d95c" opacity="0.55"/>`
    + `<polygon points="6,20 32,34 32,58 6,44" fill="#8a5f3c"/>`
    + `<polygon points="58,20 32,34 32,58 58,44" fill="#5f3d24"/>`
    + `<rect x="14" y="34" width="5" height="5" fill="#6b4423"/>`
    + `<rect x="24" y="42" width="5" height="5" fill="#75502e"/>`
    + `<rect x="40" y="38" width="5" height="5" fill="#4e3319"/>`
    + `<rect x="47" y="46" width="4" height="4" fill="#6b4423"/>`
    + `<rect x="18" y="48" width="4" height="4" fill="#75502e"/>`
    + `</svg>`;
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
        if (typeof msg === "string" && msg.startsWith("WIEDERHOLT:")) {
          alert(msg);
        } else {
          alert(
            "Mod-Konflikt erkannt und automatisch behoben:\n\n" +
              msg +
              "\n\nDer Launcher wird neu gestartet…"
          );
          launchGame(activeInstance, true);
        }
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
        ? "Verbunden"
        : `Status: ${status.state} ${status.msg ? '(' + status.msg + ')' : ''}`;
      statusEl.classList.toggle("ok", status.state === "done");
      const head = $("accountHead");
      if (head) {
        if (status.state === "done" && status.username) {
          head.src = `https://mc-heads.net/avatar/${encodeURIComponent(status.username)}/64`;
          head.hidden = false;
        } else {
          head.hidden = true;
        }
      }
      if (status.state === "done") {
        const li = $("loginInfo");
        if (li) li.style.display = "none";
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
      if (u && u.avatar_url) discordAvatarFallback = u.avatar_url;
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
       // Nur anzeigen, wenn keine Kollegen-Freunde vorhanden sind
       if (socialFriends && socialFriends.length) {
         hint.style.display = "none";
         return;
       }
       hint.style.display = "";
       hint.textContent = connected
         ? "Noch keine Discord-Freunde geladen."
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
        badge.textContent = "Kollegen Client";
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
    if (window.renderGroups) { try { window.renderGroups(); } catch (e) {} }
    if (window.renderKollegenSummary) { try { window.renderKollegenSummary(); } catch (e) {} }
    if (window.renderKollegenStore) { try { window.renderKollegenStore(); } catch (e) {} }
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
  let discordAvatarFallback = "";
  function normalizeProfile(me) {
    if (!me) return me;
    const accts = Array.isArray(me.accounts) ? me.accounts : [];
    const disc = accts.find((a) => (a.type || "").toLowerCase().indexOf("discord") >= 0) || null;
    const avatar = me.avatar || (disc && (disc.avatar_url || disc.avatar)) || "";
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
      profile: me.profile && typeof me.profile === "object" ? me.profile : null,
    };
  }

  function resolveAvatar(me) {
    if (!me) return "";
    const prof = me.profile || {};
    const choice = prof.avatar_choice || "discord";
    const nm = me.mc_name || me.username || "";
    if (choice === "minecraft" && nm) return `https://mc-heads.net/avatar/${encodeURIComponent(nm).replace(/%20/g, "_")}/128`;
    if (choice === "upload" && prof.avatar_data_url) return prof.avatar_data_url;
    const url = avatarUrl(me) || discordAvatarFallback;
    if (url) return url;
    if (nm) return `https://mc-heads.net/avatar/${encodeURIComponent(nm).replace(/%20/g, "_")}/128`;
    return "";
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
       online: !!(f.online || f.is_online || f.status === "online" || f.status === "Online" || f.online_status === "online"),
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
        const url = resolveAvatar(socialMe);
        if (url) {
          const av = $("pwAvatar");
          if (av.getAttribute("src") !== url) av.src = url;
          av.style.display = "";
          av.onerror = () => { av.style.display = "none"; };
        } else {
          $("pwAvatar").style.display = "none";
        }
        $("pwName").textContent = socialMe.mc_name || socialMe.username || "—";
        $("pwDiscord").textContent = socialMe.global_name || socialMe.username || "";
      }
    }
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
          const url = resolveAvatar(socialMe);
          if (url) {
            if (spAvatar.getAttribute("src") !== url) spAvatar.src = url;
            spAvatar.style.display = "";
            spAvatar.onerror = () => { spAvatar.style.display = "none"; };
          } else spAvatar.style.display = "none";
        }
      }
    }
    loadHomeSkin(false);
  }

// Tabs im Profil-Fenster (Profil / Kosmetik / Skin & Capes)
(function wirePmTabs() {
  const tabs = document.getElementById("pmTabs");
  if (!tabs) return;
  const apply = (key) => {
    tabs.querySelectorAll("button[data-pane]").forEach((b) => {
      const on = b.dataset.pane === key;
      b.classList.toggle("active", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
    document.querySelectorAll("#profileModal .pm-pane").forEach((p) => {
      p.classList.toggle("on", p.dataset.pane === key);
    });
  };
  tabs.addEventListener("click", (e) => {
    const b = e.target.closest("button[data-pane]");
    if (b) apply(b.dataset.pane);
  });
  window.pmShowPane = apply;
  // Beim Schließen das Ergebnis-Element wieder auf die Socials-Seite zurücksetzen
  const pmEl = document.getElementById("profileModal");
  if (pmEl) {
    new MutationObserver(() => {
      if (pmEl.style.display !== "none") return;
      const wr = document.getElementById("viewProfileResult");
      const back = document.getElementById("vpResultSlot");
      if (wr && back && wr.parentElement !== back) back.append(wr);
      const c = pmEl.querySelector(".profile-modal");
      if (c) c.classList.remove("foreign");
    }).observe(pmEl, { attributes: true, attributeFilter: ["style"] });
  }
})();

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
    avatarUpload.hidden = v !== 'upload';
    const av = $("profileAvatarPreview");
    if (!av) return;
    if (v === 'minecraft') {
      const nm = (socialMe && socialMe.mc_name) || "";
      if (nm) {
        av.src = `https://mc-heads.net/avatar/${encodeURIComponent(nm).replace(/%20/g, "_")}/128`;
        av.style.display = "";
      }
    } else if (v === 'discord') {
      const url = avatarUrl(socialMe) || discordAvatarFallback;
      if (url) { av.src = url; av.style.display = ""; }
      else { av.removeAttribute("src"); av.style.display = "none"; }
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
     const hint = $("kollegenHint");
     if (hint) {
       if (!socialFriends || !socialFriends.length) {
         hint.textContent = "Noch keine Kollegen-Freunde. Füge welche über deinen Code hinzu.";
         hint.style.display = "";
       } else {
         hint.style.display = "none";
       }
     }
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
      list.innerHTML = `<li class="list-empty">Keine Freunde – füge welche über deinen Code hinzu.</li>`;
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
      const ti = (window.kmPref && window.kmPref(u)) || {};
      const visible = u.mc_name || u.global_name || u.username || "—";
      if (ti.title && ti.title.data && ti.title.data.text) name.append(ti.title.data.text + " \u00b7 ");
      name.append(visible);
      if (ti.accent) name.style.color = ti.accent;
      if (ti.font) name.style.fontFamily = ti.font;
      if (ti.sticker && ti.sticker.icon) {
        const st = document.createElement("span");
        st.style.color = ti.sticker.color || "#fff";
        st.textContent = " " + ti.sticker.icon;
        name.append(st);
      }
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
    pmSetView(true);
    const wrap = $("viewProfileResult");
    if (!wrap) return;
    wrap.innerHTML = "";
    const card = document.createElement("div");
    card.className = "view-profile-result";
    const th = (window.themeOf && window.themeOf(u)) || {};
    let cardStyle = "";
    if (th.pBg && th.pBg.gradient) cardStyle += `background:linear-gradient(135deg,${th.pBg.gradient.split(",")[0]},${th.pBg.gradient.split(",")[1] || th.pBg.gradient.split(",")[0]});color:#fff;`;
    else if (th.pBg && (th.pBg.color1 || th.pBg.accent)) cardStyle += `background:linear-gradient(135deg,${esc(th.pBg.color1 || th.pBg.accent)},${esc(th.pBg.color2 || th.pBg.color1 || th.pBg.accent)});color:#fff;`;
    if (th.pFrame && th.pFrame.color1) cardStyle += `border:${(th.pFrame.width || 3)}px solid ${esc(th.pFrame.color1)};`;
    if (cardStyle) card.setAttribute("style", cardStyle);
    const hdr = document.createElement("div");
    hdr.style.display = "flex"; hdr.style.alignItems = "center"; hdr.style.gap = "0.7rem";
    const head = document.createElement("img");
    let avStyle = "width:56px;height:56px;border-radius:10px;object-fit:cover;background:#222;";
    if (th.aFrame && th.aFrame.color1) avStyle += `border:${(th.aFrame.width || 3)}px solid ${esc(th.aFrame.color1)};`;
    if (th.aTheme && th.aTheme.gradient) avStyle += `background:${esc(th.aTheme.gradient)};`;
    head.setAttribute("style", avStyle);
    const nm = u.mc_name || u.global_name || u.username || "";
    if (nm) head.src = `https://mc-heads.net/head/${encodeURIComponent(nm).replace(/%20/g, "_")}/256`;
    else if (u.avatar) head.src = u.avatar;
    else head.style.display = "none";
    head.onerror = () => { head.style.display = "none"; };
    hdr.append(head);
    const info = document.createElement("div");
    info.style.flex = "1"; info.style.minWidth = "0";
    let nameHtml = escapeHtml(nm || "—");
    if (th.title && th.title.text) nameHtml = `${escapeHtml(th.title.text)} ${nameHtml}`;
    if (th.badge && th.badge.icon) nameHtml = `<span style="color:${escapeHtml(th.badge.color)}">${escapeHtml(th.badge.icon)}</span> ${nameHtml}`;
    if (th.sticker && th.sticker.icon) nameHtml += ` <span style="color:${escapeHtml(th.sticker.color)}">${escapeHtml(th.sticker.icon)}</span>`;
    const accent = th.accent || "";
    const nmStyle = `font-weight:700;font-size:1.05rem;${accent ? `color:${escapeHtml(accent)};` : ""}${th.font ? `font-family:${escapeHtml(th.font)};` : ""}`;
    let html = `<div style="${nmStyle}">${nameHtml} <span class="status-dot ${u.online ? "online" : "offline"}"></span></div>`;
    html += `<div style="color:var(--muted);font-size:.85rem;">${u.online ? (u.server ? `Online auf ${escapeHtml(u.server)}` : "Online") : "Offline"}</div>`;
    if (u.friend_code) html += `<div style="color:var(--muted);font-size:.85rem;">Code: ${escapeHtml(u.friend_code)}</div>`;
    if (u.uuid) html += `<div style="color:var(--muted);font-size:.75rem;">UUID ${escapeHtml(String(u.uuid).slice(0, 8))}…</div>`;
    const banner = u.banner_data_url || (th.banner && (th.banner.data_url || th.banner.gradient));
    if (banner) html += `<div style="margin-top:.5rem;">${/^https?:|^data:/.test(banner) ? `<img src="${escapeHtml(banner)}" style="max-width:100%;border-radius:8px;"/>` : `<div style="height:52px;border-radius:8px;background:${escapeHtml(banner)};"></div>`}</div>`;
    if (u.bio) html += `<div style="margin-top:.5rem;">${escapeHtml(u.bio)}</div>`;
    info.innerHTML = html;
    hdr.append(info);
    card.append(hdr);
    wrap.append(card);
  }

     function openProfileModal(skinOnly) {
       skinOnly = !!skinOnly;
       const m = $("profileModal");
       if (!m) return;
       m.style.display = "flex";
       const content = m.querySelector(".profile-modal");
       if (content) content.classList.toggle("skin-only", skinOnly);
       if (!skinOnly) pmSetView(false);
       if (window.pmShowPane) window.pmShowPane("profile");
       setTimeout(() => loadSkinChanger(false), 60);

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
        setProfileEditMode(true);
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
  }

  // Profil-Modal: eigene Bearbeitung vs. Fremdansicht
  function pmSetView(foreign) {
    const mode = foreign ? "none" : "";
    ["profileSkinCol", "pmOwnHead", "pmTabs", "profileCustomize", "kosmetBox", "skinChanger", "capeChanger"].forEach((id) => {
      const el = document.getElementById(id);
      if (el) el.style.display = mode;
    });
    const wr = document.getElementById("viewProfileResult");
    if (wr) wr.style.display = foreign ? "block" : "";
    // Fremdansicht: Ergebnis im Fenster anzeigen, sonst zurück auf die Socials-Seite
    const pmc = document.querySelector("#profileModal .profile-modal");
    if (pmc) pmc.classList.toggle("foreign", !!foreign);
    const slot = document.getElementById(foreign ? "pmForeign" : "vpResultSlot");
    if (wr && slot && wr.parentElement !== slot) slot.append(wr);
  }

  // Profile editing helpers
  function setProfileEditMode(on) {
    const editBtn = $("profileEditToggle");
    const saveBtn = $("profileSaveBtn");
    const cancelBtn = $("profileCancelBtn");
    const inputs = ["profileBio","profileBannerInput","profileAvatarChoice","profileAvatarUpload","viewProfileName","viewProfileBtn"];
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
      profile.public = true;
      profile.avatar_choice = $("profileAvatarChoice").value;
      if ($("profileBannerPreview").src) profile.banner_data_url = $("profileBannerPreview").src;
      if (profile.avatar_choice === "upload" && $("profileAvatarPreview").src) {
        profile.avatar_data_url = $("profileAvatarPreview").src;
      }
      s.profile = profile;
      await invoke('save_settings', { settings: s });
      if (socialMe) {
        socialMe.profile = Object.assign({}, socialMe.profile, {
          bio: profile.bio,
          banner_data_url: profile.banner_data_url || null,
          avatar_data_url: profile.avatar_data_url || null,
          avatar_choice: profile.avatar_choice,
          public: true,
        });
      }
      renderProfileWidget();
      toast('Profil gespeichert', 'ok');
      setProfileEditMode(false);
    } catch (e) { toast('Speichern fehlgeschlagen: '+e, 'error'); }
  }

  // Publish profile to the Kollegen-Cloud via Launcher-Discord-Session.
  async function publishProfileToServer() {
    const choice = $("profileAvatarChoice").value;
    const profile = {
      uuid: socialMe && socialMe.uuid ? socialMe.uuid : null,
      name: socialMe && socialMe.mc_name ? socialMe.mc_name : (socialMe && socialMe.username ? socialMe.username : null),
      accounts: socialMe && socialMe.accounts ? socialMe.accounts : [],
      profile: {
        bio: $("profileBio").value.trim(),
        banner_data_url: $("profileBannerPreview").getAttribute("src") || null,
        avatar_data_url: choice === "upload" ? ($("profileAvatarPreview").getAttribute("src") || null) : null,
        avatar_choice: choice,
        public: true,
      }
    };

    const j = await invoke("kollegen_publish_profile", { profile });
    if (j && j.ok) {
      if (socialMe) {
        socialMe.profile = Object.assign({}, socialMe.profile, profile.profile);
      }
      renderProfileWidget();
      toast('Profil veröffentlicht', 'ok');
      refreshSocial(true);
    } else {
      toast('Publish fehlgeschlagen: ' + ((j && j.error) || 'unbekannt'), 'error');
    }
  }

  // Browse public profiles in the Kollegen-Cloud via Launcher-Backend.
  async function browsePublicProfiles(search) {
    const wrap = $("viewProfileResult");
    wrap.innerHTML = "";
    const j = await invoke("kollegen_browse_profiles", { search: search || "" });
    if (j && j.error) return alert('Fehler beim Laden: ' + j.error);
    renderProfileItems(j && j.items ? j.items : [], wrap, true);
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
      const th = (window.themeOf && window.themeOf(p)) || {};
      if (th.aFrame && th.aFrame.color1) img.style.border = `${(th.aFrame.width || 3)}px solid ${esc(th.aFrame.color1)}`;
      if (th.aTheme && th.aTheme.gradient) img.style.background = esc(th.aTheme.gradient);
      const name = document.createElement('div'); name.textContent = p.name || '—'; name.style.fontWeight = '700';
      if (th.accent) name.style.color = esc(th.accent);
      if (th.font) name.style.fontFamily = esc(th.font);
      hdr.append(img, name);
      const bio = document.createElement('div'); bio.textContent = p.bio || ''; bio.style.marginTop = '0.25rem'; bio.style.color = 'var(--muted)';
      el.append(hdr, bio);
      const btns = document.createElement('div'); btns.style.marginTop = '0.4rem'; btns.style.display = 'flex'; btns.style.gap = '0.4rem';
      const viewBtn = document.createElement('button');
      viewBtn.textContent = 'Öffnen';
      viewBtn.onclick = () => showCloudProfile(p);
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
    const th = (window.themeOf && window.themeOf(p)) || {};
    let cardStyle = "";
    if (th.pBg && th.pBg.gradient) cardStyle += `background:linear-gradient(135deg,${th.pBg.gradient.split(",")[0]},${th.pBg.gradient.split(",")[1] || th.pBg.gradient.split(",")[0]});color:#fff;`;
    else if (th.pBg && (th.pBg.color1 || th.pBg.accent)) cardStyle += `background:linear-gradient(135deg,${esc(th.pBg.color1 || th.pBg.accent)},${esc(th.pBg.color2 || th.pBg.color1 || th.pBg.accent)});color:#fff;`;
    if (th.pFrame && th.pFrame.color1) cardStyle += `border:${(th.pFrame.width || 3)}px solid ${esc(th.pFrame.color1)};`;
    if (cardStyle) card.setAttribute("style", cardStyle);
    const hdr = document.createElement("div");
    hdr.style.display = "flex"; hdr.style.alignItems = "center"; hdr.style.gap = "0.7rem";
    const head = document.createElement("img");
    let avStyle = "width:56px;height:56px;border-radius:10px;object-fit:cover;background:#222;";
    if (th.aFrame && th.aFrame.color1) avStyle += `border:${(th.aFrame.width || 3)}px solid ${esc(th.aFrame.color1)};`;
    if (th.aTheme && th.aTheme.gradient) avStyle += `background:${esc(th.aTheme.gradient)};`;
    head.setAttribute("style", avStyle);
    const nm = p.name || p.mc_name || "";
    if (nm) head.src = `https://mc-heads.net/head/${encodeURIComponent(nm).replace(/%20/g, "_")}/256`;
    else if (p.avatar_data_url) head.src = p.avatar_data_url;
    else head.style.display = "none";
    head.onerror = () => { head.style.display = "none"; };
    hdr.append(head);
    const info = document.createElement("div");
    info.style.flex = "1"; info.style.minWidth = "0";
    let nameHtml = escapeHtml(nm || p.name || "—");
    if (th.title && th.title.text) nameHtml = `${escapeHtml(th.title.text)} ${nameHtml}`;
    if (th.badge && th.badge.icon) nameHtml = `<span style="color:${escapeHtml(th.badge.color)}">${escapeHtml(th.badge.icon)}</span> ${nameHtml}`;
    if (th.sticker && th.sticker.icon) nameHtml += ` <span style="color:${escapeHtml(th.sticker.color)}">${escapeHtml(th.sticker.icon)}</span>`;
    const accent = th.accent || "";
    const nmStyle = `font-weight:700;font-size:1.05rem;${accent ? `color:${escapeHtml(accent)};` : ""}${th.font ? `font-family:${escapeHtml(th.font)};` : ""}`;
    let html = `<div style="${nmStyle}">${nameHtml}</div>`;
    if (p.code) html += `<div style="color:var(--muted);font-size:.85rem;">Code: ${escapeHtml(p.code)}</div>`;
    if (p.level) html += `<div style="color:var(--muted);font-size:.85rem;">Level ${esc(p.level)}</div>`;
    const banner = p.banner_data_url || (th.banner && (th.banner.data_url || th.banner.gradient));
    if (banner) html += `<div style="margin-top:.5rem;">${/^https?:|^data:/.test(banner) ? `<img src="${escapeHtml(banner)}" style="max-width:100%;border-radius:8px;"/>` : `<div style="height:52px;border-radius:8px;background:${escapeHtml(banner)};"></div>`}</div>`;
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
      if (prof.banner_data_url) { $("profileBannerPreview").src = prof.banner_data_url; $("profileBannerPreview").style.display = ""; } else { $("profileBannerPreview").removeAttribute("src"); $("profileBannerPreview").style.display = "none"; }
      $("profileAvatarChoice").value = prof.avatar_choice || "discord";
      if (prof.avatar_data_url) { $("profileAvatarPreview").src = prof.avatar_data_url; $("profileAvatarPreview").style.display = ""; }
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
      // Freundes-Code nachschlagen (falls Person sich im Client/Website angemeldet hat)
      try {
        const j = await invoke('kollegen_browse_profiles', { search: res.name });
        const items = (j && j.items) ? j.items : [];
        const match = items.find(p => (p.name || p.mc_name || '').toLowerCase() === (res.name || '').toLowerCase());
        if (match && (match.friend_code || match.code)) {
          const code = document.createElement('div');
          code.textContent = 'Freundes-Code: ' + (match.friend_code || match.code);
          code.style.color = 'var(--accent)';
          code.style.fontWeight = '600';
          resWrap.append(code);
        }
      } catch (e) {}
      if (res.skin_data_url) {
        const img = document.createElement('img'); img.src = res.skin_data_url; img.style.maxWidth = '140px'; img.style.borderRadius='6px'; resWrap.append(img);
        showViewProfileSkin(res.skin_data_url, res.cape_data_url);
        const dlBtn = document.createElement('button'); dlBtn.className = 'btn-secondary'; dlBtn.textContent = 'Skin herunterladen';
        dlBtn.onclick = () => {
          const name = (res.name || 'skin') + '-' + Date.now();
          invoke('skin_upload', { name, data: res.skin_data_url, variant: 'classic' }).then(r => {
            if (r && r.ok) { toast('Skin gespeichert', 'ok'); loadSkinChanger(true); }
            else toast((r && r.error) || 'Fehler', 'error');
          }).catch(e => toast('Download fehlgeschlagen: ' + e, 'error'));
        };
        resWrap.append(dlBtn);
      } else if (res.skin_url) {
        const img = document.createElement('img'); img.src = res.skin_url; img.style.maxWidth = '140px'; img.style.borderRadius='6px'; resWrap.append(img);
        showViewProfileSkin(res.skin_url, res.cape_data_url);
      }
      if (res.cape_data_url) { const c = document.createElement('img'); c.src = res.cape_data_url; c.style.maxWidth='160px'; resWrap.append(c); }
    } catch (e) { resWrap.textContent = 'Fehler: ' + e; }
  }

  function disposeProfileSkinViewer() {
    if (!skinViewer) return;
    try { skinViewer.dispose && skinViewer.dispose(); } catch (_) {}
    skinViewer = null;
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
    url = httpsify(url);
    if (url === currentSkinUrl && skinViewer) {
      applyCapeToViewer();
      return;
    }
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
        dropSkinFallback(canvas.parentElement);
        skinViewer = new sv3d.SkinViewer({ canvas: canvas, width: 240, height: 480 });
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
    showSkinFallback(wrap, canvas, url);
  }
  function showViewProfileSkin(url, capeUrl) {
    const canvas = $("viewProfileCanvas");
    if (!canvas || !url) return;
    url = httpsify(url);
    const sv3d = window.skinview3d;
    let webglOk = false;
    try {
      const probe = document.createElement("canvas");
      const g = probe.getContext("webgl2") || probe.getContext("webgl");
      webglOk = !!(g && g.getParameter);
    } catch (_) {}
    if (window.__vpSkinViewer) { try { window.__vpSkinViewer.dispose && window.__vpSkinViewer.dispose(); } catch (_) {} window.__vpSkinViewer = null; }
    if (!(sv3d && sv3d.SkinViewer && sv3d.IdleAnimation && webglOk)) {
      canvas.style.display = "none";
      return;
    }
    try {
      canvas.style.display = "";
      window.__vpSkinViewer = new sv3d.SkinViewer({ canvas: canvas, width: 220, height: 440 });
      const v = window.__vpSkinViewer;
      const pp = v.loadSkin(url);
      const ready = () => {
        if (capeUrl) { try { const c = v.loadCape(httpsify(capeUrl)); if (c && c.catch) c.catch(()=>{}); } catch (_) {} }
      };
      if (pp && pp.then) pp.then(ready).catch(ready); else ready();
      v.animation = new sv3d.IdleAnimation();
      v.autoRotate = true;
      v.autoRotateSpeed = 1.0;
    } catch (e) {
      canvas.style.display = "none";
    }
  }

  function showSkinFromName(name) {
    if (name) showSkin(`https://mc-heads.net/skin/${encodeURIComponent(name)}`);
  }

  let homeSkinViewer = null;
  let homeSkinKey = "";
  let homeSkinFailed = false;
  let homeSkinPauseTimer = null;

  function disposeHomeSkinViewer() {
    clearTimeout(homeSkinPauseTimer);
    homeSkinPauseTimer = null;
    if (!homeSkinViewer) return;
    try { homeSkinViewer.dispose && homeSkinViewer.dispose(); } catch (_) {}
    homeSkinViewer = null;
  }

  function setSkinState(text) {
    const el = $("homeSkinState");
    if (el) el.textContent = text || "";
  }

  window.httpsify = function (url) {
    if (typeof url !== "string" || !url) return url;
    // Die Mojang-API liefert http:// - die CSP des Launchers laesst nur https
    // fuer Bilder und Verbindungen zu, sonst bleiben Skin und Cape unsichtbar.
    return url.startsWith("http://") ? "https://" + url.slice(7) : url;
  };
  const httpsify = window.httpsify;

  function showSkinFallback(wrap, canvas, url) {
    let fb = wrap.querySelector(".skin-fallback");
    if (!fb) {
      fb = document.createElement("div");
      fb.className = "skin-fallback";
      wrap.insertBefore(fb, canvas);
    }
    const img = fb.querySelector("img") || document.createElement("img");
    img.alt = "";
    const nm = (socialMe && (socialMe.mc_name || socialMe.username)) || "";
    const fallbackSrc = nm
      ? `https://mc-heads.net/body/${encodeURIComponent(nm)}/320`
      : httpsify(url);
    if (img.src !== fallbackSrc) img.src = fallbackSrc;
    if (!img.parentElement) fb.append(img);
    canvas.style.display = "none";
  }

  function dropSkinFallback(wrap) {
    const fb = wrap.querySelector(".skin-fallback");
    if (fb) fb.remove();
  }

  async function loadHomeSkin(force) {
    const canvas = $("homeSkinCanvas");
    if (!canvas) return;
    const nm = (socialMe && (socialMe.mc_name || socialMe.username)) || "";
    const nameEl = $("homeSkinName");
    if (nameEl) nameEl.textContent = nm || "—";
    let url = null;
    try {
      const list = await invoke("skin_list");
      const active = (list.skins || []).find((s) => s.name === list.active);
      if (active && active.url) url = httpsify(active.url);
    } catch (e) {}
    if (!url) {
      try {
        const prof = await invoke("skin_mc_profile");
        const sk = ((prof && prof.skins) || []).find((x) => x.state === "ACTIVE") || ((prof && prof.skins) || [])[0];
        if (sk && sk.url) url = httpsify(sk.url);
        const cp = ((prof && prof.capes) || []).find((x) => x.state === "ACTIVE");
        if (cp && cp.url) currentCapeUrl = httpsify(cp.url);
      } catch (e) {}
    }
    if (!url && nm) url = `https://mc-heads.net/skin/${encodeURIComponent(nm)}`;
    if (!url) {
      setSkinState("kein Skin gefunden");
      return;
    }
    const key = url + "|" + (currentCapeUrl || "");
    if (!force && key === homeSkinKey && (homeSkinViewer || homeSkinFailed)) return;
    homeSkinKey = key;
     homeSkinFailed = false;
     const sv3d = window.skinview3d;
    let webglInfo = "kein WebGL";
    let webglOk = false;
    try {
      const probe = document.createElement("canvas");
      const g = probe.getContext("webgl2") || probe.getContext("webgl");
      webglOk = !!(g && g.getParameter);
      if (webglOk) {
        const dbg = g.getExtension("WEBGL_debug_renderer_info");
        const name = dbg ? g.getParameter(dbg.UNMASKED_RENDERER_WEBGL) : g.getParameter(g.RENDERER);
        webglInfo = String(name || "WebGL").slice(0, 42);
      }
    } catch (_) {}
    const wrap = canvas.parentElement;
    if (!(sv3d && sv3d.SkinViewer && sv3d.IdleAnimation && webglOk)) {
      showSkinFallback(wrap, canvas, url);
      homeSkinFailed = true;
      setSkinState("");
      return;
    }
    try {
      disposeHomeSkinViewer();
      canvas.style.display = "";
      dropSkinFallback(wrap);
      homeSkinViewer = new sv3d.SkinViewer({ canvas: canvas, width: 160, height: 320 });
      homeSkinViewer.animation = new sv3d.IdleAnimation();
      const p = homeSkinViewer.loadSkin(url);
      const ready = () => {
        if (!homeSkinViewer) return;
        try {
          if (currentCapeUrl) {
            const c = homeSkinViewer.loadCape(currentCapeUrl);
            if (c && typeof c.catch === "function") c.catch(() => {});
          }
        } catch (_) {}
        try { homeSkinViewer.renderPaused = false; } catch (_) {}
        clearTimeout(homeSkinPauseTimer);
        homeSkinPauseTimer = setTimeout(() => {
          if (!homeSkinViewer) return;
          try {
            homeSkinViewer.renderPaused = true;
            homeSkinViewer.render();
          } catch (_) {}
        }, 450);
        let lost = false;
        try {
          lost = homeSkinViewer.renderer.getContext().isContextLost();
        } catch (_) {}
        setSkinState(lost ? "3D-Kontext verloren" : "");
      };
      if (p && typeof p.then === "function") p.then(ready).catch(() => {
        homeSkinFailed = true;
        setSkinState("3D · Skin-Fehler");
      });
      else ready();
    } catch (e) {
      disposeHomeSkinViewer();
      homeSkinFailed = true;
      showSkinFallback(wrap, canvas, url);
      setSkinState("");
    }
  }

  // ── Skin-Bibliothek + Cape-Wechsler im Profil ──
  let skinChangerLoaded = false;
  function loadSkinChanger(force) {
    if (!force && skinChangerLoaded) return;
    skinChangerLoaded = true;
    // Capes IMMER laden – unabhängig von der Skin-Bibliothek, damit sie auch
    // dann erscheinen, wenn `skin_list` fehlschlägt oder leer ist.
    invoke("skin_mc_profile").then(renderCapes).catch(() => { skinChangerLoaded = false; renderCapes({}); });

    invoke("skin_list").then(list => {
      renderSkinLibrary(list);
      const active = (list.skins || []).find(s => s.name === list.active);
      if (active && active.url) {
        showSkin(httpsify(active.url));
        return;
      }
      // Kein lokaler aktiver Skin: automatisch vom Minecraft-Namen ziehen
      // (funktioniert ohne Microsoft-Login über das öffentliche Mojang-Profil).
      const fromMcProfile = (prof) => {
        const skins = (prof && prof.skins) || [];
        const s = skins.find(x => x.state === "ACTIVE") || skins[0];
        if (s && s.url) { showSkin(httpsify(s.url)); return; }
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
    }).catch(() => { skinChangerLoaded = false; });
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
    showSkin(httpsify(s.url));
    invoke("skin_set_active", { name: s.name }).catch(() => {});
    invoke("skin_upload", { name: s.name, data: s.url, variant: "classic" }).then(r => {
      if (r && r.mc_uploaded) toast("Skin gewechselt", "ok");
      else if (r && r.ok) toast("Lokal gespeichert (kein MC-Upload möglich)", "ok");
      else if (r && r.error) toast(r.error, "error");
      loadSkinChanger(true);
      loadHomeSkin(true);
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
       const capName = c.alias || c.capeName || c.name || "Cape";
       const capeUrl = httpsify(c.url || c.cape_url || c.capeUrl || c.data_url || c.dataUrl || null);
       if (capeUrl) {
         img.src = capeUrl;
         img.loading = "lazy";
       } else {
         img.style.background = "var(--bg-elevated)";
         img.style.minHeight = "64px";
       }
       img.alt = capName;
       img.onerror = () => {
         img.style.visibility = "visible";
         img.style.background = "var(--bg-elevated)";
         img.style.minHeight = "64px";
         img.alt = capName;
       };
       const name = document.createElement("span");
       name.textContent = capName;
       const btn = document.createElement("button");
       btn.textContent = c.state === "ACTIVE" ? "Aktiv" : "Ausrüsten";
       btn.disabled = c.state === "ACTIVE";
       btn.onclick = () => equipCape(c.id, capeUrl);
       el.append(img, name, btn);
       list.append(el);
       if (c.state === "ACTIVE") {
         currentCapeUrl = capeUrl;
         applyCapeToViewer();
       }
     });
  }

  function equipCape(id, url) {
    invoke("cape_equip", { capeId: id }).then(r => {
      if (r && r.ok) {
        toast("Cape ausgerüstet", "ok");
        if (url) { currentCapeUrl = url; applyCapeToViewer(); }
        loadSkinChanger(true);
        loadHomeSkin(true);
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
  let discordLoginWaiting = false;
  async function discordOauthStart() {
    if (discordLoginWaiting) return;
    try {
      const url = await invoke("discord_oauth_start");
      discordLoginWaiting = true;
      renderDiscordLogin({ state: "waiting", url });
    } catch (e) {
      discordLoginWaiting = false;
      renderDiscordLogin({ state: "error", message: String(e) });
    }
  }

  async function discordOauthLogout() {
    discordLoginWaiting = false;
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
      box.textContent = "Browser wurde geöffnet – bitte bei Discord anmelden… ";
      const cancel = document.createElement("button");
      cancel.className = "btn-secondary";
      cancel.textContent = "Abbrechen";
      cancel.onclick = () => {
        discordLoginWaiting = false;
        renderDiscordLogin({ state: "idle" });
      };
      box.appendChild(cancel);
      if (info.url) {
        const wrap = document.createElement("div");
        wrap.className = "socials-hint";
        wrap.textContent = "Öffnet sich kein Browser, Link kopieren und manuell öffnen: ";
        const link = document.createElement("button");
        link.className = "btn-secondary";
        link.textContent = "Login-Link kopieren";
        link.onclick = async () => {
          try {
            await navigator.clipboard.writeText(info.url);
            link.textContent = "Kopiert ✓";
          } catch (e) {
            prompt("Login-Link kopieren:", info.url);
          }
        };
        wrap.appendChild(link);
        box.appendChild(wrap);
      }
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
      const loggedIn = !!(s && s.logged_in && s.user);
      window.__discordLoggedIn = loggedIn;
      if (s && s.user) window.__discordUser = s.user;
      if (loggedIn) {
        renderDiscordLogin({ state: "done", user: s.user });
      } else if (discordLoginWaiting) {
        renderDiscordLogin({ state: "waiting" });
      } else {
        renderDiscordLogin({ state: "idle" });
      }
      const loginSet = $("discordLoginBtnSettings");
      const logoutSet = $("discordLogoutBtn");
      if (loginSet) loginSet.style.display = loggedIn ? "none" : "";
      if (logoutSet) logoutSet.style.display = loggedIn ? "" : "none";
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
  
  const submitBtn = document.querySelector("#createForm button[type=submit]");
  try {
    if (submitBtn) { submitBtn.disabled = true; submitBtn.textContent = "Erstelle..."; }

    // 1. Create instance in json and show immediately
    await invoke("create_instance", { name, version, loader, loaderVersion });
    await refreshInstances();

    // 2. Install files
    if (submitBtn) submitBtn.textContent = "Installiere...";
    await invoke("install_instance", { name, version, loader, loaderVersion });

    alert("Instanz erfolgreich erstellt und installiert!");
    $("iName").value = "";
    await refreshInstances();
  } catch (e) {
    alert("Fehler bei Instanz-Installation: " + e);
  } finally {
    if (submitBtn) { submitBtn.disabled = false; submitBtn.textContent = "Erstellen"; }
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
    showGameSplash(name, result);
  } catch (e) {
    hideGameSplash();
    alert("Launch fehlgeschlagen: " + e);
  }
  refreshLogs();
}

let gameSplashTimer = null;
let gameSplashToken = 0;

function showGameSplash(instanceName, launchResult) {
  hideGameSplash();
  const token = ++gameSplashToken;
  let pid = 0;
  try {
    const m = String(launchResult || "").match(/PID:\s*(\d+)/);
    if (m) pid = parseInt(m[1], 10) || 0;
  } catch (e) {}
  const overlay = document.createElement("div");
  overlay.id = "gameSplash";
  overlay.innerHTML =
    '<div class="gs-glow"></div>' +
    '<div class="gs-inner">' +
    '<div class="gs-eyebrow">Kollegen Client</div>' +
    '<div class="gs-title">Game Started</div>' +
    '<div class="gs-instance">' + escapeHtml(instanceName) + '</div>' +
    '<div class="gs-status"><span class="gs-dot"></span><span id="gsStatusText">Minecraft wird gestartet …</span></div>' +
    '<div class="gs-hint">Klicken zum Schließen</div>' +
    '</div>';
  overlay.addEventListener("click", () => hideGameSplash());
  document.body.appendChild(overlay);
  requestAnimationFrame(() => overlay.classList.add("gs-visible"));
  const startedAt = Date.now();
  const markers = ["backend library: lwjgl", "using graphics device", "created window"];
  gameSplashTimer = setInterval(async () => {
    if (token !== gameSplashToken) {
      clearInterval(gameSplashTimer);
      gameSplashTimer = null;
      return;
    }
    if (Date.now() - startedAt > 180000) {
      hideGameSplash();
      return;
    }
    if (pid > 0) {
      try {
        const gone = await invoke("splash_should_close", { pid });
        if (gone) {
          hideGameSplash();
          return;
        }
      } catch (e) {}
    }
    try {
      const log = await invoke("get_game_log", { instanceName });
      const lines = String(log || "").split("\n").map((s) => s.trim()).filter(Boolean);
      const last = lines.length ? lines[lines.length - 1].slice(-140) : "";
      const statusEl = document.getElementById("gsStatusText");
      if (statusEl && last) statusEl.textContent = last;
      const low = String(log || "").toLowerCase();
      if (markers.some((mk) => low.includes(mk))) hideGameSplash();
    } catch (e) {}
  }, 1000);
}

function hideGameSplash() {
  gameSplashToken++;
  if (gameSplashTimer) {
    clearInterval(gameSplashTimer);
    gameSplashTimer = null;
  }
  const overlay = document.getElementById("gameSplash");
  if (!overlay) return;
  overlay.classList.remove("gs-visible");
  setTimeout(() => {
    const el = document.getElementById("gameSplash");
    if (el) el.remove();
  }, 350);
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
  if (modal && modalImg) {
    if (typeof consoleCloseModal === "function" && consoleNavActive) consoleCloseModal();
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

const _copyUrlBtn = $("copyUrlBtn");
if (_copyUrlBtn) _copyUrlBtn.onclick = () => {
  const lu = $("loginUrl");
  if (lu) copyText(lu.value);
  _copyUrlBtn.textContent = "Kopiert!";
  setTimeout(() => (_copyUrlBtn.textContent = "Kopieren"), 1500);
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

const _createForm = $("createForm");
if (_createForm) _createForm.addEventListener("submit", (e) => { e.preventDefault(); createInstance(); });
const _homeCreateBtn = $("homeCreateBtn");
if (_homeCreateBtn) _homeCreateBtn.onclick = () => switchTab("create");

$("discordLoginBtn").onclick = discordOauthStart;
if ($("discordLoginBtnSettings")) $("discordLoginBtnSettings").onclick = discordOauthStart;

// ─=== Akzentfarbe (wird auch in die Theme-Datei für den In-Game-Mod geschrieben) ===
function readCssVar(name) {
  return getComputedStyle(document.documentElement).getPropertyValue(name).trim();
}

// Schreibt die aktuellen Launcher-Farben in eine Datei, damit der Kollegen-Client
// In-Game-Mod sein Menü in exakt derselben Farbe rendern kann (auch wenn die
// Launcher-Farbe zur Laufzeit geändert wird).
function pushTheme() {
  const theme = {
    bg: readCssVar("--bg") || "#0a0a0f",
    panel: readCssVar("--panel") || "#12121a",
    panel2: readCssVar("--panel-2") || readCssVar("--bg-elevated") || "#1c1c26",
    accent: readCssVar("--accent") || "#00d4ff",
    accent2: readCssVar("--accent-dim") || readCssVar("--accent2") || "#0099bb",
    text: readCssVar("--text-primary") || readCssVar("--text") || "#ececf1",
    muted: readCssVar("--text-secondary") || readCssVar("--muted") || "#a7a7b8",
    border: readCssVar("--border-primary") || readCssVar("--border") || "#262633",
    danger: readCssVar("--danger") || "#ff4455",
  };
  invoke("write_theme_file", { json: JSON.stringify(theme) }).catch(() => {});
}

// Einladen zum Launcher: Text + Link zum GitHub-Repo.
const inviteLauncherBtn = $("inviteLauncherBtn");
if (inviteLauncherBtn) {
  inviteLauncherBtn.onclick = () => {
    const url = "https://github.com/FluffyBento/KollegenClient";
    copyText(url);
    invoke("open_url", { url }).catch(() => window.open(url, "_blank"));
  };
}

// Beim Start die aktuelle Launcher-Theme an den Mod weitergeben.
pushTheme();

// ─=== Settings (Verbindungen / Themes / Importieren) ===

// Farbschemata je Theme (reine Farbpaletten, kein Hell/Dunkel-Modus mehr).
// `head` = Minecraft-Kopf (PNG in dist/heads) links neben dem Theme.
const THEMES = {
  Kollegen:    { bg:"#0a0c10", panel:"#12141d", panel2:"#161922", accent:"#ffaa00", accent2:"#f5c518", text:"#ededed", muted:"#9ca3af", border:"#282d3d", danger:"#ff5b6e", head:"Kollegen.png" },
  Limit_Los:   { bg:"#140a0a", panel:"#1d0f0f", panel2:"#271414", accent:"#FF0000", accent2:"#cc0000", text:"#f3e9e9", muted:"#c39b9b", border:"#600000", danger:"#ff5b6e", head:"heads/Limit_Los.png" },
  FluffyBento: { bg:"#0d0912", panel:"#160f1e", panel2:"#1e1524", accent:"#b054d8", accent2:"#7c2fa3", accent3:"#86e14a", text:"#f6ecfa", muted:"#c2a8d4", border:"#332050", danger:"#ff6b9d", head:"heads/FluffyBento.png" },
  T_son_:      { bg:"#0c1410", panel:"#112019", panel2:"#16271e", accent:"#2ecc71", accent2:"#239b56", text:"#e8f5ee", muted:"#9bc2ac", border:"#244234", danger:"#ff5b6e", head:"heads/T_son_.png" },
  zSpicyyy:    { bg:"#0a1218", panel:"#0f1a22", panel2:"#14222c", accent:"#3498db", accent2:"#2471a3", text:"#e6f1f8", muted:"#9bbccc", border:"#223a48", danger:"#ff7a59", head:"heads/zSpicyy.png" },
  Irongirl:    { bg:"#14171a", panel:"#1c2024", panel2:"#24292e", accent:"#bdc3c7", accent2:"#95a5a6", text:"#f0f3f5", muted:"#aab4ba", border:"#2e343a", danger:"#ff5b6e", head:"heads/Irongirl_.png" },
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

function hexRgb(hex) {
  const m = /^#?([0-9a-f]{6})$/i.exec(String(hex || "").trim());
  if (!m) return null;
  const n = parseInt(m[1], 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

function shade(hex, f) {
  const c = hexRgb(hex);
  if (!c) return hex;
  return "#" + c.map((v) => Math.round(Math.min(255, Math.max(0, v * f))).toString(16).padStart(2, "0")).join("");
}

function applyTheme(name) {
  const pal = THEMES[name] || THEMES.Kollegen;
  const r = document.documentElement.style;
  const accent = pal.accent3 || pal.accent2 || pal.accent;
  const dim = shade(accent, 0.7);
  const rgb = hexRgb(accent);
  if (rgb) r.setProperty("--accent-rgb", rgb.join(", "));
  r.setProperty("--bg", pal.bg);
  r.setProperty("--bg-elevated", pal.panel2);
  r.setProperty("--panel", pal.panel);
  r.setProperty("--panel-elevated", pal.panel2);
  r.setProperty("--panel-2", pal.panel2);
  r.setProperty("--accent", accent);
  r.setProperty("--accent-dim", dim);
  r.setProperty("--on-accent", rgb && (rgb[0] * 299 + rgb[1] * 587 + rgb[2] * 114) / 1000 < 135 ? "#ffffff" : "#05080c");
  r.setProperty("--accent-warm", accent);
  r.setProperty("--accent2", pal.accent2);
  r.setProperty("--accent3", pal.accent3 || pal.accent2 || pal.accent);
  r.setProperty("--text", pal.text);
  r.setProperty("--text-primary", pal.text);
  r.setProperty("--text-secondary", pal.muted);
  r.setProperty("--text-muted", pal.muted);
  r.setProperty("--muted", pal.muted);
  r.setProperty("--border", pal.border);
  r.setProperty("--border-primary", pal.border);
  r.setProperty("--border-secondary", pal.border);
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
  removeConsoleChrome();
  const panel = $("homePanel");
  if (panel) {
    panel.innerHTML = HOME_PANEL_HTML;
    bindHomeControls();
    loadHomeSkin(true);
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
    if (document.getElementById("kcTopbar") || document.querySelector("#homePanel .kc-home")) restoreDesktopHome();
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
const UPDATE_FORMAT_LABEL = {
  appimage: "AppImage",
  nsis: "Windows Installer (.exe)",
  msi: "Windows Installer (.msi)",
  dmg: "macOS DMG",
  "deb-rpm": "Paketmanager (.deb/.rpm)",
  flatpak: "Flatpak",
  unknown: "unbekanntes Format",
};

$("updateCheckBtn").onclick = async () => {
  const status = $("updateStatus");
  const installBtn = $("updateInstallBtn");
  status.textContent = "Pruefe auf Updates\u2026";
  installBtn.style.display = "none";
  try {
    const update = await invoke("check_app_update");
    if (!update) {
      status.textContent = "Du verwendest bereits die aktuellste Version.";
      return;
    }
    pendingCanInstall = !!update.can_install;
    pendingInstallFormat = update.format || "deb-rpm";
    const label = UPDATE_FORMAT_LABEL[pendingInstallFormat] || pendingInstallFormat;
    const notes = update.notes ? "\n" + update.notes : "";
    const head = "Update verfuegbar: Version " + update.version;
    const info = "\n\nInstallation: " + label + notes;

    if (pendingCanInstall) {
      status.textContent = head + info + "\n\nKlicken zum Herunterladen und Installieren.";
      installBtn.textContent = "Update installieren";
      installBtn.style.display = "";
      return;
    }
    if (pendingInstallFormat === "deb-rpm") {
      status.textContent = head + info
        + "\n\nHinweis: fuer Paketmanager-Installation wird die Datei geladen und die"
        + " Rechteabfrage angezeigt. Ohne Rechte bitte manuell aktualisieren.";
    } else {
      status.textContent = head + info
        + "\n\nDieses Format kann nicht direkt aktualisiert werden. GitHub oeffnen"
        + " und die neue Version selbst installieren.";
    }
    installBtn.textContent = "GitHub oeffnen";
    installBtn.style.display = "";
  } catch (e) {
    status.textContent = "Update-Pruefung fehlgeschlagen: " + e;
  }
};

$("updateInstallBtn").onclick = async () => {
  const status = $("updateStatus");
  if (pendingCanInstall) {
    status.textContent = "Update wird heruntergeladen und installiert\u2026";
    try {
      await invoke("install_app_update");
    } catch (e) {
      status.textContent = "Installation fehlgeschlagen: " + e;
    }
  } else {
    await invoke("open_url", { url: UPDATE_RELEASE_URL });
  }
};

$("clearCacheBtn").onclick = async () => {
  const status = $("clearCacheStatus");
  const btn = $("clearCacheBtn");
  status.textContent = "Leere Cache...";
  btn.disabled = true;
  try {
    const result = await invoke("clear_companion_cache");
    if (result && result.success) {
      status.textContent = result.message + (result.cleared && result.cleared.length ? "\nGeleert: " + result.cleared.join(", ") : "");
      status.style.color = "var(--accent)";
    } else {
      status.textContent = "Fehler: " + (result.errors ? result.errors.join(", ") : "unbekannt");
      status.style.color = "var(--danger)";
    }
  } catch (e) {
    status.textContent = "Fehler: " + e;
    status.style.color = "var(--danger)";
  } finally {
    btn.disabled = false;
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
      sw.className = "connection-btn";
      sw.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="17 1 21 5 17 9"/><path d="M3 11V9a4 4 0 0 1 4-4h14"/><polyline points="7 23 3 19 7 15"/><path d="M21 13v2a4 4 0 0 1-4 4H3"/></svg><span>Wechseln</span>`;
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
    rm.className = "connection-btn btn-danger";
    rm.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="2.2"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/></svg><span>Entfernen</span>`;
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
      const on = p.dataset.tab === name;
      p.classList.toggle("active", on);
      if (on) p.removeAttribute("hidden");
      else p.setAttribute("hidden", "");
    });
    if (document.body.classList.contains("console-mode")) kcSyncTabs(name);
    if (name === "skins") {
      loadSkinsLibrary();
    }
    if (name === "capes") {
      loadCapesView();
    }
    if (name !== "skins" && typeof disposeAllSkinViewers === "function") {
      disposeAllSkinViewers();
    }
    if (name !== "capes" && typeof clearCapeAnimTimers === "function") {
      clearCapeAnimTimers();
    }
    if (name !== "capes" && typeof disposeAllCapeViewers === "function") {
      disposeAllCapeViewers();
    }
    if (name === "home") {
      // Der Skin-Viewer wird beim Start ggf. erzeugt, bevor das Panel sichtbar
      // ist. Beim Tabwechsel nachladen, damit WebGL-Kontext und Canvas stimmen.
      loadHomeSkin(true);
    }
    if (name === "socials") {
      renderSocialAll();
      refreshSocial();
    }
    if (name === "store" && window.renderKollegenStore) {
      try { window.renderKollegenStore(); } catch (e) {}
    }
    if (name === "cosmetics") {
      try {
        const p = window.renderCosmetics();
        if (p && p.catch) p.catch((e) => {
          const bx2 = $("cosmOwned");
          if (bx2) bx2.innerHTML = '<div class="store-msg">Fehler: ' + String((e && e.message) || e) + '</div>';
        });
      } catch (e) {
        const bx3 = $("cosmOwned");
        if (bx3) bx3.innerHTML = '<div class="store-msg">Fehler: ' + String((e && e.message) || e) + '</div>';
      }
    }
    if (name === "home" && document.body.classList.contains("console-mode")) {
      renderConsoleHome();
    }
    if (name === "support" && typeof renderConsoleSupport === "function") {
      try { renderConsoleSupport(); } catch (e) { console.error(e); }
    }
    if (name !== "cosmetics" && typeof disposeCosmSkinViewer === "function") {
      disposeCosmSkinViewer();
    }
    if (name !== "profile" && typeof disposeProfileSkinViewer === "function") {
      disposeProfileSkinViewer();
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
const openProfileModalBtn = $("openProfileModalBtn");
if (openProfileModalBtn) openProfileModalBtn.onclick = () => openProfileModal();
bindHomeControls();
$("profileClose").onclick = () => {
  $("profileModal").style.display = "none";
  disposeProfileSkinViewer();
};
$("pmCopy").onclick = () => { if (socialMe) copyText(socialMe.friend_code); };

// Skin/Cape-Changer
const skinFileInput = $("skinFileInput");
if ($("skinDownloadBtn")) {
  $("skinDownloadBtn").onclick = () => {
    invoke("skin_download_current").then(r => {
      const st = (r && (r.state || r)) || null;
      if (st) {
        renderSkinLibrary(st);
        const a = (st.skins || []).find(s => s.name === st.active);
        if (a) {
          showSkin(httpsify(a.url));
          const [meta, b64] = String(a.url || "").split(",");
          const mime = (meta || "image/png").match(/data:(.*?);/)?.[1] || "image/png";
          const bin = atob(b64 || "");
          const bytes = new Uint8Array(bin.length);
          for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
          const blob = new Blob([bytes], { type: mime });
          const objUrl = URL.createObjectURL(blob);
          const dl = document.createElement("a");
          dl.href = objUrl;
          dl.download = (a.name || "minecraft-skin") + ".png";
          document.body.appendChild(dl);
          dl.click();
          dl.remove();
          setTimeout(() => URL.revokeObjectURL(objUrl), 4000);
        }
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
          if (a) showSkin(httpsify(a.url));
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

// ─── TWITCH LINKING (Image 1) ───
async function initTwitch() {
  const btn = $("twitchLinkBtn");
  const txt = $("twitchLinkText");
  const hint = $("twitchStatusHint");
  if (!btn || !txt) return;

  async function refreshTwitch() {
    try {
      const acct = await invoke("twitch_get_account");
      if (acct && acct.connected) {
        txt.textContent = "VERBUNDEN: " + (acct.display_name || acct.username || "Twitch");
        btn.classList.add("linked");
        btn.title = "Klicken zum Trennen";
        if (hint) hint.textContent = "Twitch ist verknüpft – Alerts & Streams sind im Spiel aktiv.";
      } else {
        txt.textContent = "LINK";
        btn.classList.remove("linked");
        btn.title = "Mit Twitch verbinden";
        if (hint) hint.textContent = "";
      }
    } catch (e) {
      console.error("Twitch get account failed:", e);
    }
  }

  btn.onclick = async () => {
    try {
      const acct = await invoke("twitch_get_account");
      if (acct && acct.connected) {
        if (confirm("Möchtest du die Twitch-Verknüpfung wirklich trennen?")) {
          await invoke("twitch_unlink");
          toast("Twitch-Verknüpfung getrennt", "ok");
          refreshTwitch();
        }
      } else {
        const user = prompt("Gib deinen Twitch-Benutzernamen ein:", "");
        if (user && user.trim()) {
          await invoke("twitch_link", { username: user.trim(), token: null });
          toast("Twitch erfolgreich verbunden!", "ok");
          refreshTwitch();
        }
      }
    } catch (e) {
      toast("Twitch-Fehler: " + e, "error");
    }
  };

  refreshTwitch();
}

// ─── SKIN LIBRARY (Image 2) ───
let skinViewers = [];
function disposeAllSkinViewers() {
  skinViewers.forEach(v => {
    try { v.dispose && v.dispose(); } catch (_) {}
  });
  skinViewers = [];
}

async function loadSkinsLibrary() {
  const grid = $("skinsGrid");
  if (!grid) return;
  disposeAllSkinViewers();

  let list = { active: null, skins: [] };
  try {
    list = await invoke("skin_list");
  } catch (e) {
    console.error("Failed to list skins:", e);
  }

  const searchVal = ($("skinsSearchInput") ? $("skinsSearchInput").value.trim().toLowerCase() : "");

  grid.innerHTML = "";
  const addCard = document.createElement("div");
  addCard.className = "skin-card skin-card-add";
  addCard.id = "cardAddNewSkin";
  addCard.innerHTML = `
    <div class="skin-card-silhouette">
      <div class="skin-add-plus">
        <svg viewBox="0 0 24 24" width="28" height="28" fill="none" stroke="currentColor" stroke-width="3"><line x1="12" y1="5" x2="12" y2="19"/><line x1="5" y1="12" x2="19" y2="12"/></svg>
      </div>
    </div>
    <div class="skin-card-title">Add New Skin</div>
    <div class="skin-card-sub">
      <svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><path d="M21 15v4a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2v-4"/><polyline points="17 8 12 3 7 8"/><line x1="12" y1="3" x2="12" y2="15"/></svg>
      Upload or import a skin
    </div>
  `;
  addCard.onclick = () => openSkinEditModal(null);
  grid.appendChild(addCard);

  const skins = list.skins || [];
  skins.forEach((s) => {
    if (searchVal && !s.name.toLowerCase().includes(searchVal)) return;

    const card = document.createElement("div");
    const isActive = s.name === list.active;
    card.className = "skin-card" + (isActive ? " active" : "");

    const stage = document.createElement("div");
    stage.className = "skin-card-stage";
    const canvas = document.createElement("canvas");
    canvas.width = 160;
    canvas.height = 220;
    stage.appendChild(canvas);

    const actions = document.createElement("div");
    actions.className = "skin-card-actions";

    const editBtn = document.createElement("button");
    editBtn.className = "skin-card-action-btn";
    editBtn.title = "Skin bearbeiten";
    editBtn.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>`;
    editBtn.onclick = (e) => {
      e.stopPropagation();
      openSkinEditModal(s);
    };

    const delBtn = document.createElement("button");
    delBtn.className = "skin-card-action-btn btn-del";
    delBtn.title = "Skin löschen";
    delBtn.innerHTML = `<svg viewBox="0 0 24 24" width="15" height="15" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><polyline points="3 6 5 6 21 6"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><line x1="10" y1="11" x2="10" y2="17"/><line x1="14" y1="11" x2="14" y2="17"/></svg>`;
    delBtn.onclick = async (e) => {
      e.stopPropagation();
      if (confirm(`Möchtest du den Skin "${s.name}" wirklich löschen?`)) {
        await invoke("skin_delete", { name: s.name });
        toast(`Skin "${s.name}" gelöscht`, "ok");
        loadSkinsLibrary();
      }
    };

    actions.appendChild(editBtn);
    actions.appendChild(delBtn);

    const title = document.createElement("div");
    title.className = "skin-card-title";
    title.textContent = s.name;

    const modelTag = document.createElement("div");
    modelTag.className = "skin-card-model-tag";
    const isSlim = (s.model || "").toLowerCase() === "slim";
    modelTag.innerHTML = `<svg viewBox="0 0 24 24" width="13" height="13" fill="none" stroke="currentColor" stroke-width="2"><path d="M20.59 13.41l-7.17 7.17a2 2 0 0 1-2.83 0L2 12V2h10l8.59 8.59a2 2 0 0 1 0 2.82z"/><line x1="7" y1="7" x2="7.01" y2="7"/></svg> <span>${isSlim ? "Slim" : "Classic"}</span>`;

    card.appendChild(actions);
    card.appendChild(stage);
    card.appendChild(title);
    card.appendChild(modelTag);

    card.onclick = async () => {
      document.querySelectorAll(".skin-card").forEach(c => c.classList.remove("active"));
      card.classList.add("active");
      toast(`Skin "${s.name}" aktiviert!`, "ok");

      try {
        await invoke("skin_set_active", { name: s.name });
        loadHomeSkin(true);
        invoke("skin_upload", { name: s.name, data: s.url, variant: isSlim ? "slim" : "classic" }).catch(e => {
          console.warn("Background Mojang skin sync:", e);
        });
      } catch (err) {
        toast("Skin wechseln fehlgeschlagen: " + err, "error");
      }
    };

    grid.appendChild(card);

    if (window.skinview3d && window.skinview3d.SkinViewer) {
      try {
        const viewer = new window.skinview3d.SkinViewer({
          canvas: canvas,
          width: 160,
          height: 220,
          skin: s.url,
          model: isSlim ? "slim" : "default",
        });
        viewer.animation = new window.skinview3d.WalkingAnimation();
        viewer.animation.speed = 0.45;
        viewer.autoRotate = false;
        skinViewers.push(viewer);

        setTimeout(() => {
          try {
            viewer.renderPaused = true;
            viewer.render();
          } catch (_) {}
        }, 200);

        card.addEventListener("mouseenter", () => {
          try {
            viewer.renderPaused = false;
          } catch (_) {}
        });
        card.addEventListener("mouseleave", () => {
          try {
            viewer.renderPaused = true;
          } catch (_) {}
        });
      } catch (_) {}
    }
  });
}

let editingSkinOriginalName = null;
function openSkinEditModal(skin) {
  const modal = $("skinEditModal");
  if (!modal) return;
  editingSkinOriginalName = skin ? skin.name : null;
  const title = $("skinModalTitle");
  const nameField = $("skinNameField");
  const uploadGroup = $("skinUploadGroup");
  const radios = document.querySelectorAll('input[name="skinModelRadio"]');

  if (skin) {
    if (title) title.textContent = "Skin bearbeiten";
    if (nameField) nameField.value = skin.name;
    if (uploadGroup) uploadGroup.style.display = "none";
    const isSlim = (skin.model || "").toLowerCase() === "slim";
    radios.forEach(r => { r.checked = (r.value === (isSlim ? "slim" : "classic")); });
  } else {
    if (title) title.textContent = "Skin hinzufügen";
    if (nameField) nameField.value = "";
    if (uploadGroup) uploadGroup.style.display = "block";
    const picker = $("skinFilePicker");
    if (picker) picker.value = "";
    radios.forEach(r => { r.checked = (r.value === "classic"); });
  }
  modal.removeAttribute("hidden");
  modal.style.display = "flex";
}

function closeSkinEditModal() {
  const modal = $("skinEditModal");
  if (modal) {
    modal.setAttribute("hidden", "");
    modal.style.display = "none";
  }
}

if ($("skinEditClose")) $("skinEditClose").onclick = closeSkinEditModal;
if ($("skinCancelBtn")) $("skinCancelBtn").onclick = closeSkinEditModal;
if ($("addSkinTopBtn")) $("addSkinTopBtn").onclick = () => openSkinEditModal(null);
if ($("skinsSearchInput")) $("skinsSearchInput").oninput = () => loadSkinsLibrary();

if ($("skinSaveBtn")) {
  $("skinSaveBtn").onclick = async () => {
    const nameField = $("skinNameField");
    const name = nameField ? nameField.value.trim() : "";
    if (!name) {
      toast("Bitte gib einen Namen für den Skin ein", "error");
      return;
    }
    const modelRadio = document.querySelector('input[name="skinModelRadio"]:checked');
    const model = modelRadio ? modelRadio.value : "classic";

    if (editingSkinOriginalName) {
      try {
        await invoke("skin_edit", { oldName: editingSkinOriginalName, newName: name, model });
        toast(`Skin "${name}" aktualisiert!`, "ok");
        closeSkinEditModal();
        loadSkinsLibrary();
      } catch (e) {
        toast("Fehler: " + e, "error");
      }
    } else {
      const picker = $("skinFilePicker");
      const file = picker && picker.files && picker.files[0];
      if (!file) {
        toast("Bitte wähle eine Skin-Bilddatei aus", "error");
        return;
      }
      const reader = new FileReader();
      reader.onload = async () => {
        try {
          const dataUrl = reader.result;
          await invoke("skin_import_with_model", { name, data: dataUrl, model });
          toast(`SKIN "${name}" ADDED SUCCESSFULLY!`, "ok");
          closeSkinEditModal();
          loadSkinsLibrary();
          loadHomeSkin(true);
        } catch (e) {
          toast("Fehler beim Hinzufügen: " + e, "error");
        }
      };
      reader.readAsDataURL(file);
    }
  };
}

// ─── COMMUNITY CAPES & STORE CAPES (Image 3 & Image 4) ───
let activeCapeCat = "all";
let capeFavOnly = false;
let capeAnimTimers = [];
let capeViewers = [];
let uploadCapeViewer = null;

function clearCapeAnimTimers() {
  capeAnimTimers.forEach(t => clearInterval(t));
  capeAnimTimers = [];
}

function disposeAllCapeViewers() {
  capeViewers.forEach(v => {
    try { v.dispose && v.dispose(); } catch (_) {}
  });
  capeViewers = [];
}

function disposeUploadCapeViewer() {
  if (uploadCapeViewer) {
    try { uploadCapeViewer.dispose && uploadCapeViewer.dispose(); } catch (_) {}
    uploadCapeViewer = null;
  }
}

// Convert any standard image (wallpaper, photo, meme, etc.) to HD Minecraft Cape UV layout with Elytra mapping
function convertImageToMinecraftCape(source, elytraMode = 'normal') {
  try {
    const sw = source.naturalWidth || source.videoWidth || source.width || 64;
    const sh = source.naturalHeight || source.videoHeight || source.height || 32;

    // If it's already a standard 2:1 aspect ratio Minecraft cape template:
    if (sw === 2 * sh && sw >= 64) {
      // Preserve native resolution up to 2048x1024!
      const targetW = Math.min(sw, 2048);
      const targetH = targetW / 2;
      const c = document.createElement("canvas");
      c.width = targetW;
      c.height = targetH;
      const ctx = c.getContext("2d");
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(source, 0, 0, targetW, targetH);
      return c.toDataURL("image/png");
    }
    if (sw === 22 && sh === 17) {
      const c = document.createElement("canvas");
      c.width = sw;
      c.height = sh;
      const ctx = c.getContext("2d");
      ctx.drawImage(source, 0, 0);
      return c.toDataURL("image/png");
    }

    // Determine crisp high-resolution cape dimensions based on input resolution
    // so fine details, text, and photos are never blurred or pixelated
    const W = (sw >= 1000 || sh >= 800) ? 2048 : ((sw >= 400 || sh >= 300) ? 1024 : 512);
    const H = W / 2;
    const scale = W / 64; // 32, 16, or 8

    const c = document.createElement("canvas");
    c.width = W;
    c.height = H;
    const ctx = c.getContext("2d");
    ctx.imageSmoothingEnabled = true;

    // Cape Back in Minecraft UV: x = 12 * scale, y = 1 * scale, w = 10 * scale, h = 16 * scale
    // Cape Front in Minecraft UV: x = 1 * scale, y = 1 * scale, w = 10 * scale, h = 16 * scale
    const bw = 10 * scale;
    const bh = 16 * scale;
    const backX = 12 * scale;
    const backY = 1 * scale;
    const frontX = 1 * scale;
    const frontY = 1 * scale;

    function drawCover(dx, dy, dw, dh, src, darken = 1.0) {
      const srcW = src.naturalWidth || src.width || sw;
      const srcH = src.naturalHeight || src.height || sh;
      const imgRatio = srcW / srcH;
      const destRatio = dw / dh;
      let sx, sy, sWidth, sHeight;
      if (imgRatio > destRatio) {
        sHeight = srcH;
        sWidth = srcH * destRatio;
        sx = (srcW - sWidth) / 2;
        sy = 0;
      } else {
        sWidth = srcW;
        sHeight = srcW / destRatio;
        sx = 0;
        sy = (srcH - sHeight) / 2;
      }
      ctx.drawImage(src, sx, sy, sWidth, sHeight, dx, dy, dw, dh);
      if (darken < 1.0) {
        ctx.fillStyle = `rgba(0,0,0,${1.0 - darken})`;
        ctx.fillRect(dx, dy, dw, dh);
      }
    }

    // Back face (visible behind player)
    drawCover(backX, backY, bw, bh, source, 1.0);
    // Front face (facing spine, slightly darker)
    drawCover(frontX, frontY, bw, bh, source, 0.75);

    // Edges & borders (top, bottom, left, right)
    ctx.drawImage(c, backX, backY, bw, scale, frontX, 0, bw, scale); // Top
    ctx.drawImage(c, backX, backY + bh - scale, bw, scale, 11 * scale, 0, bw, scale); // Bottom
    ctx.drawImage(c, backX, backY, scale, bh, 0, frontY, scale, bh); // Left
    ctx.drawImage(c, backX + bw - scale, backY, scale, bh, 11 * scale, frontY, scale, bh); // Right

    // Map Elytra wings:
    // Left wing outside: x = 36 * scale, y = 2 * scale, w = 10 * scale, h = 20 * scale
    // Left wing inside:  x = 24 * scale, y = 2 * scale, w = 10 * scale, h = 20 * scale
    const elW = 10 * scale;
    const elH = 20 * scale;
    const elOutX = 36 * scale;
    const elOutY = 2 * scale;
    const elInX = 24 * scale;
    const elInY = 2 * scale;

    // Render Elytra texture with selected mapping mode
    const elCanvas = document.createElement("canvas");
    elCanvas.width = sw;
    elCanvas.height = sh;
    const elCtx = elCanvas.getContext("2d");
    if (elytraMode === "mirrored") {
      elCtx.translate(sw, 0);
      elCtx.scale(-1, 1);
      elCtx.drawImage(source, 0, 0);
    } else if (elytraMode === "split") {
      elCtx.drawImage(source, 0, 0, sw / 2, sh, 0, 0, sw, sh);
    } else if (elytraMode === "rotate90") {
      elCanvas.width = sh;
      elCanvas.height = sw;
      elCtx.translate(sh, 0);
      elCtx.rotate(Math.PI / 2);
      elCtx.drawImage(source, 0, 0);
    } else if (elytraMode === "rotate180") {
      elCtx.translate(sw, sh);
      elCtx.rotate(Math.PI);
      elCtx.drawImage(source, 0, 0);
    } else if (elytraMode === "rotate270") {
      elCanvas.width = sh;
      elCanvas.height = sw;
      elCtx.translate(0, sw);
      elCtx.rotate((3 * Math.PI) / 2);
      elCtx.drawImage(source, 0, 0);
    } else {
      elCtx.drawImage(source, 0, 0);
    }

    // Draw left wing outside
    drawCover(elOutX, elOutY, elW, elH, elCanvas, 1.0);
    // Draw left wing inside (slightly darker)
    drawCover(elInX, elInY, elW, elH, elCanvas, 0.75);

    // Elytra edges
    ctx.drawImage(c, elOutX, elOutY, elW, 2 * scale, 24 * scale, 0, elW, 2 * scale); // Top
    ctx.drawImage(c, elOutX, elOutY + elH - 2 * scale, elW, 2 * scale, 34 * scale, 0, elW, 2 * scale); // Bottom
    ctx.drawImage(c, elOutX, elOutY, 2 * scale, elH, 22 * scale, 2 * scale, 2 * scale, elH); // Left
    ctx.drawImage(c, elOutX + elW - 2 * scale, elOutY, 2 * scale, elH, 34 * scale, 2 * scale, 2 * scale, elH); // Right

    return c.toDataURL("image/png");
  } catch (err) {
    console.error("Cape conversion failed:", err);
    return null;
  }
}

async function ensureCapeTextureUrl(url, elytraMode = 'normal') {
  if (!url) return null;
  return new Promise((resolve) => {
    const img = new Image();
    img.crossOrigin = "anonymous";
    img.onload = () => {
      const capeUrl = convertImageToMinecraftCape(img, elytraMode);
      resolve(capeUrl || url);
    };
    img.onerror = () => resolve(url);
    img.src = url;
  });
}

async function loadCapesView() {
  const grid = $("capesGrid");
  if (!grid) return;
  clearCapeAnimTimers();
  disposeAllCapeViewers();

  let capeData = { active_cape: null, capes: [] };
  try {
    capeData = await invoke("capes_list");
  } catch (e) {
    console.error("Capes list failed:", e);
  }

  let storeCapes = [];
  try {
    let catalog = [];
    if (Array.isArray(window.__cosmStore)) {
      catalog = window.__cosmStore;
    } else if (window.__cosmStore && Array.isArray(window.__cosmStore.catalog)) {
      catalog = window.__cosmStore.catalog;
    } else if (Array.isArray(window.kmCat)) {
      catalog = window.kmCat;
    } else {
      const storeRes = await invoke("kollegen_store");
      if (storeRes && Array.isArray(storeRes.catalog)) {
        catalog = storeRes.catalog;
        window.__cosmStore = storeRes.catalog;
      }
    }

    if (Array.isArray(catalog)) {
      const pkgOwned = new Set();
      catalog.forEach(it => {
        if (it && it.category === "package" && (it.owned || it.is_owned) && it.data && Array.isArray(it.data.items)) {
          it.data.items.forEach(id => pkgOwned.add(id));
        }
      });

      storeCapes = catalog
        .filter(it => it && it.category === "cape" && (it.owned || it.is_owned || pkgOwned.has(it.id)))
        .map(it => {
          let tex = "";
          if (it.data && it.data.texture) {
            tex = it.data.texture.startsWith("http") ? it.data.texture : ("https://kollegen.me/" + it.data.texture.replace(/^kollegen:/, ""));
          } else if (it.texture) {
            tex = it.texture.startsWith("http") ? it.texture : ("https://kollegen.me/" + it.texture.replace(/^kollegen:/, ""));
          }
          return {
            id: it.id,
            name: it.title || it.name || "Store Cape",
            creator: "Kollegen Store",
            category: "gekauft",
            uses: it.uses || 1,
            favorite: false,
            animated: false,
            fps: 10,
            frames: [tex],
            preview: tex,
            isStoreItem: true,
          };
        });
    }
  } catch (e) {
    console.warn("Could not load store capes:", e);
  }

  let vanillaCapes = [];
  try {
    const prof = await invoke("skin_mc_profile");
    if (prof && Array.isArray(prof.capes)) {
      vanillaCapes = prof.capes.map(vc => ({
        id: "vanilla_" + vc.id,
        vanillaId: vc.id,
        name: vc.alias || "Vanilla Cape",
        creator: "Mojang",
        category: "vanilla",
        uses: 1,
        favorite: false,
        animated: false,
        fps: 10,
        frames: [httpsify(vc.url)],
        preview: httpsify(vc.url),
        isVanilla: true,
        active: vc.state === "ACTIVE"
      }));
    }
  } catch (e) {
    console.warn("Could not load vanilla capes:", e);
  }

  let allCapes = [...capeData.capes];
  storeCapes.forEach(sc => {
    if (!allCapes.some(c => c.id === sc.id)) {
      allCapes.push(sc);
    }
  });
  vanillaCapes.forEach(vc => {
    if (!allCapes.some(c => c.id === vc.id)) {
      allCapes.push(vc);
    }
  });

  const favList = Array.isArray(capeData.favorites) ? capeData.favorites : [];
  allCapes.forEach(c => {
    if (favList.includes(c.id)) c.favorite = true;
  });

  const searchVal = ($("capesSearchInput") ? $("capesSearchInput").value.trim().toLowerCase() : "");

  grid.innerHTML = "";
  const noneCard = document.createElement("div");
  const anyActive = !!(
    capeData.active_cape ||
    vanillaCapes.some(v => v.active) ||
    storeCapes.some(s => (homeState.me && homeState.me.equipped && homeState.me.equipped.cape === s.id) || (typeof socialMe !== "undefined" && socialMe && socialMe.equipped && socialMe.equipped.cape === s.id))
  );
  noneCard.className = "cape-card cape-card-none" + (!anyActive ? " active" : "");
  noneCard.innerHTML = `
    <div class="cape-none-icon">✕</div>
    <div class="cape-card-name">No Cape</div>
  `;
  noneCard.onclick = async () => {
    try {
      await invoke("capes_set_active", { capeId: null });
      try { await invoke("kollegen_store_equip", { itemId: "", category: "cape" }); } catch (_) {}
      try { await invoke("cape_equip", { capeId: "" }); } catch (_) {}
      currentCapeUrl = null;
      toast("Cape abgelegt", "ok");
      loadCapesView();
      loadHomeSkin(true);
    } catch (e) {
      toast("Fehler: " + e, "error");
    }
  };
  grid.appendChild(noneCard);

  allCapes.forEach((c) => {
    if (activeCapeCat === "my_capes" && c.category !== "my_capes") return;
    if (activeCapeCat === "community" && c.category !== "community") return;
    if (activeCapeCat === "vanilla" && c.category !== "vanilla") return;
    if (activeCapeCat === "gekauft" && c.category !== "gekauft") return;
    if (capeFavOnly && !c.favorite) return;

    if (searchVal && !c.name.toLowerCase().includes(searchVal) && !(c.creator || "").toLowerCase().includes(searchVal)) {
      return;
    }

    const card = document.createElement("div");
    const isStoreEquipped = c.isStoreItem && (
      (homeState.me && homeState.me.equipped && homeState.me.equipped.cape === c.id) ||
      (typeof socialMe !== "undefined" && socialMe && socialMe.equipped && socialMe.equipped.cape === c.id)
    );
    const isActive = (capeData.active_cape === c.id) || isStoreEquipped || (c.isVanilla && c.active);
    card.className = "cape-card" + (isActive ? " active" : "");

    const prevWrap = document.createElement("div");
    prevWrap.className = "cape-card-preview-wrap";

    const fav = document.createElement("div");
    fav.className = "cape-fav-icon" + (c.favorite ? " active" : "");
    fav.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="${c.favorite ? "currentColor" : "none"}" stroke="currentColor" stroke-width="2"><polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2"/></svg>`;
    fav.onclick = async (e) => {
      e.stopPropagation();
      try {
        const newState = await invoke("capes_toggle_favorite", { capeId: c.id });
        c.favorite = newState;
        fav.className = "cape-fav-icon" + (newState ? " active" : "");
        const star = fav.querySelector("svg");
        if (star) star.setAttribute("fill", newState ? "currentColor" : "none");
        if (capeFavOnly && !newState) {
          loadCapesView();
        }
      } catch (_) {}
    };
    prevWrap.appendChild(fav);

    // Elytra toggle button on 3D Card
    const elytraBtn = document.createElement("button");
    elytraBtn.className = "cape-card-elytra-btn";
    elytraBtn.title = "Zwischen Cape und Elytra umschalten";
    elytraBtn.innerHTML = `<svg viewBox="0 0 24 24" width="14" height="14" fill="currentColor"><path d="M12 2C9.5 5 4 8 2 15c0 4 3 7 7 7 3 0 3-4 3-7 0 3 0 7 3 7 4 0 7-3 7-7-2-7-7.5-10-10-13zm-1 18c-3 0-5.5-2.2-5.5-5.5 0-3 3-6.5 5.5-9v14.5zm2 0V5.5c2.5 2.5 5.5 6 5.5 9 0 3.3-2.5 5.5-5.5 5.5z"/></svg>`;
    prevWrap.appendChild(elytraBtn);

    // 3D Rotating & Interactive Cape Canvas
    const canvas = document.createElement("canvas");
    canvas.width = 160;
    canvas.height = 180;
    canvas.className = "cape-card-canvas";
    prevWrap.appendChild(canvas);

    const initialCapeSrc = (c.frames && c.frames[0]) || c.preview || "";
    if (window.skinview3d && window.skinview3d.SkinViewer && initialCapeSrc) {
      ensureCapeTextureUrl(initialCapeSrc).then((validUrl) => {
        if (!validUrl) return;
        try {
          const viewer = new window.skinview3d.SkinViewer({
            canvas: canvas,
            width: 160,
            height: 180,
            cape: validUrl,
          });
          viewer.playerObject.skin.visible = false;
          viewer.playerObject.cape.visible = true;
          viewer.playerWrapper.rotation.y = Math.PI; // Face the back of cape towards user
          viewer.autoRotate = false;
          viewer.autoRotateSpeed = 2.2;
          capeViewers.push(viewer);

          let isElytra = false;
          elytraBtn.onclick = (e) => {
            e.stopPropagation();
            isElytra = !isElytra;
            elytraBtn.classList.toggle("active", isElytra);
            viewer.playerObject.backEquipment = isElytra ? "elytra" : "cape";
            if (viewer.renderPaused) {
              viewer.render();
            }
          };

          setTimeout(() => {
            try {
              viewer.renderPaused = true;
              viewer.render();
            } catch (_) {}
          }, 180);

          card.addEventListener("mouseenter", () => {
            try {
              viewer.autoRotate = true;
              viewer.renderPaused = false;
            } catch (_) {}
          });
          card.addEventListener("mouseleave", () => {
            try {
              viewer.autoRotate = false;
              viewer.renderPaused = true;
            } catch (_) {}
          });

          // Animation support in 3D
          if (c.animated && c.frames && c.frames.length > 1) {
            let fIdx = 0;
            const interval = Math.max(33, Math.round(1000 / (c.fps || 10)));
            const timer = setInterval(async () => {
              if (viewer.renderPaused) return;
              fIdx = (fIdx + 1) % c.frames.length;
              const nextUrl = await ensureCapeTextureUrl(c.frames[fIdx]);
              if (nextUrl) {
                try { viewer.loadCape(nextUrl); } catch (_) {}
              }
            }, interval);
            capeAnimTimers.push(timer);
          }
        } catch (e) {
          console.warn("Cape 3D viewer failed:", e);
        }
      });
    } else {
      const img = document.createElement("img");
      img.src = initialCapeSrc;
      img.alt = c.name;
      prevWrap.appendChild(img);
    }

    const name = document.createElement("div");
    name.className = "cape-card-name";
    name.textContent = c.name;

    const uses = document.createElement("div");
    uses.className = "cape-card-uses";
    const usesStr = (c.uses || 0).toLocaleString("de-DE");
    uses.innerHTML = `<span>⤓</span> <span>${usesStr} uses</span>`;

    card.appendChild(prevWrap);
    card.appendChild(name);
    card.appendChild(uses);

    if (c.category === "gekauft" || c.isStoreItem) {
      const badge = document.createElement("span");
      badge.className = "cape-badge-tag";
      badge.textContent = "Gekauft";
      card.appendChild(badge);
    } else if (c.category === "vanilla" || c.isVanilla) {
      const badge = document.createElement("span");
      badge.className = "cape-badge-tag";
      badge.textContent = "Vanilla";
      card.appendChild(badge);
    }

    card.onclick = async () => {
      try {
        if (c.isStoreItem) {
          await invoke("kollegen_store_equip", { itemId: c.id, category: "cape" });
          currentCapeUrl = c.preview || (c.frames && c.frames[0]) || null;
          toast(`Store-Cape "${c.name}" ausgerüstet!`, "ok");
        } else if (c.isVanilla) {
          await invoke("cape_equip", { capeId: c.vanillaId });
          currentCapeUrl = c.preview || (c.frames && c.frames[0]) || null;
          toast(`Vanilla-Cape "${c.name}" ausgerüstet!`, "ok");
        } else {
          await invoke("capes_set_active", { capeId: c.id });
          currentCapeUrl = c.preview || (c.frames && c.frames[0]) || null;
          toast(`Cape "${c.name}" ausgerüstet!`, "ok");
        }
        loadCapesView();
        loadHomeSkin(true);
      } catch (err) {
        toast("Cape ausrüsten fehlgeschlagen: " + err, "error");
      }
    };

    grid.appendChild(card);
  });
}

document.querySelectorAll(".cape-cat-tab").forEach(tab => {
  if (tab.id === "capeFavToggleBtn") return;
  tab.onclick = () => {
    document.querySelectorAll(".cape-cat-tab").forEach(t => {
      if (t.id !== "capeFavToggleBtn") t.classList.remove("active");
    });
    tab.classList.add("active");
    activeCapeCat = tab.dataset.cat || "all";
    loadCapesView();
  };
});

if ($("capesSearchInput")) $("capesSearchInput").oninput = () => loadCapesView();
if ($("capeFavToggleBtn")) {
  $("capeFavToggleBtn").onclick = (e) => {
    e.stopPropagation();
    capeFavOnly = !capeFavOnly;
    $("capeFavToggleBtn").classList.toggle("active", capeFavOnly);
    const starIco = $("capeFavStarIco");
    if (starIco) starIco.setAttribute("fill", capeFavOnly ? "currentColor" : "none");
    loadCapesView();
  };
}

let uploadedCapeFrames = [];
let animPreviewTimer = null;

function openCapeUploadModal() {
  const modal = $("capeUploadModal");
  if (!modal) return;
  uploadedCapeFrames = [];
  if ($("capeNameField")) $("capeNameField").value = "";
  if ($("capeFilesPicker")) $("capeFilesPicker").value = "";
  if ($("capeFrameManager")) $("capeFrameManager").style.display = "none";
  if ($("capeUpload3dWrap")) $("capeUpload3dWrap").style.display = "none";
  disposeUploadCapeViewer();
  modal.removeAttribute("hidden");
  modal.style.display = "flex";
}

function closeCapeUploadModal() {
  const modal = $("capeUploadModal");
  if (modal) {
    modal.setAttribute("hidden", "");
    modal.style.display = "none";
  }
  if (animPreviewTimer) clearInterval(animPreviewTimer);
  disposeUploadCapeViewer();
}

if ($("capeUploadOpenBtn")) $("capeUploadOpenBtn").onclick = openCapeUploadModal;
if ($("capeUploadClose")) $("capeUploadClose").onclick = closeCapeUploadModal;
if ($("capeUploadCancelBtn")) $("capeUploadCancelBtn").onclick = closeCapeUploadModal;

if ($("capeFpsSlider")) {
  $("capeFpsSlider").oninput = (e) => {
    const val = e.target.value;
    if ($("capeFpsVal")) $("capeFpsVal").textContent = val + " FPS";
    restartAnimPreview();
  };
}

if ($("capeTemplateBtn")) {
  $("capeTemplateBtn").onclick = () => {
    const canvas = document.createElement("canvas");
    canvas.width = 64;
    canvas.height = 32;
    const ctx = canvas.getContext("2d");
    ctx.fillStyle = "#1e3a6a";
    ctx.fillRect(0, 0, 64, 32);
    ctx.fillStyle = "#00d4ff";
    ctx.fillRect(1, 1, 10, 16);
    const a = document.createElement("a");
    a.href = canvas.toDataURL("image/png");
    a.download = "kollegen-cape-template-64x32.png";
    a.click();
    toast("Cape-Template heruntergeladen!", "ok");
  };
}

let rawUploadedCapeImages = [];
let uploadIsElytra = false;

const uploadElytraToggle = $("capeUploadElytraToggle");
if (uploadElytraToggle) {
  uploadElytraToggle.onclick = () => {
    uploadIsElytra = !uploadIsElytra;
    uploadElytraToggle.classList.toggle("active", uploadIsElytra);
    const label = $("capeUploadElytraLabel");
    if (label) label.textContent = uploadIsElytra ? "Cape-Ansicht" : "Elytra-Ansicht";
    if (uploadCapeViewer && uploadCapeViewer.playerObject) {
      uploadCapeViewer.playerObject.backEquipment = uploadIsElytra ? "elytra" : "cape";
    }
  };
}

const elytraModeSelect = $("capeElytraModeSelect");
if (elytraModeSelect) {
  elytraModeSelect.onchange = async () => {
    if (!rawUploadedCapeImages.length) return;
    const mode = elytraModeSelect.value || "normal";
    const formatted = await Promise.all(rawUploadedCapeImages.map(raw => ensureCapeTextureUrl(raw, mode)));
    uploadedCapeFrames = formatted.filter(Boolean);
    renderFrameManager();
  };
}

if ($("capeFilesPicker")) {
  $("capeFilesPicker").onchange = async (e) => {
    const files = Array.from(e.target.files || []);
    if (!files.length) return;
    uploadedCapeFrames = [];

    const readPromises = files.map(f => new Promise((resolve) => {
      const r = new FileReader();
      r.onload = () => resolve(r.result);
      r.readAsDataURL(f);
    }));

    const rawResults = await Promise.all(readPromises);
    rawUploadedCapeImages = rawResults;
    const mode = $("capeElytraModeSelect") ? $("capeElytraModeSelect").value : "normal";
    // Convert normal images or templates into proper HD Minecraft Cape & Elytra UV layout
    const formatted = await Promise.all(rawResults.map(raw => ensureCapeTextureUrl(raw, mode)));
    uploadedCapeFrames = formatted.filter(Boolean);
    renderFrameManager();
  };
}

function renderFrameManager() {
  const mgr = $("capeFrameManager");
  const list = $("capeFramesList");
  const count = $("capeFrameCount");
  const wrap3d = $("capeUpload3dWrap");
  const canvas3d = $("capeUpload3dCanvas");
  if (!mgr || !list) return;

  if (uploadedCapeFrames.length > 0) {
    mgr.style.display = "block";
    if (wrap3d) wrap3d.style.display = "flex";
    if (count) count.textContent = uploadedCapeFrames.length;
    list.innerHTML = "";

    // 3D Rotating Preview in Upload Modal
    if (canvas3d && window.skinview3d && window.skinview3d.SkinViewer) {
      if (!uploadCapeViewer || uploadCapeViewer.disposed) {
        uploadCapeViewer = new window.skinview3d.SkinViewer({
          canvas: canvas3d,
          width: 160,
          height: 200,
        });
        uploadCapeViewer.playerObject.skin.visible = false;
        uploadCapeViewer.playerObject.cape.visible = true;
        uploadCapeViewer.playerWrapper.rotation.y = Math.PI; // Face the back of cape towards user
        uploadCapeViewer.autoRotate = true; // Auto rotates!
        uploadCapeViewer.autoRotateSpeed = 2.2;
      }
      uploadCapeViewer.loadCape(uploadedCapeFrames[0]);
      uploadCapeViewer.autoRotate = true;
      uploadCapeViewer.renderPaused = false;
    }

    uploadedCapeFrames.forEach((frame, idx) => {
      const row = document.createElement("div");
      row.className = "cape-frame-item";
      row.innerHTML = `
        <div style="display:flex; align-items:center; gap:10px;">
          <img src="${frame}" alt="Frame ${idx + 1}" />
          <span style="font-size:12px; font-weight:700;">Frame ${idx + 1}</span>
        </div>
        <div style="display:flex; gap:6px;">
          <button class="frame-btn btn-up" ${idx === 0 ? "disabled" : ""}>↑</button>
          <button class="frame-btn btn-down" ${idx === uploadedCapeFrames.length - 1 ? "disabled" : ""}>↓</button>
          <button class="frame-btn btn-del" style="color:#ff5577;">✕</button>
        </div>
      `;

      row.querySelector(".btn-up").onclick = () => {
        if (idx > 0) {
          const tmp = uploadedCapeFrames[idx];
          uploadedCapeFrames[idx] = uploadedCapeFrames[idx - 1];
          uploadedCapeFrames[idx - 1] = tmp;
          renderFrameManager();
        }
      };

      row.querySelector(".btn-down").onclick = () => {
        if (idx < uploadedCapeFrames.length - 1) {
          const tmp = uploadedCapeFrames[idx];
          uploadedCapeFrames[idx] = uploadedCapeFrames[idx + 1];
          uploadedCapeFrames[idx + 1] = tmp;
          renderFrameManager();
        }
      };

      row.querySelector(".btn-del").onclick = () => {
        uploadedCapeFrames.splice(idx, 1);
        renderFrameManager();
      };

      list.appendChild(row);
    });

    restartAnimPreview();
  } else {
    mgr.style.display = "none";
    if (wrap3d) wrap3d.style.display = "none";
    disposeUploadCapeViewer();
  }
}

function restartAnimPreview() {
  if (animPreviewTimer) clearInterval(animPreviewTimer);
  const canvas = $("capeAnimPreviewCanvas");
  if (!canvas || !uploadedCapeFrames.length) return;
  const ctx = canvas.getContext("2d");
  const fps = $("capeFpsSlider") ? parseInt($("capeFpsSlider").value, 10) || 10 : 10;
  let idx = 0;

  function drawCurrent() {
    const frame = uploadedCapeFrames[idx];
    if (uploadCapeViewer && !uploadCapeViewer.disposed) {
      try { uploadCapeViewer.loadCape(frame); } catch (_) {}
    }
    const img = new Image();
    img.onload = () => {
      ctx.clearRect(0, 0, canvas.width, canvas.height);
      ctx.imageSmoothingEnabled = false;
      ctx.drawImage(img, 0, 0, canvas.width, canvas.height);
    };
    img.src = frame;
  }

  drawCurrent();
  if (uploadedCapeFrames.length > 1) {
    animPreviewTimer = setInterval(() => {
      idx = (idx + 1) % uploadedCapeFrames.length;
      drawCurrent();
    }, Math.max(33, Math.round(1000 / fps)));
  }
}

if ($("capeUploadSubmitBtn")) {
  $("capeUploadSubmitBtn").onclick = async () => {
    const name = ($("capeNameField") ? $("capeNameField").value.trim() : "") || "Mein Umhang";
    if (!uploadedCapeFrames.length) {
      toast("Bitte wähle mindestens ein Bild für dein Cape aus", "error");
      return;
    }
    const fps = $("capeFpsSlider") ? parseInt($("capeFpsSlider").value, 10) || 10 : 10;
    const mode = $("capeElytraModeSelect") ? $("capeElytraModeSelect").value : "normal";
    try {
      await invoke("capes_upload", { name, images: uploadedCapeFrames, fps, elytra_mode: mode });
      toast(`Cape "${name}" erfolgreich hochgeladen & ausgerüstet!`, "ok");
      closeCapeUploadModal();
      loadCapesView();
      loadHomeSkin(true);
    } catch (err) {
      toast("Cape-Upload fehlgeschlagen: " + err, "error");
    }
  };
}
  const _socialsClose = $("socialsClose");
  if (_socialsClose) _socialsClose.onclick = () => switchTab("home");

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
  resetManageSearch();
  manageLoadInstalled();
}

function showManageOverview() {
  $("manageOverview").style.display = "block";
  $("manageBrowse").style.display = "none";
}

function showManageBrowse() {
  $("manageOverview").style.display = "none";
  $("manageBrowse").style.display = "block";
  manageSearch(true);
}

function openManage(inst) {
  manageInst = inst;
  $("manageTitle").textContent = `Verwalten: ${inst.name}`;
  $("manageQuery").value = "";
  showManageOverview();
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
$("manageBrowseBtn").onclick = () => showManageBrowse();
$("browseBack").onclick = () => {
  closeVersionMenu();
  closeChangeMenu();
  showManageOverview();
  manageLoadInstalled();
};
$("manageIconBtn").onclick = () => {
  if (manageInst) changeInstanceImage(manageInst, "icon");
};
$("manageBannerBtn").onclick = () => {
  if (manageInst) changeInstanceImage(manageInst, "banner");
};
$("manageImagesResetBtn").onclick = async () => {
  if (!manageInst) return;
  await resetInstanceImage(manageInst, "icon");
  await resetInstanceImage(manageInst, "banner");
  toast("Bilder zurückgesetzt", "ok");
};
$("manageImportBtn").onclick = async () => {
  const open = window.__TAURI__.dialog?.open || window.__TAURI__.pluginDialog?.open;
  if (!open) {
    alert("Datei-Dialog nicht verfügbar.");
    return;
  }
  const filters = {
    mod: [{ name: "Mod (.jar)", extensions: ["jar"] }],
    resourcepack: [{ name: "Resource Pack (.zip)", extensions: ["zip"] }],
    shader: [{ name: "Shader Pack (.zip)", extensions: ["zip"] }],
  };
  let path;
  try {
    path = await open({
      title: "Inhalt importieren",
      multiple: false,
      filters: filters[manageKind] || [{ name: "Datei", extensions: ["jar", "zip"] }],
    });
  } catch (e) {
    alert("Fehler: " + e);
    return;
  }
  if (!path) return;
  try {
    await invoke("import_content_file", {
      instanceName: manageInst.name,
      kind: manageKind,
      path,
    });
    manageLoadInstalled();
  } catch (e) {
    alert("Import fehlgeschlagen: " + e);
  }
};

$("manageCloneBtn").onclick = async () => {
  const open = window.__TAURI__.dialog?.open || window.__TAURI__.pluginDialog?.open;
  if (!open) {
    alert("Datei-Dialog nicht verfügbar.");
    return;
  }
  // Get available versions
  let versions = [];
  try {
    const response = await invoke("get_available_versions");
    versions = response || [];
  } catch (e) {
    console.warn("Could not fetch versions:", e);
  }
  
  const cloneDialog = document.createElement("div");
  cloneDialog.className = "clone-dialog";
  cloneDialog.innerHTML = `
    <h3>Instanz klonen</h3>
    <div style="margin-top: 1rem;">
      <label>Neuer Instanz-Name:</label>
      <input type="text" id="cloneName" value="${manageInst.name} (Kopie)" placeholder="Neuer Instanz-Name" style="width: 100%; padding: 0.5rem; margin-top: 0.5rem;" />
    </div>
    <div style="margin-top: 1rem;">
      <label>Ziel-Version:</label>
      <select id="cloneVersion" style="width: 100%; padding: 0.5rem; margin-top: 0.5rem;">
        ${versions.map(v => `<option value="${v}" ${v === manageInst.version ? 'selected' : ''}>${v}</option>`).join('')}
      </select>
    </div>
    <div style="margin-top: 1rem;">
      <label>Mod-Loader:</label>
      <select id="cloneLoader" style="width: 100%; padding: 0.5rem; margin-top: 0.5rem;">
        <option value="fabric" ${manageInst.loader === 'fabric' ? 'selected' : ''}>Fabric</option>
        <option value="forge" ${manageInst.loader === 'forge' ? 'selected' : ''}>Forge</option>
        <option value="neoforge" ${manageInst.loader === 'neoforge' ? 'selected' : ''}>NeoForge</option>
        <option value="quilt" ${manageInst.loader === 'quilt' ? 'selected' : ''}>Quilt</option>
        <option value="vanilla" ${manageInst.loader === 'vanilla' ? 'selected' : ''}>Vanilla</option>
      </select>
    </div>
    <div style="margin-top: 1.5rem; display: flex; gap: 0.5rem; justify-content: flex-end;">
      <button id="cloneCancel" class="btn-secondary">Abbrechen</button>
      <button id="cloneConfirm" class="btn-primary">Klonen</button>
    </div>
  `;
  
  const cloneModal = document.createElement("div");
  cloneModal.className = "modal";
  cloneModal.style.display = "flex";
  const modalContent = document.createElement("div");
  modalContent.className = "modal-content";
  modalContent.appendChild(cloneDialog);
  cloneModal.appendChild(modalContent);
  document.body.appendChild(cloneModal);
  
  return new Promise((resolve) => {
    const cancelBtn = cloneDialog.querySelector("#cloneCancel");
    const confirmBtn = cloneDialog.querySelector("#cloneConfirm");
    const nameInput = cloneDialog.querySelector("#cloneName");
    const versionSelect = cloneDialog.querySelector("#cloneVersion");
    const loaderSelect = cloneDialog.querySelector("#cloneLoader");
    
    const cleanup = () => {
      document.body.removeChild(cloneModal);
      resolve(null);
    };
    
    cancelBtn.onclick = cleanup;
    cloneModal.onclick = (e) => { if (e.target === cloneModal) cleanup(); };
    
    confirmBtn.onclick = async () => {
      const name = nameInput.value.trim();
      const version = versionSelect.value;
      const loader = loaderSelect.value;
      
      if (!name) {
        alert("Bitte einen Namen eingeben.");
        return;
      }
      
      confirmBtn.disabled = true;
      confirmBtn.textContent = "Wird geklont...";
      toast(`Instanz "${manageInst.name}" wird geklont und Mods angepasst...`, "info");

      try {
        const newInst = await invoke("clone_instance_v2", {
          sourceName: manageInst.name,
          newName: name,
          newVersion: version,
          newLoader: loader,
          newLoaderVersion: null,
        });
        toast(`Instanz "${name}" erfolgreich geklont!`, "ok");
        manageLoadInstalled();
        resolve(newInst);
      } catch (e) {
        alert("Klonen fehlgeschlagen: " + e);
      }
      cleanup();
    };
    
    nameInput.focus();
  });
};

function renderCard(p) {
  const card = document.createElement("div");
  card.className = "card";
  card.dataset.pid = p.id;
  const installed = currentInstalled.has(p.id);
  const iconWrap = document.createElement("div");
  iconWrap.className = "card-icon-wrap";
  if (p.icon_url) {
    const img = document.createElement("img");
    img.src = p.icon_url;
    img.className = "card-icon";
    img.loading = "lazy";
    img.decoding = "async";
    img.onerror = () => img.remove();
    iconWrap.append(img);
  } else {
    const icon = document.createElement("div");
    icon.className = "card-icon placeholder";
    icon.textContent = (p.title || "?").trim().charAt(0).toUpperCase();
    iconWrap.append(icon);
  }
  if (installed) {
    const badge = document.createElement("div");
    badge.className = "installed-badge";
    badge.textContent = "✓";
    badge.title = "Installiert";
    iconWrap.append(badge);
  }
  card.append(iconWrap);
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
  if (installed) {
    btn.textContent = "Installiert";
    btn.disabled = true;
    btn.classList.add("installed-btn");
  } else {
    btn.textContent = "Installieren";
    btn.onclick = () => manageInstall(p.id, p.title, null);
  }
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
const MANAGE_PAGE = 12;
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
    manageAllHits.push(p);
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
loadHomeSkin(false);
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
  window.kmTitleOf = (u) => {
    const t = u && u.equipped && u.equipped.title;
    if (!t) return null;
    if (t.data) return t;
    return byId(t.id) || null;
  };
  window.kmBadge = (u) => {
    const b = u && u.equipped && u.equipped.badge;
    if (!b) return null;
    if (b.data) return b;
    return byId(b.id) || null;
  };
  window.kmPref = (u) => {
    const eq = (u && u.equipped) || {};
    const val = (k) => {
      const e = eq[k];
      if (!e) return null;
      if (e.data) return { id: e.id, name: e.name, data: e.data };
      const it = byId(e && e.id ? e.id : e);
      return it ? { id: it.id, name: it.name, data: it.data } : null;
    };
    const nc = val("name_color");
    const sk = val("sticker");
    const fn = val("font");
    return {
      title: val("title"),
      badge: val("badge"),
      accent: (nc && nc.data && nc.data.accent) || null,
      sticker: sk && sk.data ? sk.data : null,
      font: (fn && fn.data && fn.data.font) || null,
    };
  };
  window.themeOf = (u) => {
    const eq = (u && u.equipped) || {};
    const val = (k) => {
      const e = eq[k];
      if (!e) return null;
      if (e && e.data) return { id: e.id, name: e.name, data: e.data };
      if (e && e.id) return byId(e.id);
      if (typeof e === "string") return byId(e);
      return null;
    };
    const nc = val("name_color");
    const stil = val("profil_stil");
    const aTheme = val("avatar_theme");
    const pBg = val("profile_bg");
    return {
      title: (val("title") || {}).data || null,
      badge: (val("badge") || {}).data || null,
      sticker: (val("sticker") || {}).data || null,
      font: (val("font") || {}).data || null,
      banner: (val("banner") || {}).data || null,
      aFrame: (val("avatar_frame") || {}).data || null,
      aTheme: aTheme && aTheme.data ? aTheme.data : null,
      pBg: pBg && pBg.data ? pBg.data : null,
      pFrame: (val("profile_frame") || {}).data || null,
      accent: (nc && nc.data && nc.data.accent) || (stil && stil.data && stil.data.accent) || null,
      nameData: (nc && nc.data) || null,
      stilData: (stil && stil.data) || null,
    };
  };
  window.kmCat = null;

  let kmStoreError = "";
  async function loadStore(force) {
    if (force) {
      kmCat = null;
      window.kmCat = null;
      kmState = null;
      kmStoreError = "";
    }
    if (!kmCat) {
      try {
        const d = await invoke("kollegen_store");
        if (d && Array.isArray(d.catalog)) {
          kmCat = d.catalog;
          window.kmCat = kmCat;
          kmState = d;
          window.kmState = d;
          kmStoreError = "";
        } else if (d && d.error) {
          kmStoreError = d.error === "not_authenticated"
            ? "Bitte mit Discord verbinden, um den Store zu laden."
            : "Store konnte nicht geladen werden: " + d.error;
        }
      } catch (e) {
        kmStoreError = "Store konnte nicht geladen werden: " + e;
      }
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
  const ORDER = ["title", "badge", "avatar_theme", "avatar_frame", "profile_bg", "profile_frame", "banner", "sticker", "name_color", "font", "profil_stil"];
  const CATNAMES = { title: "Titel", badge: "Abzeichen", avatar_theme: "Avatar-Hintergrund", avatar_frame: "Avatar-Rahmen", profile_bg: "Profil-Hintergrund", profile_frame: "Profil-Rahmen", banner: "Banner", sticker: "Aufkleber", name_color: "Namensfarbe", font: "Schriftart", profil_stil: "Profilstil" };

  function renderKosmet() {
    const groups = $("kosmetGroups");
    const save = $("kosmetPreview");
    const pts = $("kosmetPts");
    const hint = $("kosmetHint");
    if (!groups || !save || !pts || !hint) return;
    if (!kmCat) {
      groups.innerHTML = "";
      save.innerHTML = "";
      pts.textContent = "";
      hint.textContent = kmStoreError || "Kosmetik wird geladen…";
      return;
    }
    const st = kmState || {};
    const eq = st.equipped || {};
    groups.innerHTML = "";
    save.innerHTML = "";
    const eqId = (cat) => { const v = eq[cat]; return typeof v === "string" ? v : (v && v.id ? v.id : ""); };
    const eqVal = (cat) => { const id = eqId(cat); return id ? byId(id) : null; };
    const ti = eqVal("title");
    const bd = eqVal("badge");
    const nc = eqVal("name_color");
    const sk = eqVal("sticker");
    const fn = eqVal("font");
    const bn = eqVal("banner");
    const aFrame = eqVal("avatar_frame");
    const aTheme = eqVal("avatar_theme");
    const stil = eqVal("profil_stil");
    const nm = (kmMe && (kmMe.mc_name || kmMe.global_name || kmMe.username)) || "Du";
    const accent = (nc && nc.data && nc.data.accent) || (stil && stil.data && stil.data.accent) || "#f1c40f";
    const font = (fn && fn.data && fn.data.font) || "";
    const av = document.createElement("img");
    const au = previewAvatar();
    if (au) av.src = au; else av.style.display = "none";
    let avStyle = "border-radius:12px;width:56px;height:56px;object-fit:cover;";
    if (aFrame && aFrame.data && aFrame.data.color1) avStyle += `border:${(aFrame.data.width || 3)}px solid ${esc(aFrame.data.color1)};`;
    if (aTheme && aTheme.data && aTheme.data.gradient) avStyle += `background:${esc(aTheme.data.gradient)};`;
    av.setAttribute("style", avStyle);
    let nameHtml = esc(nm);
    if (ti && ti.data && ti.data.text) nameHtml = `${esc(ti.data.text)} \u00b7 ${nameHtml}`;
    if (bd && bd.data) nameHtml = `<span style="color:${esc(bd.data.color)}">${esc(bd.data.icon)}</span> ${nameHtml}`;
    if (sk && sk.data && sk.data.icon) nameHtml += ` <span style="color:${esc(sk.data.color)}">${esc(sk.data.icon)}</span>`;
    let ph = "";
    if (bn && bn.data && bn.data.gradient) ph += `<div style="height:52px;border-radius:10px;margin-bottom:0.6rem;background:${esc(bn.data.gradient)};border:1px solid rgba(255,255,255,0.08);"></div>`;
    const nmStyle = `color:${esc(accent)};font-weight:800;${font ? `font-family:${esc(font)};` : ""}`;
    save.innerHTML = ph + `<div style="display:flex;gap:0.7rem;align-items:center;"><span class="kp-avslot"></span><div style="flex:1;min-width:0;"><div class="kp-name" style="${nmStyle}">${nameHtml}</div><div class="kp-sub">Level ${esc(st.level || 1)}</div></div></div>`;
    const avSlot = save.querySelector(".kp-avslot");
    if (avSlot) avSlot.replaceWith(av); else save.append(av);
    const PREQ = [["name_color", "Namensfarbe"], ["font", "Schriftart"], ["sticker", "Aufkleber"], ["profil_stil", "Profilstil"], ["banner", "Banner"], ["profile_bg", "Hintergrund"], ["profile_frame", "Profil-Rahmen"], ["avatar_frame", "Avatar-Rahmen"]];
    let rows = "";
    for (const c of PREQ) {
      const item = eqVal(c[0]);
      if (!item || !item.data) continue;
      const d = item.data;
      const nmIt = item.name || d.text || "";
      if (!nmIt) continue;
      let band = "", ic = "", icS = "";
      if (d.gradient) band = `background:${esc(d.gradient)};`;
      else if (d.color1) band = `background:linear-gradient(135deg,${esc(d.color1)},${esc(d.color2 || d.color1)});`;
      else if (d.accent) band = `background:${esc(d.accent)};`;
      else if (d.icon) { ic = esc(d.icon); icS = `color:${esc(d.color)};`; }
      else if (d.font) { ic = "Aa"; icS = `font-family:${esc(d.font)};`; }
      const top = ic ? `<div style="height:28px;background:rgba(255,255,255,0.07);display:flex;align-items:center;justify-content:center;font-size:14px;${icS}">${ic}</div>` : `<div style="height:28px;${band || "background:rgba(255,255,255,0.07);"}"></div>`;
      rows += `<div style="border-radius:8px;overflow:hidden;border:1px solid rgba(255,255,255,0.06);">${top}<div style="background:rgba(255,255,255,0.03);color:#8b95a5;font-size:0.58rem;padding:0.25rem 0.4rem;text-transform:uppercase;letter-spacing:0.04em;">${esc(CATNAMES[c[0]] || c[1])}</div></div>`;
    }
    if (rows) save.insertAdjacentHTML("beforeend", `<div style="display:grid;grid-template-columns:repeat(3,1fr);gap:0.4rem;margin-top:0.55rem;">${rows}</div>`);
    const kv = $("kosmetViewBtn");
    if (kv && !kv.dataset.bound) {
      kv.dataset.bound = "1";
      kv.onclick = async () => {
        try { if (kmMe && kmMe.code && window.kmShowProfile) window.kmShowProfile(kmMe.code); } catch (e) {}
      };
    }
    if (typeof st.points === "number") pts.textContent = "\u2605 " + st.points + " Kollegen-Points";
    else pts.textContent = "Nicht angemeldet – keine Kollegen-Points sichtbar.";

    const owned = kmCat.filter((it) => it.owned);
    let collapsed = {};
    try { collapsed = JSON.parse(localStorage.getItem("kosmetCollapsed") || "{}"); } catch (e) { collapsed = {}; }
    const isCollapsed = (cat) => collapsed[cat] !== false;
    const setCollapsed = (cat, v) => {
      collapsed[cat] = v;
      try { localStorage.setItem("kosmetCollapsed", JSON.stringify(collapsed)); } catch (e) {}
    };
    let anyGroup = false;
    for (const cat of ORDER) {
      const mine = owned.filter((it) => it.category === cat);
      if (!mine.length) continue;
      anyGroup = true;
      const g = document.createElement("div");
      g.className = "kosmet-group" + (isCollapsed(cat) ? " collapsed" : "");
      g.innerHTML = `<div class="kg-title" role="button" tabindex="0">${CATNAMES[cat] || cat}<span class="kg-count">${mine.length}</span></div><div class="kosmet-chips"></div>`;
      const title = g.querySelector(".kg-title");
      const toggle = () => {
        const c = g.classList.toggle("collapsed");
        setCollapsed(cat, c);
      };
      title.onclick = toggle;
      title.onkeydown = (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggle(); } };
      const chips = g.querySelector(".kosmet-chips");
      const none = document.createElement("span");
      none.className = "kosmet-chip" + (eq[cat] ? "" : " eq");
      none.textContent = "Keins";
      none.onclick = () => doEquip(cat, "");
      chips.append(none);
      for (const it of mine) {
        const ch = document.createElement("span");
        ch.className = "kosmet-chip" + (eqId(cat) === it.id ? " eq" : "");
        const sw = document.createElement("span");
        sw.className = "sw";
        if (it.data && it.data.color1) sw.style.background = it.data.color1;
        else if (it.data && it.data.gradient) sw.style.background = it.data.gradient;
        else if (it.data && it.data.accent) sw.style.background = it.data.accent;
        else if (it.data && it.data.text) { sw.textContent = it.data.text; sw.style.background = "#3a3f4e"; }
        else if (it.data && it.data.icon) { sw.textContent = it.data.icon; sw.style.color = it.data.color; }
        else if (it.data && it.data.font) { sw.textContent = "Aa"; sw.style.background = "#3a3f4e"; }
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

  const STORE_RAR = { common: ["Gewöhnlich", "r-common"], rare: ["Selten", "r-rare"], epic: ["Episch", "r-epic"], legendary: ["Legendär", "r-legendary"] };
  const STORE_RANK = { common: 0, rare: 1, epic: 2, legendary: 3 };

  let storeFilter = "alle";
  let storeGroup = "profil";
  const STORE_INGAME_CATS = ["pet", "clothing", "effect", "cape", "shield", "back_accessory", "head_accessory", "face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves", "pet_skin"];
  let storeRar = "alle";
  let storeSort = "featured";
  let storeQuery = "";
  const STORE_CHUNK = 40;
  let storeList = [];
  let storeShown = 0;
  let storeMoreObs = null;

  function storePreview(item) {
    const av = previewAvatar() || "https://mc-heads.net/avatar/MHF_Steve/64";
    if (item.category === "title") return `<span style="font-weight:800;font-size:1rem;">${esc(item.data.text)}</span>`;
    if (item.category === "badge") return `<span style="font-size:30px;color:${esc(item.data.color)}">${esc(item.data.icon)}</span>`;
    if (item.category === "avatar_theme") return `<div style="width:100%;height:100%;display:flex;align-items:center;justify-content:center;background:${esc(item.data.gradient)}"><img style="width:64px;height:64px;border-radius:14px;object-fit:cover;" src="${av}"/></div>`;
    if (item.category === "banner") return `<div style="width:88%;height:58px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#0a0d13;font-weight:800;background:${esc(item.data.gradient)}">Banner</div>`;
    if (item.category === "profile_bg") return `<div style="width:88%;height:78px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#c9d4e3;font-weight:700;font-size:11px;background:${esc(item.data.gradient)}"><img style="width:34px;height:34px;border-radius:8px;" src="${av}"/></div>`;
    if (item.category === "profile_frame") return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;border:4px solid ${esc(item.data.color1)};box-shadow:0 0 12px ${esc(item.data.color1)}44;background:#0d1420;"><img style="width:40px;height:40px;border-radius:8px;" src="${av}"/></div>`;
    if (item.category === "profil_stil") return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;border:2px solid ${esc(item.data.accent)};background:#0d1420;"><span style="color:${esc(item.data.accent)};font-weight:800;font-size:22px;">Aa</span> <span style="color:#c9d4e3;font-size:11px;">${esc(item.data.font)}</span></div>`;
    if (item.category === "font") return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:#c9d4e3;font-weight:700;font-size:15px;font-family:${esc(item.data.font)};">Aa</div>`;
    if (item.category === "name_color") return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:${esc(item.data.accent)};font-weight:800;font-size:22px;">Aa</div>`;
    if (item.category === "sticker") return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;color:${esc(item.data.color)};font-size:34px;">${esc(item.data.icon)}</div>`;
    if (item.category === "cape" && item.data && item.data.texture) return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#0d1420;background-image:url('https://kollegen.me/${item.data.texture.replace("kollegen:", "")}');background-size:cover;background-position:center;">Cape</div>`;
    if (item.category === "shield" && item.data && item.data.texture) return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#0d1420;background-image:url('https://kollegen.me/${item.data.texture.replace("kollegen:", "")}');background-size:cover;background-position:center;">Schild</div>`;
    if (item.category === "back_accessory" && item.data && item.data.texture) return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#0d1420;background-image:url('https://kollegen.me/${item.data.texture.replace("kollegen:", "")}');background-size:cover;background-position:center;">Rücken</div>`;
    if (item.category === "head_accessory" && item.data && item.data.texture) return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#0d1420;background-image:url('https://kollegen.me/${item.data.texture.replace("kollegen:", "")}');background-size:cover;background-position:center;">Kopf</div>`;
    if (["face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves"].includes(item.category) && item.data && item.data.texture) { const lbl = { face_accessory: "Gesicht", aura: "Aura", shoulder_accessory: "Schulter", torso_accessory: "Oberkörper", legs_accessory: "Unterkörper", gloves: "Handschuhe" }[item.category] || "Item"; return `<div style="width:78%;height:64px;border-radius:10px;display:flex;align-items:center;justify-content:center;background:#0d1420;background-image:url('https://kollegen.me/${item.data.texture.replace("kollegen:", "")}');background-size:cover;background-position:center;">${lbl}</div>`; }
    if (item.category === "pet" && item.data && item.data.kind) { const mk = { ember_drake: 1, void_wyrm: 1, shadow_reaper: 1, rune_sentinel: 1, flame_skull: 1, storm_cloud: 1, crystal_golem: 1, spectre: 1, lantern_spirit: 1, bolt_bot: 1, mini_ufo: 1, grimoire: 1, mooncap: 1, jelly_cube: 1 }[item.data.kind]; if (mk) return `<img style="width:64px;height:64px;border-radius:14px;object-fit:cover;" src="pets/${item.data.kind}.png" onerror="this.style.display='none'"/>`; }
    let border = "", shadow = "";
    if (item.category === "avatar_frame" && item.data.color1) border = `border:${(item.data.width || 3)}px solid ${esc(item.data.color1)};`;
    return `<img style="width:64px;height:64px;border-radius:14px;object-fit:cover;${border}${shadow}" src="${av}"/>`;
  }

  function storeFiltered() {
    const ingame = (c) => STORE_INGAME_CATS.includes(c);
    const items = (kmCat || []).filter((i) => (storeGroup === "ingame" ? ingame(i.category) : !ingame(i.category)) && (storeFilter === "alle" || i.category === storeFilter) && (storeRar === "alle" || i.rarity === storeRar) && (!storeQuery || ((i.name || "") + " " + (i.desc || "")).toLowerCase().includes(storeQuery)));
    items.sort((a, b) => {
      if (storeSort === "featured") { const f = ((b.featured ? 1 : 0) - (a.featured ? 1 : 0)) || ((STORE_RANK[b.rarity] ?? -1) - (STORE_RANK[a.rarity] ?? -1)); return f || a.price - b.price; }
      if (storeSort === "price-asc") return a.price - b.price;
      if (storeSort === "price-desc") return b.price - a.price;
      if (storeSort === "rarity") { const d = (STORE_RANK[b.rarity] ?? -1) - (STORE_RANK[a.rarity] ?? -1); return d || a.price - b.price; }
      if (storeSort === "name") return (a.name || "").localeCompare(b.name || "");
      return 0;
    });
    return items;
  }

  function storeButtonFor(item) {
    const logged = !!(kmState && !kmState.needsAuth && typeof kmState.points === "number");
    const owned = logged && item.owned;
    const equipped = logged && item.equippedCategory;
    if (equipped) return { txt: "Ausgerüstet ✓", short: "Ausgerüstet", disabled: true, act: "", cls: "" };
    if (owned) return { txt: "Ausrüsten", short: "Ausrüsten", disabled: false, act: "equip", cls: "" };
    if (!logged) return { txt: "Anmelden zum Kaufen", short: "Anmelden", disabled: true, act: "", cls: "" };
    if (item.price > (kmState.points || 0)) return { txt: "Nicht genug Punkte", short: "Zu teuer", disabled: true, act: "", cls: "" };
    return { txt: `Kaufen · ${item.price} Punkte`, short: "Kaufen", disabled: false, act: "buy", cls: "buy" };
  }

  let c3dObserver = null;
  const c3dThumbCache = {};
  function c3dRenderNow(pv, item) {
    try {
      if (!window.Cosmetic3D || !window.Cosmetic3D.ready()) return;
      const id = item && item.id;
      if (!id || pv.dataset.c3dDone) return;
      if (!c3dThumbCache[id]) {
        c3dThumbCache[id] = window.Cosmetic3D.thumb(item).catch(() => null);
      }
      c3dThumbCache[id].then((url) => {
        try {
          if (!url || !pv.isConnected || pv.dataset.c3dDone) return;
          pv.dataset.c3dDone = "1";
          const img = document.createElement("img");
          img.className = "thumb3d";
          img.alt = "";
          img.src = url;
          pv.innerHTML = "";
          pv.append(img);
        } catch (e) {}
      });
    } catch (e) {}
  }
  function c3dObserve(pv, item) {
    try {
      if (!window.Cosmetic3D || !window.Cosmetic3D.ready()) return;
      if (!window.Cosmetic3D.keyFor(item) || !item.id) return;
      pv.dataset.c3dId = item.id;
      if (typeof IntersectionObserver === "undefined") { c3dRenderNow(pv, item); return; }
      if (!c3dObserver) {
        c3dObserver = new IntersectionObserver((ents) => {
          ents.forEach((en) => {
            if (!en.isIntersecting) return;
            const el = en.target;
            try { c3dObserver.unobserve(el); } catch (e) {}
            const it = byId(el.dataset.c3dId);
            if (it) c3dRenderNow(el, it);
          });
        }, { rootMargin: "300px" });
      }
      c3dObserver.observe(pv);
    } catch (e) {}
  }
  // Zustand einer Karte (Klassen, Status-Label, Fußzeile) – ohne die Vorschau neu zu bauen.
  function storeApplyState(card, item) {
    const logged = !!(kmState && !kmState.needsAuth && typeof kmState.points === "number");
    const b = storeButtonFor(item);
    card.classList.toggle("owned", !!item.owned);
    card.classList.toggle("equipped", !!item.equippedCategory);
    card.classList.toggle("cant", logged && !item.owned && item.price > (kmState.points || 0));
    if (logged && item.equippedCategory) card.dataset.state = "equipped";
    else if (logged && item.owned) card.dataset.state = "owned";
    else delete card.dataset.state;
    let foot = card.querySelector(".store-foot");
    if (!foot) {
      foot = document.createElement("div");
      foot.className = "store-foot";
      card.querySelector(".store-card-body").append(foot);
    }
    foot.innerHTML = "";
    const price = document.createElement("div");
    price.className = "store-price";
    price.innerHTML = esc(String(item.price)) + " <small>Punkte</small>";
    const btn = document.createElement("button");
    btn.className = "cardBtn " + b.cls;
    btn.tabIndex = -1;
    btn.dataset.act = b.act;
    btn.textContent = b.short;
    if (b.disabled) btn.disabled = true;
    foot.append(price, btn);
  }

  function storeCard(item) {
    const card = document.createElement("div");
    card.className = "store-card rar-" + (item.rarity || "common");
    card.tabIndex = 0;
    card.setAttribute("role", "button");
    card.dataset.id = item.id;
    card.dataset.kcKey = "shop:" + item.id;
    const rarArr = STORE_RAR[item.rarity] || ["", "r-common"];
    const pv = document.createElement("div");
    pv.className = "store-pv";
    pv.innerHTML = storePreview(item);
    card.append(pv);
    try { c3dObserve(pv, item); } catch (e) {}
    const rar = document.createElement("span");
    rar.className = "store-badge " + rarArr[1];
    rar.textContent = rarArr[0];
    card.append(rar);
    const body = document.createElement("div");
    body.className = "store-card-body";
    const t = document.createElement("div");
    t.className = "store-card-title";
    t.textContent = item.name;
    t.title = item.name || "";
    const d = document.createElement("div");
    d.className = "store-card-desc";
    d.textContent = item.desc || "";
    body.append(t, d);
    card.append(body);
    storeApplyState(card, item);
    return card;
  }

  // Bereits gerenderte Karten nur aktualisieren (Kauf/Ausrüsten) – kein Neuaufbau, kein Scroll-Sprung.
  function storeRefreshCards() {
    const grid = $("storeGridMain");
    if (!grid) return;
    grid.querySelectorAll(".store-card").forEach((card) => {
      const it = byId(card.dataset.id);
      if (it) storeApplyState(card, it);
    });
  }

  // Weitere Karten in Häppchen anhängen, damit nie hunderte Karten auf einmal gebaut werden.
  function storeRenderChunk() {
    const grid = $("storeGridMain");
    if (!grid) return;
    const old = grid.querySelector(".store-more");
    if (old) { try { storeMoreObs && storeMoreObs.unobserve(old); } catch (e) {} old.remove(); }
    const end = Math.min(storeList.length, storeShown + STORE_CHUNK);
    const frag = document.createDocumentFragment();
    for (let i = storeShown; i < end; i++) frag.append(storeCard(storeList[i]));
    storeShown = end;
    grid.append(frag);
    if (storeShown < storeList.length) {
      const sentinel = document.createElement("div");
      sentinel.className = "store-more";
      grid.append(sentinel);
      if (typeof IntersectionObserver === "undefined") { storeRenderChunk(); return; }
      if (!storeMoreObs) {
        storeMoreObs = new IntersectionObserver((ents) => {
          if (ents.some((e) => e.isIntersecting)) storeRenderChunk();
        }, { rootMargin: "700px" });
      }
      storeMoreObs.observe(sentinel);
    }
  }

  window.renderKollegenStore = renderStore;
  window.renderCosmetics = renderCosmetics;
  window.kollegenStore = {
    preview: (item) => storePreview(item),
    open: (id) => { const it = byId(id); if (it) storeOpenModal(it); }
  };
  function renderStore() {
    const grid = $("storeGridMain");
    if (!kmCat) {
      if (grid) {
        delete grid.dataset.sig;
        grid.innerHTML = "";
        const msg = document.createElement("div");
        msg.className = "store-msg";
        msg.textContent = kmStoreError || "Lade Store…";
        grid.append(msg);
        if (kmStoreError) {
          const retry = document.createElement("button");
          retry.className = "btn-secondary";
          retry.textContent = "Erneut versuchen";
          retry.onclick = async () => {
            retry.disabled = true;
            await loadStore(true);
            renderKosmet();
            renderStore();
          };
          grid.append(retry);
        }
      }
      const wallet = $("storeWalletMain");
      if (wallet) wallet.style.display = "none";
      const show = $("storeShow");
      if (show) show.style.display = "none";
      return;
    }
    const items = storeFiltered();
    try { storeRenderPackages(); } catch (e) {}
    if (grid) {
      const sig = items.map((i) => i.id).join("|");
      if (grid.dataset.sig === sig && grid.querySelector(".store-card")) {
        storeRefreshCards();
      } else {
        grid.dataset.sig = sig;
        grid.innerHTML = "";
        storeList = items;
        storeShown = 0;
        if (!items.length) grid.innerHTML = `<div class="store-msg">Keine Items in dieser Auswahl.</div>`;
        else storeRenderChunk();
      }
    }
    const cnt = $("storeCount");
    if (cnt) cnt.textContent = items.length + (items.length === 1 ? " Item" : " Items");
    const logged = kmState && !kmState.needsAuth && typeof kmState.level === "number";
    const wallet = $("storeWalletMain");
    if (wallet) {
      if (logged) {
        wallet.style.display = "flex";
        $("storePtsMain").textContent = (kmState.points || 0) + " Punkte";
        $("storeLvlMain").textContent = "Level " + kmState.level;
      } else wallet.style.display = "none";
    }
    storeRenderShow();
  }

  function storeRenderShow() {
    const show = $("storeShow");
    if (!show || !kmState || kmState.needsAuth || !kmCat) { if (show) show.style.display = "none"; return; }
    const eq = kmState.equipped || {};
    const owned = kmCat.filter((it) => it.owned).length;
    const total = kmCat.length;
    const bndata = byId(eq.banner);
    const bn = $("storeShowBanner");
    if (bn) bn.style.background = (bndata && bndata.data && bndata.data.gradient) || "linear-gradient(90deg,#1a2234,#0d1420)";
    const av = $("storeShowAv");
    if (av) {
      av.src = previewAvatar() || "https://mc-heads.net/avatar/MHF_Steve/96";
      const fr = byId(eq.avatar_frame);
      const th = byId(eq.avatar_theme);
      if (fr && fr.data && fr.data.color1) av.style.border = "3px solid " + fr.data.color1;
      else av.style.border = "0";
      if (th && th.data && th.data.gradient) av.style.background = th.data.gradient;
      else av.style.background = "";
    }
    const ti = byId(eq.title);
    const bd = byId(eq.badge);
    const nmEl = $("storeShowName");
    if (nmEl) nmEl.textContent = (ti && ti.data && ti.data.text) ? ti.data.text + " \u00b7 Dein Profil" : "Dein Profil";
    const subEl = $("storeShowSub");
    if (subEl) {
      let sub = "Level " + (kmState.level || 1);
      if (bd && bd.data) sub += " \u00b7 Abzeichen " + bd.data.icon;
      sub += " \u00b7 Code " + ((kmMe && kmMe.code) || "-");
      subEl.textContent = sub;
    }
    const pct = total ? Math.round((owned / total) * 100) : 0;
    const fill = $("storeShowFill");
    if (fill) fill.style.width = pct + "%";
    const coll = $("storeShowColl");
    if (coll) coll.textContent = owned + " von " + total + " gesammelt";
    const pctEl = $("storeShowPct");
    if (pctEl) pctEl.textContent = pct + "%";
    show.style.display = "flex";
  }

  async function storeBuy(id) {
    try {
      const r = await invoke("kollegen_store_buy", { itemId: id });
      if (r && r.ok !== false) {
        kmCat = null;
        window.kmCat = null;
        kmState = null;
        await loadStore();
        renderKosmet();
        renderStore();
        renderProfileSummary();
      } else alert("Kauf fehlgeschlagen: " + ((r && r.error) || "unbekannt"));
    } catch (e) { alert("Fehler: " + e); }
  }

  async function storeEquip(id) {
    try {
      const cat = (kmCat || []).find((it) => it.id === id);
      const r = await invoke("kollegen_store_equip", { itemId: id, category: cat ? cat.category : "" });
      if (r && (r.ok || r.equipped)) {
        kmState.equipped = r.equipped || kmState.equipped;
        if (kmCat) {
          for (const it of kmCat) it.equippedCategory = it.category && kmState.equipped && kmState.equipped[it.category] === it.id;
        }
        renderKosmet();
        renderStore();
      } else alert("Ausrüsten fehlgeschlagen: " + ((r && r.error) || "unbekannt"));
    } catch (e) { alert("Fehler: " + e); }
  }

  function storeOpenModal(item) {
    const m = $("storeItemModal");
    const body = $("storeItemBody");
    if (!m || !body) return;
    const rarArr = STORE_RAR[item.rarity] || ["", "r-common"];
    const pv = document.createElement("div");
    pv.className = "store-pv store-pv-lg";
    pv.style.setProperty("--rar-rgb", "var(--r-" + (item.rarity || "common") + "-rgb)");
    pv.innerHTML = storePreview(item);
    try {
      if (window.Cosmetic3D && window.Cosmetic3D.ready() && window.Cosmetic3D.keyFor(item)) {
        pv.innerHTML = "";
        pv.style.minHeight = "300px";
        window.Cosmetic3D.mount(pv, item);
      }
    } catch (e) {}
    const head = document.createElement("div");
    head.className = "store-mm-head";
    const rar = document.createElement("span");
    rar.className = "store-badge " + rarArr[1];
    rar.textContent = rarArr[0];
    head.append(rar);
    const t = document.createElement("div");
    t.className = "store-mm-title";
    t.textContent = item.name;
    const cat = document.createElement("div");
    cat.className = "store-mm-cat";
    cat.textContent = storeCatLabel(item.category);
    const d = document.createElement("div");
    d.className = "store-mm-desc";
    d.textContent = item.desc || "";
    const pr = document.createElement("div");
    pr.className = "store-price store-mm-price";
    pr.textContent = item.price + " Punkte";
    const btn = document.createElement("button");
    btn.className = "cardBtn store-mm-btn";
    const b = storeButtonFor(item);
    if (b.cls) btn.classList.add(b.cls);
    btn.textContent = b.txt;
    if (b.disabled) btn.disabled = true;
    if (b.act === "equip") btn.onclick = () => { storeEquip(item.id); storeCloseModal(); };
    if (b.act === "buy") btn.onclick = () => { storeBuy(item.id); storeCloseModal(); };
    body.innerHTML = "";
    body.append(pv, head, t, cat, d, pr, btn);
    m.style.display = "flex";
  }

  function storeCatLabel(c) {
    const m = { title: "Titel", avatar_frame: "Avatar-Rahmen", avatar_theme: "Avatar-Hintergrund", badge: "Abzeichen", profile_frame: "Profil-Rahmen", profile_bg: "Profil-Hintergrund", banner: "Banner", profil_stil: "Profilstil", font: "Schriftart", name_color: "Namensfarbe", sticker: "Aufkleber", pet: "Pet", pet_skin: "Pet-Skin", cape: "Umhang", shield: "Schild", back_accessory: "Rücken", head_accessory: "Kopf", face_accessory: "Gesicht", aura: "Aura", shoulder_accessory: "Schulter", torso_accessory: "Oberkörper", legs_accessory: "Unterkörper", package: "Paket" };
    return m[c] || String(c || "");
  }

  function storeOpenPackage(pkg) {
    const m = $("storeItemModal");
    const body = $("storeItemBody");
    if (!m || !body) return;
    const ids = (pkg.data && Array.isArray(pkg.data.items)) ? pkg.data.items : [];
    const items = ids.map((id) => { try { return byId(id); } catch (e) { return null; } }).filter(Boolean);
    let total = 0;
    body.innerHTML = "";
    const t = document.createElement("div");
    t.className = "store-mm-title";
    t.textContent = pkg.name || "Paket";
    body.append(t);
    const d = document.createElement("div");
    d.textContent = pkg.desc || "";
    d.style.cssText = "color:#a9b3c0;font-size:.9rem;margin-top:.4rem;";
    body.append(d);
    if (!items.length) {
      const n = document.createElement("div");
      n.className = "store-msg";
      n.textContent = "Inhalt folgt in Kürze.";
      body.append(n);
    } else {
      const list = document.createElement("div");
      list.style.cssText = "display:flex;flex-direction:column;gap:.45rem;margin-top:.8rem;";
      for (const it of items) {
        total += (it.price || 0);
        const row = document.createElement("button");
        row.style.cssText = "display:flex;align-items:center;gap:.6rem;background:var(--panel-2);border:1px solid var(--border-secondary);border-radius:10px;padding:.45rem .6rem;color:inherit;cursor:pointer;text-align:left;";
        row.innerHTML = `<span style="font-weight:700;">${esc(it.name || it.id)}</span><span style="margin-left:auto;color:var(--accent);">${it.price || 0} Punkte</span>`;
        row.onclick = () => { storeCloseModal(); setTimeout(() => storeOpenModal(it), 50); };
        list.append(row);
      }
      body.append(list);
      const tot = document.createElement("div");
      tot.className = "store-price";
      tot.style.cssText = "margin-top:.8rem;";
      tot.textContent = "Gesamt " + total + " Punkte";
      body.append(tot);
    }
    m.style.display = "flex";
  }

  function storeRenderPackages() {
    const slot = $("storePkgSlot");
    if (!slot) return;
    const pkgs = (kmCat || []).filter((i) => i.category === "package" && !i.hidden);
    const sig = pkgs.map((p) => p.id).join("|");
    if (slot.dataset.sig === sig) return;
    slot.dataset.sig = sig;
    slot.innerHTML = "";
    if (!pkgs.length) return;
    const strip = document.createElement("div");
    strip.id = "storePkgStrip";
    strip.className = "pkg-strip";
    const head = document.createElement("div");
    head.className = "pkg-head";
    head.textContent = "Pakete";
    const row = document.createElement("div");
    row.className = "pkg-row";
    for (const p of pkgs) {
      const card = document.createElement("button");
      card.className = "pkg-card rar-" + (p.rarity || "common");
      card.innerHTML = `<span class="pkg-name">${esc(p.name || "Paket")}</span><span class="pkg-price">${p.price || 0} Punkte</span>`;
      card.onclick = () => storeOpenPackage(p);
      row.append(card);
    }
    strip.append(head, row);
    slot.append(strip);
  }

  function storeCloseModal() {
    const m = $("storeItemModal");
    if (m) m.style.display = "none";
    try { if (window.Cosmetic3D) window.Cosmetic3D.unmount(); } catch (e) {}
  }

  function storeSyncChips() {
    for (const id of ["storeRarChipsMain"]) {
      const box = $(id);
      if (!box) continue;
      box.querySelectorAll(".chip").forEach((x) => {
        const on = x.getAttribute("data-r") === storeRar;
        x.classList.toggle("on", on);
        x.classList.toggle("active", on);
      });
    }
  }
  function storeSyncTabs() {
    for (const id of ["storeTabsMain"]) {
      const box = $(id);
      if (!box) continue;
      box.querySelectorAll(".tab").forEach((x) => {
        const f = x.getAttribute("data-f");
        const on = f === storeFilter;
        x.classList.toggle("on", on);
        x.classList.toggle("active", on);
        x.setAttribute("aria-selected", on ? "true" : "false");
        const show = !f || f === "alle" || (storeGroup === "ingame" ? STORE_INGAME_CATS.includes(f) : !STORE_INGAME_CATS.includes(f));
        x.style.display = show ? "" : "none";
      });
    }
    const grp = $("storeGroupMain");
    if (grp) grp.querySelectorAll(".tab").forEach((x) => {
      const on = x.getAttribute("data-g") === storeGroup;
      x.classList.toggle("on", on);
      x.classList.toggle("active", on);
      x.setAttribute("aria-selected", on ? "true" : "false");
    });
  }
  function storeInit() {
    for (const id of ["storeRarChipsMain"]) {
      const rarBox = $(id);
      if (!rarBox || rarBox.dataset.bound) continue;
      rarBox.dataset.bound = "1";
      rarBox.addEventListener("click", (e) => {
        const c = e.target.closest(".chip");
        if (!c) return;
        storeRar = c.getAttribute("data-r");
        storeSyncChips();
        renderStore();
      });
    }
    for (const id of ["storeSortMain"]) {
      const sort = $(id);
      if (!sort || sort.dataset.bound) continue;
      sort.dataset.bound = "1";
      sort.addEventListener("change", () => {
        storeSort = sort.value;
        renderStore();
      });
    }
    for (const id of ["storeTabsMain"]) {
      const tabs = $(id);
      if (!tabs || tabs.dataset.bound) continue;
      tabs.dataset.bound = "1";
      tabs.addEventListener("click", (e) => {
        const t = e.target.closest(".tab");
        if (!t) return;
        storeFilter = t.getAttribute("data-f");
        storeSyncTabs();
        renderStore();
      });
    }
    for (const id of ["storeGroupMain"]) {
      const grp = $(id);
      if (!grp || grp.dataset.bound) continue;
      grp.dataset.bound = "1";
      grp.addEventListener("click", (e) => {
        const t = e.target.closest(".tab");
        if (!t || !t.getAttribute("data-g")) return;
        storeGroup = t.getAttribute("data-g");
        storeFilter = "alle";
        storeSyncTabs();
        renderStore();
      });
    }
    const search = $("storeSearch");
    if (search && !search.dataset.bound) {
      search.dataset.bound = "1";
      let tmr = null;
      search.addEventListener("input", () => {
        clearTimeout(tmr);
        tmr = setTimeout(() => { storeQuery = (search.value || "").trim().toLowerCase(); renderStore(); }, 160);
      });
    }
    const gridEl = $("storeGridMain");
    if (gridEl && !gridEl.dataset.delegated) {
      gridEl.dataset.delegated = "1";
      const itemOf = (el) => { const c = el.closest(".store-card"); return c ? byId(c.dataset.id) : null; };
      gridEl.addEventListener("click", (e) => {
        const it = itemOf(e.target);
        if (!it) return;
        const btn = e.target.closest(".cardBtn");
        if (btn) {
          e.stopPropagation();
          if (btn.disabled) return;
          if (btn.dataset.act === "equip") storeEquip(it.id);
          else if (btn.dataset.act === "buy") storeBuy(it.id);
          return;
        }
        storeOpenModal(it);
      });
      gridEl.addEventListener("keydown", (e) => {
        if (e.key !== "Enter" && e.key !== " ") return;
        if (e.target.closest(".cardBtn")) return;
        const it = itemOf(e.target);
        if (it) { e.preventDefault(); storeOpenModal(it); }
      });
    }
    storeSyncChips();
    storeSyncTabs();
    const close = $("storeItemClose");
    if (close) close.onclick = storeCloseModal;
    const modal = $("storeItemModal");
    if (modal) modal.addEventListener("click", (e) => { if (e.target === modal) storeCloseModal(); });
    document.addEventListener("keydown", (e) => { if (e.key === "Escape") storeCloseModal(); });
  }

  let cosmSkinViewer = null;
  let cosmSkinKey = "";
  let cosmSkinUrlCache = null;
  async function renderCosmetics(force) {
    try {
      const cc = window.__cosmCache;
      if (!force && cc && cc.me && cc.store && (Date.now() - cc.t) < 45000) {
        renderCosmeticsFrom(cc.me, cc.store);
        return;
      }
    } catch (e) {}
    const box = $("cosmOwned");
    if (box) box.innerHTML = '<div class="store-msg">Lade …</div><div class="store-msg" id="cosmDbg"></div>';
    const t0 = Date.now();
    let meState = "…";
    let storeState = "…";
    const dbgTimer = setInterval(() => {
      const el = $("cosmDbg");
      if (el) el.textContent = "Lade … (" + Math.round((Date.now() - t0) / 1000) + "s) me:" + meState + " store:" + storeState;
    }, 1000);
    const stopDbg = () => {
      clearInterval(dbgTimer);
      const el = $("cosmDbg");
      if (el) el.remove();
    };
    let me = null;
    let store = null;
    let errText = "";

    const withTimeout = (promise, ms, label) => Promise.race([
      promise,
      new Promise((_, reject) => setTimeout(() => reject(new Error(label + " timeout")), ms))
    ]);

    const safeInvoke = (cmd) => Promise.resolve().then(() => invoke(cmd)).catch((e) => ({ error: String((e && e.message) || e) }));
    const settled = (p) => p.then(
      (value) => ({ status: "fulfilled", value }),
      (reason) => ({ status: "rejected", reason })
    );
    const [meRes, storeRes] = await Promise.all([
      settled(withTimeout(safeInvoke("kollegen_me"), 25000, "kollegen_me")),
      settled(withTimeout(safeInvoke("kollegen_store"), 25000, "kollegen_store"))
    ]);

    if (meRes.status === "fulfilled") {
      const r = meRes.value;
      if (r && !r.error) { me = r; meState = "ok"; }
      else { errText = (r && r.error) || "unbekannt"; meState = "fehler"; }
    } else {
      errText = meRes.reason?.message || "timeout"; meState = "fehler";
    }

    if (storeRes.status === "fulfilled") {
      const r = storeRes.value;
      if (r && !r.error && Array.isArray(r.catalog)) { store = r; storeState = "ok"; }
      else { if (!errText) errText = (r && r.error) || "unbekannt"; storeState = "fehler"; }
    } else {
      if (!errText) errText = storeRes.reason?.message || "timeout"; storeState = "fehler";
    }
     if (!me || !store) {
       stopDbg();
       // Automatischer Retry bei transienten Netzwerkfehlern (bis zu 2x)
       const retryCount = window.__cosmRetryCount || 0;
       if (retryCount < 2) {
         window.__cosmRetryCount = retryCount + 1;
         setTimeout(() => renderCosmetics(true), 2000);
         if (box) box.innerHTML = '<div class="store-msg">Laden fehlgeschlagen, versuche erneut …</div>';
         return;
       }
       window.__cosmRetryCount = 0;
       if (box) {
         box.innerHTML = "";
         const msg = document.createElement("div");
         msg.className = "store-msg";
         msg.textContent = "Laden fehlgeschlagen" + (errText ? ": " + errText : "") + ".";
         box.appendChild(msg);
         const retry = document.createElement("button");
         retry.className = "btn-secondary";
         retry.textContent = "Erneut versuchen";
         retry.onclick = () => { window.__cosmRetryCount = 0; renderCosmetics(true); };
         box.appendChild(retry);
       }
       return;
    }
    try {
      window.__cosmCache = { t: Date.now(), me: me, store: store };
      window.__cosmStore = store.catalog || [];
      window.__cosmMe = me;
    } catch (e) {}
     window.__cosmRetryCount = 0;
     renderCosmeticsFrom(me, store);
    return;
    function renderCosmeticsFrom(me, store) {
    const equipped = me.equipped || {};
    const owned = store.catalog.filter((it) => it.owned && it.category !== "cape" && ["pet", "pet_skin", "name_color", "badge", "sticker", "title", "profil_stil", "shield", "back_accessory", "head_accessory", "face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves"].includes(it.category));
    const cosmCatLabel = (c) => {
      const known = { pet: "Haustier", title: "Titel", name_color: "Namensfarbe", badge: "Abzeichen", profile_frame: "Profil-Rahmen", profile_bg: "Profil-Hintergrund", banner: "Banner", profil_stil: "Profilstil", font: "Schriftart", sticker: "Aufkleber", avatar_theme: "Avatar-Hintergrund", cape: "Umhang", shield: "Schild", back_accessory: "Rücken", head_accessory: "Kopf", face_accessory: "Gesicht", aura: "Aura", shoulder_accessory: "Schulter", torso_accessory: "Oberkörper", legs_accessory: "Unterkörper", gloves: "Handschuhe" };
      if (c && known[c]) return known[c];
      const s = String(c || "").replace(/_/g, " ").trim();
      return s ? s.charAt(0).toUpperCase() + s.slice(1) : "";
    };
    const cosmOrder = ["title", "badge", "sticker", "name_color", "profil_stil", "shield", "back_accessory", "head_accessory", "face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves"];
    let cosmCollapsed = {};
    try { cosmCollapsed = JSON.parse(localStorage.getItem("kosmetCollapsed") || "{}"); } catch (e) { cosmCollapsed = {}; }
    const cosmIsCollapsed = (cat) => cosmCollapsed[cat] !== false;
    const cosmSetCollapsed = (cat, v) => {
      cosmCollapsed[cat] = v;
      try { localStorage.setItem("kosmetCollapsed", JSON.stringify(cosmCollapsed)); } catch (e) {}
    };
    const cosmEquip = async (cat, itemId, busyEl) => {
      if (busyEl && busyEl.dataset.busy) return;
      if (busyEl) busyEl.dataset.busy = "1";
      try {
        if (itemId) await invoke("kollegen_store_equip", { itemId, category: cat || "" });
        else await invoke("kollegen_store_equip", { itemId: "", category: cat });
      } catch (e) {}
      renderCosmetics(true);
      if (window.renderKollegenStore) { try { window.renderKollegenStore(); } catch (e) {} }
    };
    const cosmSwatch = (ch, it) => {
      const sw = document.createElement("span");
      sw.className = "sw";
      if (it.data && it.data.color1) sw.style.background = it.data.color1;
      else if (it.data && it.data.gradient) sw.style.background = it.data.gradient;
      else if (it.data && it.data.accent) sw.style.background = it.data.accent;
      else if (it.data && it.data.text) { sw.textContent = it.data.text; sw.style.background = "#3a3f4e"; }
      else if (it.data && it.data.icon) { sw.textContent = it.data.icon; sw.style.color = it.data.color; }
      else if (it.data && it.data.font) { sw.textContent = "Aa"; sw.style.background = "#3a3f4e"; }
      ch.append(sw);
    };
    const cosmChipKeys = (el, fn) => {
      el.setAttribute("role", "button");
      el.tabIndex = 0;
      el.onclick = fn;
      el.onkeydown = (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); fn(); } };
    };
    if (box) {
      box.innerHTML = "";
      if (!owned.length) box.innerHTML = '<div class="store-msg">Noch keine Ingame-Gegenstände – schau im Store vorbei.</div>';
      for (const cat of cosmOrder) {
        const mine = owned.filter((it) => it.category === cat);
        if (!mine.length) continue;
        const g = document.createElement("div");
        g.className = "kosmet-group" + (cosmIsCollapsed(cat) ? " collapsed" : "");
        const title = document.createElement("div");
        title.className = "kg-title";
        title.textContent = cosmCatLabel(cat) || "Sonstiges";
        const cnt = document.createElement("span");
        cnt.className = "kg-count";
        cnt.textContent = String(mine.length);
        title.append(cnt);
        title.setAttribute("role", "button");
        title.tabIndex = 0;
        const toggleGroup = () => {
          const c = g.classList.toggle("collapsed");
          cosmSetCollapsed(cat, c);
        };
        title.onclick = toggleGroup;
        title.onkeydown = (e) => { if (e.key === "Enter" || e.key === " ") { e.preventDefault(); toggleGroup(); } };
        g.append(title);
        const chips = document.createElement("div");
        chips.className = "kosmet-chips";
        const none = document.createElement("span");
        none.className = "kosmet-chip" + (equipped[cat] ? "" : " eq");
        none.textContent = "Keins";
        cosmChipKeys(none, () => cosmEquip(cat, "", none));
        chips.append(none);
        for (const it of mine) {
          const ch = document.createElement("span");
          ch.className = "kosmet-chip" + (equipped[it.category] === it.id ? " eq" : "");
          cosmSwatch(ch, it);
          ch.append(" " + (it.name || it.id));
          cosmChipKeys(ch, () => cosmEquip(it.category, it.id, ch));
          chips.append(ch);
        }
        g.append(chips);
        box.appendChild(g);
      }
    }
     const eqBox = $("cosmEquipped");
     if (eqBox) {
       eqBox.innerHTML = "";
      const catOf = {};
      for (const it of store.catalog) { catOf[it.id] = it; }
      const rows = [];
      for (const cat of Object.keys(equipped || {})) {
        const id = equipped[cat];
        if (!id) continue;
        const it = catOf[id];
        rows.push({
          label: (cosmCatLabel(cat) || cat),
          name: it ? (it.name || id) : id,
          accent: (it && it.data && (it.data.accent || it.data.color1)) || null,
        });
      }
      if (!rows.length) {
        eqBox.innerHTML = '<div class="store-msg">Nichts ausgerüstet – wähle links eine Kategorie.</div>';
      } else {
        for (const r of rows) {
          const row = document.createElement("div");
          row.className = "cosm-eq-row";
          const dot = document.createElement("span");
          dot.className = "cosm-eq-dot";
          if (r.accent) dot.style.background = r.accent;
          const txt = document.createElement("span");
          txt.className = "cosm-eq-txt";
          const lab = document.createElement("span");
          lab.className = "cosm-eq-label";
          lab.textContent = r.label;
          const nm = document.createElement("span");
          nm.className = "cosm-eq-name";
          nm.textContent = r.name;
          nm.title = r.name;
          txt.append(lab, nm);
          row.append(dot, txt);
          eqBox.appendChild(row);
        }
      }
     }
     // Store-ausgerüsteten Cape auf die Skin-Viewer anwenden
     (function () {
       const eqCapeId = equipped && equipped.cape;
       const eqCapeItem = eqCapeId ? (store.catalog || []).find((it) => it.id === eqCapeId) : null;
       if (eqCapeItem && eqCapeItem.data && eqCapeItem.data.texture) {
         const texPath = eqCapeItem.data.texture;
         invoke("kollegen_mod_asset", { file: texPath }).then((res) => {
           let url = null;
           if (res && res.texture) url = res.texture;
           else if (res && res.data) url = res.data;
           if (url) {
             currentCapeUrl = url;
             applyCapeToViewer();
             if (homeSkinViewer) {
               try { homeSkinViewer.loadCape(url); } catch (_) {}
             }
             if (cosmSkinViewer) {
               try { const c = cosmSkinViewer.loadCape(url); if (c && typeof c.catch === "function") c.catch(() => {}); } catch (_) {}
             }
           }
         }).catch(() => {});
       } else if (!eqCapeId) {
         currentCapeUrl = null;
       }
     })();
     const PET_MODE_OPTS = {
      pet_allay: [["follow", "Folgen"], ["hover", "Fliegen"], ["shoulder", "Schulter"], ["head", "Kopf"]],
      pet_blub: [["follow", "Folgen"]],
    };
    const PET_ICON = { pet_allay: "🧚", pet_blub: "🫧", pet_cat: "🐱", pet_dog: "🐶", pet_chicken: "🐔", pet_rabbit: "🐰", pet_axolotl: "🦎", pet_bat: "🦇", pet_fox: "🦊", pet_parrot: "🦜", pet_bee: "🐝", pet_squid: "🦑", pet_nautilus: "🐚", pet_copper_golem: "🤖" };
    const petModesFor = (id) => PET_MODE_OPTS[id] || [["follow", "Folgen"], ["hover", "Fliegen"], ["shoulder", "Schulter"], ["head", "Kopf"]];
    const petSave = async (petId, patch) => {
      try {
        const r = await invoke("kollegen_pet_config", Object.assign({ pet: petId }, patch));
        if (r && r.error) toast("Pet speichern fehlgeschlagen: " + r.error, "error");
        else if (r && r.petConfigs) {
          me.petConfigs = r.petConfigs;
          refreshPetCardTitle(petId);
        }
      } catch (e) {
        toast("Pet speichern fehlgeschlagen", "error");
      }
    };
    const refreshPetCardTitle = (petId) => {
      const cardsEl = $("cosmPetCards");
      const card = cardsEl ? cardsEl.querySelector('[data-pet-card="' + petId + '"]') : null;
      if (!card) return;
      const base = card.dataset.petBase || petId;
      const cfgs = (me.petConfigs && typeof me.petConfigs === "object") ? me.petConfigs : {};
      const cfg = (cfgs[petId] && typeof cfgs[petId] === "object") ? cfgs[petId] : {};
      const t = card.querySelector("[data-pet-title]");
      if (t) {
        t.textContent = cfg.name || base;
        t.style.color = (cfg.name && /^#[0-9a-fA-F]{6}$/.test(cfg.nameColor || "")) ? cfg.nameColor : "";
      }
      let sub = card.querySelector(".pet-subname");
      if (cfg.name && cfg.name !== base) {
        if (!sub) {
          sub = document.createElement("div");
          sub.className = "pet-subname";
          if (t && t.parentElement) t.parentElement.insertBefore(sub, t.nextSibling);
        }
        if (sub) sub.textContent = base;
      } else if (sub) sub.remove();
    };
    const petOwned = owned.filter((it) => it.category === "pet");
    const petBox = $("cosmPetBox");
    if (petBox) {
      const noneBtn = $("cosmPetNone");
      if (noneBtn && !noneBtn.dataset.bound) {
        noneBtn.dataset.bound = "1";
        noneBtn.onclick = () => cosmEquip("pet", "", null);
      }
      const cards = $("cosmPetCards");
      if (!petOwned.length) {
        petBox.style.display = "none";
      } else {
        petBox.style.display = "";
        const focus = document.activeElement;
        const focusPet = focus && focus.dataset ? focus.dataset.petField : null;
        const focusKind = focus && focus.dataset ? focus.dataset.petKind : null;
        const focusVal = focus && ("value" in focus) ? focus.value : null;
        const equipped = (me.equipped || {}).pet;
        const cfgs = (me.petConfigs && typeof me.petConfigs === "object") ? me.petConfigs : ((me.petConfig && typeof me.petConfig === "object") ? { _: me.petConfig } : {});
        const getCfg = (id) => (cfgs[id] && typeof cfgs[id] === "object") ? cfgs[id] : ((cfgs._ && typeof cfgs._ === "object") ? cfgs._ : {});
        cards.innerHTML = "";
        for (const it of petOwned) {
          const cfg = getCfg(it.id);
          const card = document.createElement("div");
          card.className = "store-card pet-card" + (equipped === it.id ? " equipped" : "");
          card.dataset.petCard = it.id;
          if (equipped === it.id) card.dataset.state = "equipped";
          card.dataset.petBase = it.name || it.id;
          const pv = document.createElement("div");
          pv.className = "store-pv pet-pv";
          const mythicKinds = { pet_ember_drake: "ember_drake", pet_void_wyrm: "void_wyrm", pet_shadow_reaper: "shadow_reaper", pet_rune_sentinel: "rune_sentinel", pet_flame_skull: "flame_skull", pet_storm_cloud: "storm_cloud", pet_crystal_golem: "crystal_golem", pet_spectre: "spectre", pet_lantern_spirit: "lantern_spirit", pet_bolt_bot: "bolt_bot", pet_mini_ufo: "mini_ufo", pet_grimoire: "grimoire", pet_mooncap: "mooncap", pet_jelly_cube: "jelly_cube" };
          if (mythicKinds[it.id]) {
            const img = document.createElement("img");
            img.className = "pet-preview";
            img.alt = "";
            img.src = "pets/" + mythicKinds[it.id] + ".png";
            img.onerror = () => { img.remove(); pv.textContent = PET_ICON[it.id] || "🐾"; };
            pv.append(img);
          } else {
            pv.textContent = PET_ICON[it.id] || "🐾";
          }
          card.append(pv);
          const body = document.createElement("div");
          body.className = "store-card-body";
          const t = document.createElement("div");
          t.className = "store-card-title";
          t.dataset.petTitle = it.id;
          t.textContent = cfg.name || it.name || it.id;
          if (cfg.name && /^#[0-9a-fA-F]{6}$/.test(cfg.nameColor || "")) t.style.color = cfg.nameColor;
          body.append(t);
          if (cfg.name && it.name && cfg.name !== it.name) {
            const sub = document.createElement("div");
            sub.className = "pet-subname";
            sub.textContent = it.name;
            body.append(sub);
          }
          const nameIn = document.createElement("input");
          nameIn.className = "store-sort pet-name";
          nameIn.maxLength = 16;
          nameIn.placeholder = "Name";
          nameIn.dataset.petField = it.id;
          nameIn.dataset.petKind = "name";
          nameIn.value = (focusPet === it.id && focusKind === "name" && focusVal !== null) ? focusVal : (cfg.name || "");
          const nameRow = document.createElement("div");
          nameRow.className = "pet-namerow";
          nameIn.addEventListener("change", () => petSave(it.id, { name: nameIn.value || null }));
          nameIn.addEventListener("keydown", (e) => { if (e.key === "Enter") { e.preventDefault(); nameIn.blur(); } });
          nameRow.append(nameIn);
          const saveBtn = document.createElement("button");
          saveBtn.className = "pet-savebtn";
          saveBtn.textContent = "Speichern";
          saveBtn.title = "Name speichern";
          saveBtn.dataset.petField = it.id;
          saveBtn.dataset.petKind = "save";
          saveBtn.addEventListener("click", (e) => {
            e.stopPropagation();
            petSave(it.id, { name: nameIn.value || null });
            try { nameIn.blur(); } catch (ee) {}
          });
          nameRow.append(saveBtn);
          body.append(nameRow);
          const row = document.createElement("div");
          row.className = "pet-optrow";
          const colorIn = document.createElement("input");
          colorIn.type = "color";
          colorIn.className = "pet-color";
          colorIn.dataset.petField = it.id;
          colorIn.dataset.petKind = "color";
          colorIn.value = /^#[0-9a-fA-F]{6}$/.test(cfg.nameColor || "") ? cfg.nameColor : "#ffffff";
          colorIn.addEventListener("change", () => petSave(it.id, { color: colorIn.value || null }));
          row.append(colorIn);
          const modes = petModesFor(it.id);
          if (modes.length > 1) {
            const sel = document.createElement("select");
            sel.className = "store-sort pet-mode";
            sel.dataset.petField = it.id;
            sel.dataset.petKind = "mode";
            for (const [v, label] of modes) {
              const o = document.createElement("option");
              o.value = v;
              o.textContent = label;
              sel.append(o);
            }
            sel.value = modes.some(([v]) => v === cfg.mode) ? cfg.mode : "follow";
            sel.addEventListener("change", () => petSave(it.id, { mode: sel.value || null }));
            row.append(sel);
          } else {
            const tag = document.createElement("span");
            tag.className = "pet-modelock";
            tag.textContent = modes[0][1];
            row.append(tag);
          }
          body.append(row);
          const skins = owned.filter((c) => c && c.category === "pet_skin" && c.data && c.data.variantOf === it.id);
          if (skins.length) {
            const skinSel = document.createElement("select");
            skinSel.className = "store-sort pet-skin";
            skinSel.dataset.petField = it.id;
            skinSel.dataset.petKind = "variant";
            const no = document.createElement("option");
            no.value = "";
            no.textContent = "Skin: Normal";
            skinSel.append(no);
            for (const s of skins) {
              const o = document.createElement("option");
              o.value = s.data.variant;
              o.textContent = "Skin: " + (s.data.label || s.name || s.data.variant);
              skinSel.append(o);
            }
            skinSel.value = cfg.variant || "";
            if (focusPet === it.id && focusKind === "variant" && focusVal !== null) skinSel.value = focusVal;
            skinSel.addEventListener("change", () => petSave(it.id, { variant: skinSel.value || null }));
            body.append(skinSel);
          }
          card.append(body);
          card.addEventListener("click", (e) => {
            if (e.target.closest("input,select,option,button")) return;
            cosmEquip("pet", it.id, null);
          });
          cards.append(card);
        }
        if (focusPet && focusKind) {
          const back = cards.querySelector('[data-pet-field="' + focusPet + '"][data-pet-kind="' + focusKind + '"]');
          if (back) { try { back.focus({ preventScroll: true }); } catch (e) { try { back.focus(); } catch (ee) {} } }
        }
      }
    }
    stopDbg();
    renderCosmSkin(me);
    renderCosmName(me, store.catalog);
    }
  }

  function cosmCatById(list, id) {
    if (!id || !Array.isArray(list)) return null;
    return list.find((c) => c && c.id === id) || null;
  }

  function renderCosmName(me, catalog) {
    const el = $("cosmNamePreview");
    if (!el || !me) return;
    const eq = me.equipped || {};
    const parts = [];
    const badge = cosmCatById(catalog, eq.badge);
    if (badge && badge.data && badge.data.icon) parts.push({ t: badge.data.icon + " ", c: badge.data.color || "#FFD700" });
    const nc = cosmCatById(catalog, eq.name_color);
    const stil = cosmCatById(catalog, eq.profil_stil);
    const accent = (nc && nc.data && nc.data.accent) || (stil && stil.data && stil.data.accent) || "#FFFFFF";
    parts.push({ t: me.name || me.mc_name || "Spieler", c: accent, b: true });
    const sticker = cosmCatById(catalog, eq.sticker);
    if (sticker && sticker.data && sticker.data.icon) parts.push({ t: " " + sticker.data.icon, c: sticker.data.color || "#FFFFFF" });
    el.innerHTML = "";
    for (const p of parts) {
      const s = document.createElement("span");
      s.textContent = p.t;
      s.style.color = p.c;
      if (p.b) s.style.fontWeight = "800";
      el.appendChild(s);
    }
    const title = cosmCatById(catalog, eq.title);
    if (title && title.data && title.data.text) {
      const t = document.createElement("div");
      t.className = "cosm-titlepreview";
      t.textContent = title.data.text;
      t.style.color = accent;
      el.appendChild(t);
    }
    if (typeof me.level === "number") {
      const l = document.createElement("div");
      l.className = "cosm-levelpreview";
      l.textContent = "[lv " + me.level + "]";
      el.appendChild(l);
    }
  }

  function disposeCosmSkinViewer() {
    if (!cosmSkinViewer) return;
    try { cosmSkinViewer.dispose && cosmSkinViewer.dispose(); } catch (_) {}
    cosmSkinViewer = null;
  }
  async function renderCosmSkin(me) {
    const canvas = $("cosmSkinCanvas");
    if (!canvas) return;
    let url = null;
    const norm = (u) => (u ? window.httpsify(u) : u);
    const skinTimeout = (p, ms) => Promise.race([p, new Promise((_, rej) => setTimeout(() => rej(new Error("skin timeout")), ms))]);
    const showFallback = () => {
      const cv = $("cosmSkinCanvas");
      if (!cv) return;
      const w = cv.parentElement;
      let fb = w.querySelector("img.skin-fallback");
      if (!fb) {
        fb = new Image();
        fb.className = "skin-fallback";
        fb.alt = "";
        w.insertBefore(fb, cv);
      }
      fb.src = window.httpsify(url);
      cv.style.display = "none";
    };
    if (cosmSkinUrlCache) url = cosmSkinUrlCache;
    if (!url) {
      try {
        const list = await skinTimeout(invoke("skin_list"), 5000);
        const skins = (list && list.skins) || [];
        const active = skins.find((s) => s.name === (list && list.active));
        if (active && active.url) { url = httpsify(active.url); cosmSkinUrlCache = url; }
      } catch (e) {}
      if (!url) {
        try {
          const prof = await skinTimeout(invoke("skin_mc_profile"), 5000);
          const sk = ((prof && prof.skins) || []).find((x) => x.state === "ACTIVE") || ((prof && prof.skins) || [])[0];
          if (sk && sk.url) { url = httpsify(sk.url); cosmSkinUrlCache = url; }
        } catch (e) {}
      }
    }
    const nm = (me && (me.name || me.mc_name)) || "";
    if (!url && nm) url = `https://mc-heads.net/skin/${encodeURIComponent(nm)}`;
    if (!url) return;
    const key = url + "|" + (currentCapeUrl || "");
    if (key === cosmSkinKey && cosmSkinViewer) return;
    cosmSkinKey = key;
    const sv3d = window.skinview3d;
    let webglOk = false;
    try {
      const probe = document.createElement("canvas");
      const g = probe.getContext("webgl2") || probe.getContext("webgl");
      webglOk = !!(g && g.getParameter);
    } catch (_) {}
    const wrap = canvas.parentElement;
    if (!(sv3d && sv3d.SkinViewer && sv3d.WalkingAnimation && webglOk)) {
      let fb = wrap.querySelector("img.skin-fallback");
      if (!fb) {
        fb = new Image();
        fb.className = "skin-fallback";
        fb.alt = "";
        wrap.insertBefore(fb, canvas);
      }
      fb.src = window.httpsify(url);
      canvas.style.display = "none";
      return;
    }
    try {
      if (cosmSkinViewer) {
        try { cosmSkinViewer.dispose && cosmSkinViewer.dispose(); } catch (_) {}
        cosmSkinViewer = null;
      }
      canvas.style.display = "";
      const fb = wrap.querySelector("img.skin-fallback");
      if (fb) fb.remove();
      cosmSkinViewer = new sv3d.SkinViewer({ canvas: canvas, width: 220, height: 440 });
      let skinUrl = norm(url);
      if (/^https?:\/\//i.test(url)) {
        try {
          const ctrl = new AbortController();
          const to = setTimeout(() => ctrl.abort(), 15000);
          const resp = await fetch(url, { signal: ctrl.signal });
          clearTimeout(to);
          if (resp.ok) skinUrl = URL.createObjectURL(await resp.blob());
        } catch (e) {}
      }
       if (window.kollegen3dRenderEquipped && me) {
         try {
           const cm = $("cosmModels");
           if (cm) {
             if (!window.__cosmStore || !window.__cosmStore.length) {
               try { const st = await invoke("kollegen_store"); window.__cosmStore = (st && st.catalog) || []; } catch (e) {}
             }
             window.kollegen3dRenderEquipped(cm, me.equipped || {}, window.__cosmStore || []);
           }
         } catch (e) {}
       }
       const p = cosmSkinViewer.loadSkin(skinUrl);
       if (p && typeof p.then === "function") p.catch(() => { showFallback(); });
       cosmSkinViewer.animation = new sv3d.WalkingAnimation();
       cosmSkinViewer.animation.speed = 0.5;
       cosmSkinViewer.autoRotate = true;
       cosmSkinViewer.autoRotateSpeed = 1.0;
       if (currentCapeUrl) {
         try { const c = cosmSkinViewer.loadCape(currentCapeUrl); if (c && typeof c.catch === "function") c.catch(() => {}); } catch (_) {}
       }
    } catch (e) {
      console.error("Cosmetics-Skin fehlgeschlagen:", e);
      showFallback();
    }
  }

  window.renderKollegenSummary = renderProfileSummary;
  function renderProfileSummary() {
    const wrap = $("spProgressWrap");
    const codeWrap = $("spCodeWrap");
    const st = window.kmState;
    if (!st || !kmMe) return;
    const pts = $("spPoints");
    if (pts) pts.textContent = (typeof st.points === "number" ? st.points : "–") + " Punkte · " + (typeof st.level === "number" ? "Level " + st.level : "Level –");
    const total = typeof st.points_total === "number" ? st.points_total : 0;
    const inLevel = total ? ((total - 1) % 300 + 300) % 300 + 1 : 1;
    const fill = $("spProgressFill");
    if (fill) fill.style.width = inLevel + "%";
    const lvl = typeof st.level === "number" ? st.level : 1;
    const next = 300 - inLevel;
    const label = $("spProgressLabel");
    if (label) label.textContent = `Level ${lvl} → ${lvl + 1} · ${next} Punkte bis Level ${lvl + 1}`;
    if (wrap) wrap.style.display = "";
    const myCode = (kmMe && (kmMe.code || kmMe.friend_code)) || "";
    const codeEl = $("spCode");
    if (codeEl) codeEl.textContent = myCode;
    const codeCopy = $("spCodeCopy");
    if (codeCopy) codeCopy.onclick = () => {
      if (!myCode) return;
      if (navigator.clipboard) navigator.clipboard.writeText(myCode).catch(() => {});
      codeCopy.textContent = "✓";
      setTimeout(() => { codeCopy.textContent = "Kopieren"; }, 1500);
    };
    if (codeWrap && myCode) codeWrap.style.display = "";
  }
  window.kmShowProfile = async function (code) {
    if (!code) return;
    const res = $("viewProfileResult");
    const pm = $("profileModal");
    if (pm) pm.style.display = "flex";
    pmSetView(true);
    if (!res) return;
    res.innerHTML = `<div style="color:var(--muted);padding:0.5rem 0;">Lade Profil…</div>`;
    let p;
    try { p = await invoke("kollegen_profile_view", { code }); } catch (e) { p = { error: String(e) }; }
    if (!p || p.error) {
      res.innerHTML = `<div style="color:#f85149;padding:0.5rem 0;">Profil nicht gefunden: ${esc((p && p.error) || "?")}</div>`;
      return;
    }
    pmSetView(!p.isViewer);
    const eq = p.equipped || {};
    const find = (id) => {
      if (!id) return null;
      if (id && id.id) id = id.id;
      for (const k in eq) {
        const e = eq[k];
        if (e && e.id === id) return e.data || null;
      }
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
    const nc = find(eq.name_color);
    const sk = find(eq.sticker);
    const fn = find(eq.font);
    const accent = (nc && nc.accent) || (stil && stil.accent) || "#f1c40f";
    const head = (() => {
      if (p.uuid) return `https://mc-heads.net/head/${p.uuid}/256`;
      if (p.avatar_data_url) return p.avatar_data_url;
      return "https://mc-heads.net/head/MHF_Steve/256";
    })();
    const name = (ti && ti.text ? ti.text + " \u00b7 " : "") + (p.name || "User " + p.id);
    const status = p.online ? `<span style="color:#3fb950;">\u25cf</span> ${esc(p.server || "Online")}` : `<span style="color:#555;">\u25cf</span> Offline`;
    let h = "";
    if (banner && banner.gradient) h += `<div style="height:96px;border-radius:10px;margin-bottom:0.6rem;background:${esc(banner.gradient)};border:1px solid rgba(255,255,255,0.08);"></div>`;
    else if (p.banner_data_url) h += `<div style="height:96px;border-radius:10px;margin-bottom:0.6rem;background:url(${esc(p.banner_data_url)}) center/cover;"></div>`;
    let frm = "";
    if (frameData && frameData.color1) frm = `border:${(frameData.width || 3)}px solid ${esc(frameData.color1)};box-shadow:0 0 16px ${esc(frameData.color1)}66;`;
    let thm = "";
    if (themeData && themeData.gradient) thm = `background:${esc(themeData.gradient)};`;
    h += `<div style="display:flex;gap:0.8rem;align-items:center;">`;
    h += `<img src="${esc(head)}" style="width:64px;height:64px;border-radius:14px;object-fit:cover;${frm}${thm}" onerror="this.style.display='none';" alt=""/>`;
    h += `<div style="flex:1;min-width:0;">`;
    h += `<div style="font-weight:800;font-size:1.05rem;color:${esc(accent)}${fn && fn.font ? `;font-family:${esc(fn.font)}` : ""}">${bd && bd.icon ? `<span style="color:${esc(bd.color)}">${esc(bd.icon)}</span> ` : ""}${esc(name)}${sk && sk.icon ? `<span style="color:${esc(sk.color)}"> ${esc(sk.icon)}</span>` : ""}</div>`;
    h += `<div style="color:var(--muted);font-size:0.8rem;">${status} \u00b7 Level ${esc(p.level)} \u00b7 Code ${esc(p.code)}</div>`;
    h += `</div></div>`;
    const EQCATS = [["title", "Titel"], ["badge", "Abzeichen"], ["avatar_theme", "Avatar-Hintergrund"], ["avatar_frame", "Avatar-Rahmen"], ["profile_bg", "Profil-Hintergrund"], ["profile_frame", "Profil-Rahmen"], ["banner", "Banner"], ["sticker", "Sticker"], ["name_color", "Namensfarbe"], ["font", "Schrift"], ["profil_stil", "Profil-Stil"]];
    let eqRows = "";
    for (const c of EQCATS) {
      const entry = eq[c[0]];
      if (!entry) continue;
      const d = entry.data || {};
      const nmItem = entry.name || d.text || "";
      if (!nmItem) continue;
      let swatch = "";
      let band = "";
      let ic = "";
      let icStyle = "";
      if (d.gradient) swatch = `background:${esc(d.gradient)};`;
      else if (d.color1) swatch = `background:linear-gradient(135deg,${esc(d.color1)},${esc(d.color2 || d.color1)});`;
      else if (d.accent) swatch = `background:${esc(d.accent)};`;
      else if (d.icon) { ic = esc(d.icon); icStyle = `color:${esc(d.color)};`; }
      else if (d.font) { ic = "Aa"; icStyle = `font-family:${esc(d.font)};`; }
      if (ic) band = `<div style="height:36px;background:rgba(255,255,255,0.07);display:flex;align-items:center;justify-content:center;font-size:16px;${icStyle}">${ic}</div>`;
      else band = `<div style="height:36px;${swatch || "background:rgba(255,255,255,0.07);"}"></div>`;
      if (band === "") band = `<div style="height:36px;background:rgba(255,255,255,0.07);"></div>`;
      eqRows += `<div style="border-radius:9px;overflow:hidden;min-width:0;border:1px solid rgba(255,255,255,0.06);">${band}`;
      eqRows += `<div style="display:flex;align-items:center;gap:0.4rem;padding:0.4rem 0.5rem;background:rgba(255,255,255,0.03);">`;
      eqRows += `<span style="color:#8b95a5;font-size:0.62rem;flex:none;text-transform:uppercase;letter-spacing:0.04em;">${esc(c[1])}</span>`;
      eqRows += `<span style="color:#e6ecf5;font-weight:600;font-size:0.72rem;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;flex:1;">${esc(nmItem)}</span>`;
      eqRows += `</div></div>`;
    }
    if (eqRows) h += `<div style="display:grid;grid-template-columns:1fr 1fr;gap:0.5rem;margin-top:0.7rem;">${eqRows}</div>`;
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
    stopDmCall();
    const cb = $("dmCallBtn");
    if (cb) cb.style.display = "none";
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
      const th = (window.themeOf && window.themeOf(u)) || {};
      if (th.aFrame && th.aFrame.color1) img.style.border = `${(th.aFrame.width || 3)}px solid ${esc(th.aFrame.color1)}`;
      const info = document.createElement("div");
      info.className = "dm-ci";
      const n = document.createElement("div");
      n.className = "dm-cn";
      const dot = document.createElement("span");
      dot.style.color = u.online ? "#3fb950" : "#4b5563";
      dot.textContent = "\u25cf";
      dot.style.marginRight = "0.25rem";
      let nHtml = "";
      if (th.title && th.title.text) nHtml += escapeHtml(th.title.text) + " ";
      nHtml += escapeHtml(u.name || ("User " + u.id));
      n.innerHTML = nHtml;
      n.prepend(dot);
      if (th.accent) n.style.color = th.accent;
      if (th.font) n.style.fontFamily = th.font;
      const last = document.createElement("div");
      last.className = "dm-cl";
      const l = c.last || {};
      last.textContent = (l.from === dmMeId ? "Du: " : "") + (l.text || "") + (l.ts ? " \u00b7 " + timeStr(l.ts) : "");
      info.append(n, last);
      row.append(img, info);
      row.onclick = () => openOther(u.discordId, u.name || "User " + u.id, u.code, u.profile && u.profile.avatar_data_url);
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
      openOther(p.discordId, p.name, p.code, p.profile && p.profile.avatar_data_url);
      loadConvs();
    } catch (e) { alert("Fehler: " + e); }
  }

  function openOther(did, name, code, avatarUrl) {
    dmCurrent = did;
    const head = $("dmThreadHead");
    if (head) {
      head.style.color = "";
      $("dmThName").textContent = name || "Chat";
      const av = $("dmThAv");
      if (av) {
        const src = avatarUrl || "https://mc-heads.net/head/" + (name || "MHF_Steve") + "/96";
        av.src = src;
        av.style.display = "";
        av.onerror = () => { av.src = "https://mc-heads.net/head/MHF_Steve/96"; };
      }
      const pr = $("dmThProf");
      if (pr) {
        if (code) { pr.style.display = ""; window.kmThreadProfile = () => { if (window.kmShowProfile) window.kmShowProfile(code); }; }
        else pr.style.display = "none";
        if (!pr.dataset.bound) {
          pr.dataset.bound = "1";
          pr.addEventListener("click", (e) => { e.preventDefault(); if (window.kmThreadProfile) window.kmThreadProfile(); });
        }
      }
    }
    $("dmInputBox").style.display = "flex";
    const cb = $("dmCallBtn");
    if (cb) cb.style.display = "";
    document.querySelectorAll(".dm-conv.on").forEach((x) => x.classList.remove("on"));
    loadConvs();
    loadMsgs();
    if (dmPoll) clearInterval(dmPoll);
    dmPoll = setInterval(() => { if (dmCurrent) loadMsgs(true); }, 4000);
    checkDmActiveCall();
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

  // ── Gruppen ──
  let groupsCache = [];
  let groupCurrent = null;
  let groupPoll = null;
  let groupSinceMsg = 0;
  let groupMemberNames = {};

  window.renderGroups = renderGroups;
  async function renderGroups() {
    const el = $("kollegenGroups");
    if (!el) return;
    let list;
    try { list = await invoke("kollegen_groups"); } catch (e) { list = []; }
    list = Array.isArray(list) ? list : [];
    groupsCache = list;
    el.innerHTML = "";
    if (!list.length) { el.innerHTML = `<li class="list-empty">Noch keine Gruppen. Erstelle die erste!</li>`; return; }
    for (const g of list) {
      const li = document.createElement("li");
      const ic = document.createElement("div");
      ic.className = "friend-avatar";
      ic.style.display = "flex"; ic.style.alignItems = "center"; ic.style.justifyContent = "center";
      ic.style.background = "#2a3140"; ic.style.fontSize = "18px";
      ic.textContent = "\uD83D\uDC65";
      li.append(ic);
      const meta = document.createElement("div");
      meta.className = "friend-meta";
      const name = document.createElement("div");
      name.className = "friend-name";
      name.textContent = g.name || "Gruppe";
      const sub = document.createElement("div");
      sub.className = "friend-sub";
      const l = g.last || {};
      sub.textContent = ((l.text ? "Letzte Nachricht: " + l.text : "Noch keine Nachrichten") + " · " + (g.memberCount || 1) + " Mitglieder").slice(0, 80);
      meta.append(name, sub);
      li.append(meta);
      const ob = document.createElement("button");
      ob.textContent = "Öffnen";
      ob.onclick = () => openGroup(g.id);
      li.append(ob);
      el.append(li);
    }
  }

  $("groupCreateBtn").onclick = async () => {
    const inp = $("groupNameInput");
    const name = (inp.value || "").trim();
    if (!name) return;
    inp.value = "";
    try {
      const r = await invoke("kollegen_group_create", { name, memberIds: [] });
      if (r && r.id) { renderGroups(); openGroup(r.id); }
      else alert(r && r.error ? r.error : "Erstellen fehlgeschlagen.");
    } catch (e) { alert("Fehler: " + e); }
  };

  function closeGroup() {
    if (groupPoll) clearInterval(groupPoll);
    groupPoll = null;
    stopGroupCall();
    groupCurrent = null;
    groupMemberNames = {};
  }
  $("groupClose").onclick = () => { closeGroup(); $("groupModal").style.display = "none"; };

  async function openGroup(id) {
    groupCurrent = id;
    groupSinceMsg = 0;
    const m = $("groupModal");
    if (m) m.style.display = "flex";
    const box = $("groupMsgList");
    if (box) box.innerHTML = `<div class="dm-empty">Lade Gruppe…</div>`;
    let v = null;
    try { v = await invoke("kollegen_group_view", { groupId: id }); } catch (e) { v = null; }
    $("groupTitle").textContent = (v && v.name) ? v.name : "Gruppe";
    const members = (v && v.members) ? v.members : [];
    groupMemberNames = {};
    for (const mm of members) groupMemberNames[mm.discordId] = mm.name || ("User " + mm.id);
    renderGroupMembers(members);
    $("groupInputBox").style.display = "flex";
    loadGroupMsgs();
    if (groupPoll) clearInterval(groupPoll);
    groupPoll = setInterval(() => { if (groupCurrent) loadGroupMsgs(true); }, 4000);
    checkGroupActiveCall();
  }

  function renderGroupMembers(members) {
    const box = $("groupMembers");
    box.innerHTML = "";
    for (const mm of members) {
      const c = document.createElement("span");
      c.className = "group-member";
      const im = document.createElement("img");
      im.className = "gm-av";
      const uu = mm.uuid || mm.name || "";
      im.src = uu ? `https://mc-heads.net/head/${encodeURIComponent(uu).replace(/%20/g, "_")}/48` : "https://mc-heads.net/head/MHF_Steve/48";
      im.onerror = () => { im.src = "https://mc-heads.net/head/MHF_Steve/48"; };
      c.append(im);
      const n = document.createElement("span");
      n.textContent = mm.name || ("User " + mm.id);
      c.append(n);
      box.append(c);
    }
  }

  async function loadGroupMsgs(silent) {
    if (!groupCurrent) return;
    const box = $("groupMsgList");
    if (!box) return;
    let r;
    try { r = await invoke("kollegen_group_poll", { groupId: groupCurrent, sinceMsg: groupSinceMsg, sinceSig: 0 }); } catch (e) { r = { messages: [] }; }
    const msgs = (r && r.messages) ? r.messages : [];
    const had = box.querySelector(".dm-b");
    const wasBottom = had ? box.scrollHeight - box.scrollTop - box.clientHeight < 60 : true;
    for (const m of msgs) {
      if (m.ts > groupSinceMsg) groupSinceMsg = m.ts;
      const me = m.from === dmMeId;
      const b = document.createElement("div");
      b.className = "dm-b" + (me ? "" : " oth");
      const sendLabel = me ? "Du: " : ((groupMemberNames[m.from] ? groupMemberNames[m.from] + ": " : ""));
      b.textContent = sendLabel + m.text;
      const t = document.createElement("div");
      t.className = "dm-t";
      t.textContent = timeStr(m.ts);
      b.append(t);
      box.append(b);
    }
    if (!msgs.length && !box.querySelector(".dm-b")) {
      box.innerHTML = `<div class="dm-empty">Noch keine Nachrichten. Starte den Chat!</div>`;
      return;
    }
    if (!silent || wasBottom) box.scrollTop = box.scrollHeight;
  }

  $("groupSend").onclick = () => sendGroupMsg();
  $("groupText").addEventListener("keydown", (e) => { if (e.key === "Enter") sendGroupMsg(); });
  async function sendGroupMsg() {
    const inp = $("groupText");
    const t = (inp.value || "").trim();
    if (!t || !groupCurrent) return;
    inp.value = "";
    try {
      const r = await invoke("kollegen_group_send", { groupId: groupCurrent, text: t });
      if (r && r.ok !== false) loadGroupMsgs();
      else inp.value = t;
    } catch (e) { inp.value = t; alert("Fehler: " + e); }
  }

  $("groupAddBtn").onclick = async () => {
    const inp = $("groupAddCode");
    const code = (inp.value || "").trim().toUpperCase();
    if (!code || !groupCurrent) return;
    inp.value = "";
    try {
      let memberId = code;
      const p = await invoke("kollegen_profile_view", { code });
      if (p && p.discordId) memberId = p.discordId;
      const r = await invoke("kollegen_group_add_member", { groupId: groupCurrent, memberId });
      if (r && r.ok === false) alert(r.error || "Hinzufügen fehlgeschlagen.");
      else openGroup(groupCurrent);
    } catch (e) { alert("Fehler: " + e); }
  };

  $("groupLeaveBtn").onclick = async () => {
    if (!groupCurrent) return;
    try {
      await invoke("kollegen_group_leave", { groupId: groupCurrent });
      closeGroup();
      $("groupModal").style.display = "none";
      renderGroups();
    } catch (e) { alert("Fehler: " + e); }
  };

  // ── Anrufe (Gerüst, kein Audio) ──
  let dmCallId = null;
  let dmCallPoll = null;
  let dmCallSinceSig = 0;
  let dmCallPeerName = "";

  function showDmCallBar(text) {
    $("dmCallState").textContent = text;
    $("dmCallBar").style.display = "flex";
  }
  function hideDmCallBar() {
    $("dmCallBar").style.display = "none";
  }

  function stopDmCall() {
    if (dmCallPoll) clearInterval(dmCallPoll);
    dmCallPoll = null;
    const cid = dmCallId;
    dmCallId = null;
    if (cid) invoke("kollegen_call_leave", { callId: cid }).catch(() => null);
    hideDmCallBar();
  }

  async function dmStartCall() {
    if (!dmCurrent) return;
    const act = await invoke("kollegen_call_direct_active").catch(() => []);
    const acts = Array.isArray(act) ? act : [];
    const existing = acts.find((c) => c.direct && c.peer && (c.peer.discordId === dmCurrent || c.peer.id === dmCurrent));
    let r;
    if (existing) {
      await invoke("kollegen_call_join", { callId: existing.callId }).catch(() => null);
      dmCallPeerName = existing.peer.name || "der Person";
      showDmCallBar("Klingelt…");
    } else {
      r = await invoke("kollegen_call_direct_open", { peerId: dmCurrent });
      if (r && r.callId) {
        dmCallPeerName = (r.peer && r.peer.name) || "der Person";
        showDmCallBar("Klingelt…");
        await invoke("kollegen_call_signal", { callId: r.callId, toId: dmCurrent, kind: "ring", data: null }).catch(() => null);
      }
    }
    if (!r && !existing) {
      if (r && r.error) alert(r.error);
      else alert("Anruf fehlgeschlagen.");
      return;
    }
    dmCallId = existing ? existing.callId : r.callId;
    dmCallSinceSig = 0;
    startDmCallPoll();
  }

  const DM_CALL_LABELS = {
    ring: "Klingelt…",
    accept: "Verbunden – Anruf aktiv",
    decline: "Anruf beendet",
  };
  function startDmCallPoll() {
    if (dmCallPoll) clearInterval(dmCallPoll);
    dmCallPoll = setInterval(async () => {
      if (!dmCallId) return;
      let r = null;
      try { r = await invoke("kollegen_call_direct_poll", { callId: dmCallId, sinceSig: dmCallSinceSig }); } catch (e) { r = null; }
      if (!r) return;
      const sigs = (r.signals || []).filter((s) => s.ts > dmCallSinceSig);
      for (const s of sigs) {
        if (s.ts > dmCallSinceSig) dmCallSinceSig = s.ts;
        if (s.kind === "accept") { showDmCallBar(DM_CALL_LABELS.accept); }
        else if (s.kind === "ring") { showDmCallBar(DM_CALL_LABELS.ring); }
      }
    }, 4000);
  }

  $("dmCallBtn").onclick = () => dmStartCall();
  $("dmCallEnd").onclick = () => stopDmCall();

  async function checkDmActiveCall() {
    if (!dmCurrent) return;
    const act = await invoke("kollegen_call_direct_active").catch(() => []);
    const acts = Array.isArray(act) ? act : [];
    const existing = acts.find((c) => c.direct && c.peer && (c.peer.discordId === dmCurrent || c.peer.id === dmCurrent));
    if (!existing) return;
    dmCallPeerName = (existing.peer && existing.peer.name) || "der Person";
    await invoke("kollegen_call_join", { callId: existing.callId }).catch(() => null);
    dmCallId = existing.callId;
    dmCallSinceSig = 0;
    showDmCallBar("Direktanruf läuft – " + dmCallPeerName);
    startDmCallPoll();
  }

  // ── Gruppenanruf ──
  let groupCallId = null;
  let groupCallPoll = null;
  let groupCallSinceSig = 0;

  function showGroupCallBar(text) {
    $("groupCallState").textContent = text;
    $("groupCallBar").style.display = "flex";
  }
  function hideGroupCallBar() {
    $("groupCallBar").style.display = "none";
  }

  function stopGroupCall() {
    if (groupCallPoll) clearInterval(groupCallPoll);
    groupCallPoll = null;
    const cid = groupCallId;
    groupCallId = null;
    if (cid) invoke("kollegen_call_leave", { callId: cid }).catch(() => null);
    hideGroupCallBar();
  }

  async function checkGroupActiveCall() {
    if (!groupCurrent) return;
    const act = await invoke("kollegen_call_direct_active").catch(() => []);
    const acts = Array.isArray(act) ? act : [];
    const existing = acts.find((c) => !c.direct && c.groupId === groupCurrent);
    if (!existing) return;
    await invoke("kollegen_call_join", { callId: existing.callId }).catch(() => null);
    groupCallId = existing.callId;
    groupCallSinceSig = 0;
    showGroupCallBar("Gruppenanruf aktiv – tretet bei");
    startGroupCallPoll();
  }

  $("groupCallBtn").onclick = async () => {
    if (!groupCurrent) return;
    const act = await invoke("kollegen_call_direct_active").catch(() => []);
    const acts = Array.isArray(act) ? act : [];
    const existing = acts.find((c) => !c.direct && c.groupId === groupCurrent);
    let r;
    if (existing) {
      await invoke("kollegen_call_join", { callId: existing.callId }).catch(() => null);
      groupCallId = existing.callId;
      showGroupCallBar("Gruppenanruf aktiv");
    } else {
      r = await invoke("kollegen_call_open", { groupId: groupCurrent });
      if (!r || !r.callId) { if (r && r.error) alert(r.error); return; }
      groupCallId = r.callId;
      showGroupCallBar("Gruppenanruf wird aufgebaut…");
      await invoke("kollegen_call_signal", { callId: r.callId, toId: "", kind: "ring", data: null }).catch(() => null);
    }
    groupCallSinceSig = 0;
    startGroupCallPoll();
  };

  function startGroupCallPoll() {
    if (groupCallPoll) clearInterval(groupCallPoll);
    groupCallPoll = setInterval(async () => {
      if (!groupCallId) return;
      let r = null;
      try { r = await invoke("kollegen_call_direct_poll", { callId: groupCallId, sinceSig: groupCallSinceSig }); } catch (e) { r = null; }
      if (!r) return;
      for (const s of (r.signals || [])) {
        if (s.ts > groupCallSinceSig) groupCallSinceSig = s.ts;
      }
      const joined = (r.members && r.members.length) ? r.members.length : 1;
      showGroupCallBar(`Gruppenanruf aktiv – ${joined} im Anruf`);
    }, 4000);
  }

  $("groupCallEnd").onclick = () => stopGroupCall();

  let dmMeId = "";
  (async function init() {
    await loadStore();
    storeInit();
    renderStore();
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
    renderProfileSummary();
    renderGroups();
    initTwitch();
    // Freundes-Zeilen aktualisieren, falls schon gerendert.
    if (window.refreshSocial) { try { window.refreshSocial(true); } catch (e) {} }
  })();
})();
