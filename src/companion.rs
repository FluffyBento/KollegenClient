














use log::{info, warn};
use std::io::Write;
use std::path::{Path, PathBuf};


pub const COMPANION_MOD_FILENAME: &str = "kollegen-client-mod.jar";

pub const COMPANION_MOD_PREFIX: &str = "kollegen-client";








pub const COMPANION_TARGET_MC_VERSION: &str = "1.21.11";

const GITHUB_DOWNLOAD_URL: &str =
    "https://github.com/FluffyBento/KollegenClient/releases/latest/download/kollegen-client-mod.jar";



pub fn is_companion_mod_name(filename: &str) -> bool {
    let lc = filename.to_ascii_lowercase();
    lc == COMPANION_MOD_FILENAME
        || (lc.starts_with(COMPANION_MOD_PREFIX) && lc.ends_with(".jar"))
}



fn is_valid_jar(p: &Path) -> bool {
    match std::fs::metadata(p) {
        Ok(m) if m.len() > 0 => match std::fs::read(p) {
            Ok(b) => b.len() >= 4 && b.starts_with(b"PK"),
            Err(_) => false,
        },
        _ => false,
    }
}

fn cache_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("companion")
}


fn jar_fabric_version(p: &Path) -> Option<String> {
    use std::io::Read;
    let file = std::fs::File::open(p).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut fmj = String::new();
    archive
        .by_name("fabric.mod.json")
        .ok()?
        .read_to_string(&mut fmj)
        .ok()?;
    let value: serde_json::Value = serde_json::from_str(&fmj).ok()?;
    value.get("version")?.as_str().map(str::to_string)
}





fn version_at_least(a: &str, b: &str) -> bool {
    let parse = |v: &str| -> Vec<(u64, bool)> {
        v.split(['.', '-', '+'])
            .map(|s| (s.parse::<u64>().unwrap_or(0), s.chars().all(|c| c.is_ascii_digit())))
            .collect()
    };
    let (va, vb) = (parse(a), parse(b));
    for i in 0..vb.len().max(va.len()) {
        let (sa, sb) = (va.get(i), vb.get(i));
        match (sa, sb) {
            (Some(x), Some(y)) => match x.cmp(y) {
                std::cmp::Ordering::Greater => return true,
                std::cmp::Ordering::Less => return false,
                std::cmp::Ordering::Equal => {}
            },
            
            
            (Some(x), None) => return x.0 > 0 && x.1,
            (None, Some(y)) => return !(y.0 > 0 && y.1),
            (None, None) => break,
        }
    }
    true
}

fn try_download(data_dir: &Path) -> Option<PathBuf> {
    let dest = cache_dir(data_dir).join(COMPANION_MOD_FILENAME);
    if is_valid_jar(&dest) {
        return Some(dest);
    }
    info!("Lade Kollegen Client Mod von GitHub Releases herunter…");
    match crate::utils::download_file(GITHUB_DOWNLOAD_URL, &dest) {
        Ok(()) if is_valid_jar(&dest) => Some(dest),
        _ => {
            warn!("Kollegen Mod-Download fehlgeschlagen.");
            None
        }
    }
}




fn refresh_cache(data_dir: &Path) -> Option<PathBuf> {
    let dir = cache_dir(data_dir);
    let _ = std::fs::create_dir_all(&dir);
    let dest = dir.join(COMPANION_MOD_FILENAME);
    let tmp = dir.join("kollegen-client-mod.jar.tmp");
    match crate::utils::download_file(GITHUB_DOWNLOAD_URL, &tmp) {
        Ok(()) if is_valid_jar(&tmp) => {
            
            
            
            let keep_cached = is_valid_jar(&dest)
                .then(|| {
                    match (
                        jar_fabric_version(&dest).as_deref(),
                        jar_fabric_version(&tmp).as_deref(),
                    ) {
                        (Some(cur), Some(new)) => version_at_least(cur, new),
                        
                        _ => false,
                    }
                })
                .unwrap_or(false);
            if keep_cached {
                let _ = std::fs::remove_file(&tmp);
            } else {
                let _ = std::fs::rename(&tmp, &dest);
            }
            Some(dest)
        }
        _ => {
            let _ = std::fs::remove_file(&tmp);
            warn!("Kollegen-Mod-Update fehlgeschlagen – bestehende Version wird genutzt.");
            None
        }
    }
}



pub fn companion_jar(data_dir: &Path) -> Option<PathBuf> {
    
    let cached = cache_dir(data_dir).join(COMPANION_MOD_FILENAME);
    if is_valid_jar(&cached) {
        return Some(cached);
    }

    
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            for cand in [
                dir.join("resources").join(COMPANION_MOD_FILENAME),
                dir.join(COMPANION_MOD_FILENAME),
            ] {
                if is_valid_jar(&cand) {
                    return Some(cand);
                }
            }
        }
    }

    
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let libs = manifest.join("kollegen-mod").join("build").join("libs");
    if let Ok(entries) = std::fs::read_dir(&libs) {
        let mut candidates: Vec<PathBuf> = entries
            .flatten()
            .map(|e| e.path())
            .filter(|p| p.extension().and_then(|x| x.to_str()) == Some("jar"))
            .collect();
        
        candidates.sort_by_key(|p| {
            p.file_name()
                .and_then(|n| n.to_str())
                .map(|n| if n == COMPANION_MOD_FILENAME { 0 } else { 1 })
                .unwrap_or(2)
        });
        if let Some(p) = candidates
            .into_iter()
            .find(|p| {
                p.file_name()
                    .and_then(|n| n.to_str())
                    .map(|n| is_companion_mod_name(n))
                    .unwrap_or(false)
            })
            .filter(|p| is_valid_jar(p))
        {
            return Some(p);
        }
    }

    
    try_download(data_dir)
}






