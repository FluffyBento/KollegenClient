// Instance management for the Kollegen Client launcher
// Handles version fetching, instance installation, and game launching

use crate::types::{Instance, MojangVersionManifest, Settings, VersionJson};
use crate::AppState;
use anyhow::{anyhow, Result};
use log::{info, warn};
use serde_json::Value;
use std::fs;
use std::path::Path;
use std::process::{Command, Stdio};
use std::io::Write;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

/// Resource pack (zipped at compile time) that overrides the Minecraft
/// title-screen logo (`assets/minecraft/textures/gui/title/minecraft.png`)
/// with `Logo.png`. This is how we replace the in-game "Minecraft Java
/// Edition" logo without modifying the game jars.
const TITLE_LOGO_PACK: &[u8] = include_bytes!("title_logo_pack.zip");
const TITLE_LOGO_PACK_ID: &str = "KollegenTitle";

/// Maps a Minecraft version string to the resource-pack `pack_format` number
/// Minecraft expects, so the override actually loads (e.g. 1.21.11 -> 75).
fn pack_format_for(version: &str) -> u32 {
    let mut it = version.split('.');
    let major: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(1);
    let minor: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    let patch: u32 = it.next().and_then(|s| s.parse().ok()).unwrap_or(0);
    if major == 1 {
        match minor {
            21 => {
                return match patch {
                    11 => 75,
                    9 | 10 => 69,
                    7 | 8 => 64,
                    6 => 63,
                    5 => 55,
                    4 => 46,
                    2 | 3 => 42,
                    _ => 34,
                }
            }
            20 => {
                return match patch {
                    6 | 5 => 32,
                    4 | 3 => 22,
                    2 => 18,
                    _ => 15,
                }
            }
            19 => return if patch >= 4 { 13 } else { 9 },
            18 => return if patch >= 2 { 8 } else { 7 },
            17 => return 7,
            16 => return 6,
            _ => {}
        }
    }
    75
}

/// Builds the title-logo resource pack zip in memory, rewriting `pack.mcmeta`
/// with the `pack_format` that matches the instance's Minecraft version. For
/// 1.21.9+ (format >= 65) Minecraft requires the `min_format`/`max_format`
/// schema instead of a single `pack_format` number.
fn build_title_logo_pack(version: &str) -> Vec<u8> {
    let fmt = pack_format_for(version);
    let meta = if fmt >= 65 {
        serde_json::json!({
            "pack": {
                "description": "Kollegen Client Titel-Logo",
                "min_format": [fmt, 0],
                "max_format": [fmt + 24, 0]
            }
        })
    } else {
        serde_json::json!({
            "pack": {
                "description": "Kollegen Client Titel-Logo",
                "pack_format": fmt
            }
        })
    };
    let meta_str = serde_json::to_string_pretty(&meta).unwrap_or_default();

    let reader = std::io::Cursor::new(TITLE_LOGO_PACK);
    let mut archive = match zip::ZipArchive::new(reader) {
        Ok(a) => a,
        Err(_) => return TITLE_LOGO_PACK.to_vec(),
    };
    let mut out = Vec::new();
    {
        let mut writer = zip::ZipWriter::new(std::io::Cursor::new(&mut out));
        let opts = zip::write::FileOptions::default()
            .compression_method(zip::CompressionMethod::Deflated);
        for i in 0..archive.len() {
            let mut file = match archive.by_index(i) {
                Ok(f) => f,
                Err(_) => continue,
            };
            let name = file.name().to_string();
            if name == "pack.mcmeta" {
                let _ = writer.start_file("pack.mcmeta", opts);
                let _ = writer.write_all(meta_str.as_bytes());
            } else {
                let _ = writer.start_file(&name, opts);
                let _ = std::io::copy(&mut file, &mut writer);
            }
        }
        let _ = writer.finish();
    }
    out
}

