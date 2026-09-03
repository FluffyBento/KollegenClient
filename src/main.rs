// Prevents additional console window on Windows in release
#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod app_updates;
mod auth;
mod skins;
mod companion;
mod import;
mod presence;
mod discord;
mod discord_auth;
mod instance;
mod java;
mod modrinth;
mod types;
mod utils;

use anyhow::Result;
use directories::ProjectDirs;
use log::warn;
use serde_json::Value;
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use tauri::Manager;
use tauri::State;

// ─=== Constants ===
// Microsoft Identity Platform (Azure AD) public client ID used for the device-code
// OAuth flow that authenticates with PrismLauncher / Minecraft. This is a "public
// client" (no secret) so it can be shared openly, but if Microsoft ever blocks or
// throttles this ID you can point the app at your own Entra app registration via
// the `MICROSOFT_CLIENT_ID` environment variable.
pub const CLIENT_ID_DEFAULT: &str = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";

/// Microsoft OAuth client ID, overridable via the `MICROSOFT_CLIENT_ID` env var.
pub fn client_id() -> &'static str {
    static V: std::sync::OnceLock<String> = std::sync::OnceLock::new();
    V.get_or_init(|| {
        std::env::var("MICROSOFT_CLIENT_ID")
            .ok()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| CLIENT_ID_DEFAULT.to_string())
    })
    .as_str()
}

// Public Microsoft client IDs that work with the device-code flow and the
// `XboxLive.Signin` / `offline_access` scopes. These are *public* clients (no
// secret) that are reused across the open-source Minecraft-launcher ecosystem at
// no cost and without registering your own Azure app. When Microsoft blocks or
// throttles one ID for a user, `ms_auth_start` automatically retries with the
// next candidate so login keeps working. Add more known-good public IDs here.
pub const MS_CLIENT_IDS: &[&str] = &[
    CLIENT_ID_DEFAULT,                                   // PrismLauncher's public client
    "9c1f1f43-58d5-4b7a-af0d-4e487f073441",              // public client used by minecraft-rs/auth
];

// Client ID selected for the in-progress login attempt (a fallback candidate).
static ACTIVE_MS_CLIENT_ID: std::sync::Mutex<Option<String>> = std::sync::Mutex::new(None);

/// Client ID for the current login attempt: `MICROSOFT_CLIENT_ID` env override,
/// then the selected fallback candidate, then the default.
pub fn auth_client_id() -> String {
    if let Some(env) = std::env::var("MICROSOFT_CLIENT_ID").ok().filter(|s| !s.is_empty()) {
        return env;
    }
    if let Some(active) = ACTIVE_MS_CLIENT_ID.lock().unwrap().as_ref() {
        return active.clone();
    }
    CLIENT_ID_DEFAULT.to_string()
}

/// Selects which fallback client ID the next login attempt uses.
pub fn set_auth_client_id(id: &str) {
    *ACTIVE_MS_CLIENT_ID.lock().unwrap() = Some(id.to_string());
}
pub const USER_AGENT: &str = "KollegenClient/1.0 (+https://kollegen.dev)";
pub const DEFAULT_MEMORY_MIN: &str = "2G";
pub const DEFAULT_MEMORY_MAX: &str = "4G";
pub const MAX_LOG_LINES: usize = 1000;
/// Hotfix suffix appended to the version string (e.g. "v1", "v2"). Empty for a
/// clean release. Shown in the UI corner so users can identify the exact build.
pub const VERSION_HOTFIX: &str = "";

// Discord Rich Presence application ID. Replace this with the numeric Client ID
// of YOUR OWN Discord application (https://discord.com/developers/applications).
// The rich presence only appears if this ID matches a real Discord app and the
// Discord desktop client is running.
//
// This value can be overridden at runtime via the DISCORD_CLIENT_ID environment
// variable. It must be the Client ID of a real Discord application (see
// https://discord.com/developers/applications) for the rich presence to appear.
pub const DISCORD_CLIENT_ID_DEFAULT: &str = "1538588736718373034";

/// Discord application Client ID, overridable via the `DISCORD_CLIENT_ID` env var.
pub fn discord_client_id() -> &'static str {
    static V: std::sync::OnceLock<String> = std::sync::OnceLock::new();
    V.get_or_init(|| {
        std::env::var("DISCORD_CLIENT_ID")
            .ok()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| DISCORD_CLIENT_ID_DEFAULT.to_string())
    })
    .as_str()
}


// The "Kollegen Client" companion mod is injected into every instance from the
// bundled/downloaded `kollegen-client-mod.jar` (see `companion` module). It is
// hidden from the mod browser and can't be removed by the user.

// ─=== App State ===
pub struct AppState {
    pub instances: Mutex<Vec<types::Instance>>,
    pub accounts: Mutex<Vec<types::Account>>,
    pub data_dir: PathBuf,
    pub logs: Arc<Mutex<Vec<String>>>,
    pub discord: discord::DiscordHandle,
}

// ─=== Tauri Commands ===

/// Returns the exact app version shown in the UI corner, composed of the Cargo
/// package version plus an optional hotfix suffix (e.g. `1.3.0v1`).
#[tauri::command]
fn get_version() -> String {
    let base = env!("CARGO_PKG_VERSION");
    if VERSION_HOTFIX.is_empty() {
        base.to_string()
    } else {
        format!("{}{}", base, VERSION_HOTFIX)
    }
}

#[tauri::command]
fn get_instances(state: State<'_, AppState>) -> Result<Vec<types::Instance>, String> {
    let path = utils::instances_file(&state.data_dir);
    let instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    Ok(instances)
}

#[tauri::command]
fn get_accounts(state: State<'_, AppState>) -> Result<Vec<types::Account>, String> {
    let path = utils::accounts_file(&state.data_dir);
    let accounts = utils::load_json::<Vec<types::Account>>(&path, vec![]);
    Ok(accounts)
}

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> Result<types::Settings, String> {
    let path = utils::settings_file(&state.data_dir);
    let settings = utils::load_json::<types::Settings>(&path, types::Settings::default());
    Ok(settings)
}

#[tauri::command]
fn save_settings(
    state: State<'_, AppState>,
    settings: types::Settings,
) -> Result<(), String> {
    let path = utils::settings_file(&state.data_dir);
    utils::save_json(&path, &settings).map_err(|e| e.to_string())
}

