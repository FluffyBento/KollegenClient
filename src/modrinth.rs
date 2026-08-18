// Modrinth integration: search, install and manage instance content
// (mods, resource packs, shader packs).

use anyhow::{anyhow, Result};
use log::info;
use serde::Serialize;
use serde_json::Value;
use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::time::Duration;

const MODRINTH_API: &str = "https://api.modrinth.com/v2";

#[derive(Serialize)]
pub struct ModrinthProject {
    pub id: String,
    pub slug: Option<String>,
    pub title: String,
    pub description: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
    pub project_type: String,
    pub categories: Vec<String>,
}

#[derive(Serialize)]
pub struct ModrinthVersion {
    pub id: String,
    pub name: String,
    pub version_number: String,
    pub game_versions: Vec<String>,
    pub loaders: Vec<String>,
    pub date_published: String,
}

fn client() -> Result<reqwest::blocking::Client> {
    reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(60))
        .build()
        .map_err(|e| anyhow!(e.to_string()))
}

fn category_dir(kind: &str) -> Result<&'static str> {
    match kind {
        "mod" => Ok("mods"),
        "resourcepack" => Ok("resourcepacks"),
        "shader" => Ok("shaderpacks"),
        _ => Err(anyhow!("Unbekannter Inhaltstyp: {}", kind)),
    }
}

/// Searches Modrinth for projects of the given kind, compatible with the
/// instance's Minecraft version (and loader, for mods).
pub fn search(
    kind: &str,
    query: &str,
    mc_version: &str,
    loader: &str,
    offset: usize,
) -> Result<Vec<ModrinthProject>> {
    let project_type = match kind {
        "mod" => "mod",
        "resourcepack" => "resourcepack",
        "shader" => "shader",
        _ => return Err(anyhow!("Unbekannter Inhaltstyp: {}", kind)),
    };

    let mut facets: Vec<Vec<String>> = vec![vec![format!("project_type:{}", project_type)]];
    if !mc_version.is_empty() {
        facets.push(vec![format!("versions:{}", mc_version)]);
    }
    let loader_lc = loader.to_lowercase();
    if kind == "mod" && !loader_lc.is_empty() && loader_lc != "vanilla" {
        facets.push(vec![format!("categories:{}", loader_lc)]);
    }
    let facets_json = serde_json::to_string(&facets)?;

    let mut url = format!(
        "{}/search?limit=24&index=relevance&offset={}",
        MODRINTH_API, offset
    );
    if !query.is_empty() {
        url.push_str(&format!("&query={}", urlencoding::encode(query)));
    }
    url.push_str(&format!("&facets={}", urlencoding::encode(&facets_json)));

    let resp = client()?.get(&url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!("Modrinth Suche fehlgeschlagen: {}", resp.status()));
    }
    let data: Value = resp.json()?;
    let hits = data
        .get("hits")
        .and_then(|h| h.as_array())
        .cloned()
        .unwrap_or_default();

    let mut out = Vec::new();
    for h in hits {
        out.push(ModrinthProject {
            id: h
                .get("project_id")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            slug: h
                .get("slug")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            title: h
                .get("title")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            description: h
                .get("description")
                .and_then(|v| v.as_str())
                .unwrap_or("")
                .to_string(),
            icon_url: h
                .get("icon_url")
                .and_then(|v| v.as_str())
                .map(|s| s.to_string()),
            downloads: h.get("downloads").and_then(|v| v.as_u64()).unwrap_or(0),
            project_type: h
                .get("project_type")
                .and_then(|v| v.as_str())
                .unwrap_or(project_type)
                .to_string(),
            categories: h
                .get("categories")
                .and_then(|v| v.as_array())
                .map(|a| a.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect())
                .unwrap_or_default(),
        });
    }
    Ok(out)
}

