// Microsoft OAuth authentication - Device Code Flow
// Flow: MSA (device code) -> Xbox Live -> XSTS -> Minecraft
// Based on https://github.com/i0nx/MinecraftOAuth

use crate::types::Account;
use anyhow::{anyhow, Result};
use lazy_static::lazy_static;
use serde_json::Value;
use std::path::Path;
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

// ─=== Global Auth Status for Polling ===

lazy_static! {
    static ref AUTH_STATUS: Mutex<Value> = Mutex::new(serde_json::json!({
        "state": "idle",
        "user_code": "",
        "verification_uri": "",
        "msg": ""
    }));
    static ref REFRESHING: Mutex<bool> = Mutex::new(false);
}

/// Returns the current auth status (for polling from frontend).
/// If a valid account is stored on disk it is reflected as "done", and an
/// expired-but-refreshable account is renewed automatically in the background.
pub fn get_auth_status() -> Value {
    let mut status = AUTH_STATUS.lock().unwrap();
    if status["state"] == "idle" {
        if let Ok(data_dir) = crate::utils::get_project_dirs() {
            let path = crate::utils::accounts_file(&data_dir);
            let accts = crate::utils::load_json::<Vec<Account>>(&path, vec![]);
            if let Some(acc) = accts.first() {
                let now = SystemTime::now()
                    .duration_since(UNIX_EPOCH)
                    .unwrap()
                    .as_secs();
                let expired = acc.expires_at.map(|e| now >= e).unwrap_or(true);
                if !expired {
                    *status = serde_json::json!({
                        "state": "done",
                        "user_code": "",
                        "verification_uri": "",
                        "msg": format!("Angemeldet als {}", acc.username),
                        "username": acc.username,
                        "uuid": acc.uuid,
                    });
                } else if acc.refresh_token.is_some() {
                    let mut refreshing = REFRESHING.lock().unwrap();
                    if !*refreshing {
                        *refreshing = true;
                        drop(refreshing);
                        *status = serde_json::json!({
                            "state": "pending",
                            "user_code": "",
                            "verification_uri": "",
                            "msg": "Session wird erneuert..."
                        });
                        drop(status);
                        std::thread::spawn(|| {
                            let res = refresh_stored_account();
                            let mut s = AUTH_STATUS.lock().unwrap();
                            match res {
                                Ok(_) => {
                                    if let Ok(dd) = crate::utils::get_project_dirs() {
                                        let p = crate::utils::accounts_file(&dd);
                                        let a = crate::utils::load_json::<Vec<Account>>(&p, vec![]);
                                        if let Some(acc) = a.first() {
                                            *s = serde_json::json!({
                                                "state": "done",
                                                "user_code": "",
                                                "verification_uri": "",
                                                "msg": format!("Angemeldet als {}", acc.username),
                                                "username": acc.username,
                                                "uuid": acc.uuid,
                                            });
                                        }
                                    }
                                }
                                Err(e) => {
                                    *s = serde_json::json!({
                                        "state": "idle",
                                        "user_code": "",
                                        "verification_uri": "",
                                        "msg": format!("Session abgelaufen, bitte erneut anmelden: {}", e)
                                    });
                                }
                            }
                            *REFRESHING.lock().unwrap() = false;
                        });
                        return AUTH_STATUS.lock().unwrap().clone();
                    }
                }
            }
        }
    }
    status.clone()
}

/// Starts the Microsoft OAuth login flow using the device code flow.
/// Returns the auth status with user code and verification URI.
pub fn ms_auth_start() -> Result<Value> {
    let body = format!(
        "client_id={}&scope={}",
        crate::client_id(),
        urlencoding::encode("XboxLive.Signin XboxLive.offline_access")
    );

    let resp = reqwest::blocking::Client::new()
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .body(body)
        .send()?;

    let r: Value = resp.json()?;

    if !r.get("device_code").is_some() {
        let err = r.get("error_description")
            .or_else(|| r.get("error"))
            .and_then(|v| v.as_str())
            .unwrap_or("Unbekannter Fehler bei der OAuth-Anfrage");
        return Err(anyhow!("{}", err));
    }

    let device_code = r.get("device_code").and_then(|v| v.as_str()).unwrap_or("").to_string();
    let interval = r.get("interval").and_then(|v| v.as_i64()).unwrap_or(5) as i64;
    let expires_in = r.get("expires_in").and_then(|v| v.as_i64()).unwrap_or(900);
    let user_code = r.get("user_code").and_then(|v| v.as_str()).unwrap_or("").to_string();
    let verification_uri = r.get("verification_uri")
        .or_else(|| r.get("verification_uri_complete"))
        .and_then(|v| v.as_str())
        .unwrap_or("https://microsoft.com/devicelogin")
        .to_string();

    {
        let mut status = AUTH_STATUS.lock().unwrap();
        *status = serde_json::json!({
            "state": "pending",
            "user_code": user_code,
            "verification_uri": verification_uri,
            "msg": "Browser öffnen und Code eingeben..."
        });
    }

    // Spawn polling thread
    std::thread::spawn(move || {
        ms_auth_poll(device_code, interval, expires_in);
    });

    Ok(AUTH_STATUS.lock().unwrap().clone())
}