#[tauri::command]
fn create_instance(
    state: State<'_, AppState>,
    name: String,
    version: String,
    loader: String,
    loader_version: Option<String>,
) -> Result<types::Instance, String> {
    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);

    // The instance name is the on-disk identifier (`instances/<name>`). Two
    // instances with the same name would share one directory, which breaks
    // management (e.g. deleting one would wipe the other). Reject duplicates.
    if instances.iter().any(|i| i.name == name) {
        return Err(format!(
            "Eine Instanz mit dem Namen '{}' existiert bereits.",
            name
        ));
    }

    let inst = types::Instance {
        id: uuid::Uuid::new_v4().to_string(),
        name: name.clone(),
        version: version.clone(),
        loader,
        loader_version,
        description: String::new(),
        mods: vec!["essentialmod.jar".to_string()],
        vulkan_enabled: false,
        memory_min: DEFAULT_MEMORY_MIN.to_string(),
        memory_max: DEFAULT_MEMORY_MAX.to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played: None,
        java_args: None,
        server: None,
    };

    let dir = utils::instance_dir(&state.data_dir, &name);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;

    instances.push(inst.clone());
    utils::save_json(&path, &instances).map_err(|e| e.to_string())?;

    // JRE für die benötigte Java-Version automatisch herunterladen (best-effort),
    // sobald die Instanz erstellt wird – der separate „JRE herunterladen"-Knopf
    // entfällt. Nur laden, wenn noch kein passendes JRE vorhanden ist.
    let required_java = required_java_for_version(&version);
    if java::find_java(&state.data_dir, required_java).is_err() {
        let _ = java::download_jre_internal(required_java).map_err(|e| {
            warn!(
                "JRE {} konnte nicht automatisch heruntergeladen werden: {}",
                required_java, e
            )
        });
    }

    // Fabric/Quilt: stärkstes Performance-Modpack automatisch vorinstallieren
    // (best-effort – einzelne Mods dürfen fehlschlagen, ohne die Instanz zu
    // blockieren).
    if inst.loader.eq_ignore_ascii_case("fabric")
        || inst.loader.eq_ignore_ascii_case("quilt")
    {
        let settings = utils::load_json::<types::Settings>(
            &utils::settings_file(&state.data_dir),
            types::Settings::default(),
        );
        if settings.perf_mods {
            let _ = modrinth::install_perf_mods(
                &state.data_dir,
                &name,
                &version,
                &inst.loader,
            )
            .map_err(|e| warn!("Performance-Mods nicht installiert: {}", e));
        }
    }

    // Begleit-Mod direkt bei der Erstellung in die Instanz legen (1.21.x – 1.26.x).
    crate::instance::ensure_kollegen_mod(&state.data_dir, &inst.name, &inst.loader, &inst.version);

    Ok(inst)
}

#[tauri::command]
fn delete_instance(
    state: State<'_, AppState>,
    name: String,
    id: Option<String>,
) -> Result<(), String> {
    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);

    // Address exactly one instance: prefer the unique id, otherwise fall back
    // to the FIRST instance with this name. Never delete-all-matching, so two
    // legacy instances sharing a display name don't both get removed.
    let target_idx = if let Some(uid) = id.as_deref().filter(|s| !s.is_empty()) {
        instances.iter().position(|i| i.id == uid)
    } else {
        instances.iter().position(|i| i.name == name)
    };

    if let Some(idx) = target_idx {
        instances.remove(idx);
        utils::save_json(&path, &instances).map_err(|e| e.to_string())?;
    }

    // The on-disk data dir is shared by all instances with the same name; only
    // wipe it when no remaining instance still uses that name.
    if !instances.iter().any(|i| i.name == name) {
        let dir = utils::instance_dir(&state.data_dir, &name);
        if dir.exists() {
            std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
        }
    }

    Ok(())
}

#[tauri::command]
fn get_logs(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let logs = state.logs.lock().unwrap().clone();
    Ok(logs)
}

#[tauri::command]
fn get_game_log(state: State<'_, AppState>, instance_name: String) -> String {
    let p = crate::utils::instance_dir(&state.data_dir, &instance_name)
        .join("logs")
        .join("latest.log");
    read_log_tail(&p, 200 * 1024)
}

/// Reads at most the last `max_bytes` of a file. Used for `latest.log` so we
/// never ship a multi-hundred-MB string over IPC / into the DOM on every poll
/// (that unbounded transfer is what ballooned the webview's memory).
fn read_log_tail(path: &Path, max_bytes: usize) -> String {
    use std::io::{Read, Seek, SeekFrom};
    let mut f = match std::fs::File::open(path) {
        Ok(f) => f,
        Err(_) => return String::new(),
    };
    let len = match f.metadata() {
        Ok(m) => m.len() as usize,
        Err(_) => return String::new(),
    };
    if len <= max_bytes {
        let mut s = String::new();
        let _ = f.read_to_string(&mut s);
        return s;
    }
    if f.seek(SeekFrom::Start((len - max_bytes) as u64)).is_err() {
        return String::new();
    }
    let mut buf = Vec::new();
    let _ = f.read_to_end(&mut buf);
    let s = String::from_utf8_lossy(&buf).to_string();
    // Drop the leading partial line so we start on a clean log line.
    match s.find('\n') {
        Some(i) => s[i + 1..].to_string(),
        None => s,
    }
}

/// Detects a Fabric mod-incompatibility crash in the game log and automatically
/// fixes it: for every conflicting mod Fabric lists, the matching jar is
/// replaced with the recommended (or latest compatible) version from Modrinth.
/// If no compatible version can be installed, the incompatible mod is removed.
#[tauri::command]
fn auto_resolve_conflict(
    state: State<'_, AppState>,
    instance_name: String,
) -> Result<String, String> {
    let log_path = crate::utils::instance_dir(&state.data_dir, &instance_name)
        .join("logs")
        .join("latest.log");
    let text = std::fs::read_to_string(&log_path).unwrap_or_default();
    let lowered = text.to_lowercase();
    if !lowered.contains("incompatible mods found") {
        return Ok("Kein Mod-Konflikt erkannt.".to_string());
    }

    // The instance's Minecraft version + loader decide which replacement
    // version is compatible.
    let instances = utils::load_json::<Vec<types::Instance>>(
        &utils::instances_file(&state.data_dir),
        vec![],
    );
    let (mc_version, loader) = instances
        .iter()
        .find(|i| i.name == instance_name)
        .map(|i| (i.version.clone(), i.loader.clone()))
        .unwrap_or_else(|| ("".to_string(), "".to_string()));

    let mods_dir = crate::utils::instance_dir(&state.data_dir, &instance_name).join("mods");
    let targets = parse_conflict_targets(&text);
    if targets.is_empty() {
        return Ok("Konflikt erkannt, aber keine ersetzbaren Mods gefunden.".to_string());
    }

    let mut fixed = Vec::new();
    let mut removed = Vec::new();
    let mut skipped = Vec::new();

    for (modid, name) in targets {
        let label = if !name.is_empty() {
            name.clone()
        } else {
            modid.clone()
        };
        let jar = match find_mod_jar(&mods_dir, &modid, &name) {
            Some(j) => j,
            None => {
                skipped.push(format!("{} (Datei nicht gefunden)", label));
                continue;
            }
        };
        let fname = jar.file_name().unwrap().to_string_lossy().to_string();
        if crate::companion::is_companion_mod_name(&fname) {
            continue;
        }

        let pid = resolve_mod_pid(
            &state.data_dir,
            &instance_name,
            &modid,
            &name,
            &mc_version,
            &loader,
        );

        match pid {
            Some(pid) => {
                // Remove the conflicting file before installing the fix so only
                // the new version remains.
                let _ = std::fs::remove_file(&jar);
                let preferred = preferred_version_id(&pid, &mc_version, &loader, &modid, &name, &text);
                // Try the recommended version from the error first, then fall
                // back to the latest compatible version.
                let mut install = match &preferred {
                    Some(vid) => crate::modrinth::install_content(
                        &instance_name,
                        &state.data_dir,
                        "mod",
                        &pid,
                        "",
                        "",
                        Some(vid),
                    ),
                    None => crate::modrinth::install_content(
                        &instance_name,
                        &state.data_dir,
                        "mod",
                        &pid,
                        &mc_version,
                        &loader,
                        None,
                    ),
                };
                if install.is_err() && preferred.is_some() {
                    install = crate::modrinth::install_content(
                        &instance_name,
                        &state.data_dir,
                        "mod",
                        &pid,
                        &mc_version,
                        &loader,
                        None,
                    );
                }
                match install {
                    Ok(()) => fixed.push(format!("{} → {}", label, fname)),
                    Err(_) => {
                        // Fix failed -> ensure the incompatible mod is removed.
                        let _ = std::fs::remove_file(&jar);
                        removed.push(format!("{} (entfernt – keine kompatible Version)", label));
                    }
                }
            }
            None => {
                // Not a known Modrinth mod -> remove the incompatible file.
                let _ = std::fs::remove_file(&jar);
                removed.push(format!("{} (entfernt)", label));
            }
        }
    }

    let mut msg = String::new();
    if !fixed.is_empty() {
        msg.push_str("Mod automatisch repariert (Version angepasst):\n- ");
        msg.push_str(&fixed.join("\n- "));
    }
    if !removed.is_empty() {
        if !msg.is_empty() {
            msg.push_str("\n\n");
        }
        msg.push_str("Inkompatible Mods entfernt:\n- ");
        msg.push_str(&removed.join("\n- "));
    }
    if !skipped.is_empty() {
        if !msg.is_empty() {
            msg.push_str("\n\n");
        }
        msg.push_str("Nicht automatisch behoben:\n- ");
        msg.push_str(&skipped.join("\n- "));
    }
    if msg.is_empty() {
        msg.push_str("Keine ersetzbaren Mods im Konflikt gefunden.");
    }
    Ok(msg)
}

