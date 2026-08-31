// Presence-Reporter für den Kollegen-Client-Launcher.
//
// Die im Spiel laufende Begleit-Mod schreibt `~/.kollegen/presence.json`
// ({server, name, timestamp}), sobald der Spieler auf einem Server ist, und
// löscht die Datei beim Verlassen. Dieser Reporter beobachtet die Datei und
// meldet die Presence an das externe Backend.
//
// Authentifizierung läuft über Discord: Der Launcher nutzt seinen gespeicherten
// Discord-Access-Token, tauscht ihn gegen ein Backend-Session-Token ein
// (`POST {backend}/auth`) und meldet dann die Presence:
//
//   POST   {backend}/auth           Body: {"discord_token"}            (-> session)
//   PUT    {backend}/presence       Body: {"server","name","timestamp"} (Bearer session)
//   DELETE {backend}/presence                                                (Bearer session)
//
// Die Mod fragt öffentlich `GET {backend}/presence?server=…` ab und erhält die
// Liste der MC-Namen, die gerade als Discord-authentifizierte Kollegen-User auf
// diesem Server online sind -> Kollegen.png Icon. Nur wer sich per Discord
// authentifiziert hat, landet in dieser Liste.
//
// Backend-URL aus den Launcher-Einstellungen bzw. KOLLEGEN_PRESENCE_BACKEND.

use log::{debug, warn};
use serde::{Deserialize, Serialize};
use serde_json;
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize)]
struct PresenceEntry {
    server: String,
    name: String,
    timestamp: u64,
}

const HEARTBEAT_MS: u64 = 20_000;
const POLL_MS: u64 = 2_000;

/// Backend-Session-Token (flüchtig, wird bei 401 verworfen und neu geholt).
static SESSION: Mutex<Option<String>> = Mutex::new(None);

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn now_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Liefert das aktive Minecraft-Profil (uuid + name) als JSON-Value, damit das
/// Backend bei `/auth` den Kollegen-User mit MC-Daten anlegen kann.
fn mc_profile_value(data_dir: &PathBuf) -> Option<serde_json::Value> {
    let accounts = crate::utils::load_json::<Vec<crate::types::Account>>(
        &crate::utils::accounts_file(data_dir),
        Vec::new(),
    );
    let acc = accounts.into_iter().next()?;
    if acc.uuid.is_empty() {
        return None;
    }
    Some(serde_json::json!({
        "uuid": acc.uuid,
        "name": acc.username,
        "accounts": [{ "type": "microsoft", "name": acc.username }]
    }))
}

/// Liefert einen gültigen Discord-Access-Token (refresht ihn ggf.).
fn discord_access_token(data_dir: &PathBuf) -> Option<String> {
    let tok = crate::discord_auth::load_token(data_dir)?;
    if !tok.access_token.is_empty() {
        if let Some(exp) = tok.expires_at {
            if exp > now_secs() + 60 {
                return Some(tok.access_token.clone());
            }
        }
    }
    if let Some(rt) = &tok.refresh_token {
        if let Some(at) = refresh_discord_token(rt, data_dir) {
            return Some(at);
        }
    }
    Some(tok.access_token)
}

/// Versucht, einen neuen Discord-Access-Token über den Refresh-Token zu holen.
fn refresh_discord_token(refresh: &str, data_dir: &PathBuf) -> Option<String> {
    let params = [
        ("client_id", crate::discord_client_id()),
        ("grant_type", "refresh_token"),
        ("refresh_token", refresh),
    ];
    let client = reqwest::blocking::Client::new();
    let resp = client
        .post("https://discord.com/api/v10/oauth2/token")
        .form(&params)
        .send()
        .ok()?;
    if !resp.status().is_success() {
        return None;
    }
    let v: serde_json::Value = resp.json().ok()?;
    let at = v.get("access_token")?.as_str()?.to_string();
    let rt = v
        .get("refresh_token")
        .and_then(|x| x.as_str())
        .map(String::from);
    let exp = v
        .get("expires_in")
        .and_then(|x| x.as_u64())
        .map(|s| now_secs() + s);
    let mut base = crate::discord_auth::load_token(data_dir)?;
    base.access_token = at.clone();
    if let Some(rt) = rt {
        base.refresh_token = Some(rt);
    }
    base.expires_at = exp;
    crate::discord_auth::save_token(data_dir, &base);
    Some(at)
}

/// Standard-Backend-URL des Kollegen-Servers. Nutzer müssen nichts konfigurieren –
/// Presence/Freunde funktionieren out-of-the-box. Env/Setting überschreiben das.
const DEFAULT_PRESENCE_BACKEND: &str = "http://5.175.192.69:8080";