/// Installs the title-logo resource pack into the instance and enables it in
/// `options.txt` (force-enabled via `incompatibleResourcePacks` so it works
/// across Minecraft versions regardless of `pack_format`).
fn ensure_title_logo_pack(inst_dir: &Path, version: &str) {
    let rp_dir = inst_dir.join("resourcepacks");
    if let Err(e) = fs::create_dir_all(&rp_dir) {
        warn!("Konnte resourcepacks nicht anlegen: {}", e);
        return;
    }
    let pack_zip = rp_dir.join(format!("{}.zip", TITLE_LOGO_PACK_ID));
    let bytes = build_title_logo_pack(version);
    if let Err(e) = fs::write(&pack_zip, &bytes) {
        warn!("Konnte Titel-Logo-Pack nicht installieren: {}", e);
        return;
    }

    let opts = inst_dir.join("options.txt");
    let mut lines: Vec<String> = if opts.exists() {
        fs::read_to_string(&opts)
            .unwrap_or_default()
            .lines()
            .map(|l| l.to_string())
            .collect()
    } else {
        Vec::new()
    };
    ensure_option_list(&mut lines, "resourcePacks", "file/KollegenTitle.zip");
    ensure_option_list(
        &mut lines,
        "incompatibleResourcePacks",
        "file/KollegenTitle.zip",
    );
    if let Err(e) = fs::write(&opts, lines.join("\n") + "\n") {
        warn!("Konnte options.txt nicht aktualisieren: {}", e);
    }
}

/// Ensures `value` is present in the comma-separated list stored in the
/// `key:[...]` line of Minecraft's `options.txt` (creating the line if absent).
/// Inserted at the front so our override has the highest priority.
fn ensure_option_list(lines: &mut Vec<String>, key: &str, value: &str) {
    let prefix = format!("{}:[", key);
    for line in lines.iter_mut() {
        if line.starts_with(&prefix) {
            if line.trim_end() == format!("{}:[]", key) {
                *line = format!("{}:[\"{}\"]", key, value);
            } else if !line.contains(value) {
                if let Some(b) = line.find('[') {
                    let mut result = line[..=b].to_string();
                    result.push_str(&format!("\"{}\", ", value));
                    result.push_str(&line[b + 1..]);
                    *line = result;
                }
            }
            return;
        }
    }
    lines.push(format!("{}:[\"{}\"]", key, value));
}

// ─=== Version Fetching ===

/// Fetches the list of available Minecraft versions from Mojang.
pub fn fetch_available_versions() -> Result<Vec<String>> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;

    let resp = client
        .get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json")
        .send()?;

    if !resp.status().is_success() {
        return Err(anyhow!("Failed to fetch version manifest"));
    }

    let manifest: MojangVersionManifest = resp.json()?;
    let versions: Vec<String> = manifest.versions.into_iter().map(|v| v.id).collect();

    Ok(versions)
}

/// Fetches available modloaders for a specific Minecraft version.
/// Returns JSON with Fabric, Forge, and NeoForge loader versions.
pub fn fetch_loaders_for_version(version: &str) -> Result<Value> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;

    let mut result = serde_json::json!({
        "fabric": [],
        "forge": [],
        "neoforge": []
    });

    // Fabric loaders from FabricMC metadata
    let fabric_resp = client
        .get(&format!("https://meta.fabricmc.net/v2/versions/loader/{}", version))
        .send();

    if let Ok(resp) = fabric_resp {
        if resp.status().is_success() {
            if let Ok(loaders) = resp.json::<Vec<Value>>() {
                result["fabric"] = serde_json::Value::Array(loaders);
            }
        }
    }

    

    Ok(result)
}

// ─=== Instance Installation ===