/// Parses Fabric's "Incompatible mods found!" report and returns, for each
/// conflicting mod, its modid (from the `(modid)` parenthetical) and display
/// name. Handles both "- Replace 'Name' (modid) ver ..." and
/// "- Remove Name (modid) ver" lines (Fabric uses a capital "Mod").
fn parse_conflict_targets(text: &str) -> Vec<(String, String)> {
    let mut out = Vec::new();
    for line in text.lines() {
        let l = line.trim();
        if !(l.starts_with("- Remove") || l.starts_with("- Replace")) {
            continue;
        }
        let s = match l.find('(') {
            Some(s) => s,
            None => continue,
        };
        let e = match l[s..].find(')') {
            Some(e) => e,
            None => continue,
        };
        let modid = l[s + 1..s + e].trim().to_string();
        if modid.is_empty() {
            continue;
        }
        // Display name: between single quotes (Replace) or, when Fabric has no
        // quotes (e.g. "- Remove fabric-api (fabric-api)"), the modid itself.
        let name = if let Some(a) = l.find('\'') {
            if let Some(b) = l[a + 1..].find('\'') {
                l[a + 1..a + 1 + b].to_string()
            } else {
                modid.clone()
            }
        } else {
            modid.clone()
        };
        out.push((modid, name));
    }
    out
}

/// Finds the jar file in `mods_dir` that belongs to a mod with the given modid
/// / display name (case-insensitive substring match).
fn find_mod_jar(mods_dir: &Path, modid: &str, name: &str) -> Option<PathBuf> {
    let mid = modid.to_lowercase();
    let nm = name.to_lowercase();
    if let Ok(entries) = std::fs::read_dir(mods_dir) {
        for e in entries.flatten() {
            let p = e.path();
            let fname = p.file_name()?.to_string_lossy().to_lowercase();
            if !fname.ends_with(".jar") {
                continue;
            }
            // Vom Kollegen-Client verwaltete Renderer-Mods (Sodium/Iris/
            // VulkanMod/Beryl) werden vom Crash-Resolver ignoriert – sie werden
            // von der Begleit-Mod verwaltet, nicht über Modrinth ersetzt.
            if crate::modrinth::is_managed_renderer_mod(&fname) {
                continue;
            }
            if !mid.is_empty() && fname.contains(&mid) {
                return Some(p);
            }
            if nm.len() > 2 && fname.contains(&nm) {
                return Some(p);
            }
        }
    }
    None
}

/// Resolves a Modrinth project id for a conflicting mod: first from the
/// launcher's own install metadata (if the jar is managed), then by searching
/// Modrinth with the modid or display name.
fn resolve_mod_pid(
    data_dir: &Path,
    instance_name: &str,
    modid: &str,
    name: &str,
    mc_version: &str,
    loader: &str,
) -> Option<String> {
    let mods_dir = crate::utils::instance_dir(data_dir, instance_name).join("mods");
    if let Some(jar) = find_mod_jar(&mods_dir, modid, name) {
        let fname = jar.file_name()?.to_string_lossy().to_string();
        if let Some(pid) =
            crate::modrinth::project_id_for_file(data_dir, instance_name, "mod", &fname)
        {
            return Some(pid);
        }
    }
    if !modid.is_empty() {
        if let Ok(r) = crate::modrinth::search("mod", modid, mc_version, loader, 0) {
            if let Some(p) = r.first() {
                return Some(p.id.clone());
            }
        }
    }
    if name.len() > 2 {
        if let Ok(r) = crate::modrinth::search("mod", name, mc_version, loader, 0) {
            if let Some(p) = r.first() {
                return Some(p.id.clone());
            }
        }
    }
    None
}

/// Extracts the first `X.Y.Z`-style version token from a string.
fn extract_version(s: &str) -> Option<String> {
    let bytes = s.as_bytes();
    let mut i = 0;
    while i < bytes.len() {
        if bytes[i].is_ascii_digit() {
            let start = i;
            while i < bytes.len() && (bytes[i].is_ascii_digit() || bytes[i] == b'.') {
                i += 1;
            }
            let tok = &s[start..i];
            if tok.chars().filter(|c| *c == '.').count() >= 1 {
                return Some(tok.to_string());
            }
        } else {
            i += 1;
        }
    }
    None
}

/// If Fabric's report names a specific version that is "compatible", returns the
/// matching Modrinth version id so we can install exactly that recommended fix.
fn preferred_version_id(
    pid: &str,
    mc_version: &str,
    loader: &str,
    modid: &str,
    name: &str,
    text: &str,
) -> Option<String> {
    let needle = if name.len() > 2 {
        name.to_lowercase()
    } else {
        modid.to_lowercase()
    };
    let mut hint = None;
    for line in text.lines() {
        let l = line.to_lowercase();
        if l.contains(&needle) && l.contains("is compatible with") {
            if let Some(v) = extract_version(&l) {
                hint = Some(v);
                break;
            }
        }
    }
    let hint = hint?;
    if let Ok(versions) = crate::modrinth::list_versions(pid, mc_version, loader) {
        for v in &versions {
            if v.version_number.contains(&hint) || v.name.contains(&hint) {
                return Some(v.id.clone());
            }
        }
    }
    None
}