/// Picks the first version compatible with the instance's Minecraft version
/// (and loader, for mods) from a project's version list.
fn pick_compatible<'a>(
    versions: &'a [Value],
    mc_version: &str,
    loader: &str,
    is_mod: bool,
) -> Option<&'a Value> {
    versions.iter().find(|v| {
        let game_versions = v
            .get("game_versions")
            .and_then(|g| g.as_array())
            .map(|a| a.iter().filter_map(|x| x.as_str()).collect::<Vec<_>>())
            .unwrap_or_default();
        let game_ok = game_versions.iter().any(|g| g.eq_ignore_ascii_case(mc_version));
        let loader_ok = !is_mod
            || {
                let loaders = v
                    .get("loaders")
                    .and_then(|l| l.as_array())
                    .map(|a| a.iter().filter_map(|x| x.as_str()).collect::<Vec<_>>())
                    .unwrap_or_default();
                loaders.iter().any(|l| l.eq_ignore_ascii_case(loader))
            };
        game_ok && loader_ok
    })
}

/// Returns the (url, filename) of a version's primary file.
fn primary_file(v: &Value) -> Option<(String, String)> {
    let files = v
        .get("files")
        .and_then(|f| f.as_array())
        .cloned()
        .unwrap_or_default();
    let file = files
        .iter()
        .find(|f| f.get("primary").and_then(|p| p.as_bool()).unwrap_or(false))
        .or_else(|| files.first())?;
    let url = file.get("url").and_then(|u| u.as_str())?.to_string();
    let filename = file
        .get("filename")
        .and_then(|u| u.as_str())?
        .to_string();
    Some((url, filename))
}

/// Lists Modrinth versions of `project_id` that are compatible with the given
/// Minecraft version (and loader, for mods). Newest first.
pub fn list_versions(project_id: &str, mc_version: &str, loader: &str) -> Result<Vec<ModrinthVersion>> {
    let url = format!("{}/project/{}/version", MODRINTH_API, project_id);
    let resp = client()?.get(&url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!(
            "Modrinth Versionen konnten nicht geladen werden: {}",
            resp.status()
        ));
    }
    let versions: Vec<Value> = resp.json()?;
    let mut out = Vec::new();
    for v in &versions {
        let game_versions = v
            .get("game_versions")
            .and_then(|g| g.as_array())
            .map(|a| a.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect::<Vec<_>>())
            .unwrap_or_default();
        let game_ok = game_versions.iter().any(|g| g.eq_ignore_ascii_case(mc_version));
        let loaders = v
            .get("loaders")
            .and_then(|l| l.as_array())
            .map(|a| a.iter().filter_map(|x| x.as_str().map(|s| s.to_string())).collect::<Vec<_>>())
            .unwrap_or_default();
        // Mods carry a loader list; resource packs / shaders have none, so the
        // loader filter only applies when the version actually declares loaders.
        let loader_ok = loaders.is_empty() || loaders.iter().any(|l| l.eq_ignore_ascii_case(loader));
        if !(game_ok && loader_ok) {
            continue;
        }
        out.push(ModrinthVersion {
            id: v.get("id").and_then(|x| x.as_str()).unwrap_or("").to_string(),
            name: v.get("name").and_then(|x| x.as_str()).unwrap_or("").to_string(),
            version_number: v
                .get("version_number")
                .and_then(|x| x.as_str())
                .unwrap_or("")
                .to_string(),
            game_versions,
            loaders,
            date_published: v
                .get("date_published")
                .and_then(|x| x.as_str())
                .unwrap_or("")
                .to_string(),
        });
    }
    out.sort_by(|a, b| b.date_published.cmp(&a.date_published));
    Ok(out)
}