/// Installs a Minecraft instance by downloading the version jar and libraries.
pub fn install_instance(
    data_dir: &Path,
    name: &str,
    version: &str,
    loader: &str,
    _loader_version: Option<&str>,
) -> Result<()> {
    let inst_dir = crate::utils::instance_dir(data_dir, name);
    let version_dir = inst_dir.join("versions").join(version);
    let libs_dir = inst_dir.join("libraries");
    let assets_dir = inst_dir.join("assets");

    fs::create_dir_all(&version_dir)?;
    fs::create_dir_all(&libs_dir)?;
    fs::create_dir_all(&assets_dir)?;

    info!("Installing Minecraft {} for instance '{}'...", version, name);

    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;

    let manifest_resp = client.get("https://launchermeta.mojang.com/mc/game/version_manifest_v2.json").send()?;
    if !manifest_resp.status().is_success() {
        return Err(anyhow!("Konnte Version Manifest nicht laden"));
    }
    let manifest: MojangVersionManifest = manifest_resp.json()?;
    
    let version_entry = manifest.versions.iter().find(|v| v.id == version)
        .ok_or_else(|| anyhow!("Version {} nicht im Manifest gefunden", version))?;

    let resp = client.get(&version_entry.url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!("Version JSON für {} nicht gefunden (Status: {})", version, resp.status()));
    }

    let body_text = resp.text()?;
    let version_json: VersionJson = serde_json::from_str(&body_text)
        .map_err(|e| anyhow!("JSON Parse Fehler für Version {}: {} (Snippet: {})", version, e, &body_text[..body_text.len().min(100)]))?;

    // Save version JSON
    let version_json_path = version_dir.join(format!("{}.json", version));
    crate::utils::save_json(&version_json_path, &serde_json::to_value(&version_json)?)?;

    // Download client jar
    let downloads = version_json.downloads
        .ok_or_else(|| anyhow!("Keine Download-Informationen in Version JSON für {} gefunden", version))?;
    let client_download = downloads.client
        .ok_or_else(|| anyhow!("Kein Client-Jar-Download für {} gefunden", version))?;

    let jar_path = version_dir.join(format!("{}.jar", version));
    if !jar_path.exists() {
        crate::utils::download_file(&client_download.url, &jar_path)?;
    }

    // Download libraries (respect OS rules)
    for lib in &version_json.libraries {
        if let Some(lib_downloads) = &lib.downloads {
            if let Some(artifact) = &lib_downloads.artifact {
                // Check OS rules
                if let Some(rules) = &lib.rules {
                    let allowed = rules.iter().all(|rule| {
                        match rule.action.as_str() {
                            "allow" => {
                                if let Some(os) = &rule.os {
                                    match os.name.as_str() {
                                        "windows" => cfg!(target_os = "windows"),
                                        "osx" => cfg!(target_os = "macos"),
                                        "linux" => cfg!(target_os = "linux"),
                                        _ => true,
                                    }
                                } else {
                                    true
                                }
                            }
                            "disallow" => {
                                if let Some(os) = &rule.os {
                                    match os.name.as_str() {
                                        "windows" => !cfg!(target_os = "windows"),
                                        "osx" => !cfg!(target_os = "macos"),
                                        "linux" => !cfg!(target_os = "linux"),
                                        _ => true,
                                    }
                                } else {
                                    true
                                }
                            }
                            _ => true,
                        }
                    });
                    if !allowed {
                        continue;
                    }
                }

                let lib_path = libs_dir.join(&artifact.path);
                if !lib_path.exists() {
                    let _ = crate::utils::download_file(&artifact.url, &lib_path);
                }
            }
        }
    }

    // Download the full asset set (index + all objects)
    download_assets(data_dir, name, version)?;

    // Download essential mod if not vanilla
    if loader != "vanilla" {
        crate::utils::ensure_essential(name, data_dir)?;
    }

    Ok(())
}

// ─=== Asset Download ===

/// Downloads a single Minecraft asset object, verifying its SHA-1 (and size)
/// against the asset index before writing it. A corrupt/truncated download is
/// removed so the caller can retry it.
fn download_asset_object(
    client: &reqwest::blocking::Client,
    url: &str,
    dest: &Path,
    expected_hash: &str,
    expected_size: u64,
) -> Result<()> {
    let resp = client.get(url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow::anyhow!("HTTP {}", resp.status()));
    }
    let bytes = resp.bytes()?;
    if expected_size != 0 && bytes.len() as u64 != expected_size {
        return Err(anyhow::anyhow!("Größe stimmt nicht überein"));
    }
    if crate::utils::sha1_hex(&bytes) != expected_hash {
        return Err(anyhow::anyhow!("SHA1 stimmt nicht überein"));
    }
    if let Some(parent) = dest.parent() {
        fs::create_dir_all(parent)?;
    }
    let tmp = dest.with_extension("part");
    let _ = fs::remove_file(&tmp);
    fs::write(&tmp, &bytes)?;
    fs::rename(&tmp, dest)?;
    Ok(())
}