#[tauri::command]
fn get_available_versions() -> Result<Vec<String>, String> {
    instance::fetch_available_versions().map_err(|e| e.to_string())
}

#[tauri::command]
fn get_loaders_for_version(version: String) -> Result<Value, String> {
    instance::fetch_loaders_for_version(&version).map_err(|e| e.to_string())
}

#[tauri::command]
fn install_instance(
    state: State<'_, AppState>,
    name: String,
    version: String,
    loader: String,
    loader_version: Option<String>,
) -> Result<(), String> {
    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    if let Some(inst) = instances.iter_mut().find(|i| i.name == name) {
        inst.version = version.clone();
        inst.loader = loader.clone();
        inst.loader_version = loader_version.clone();
    }
    utils::save_json(&path, &instances).map_err(|e| e.to_string())?;
    instance::install_instance(&state.data_dir, &name, &version, &loader, loader_version.as_deref())
        .map_err(|e| e.to_string())?;
    // Ensure every installed instance carries the Kollegen Client companion
    // mod (drives in-game rich presence + join). No-op for vanilla/offline.
    companion::install_companion_mod(&state.data_dir, &name, &version, &loader);
    Ok(())
}

/// Installs the "Kollegen Client" companion Fabric mod into an instance if it
/// isn't already present. The mod is bundled/downloaded (see `companion`
/// module), hidden from the mod browser and cannot be removed by the user.
/// Best-effort: no-op when the jar is unavailable or the loader is not
/// Fabric/Quilt compatible (Forge/NeoForge/vanilla).
fn ensure_companion_mod(data_dir: &Path, instance_name: &str, version: &str, loader: &str) {
    crate::companion::install_companion_mod(data_dir, instance_name, version, loader);
}

/// Maps a Minecraft version string to the Java major version it requires, used
/// as a fallback when the instance's Mojang version JSON isn't available locally
/// (e.g. imported instances). 1.21+ needs Java 21; 1.17–1.20 need Java 17;
/// 1.16 needs Java 16; everything older needs Java 8.
fn required_java_for_version(version: &str) -> u32 {
    let mut it = version.split('.');
    let major: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(99);
    let minor: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    if major > 1 || (major == 1 && minor >= 21) {
        21
    } else if major == 1 && minor >= 17 {
        17
    } else if major == 1 && minor >= 16 {
        16
    } else {
        8
    }
}

#[tauri::command]
fn launch_game(
    state: State<'_, AppState>,
    instance_name: String,
    server: Option<String>,
) -> Result<String, String> {
    let path = utils::instances_file(&state.data_dir);
    let instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    let inst = instances.iter().find(|i| i.name == instance_name)
        .ok_or("Instanz nicht gefunden")?;
    let mut inst = inst.clone();
    if let Some(s) = &server {
        // Normalize a bare host to host:25565 for the --server/--port args.
        inst.server = Some(if s.contains(':') { s.clone() } else { format!("{}:25565", s) });
    }

    // Zwei-Wege-Sync des Renderer-Toggles: Der In-Game-Schalter schreibt nur
    // die State-Datei mods/.kollegen-renderer ("vulkan"/"opengl") – Jars dürfen
    // im laufenden Spiel nicht umbenannt werden (SIGSEGV). Beim Start übernehmen
    // wir den Wunsch als Quelle der Wahrheit, bevor enforce_renderer_consistency
    // ihn überschreiben würde, und persistieren ihn in instances.json.
    {
        let mods_dir = utils::instance_dir(&state.data_dir, &inst.name).join("mods");
        if let Some(desired) = instance::read_renderer_state(&mods_dir) {
            if desired != inst.vulkan_enabled {
                inst.vulkan_enabled = desired;
                let mut instances = instances;
                if let Some(i) = instances.iter_mut().find(|i| i.name == instance_name) {
                    i.vulkan_enabled = desired;
                }
                if let Err(e) = utils::save_json(&path, &instances) {
                    utils::append_log(&state, &format!("Renderer-Wunsch konnte nicht gespeichert werden: {}", e));
                }
                utils::append_log(
                    &state,
                    &format!(
                        "In-Game-Renderer-Toggle übernommen: {}",
                        if desired { "Vulkan" } else { "OpenGL" }
                    ),
                );
            }
        }
    }

    // Make sure the Kollegen Client companion mod is present (drives in-game
    // rich presence + join). Best-effort; no-op if the project id is empty or
    // the instance is vanilla.
    ensure_companion_mod(&state.data_dir, &inst.name, &inst.version, &inst.loader);

    // Determine the required Java version. We read every version JSON in the
    // instance's version dir (the Mojang one AND the Fabric-merged one) and take
    // the highest `javaVersion.majorVersion`, falling back to a mapping derived
    // from the Minecraft version string. This matters for instances imported
    // from other launchers (e.g. Prism) whose Mojang version JSON may not be
    // present locally – without the fallback they'd silently launch with Java 17
    // and crash on 1.21+ (which needs Java 21).
    let required_java = {
        let fallback = required_java_for_version(&inst.version);
        let version_dir = utils::instance_dir(&state.data_dir, &inst.name)
            .join("versions")
            .join(&inst.version);
        let mut from_json = None;
        if let Ok(entries) = std::fs::read_dir(&version_dir) {
            for e in entries.flatten() {
                let p = e.path();
                if p.extension().and_then(|x| x.to_str()) == Some("json") {
                    if let Ok(s) = std::fs::read_to_string(&p) {
                        if let Ok(vj) = serde_json::from_str::<types::VersionJson>(&s) {
                            if let Some(jv) = vj.java_version {
                                from_json = Some(from_json.unwrap_or(0).max(jv.major_version));
                            }
                        }
                    }
                }
            }
        }
        from_json.unwrap_or(fallback)
    };

    let java_path = match java::find_java(&state.data_dir, required_java) {
        Ok(p) => p,
        Err(_) => {
            utils::append_log(&state, &format!("Java {} nicht gefunden, lade JRE herunter...", required_java));
            java::download_jre_internal(required_java).map_err(|e| e.to_string())?;
            java::find_java(&state.data_dir, required_java).map_err(|e| e.to_string())?
        }
    };

    utils::append_log(
        &state,
        &format!(
            "Instanz '{}' (MC {}) benötigt Java {}, verwende: {}",
            inst.name, inst.version, required_java, java_path
        ),
    );

    let settings = utils::load_json::<types::Settings>(
        &utils::settings_file(&state.data_dir),
        types::Settings::default(),
    );

    let launch_result = instance::launch(&state, &state.data_dir, &inst, &java_path, &settings);
    match launch_result {
        Ok(result) => Ok(result),
        Err(e) => {
            utils::append_log(&state, &format!("Launch fehlgeschlagen: {}", e));
            Err(e.to_string())
        }
    }
}

