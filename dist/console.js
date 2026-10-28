let consoleNavActive = false;
let consoleFocusables = [];
let consoleFocusEl = null;
let consolePickerTarget = null;
let kcModalStack = [];
let kcClockTimer = null;
let kcObserver = null;
let kcSyncTimer = null;
let kcStorePoll = null;
let kcLastInputAt = 0;
let kcLastCenter = { x: 0, y: 0 };
let kcHeroInst = null;
let kcWantFirst = 0;
let kcToastTimer = null;

const KC_DISCORD_URL = "https://kollegen.me/discord";
const KC_SITE_URL = "https://kollegen.me/minecraft";
const KC_ISSUES_URL = "https://github.com/FluffyBento/KollegenClient/issues";

const KC_ICONS = {
  play: '<path d="M8 5.5v13a1 1 0 0 0 1.5.86l10.5-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z" fill="currentColor" stroke="none"/>',
  store: '<path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/>',
  cosmetics: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 15l.9 2.1L22 18l-2.1.9L19 21l-.9-2.1L16 18l2.1-.9z"/>',
  socials: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  support: '<circle cx="12" cy="12" r="9"/><path d="M9.3 9.3a2.8 2.8 0 1 1 4 2.5c-.9.5-1.3 1-1.3 2"/><circle cx="12" cy="17.2" r=".7" fill="currentColor"/>',
  gear: '<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.5V21a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1-1.5 1.7 1.7 0 0 0-1.9.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.9.3h0a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1a1.7 1.7 0 0 0 1 1.5h0a1.7 1.7 0 0 0 1.9-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.9v0a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z"/>',
  plus: '<path d="M12 5v14M5 12h14"/>',
  discord: '<path d="M21 12a8 8 0 0 1-11.5 7.2L4 20l1-4.6A8 8 0 1 1 21 12z"/><path d="M8.5 10.5h7M8.5 13.5h4.5"/>',
  bug: '<rect x="8" y="8" width="8" height="12" rx="4"/><path d="M12 8V5M8 12H4M16 12h4M8 16l-3 2M16 16l3 2M9 5l1.500 2M15 5l-1.500 2"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M16 13H8M16 17H8M10 9H8"/>',
  copy: '<rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/>',
  globe: '<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3a14 14 0 0 1 0 18M12 3a14 14 0 0 0 0 18"/>',
  user: '<path d="M20 21v-2a4 4 0 0 0-4-4H8a4 4 0 0 0-4 4v2"/><circle cx="12" cy="7" r="4"/>',
  manage: '<path d="M4 6h9M17 6h3M4 12h3M11 12h9M4 18h11M19 18h1"/><circle cx="15" cy="6" r="2"/><circle cx="9" cy="12" r="2"/><circle cx="17" cy="18" r="2"/>',
  chevron: '<path d="m9 6 6 6-6 6"/>'
};

const KC_TABS = [
  { id: "home", label: "Spielen", icon: "play" },
  { id: "store", label: "Store", icon: "store" },
  { id: "cosmetics", label: "Cosmetics", icon: "cosmetics" },
  { id: "socials", label: "Sozial", icon: "socials" },
  { id: "support", label: "Support", icon: "support" }
];

const KC_FAQ = [
  {
    q: "Wie steuere ich den Launcher mit dem Controller?",
    keys: [
      ["dpad", "Auswahl bewegen (auch linker Stick)"],
      ["a", "Auswählen / Starten"],
      ["b", "Zurück / Schließen"],
      ["x", "Instanz verwalten"],
      ["y", "Neue Instanz, im Store Sortierung wechseln"],
      ["lb", "Vorheriger Tab"],
      ["rb", "Nächster Tab"],
      ["lt", "Im Store: vorherige Kategorie, sonst Seite hoch"],
      ["rt", "Im Store: nächste Kategorie, sonst Seite runter"],
      ["rs", "Seite scrollen (rechter Stick)"],
      ["start", "Einstellungen"]
    ]
  },
  {
    q: "Wie steuere ich Minecraft mit dem Controller?",
    keys: [
      ["ls", "Bewegen, Klick auf den Stick = Sprinten. In Menüs: Zeiger"],
      ["rs", "Umsehen, Klick auf den Stick = Perspektive. In Menüs: scrollen"],
      ["rt", "Angreifen / Abbauen"],
      ["lt", "Benutzen / Platzieren"],
      ["a", "Springen. In Menüs: Klick"],
      ["b", "Schleichen. In Menüs: Zurück"],
      ["x", "Inventar öffnen. In Menüs: Rechtsklick"],
      ["lb", "Hotbar nach links"],
      ["rb", "Hotbar nach rechts"],
      ["select", "Chat"],
      ["start", "Pause"]
    ]
  },
  {
    a: "Der Store und die Cosmetics brauchen dein verbundenes Discord-Konto. Verbinde es unter Einstellungen > Verbindungen und öffne danach den Store erneut.",
    q: "Store oder Cosmetics laden nicht"
  },
  {
    a: "Aktiviere den Konsolen-Modus unter Einstellungen > SteamDeck. Danach nimmt der Launcher Eingaben deines Controllers entgegen. In der Diagnose weiter unten siehst du, ob Controller-Eingaben ankommen.",
    q: "Der Controller reagiert nicht"
  },
  {
    a: "Wähle ein Textfeld mit A aus. Auf dem Steam Deck öffnest du die Bildschirmtastatur mit Steam + X. Mit B beendest du die Eingabe.",
    q: "Wie tippe ich Text ein?"
  },
  {
    a: "Tritt unserem Discord bei und schreib uns. Hänge nach Möglichkeit die Logs an. Du kopierst sie oben mit Logs kopieren.",
    q: "Ich brauche weitere Hilfe"
  }
];

function kcEl(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}

function kcIcon(name) {
  return '<svg class="kc-ico" viewBox="0 0 24 24" aria-hidden="true">' + (KC_ICONS[name] || "") + "</svg>";
}

function kcGlyph(kind) {
  const labels = { a: "A", b: "B", x: "X", y: "Y", lb: "LB", rb: "RB", lt: "LT", rt: "RT", start: "≡", select: "⧉", rs: "RS", ls: "LS", dpad: "✥" };
  const s = kcEl("i", "kc-btn kc-btn-" + kind, labels[kind] || kind);
  return s;
}

