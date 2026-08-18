// Discord Rich Presence integration for the Kollegen Client launcher.
//
// Two background threads share the Discord IPC:
//   1. The "presence" loop owns the activity connection and reacts to
//      `Set`/`Clear` messages coming from the app (via an `mpsc` channel).
//   2. The "events" loop owns a second connection that subscribes to
//      `ACTIVITY_JOIN`. When a friend clicks "Join" on our presence, Discord
//      delivers the join secret here; we write it to a file that the in-game
//      Kollegen Client mod polls to actually connect to the friend's server.
//
// The IPC client is not `Send`, so each thread keeps its own client locally.

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

/// Identity of the local Discord user, captured from the RPC `READY` event.
#[derive(Clone, Debug)]
pub struct DiscordUserInfo {
    pub id: String,
    pub username: String,
    pub global_name: Option<String>,
    pub avatar_url: String,
}

/// A pending "Join" invitation received via RPC (a friend clicked "Beitreten"
/// on our presence). `secret` is the server address (`host:port`) they exposed.
#[derive(Clone, Debug)]
pub struct DiscordInvite {
    pub secret: String,
    pub received_at: String,
}

/// Shared, process-wide Discord connection state surfaced to the frontend.
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

/// Returns a snapshot of the current Discord connection state.
pub fn discord_state() -> DiscordState {
    DISCORD_STATE.lock().unwrap().clone()
}

/// Removes a single join invite (e.g. after the user joined or dismissed it).
pub fn dismiss_invite(secret: &str) {
    let mut s = DISCORD_STATE.lock().unwrap();
    s.invites.retain(|i| i.secret != secret);
}

/// Clears all pending join invites.
pub fn clear_invites() {
    DISCORD_STATE.lock().unwrap().invites.clear();
}

/// A Discord friend (relationship type 1) with their current presence, as
/// reported by the RPC `RELATIONSHIPS` / `RELATIONSHIP_UPDATE` events.
#[derive(Clone, Debug, Default)]
pub struct DiscordFriend {
    pub id: String,
    pub username: String,
    pub global_name: Option<String>,
    pub avatar_url: Option<String>,
    /// Presence status: "online" | "idle" | "dnd" | "offline".
    pub status: String,
    /// True only when Discord actually delivered a `presence` object for this
    /// friend. When false, `status` is just the "offline" default and must not
    /// be trusted (the `RELATIONSHIPS` subscription often returns friends
    /// without presence unless the app has the relationships scope).
    pub presence_known: bool,
    /// Name of the friend's current activity (e.g. "Minecraft").
    pub game: Option<String>,
    /// Minecraft version parsed from the activity, if detectable.
    pub version: Option<String>,
    /// Join secret advertised by the friend's game (enables "Beitreten").
    pub join_secret: Option<String>,
    /// True when the friend's presence marks them as a Kollegen Client user
    /// (`details`/`state`/activity name containing "Kollegen").
    pub kollegen: bool,
}

lazy_static! {
    static ref DISCORD_FRIENDS: Mutex<Vec<DiscordFriend>> = Mutex::new(Vec::new());
}

/// Returns the current list of Discord friends with an online presence.
pub fn friends() -> Vec<DiscordFriend> {
    DISCORD_FRIENDS.lock().unwrap().clone()
}

