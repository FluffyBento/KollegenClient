use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
use lazy_static::lazy_static;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::io::{Read, Write};
use std::net::TcpListener;
use std::path::Path;
use std::thread;
use std::time::{Duration, Instant};

const AUTHORIZE_ENDPOINT: &str = "https://discord.com/oauth2/authorize";
const TOKEN_ENDPOINT: &str = "https://discord.com/api/v10/oauth2/token";
const USER_ENDPOINT: &str = "https://discord.com/api/v10/users/@me";
// Scopes requested on the Discord OAuth authorize page. `identify` + `guilds`
// are standard (non-privileged) scopes and work for every application.
//
// NOTE: `relationships` (which previously lived here and powers the REST
// friend list) is a *privileged* scope: Discord rejects the authorize request
// with "Invalid scope: relationships" unless it is explicitly enabled for the
// application in the developer portal (OAuth2 → Scopes). It is therefore not
// requested here – the friends list still works via Rich Presence (RPC), which
// does not need any OAuth scope. If the scope gets enabled later, the REST
// friend fetch (`fetch_friends`) starts working again automatically.
const SCOPES: &str = "identify guilds";
const RELATIONSHIPS_ENDPOINT: &str = "https://discord.com/api/v10/users/@me/relationships";
/// Fixed localhost port the browser is redirected back to. This exact URI must
/// be registered as a Redirect URI in the Discord application's OAuth2 settings.
const CALLBACK_PORT: u16 = 31337;
const REDIRECT_URI: &str = "http://127.0.0.1:31337/callback";

#[derive(Serialize, Deserialize, Clone)]
pub struct DiscordUser {
    pub id: String,
    pub username: String,
    #[serde(default)]
    pub global_name: Option<String>,
    #[serde(default)]
    pub avatar: Option<String>,
    #[serde(default)]
    pub discriminator: Option<String>,
}

impl DiscordUser {
    pub fn display_name(&self) -> String {
        self.global_name
            .clone()
            .filter(|s| !s.is_empty())
            .unwrap_or_else(|| self.username.clone())
    }

    pub fn avatar_url(&self) -> Option<String> {
        self.avatar
            .as_ref()
            .map(|a| format!("https://cdn.discordapp.com/avatars/{}/{}.png", self.id, a))
    }
}

#[derive(Serialize, Deserialize, Clone)]
pub struct Token {
    pub access_token: String,
    #[serde(default)]
    pub refresh_token: Option<String>,
    #[serde(default)]
    pub expires_at: Option<u64>,
    #[serde(default)]
    pub user: Option<DiscordUser>,
}

#[derive(Deserialize)]
struct TokenResponse {
    access_token: String,
    #[serde(default)]
    refresh_token: Option<String>,
    #[serde(default)]
    expires_in: Option<u64>,
}

fn token_path(data_dir: &Path) -> std::path::PathBuf {
    data_dir.join("discord_token.json")
}

