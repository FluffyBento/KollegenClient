








use tauri_plugin_dialog::DialogExt;
use tauri_plugin_updater::UpdaterExt;



const RELEASE_URL: &str = "https://github.com/FluffyBento/KollegenClient/releases/latest";





const UPDATE_API_URL: &str =
    "https://api.github.com/repos/FluffyBento/KollegenClient/releases/latest";










pub fn can_self_install() -> bool {
    #[cfg(target_os = "windows")]
    {
        true
    }
    #[cfg(target_os = "linux")]
    {
        
        
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



pub fn install_format() -> &'static str {
    #[cfg(target_os = "windows")]
    {
        "nsis"
    }
    #[cfg(target_os = "macos")]
    {
        "dmg"
    }
    #[cfg(target_os = "linux")]
    {
        if is_flatpak() {
            "flatpak"
        } else if std::env::var("APPIMAGE").is_ok() {
            "appimage"
        } else {
            "deb-rpm"
        }
    }
    #[cfg(not(any(target_os = "windows", target_os = "linux", target_os = "macos")))]
    {
        "unknown"
    }
}





pub fn is_flatpak() -> bool {
    #[cfg(target_os = "linux")]
    {
        if std::env::var("FLATPAK_ID").is_ok() {
            return true;
        }
        std::env::current_exe()
            .map(|p| p.starts_with("/app/"))
            .unwrap_or(false)
    }
    #[cfg(not(target_os = "linux"))]
    {
        false
    }
}


pub fn spawn(app: tauri::AppHandle) {
    tauri::async_runtime::spawn(async move {
        
        tokio::time::sleep(std::time::Duration::from_secs(4)).await;
        if let Err(e) = check_and_prompt(&app).await {
            eprintln!("[app_updates] update check failed: {e}");
        }

        
        let mut ticker = tokio::time::interval(std::time::Duration::from_secs(6 * 3600));
        loop {
            ticker.tick().await;
            if let Err(e) = check_and_prompt(&app).await {
                eprintln!("[app_updates] update check failed: {e}");
            }
        }
    });
}



pub async fn check_info(app: &tauri::AppHandle) -> Result<Option<(String, String)>, String> {
    
    
    if let Ok(updater) = app.updater() {
        match updater.check().await {
            Ok(Some(u)) => {
                return Ok(Some((u.version.to_string(), u.body.clone().unwrap_or_default())));
            }
            
            
            
            _ => {}
        }
    }

    
    
    
    
    fetch_update_via_http(app).await
}




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
    app.restart()
}

async fn check_and_prompt(app: &tauri::AppHandle) -> tauri_plugin_updater::Result<()> {
    let update = match app.updater()?.check().await? {
        Some(u) => u,
        None => return Ok(()),
    };

    let version = update.version.clone();
    let notes = update.body.clone().unwrap_or_default();

    if can_self_install() {
        
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
        app.restart()
    } else {
        
        
        let (tx, rx) = std::sync::mpsc::channel::<bool>();
        let body = if is_flatpak() {
            format!(
                "Update verfügbar\n\nEine neue Version {version} des Kollegen Clients ist verfügbar.\n\n{notes}\n\nDa diese Installation ein Flatpak ist, kann sie nicht direkt aktualisiert werden. Die neue Version herunterladen (Flatpak-Bundle) und danach installieren:\n\n  flatpak install --user ./dev.kollegen.Client.flatpak"
            )
        } else {
            format!(
                "Update verfügbar\n\nEine neue Version {version} des Kollegen Clients ist verfügbar.\n\n{notes}\n\nDa diese Installation über einen Paketmanager (.deb/.rpm) erfolgte, kann sie nicht direkt aktualisiert werden. Die neue Version auf GitHub öffnen?"
            )
        };
        app.dialog()
            .message(body)
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
