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
    super::CLIENT_ID
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

/// Ensures the Essential mod is installed for an instance.
pub fn ensure_essential(name: &str, data_dir: &Path) -> Result<()> {
    let mods_dir = instance_dir(data_dir, name).join("mods");
    fs::create_dir_all(&mods_dir)?;
    let target = mods_dir.join("essentialmod.jar");

    // Already installed (valid, non-empty jar)?
    if target.exists() {
        if let Ok(meta) = fs::metadata(&target) {
            if meta.len() > 0 {
                return Ok(());
            }
        }
        // Stale/empty placeholder from a previous failed download.
        let _ = fs::remove_file(&target);
    }

    info!("Installing Essential mod for {}...", name);
    for url in [
        "https://cdn.modrinth.com/data/essential/files/latest/essential.jar",
        "https://github.com/sparkuniverse/essential-mod/releases/latest/download/essential.jar",
    ] {
        let client = reqwest::blocking::Client::builder()
            .user_agent(crate::USER_AGENT)
            .timeout(Duration::from_secs(60))
            .build()?;
        match client.get(url).send() {
            Ok(resp) if resp.status().is_success() => {
                if let Ok(bytes) = resp.bytes() {
                    // Only accept a real (non-empty) zip archive.
                    if bytes.len() > 0 && bytes.starts_with(b"PK") {
                        fs::write(&target, &bytes)?;
                        info!("Essential mod installed successfully.");
                        return Ok(());
                    }
                }
            }
            Err(e) => {
                log::warn!("Essential download failed ({}): {}", url, e);
            }
            _ => {}
        }
    }
    log::warn!(
        "Essential mod konnte nicht heruntergeladen werden; wird übersprungen, damit Fabric nicht blockiert wird."
    );
    Ok(())
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
