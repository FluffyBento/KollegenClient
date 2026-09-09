











use crate::discord_client_id;
use log::{info, warn};
use discord_rich_presence::{
    activity::{Activity, Assets, Button, Party, Secrets, Timestamps},
    DiscordIpc, DiscordIpcClient,
};
use std::path::Path;
use std::sync::Mutex;

use lazy_static::lazy_static;
use serde_json::Value;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::mpsc::{Receiver, RecvTimeoutError, Sender};
use std::sync::Arc;
use std::time::Duration;
use std::{path::PathBuf, sync::mpsc::channel};


#[derive(Clone, Debug)]
pub struct DiscordUserInfo {
    pub id: String,
    pub username: String,
    pub global_name: Option<String>,
    pub avatar_url: String,
}



#[derive(Clone, Debug)]
pub struct DiscordInvite {
    pub secret: String,
    pub received_at: String,
}


#[derive(Clone, Debug, Default)]
pub struct DiscordState {
    pub connected: bool,
    pub user: Option<DiscordUserInfo>,
    pub current_server: Option<String>,
    pub invites: Vec<DiscordInvite>,
}

lazy_static! {
    static ref DISCORD_STATE: Mutex<DiscordState> = Mutex::new(DiscordState::default());
}


pub fn discord_state() -> DiscordState {
    DISCORD_STATE.lock().unwrap().clone()
}


pub fn dismiss_invite(secret: &str) {
    let mut s = DISCORD_STATE.lock().unwrap();
    s.invites.retain(|i| i.secret != secret);
}


pub fn clear_invites() {
    DISCORD_STATE.lock().unwrap().invites.clear();
}



#[derive(Clone, Debug, Default)]
pub struct DiscordFriend {
    pub id: String,
    pub username: String,
    pub global_name: Option<String>,
    pub avatar_url: Option<String>,
    
    pub status: String,
    
    
    
    
    pub presence_known: bool,
    
    pub game: Option<String>,
    
    pub version: Option<String>,
    
    pub join_secret: Option<String>,
    
    
    pub kollegen: bool,
}

lazy_static! {
    static ref DISCORD_FRIENDS: Mutex<Vec<DiscordFriend>> = Mutex::new(Vec::new());
}


pub fn friends() -> Vec<DiscordFriend> {
    DISCORD_FRIENDS.lock().unwrap().clone()
}




fn extract_version(blob: &str) -> Option<String> {
    let bytes = blob.as_bytes();
    let n = bytes.len();
    let mut i = 0;
    while i < n {
        
        if !bytes[i].is_ascii_digit() {
            i += 1;
            continue;
        }
        let start = i;
        while i < n && bytes[i].is_ascii_digit() {
            i += 1;
        }
        let mut dots = 0;
        while i < n && bytes[i] == b'.' && dots < 2 {
            let saved = i;
            i += 1;
            let mut nums = 0;
            while i < n && bytes[i].is_ascii_digit() {
                i += 1;
                nums += 1;
            }
            if nums == 0 {
                i = saved; 
                break;
            }
            dots += 1;
        }
        if dots > 0 {
            return Some(blob[start..i].to_string());
        }
    }
    None
}



