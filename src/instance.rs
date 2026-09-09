


use crate::types::{Instance, MojangVersionManifest, Settings, VersionJson};
use crate::AppState;
use anyhow::{anyhow, Result};
use log::{info, warn};
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::{Command, Stdio};
use std::io::Write;
use std::io::Read;
use std::sync::{Arc, Mutex};
use std::sync::atomic::{AtomicU64, Ordering};
use std::thread;
use std::time::Duration;





const TITLE_LOGO_PACK: &[u8] = include_bytes!("title_logo_pack.zip");
const TITLE_LOGO_PACK_ID: &str = "KollegenTitle";



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





fn fit_logo_square(png: &[u8]) -> Vec<u8> {
    const SIZE: u32 = 256;
    const FILL: f32 = 0.92;
    let img = match image::load_from_memory(png) {
        Ok(i) => i,
        Err(_) => return png.to_vec(),
    };
    let (w, h) = (img.width(), img.height());
    if w == 0 || h == 0 {
        return png.to_vec();
    }
    let scale = (SIZE as f32 * FILL) / (w.max(h) as f32);
    let nw = ((w as f32) * scale).max(1.0) as u32;
    let nh = ((h as f32) * scale).max(1.0) as u32;
    let resized = img.resize(nw, nh, image::imageops::FilterType::Lanczos3);
    let mut canvas = image::RgbaImage::from_pixel(SIZE, SIZE, image::Rgba([0, 0, 0, 0]));
    let x = ((SIZE as i64 - nw as i64) / 2) as i64;
    let y = ((SIZE as i64 - nh as i64) / 2) as i64;
    image::imageops::overlay(&mut canvas, &resized, x, y);
    let mut buf = Vec::new();
    {
        let img = image::DynamicImage::ImageRgba8(canvas);
        let mut cursor = std::io::Cursor::new(&mut buf);
        if !img
            .write_to(&mut cursor, image::ImageFormat::Png)
            .is_ok()
        {
            return png.to_vec();
        }
    }
    buf
}





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
            } else if name.ends_with("title/minecraft.png") {
                let mut data = Vec::new();
                let _ = std::io::copy(&mut file, &mut data);
                let processed = fit_logo_square(&data);
                let _ = writer.start_file(&name, opts);
                let _ = writer.write_all(&processed);
            } else {
                let _ = writer.start_file(&name, opts);
                let _ = std::io::copy(&mut file, &mut writer);
            }
        }
        let _ = writer.finish();
    }
    out
}





