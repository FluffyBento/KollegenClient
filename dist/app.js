const invoke = window.__TAURI__.core.invoke;

const $ = (id) => document.getElementById(id);

let availableVersions = [];

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

async function refreshLogs() {
  try {
    const logs = await invoke("get_logs");
    $("logs").textContent = logs.join("\n");
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

async function launchGame(name) {
  try {
    const result = await invoke("launch_game", { instanceName: name });
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

$("createBtn").onclick = createInstance;

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
  viewBtn.onclick = () => showDetail(p);
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

function showDetail(p) {
  const d = $("manageDetail");
  const link = `https://modrinth.com/project/${encodeURIComponent(p.id)}`;
  d.innerHTML = `
    <div class="detail-header">
      <button id="detailClose">Schließen</button>
    </div>
    <div class="detail-body">
      ${p.icon_url ? `<img class="detail-icon" src="${escapeHtml(p.icon_url)}" alt="">` : ""}
      <h2>${escapeHtml(p.title)}</h2>
      <div class="detail-meta">${(p.downloads || 0).toLocaleString()} Downloads${p.author ? " · " + escapeHtml(p.author) : ""}</div>
      <p class="detail-desc">${escapeHtml(p.description || "Keine Beschreibung verfügbar.")}</p>
      <a class="detail-link" href="${link}" target="_blank" rel="noopener">Auf Modrinth öffnen</a>
    </div>
  `;
  $("detailClose").onclick = hideDetail;
  d.style.display = "flex";
}

function hideDetail() {
  $("manageDetail").style.display = "none";
}

// ─=== Background intervals (paused while the content browser is open) ===
let bgIntervals = [];
function startBackgroundIntervals() {
  stopBackgroundIntervals();
  bgIntervals.push(setInterval(refreshLogs, 3000));
  bgIntervals.push(setInterval(refreshAuth, 2000));
}
function stopBackgroundIntervals() {
  bgIntervals.forEach((id) => clearInterval(id));
  bgIntervals = [];
}

loadVersions();
refreshInstances();
refreshLogs();
refreshAuth();
startBackgroundIntervals();
