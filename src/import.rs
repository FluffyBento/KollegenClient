// Detection of other Minecraft launchers and import of their instances.
//
// Works on both Windows and Linux by probing each launcher's well-known
// instance directory. Only launchers whose instance directory actually exists
// are reported, so the UI never shows empty/ghost entries.

use anyhow::{anyhow, Result};
use serde_json::Value;
use std::fs;
use std::path::{Path, PathBuf};

use crate::types::Instance;
use crate::utils;

#[derive(Clone, Copy)]
enum LauncherKind {
    Prism,
    MultiMC,
    ATLauncher,
    Modrinth,
    GDLauncher,
    CurseForge,
    Technic,
}

fn home() -> PathBuf {
    dirs::home_dir().unwrap_or_else(|| PathBuf::from("."))
}

/// Returns every known launcher together with the candidate instance-root
/// directories for the current OS. `detect_launchers` only reports those whose
/// directory exists.
fn launcher_defs() -> Vec<(&'static str, &'static str, LauncherKind, Vec<PathBuf>)> {
    let h = home();
    let data_local = dirs::data_local_dir().unwrap_or_else(|| h.clone());
    let config = dirs::config_dir().unwrap_or_else(|| h.clone());
    let flatpak = h.join(".var").join("app");

    if cfg!(target_os = "windows") {
        vec![
            (
                "prism",
                "Prism Launcher",
                LauncherKind::Prism,
                vec![config.join("PrismLauncher").join("instances")],
            ),
            (
                "multimc",
                "MultiMC",
                LauncherKind::MultiMC,
                vec![config.join("MultiMC").join("instances")],
            ),
            (
                "atlauncher",
                "ATLauncher",
                LauncherKind::ATLauncher,
                vec![
                    config.join("ATLauncher").join("instances"),
                    h.join("ATLauncher").join("instances"),
                ],
            ),
            (
                "modrinth",
                "Modrinth App",
                LauncherKind::Modrinth,
                vec![
                    config.join("modrinth").join("instances"),
                    config.join("com.modrinth.theseus").join("instances"),
                    data_local.join("modrinth").join("instances"),
                    data_local.join("Modrinth").join("instances"),
                    h.join("Modrinth").join("instances"),
                ],
            ),
            (
                "gdlauncher",
                "GDLauncher",
                LauncherKind::GDLauncher,
                vec![
                    config.join("gdlauncher_next").join("instances"),
                    data_local.join("gdlauncher").join("instances"),
                ],
            ),
            (
                "curseforge",
                "CurseForge",
                LauncherKind::CurseForge,
                vec![config.join("CurseForge").join("Minecraft").join("Instances")],
            ),
            (
                "technic",
                "Technic Launcher",
                LauncherKind::Technic,
                vec![
                    config.join(".technic").join("modpacks"),
                    h.join("TechnicLauncher").join("modpacks"),
                ],
            ),
        ]
    } else {
        vec![
            (
                "prism",
                "Prism Launcher",
                LauncherKind::Prism,
                vec![
                    data_local.join("PrismLauncher").join("instances"),
                    flatpak
                        .join("org.prismlauncher.PrismLauncher")
                        .join("data")
                        .join("PrismLauncher")
                        .join("instances"),
                ],
            ),
            (
                "multimc",
                "MultiMC",
                LauncherKind::MultiMC,
                vec![
                    data_local.join("MultiMC").join("instances"),
                    flatpak
                        .join("org.multimc.MultiMC")
                        .join("data")
                        .join("MultiMC")
                        .join("instances"),
                ],
            ),
            (
                "atlauncher",
                "ATLauncher",
                LauncherKind::ATLauncher,
                vec![
                    data_local.join("ATLauncher").join("instances"),
                    h.join("ATLauncher").join("instances"),
                    flatpak
                        .join("com.atlauncher.ATLauncher")
                        .join("data")
                        .join("atlauncher")
                        .join("instances"),
                ],
            ),
            (
                "modrinth",
                "Modrinth App",
                LauncherKind::Modrinth,
                vec![
                    data_local.join("modrinth").join("instances"),
                    h.join("Modrinth").join("instances"),
                    config.join("com.modrinth.theseus").join("instances"),
                    data_local.join("com.modrinth.theseus").join("instances"),
                ],
            ),
            (
                "gdlauncher",
                "GDLauncher",
                LauncherKind::GDLauncher,
                vec![
                    data_local.join("gdlauncher_next").join("instances"),
                    data_local.join("gdlauncher").join("instances"),
                    flatpak
                        .join("io.gdl.Launcher")
                        .join("data")
                        .join("instances"),
                ],
            ),
            (
                "curseforge",
                "CurseForge",
                LauncherKind::CurseForge,
                vec![
                    h.join("Games").join("CurseForge").join("Minecraft").join("Instances"),
                    data_local
                        .join("CurseForge")
                        .join("Minecraft")
                        .join("Instances"),
                ],
            ),
            (
                "technic",
                "Technic Launcher",
                LauncherKind::Technic,
                vec![
                    h.join(".technic").join("modpacks"),
                    h.join("TechnicLauncher").join("modpacks"),
                ],
            ),
        ]
    }
}

