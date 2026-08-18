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
use std::path::{Path, PathBuf};

/// File name the companion mod is copied to inside an instance's `mods/`.
pub const COMPANION_MOD_FILENAME: &str = "kollegen-client-mod.jar";
/// Name prefix used by the mod's build outputs (`kollegen-client-mod-<version>.jar`).
pub const COMPANION_MOD_PREFIX: &str = "kollegen-client";

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

    let jar = match companion_jar(data_dir) {
        Some(j) => j,
        None => {
            warn!(
                "Kollegen-Client-Mod konnte nicht gefunden werden – Instanz '{}' ohne Companion-Mod.",
                instance_name
            );
            return;
        }
    };

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
    let up_to_date = target.exists()
        && std::fs::metadata(&target)
            .and_then(|m| std::fs::metadata(&jar).map(|j| m.len() == j.len()))
            .unwrap_or(false);
    if up_to_date {
        return;
    }

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