(function () {
  const $ = (id) => document.getElementById(id);

  const PAGES = {
    home: ["Home", "Deine Instanzen und Neuigkeiten"],
    create: ["Neue Instanz", "Version und Mod-Loader wählen"],
    kmod: ["Kollegen-Mod", "Begleit-Mod, Clips und alle Features"],
    skins: ["Garderobe", "Skins, Capes und Kosmetik"],
    capes: ["Garderobe", "Skins, Capes und Kosmetik"],
    cosmetics: ["Garderobe", "Skins, Capes und Kosmetik"],
    store: ["Store", "Kosmetik und Items für den Client"],
    socials: ["Soziales", "Freunde, Profile und Aktivität"],
    logs: ["Logs & Updates", "Fehlersuche und Launcher-Version"],
  };

  const WARDROBE = [
    ["skins", "Skins"],
    ["capes", "Capes"],
    ["cosmetics", "Kosmetik"],
  ];

  const SETTINGS = [
    { tab: "connections", label: "Konten", hint: "Microsoft, Discord und Twitch verbinden", kw: "microsoft discord twitch konto login anmelden abmelden verbinden account" },
    { tab: "themes", label: "Darstellung", hint: "Farbschema, Layout und Konsolen-Modus", kw: "theme design farbe farbschema layout schrift dichte animation konsole steamdeck controller tv" },
    { tab: "servers", label: "Spiel und Server", hint: "Server-Einträge und Mod-Konflikte", kw: "server ip adresse verstecken ausblenden konflikt mod auflösen" },
    { tab: "import", label: "Importieren", hint: "Instanzen aus anderen Launchern übernehmen", kw: "import prism curseforge modrinth multimc atlauncher übernehmen" },
    { tab: "updates", label: "Updates und Hilfe", hint: "Launcher aktualisieren, Support", kw: "update version aktualisieren hilfe support discord" },
  ];

  const KMOD_CATS = [
    ["hud", "HUD"],
    ["performance", "Leistung"],
    ["visual", "Grafik"],
    ["gameplay", "Gameplay"],
    ["world", "Welt"],
    ["chat", "Chat und Stream"],
    ["extras", "Extras"],
  ];

  const KMOD = [
    ["fps", "FPS", "Bildwiederholrate anzeigen.", "hud"],
    ["ping", "Ping", "Latenz zum Server mit optionaler Jitter- und Stabilitätsanzeige.", "hud"],
    ["tps", "TPS", "Server-Ticks pro Sekunde.", "hud"],
    ["cps", "CPS", "Klicks pro Sekunde, links und rechts.", "hud"],
    ["keystrokes", "Keystrokes", "Zeigt gedrückte Tasten (W/A/S/D, Maus).", "hud"],
    ["coordinates", "Koordinaten", "Zeigt X / Y / Z und die Dimension.", "hud"],
    ["direction", "Richtung", "Blickrichtung als Kompass-Leiste oder Text.", "hud"],
    ["biome", "Biom", "Aktuelles Biom an der Spielerposition.", "hud"],
    ["clock", "Uhrzeit", "Echte Uhrzeit mit Sekunden.", "hud"],
    ["daycounter", "Tageszähler", "Zeigt den aktuellen Minecraft-Tag der Welt.", "hud"],
    ["speed", "Speed", "Bewegungsgeschwindigkeit in m/s.", "hud"],
    ["memory", "Arbeitsspeicher", "Zeigt den belegten RAM.", "hud"],
    ["entitycount", "Entitäten", "Anzahl geladener Entitäten.", "hud"],
    ["serverinfo", "Server-Info", "Adresse und Spielerzahl des Servers.", "hud"],
    ["armor", "Rüstungs-HUD", "Rüstung und Hand-Items mit Haltbarkeitsanzeige.", "hud"],
    ["potions", "Effekte", "Aktive Statuseffekte mit Restdauer.", "hud"],
    ["reach", "Reichweite", "Distanz des Angriffs-Treffers oder Ziels.", "hud"],
    ["combo", "Combo", "Aufeinanderfolgende Linksklicks der letzten 2 Sekunden.", "hud"],
    ["saturation", "Sättigung", "Nahrungssättigung und was die gehaltene Speise auffüllt.", "hud"],
    ["shield", "Schild-Status", "Grün bei bereitem Schild, rot bei Cooldown.", "hud"],
    ["itemcounter", "Item-Zähler", "Zählt frei wählbare Items wie Totems, Äpfel oder Perlen.", "hud"],
    ["totemcounter", "Totem-Zähler", "Verbrauchte Totems für Spieler und Gegner.", "hud"],
    ["invhud", "Inventar-Vorschau", "Zeigt die 27 Inventar-Slots dauerhaft auf dem HUD.", "hud"],
    ["geartiers", "Ausrüstungs-Tiers", "Material-Tier von Waffe und Rüstung (I bis V).", "hud"],
    ["itemchanges", "Item-Änderungen", "Aufgenommene und verbrauchte Items als Benachrichtigung.", "hud"],
    ["togglesprintdisplay", "Sprint-Anzeige", "Status von Sprint, Schleichen und Flugmodus.", "hud"],
    ["crosshair", "Fadenkreuz", "Modulares Fadenkreuz mit Angriffs- und Bogen-Indikator.", "hud"],

    ["kollegentricks", "Kollegen Tricks", "Wand- und Sichtfeld-Culling, Fast-Math und Thread-Boost ohne Qualitätsverlust.", "performance"],
    ["kollegendistance", "Kollegen Distance", "Bereits besuchte Chunks bleiben über das Server-Limit hinaus sichtbar, bis 64 Chunks.", "performance"],
    ["adaptiveperf", "Adaptive Performance", "Passt Render-Distanz und Grafik automatisch an die FPS an.", "performance"],
    ["cpuoffload", "CPU-Last", "Verschiebt Last von der GPU auf die CPU.", "performance"],
    ["maxfps", "Max FPS", "Wendet die aggressivsten Performance-Einstellungen auf einmal an.", "performance"],
    ["fastgraphics", "Fast Graphics", "Setzt die Grafik auf Schnell.", "performance"],
    ["noclouds", "Keine Wolken", "Blendet Wolken aus.", "performance"],
    ["smoothlighting", "Ohne weiche Beleuchtung", "Schaltet Ambient Occlusion aus.", "performance"],
    ["reducedparticles", "Partikel", "Steuert die Sichtbarkeit von Partikeln.", "performance"],

    ["kollegenshader", "Kollegen Shader", "Sättigung, Motion Blur, Kontrast und Bildfilter ohne Zusatz-Mods.", "visual"],
    ["zoom", "Zoom", "Heranzoomen ohne Maus-Verzögerung.", "visual"],
    ["fullbright", "Fullbright", "Permanente Helligkeit.", "visual"],
    ["nofog", "Kein Nebel", "Entfernt Nebel in Oberwelt, Nether, Wasser und Lava.", "visual"],
    ["freecam", "Freecam", "Kamera frei bewegen, unabhängig vom Spieler.", "visual"],
    ["fovmodifier", "FOV-Effekte", "Sprint-, Speed-, Bogen- und Flug-FOV einzeln regeln.", "visual"],
    ["hitboxes", "Hitboxen", "Entity-Hitboxen anpassen und dauerhaft anzeigen.", "visual"],
    ["hitcolor", "Hit Color", "Farbe, in der Entities bei Treffern aufleuchten.", "visual"],
    ["armortransparency", "Transparente Rüstung", "Deckkraft der Rüstungsteile einzeln anpassen.", "visual"],
    ["ownnametag", "Nametag verbergen", "Nametags, Titel und Level für dich oder andere ausblenden.", "visual"],
    ["waveycapes", "Wavey Capes", "Wellen- und Stoffbewegung für Umhänge.", "visual"],
    ["itemphysics", "Item Physics", "Items fallen realistisch und liegen flach am Boden.", "visual"],
    ["fightcamera", "Kampf-Kamera", "Intensität des Kamerawackelns bei Schaden.", "visual"],
    ["smoothswapping", "Flüssiger Item-Wechsel", "Weiche Animation beim Wechseln der Items.", "visual"],
    ["shieldsettings", "Schild-Einstellungen", "Größe, Position und Drehung des Schilds für freies Sichtfeld.", "visual"],
    ["spectatorhighlight", "Zuschauer-Leuchten", "Spieler im Zuschauermodus leuchten mit Kontur.", "visual"],
    ["afkcinematic", "AFK-Kamera", "Rotierende Kamera nach Inaktivität.", "visual"],

    ["autosprint", "Auto-Sprint", "Sprintet automatisch beim Vorwärtslaufen.", "gameplay"],
    ["togglesprint", "Toggle-Sprint", "Eine Taste schaltet permanenten Sprint um.", "gameplay"],
    ["togglesneak", "Toggle-Schleichen", "Eine Taste schaltet permanentes Schleichen um.", "gameplay"],
    ["autojump", "Auto-Jump", "Springt automatisch über Blöcke.", "gameplay"],
    ["autoreconnect", "Auto-Reconnect", "Verbindet nach einem Abbruch automatisch neu.", "gameplay"],
    ["invenhance", "Inventar-Helfer", "Suchleiste und Item-Sperre für Kisten und Inventar.", "gameplay"],
    ["controllersupport", "Controller-Support", "Gamepad-Steuerung und Menü-Navigation.", "gameplay"],

    ["timechanger", "Time Changer", "Friert die Tageszeit auf einen Wert ein.", "world"],
    ["weather", "Wetter", "Wetter manuell einstellen.", "world"],
    ["celestial", "Himmelskörper", "Sonne und Mond ausblenden.", "world"],
    ["lightleveloverlay", "Licht-Level", "Lichtlevel-Raster direkt auf dem Boden.", "world"],

    ["twitchchat", "Twitch-Chat", "Live-Chat mit 7TV-Unterstützung im Spielchat oder HUD.", "chat", { label: "Twitch verbinden", settings: "connections" }],
    ["autotext", "Schnellantwort", "Vorgefertigte Nachrichten per Hotkey in den Chat.", "chat"],
    ["clearchat", "Chat leeren", "Chat-Verlauf auf Tastendruck leeren.", "chat"],
    ["smoothscroll", "Flüssiges Scrollen", "Weiches Scrollen in Chat, Menüs und Server-Listen.", "chat"],

    ["clips", "Clips", "Die letzten Spielminuten auf Tastendruck sichern.", "extras", { label: "Einrichten", scroll: "kmodClips" }],
    ["emotes", "Emotes", "Emote-Rad zum Abspielen und Verwalten von Emotes.", "extras"],
    ["soundcontroller", "Sound-Controller", "Eigene Lautstärke für störende Spielgeräusche.", "extras"],
    ["games", "Minispiele", "Schach, Vier gewinnt, Tic-Tac-Toe, Snake, Tetris und mehr im Menü.", "extras"],
    ["switchers", "Konto- und Server-Wechsler", "Account und Server wechseln, ohne das Spiel zu verlassen.", "extras"],
    ["share", "Screenshots teilen", "Screenshots direkt aus dem Spiel teilen.", "extras"],
  ];

  const KMOD_CAT_LABEL = Object.fromEntries(KMOD_CATS);
  let currentTab = "home";
  let pendingSettingsTab = null;
  let kmodFilter = "all";
  let kmodPreset = "";

  function norm(t) {
    return String(t || "").toLowerCase().normalize("NFD").replace(/[̀-ͯ]/g, "").replace(/ß/g, "ss");
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  function openSettings(tab) {
    pendingSettingsTab = tab || null;
    const b = $("settingsBtn");
    if (b) b.click();
  }

  function goTo(tab) {
    if (typeof window.switchTab === "function") window.switchTab(tab);
  }

  function updateTopbar(tab) {
    const meta = PAGES[tab];
    if (!meta) return;
    const t = $("topbarTitle");
    const s = $("topbarSub");
    if (t) t.textContent = meta[0];
    if (s) s.textContent = meta[1];
  }

  function syncSidebar(tab) {
    const inWardrobe = tab === "skins" || tab === "capes" || tab === "cosmetics";
    document.querySelectorAll(".sidebar-link[data-tab]").forEach((l) => {
      const on = l.dataset.tab === tab || (inWardrobe && l.dataset.tab === "skins");
      l.classList.toggle("active", on);
      if (on) l.setAttribute("aria-current", "page");
      else l.removeAttribute("aria-current");
    });
    document.querySelectorAll(".wardrobe-head button").forEach((b) => {
      const on = b.dataset.go === tab;
      b.classList.toggle("active", on);
      b.setAttribute("aria-selected", on ? "true" : "false");
    });
  }

  function buildWardrobeHeads() {
    document.querySelectorAll(".wardrobe-head").forEach((host) => {
      host.innerHTML = "";
      host.setAttribute("role", "tablist");
      host.setAttribute("aria-label", "Garderobe");
      WARDROBE.forEach(([key, label]) => {
        const b = el("button", "wardrobe-tab", label);
        b.type = "button";
        b.dataset.go = key;
        b.setAttribute("role", "tab");
        b.addEventListener("click", () => goTo(key));
        host.append(b);
      });
    });
  }

  async function initKmodPage() {
    try {
      if (typeof initFeaturesTab === "function") initFeaturesTab();
    } catch (e) {}
    try {
      const s = await loadSettingsOnce();
      const cmt = $("companionModToggle");
      if (cmt && s) cmt.checked = !!s.companion_mod;
      const pmt = $("perfModsToggle");
      if (pmt && s) pmt.checked = s.perf_mods !== false;
    } catch (e) {}
    const ks = $("kmodSearch");
    if (ks) ks.value = kmodPreset;
    kmodPreset = "";
    kmodFilter = "all";
    renderKmod();
  }

  function renderKmod() {
    const chips = $("kmodChips");
    const grid = $("kmodGrid");
    if (!chips || !grid) return;
    const q = norm(($("kmodSearch") || {}).value || "");
    const match = (f) => !q || norm(f[1] + " " + f[2] + " " + KMOD_CAT_LABEL[f[3]]).includes(q);
    const counts = { all: 0 };
    KMOD.forEach((f) => {
      if (!match(f)) return;
      counts.all += 1;
      counts[f[3]] = (counts[f[3]] || 0) + 1;
    });
    chips.innerHTML = "";
    [["all", "Alle"]].concat(KMOD_CATS).forEach(([key, label]) => {
      const b = el("button", "chip" + (kmodFilter === key ? " active" : ""));
      b.type = "button";
      b.setAttribute("role", "tab");
      b.setAttribute("aria-selected", kmodFilter === key ? "true" : "false");
      b.append(document.createTextNode(label), el("span", "chip-count", String(counts[key] || 0)));
      b.addEventListener("click", () => {
        kmodFilter = key;
        renderKmod();
      });
      chips.append(b);
    });
    grid.innerHTML = "";
    const shown = KMOD.filter((f) => match(f) && (kmodFilter === "all" || f[3] === kmodFilter));
    if (shown.length === 0) {
      grid.append(el("div", "kmod-empty", "Kein Feature gefunden."));
      return;
    }
    shown.forEach((f) => {
      const card = el("article", "kmod-feature");
      card.setAttribute("role", "listitem");
      const top = el("div", "kmod-feature-top");
      top.append(el("h4", "kmod-feature-name", f[1]), el("span", "kmod-tag kmod-tag-" + f[3], KMOD_CAT_LABEL[f[3]]));
      card.append(top, el("p", "kmod-feature-desc", f[2]));
      const act = f[4];
      if (act) {
        const b = el("button", "btn-small kmod-feature-act", act.label);
        b.type = "button";
        b.addEventListener("click", () => {
          if (act.settings) openSettings(act.settings);
          if (act.scroll) {
            const t = $(act.scroll);
            if (t) t.scrollIntoView({ behavior: "smooth", block: "start" });
          }
        });
        card.append(b);
      }
      grid.append(card);
    });
  }

  const wrapSwitch = (() => {
    const orig = window.switchTab;
    return function (name) {
      currentTab = name;
      orig.apply(this, arguments);
      updateTopbar(name);
      syncSidebar(name);
      if (name === "kmod") initKmodPage();
      if (name === "capes" && typeof loadCapesView === "function") loadCapesView();
      if (name === "skins" && typeof loadSkinsLibrary === "function") loadSkinsLibrary();
      closePalette();
    };
  })();
  window.switchTab = wrapSwitch;

  (function wrapSettings() {
    const orig = window.showSettingsTab;
    window.showSettingsTab = function (t) {
      if (pendingSettingsTab && t === "connections") {
        t = pendingSettingsTab;
        pendingSettingsTab = null;
      }
      orig.call(this, t);
      if (t === "themes" && typeof initSteamDeckTab === "function") initSteamDeckTab();
      const tabs = document.querySelectorAll(".settings-tab");
      tabs.forEach((b) => b.setAttribute("aria-selected", b.dataset.tab === t ? "true" : "false"));
    };
  })();

  function syncAccount() {
    const status = $("authStatus");
    const loginBtn = $("authBtn");
    const loggedIn = !!(status && status.classList.contains("ok")) || !!(loginBtn && loginBtn.style.display === "none");
    const head = $("accountHead");
    const pw = $("pwAvatar");
    let src = "";
    let mcName = "";
    if (head && !head.hidden && head.getAttribute("src")) {
      src = head.getAttribute("src");
      const m = /\/avatar\/([^/?]+)/.exec(src);
      if (m) {
        try { mcName = decodeURIComponent(m[1]); } catch (e) { mcName = m[1]; }
      }
    } else if (pw && pw.getAttribute("src") && pw.style.display !== "none") {
      src = pw.getAttribute("src");
    }
    const pwName = $("pwName");
    const socialName = pwName ? pwName.textContent.trim() : "";
    const social = !!socialName && !/nicht verbunden/i.test(socialName);
    const hasProfile = loggedIn || social;
    const av = $("acctAv");
    const ph = document.querySelector(".acct-ph");
    const showAv = !!src && hasProfile;
    if (av) {
      if (showAv) {
        if (av.getAttribute("src") !== src) av.setAttribute("src", src);
        av.hidden = false;
      } else {
        av.hidden = true;
      }
    }
    if (ph) ph.hidden = showAv;
    const nameEl = $("acctName");
    if (nameEl) nameEl.textContent = mcName || (social ? socialName : loggedIn ? "Angemeldet" : "Nicht angemeldet");
    const dc = $("discordAccountName");
    const dcText = dc ? dc.textContent.trim() : "";
    const dcOn = !!dcText && !/nicht verbunden/i.test(dcText);
    const sub = $("acctSub");
    if (sub) sub.textContent = dcOn ? dcText : loggedIn ? "Profil öffnen" : "Konto verbinden";
    const dot = $("acctDot");
    if (dot) dot.classList.toggle("on", loggedIn);
    const acct = $("acct");
    if (acct) acct.classList.toggle("is-in", loggedIn);
    const main = $("profileWidget");
    if (main) main.style.display = hasProfile ? "" : "none";
  }

  function watchAccount() {
    const src = document.querySelector(".acct-src");
    if (!src) return;
    const obs = new MutationObserver(syncAccount);
    const opts = { subtree: true, childList: true, characterData: true, attributes: true };
    obs.observe(src, opts);
    const btn = $("authBtn");
    if (btn) obs.observe(btn, { attributes: true, attributeFilter: ["style", "class"] });
    syncAccount();
  }

  let palette = null;
  let palItems = [];
  let palIndex = 0;

  function paletteEntries() {
    const out = [];
    Object.keys(PAGES).forEach((k) => {
      if (k === "capes" || k === "cosmetics") return;
      out.push({ group: "Seiten", label: PAGES[k][0], hint: PAGES[k][1], kw: k === "skins" ? "skin skins garderobe" : k, run: () => goTo(k) });
    });
    out.push({ group: "Seiten", label: "Capes", hint: "Garderobe", kw: "cape capes umhang", run: () => goTo("capes") });
    out.push({ group: "Seiten", label: "Kosmetik", hint: "Garderobe", kw: "kosmetik cosmetics pets hut flügel", run: () => goTo("cosmetics") });
    SETTINGS.forEach((s) => out.push({ group: "Einstellungen", label: s.label, hint: s.hint, kw: s.kw, run: () => openSettings(s.tab) }));
    const insts = typeof instancesCache !== "undefined" && Array.isArray(instancesCache) ? instancesCache : [];
    insts.forEach((i) => {
      const meta = [i.version, i.loader].filter(Boolean).join(" · ");
      out.push({ group: "Instanzen", label: i.name, hint: "Starten · " + meta, kw: "instanz spielen starten " + meta, run: () => window.launchGame && window.launchGame(i.name) });
      out.push({ group: "Instanzen", label: i.name + " verwalten", hint: "Mods, Einstellungen, Welten", kw: "verwalten mods bearbeiten " + i.name, run: () => window.openManage && window.openManage(i) });
    });
    KMOD.forEach((f) => {
      out.push({ group: "Kollegen-Mod", label: f[1], hint: f[2], kw: KMOD_CAT_LABEL[f[3]], run: () => { kmodPreset = f[1]; goTo("kmod"); } });
    });
    return out;
  }

  function score(entry, q) {
    const l = norm(entry.label);
    if (!q) return entry.group === "Seiten" || entry.group === "Einstellungen" ? 1 : 0;
    if (l === q) return 100;
    if (l.startsWith(q)) return 80;
    if (l.includes(q)) return 60;
    if (norm(entry.kw + " " + entry.hint).includes(q)) return 30;
    return -1;
  }

  function renderPalette() {
    const input = palette.querySelector(".pal-input");
    const list = palette.querySelector(".pal-list");
    const q = norm(input.value.trim());
    const all = paletteEntries()
      .map((e) => ({ e, s: score(e, q) }))
      .filter((x) => x.s >= (q ? 0 : 1))
      .sort((a, b) => b.s - a.s)
      .slice(0, q ? 24 : 12)
      .map((x) => x.e);
    palItems = all;
    if (palIndex >= palItems.length) palIndex = 0;
    list.innerHTML = "";
    if (palItems.length === 0) {
      list.append(el("div", "pal-empty", "Nichts gefunden."));
      return;
    }
    let lastGroup = "";
    palItems.forEach((it, idx) => {
      if (it.group !== lastGroup) {
        lastGroup = it.group;
        list.append(el("div", "pal-group", it.group));
      }
      const row = el("button", "pal-row" + (idx === palIndex ? " active" : ""));
      row.type = "button";
      row.setAttribute("role", "option");
      row.setAttribute("aria-selected", idx === palIndex ? "true" : "false");
      row.append(el("span", "pal-label", it.label), el("span", "pal-hint", it.hint));
      row.addEventListener("mousemove", () => {
        if (palIndex === idx) return;
        palIndex = idx;
        list.querySelectorAll(".pal-row").forEach((r, i) => r.classList.toggle("active", i === idx));
      });
      row.addEventListener("click", () => runPalette(idx));
      list.append(row);
    });
    const active = list.querySelector(".pal-row.active");
    if (active) active.scrollIntoView({ block: "nearest" });
  }

  function runPalette(idx) {
    const it = palItems[idx];
    if (!it) return;
    closePalette();
    setTimeout(() => {
      try { it.run(); } catch (e) {}
    }, 0);
  }

  function buildPalette() {
    palette = el("div", "pal-overlay");
    palette.hidden = true;
    palette.setAttribute("role", "dialog");
    palette.setAttribute("aria-modal", "true");
    palette.setAttribute("aria-label", "Suche");
    const box = el("div", "pal-box");
    const input = el("input", "pal-input");
    input.type = "text";
    input.placeholder = "Seite, Einstellung, Instanz oder Feature suchen";
    input.setAttribute("aria-label", "Suche");
    input.autocomplete = "off";
    input.spellcheck = false;
    const list = el("div", "pal-list");
    list.setAttribute("role", "listbox");
    const foot = el("div", "pal-foot");
    foot.innerHTML = "<span><kbd>↑</kbd><kbd>↓</kbd> wählen</span><span><kbd>Enter</kbd> öffnen</span><span><kbd>Esc</kbd> schließen</span>";
    box.append(input, list, foot);
    palette.append(box);
    document.body.append(palette);
    input.addEventListener("input", () => {
      palIndex = 0;
      renderPalette();
    });
    input.addEventListener("keydown", (e) => {
      if (e.key === "ArrowDown") {
        e.preventDefault();
        palIndex = palItems.length ? (palIndex + 1) % palItems.length : 0;
        renderPalette();
      } else if (e.key === "ArrowUp") {
        e.preventDefault();
        palIndex = palItems.length ? (palIndex - 1 + palItems.length) % palItems.length : 0;
        renderPalette();
      } else if (e.key === "Enter") {
        e.preventDefault();
        runPalette(palIndex);
      }
    });
    palette.addEventListener("mousedown", (e) => {
      if (e.target === palette) closePalette();
    });
  }

  function openPalette() {
    if (!palette) buildPalette();
    palette.hidden = false;
    const input = palette.querySelector(".pal-input");
    input.value = "";
    palIndex = 0;
    renderPalette();
    input.focus();
  }

  function closePalette() {
    if (palette && !palette.hidden) palette.hidden = true;
  }

  document.addEventListener("keydown", (e) => {
    if ((e.ctrlKey || e.metaKey) && !e.shiftKey && !e.altKey && e.key.toLowerCase() === "k") {
      if (document.body.classList.contains("console-mode")) return;
      e.preventDefault();
      if (palette && !palette.hidden) closePalette();
      else openPalette();
    } else if (e.key === "Escape" && palette && !palette.hidden) {
      e.preventDefault();
      e.stopPropagation();
      closePalette();
    }
  }, true);

  document.addEventListener("click", (e) => {
    const go = e.target.closest("[data-goto-tab]");
    if (go) {
      const closeId = go.dataset.close;
      if (closeId && $(closeId)) $(closeId).click();
      goTo(go.dataset.gotoTab);
      return;
    }
    const os = e.target.closest("[data-open-settings]");
    if (os) openSettings(os.dataset.openSettings);
  });

  function wireLoaderPills() {
    const sel = $("iLoader");
    const host = $("iLoaderPills");
    const hint = $("iLoaderHint");
    if (!sel || !host) return;
    const info = {
      vanilla: ["Vanilla", "Reines Minecraft ohne Mods."],
      fabric: ["Fabric", "Empfohlen: leicht, schnell und die Basis der Kollegen-Mod."],
      forge: ["Forge", "Für klassische Mod-Packs. Die Kollegen-Mod läuft hier nicht."],
      neoforge: ["NeoForge", "Moderner Forge-Nachfolger. Die Kollegen-Mod läuft hier nicht."],
    };
    const build = () => {
      host.innerHTML = "";
      Array.from(sel.options).forEach((o) => {
        const meta = info[o.value] || [o.textContent, ""];
        const b = el("button", "loader-pill", meta[0]);
        b.type = "button";
        b.dataset.value = o.value;
        b.setAttribute("role", "radio");
        b.addEventListener("click", () => {
          sel.value = o.value;
          sel.dispatchEvent(new Event("change", { bubbles: true }));
          sync();
        });
        host.append(b);
      });
      sync();
    };
    const sync = () => {
      host.querySelectorAll(".loader-pill").forEach((b) => {
        const on = b.dataset.value === sel.value;
        b.classList.toggle("active", on);
        b.setAttribute("aria-checked", on ? "true" : "false");
      });
      if (hint) hint.textContent = (info[sel.value] || ["", ""])[1];
    };
    build();
    sel.addEventListener("change", sync);
    new MutationObserver(sync).observe(sel, { attributes: true, attributeFilter: ["value"] });
  }

  function wireSupport() {
    const b = $("supportDiscordBtn");
    if (!b) return;
    b.addEventListener("click", async () => {
      const url = "https://kollegen.me/discord";
      try {
        const t = window.__TAURI__;
        if (t && t.opener && t.opener.openUrl) await t.opener.openUrl(url);
        else if (t && t.shell && t.shell.open) await t.shell.open(url);
        else window.open(url, "_blank");
      } catch (e) {
        window.open(url, "_blank");
      }
    });
  }

  function init() {
    buildWardrobeHeads();
    wireLoaderPills();
    wireSupport();
    watchAccount();
    const sb = $("searchBtn");
    if (sb) sb.addEventListener("click", openPalette);
    const ks = $("kmodSearch");
    if (ks) ks.addEventListener("input", renderKmod);
    const pw = $("profileWidget");
    if (pw) {
      pw.addEventListener("keydown", (e) => {
        if (e.key === "Enter" || e.key === " ") {
          e.preventDefault();
          pw.click();
        }
      });
    }
    updateTopbar(currentTab);
    syncSidebar(currentTab);
  }

  init();
})();