fn read_text(p: &Path) -> Option<String> {
    fs::read_to_string(p).ok()
}

/// Lists launchers whose instance directory actually exists on this machine.
pub fn detect_launchers() -> Vec<Value> {
    let mut out = Vec::new();
    for (id, name, _kind, roots) in launcher_defs() {
        if let Some(root) = roots.iter().find(|p| p.is_dir()) {
            out.push(serde_json::json!({
                "id": id,
                "name": name,
                "path": root.to_string_lossy().to_string(),
            }));
        }
    }
    out
}

/// Lists the instances found inside a detected launcher's instance directory.
pub fn list_launcher_instances(launcher_id: &str) -> Vec<Value> {
    let mut out = Vec::new();
    for (id, _name, kind, roots) in launcher_defs() {
        if id != launcher_id {
            continue;
        }
        if let Some(root) = roots.iter().find(|p| p.is_dir()) {
            if let Ok(entries) = fs::read_dir(root.as_path()) {
                for e in entries.flatten() {
                    let p = e.path();
                    if !p.is_dir() {
                        continue;
                    }
                    let dir_name = e.file_name().to_string_lossy().to_string();
                    let (name, version, loader) = parse_instance(kind, &p);
                    out.push(serde_json::json!({
                        "name": name,
                        "dir_name": dir_name,
                        "version": version,
                        "loader": loader,
                        "path": p.to_string_lossy().to_string(),
                    }));
                }
            }
        }
    }
    out
}