function kcOpenUrl(url) {
  try {
    invoke("open_url", { url }).catch(() => { try { window.open(url, "_blank"); } catch (e) {} });
  } catch (e) {
    try { window.open(url, "_blank"); } catch (e2) {}
  }
}

function kcToast(msg) {
  let t = document.getElementById("kcToast");
  if (!t) {
    t = kcEl("div", "kc-toast");
    t.id = "kcToast";
    document.body.appendChild(t);
  }
  t.textContent = msg;
  t.classList.add("show");
  if (kcToastTimer) clearTimeout(kcToastTimer);
  kcToastTimer = setTimeout(() => t.classList.remove("show"), 2200);
}

function kcActiveTab() {
  const p = document.querySelector(".tab-panel.active");
  return p ? p.dataset.tab : "";
}

function kcOpenModals() {
  return Array.from(document.querySelectorAll(".modal")).filter((m) => m.style.display && m.style.display !== "none");
}

function kcTopModal() {
  const l = kcOpenModals();
  return l.length ? l[l.length - 1] : null;
}

function kcIsEditing() {
  const a = document.activeElement;
  if (!a) return false;
  if (a.tagName === "TEXTAREA") return true;
  if (a.tagName !== "INPUT") return false;
  return !/^(checkbox|radio|button|submit|range|file|color)$/.test(a.type);
}

function kcAvatarSrc() {
  const h = document.getElementById("accountHead");
  if (h && !h.hidden && h.getAttribute("src")) return h.src;
  const p = document.getElementById("pwAvatar");
  if (p && p.getAttribute("src")) return p.src;
  return "";
}

function kcPlayerName() {
  const n = document.getElementById("pwName");
  const txt = n ? n.textContent.trim() : "";
  if (txt && txt !== "nicht verbunden") return txt;
  const h = document.getElementById("accountHead");
  const m = h && h.getAttribute("src") ? /avatar\/([^/]+)\//.exec(h.getAttribute("src")) : null;
  if (m) return decodeURIComponent(m[1]);
  return "Spieler";
}

function kcNeedsLogin() {
  const b = document.getElementById("authBtn");
  return !!b && b.style.display !== "none";
}

function ensureConsoleChrome() {
  if (document.getElementById("kcTopbar")) return;
  const top = kcEl("div", "kc-topbar");
  top.id = "kcTopbar";

  const brand = kcEl("div", "kc-brand");
  const logo = kcEl("img");
  logo.src = "Kollegen.png";
  logo.alt = "";
  brand.append(logo, kcEl("span", "", "Kollegen Client"));

  const nav = kcEl("nav", "kc-tabs");
  nav.setAttribute("aria-label", "Hauptnavigation");
  nav.append(kcGlyph("lb"));
  KC_TABS.forEach((t) => {
    const b = kcEl("button", "kc-tab");
    b.type = "button";
    b.dataset.tab = t.id;
    b.dataset.kcKey = "tab:" + t.id;
    b.innerHTML = kcIcon(t.icon) + "<span>" + t.label + "</span>";
    b.onclick = () => switchTab(t.id);
    nav.append(b);
  });
  nav.append(kcGlyph("rb"));

  const status = kcEl("div", "kc-status");
  const wallet = kcEl("div", "kc-wallet");
  wallet.id = "kcWallet";
  wallet.hidden = true;
  const acct = kcEl("button", "kc-chip kc-account");
  acct.type = "button";
  acct.id = "kcAccount";
  acct.dataset.kcKey = "account";
  acct.innerHTML = '<img id="kcAvatar" alt=""><span id="kcName">…</span>';
  acct.onclick = () => {
    if (kcNeedsLogin()) document.getElementById("authBtn").click();
    else openProfileModal();
  };
  const gear = kcEl("button", "kc-chip kc-gear");
  gear.type = "button";
  gear.id = "kcSettings";
  gear.dataset.kcKey = "settings";
  gear.setAttribute("aria-label", "Einstellungen");
  gear.innerHTML = kcIcon("gear");
  gear.onclick = () => document.getElementById("settingsBtn").click();
  const clock = kcEl("div", "kc-clock");
  clock.id = "kcClock";
  status.append(wallet, acct, gear, clock);

  top.append(brand, nav, status);
  document.body.appendChild(top);

  const hints = kcEl("div", "kc-hints");
  hints.id = "kcHints";
  hints.append(kcEl("div", "kc-hints-l"), kcEl("div", "kc-hints-r"));
  document.body.appendChild(hints);
}

function removeConsoleChrome() {
  ["kcTopbar", "kcHints", "kcToast"].forEach((id) => {
    const e = document.getElementById(id);
    if (e) e.remove();
  });
}

function kcSetText(el, txt) {
  if (el && el.textContent !== txt) el.textContent = txt;
}

function kcSetShown(el, show) {
  const want = show ? "" : "none";
  if (el && el.style.display !== want) el.style.display = want;
}

function kcSetAvatar(img, src) {
  if (!img) return;
  if (src && img.getAttribute("src") !== src) img.src = src;
  kcSetShown(img, !!src);
}

