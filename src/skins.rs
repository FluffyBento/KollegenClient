// Skin- & Cape-Verwaltung für den Kollegen-Client.
//
// Ermöglicht es dem Nutzer, seinen Minecraft-Skin direkt im Launcher zu
// wechseln (3D-Vorschau + lokale Bibliothek + Upload zum Minecraft-Konto)
// sowie besitzte Capes auszurüsten. Alle Minecraft-API-Aufrufe laufen über das
// aktive (erste) Microsoft-Konto; ohne Konto funktioniert die lokale
// Bibliothek + 3D-Vorschau trotzdem.

use base64::Engine;
use serde_json::Value;
use std::path::Path;
use std::time::{SystemTime, UNIX_EPOCH};

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Frisches Minecraft-Access-Token des aktiven (ersten) Kontos – bei Bedarf
/// wird die Session über den Refresh-Token erneuert.
fn mc_token(data_dir: &Path) -> Option<String> {
    let path = crate::utils::accounts_file(data_dir);
    let mut accts =
        crate::utils::load_json::<Vec<crate::types::Account>>(&path, Vec::new());
    let expired = accts
        .first()
        .and_then(|a| a.expires_at)
        .map(|e| now_secs() >= e)
        .unwrap_or(true);
    if expired {
        if let Some(acc) = accts.first() {
            if acc.refresh_token.is_some() {
                let _ = crate::auth::refresh_stored_account();
            }
        }
        accts = crate::utils::load_json::<Vec<crate::types::Account>>(&path, Vec::new());
    }
    accts.first().map(|a| a.access_token.clone())
}

fn skins_dir(data_dir: &Path) -> std::path::PathBuf {
    data_dir.join("skins")
}

fn index_path(data_dir: &Path) -> std::path::PathBuf {
    skins_dir(data_dir).join("index.json")
}

fn read_index(data_dir: &Path) -> Value {
    let v = crate::utils::load_json::<Value>(
        &index_path(data_dir),
        serde_json::json!({ "active": Value::Null, "skins": [] }),
    );
    let mut v = if v.is_object() {
        v
    } else {
        serde_json::json!({ "active": Value::Null, "skins": [] })
    };
    if v.get("skins").is_none() {
        v["skins"] = serde_json::json!([]);
    }
    if v.get("active").is_none() {
        v["active"] = Value::Null;
    }
    v
}

fn write_index(data_dir: &Path, idx: &Value) {
    let _ = std::fs::create_dir_all(skins_dir(data_dir));
    let _ = crate::utils::save_json(&index_path(data_dir), idx);
}

fn data_url_of(data_dir: &Path, file: &str) -> String {
    let p = skins_dir(data_dir).join(file);
    match std::fs::read(&p) {
        Ok(b) => format!(
            "data:image/png;base64,{}",
            base64::engine::general_purpose::STANDARD.encode(&b)
        ),
        Err(_) => String::new(),
    }
}

fn b64_to_bytes(s: &str) -> Result<Vec<u8>, String> {
    let s = s.split(',').last().unwrap_or(s).to_string();
    base64::engine::general_purpose::STANDARD
        .decode(&s)
        .map_err(|e| format!("Ungültiges Bild (Base64): {}", e))
}

/// Listet die lokale Skin-Bibliothek inkl. Vorschau-Data-URLs.
pub fn list_skins(data_dir: &Path) -> Value {
    let idx = read_index(data_dir);
    let skins = idx
        .get("skins")
        .and_then(|s| s.as_array())
        .cloned()
        .unwrap_or_default();
    let mut out = Vec::new();
    for s in skins {
        if let Some(name) = s.get("name").and_then(|n| n.as_str()) {
            let file = s
                .get("file")
                .and_then(|f| f.as_str())
                .unwrap_or("")
                .to_string();
            let url = data_url_of(data_dir, &file);
            out.push(serde_json::json!({ "name": name, "url": url }));
        }
    }
    serde_json::json!({
        "active": idx.get("active").cloned().unwrap_or(Value::Null),
        "skins": out,
    })
}