/// Imports a single instance from another launcher into the Kollegen Client
/// instance store: copies the instance files, normalizes a nested `.minecraft`
/// folder to the instance root, and registers it in `instances.json`.
pub fn import_instance(
    data_dir: &Path,
    launcher_id: &str,
    instance_name: &str,
) -> Result<Instance> {
    let mut src: Option<PathBuf> = None;
    let mut kind: Option<LauncherKind> = None;
    for (id, _name, k, roots) in launcher_defs() {
        if id != launcher_id {
            continue;
        }
        if let Some(root) = roots.iter().find(|p| p.is_dir()) {
            let cand = root.join(instance_name);
            if cand.is_dir() {
                src = Some(cand);
                kind = Some(k);
            }
        }
    }
    let src = src.ok_or_else(|| anyhow!("Instanz nicht gefunden: {}", instance_name))?;
    let kind = kind.unwrap_or(LauncherKind::Prism);

    // Pick a destination name that does not collide with an existing instance.
    let mut dest_name = utils::sanitize_name(instance_name);
    let inst_file = utils::instances_file(data_dir);
    let mut instances = utils::load_json::<Vec<Instance>>(&inst_file, vec![]);
    let base = dest_name.clone();
    let mut n = 2;
    while instances.iter().any(|i| i.name == dest_name) {
        dest_name = format!("{} ({})", base, n);
        n += 1;
    }

    let dest = utils::instance_dir(data_dir, &dest_name);
    fs::create_dir_all(dest.as_path())?;
    copy_dir_contents(&src, &dest)?;

    // Lift the source launcher's game directory to the instance root so our
    // launcher layout matches. Prism/MultiMC use `.minecraft`, GDLauncher/
    // Technic/ATLauncher use `minecraft`, and instances may override the path via
    // Prism's instance.cfg `GameDirectory`. Without this, mods/saves/resource
    // packs/options end up in a subfolder that Minecraft (gameDir = instance
    // root) never reads – so the whole instance appears "empty" after import.
    if let Some(gd) = detect_game_dir(&dest, kind) {
        merge_dir_to_root(&gd, &dest);
        let _ = fs::remove_dir_all(&gd);
    }

    let (_, version, loader) = parse_instance(kind, &src);
    let (java_args, mem_min, mem_max) = parse_import_settings(kind, &src);
    let inst = Instance {
        id: uuid::Uuid::new_v4().to_string(),
        name: dest_name.clone(),
        version,
        loader,
        loader_version: None,
        description: format!("Importiert von {}", launcher_id),
        mods: vec!["essentialmod.jar".to_string()],
        vulkan_enabled: true,
        memory_min: mem_min.unwrap_or_else(|| crate::DEFAULT_MEMORY_MIN.to_string()),
        memory_max: mem_max.unwrap_or_else(|| crate::DEFAULT_MEMORY_MAX.to_string()),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played: None,
        java_args,
        server: None,
    };
    instances.push(inst.clone());
    utils::save_json(&inst_file, &instances)?;

    // Make sure the Essential mod is present for modded instances (best effort).
    if !inst.loader.eq_ignore_ascii_case("vanilla") {
        let _ = crate::utils::ensure_essential(&dest_name, data_dir);
    }

    Ok(inst)
}

fn copy_dir_contents(src: &Path, dst: &Path) -> Result<()> {
    if let Ok(entries) = fs::read_dir(src) {
        for e in entries.flatten() {
            let p = e.path();
            let target = dst.join(e.file_name());
            if p.is_dir() {
                fs::create_dir_all(target.as_path())?;
                copy_dir_contents(&p, &target)?;
            } else {
                if let Some(parent) = target.parent() {
                    fs::create_dir_all(parent)?;
                }
                fs::copy(p.as_path(), target.as_path())?;
            }
        }
    }
    Ok(())
}

/// Finds the source launcher's game directory inside the copied instance.
/// Prism/MultiMC can override it via `instance.cfg` `GameDirectory` (relative or
/// absolute); otherwise we fall back to the conventional `.minecraft` / `minecraft`
/// folder names. Returns `None` when the game files already live at the instance
/// root (e.g. `GameDirectory=.`).
fn detect_game_dir(dest: &Path, kind: LauncherKind) -> Option<PathBuf> {
    if matches!(kind, LauncherKind::Prism | LauncherKind::MultiMC) {
        if let Some(txt) = read_text(&dest.join("instance.cfg")) {
            for line in txt.lines() {
                let line = line.trim();
                if let Some(v) = line.strip_prefix("GameDirectory=") {
                    let v = v.trim();
                    if v.is_empty() || v == "." {
                        return None;
                    }
                    let p = if Path::new(v).is_absolute() {
                        PathBuf::from(v)
                    } else {
                        dest.join(v.trim_start_matches("./"))
                    };
                    if p.is_dir() {
                        return Some(p);
                    }
                }
            }
        }
    }
    for name in [".minecraft", "minecraft"] {
        let p = dest.join(name);
        if p.is_dir() {
            return Some(p);
        }
    }
    None
}

