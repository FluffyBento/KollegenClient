use crate::instance::{download_assets_to_dir, download_libraries};
use crate::java::sanitize_java_env;
use crate::types::{Account, Instance, MojangVersionManifest, VersionJson};
use crate::AppState;
use anyhow::{anyhow, Result};
use log::{info, warn};
use serde::Serialize;
use serde_json::Value;
use sha1::Digest;
use std::fs;
use std::io::{Cursor, Read};
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::sync::{
    atomic::{AtomicU64, Ordering},
    Arc,
};
use std::thread;
use std::time::Duration;
use tauri::Emitter;

const HORIZON_REPO_API: &str = "https://api.github.com/repos/Stink-O/horizon/releases";
const ORBITAL_REPO_API: &str = "https://api.github.com/repos/Stink-O/orbital-laser/releases/latest";
const ORBITAL_REPO_URL: &str = "https://github.com/Stink-O/orbital-laser";
const ORBITAL_WIN_FALLBACK_URL: &str = "https://github.com/Stink-O/orbital-laser/releases/download/v0.5.16/orbital-laser-windows-test.zip";
const HORIZON_TAG_FALLBACK: &str = "orbital-mod-api-13";
const ORBITAL_ZIP_FALLBACK_URL: &str = "https://github.com/Stink-O/orbital-laser/releases/download/v0.5.18/orbital-laser-v0.5.18.zip";

#[derive(Clone, Serialize)]
pub struct OrbitalProgress {
    pub step: String,
    pub detail: String,
    pub pct: u8,
}

#[derive(Clone, Serialize)]
pub struct RustModCatalogEntry {
    pub id: String,
    pub slug: String,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    pub project_type: String,
    pub categories: Vec<String>,
    pub version: String,
    pub engine: String,
    pub mc_version: String,
    pub external_url: String,
    pub is_rust_mod: bool,
}

fn http_client(timeout_secs: u64) -> Result<reqwest::blocking::Client> {
    reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(timeout_secs))
        .build()
        .map_err(|e| anyhow!(e.to_string()))
}

fn emit_progress(app: Option<&tauri::AppHandle>, step: &str, detail: &str, pct: u8) {
    info!("Orbital Setup [{}%]: {} - {}", pct, step, detail);
    if let Some(handle) = app {
        let _ = handle.emit(
            "orbital-install-progress",
            OrbitalProgress {
                step: step.to_string(),
                detail: detail.to_string(),
                pct,
            },
        );
    }
}

pub fn list_rust_mods() -> Vec<RustModCatalogEntry> {
    let mut latest_ver = "v0.5.18".to_string();
    if let Ok(c) = http_client(10) {
        if let Ok(resp) = c.get(ORBITAL_REPO_API).send() {
            if resp.status().is_success() {
                if let Ok(v) = resp.json::<Value>() {
                    if let Some(tag) = v.get("tag_name").and_then(|t| t.as_str()) {
                        if !tag.is_empty() {
                            latest_ver = tag.to_string();
                        }
                    }
                }
            }
        }
    }

    vec![RustModCatalogEntry {
        id: "orbital-laser".to_string(),
        slug: "orbital-laser".to_string(),
        title: "Orbital Laser".to_string(),
        description: "23 zerstörerische Orbital-Laser-Schläge für Horizon (Rust-Engine für MC 26.3). Wähle per Rad (R) einen Laser, markiere das Ziel und starte einen Orbitalschlag mit eigenen Shadern, Kratern und Sounds.".to_string(),
        icon_url: Some("https://raw.githubusercontent.com/Stink-O/orbital-laser/main/docs/62-skybreak-eye-flare.jpg".to_string()),
        downloads: 0,
        project_type: "modpack".to_string(),
        categories: vec!["rust-mod".to_string(), "horizon".to_string()],
        version: latest_ver,
        engine: "Horizon Rust".to_string(),
        mc_version: "26.3".to_string(),
        external_url: ORBITAL_REPO_URL.to_string(),
        is_rust_mod: true,
    }]
}

fn horizon_exe_name(base: &str) -> String {
    if cfg!(target_os = "windows") {
        format!("{}.exe", base)
    } else {
        base.to_string()
    }
}

fn jdk_exe(name: &str) -> String {
    if cfg!(target_os = "windows") {
        format!("{}.exe", name)
    } else {
        name.to_string()
    }
}

fn probe_jdk_major(bin: &Path) -> Option<u32> {
    let mut cmd = Command::new(bin);
    crate::java::sanitize_java_env(&mut cmd);
    let out = cmd.arg("-version").output().ok()?;
    let combined = format!(
        "{}\n{}",
        String::from_utf8_lossy(&out.stdout),
        String::from_utf8_lossy(&out.stderr)
    );
    for line in combined.lines() {
        let ver = if let Some(q) = line.split('"').nth(1) {
            q
        } else if let Some(rest) = line.trim().strip_prefix("javac ") {
            rest.trim()
        } else {
            continue;
        };
        if let Some(maj) = ver.split('.').next().and_then(|s| s.parse::<u32>().ok()) {
            return Some(maj);
        }
    }
    None
}

fn is_valid_jdk_home(home: &Path, required_major: u32) -> bool {
    let java_bin = home.join("bin").join(jdk_exe("java"));
    let javac_bin = home.join("bin").join(jdk_exe("javac"));
    java_bin.is_file()
        && javac_bin.is_file()
        && probe_jdk_major(&javac_bin) == Some(required_major)
}

fn find_jdk_bin_recursive(dir: &Path) -> Option<PathBuf> {
    let candidate = dir.join("bin").join(jdk_exe("javac"));
    if candidate.is_file() {
        return Some(dir.to_path_buf());
    }
    if let Ok(entries) = fs::read_dir(dir) {
        for e in entries.flatten() {
            let p = e.path();
            if p.is_dir() {
                if let Some(found) = find_jdk_bin_recursive(&p) {
                    return Some(found);
                }
            }
        }
    }
    None
}