fn now_secs() -> u64 {
    std::time::SystemTime::now()
        .duration_since(std::time::UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

pub fn load_token(data_dir: &Path) -> Option<Token> {
    let p = token_path(data_dir);
    let s = std::fs::read_to_string(&p).ok()?;
    serde_json::from_str(&s).ok()
}

pub fn save_token(data_dir: &Path, token: &Token) {
    let p = token_path(data_dir);
    if let Ok(s) = serde_json::to_string_pretty(token) {
        let _ = std::fs::write(&p, s);
    }
}

pub fn delete_token(data_dir: &Path) {
    let _ = std::fs::remove_file(token_path(data_dir));
}

/// Returns the persisted login state for the frontend.
pub fn status(data_dir: &Path) -> serde_json::Value {
    match load_token(data_dir) {
        Some(t) => serde_json::json!({ "logged_in": true, "user": t.user }),
        None => serde_json::json!({ "logged_in": false }),
    }
}

pub fn logout(data_dir: &Path) {
    delete_token(data_dir);
}

lazy_static! {
    static ref OAUTH_FRIENDS_CACHE: std::sync::Mutex<(std::time::Instant, Vec<serde_json::Value>)> =
        std::sync::Mutex::new((std::time::Instant::now(), Vec::new()));
}

/// Fetches the authenticated user's Discord friends via the REST API
/// (`/users/@me/relationships`, requires the privileged `relationships` OAuth
/// scope). Returns lightweight friend entries (no rich-presence join secret);
/// `discord_social` merges these with the RPC-sourced friends (which carry live
/// presence). Network/permission failures degrade gracefully to an empty list.
/// Results are cached for 60s so the Socials tab can poll without hammering
/// the Discord API.
pub fn fetch_friends(data_dir: &Path) -> Vec<serde_json::Value> {
    {
        let cache = OAUTH_FRIENDS_CACHE.lock().unwrap();
        if cache.0.elapsed() < std::time::Duration::from_secs(60) {
            return cache.1.clone();
        }
    }
    let token = match load_token(data_dir) {
        Some(t) => t,
        None => return Vec::new(),
    };

    // Collect the raw friend entries first.
    let mut base: Vec<(
        String,
        String,
        Option<String>,
        Option<String>,
        String,
        Option<String>,
        bool,
    )> = Vec::new();
    if let Ok(resp) = reqwest::blocking::Client::new()
        .get(RELATIONSHIPS_ENDPOINT)
        .bearer_auth(&token.access_token)
        .send()
    {
        if let Ok(arr) = resp.json::<Vec<serde_json::Value>>() {
            for r in arr {
                // type 1 == friend (2 = blocked, 3 = incoming, 4 = outgoing).
                if r.get("type").and_then(|t| t.as_i64()) != Some(1) {
                    continue;
                }
                let user = match r.get("user") {
                    Some(u) => u,
                    None => continue,
                };
                let id = user
                    .get("id")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                if id.is_empty() {
                    continue;
                }
                let username = user
                    .get("username")
                    .and_then(|v| v.as_str())
                    .unwrap_or("")
                    .to_string();
                let global_name = user
                    .get("global_name")
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                let avatar = user.get("avatar").and_then(|v| v.as_str());
                let avatar_url = avatar.map(|a| {
                    let ext = if a.starts_with("a_") { "gif" } else { "png" };
                    format!("https://cdn.discordapp.com/avatars/{}/{}.{}", id, a, ext)
                });
                let presence = r.get("presence");
                let status = presence
                    .and_then(|p| p.get("status"))
                    .and_then(|v| v.as_str())
                    .unwrap_or("offline")
                    .to_string();
                let game = presence
                    .and_then(|p| p.get("activities"))
                    .and_then(|a| a.as_array())
                    .and_then(|a| a.first())
                    .and_then(|act| act.get("name"))
                    .and_then(|n| n.as_str())
                    .map(|s| s.to_string());
                base.push((
                    id,
                    username,
                    global_name,
                    avatar_url,
                    status,
                    game,
                    presence.is_some(),
                ));
            }
        }
    }

    // Own guild ids -> names (needs the `guilds` scope) for nicer "join server"
    // labels. Missing names (scope not granted / fetch failed) are tolerated.
    let mut guild_names: std::collections::HashMap<String, String> =
        std::collections::HashMap::new();
    if let Ok(resp) = reqwest::blocking::Client::new()
        .get("https://discord.com/api/v10/users/@me/guilds")
        .bearer_auth(&token.access_token)
        .send()
    {
        if let Ok(arr) = resp.json::<Vec<serde_json::Value>>() {
            for g in arr {
                if let (Some(id), Some(name)) = (
                    g.get("id").and_then(|v| v.as_str()),
                    g.get("name").and_then(|v| v.as_str()),
                ) {
                    guild_names.insert(id.to_string(), name.to_string());
                }
            }
        }
    }

    // `mutual_guilds` is NOT part of the relationship payload. It lives on the
    // per-user profile endpoint, whose response carries a top-level
    // `mutual_guilds` array of `{ id, nick }`. We fetch it per friend. The whole
    // result is cached 60s at the call site to limit the number of requests.
    let client = reqwest::blocking::Client::new();
    let mut result: Vec<serde_json::Value> = Vec::new();
    for (id, username, global_name, avatar_url, status, game, presence_known) in base {
        let mut mutual: Vec<String> = Vec::new();
        if let Ok(resp) = client
            .get(format!(
                "https://discord.com/api/v10/users/{}/profile?with_mutual_guilds=true",
                id
            ))
            .bearer_auth(&token.access_token)
            .send()
        {
            if let Ok(profile) = resp.json::<serde_json::Value>() {
                if let Some(arr) = profile.get("mutual_guilds").and_then(|m| m.as_array()) {
                    for g in arr {
                        if let Some(gid) = g.get("id").and_then(|v| v.as_str()) {
                            mutual.push(gid.to_string());
                        }
                    }
                }
            }
        }
        let mutual_guilds: Vec<serde_json::Value> = mutual
            .iter()
            .map(|gid| {
                serde_json::json!({
                    "id": gid,
                    "name": guild_names.get(gid).cloned().unwrap_or_default(),
                })
            })
            .collect();
        result.push(serde_json::json!({
            "id": id,
            "username": username,
            "global_name": global_name,
            "avatar_url": avatar_url,
            "status": status,
            "game": game,
            "version": null,
            "join_secret": null,
            "presence_known": presence_known,
            "kollegen": false,
            "mutual_guilds": mutual_guilds,
        }));
    }

    let mut cache = OAUTH_FRIENDS_CACHE.lock().unwrap();
    *cache = (std::time::Instant::now(), result.clone());
    result
}

/// Aktive Login-Session (PKCE-Verifier + State). Wird global gehalten, damit
/// der (einzige) gebundene Callback-Server immer gegen die *neueste* Session
/// validiert – auch wenn der Nutzer den Login mehrfach auslöst oder einen
/// abbricht und erneut startet. Sonst prüft ein noch lebender alter Server
/// gegen ein veraltetes State und wirft "State stimmt nicht überein".
#[derive(Clone)]
struct OAuthSession {
    verifier: String,
    state: String,
}

lazy_static! {
    static ref OAUTH_SESSION: std::sync::Mutex<Option<OAuthSession>> =
        std::sync::Mutex::new(None);
}

fn random_base64url(bytes: usize) -> String {
    use rand::Rng;
    let mut buf = vec![0u8; bytes];
    rand::thread_rng().fill(&mut buf[..]);
    URL_SAFE_NO_PAD.encode(&buf)
}

fn pkce_challenge(verifier: &str) -> String {
    let mut hasher = Sha256::new();
    hasher.update(verifier.as_bytes());
    URL_SAFE_NO_PAD.encode(hasher.finalize())
}

fn fetch_user(access_token: &str) -> Result<DiscordUser, String> {
    let client = reqwest::blocking::Client::new();
    let resp = client
        .get(USER_ENDPOINT)
        .bearer_auth(access_token)
        .send()
        .map_err(|e| format!("Konnte Discord-Nutzer nicht laden: {}", e))?;
    if !resp.status().is_success() {
        return Err(format!("Discord antwortete mit {}", resp.status()));
    }
    resp.json().map_err(|e| e.to_string())
}

fn exchange_token(code: &str, verifier: &str, data_dir: &Path) -> Result<(), String> {
    let client = reqwest::blocking::Client::new();
    let params: Vec<(&str, &str)> = vec![
        ("client_id", crate::discord_client_id()),
        ("grant_type", "authorization_code"),
        ("code", code),
        ("redirect_uri", REDIRECT_URI),
        ("code_verifier", verifier),
    ];
    let resp = client
        .post(TOKEN_ENDPOINT)
        .form(&params)
        .send()
        .map_err(|e| format!("Token-Tausch fehlgeschlagen: {}", e))?;
    if !resp.status().is_success() {
        let status = resp.status();
        let body = resp.text().unwrap_or_default();
        return Err(format!("Discord antwortete mit {}: {}", status, body));
    }
    let tr: TokenResponse = resp.json().map_err(|e| e.to_string())?;
    let user = fetch_user(&tr.access_token)?;
    let token = Token {
        access_token: tr.access_token,
        refresh_token: tr.refresh_token,
        expires_at: tr.expires_in.map(|s| now_secs() + s),
        user: Some(user),
    };
    save_token(data_dir, &token);
    // Invalidate the cached OAuth friend list so it is refreshed for the new user.
    OAUTH_FRIENDS_CACHE.lock().unwrap().0 =
        std::time::Instant::now() - std::time::Duration::from_secs(120);
    Ok(())
}

/// Runs a minimal localhost HTTP server that waits for Discord's redirect,
/// exchanges the code for a token, and shows a "done" page in the browser.
/// State/Verifier werden aus dem globalen `OAUTH_SESSION` gelesen, damit auch
/// ein bereits laufender Server (von einem vorherigen Login-Versuch) den
/// aktuellen State akzeptiert.
fn run_callback_server(data_dir: std::path::PathBuf) {
    let listener = match TcpListener::bind(("127.0.0.1", CALLBACK_PORT)) {
        Ok(l) => l,
        Err(e) => {
            // Port schon belegt (z. B. ein vorheriger Callback-Server aus diesem
            // oder einem anderen Prozess läuft noch). Wir verlassen uns darauf,
            // dass jener Server den Redirect bearbeitet – er validiert gegen
            // dieselbe globale Session.
            eprintln!(
                "[discord_auth] Callback-Server konnte Port {} nicht binden (läuft evtl. schon): {}",
                CALLBACK_PORT, e
            );
            return;
        }
    };
    let _ = listener.set_nonblocking(true);
    let deadline = Instant::now() + Duration::from_secs(300);

    while Instant::now() < deadline {
        match listener.accept() {
            Ok((mut stream, _)) => {
                let mut buf = [0u8; 4096];
                let n = match stream.read(&mut buf) {
                    Ok(n) if n > 0 => n,
                    _ => {
                        let _ = stream.write_all(b"HTTP/1.1 400 Bad Request\r\n\r\n");
                        break;
                    }
                };
                let req = String::from_utf8_lossy(&buf[..n]);
                let first_line = req.lines().next().unwrap_or("");
                let path = first_line.split_whitespace().nth(1).unwrap_or("");
                let query = path.splitn(2, '?').nth(1).unwrap_or("");

                let mut code = String::new();
                let mut returned_state = String::new();
                for kv in query.split('&') {
                    let mut it = kv.splitn(2, '=');
                    let k = it.next().unwrap_or("");
                    let v = it.next().unwrap_or("");
                    if k == "code" {
                        code = v.to_string();
                    } else if k == "state" {
                        // Discord kann State percent-kodieren – sicher decodieren.
                        returned_state = urlencoding::decode(v).unwrap_or_default().to_string();
                    }
                }

                let (status_line, body): (&str, String) = {
                    let session = OAUTH_SESSION.lock().unwrap().clone();
                    match session {
                        None => (
                            "HTTP/1.1 403 Forbidden",
                            "<h2>Kein aktiver Login-Vorgang. Bitte erneut versuchen.</h2>"
                                .to_string(),
                        ),
                        Some(s) => {
                            if code.is_empty() {
                                (
                                    "HTTP/1.1 400 Bad Request",
                                    "<h2>Authentifizierung fehlgeschlagen.</h2>".to_string(),
                                )
                            } else if returned_state != s.state {
                                (
                                    "HTTP/1.1 403 Forbidden",
                                    "<h2>State stimmt nicht überein. Schließe ggf. weitere Launcher-Fenster und starte den Login erneut.</h2>".to_string(),
                                )
                            } else {
                                match exchange_token(&code, &s.verifier, &data_dir) {
                                    Ok(()) => (
                                        "HTTP/1.1 200 OK",
                                        "<h2>Erfolgreich mit Discord verbunden!</h2><p>Du kannst dieses Fenster schließen.</p>".to_string(),
                                    ),
                                    Err(e) => (
                                        "HTTP/1.1 500 Internal Server Error",
                                        format!("<h2>Fehler:</h2><pre>{}</pre>", e),
                                    ),
                                }
                            }
                        }
                    }
                };

                let html = format!(
                    "<!doctype html><html><head><meta charset='utf-8'><title>Kollegen Client</title></head><body style='font-family:sans-serif;padding:2rem;'>{body}</body></html>"
                );
                let response = format!(
                    "{}\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: {}\r\n\r\n{}",
                    status_line,
                    html.len(),
                    html
                );
                let _ = stream.write_all(response.as_bytes());
                let _ = stream.flush();
                break;
            }
            Err(ref e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                thread::sleep(Duration::from_millis(200));
                continue;
            }
            Err(_) => break,
        }
    }
}

/// Builds the Discord authorize URL, opens it in the default browser, and starts
/// the local callback server that completes the login.
pub fn start_flow(data_dir: std::path::PathBuf) -> Result<String, String> {
    let verifier = random_base64url(32);
    let challenge = pkce_challenge(&verifier);
    let state = random_base64url(16);
    let client_id = crate::discord_client_id();

    let url = format!(
        "{}?response_type=code&client_id={}&redirect_uri={}&scope={}&state={}&code_challenge={}&code_challenge_method=S256",
        AUTHORIZE_ENDPOINT,
        client_id,
        urlencoding::encode(REDIRECT_URI),
        SCOPES,
        state,
        challenge
    );

    // Aktive Session global speichern, damit der (einzige) Callback-Server
    // selbst bei einem erneuten Login-Versuch die jeweils neueste Session
    // validiert (verhindert "State stimmt nicht überein").
    *OAUTH_SESSION.lock().unwrap() = Some(OAuthSession {
        verifier: verifier.clone(),
        state: state.clone(),
    });

    // Spawn the callback server (it stores the token on success).
    thread::spawn(move || {
        run_callback_server(data_dir);
    });

    // Open the browser for the user to authenticate.
    if let Err(e) = open::that(url.as_str()) {
        return Err(format!(
            "Browser konnte nicht geöffnet werden: {:?}. URL manuell öffnen: {}",
            e, url
        ));
    }

    Ok(url)
}