/// Moves the contents of `src` into `dest`, merging directories and never
/// overwriting existing files. Used to lift a nested game directory to the
/// instance root without losing anything.
fn merge_dir_to_root(src: &Path, dest: &Path) {
    if let Ok(entries) = fs::read_dir(src) {
        for e in entries.flatten() {
            let p = e.path();
            let target = dest.join(e.file_name());
            if p.is_dir() {
                fs::create_dir_all(&target).ok();
                merge_dir_to_root(&p, &target);
            } else if !target.exists() {
                let _ = fs::rename(&p, &target);
            }
        }
    }
}

/// Extracts per-instance JVM settings (extra args, min/max memory) from the
/// source launcher's config so they survive the import. Missing fields fall back
/// to the Kollegen Client defaults.
fn parse_import_settings(
    kind: LauncherKind,
    dir: &Path,
) -> (Option<String>, Option<String>, Option<String>) {
    let mb_to_str = |mb: i64| -> Option<String> {
        if mb > 0 {
            Some(format!("{}M", mb))
        } else {
            None
        }
    };
    let mut java_args = None;
    let mut mem_min = None;
    let mut mem_max = None;

    match kind {
        LauncherKind::Prism | LauncherKind::MultiMC => {
            if let Some(txt) = read_text(&dir.join("instance.cfg")) {
                for line in txt.lines() {
                    let line = line.trim();
                    if let Some(v) = line.strip_prefix("JvmArgs=") {
                        let v = v.trim();
                        if !v.is_empty() {
                            java_args = Some(v.to_string());
                        }
                    } else if let Some(v) = line.strip_prefix("Memory=") {
                        if let Ok(mb) = v.trim().parse::<i64>() {
                            if let Some(s) = mb_to_str(mb) {
                                mem_min = Some(s.clone());
                                mem_max = Some(s);
                            }
                        }
                    }
                }
            }
        }
        LauncherKind::Modrinth => {
            for file in ["instance.json", "profile.json"] {
                if let Some(txt) = read_text(&dir.join(file)) {
                    if let Ok(v) = serde_json::from_str::<Value>(&txt) {
                        if let Some(j) = v.get("java_args").and_then(|x| x.as_str()) {
                            if !j.is_empty() {
                                java_args = Some(j.to_string());
                            }
                        }
                        if let Some(m) = v.get("memory").and_then(|x| x.as_object()) {
                            if let Some(n) = m.get("min").and_then(|x| x.as_i64()) {
                                mem_min = mem_min.or(mb_to_str(n));
                            }
                            if let Some(n) = m.get("max").and_then(|x| x.as_i64()) {
                                mem_max = mem_max.or(mb_to_str(n));
                            }
                        }
                        break;
                    }
                }
            }
        }
        LauncherKind::GDLauncher => {
            if let Some(txt) = read_text(&dir.join("instance.json")) {
                if let Ok(v) = serde_json::from_str::<Value>(&txt) {
                    if let Some(j) = v.get("javaArgs").and_then(|x| x.as_str()) {
                        if !j.is_empty() {
                            java_args = Some(j.to_string());
                        }
                    }
                    if let Some(m) = v.get("memory").and_then(|x| x.as_object()) {
                        if let Some(n) = m.get("min").and_then(|x| x.as_i64()) {
                            mem_min = mem_min.or(mb_to_str(n));
                        }
                        if let Some(n) = m.get("max").and_then(|x| x.as_i64()) {
                            mem_max = mem_max.or(mb_to_str(n));
                        }
                    }
                }
            }
        }
        LauncherKind::ATLauncher => {
            if let Some(txt) = read_text(&dir.join("instance.json")) {
                if let Ok(v) = serde_json::from_str::<Value>(&txt) {
                    if let Some(j) = v.get("javaArguments").and_then(|x| x.as_str()) {
                        if !j.is_empty() {
                            java_args = Some(j.to_string());
                        }
                    }
                    if let Some(m) = v.get("memory").and_then(|x| x.as_object()) {
                        if let Some(n) = m.get("min").and_then(|x| x.as_i64()) {
                            mem_min = mem_min.or(mb_to_str(n));
                        }
                        if let Some(n) = m.get("max").and_then(|x| x.as_i64()) {
                            mem_max = mem_max.or(mb_to_str(n));
                        }
                    }
                }
            }
        }
        _ => {}
    }
    (java_args, mem_min, mem_max)
}