/// Liefert die konfigurierte Backend-URL (Env > Setting > Default).
fn backend_url(data_dir: &PathBuf) -> Option<String> {
    let env = std::env::var("KOLLEGEN_PRESENCE_BACKEND").ok();
    if let Some(b) = env.filter(|s| !s.trim().is_empty()) {
        return Some(b.trim().trim_end_matches('/').to_string());
    }
    let settings = crate::utils::load_json::<crate::types::Settings>(
        &crate::utils::settings_file(data_dir),
        crate::types::Settings::default(),
    );
    let b = settings.presence_backend.trim().to_string();
    if b.is_empty() {
        Some(DEFAULT_PRESENCE_BACKEND.to_string())
    } else {
        Some(b.trim_end_matches('/').to_string())
    }
}

/// Holt (falls nötig) ein Backend-Session-Token via Discord-Auth.
fn ensure_session(
    client: &reqwest::blocking::Client,
    backend: &str,
    discord_token: &str,
    data_dir: &PathBuf,
) -> Option<String> {
    if let Some(s) = SESSION.lock().unwrap().clone() {
        return Some(s);
    }
    let url = format!("{}/auth", backend);
    let mut body = serde_json::json!({ "discord_token": discord_token });
    if let Some(profile) = mc_profile_value(data_dir) {
        body["profile"] = profile;
    }
    let resp = client.post(&url).json(&body).send();
    let resp = match resp {
        Ok(r) => r,
        Err(e) => {
            debug!("Presence-Auth fehlgeschlagen: {}", e);
            return None;
        }
    };
    if !resp.status().is_success() {
        debug!("Presence-Auth lieferte {}", resp.status());
        return None;
    }
    let v: serde_json::Value = match resp.json() {
        Ok(j) => j,
        Err(_) => return None,
    };
    let tok = v.get("token")?.as_str()?.to_string();
    *SESSION.lock().unwrap() = Some(tok.clone());
    Some(tok)
}

/// Authentifizierte Anfrage-Hilfe für die sozialen Endpunkte (Directory/Freunde).
///
/// Wichtig: Der hier erzeugte Client MUSS ein kurzes Connect/Total-Timeout tragen.
/// Früher stand hier `Client::new()` (= kein Timeout). Wird das Backend
/// (`5.175.192.69:8080` etc.) nicht erreicht, hängt der `POST /auth` dann bis zum
/// OS-Connect-Timeout – und da `sync_social`/`kollegen_me` auf dem Hauptthread
/// laufen können, friert der komplette Launcher-Start (Weißschirm, "lädt 20–30
/// Minuten") ein. Mit begrenztem Timeout schlägt der Versuch in Sekunden fehl.
fn authed_request(
    data_dir: &PathBuf,
) -> Option<(reqwest::blocking::Client, String, String)> {
    let backend = backend_url(data_dir)?;
    let discord_token = discord_access_token(data_dir)?;
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(3))
        .connect_timeout(Duration::from_secs(3))
        .build()
        .ok()?;
    let session = ensure_session(&client, &backend, &discord_token, data_dir)?;
    Some((client, backend, session))
}

/// Holt das eigene Profil (GET /me).
///
/// Die Freundesliste + social.json für die Mod schreibt der Hintergrund-
/// Presence-Loop periodisch selbst (sync_social); hier wird sie NICHT noch
/// einmal nachgezogen, sonst machten wir beim Öffnen des Socials-Tabs mehrere
/// redundante Blocking-HTTP-Calls in Serie (früher: /me + /me + /friends).
pub fn kollegen_me(data_dir: &PathBuf) -> serde_json::Value {
    me_value(data_dir)
}

