// Kollegen Client companion mod injection.
//
// The launcher ships a small Fabric mod ("Kollegen Client Mod") that drives the
// in-game Discord rich presence, the Right-Shift mod menu and the join-secret
// flow. It is injected into every (Fabric/Quilt) instance and is hidden +
// protected inside the mod browser (see `modrinth::list_content` /
// `delete_content`), so users cannot accidentally remove it.
//
// The jar is resolved in this order:
//   1. `<data_dir>/companion/kollegen-client-mod.jar` (downloaded cache)
//   2. Bundled resources next to the executable (`resources/…`) – set via
//      `bundle.resources` in tauri.conf.json
//   3. A locally built jar (`kollegen-mod/build/libs/…`) during development
//   4. The latest GitHub release asset `kollegen-client-mod.jar` (fallback)

use log::{info, warn};
use std::io::Write;
use std::path::{Path, PathBuf};

/// File name the companion mod is copied to inside an instance's `mods/`.
pub const COMPANION_MOD_FILENAME: &str = "kollegen-client-mod.jar";
/// Name prefix used by the mod's build outputs (`kollegen-client-mod-<version>.jar`).
pub const COMPANION_MOD_PREFIX: &str = "kollegen-client";

/// Minecraft version the published companion-mod jar is *built* against
/// (`minecraftVersion` in `kollegen-mod/build.gradle`). The mod's classes are
/// remapped to this version's intermediary, so the jar can only actually load
/// on the same `major.minor` line. Installing it onto a different line (e.g.
/// `26.2`) makes the loader crash with cryptic `class_xxxx`
/// `NoClassDefFoundError`s, so we refuse those versions explicitly instead of
/// silently relaxing the metadata constraint.
pub const COMPANION_TARGET_MC_VERSION: &str = "1.21.11";

const GITHUB_DOWNLOAD_URL: &str =
    "https://github.com/FluffyBento/KollegenClient/releases/latest/download/kollegen-client-mod.jar";

/// True when `filename` belongs to the built-in Kollegen Client mod, so the mod
/// browser can hide it and refuse to delete it.
pub fn is_companion_mod_name(filename: &str) -> bool {
    let lc = filename.to_ascii_lowercase();
    lc == COMPANION_MOD_FILENAME
        || (lc.starts_with(COMPANION_MOD_PREFIX) && lc.ends_with(".jar"))
}

/// True when the file exists, is non-empty and looks like a real zip/jar
/// (jars start with `PK`).
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
/// Aktualisiert den Cache mit der neuesten Companion-Mod aus dem GitHub-Release
/// (überschreibt die bisherige Cache-Datei). Best-effort: schlägt der Download
/// fehl (offline), bleibt der bestehende Cache erhalten. Wird vor jeder
/// Installation aufgerufen, damit sich die Mod automatisch aktualisiert.
fn refresh_cache(data_dir: &Path) -> Option<PathBuf> {
    let dir = cache_dir(data_dir);
    let _ = std::fs::create_dir_all(&dir);
    let dest = dir.join(COMPANION_MOD_FILENAME);
    let tmp = dir.join("kollegen-client-mod.jar.tmp");
    match crate::utils::download_file(GITHUB_DOWNLOAD_URL, &tmp) {
        Ok(()) if is_valid_jar(&tmp) => {
            let _ = std::fs::rename(&tmp, &dest);
            Some(dest)
        }
        _ => {
            let _ = std::fs::remove_file(&tmp);
            warn!("Kollegen-Mod-Update fehlgeschlagen – bestehende Version wird genutzt.");
            None
        }
    }
}