fn ensure_jdk_25(data_dir: &Path) -> Result<PathBuf> {
    let mut candidates: Vec<PathBuf> = vec![data_dir.join("jdk-25")];
    if let Ok(home) = std::env::var("JAVA_HOME") {
        candidates.push(PathBuf::from(home));
    }
    #[cfg(unix)]
    {
        for root in ["/usr/lib/jvm", "/usr/lib64/jvm"] {
            if let Ok(entries) = fs::read_dir(root) {
                for e in entries.flatten() {
                    candidates.push(e.path());
                }
            }
        }
    }
    #[cfg(windows)]
    {
        for root in [
            "C:\\Program Files\\Java",
            "C:\\Program Files\\Eclipse Adoptium",
            "C:\\Program Files\\Microsoft",
        ] {
            if let Ok(entries) = fs::read_dir(root) {
                for e in entries.flatten() {
                    candidates.push(e.path());
                }
            }
        }
    }

    for cand in &candidates {
        if is_valid_jdk_home(cand, 25) {
            return Ok(cand.clone());
        }
    }

    let jdk_dir = data_dir.join("jdk-25");
    if jdk_dir.exists() {
        let _ = fs::remove_dir_all(&jdk_dir);
    }
    let os_name = if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "mac"
    } else {
        "linux"
    };
    let arch = if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else {
        "x64"
    };
    let url = format!(
        "https://api.adoptium.net/v3/binary/latest/25/ga/{}/{}/jdk/hotspot/normal/eclipse",
        os_name, arch
    );
    let client = http_client(180)?;
    let resp = client.get(&url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!("JDK 25 Download fehlgeschlagen: HTTP {}", resp.status()));
    }
    let bytes = resp.bytes()?;
    fs::create_dir_all(&jdk_dir)?;

    if os_name == "windows" {
        let mut archive = zip::ZipArchive::new(Cursor::new(bytes))?;
        archive.extract(&jdk_dir)?;
    } else {
        let gz = flate2::read::GzDecoder::new(Cursor::new(bytes));
        let mut tar = tar::Archive::new(gz);
        tar.unpack(&jdk_dir)?;
    }

    if !jdk_dir.join("bin").join(jdk_exe("javac")).is_file() {
        if let Some(root) = find_jdk_bin_recursive(&jdk_dir) {
            if root != jdk_dir {
                let tmp_move = data_dir.join("jdk-25-unpack");
                let _ = fs::remove_dir_all(&tmp_move);
                fs::rename(&root, &tmp_move)?;
                let _ = fs::remove_dir_all(&jdk_dir);
                fs::rename(&tmp_move, &jdk_dir)?;
            }
        }
    }

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for tool in ["java", "javac", "jar", "jpackage"] {
            let p = jdk_dir.join("bin").join(tool);
            if p.is_file() {
                if let Ok(mut perms) = fs::metadata(&p).map(|m| m.permissions()) {
                    perms.set_mode(0o755);
                    let _ = fs::set_permissions(&p, perms);
                }
            }
        }
    }

    if !is_valid_jdk_home(&jdk_dir, 25) {
        return Err(anyhow!("JDK 25 wurde geladen, aber javac 25 fehlt in {}", jdk_dir.display()));
    }
    Ok(jdk_dir)
}

fn fetch_orbital_release_asset(client: &reqwest::blocking::Client) -> (String, Option<String>) {
    if let Ok(resp) = client.get(ORBITAL_REPO_API).send() {
        if resp.status().is_success() {
            if let Ok(v) = resp.json::<Value>() {
                let tag = v
                    .get("tag_name")
                    .and_then(|t| t.as_str())
                    .unwrap_or("v0.5.18")
                    .to_string();
                if let Some(assets) = v.get("assets").and_then(|a| a.as_array()) {
                    for a in assets {
                        let name = a.get("name").and_then(|n| n.as_str()).unwrap_or("");
                        if name.starts_with("orbital-laser-")
                            && name.ends_with(".zip")
                            && !name.contains("windows-test")
                            && !name.contains("native")
                        {
                            if let Some(url) = a.get("browser_download_url").and_then(|u| u.as_str()) {
                                return (url.to_string(), Some(tag));
                            }
                        }
                    }
                }
            }
        }
    }
    (ORBITAL_ZIP_FALLBACK_URL.to_string(), None)
}

fn fetch_horizon_release(client: &reqwest::blocking::Client) -> (String, String, Option<String>) {
    if let Ok(resp) = client.get(HORIZON_REPO_API).send() {
        if resp.status().is_success() {
            if let Ok(list) = resp.json::<Vec<Value>>() {
                for rel in list {
                    let tag = rel.get("tag_name").and_then(|t| t.as_str()).unwrap_or("");
                    if !tag.starts_with("orbital-mod-api-") {
                        continue;
                    }
                    let api_ver: u32 = tag
                        .trim_start_matches("orbital-mod-api-")
                        .parse()
                        .unwrap_or(0);
                    if api_ver < 13 {
                        continue;
                    }
                    if let Some(assets) = rel.get("assets").and_then(|a| a.as_array()) {
                        let target_win = cfg!(target_os = "windows");
                        let mut matched_url = None;
                        for a in assets {
                            let name = a.get("name").and_then(|n| n.as_str()).unwrap_or("");
                            let ok = if target_win {
                                name.contains("windows") && name.ends_with(".zip")
                            } else {
                                name.contains("linux-x86_64") && name.ends_with(".tar.gz")
                            };
                            if ok {
                                matched_url = a
                                    .get("browser_download_url")
                                    .and_then(|u| u.as_str())
                                    .map(|s| s.to_string());
                                break;
                            }
                        }
                        if let Some(url) = matched_url {
                            return (tag.to_string(), url, rel.get("tarball_url").and_then(|u| u.as_str()).map(|s| s.to_string()));
                        }
                    }
                }
            }
        }
    }
    let fallback_bin = format!(
        "https://github.com/Stink-O/horizon/releases/download/{}/horizon-orbital-linux-x86_64.tar.gz",
        HORIZON_TAG_FALLBACK
    );
    (HORIZON_TAG_FALLBACK.to_string(), fallback_bin, None)
}

