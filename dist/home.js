const HOME_ICONS = {
  plus: '<path d="M12 5v14M5 12h14"/>',
  store: '<path d="M6 2 3 6v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2V6l-3-4z"/><path d="M3 6h18"/><path d="M16 10a4 4 0 0 1-8 0"/>',
  spark: '<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/>',
  users: '<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87"/><path d="M16 3.13a4 4 0 0 1 0 7.75"/>',
  chat: '<path d="M21 12a8 8 0 0 1-11.6 7.1L4 20l1-4.6A8 8 0 1 1 21 12z"/>',
  chevron: '<path d="m9 6 6 6-6 6"/>'
};

const homeState = { me: null, friends: null, store: null, loadedAt: 0, loading: false };

function homeIcon(name) {
  return '<svg class="ui-ico" viewBox="0 0 24 24" aria-hidden="true">' + (HOME_ICONS[name] || "") + "</svg>";
}

function homeEl(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text !== undefined) e.textContent = text;
  return e;
}

function homeWithTimeout(promise, ms) {
  return Promise.race([promise, new Promise((_, reject) => setTimeout(() => reject(new Error("timeout")), ms))]);
}

function homeOpenUrl(url) {
  invoke("open_url", { url }).catch(() => window.open(url, "_blank"));
}

async function homeLoadData(force) {
  if (homeState.loading) return false;
  if (!force && homeState.loadedAt && Date.now() - homeState.loadedAt < 60000) return false;
  homeState.loading = true;
  try {
    const [me, friends, store] = await Promise.all([
      homeWithTimeout(invoke("kollegen_me"), 15000).catch(() => null),
      homeWithTimeout(invoke("kollegen_friends"), 15000).catch(() => null),
      homeWithTimeout(invoke("kollegen_store"), 15000).catch(() => null)
    ]);
    homeState.me = me && !me.error ? me : null;
    homeState.friends = Array.isArray(friends) ? friends : null;
    homeState.store = store && Array.isArray(store.catalog) ? store : null;
    homeState.loadedAt = Date.now();
    return true;
  } finally {
    homeState.loading = false;
  }
}

function homeGreeting() {
  const h = new Date().getHours();
  if (h >= 5 && h < 11) return "Guten Morgen";
  if (h >= 11 && h < 17) return "Guten Tag";
  if (h >= 17 && h < 22) return "Guten Abend";
  return "Hey";
}

function homeFriendName(f) {
  return f.name || f.mc_name || f.global_name || f.username || "";
}

function homeOnlineCount() {
  return Array.isArray(homeState.friends) ? homeState.friends.filter((f) => f.online).length : 0;
}

function homeStoreItems() {
  const cat = (homeState.store && Array.isArray(homeState.store.catalog))
    ? homeState.store.catalog
    : (Array.isArray(window.kmCat) ? window.kmCat : (Array.isArray(window.__cosmStore) ? window.__cosmStore : []));
  const rank = { common: 0, rare: 1, epic: 2, legendary: 3 };
  return cat
    .filter((i) => i && i.category !== "package" && !i.hidden && !i.owned)
    .sort((a, b) => ((b.featured ? 1 : 0) - (a.featured ? 1 : 0)) || ((rank[b.rarity] || 0) - (rank[a.rarity] || 0)) || (a.price - b.price));
}

function renderHomeHeroExtras() {
  const hero = document.getElementById("homeHero");
  const copy = hero && hero.querySelector(".hero-copy");
  if (!copy) return;
  const old = hero.querySelector(".hero-stats");
  if (old) old.remove();
  const eyebrow = copy.querySelector(".hero-eyebrow");
  const name = homeState.me ? homeState.me.name || homeState.me.mc_name || "" : "";
  if (eyebrow && name) eyebrow.textContent = homeGreeting() + ", " + name;
  const list = typeof instancesCache !== "undefined" && Array.isArray(instancesCache) ? instancesCache : [];
  if (!list.length) return;
  const inst = sortedInstances(list)[0];
  const stats = homeEl("div", "hero-stats");
  const add = (label, value) => {
    const cell = homeEl("div", "hero-stat");
    cell.append(homeEl("span", "hero-stat-label", label), homeEl("span", "hero-stat-value", value));
    stats.append(cell);
  };
  add("Mods", String(Array.isArray(inst.mods) ? inst.mods.length : 0));
  add("Arbeitsspeicher", inst.memory_max || "–");
  const created = Date.parse(inst.created_at || "");
  if (Number.isFinite(created)) add("Erstellt", new Date(created).toLocaleDateString("de-DE"));
  hero.insertBefore(stats, hero.querySelector(".hero-art"));
}