/// Downloads a project (and recursively its required dependencies) into the
/// instance's content folder. `visited` prevents cycles / duplicate installs.
/// `version_id` selects a specific version; when `None` the newest compatible
/// version is installed (dependencies always use their newest compatible version).
fn install_project_recursive(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    project_id: &str,
    mc_version: &str,
    loader: &str,
    version_id: Option<&str>,
    visited: &mut std::collections::HashSet<String>,
    depth: usize,
) -> Result<()> {
    if depth > 6 || !visited.insert(project_id.to_string()) {
        return Ok(());
    }

    let target_dir =
        crate::utils::instance_dir(data_dir, instance_name).join(category_dir(kind)?);
    fs::create_dir_all(&target_dir)?;

    let url = format!("{}/project/{}/version", MODRINTH_API, project_id);
    let resp = client()?.get(&url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!(
            "Modrinth Versionen konnten nicht geladen werden: {}",
            resp.status()
        ));
    }
    let versions: Vec<Value> = resp.json()?;
    let chosen = if let Some(vid) = version_id {
        versions
            .iter()
            .find(|v| v.get("id").and_then(|x| x.as_str()) == Some(vid))
            .ok_or_else(|| anyhow!("Version {} wurde nicht gefunden", vid))?
    } else {
        pick_compatible(&versions, mc_version, loader, kind == "mod").ok_or_else(|| {
            anyhow!(
                "Keine kompatible Version für Minecraft {} / {} gefunden",
                mc_version,
                loader
            )
        })?
    };

    let (file_url, filename) = primary_file(chosen)
        .ok_or_else(|| anyhow!("Keine Datei in dieser Version gefunden"))?;
    let dest = target_dir.join(&filename);
    if !dest.exists() {
        crate::utils::download_file(&file_url, &dest)?;
        info!("Installiert: {} ({})", filename, project_id);
    }
    // Always record the install so the project is tracked as "already
    // installed" (and filtered out of search). Previously this only ran when
    // the file was freshly downloaded, so mods whose file already existed on
    // disk (older builds, re-installs) were never recorded and kept showing
    // up in the browse list after a restart.
    let _ = record_install(data_dir, instance_name, kind, &filename, project_id);

    // Recurse into required dependencies
    if let Some(deps) = chosen.get("dependencies").and_then(|d| d.as_array()) {
        for dep in deps {
            let dep_type = dep
                .get("dependency_type")
                .and_then(|t| t.as_str())
                .unwrap_or("");
            if dep_type != "required" {
                continue;
            }
            if let Some(pid) = dep.get("project_id").and_then(|p| p.as_str()) {
                if visited.contains(pid) {
                    continue;
                }
                let _ = install_project_recursive(
                    data_dir,
                    instance_name,
                    kind,
                    pid,
                    mc_version,
                    loader,
                    None,
                    visited,
                    depth + 1,
                );
            }
        }
    }
    Ok(())
}

/// Downloads the latest version of a project compatible with the instance's
/// Minecraft version (and loader, for mods) into the correct subfolder,
/// including its required dependencies.
pub fn install_content(
    instance_name: &str,
    data_dir: &Path,
    kind: &str,
    project_id: &str,
    mc_version: &str,
    loader: &str,
    version_id: Option<&str>,
) -> Result<()> {
    let mut visited = HashSet::new();
    install_project_recursive(
        data_dir,
        instance_name,
        kind,
        project_id,
        mc_version,
        loader,
        version_id,
        &mut visited,
        0,
    )
}

/// Lists installed content filenames per category for an instance.
pub fn list_content(data_dir: &Path, instance_name: &str) -> Value {
    let inst_dir = crate::utils::instance_dir(data_dir, instance_name);
    let cats = [
        ("mods", "mod"),
        ("resourcepacks", "resourcepack"),
        ("shaderpacks", "shader"),
    ];
    let mut result = serde_json::json!({});
    for (dir, key) in cats {
        let d = inst_dir.join(dir);
        let mut items = vec![];
        if let Ok(entries) = fs::read_dir(&d) {
            for e in entries.flatten() {
                if e.path().is_file() {
                    if let Some(name) = e.file_name().to_str() {
                        let name = name.to_string();
                        // Launcher-managed files (companion mod / logo pack) are
                        // hidden from the content browser.
                        if is_managed_content(key, &name) {
                            continue;
                        }
                        let mut obj = serde_json::json!({ "name": name.clone() });
                        if let Some(pid) =
                            project_id_for_file(data_dir, instance_name, key, &name)
                        {
                            obj["project_id"] = serde_json::Value::String(pid);
                        }
                        items.push(obj);
                    }
                }
            }
        }
        items.sort_by(|a, b| {
            let an = a.get("name").and_then(|v| v.as_str()).unwrap_or("");
            let bn = b.get("name").and_then(|v| v.as_str()).unwrap_or("");
            an.cmp(bn)
        });
        result[key] = serde_json::to_value(items).unwrap();
    }
    result
}

