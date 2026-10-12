use crate::error::CommandError;
use crate::utils::java_detector::{
    detect_java_installations, find_best_java_for_minecraft, get_java_info, invalidate_java_cache,
    JavaInstallation,
};
use log::info;
use std::path::PathBuf;

/// Detects all Java installations on the system
#[tauri::command]
pub async fn detect_java_installations_command() -> Result<Vec<JavaInstallation>, CommandError> {
    info!("Command: Detecting Java installations");
    Ok(detect_java_installations().await?)
}

/// Gets information about a Java installation at the given path
#[tauri::command]
pub async fn get_java_info_command(path: String) -> Result<JavaInstallation, CommandError> {
    info!("Command: Getting Java info for path: {}", path);
    let java_path = PathBuf::from(path);
    Ok(get_java_info(&java_path).await?)
}

/// Finds the best Java installation for the given Minecraft version
#[tauri::command]
pub async fn find_best_java_for_minecraft_command(
    minecraft_version: String,
) -> Result<Option<JavaInstallation>, CommandError> {
    info!(
        "Command: Finding best Java for Minecraft version: {}",
        minecraft_version
    );
    Ok(find_best_java_for_minecraft(&minecraft_version).await?)
}

/// Invalidates the Java installation cache, forcing a fresh scan on the next query
#[tauri::command]
pub async fn invalidate_java_cache_command() -> Result<(), CommandError> {
    info!("Command: Invalidating Java cache");
    invalidate_java_cache().await;
    Ok(())
}

/// Checks if a custom Java path exists and is valid
#[tauri::command]
pub async fn validate_java_path_command(path: String) -> Result<bool, CommandError> {
    info!("Command: Validating Java path: {}", path);
    let java_path = PathBuf::from(path);

    // First check if the path exists
    if !java_path.exists() {
        info!("Java path does not exist: {}", java_path.display());
        return Ok(false);
    }

    // Then try to get info about it (will run java -version)
    match get_java_info(&java_path).await {
        Ok(_) => {
            info!("Java path is valid: {}", java_path.display());
            Ok(true)
        }
        Err(e) => {
            info!(
                "Java path is invalid: {} (Error: {})",
                java_path.display(),
                e
            );
            Ok(false)
        }
    }
}