function homeQuickTile(icon, title, sub, onclick) {
  const b = homeEl("button", "quick-tile");
  b.type = "button";
  const ico = homeEl("span", "quick-ico");
  ico.innerHTML = homeIcon(icon);
  const text = homeEl("span", "quick-text");
  text.append(homeEl("span", "quick-title", title), homeEl("span", "quick-sub", sub));
  const chev = homeEl("span", "quick-chev");
  chev.innerHTML = homeIcon("chevron");
  b.append(ico, text, chev);
  b.onclick = onclick;
  return b;
}

function renderHomeQuick() {
  const box = document.getElementById("homeQuick");
  if (!box) return;
  box.innerHTML = "";
  const items = homeState.store ? homeStoreItems().length : 0;
  const online = homeOnlineCount();
  const discord = typeof KC_DISCORD_URL !== "undefined" ? KC_DISCORD_URL : "https://kollegen.me/discord";
  box.append(
    homeQuickTile("plus", "Neue Instanz", "Version wählen und los", () => switchTab("create")),
    homeQuickTile("store", "Store", items ? items + " neue Items" : "Items ansehen", () => switchTab("store")),
    homeQuickTile("users", "Soziales", Array.isArray(homeState.friends) ? online + " Freunde online" : "Freunde und Gruppen", () => switchTab("socials")),
    homeQuickTile("chat", "Discord", "Community beitreten", () => homeOpenUrl(discord))
  );
}

function renderHomeLevel() {
  const box = document.getElementById("homeLevel");
  if (!box) return;
  const st = homeState.store;
  if (!st || typeof st.level !== "number") {
    box.hidden = true;
    return;
  }
  const total = typeof st.points_total === "number" ? st.points_total : 0;
  const inLevel = total ? ((((total - 1) % 300) + 300) % 300) + 1 : 1;
  const pct = Math.round((inLevel / 300) * 100);
  box.hidden = false;
  box.innerHTML = "";
  const row = homeEl("div", "home-level-row");
  row.append(homeEl("b", "", "Level " + st.level), homeEl("span", "", (st.points || 0) + " Punkte"));
  const bar = homeEl("div", "home-level-bar");
  const fill = document.createElement("i");
  fill.style.width = pct + "%";
  bar.append(fill);
  box.append(row, bar, homeEl("div", "home-level-sub", 300 - inLevel + " Punkte bis Level " + (st.level + 1)));
}

function homeCardHead(title, meta, linkText, onlink) {
  const head = homeEl("div", "rail-head");
  const left = homeEl("div", "rail-title");
  left.append(homeEl("h3", "", title));
  if (meta) left.append(homeEl("span", "rail-meta", meta));
  head.append(left);
  if (linkText) {
    const link = homeEl("button", "rail-link", linkText);
    link.type = "button";
    link.onclick = onlink;
    head.append(link);
  }
  return head;
}

