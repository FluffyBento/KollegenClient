// Utility functions for Kollegen Client launcher

use anyhow::Result;
use directories::ProjectDirs;
use log::info;
use serde::Serialize;
use sha1::Digest;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::Duration;

/// Returns the project data directory for Kollegen Client.
pub fn get_project_dirs() -> Result<PathBuf> {
    ProjectDirs::from("dev", "kollegen", "KollegenClient")
        .map(|p| p.data_dir().to_path_buf())
        .ok_or_else(|| anyhow::anyhow!("Could not find project directory"))
}

/// Returns the instances.json file path.
pub fn instances_file(dir: &Path) -> PathBuf {
    dir.join("instances.json")
}

/// Returns the accounts.json file path.
pub fn accounts_file(dir: &Path) -> PathBuf {
    dir.join("accounts.json")
}

/// Returns the settings.json file path.
pub fn settings_file(dir: &Path) -> PathBuf {
    dir.join("settings.json")
}

/// Returns the launcher.log file path.
pub fn log_file(dir: &Path) -> PathBuf {
    dir.join("launcher.log")
}

/// Returns the directory for a specific instance.
pub fn instance_dir(data_dir: &Path, name: &str) -> PathBuf {
    data_dir.join("instances").join(sanitize_filename::sanitize(name))
}

/// Sanitizes a filename for safe use across platforms.
pub fn sanitize_name(name: &str) -> String {
    sanitize_filename::sanitize(name)
}

/// Returns the user agent string for HTTP requests.
pub fn user_agent() -> &'static str {
    super::USER_AGENT
}

/// Returns the Prism Launcher client ID for Microsoft OAuth.
pub fn client_id() -> &'static str {
    super::client_id()
}

/// Loads JSON from a file path, returning default if file doesn't exist or parsing fails.
pub fn load_json<T: serde::de::DeserializeOwned>(path: &Path, default: T) -> T {
    if path.exists() {
        if let Ok(content) = fs::read_to_string(path) {
            if let Ok(data) = serde_json::from_str::<T>(&content) {
                return data;
            }
        }
    }
    default
}

/// Saves JSON data to a file path.
pub fn save_json<T: Serialize>(path: &Path, data: &T) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let content = serde_json::to_string_pretty(data)?;
    fs::write(path, content)?;
    Ok(())
}

/// Downloads a file to the specified destination.
pub fn download_file(url: &str, dest: &Path) -> Result<()> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(120))
        .build()?;
    download_file_client(&client, url, dest)
}

/// Returns the lowercase hex SHA-1 of `data`, matching Mojang's asset hashes.
pub fn sha1_hex(data: &[u8]) -> String {
    let mut hasher = sha1::Sha1::new();
    hasher.update(data);
    let digest = hasher.finalize();
    let mut out = String::with_capacity(digest.len() * 2);
    for b in digest.as_slice() {
        out.push_str(&format!("{:02x}", b));
    }
    out
}

/// Downloads a file using a caller-provided client (reused across many downloads).
///
/// Writes to a temporary `.part` file first and then atomically renames it into
/// place, so an interrupted/truncated download never leaves a corrupt file at
/// `dest` (which would otherwise be treated as "already downloaded" and skipped).
pub fn download_file_client(client: &reqwest::blocking::Client, url: &str, dest: &Path) -> Result<()> {
    let resp = client.get(url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow::anyhow!("{}", resp.status()));
    }
    let bytes = resp.bytes()?;
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = dest.with_extension("part");
    let _ = fs::remove_file(&tmp);
    fs::write(&tmp, &bytes)?;
    fs::rename(&tmp, dest)?;
    Ok(())
}