/// Removes an installed content file. Filenames are validated to prevent
/// path traversal. Launcher-managed files (Kollegen Client mod, title-logo
/// resource pack) are protected and cannot be removed here.
pub fn delete_content(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    filename: &str,
) -> Result<()> {
    if filename.contains('/') || filename.contains('\\') || filename.contains("..") {
        return Err(anyhow!("Ungültiger Dateiname"));
    }
    if is_managed_content(kind, filename) {
        return Err(anyhow!(
            "'{}' wird vom Kollegen Client automatisch verwaltet und kann nicht entfernt werden.",
            filename
        ));
    }
    let dir_name = category_dir(kind)?;
    let inst_dir = crate::utils::instance_dir(data_dir, instance_name);
    let path = inst_dir.join(dir_name).join(filename);
    if path.exists() {
        fs::remove_file(&path)?;
    }
    let _ = remove_install_record(data_dir, instance_name, kind, &filename);
    Ok(())
}

/// Changes the installed version of a Modrinth-managed project: installs the
/// requested version (downloading it if needed, including required dependencies)
/// and removes the previously installed file for the same project so only one
/// version stays installed at a time.
pub fn change_content_version(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    filename: &str,
    version_id: &str,
) -> Result<()> {
    let inst_dir = crate::utils::instance_dir(data_dir, instance_name);
    let target_dir = inst_dir.join(category_dir(kind)?);

    let snapshot = |dir: &Path| -> std::collections::HashSet<String> {
        if let Ok(entries) = fs::read_dir(dir) {
            entries
                .flatten()
                .filter(|e| e.path().is_file())
                .filter_map(|e| e.file_name().into_string().ok())
                .collect()
        } else {
            std::collections::HashSet::new()
        }
    };

    let before = snapshot(&target_dir);

    let project_id = project_id_for_file(data_dir, instance_name, kind, filename)
        .ok_or_else(|| anyhow!("Projekt-ID für '{}' nicht gefunden", filename))?;

    // A specific version_id is given, so mc_version/loader are only required by
    // the signature and are not used to pick a compatible version.
    install_content(instance_name, data_dir, kind, &project_id, "", "", Some(version_id))?;

    let after = snapshot(&target_dir);
    // A newly added file means the chosen version installed under a different
    // name than the old one – remove the previous file so only one remains.
    let added: std::collections::HashSet<String> =
        after.difference(&before).cloned().collect();
    if !added.is_empty() && !added.contains(filename) {
        let _ = delete_content(data_dir, instance_name, kind, filename);
    }
    Ok(())
}

/// Returns true when `filename` is managed by the launcher itself (the
/// injected Kollegen Client mod and the title-logo resource pack). Such files
/// are hidden from the content browser and cannot be deleted by the user.
fn is_managed_content(kind: &str, filename: &str) -> bool {
    if crate::companion::is_companion_mod_name(filename) {
        return true;
    }
    // The KollegenTitle.zip pack is reinstalled + force-enabled on every launch.
    kind == "resourcepack" && filename == "KollegenTitle.zip"
}

/// Returns the singular metadata key for a content kind.
fn meta_key(kind: &str) -> Option<&'static str> {
    match kind {
        "mod" => Some("mod"),
        "resourcepack" => Some("resourcepack"),
        "shader" => Some("shader"),
        _ => None,
    }
}

fn meta_path(data_dir: &Path, instance_name: &str) -> PathBuf {
    crate::utils::instance_dir(data_dir, instance_name)
        .join(".kollegen")
        .join("installed.json")
}

