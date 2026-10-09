














use log::{info, warn};
use std::io::Write;
use std::path::{Path, PathBuf};


pub const COMPANION_MOD_FILENAME: &str = "kollegen-client-mod.jar";
pub const COMPANION_MOD_PREFIX: &str = "kollegen-client";
pub const COMPANION_MOD_FILENAME_1_21: &str = "kollegen-client-mod-1.21.11.jar";
pub const COMPANION_MOD_FILENAME_26_2: &str = "kollegen-client-mod-26.2.jar";

pub fn companion_target_versions() -> &'static [&'static str] {
    &["1.21.11", "26.2"]
}

pub fn is_version_supported(version: &str) -> bool {
    companion_target_versions().iter().any(|&v| version_compatible(version, v))
}

fn version_compatible(instance_version: &str, target_version: &str) -> bool {
    if instance_version == target_version {
        return true;
    }
    if target_version == "1.21.11" && instance_version.starts_with("1.21.") {
        return true;
    }
    if target_version == "26.2" && (instance_version.starts_with("26.") || instance_version == "1.21.11") {
        return true;
    }
    false
}

fn companion_filename_for_version(version: &str) -> &'static str {
    if version.starts_with("1.21.") {
        COMPANION_MOD_FILENAME_1_21
    } else if version.starts_with("26.") || version == "1.21.11" {
        COMPANION_MOD_FILENAME_26_2
    } else {
        COMPANION_MOD_FILENAME
    }
}

