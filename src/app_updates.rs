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

/// Where users can grab a new version manually (used when self-update is
/// unsupported for the current install format, e.g. .deb/.rpm).
const RELEASE_URL: &str = "https://github.com/FluffyBento/KollegenClient/releases/latest";

/// GitHub REST API for the latest release. Used as a robust fallback for the
/// manual "Check for updates" button: it returns clean JSON (no `latest.json`
/// CDN redirect, which some reqwest configurations fail to follow) and gives us
/// the tag + notes directly.
const UPDATE_API_URL: &str =
    "https://api.github.com/repos/FluffyBento/KollegenClient/releases/latest";

/// Whether this running binary can update itself in place.
///
/// * Windows: the NSIS installer self-updates -> true.
/// * macOS: the app bundle self-updates -> true.
/// * Linux: only the **AppImage** can be replaced in place. When the app was
///   installed via a system package manager (.deb/.rpm) the current executable
///   is not an AppImage, so downloading the AppImage artifact would fail with
///   "invalid updater binary format". In that case we fall back to a
///   "download manually" notification instead of attempting an in-place install.
pub fn can_self_install() -> bool {
    #[cfg(target_os = "windows")]
    {
        true
    }
    #[cfg(target_os = "linux")]
    {
        // AppImage launchers set the `APPIMAGE` environment variable to the
        // running AppImage path; .deb/.rpm installs do not.
        std::env::var("APPIMAGE").is_ok()
    }
    #[cfg(target_os = "macos")]
    {
        true
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
    {
        false
    }
}

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
    // 1) Prefer the official plugin: it verifies the artifact signature and is
    //    what performs the actual in-place install (AppImage/NSIS).
    if let Ok(updater) = app.updater() {
        match updater.check().await {
            Ok(Some(u)) => {
                return Ok(Some((u.version.to_string(), u.body.clone().unwrap_or_default())));
            }
            // `None` = up to date; an error (e.g. transient fetch/parse problem)
            // falls through to the direct fallback below so the manual check in
            // the Settings UI never hard-fails with a cryptic message.
            _ => {}
        }
    }

    // 2) Fallback: fetch `latest.json` ourselves and compare versions. Robust
    //    against plugin quirks and keeps the "Update verfügbar" notice working
    //    for every install format (including .deb/.rpm, where self-install is
    //    unsupported but the user still wants to know about new releases).
    fetch_update_via_http(app).await
}

/// Directly fetches the latest release via the GitHub REST API (bypassing the
/// updater plugin) and returns the version/notes only when the remote version
/// is newer than the running app.
async fn fetch_update_via_http(app: &tauri::AppHandle) -> Result<Option<(String, String)>, String> {
    let url = UPDATE_API_URL.to_string();
    let current = app.package_info().version.to_string();
    let body = tauri::async_runtime::spawn_blocking(move || {
        let client = reqwest::blocking::Client::builder()
            .user_agent(crate::USER_AGENT)
            .build()
            .map_err(|e| e.to_string())?;
        let resp = client.get(&url).send().map_err(|e| e.to_string())?;
        let status = resp.status();
        let text = resp.text().map_err(|e| e.to_string())?;
        if !status.is_success() {
            return Err(format!(
                "GitHub API lieferte HTTP {}: {}",
                status,
                &text[..text.len().min(200)]
            ));
        }
        Ok(text)
    })
    .await
    .map_err(|e| e.to_string())??;

    let v: serde_json::Value = serde_json::from_str(&body).map_err(|e| {
        format!(
            "Konnte Release-Info nicht lesen ({}): {}",
            e,
            &body[..body.len().min(200)]
        )
    })?;
    let tag = v
        .get("tag_name")
        .and_then(|x| x.as_str())
        .ok_or_else(|| "Kein tag_name in Release-Info.".to_string())?
        .to_string();
    let latest = tag.trim_start_matches('v').to_string();
    let notes = v
        .get("body")
        .and_then(|x| x.as_str())
        .unwrap_or("")
        .to_string();

    let cur_ver = semver::Version::parse(&current).map_err(|e| e.to_string())?;
    let new_ver = semver::Version::parse(&latest).map_err(|e| e.to_string())?;
    if new_ver > cur_ver {
        Ok(Some((latest, notes)))
    } else {
        Ok(None)
    }
}

/// Downloads and installs the pending update, then restarts the app.
/// Returns an error (without attempting an install) when the current install
/// format cannot self-update (e.g. .deb/.rpm on Linux).
pub async fn install(app: &tauri::AppHandle) -> Result<(), String> {
    if !can_self_install() {
        return Err(
            "Direktes Update wird für dieses Installationsformat (.deb/.rpm) nicht unterstützt. \
             Bitte die neue Version manuell von GitHub herunterladen."
                .into(),
        );
    }
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

    if can_self_install() {
        // The normal flow: ask to install in place.
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
            .download_and_install(|_chunk_length, _content_length| {}, || {})
            .await?;
        app.restart();
        Ok(())
    } else {
        // Notification-only: .deb/.rpm can't self-update, point the user to GitHub.
        let (tx, rx) = std::sync::mpsc::channel::<bool>();
        app.dialog()
            .message(format!(
                "Update verfügbar\n\nEine neue Version {version} des Kollegen Clients ist verfügbar.\n\n{notes}\n\nDa diese Installation über einen Paketmanager (.deb/.rpm) erfolgte, kann sie nicht direkt aktualisiert werden. Die neue Version auf GitHub öffnen?"
            ))
            .buttons(tauri_plugin_dialog::MessageDialogButtons::OkCancelCustom(
                "Download öffnen".to_string(),
                "Später".to_string(),
            ))
            .show(move |yes| {
                let _ = tx.send(yes);
            });
        if rx.recv().unwrap_or(false) {
            let _ = open::that(RELEASE_URL);
        }
        Ok(())
    }
}
