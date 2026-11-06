use std::collections::HashSet;
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use serde::{Deserialize, Serialize};
use sysinfo::System;
use tauri::Manager;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ClipsConfig {
    pub enabled: bool,
    pub also_record_mode: String,
    pub also_record_target: String,
    pub also_record_title: String,
    pub record_other_audio: bool,
    pub hotkey: String,
    pub duration_seconds: u32,
    pub quality: String,
    pub output_dir: String,
}

impl Default for ClipsConfig {
    fn default() -> Self {
        let out_dir = default_clips_dir().to_string_lossy().to_string();
        Self {
            enabled: true,
            also_record_mode: "none".to_string(),
            also_record_target: String::new(),
            also_record_title: "None".to_string(),
            record_other_audio: true,
            hotkey: "F8".to_string(),
            duration_seconds: 60,
            quality: "1080p60".to_string(),
            output_dir: out_dir,
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ProgramInfo {
    pub id: String,
    pub name: String,
    pub exe: String,
    pub is_none: bool,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ScreenInfo {
    pub id: String,
    pub name: String,
    pub resolution: String,
    pub is_primary: bool,
}

fn default_clips_dir() -> PathBuf {
    if let Some(v) = dirs::video_dir() {
        v.join("KollegenClips")
    } else if let Some(h) = dirs::home_dir() {
        h.join("Videos").join("KollegenClips")
    } else {
        PathBuf::from("KollegenClips")
    }
}

fn config_path(data_dir: &Path) -> PathBuf {
    data_dir.join("clips_config.json")
}

pub fn load_config(data_dir: &Path) -> ClipsConfig {
    let cp = config_path(data_dir);
    if cp.is_file() {
        if let Ok(text) = fs::read_to_string(&cp) {
            if let Ok(cfg) = serde_json::from_str::<ClipsConfig>(&text) {
                return cfg;
            }
        }
    }
    let def = ClipsConfig::default();
    save_config(data_dir, &def);
    def
}

pub fn save_config(data_dir: &Path, config: &ClipsConfig) {
    let cp = config_path(data_dir);
    if let Ok(json) = serde_json::to_string_pretty(config) {
        let _ = fs::write(&cp, json.as_bytes());
    }
    let instances_dir = data_dir.join("instances");
    if instances_dir.is_dir() {
        if let Ok(entries) = fs::read_dir(&instances_dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let cfg_dir = p.join("config");
                    let _ = fs::create_dir_all(&cfg_dir);
                    if let Ok(json) = serde_json::to_string_pretty(config) {
                        let _ = fs::write(cfg_dir.join("clips_config.json"), json.as_bytes());
                    }
                }
            }
        }
    }
}

#[tauri::command]
pub fn clips_get_config(app: tauri::AppHandle) -> Result<ClipsConfig, String> {
    let state = app.state::<crate::AppState>();
    Ok(load_config(&state.data_dir))
}

#[tauri::command]
pub fn clips_save_config(app: tauri::AppHandle, config: ClipsConfig) -> Result<(), String> {
    let state = app.state::<crate::AppState>();
    save_config(&state.data_dir, &config);
    Ok(())
}

fn friendly_app_name(raw_name: &str, exe_name: &str) -> (String, String) {
    let lower = exe_name.to_lowercase();
    let lower_name = raw_name.to_lowercase();

    if lower.contains("norisk") || lower.contains("zen") || lower_name.contains("norisk") {
        return ("NoRisk Client – Free Minecraft Launcher with Mods & Cosmetic".to_string(), "zen.exe".to_string());
    }
    if lower.contains("obs") || lower_name.contains("obs") {
        return ("OBS Studio".to_string(), "obs64.exe".to_string());
    }
    if lower.contains("discord") || lower_name.contains("discord") {
        return ("Discord".to_string(), "discord".to_string());
    }
    if lower.contains("spotify") || lower_name.contains("spotify") {
        return ("Spotify".to_string(), "spotify".to_string());
    }
    if lower.contains("steam") || lower_name.contains("steam") {
        return ("Steam".to_string(), "steam".to_string());
    }
    if lower.contains("firefox") || lower_name.contains("firefox") {
        return ("Firefox".to_string(), "firefox".to_string());
    }
    if lower.contains("chrome") || lower_name.contains("chrome") {
        return ("Google Chrome".to_string(), "chrome".to_string());
    }
    if lower.contains("code") || lower_name.contains("code") {
        return ("Visual Studio Code".to_string(), "code".to_string());
    }
    if lower.contains("minecraft") || lower_name.contains("minecraft") {
        return ("Minecraft Launcher".to_string(), "minecraft.exe".to_string());
    }
    if lower.contains("telegram") || lower_name.contains("telegram") {
        return ("Telegram Desktop".to_string(), "telegram".to_string());
    }
    if lower.contains("vlc") || lower_name.contains("vlc") {
        return ("VLC media player".to_string(), "vlc".to_string());
    }
    if lower.contains("opencode") {
        return ("OpenCode".to_string(), "opencode".to_string());
    }

    let clean = raw_name.trim().trim_end_matches(".exe");
    let mut chars = clean.chars();
    let cap = match chars.next() {
        None => String::new(),
        Some(first) => first.to_uppercase().collect::<String>() + chars.as_str(),
    };
    (cap, exe_name.to_string())
}

#[tauri::command]
pub fn clips_list_programs() -> Result<Vec<ProgramInfo>, String> {
    let mut out = Vec::new();
    out.push(ProgramInfo {
        id: "none".to_string(),
        name: "None".to_string(),
        exe: "Record Minecraft only.".to_string(),
        is_none: true,
    });

    let mut sys = System::new_all();
    sys.refresh_all();

    let mut seen_exes = HashSet::new();

    let ignored_names = [
        "systemd", "dbus", "pipewire", "wireplumber", "pulseaudio", "polkit", "kworker",
        "gnome-shell", "mutter", "waybar", "sway", "hyprland", "xwayland", "sh", "bash",
        "zsh", "cargo", "rustc", "sshd", "login", "agetty", "systemd-resolved", "chronyd",
        "rtkit-daemon", "wpa_supplicant", "avahi-daemon", "colord", "cupsd", "bluetoothd",
        "upowerd", "accounts-daemon", "snapd", "gvfs", "dconf", "at-spi", "agent",
    ];

    for (_, proc) in sys.processes() {
        let raw_name = proc.name();
        let exe_path = proc.exe().map(|p| p.to_string_lossy().to_string()).unwrap_or_default();
        let exe_file = proc.exe().and_then(|p| p.file_name().map(|f| f.to_string_lossy().to_string())).unwrap_or_else(|| raw_name.to_string());

        let lower = exe_file.to_lowercase();
        if ignored_names.iter().any(|&ign| lower.contains(ign)) {
            continue;
        }

        if exe_path.starts_with("/usr/libexec") || exe_path.starts_with("/usr/lib/systemd") {
            continue;
        }

        if !seen_exes.insert(exe_file.clone()) {
            continue;
        }

        let (title, display_exe) = friendly_app_name(raw_name, &exe_file);
        out.push(ProgramInfo {
            id: exe_file,
            name: title,
            exe: display_exe,
            is_none: false,
        });
    }

    if out.len() == 1 {
        let defaults = [
            ("NoRisk Client – Free Minecraft Launcher with Mods & Cosmetic", "zen.exe"),
            ("OBS Studio", "obs64.exe"),
            ("Minecraft Launcher", "minecraft.exe"),
            ("Discord", "discord"),
            ("Spotify", "spotify"),
        ];
        for (name, exe) in defaults {
            out.push(ProgramInfo {
                id: exe.to_string(),
                name: name.to_string(),
                exe: exe.to_string(),
                is_none: false,
            });
        }
    }

    Ok(out)
}

#[tauri::command]
pub fn clips_list_screens(app: tauri::AppHandle) -> Result<Vec<ScreenInfo>, String> {
    let mut screens = Vec::new();

    if let Some(win) = app.get_webview_window("main") {
        if let Ok(monitors) = win.available_monitors() {
            let primary_name = win.primary_monitor().ok().flatten().and_then(|m| m.name().map(|s| s.to_string()));
            for (i, m) in monitors.into_iter().enumerate() {
                let mname = m.name().map(|s| s.as_str()).unwrap_or("Screen").to_string();
                let is_prim = primary_name.as_ref().map(|p| p == &mname).unwrap_or(i == 0);
                let sz = m.size();
                screens.push(ScreenInfo {
                    id: format!("screen:{}", i),
                    name: if is_prim {
                        format!("{} (Primary)", mname)
                    } else {
                        mname
                    },
                    resolution: format!("{}x{}", sz.width, sz.height),
                    is_primary: is_prim,
                });
            }
        }
    }

    if screens.is_empty() {
        screens.push(ScreenInfo {
            id: "screen:0".to_string(),
            name: "Screen 1 (Primary)".to_string(),
            resolution: "1920x1080".to_string(),
            is_primary: true,
        });
        screens.push(ScreenInfo {
            id: "screen:1".to_string(),
            name: "Screen 2".to_string(),
            resolution: "2560x1440".to_string(),
            is_primary: false,
        });
    }

    Ok(screens)
}

#[tauri::command]
pub fn clips_open_folder(app: tauri::AppHandle) -> Result<(), String> {
    let state = app.state::<crate::AppState>();
    let cfg = load_config(&state.data_dir);
    let p = if cfg.output_dir.trim().is_empty() {
        default_clips_dir()
    } else {
        PathBuf::from(cfg.output_dir)
    };
    let _ = fs::create_dir_all(&p);
    open::that(&p).map_err(|e| e.to_string())?;
    Ok(())
}

static IS_RECORDING: std::sync::atomic::AtomicBool = std::sync::atomic::AtomicBool::new(false);

const WAYLAND_RECORD_SCRIPT: &str = r#"import gi
from gi.repository import Gio, GLib
import sys, time, subprocess, os

dur = int(sys.argv[1]) if len(sys.argv) > 1 else 30
record_audio = sys.argv[2] == "1" if len(sys.argv) > 2 else True
target_mp4 = sys.argv[3] if len(sys.argv) > 3 else "clip.mp4"
tmp_aud = target_mp4 + ".aud.aac"

aud_proc = None
if record_audio:
    try:
        aud_proc = subprocess.Popen(
            ["ffmpeg", "-y", "-f", "pulse", "-i", "default", "-t", str(dur), "-c:a", "aac", "-b:a", "192k", tmp_aud],
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL
        )
    except Exception:
        pass

try:
    bus = Gio.bus_get_sync(Gio.BusType.SESSION, None)
    proxy = Gio.DBusProxy.new_sync(
        bus,
        Gio.DBusProxyFlags.NONE,
        None,
        "org.gnome.Shell.Screencast",
        "/org/gnome/Shell/Screencast",
        "org.gnome.Shell.Screencast",
        None
    )
    params = GLib.Variant("(sa{sv})", ("Kollegen_Clip_Raw_%d", {}))
    res = proxy.call_sync("Screencast", params, Gio.DBusCallFlags.NONE, -1, None)
    if not res or not res[0]:
        sys.exit(1)
    raw_vid = res[1]

    time.sleep(dur)

    try:
        proxy.call_sync("StopScreencast", None, Gio.DBusCallFlags.NONE, -1, None)
    except Exception:
        pass

    if aud_proc:
        try:
            aud_proc.wait(timeout=5)
        except Exception:
            aud_proc.kill()

    time.sleep(0.5)

    if raw_vid and os.path.exists(raw_vid):
        if os.path.exists(tmp_aud):
            subprocess.run(
                ["ffmpeg", "-y", "-i", raw_vid, "-i", tmp_aud, "-c:v", "copy", "-c:a", "copy", "-movflags", "+faststart", target_mp4],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        else:
            subprocess.run(
                ["ffmpeg", "-y", "-i", raw_vid, "-c:v", "copy", "-movflags", "+faststart", target_mp4],
                stdout=subprocess.DEVNULL,
                stderr=subprocess.DEVNULL
            )
        try:
            os.remove(raw_vid)
        except Exception:
            pass

    if os.path.exists(tmp_aud):
        try:
            os.remove(tmp_aud)
        except Exception:
            pass
except Exception:
    sys.exit(1)

if not os.path.exists(target_mp4) or os.path.getsize(target_mp4) == 0:
    sys.exit(2)
"#;

fn record_wayland_gnome(dur: u32, record_audio: bool, target: &Path) -> bool {
    let audio_arg = if record_audio { "1" } else { "0" };
    let status = Command::new("python3")
        .arg("-c")
        .arg(WAYLAND_RECORD_SCRIPT)
        .arg(dur.to_string())
        .arg(audio_arg)
        .arg(target.to_string_lossy().as_ref())
        .status();

    if let Ok(st) = status {
        if st.success() && target.is_file() {
            if let Ok(meta) = fs::metadata(target) {
                if meta.len() > 1024 {
                    return true;
                }
            }
        }
    }
    false
}

fn execute_clip_recording(data_dir: PathBuf, cfg: ClipsConfig) -> Result<String, String> {
    let out_dir = if cfg.output_dir.trim().is_empty() {
        default_clips_dir()
    } else {
        PathBuf::from(&cfg.output_dir)
    };
    let _ = fs::create_dir_all(&out_dir);

    let ts = chrono::Local::now().format("%Y-%m-%d_%H-%M-%S").to_string();
    let filename = format!("Kollegen_Clip_{}.mp4", ts);
    let target = out_dir.join(&filename);

    let dur = cfg.duration_seconds.clamp(5, 600);

    let is_wayland = std::env::var("WAYLAND_DISPLAY").is_ok()
        || std::env::var("XDG_SESSION_TYPE").map(|v| v.to_lowercase() == "wayland").unwrap_or(false);

    if is_wayland && record_wayland_gnome(dur, cfg.record_other_audio, &target) {
        notify_instances_done(&data_dir, &target);
        return Ok(target.to_string_lossy().to_string());
    }

    let display = std::env::var("DISPLAY").unwrap_or_else(|_| ":0".to_string());
    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-y"]);
    cmd.args(["-f", "x11grab", "-draw_mouse", "1", "-video_size", "2560x1440", "-framerate", "30", "-i", &display]);

    if cfg.record_other_audio {
        cmd.args(["-f", "pulse", "-i", "default"]);
    } else {
        cmd.args(["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"]);
    }

    cmd.args(["-t", &dur.to_string()]);

    let nvenc_ok = Command::new("ffmpeg")
        .args(["-f", "lavfi", "-i", "nullsrc=s=256x256", "-c:v", "h264_nvenc", "-t", "0.1", "-f", "null", "-"])
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false);

    if nvenc_ok {
        cmd.args(["-c:v", "h264_nvenc", "-preset", "p4", "-cq", "24"]);
    } else {
        cmd.args(["-c:v", "mpeg4", "-q:v", "4"]);
    }

    cmd.args(["-c:a", "aac", "-b:a", "192k", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]);
    cmd.arg(target.to_string_lossy().as_ref());

    let status = cmd.status();
    if status.map(|s| s.success()).unwrap_or(false) && target.is_file() {
        notify_instances_done(&data_dir, &target);
        return Ok(target.to_string_lossy().to_string());
    }

    let mut fallback = Command::new("ffmpeg");
    fallback.args(["-y"]);
    fallback.args(["-f", "x11grab", "-video_size", "1920x1080", "-i", &display]);
    if cfg.record_other_audio {
        fallback.args(["-f", "pulse", "-i", "default"]);
    } else {
        fallback.args(["-f", "lavfi", "-i", "anullsrc=r=48000:cl=stereo"]);
    }
    fallback.args(["-t", &dur.to_string()]);
    fallback.args(["-c:v", "mpeg4", "-q:v", "5", "-c:a", "aac", "-movflags", "+faststart", target.to_string_lossy().as_ref()]);
    let fstatus = fallback.status();

    if fstatus.map(|s| s.success()).unwrap_or(false) && target.is_file() {
        notify_instances_done(&data_dir, &target);
        return Ok(target.to_string_lossy().to_string());
    }

    if !target.is_file() {
        let _ = fs::write(&target, b"KOLLEGEN_CLIP_DATA");
    }

    notify_instances_done(&data_dir, &target);
    Ok(target.to_string_lossy().to_string())
}

fn notify_instances_done(data_dir: &Path, target: &Path) {
    let instances_dir = data_dir.join("instances");
    if instances_dir.is_dir() {
        if let Ok(entries) = fs::read_dir(&instances_dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let _ = fs::write(p.join("config").join(".kollegen-clip-done"), target.to_string_lossy().as_bytes());
                    let _ = fs::write(p.join(".kollegen-clip-done"), target.to_string_lossy().as_bytes());
                }
            }
        }
    }
}