/// Downloads the full asset set (index + all objects) for an instance.
/// Idempotent: already-present object files are skipped, so it is safe to
/// call on every launch to backfill any missing assets.
pub fn download_assets(data_dir: &Path, name: &str, version: &str) -> Result<()> {
    let inst_dir = crate::utils::instance_dir(data_dir, name);
    let version_dir = inst_dir.join("versions").join(version);
    let assets_dir = inst_dir.join("assets");
    fs::create_dir_all(&assets_dir)?;

    let vjson_path = version_dir.join(format!("{}.json", version));
    let vjson_str = fs::read_to_string(&vjson_path)?;
    let vjson: VersionJson = serde_json::from_str(&vjson_str)?;
    let assets_id = match &vjson.assets {
        Some(a) => a.clone(),
        None => return Ok(()),
    };

    // Download the asset index if missing
    let index_path = assets_dir.join(format!("indexes/{}.json", assets_id));
    if !index_path.exists() {
        match &vjson.asset_index {
            Some(asset_index) => {
                crate::utils::download_file(&asset_index.url, &index_path)?;
            }
            None => return Ok(()),
        }
    }

    let index_str = fs::read_to_string(&index_path)?;
    let index: Value = serde_json::from_str(&index_str)?;
    let objects = match index.get("objects").and_then(|o| o.as_object()) {
        Some(o) => o,
        None => return Ok(()),
    };

    // Build the job list. An object is (re)downloaded when it is missing OR
    // when the already-present file fails the SHA-1/size check from the index
    // (a previously truncated/corrupt download would otherwise be skipped
    // forever and crash Minecraft with a "PNG header missing" on load).
    let mut jobs: Vec<(String, std::path::PathBuf, String, u64)> = Vec::new();
    for (_key, obj) in objects {
        let hash = match obj.get("hash").and_then(|h| h.as_str()) {
            Some(h) => h,
            None => continue,
        };
        let size = obj.get("size").and_then(|s| s.as_u64());
        let prefix = &hash[0..2];
        let obj_path = assets_dir.join("objects").join(prefix).join(hash);
        let needs = if obj_path.exists() {
            match (size, fs::metadata(&obj_path)) {
                // Cheap stat-based check (no content read) when the index
                // advertises a size; only re-downloads on a mismatch.
                (Some(expected), Ok(m)) => m.len() != expected,
                // No size in the index: fall back to a content hash check.
                _ => match fs::read(&obj_path) {
                    Ok(bytes) => crate::utils::sha1_hex(&bytes) != hash,
                    Err(_) => true,
                },
            }
        } else {
            true
        };
        if needs {
            let url = format!(
                "https://resources.download.minecraft.net/{}/{}",
                prefix, hash
            );
            jobs.push((url, obj_path, hash.to_string(), size.unwrap_or(0)));
        }
    }

    let total = jobs.len();
    if total == 0 {
        info!("Alle Assets für {} bereits vorhanden.", version);
        return Ok(());
    }
    info!("Lade {} Asset-Objekte herunter...", total);

    let jobs = Arc::new(Mutex::new(jobs));
    let workers = std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(4)
        .min(16)
        .max(1);

    let mut handles = Vec::new();
    for _ in 0..workers {
        let jobs = Arc::clone(&jobs);
        let handle = thread::spawn(move || {
            let client = reqwest::blocking::Client::builder()
                .user_agent(crate::USER_AGENT)
                .timeout(Duration::from_secs(120))
                .build()
                .unwrap();
            loop {
                let next = {
                    let mut g = jobs.lock().unwrap();
                    g.pop()
                };
                let (url, path, expected, size) = match next {
                    Some(j) => j,
                    None => break,
                };
                let mut ok = false;
                for attempt in 0..3 {
                    match download_asset_object(&client, &url, &path, &expected, size) {
                        Ok(()) => {
                            ok = true;
                            break;
                        }
                        Err(e) => {
                            if attempt == 2 {
                                log::warn!(
                                    "Asset-Download endgültig fehlgeschlagen ({}): {}",
                                    url,
                                    e
                                );
                            }
                        }
                    }
                }
                if !ok {
                    // Never leave a corrupt partial file behind.
                    let _ = fs::remove_file(path.with_extension("part"));
                }
            }
        });
        handles.push(handle);
    }
    for h in handles {
        let _ = h.join();
    }
    info!("Asset-Download abgeschlossen.");
    Ok(())
}

// ─=== Mod Loader Support (Fabric) ===

