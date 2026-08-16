// Prevents additional console window on Windows in release
#![cfg_attr(
    all(not(debug_assertions), target_os = "windows"),
    windows_subsystem = "windows"
)]

mod auth;
mod instance;
mod java;
mod modrinth;
mod types;
mod utils;

use anyhow::Result;
use directories::ProjectDirs;
use serde_json::Value;
use std::path::PathBuf;
use std::sync::Mutex;
use tauri::State;

// ─=== Constants ===
pub const CLIENT_ID: &str = "c36a9fb6-4f2a-41ff-90bd-ae7cc92031eb";
pub const USER_AGENT: &str = "KollegenClient/1.0 (+https://kollegen.dev)";
pub const DEFAULT_MEMORY_MIN: &str = "2G";
pub const DEFAULT_MEMORY_MAX: &str = "4G";
pub const MAX_LOG_LINES: usize = 1000;

// ─=== App State ===
pub struct AppState {
    pub instances: Mutex<Vec<types::Instance>>,
    pub accounts: Mutex<Vec<types::Account>>,
    pub data_dir: PathBuf,
    pub logs: Mutex<Vec<String>>,
}

// ─=== Tauri Commands ===

#[tauri::command]
fn get_instances(state: State<'_, AppState>) -> Result<Vec<types::Instance>, String> {
    let path = utils::instances_file(&state.data_dir);
    let instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    Ok(instances)
}

#[tauri::command]
fn get_accounts(state: State<'_, AppState>) -> Result<Vec<types::Account>, String> {
    let path = utils::accounts_file(&state.data_dir);
    let accounts = utils::load_json::<Vec<types::Account>>(&path, vec![]);
    Ok(accounts)
}

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> Result<types::Settings, String> {
    let path = utils::settings_file(&state.data_dir);
    let settings = utils::load_json::<types::Settings>(&path, types::Settings::default());
    Ok(settings)
}

#[tauri::command]
fn save_settings(
    state: State<'_, AppState>,
    settings: types::Settings,
) -> Result<(), String> {
    let path = utils::settings_file(&state.data_dir);
    utils::save_json(&path, &settings).map_err(|e| e.to_string())
}

#[tauri::command]
fn create_instance(
    state: State<'_, AppState>,
    name: String,
    version: String,
    loader: String,
    loader_version: Option<String>,
) -> Result<types::Instance, String> {
    let inst = types::Instance {
        name: name.clone(),
        version: version.clone(),
        loader,
        loader_version,
        description: String::new(),
        mods: vec!["essentialmod.jar".to_string()],
        vulkan_enabled: true,
        memory_min: DEFAULT_MEMORY_MIN.to_string(),
        memory_max: DEFAULT_MEMORY_MAX.to_string(),
        created_at: chrono::Utc::now().to_rfc3339(),
        last_played: None,
        java_args: None,
        server: None,
    };

    let dir = utils::instance_dir(&state.data_dir, &name);
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;

    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    instances.push(inst.clone());
    utils::save_json(&path, &instances).map_err(|e| e.to_string())?;

    Ok(inst)
}

#[tauri::command]
fn delete_instance(state: State<'_, AppState>, name: String) -> Result<(), String> {
    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    instances.retain(|i| i.name != name);
    utils::save_json(&path, &instances).map_err(|e| e.to_string())?;

    let dir = utils::instance_dir(&state.data_dir, &name);
    if dir.exists() {
        std::fs::remove_dir_all(&dir).map_err(|e| e.to_string())?;
    }

    Ok(())
}

#[tauri::command]
fn get_logs(state: State<'_, AppState>) -> Result<Vec<String>, String> {
    let logs = state.logs.lock().unwrap().clone();
    Ok(logs)
}

#[tauri::command]
fn get_available_versions() -> Result<Vec<String>, String> {
    instance::fetch_available_versions().map_err(|e| e.to_string())
}

#[tauri::command]
fn get_loaders_for_version(version: String) -> Result<Value, String> {
    instance::fetch_loaders_for_version(&version).map_err(|e| e.to_string())
}