fn parse_relationship(r: &Value) -> Option<DiscordFriend> {
    
    let rtype = r.get("type").and_then(|v| v.as_i64()).unwrap_or(0);
    if rtype != 1 {
        return None;
    }
    let user = r.get("user")?;
    let id = user.get("id").and_then(|v| v.as_str())?.to_string();
    if id.is_empty() {
        return None;
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
    let presence_known = presence.is_some();
    let status = presence
        .and_then(|p| p.get("status"))
        .and_then(|v| v.as_str())
        .unwrap_or("offline")
        .to_string();

    let mut game = None;
    let mut version = None;
    let mut join_secret = None;
    let mut kollegen = false;
    if let Some(activities) = presence
        .and_then(|p| p.get("activities"))
        .and_then(|a| a.as_array())
    {
        for act in activities {
            if let Some(name) = act.get("name").and_then(|v| v.as_str()) {
                game = Some(name.to_string());
                let blob = format!(
                    "{} {} {}",
                    name,
                    act.get("details").and_then(|v| v.as_str()).unwrap_or(""),
                    act.get("state").and_then(|v| v.as_str()).unwrap_or("")
                );
                
                
                if blob.to_lowercase().contains("kollegen") {
                    kollegen = true;
                }
                if let Some(v) = extract_version(&blob) {
                    version = Some(v);
                }
                join_secret = act
                    .get("secrets")
                    .and_then(|s| s.get("join"))
                    .and_then(|v| v.as_str())
                    .map(|s| s.to_string());
                break;
            }
        }
    }

    Some(DiscordFriend {
        id,
        username,
        global_name,
        avatar_url,
        status,
        presence_known,
        game,
        version,
        join_secret,
        kollegen,
    })
}



pub fn set_current_server(server: Option<String>) {
    DISCORD_STATE.lock().unwrap().current_server = server;
}

fn avatar_url(user: &Value) -> String {
    let id = user.get("id").and_then(|v| v.as_str()).unwrap_or("");
    match user.get("avatar").and_then(|v| v.as_str()) {
        Some(hash) if !hash.is_empty() => {
            let ext = if hash.starts_with("a_") { "gif" } else { "png" };
            format!("https://cdn.discordapp.com/avatars/{}/{}.{}", id, hash, ext)
        }
        _ => {
            let idx = user
                .get("discriminator")
                .and_then(|v| v.as_str())
                .and_then(|d| d.parse::<usize>().ok())
                .unwrap_or(0)
                % 5;
            format!("https://cdn.discordapp.com/embed/avatars/{}.png", idx)
        }
    }
}

fn user_from_ready(ready: &Value) -> Option<DiscordUserInfo> {
    let user = ready.get("data").and_then(|d| d.get("user"))?;
    let id = user.get("id").and_then(|v| v.as_str()).unwrap_or("").to_string();
    if id.is_empty() {
        return None;
    }
    Some(DiscordUserInfo {
        id,
        username: user
            .get("username")
            .and_then(|v| v.as_str())
            .unwrap_or("")
            .to_string(),
        global_name: user
            .get("global_name")
            .and_then(|v| v.as_str())
            .map(|s| s.to_string()),
        avatar_url: avatar_url(user),
    })
}


const BUTTON_LABEL: &str = "Kollegen Client";
const BUTTON_URL: &str = "https://dsc.gg/Kollegen";






const PRESENCE_IMAGE: &str = "kollegen";

pub enum RpcMessage {
    Set {
        details: String,
        state: String,
        large_text: String,
        
        
        server: Option<String>,
        
        players: Option<u32>,
    },
    Clear,
    Shutdown,
}


pub struct DiscordHandle {
    pub tx: Sender<RpcMessage>,
}


pub fn start(data_dir: PathBuf) -> DiscordHandle {
    let (tx, rx) = channel::<RpcMessage>();
    let shutdown = Arc::new(AtomicBool::new(false));

    let ev_shutdown = Arc::clone(&shutdown);
    let ev_data = data_dir.clone();
    std::thread::spawn(move || events_loop(ev_data, ev_shutdown));

    std::thread::spawn(move || run_loop(rx, shutdown));

    DiscordHandle { tx }
}

fn run_loop(rx: Receiver<RpcMessage>, shutdown: Arc<AtomicBool>) {
    let mut client: Option<DiscordIpcClient> = None;
    let mut connected = false;

    loop {
        match rx.recv_timeout(Duration::from_secs(10)) {
            Ok(RpcMessage::Shutdown) => {
                shutdown.store(true, Ordering::Relaxed);
                break;
            }
            Ok(RpcMessage::Set {
                details,
                state,
                large_text,
                server,
                players,
            }) => {
                if !connected {
                    connected = try_connect(&mut client);
                }
                if connected {
                    if let Some(c) = client.as_mut() {
                        let start = chrono::Utc::now().timestamp();
                        let mut activity = Activity::new()
                            .details(details.as_str())
                            .state(state.as_str())
                                .assets(
                                    Assets::new()
                                        .large_image(PRESENCE_IMAGE)
                                        .large_text(large_text.as_str()),
                                )
                            .timestamps(Timestamps::new().start(start));

                        let buttons = vec![Button::new(BUTTON_LABEL, BUTTON_URL)];

                        if let Some(srv) = &server {
                            if !srv.is_empty() {
                                let max = players.unwrap_or(10).max(2) as i32;
                                activity = activity
                                    .party(Party::new().id(srv.as_str()).size([1, max]))
                                    .secrets(Secrets::new().join(srv.as_str()));
                            }
                        }

                        let activity = activity.buttons(buttons);

                        if c.set_activity(activity).is_err() {
                            
                            warn!("set_activity fehlgeschlagen – Verbindung verloren");
                            connected = false;
                        } else {
                            info!(
                                "Rich Presence gesetzt: '{}' / '{}'{}",
                                details,
                                state,
                                if server.as_deref().filter(|s| !s.is_empty()).is_some() {
                                    " (mit Join)"
                                } else {
                                    ""
                                }
                            );
                        }
                    }
                }
            }
            Ok(RpcMessage::Clear) => {
                if let Some(c) = client.as_mut() {
                    let _ = c.clear_activity();
                }
            }
            Err(RecvTimeoutError::Timeout) => {
                if !connected {
                    connected = try_connect(&mut client);
                }
            }
            Err(RecvTimeoutError::Disconnected) => break,
        }
    }

    if let Some(mut c) = client.take() {
        let _ = c.close();
    }
}







fn events_loop(data_dir: PathBuf, shutdown: Arc<AtomicBool>) {
    let mut client = DiscordIpcClient::new(discord_client_id());
    loop {
        if shutdown.load(Ordering::Relaxed) {
            break;
        }
        if client.connect().is_ok() {
            info!("Discord (join-events) verbunden – warte auf READY/ACTIVITY_JOIN");
            
            if client
                .send(
                    serde_json::json!({ "cmd": "SUBSCRIBE", "evt": "ACTIVITY_JOIN", "args": {} }),
                    1u8,
                )
                .is_err()
            {
                warn!("Konnte ACTIVITY_JOIN nicht abonnieren");
            }
            
            
            
            
            if client
                .send(
                    serde_json::json!({ "cmd": "SUBSCRIBE", "evt": "RELATIONSHIPS", "args": {} }),
                    1u8,
                )
                .is_err()
            {
                warn!("Konnte RELATIONSHIPS nicht abonnieren (Scope verweigert?)");
            }

            loop {
                if shutdown.load(Ordering::Relaxed) {
                    break;
                }
                match client.recv() {
                    Ok((op, val)) => {
                        
                        
                        
                        if op == 2 {
                            let pong = serde_json::json!({
                                "cmd": "PONG",
                                "data": val.get("data").cloned().unwrap_or(serde_json::Value::Null),
                            });
                            let _ = client.send(pong, 3u8);
                            continue;
                        }
                        let evt = val
                            .get("evt")
                            .and_then(|e| e.as_str())
                            .unwrap_or("");
                        if evt == "READY" {
                            if let Some(u) = user_from_ready(&val) {
                                let mut s = DISCORD_STATE.lock().unwrap();
                                s.connected = true;
                                s.user = Some(u);
                            }
                        } else if evt == "ACTIVITY_JOIN" {
                            if let Some(secret) = val
                                .get("data")
                                .and_then(|d| d.get("secret"))
                                .and_then(|s| s.as_str())
                            {
                                info!("ACTIVITY_JOIN erhalten: {}", secret);
                                if write_join_request(&data_dir, secret).is_err() {
                                    warn!("Konnte join_request.json nicht schreiben");
                                }
                                let mut s = DISCORD_STATE.lock().unwrap();
                                if !s.invites.iter().any(|i| i.secret == secret) {
                                    s.invites.push(DiscordInvite {
                                        secret: secret.to_string(),
                                        received_at: chrono::Utc::now().to_rfc3339(),
                                    });
                                }
                            }
                        } else if evt == "RELATIONSHIPS" {
                            
                            if let Some(arr) = val.get("data").and_then(|d| d.as_array()) {
                                let mut friends = Vec::new();
                                for r in arr {
                                    if let Some(f) = parse_relationship(r) {
                                        friends.push(f);
                                    }
                                }
                                let n = friends.len();
                                *DISCORD_FRIENDS.lock().unwrap() = friends;
                                info!("{} Discord-Freunde geladen", n);
                            }
                        } else if evt == "RELATIONSHIP_UPDATE" {
                            if let Some(r) = val.get("data") {
                                if let Some(f) = parse_relationship(r) {
                                    let mut friends = DISCORD_FRIENDS.lock().unwrap();
                                    if let Some(pos) =
                                        friends.iter().position(|x| x.id == f.id)
                                    {
                                        friends[pos] = f;
                                    } else {
                                        friends.push(f);
                                    }
                                }
                            }
                        } else if evt == "RELATIONSHIP_DELETE" {
                            if let Some(id) = val
                                .get("data")
                                .and_then(|d| d.get("id"))
                                .and_then(|v| v.as_str())
                            {
                                DISCORD_FRIENDS
                                    .lock()
                                    .unwrap()
                                    .retain(|x| x.id != id);
                            }
                        }
                    }
                    Err(_) => break, 
                }
            }
            
            let mut s = DISCORD_STATE.lock().unwrap();
            s.connected = false;
            s.invites.clear();
        } else {
            warn!("Discord (join-events) nicht erreichbar – erneut in 5s");
            std::thread::sleep(Duration::from_secs(5));
        }
    }
}




pub fn write_join_request(data_dir: &Path, secret: &str) -> std::io::Result<()> {
    let dir = data_dir.join(".kollegen");
    std::fs::create_dir_all(&dir)?;
    let payload = serde_json::json!({
        "secret": secret,
        "received_at": chrono::Utc::now().to_rfc3339(),
    });
    let body = serde_json::to_string_pretty(&payload)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e))?;
    std::fs::write(dir.join("join_request.json"), &body)?;
    
    
    if let Some(home) = dirs::home_dir() {
        let hdir = home.join(".kollegen");
        if std::fs::create_dir_all(&hdir).is_ok() {
            let _ = std::fs::write(hdir.join("join_request.json"), &body);
        }
    }
    Ok(())
}

fn try_connect(client: &mut Option<DiscordIpcClient>) -> bool {
    let mut c = DiscordIpcClient::new(discord_client_id());
    if c.connect().is_ok() {
        *client = Some(c);
        info!("Discord verbunden – Rich Presence aktiv");
        true
    } else {
        warn!("Discord nicht erreichbar (läuft der Desktop-Client? richtige Client-ID?)");
        false
    }
}