/// Aktualisiert sofort (ohne Neustart) den Controller-Modus des Begleit-Mods in
/// allen bestehenden Instanzen – z. B. wenn der Nutzer den SteamDeck-Modus in
/// den Einstellungen umschaltet. Der Mod wird den Zustand beim nächsten Start
/// zusätzlich selbst aus der Shared-State-Datei übernehmen.
#[tauri::command]
fn set_console_mode(state: State<'_, AppState>, on: bool) -> Result<(), String> {
    let data_dir = state.data_dir.clone();
    let instances = utils::load_json::<Vec<types::Instance>>(
        &utils::instances_file(&data_dir),
        vec![],
    );
    for inst in &instances {
        let mods_dir = utils::instance_dir(&data_dir, &inst.name).join("mods");
        instance::enforce_controller_state(&mods_dir, on);
    }
    Ok(())
}

// ─=== Auth Commands ===

#[tauri::command]
fn auth_check_status() -> Result<Value, String> {
    let status = auth::get_auth_status();
    Ok(status)
}

#[tauri::command]
fn auth_start() -> Result<Value, String> {
    auth::ms_auth_start().map_err(|e| e.to_string())
}

// (device-code Discord login removed – RPC-only integration)

#[tauri::command]
fn download_jre_command(version: Option<u32>) -> Result<Value, String> {
    let v = version.unwrap_or(21);
    match java::download_jre_internal(v) {
        Ok(path) => Ok(serde_json::json!({"ok": true, "path": path, "version": v})),
        Err(e) => Ok(serde_json::json!({"ok": false, "error": e.to_string()})),
    }
}

#[tauri::command]
fn modrinth_search(
    kind: String,
    query: String,
    mc_version: String,
    loader: String,
    offset: usize,
) -> Result<Vec<crate::modrinth::ModrinthProject>, String> {
    crate::modrinth::search(&kind, &query, &mc_version, &loader, offset).map_err(|e| e.to_string())
}

#[tauri::command]
fn installed_project_ids(
    state: State<'_, AppState>,
    instance_name: String,
) -> Result<Value, String> {
    Ok(crate::modrinth::installed_project_ids(
        &state.data_dir,
        &instance_name,
    ))
}

#[tauri::command]
fn modrinth_project(id: String) -> Result<Value, String> {
    crate::modrinth::project_details(&id)
}