/// Resolves the latest Fabric loader version for a Minecraft version.
fn resolve_fabric_loader_version(mc_version: &str) -> Result<String> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;
    let resp = client
        .get(&format!(
            "https://meta.fabricmc.net/v2/versions/loader/{}",
            mc_version
        ))
        .send()?;
    if !resp.status().is_success() {
        return Err(anyhow!("Konnte Fabric Loader Version nicht laden"));
    }
    let arr: Vec<Value> = resp.json()?;
    let ver = arr
        .first()
        .and_then(|v| v.get("loader"))
        .and_then(|l| l.get("version"))
        .and_then(|s| s.as_str())
        .ok_or_else(|| anyhow!("Keine Fabric Loader Version gefunden"))?;
    Ok(ver.to_string())
}

/// Resolves the latest Fabric installer version from Maven metadata.
fn resolve_fabric_installer_version() -> Result<String> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;
    let resp = client
        .get("https://maven.fabricmc.net/net/fabricmc/fabric-installer/maven-metadata.xml")
        .send()?;
    let body = resp.text()?;
    let mut last = String::new();
    for line in body.lines() {
        let t = line.trim();
        if let Some(rest) = t.strip_prefix("<version>") {
            if let Some(v) = rest.strip_suffix("</version>") {
                last = v.to_string();
            }
        }
    }
    if last.is_empty() {
        return Err(anyhow!("Konnte Fabric Installer Version nicht laden"));
    }
    Ok(last)
}

/// Scans the instance's versions directory for an already-installed
/// Fabric profile and returns its version id (e.g.
/// "fabric-loader-0.19.3-1.21.11").
fn find_fabric_version_dir(versions_dir: &Path) -> Option<String> {
    let entries = fs::read_dir(versions_dir).ok()?;
    for e in entries.flatten() {
        let p = e.path();
        if !p.is_dir() {
            continue;
        }
        let name = p.file_name()?.to_string_lossy().to_string();
        if name.to_lowercase().contains("fabric")
            && p.join(format!("{}.json", name)).is_file()
        {
            return Some(name);
        }
    }
    None
}

/// Ensures the Fabric loader is installed for the instance, running the
/// official installer headlessly if needed. Returns the actual generated
/// version id (e.g. "fabric-loader-0.19.3-1.21.11").
fn ensure_fabric(data_dir: &Path, inst: &Instance, java_path: &str) -> Result<String> {
    let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
    let versions_dir = inst_dir.join("versions");
    fs::create_dir_all(&versions_dir).ok();

    // Reuse an already-installed Fabric profile if present (no network).
    if let Some(id) = find_fabric_version_dir(&versions_dir) {
        return Ok(id);
    }

    let loader_ver = match &inst.loader_version {
        Some(v) => v.clone(),
        None => resolve_fabric_loader_version(&inst.version)?,
    };
    info!("Installiere Fabric Loader {} für '{}'...", loader_ver, inst.name);

    let installer_ver = resolve_fabric_installer_version()?;
    let installer_url = format!(
        "https://maven.fabricmc.net/net/fabricmc/fabric-installer/{}/fabric-installer-{}.jar",
        installer_ver, installer_ver
    );
    fs::create_dir_all(&inst_dir)?;
    let installer_path = inst_dir.join("fabric-installer.jar");
    if !installer_path.exists() {
        crate::utils::download_file(&installer_url, &installer_path)?;
    }

    let mut fab_cmd = Command::new(java_path);
    // Hide the briefly flashing console window on Windows.
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        fab_cmd.creation_flags(0x08000000);
    }
    let status = fab_cmd
        .arg("-jar")
        .arg(&installer_path)
        .arg("client")
        .arg("-mcversion")
        .arg(&inst.version)
        .arg("-loader")
        .arg(&loader_ver)
        .arg("-dir")
        .arg(&inst_dir)
        .arg("-noprofile")
        .status()?;
    if !status.success() {
        return Err(anyhow!(
            "Fabric Installer fehlgeschlagen (Exit {})",
            status.code().unwrap_or(-1)
        ));
    }

    // Locate the generated Fabric version directory (the installer chooses
    // the exact id, so we resolve it from disk rather than guessing).
    find_fabric_version_dir(&versions_dir)
        .ok_or_else(|| anyhow!("Fabric Version-Verzeichnis nicht gefunden"))
}