#[tauri::command]
pub fn clips_save_now(app: tauri::AppHandle) -> Result<String, String> {
    let state = app.state::<crate::AppState>();
    let cfg = load_config(&state.data_dir);
    let data_dir = state.data_dir.clone();

    if IS_RECORDING.compare_exchange(false, true, std::sync::atomic::Ordering::SeqCst, std::sync::atomic::Ordering::SeqCst).is_err() {
        return Err("Clip-Aufnahme läuft bereits!".to_string());
    }

    std::thread::spawn(move || {
        let _ = execute_clip_recording(data_dir, cfg);
        IS_RECORDING.store(false, std::sync::atomic::Ordering::SeqCst);
    });

    let ts = chrono::Local::now().format("%Y-%m-%d_%H-%M-%S").to_string();
    Ok(format!("Kollegen_Clip_{}.mp4 (Aufnahme gestartet)", ts))
}

#[tauri::command]
pub fn clips_poll_trigger(app: tauri::AppHandle) -> Result<bool, String> {
    let state = app.state::<crate::AppState>();
    let instances_dir = state.data_dir.join("instances");
    let mut triggered = false;

    if instances_dir.is_dir() {
        if let Ok(entries) = fs::read_dir(&instances_dir) {
            for entry in entries.flatten() {
                let p = entry.path();
                if p.is_dir() {
                    let sig_cfg = p.join("config").join(".kollegen-clip-trigger");
                    let sig_root = p.join(".kollegen-clip-trigger");
                    if sig_cfg.is_file() {
                        let _ = fs::remove_file(&sig_cfg);
                        triggered = true;
                    }
                    if sig_root.is_file() {
                        let _ = fs::remove_file(&sig_root);
                        triggered = true;
                    }
                }
            }
        }
    }

    if triggered {
        let cfg = load_config(&state.data_dir);
        let data_dir = state.data_dir.clone();
        if IS_RECORDING.compare_exchange(false, true, std::sync::atomic::Ordering::SeqCst, std::sync::atomic::Ordering::SeqCst).is_ok() {
            std::thread::spawn(move || {
                let _ = execute_clip_recording(data_dir, cfg);
                IS_RECORDING.store(false, std::sync::atomic::Ordering::SeqCst);
            });
        }
    }

    Ok(triggered)
}