fn save_skin_bytes(data_dir: &Path, name: &str, bytes: &[u8]) -> Result<String, String> {
    let safe = sanitize_filename::sanitize(name);
    let safe = if safe.is_empty() { "skin".to_string() } else { safe };
    let file = format!("{}.png", safe);
    let _ = std::fs::create_dir_all(skins_dir(data_dir));
    std::fs::write(skins_dir(data_dir).join(&file), bytes)
        .map_err(|e| format!("Konnte Skin nicht speichern: {}", e))?;
    Ok(file)
}

/// Speichert einen Skin in der lokalen Bibliothek (ersetzt gleichnamige) und
/// setzt ihn bei Bedarf als aktiv. Gibt die aktualisierte Liste zurück.
pub fn import_skin(data_dir: &Path, name: &str, bytes: &[u8]) -> Value {
    let file = match save_skin_bytes(data_dir, name, bytes) {
        Ok(f) => f,
        Err(e) => return serde_json::json!({ "error": e }),
    };
    let mut idx = read_index(data_dir);
    let mut skins: Vec<Value> = idx
        .get("skins")
        .and_then(|s| s.as_array())
        .cloned()
        .unwrap_or_default();
    skins.retain(|s| s.get("name").and_then(|n| n.as_str()) != Some(name));
    skins.push(serde_json::json!({ "name": name, "file": file }));
    idx["skins"] = serde_json::Value::Array(skins);
    if idx.get("active").map(|v| v.is_null()).unwrap_or(true) {
        idx["active"] = serde_json::json!(name);
    }
    write_index(data_dir, &idx);
    list_skins(data_dir)
}

/// Markiert einen bibliotheks-Skin als aktiv.
pub fn set_active_skin(data_dir: &Path, name: &str) -> Value {
    let mut idx = read_index(data_dir);
    idx["active"] = serde_json::json!(name);
    write_index(data_dir, &idx);
    list_skins(data_dir)
}

/// Entfernt einen Skin aus der Bibliothek.
pub fn delete_skin(data_dir: &Path, name: &str) -> Value {
    let mut idx = read_index(data_dir);
    let skins: Vec<Value> = idx
        .get("skins")
        .and_then(|s| s.as_array())
        .cloned()
        .unwrap_or_default();
    let mut kept = Vec::new();
    for s in skins {
        if let Some(n) = s.get("name").and_then(|n| n.as_str()) {
            if n == name {
                if let Some(f) = s.get("file").and_then(|x| x.as_str()) {
                    let _ = std::fs::remove_file(skins_dir(data_dir).join(f));
                }
            } else {
                kept.push(s);
            }
        }
    }
    idx["skins"] = serde_json::Value::Array(kept);
    if idx
        .get("active")
        .and_then(|v| v.as_str())
        .map(|a| a == name)
        .unwrap_or(false)
    {
        idx["active"] = Value::Null;
    }
    write_index(data_dir, &idx);
    list_skins(data_dir)
}