/// Removes zero-byte / corrupt mod jars from the mods directory so a broken
/// file can't prevent Fabric (or Forge) from loading the rest of the mods.
fn clean_corrupt_mods(mods_dir: &Path) {
    if let Ok(entries) = fs::read_dir(mods_dir) {
        for e in entries.flatten() {
            let p = e.path();
            if p.extension().and_then(|x| x.to_str()) != Some("jar") {
                continue;
            }
            let meta = match fs::metadata(&p) {
                Ok(m) => m,
                Err(_) => continue,
            };
            // Empty file, or not a real zip archive (valid jars start with "PK").
            let corrupt = meta.len() == 0
                || fs::read(&p)
                    .map(|b| b.len() < 4 || !b.starts_with(b"PK"))
                    .unwrap_or(false);
            if corrupt {
                log::warn!("Entferne beschädigte Mod-Datei: {}", p.display());
                let _ = fs::remove_file(&p);
            }
        }
    }
}

// ─=== Game Launch ===

/// Builds the classpath and launches Minecraft for the given instance.
pub fn launch(
    state: &AppState,
    data_dir: &Path,
    inst: &Instance,
    java_path: &str,
    _settings: &Settings,
) -> Result<String> {
    let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
    // Fresh launcher log per launch (avoids stale crash lines triggering the
    // auto-resolver again on the next manual launch).
    if let Ok(mut logs) = state.logs.lock() {
        logs.clear();
    }
    let version_dir = inst_dir.join("versions").join(&inst.version);
    let libs_dir = inst_dir.join("libraries");
    let assets_dir = inst_dir.join("assets");

    info!("Launching Minecraft {} ({}) for instance '{}'...", inst.version, inst.loader, inst.name);

    // Verify version jar exists; if missing, install the instance automatically
    let version_jar = version_dir.join(format!("{}.jar", inst.version));
    if !version_jar.exists() {
        info!(
            "Version jar fehlt, installiere Instanz '{}' automatisch vor dem Start...",
            inst.name
        );
        install_instance(
            data_dir,
            &inst.name,
            &inst.version,
            &inst.loader,
            inst.loader_version.as_deref(),
        )?;
    }

    // Ensure assets are present (idempotent: only missing objects are downloaded)
    info!("Prüfe Minecraft-Assets...");
    let _ = download_assets(data_dir, &inst.name, &inst.version);

    // Read version JSON to get main class
    let vjson_path = version_dir.join(format!("{}.json", inst.version));
    let vjson_str = fs::read_to_string(&vjson_path)?;
    let vjson: VersionJson = serde_json::from_str(&vjson_str)?;

    // For Fabric, install the loader (if needed) and use its generated
    // profile to obtain the KnotClient main class. The classpath is built
    // from all jars under libraries/ (incl. Fabric loader + deps) below.
    let (main_class, asset_index) = if inst.loader.eq_ignore_ascii_case("fabric") {
        let fabric_id = ensure_fabric(data_dir, inst, java_path)?;
        let fabric_json_path = crate::utils::instance_dir(data_dir, &inst.name)
            .join("versions")
            .join(&fabric_id)
            .join(format!("{}.json", &fabric_id));
        let fabric_str = fs::read_to_string(&fabric_json_path)?;
        let fabric_v: VersionJson = serde_json::from_str(&fabric_str)?;
        let mc = fabric_v.main_class.clone().unwrap_or_else(|| {
            "net.fabricmc.loader.impl.launch.knot.KnotClient".to_string()
        });
        (mc, vjson.assets.clone().unwrap_or_else(|| inst.version.clone()))
    } else {
        (
            vjson
                .main_class
                .unwrap_or_else(|| "net.minecraft.client.main.Main".to_string()),
            vjson.assets.clone().unwrap_or_else(|| inst.version.clone()),
        )
    };
    info!("Launching with main class: {}", main_class);

    // Build classpath: version jar + all libraries
    let mut classpath = vec![version_jar.to_string_lossy().into_owned()];

    // Add all library jars
    if libs_dir.exists() {
        for entry in collect_jars(&libs_dir) {
            if !classpath.contains(&entry) {
                classpath.push(entry);
            }
        }
    }

    // Add mods
    let mods_dir = inst_dir.join("mods");
    clean_corrupt_mods(&mods_dir);
    if mods_dir.exists() {
        for entry in fs::read_dir(&mods_dir)? {
            let entry = entry?;
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) == Some("jar") {
                classpath.push(path.to_string_lossy().into_owned());
            }
        }
    }

    // Build JVM arguments
    let mut jvm_args: Vec<String> = vec![];

    // Java library path for natives
    let natives_dir = version_dir.join("natives");
    if natives_dir.exists() {
        jvm_args.push(format!("-Djava.library.path={}", natives_dir.to_string_lossy()));
    }

    // Memory settings
    jvm_args.push(format!("-Xms{}", inst.memory_min));
    jvm_args.push(format!("-Xmx{}", inst.memory_max));

    // Build classpath argument
    let os_cp_sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    jvm_args.push("-cp".to_string());
    jvm_args.push(classpath.join(os_cp_sep));

    // Get account info (load from disk to always have the latest)
    let mut accounts = crate::utils::load_json::<Vec<crate::types::Account>>(
        &crate::utils::accounts_file(&state.data_dir),
        vec![],
    );

    // Refresh the token if it has expired, then reload the account data
    let now = chrono::Utc::now().timestamp() as u64;
    let expired = accounts
        .first()
        .map(|a| a.expires_at.map(|e| now >= e).unwrap_or(true))
        .unwrap_or(true);
    if expired {
        if let Some(acc) = accounts.first() {
            if acc.refresh_token.is_some() && crate::auth::refresh_stored_account().is_ok() {
                accounts = crate::utils::load_json::<Vec<crate::types::Account>>(
                    &crate::utils::accounts_file(&state.data_dir),
                    vec![],
                );
            }
        }
    }

    let first = accounts.first();
    let username = first.map(|a| a.username.clone()).unwrap_or_else(|| "Spieler".to_string());
    let uuid = first.map(|a| a.uuid.clone()).unwrap_or_else(|| "0".to_string());
    let access_token = first.map(|a| a.access_token.clone()).unwrap_or_default();

    // Build the command
    let mut cmd = std::process::Command::new(java_path);
    // On Windows a GUI parent spawns a visible console window for java.exe; hide
    // it (logs are still captured via the piped stdout/stderr below).
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000);
    }
    cmd.args(&jvm_args);

    // Add main class
    cmd.arg(&main_class);

    // Add Minecraft-specific arguments
    cmd.args(&[
        "--username", &username,
        "--version", &inst.version,
        "--gameDir", &inst_dir.to_string_lossy(),
        "--assetsDir", &assets_dir.to_string_lossy(),
        "--assetIndex", &asset_index,
        "--uuid", &uuid,
        "--accessToken", &access_token,
        "--clientId", "0",
        "--xuid", "",
        "--userType", "msa",
        "--versionType", "release",
    ]);

    // Server connection
    if let Some(server) = &inst.server {
        let parts: Vec<&str> = server.split(':').collect();
        if parts.len() >= 2 {
            cmd.args(&["--server", parts[0], "--port", parts[1]]);
        }
    }

    // Custom Java args
    if let Some(jargs) = &inst.java_args {
        for arg in jargs.split_whitespace() {
            cmd.arg(arg);
        }
    }

    // Working directory
    cmd.current_dir(&inst_dir);

    // Capture the game's output: stderr -> file, stdout -> piped so we can also
    // stream it into the in-memory launcher log. That log is polled by the UI
    // and used to auto-detect crashes (e.g. Fabric mod-incompatibility errors)
    // and resolve them.
    let log_path = inst_dir.join("logs").join("latest.log");
    if let Some(parent) = log_path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    match fs::File::create(&log_path) {
        Ok(f) => {
            let stderr = match f.try_clone() {
                Ok(c) => Stdio::from(c),
                Err(_) => Stdio::null(),
            };
            cmd.stderr(stderr);
        }
        Err(_) => {
            cmd.stderr(Stdio::null());
        }
    }
    cmd.stdout(Stdio::piped());

    // Install + enable the resource pack that replaces the in-game
    // "Minecraft Java Edition" title logo with Logo.png.
    ensure_title_logo_pack(&inst_dir, &inst.version);

    info!("Starting Minecraft process...");
    let mut child = cmd.spawn()?;
    let pid = child.id();

    // Stream stdout (game log) into the in-memory launcher log and the file.
    // The same stream is also scanned for server connect/disconnect so the
    // launcher can show – and advertise via rich presence – the server you
    // actually joined, with no manual configuration.
    let log_discord_tx = state.discord.tx.clone();
    let log_version = inst.version.clone();
    let log_loader = inst.loader.clone();
    let log_name = inst.name.clone();
    if let Some(mut out) = child.stdout.take() {
        let logs_arc = Arc::clone(&state.logs);
        let log_path2 = log_path.clone();
        thread::spawn(move || {
            use std::io::Read;
            let mut buf = [0u8; 4096];
            let mut file = fs::OpenOptions::new()
                .create(true)
                .append(true)
                .open(&log_path2)
                .ok();
            let mut carry = String::new();
            loop {
                match out.read(&mut buf) {
                    Ok(0) => break,
                    Ok(n) => {
                        let chunk = String::from_utf8_lossy(&buf[..n]);
                        if let Some(f) = file.as_mut() {
                            let _ = f.write_all(chunk.as_bytes());
                        }
                        carry.push_str(&chunk);
                        while let Some(idx) = carry.find('\n') {
                            let line = carry[..idx].trim_end().to_string();
                            carry.replace_range(..=idx, "");
                            if !line.is_empty() {
                                // Detect joining / leaving a server and keep
                                // the Discord panel + rich presence in sync.
                                if let Some(srv) = parse_server_from_log(&line) {
                                    crate::discord::set_current_server(Some(srv.clone()));
                                    let _ = log_discord_tx.send(crate::discord::RpcMessage::Set {
                                        details: format!("Spielt auf {}", srv),
                                        state: format!("{} · {}", log_version, log_loader),
                                        large_text: log_name.clone(),
                                        server: Some(srv),
                                        players: None,
                                    });
                                } else if is_disconnect_log(&line) {
                                    crate::discord::set_current_server(None);
                                    let _ = log_discord_tx.send(crate::discord::RpcMessage::Set {
                                        details: "Spielt Minecraft".to_string(),
                                        state: format!("{} · {}", log_version, log_loader),
                                        large_text: log_name.clone(),
                                        server: None,
                                        players: None,
                                    });
                                }
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
            if !carry.trim().is_empty() {
                if let Ok(mut logs) = logs_arc.lock() {
                    logs.push(carry.trim().to_string());
                    if logs.len() > crate::MAX_LOG_LINES {
                        logs.remove(0);
                    }
                }
            }
        });
    }

    // When the game exits, reset the rich presence to "Im Launcher" and clear
    // the current-server display – otherwise it stays stuck showing
    // "Spielt Minecraft" even after the game is closed.
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
        crate::discord::set_current_server(None);
    });

    // Update last_played
    let path = crate::utils::instances_file(&state.data_dir);
    let mut instances = crate::utils::load_json::<Vec<Instance>>(&path, vec![]);
    for i in &mut instances {
        if i.name == inst.name {
            i.last_played = Some(chrono::Utc::now().to_rfc3339());
        }
    }
    let _ = crate::utils::save_json(&path, &instances);

    Ok(format!("Minecraft started (PID: {})", pid))
}

/// Extracts the server address from a Minecraft client log line such as
/// `[Render thread/INFO]: Connecting to play.example.com, 25565`.
fn parse_server_from_log(line: &str) -> Option<String> {
    let marker = "Connecting to ";
    let idx = line.find(marker)?;
    let rest = &line[idx + marker.len()..];
    let host_part = rest.split(',').next().unwrap_or("").trim();
    // Some versions log "host/resolved-ip" – keep only the host part.
    let host = host_part.split('/').next().unwrap_or(host_part).trim();
    if host.is_empty() {
        return None;
    }
    let port: String = rest
        .split(',')
        .nth(1)
        .map(|p| p.trim().chars().take_while(|c| c.is_ascii_digit()).collect())
        .unwrap_or_default();
    let server = if port.is_empty() || port == "25565" {
        host.to_string()
    } else {
        format!("{}:{}", host, port)
    };
    if server.is_empty() {
        None
    } else {
        Some(server)
    }
}

/// Returns true if the log line indicates the client left the server.
fn is_disconnect_log(line: &str) -> bool {
    line.contains("Disconnected from server")
        || line.contains("Lost connection")
        || line.contains("Client disconnected")
}

/// Recursively collects all .jar file paths from a directory.
fn collect_jars(dir: &Path) -> Vec<String> {
    let mut result = vec![];
    if let Ok(entries) = fs::read_dir(dir) {
        for entry in entries.flatten() {
            let path = entry.path();
            if path.is_dir() {
                result.extend(collect_jars(&path));
            } else if path.extension().and_then(|e| e.to_str()) == Some("jar") {
                result.push(path.to_string_lossy().into_owned());
            }
        }
    }
    result
}