function renderHomeFriends() {
  const box = document.getElementById("homeFriends");
  if (!box) return;
  box.innerHTML = "";
  const friends = homeState.friends;
  const online = homeOnlineCount();
  box.append(homeCardHead("Freunde", Array.isArray(friends) && friends.length ? online + " online" : "", "Alle", () => switchTab("socials")));
  if (!Array.isArray(friends)) {
    const msg = homeEl("div", "rail-empty");
    msg.append(homeEl("p", "", "Verbinde dich mit Discord, um zu sehen, wer gerade spielt."));
    const btn = homeEl("button", "btn-secondary", "Soziales öffnen");
    btn.type = "button";
    btn.onclick = () => switchTab("socials");
    msg.append(btn);
    box.append(msg);
    return;
  }
  if (!friends.length) {
    const msg = homeEl("div", "rail-empty");
    msg.append(homeEl("p", "", "Noch keine Freunde. Füge Kollegen per Freundescode hinzu."));
    const btn = homeEl("button", "btn-secondary", "Freunde hinzufügen");
    btn.type = "button";
    btn.onclick = () => switchTab("socials");
    msg.append(btn);
    box.append(msg);
    return;
  }
  const list = homeEl("div", "friend-rows");
  [...friends]
    .sort((a, b) => (b.online ? 1 : 0) - (a.online ? 1 : 0) || homeFriendName(a).localeCompare(homeFriendName(b)))
    .slice(0, 5)
    .forEach((f) => {
      const name = homeFriendName(f);
      const row = homeEl("button", "friend-row" + (f.online ? " on" : ""));
      row.type = "button";
      const av = document.createElement("img");
      av.className = "friend-av";
      av.alt = "";
      av.loading = "lazy";
      av.src = "https://mc-heads.net/avatar/" + encodeURIComponent(name) + "/48";
      av.onerror = () => { av.style.visibility = "hidden"; };
      const text = homeEl("span", "friend-text");
      text.append(
        homeEl("span", "friend-name", name),
        homeEl("span", "friend-sub", f.online ? (f.server ? "Spielt auf " + f.server : "Online") : "Offline")
      );
      row.append(av, text, homeEl("span", "friend-dot"));
      row.onclick = () => switchTab("socials");
      list.append(row);
    });
  box.append(list);
}

function renderHomeStore() {
  const box = document.getElementById("homeStore");
  if (!box) return;
  box.innerHTML = "";
  const items = homeStoreItems().slice(0, 2);
  box.append(homeCardHead("Store-Highlights", "", "Alle", () => switchTab("store")));
  if (!items.length) {
    const msg = homeEl("div", "rail-empty");
    const isAuthed = (typeof socialMe !== "undefined" && !!socialMe) ||
                     (homeState.me && !homeState.me.error) ||
                     (typeof window !== "undefined" && (window.__discordLoggedIn || window.__discordUser));
    const msgText = isAuthed
      ? "Momentan keine neuen Highlights verfügbar."
      : (homeState.loadedAt ? "Verbinde dich mit Discord, um Store-Highlights zu sehen." : "Store wird geladen …");
    msg.append(homeEl("p", "", msgText));
    box.append(msg);
    return;
  }
  const tools = window.kollegenStore;
  const grid = homeEl("div", "store-mini");
  items.forEach((item) => {
    const tile = homeEl("button", "store-mini-tile rar-" + (item.rarity || "common"));
    tile.type = "button";
    const pv = homeEl("div", "store-mini-pv");
    try { pv.innerHTML = tools ? tools.preview(item) : ""; } catch (e) { pv.innerHTML = ""; }
    const body = homeEl("div", "store-mini-body");
    body.append(homeEl("span", "store-mini-name", item.name), homeEl("span", "store-mini-price", item.price + " Punkte"));
    tile.append(pv, body);
    tile.onclick = () => {
      if (tools) tools.open(item.id);
      else switchTab("store");
    };
    grid.append(tile);
  });
  box.append(grid);
}

function renderHomeModels() {
  const canvas = document.getElementById("homeModels");
  if (!canvas) return;
  const eq = homeState.me && homeState.me.equipped && typeof homeState.me.equipped === "object" ? homeState.me.equipped : null;
  const parts = ["cape", "shield", "back_accessory", "head_accessory", "face_accessory", "aura", "shoulder_accessory", "torso_accessory", "legs_accessory", "gloves", "pet"];
  const has = !!eq && parts.some((k) => eq[k]);
  canvas.hidden = !has;
}

function renderHomeExtras() {
  const paint = () => {
    renderHomeHeroExtras();
    renderHomeQuick();
    renderHomeFriends();
    renderHomeStore();
    renderHomeLevel();
    renderHomeModels();
  };
  paint();
  homeLoadData(false).then((fresh) => { if (fresh) paint(); });
}
