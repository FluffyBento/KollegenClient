use crate::types::Account;
use serde_json::Value;
use std::path::{Path, PathBuf};

fn home_dir() -> PathBuf {
    dirs::home_dir().unwrap_or_else(|| PathBuf::from("."))
}

pub fn detect_login_sources() -> Vec<Value> {
    let mut sources = Vec::new();
    let h = home_dir();
    let appdata = dirs::data_dir().unwrap_or_else(home_dir);
    let config_dir = dirs::config_dir().unwrap_or_else(home_dir);
    let local_data = dirs::data_local_dir().unwrap_or_else(home_dir);
    let flatpak = h.join(".var").join("app");

    // 1. Modrinth App
    let modrinth_candidates = vec![
        appdata.join("ModrinthApp"),
        appdata.join("com.modrinth.theseus"),
        local_data.join("ModrinthApp"),
        local_data.join("com.modrinth.theseus"),
        flatpak.join("com.modrinth.ModrinthApp").join("data").join("ModrinthApp"),
    ];
    for base in modrinth_candidates {
        if base.exists() {
            let db_path = base.join("app.db");
            let json_path = base.join("caches/metadata/minecraft_auth.json");
            if db_path.exists() || json_path.exists() {
                sources.push(serde_json::json!({
                    "id": "modrinth",
                    "name": "Modrinth App",
                    "path": base.to_string_lossy(),
                }));
                break;
            }
        }
    }

    // 2. Prism Launcher
    let prism_candidates = vec![
        config_dir.join("PrismLauncher"),
        local_data.join("PrismLauncher"),
        appdata.join("PrismLauncher"),
        flatpak.join("org.prismlauncher.PrismLauncher").join("data").join("PrismLauncher"),
    ];
    for base in prism_candidates {
        if base.exists() && base.join("accounts.json").exists() {
            sources.push(serde_json::json!({
                "id": "prism",
                "name": "Prism Launcher",
                "path": base.to_string_lossy(),
            }));
            break;
        }
    }

    // 3. MultiMC
    let multimc_candidates = vec![
        config_dir.join("MultiMC"),
        local_data.join("MultiMC"),
        appdata.join("MultiMC"),
        flatpak.join("org.multimc.MultiMC").join("data").join("MultiMC"),
    ];
    for base in multimc_candidates {
        if base.exists() && base.join("accounts.json").exists() {
            sources.push(serde_json::json!({
                "id": "multimc",
                "name": "MultiMC",
                "path": base.to_string_lossy(),
            }));
            break;
        }
    }

    sources
}

