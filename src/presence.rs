// Presence-Reporter für den Kollegen-Client-Launcher.
//
// Die im Spiel laufende Begleit-Mod schreibt `~/.kollegen/presence.json`
// ({server, name, timestamp}), sobald der Spieler auf einem Server ist, und
// löscht die Datei beim Verlassen. Dieser Reporter beobachtet die Datei und
// meldet die Presence an das externe Backend:
//
//   PUT    {backend}/presence        Body: {"server","name","timestamp"}  (Upsert)
//   DELETE {backend}/presence        Body: {"server","name"}              (Offline)
//
// Die Mod fragt später `GET {backend}/presence?server=…` ab, um die Liste der
// Kollegen-Namen zu erhalten. Optional: Header `Authorization: Bearer <token>`.
//
// Backend-URL und Token kommen aus den Launcher-Einstellungen bzw. den
// Umgebungsvariablen KOLLEGEN_PRESENCE_BACKEND / KOLLEGEN_PRESENCE_TOKEN.

use log::{debug, warn};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use std::time::{Duration, SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize)]
struct PresenceEntry {
    server: String,
    name: String,
    timestamp: u64,
}

const HEARTBEAT_MS: u64 = 20_000;
const POLL_MS: u64 = 2_000;

/// Startet den Hintergrund-Reporter-Thread. Einrichtungsfehler sind harmlos –
/// der Thread läuft einfach nicht (das Spiel zeigt dann kein Presence-Icon).
pub fn start(data_dir: PathBuf) {
    std::thread::Builder::new()
        .name("kollegen-presence".into())
        .spawn(move || run(data_dir))
        .ok();
}

fn now_ms() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis() as u64)
        .unwrap_or(0)
}

fn backend_and_token(data_dir: &PathBuf) -> Option<(String, String)> {
    let env_backend = std::env::var("KOLLEGEN_PRESENCE_BACKEND").ok();
    let env_token = std::env::var("KOLLEGEN_PRESENCE_TOKEN").ok();

    let (backend, token) = if let Some(b) = env_backend.filter(|s| !s.trim().is_empty()) {
        (b.trim().trim_end_matches('/').to_string(), env_token.unwrap_or_default())
    } else {
        let settings = crate::utils::load_json::<crate::types::Settings>(
            &crate::utils::settings_file(data_dir),
            crate::types::Settings::default(),
        );
        let b = settings.presence_backend.trim().to_string();
        if b.is_empty() {
            return None;
        }
        (
            b.trim_end_matches('/').to_string(),
            settings.presence_token.trim().to_string(),
        )
    };

    if backend.is_empty() {
        None
    } else {
        Some((backend, token))
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

fn auth_header(token: &str) -> Option<(String, String)> {
    if token.is_empty() {
        None
    } else {
        Some(("Authorization".into(), format!("Bearer {}", token)))
    }
}

fn send_upsert(client: &reqwest::blocking::Client, backend: &str, token: &str, e: &PresenceEntry) -> Result<(), String> {
    let url = format!("{}/presence", backend);
    let mut req = client.put(&url).json(e).header("Content-Type", "application/json");
    if let Some((k, v)) = auth_header(token) {
        req = req.header(k, v);
    }
    let resp = req.send().map_err(|e| e.to_string())?;
    if resp.status().is_success() {
        Ok(())
    } else {
        Err(format!("HTTP {}", resp.status()))
    }
}

fn send_delete(client: &reqwest::blocking::Client, backend: &str, token: &str, e: &PresenceEntry) -> Result<(), String> {
    let url = format!("{}/presence", backend);
    let body = serde_json::json!({ "server": e.server, "name": e.name });
    let mut req = client.delete(&url).json(&body).header("Content-Type", "application/json");
    if let Some((k, v)) = auth_header(token) {
        req = req.header(k, v);
    }
    let resp = req.send().map_err(|e| e.to_string())?;
    if resp.status().is_success() {
        Ok(())
    } else {
        Err(format!("HTTP {}", resp.status()))
    }
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

    loop {
        std::thread::sleep(Duration::from_millis(POLL_MS));
        let (backend, token) = match backend_and_token(&data_dir) {
            Some(bt) => bt,
            None => {
                // Kein Backend konfiguriert → gemeldete Presence verwerfen (kein Request).
                last_reported = None;
                continue;
            }
        };

        let now = now_ms();
        let entry = read_entry(&presence_path);

        match entry {
            None => {
                if let Some(prev) = last_reported.take() {
                    if let Err(err) = send_delete(&client, &backend, &token, &prev) {
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
                    match send_upsert(&client, &backend, &token, &e) {
                        Ok(()) => {
                            last_reported = Some(e);
                            last_heartbeat = now;
                        }
                        Err(err) => debug!("Presence-Meldung fehlgeschlagen: {}", err),
                    }
                }
            }
        }
    }
}