fn load_meta(data_dir: &Path, instance_name: &str) -> Value {
    fs::read_to_string(meta_path(data_dir, instance_name))
        .ok()
        .and_then(|s| serde_json::from_str(&s).ok())
        .unwrap_or_else(|| serde_json::json!({ "mod": {}, "resourcepack": {}, "shader": {} }))
}

fn save_meta(data_dir: &Path, instance_name: &str, meta: &Value) -> Result<()> {
    let p = meta_path(data_dir, instance_name);
    if let Some(parent) = p.parent() {
        fs::create_dir_all(parent)?;
    }
    fs::write(&p, serde_json::to_string_pretty(meta)?)?;
    Ok(())
}

/// Records that `filename` (belonging to `project_id`) was installed for `kind`.
fn record_install(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    filename: &str,
    project_id: &str,
) -> Result<()> {
    let key = match meta_key(kind) {
        Some(k) => k,
        None => return Ok(()),
    };
    let mut meta = load_meta(data_dir, instance_name);
    if meta.get(key).is_none() {
        meta[key] = serde_json::json!({});
    }
    if let Some(obj) = meta.get_mut(key).and_then(|v| v.as_object_mut()) {
        obj.insert(filename.to_string(), Value::String(project_id.to_string()));
    }
    save_meta(data_dir, instance_name, &meta)
}

/// Removes a recorded install entry (used when deleting content).
fn remove_install_record(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    filename: &str,
) -> Result<()> {
    let key = match meta_key(kind) {
        Some(k) => k,
        None => return Ok(()),
    };
    let mut meta = load_meta(data_dir, instance_name);
    if let Some(obj) = meta.get_mut(key).and_then(|v| v.as_object_mut()) {
        obj.remove(filename);
    }
    save_meta(data_dir, instance_name, &meta)
}

/// Returns the set of installed Modrinth project ids per content kind,
/// so the UI can hide already-installed projects from search results.
pub fn installed_project_ids(data_dir: &Path, instance_name: &str) -> Value {
    let meta = load_meta(data_dir, instance_name);
    let mut out = serde_json::json!({});
    for key in ["mod", "resourcepack", "shader"] {
        let ids: Vec<String> = meta
            .get(key)
            .and_then(|v| v.as_object())
            .map(|o| {
                o.values()
                    .filter_map(|v| v.as_str().map(|s| s.to_string()))
                    .collect()
            })
            .unwrap_or_default();
        out[key] = serde_json::to_value(ids).unwrap();
    }
    out
}

/// Returns the recorded Modrinth project id for an installed file (by content
/// kind + filename), or `None` if the file isn't tracked as a Modrinth install.
pub fn project_id_for_file(
    data_dir: &Path,
    instance_name: &str,
    kind: &str,
    filename: &str,
) -> Option<String> {
    let key = meta_key(kind)?;
    load_meta(data_dir, instance_name)
        .get(key)
        .and_then(|v| v.as_object())
        .and_then(|o| o.get(filename))
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
}

/// Fetches full project details (and gallery images) from Modrinth for the
/// in-client detail view opened via "Ansehen".
pub fn project_details(id: &str) -> Result<Value, String> {
    let client = client().map_err(|e| e.to_string())?;
    let proj_url = format!("{}/project/{}", MODRINTH_API, id);
    let proj_resp = client
        .get(&proj_url)
        .send()
        .map_err(|e| e.to_string())?;
    if !proj_resp.status().is_success() {
        return Err(format!(
            "Projekt konnte nicht geladen werden: {}",
            proj_resp.status()
        ));
    }
    let project: Value = proj_resp.json().map_err(|e| e.to_string())?;

    let gal_url = format!("{}/project/{}/gallery", MODRINTH_API, id);
    let gallery: Value = match client.get(&gal_url).send() {
        Ok(r) if r.status().is_success() => r.json().unwrap_or(serde_json::Value::Null),
        _ => serde_json::Value::Null,
    };

    Ok(serde_json::json!({ "project": project, "gallery": gallery }))
}
