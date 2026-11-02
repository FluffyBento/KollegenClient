








use tauri_plugin_dialog::DialogExt;
use tauri_plugin_updater::UpdaterExt;
use std::process::Command;
use std::time::Duration;
use log::info;



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
        
        
        is_flatpak() || std::env::var("APPIMAGE").is_ok()
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
    match install_format() {
        "appimage" | "nsis" | "dmg" => install_via_updater(app).await,
        "flatpak" => install_flatpak(app).await,
        "deb-rpm" => {
            let version = match check_info(app).await? {
                Some((v, _)) => v,
                None => return Err("Kein Update verfuegbar.".into()),
            };
            install_rpm_deb(app, &version).await
        }
        other => Err(format!(
            "Fuer dieses Installationsformat ({}) gibt es kein Auto-Update. Bitte die neue Version von GitHub herunterladen.",
            other
        )),
    }
}

async fn install_via_updater(app: &tauri::AppHandle) -> Result<(), String> {
    let update = app
        .updater()
        .map_err(|e| e.to_string())?
        .check()
        .await
        .map_err(|e| e.to_string())?
        .ok_or_else(|| "Kein Update verfuegbar.".to_string())?;
    update
        .download_and_install(|_chunk_length, _content_length| {}, || {})
        .await
        .map_err(|e| e.to_string())?;
    app.restart()
}

async fn install_flatpak(app: &tauri::AppHandle) -> Result<(), String> {
    let app_id = flatpak_app_id();
    info!("Aktualisiere Flatpak {}", app_id);

    let attempts: Vec<Vec<String>> = vec![
        vec![
            "flatpak-spawn".into(),
            "--host".into(),
            "flatpak".into(),
            "update".into(),
            "-y".into(),
            app_id.clone(),
        ],
        vec![
            "flatpak-spawn".into(),
            "--host".into(),
            "flatpak".into(),
            "update".into(),
            "-y".into(),
            "--user".into(),
            app_id.clone(),
        ],
    ];

    let mut last_err = String::new();
    for args in attempts {
        let out = tauri::async_runtime::spawn_blocking(move || {
            Command::new(&args[0]).args(&args[1..]).output()
        })
        .await
        .map_err(|e| e.to_string())?
        .map_err(|e| e.to_string())?;
        if out.status.success() {
            info!("Flatpak-Update erfolgreich, starte neu");
            app.restart();
        }
        let err = String::from_utf8_lossy(&out.stderr).trim().to_string();
        if !err.is_empty() {
            last_err = err;
        }
    }

    Err(format!(
        "Flatpak-Update fehlgeschlagen. Bitte manuell ausfuehren: flatpak update -y {}{}",
        app_id,
        if last_err.is_empty() { String::new() } else { format!(" ({})", last_err) }
    ))
}

fn flatpak_app_id() -> String {
    std::env::var("FLATPAK_ID")
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| "dev.kollegen.client".to_string())
}

fn os_release_field(key: &str) -> String {
    let content = std::fs::read_to_string("/etc/os-release").unwrap_or_default();
    for line in content.lines() {
        if let Some(rest) = line.strip_prefix(&format!("{}=", key)) {
            return rest.trim_matches('"').to_lowercase();
        }
    }
    String::new()
}

fn has_binary(name: &str) -> bool {
    Command::new("which")
        .arg(name)
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false)
}

fn package_manager() -> Result<&'static str, String> {
    let id = os_release_field("ID");
    let like = os_release_field("ID_LIKE");

    if id.contains("fedora")
        || id.contains("rhel")
        || id.contains("centos")
        || id.contains("suse")
        || like.contains("fedora")
        || like.contains("rhel")
    {
        return Ok("rpm");
    }
    if id.contains("debian")
        || id.contains("ubuntu")
        || id.contains("mint")
        || id.contains("pop")
        || id.contains("elementary")
        || id.contains("kali")
        || like.contains("debian")
    {
        return Ok("deb");
    }
    if has_binary("apt-get") {
        return Ok("deb");
    }
    if has_binary("dnf") {
        return Ok("rpm");
    }
    Err("Weder Debian/Ubuntu noch Fedora/RHEL erkannt - bitte manuell aktualisieren.".into())
}

fn elevate(program: &str, args: &[String]) -> Command {
    let mut c = if has_binary("pkexec") {
        let mut c = Command::new("pkexec");
        c.arg(program);
        c
    } else {
        let mut c = Command::new("sudo");
        c.args(["-n", program]);
        c
    };
    c.args(args);
    c
}

