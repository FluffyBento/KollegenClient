use serde::{Deserialize, Serialize};
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Mutex;
use std::time::{Duration, Instant};

pub const TWITCH_CLIENT_ID_DEFAULT: &str = "g5zg0400k4vhrx2g6xi4hgveruamlv";

pub fn twitch_client_id() -> String {
    std::env::var("TWITCH_CLIENT_ID")
        .ok()
        .filter(|s| !s.is_empty())
        .unwrap_or_else(|| TWITCH_CLIENT_ID_DEFAULT.to_string())
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct TwitchAccount {
    pub connected: bool,
    pub username: String,
    pub display_name: String,
    pub user_id: String,
    pub profile_image_url: Option<String>,
    pub access_token: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct TwitchDeviceStartResponse {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct TwitchFlowStatus {
    pub state: String,
    pub account: Option<TwitchAccount>,
    pub error: Option<String>,
    pub user_code: Option<String>,
    pub verification_uri: Option<String>,
}

lazy_static::lazy_static! {
    static ref FLOW_STATE: Mutex<TwitchFlowStatus> = Mutex::new(TwitchFlowStatus {
        state: "idle".to_string(),
        account: None,
        error: None,
        user_code: None,
        verification_uri: None,
    });
    static ref FLOW_CANCEL: AtomicBool = AtomicBool::new(false);
}

fn path(data_dir: &Path) -> PathBuf {
    data_dir.join("twitch_account.json")
}

pub fn load(data_dir: &Path) -> TwitchAccount {
    let p = path(data_dir);
    if let Ok(s) = std::fs::read_to_string(&p) {
        if let Ok(acct) = serde_json::from_str::<TwitchAccount>(&s) {
            return acct;
        }
    }
    TwitchAccount::default()
}

pub fn save(data_dir: &Path, acct: &TwitchAccount) {
    let p = path(data_dir);
    if let Ok(s) = serde_json::to_string_pretty(acct) {
        let _ = std::fs::write(&p, s);
    }
    sync_to_instances(data_dir, Some(acct));
}

pub fn unlink(data_dir: &Path) {
    let p = path(data_dir);
    let _ = std::fs::remove_file(p);
    sync_to_instances(data_dir, None);
    let mut st = FLOW_STATE.lock().unwrap();
    st.state = "idle".to_string();
    st.account = None;
    st.error = None;
    st.user_code = None;
    st.verification_uri = None;
}

fn sync_to_instances(data_dir: &Path, acct: Option<&TwitchAccount>) {
    let instances = data_dir.join("instances");
    let entries = match std::fs::read_dir(&instances) {
        Ok(e) => e,
        Err(_) => return,
    };
    for entry in entries.flatten() {
        let cfg = entry.path().join("config");
        let dest = cfg.join("twitch_account.json");
        match acct {
            Some(a) => {
                let _ = std::fs::create_dir_all(&cfg);
                if let Ok(s) = serde_json::to_string_pretty(a) {
                    let _ = std::fs::write(&dest, s);
                }
            }
            None => {
                let _ = std::fs::remove_file(&dest);
            }
        }
    }
}

pub fn fetch_account_profile(client_id: &str, access_token: &str) -> TwitchAccount {
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(8))
        .build()
        .unwrap_or_else(|_| reqwest::blocking::Client::new());
    let mut username = String::new();
    let mut user_id = String::new();
    if let Ok(val_resp) = client
        .get("https://id.twitch.tv/oauth2/validate")
        .header("Authorization", format!("OAuth {}", access_token))
        .send()
    {
        if val_resp.status().is_success() {
            if let Ok(v) = val_resp.json::<serde_json::Value>() {
                username = v.get("login").and_then(|s| s.as_str()).unwrap_or("").to_string();
                user_id = v.get("user_id").and_then(|s| s.as_str()).unwrap_or("").to_string();
            }
        }
    }
    let mut display_name = username.clone();
    let mut profile_image_url = None;
    if let Ok(helix_resp) = client
        .get("https://api.twitch.tv/helix/users")
        .header("Client-Id", client_id)
        .header("Authorization", format!("Bearer {}", access_token))
        .send()
    {
        if helix_resp.status().is_success() {
            if let Ok(h) = helix_resp.json::<serde_json::Value>() {
                if let Some(arr) = h.get("data").and_then(|d| d.as_array()) {
                    if let Some(u) = arr.first() {
                        if let Some(l) = u.get("login").and_then(|s| s.as_str()) {
                            if username.is_empty() {
                                username = l.to_string();
                            }
                        }
                        if let Some(id) = u.get("id").and_then(|s| s.as_str()) {
                            if user_id.is_empty() {
                                user_id = id.to_string();
                            }
                        }
                        if let Some(dn) = u.get("display_name").and_then(|s| s.as_str()) {
                            display_name = dn.to_string();
                        }
                        if let Some(pimg) = u.get("profile_image_url").and_then(|s| s.as_str()) {
                            profile_image_url = Some(pimg.to_string());
                        }
                    }
                }
            }
        }
    }
    if display_name.is_empty() {
        display_name = if username.is_empty() {
            "TwitchUser".to_string()
        } else {
            username.clone()
        };
    }
    TwitchAccount {
        connected: true,
        username: username.to_lowercase(),
        display_name,
        user_id,
        profile_image_url,
        access_token: Some(access_token.to_string()),
    }
}

pub fn start_device_flow(data_dir: PathBuf) -> Result<TwitchDeviceStartResponse, String> {
    FLOW_CANCEL.store(false, Ordering::SeqCst);
    let client = reqwest::blocking::Client::builder()
        .timeout(Duration::from_secs(10))
        .build()
        .unwrap_or_else(|_| reqwest::blocking::Client::new());
    let client_id = twitch_client_id();
    let resp = client
        .post("https://id.twitch.tv/oauth2/device")
        .form(&[
            ("client_id", client_id.as_str()),
            ("scopes", "chat:read chat:edit"),
        ])
        .send()
        .map_err(|e| format!("Twitch-Anfrage fehlgeschlagen: {}", e))?;
    if !resp.status().is_success() {
        return Err(format!("Twitch antwortete mit Status {}", resp.status()));
    }
    let body: serde_json::Value = resp.json().map_err(|e| e.to_string())?;
    let device_code = body
        .get("device_code")
        .and_then(|v| v.as_str())
        .ok_or("Kein device_code in Twitch-Antwort")?
        .to_string();
    let user_code = body
        .get("user_code")
        .and_then(|v| v.as_str())
        .ok_or("Kein user_code in Twitch-Antwort")?
        .to_string();
    let verification_uri = body
        .get("verification_uri")
        .and_then(|v| v.as_str())
        .unwrap_or("https://www.twitch.tv/activate")
        .to_string();
    let expires_in = body.get("expires_in").and_then(|v| v.as_u64()).unwrap_or(1800);
    let interval = body.get("interval").and_then(|v| v.as_u64()).unwrap_or(5);

    {
        let mut st = FLOW_STATE.lock().unwrap();
        st.state = "waiting".to_string();
        st.account = None;
        st.error = None;
        st.user_code = Some(user_code.clone());
        st.verification_uri = Some(verification_uri.clone());
    }

    let _ = open::that(&verification_uri);

    let dev_code = device_code.clone();
    let poll_cid = client_id.clone();
    let d_dir = data_dir.clone();
    std::thread::spawn(move || {
        let deadline = Instant::now() + Duration::from_secs(expires_in);
        let poll_interval = Duration::from_secs(interval.max(2));
        while Instant::now() < deadline {
            if FLOW_CANCEL.load(Ordering::SeqCst) {
                let mut st = FLOW_STATE.lock().unwrap();
                st.state = "idle".to_string();
                st.error = None;
                return;
            }
            std::thread::sleep(poll_interval);
            if FLOW_CANCEL.load(Ordering::SeqCst) {
                let mut st = FLOW_STATE.lock().unwrap();
                st.state = "idle".to_string();
                st.error = None;
                return;
            }
            let poll_client = reqwest::blocking::Client::builder()
                .timeout(Duration::from_secs(8))
                .build()
                .unwrap_or_else(|_| reqwest::blocking::Client::new());
            let token_resp = match poll_client
                .post("https://id.twitch.tv/oauth2/token")
                .form(&[
                    ("client_id", poll_cid.as_str()),
                    ("device_code", dev_code.as_str()),
                    ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ])
                .send()
            {
                Ok(r) => r,
                Err(_) => continue,
            };
            if token_resp.status().is_success() {
                if let Ok(tok) = token_resp.json::<serde_json::Value>() {
                    if let Some(access_token) = tok.get("access_token").and_then(|v| v.as_str()) {
                        let acct = fetch_account_profile(&poll_cid, access_token);
                        save(&d_dir, &acct);
                        let mut st = FLOW_STATE.lock().unwrap();
                        st.state = "success".to_string();
                        st.account = Some(acct);
                        st.error = None;
                        return;
                    }
                }
            } else {
                if let Ok(err_body) = token_resp.json::<serde_json::Value>() {
                    let msg = err_body.get("message").and_then(|v| v.as_str()).unwrap_or("");
                    if msg == "authorization_pending" {
                        continue;
                    }
                    let mut st = FLOW_STATE.lock().unwrap();
                    st.state = "error".to_string();
                    st.error = Some(msg.to_string());
                    return;
                }
            }
        }
        let mut st = FLOW_STATE.lock().unwrap();
        st.state = "error".to_string();
        st.error = Some("Zeitlimit abgelaufen".to_string());
    });

    Ok(TwitchDeviceStartResponse {
        device_code,
        user_code,
        verification_uri,
        expires_in,
        interval,
    })
}

pub fn cancel_device_flow() {
    FLOW_CANCEL.store(true, Ordering::SeqCst);
    let mut st = FLOW_STATE.lock().unwrap();
    st.state = "idle".to_string();
    st.error = None;
    st.user_code = None;
    st.verification_uri = None;
}

pub fn device_status(data_dir: &Path) -> TwitchFlowStatus {
    let current = load(data_dir);
    let st = FLOW_STATE.lock().unwrap();
    if current.connected && st.state == "idle" {
        TwitchFlowStatus {
            state: "success".to_string(),
            account: Some(current),
            error: None,
            user_code: None,
            verification_uri: None,
        }
    } else {
        st.clone()
    }
}

pub fn link(data_dir: &Path, username: &str, token: Option<String>) -> TwitchAccount {
    let raw = username.trim().to_string();
    let cid = twitch_client_id();
    if let Some(ref t) = token {
        let acct = fetch_account_profile(&cid, t);
        save(data_dir, &acct);
        return acct;
    }
    if raw.starts_with("oauth:") {
        let acct = fetch_account_profile(&cid, &raw.replace("oauth:", ""));
        save(data_dir, &acct);
        return acct;
    }
    let display = if raw.is_empty() {
        "TwitchUser".to_string()
    } else {
        raw.clone()
    };
    let acct = TwitchAccount {
        connected: true,
        username: raw.to_lowercase(),
        display_name: display,
        user_id: format!("tw_{}", rand_id()),
        profile_image_url: Some(
            "https://static-cdn.jtvnw.net/user-default-pictures-uv/75305d54-c7cc-40d1-bb60-108c142d8479-profile_image-300x300.png"
                .to_string(),
        ),
        access_token: token,
    };
    save(data_dir, &acct);
    acct
}

fn rand_id() -> u32 {
    use std::time::{SystemTime, UNIX_EPOCH};
    (SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0) % 1_000_000) as u32
}