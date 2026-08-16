use base64::{engine::general_purpose::URL_SAFE_NO_PAD, Engine as _};
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
// `identify` is always required. `relationships` grants the application access
// to the user's Discord friend list (used to surface friends inside the client).
// NOTE: `relationships` is a *privileged* OAuth scope – it must be enabled for
// the Discord application in the developer portal (OAuth2 → scopes) and approved
// by Discord before tokens actually return the friend list.
const SCOPES: &str = "identify relationships";
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
    Ok(())
}

/// Runs a minimal localhost HTTP server that waits for Discord's redirect,
/// exchanges the code for a token, and shows a "done" page in the browser.
fn run_callback_server(verifier: String, expected_state: String, data_dir: std::path::PathBuf) {
    let listener = match TcpListener::bind(("127.0.0.1", CALLBACK_PORT)) {
        Ok(l) => l,
        Err(e) => {
            eprintln!("[discord_auth] Callback-Server konnte Port {} nicht binden: {}", CALLBACK_PORT, e);
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

                let mut code = "";
                let mut returned_state = "";
                for kv in query.split('&') {
                    let mut it = kv.splitn(2, '=');
                    let k = it.next().unwrap_or("");
                    let v = it.next().unwrap_or("");
                    if k == "code" {
                        code = v;
                    } else if k == "state" {
                        returned_state = v;
                    }
                }

                let (status_line, body): (&str, String) = if code.is_empty() {
                    (
                        "HTTP/1.1 400 Bad Request",
                        "<h2>Authentifizierung fehlgeschlagen.</h2>".to_string(),
                    )
                } else if returned_state != expected_state {
                    (
                        "HTTP/1.1 403 Forbidden",
                        "<h2>State stimmt nicht überein.</h2>".to_string(),
                    )
                } else {
                    match exchange_token(code, &verifier, &data_dir) {
                        Ok(()) => (
                            "HTTP/1.1 200 OK",
                            "<h2>Erfolgreich mit Discord verbunden!</h2><p>Du kannst dieses Fenster schließen.</p>".to_string(),
                        ),
                        Err(e) => (
                            "HTTP/1.1 500 Internal Server Error",
                            format!("<h2>Fehler:</h2><pre>{}</pre>", e),
                        ),
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

    // Spawn the callback server (it stores the token on success).
    thread::spawn(move || {
        run_callback_server(verifier, state, data_dir);
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