pub fn import_login_source(data_dir: &Path, source_id: &str) -> Result<usize, String> {
    let appdata = dirs::data_dir().unwrap_or_else(home_dir);
    let config_dir = dirs::config_dir().unwrap_or_else(home_dir);
    let local_data = dirs::data_local_dir().unwrap_or_else(home_dir);

    let mut imported_accounts = Vec::new();

    match source_id {
        "modrinth" => {
            let flatpak = home_dir().join(".var").join("app");
            let candidates = vec![
                appdata.join("ModrinthApp"),
                appdata.join("com.modrinth.theseus"),
                local_data.join("ModrinthApp"),
                local_data.join("com.modrinth.theseus"),
                flatpak.join("com.modrinth.ModrinthApp").join("data").join("ModrinthApp"),
            ];
            for base in candidates {
                if !base.exists() {
                    continue;
                }
                // Try SQLite app.db
                let db_path = base.join("app.db");
                if db_path.exists() {
                    if let Ok(conn) = rusqlite::Connection::open_with_flags(
                        &db_path,
                        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY,
                    ) {
                        if let Ok(mut stmt) = conn.prepare("SELECT uuid, username, refresh_token FROM minecraft_users WHERE refresh_token IS NOT NULL") {
                            let rows = stmt.query_map([], |row| {
                                Ok((
                                    row.get::<_, String>(0)?,
                                    row.get::<_, String>(1)?,
                                    row.get::<_, String>(2)?,
                                ))
                            });
                            if let Ok(iter) = rows {
                                for r in iter.flatten() {
                                    let (uuid, username, refresh_token) = r;
                                    let clean_uuid = uuid.replace("-", "");
                                    imported_accounts.push(Account {
                                        username,
                                        uuid: clean_uuid.clone(),
                                        access_token: String::new(),
                                        refresh_token: Some(refresh_token),
                                        expires_at: None,
                                        avatar_id: Some(clean_uuid),
                                        xuid: None,
                                        client_id: Some("00000000402b5328".to_string()),
                                    });
                                }
                            }
                        }
                    }
                }
                // Try legacy minecraft_auth.json
                let json_path = base.join("caches/metadata/minecraft_auth.json");
                if json_path.exists() {
                    if let Ok(s) = std::fs::read_to_string(&json_path) {
                        if let Ok(v) = serde_json::from_str::<Value>(&s) {
                            if let Some(users) = v.get("users").and_then(|u| u.as_object()) {
                                for (_uuid_key, uval) in users {
                                    if let (Some(uname), Some(rt)) = (
                                        uval.get("username").and_then(|x| x.as_str()),
                                        uval.get("refresh_token").and_then(|x| x.as_str()),
                                    ) {
                                        let uuid = uval.get("id")
                                            .and_then(|x| x.as_str())
                                            .map(|s| s.replace("-", ""))
                                            .unwrap_or_else(|| "0".to_string());
                                        imported_accounts.push(Account {
                                            username: uname.to_string(),
                                            uuid: uuid.clone(),
                                            access_token: String::new(),
                                            refresh_token: Some(rt.to_string()),
                                            expires_at: None,
                                            avatar_id: Some(uuid),
                                            xuid: None,
                                            client_id: Some("00000000402b5328".to_string()),
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        "prism" | "multimc" => {
            let flatpak = home_dir().join(".var").join("app");
            let candidates = if source_id == "prism" {
                vec![
                    config_dir.join("PrismLauncher"),
                    local_data.join("PrismLauncher"),
                    appdata.join("PrismLauncher"),
                    flatpak.join("org.prismlauncher.PrismLauncher").join("data").join("PrismLauncher"),
                ]
            } else {
                vec![
                    config_dir.join("MultiMC"),
                    local_data.join("MultiMC"),
                    appdata.join("MultiMC"),
                    flatpak.join("org.multimc.MultiMC").join("data").join("MultiMC"),
                ]
            };
            for base in candidates {
                let acc_file = base.join("accounts.json");
                if !acc_file.exists() {
                    continue;
                }
                if let Ok(s) = std::fs::read_to_string(&acc_file) {
                    if let Ok(v) = serde_json::from_str::<Value>(&s) {
                        let arr = v.get("accounts")
                            .and_then(|a| a.as_array())
                            .or_else(|| v.as_array());
                        if let Some(list) = arr {
                            for acc in list {
                                if acc.get("type").and_then(|t| t.as_str()) == Some("MSA") {
                                    let msa = acc.get("msa");
                                    let rt = msa.and_then(|m| m.get("refresh_token").and_then(|x| x.as_str()))
                                        .or_else(|| acc.get("refreshToken").and_then(|x| x.as_str()));
                                    let profile = acc.get("profile");
                                    let uname = profile.and_then(|p| p.get("name").and_then(|x| x.as_str()))
                                        .or_else(|| acc.get("username").and_then(|x| x.as_str()))
                                        .unwrap_or("Spieler");
                                    let uuid = profile.and_then(|p| p.get("id").and_then(|x| x.as_str()))
                                        .or_else(|| acc.get("uuid").and_then(|x| x.as_str()))
                                        .map(|s| s.replace("-", ""))
                                        .unwrap_or_else(|| "0".to_string());
                                    let cid = acc.get("msa-client-id")
                                        .and_then(|x| x.as_str())
                                        .map(|s| s.to_string());

                                    if let Some(token) = rt {
                                        imported_accounts.push(Account {
                                            username: uname.to_string(),
                                            uuid: uuid.clone(),
                                            access_token: String::new(),
                                            refresh_token: Some(token.to_string()),
                                            expires_at: None,
                                            avatar_id: Some(uuid),
                                            xuid: None,
                                            client_id: cid,
                                        });
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        _ => return Err(format!("Unbekannte Launcher-ID: {}", source_id)),
    }

    if imported_accounts.is_empty() {
        return Err("Keine Konten in diesem Launcher gefunden.".to_string());
    }

    let accounts_path = crate::utils::accounts_file(data_dir);
    let mut existing = crate::utils::load_json::<Vec<Account>>(&accounts_path, vec![]);
    for acc in imported_accounts {
        existing.retain(|a| a.uuid != acc.uuid && a.username != acc.username);
        existing.insert(0, acc);
    }
    crate::utils::save_json(&accounts_path, &existing).map_err(|e| e.to_string())?;

    // Versuche das erste importierte Konto direkt zu aktualisieren/validieren
    let _ = crate::auth::refresh_stored_account();

    Ok(existing.len())
}