async fn install_rpm_deb(app: &tauri::AppHandle, version: &str) -> Result<(), String> {
    let kind = package_manager()?;
    let arch = match std::env::consts::ARCH {
        "x86_64" => "amd64",
        "aarch64" => "arm64",
        other => other,
    };
    let generic = if kind == "rpm" {
        "KollegenClient.rpm"
    } else {
        "KollegenClient.deb"
    };
    let candidates = if std::env::consts::ARCH == "x86_64" {
        vec![generic.to_string()]
    } else if kind == "rpm" {
        vec![format!("KollegenClient-{}.rpm", arch), generic.to_string()]
    } else {
        vec![format!("KollegenClient_{}.deb", arch), generic.to_string()]
    };

    let update_dir = std::env::temp_dir().join("kollegen-update");
    std::fs::create_dir_all(&update_dir).map_err(|e| e.to_string())?;

    let mut dest = update_dir.join(generic);
    let mut last_status = String::new();
    let mut got = false;
    for asset_name in &candidates {
        let url = format!(
            "https://github.com/FluffyBento/KollegenClient/releases/download/v{}/{}",
            version, asset_name
        );
        info!("Lade {} herunter: {}", asset_name, url);
        let target = update_dir.join(asset_name.clone());
        let target_in_thread = target.clone();
        let attempt = tauri::async_runtime::spawn_blocking(move || -> Result<(), String> {
            let client = reqwest::blocking::Client::builder()
                .user_agent(crate::USER_AGENT)
                .timeout(Duration::from_secs(180))
                .build()
                .map_err(|e| e.to_string())?;
            let resp = client.get(&url).send().map_err(|e| e.to_string())?;
            if !resp.status().is_success() {
                return Err(format!("HTTP {}", resp.status()));
            }
            let bytes = resp.bytes().map_err(|e| e.to_string())?;
            std::fs::write(&target_in_thread, &bytes).map_err(|e| e.to_string())
        })
        .await
        .map_err(|e| e.to_string())?;
        match attempt {
            Ok(_) => {
                dest = target;
                got = true;
                break;
            }
            Err(e) => last_status = e,
        }
    }
    if !got {
        return Err(format!(
            "Paket fuer diese Architektur nicht gefunden ({}). Bitte manuell von GitHub laden.",
            last_status
        ));
    }

    let file = dest.to_string_lossy().to_string();
    let file_in_thread = file.clone();
    let output = tauri::async_runtime::spawn_blocking(move || {
        let program = if kind == "rpm" { "dnf" } else { "apt-get" };
        let args = vec!["install".to_string(), "-y".to_string(), file_in_thread.clone()];
        elevate(program, &args).output()
    })
    .await
    .map_err(|e| e.to_string())?
    .map_err(|e| e.to_string())?;

    if !output.status.success() {
        let err = String::from_utf8_lossy(&output.stderr);
        let err = err.trim();
        return Err(if err.is_empty() {
            format!(
                "Installation fehlgeschlagen. Wurde die Rechteabfrage abgebrochen? \
                 Alternativ manuell: sudo {} install -y {}",
                if kind == "rpm" { "dnf" } else { "apt-get" },
                file
            )
        } else {
            format!("Installation fehlgeschlagen: {}", err)
        });
    }

    info!("Update erfolgreich installiert, starte neu");
    app.restart()
}

async fn ask(app: &tauri::AppHandle, title: &str, body: &str, yes: &str) -> bool {
    let (tx, rx) = std::sync::mpsc::channel::<bool>();
    let title = title.to_string();
    let body = body.to_string();
    let yes = yes.to_string();
    app.dialog()
        .message(format!("{}\n\n{}", title, body))
        .buttons(tauri_plugin_dialog::MessageDialogButtons::OkCancelCustom(
            yes,
            "Spaeter".to_string(),
        ))
        .show(move |clicked| {
            let _ = tx.send(clicked);
        });
    rx.recv().unwrap_or(false)
}

async fn check_and_prompt(app: &tauri::AppHandle) -> tauri_plugin_updater::Result<()> {
    let update = match app.updater()?.check().await? {
        Some(u) => u,
        None => return Ok(()),
    };

    let version = update.version.clone();
    let notes = update.body.clone().unwrap_or_default();
    let format = install_format();

    if can_self_install() {
        if !ask(
            app,
            "Update verfuegbar",
            &format!(
                "Version {version} des Kollegen Clients ist verfuegbar.\n\n{}\n\nJetzt herunterladen und installieren?",
                notes
            ),
            "Installieren",
        )
        .await
        {
            return Ok(());
        }
        info!("Update {} wird installiert", version);
        match install(app).await {
            Ok(()) => Ok(()),
            Err(e) => {
                let _ = open::that(RELEASE_URL);
                eprintln!("[app_updates] Auto-Install fehlgeschlagen: {e}");
                Ok(())
            }
        }
    } else {
        let hint = match format {
            "flatpak" => format!(
                "Flatpak-Installation erkannt, aber der Befehl zur Rechteerhoe hung nicht ausgefuehrt werden.\nBitte manuell: flatpak update -y dev.kollegen.client"
            ),
            _ => format!(
                "Diese Installation erfolgte ueber Paketmanager ({}).\nBitte manuell aktualisieren oder die neue Version von GitHub laden.",
                format
            ),
        };
        if ask(app, "Update verfuegbar", &format!("Version {version}\n\n{}\n\n{}", notes, hint), "GitHub oeffnen").await {
            let _ = open::that(RELEASE_URL);
        }
        Ok(())
    }
}