/// Locates a usable companion-mod jar, downloading it on demand. Returns
/// `None` when no jar could be found/obtained (offline + not bundled).
pub fn companion_jar(data_dir: &Path) -> Option<PathBuf> {
    // 1) Freshly downloaded / previously cached copy.
    let cached = cache_dir(data_dir).join(COMPANION_MOD_FILENAME);
    if is_valid_jar(&cached) {
        return Some(cached);
    }

    // 2) Bundled resources next to the executable (packaged builds).
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

    // 3) Dev build: a locally built jar inside `kollegen-mod/build/libs`.
    let manifest = Path::new(env!("CARGO_MANIFEST_DIR"));
    let libs = manifest.join("kollegen-mod").join("build").join("libs");
    if let Ok(entries) = std::fs::read_dir(&libs) {
        let mut candidates: Vec<PathBuf> = entries
            .flatten()
            .map(|e| e.path())
            .filter(|p| p.extension().and_then(|x| x.to_str()) == Some("jar"))
            .collect();
        // Prefer the stable name over versioned build outputs.
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

    // 4) Network fallback (also seeds the cache for next time).
    try_download(data_dir)
}

/// The published companion-mod jar pins `minecraft >= 1.21.11`. To let it load
/// on *any* 1.21.x instance we relax that constraint to `>= 1.21` by rewriting
/// the jar's `fabric.mod.json` on the fly (the mod's code is compatible across
/// the whole 1.21 line). Returns a path to a patched copy; on any failure the
/// original jar path is returned so installation still proceeds.
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
                // Copy every other entry verbatim.
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
    // Fallback: install the original jar unchanged.
    jar.to_path_buf()
}

/// Returns the `major.minor` of a Minecraft version string, if parseable.
fn version_major_minor(version: &str) -> Option<(u32, u32)> {
    let mut parts = version.split('.');
    let major = parts.next().and_then(|s| s.parse::<u32>().ok())?;
    let minor = parts.next().and_then(|s| s.parse::<u32>().ok())?;
    Some((major, minor))
}

/// True when the instance's Minecraft version is on the same `major.minor`
/// line as the companion mod's build target. The mod is remapped to
/// `COMPANION_TARGET_MC_VERSION`'s intermediary, so it can only load on that
/// line (e.g. any `1.21.x`); other lines (e.g. `26.2`) crash at runtime with
/// `NoClassDefFoundError`. We therefore refuse them before injecting anything.
fn is_compatible_version(version: &str) -> bool {
    match (
        version_major_minor(version),
        version_major_minor(COMPANION_TARGET_MC_VERSION),
    ) {
        (Some(a), Some(b)) => a == b,
        _ => false,
    }
}

/// Injects the companion mod into an instance's `mods/` folder. The file name
/// is the fixed `kollegen-client-mod.jar` so `list_content`/`delete_content`
/// can hide/protect it. Skipped for vanilla servers (nothing would load it)
/// and for loaders Fabric cannot run on (Forge/NeoForge). Idempotent.
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
    // The companion mod is built against a single Minecraft major.minor line
    // (see `COMPANION_TARGET_MC_VERSION`). Installing it onto a different line
    // would make the loader crash with cryptic class-not-found errors, so we
    // refuse early with a clear message instead of relaxing the constraint.
    if !is_compatible_version(version) {
        let line = version_major_minor(COMPANION_TARGET_MC_VERSION)
            .map(|(m, n)| format!("{}.{}", m, n))
            .unwrap_or_else(|| COMPANION_TARGET_MC_VERSION.to_string());
        warn!(
            "Kollegen-Client-Mod bei MC {v} übersprungen: die Mod ist nur mit Minecraft {t} ({line}.x) kompatibel. \
             Eine Installation auf {v} würde beim Start mit NoClassDefFoundError abstürzen. \
             Bitte eine Instanz mit {t} (oder einer anderen {line}.x-Version) nutzen.",
            v = version,
            t = COMPANION_TARGET_MC_VERSION,
            line = line
        );
        return;
    }

    // Auto-Update: Cache immer mit der neuesten Mod-Version vom Release
    // versorgen, bevor wir installieren (best-effort, offline = alter Cache).
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
    // Relax the `minecraft` version constraint so the mod also loads on
    // 1.21.0–1.21.10 (the published jar requires >= 1.21.11).
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

    // Immer neu injizieren: der Cache hält bereits die neueste Mod-Version
    // (refresh_cache lädt bei jedem Start releases/latest), und ein erneutes
    // Kopieren stellt sicher, dass in keiner Instanz eine veraltete Mod
    // hängen bleibt – auch wenn ein früheres Update übersprungen wurde.
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