fn unpack_strip_top_tar_gz(bytes: &[u8], dest: &Path) -> Result<()> {
    let gz = flate2::read::GzDecoder::new(Cursor::new(bytes));
    let mut tar = tar::Archive::new(gz);
    for entry in tar.entries()? {
        let mut entry = entry?;
        let raw_path = entry.path()?.to_path_buf();
        let mut comps = raw_path.components();
        comps.next();
        let rel: PathBuf = comps.as_path().to_path_buf();
        if rel.as_os_str().is_empty() {
            continue;
        }
        let target = dest.join(&rel);
        if entry.header().entry_type().is_dir() {
            fs::create_dir_all(&target)?;
        } else {
            if let Some(p) = target.parent() {
                fs::create_dir_all(p)?;
            }
            entry.unpack(&target)?;
        }
    }
    Ok(())
}

fn unpack_horizon_sources_from_tar_gz(bytes: &[u8], horizon_dir: &Path) -> Result<()> {
    let gz = flate2::read::GzDecoder::new(Cursor::new(bytes));
    let mut tar = tar::Archive::new(gz);
    for entry in tar.entries()? {
        let mut entry = entry?;
        let raw_path = entry.path()?.to_path_buf();
        let mut comps = raw_path.components();
        comps.next();
        let rel: PathBuf = comps.as_path().to_path_buf();
        let rel_str = rel.to_string_lossy().replace('\\', "/");
        if rel_str != "research-lock.json" && !rel_str.starts_with("mods/horizon/") {
            continue;
        }
        let target = horizon_dir.join(&rel);
        if entry.header().entry_type().is_dir() {
            fs::create_dir_all(&target)?;
        } else {
            if let Some(p) = target.parent() {
                fs::create_dir_all(p)?;
            }
            entry.unpack(&target)?;
        }
    }
    Ok(())
}

fn extract_orbital_mod_from_zip(bytes: &[u8], game_dir: &Path) -> Result<()> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes))?;
    let mut orbital_prefix: Option<String> = None;
    for i in 0..archive.len() {
        if let Ok(f) = archive.by_index(i) {
            let n = f.name().replace('\\', "/");
            if let Some(pos) = n.find("/orbital/mod.wasm") {
                orbital_prefix = Some(n[..=pos + "orbital".len()].to_string());
                break;
            }
            if n == "orbital/mod.wasm" {
                orbital_prefix = Some("orbital".to_string());
                break;
            }
        }
    }
    let prefix = orbital_prefix
        .ok_or_else(|| anyhow!("Konnte orbital/mod.wasm im Orbital-Laser-Archiv nicht finden"))?;

    let dest_mod = game_dir.join("horizon-mods").join("orbital");
    if dest_mod.exists() {
        fs::remove_dir_all(&dest_mod)?;
    }
    fs::create_dir_all(&dest_mod)?;

    let prefix_slash = format!("{}/", prefix.trim_end_matches('/'));
    for i in 0..archive.len() {
        let mut f = archive.by_index(i)?;
        let n = f.name().replace('\\', "/");
        let Some(rel) = n.strip_prefix(&prefix_slash) else {
            continue;
        };
        if rel.is_empty() {
            continue;
        }
        let target = dest_mod.join(rel);
        if f.is_dir() || n.ends_with('/') {
            fs::create_dir_all(&target)?;
        } else {
            if let Some(p) = target.parent() {
                fs::create_dir_all(p)?;
            }
            let mut buf = Vec::new();
            f.read_to_end(&mut buf)?;
            fs::write(&target, buf)?;
        }
    }
    Ok(())
}

fn extract_windows_bundle_from_zip(bytes: &[u8], horizon_dir: &Path, game_dir: &Path) -> Result<()> {
    let mut archive = zip::ZipArchive::new(Cursor::new(bytes))?;
    for i in 0..archive.len() {
        let mut f = archive.by_index(i)?;
        let n = f.name().replace('\\', "/");
        let mut comps = n.splitn(2, '/');
        let _top = comps.next();
        let rest = match comps.next() {
            Some(r) if !r.is_empty() => r,
            _ => continue,
        };
        let target = if let Some(hrel) = rest.strip_prefix("horizon/") {
            if hrel.is_empty() {
                continue;
            }
            horizon_dir.join(hrel)
        } else if let Some(grel) = rest.strip_prefix("game/") {
            if grel.is_empty() {
                continue;
            }
            game_dir.join(grel)
        } else {
            continue;
        };
        if f.is_dir() || n.ends_with('/') {
            fs::create_dir_all(&target)?;
        } else {
            if let Some(p) = target.parent() {
                fs::create_dir_all(p)?;
            }
            let mut buf = Vec::new();
            f.read_to_end(&mut buf)?;
            fs::write(&target, buf)?;
        }
    }
    Ok(())
}

fn horizon_resource_bytes(name: &str, embedded: &[u8]) -> Vec<u8> {
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            for cand in [
                dir.join("resources").join(name),
                dir.join(name),
                dir.join("../Resources").join(name),
                dir.join("../lib/Kollegen Client/resources").join(name),
                dir.join("../lib/kollegen-client/resources").join(name),
            ] {
                if cand.is_file() {
                    if let Ok(b) = fs::read(&cand) {
                        if b.starts_with(b"PK") {
                            return b;
                        }
                    }
                }
            }
        }
    }
    let manifest_cand = Path::new(env!("CARGO_MANIFEST_DIR"))
        .join("resources")
        .join(name);
    if manifest_cand.is_file() {
        if let Ok(b) = fs::read(&manifest_cand) {
            if b.starts_with(b"PK") {
                return b;
            }
        }
    }
    embedded.to_vec()
}