/// Ensures the Essential mod is installed and kept up to date for an instance.
///
/// Essential wird bei jedem Start neu heruntergeladen, wenn eine Verbindung
/// besteht: veraltete Essential-Versionen laden eine inkompatible
/// kotlinx.serialization und verursachen einen AbstractMethodError
/// (typeParametersSerializers) im Cosmetics-Loader. Schlägt der Download fehl
/// (offline/zentrale nicht erreichbar), bleibt die vorhandene Datei erhalten,
/// damit der Start nicht blockiert wird.
pub fn ensure_essential(name: &str, data_dir: &Path, mc_version: &str) -> Result<()> {
    let mods_dir = instance_dir(data_dir, name).join("mods");
    fs::create_dir_all(&mods_dir)?;
    let target = mods_dir.join("essentialmod.jar");

    // Stale/empty placeholder from a previous failed download. Auch
    // Nicht-Fabric-Stubs ohne `fabric.mod.json` (z.B. ein 407-Byte-Placeholder,
    // "PK"-Magie, aber keine Mod) entfernen, damit sie die Suche nicht als
    // "vorhandene Version" verwerfen und Essentia nie aktualisiert wird.
    if target.exists() {
        let mut remove = fs::metadata(&target)
            .map(|meta| meta.len() == 0)
            .unwrap_or(false);
        if !remove {
            let valid_fabric_mod = fs::read(&target)
                .ok()
                .and_then(|b| {
                    if b.starts_with(b"PK") {
                        let mut zip = zip::ZipArchive::new(std::io::Cursor::new(b)).ok()?;
                        for i in 0..zip.len() {
                            let name = zip.by_index(i).ok()?.name().to_string();
                            if name == "fabric.mod.json" {
                                return Some(true);
                            }
                        }
                        Some(false)
                    } else {
                        Some(false)
                    }
                })
                .unwrap_or(false);
            if !valid_fabric_mod {
                log::warn!(
                    "Ersetze invalide essentialmod.jar (kein Fabric-Mod): {}",
                    target.display()
                );
                remove = true;
            }
        }
        if remove {
            let _ = fs::remove_file(&target);
        }
    }

    info!("Aktualisiere Essential-Mod für {} (MC {})...", name, mc_version);
    // Modrinth-CDN hat kein stable "<slug>/files/latest"-Muster; die Datei-URLs
    // folgen "data/<projekt-id>/versions/<hash>/<datei>" und müssen über die
    // Version-API aufgelöst werden. Essential veröffentlicht pro Minecraft-
    // Version ein eigenes Jar (NICHT multiversion-fähig), daher die Versionen
    // auf die Ziel-MC-Version filtern und die neueste davon nehmen.
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(60))
        .build()?;
    let versions: Vec<serde_json::Value> = match client
        .get("https://api.modrinth.com/v2/project/essential/version?loaders=%5B%22fabric%22%5D")
        .send()
    {
        Ok(resp) if resp.status().is_success() => match resp.json() {
            Ok(v) => v,
            Err(e) => {
                log::warn!("Essential-Versionsliste nicht lesbar: {}", e);
                vec![]
            }
        },
        Ok(resp) => {
            log::warn!("Essential-API antwortete mit Status {}", resp.status());
            vec![]
        }
        Err(e) => {
            log::warn!("Essential-API nicht erreichbar: {}", e);
            vec![]
        }
    };
    for version in versions {
        let supports_mc = version
            .get("game_versions")
            .and_then(|g| g.as_array())
            .map(|g| g.iter().any(|v| v.as_str() == Some(mc_version)))
            .unwrap_or(false);
        if !supports_mc {
            continue;
        }
        let files = version
            .get("files")
            .and_then(|f| f.as_array())
            .cloned()
            .unwrap_or_default();
        for file in files {
            if file.get("primary").and_then(|p| p.as_bool()).unwrap_or(false) != true {
                continue;
            }
            let Some(url) = file.get("url").and_then(|u| u.as_str()) else {
                continue;
            };
            match client.get(url).send() {
                Ok(resp) if resp.status().is_success() => {
                    if let Ok(bytes) = resp.bytes() {
                        // Only accept a real (non-empty) zip archive.
                        if bytes.len() > 0 && bytes.starts_with(b"PK") {
                            fs::write(&target, &bytes)?;
                            info!("Essential mod aktualisiert/installiert ({}).", url);
                            return Ok(());
                        }
                    }
                }
                Err(e) => {
                    log::warn!("Essential-Download fehlgeschlagen ({}): {}", url, e);
                }
                _ => {}
            }
        }
    }
    if target.exists() {
        log::warn!(
            "Essential konnte nicht aktualisiert werden; vorhandene Version wird beibehalten (API/Download fehlgeschlagen)."
        );
        Ok(())
    } else {
        log::warn!(
            "Essential mod konnte nicht heruntergeladen werden; wird übersprungen, damit Fabric nicht blockiert wird."
        );
        Ok(())
    }
}

/// Appends a message to the launcher log.
pub fn append_log(state: &crate::AppState, msg: &str) {
    let ts = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
    let line = format!("[{}] {}", ts, msg);
    if let Ok(mut logs) = state.logs.lock() {
        logs.push(line.clone());
        if logs.len() > crate::MAX_LOG_LINES {
            logs.remove(0);
        }
    }
    let lf = log_file(&state.data_dir);
    if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(lf) {
        let _ = std::writeln!(f, "{}", line);
    }
    info!("{}", line);
}

/// Initializes logging to file.
pub fn init_logging(data_dir: &Path) {
    let log_path = log_file(data_dir);
    if let Some(parent) = log_path.parent() {
        let _ = fs::create_dir_all(parent);
    }

    if let Ok(file) = fs::OpenOptions::new()
        .create(true)
        .append(true)
        .open(&log_path)
    {
        env_logger::Builder::from_env(
            env_logger::Env::default().default_filter_or("debug"),
        )
        .target(env_logger::Target::Pipe(Box::new(file)))
        .init();
    }
}
