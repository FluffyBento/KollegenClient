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
                vec![config.join("modrinth").join("instances")],
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
            if let Ok(entries) = fs::read_dir(root) {
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
    fs::create_dir_all(&dest)?;
    copy_dir_contents(&src, &dest)?;

    // Prism/MultiMC keep the real game files in a `.minecraft` subfolder; lift
    // them to the instance root so our launcher layout matches.
    let mc = dest.join(".minecraft");
    if mc.is_dir() {
        if let Ok(entries) = fs::read_dir(&mc) {
            for e in entries.flatten() {
                let target = dest.join(e.file_name());
                let _ = fs::rename(e.path(), &target);
            }
        }
        let _ = fs::remove_dir_all(&mc);
    }

    let (_, version, loader) = parse_instance(kind, &src);
    let inst = Instance {
        name: dest_name.clone(),
        version,
        loader,
        loader_version: None,
        description: format!("Importiert von {}", launcher_id),
        mods: vec!["essentialmod.jar".to_string()],
        vulkan_enabled: true,
        memory_min: crate::DEFAULT_MEMORY_MIN.to_string(),
        memory_max: crate::DEFAULT_MEMORY_MAX.to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played: None,
        java_args: None,
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
                fs::create_dir_all(&target)?;
                copy_dir_contents(&p, &target)?;
            } else {
                if let Some(parent) = target.parent() {
                    fs::create_dir_all(parent)?;
                }
                fs::copy(&p, &target)?;
            }
        }
    }
    Ok(())
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
    if let Some(txt) = read_text(&dir.join("instance.cfg")) {
        for line in txt.lines() {
            let line = line.trim();
            if let Some((k, v)) = line.split_once('=') {
                match k {
                    "name" => name = v.to_string(),
                    "IntendedVersion" => version = v.to_string(),
                    "ForgeVersion" => loader = "forge".to_string(),
                    "FabricVersion" => loader = "fabric".to_string(),
                    "NeoForgeVersion" => loader = "neoforge".to_string(),
                    "QuiltVersion" => loader = "quilt".to_string(),
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
    if let Some(txt) = read_text(&dir.join("instance.json")) {
        if let Ok(v) = serde_json::from_str::<Value>(&txt) {
            if let Some(n) = v.get("name").and_then(|x| x.as_str()) {
                name = n.to_string();
            }
            if let Some(n) = v.get("game_version").and_then(|x| x.as_str()) {
                version = n.to_string();
            }
            if let Some(n) = v.get("loader").and_then(|x| x.as_str()) {
                loader = match n {
                    "fabric" | "forge" | "neoforge" | "quilt" => n.to_string(),
                    _ => "vanilla".to_string(),
                };
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