fn ensure_horizon_branding_pack(game_dir: &Path) -> Result<()> {
    let rp_dir = game_dir.join("resourcepacks");
    fs::create_dir_all(&rp_dir)?;
    let target = rp_dir.join("kollegen_branding.zip");
    let first_pack = !target.is_file();
    let bytes = horizon_resource_bytes(
        "kollegen_branding.zip",
        include_bytes!("../resources/kollegen_branding.zip"),
    );
    let unchanged = fs::read(&target).map(|cur| cur == bytes).unwrap_or(false);
    if !unchanged {
        fs::write(&target, &bytes)?;
    }

    let options_path = game_dir.join("options.txt");
    let existing = if options_path.is_file() {
        fs::read_to_string(&options_path).unwrap_or_default()
    } else {
        String::new()
    };
    let pack_entry = "\"file/kollegen_branding.zip\"";
    let mut lines: Vec<String> = Vec::new();
    let mut has_rp = false;
    let mut changed = false;
    for ln in existing.lines() {
        if let Some(val) = ln.strip_prefix("resourcePacks:") {
            has_rp = true;
            let t = val.trim();
            if t == "[]" {
                lines.push(format!("resourcePacks:[\"vanilla\",{}]", pack_entry));
                changed = true;
                continue;
            } else if first_pack && !t.contains("kollegen_branding.zip") && t.ends_with(']') {
                let inner = t[1..t.len() - 1].trim();
                let next = if inner.is_empty() {
                    format!("resourcePacks:[\"vanilla\",{}]", pack_entry)
                } else {
                    format!("resourcePacks:[{},{}]", inner, pack_entry)
                };
                lines.push(next);
                changed = true;
                continue;
            }
        }
        lines.push(ln.to_string());
    }
    if !has_rp {
        lines.push(format!("resourcePacks:[\"vanilla\",{}]", pack_entry));
        changed = true;
    }
    if changed {
        fs::write(&options_path, lines.join("\n") + "\n")?;
    }
    Ok(())
}

fn ensure_horizon_companion_mod(game_dir: &Path) -> Result<()> {
    let mods_dir = game_dir.join("horizon-mods");
    fs::create_dir_all(&mods_dir)?;
    let target = mods_dir.join("kollegen_companion.hmod");
    let first_install = !target.is_file();
    let bytes = horizon_resource_bytes(
        "kollegen_companion.hmod",
        include_bytes!("../resources/kollegen_companion.hmod"),
    );
    let unchanged = fs::read(&target).map(|cur| cur == bytes).unwrap_or(false);
    if !unchanged {
        fs::write(&target, &bytes)?;
    }
    let mods_txt = game_dir.join("horizon-mods.txt");
    if first_install || !mods_txt.is_file() {
        set_horizon_kv_line(&mods_txt, "mod.kollegen_companion", "true")?;
    }
    let _ = ensure_horizon_branding_pack(game_dir);
    Ok(())
}

fn set_horizon_kv_line(path: &Path, key: &str, value: &str) -> Result<()> {
    let existing = if path.is_file() {
        fs::read_to_string(path).unwrap_or_default()
    } else {
        String::new()
    };
    let mut lines: Vec<String> = Vec::new();
    let mut replaced = false;
    for ln in existing.lines() {
        if let Some((k, _)) = ln.split_once(':') {
            if k == key {
                lines.push(format!("{}:{}", key, value));
                replaced = true;
                continue;
            }
        }
        lines.push(ln.to_string());
    }
    if !replaced {
        lines.push(format!("{}:{}", key, value));
    }
    if let Some(p) = path.parent() {
        fs::create_dir_all(p)?;
    }
    fs::write(path, lines.join("\n") + "\n")?;
    Ok(())
}

fn sha1_file_hex(path: &Path) -> Result<String> {
    let mut f = fs::File::open(path)?;
    let mut hasher = sha1::Sha1::new();
    let mut buf = vec![0u8; 65536];
    loop {
        let n = f.read(&mut buf)?;
        if n == 0 {
            break;
        }
        hasher.update(&buf[..n]);
    }
    let d = hasher.finalize();
    Ok(d.iter().map(|b| format!("{:02x}", b)).collect())
}

fn ensure_research_lock_accepts(
    lock_path: &Path,
    manifest_sha1: &str,
    asset_index_sha1: Option<&str>,
) -> Result<()> {
    if !lock_path.is_file() {
        return Ok(());
    }
    let raw = fs::read_to_string(lock_path)?;
    let mut v: Value = serde_json::from_str(&raw)?;
    if let Some(mc) = v.get_mut("minecraft").and_then(|m| m.as_object_mut()) {
        let pin_manifest = mc
            .get("manifest_sha1")
            .and_then(|x| x.as_str())
            .unwrap_or("")
            .to_string();
        if pin_manifest != manifest_sha1 {
            let arr = mc
                .entry("accepted_manifest_sha1")
                .or_insert_with(|| Value::Array(Vec::new()));
            if let Some(list) = arr.as_array_mut() {
                if !list.iter().any(|x| x.as_str() == Some(manifest_sha1)) {
                    list.push(Value::String(manifest_sha1.to_string()));
                }
            }
        }
        if let Some(asha1) = asset_index_sha1 {
            let arr = mc
                .entry("accepted_asset_index_sha1")
                .or_insert_with(|| Value::Array(Vec::new()));
            if let Some(list) = arr.as_array_mut() {
                if !list.iter().any(|x| x.as_str() == Some(asha1)) {
                    list.push(Value::String(asha1.to_string()));
                }
            }
        }
    }
    fs::write(lock_path, serde_json::to_string_pretty(&v)?)?;
    Ok(())
}