pub fn is_companion_mod_name(filename: &str) -> bool {
    let lc = filename.to_ascii_lowercase();
    lc == COMPANION_MOD_FILENAME
        || lc == COMPANION_MOD_FILENAME_1_21
        || lc == COMPANION_MOD_FILENAME_26_2
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

fn try_download(data_dir: &Path, version: &str) -> Option<PathBuf> {
    // Primär: versionierter Cache + versionierter Download (alle OS).
    // Der alte generische Dateiname hat version-fremde Dateien angeliefert.
    let filename = companion_filename_for_version(version);
    let dest = cache_dir(data_dir).join(filename);
    if companion_candidate_ok(&dest, version) {
        return Some(dest);
    }
    let _ = std::fs::create_dir_all(cache_dir(data_dir));
    let url = format!(
        "https://github.com/FluffyBento/KollegenClient/releases/latest/download/{}",
        filename
    );
    info!(
        "Lade Kollegen Client Mod ({}) von GitHub Releases herunter…",
        filename
    );
    let tmp = cache_dir(data_dir).join(format!("{}.tmp", filename));
    match crate::utils::download_file(&url, &tmp) {
        Ok(()) if companion_candidate_ok(&tmp, version) => {
            let _ = std::fs::rename(&tmp, &dest);
            Some(dest)
        }
        _ => {
            let _ = std::fs::remove_file(&tmp);
            // Letzter Notnagel: legacy generischer Cache – aber nur validiert!
            // (Eine kaputte Datei hier hat früher Crash-Loops verursacht.)
            let legacy = cache_dir(data_dir).join(COMPANION_MOD_FILENAME);
            if companion_candidate_ok(&legacy, version) {
                warn!(
                    "Nutze legacy Companion-Cache ({}).",
                    COMPANION_MOD_FILENAME
                );
                return Some(legacy);
            }
            warn!("Kollegen Mod-Download fehlgeschlagen.");
            None
        }
    }
}




fn refresh_cache(data_dir: &Path, version: &str) -> Option<PathBuf> {
    let dir = cache_dir(data_dir);
    let _ = std::fs::create_dir_all(&dir);
    let filename = companion_filename_for_version(version);
    let dest = dir.join(filename);
    let tmp = dir.join(format!("{}.tmp", filename));
    let url = format!(
        "https://github.com/FluffyBento/KollegenClient/releases/latest/download/{}",
        filename
    );
    match crate::utils::download_file(&url, &tmp) {
        Ok(()) if companion_candidate_ok(&tmp, version) => {



            let keep_cached = companion_candidate_ok(&dest, version)
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



fn jar_mc_constraint(p: &Path) -> Option<String> {
    let file = std::fs::File::open(p).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut entry = archive.by_name("fabric.mod.json").ok()?;
    let mut text = String::new();
    use std::io::Read;
    entry.read_to_string(&mut text).ok()?;
    let v: serde_json::Value = serde_json::from_str(&text).ok()?;
    v.get("depends")?.get("minecraft")?.as_str().map(|s| s.to_string())
}

fn mc_constraint_supports(constraint: &str, version: &str) -> bool {
    if version.starts_with("1.21.") {
        return constraint.contains("1.21");
    }
    if version.starts_with("26.") {
        return constraint.contains("26");
    }
    constraint.contains(version)
}

pub(crate) fn jar_supports_mc(p: &Path, version: &str) -> bool {
    let constraint = match jar_mc_constraint(p) {
        Some(c) => c,
        None => return true,
    };
    mc_constraint_supports(&constraint, version)
}

/// Liest die MC-Version einer eingebetteten Bundle-Payload (.bin ist selbst ein Jar).
fn bin_mc_constraint(jar: &Path, bin_path: &str) -> Option<String> {
    use std::io::Read;
    let file = std::fs::File::open(jar).ok()?;
    let mut archive = zip::ZipArchive::new(file).ok()?;
    let mut entry = archive.by_name(bin_path).ok()?;
    let mut bytes = Vec::new();
    entry.read_to_end(&mut bytes).ok()?;
    let cursor = std::io::Cursor::new(bytes);
    let mut inner = zip::ZipArchive::new(cursor).ok()?;
    let mut fmj = String::new();
    inner
        .by_name("fabric.mod.json")
        .ok()?
        .read_to_string(&mut fmj)
        .ok()?;
    let v: serde_json::Value = serde_json::from_str(&fmj).ok()?;
    v.get("depends")?
        .get("minecraft")?
        .as_str()
        .map(str::to_string)
}

/// Erkennt Companion-Jars, deren Code nie durch Loom geremapped wurde
/// (Mojang-Namen im Klartext statt Intermediary-`class_`-Namen).
/// Solche Jars crashen zur Laufzeit garantiert mit NoClassDefFoundError –
/// sie dürfen weder deployed noch als Quelle verwendet werden.
/// Gibt nur bei positivem Befund `true` zurück (Mod-Code mit Mojang-Refs
/// aber ohne einzige Intermediary-Ref); unbekannte Inhalte gelten als OK.
fn jar_code_is_unmapped(jar: &Path) -> bool {
    use std::io::Read;
    let file = match std::fs::File::open(jar) {
        Ok(f) => f,
        Err(_) => return false,
    };
    let mut archive = match zip::ZipArchive::new(file) {
        Ok(a) => a,
        Err(_) => return false,
    };
    const NEEDLE: &[u8] = b"net/minecraft/";
    let mut mapped = 0u32;
    let mut unmapped = 0u32;
    let mut checked = 0u32;
    for i in 0..archive.len() {
        let bytes = match archive.by_index(i) {
            Ok(mut entry) => {
                let name = entry.name().to_string();
                if !name.starts_with("dev/kollegen/") || !name.ends_with(".class") {
                    continue;
                }
                let mut bytes = Vec::new();
                if entry.read_to_end(&mut bytes).is_err() {
                    continue;
                }
                bytes
            }
            Err(_) => continue,
        };
        for (idx, w) in bytes.windows(NEEDLE.len()).enumerate() {
            if w == NEEDLE {
                if bytes[idx + NEEDLE.len()..].starts_with(b"class_") {
                    mapped += 1;
                    if mapped > 0 {
                        return false;
                    }
                } else {
                    unmapped += 1;
                }
            }
        }
        checked += 1;
        if checked >= 40 {
            break;
        }
    }
    unmapped > 0 && mapped == 0
}

/// Laufzeit-Namensschema: Seit MC 26 liefert Mojang unobfuskierte Jars
/// (keine client_mappings, kein Yarn, Klarnamen im Jar, Loader meldet
/// "Mappings not present!"). Dort ist ungemappter Mojang-Code KORREKT.
/// Ältere Versionen (1.21.x) laufen unter Intermediary-Namen und brauchen
/// Loom-geremappte Jars – ungemappte crashen dort garantiert.
fn mc_runtime_uses_mojang_names(version: &str) -> bool {
    version.starts_with("26.")
}

/// Vollvalidierung eines Companion-Kandidaten für eine MC-Version:
/// gültiges Jar + MC-Constraint + .bin-Payloads + (wo nötig) Remapping.
fn companion_candidate_ok(p: &Path, version: &str) -> bool {
    is_valid_jar(p)
        && jar_supports_mc(p, version)
        && companion_bins_support_mc(p, version)
        && (mc_runtime_uses_mojang_names(version) || !jar_code_is_unmapped(p))
}

/// Prüft zusätzlich die eingebetteten Bundle-Payloads: Ein Jar mit umgeschriebener
/// fabric.mod.json (relaxed) fällt durch reine Metadaten-Checks – die .bins
/// verraten die wahre MC-Version. Fehlende/ungültige Bins gelten als OK (lenient).
pub(crate) fn companion_bins_support_mc(jar: &Path, version: &str) -> bool {
    for bin in [
        "dev/kollegen/client/chatheads.bin",
        "dev/kollegen/client/silk.bin",
    ] {
        if let Some(c) = bin_mc_constraint(jar, bin) {
            if !mc_constraint_supports(&c, version) {
                return false;
            }
        }
    }
    true
}

pub fn companion_jar(data_dir: &Path, version: &str) -> Option<PathBuf> {
    let filename = companion_filename_for_version(version);
    
    let cached = cache_dir(data_dir).join(filename);
    // Hinweis: Dateiname allein genügt nicht – eine veraltete/wrong-version
    // Datei im Cache (z. B. 26.2-Build unter 1.21.11-Name) würde sonst falsche
    // Bundles in die Instanz extrahieren. Es zählen auch die .bin-Payloads,
    // weil ein relaxed Jar falsche Metadaten tragen kann.
    if companion_candidate_ok(&cached, version) {
        return Some(cached);
    }

    
    if let Ok(exe) = std::env::current_exe() {
        if let Some(dir) = exe.parent() {
            // Ressourcen liegen je nach Paketformat woanders (alle OS abgedeckt):
            // Windows NSIS / AppImage / Flatpak: neben der Binary (resources/),
            // macOS .app-Bundle: Contents/Resources,
            // Linux deb/rpm: /usr/lib/<Produkt>/resources.
            for cand in [
                dir.join("resources").join(filename),
                dir.join(filename),
                dir.join("../Resources").join(filename),
                dir.join("../lib/Kollegen Client/resources").join(filename),
                dir.join("../lib/kollegen-client/resources").join(filename),
            ] {
                if companion_candidate_ok(&cand, version) {
                    return Some(cand);
                }
            }
            for cand in [
                dir.join("resources").join(COMPANION_MOD_FILENAME),
                dir.join(COMPANION_MOD_FILENAME),
                dir.join("../Resources").join(COMPANION_MOD_FILENAME),
                dir.join("../lib/Kollegen Client/resources").join(COMPANION_MOD_FILENAME),
                dir.join("../lib/kollegen-client/resources").join(COMPANION_MOD_FILENAME),
            ] {
                if companion_candidate_ok(&cand, version) {
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
                .map(|n| if n == filename { 0 } else if n == COMPANION_MOD_FILENAME { 1 } else { 2 })
                .unwrap_or(3)
        });
        if let Some(p) = candidates
            .into_iter()
            .find(|p| {
                p.file_name()
                    .and_then(|n| n.to_str())
                    .map(|n| is_companion_mod_name(n))
                    .unwrap_or(false)
            })
            .filter(|p| companion_candidate_ok(p, version))
        {
            return Some(p);
        }
    }

    for cand in [
        manifest.join("resources").join(filename),
        manifest.join("resources").join(COMPANION_MOD_FILENAME),
    ] {
        if companion_candidate_ok(&cand, version) {
            return Some(cand);
        }
    }


    try_download(data_dir, version)
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
    is_version_supported(version)
}











pub fn bundles_compatible(version: &str) -> bool {
    is_version_supported(version)
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

    // DAU-sicher: kaputte Altlasten entfernen. Eine Companion ohne
    // Loom-Remapping crasht garantiert (NoClassDefFoundError) – sie darf
    // weder deployed bleiben noch je wieder als Quelle dienen.
    // Es werden ausschließlich eigene kollegen-client*.jar-Dateien angefasst.
    {
        let mods_dir = crate::utils::instance_dir(data_dir, instance_name).join("mods");
        if let Ok(entries) = std::fs::read_dir(&mods_dir) {
            for e in entries.flatten() {
                let p = e.path();
                if !p.is_file() {
                    continue;
                }
                let name = e.file_name().to_string_lossy().to_lowercase();
                if is_companion_mod_name(&name) && !companion_candidate_ok(&p, version) {
                    warn!(
                        "Entferne unpassende Companion-Altlast für MC {}: {}",
                        version, name
                    );
                    let _ = std::fs::remove_file(&p);
                }
            }
        }
        let stale_cache = cache_dir(data_dir).join(COMPANION_MOD_FILENAME);
        if stale_cache.is_file() && !companion_candidate_ok(&stale_cache, version) {
            warn!("Entferne unpassende Companion-Cache-Leiche.");
            let _ = std::fs::remove_file(&stale_cache);
        }
    }
    
    
    
    
    
    
    
    
    
    
    
    if !bundles_compatible(version) {
        warn!(
            "Kollegen-Client-Mod bei MC {v} übersprungen: die Mod (Mixins) ist nur mit Minecraft {t} kompatibel. \
             Eine Installation auf {v} würde beim Start mit einer Mixin-'Critical injection failure' abstürzen. \
             Bitte eine Instanz mit einer unterstützten Version nutzen (die Integrations-Bundles folgen derselben Regel).",
            v = version,
            t = companion_target_versions().join(", ")
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

    
    
    let _ = refresh_cache(data_dir, version);

    let source = match companion_jar(data_dir, version) {
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

    let target = mods_dir.join(companion_filename_for_version(version));

    
    
    
    
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