/// Polls Microsoft for the OAuth token, then completes the full auth chain.
fn ms_auth_poll(device_code: String, interval: i64, expires_in: i64) {
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()
        .unwrap();

    let token_url = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    let body = format!(
        "client_id={}&grant_type=urn:ietf:params:oauth:grant-type:device_code&device_code={}",
        crate::client_id(),
        urlencoding::encode(&device_code)
    );

    let start = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs() as i64;

    loop {
        if SystemTime::now()
            .duration_since(UNIX_EPOCH)
            .unwrap()
            .as_secs() as i64
            >= start + expires_in
        {
            set_auth_error("Zeit abgelaufen");
            return;
        }

        std::thread::sleep(Duration::from_secs(interval.max(1) as u64));

        let result = client.post(token_url)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .body(body.clone())
            .send();

        match result {
            Ok(resp) => {
                if let Ok(msa) = resp.json::<Value>() {
                    if let Some(err) = msa.get("error").and_then(|v| v.as_str()) {
                        if err == "authorization_pending" {
                            set_auth_msg("Warte auf Anmeldung...");
                            continue;
                        }
                        if err == "slow_down" {
                            set_auth_msg("Warte auf Anmeldung...");
                            std::thread::sleep(Duration::from_secs(5));
                            continue;
                        }
                        set_auth_error(&format!("Fehler: {}", err));
                        return;
                    }

                    // Success - complete the auth chain
                    let access_token = msa["access_token"]
                        .as_str()
                        .unwrap_or("")
                        .to_string();

                    set_auth_msg("Token erhalten, Xbox-Auth...");

                    let result: anyhow::Result<(String, String, String, Value, Value)> = (|| {
                        let xbl_token = xbox_auth(&access_token)?;
                        let (xsts_token, uhs) = xsts_auth(&xbl_token)?;
                        let mc = mc_login(&xsts_token, &uhs)?;
                        let prof = mc_profile(mc["access_token"].as_str().unwrap_or(""))?;

                        let username = prof["name"].as_str().unwrap_or("Spieler").to_string();
                        let uuid = prof["id"].as_str().unwrap_or("0").to_string();
                        let mc_token = mc["access_token"].as_str().unwrap_or("").to_string();

                        Ok((username, uuid, mc_token, prof, msa))
                    })();

                    match result {
                        Ok((username, uuid, mc_token, prof, msa)) => {
                            complete_auth(&username, &uuid, &mc_token, &prof, &msa);
                            return;
                        }
                        Err(e) => {
                            set_auth_error(&format!("Auth fehlgeschlagen: {}", e));
                            return;
                        }
                    }
                } else {
                    set_auth_error("Ungültige Antwort vom Server");
                }
            }
            Err(e) => {
                set_auth_error(&format!("Verbindungsfehler: {}", e));
            }
        }
    }
}

fn set_auth_msg(msg: &str) {
    let mut status = AUTH_STATUS.lock().unwrap();
    status["msg"] = msg.into();
}

fn set_auth_error(msg: &str) {
    let mut status = AUTH_STATUS.lock().unwrap();
    *status = serde_json::json!({
        "state": "error",
        "user_code": "",
        "verification_uri": "",
        "msg": msg
    });
}

fn complete_auth(username: &str, uuid: &str, mc_token: &str, prof: &Value, msa: &Value) {
    let avatar_id = prof["id"].as_str().map(|s| s.to_string());
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs();

    let acc = Account {
        username: username.to_string(),
        uuid: uuid.to_string(),
        access_token: mc_token.to_string(),
        refresh_token: msa.get("refresh_token")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string()),
        expires_at: Some(now + 24 * 3600),
        avatar_id,
        xuid: None,
    };

    // Save account to disk
    if let Ok(data_dir) = crate::utils::get_project_dirs() {
        let path = crate::utils::accounts_file(&data_dir);
        let mut accts = crate::utils::load_json::<Vec<Account>>(&path, vec![]);
        accts.retain(|a| a.username != acc.username);
        accts.push(acc.clone());
        let _ = crate::utils::save_json(&path, &accts);
    }

    let mut status = AUTH_STATUS.lock().unwrap();
    *status = serde_json::json!({
        "state": "done",
        "user_code": "",
        "verification_uri": "",
        "msg": format!("Angemeldet als {}", username),
        "username": username,
        "uuid": uuid,
    });
}

