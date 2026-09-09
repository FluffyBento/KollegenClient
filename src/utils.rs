

use anyhow::Result;
use directories::ProjectDirs;
use log::info;
use serde::Serialize;
use sha1::Digest;
use std::fs;
use std::io::Write;
use std::path::{Path, PathBuf};
use std::time::Duration;


pub fn get_project_dirs() -> Result<PathBuf> {
    ProjectDirs::from("dev", "kollegen", "KollegenClient")
        .map(|p| p.data_dir().to_path_buf())
        .ok_or_else(|| anyhow::anyhow!("Could not find project directory"))
}


pub fn instances_file(dir: &Path) -> PathBuf {
    dir.join("instances.json")
}


pub fn accounts_file(dir: &Path) -> PathBuf {
    dir.join("accounts.json")
}


pub fn settings_file(dir: &Path) -> PathBuf {
    dir.join("settings.json")
}


pub fn log_file(dir: &Path) -> PathBuf {
    dir.join("launcher.log")
}


pub fn instance_dir(data_dir: &Path, name: &str) -> PathBuf {
    data_dir.join("instances").join(sanitize_filename::sanitize(name))
}


pub fn sanitize_name(name: &str) -> String {
    sanitize_filename::sanitize(name)
}


pub fn user_agent() -> &'static str {
    super::USER_AGENT
}


pub fn client_id() -> &'static str {
    super::client_id()
}


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


pub fn save_json<T: Serialize>(path: &Path, data: &T) -> Result<()> {
    if let Some(parent) = path.parent() {
        fs::create_dir_all(parent)?;
    }
    let content = serde_json::to_string_pretty(data)?;
    fs::write(path, content)?;
    Ok(())
}


pub fn download_file(url: &str, dest: &Path) -> Result<()> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(120))
        .build()?;
    download_file_client(&client, url, dest)
}


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









pub fn ensure_essential(name: &str, data_dir: &Path, mc_version: &str) -> Result<()> {
    let mods_dir = instance_dir(data_dir, name).join("mods");
    fs::create_dir_all(&mods_dir)?;
    let target = mods_dir.join("essentialmod.jar");

    
    
    
    
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