function kcTick() {
  kcSetText(document.getElementById("kcClock"), new Date().toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" }));
  const wallet = document.getElementById("kcWallet");
  const st = window.kmState;
  const hasWallet = !!(st && !st.needsAuth && typeof st.points === "number");
  if (wallet) {
    if (wallet.hidden === hasWallet) wallet.hidden = !hasWallet;
    if (hasWallet) kcSetText(wallet, st.points + " Punkte · Lv " + (st.level || 1));
  }
  const login = kcNeedsLogin();
  const src = login ? "" : kcAvatarSrc();
  kcSetText(document.getElementById("kcName"), login ? "Anmelden" : kcPlayerName());
  kcSetAvatar(document.getElementById("kcAvatar"), src);
  kcSetAvatar(document.querySelector(".kc-player-av"), src);
  kcSetText(document.querySelector(".kc-player-name"), login ? "Nicht angemeldet" : kcPlayerName());
  const bits = [login ? "Mit Microsoft anmelden, um zu spielen" : "Microsoft verbunden"];
  if (hasWallet && typeof st.level === "number") bits.push("Level " + st.level);
  kcSetText(document.querySelector(".kc-player-sub"), bits.join(" · "));
}

function kcSyncTabs(name) {
  document.body.dataset.kcTab = name;
  kcWantFirst = Date.now() + 2500;
  document.querySelectorAll(".kc-tab").forEach((b) => b.classList.toggle("active", b.dataset.tab === name));
  try { window.scrollTo(0, 0); } catch (e) {}
  if (name === "support") renderConsoleSupport();
}

function kcBuildChips(inst) {
  const wrap = kcEl("div", "kc-hero-chips");
  const loader = String(inst.loader || "vanilla");
  const c1 = kcEl("span", "kc-chip-tag", inst.version);
  const c2 = kcEl("span", "kc-chip-tag kc-chip-loader", loader.charAt(0).toUpperCase() + loader.slice(1));
  c2.style.setProperty("--chip-rgb", loaderRgb(loader));
  wrap.append(c1, c2);
  if (Array.isArray(inst.mods) && inst.mods.length) wrap.append(kcEl("span", "kc-chip-tag", inst.mods.length + " Mods"));
  if (inst.memory_max) wrap.append(kcEl("span", "kc-chip-tag", "RAM " + inst.memory_max));
  return wrap;
}

function kcFillHero(hero, inst) {
  kcHeroInst = inst ? inst.name : null;
  const copy = hero.querySelector(".kc-hero-copy");
  const art = hero.querySelector(".kc-hero-art");
  copy.innerHTML = "";
  if (!inst) {
    hero.style.setProperty("--hero-rgb", "var(--accent-rgb)");
    art.innerHTML = "";
    copy.append(kcEl("div", "kc-eyebrow", "Willkommen"));
    copy.append(kcEl("h1", "kc-hero-title", "Los geht es"));
    copy.append(kcEl("div", "kc-hero-sub", "Du hast noch keine Instanz. Erstelle deine erste und starte Minecraft."));
    const actions = kcEl("div", "kc-hero-actions");
    const b = kcEl("button", "kc-btn-hero primary");
    b.type = "button";
    b.dataset.kcKey = "hero-create";
    b.innerHTML = kcIcon("plus") + "<span>Instanz erstellen</span>";
    b.onclick = () => switchTab("create");
    actions.append(b);
    copy.append(actions);
    return;
  }
  hero.style.setProperty("--hero-rgb", loaderRgb(inst.loader));
  art.innerHTML = instIconSVG(inst.name, inst.loader);
  const played = inst.last_played ? "Zuletzt gespielt · " + relativeTime(inst.last_played) : "Noch nie gespielt";
  copy.append(kcEl("div", "kc-eyebrow", played));
  copy.append(kcEl("h1", "kc-hero-title", inst.name));
  copy.append(kcBuildChips(inst));
  const actions = kcEl("div", "kc-hero-actions");
  const play = kcEl("button", "kc-btn-hero primary");
  play.type = "button";
  play.dataset.inst = inst.name;
  play.dataset.kcKey = "hero-play";
  play.innerHTML = kcIcon("play") + "<span>Spielen</span>";
  play.onclick = () => launchGame(inst.name);
  const manage = kcEl("button", "kc-btn-hero");
  manage.type = "button";
  manage.dataset.inst = inst.name;
  manage.dataset.kcKey = "hero-manage";
  manage.innerHTML = kcIcon("manage") + "<span>Verwalten</span>";
  manage.onclick = () => openManage(inst);
  actions.append(play, manage);
  copy.append(actions);
}

function kcBuildHero(inst) {
  const hero = kcEl("section", "kc-hero");
  hero.append(kcEl("div", "kc-hero-art"), kcEl("div", "kc-hero-copy"));
  const player = kcEl("div", "kc-player");
  const av = kcEl("img", "kc-player-av");
  av.alt = "";
  const meta = kcEl("div", "kc-player-meta");
  meta.append(kcEl("div", "kc-player-name", "…"), kcEl("div", "kc-player-sub", ""));
  player.append(av, meta);
  hero.append(player);
  kcFillHero(hero, inst);
  return hero;
}

function kcRowHead(title, count) {
  const head = kcEl("div", "kc-row-head");
  head.append(kcEl("h2", "", title));
  if (count != null) head.append(kcEl("span", "kc-row-count", String(count)));
  return head;
}

function kcLinkTile(key, icon, title, sub, onclick, extra) {
  const t = kcEl("div", "kc-tile kc-link" + (extra ? " " + extra : ""));
  t.tabIndex = 0;
  t.setAttribute("role", "button");
  t.dataset.kcKey = key;
  const art = kcEl("div", "kc-tile-art kc-link-art");
  art.innerHTML = kcIcon(icon);
  const body = kcEl("div", "kc-tile-body");
  body.append(kcEl("div", "kc-tile-title", title), kcEl("div", "kc-tile-meta", sub));
  t.append(art, body);
  t.onclick = onclick;
  return t;
}

function kcBuildInstanceRow(list) {
  const row = kcEl("section", "kc-row");
  row.append(kcRowHead("Deine Instanzen", list.length));
  const track = kcEl("div", "kc-track");
  list.forEach((inst) => {
    const t = kcEl("div", "kc-tile kc-inst");
    t.tabIndex = 0;
    t.setAttribute("role", "button");
    t.dataset.inst = inst.name;
    t.dataset.kcKey = "inst:" + inst.name;
    t.style.setProperty("--tile-rgb", loaderRgb(inst.loader));
    const art = kcEl("div", "kc-tile-art");
    art.innerHTML = instIconSVG(inst.name, inst.loader);
    const body = kcEl("div", "kc-tile-body");
    const loader = String(inst.loader || "vanilla");
    body.append(
      kcEl("div", "kc-tile-title", inst.name),
      kcEl("div", "kc-tile-meta", inst.version + " · " + loader.charAt(0).toUpperCase() + loader.slice(1)),
      kcEl("div", "kc-tile-sub", inst.last_played ? relativeTime(inst.last_played) : "Noch nie gespielt")
    );
    t.append(art, body);
    t.onclick = () => launchGame(inst.name);
    track.append(t);
  });
  track.append(kcLinkTile("new-instance", "plus", "Neue Instanz", "Version und Loader wählen", () => switchTab("create"), "kc-new"));
  row.append(track);
  return row;
}

function kcStoreCandidates() {
  const cat = Array.isArray(window.kmCat) ? window.kmCat : [];
  const rank = { common: 0, rare: 1, epic: 2, legendary: 3 };
  return cat
    .filter((i) => i && i.category !== "package" && !i.hidden)
    .sort((a, b) => ((b.featured ? 1 : 0) - (a.featured ? 1 : 0)) || ((rank[b.rarity] || 0) - (rank[a.rarity] || 0)) || (a.price - b.price))
    .slice(0, 10);
}

function kcBuildStoreRow() {
  const row = kcEl("section", "kc-row kc-row-store");
  row.append(kcRowHead("Store-Highlights"));
  const track = kcEl("div", "kc-track");
  const items = kcStoreCandidates();
  const tools = window.kollegenStore;
  items.forEach((item) => {
    const t = kcEl("div", "kc-tile kc-shop rar-" + (item.rarity || "common"));
    t.tabIndex = 0;
    t.setAttribute("role", "button");
    t.dataset.kcKey = "shop:" + item.id;
    const pv = kcEl("div", "kc-tile-art kc-shop-pv");
    try { pv.innerHTML = tools ? tools.preview(item) : ""; } catch (e) { pv.innerHTML = ""; }
    const body = kcEl("div", "kc-tile-body");
    body.append(
      kcEl("div", "kc-tile-title", item.name),
      kcEl("div", "kc-tile-meta", item.owned ? "Im Besitz" : item.price + " Punkte")
    );
    t.append(pv, body);
    t.onclick = () => {
      if (tools) tools.open(item.id);
      else switchTab("store");
    };
    track.append(t);
  });
  track.append(kcLinkTile(
    "store-all",
    "store",
    "Zum Store",
    items.length ? "Alle Items ansehen" : (window.kmState && window.kmState.needsAuth ? "Mit Discord verbinden" : "Items ansehen"),
    () => switchTab("store")
  ));
  row.append(track);
  return row;
}

function kcBuildCommunityRow() {
  const row = kcEl("section", "kc-row");
  row.append(kcRowHead("Community und Hilfe"));
  const track = kcEl("div", "kc-track");
  track.append(
    kcLinkTile("go-discord", "discord", "Discord", "Community beitreten", () => kcOpenUrl(KC_DISCORD_URL)),
    kcLinkTile("go-cosmetics", "cosmetics", "Cosmetics", "Ausrüsten und ansehen", () => switchTab("cosmetics")),
    kcLinkTile("go-socials", "socials", "Sozial", "Freunde und Gruppen", () => switchTab("socials")),
    kcLinkTile("go-support", "support", "Support", "Hilfe und Anleitungen", () => switchTab("support")),
    kcLinkTile("go-site", "globe", "Website", "kollegen.me/minecraft", () => kcOpenUrl(KC_SITE_URL))
  );
  row.append(track);
  return row;
}

function kcRefreshStoreRow() {
  const home = document.querySelector("#homePanel .kc-home");
  if (!home) return;
  const old = home.querySelector(".kc-row-store");
  if (!old) return;
  old.replaceWith(kcBuildStoreRow());
  kcScheduleSync();
}

function kcWatchStore() {
  if (kcStorePoll) clearInterval(kcStorePoll);
  if (Array.isArray(window.kmCat)) return;
  let tries = 0;
  kcStorePoll = setInterval(() => {
    tries++;
    if (Array.isArray(window.kmCat) || tries > 12) {
      clearInterval(kcStorePoll);
      kcStorePoll = null;
      if (Array.isArray(window.kmCat)) kcRefreshStoreRow();
    }
  }, 1200);
}

function renderConsoleHome() {
  ensureConsoleChrome();
  const panel = document.getElementById("homePanel");
  if (!panel) return;
  const list = sortedInstances(instancesCache || []);
  const wrap = kcEl("div", "kc-home");
  const heroInst = (kcHeroInst && list.find((i) => i.name === kcHeroInst)) || list[0] || null;
  wrap.append(kcBuildHero(heroInst), kcBuildInstanceRow(list), kcBuildStoreRow(), kcBuildCommunityRow());
  panel.innerHTML = "";
  panel.append(wrap);
  kcSyncTabs(kcActiveTab() || "home");
  kcTick();
  kcWatchStore();
  kcScheduleSync();
}

function renderConsoleSupport() {
  const panel = document.getElementById("supportPanel");
  if (!panel) return;
  let box = panel.querySelector(".kc-support");
  if (!box) {
    box = kcEl("div", "kc-support");
    panel.append(box);
  }
  box.innerHTML = "";

  const cards = kcEl("div", "kc-cards");
  const mk = (key, icon, title, sub, fn) => {
    const c = kcEl("div", "kc-card");
    c.tabIndex = 0;
    c.setAttribute("role", "button");
    c.dataset.kcKey = key;
    const ic = kcEl("div", "kc-card-ico");
    ic.innerHTML = kcIcon(icon);
    const tx = kcEl("div", "kc-card-text");
    tx.append(kcEl("div", "kc-card-title", title), kcEl("div", "kc-card-sub", sub));
    c.append(ic, tx);
    c.onclick = fn;
    return c;
  };
  cards.append(
    mk("sup-discord", "discord", "Discord-Support", "Stell deine Frage in unserer Community", () => kcOpenUrl(KC_DISCORD_URL)),
    mk("sup-issue", "bug", "Fehler melden", "Problem auf GitHub einreichen", () => kcOpenUrl(KC_ISSUES_URL)),
    mk("sup-copylogs", "copy", "Logs kopieren", "Für deine Support-Anfrage", () => kcCopyLogs()),
    mk("sup-logs", "file", "Logs ansehen", "Launcher- und Spiel-Logs öffnen", () => switchTab("logs")),
    mk("sup-site", "globe", "Website", "kollegen.me/minecraft", () => kcOpenUrl(KC_SITE_URL)),
    mk("sup-profile", "user", "Profil", "Konto und Profil verwalten", () => openProfileModal())
  );
  box.append(cards);

  const faqHead = kcEl("h3", "kc-sec", "Häufige Fragen");
  box.append(faqHead);
  const faq = kcEl("div", "kc-faq");
  KC_FAQ.forEach((f, i) => {
    const item = kcEl("div", "kc-faq-item");
    const q = kcEl("button", "kc-faq-q");
    q.type = "button";
    q.dataset.kcKey = "faq:" + i;
    q.innerHTML = '<span class="kc-faq-chev">' + kcIcon("chevron") + "</span><span></span>";
    q.lastChild.textContent = f.q;
    const a = kcEl("div", "kc-faq-a");
    if (f.keys) {
      const grid = kcEl("div", "kc-keys");
      f.keys.forEach((k) => {
        const row = kcEl("div", "kc-key-row");
        row.append(kcGlyph(k[0]), kcEl("span", "", k[1]));
        grid.append(row);
      });
      a.append(grid);
    } else {
      a.append(kcEl("p", "", f.a));
    }
    q.onclick = () => item.classList.toggle("open");
    item.append(q, a);
    faq.append(item);
  });
  box.append(faq);

  box.append(kcEl("h3", "kc-sec", "Diagnose"));
  const diag = kcEl("div", "kc-diag");
  const lines = kcDiagLines();
  lines.forEach((l) => {
    const r = kcEl("div", "kc-diag-row");
    r.append(kcEl("span", "", l[0]), kcEl("b", "", l[1]));
    diag.append(r);
  });
  const copy = kcEl("button", "kc-btn-hero");
  copy.type = "button";
  copy.dataset.kcKey = "diag-copy";
  copy.innerHTML = kcIcon("copy") + "<span>Diagnose kopieren</span>";
  copy.onclick = async () => {
    try { await copyText(lines.map((l) => l[0] + ": " + l[1]).join("\n")); kcToast("Diagnose kopiert"); } catch (e) { kcToast("Kopieren fehlgeschlagen"); }
  };
  diag.append(copy);
  box.append(diag);
  kcScheduleSync();
}

function kcDiagLines() {
  const v = document.getElementById("versionBadge");
  const st = window.kmState;
  const ago = kcLastInputAt ? Math.max(0, Math.round((Date.now() - kcLastInputAt) / 1000)) : -1;
  return [
    ["Launcher", v ? v.textContent.trim() : "?"],
    ["Konsolen-Modus", consoleNavActive ? "an" : "aus"],
    ["Controller-Eingaben", ago < 0 ? "noch keine erkannt" : "zuletzt vor " + ago + " s"],
    ["Microsoft-Konto", kcNeedsLogin() ? "nicht angemeldet" : "verbunden"],
    ["Store", Array.isArray(window.kmCat) ? "geladen (" + window.kmCat.length + " Items)" : (st && st.needsAuth ? "Discord-Anmeldung nötig" : "nicht geladen")],
    ["Fenster", window.innerWidth + " x " + window.innerHeight]
  ];
}

async function kcCopyLogs() {
  try {
    const logs = await invoke("get_logs");
    let text = Array.isArray(logs) ? logs.join("\n") : String(logs || "");
    if (typeof activeInstance === "string" && activeInstance) {
      try { text += "\n" + (await invoke("get_game_log", { instanceName: activeInstance })); } catch (e) {}
    }
    await copyText(text);
    kcToast("Logs kopiert");
  } catch (e) {
    kcToast("Logs konnten nicht kopiert werden");
  }
}

const KC_SEL = "button, a[href], select, input, textarea, [tabindex], [role=button], label.settings-check, summary";

function kcVisible(el) {
  const r = el.getBoundingClientRect();
  if (r.width < 2 || r.height < 2) return false;
  const cs = getComputedStyle(el);
  return cs.visibility !== "hidden" && cs.display !== "none" && cs.pointerEvents !== "none";
}

function kcCollect() {
  const modal = kcTopModal();
  const root = modal || document.body;
  const out = [];
  const seen = new Set();
  const consider = (el, byCursor) => {
    if (seen.has(el)) return;
    seen.add(el);
    if (el.disabled) return;
    if (el.getAttribute("tabindex") === "-1") return;
    if (el.hasAttribute("data-kc-skip")) return;
    if (el.tagName === "INPUT" && /^(hidden|file)$/.test(el.type)) return;
    if (el.type === "checkbox" && el.closest("label.settings-check")) return;
    if (!modal && el.closest(".modal")) return;
    if (!kcVisible(el)) return;
    out.push({ el, byCursor: !!byCursor });
  };
  root.querySelectorAll(KC_SEL).forEach((el) => consider(el, false));
  // byCursor-Fallback deaktiviert: teurer DOM-Walk + getComputedStyle pro Sync.
  // Interaktive Elemente sind ueber KC_SEL abgedeckt (tabindex/role=button/onclick).
  const els = out.map((o) => o.el);
  return out
    .filter((o) => {
      if (!o.byCursor) return true;
      return !els.some((other) => other !== o.el && o.el.contains(other));
    })
    .map((o) => o.el);
}

function kcKeyOf(el) {
  if (!el || !el.dataset) return "";
  return el.dataset.kcKey || el.dataset.inst || el.id || "";
}

function kcCenter(el) {
  const r = el.getBoundingClientRect();
  return { x: r.left + r.width / 2, y: r.top + r.height / 2 };
}

function kcFirstContent(list) {
  const top = document.getElementById("kcTopbar");
  const modal = kcTopModal();
  let pick = list.find((el) => !(top && top.contains(el)) && !el.classList.contains("icon-btn"));
  if (!pick && modal) pick = list.find((el) => !el.classList.contains("icon-btn"));
  return pick || list[0] || null;
}

function kcApplyFocusClass() {
  document.querySelectorAll(".cfocus").forEach((e) => { if (e !== consoleFocusEl) e.classList.remove("cfocus"); });
  if (consoleFocusEl && consoleFocusEl.classList) consoleFocusEl.classList.add("cfocus");
}

function kcTrackModals() {
  const open = kcOpenModals();
  let restore = null;
  let opened = null;
  while (kcModalStack.length && !open.includes(kcModalStack[kcModalStack.length - 1].modal)) {
    restore = kcModalStack.pop().ret;
  }
  open.forEach((m) => {
    if (!kcModalStack.some((e) => e.modal === m)) {
      kcModalStack.push({ modal: m, ret: consoleFocusEl && consoleFocusEl.nodeType ? consoleFocusEl : null });
      opened = m;
    }
  });
  return { restore, opened };
}

function refreshConsoleFocusables() {
  if (!consoleNavActive) return;
  const track = kcTrackModals();
  consoleFocusables = kcCollect();
  if (!consoleFocusables.length) {
    consoleFocusEl = null;
    kcApplyFocusClass();
    kcUpdateHints();
    return;
  }
  const cur = consoleFocusEl;
  let next = null;
  const top = document.getElementById("kcTopbar");
  const content = consoleFocusables.find((el) => !(top && top.contains(el)));
  if (kcWantFirst && Date.now() > kcWantFirst) kcWantFirst = 0;
  if (kcWantFirst && content && !kcTopModal()) {
    kcWantFirst = 0;
    next = content;
  } else if (track.opened) {
    next = kcFirstContent(consoleFocusables);
  } else if (track.restore && consoleFocusables.includes(track.restore)) {
    next = track.restore;
  } else if (cur && consoleFocusables.includes(cur)) {
    next = cur;
  } else {
    const key = cur ? kcKeyOf(cur) : "";
    if (key) next = consoleFocusables.find((el) => kcKeyOf(el) === key) || null;
    if (!next && cur) {
      let best = Infinity;
      consoleFocusables.forEach((el) => {
        const c = kcCenter(el);
        const d = Math.hypot(c.x - kcLastCenter.x, c.y - kcLastCenter.y);
        if (d < best) { best = d; next = el; }
      });
    }
    if (!next) next = kcFirstContent(consoleFocusables);
  }
  consoleFocusEl = next;
  if (next) kcLastCenter = kcCenter(next);
  kcApplyFocusClass();
  if (track.opened || (next && next !== cur)) kcReveal(next);
  kcUpdateHints();
}

function kcScheduleSync() {
  if (!consoleNavActive) return;
  if (kcSyncTimer) clearTimeout(kcSyncTimer);
  kcSyncTimer = setTimeout(() => { kcSyncTimer = null; refreshConsoleFocusables(); }, 50);
}

function kcReveal(el) {
  if (!el || !el.scrollIntoView) return;
  const top = document.getElementById("kcTopbar");
  if (top && top.contains(el)) return;
  const first = consoleFocusables.find((e) => !(top && top.contains(e)));
  try {
    const r = el.getBoundingClientRect();
    const inView = r.top >= 0 && r.bottom <= (window.innerHeight || document.documentElement.clientHeight);
    if (!inView) {
      if (first === el && !kcTopModal()) window.scrollTo({ top: 0, behavior: "auto" });
      else el.scrollIntoView({ block: "nearest", inline: "center", behavior: "auto" });
    }
  } catch (e) {}
}

function setConsoleFocusEl(el) {
  if (!el) return;
  consoleFocusEl = el;
  kcLastCenter = kcCenter(el);
  kcApplyFocusClass();
  kcReveal(el);
  kcOnFocused(el);
  kcUpdateHints();
}

function kcOnFocused(el) {
  const tile = el.closest && el.closest(".kc-inst");
  if (!tile) return;
  const hero = document.querySelector("#homePanel .kc-hero");
  if (!hero) return;
  const name = tile.dataset.inst;
  if (name === kcHeroInst) return;
  const inst = (instancesCache || []).find((i) => i.name === name);
  if (inst) kcFillHero(hero, inst);
}

function setConsoleFocusFirstNonIcon() {
  if (!consoleNavActive) return;
  kcTrackModals();
  consoleFocusables = kcCollect();
  const pick = kcFirstContent(consoleFocusables);
  if (pick) setConsoleFocusEl(pick);
  else { consoleFocusEl = null; kcApplyFocusClass(); }
}

function moveConsoleFocus(dx, dy) {
  kcWantFirst = 0;
  if (kcIsEditing()) { try { document.activeElement.blur(); } catch (e) {} }
  if (!consoleFocusables.length) { refreshConsoleFocusables(); }
  if (!consoleFocusables.length) return;
  const cur = consoleFocusEl && consoleFocusables.includes(consoleFocusEl) ? consoleFocusEl : null;
  if (!cur) { setConsoleFocusEl(kcFirstContent(consoleFocusables)); return; }
  const c = cur.getBoundingClientRect();
  const ccx = c.left + c.width / 2;
  const ccy = c.top + c.height / 2;
  let best = null;
  let bestScore = Infinity;
  for (const el of consoleFocusables) {
    if (el === cur) continue;
    const r = el.getBoundingClientRect();
    const ex = r.left + r.width / 2;
    const ey = r.top + r.height / 2;
    let primary;
    let gap;
    let align;
    if (dx !== 0) {
      if ((ex - ccx) * dx <= 4) continue;
      primary = dx > 0 ? Math.max(0, r.left - c.right) : Math.max(0, c.left - r.right);
      gap = Math.max(0, Math.max(c.top, r.top) - Math.min(c.bottom, r.bottom));
      align = Math.abs(ey - ccy);
    } else {
      if ((ey - ccy) * dy <= 4) continue;
      primary = dy > 0 ? Math.max(0, r.top - c.bottom) : Math.max(0, c.top - r.bottom);
      gap = Math.max(0, Math.max(c.left, r.left) - Math.min(c.right, r.right));
      align = Math.abs(ex - ccx);
    }
    const score = primary + gap * 3 + align * 0.35;
    if (score < bestScore) { bestScore = score; best = el; }
  }
  if (best) { setConsoleFocusEl(best); return; }
  if (dy !== 0) kcScrollBy(dy * 120);
}

function kcScrollParent(el) {
  let n = el;
  while (n && n !== document.body && n !== document.documentElement) {
    const cs = getComputedStyle(n);
    if ((cs.overflowY === "auto" || cs.overflowY === "scroll") && n.scrollHeight > n.clientHeight + 4) return n;
    n = n.parentElement;
  }
  return null;
}

function kcScrollBy(px) {
  const sp = consoleFocusEl && consoleFocusEl.nodeType ? kcScrollParent(consoleFocusEl) : null;
  const modal = kcTopModal();
  const target = sp || (modal ? kcScrollParent(modal.querySelector(".modal-content") || modal) : null);
  if (target) target.scrollBy({ top: px });
  else window.scrollBy({ top: px });
}

function consoleDoStart() {
  const el = consoleFocusEl && consoleFocusEl.nodeType ? consoleFocusEl : null;
  if (!el) return;
  const tag = el.tagName;
  if (tag === "SELECT") { openConsolePicker(el); return; }
  if (tag === "TEXTAREA" || (tag === "INPUT" && !/^(checkbox|radio|button|submit|range|color)$/.test(el.type))) {
    el.focus();
    kcUpdateHints();
    return;
  }
  el.click();
  kcScheduleSync();
}

function consoleFocusedInstance() {
  const el = consoleFocusEl && consoleFocusEl.nodeType ? consoleFocusEl : null;
  if (!el) return null;
  const n = el.closest && el.closest("[data-inst]");
  if (!n || !instancesCache) return null;
  return instancesCache.find((i) => i.name === n.dataset.inst) || null;
}

function consoleDoManage() {
  const inst = consoleFocusedInstance();
  if (inst) openManage(inst);
}

function consoleDoY() {
  if (kcActiveTab() === "store" && !kcTopModal()) {
    const s = document.getElementById("storeSortMain");
    if (s && s.options.length) {
      s.selectedIndex = (s.selectedIndex + 1) % s.options.length;
      s.dispatchEvent(new Event("change", { bubbles: true }));
      kcToast("Sortierung: " + s.options[s.selectedIndex].textContent.trim());
      kcScheduleSync();
      return;
    }
  }
  try { switchTab("create"); } catch (e) {}
}

function consoleOpenSettings() {
  closeConsolePicker();
  const s = document.getElementById("settingsBtn");
  if (s) s.click();
}

function kcCycleSettingsTab(step) {
  const tabs = Array.from(document.querySelectorAll("#settingsModal .settings-tab"));
  if (!tabs.length) return false;
  let i = tabs.findIndex((t) => t.classList.contains("active"));
  if (i < 0) i = 0;
  i = Math.max(0, Math.min(tabs.length - 1, i + step));
  tabs[i].click();
  return true;
}

function kcCycleTab(step) {
  const modal = kcTopModal();
  if (modal) {
    if (modal.id === "settingsModal") kcCycleSettingsTab(step);
    return;
  }
  const cur = kcActiveTab();
  let i = KC_TABS.findIndex((t) => t.id === cur);
  if (i < 0) { switchTab("home"); return; }
  i = (i + step + KC_TABS.length) % KC_TABS.length;
  switchTab(KC_TABS[i].id);
}

function kcSubNav(step) {
  if (!kcTopModal() && kcActiveTab() === "store") {
    const tabs = Array.from(document.querySelectorAll("#storeTabsMain .tab")).filter((t) => t.style.display !== "none");
    if (tabs.length) {
      let i = tabs.findIndex((t) => t.classList.contains("active"));
      if (i < 0) i = 0;
      i = Math.max(0, Math.min(tabs.length - 1, i + step));
      tabs[i].click();
      kcToast("Kategorie: " + tabs[i].textContent.trim());
      kcScheduleSync();
      return;
    }
  }
  kcScrollBy(step * window.innerHeight * 0.8);
}

function consoleBack() {
  if (document.getElementById("gameSplash") && typeof hideGameSplash === "function") { hideGameSplash(); return true; }
  if (closeConsolePicker()) return true;
  if (kcIsEditing()) { try { document.activeElement.blur(); } catch (e) {} kcUpdateHints(); return true; }
  const m = kcTopModal();
  if (m) {
    const btn = m.querySelector('[id$="Close"]');
    if (btn) btn.click();
    else m.style.display = "none";
    kcScheduleSync();
    return true;
  }
  const tab = kcActiveTab();
  if (tab && tab !== "home") { switchTab("home"); return true; }
  return false;
}

function consoleCloseModal() {
  return consoleBack();
}

function consoleInputAction(action) {
  if (!consoleNavActive) return;
  kcLastInputAt = Date.now();
  if (document.getElementById("gameSplash") && /^(A|B|X|Y|START|SELECT)$/.test(action)) {
    if (typeof hideGameSplash === "function") hideGameSplash();
    return;
  }
  switch (action) {
    case "A": consoleDoStart(); break;
    case "B":
    case "SELECT": consoleBack(); break;
    case "X": consoleDoManage(); break;
    case "Y": consoleDoY(); break;
    case "START": consoleOpenSettings(); break;
    case "LB": kcCycleTab(-1); break;
    case "RB": kcCycleTab(1); break;
    case "LT": kcSubNav(-1); break;
    case "RT": kcSubNav(1); break;
    case "SCROLL_UP": kcScrollBy(-90); break;
    case "SCROLL_DOWN": kcScrollBy(90); break;
    case "UP": moveConsoleFocus(0, -1); break;
    case "DOWN": moveConsoleFocus(0, 1); break;
    case "LEFT": moveConsoleFocus(-1, 0); break;
    case "RIGHT": moveConsoleFocus(1, 0); break;
  }
}

if (window.__TAURI__ && window.__TAURI__.event && window.__TAURI__.event.listen && !window.__consoleNavTauriSub) {
  window.__consoleNavTauriSub = true;
  window.__TAURI__.event.listen("console-input", (e) => {
    consoleInputAction(String((e && e.payload) || ""));
  });
}

function openConsolePicker(select) {
  const modal = document.getElementById("pickModal");
  const list = document.getElementById("pickList");
  if (!modal || !list) { try { select.click(); } catch (e) {} return; }
  const opts = Array.from(select.options).filter((o) => !o.disabled);
  if (!opts.length) return;
  consolePickerTarget = select;
  document.getElementById("pickTitle").textContent = select.getAttribute("aria-label") || select.title || "Auswahl";
  list.innerHTML = "";
  let selectedBtn = null;
  opts.forEach((o) => {
    const b = kcEl("button", "picker-option" + (o.selected ? " picked" : ""), o.textContent.trim());
    b.type = "button";
    b.onclick = () => pickConsoleOption(select, o);
    if (o.selected) selectedBtn = b;
    list.append(b);
  });
  modal.style.display = "flex";
  if (consoleNavActive) {
    kcTrackModals();
    consoleFocusables = kcCollect();
    const pick = selectedBtn || consoleFocusables.find((el) => el.classList.contains("picker-option"));
    if (pick) setConsoleFocusEl(pick);
  }
}

function pickConsoleOption(select, option) {
  try { select.value = option.value; select.selectedIndex = option.index; } catch (e) {}
  select.dispatchEvent(new Event("change", { bubbles: true }));
  closeConsolePicker();
}

function closeConsolePicker() {
  const modal = document.getElementById("pickModal");
  if (!modal || modal.style.display === "none" || !modal.style.display) return false;
  modal.style.display = "none";
  consolePickerTarget = null;
  kcScheduleSync();
  return true;
}

function kcHintsFor() {
  const left = [];
  const right = [["lb", "rb", "Tabs"], ["start", "Einstellungen"]];
  const modal = kcTopModal();
  const el = consoleFocusEl && consoleFocusEl.nodeType ? consoleFocusEl : null;
  if (modal) {
    left.push(["a", "Auswählen"], ["b", "Schließen"], ["rs", "Scrollen"]);
    return { left, right: modal.id === "settingsModal" ? [["lb", "rb", "Reiter"]] : [] };
  }
  if (kcIsEditing()) {
    left.push(["b", "Fertig"]);
    return { left, right: [["Tastatur: Steam + X"]] };
  }
  const tab = kcActiveTab();
  const onInst = el && el.closest && el.closest("[data-inst]");
  if (onInst) left.push(["a", "Starten"], ["x", "Verwalten"], ["y", "Neue Instanz"]);
  else if (tab === "store") left.push(["a", "Details"], ["y", "Sortieren"], ["lt", "rt", "Kategorie"]);
  else left.push(["a", "Auswählen"]);
  if (tab !== "home") left.push(["b", "Zurück"]);
  left.push(["rs", "Scrollen"]);
  return { left, right };
}

function kcUpdateHints() {
  const box = document.getElementById("kcHints");
  if (!box) return;
  const h = kcHintsFor();
  const fill = (target, list) => {
    target.innerHTML = "";
    list.forEach((item) => {
      const w = kcEl("span", "kc-hint");
      const label = item[item.length - 1];
      for (let i = 0; i < item.length - 1; i++) w.append(kcGlyph(item[i]));
      w.append(kcEl("b", "", label));
      target.append(w);
    });
  };
  fill(box.querySelector(".kc-hints-l"), h.left);
  fill(box.querySelector(".kc-hints-r"), h.right);
}

function consoleNavKey(e) {
  const k = e.key;
  const editing = kcIsEditing();
  if (editing) {
    if (k === "Escape") { e.preventDefault(); consoleBack(); return; }
    if (k === "ArrowUp" || k === "ArrowDown") { e.preventDefault(); moveConsoleFocus(0, k === "ArrowDown" ? 1 : -1); }
    return;
  }
  const dir = { ArrowUp: [0, -1], ArrowDown: [0, 1], ArrowLeft: [-1, 0], ArrowRight: [1, 0], w: [0, -1], s: [0, 1], a: [-1, 0], d: [1, 0] }[k];
  if (dir) { e.preventDefault(); moveConsoleFocus(dir[0], dir[1]); return; }
  if (k === "Enter") { e.preventDefault(); consoleDoStart(); return; }
  if (k === "Escape" || k === "Backspace") { e.preventDefault(); consoleBack(); return; }
  if (k === "x" || k === "X") { e.preventDefault(); consoleDoManage(); return; }
  if (k === "y" || k === "Y") { e.preventDefault(); consoleDoY(); return; }
  if (k === "q" || k === "Q") { e.preventDefault(); kcCycleTab(-1); return; }
  if (k === "e" || k === "E") { e.preventDefault(); kcCycleTab(1); return; }
}

function kcOnPointer(e) {
  if (!consoleNavActive) return;
  const hit = consoleFocusables.find((el) => el === e.target || el.contains(e.target));
  if (hit) {
    consoleFocusEl = hit;
    kcLastCenter = kcCenter(hit);
    kcApplyFocusClass();
    kcOnFocused(hit);
    kcUpdateHints();
  }
}

function setupConsoleNavigation(on) {
  consoleNavActive = on;
  document.body.classList.toggle("console-nav", on);
  document.documentElement.classList.toggle("console-mode", on);
  if (!on) {
    document.querySelectorAll(".cfocus").forEach((e) => e.classList.remove("cfocus"));
    consoleFocusEl = null;
    consoleFocusables = [];
    kcModalStack = [];
    document.onkeydown = null;
    document.removeEventListener("pointerdown", kcOnPointer, true);
    if (kcObserver) { kcObserver.disconnect(); kcObserver = null; }
    if (kcClockTimer) { clearInterval(kcClockTimer); kcClockTimer = null; }
    if (kcStorePoll) { clearInterval(kcStorePoll); kcStorePoll = null; }
    removeConsoleChrome();
    return;
  }
  ensureConsoleChrome();
  document.onkeydown = consoleNavKey;
  document.removeEventListener("pointerdown", kcOnPointer, true);
  document.addEventListener("pointerdown", kcOnPointer, true);
  if (!kcObserver) {
    kcObserver = new MutationObserver((muts) => {
      const relevant = muts.some((m) => {
        const t = m.target && m.target.nodeType === 1 ? m.target : m.target.parentElement;
        if (!t) return false;
        if (t.closest && t.closest("#logs, #kcToast, #kcHints, #kcTopbar, .kc-player")) return false;
        return true;
      });
      if (relevant) kcScheduleSync();
    });
    kcObserver.observe(document.body, { childList: true, subtree: true, attributes: true, attributeFilter: ["style", "hidden"] });
  }
  if (!kcClockTimer) kcClockTimer = setInterval(kcTick, 1000);
  kcTick();
  kcSyncTabs(kcActiveTab() || "home");
  kcUpdateHints();
  kcScheduleSync();
}