/// Best-effort extraction of a `x.y.z` / `x.y` version token from a blob of text
/// (activity name / details / state). Returns `None` if nothing looks like a
/// Minecraft version.
fn extract_version(blob: &str) -> Option<String> {
    let bytes = blob.as_bytes();
    let n = bytes.len();
    let mut i = 0;
    while i < n {
        // Find start of a digit.
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
                i = saved; // trailing dot, not a version
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

/// Parses a Discord relationship object (from `RELATIONSHIPS` / `RELATIONSHIP_*`
/// events) into a `DiscordFriend`, returning `None` for non-friend relationships.
fn parse_relationship(r: &Value) -> Option<DiscordFriend> {
    // type 1 == friend. (2 = blocked, 3 = incoming request, 4 = outgoing request)
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
                // Kollegen Client users advertise "Kollegen Client" in their
                // presence details/state, so the UI can highlight them.
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

/// Records the server the launcher is currently advertising in its rich
/// presence (set whenever an instance is launched / the presence changes).
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

// Presence button pointing at the Kollegen Client project/community.
const BUTTON_LABEL: &str = "Kollegen Client";
const BUTTON_URL: &str = "https://dsc.gg/Kollegen";

// Rich-presence image asset key. Upload `Kollegen.png` to your Discord
// application's *Rich Presence → Art Assets* with the key EXACTLY "kollegen"
// (max 1024x1024, <1 MB). Discord can only show images that were uploaded to
// the app whose client id is `DISCORD_CLIENT_ID`; a local file path does not
// work as a rich-presence image.
const PRESENCE_IMAGE: &str = "kollegen";

pub enum RpcMessage {
    Set {
        details: String,
        state: String,
        large_text: String,
        /// Server address (ip:port) used as the Discord "join" secret so a
        /// friend can join the same server directly from the rich presence.
        server: Option<String>,
        /// Max party size shown next to the "Join" button.
        players: Option<u32>,
    },
    Clear,
    Shutdown,
}

/// Handle kept in `AppState`. Cloneable sender so any command can push updates.
pub struct DiscordHandle {
    pub tx: Sender<RpcMessage>,
}

/// Spawns the presence + events threads and returns a handle for sending updates.
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
                            // Lost connection (e.g. Discord closed) -> reconnect next time.
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

/// Listens for Discord `ACTIVITY_JOIN` events and the `READY` user, writing
/// both into the shared `DISCORD_STATE`:
///   * `user` + `connected` drive the launcher's Discord account display.
///   * received join secrets become "invites" the launcher can act on, and are
///     also written to `<data_dir>/.kollegen/join_request.json` for the in-game
///     Kollegen Client mod to pick up.
fn events_loop(data_dir: PathBuf, shutdown: Arc<AtomicBool>) {
    let mut client = DiscordIpcClient::new(discord_client_id());
    loop {
        if shutdown.load(Ordering::Relaxed) {
            break;
        }
        if client.connect().is_ok() {
            info!("Discord (join-events) verbunden – warte auf READY/ACTIVITY_JOIN");
            // Subscribe to join events on this connection.
            if client
                .send(
                    serde_json::json!({ "cmd": "SUBSCRIBE", "evt": "ACTIVITY_JOIN", "args": {} }),
                    1u8,
                )
                .is_err()
            {
                warn!("Konnte ACTIVITY_JOIN nicht abonnieren");
            }
            // Subscribe to the friends list so the launcher can show which
            // Discord friends are currently playing (and let the user join them).
            // This requires the user to grant the RPC "relationships" scope; if
            // they decline, Discord simply won't deliver the events.
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
                        // Discord sends periodic PING (opcode 2) frames that the
                        // client must answer with a PONG (opcode 3), otherwise the
                        // connection is dropped. The crate does not do this for us.
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
                            // Initial full list delivered right after subscribing.
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
                    Err(_) => break, // connection dropped -> reconnect in outer loop
                }
            }
            // Disconnected: mark offline and drop stale invites.
            let mut s = DISCORD_STATE.lock().unwrap();
            s.connected = false;
            s.invites.clear();
        } else {
            warn!("Discord (join-events) nicht erreichbar – erneut in 5s");
            std::thread::sleep(Duration::from_secs(5));
        }
    }
}

/// Writes the join secret to `<data_dir>/.kollegen/join_request.json` (and a
/// mirrored copy at `~/.kollegen/join_request.json`) so the in-game mod can
/// pick it up and connect to the friend's server.
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
    // Mirrored to the user home so the Fabric mod finds it regardless of the
    // launcher's data dir layout on the current OS.
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
