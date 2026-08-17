//! Automatic update checking + prompting using the official Tauri updater plugin.
//!
//! On startup (and then every few hours) we ask the configured update endpoint
//! for a newer version. If one exists we pop a native dialog asking the user to
//! install it; on confirmation the update is downloaded + installed and the app
//! restarts. The endpoint + signing public key live in `tauri.conf.json`.
use tauri_plugin_dialog::DialogExt;
use tauri_plugin_updater::UpdaterExt;

/// Spawns the background update-checker loop. Safe to call once during setup.
pub fn spawn(app: tauri::AppHandle) {
    tauri::async_runtime::spawn(async move {
        // Give the UI a moment to come up before we might pop a dialog.
        tokio::time::sleep(std::time::Duration::from_secs(4)).await;
        let _ = check_and_prompt(&app).await;

        // Re-check periodically in the background.
        let mut ticker = tokio::time::interval(std::time::Duration::from_secs(6 * 3600));
        loop {
            ticker.tick().await;
            let _ = check_and_prompt(&app).await;
        }
    });
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
}