#[tauri::command]
fn install_instance(
    state: State<'_, AppState>,
    name: String,
    version: String,
    loader: String,
    loader_version: Option<String>,
) -> Result<(), String> {
    let path = utils::instances_file(&state.data_dir);
    let mut instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    if let Some(inst) = instances.iter_mut().find(|i| i.name == name) {
        inst.version = version.clone();
        inst.loader = loader.clone();
        inst.loader_version = loader_version.clone();
    }
    utils::save_json(&path, &instances).map_err(|e| e.to_string())?;
    instance::install_instance(&state.data_dir, &name, &version, &loader, loader_version.as_deref())
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn launch_game(
    state: State<'_, AppState>,
    instance_name: String,
) -> Result<String, String> {
    let path = utils::instances_file(&state.data_dir);
    let instances = utils::load_json::<Vec<types::Instance>>(&path, vec![]);
    let inst = instances.iter().find(|i| i.name == instance_name)
        .ok_or("Instanz nicht gefunden")?;

    // Determine the required Java version from the instance's version JSON
    let required_java = {
        let version_dir = utils::instance_dir(&state.data_dir, &inst.name)
            .join("versions")
            .join(&inst.version);
        let vjson_path = version_dir.join(format!("{}.json", inst.version));
        if vjson_path.exists() {
            if let Ok(s) = std::fs::read_to_string(&vjson_path) {
                if let Ok(vj) = serde_json::from_str::<types::VersionJson>(&s) {
                    vj.java_version.map(|j| j.major_version).unwrap_or(17)
                } else { 17 }
            } else { 17 }
        } else { 17 }
    };

    let java_path = match java::find_java(&state.data_dir, required_java) {
        Ok(p) => p,
        Err(_) => {
            utils::append_log(&state, &format!("Java {} nicht gefunden, lade JRE herunter...", required_java));
            java::download_jre_internal(required_java).map_err(|e| e.to_string())?;
            java::find_java(&state.data_dir, required_java).map_err(|e| e.to_string())?
        }
    };

    let settings = utils::load_json::<types::Settings>(
        &utils::settings_file(&state.data_dir),
        types::Settings::default(),
    );

    let launch_result = instance::launch(&state, &state.data_dir, inst, &java_path, &settings);
    match launch_result {
        Ok(result) => Ok(result),
        Err(e) => {
            utils::append_log(&state, &format!("Launch fehlgeschlagen: {}", e));
            Err(e.to_string())
        }
    }
}

// ─=== Auth Commands ===

#[tauri::command]
fn auth_check_status() -> Result<Value, String> {
    let status = auth::get_auth_status();
    Ok(status)
}

#[tauri::command]
fn auth_start() -> Result<Value, String> {
    auth::ms_auth_start().map_err(|e| e.to_string())
}

#[tauri::command]
fn download_jre_command(version: Option<u32>) -> Result<Value, String> {
    let v = version.unwrap_or(21);
    match java::download_jre_internal(v) {
        Ok(path) => Ok(serde_json::json!({"ok": true, "path": path, "version": v})),
        Err(e) => Ok(serde_json::json!({"ok": false, "error": e.to_string()})),
    }
}

#[tauri::command]
fn modrinth_search(
    kind: String,
    query: String,
    mc_version: String,
    loader: String,
    offset: usize,
) -> Result<Vec<crate::modrinth::ModrinthProject>, String> {
    crate::modrinth::search(&kind, &query, &mc_version, &loader, offset).map_err(|e| e.to_string())
}

#[tauri::command]
fn installed_project_ids(
    state: State<'_, AppState>,
    instance_name: String,
) -> Result<Value, String> {
    Ok(crate::modrinth::installed_project_ids(
        &state.data_dir,
        &instance_name,
    ))
}

#[tauri::command]
fn modrinth_project(id: String) -> Result<Value, String> {
    crate::modrinth::project_details(&id)
}

#[tauri::command]
fn install_content(
    state: State<'_, AppState>,
    instance_name: String,
    kind: String,
    project_id: String,
    mc_version: String,
    loader: String,
) -> Result<(), String> {
    crate::modrinth::install_content(
        &instance_name,
        &state.data_dir,
        &kind,
        &project_id,
        &mc_version,
        &loader,
    )
    .map_err(|e| e.to_string())
}

#[tauri::command]
fn list_content(state: State<'_, AppState>, instance_name: String) -> Result<Value, String> {
    Ok(crate::modrinth::list_content(&state.data_dir, &instance_name))
}

#[tauri::command]
fn delete_content(
    state: State<'_, AppState>,
    instance_name: String,
    kind: String,
    filename: String,
) -> Result<(), String> {
    crate::modrinth::delete_content(&state.data_dir, &instance_name, &kind, &filename)
        .map_err(|e| e.to_string())
}

fn main() {
    // Wayland compatibility: WebKit's DMABUF renderer crashes under some
    // Wayland compositors ("Error 71 dispatching to Wayland display").
    // This is a no-op on X11, so the app works on both backends.
    if std::env::var_os("WEBKIT_DISABLE_DMABUF_RENDERER").is_none() {
        unsafe {
            std::env::set_var("WEBKIT_DISABLE_DMABUF_RENDERER", "1");
        }
    }

    // Best-effort: force WebKitGTK's GPU compositing mode where a GPU is
    // available. This dramatically improves scrolling performance in the
    // Modrinth content browser. Harmless if no GPU is present (WebKit falls
    // back to software rendering).
    if std::env::var_os("WEBKIT_FORCE_COMPOSITING_MODE").is_none() {
        unsafe {
            std::env::set_var("WEBKIT_FORCE_COMPOSITING_MODE", "1");
        }
    }

    let data_dir = match ProjectDirs::from("dev", "kollegen", "KollegenClient") {
        Some(dir) => dir.data_dir().to_path_buf(),
        None => {
            eprintln!("Could not find project directory");
            std::process::exit(1);
        }
    };

    std::fs::create_dir_all(&data_dir).expect("Could not create data directory");

    let state = AppState {
        instances: Mutex::new(vec![]),
        accounts: Mutex::new(vec![]),
        data_dir: data_dir.clone(),
        logs: Mutex::new(vec![]),
    };

    utils::init_logging(&data_dir);

    tauri::Builder::default()
        .manage(state)
        .invoke_handler(tauri::generate_handler![
            get_instances,
            get_accounts,
            get_settings,
            save_settings,
            create_instance,
            delete_instance,
            get_logs,
            get_available_versions,
            get_loaders_for_version,
            install_instance,
            launch_game,
            auth_check_status,
            auth_start,
            download_jre_command,
            modrinth_search,
            install_content,
            list_content,
            delete_content,
            installed_project_ids,
            modrinth_project,
        ])
        .run(tauri::generate_context!())
        .expect("error while running tauri application");
}