fn prepare_minecraft_263_files(
    app: Option<&tauri::AppHandle>,
    mc_dir: &Path,
    lock_path: &Path,
) -> Result<PathBuf> {
    let version = "26.3";
    let version_dir = mc_dir.join("versions").join(version);
    let libs_dir = mc_dir.join("libraries");
    let assets_dir = mc_dir.join("assets");
    fs::create_dir_all(&version_dir)?;
    fs::create_dir_all(&libs_dir)?;
    fs::create_dir_all(&assets_dir)?;

    let client = http_client(120)?;
    emit_progress(app, "Minecraft 26.3", "Lade Version-Manifest...", 45);
    let manifest_resp = client
        .get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
        .send()?;
    if !manifest_resp.status().is_success() {
        return Err(anyhow!("Konnte Minecraft Version-Manifest nicht laden"));
    }
    let manifest: MojangVersionManifest = manifest_resp.json()?;
    let entry = manifest
        .versions
        .iter()
        .find(|v| v.id == version)
        .ok_or_else(|| anyhow!("Minecraft 26.3 nicht im Manifest gefunden"))?;

    let raw_vjson_bytes = client.get(&entry.url).send()?.bytes()?.to_vec();
    let raw_vjson_sha1 = crate::utils::sha1_hex(&raw_vjson_bytes);
    let vjson_path = version_dir.join("26.3.json");
    fs::write(&vjson_path, &raw_vjson_bytes)?;

    let raw_vjson: Value = serde_json::from_slice(&raw_vjson_bytes)?;
    let vjson: VersionJson = serde_json::from_slice(&raw_vjson_bytes)?;

    let asset_index_sha1 = raw_vjson
        .get("assetIndex")
        .and_then(|a| a.get("sha1"))
        .and_then(|s| s.as_str());
    ensure_research_lock_accepts(lock_path, &raw_vjson_sha1, asset_index_sha1)?;

    let client_dl = raw_vjson
        .get("downloads")
        .and_then(|d| d.get("client"))
        .ok_or_else(|| anyhow!("Kein Client-Download in 26.3.json"))?;
    let client_url = client_dl
        .get("url")
        .and_then(|u| u.as_str())
        .ok_or_else(|| anyhow!("Client-URL fehlt"))?;
    let client_sha1 = client_dl.get("sha1").and_then(|s| s.as_str()).unwrap_or("");
    let client_jar = version_dir.join("26.3.jar");
    if !client_jar.is_file() || (!client_sha1.is_empty() && sha1_file_hex(&client_jar).unwrap_or_default() != client_sha1) {
        emit_progress(app, "Minecraft 26.3", "Lade 26.3 Client-JAR...", 50);
        crate::utils::download_file_client(&client, client_url, &client_jar)?;
    }

    let server_dl = raw_vjson
        .get("downloads")
        .and_then(|d| d.get("server"))
        .ok_or_else(|| anyhow!("Kein Server-Download in 26.3.json"))?;
    let server_url = server_dl
        .get("url")
        .and_then(|u| u.as_str())
        .ok_or_else(|| anyhow!("Server-URL fehlt"))?;
    let server_sha1 = server_dl.get("sha1").and_then(|s| s.as_str()).unwrap_or("");
    let server_jar = mc_dir.join("server.jar");
    if !server_jar.is_file() || (!server_sha1.is_empty() && sha1_file_hex(&server_jar).unwrap_or_default() != server_sha1) {
        emit_progress(app, "Minecraft 26.3", "Lade 26.3 Server-JAR...", 56);
        crate::utils::download_file_client(&client, server_url, &server_jar)?;
    }

    emit_progress(app, "Minecraft 26.3", "Lade benötigte Bibliotheken...", 62);
    download_libraries(&libs_dir, &vjson);

    if let (Some(assets_id), Some(asset_idx)) = (&vjson.assets, &vjson.asset_index) {
        let index_path = assets_dir.join("indexes").join(format!("{}.json", assets_id));
        if !index_path.is_file() || sha1_file_hex(&index_path).unwrap_or_default() != asset_idx.sha1 {
            crate::utils::download_file_client(&client, &asset_idx.url, &index_path)?;
        }
    }

    emit_progress(app, "Minecraft 26.3", "Lade Assets & Sounds für Horizon...", 68);
    download_assets_to_dir(&version_dir, &assets_dir, version)?;

    Ok(server_jar)
}

fn run_horizon_prepare(
    horizon_dir: &Path,
    jdk_home: &Path,
    args: &[&str],
) -> Result<()> {
    let bin = horizon_dir.join(horizon_exe_name("horizon-prepare"));
    if !bin.is_file() {
        return Err(anyhow!("horizon-prepare nicht gefunden unter {}", bin.display()));
    }
    let mut cmd = Command::new(&bin);
    cmd.args(args);
    cmd.current_dir(horizon_dir);
    crate::java::sanitize_java_env(&mut cmd);
    cmd.env("JAVA_HOME", jdk_home);
    let jdk_bin = jdk_home.join("bin");
    let old_path = std::env::var_os("PATH").unwrap_or_default();
    let sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    let new_path = format!(
        "{}{}{}",
        jdk_bin.to_string_lossy(),
        sep,
        old_path.to_string_lossy()
    );
    cmd.env("PATH", new_path);

    let output = cmd.output()?;
    if !output.status.success() {
        let stdout = String::from_utf8_lossy(&output.stdout);
        let stderr = String::from_utf8_lossy(&output.stderr);
        return Err(anyhow!(
            "horizon-prepare fehlgeschlagen (Exit {:?}):\n{}\n{}",
            output.status.code(),
            stderr.trim(),
            stdout.trim()
        ));
    }
    Ok(())
}

pub fn install_orbital_laser(
    app: Option<&tauri::AppHandle>,
    data_dir: &Path,
    instance_name: Option<String>,
    target_instance: Option<String>,
) -> Result<Instance> {
    install_horizon_setup(app, data_dir, instance_name, target_instance, true)
}