/// Holt das Minecraft-Profil (Skins + Capes) des aktiven Kontos.
pub fn minecraft_profile(data_dir: &Path) -> Value {
    let token = match mc_token(data_dir) {
        Some(t) => t,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let resp = reqwest::blocking::Client::new()
        .get("https://api.minecraftservices.com/minecraft/profile")
        .bearer_auth(&token)
        .header("Accept", "application/json")
        .send();
    match resp {
        Ok(r) if r.status().is_success() => match r.json::<Value>() {
            Ok(v) => v,
            Err(e) => serde_json::json!({ "error": e.to_string() }),
        },
        Ok(r) => serde_json::json!({ "error": format!("HTTP {}", r.status()) }),
        Err(e) => serde_json::json!({ "error": e.to_string() }),
    }
}

/// Lädt den aktuell bei Minecraft hinterlegten Skin in die Bibliothek.
pub fn download_current_skin(data_dir: &Path) -> Value {
    let prof = minecraft_profile(data_dir);
    if prof.get("error").is_some() {
        return prof;
    }
    let skins = prof.get("skins").and_then(|s| s.as_array());
    let url = skins
        .and_then(|arr| {
            arr.iter()
                .find(|s| s.get("state").and_then(|v| v.as_str()) == Some("ACTIVE"))
                .or_else(|| arr.first())
        })
        .and_then(|s| s.get("url").and_then(|u| u.as_str()))
        .map(|s| s.to_string());
    let url = match url {
        Some(u) => u,
        None => return serde_json::json!({ "error": "Keine Skin-URL im Profil gefunden" }),
    };
    let bytes = match reqwest::blocking::Client::new()
        .get(&url)
        .send()
        .and_then(|r| r.bytes())
    {
        Ok(b) => b.to_vec(),
        Err(e) => {
            return serde_json::json!({ "error": format!("Skin-Download fehlgeschlagen: {}", e) })
        }
    };
    let name = format!("minecraft-{}", now_secs());
    import_skin(data_dir, &name, &bytes)
}

/// Lädt einen Skin zum Minecraft-Konto hoch (und speichert ihn lokal).
/// Ohne Microsoft-Konto wird der Skin nur lokal abgelegt.
pub fn upload_skin(data_dir: &Path, name: &str, b64: &str, variant: &str) -> Value {
    let bytes = match b64_to_bytes(b64) {
        Ok(b) => b,
        Err(e) => return serde_json::json!({ "error": e }),
    };
    let variant = if variant == "slim" { "slim" } else { "classic" };

    let token = match mc_token(data_dir) {
        Some(t) => t,
        None => {
            let r = import_skin(data_dir, name, &bytes);
            return serde_json::json!({
                "ok": true,
                "mc_uploaded": false,
                "state": r.get("state").cloned().unwrap_or(Value::Null),
            });
        }
    };

    let part = reqwest::blocking::multipart::Part::bytes(bytes.clone())
        .file_name(format!("{}.png", sanitize_filename::sanitize(name)))
        .mime_str("image/png")
        .unwrap_or_else(|_| reqwest::blocking::multipart::Part::bytes(bytes.clone()));
    let form = reqwest::blocking::multipart::Form::new()
        .text("variant", variant.to_string())
        .part("file", part);

    let resp = reqwest::blocking::Client::new()
        .post("https://api.minecraftservices.com/minecraft/profile/skins")
        .bearer_auth(&token)
        .multipart(form)
        .send();

    match resp {
        Ok(r) if r.status().is_success() => {
            let r = import_skin(data_dir, name, &bytes);
            serde_json::json!({
                "ok": true,
                "mc_uploaded": true,
                "state": r.get("state").cloned().unwrap_or(Value::Null),
            })
        }
        Ok(r) => {
            let status = r.status();
            let body = r.text().unwrap_or_default();
            serde_json::json!({ "error": format!("Hochladen fehlgeschlagen ({}): {}", status, body) })
        }
        Err(e) => serde_json::json!({ "error": format!("Hochladen fehlgeschlagen: {}", e) }),
    }
}

/// Rüstet ein besitztes Cape aus (aktiviert es im Minecraft-Konto).
pub fn equip_cape(data_dir: &Path, cape_id: &str) -> Value {
    let token = match mc_token(data_dir) {
        Some(t) => t,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let resp = reqwest::blocking::Client::new()
        .put("https://api.minecraftservices.com/minecraft/profile/capes/active")
        .bearer_auth(&token)
        .json(&serde_json::json!({ "capeId": cape_id }))
        .send();
    match resp {
        Ok(r) if r.status().is_success() => serde_json::json!({ "ok": true }),
        Ok(r) => serde_json::json!({ "error": format!("HTTP {}", r.status()) }),
        Err(e) => serde_json::json!({ "error": e.to_string() }),
    }
}

/// Holt das öffentliche Minecraft-Profil (aktueller Skin) eines Spielers per
/// Minecraft-Name (kein Login erforderlich). Liefert Skin- und Cape-URLs sowie
/// Data-URLs (base64) für direkte Anzeige in der UI.
pub fn minecraft_profile_by_name(_data_dir: &Path, name: &str) -> Value {
    // 1) Name → UUID
    let lookup = format!("https://api.mojang.com/users/profiles/minecraft/{}", name);
    let id_resp = match reqwest::blocking::get(&lookup) {
        Ok(r) if r.status().is_success() => match r.json::<Value>() {
            Ok(v) => v,
            Err(e) => return serde_json::json!({ "error": format!("JSON parse: {}", e) }),
        },
        Ok(r) => return serde_json::json!({ "error": format!("Name lookup HTTP {}", r.status()) }),
        Err(e) => return serde_json::json!({ "error": format!("Name lookup failed: {}", e) }),
    };
    let uuid = match id_resp.get("id").and_then(|v| v.as_str()) {
        Some(s) => s,
        None => return serde_json::json!({ "error": "UUID not found for name" }),
    };

    // 2) Session profile → textures
    let sess_url = format!("https://sessionserver.mojang.com/session/minecraft/profile/{}", uuid);
    let sess = match reqwest::blocking::get(&sess_url) {
        Ok(r) if r.status().is_success() => match r.json::<Value>() {
            Ok(v) => v,
            Err(e) => return serde_json::json!({ "error": format!("Profile JSON parse: {}", e) }),
        },
        Ok(r) => return serde_json::json!({ "error": format!("Profile HTTP {}", r.status()) }),
        Err(e) => return serde_json::json!({ "error": format!("Profile fetch failed: {}", e) }),
    };

    // Extract textures property (base64)
    let props = sess.get("properties").and_then(|p| p.as_array()).cloned().unwrap_or_default();
    let mut textures_b64: Option<String> = None;
    for p in props {
        if p.get("name").and_then(|n| n.as_str()) == Some("textures") {
            textures_b64 = p.get("value").and_then(|v| v.as_str()).map(|s| s.to_string());
            break;
        }
    }
    let textures = if let Some(tb) = textures_b64 {
        match base64::engine::general_purpose::STANDARD.decode(tb) {
            Ok(b) => match String::from_utf8(b) {
                Ok(s) => match serde_json::from_str::<Value>(&s) {
                    Ok(v) => v,
                    Err(_) => serde_json::json!({}),
                },
                Err(_) => serde_json::json!({}),
            },
            Err(_) => serde_json::json!({}),
        }
    } else {
        serde_json::json!({})
    };

    let skin_url = textures
        .get("textures")
        .and_then(|t| t.get("SKIN"))
        .and_then(|s| s.get("url"))
        .and_then(|u| u.as_str())
        .map(|s| s.to_string());
    let cape_url = textures
        .get("textures")
        .and_then(|t| t.get("CAPE"))
        .and_then(|s| s.get("url"))
        .and_then(|u| u.as_str())
        .map(|s| s.to_string());

    // Try to fetch skin bytes and produce data URL if possible
    let skin_data_url = skin_url.as_ref().and_then(|url| match reqwest::blocking::get(url) {
        Ok(r) if r.status().is_success() => match r.bytes() {
            Ok(b) => Some(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(&b))),
            Err(_) => None,
        },
        _ => None,
    });

    let cape_data_url = cape_url.as_ref().and_then(|url| match reqwest::blocking::get(url) {
        Ok(r) if r.status().is_success() => match r.bytes() {
            Ok(b) => Some(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(&b))),
            Err(_) => None,
        },
        _ => None,
    });

    serde_json::json!({
        "name": name,
        "uuid": uuid,
        "skin_url": skin_url,
        "skin_data_url": skin_data_url,
        "cape_url": cape_url,
        "cape_data_url": cape_data_url,
        "raw_textures": textures
    })
}