/// Renews the stored account using its refresh_token and re-runs the full
/// auth chain (Xbox Live -> XSTS -> Minecraft). Updates the saved account.
pub fn refresh_stored_account() -> Result<()> {
    let data_dir = crate::utils::get_project_dirs()?;
    let path = crate::utils::accounts_file(&data_dir);
    let mut accts = crate::utils::load_json::<Vec<Account>>(&path, vec![]);
    let idx = accts
        .iter()
        .position(|a| a.refresh_token.is_some())
        .ok_or_else(|| anyhow!("Kein gespeichertes Konto zum Erneuern gefunden"))?;
    let refresh_token = accts[idx].refresh_token.clone().unwrap();

    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(30))
        .build()?;
    let body = format!(
        "client_id={}&grant_type=refresh_token&refresh_token={}",
        crate::client_id(),
        urlencoding::encode(&refresh_token)
    );
    let resp = client
        .post("https://login.microsoftonline.com/consumers/oauth2/v2.0/token")
        .header("Content-Type", "application/x-www-form-urlencoded")
        .header("Accept", "application/json")
        .body(body)
        .send()?;
    let msa: Value = resp.json()?;
    if msa.get("error").is_some() {
        return Err(anyhow!(
            "Token-Erneuerung fehlgeschlagen: {}",
            msa.get("error_description")
                .and_then(|v| v.as_str())
                .unwrap_or("unbekannt")
        ));
    }
    let access_token = msa["access_token"].as_str().unwrap_or("").to_string();
    let new_refresh = msa
        .get("refresh_token")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .unwrap_or(refresh_token);

    let xbl_token = xbox_auth(&access_token)?;
    let (xsts_token, uhs) = xsts_auth(&xbl_token)?;
    let mc = mc_login(&xsts_token, &uhs)?;
    let prof = mc_profile(mc["access_token"].as_str().unwrap_or(""))?;

    let username = prof["name"].as_str().unwrap_or("Spieler").to_string();
    let uuid = prof["id"].as_str().unwrap_or("0").to_string();
    let mc_token = mc["access_token"].as_str().unwrap_or("").to_string();
    let avatar_id = prof["id"].as_str().map(|s| s.to_string());
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap()
        .as_secs();

    let mut acc = accts[idx].clone();
    acc.username = username;
    acc.uuid = uuid;
    acc.access_token = mc_token;
    acc.refresh_token = Some(new_refresh);
    acc.expires_at = Some(now + 24 * 3600);
    acc.avatar_id = avatar_id;
    accts[idx] = acc;
    crate::utils::save_json(&path, &accts)?;
    Ok(())
}

/// Marks the account with the given uuid as the "active" (first) account by
/// moving it to the front of the stored list. The first account is what the
/// rest of the launcher treats as the signed-in identity.
pub fn switch_account(data_dir: &Path, uuid: &str) -> Result<()> {
    let path = crate::utils::accounts_file(data_dir);
    let mut accts = crate::utils::load_json::<Vec<Account>>(&path, vec![]);
    if let Some(pos) = accts.iter().position(|a| a.uuid == uuid) {
        let acc = accts.remove(pos);
        accts.insert(0, acc);
        crate::utils::save_json(&path, &accts)?;
    }
    Ok(())
}

/// Removes the account with the given uuid from the stored list.
pub fn remove_account(data_dir: &Path, uuid: &str) -> Result<()> {
    let path = crate::utils::accounts_file(data_dir);
    let mut accts = crate::utils::load_json::<Vec<Account>>(&path, vec![]);
    accts.retain(|a| a.uuid != uuid);
    crate::utils::save_json(&path, &accts)?;
    Ok(())
}

// ─=== Xbox Live Authentication ===

/// Authenticates with Xbox Live using the MSA access token.
/// Returns the Xbox Live token.
fn xbox_auth(msa_token: &str) -> Result<String> {
    let body = serde_json::json!({
        "Properties": {
            "AuthMethod": "RPS",
            "SiteName": "user.auth.xboxlive.com",
            "RpsTicket": format!("d={}", msa_token)
        },
        "RelyingParty": "http://auth.xboxlive.com",
        "TokenType": "JWT"
    });

    let resp = reqwest::blocking::Client::new()
        .post("https://user.auth.xboxlive.com/user/authenticate")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("x-xbl-contract-version", "1")
        .body(body.to_string())
        .send()?;

    let data: Value = resp.json()?;

    data.get("Token")
        .and_then(|v| v.as_str())
        .map(|s| s.to_string())
        .ok_or_else(|| anyhow!("Xbox auth failed: no token in response"))
}