pub fn install_horizon_only(
    app: Option<&tauri::AppHandle>,
    data_dir: &Path,
    instance_name: Option<String>,
) -> Result<Instance> {
    install_horizon_setup(app, data_dir, instance_name, None, false)
}

fn copy_dir_if_missing(src: &Path, dest: &Path) {
    if !src.is_dir() || dest.exists() {
        return;
    }
    if fs::create_dir_all(dest).is_err() {
        return;
    }
    if let Ok(entries) = fs::read_dir(src) {
        for e in entries.flatten() {
            let p = e.path();
            let target = dest.join(e.file_name());
            if p.is_dir() {
                copy_dir_if_missing(&p, &target);
            } else if p.is_file() {
                let _ = fs::copy(&p, &target);
            }
        }
    }
}

fn install_horizon_setup(
    app: Option<&tauri::AppHandle>,
    data_dir: &Path,
    instance_name: Option<String>,
    target_instance: Option<String>,
    include_orbital_mod: bool,
) -> Result<Instance> {
    let inst_path = crate::utils::instances_file(data_dir);
    let instances = crate::utils::load_json::<Vec<Instance>>(&inst_path, vec![]);

    let updating_existing = target_instance
        .as_deref()
        .and_then(|tn| instances.iter().find(|i| i.name == tn).cloned());

    let name = if let Some(ref existing) = updating_existing {
        existing.name.clone()
    } else {
        let default_name = if include_orbital_mod { "Orbital Laser" } else { "Horizon 26.3" };
        let raw_base = instance_name
            .as_deref()
            .map(str::trim)
            .filter(|s| !s.is_empty())
            .unwrap_or(default_name);
        let base = crate::utils::sanitize_name(raw_base);
        let base = if base.is_empty() { default_name.to_string() } else { base };
        let mut candidate = base.clone();
        let mut n = 2;
        while instances.iter().any(|i| i.name == candidate) {
            candidate = format!("{} ({})", base, n);
            n += 1;
        }
        candidate
    };

    let inst_dir = crate::utils::instance_dir(data_dir, &name);
    let horizon_dir = inst_dir.join("horizon");
    let mc_dir = inst_dir.join("mc-26.3");
    let game_dir = inst_dir.join("game");
    fs::create_dir_all(&horizon_dir)?;
    fs::create_dir_all(&mc_dir)?;
    fs::create_dir_all(&game_dir)?;
    copy_dir_if_missing(&inst_dir.join("saves"), &game_dir.join("saves"));

    let client = http_client(180)?;

    emit_progress(app, "Horizon Engine", "Lade Horizon (Orbital Laser Build)...", 10);
    let mut windows_bundled = false;
    let (h_tag, h_bin_url, h_src_opt) = fetch_horizon_release(&client);

    if cfg!(target_os = "windows") && h_bin_url.ends_with(".tar.gz") {
        emit_progress(app, "Windows Test-Build", "Lade Orbital Laser Windows-Paket...", 18);
        let win_resp = client.get(ORBITAL_WIN_FALLBACK_URL).send()?;
        if !win_resp.status().is_success() {
            return Err(anyhow!("Windows-Build Download fehlgeschlagen: HTTP {}", win_resp.status()));
        }
        let win_bytes = win_resp.bytes()?;
        extract_windows_bundle_from_zip(&win_bytes, &horizon_dir, &game_dir)?;
        windows_bundled = true;
    } else {
        let h_resp = client.get(&h_bin_url).send()?;
        if !h_resp.status().is_success() {
            return Err(anyhow!("Horizon Build Download fehlgeschlagen: HTTP {}", h_resp.status()));
        }
        let h_bytes = h_resp.bytes()?;
        if h_bin_url.ends_with(".zip") {
            let mut archive = zip::ZipArchive::new(Cursor::new(h_bytes))?;
            archive.extract(&horizon_dir)?;
        } else {
            unpack_strip_top_tar_gz(&h_bytes, &horizon_dir)?;
        }

        if !horizon_dir.join("research-lock.json").is_file() || !horizon_dir.join("mods").join("horizon").is_dir() {
            emit_progress(app, "Horizon Quellen", "Lade research-lock & Parity-Exporters...", 22);
            let src_url = h_src_opt.unwrap_or_else(|| {
                format!("https://github.com/Stink-O/horizon/archive/refs/tags/{}.tar.gz", h_tag)
            });
            let src_resp = client.get(&src_url).send()?;
            if !src_resp.status().is_success() {
                return Err(anyhow!("Horizon Quellarchiv Download fehlgeschlagen: HTTP {}", src_resp.status()));
            }
            let src_bytes = src_resp.bytes()?;
            unpack_horizon_sources_from_tar_gz(&src_bytes, &horizon_dir)?;
        }

        #[cfg(unix)]
        {
            use std::os::unix::fs::PermissionsExt;
            for bin_name in ["horizon-client", "horizon-ctl", "horizon-prepare"] {
                let p = horizon_dir.join(bin_name);
                if p.is_file() {
                    if let Ok(mut perms) = fs::metadata(&p).map(|m| m.permissions()) {
                        perms.set_mode(0o755);
                        let _ = fs::set_permissions(&p, perms);
                    }
                }
            }
        }
    }

    if include_orbital_mod {
        if !windows_bundled {
            emit_progress(app, "Orbital Laser Mod", "Lade & installiere Orbital Laser Mod...", 28);
            let (ol_url, _) = fetch_orbital_release_asset(&client);
            let ol_resp = client.get(&ol_url).send()?;
            if !ol_resp.status().is_success() {
                return Err(anyhow!("Orbital Laser Download fehlgeschlagen: HTTP {}", ol_resp.status()));
            }
            let ol_bytes = ol_resp.bytes()?;
            extract_orbital_mod_from_zip(&ol_bytes, &game_dir)?;
        }
        set_horizon_kv_line(&game_dir.join("horizon-mods.txt"), "mod.orbital", "true")?;
    } else if !windows_bundled {
        let _ = fs::create_dir_all(game_dir.join("horizon-mods"));
    }
    let _ = ensure_horizon_companion_mod(&game_dir);

    emit_progress(app, "Java 25 JDK", "Prüfe / lade Java 25 JDK für Horizon...", 36);
    let jdk_home = ensure_jdk_25(data_dir)?;

    let lock_path = horizon_dir.join("research-lock.json");
    let server_jar = prepare_minecraft_263_files(app, &mc_dir, &lock_path)?;

    emit_progress(
        app,
        "Horizon Prepare",
        "Generiere & kompiliere Horizon-Ressourcen (kann etwas dauern)...",
        78,
    );
    let server_jar_str = server_jar.to_string_lossy().into_owned();
    let mc_dir_str = mc_dir.to_string_lossy().into_owned();
    let horizon_dir_str = horizon_dir.to_string_lossy().into_owned();

    run_horizon_prepare(
        &horizon_dir,
        &jdk_home,
        &[
            "--prepare",
            "--server-jar",
            &server_jar_str,
            "--minecraft-dir",
            &mc_dir_str,
            "--root",
            &horizon_dir_str,
        ],
    )?;

    emit_progress(app, "Sounds", "Importiere Minecraft-Sounds für Horizon...", 90);
    if let Err(e) = run_horizon_prepare(
        &horizon_dir,
        &jdk_home,
        &["sounds", "--minecraft-dir", &mc_dir_str, "--root", &horizon_dir_str],
    ) {
        warn!("Horizon sounds prepare Warnung (Spiel startet trotzdem): {}", e);
    }

    let manifest_check = horizon_dir
        .join(".local")
        .join("horizon-client")
        .join("26.3")
        .join("horizon-resources.json");
    if !manifest_check.is_file() {
        return Err(anyhow!(
            "Horizon-Ressourcen unvollständig ({} fehlt)",
            manifest_check.display()
        ));
    }

    let desc = if include_orbital_mod {
        "Orbital Laser · Horizon Rust-Engine (26.3)".to_string()
    } else {
        "Horizon Rust-Engine (26.3)".to_string()
    };
    let mut instances = crate::utils::load_json::<Vec<Instance>>(&inst_path, vec![]);
    let result_inst = if let Some(existing) = instances.iter_mut().find(|i| i.name == name) {
        existing.version = "26.3".to_string();
        existing.loader = "horizon".to_string();
        existing.loader_version = Some(h_tag);
        existing.description = desc;
        if include_orbital_mod && !existing.mods.iter().any(|m| m == "orbital") {
            existing.mods.push("orbital".to_string());
        }
        existing.clone()
    } else {
        let inst = Instance {
            id: uuid::Uuid::new_v4().to_string(),
            name: name.clone(),
            version: "26.3".to_string(),
            loader: "horizon".to_string(),
            loader_version: Some(h_tag),
            description: desc,
            mods: if include_orbital_mod { vec!["orbital".to_string()] } else { vec![] },
            vulkan_enabled: true,
            memory_min: crate::DEFAULT_MEMORY_MIN.to_string(),
            memory_max: crate::DEFAULT_MEMORY_MAX.to_string(),
            created_at: chrono::Utc::now().to_rfc3339(),
            last_played: None,
            java_args: None,
            server: None,
        };
        instances.push(inst.clone());
        inst
    };
    crate::utils::save_json(&inst_path, &instances)?;

    emit_progress(app, "Fertig!", &format!("Instanz '{}' bereit zum Starten!", name), 100);
    Ok(result_inst)
}

