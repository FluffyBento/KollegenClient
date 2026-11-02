use serde::{Deserialize, Serialize};
use std::path::Path;

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct TwitchAccount {
    pub connected: bool,
    pub username: String,
    pub display_name: String,
    pub user_id: String,
    pub profile_image_url: Option<String>,
    pub access_token: Option<String>,
}

fn path(data_dir: &Path) -> std::path::PathBuf {
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
}

pub fn unlink(data_dir: &Path) {
    let p = path(data_dir);
    let _ = std::fs::remove_file(p);
}

pub fn link(data_dir: &Path, username: &str, token: Option<String>) -> TwitchAccount {
    let clean = username.trim().to_string();
    let display = if clean.is_empty() { "TwitchUser".to_string() } else { clean.clone() };
    let acct = TwitchAccount {
        connected: true,
        username: clean.to_lowercase(),
        display_name: display,
        user_id: format!("tw_{}", rand_id()),
        profile_image_url: Some("https://static-cdn.jtvnw.net/user-default-pictures-uv/75305d54-c7cc-40d1-bb60-108c142d8479-profile_image-300x300.png".to_string()),
        access_token: token,
    };
    save(data_dir, &acct);
    acct
}

fn rand_id() -> u32 {
    use std::time::{SystemTime, UNIX_EPOCH};
    (SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_millis()).unwrap_or(0) % 1_000_000) as u32
}