fn relax_companion_constraints(jar: &Path) -> PathBuf {
    let patched = std::env::temp_dir().join(format!(
        "kollegen-mod-relaxed-{}.jar",
        std::process::id()
    ));
    if let (Ok(file), Ok(out)) = (std::fs::File::open(jar), std::fs::File::create(&patched)) {
        if let Ok(mut archive) = zip::ZipArchive::new(file) {
            let mut writer = zip::ZipWriter::new(out);
            let opts = zip::write::FileOptions::default()
                .compression_method(zip::CompressionMethod::Deflated);
            let mut ok = true;
            for i in 0..archive.len() {
                let mut entry = match archive.by_index(i) {
                    Ok(e) => e,
                    Err(_) => {
                        ok = false;
                        break;
                    }
                };
                let name = entry.name().to_string();
                if name == "fabric.mod.json" {
                    let mut bytes = Vec::new();
                    if std::io::Read::read_to_end(&mut entry, &mut bytes).is_err() {
                        ok = false;
                        break;
                    }
                    match serde_json::from_slice::<serde_json::Value>(&bytes) {
                        Ok(mut doc) => {
                            if let Some(depends) =
                                doc.get_mut("depends").and_then(|d| d.as_object_mut())
                            {
                                depends.insert(
                                    "minecraft".to_string(),
                                    serde_json::Value::String(">=1.21".to_string()),
                                );
                            }
                            if let Ok(patched_bytes) = serde_json::to_vec(&doc) {
                                let _ = writer.start_file(&name, opts);
                                let _ = writer.write_all(&patched_bytes);
                                continue;
                            }
                            ok = false;
                            break;
                        }
                        Err(_) => {
                            ok = false;
                            break;
                        }
                    }
                }
                
                let mut bytes = Vec::new();
                if std::io::Read::read_to_end(&mut entry, &mut bytes).is_err() {
                    ok = false;
                    break;
                }
                let _ = writer.start_file(&name, opts);
                let _ = writer.write_all(&bytes);
            }
            let _ = writer.finish();
            if ok && patched.exists() {
                return patched;
            }
        }
    }
    
    jar.to_path_buf()
}


fn version_major_minor(version: &str) -> Option<(u32, u32)> {
    let mut parts = version.split('.');
    let major = parts.next().and_then(|s| s.parse::<u32>().ok())?;
    let minor = parts.next().and_then(|s| s.parse::<u32>().ok())?;
    Some((major, minor))
}






pub fn is_compatible_version(version: &str) -> bool {
    match (
        version_major_minor(version),
        version_major_minor(COMPANION_TARGET_MC_VERSION),
    ) {
        (Some(a), Some(b)) => a == b,
        _ => false,
    }
}











pub fn bundles_compatible(version: &str) -> bool {
    version == COMPANION_TARGET_MC_VERSION
}





pub fn install_companion_mod(data_dir: &Path, instance_name: &str, version: &str, loader: &str) {
    let loader_lc = loader.to_ascii_lowercase();
    if loader_lc.is_empty() || loader_lc == "vanilla" {
        return;
    }
    let fabric_compatible = loader_lc.contains("fabric") || loader_lc.contains("quilt");
    if !fabric_compatible {
        warn!(
            "Kollegen-Client-Mod wird bei Loader '{}' übersprungen (nur Fabric/Quilt unterstützt).",
            loader
        );
        return;
    }
    
    
    
    
    
    
    
    
    
    
    
    if !bundles_compatible(version) {
        warn!(
            "Kollegen-Client-Mod bei MC {v} übersprungen: die Mod (Mixins) ist nur mit Minecraft {t} kompatibel. \
             Eine Installation auf {v} würde beim Start mit einer Mixin-'Critical injection failure' abstürzen. \
             Bitte eine Instanz mit {t} nutzen (die Integrations-Bundles folgen derselben Regel).",
            v = version,
            t = COMPANION_TARGET_MC_VERSION
        );
        
        
        
        
        
        let mods_dir = crate::utils::instance_dir(data_dir, instance_name).join("mods");
        if let Ok(entries) = std::fs::read_dir(&mods_dir) {
            for e in entries.flatten() {
                let p = e.path();
                if !p.is_file() {
                    continue;
                }
                let name = e.file_name().to_string_lossy().to_lowercase();
                if is_companion_mod_name(&name) {
                    info!("Entferne inkompatible Kollegen-Client-Mod aus Instanz '{}': {}", instance_name, name);
                    let _ = std::fs::remove_file(&p);
                }
            }
        }
        return;
    }

    
    
    let _ = refresh_cache(data_dir);

    let source = match companion_jar(data_dir) {
        Some(j) => j,
        None => {
            warn!(
                "Kollegen-Client-Mod konnte nicht gefunden werden – Instanz '{}' ohne Companion-Mod.",
                instance_name
            );
            return;
        }
    };
    
    
    let jar = relax_companion_constraints(&source);

    let mods_dir = crate::utils::instance_dir(data_dir, instance_name).join("mods");
    if let Err(e) = std::fs::create_dir_all(&mods_dir) {
        warn!(
            "Konnte Ordner 'mods' nicht anlegen ({}): {}",
            mods_dir.display(),
            e
        );
        return;
    }

    let target = mods_dir.join(COMPANION_MOD_FILENAME);

    
    
    
    
    match std::fs::copy(&jar, &target) {
        Ok(_) => info!(
            "Kollegen-Client-Mod in Instanz '{}' (MC {}) injiziert.",
            instance_name, version
        ),
        Err(e) => warn!(
            "Konnte Kollegen-Client-Mod in Instanz '{}' nicht injizieren: {}",
            instance_name, e
        ),
    }
}