pub fn launch_horizon(state: &AppState, data_dir: &Path, inst: &Instance) -> Result<(String, u32)> {
    let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
    let horizon_dir = inst_dir.join("horizon");
    let game_dir = inst_dir.join("game");
    fs::create_dir_all(&game_dir)?;

    let client_bin = horizon_dir.join(horizon_exe_name("horizon-client"));
    let resources_dir = horizon_dir.join(".local").join("horizon-client").join(&inst.version);
    if !client_bin.is_file() || !resources_dir.join("horizon-resources.json").is_file() {
        return Err(anyhow!(
            "Horizon-Installation unvollständig. Bitte Orbital Laser erneut installieren."
        ));
    }

    if let Ok(mut logs) = state.logs.lock() {
        logs.clear();
    }

    let _ = ensure_horizon_companion_mod(&game_dir);
    let _ = crate::servers::sync_instance_horizon_servers(data_dir, &inst.name);

    let mut accounts = crate::utils::load_json::<Vec<Account>>(&crate::utils::accounts_file(data_dir), vec![]);
    let now = chrono::Utc::now().timestamp() as u64;
    let expired = accounts
        .first()
        .map(|a| a.expires_at.map(|e| now >= e).unwrap_or(true))
        .unwrap_or(true);
    if expired {
        if let Some(acc) = accounts.first() {
            if acc.refresh_token.is_some() && crate::auth::refresh_stored_account().is_ok() {
                accounts = crate::utils::load_json::<Vec<Account>>(&crate::utils::accounts_file(data_dir), vec![]);
            }
        }
    }

    let mut cmd = Command::new(&client_bin);
    cmd.current_dir(&game_dir);
    sanitize_java_env(&mut cmd);
    cmd.arg("--resources").arg(&resources_dir);

    let mut used_session = false;
    if let Some(acc) = accounts.first() {
        let valid_name = !acc.username.is_empty() && acc.username.len() <= 16;
        let uuid_hex_len = acc.uuid.chars().filter(|c| c.is_ascii_hexdigit()).count();
        let valid_uuid = uuid_hex_len == 32
            && acc.uuid.chars().all(|c| c.is_ascii_hexdigit() || c == '-');
        let valid_token = !acc.access_token.is_empty() && acc.access_token != "0";
        if valid_name && valid_uuid && valid_token {
            let session_path = inst_dir.join(".horizon-session.json");
            let mut payload = serde_json::json!({
                "name": acc.username,
                "uuid": acc.uuid,
                "access_token": acc.access_token,
                "user_type": "msa"
            });
            if let Some(ref xuid) = acc.xuid {
                if !xuid.is_empty() {
                    payload["xuid"] = serde_json::Value::String(xuid.clone());
                }
            }
            if let Ok(json_str) = serde_json::to_string(&payload) {
                if fs::write(&session_path, json_str).is_ok() {
                    #[cfg(unix)]
                    {
                        use std::os::unix::fs::PermissionsExt;
                        let _ = fs::set_permissions(&session_path, fs::Permissions::from_mode(0o600));
                    }
                    cmd.arg("--session-file").arg(&session_path);
                    used_session = true;
                }
            }
        }
    }
    if !used_session {
        if let Some(acc) = accounts.first() {
            if !acc.username.is_empty() {
                cmd.arg("--name").arg(&acc.username);
            }
        }
    }
    if let Some(ref srv) = inst.server {
        if !srv.trim().is_empty() {
            cmd.arg("--server").arg(srv.trim());
        }
    }

    cmd.stdout(Stdio::piped()).stderr(Stdio::piped());

    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        const CREATE_NO_WINDOW: u32 = 0x08000000;
        cmd.creation_flags(CREATE_NO_WINDOW);
    }

    let logs_dir = inst_dir.join("logs");
    let _ = fs::create_dir_all(&logs_dir);
    let log_path = logs_dir.join("latest.log");
    let _ = fs::write(&log_path, "");

    info!("Starte Horizon (Orbital Laser) in {}...", game_dir.display());
    let mut child = cmd.spawn()?;
    let pid = child.id();

    let inst_path = crate::utils::instances_file(data_dir);
    let mut instances = crate::utils::load_json::<Vec<Instance>>(&inst_path, vec![]);
    if let Some(item) = instances.iter_mut().find(|i| i.name == inst.name) {
        item.last_played = Some(chrono::Utc::now().to_rfc3339());
        let _ = crate::utils::save_json(&inst_path, &instances);
    }

    let last_activity = Arc::new(AtomicU64::new(0));

    if let Some(mut out) = child.stdout.take() {
        let logs_arc = Arc::clone(&state.logs);
        let log_path2 = log_path.clone();
        let last_activity2 = Arc::clone(&last_activity);
        thread::spawn(move || {
            let mut buf = [0u8; 4096];
            let mut file = fs::OpenOptions::new().create(true).append(true).open(&log_path2).ok();
            let mut carry = String::new();
            loop {
                match out.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        let chunk = String::from_utf8_lossy(&buf[..n]);
                        if let Some(f) = file.as_mut() {
                            use std::io::Write;
                            let _ = f.write_all(chunk.as_bytes());
                        }
                        carry.push_str(&chunk);
                        while let Some(idx) = carry.find('\n') {
                            let line = carry[..idx].trim_end().to_string();
                            carry.replace_range(..=idx, "");
                            if !line.is_empty() {
                                if let Ok(mut logs) = logs_arc.lock() {
                                    logs.push(line);
                                    if logs.len() > crate::MAX_LOG_LINES {
                                        logs.remove(0);
                                    }
                                }
                            }
                        }
                        last_activity2.store(
                            std::time::SystemTime::now()
                                .duration_since(std::time::UNIX_EPOCH)
                                .map(|d| d.as_millis() as u64)
                                .unwrap_or(0),
                            Ordering::Relaxed,
                        );
                    }
                    Err(_) => break,
                }
            }
        });
    }

    if let Some(mut err) = child.stderr.take() {
        let logs_arc = Arc::clone(&state.logs);
        let log_path2 = log_path.clone();
        thread::spawn(move || {
            let mut buf = [0u8; 4096];
            let mut file = fs::OpenOptions::new().create(true).append(true).open(&log_path2).ok();
            let mut carry = String::new();
            loop {
                match err.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        let chunk = String::from_utf8_lossy(&buf[..n]);
                        if let Some(f) = file.as_mut() {
                            use std::io::Write;
                            let _ = f.write_all(chunk.as_bytes());
                        }
                        carry.push_str(&chunk);
                        while let Some(idx) = carry.find('\n') {
                            let line = carry[..idx].trim_end().to_string();
                            carry.replace_range(..=idx, "");
                            if !line.is_empty() {
                                if let Ok(mut logs) = logs_arc.lock() {
                                    logs.push(line);
                                    if logs.len() > crate::MAX_LOG_LINES {
                                        logs.remove(0);
                                    }
                                }
                            }
                        }
                    }
                    Err(_) => break,
                }
            }
        });
    }

    let discord_tx = state.discord.tx.clone();
    thread::spawn(move || {
        let _ = child.wait();
        let _ = discord_tx.send(crate::discord::RpcMessage::Set {
            details: "Kollegen Client".to_string(),
            state: "Im Launcher".to_string(),
            large_text: "Kollegen Client".to_string(),
            server: None,
            players: None,
        });
    });

    Ok(("Gestartet".to_string(), pid))
}

pub fn list_installed_horizon_mods(data_dir: &Path, instance_name: &str) -> Vec<Value> {
    let mods_dir = crate::utils::instance_dir(data_dir, instance_name)
        .join("game")
        .join("horizon-mods");
    let mut out = Vec::new();
    if let Ok(entries) = fs::read_dir(&mods_dir) {
        for e in entries.flatten() {
            let p = e.path();
            if let Some(name) = e.file_name().to_str() {
                if name.starts_with('.') {
                    continue;
                }
                let is_hmod = p.is_file() && p.extension().and_then(|x| x.to_str()) == Some("hmod");
                if p.is_dir() || is_hmod {
                    out.push(serde_json::json!({
                        "name": name,
                        "rust_mod": true
                    }));
                }
            }
        }
    }
    out
}