// ─=== Instance metadata parsing (per launcher) ===

fn parse_instance(kind: LauncherKind, dir: &Path) -> (String, String, String) {
    match kind {
        LauncherKind::Prism | LauncherKind::MultiMC => parse_cfg_instance(dir),
        LauncherKind::Modrinth => parse_modrinth_instance(dir),
        LauncherKind::GDLauncher => parse_gdlauncher_instance(dir),
        LauncherKind::ATLauncher => parse_atlauncher_instance(dir),
        LauncherKind::CurseForge => parse_curseforge_instance(dir),
        LauncherKind::Technic => parse_technic_instance(dir),
    }
}

fn dir_name_of(dir: &Path) -> String {
    dir.file_name()
        .unwrap_or_default()
        .to_string_lossy()
        .to_string()
}

fn parse_cfg_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();

    // Prefer mmc-pack.json (Prism/MultiMC) which authoritatively lists the
    // Minecraft version and the mod-loader components.
    if let Some(txt) = read_text(&dir.join("mmc-pack.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(comps) = v.get("components").and_then(|c| c.as_array()) {
                for c in comps {
                    let uid = c.get("uid").and_then(|x| x.as_str()).unwrap_or("").to_string();
                    let ver = c.get("version").and_then(|x| x.as_str()).unwrap_or("").to_string();
                    let lower = uid.to_lowercase();
                    if lower == "net.minecraft" {
                        version = ver;
                    } else if lower.contains("fabric") {
                        loader = "fabric".to_string();
                    } else if lower.contains("neoforge") || lower.contains("neoforged") {
                        loader = "neoforge".to_string();
                    } else if lower.contains("forge") {
                        loader = "forge".to_string();
                    } else if lower.contains("quilt") {
                        loader = "quilt".to_string();
                    }
                }
            }
        }
    }

    // Fall back to instance.cfg for older MultiMC layouts.
    if let Some(txt) = read_text(&dir.join("instance.cfg")) {
        for line in txt.lines() {
            let line = line.trim();
            if let Some((k, v)) = line.split_once('=') {
                match k {
                    "name" => {
                        if !v.is_empty() {
                            name = v.to_string();
                        }
                    }
                    "IntendedVersion" => {
                        if version.is_empty() && !v.is_empty() {
                            version = v.to_string();
                        }
                    }
                    "MinecraftVersion" => {
                        if version.is_empty() && !v.is_empty() {
                            version = v.to_string();
                        }
                    }
                    "ForgeVersion" => {
                        if loader == "vanilla" && !v.is_empty() {
                            loader = "forge".to_string();
                        }
                    }
                    "FabricVersion" => {
                        if loader == "vanilla" && !v.is_empty() {
                            loader = "fabric".to_string();
                        }
                    }
                    "NeoForgeVersion" => {
                        if loader == "vanilla" && !v.is_empty() {
                            loader = "neoforge".to_string();
                        }
                    }
                    "QuiltVersion" => {
                        if loader == "vanilla" && !v.is_empty() {
                            loader = "quilt".to_string();
                        }
                    }
                    "LoaderType" => {
                        if loader == "vanilla" {
                            match v {
                                "1" => loader = "forge".to_string(),
                                "3" => loader = "fabric".to_string(),
                                "4" => loader = "quilt".to_string(),
                                "2" => loader = "neoforge".to_string(),
                                _ => {}
                            }
                        }
                    }
                    _ => {}
                }
            }
        }
    }
    (name, version, loader)
}