// ─=== XSTS Authentication ===

/// Authenticates with XSTS using the Xbox Live token.
/// Returns (XSTS token, UHS - User Hash).
fn xsts_auth(xbl_token: &str) -> Result<(String, String)> {
    let body = serde_json::json!({
        "Properties": {
            "SandboxId": "RETAIL",
            "UserTokens": [xbl_token]
        },
        "RelyingParty": "rp://api.minecraftservices.com/",
        "TokenType": "JWT"
    });

    let resp = reqwest::blocking::Client::new()
        .post("https://xsts.auth.xboxlive.com/xsts/authorize")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .header("x-xbl-contract-version", "1")
        .body(body.to_string())
        .send()?;

    let data: Value = resp.json()?;

    // Check for XSTS errors (e.g., 4 for Xbox Live account not able to get XSTS)
    if data.get("error").is_some() {
        let error = data["error"].as_i64().unwrap_or(0);
        let error_description = data["error_description"].as_str().unwrap_or("");
        if error == 4 || error == 5 || error == 6 {
            return Err(anyhow!("Microsoft/Xbox account not eligible for Minecraft: {}", error_description));
        }
        return Err(anyhow!("XSTS auth failed: {} ({})", error_description, error));
    }

    let token = data.get("Token")
        .or_else(|| data.get("XstsToken"))
        .and_then(|v| v.as_str())
        .ok_or_else(|| anyhow!("XSTS auth failed: no token in response"))?
        .to_string();

    let uhs = data["DisplayClaims"]["xui"][0]["uhs"]
        .as_str()
        .ok_or_else(|| anyhow!("XSTS auth failed: no UHS in response"))?
        .to_string();

    Ok((token, uhs))
}

// ─=== Minecraft Login ===

/// Logs in to Minecraft services using the XSTS token.
/// Returns the Minecraft access token and other profile data.
fn mc_login(xsts_token: &str, uhs: &str) -> Result<Value> {
    let token_str = format!("XBL3.0 x={};{}", uhs, xsts_token);
    let body = serde_json::json!({
        "platform": "PC_LAUNCHER",
        "xtoken": token_str
    });

    let resp = reqwest::blocking::Client::new()
        .post("https://api.minecraftservices.com/launcher/login")
        .header("Content-Type", "application/json")
        .header("Accept", "application/json")
        .body(body.to_string())
        .send()
        .map_err(|e| anyhow!("Minecraft-Login-Anfrage fehlgeschlagen: {}", e))?;

    let status = resp.status();
    let data: Value = resp
        .json()
        .map_err(|e| anyhow!("Minecraft-Login-Antwort ungültig (Status {}): {}", status, e))?;

    if let Some(err) = data.get("error") {
        let desc = data["error_description"]
            .as_str()
            .or_else(|| data["errorMessage"].as_str())
            .unwrap_or("unbekannter Fehler");
        return Err(anyhow!("Minecraft-Login fehlgeschlagen: {} ({})", err, desc));
    }

    Ok(data)
}

// ─=== Minecraft Profile ===

/// Retrieves the Minecraft profile for the logged-in user.
fn mc_profile(mc_token: &str) -> Result<Value> {
    let resp = reqwest::blocking::Client::new()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .header("Authorization", format!("Bearer {}", mc_token))
        .header("Accept", "application/json")
        .send()
        .map_err(|e| anyhow!("Minecraft-Profil-Anfrage fehlgeschlagen: {}", e))?;

    let status = resp.status();
    let data: Value = resp
        .json()
        .map_err(|e| anyhow!("Minecraft-Profil-Antwort ungültig (Status {}): {}", status, e))?;

    if let Some(err) = data.get("error") {
        let desc = data["error_description"]
            .as_str()
            .or_else(|| data["errorMessage"].as_str())
            .unwrap_or("unbekannter Fehler");
        // `NOT_FOUND` means the Microsoft account does not own Minecraft Java
        // Edition (or it isn't linked) – by far the most common cause, so say so.
        if err.as_str() == Some("NOT_FOUND") {
            return Err(anyhow!(
                "Minecraft-Profil nicht gefunden: dieses Microsoft-Konto besitzt keine Minecraft Java Edition (oder sie ist nicht mit diesem Konto verknüpft). Details: {}",
                desc
            ));
        }
        return Err(anyhow!("Minecraft-Profil abrufen fehlgeschlagen: {} ({})", err, desc));
    }

    Ok(data)
}



