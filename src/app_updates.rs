//! Automatic update checking + prompting using the official Tauri updater plugin.
//!
//! On startup (and then every few hours) we ask the configured update endpoint
//! for a newer version. If one exists we pop a native dialog asking the user to
//! install it; on confirmation the update is downloaded + installed and the app
//! restarts. The endpoint + signing public key live in `tauri.conf.json`.
//!
//! `check_info` / `install` are also exposed as Tauri commands so the Settings
//! UI can offer a manual "Check for updates" button with visible feedback.
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_updater::UpdaterExt;

/// Spawns the background update-checker loop. Safe to call once during setup.
pub fn spawn(app: tauri::AppHandle) {
    tauri::async_runtime::spawn(async move {
        // Give the UI a moment to come up before we might pop a dialog.
        tokio::time::sleep(std::time::Duration::from_secs(4)).await;
        if let Err(e) = check_and_prompt(&app).await {
            eprintln!("[app_updates] update check failed: {e}");
        }

        // Re-check periodically in the background.
        let mut ticker = tokio::time::interval(std::time::Duration::from_secs(6 * 3600));
        loop {
            ticker.tick().await;
            if let Err(e) = check_and_prompt(&app).await {
                eprintln!("[app_updates] update check failed: {e}");
            }
        }
    });
}

/// Returns `(version, notes)` of an available update, or `None` if up to date.
/// Errors are returned as `Err(String)` so the UI can surface them.
pub async fn check_info(app: &tauri::AppHandle) -> Result<Option<(String, String)>, String> {
    let update = app
        .updater()
        .map_err(|e| e.to_string())?
        .check()
        .await
        .map_err(|e| e.to_string())?;
    match update {
        Some(u) => Ok(Some((u.version.clone(), u.body.clone().unwrap_or_default()))),
        None => Ok(None),
    }
}

/// Downloads and installs the pending update, then restarts the app.
pub async fn install(app: &tauri::AppHandle) -> Result<(), String> {
    let update = app
        .updater()
        .map_err(|e| e.to_string())?
        .check()
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Kein Update verfügbar.".to_string())?;
    update
        .download_and_install(|_chunk_length, _content_length| {}, || {})
        .await
        .map_err(|e| e.to_string())?;
    app.restart();
    Ok(())
}

async fn check_and_prompt(app: &tauri::AppHandle) -> tauri_plugin_updater::Result<()> {
    let update = match app.updater()?.check().await? {
        Some(u) => u,
        None => return Ok(()),
    };

    let version = update.version.clone();
    let notes = update.body.clone().unwrap_or_default();

    // The dialog callback runs on the main thread; ship the answer back through
    // a channel so this async task can wait for the user's decision.
    let (tx, rx) = std::sync::mpsc::channel::<bool>();
    app.dialog()
        .message(format!(
            "Update verfügbar\n\nEine neue Version {version} des Kollegen Clients ist verfügbar.\n\n{notes}\n\nJetzt installieren?"
        ))
        .buttons(tauri_plugin_dialog::MessageDialogButtons::OkCancelCustom(
            "Installieren".to_string(),
            "Später".to_string(),
        ))
        .show(move |yes| {
            let _ = tx.send(yes);
        });

    if !rx.recv().unwrap_or(false) {
        return Ok(());
    }

    update
        .download_and_install(
            |_chunk_length, _content_length| {},
            || {},
        )
        .await?;

    // On Windows the installer exits the app automatically before installing;
    // on Linux/macOS we restart explicitly once the new bundle is in place.
    app.restart();
    Ok(())
}