fn parse_modrinth_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();
    // Legacy Modrinth App stores instance.json; the newer "Theseus" build uses
    // profile.json. Both expose the same fields (name, game_version, loader).
    for file in ["instance.json", "profile.json"] {
        if let Some(txt) = read_text(&dir.join(file)) {
            if let Ok(v) = serde_json::from_str::<Value>(&txt) {
                if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                    if !n.is_empty() {
                        name = n.to_string();
                    }
                }
                if let Some(n) = v.get("game_version").and_then(|x| x.as_str()) {
                    if !n.is_empty() {
                        version = n.to_string();
                    }
                }
                if let Some(n) = v.get("loader").and_then(|x| x.as_str()) {
                    loader = match n {
                        "fabric" | "forge" | "neoforge" | "quilt" => n.to_string(),
                        _ => "vanilla".to_string(),
                    };
                }
            }
        }
    }
    (name, version, loader)
}

fn parse_gdlauncher_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();
    if let Some(txt) = read_text(&dir.join("instance.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                name = n.to_string();
            }
            if let Some(n) = v.get("gameVersion").and_then(|x| x.as_str()) {
                version = n.to_string();
            }
            if let Some(n) = v.get("loader").and_then(|x| x.as_str()) {
                let l = n.to_lowercase();
                loader = if l.contains("fabric") {
                    "fabric".to_string()
                } else if l.contains("forge") {
                    "forge".to_string()
                } else if l.contains("quilt") {
                    "quilt".to_string()
                } else if l.contains("neoforge") {
                    "neoforge".to_string()
                } else {
                    "vanilla".to_string()
                };
            }
        }
    }
    (name, version, loader)
}

fn parse_atlauncher_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();
    if let Some(txt) = read_text(&dir.join("instance.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                name = n.to_string();
            }
            if let Some(n) = v.get("minecraftVersion").and_then(|x| x.as_str()) {
                version = n.to_string();
            }
            let lv = v.get("loaderVersion").and_then(|x| x.as_str()).unwrap_or("");
            let ld = v.get("loader").and_then(|x| x.as_str()).unwrap_or("");
            let combined = format!("{} {}", lv, ld).to_lowercase();
            if combined.contains("fabric") {
                loader = "fabric".to_string();
            } else if combined.contains("forge") {
                loader = "forge".to_string();
            } else if combined.contains("quilt") {
                loader = "quilt".to_string();
            } else if combined.contains("neoforge") {
                loader = "neoforge".to_string();
            }
        }
    }
    (name, version, loader)
}

fn parse_curseforge_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();
    if let Some(txt) = read_text(&dir.join("minecraftinstance.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                name = n.to_string();
            }
            if let Some(n) = v.get("gameVersion").and_then(|x| x.as_str()) {
                version = n.to_string();
            }
            if let Some(bml) = v.get("baseModLoader") {
                if let Some(n) = bml.get("name").and_then(|x| x.as_str()) {
                    let s = n.to_lowercase();
                    if s.contains("fabric") {
                        loader = "fabric".to_string();
                    } else if s.contains("forge") {
                        loader = "forge".to_string();
                    } else if s.contains("quilt") {
                        loader = "quilt".to_string();
                    } else if s.contains("neoforge") {
                        loader = "neoforge".to_string();
                    }
                }
            }
        }
    }
    (name, version, loader)
}

fn parse_technic_instance(dir: &Path) -> (String, String, String) {
    let mut name = dir_name_of(dir);
    let mut version = String::new();
    let mut loader = "vanilla".to_string();
    if let Some(txt) = read_text(&dir.join("version.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                name = n.to_string();
            }
            if let Some(n) = v.get("version").and_then(|x| x.as_str()) {
                version = n.to_string();
            }
        }
    }
    if dir.join("bin").join("modpack.jar").exists() && dir.join("bin").join("forge.jar").exists() {
        loader = "forge".to_string();
    }
    (name, version, loader)
}