pub fn ensure_title_logo_pack(inst_dir: &Path, version: &str) {
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




pub fn ensure_kollegen_mod(data_dir: &Path, name: &str, loader: &str, version: &str) {
    crate::companion::install_companion_mod(data_dir, name, version, loader);
}




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



pub fn fetch_loaders_for_version(version: &str) -> Result<Value> {
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .build()?;

    let mut result = serde_json::json!({
        "fabric": [],
        "forge": [],
        "neoforge": []
    });

    
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

    
    let version_json_path = version_dir.join(format!("{}.json", version));
    crate::utils::save_json(&version_json_path, &serde_json::to_value(&version_json)?)?;

    
    let downloads = version_json.downloads
        .ok_or_else(|| anyhow!("Keine Download-Informationen in Version JSON für {} gefunden", version))?;
    let client_download = downloads.client
        .ok_or_else(|| anyhow!("Kein Client-Jar-Download für {} gefunden", version))?;

    let jar_path = version_dir.join(format!("{}.jar", version));
    if !jar_path.exists() {
        crate::utils::download_file(&client_download.url, &jar_path)?;
    }

    
    for lib in &version_json.libraries {
        if let Some(lib_downloads) = &lib.downloads {
            if let Some(artifact) = &lib_downloads.artifact {
                
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

    
    download_assets(data_dir, name, version)?;

    
    if loader != "vanilla" {
        crate::utils::ensure_essential(name, data_dir, version)?;
    }

    
    ensure_kollegen_mod(data_dir, name, loader, version);

    
    
    ensure_title_logo_pack(&inst_dir, version);

    Ok(())
}






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
                
                
                (Some(expected), Ok(m)) => m.len() != expected,
                
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




fn ensure_fabric(data_dir: &Path, inst: &Instance, java_path: &str) -> Result<String> {
    let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
    let versions_dir = inst_dir.join("versions");
    fs::create_dir_all(&versions_dir).ok();

    
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
    crate::java::sanitize_java_env(&mut fab_cmd);
    
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

    
    
    find_fabric_version_dir(&versions_dir)
        .ok_or_else(|| anyhow!("Fabric Version-Verzeichnis nicht gefunden"))
}



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






#[derive(Clone, Copy, PartialEq, Eq)]
enum RendererGroup {
    Opengl, 
    Vulkan, 
}











fn renderer_mod_group(fname: &str) -> Option<RendererGroup> {
    let f = fname.to_lowercase();
    let f = f.strip_suffix(".disabled").unwrap_or(&f);
    if f.starts_with("sodium")
        || f.starts_with("iris")
        || f.starts_with("axiom")
        || f.starts_with("xaeroworldmap")
        || f.starts_with("xaerominimap")
        || f.starts_with("xaerolib")
    {
        Some(RendererGroup::Opengl)
    } else if f.starts_with("vulkanmod") || f.starts_with("beryl") {
        Some(RendererGroup::Vulkan)
    } else {
        None
    }
}




fn renderer_state_path(mods_dir: &Path) -> PathBuf {
    mods_dir.join(".kollegen-renderer")
}






pub(crate) fn read_renderer_state(mods_dir: &Path) -> Option<bool> {
    let s = fs::read_to_string(renderer_state_path(mods_dir)).ok()?;
    match s.trim().to_lowercase().as_str() {
        "vulkan" => Some(true),
        "opengl" => Some(false),
        _ => None,
    }
}




fn controller_state_path(mods_dir: &Path) -> PathBuf {
    mods_dir.join(".kollegen-controller")
}



pub(crate) fn enforce_controller_state(mods_dir: &Path, on: bool) {
    if !mods_dir.exists() {
        let _ = std::fs::create_dir_all(mods_dir);
    }
    let _ = fs::write(controller_state_path(mods_dir), if on { "on" } else { "off" });
}












pub(crate) fn enforce_renderer_consistency(mods_dir: &Path, vulkan_enabled: bool) {
    if !mods_dir.exists() {
        return;
    }
    
    
    
    
    
    let essential_present = fs::read_dir(mods_dir)
        .map(|entries| {
            entries.flatten().any(|e| {
                let name = e.file_name().to_string_lossy().to_lowercase();
                name.starts_with("essential") && name.ends_with(".jar")
            })
        })
        .unwrap_or(false);
    let vulkan_enabled = if essential_present {
        if vulkan_enabled {
            info!("Essential-Mod erkannt – erzwinge OpenGL-Renderer (Vulkan/VulkanMod+Beryl ist mit Essential inkompatibel: kopfstehende UI/Crash).");
        }
        false
    } else {
        vulkan_enabled
    };
    
    let state = if vulkan_enabled { "vulkan" } else { "opengl" };
    let _ = fs::write(renderer_state_path(mods_dir), state);

    if let Ok(entries) = fs::read_dir(mods_dir) {
        for e in entries.flatten() {
            let p = e.path();
            let ext = p.to_string_lossy().to_lowercase();
            
            let is_jar = p.extension().and_then(|x| x.to_str()) == Some("jar")
                || ext.ends_with(".jar.disabled");
            if !is_jar {
                continue;
            }
            let fname = p.file_name().unwrap().to_string_lossy().to_lowercase();
            let group = match renderer_mod_group(&fname) {
                Some(g) => g,
                None => continue,
            };
            let active = if vulkan_enabled {
                group == RendererGroup::Vulkan
            } else {
                group == RendererGroup::Opengl
            };
            let is_disabled = fname.ends_with(".disabled");
            if active && is_disabled {
                
                let target = p.with_file_name(fname.trim_end_matches(".disabled").to_string());
                let _ = fs::rename(&p, &target);
            } else if !active && !is_disabled {
                
                let target = p.with_file_name(format!("{}.disabled", fname));
                let _ = fs::rename(&p, &target);
            }
        }
    }
}





const BUNDLED_MODS: &[(&str, &str, &str)] = &[
    ("spotify", "kollegen-bundle-spotify.jar", "dev/kollegen/client/spotify.bin"),
    ("chatheads", "kollegen-bundle-chatheads.jar", "dev/kollegen/client/chatheads.bin"),
    ("@deps", "kollegen-bundle-fabric-api.jar", "dev/kollegen/client/fabricapi.bin"),
    ("@deps", "kollegen-bundle-flk.jar", "dev/kollegen/client/flk.bin"),
    ("@deps", "kollegen-bundle-owo.jar", "dev/kollegen/client/owo.bin"),
    ("@deps", "kollegen-bundle-modmenu.jar", "dev/kollegen/client/modmenu.bin"),
    ("@deps", "kollegen-bundle-tpa.jar", "dev/kollegen/client/tpa.bin"),
    ("@deps", "kollegen-bundle-silk.jar", "dev/kollegen/client/silk.bin"),
    ("@deps", "kollegen-bundle-clothconfig.jar", "dev/kollegen/client/clothconfig.bin"),
];





const LEGACY_BUNDLE_JARS: &[&str] = &["kollegen-bundle-flk.jar"];






fn bundle_standalone_prefixes(jar_name: &str) -> &'static [&'static str] {
    match jar_name {
        "kollegen-bundle-spotify.jar" => &["spotify_overlay", "spotify-overlay"],
        "kollegen-bundle-chatheads.jar" => &["chat_heads", "chat-heads"],
        "kollegen-bundle-fabric-api.jar" => &["fabric-api", "fabric_api"],
        "kollegen-bundle-flk.jar" => &["fabric-language-kotlin", "fabric_language_kotlin"],
        "kollegen-bundle-owo.jar" => &["owo-lib", "owo_lib"],
        "kollegen-bundle-modmenu.jar" => &["modmenu"],
        "kollegen-bundle-tpa.jar" => &["kollegen-tpa", "tpa-"],
        "kollegen-bundle-silk.jar" => &["silk-"],
        "kollegen-bundle-clothconfig.jar" => &["cloth-config", "cloth_config"],
        _ => &[],
    }
}

fn bundles_flag_path(mods_dir: &Path) -> PathBuf {
    mods_dir.join(".kollegen-bundles.json")
}



fn read_bundle_flags(mods_dir: &Path) -> Value {
    let mut flags = serde_json::json!({ "spotify": true, "chatheads": true });
    if let Ok(s) = fs::read_to_string(bundles_flag_path(mods_dir)) {
        if let Ok(v) = serde_json::from_str::<Value>(&s) {
            if v.is_object() {
                for k in ["spotify", "chatheads"] {
                    if let Some(b) = v.get(k).and_then(|x| x.as_bool()) {
                        flags[k] = serde_json::Value::Bool(b);
                    }
                }
            }
        }
    }
    flags
}










fn strip_shaded_kotlin(jar: &[u8]) -> Option<Vec<u8>> {
    let reader = std::io::Cursor::new(jar);
    let mut archive = zip::ZipArchive::new(reader).ok()?;
    let should_strip = |name: &str| {
        name.starts_with("kotlin/")
            || name.starts_with("kotlinx/")
            || name.starts_with("_COROUTINE/")
            || (name.starts_with("META-INF/kotlinx-")
                && (name.ends_with(".kotlin_module") || name.ends_with(".pro")))
            || name.starts_with("META-INF/kotlin-stdlib")
    };
    let mut changed = false;
    let mut out = Vec::new();
    {
        let mut writer = zip::ZipWriter::new(std::io::Cursor::new(&mut out));
        let opts =
            zip::write::FileOptions::default().compression_method(zip::CompressionMethod::Deflated);
        for i in 0..archive.len() {
            let mut entry = match archive.by_index(i) {
                Ok(e) => e,
                Err(_) => continue,
            };
            let name = entry.name().to_string();
            if should_strip(&name) {
                changed = true;
                continue;
            }
            let mut data = Vec::new();
            if entry.read_to_end(&mut data).is_err() {
                continue;
            }
            let _ = writer.start_file(&name, opts);
            let _ = writer.write_all(&data);
        }
        if !changed {
            return None;
        }
        let _ = writer.finish();
    }
    Some(out)
}











fn sanitize_spotify_bundle(mods_dir: &Path) {
    let path = mods_dir.join("kollegen-bundle-spotify.jar");
    
    let replacements = {
        let file = match fs::File::open(&path) {
            Ok(f) => f,
            Err(_) => return,
        };
        let mut archive = match zip::ZipArchive::new(file) {
            Ok(a) => a,
            Err(_) => return,
        };
        let mut reps: Vec<(String, Vec<u8>)> = Vec::new();
        for i in 0..archive.len() {
            let mut entry = match archive.by_index(i) {
                Ok(e) => e,
                Err(_) => continue,
            };
            let name = entry.name().to_string();
            if !(name.starts_with("META-INF/jars/") && name.ends_with(".jar")) {
                continue;
            }
            let mut data = Vec::new();
            if entry.read_to_end(&mut data).is_err() {
                continue;
            }
            if let Some(cleaned) = strip_shaded_kotlin(&data) {
                reps.push((name, cleaned));
            }
        }
        reps
    };
    if replacements.is_empty() {
        return;
    }
    info!(
        "Entferne shadowed kotlinx.serialization <1.8.0 aus kollegen-bundle-spotify.jar \
         (Essential-AbstractMethodError-Fix)"
    );
    
    let file = match fs::File::open(&path) {
        Ok(f) => f,
        Err(_) => return,
    };
    let mut archive = match zip::ZipArchive::new(file) {
        Ok(a) => a,
        Err(_) => return,
    };
    let tmp = path.with_extension("jar.tmp2");
    let ok = (|| -> std::io::Result<()> {
        let out = fs::File::create(&tmp)?;
        {
            let mut writer = zip::ZipWriter::new(out);
            let opts =
                zip::write::FileOptions::default().compression_method(zip::CompressionMethod::Deflated);
            for i in 0..archive.len() {
                let mut entry = match archive.by_index(i) {
                    Ok(e) => e,
                    Err(_) => continue,
                };
                let name = entry.name().to_string();
                let mut data = Vec::new();
                if entry.read_to_end(&mut data).is_err() {
                    continue;
                }
                let bytes = replacements
                    .iter()
                    .find(|(n, _)| *n == name)
                    .map(|(_, b)| b.as_slice())
                    .unwrap_or(&data);
                let _ = writer.start_file(&name, opts);
                let _ = writer.write_all(bytes);
            }
            writer.finish()?;
        }
        fs::rename(&tmp, &path)?;
        Ok(())
    })();
    if ok.is_err() {
        let _ = fs::remove_file(&tmp);
    }
}

fn remove_bundle_jars(mods_dir: &Path) {
    let Ok(entries) = fs::read_dir(mods_dir) else {
        return;
    };
    for e in entries.flatten() {
        let p = e.path();
        if !p.is_file() {
            continue;
        }
        let name = e.file_name().to_string_lossy().to_lowercase();
        if name.starts_with("kollegen-bundle-") {
            info!("Entferne 1.21.x-Bundle aus inkompatibler Instanz: {}", name);
            let _ = fs::remove_file(&p);
        }
    }
}







pub(crate) fn enforce_bundled_mods(mods_dir: &Path, mc_version: &str, companion_jar: Option<&Path>) {
    if let Err(e) = fs::create_dir_all(mods_dir) {
        warn!("mods-Verzeichnis {} nicht erstellbar: {}", mods_dir.display(), e);
        return;
    }

    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    
    if !crate::companion::bundles_compatible(mc_version) {
        warn!(
            "Integrations-Bundles bei MC {} übersprungen: die gebündelten Jars sind exakt für {} kompiliert/remapped. \
             Lade keine 1.21.x-Jars in diese Instanz.",
            mc_version, crate::companion::COMPANION_TARGET_MC_VERSION
        );
        remove_bundle_jars(mods_dir);
        return;
    }
    let flags = read_bundle_flags(mods_dir);
    let flag_on =
        |k: &str| flags.get(k).and_then(|v| v.as_bool()).unwrap_or(true);

    
    let mut archive: Option<zip::ZipArchive<fs::File>> = None;
    if let Some(path) = companion_jar {
        match fs::File::open(path) {
            Ok(f) => match zip::ZipArchive::new(f) {
                Ok(z) => archive = Some(z),
                Err(e) => warn!("Begleit-Jar {} nicht lesbar: {}", path.display(), e),
            },
            Err(e) => warn!("Begleit-Jar {} nicht gefunden: {}", path.display(), e),
        }
    }
    let archive_available = archive.is_some();
    let available: std::collections::HashSet<String> = archive
        .as_mut()
        .map(|a| a.file_names().map(|s| s.to_string()).collect())
        .unwrap_or_default();

    
    
    
    
    
    
    
    
    let essential_present = fs::read_dir(mods_dir)
        .map(|entries| {
            entries.flatten().any(|e| {
                e.file_name().to_string_lossy().to_lowercase().starts_with("essential")
            })
        })
        .unwrap_or(false);

    
    
    
    
    
    let decisions: Vec<(&str, &str, bool, bool)> = BUNDLED_MODS
        .iter()
        .map(|&(flag_key, jar_name, bin_path)| {
            
            
            
            
            
            
            
            let desired = if flag_key == "@deps" { true } else { flag_on(flag_key) };
            let deployable = archive_available && available.contains(bin_path);
            (jar_name, bin_path, desired, desired && deployable)
        })
        .collect();

    
    
    
    let flk_will_deploy = decisions
        .iter()
        .any(|&(jn, _, _, wd)| jn == "kollegen-bundle-flk.jar" && wd);

    
    
    
    
    let autoremove = mods_dir.join(".kollegen-autoremove").exists();
    if autoremove {
    if let Ok(entries) = fs::read_dir(mods_dir) {
        for e in entries.flatten() {
            let p = e.path();
            if !p.is_file() {
                continue;
            }
            let name = e.file_name().to_string_lossy().to_lowercase();
            
            
            if LEGACY_BUNDLE_JARS
                .iter()
                .any(|l| name == *l || name == format!("{l}.disabled"))
            {
                info!("Entferne Legacy-Bundle (wird nicht mehr gebündelt): {}", name);
                let _ = fs::remove_file(&p);
                continue;
            }
            
            
            
            
            
            
            
            
            if name.starts_with("fabric-language-kotlin") || name.starts_with("fabric_language_kotlin") {
                info!("Entferne eigenständiges FLK (Essential stellt es selbst bereit): {}", name);
                let _ = fs::remove_file(&p);
                continue;
            }
            if !name.ends_with(".jar") && !name.ends_with(".disabled") {
                continue;
            }
            if name.starts_with("kollegen-client-mod") || name.starts_with("kollegen-bundle") {
                continue;
            }
            let will_replace = decisions.iter().any(|&(jn, _, _, wd)| {
                wd && bundle_standalone_prefixes(jn).iter().any(|pre| name.starts_with(*pre))
            });
            if will_replace {
                info!("Entferne Standalone-Kopie (bündelt der Kollegen Client): {}", name);
                let _ = fs::remove_file(&p);
            }
        }
    }
    } 

    
    
    
    
    
    
    
    
    if essential_present && flk_will_deploy {
        if let Ok(entries) = fs::read_dir(mods_dir) {
            for e in entries.flatten() {
                let p = e.path();
                if !p.is_file() {
                    continue;
                }
                let name = e.file_name().to_string_lossy().to_lowercase();
                if name.starts_with("fabric-language-kotlin") || name.starts_with("fabric_language_kotlin") {
                    info!("Entferne eigenständiges FLK (Essential stellt es selbst bereit): {}", name);
                    let _ = fs::remove_file(&p);
                }
            }
        }
    }

    
    
    
    for &(jar_name, bin_path, desired, will_deploy) in &decisions {
        let dest = mods_dir.join(jar_name);
        if will_deploy {
            let Some(archive) = archive.as_mut() else { continue };
            let mut src = match archive.by_name(bin_path) {
                Ok(s) => s,
                Err(_) => continue,
            };
            let tmp = dest.with_extension("jar.tmp");
            let ok = fs::File::create(&tmp)
                .and_then(|mut out| std::io::copy(&mut src, &mut out).map(|_| ()))
                .is_ok();
            if ok {
                let _ = fs::rename(&tmp, &dest);
            } else {
                let _ = fs::remove_file(&tmp);
                warn!("Konnte Bundle {} nicht aus der Begleit-Mod extrahieren.", jar_name);
            }
        } else if !desired {
            let _ = fs::remove_file(&dest);
            let _ = fs::remove_file(mods_dir.join(format!("{}.disabled", jar_name)));
        }
    }

    
    
    
    
    
    
    sanitize_spotify_bundle(mods_dir);

    
    if let Ok(json) = serde_json::to_string_pretty(&flags) {
        let _ = fs::write(bundles_flag_path(mods_dir), json);
    }
}


fn write_classpath_jar(inst_dir: &Path, entries: &[String]) -> Result<String> {
    let jar_path = inst_dir.join("classpath.jar");
    let mut tokens: Vec<String> = Vec::with_capacity(entries.len());
    for abs in entries {
        match Path::new(abs).strip_prefix(inst_dir) {
            Ok(rel) => tokens.push(rel.to_string_lossy().replace('\\', "/").replace(' ', "%20")),
            Err(_) => tokens.push(format!("file:///{}", abs.replace('\\', "/").replace(' ', "%20"))),
        }
    }
    let mut manifest = String::from("Manifest-Version: 1.0\r\n");
    let mut line = String::from("Class-Path: ");
    for (i, t) in tokens.iter().enumerate() {
        let candidate = if i == 0 {
            t.clone()
        } else {
            format!("{} {}", line, t)
        };
        if candidate.len() > 72 {
            manifest.push_str(&line);
            manifest.push_str("\r\n ");
            line = t.clone();
        } else {
            line = candidate;
        }
    }
    manifest.push_str(&line);
    manifest.push_str("\r\n\r\n");
    let file = fs::File::create(&jar_path)?;
    let mut zw = zip::ZipWriter::new(file);
    let opts = zip::write::FileOptions::default()
        .compression_method(zip::CompressionMethod::Stored);
    zw.start_file("META-INF/MANIFEST.MF", opts)?;
    zw.write_all(manifest.as_bytes())?;
    zw.finish()?;
    Ok(jar_path.to_string_lossy().into_owned())
}

pub fn launch(
    state: &AppState,
    data_dir: &Path,
    inst: &Instance,
    java_path: &str,
    settings: &Settings,
) -> Result<String> {
    let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
    
    ensure_kollegen_mod(data_dir, &inst.name, &inst.loader, &inst.version);
    
    
    
    
    let mods_dir = inst_dir.join("mods");
    enforce_renderer_consistency(&mods_dir, inst.vulkan_enabled);
    
    
    enforce_controller_state(&mods_dir, settings.steamdeck_mode);
    
    
    
    
    let companion_jar = crate::companion::companion_jar(data_dir);
    enforce_bundled_mods(&mods_dir, &inst.version, companion_jar.as_deref());
    
    
    if let Ok(mut logs) = state.logs.lock() {
        logs.clear();
    }
    let version_dir = inst_dir.join("versions").join(&inst.version);
    let libs_dir = inst_dir.join("libraries");
    let assets_dir = inst_dir.join("assets");

    info!("Launching Minecraft {} ({}) for instance '{}'...", inst.version, inst.loader, inst.name);

    
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

    
    info!("Prüfe Minecraft-Assets...");
    let _ = download_assets(data_dir, &inst.name, &inst.version);

    
    let vjson_path = version_dir.join(format!("{}.json", inst.version));
    let vjson_str = fs::read_to_string(&vjson_path)?;
    let vjson: VersionJson = serde_json::from_str(&vjson_str)?;

    
    
    
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

    
    let mut classpath = vec![version_jar.to_string_lossy().into_owned()];

    
    if libs_dir.exists() {
        for entry in collect_jars(&libs_dir) {
            if !classpath.contains(&entry) {
                classpath.push(entry);
            }
        }
    }

    
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

    
    let mut jvm_args: Vec<String> = vec![];

    
    if cfg!(target_os = "macos") {
        jvm_args.push("-XstartOnFirstThread".to_string());
    }

    
    let natives_dir = version_dir.join("natives");
    if natives_dir.exists() {
        jvm_args.push(format!("-Djava.library.path={}", natives_dir.to_string_lossy()));
    }

    
    jvm_args.push(format!("-Xms{}", inst.memory_min));
    jvm_args.push(format!("-Xmx{}", inst.memory_max));

    
    let os_cp_sep = if cfg!(target_os = "windows") { ";" } else { ":" };
    let joined = classpath.join(os_cp_sep);
    let cp_arg = if cfg!(target_os = "windows") && joined.len() > 28000 {
        write_classpath_jar(&inst_dir, &classpath)?
    } else {
        joined
    };
    jvm_args.push("-cp".to_string());
    jvm_args.push(cp_arg);

    
    let mut accounts = crate::utils::load_json::<Vec<crate::types::Account>>(
        &crate::utils::accounts_file(&state.data_dir),
        vec![],
    );

    
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

    
    let mut cmd = std::process::Command::new(java_path);
    crate::java::sanitize_java_env(&mut cmd);
    
    
    #[cfg(windows)]
    {
        use std::os::windows::process::CommandExt;
        cmd.creation_flags(0x08000000);
    }
    cmd.args(&jvm_args);

    
    cmd.arg(&main_class);

    
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

    
    if let Some(server) = &inst.server {
        let parts: Vec<&str> = server.split(':').collect();
        if parts.len() >= 2 {
            cmd.args(&["--server", parts[0], "--port", parts[1]]);
        }
    }

    
    if let Some(jargs) = &inst.java_args {
        for arg in jargs.split_whitespace() {
            cmd.arg(arg);
        }
    }

    
    cmd.current_dir(&inst_dir);

    
    
    
    
    
    let log_path = inst_dir.join("logs").join("latest.log");
    if let Some(parent) = log_path.parent() {
        let _ = fs::create_dir_all(parent);
    }
    let _ = fs::File::create(&log_path);
    cmd.stdout(Stdio::piped());
    cmd.stderr(Stdio::piped());

    
    
    ensure_title_logo_pack(&inst_dir, &inst.version);

    
    
    
    
    
    
    
    
    
    let report_path = dirs::home_dir().map(|h| h.join(".kollegen").join("last-launch-report.txt"));
    if let Some(rp) = &report_path {
        if let Some(p) = rp.parent() {
            let _ = fs::create_dir_all(p);
        }
    }

    
    let java_probe = std::process::Command::new(java_path)
        .arg("-version")
        .output();
    let java_version_note = match &java_probe {
        Ok(o) => {
            let s = String::from_utf8_lossy(&o.stderr);
            let t = s.trim();
            if t.is_empty() {
                String::from_utf8_lossy(&o.stdout).trim().to_string()
            } else {
                t.to_string()
            }
        }
        Err(e) => format!("java -version fehlgeschlagen: {}", e),
    };
    if java_probe.is_err() {
        let mut extra = String::new();
        if let Some(rp) = &report_path {
            extra = format!(" (Bericht: {})", rp.display());
        }
        return Err(anyhow!(
            "Java ist nicht startbar ({}):{}{}",
            java_path,
            if java_version_note.is_empty() {
                String::new()
            } else {
                format!(" {}", java_version_note)
            },
            extra
        ));
    }

    let cmdline = std::iter::once(java_path.to_string())
        .chain(cmd.get_args().map(|a| a.to_string_lossy().into_owned()))
        .collect::<Vec<_>>()
        .join(" ");
    {
        let mut txt = String::new();
        txt.push_str(&format!(
            "Kollegen-Launcher Start-Bericht — {}\n",
            chrono::Local::now().format("%Y-%m-%d %H:%M:%S")
        ));
        txt.push_str(&format!(
            "Instanz: {} ({} / {})\n",
            inst.name, inst.version, inst.loader
        ));
        txt.push_str(&format!("java-Binary: {}\n", java_path));
        txt.push_str(&format!("java -version:\n{}\n", java_version_note));
        txt.push_str("Umgebung (relevant):\n");
        for key in [
            "DISPLAY",
            "WAYLAND_DISPLAY",
            "XDG_SESSION_TYPE",
            "XDG_CURRENT_DESKTOP",
            "STEAM_GAMEID",
            "STEAM_COMPAT_APP_ID",
            "APPIMAGE",
            "APPDIR",
            "JAVA_HOME",
            "JAVA_TOOL_OPTIONS",
            "_JAVA_OPTIONS",
            "GDK_BACKEND",
            "LIBGL_ALWAYS_SOFTWARE",
            "MESA_VK_WSI_PRESENT_MODE",
        ] {
            if let Ok(v) = std::env::var(key) {
                txt.push_str(&format!("  {}={}\n", key, v));
            }
        }
        txt.push_str("Aufruf:\n");
        txt.push_str(&cmdline);
        txt.push_str("\n");
        txt.push_str("── Start (Spawn) ──\n");
        if let Some(rp) = &report_path {
            let _ = fs::write(rp, txt);
        }
    }

    info!("Starting Minecraft process...");
    let mut child = cmd.spawn()?;
    let pid = child.id();

    
    
    
    
    let last_activity = Arc::new(AtomicU64::new(0));

    
    
    
    
    let log_discord_tx = state.discord.tx.clone();
    let log_version = inst.version.clone();
    let log_loader = inst.loader.clone();
    let log_name = inst.name.clone();
    if let Some(mut out) = child.stdout.take() {
        let logs_arc = Arc::clone(&state.logs);
        let log_path2 = log_path.clone();
        let last_activity2 = Arc::clone(&last_activity);
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
                                last_activity2.store(
                                    std::time::SystemTime::now()
                                        .duration_since(std::time::UNIX_EPOCH)
                                        .map(|d| d.as_millis() as u64)
                                        .unwrap_or(0),
                                    Ordering::Relaxed,
                                );
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

    
    
    if let Some(mut err) = child.stderr.take() {
        let logs_arc = Arc::clone(&state.logs);
        let log_path2 = log_path.clone();
        let last_activity2 = Arc::clone(&last_activity);
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
                match err.read(&mut buf) {
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

    
    
    
    
    
    
    let discord_tx = state.discord.tx.clone();
    let logs_arc = Arc::clone(&state.logs);
    let log_path2 = log_path.clone();
    let data_dir2 = state.data_dir.clone();
    let report_path2 = report_path.clone();
    thread::spawn(move || {
        let t0 = std::time::Instant::now();
        let mut exit_code: Option<i32> = None;
        let mut signalled = false;
        let mut last_silent_warn = 0u64;
        loop {
            match child.try_wait() {
                Ok(Some(status)) => {
                    match status.code() {
                        Some(c) => exit_code = Some(c),
                        None => signalled = true,
                    }
                    break;
                }
                Ok(None) => {
                    
                    
                    
                    let last = last_activity.load(Ordering::Relaxed);
                    if last != 0 && t0.elapsed().as_secs() > 10 {
                        let now_ms = std::time::SystemTime::now()
                            .duration_since(std::time::UNIX_EPOCH)
                            .map(|d| d.as_millis() as u64)
                            .unwrap_or(0);
                        let silent = now_ms.saturating_sub(last);
                        if silent > 60_000 && last != last_silent_warn {
                            last_silent_warn = last;
                            let warn = format!(
                                "WARNUNG: Minecraft läuft (PID {}), schreibt aber seit {}s keinen Output mehr – das Spiel hängt vermutlich (Renderer/OpenAL/Vulkan?).",
                                pid, silent / 1000
                            );
                            if let Ok(mut logs) = logs_arc.lock() {
                                logs.push(warn);
                                if logs.len() > crate::MAX_LOG_LINES {
                                    logs.remove(0);
                                }
                            }
                        }
                    }
                    thread::sleep(Duration::from_millis(2000));
                }
                Err(_) => {
                    signalled = true;
                    break;
                }
            }
        }
        let runtime = t0.elapsed().as_secs_f64();
        let mut line = format!("Minecraft-Prozess beendet ({:.0}s).", runtime);
        match exit_code {
            Some(0) => {}
            Some(c) => line.push_str(&format!(" Exit-Code: {}.", c)),
            None => line.push_str(if signalled { " Vom Signal beendet (Absturz?)." } else { "" }),
        }
        if runtime < 20.0 {
            line.push_str(" Sehr kurze Laufzeit – vermutlich Startabsturz.");
        }
        if let Ok(mut logs) = logs_arc.lock() {
            logs.push(line.clone());
            if logs.len() > crate::MAX_LOG_LINES {
                logs.remove(0);
            }
        }
        let lf = crate::utils::log_file(&data_dir2);
        if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(lf) {
            let ts = chrono::Local::now().format("%Y-%m-%d %H:%M:%S").to_string();
            let _ = std::writeln!(f, "[{}] {}", ts, line);
        }
        
        
        let log_tail = crate::read_log_tail(&log_path2, 16 * 1024);
        if !log_tail.trim().is_empty() {
            if let Ok(mut logs) = logs_arc.lock() {
                logs.push("── Letzte Log-Zeilen vor dem Ende ──".to_string());
                for l in log_tail.lines().rev().take(8) {
                    logs.push(l.trim_end().to_string());
                }
            }
        }
        
        if let Some(rp) = &report_path2 {
            let mut txt = String::new();
            txt.push_str(&format!(
                "\n── Ende ({}) ──\n{}\n",
                chrono::Local::now().format("%Y-%m-%d %H:%M:%S"),
                line,
            ));
            if !log_tail.trim().is_empty() {
                txt.push_str("── Letzte Log-Zeilen ──\n");
                for l in log_tail.lines().rev().take(15) {
                    txt.push_str(l.trim_end());
                    txt.push('\n');
                }
            }
            if let Ok(mut f) = fs::OpenOptions::new().create(true).append(true).open(rp) {
                let _ = f.write_all(txt.as_bytes());
            }
        }
        let _ = discord_tx.send(crate::discord::RpcMessage::Set {
            details: "Kollegen Client".to_string(),
            state: "Im Launcher".to_string(),
            large_text: "Kollegen Client".to_string(),
            server: None,
            players: None,
        });
        crate::discord::set_current_server(None);
    });

    
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



fn parse_server_from_log(line: &str) -> Option<String> {
    let marker = "Connecting to ";
    let idx = line.find(marker)?;
    let rest = &line[idx + marker.len()..];
    let host_part = rest.split(',').next().unwrap_or("").trim();
    
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


fn is_disconnect_log(line: &str) -> bool {
    line.contains("Disconnected from server")
        || line.contains("Lost connection")
        || line.contains("Client disconnected")
}


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





fn sanitize_name(name: &str) -> String {
    let cleaned: String = name
        .trim()
        .chars()
        .map(|c| match c {
            '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|' => '_',
            c => c,
        })
        .collect();
    if cleaned.is_empty() {
        "Modpack".to_string()
    } else {
        cleaned
    }
}





pub fn import_pack(data_dir: &Path, path: &str) -> Result<Instance> {
    let file = fs::File::open(path)
        .map_err(|e| anyhow!("Konnte Paket nicht öffnen: {}", e))?;
    let mut archive = zip::ZipArchive::new(file)
        .map_err(|e| anyhow!("Datei ist kein gültiges zip: {}", e))?;

    
    let mut index_name = None;
    for i in 0..archive.len() {
        if let Ok(f) = archive.by_index(i) {
            let lower = f.name().to_lowercase();
            if lower == "modrinth.index.json" || lower.ends_with("/modrinth.index.json") {
                index_name = Some(f.name().to_string());
                break;
            }
        }
    }
    let index_name = index_name.ok_or_else(|| {
        anyhow!(
            "Kein Modrinth-Pack erkannt (modrinth.index.json fehlt). Nur .mrpack / Modrinth-zip werden unterstützt."
        )
    })?;

    let mut idx_file = archive
        .by_name(&index_name)
        .map_err(|e| anyhow!("Konnte Index nicht lesen: {}", e))?;
    let mut index_str = String::new();
    idx_file
        .read_to_string(&mut index_str)
        .map_err(|e| anyhow!("Index ungültig: {}", e))?;
    let index: Value = serde_json::from_str(&index_str)
        .map_err(|e| anyhow!("modrinth.index.json Parse-Fehler: {}", e))?;

    let game = index
        .get("game")
        .and_then(|g| g.as_str())
        .unwrap_or("minecraft");
    if game != "minecraft" {
        return Err(anyhow!(
            "Nur Minecraft-Packs werden unterstützt (game={}).",
            game
        ));
    }

    let deps = index
        .get("dependencies")
        .and_then(|d| d.as_object())
        .ok_or_else(|| anyhow!("Keine dependencies im Pack."))?;
    let mc_version = deps
        .get("minecraft")
        .and_then(|v| v.as_str())
        .ok_or_else(|| anyhow!("Minecraft-Version fehlt im Pack."))?
        .to_string();

    
    let (loader, loader_version) = if let Some(v) =
        deps.get("fabric-loader").and_then(|v| v.as_str())
    {
        ("fabric".to_string(), Some(v.to_string()))
    } else if let Some(v) = deps.get("forge").and_then(|v| v.as_str()) {
        ("forge".to_string(), Some(v.to_string()))
    } else if let Some(v) = deps.get("neoforge").and_then(|v| v.as_str()) {
        ("neoforge".to_string(), Some(v.to_string()))
    } else if let Some(v) = deps.get("quilt-loader").and_then(|v| v.as_str()) {
        ("quilt".to_string(), Some(v.to_string()))
    } else {
        ("vanilla".to_string(), None)
    };

    let pack_name = index
        .get("name")
        .and_then(|n| n.as_str())
        .unwrap_or("Importiertes Pack")
        .to_string();
    let summary = index
        .get("summary")
        .and_then(|s| s.as_str())
        .unwrap_or("")
        .to_string();

    
    let inst_path = crate::utils::instances_file(data_dir);
    let mut instances = crate::utils::load_json::<Vec<Instance>>(&inst_path, vec![]);
    let base = sanitize_name(&pack_name);
    let mut name = base.clone();
    let mut n = 2;
    while instances.iter().any(|i| i.name == name) {
        name = format!("{} ({})", base, n);
        n += 1;
    }

    let inst = Instance {
        id: uuid::Uuid::new_v4().to_string(),
        name: name.clone(),
        version: mc_version.clone(),
        loader: loader.clone(),
        loader_version,
        description: summary,
        mods: vec!["essentialmod.jar".to_string()],
        vulkan_enabled: false,
        memory_min: crate::DEFAULT_MEMORY_MIN.to_string(),
        memory_max: crate::DEFAULT_MEMORY_MAX.to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played: None,
        java_args: None,
        server: None,
    };

    instances.push(inst.clone());
    crate::utils::save_json(&inst_path, &instances)?;

    
    info!("Importiere Modpack '{}' (MC {})…", name, mc_version);
    install_instance(
        data_dir,
        &name,
        &mc_version,
        &loader,
        inst.loader_version.as_deref(),
    )?;

    
    let inst_dir = crate::utils::instance_dir(data_dir, &name);
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(120))
        .build()?;
    if let Some(files) = index.get("files").and_then(|f| f.as_array()) {
        for f in files {
            let fpath = match f.get("path").and_then(|p| p.as_str()) {
                Some(p) => p,
                None => continue,
            };
            
            if let Some(env) = f.get("env").and_then(|e| e.as_object()) {
                let client_env = env
                    .get("client")
                    .and_then(|c| c.as_str())
                    .unwrap_or("required");
                if client_env == "unsupported" {
                    continue;
                }
            }
            let downloads = match f.get("downloads").and_then(|d| d.as_array()) {
                Some(d) if !d.is_empty() => d,
                _ => continue,
            };
            let url = match downloads[0].as_str() {
                Some(u) => u.to_string(),
                None => continue,
            };
            let dest = inst_dir.join(fpath);
            if let Some(parent) = dest.parent() {
                let _ = fs::create_dir_all(parent);
            }
            if dest.exists() {
                continue;
            }
            info!("Lade Pack-Datei {}…", fpath);
            if let Err(e) = crate::utils::download_file_client(&client, &url, &dest) {
                warn!("Konnte {} nicht laden: {}", fpath, e);
            } else if let Some(h) = f
                .get("hashes")
                .and_then(|h| h.get("sha1"))
                .and_then(|h| h.as_str())
            {
                if let Ok(bytes) = fs::read(&dest) {
                    if crate::utils::sha1_hex(&bytes) != h {
                        warn!("SHA1 stimmt nicht überein bei {} (ignoriert).", fpath);
                    }
                }
            }
        }
    }

    Ok(inst)
}