fn me_value(data_dir: &PathBuf) -> serde_json::Value {
    let (client, backend, session) = match authed_request(data_dir) {
        Some(x) => x,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let url = format!("{}/me", backend);
    match client
        .get(&url)
        .header("Authorization", format!("Bearer {}", session))
        .send()
    {
        Ok(r) if r.status().is_success() => r.json().unwrap_or(serde_json::json!({})),
        Ok(r) if r.status() == 401 => {
            *SESSION.lock().unwrap() = None;
            serde_json::json!({ "error": "not_authenticated" })
        }
        _ => serde_json::json!({ "error": "request_failed" }),
    }
}

fn friends_value(data_dir: &PathBuf) -> serde_json::Value {
    let (client, backend, session) = match authed_request(data_dir) {
        Some(x) => x,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let url = format!("{}/friends", backend);
    match client
        .get(&url)
        .header("Authorization", format!("Bearer {}", session))
        .send()
    {
        Ok(r) if r.status().is_success() => r.json().unwrap_or(serde_json::json!([])),
        Ok(r) if r.status() == 401 => {
            *SESSION.lock().unwrap() = None;
            serde_json::json!({ "error": "not_authenticated" })
        }
        _ => serde_json::json!({ "error": "request_failed" }),
    }
}

/// Schreibt ~/.kollegen/social.json (eigenes Profil + Freunde) für die Mod.
pub fn sync_social(data_dir: &PathBuf) {
    let me = me_value(data_dir);
    if me.get("error").is_some() {
        return;
    }
    let friends = friends_value(data_dir);
    let out = serde_json::json!({ "me": me, "friends": friends });
    if let Some(home) = dirs::home_dir() {
        let dir = home.join(".kollegen");
        if std::fs::create_dir_all(&dir).is_ok() {
            if let Ok(s) = serde_json::to_string_pretty(&out) {
                let _ = std::fs::write(dir.join("social.json"), s);
            }
        }
    }
}

/// Bearbeitet ausstehende Freundes-Code-Anfragen, die die Mod (im Spiel)
/// nach ~/.kollegen/friend_add.json schreibt.
pub fn process_pending_friend_add(data_dir: &PathBuf) {
    if let Some(home) = dirs::home_dir() {
        let file = home.join(".kollegen").join("friend_add.json");
        if let Ok(text) = std::fs::read_to_string(&file) {
            if let Ok(v) = serde_json::from_str::<serde_json::Value>(&text) {
                if let Some(code) = v.get("code").and_then(|c| c.as_str()) {
                    let _ = add_friend_by_code(data_dir, code);
                }
            }
            let _ = std::fs::remove_file(&file);
        }
    }
}

/// Fügt einen Freund über dessen Freundes-Code hinzu.
pub fn add_friend_by_code(data_dir: &PathBuf, code: &str) -> serde_json::Value {
    let (client, backend, session) = match authed_request(data_dir) {
        Some(x) => x,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let url = format!("{}/friends", backend);
    match client
        .post(&url)
        .header("Authorization", format!("Bearer {}", session))
        .json(&serde_json::json!({ "code": code }))
        .send()
    {
        Ok(r) if r.status().is_success() => serde_json::json!({ "ok": true }),
        Ok(r) if r.status() == 401 => {
            *SESSION.lock().unwrap() = None;
            serde_json::json!({ "error": "not_authenticated" })
        }
        Ok(r) => serde_json::json!({ "error": format!("HTTP {}", r.status()) }),
        Err(e) => serde_json::json!({ "error": e.to_string() }),
    }
}

/// Eigene Freundesliste (Profile inkl. Status/Server).
pub fn kollegen_friends(data_dir: &PathBuf) -> serde_json::Value {
    friends_value(data_dir)
}

/// Fügt einen Freund über dessen Freundes-Code hinzu.
pub fn kollegen_friend_add(data_dir: &PathBuf, code: &str) -> serde_json::Value {
    add_friend_by_code(data_dir, code)
}

/// Entfernt einen Freund (per id).
pub fn kollegen_friend_remove(data_dir: &PathBuf, target_id: &str) -> serde_json::Value {
    let (client, backend, session) = match authed_request(data_dir) {
        Some(x) => x,
        None => return serde_json::json!({ "error": "not_authenticated" }),
    };
    let url = format!("{}/friends", backend);
    match client
        .delete(&url)
        .header("Authorization", format!("Bearer {}", session))
        .json(&serde_json::json!({ "target_id": target_id }))
        .send()
    {
        Ok(r) if r.status().is_success() => serde_json::json!({ "ok": true }),
        Ok(r) if r.status() == 401 => {
            *SESSION.lock().unwrap() = None;
            serde_json::json!({ "error": "not_authenticated" })
        }
        Ok(r) => serde_json::json!({ "error": format!("HTTP {}", r.status()) }),
        Err(e) => serde_json::json!({ "error": e.to_string() }),
    }
}

fn read_entry(path: &PathBuf) -> Option<PresenceEntry> {
    let text = std::fs::read_to_string(path).ok()?;
    let v: serde_json::Value = serde_json::from_str(&text).ok()?;
    let server = v.get("server")?.as_str()?.to_string();
    let name = v.get("name")?.as_str()?.to_string();
    if server.is_empty() || name.is_empty() {
        return None;
    }
    let timestamp = v
        .get("timestamp")
        .and_then(|t| t.as_u64())
        .unwrap_or_else(now_ms);
    Some(PresenceEntry { server, name, timestamp })
}

fn send_upsert(
    client: &reqwest::blocking::Client,
    backend: &str,
    session: &str,
    e: &PresenceEntry,
) -> Result<(), String> {
    let url = format!("{}/presence", backend);
    let resp = client
        .put(&url)
        .header("Authorization", format!("Bearer {}", session))
        .json(e)
        .send()
        .map_err(|e| e.to_string())?;
    match resp.status() {
        s if s.is_success() => Ok(()),
        s if s == reqwest::StatusCode::UNAUTHORIZED => {
            *SESSION.lock().unwrap() = None;
            Err("401".into())
        }
        s => Err(format!("HTTP {}", s)),
    }
}

fn send_delete(
    client: &reqwest::blocking::Client,
    backend: &str,
    session: &str,
    e: &PresenceEntry,
) -> Result<(), String> {
    let url = format!("{}/presence", backend);
    let body = serde_json::json!({ "server": e.server, "name": e.name });
    let resp = client
        .delete(&url)
        .header("Authorization", format!("Bearer {}", session))
        .json(&body)
        .send()
        .map_err(|e| e.to_string())?;
    match resp.status() {
        s if s.is_success() => Ok(()),
        s if s == reqwest::StatusCode::UNAUTHORIZED => {
            *SESSION.lock().unwrap() = None;
            Err("401".into())
        }
        s => Err(format!("HTTP {}", s)),
    }
}

/// Startet den Hintergrund-Reporter-Thread. Einrichtungsfehler sind harmlos –
/// der Thread läuft einfach nicht (das Spiel zeigt dann kein Presence-Icon).
pub fn start(data_dir: PathBuf) {
    std::thread::Builder::new()
        .name("kollegen-presence".into())
        .spawn(move || run(data_dir))
        .ok();
}

fn run(data_dir: PathBuf) {
    let presence_path = match dirs::home_dir() {
        Some(h) => h.join(".kollegen").join("presence.json"),
        None => {
            warn!("Konnte Home-Verzeichnis nicht ermitteln – Presence deaktiviert.");
            return;
        }
    };

    let client = match reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(5))
        .connect_timeout(Duration::from_secs(3))
        .build()
    {
        Ok(c) => c,
        Err(e) => {
            warn!("Presence-HTTP-Client konnte nicht erstellt werden: {}", e);
            return;
        }
    };

    let mut last_reported: Option<PresenceEntry> = None;
    let mut last_heartbeat: u64 = 0;
    let mut last_social: u64 = 0;
    let mut auth_failures: u32 = 0;

    loop {
        std::thread::sleep(Duration::from_millis(POLL_MS));

        // Backoff: ist das Backend nicht erreichbar, hämmert der Loop sonst im
        // Sekundentakt auf die tote IP (5.175.192.69) und blockiert die
        // Presence-Network-Arbeit. Bei wiederholtem Fehlschlag warten wir
        // zunehmend länger (bis ~30 s), statt permanent zu raten.
        if auth_failures > 0 {
            let backoff = std::cmp::min(auth_failures, 15) as u64;
            std::thread::sleep(Duration::from_secs(backoff));
        }

        let backend = match backend_url(&data_dir) {
            Some(b) => b,
            None => {
                *SESSION.lock().unwrap() = None;
                last_reported = None;
                continue;
            }
        };

        let discord_token = match discord_access_token(&data_dir) {
            Some(t) => t,
            None => {
                *SESSION.lock().unwrap() = None;
                last_reported = None;
                continue;
            }
        };

        let session = match ensure_session(&client, &backend, &discord_token, &data_dir) {
            Some(s) => {
                auth_failures = 0;
                s
            }
            None => {
                auth_failures = auth_failures.saturating_add(1);
                last_reported = None;
                continue;
            }
        };

        let now = now_ms();
        let entry = read_entry(&presence_path);

        match entry {
            None => {
                if let Some(prev) = last_reported.take() {
                    if let Err(err) = send_delete(&client, &backend, &session, &prev) {
                        debug!("Presence-Delete fehlgeschlagen: {}", err);
                    }
                }
            }
            Some(e) => {
                let changed = match &last_reported {
                    Some(p) => p.server != e.server || p.name != e.name,
                    None => true,
                };
                let need_heartbeat = now - last_heartbeat > HEARTBEAT_MS;
                if changed || need_heartbeat {
                    match send_upsert(&client, &backend, &session, &e) {
                        Ok(()) => {
                            last_reported = Some(e);
                            last_heartbeat = now;
                        }
                    Err(err) => debug!("Presence-Meldung fehlgeschlagen: {}", err),
                }
            }
        }
        }

        // Profil + Freundesliste für die Mod schreiben und ausstehende
        // Freundes-Code-Anfragen der Mod bearbeiten.
        if now - last_social > 5000 {
            last_social = now;
            sync_social(&data_dir);
            process_pending_friend_add(&data_dir);
        }
    }
}