#[tauri::command]
fn install_content(
    state: State<'_, AppState>,
    instance_name: String,
    kind: String,
    project_id: String,
    mc_version: String,
    loader: String,
    version_id: Option<String>,
) -> Result<(), String> {
    crate::modrinth::install_content(
        &instance_name,
        &state.data_dir,
        &kind,
        &project_id,
        &mc_version,
        &loader,
        version_id.as_deref(),
    )
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn modrinth_versions(
    project_id: String,
    mc_version: String,
    loader: String,
) -> Result<Value, String> {
    let versions = crate::modrinth::list_versions(&project_id, &mc_version, &loader)
        .map_err(|e| e.to_string())?;
    Ok(serde_json::to_value(versions).unwrap_or(Value::Null))
}

/// Installiert das kuratierte Performance-Modpack in eine bestehende
/// Fabric-/Quilt-Instanz nachträglich (best-effort) und meldet das Ergebnis.
#[tauri::command]
fn optimize_instance(
    state: State<'_, AppState>,
    name: String,
) -> Result<String, String> {
    let path = utils::instances_file(&state.data_dir);
    let instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    let inst = instances
        .iter()
        .find(|i| i.name == name)
        .ok_or_else(|| format!("Instanz '{}' nicht gefunden.", name))?;
    let installed = crate::modrinth::install_perf_mods(
        &state.data_dir,
        &name,
        &inst.version,
        &inst.loader,
    )
    .map_err(|e| e.to_string())?;
    // Keine einzelne Mod-Liste anzeigen – nur kurz bestätigen, dass die
    // Instanz optimiert wurde (auch wenn kein Mod neu hinzugefügt wurde).
    let _ = installed;
    // Kollegen Client Begleit-Mod sicherstellen, auch wenn die Instanz noch
    // nicht gestartet wurde (best-effort; no-op bei Vanilla/Forge/NeoForge).
    crate::companion::install_companion_mod(&state.data_dir, &name, &inst.version, &inst.loader);
    Ok("Instanz wurde optimiert.".to_string())
}

#[tauri::command]
fn list_content(state: State<'_, AppState>, instance_name: String) -> Result<Value, String> {
    Ok(crate::modrinth::list_content(&state.data_dir, &instance_name))
}

#[tauri::command]
fn delete_content(
    state: State<'_, AppState>,
    instance_name: String,
    kind: String,
    filename: String,
) -> Result<(), String> {
    crate::modrinth::delete_content(&state.data_dir, &instance_name, &kind, &filename)
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn change_content_version(
    state: State<'_, AppState>,
    instance_name: String,
    kind: String,
    filename: String,
    version_id: String,
) -> Result<(), String> {
    crate::modrinth::change_content_version(
        &state.data_dir,
        &instance_name,
        &kind,
        &filename,
        &version_id,
    )
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn open_url(url: String) -> Result<(), String> {
    open::that(&url).map_err(|e| e.to_string())
}

#[tauri::command]
fn open_logs_folder(state: State<'_, AppState>) -> Result<(), String> {
    let dir = state.data_dir.join("logs");
    let _ = std::fs::create_dir_all(&dir);
    open::that(dir.as_os_str()).map_err(|e| e.to_string())
}

/// Persists the launcher's current theme colors so the in-game Kollegen Client
/// mod can render its menu with the exact same palette (even when the user
/// changes the accent color at runtime). Written both to the app data dir and to
/// `~/.kollegen-theme.json` (a fixed, user-home location the mod can read).
#[tauri::command]
fn write_theme_file(state: State<'_, AppState>, json: String) -> Result<(), String> {
    let dir = state.data_dir.join(".kollegen");
    let _ = std::fs::create_dir_all(&dir);
    let _ = std::fs::write(dir.join("theme.json"), &json);
    if let Some(home) = dirs::home_dir() {
        let _ = std::fs::write(home.join(".kollegen-theme.json"), &json);
    }
    Ok(())
}

#[tauri::command]
fn toggle_fullscreen(app: tauri::AppHandle) -> Result<(), String> {
    let window = app
        .get_webview_window("main")
        .ok_or("Hauptfenster nicht gefunden")?;
    let is_fs = window.is_fullscreen().map_err(|e| e.to_string())?;
    window
        .set_fullscreen(!is_fs)
        .map_err(|e| e.to_string())?;
    Ok(())
}

#[tauri::command]
fn set_discord_presence(
    state: State<'_, AppState>,
    details: String,
    state_str: String,
    large_text: String,
    server: Option<String>,
    players: Option<u32>,
) -> Result<(), String> {
    let _ = state.discord.tx.send(discord::RpcMessage::Set {
        details,
        state: state_str,
        large_text,
        server: server.clone(),
        players,
    });
    discord::set_current_server(server);
    Ok(())
}

#[tauri::command]
fn clear_discord_presence(state: State<'_, AppState>) -> Result<(), String> {
    let _ = state.discord.tx.send(discord::RpcMessage::Clear);
    Ok(())
}

/// Snapshot of the Discord RPC connection (account + pending join invites).
#[tauri::command]
fn discord_status() -> Result<Value, String> {
    let s = discord::discord_state();
    let user = s.user.as_ref().map(|u| {
        serde_json::json!({
            "id": u.id,
            "username": u.username,
            "global_name": u.global_name,
            "avatar_url": u.avatar_url,
        })
    });
    Ok(serde_json::json!({
        "connected": s.connected,
        "user": user,
        "current_server": s.current_server,
        "invites": s.invites.iter().map(|i| serde_json::json!({
            "secret": i.secret,
            "received_at": i.received_at,
        })).collect::<Vec<_>>(),
    }))
}

/// Combined Discord view for the Socials tab: RPC connection state, OAuth login,
/// the local user, current server, pending invites and the friends list.
#[tauri::command]
fn discord_social(state: State<'_, AppState>) -> Result<Value, String> {
    let s = discord::discord_state();
    let oauth = discord_auth::status(&state.data_dir);
    let oauth_logged_in = oauth
        .get("logged_in")
        .and_then(|v| v.as_bool())
        .unwrap_or(false);
    let oauth_user = oauth.get("user").cloned();

    // Normalize the identity into a single shape that always carries
    // `avatar_url`, preferring the OAuth user (richer, present once logged in)
    // and falling back to the RPC READY user.
    let user = if let Some(u) = &oauth_user {
        let id = u.get("id").and_then(|v| v.as_str()).unwrap_or("");
        let avatar = u.get("avatar").and_then(|v| v.as_str());
        let avatar_url = avatar.map(|a| {
            let ext = if a.starts_with("a_") { "gif" } else { "png" };
            format!("https://cdn.discordapp.com/avatars/{}/{}.{}", id, a, ext)
        });
        Some(serde_json::json!({
            "id": id,
            "username": u.get("username").and_then(|v| v.as_str()).unwrap_or(""),
            "global_name": u.get("global_name").and_then(|v| v.as_str()),
            "avatar_url": avatar_url,
        }))
    } else {
        s.user.as_ref().map(|u| {
            serde_json::json!({
                "id": u.id,
                "username": u.username,
                "global_name": u.global_name,
                "avatar_url": u.avatar_url,
            })
        })
    };

    // RPC-sourced friends carry live rich presence (game/version/join secret).
    let rpc_friends: Vec<serde_json::Value> = discord::friends()
        .iter()
        .map(|f| {
            serde_json::json!({
                "id": f.id,
                "username": f.username,
                "global_name": f.global_name,
                "avatar_url": f.avatar_url,
                "status": f.status,
                "game": f.game,
                "version": f.version,
                "join_secret": f.join_secret,
                "presence_known": f.presence_known,
                "kollegen": f.kollegen,
                "mutual_guilds": serde_json::json!([]),
            })
        })
        .collect();

    // OAuth-sourced friends (via the `relationships` scope) so users who only
    // authenticated in the browser – without the Discord desktop app / RPC –
    // can still see their friends. RPC entries win on id collisions because
    // they carry richer presence data.
    let mut merged: std::collections::HashMap<String, serde_json::Value> =
        std::collections::HashMap::new();
    for f in discord_auth::fetch_friends(&state.data_dir) {
        let key = match f.get("id").and_then(|v| v.as_str()) {
            Some(s) => s.to_string(),
            None => continue,
        };
        merged.insert(key, f);
    }
    for f in &rpc_friends {
        // Only let RPC friends override OAuth friends when Discord actually
        // delivered a real presence – otherwise they'd clobber a known friend
        // with a bogus "offline" status.
        let known = f
            .get("presence_known")
            .and_then(|v| v.as_bool())
            .unwrap_or(false);
        if !known {
            continue;
        }
        if let Some(id) = f.get("id").and_then(|v| v.as_str()) {
            merged.insert(id.to_string(), f.clone());
        }
    }
    let friends: Vec<serde_json::Value> = merged.into_values().collect();
    Ok(serde_json::json!({
        "rpc_connected": s.connected,
        "oauth_logged_in": oauth_logged_in,
        "oauth_user": oauth_user,
        "user": user,
        "current_server": s.current_server,
        "invites": s.invites.iter().map(|i| serde_json::json!({
            "secret": i.secret,
            "received_at": i.received_at,
        })).collect::<Vec<_>>(),
        "friends": friends,
    }))
}

/// Launches an instance and connects straight to the server from a Discord
/// join invite (`server` is the `host:port` the friend exposed). The secret is
/// also written to `join_request.json` so the in-game mod connects on launch.
#[tauri::command]
fn discord_join(
    state: State<'_, AppState>,
    instance_name: String,
    server: String,
) -> Result<String, String> {
    let _ = discord::write_join_request(&state.data_dir, &server);
    launch_game(state, instance_name, Some(server))
}

#[tauri::command]
fn discord_dismiss_invite(secret: String) -> Result<(), String> {
    discord::dismiss_invite(&secret);
    Ok(())
}

#[tauri::command]
fn discord_clear_invites() -> Result<(), String> {
    discord::clear_invites();
    Ok(())
}

// ─=== Discord OAuth (browser login) ===

#[tauri::command]
fn discord_oauth_start(state: State<'_, AppState>) -> Result<String, String> {
    discord_auth::start_flow(state.data_dir.clone())
}

#[tauri::command]
fn discord_oauth_status(state: State<'_, AppState>) -> Result<serde_json::Value, String> {
    Ok(discord_auth::status(&state.data_dir))
}

#[tauri::command]
fn discord_oauth_logout(state: State<'_, AppState>) -> Result<(), String> {
    discord_auth::logout(&state.data_dir);
    Ok(())
}

// ─=== Microsoft account management (Connections) ===

/// Switches the active Microsoft account to the one with the given uuid.
#[tauri::command]
fn ms_switch_account(state: State<'_, AppState>, uuid: String) -> Result<(), String> {
    auth::switch_account(&state.data_dir, &uuid).map_err(|e| e.to_string())
}

/// Removes the Microsoft account with the given uuid.
#[tauri::command]
fn ms_remove_account(state: State<'_, AppState>, uuid: String) -> Result<(), String> {
    auth::remove_account(&state.data_dir, &uuid).map_err(|e| e.to_string())
}

// ─=== Instance import from other launchers ===

#[tauri::command]
fn detect_launchers() -> Result<Vec<Value>, String> {
    Ok(crate::import::detect_launchers())
}

#[tauri::command]
fn list_launcher_instances(launcher_id: String) -> Result<Vec<Value>, String> {
    Ok(crate::import::list_launcher_instances(&launcher_id))
}

#[tauri::command]
fn import_instance(
    state: State<'_, AppState>,
    launcher_id: String,
    instance_name: String,
) -> Result<types::Instance, String> {
    crate::import::import_instance(&state.data_dir, &launcher_id, &instance_name)
        .map_err(|e| e.to_string())
}

/// Importiert ein Modrinth-Modpack (`.mrpack` oder `.zip` mit
/// `modrinth.index.json`) als neue Instanz.
#[tauri::command]
fn import_pack(state: State<'_, AppState>, path: String) -> Result<types::Instance, String> {
    crate::instance::import_pack(&state.data_dir, &path).map_err(|e| e.to_string())
}

#[tauri::command]
async fn kollegen_me(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    let data_dir = app.state::<AppState>().data_dir.clone();
    Ok(crate::presence::kollegen_me(&data_dir))
}

#[tauri::command]
async fn kollegen_friends(app: tauri::AppHandle) -> Result<serde_json::Value, String> {
    let data_dir = app.state::<AppState>().data_dir.clone();
    Ok(crate::presence::kollegen_friends(&data_dir))
}

#[tauri::command]
async fn kollegen_friend_add(app: tauri::AppHandle, target_id: String) -> Result<serde_json::Value, String> {
    let data_dir = app.state::<AppState>().data_dir.clone();
    Ok(crate::presence::kollegen_friend_add(&data_dir, &target_id))
}

#[tauri::command]
async fn kollegen_friend_remove(app: tauri::AppHandle, target_id: String) -> Result<serde_json::Value, String> {
    let data_dir = app.state::<AppState>().data_dir.clone();
    Ok(crate::presence::kollegen_friend_remove(&data_dir, &target_id))
}

// ─=== Skin / Cape Changer ===
#[tauri::command]
fn skin_list(state: State<'_, AppState>) -> Value {
    crate::skins::list_skins(&state.data_dir)
}
#[tauri::command]
fn skin_set_active(state: State<'_, AppState>, name: String) -> Value {
    crate::skins::set_active_skin(&state.data_dir, &name)
}
#[tauri::command]
fn skin_delete(state: State<'_, AppState>, name: String) -> Value {
    crate::skins::delete_skin(&state.data_dir, &name)
}
#[tauri::command]
fn skin_download_current(state: State<'_, AppState>) -> Value {
    crate::skins::download_current_skin(&state.data_dir)
}
#[tauri::command]
fn skin_upload(
    state: State<'_, AppState>,
    name: String,
    data: String,
    variant: String,
) -> Value {
    crate::skins::upload_skin(&state.data_dir, &name, &data, &variant)
}
#[tauri::command]
fn skin_mc_profile(state: State<'_, AppState>) -> Value {
    crate::skins::minecraft_profile(&state.data_dir)
}
#[tauri::command]
fn cape_equip(state: State<'_, AppState>, cape_id: String) -> Value {
    crate::skins::equip_cape(&state.data_dir, &cape_id)
}

#[tauri::command]
async fn check_app_update(app: tauri::AppHandle) -> Result<Option<Value>, String> {
    match crate::app_updates::check_info(&app).await {
        Ok(Some((version, notes))) => Ok(Some(serde_json::json!({
            "version": version,
            "notes": notes,
            "can_install": crate::app_updates::can_self_install(),
        }))),
        Ok(None) => Ok(None),
        Err(e) => Err(e),
    }
}

#[tauri::command]
async fn install_app_update(app: tauri::AppHandle) -> Result<(), String> {
    crate::app_updates::install(&app).await
}

/// Configures WebKitGTK's runtime location. The AppImage is not fully
/// self-contained for WebKit on every distro (notably Fedora), where
/// webkit2gtk4.1 is provided by the host – and the same applies when the user
/// extracts the AppImage and runs the binary directly. Without
/// `WEBKIT_EXEC_PATH` the WebKit web/network processes can't be found and the
/// window stays blank (white screen); without the host lib dirs on
/// `LD_LIBRARY_PATH` the spawned web process may fail to load libwebkit.
///
/// (The main binary's libwebkit is resolved by the dynamic linker at exec time,
/// which is handled separately via the RPATH baked into the binary at link
/// time – see `.cargo/config.toml`.) Must run before GTK/WebKit initializes.
fn setup_webkit_appimage_env() {
    // Collect candidate dirs that may contain the WebKit helper processes
    // (WebKitWebProcess / WebKitNetworkProcess). WebKit spawns these as child
    // processes; if `WEBKIT_EXEC_PATH` isn't set to a valid, *absolute* dir the
    // webkit process falls back to a path relative to the current prefix and
    // fails ("Unable to spawn a new child process: .../WebKitNetworkProcess" /
    // "Datei oder Verzeichnis nicht gefunden"), which aborts startup with a
    // white screen + SIGABRT. This happened for users who extract the AppImage
    // and run the bundled binary directly.
    let mut candidates: Vec<PathBuf> = Vec::new();

    // 1) Host system webkit dirs (most distros, incl. Fedora webkit2gtk4.1).
    for dir in [
        "/usr/libexec/webkit2gtk-4.1",
        "/usr/libexec/webkit2gtk-4.0",
        "/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1",
        "/usr/lib/x86_64-linux-gnu/webkit2gtk-4.0",
        "/usr/lib/webkit2gtk-4.1",
        "/usr/lib/webkit2gtk-4.0",
        "/usr/lib64/webkit2gtk-4.1",
        "/usr/lib64/webkit2gtk-4.0",
    ] {
        candidates.push(PathBuf::from(dir));
    }

    // 2) Bundled webkit shipped inside the AppImage / extracted squashfs-root.
    // The process runs from <root>/usr/bin/kollegen-client, so the webkit
    // helpers live at <root>/usr/lib/x86_64-linux-gnu/webkit2gtk-4.1 etc.
    if let Ok(exe) = std::env::current_exe() {
        if let Some(bindir) = exe.parent() {
            for rel in [
                "../lib/x86_64-linux-gnu/webkit2gtk-4.1",
                "../lib64/webkit2gtk-4.1",
                "../lib/webkit2gtk-4.1",
                "../lib/x86_64-linux-gnu/webkit2gtk-4.0",
                "../lib64/webkit2gtk-4.0",
                "../../usr/lib/x86_64-linux-gnu/webkit2gtk-4.1",
                "../../usr/lib64/webkit2gtk-4.1",
                "../../lib/x86_64-linux-gnu/webkit2gtk-4.1",
                "lib/x86_64-linux-gnu/webkit2gtk-4.1",
            ] {
                let p = bindir.join(rel);
                candidates.push(if p.is_absolute() {
                    p
                } else {
                    std::env::current_dir()
                        .map(|c| c.join(&p))
                        .unwrap_or(p)
                });
            }
        }
    }

    // 3) AppImage run-time mount dir (APPIMAGE env points at the .AppImage;
    //    AppImage's own runtime exposes the webkit dir under /tmp/.mount_*).
    if let Ok(ai) = std::env::var("APPIMAGE") {
        let ai = PathBuf::from(ai.trim());
        if let Some(parent) = ai.parent() {
            candidates.push(parent.join("usr/lib/x86_64-linux-gnu/webkit2gtk-4.1"));
        }
    }

    if std::env::var_os("WEBKIT_EXEC_PATH").is_none() {
        for dir in candidates {
            if dir.join("WebKitWebProcess").exists()
                || dir.join("WebKitNetworkProcess").exists()
            {
                let abs = if dir.is_absolute() {
                    dir
                } else {
                    std::env::current_dir()
                        .map(|c| c.join(&dir))
                        .unwrap_or(dir)
                };
                unsafe {
                    std::env::set_var("WEBKIT_EXEC_PATH", abs);
                }
                break;
            }
        }
    }

    // Diagnostic: show what WEBKIT_EXEC_PATH was resolved to (or that nothing
    // matched), so we can confirm whether the WebKit child-process lookup fix
    // is active in the running binary.
    match std::env::var_os("WEBKIT_EXEC_PATH") {
        Some(p) => eprintln!(
            "[kollegen] WEBKIT_EXEC_PATH = {}",
            p.to_string_lossy()
        ),
        None => eprintln!(
            "[kollegen] WEBKIT_EXEC_PATH NOT SET - no WebKit helper process dir found!"
        ),
    }

    // Make the host's webkit libraries discoverable for the spawned web process.
    // Append (don't replace) the usual system lib dirs so any bundled copy
    // still takes precedence.
    let extra_libs = ["/usr/lib64", "/usr/lib/x86_64-linux-gnu", "/usr/lib"];
    let existing: Vec<String> = std::env::var("LD_LIBRARY_PATH")
        .unwrap_or_default()
        .split(':')
        .filter(|s| !s.is_empty())
        .map(|s| s.to_string())
        .collect();
    let needs: Vec<&str> = extra_libs
        .iter()
        .copied()
        .filter(|d| Path::new(d).exists() && !existing.iter().any(|p| p == *d))
        .collect();
    if !needs.is_empty() {
        let mut paths = existing;
        paths.extend(needs.iter().map(|s| s.to_string()));
        unsafe {
            std::env::set_var("LD_LIBRARY_PATH", paths.join(":"));
        }
    }

    if std::env::var_os("WEBKIT_EXEC_PATH").is_none() {
        warn!(
            "WebKit-Webprozesse nicht gefunden (WEBKIT_EXEC_PATH). Ist webkit2gtk4.1 installiert? \
             Unter Fedora z.B. 'sudo dnf install webkit2gtk4.1'."
        );
    }
}

fn main() {
    // Wayland compatibility: WebKit's DMABUF renderer crashes/white-screens
    // under several Wayland compositors (e.g. "Error 71 dispatching to Wayland
    // display", or a blank white window). Disabling it is a no-op on X11, so
    // the app works on both backends.
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        unsafe {
            std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
        }
    }

    // Render-config variant selection (empirical, user-driven):
    //   * Wayland is handled exactly like v1.9.8  – DMABUF off + GPU
    //     compositing forced, NO GDK_BACKEND override.
    //   * X11     is handled exactly like v1.9.11 – same: DMABUF off + GPU
    //     compositing forced.
    // Both boil down to the same minimal, session-independent config, which is
    // what actually rendered correctly (rather than the session-adaptive
    // GDK_BACKEND/compositing-off experiments in 1.9.9–1.9.13, all of which
    // white-screened for the reporter). Must run before GTK/WebKit initializes.
    // Force WebKitGTK's GPU compositing mode (harmless/no-op if no GPU present).
    if std::env::var_os("WEBKIT_FORCE_COMPOSITING_MODE").is_none() {
        unsafe {
            std::env::set_var("WEBKIT_FORCE_COMPOSITING_MODE", "1");
        }
    }

    // AppImage: wire up the host's WebKit (Fedora etc.) so the webview doesn't
    // white-screen. Must run before GTK/WebKit initializes.
    setup_webkit_appimage_env();

    let data_dir = match ProjectDirs::from("dev", "kollegen", "KollegenClient") {
        Some(dir) => dir.data_dir().to_path_buf(),
        None => {
            eprintln!("Could not find project directory");
            std::process::exit(1);
        }
    };

    std::fs::create_dir_all(&data_dir).expect("Could not create data directory");

    let discord = discord::start(data_dir.clone());
    // Presence-Reporter: meldet die im Spiel erkannte Server-Präsenz an das
    // externe Backend, damit andere Kollegen-Client-Nutzer markiert werden.
    presence::start(data_dir.clone());
    // Profil + Freundesliste sofort schreiben (falls bereits mit Discord
    // angemeldet), damit die Begleit-Mod im Spiel direkt Daten hat.
    // Läuft auf einem eigenen Thread: `sync_social` macht einen (jetzt mit
    // kurzem Timeout versehenen) Netzwerk-Call zum Presence-Backend. Auf dem
    // Hauptthread ausgeführt, blockiert das den kompletten Tauri-/WebKit-Start
    // (Weißschirm/"lädt 20–30 Min"), solange das Backend nicht erreichbar ist.
    {
        let dd = data_dir.clone();
        std::thread::Builder::new()
            .name("kollegen-social-sync".into())
            .spawn(move || presence::sync_social(&dd))
            .ok();
    }
    // Initial "in launcher" presence (only shows if Discord is running).
    let _ = discord.tx.send(discord::RpcMessage::Set {
        details: "Kollegen Client".to_string(),
        state: "Im Launcher".to_string(),
        large_text: "Kollegen Client".to_string(),
        server: None,
        players: None,
    });

    let state = AppState {
        instances: Mutex::new(vec![]),
        accounts: Mutex::new(vec![]),
        data_dir: data_dir.clone(),
        logs: Arc::new(Mutex::new(vec![])),
        discord,
    };

    utils::init_logging(&data_dir);

    tauri::Builder::default()
        .manage(state)
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_updater::Builder::new().build())
        .setup(|app| {
            app_updates::spawn(app.handle().clone());
            Ok(())
        })
        .invoke_handler(tauri::generate_handler![
            get_instances,
            get_version,
            get_accounts,
            get_settings,
            save_settings,
            set_console_mode,
            create_instance,
            delete_instance,
            get_logs,
            get_available_versions,
            get_loaders_for_version,
            install_instance,
            launch_game,
            auth_check_status,
            auth_start,
            download_jre_command,
            modrinth_search,
            install_content,
            modrinth_versions,
            optimize_instance,
            list_content,
            delete_content,
            change_content_version,
            installed_project_ids,
            modrinth_project,
            open_url,
            write_theme_file,
            get_game_log,
            auto_resolve_conflict,
            toggle_fullscreen,
            set_discord_presence,
            clear_discord_presence,
            discord_status,
            discord_social,
            discord_join,
            discord_dismiss_invite,
            discord_clear_invites,
            discord_oauth_start,
            discord_oauth_status,
            discord_oauth_logout,
            ms_switch_account,
            ms_remove_account,
            detect_launchers,
            list_launcher_instances,
            import_instance,
            import_pack,
            check_app_update,
            install_app_update,
            kollegen_me,
            kollegen_friends,
            kollegen_friend_add,
            kollegen_friend_remove,
            skin_list,
            skin_set_active,
            skin_delete,
            skin_download_current,
            skin_upload,
            skin_mc_profile,
            cape_equip,
            open_logs_folder,
        ])
        .run(tauri::generate_context!())
        .unwrap_or_else(|e| {
            eprintln!("Kollegen Client failed to start: {e:?}");
            std::process::exit(1);
        });
}



