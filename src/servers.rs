use crate::types::{Instance, SavedServer};
use serde_json::{json, Value};
use std::path::{Path, PathBuf};
use std::time::Instant;

fn global_servers_file(data_dir: &Path) -> PathBuf {
    data_dir.join("global_servers.dat")
}

fn read_u16(d: &[u8], o: &mut usize) -> Option<u16> {
    if *o + 2 > d.len() {
        return None;
    }
    let v = u16::from_be_bytes([d[*o], d[*o + 1]]);
    *o += 2;
    Some(v)
}

fn read_u32(d: &[u8], o: &mut usize) -> Option<u32> {
    if *o + 4 > d.len() {
        return None;
    }
    let v = u32::from_be_bytes([d[*o], d[*o + 1], d[*o + 2], d[*o + 3]]);
    *o += 4;
    Some(v)
}

fn read_i32(d: &[u8], o: &mut usize) -> Option<i32> {
    read_u32(d, o).map(|v| v as i32)
}

fn read_nbt_string(d: &[u8], o: &mut usize) -> Option<String> {
    let len = read_u16(d, o)? as usize;
    if *o + len > d.len() {
        return None;
    }
    let s = String::from_utf8_lossy(&d[*o..*o + len]).into_owned();
    *o += len;
    Some(s)
}

fn skip_payload(d: &[u8], o: &mut usize, tag: u8) -> Option<()> {
    match tag {
        0x01 => *o += 1,
        0x02 => *o += 2,
        0x03 | 0x05 => *o += 4,
        0x04 | 0x06 => *o += 8,
        0x07 => {
            let len = read_i32(d, o)?;
            if len < 0 || *o + len as usize > d.len() {
                return None;
            }
            *o += len as usize;
        }
        0x08 => {
            read_nbt_string(d, o)?;
        }
        0x09 => {
            let elem = {
                if *o >= d.len() {
                    return None;
                }
                let t = d[*o];
                *o += 1;
                t
            };
            let count = read_i32(d, o)?;
            if count < 0 {
                return None;
            }
            for _ in 0..count {
                skip_payload(d, o, elem)?;
            }
        }
        0x0A => {
            loop {
                if *o >= d.len() {
                    return None;
                }
                let t = d[*o];
                *o += 1;
                if t == 0x00 {
                    break;
                }
                read_nbt_string(d, o)?;
                skip_payload(d, o, t)?;
            }
        }
        0x0B | 0x0C => {
            let len = read_i32(d, o)?;
            if len < 0 {
                return None;
            }
            let step = if tag == 0x0B { 4 } else { 8 };
            if *o + len as usize * step > d.len() {
                return None;
            }
            *o += len as usize * step;
        }
        _ => return None,
    }
    if *o > d.len() {
        return None;
    }
    Some(())
}

fn parse_compound_payload(d: &[u8], o: &mut usize) -> Option<SavedServer> {
    let mut entry = SavedServer::default();
    loop {
        if *o >= d.len() {
            return None;
        }
        let tag = d[*o];
        *o += 1;
        if tag == 0x00 {
            break;
        }
        let name = read_nbt_string(d, o)?;
        if tag == 0x08 {
            let value = read_nbt_string(d, o)?;
            match name.as_str() {
                "name" => entry.name = value,
                "ip" => entry.ip = value,
                "icon" => entry.icon = Some(value),
                _ => {}
            }
        } else {
            skip_payload(d, o, tag)?;
        }
    }
    Some(entry)
}

pub fn parse_servers_dat(bytes: &[u8]) -> Vec<SavedServer> {
    let mut out = Vec::new();
    if bytes.len() < 2 || bytes[0] != 0x0A {
        return out;
    }
    let mut o = 1usize;
    if read_u16(bytes, &mut o).is_none() {
        return out;
    }
    loop {
        if o >= bytes.len() {
            break;
        }
        let tag = bytes[o];
        o += 1;
        if tag == 0x00 {
            break;
        }
        let name = match read_nbt_string(bytes, &mut o) {
            Some(n) => n,
            None => break,
        };
        if tag == 0x09 && name == "servers" {
            if o >= bytes.len() {
                break;
            }
            let elem = bytes[o];
            o += 1;
            let count = match read_i32(bytes, &mut o) {
                Some(c) if c >= 0 => c as usize,
                _ => break,
            };
            if elem == 0x0A {
                for _ in 0..count {
                    match parse_compound_payload(bytes, &mut o) {
                        Some(e) => out.push(e),
                        None => return out,
                    }
                }
            } else {
                for _ in 0..count {
                    if skip_payload(bytes, &mut o, elem).is_none() {
                        return out;
                    }
                }
            }
        } else if skip_payload(bytes, &mut o, tag).is_none() {
            break;
        }
    }
    out
}

fn push_u16(out: &mut Vec<u8>, v: u16) {
    out.extend_from_slice(&v.to_be_bytes());
}

fn push_u32(out: &mut Vec<u8>, v: u32) {
    out.extend_from_slice(&v.to_be_bytes());
}

fn push_nbt_string(out: &mut Vec<u8>, s: &str) {
    let bytes = s.as_bytes();
    let len = bytes.len().min(u16::MAX as usize) as u16;
    push_u16(out, len);
    out.extend_from_slice(&bytes[..len as usize]);
}

fn push_nbt_tag_string(out: &mut Vec<u8>, name: &str, value: &str) {
    out.push(0x08);
    push_nbt_string(out, name);
    push_nbt_string(out, value);
}

pub fn write_servers_dat(servers: &[SavedServer]) -> Vec<u8> {
    let mut out = Vec::new();
    out.push(0x0A);
    push_u16(&mut out, 0);
    out.push(0x09);
    push_nbt_string(&mut out, "servers");
    out.push(0x0A);
    push_u32(&mut out, servers.len() as u32);
    for s in servers {
        push_nbt_tag_string(&mut out, "name", &s.name);
        push_nbt_tag_string(&mut out, "ip", &s.ip);
        if let Some(icon) = &s.icon {
            if !icon.is_empty() && icon.len() <= u16::MAX as usize {
                push_nbt_tag_string(&mut out, "icon", icon);
            }
        }
        out.push(0x00);
    }
    out.push(0x00);
    out
}

pub fn canonical_server_key(ip: &str) -> String {
    let mut k = ip.trim().to_lowercase();
    if let Some(stripped) = k.strip_suffix(":25565") {
        k = stripped.to_string();
    }
    k
}

pub fn is_generic_server_name(name: &str, ip: &str) -> bool {
    let n = name.trim().to_lowercase();
    if n.is_empty() || n == ip.trim().to_lowercase() {
        return true;
    }
    matches!(
        n.as_str(),
        "minecraft server"
            | "server"
            | "mc server"
            | "default server"
            | "minecraft"
            | "neuer server"
            | "new server"
    )
}

fn is_voice_ip(ip: &str) -> bool {
    let l = ip.to_lowercase();
    l.contains("voice") || l.contains("voicechat")
}

fn read_servers_file(path: &Path) -> Vec<SavedServer> {
    match std::fs::read(path) {
        Ok(bytes) => parse_servers_dat(&bytes),
        Err(_) => Vec::new(),
    }
}

fn instance_servers_file(data_dir: &Path, name: &str) -> PathBuf {
    crate::utils::instance_dir(data_dir, name).join("servers.dat")
}

fn load_instances(data_dir: &Path) -> Vec<Instance> {
    crate::utils::load_json(&crate::utils::instances_file(data_dir), vec![])
}

pub fn merged_servers(data_dir: &Path) -> Vec<SavedServer> {
    let mut merged: Vec<SavedServer> = Vec::new();
    let push_all = |list: Vec<SavedServer>, merged: &mut Vec<SavedServer>| {
        for s in list {
            let ip = s.ip.trim().to_string();
            if ip.is_empty() || is_voice_ip(&ip) {
                continue;
            }
            let key = canonical_server_key(&ip);
            match merged.iter_mut().find(|m| canonical_server_key(&m.ip) == key) {
                None => merged.push(SavedServer {
                    name: {
                        let n = s.name.trim().to_string();
                        if n.is_empty() { ip.clone() } else { n }
                    },
                    ip,
                    icon: s.icon,
                }),
                Some(existing) => {
                    let generic_now = is_generic_server_name(&existing.name, &existing.ip);
                    if generic_now && !is_generic_server_name(&s.name, &ip) {
                        existing.name = s.name.trim().to_string();
                    }
                    if existing.icon.is_none() && s.icon.is_some() {
                        existing.icon = s.icon;
                    }
                }
            }
        }
    };
    push_all(read_servers_file(&global_servers_file(data_dir)), &mut merged);
    let mut instances = load_instances(data_dir);
    instances.sort_by(|a, b| a.name.cmp(&b.name));
    for inst in instances {
        push_all(read_servers_file(&instance_servers_file(data_dir, &inst.name)), &mut merged);
    }
    merged
}

pub fn sync_all_servers(data_dir: &Path) -> Result<usize, String> {
    let merged = merged_servers(data_dir);
    let bytes = write_servers_dat(&merged);
    std::fs::write(global_servers_file(data_dir), &bytes)
        .map_err(|e| format!("global_servers.dat: {}", e))?;
    let mut instances = load_instances(data_dir);
    instances.sort_by(|a, b| a.name.cmp(&b.name));
    for inst in instances {
        let path = instance_servers_file(data_dir, &inst.name);
        if let Some(parent) = path.parent() {
            let _ = std::fs::create_dir_all(parent);
        }
        let _ = std::fs::write(&path, &bytes);
    }
    Ok(merged.len())
}

fn options_file(data_dir: &Path, instance: &str) -> PathBuf {
    crate::utils::instance_dir(data_dir, instance).join("options.txt")
}

fn read_keybind_lines(path: &Path) -> Option<Vec<String>> {
    let content = std::fs::read_to_string(path).ok()?;
    Some(
        content
            .lines()
            .filter(|l| l.starts_with("key_"))
            .map(|l| l.to_string())
            .collect(),
    )
}

pub fn sync_keybinds(data_dir: &Path) -> Result<usize, String> {
    let mut instances = load_instances(data_dir);
    if instances.len() < 2 {
        return Ok(0);
    }
    let source = instances
        .iter()
        .find(|i| i.name == "3")
        .or_else(|| {
            instances
                .iter()
                .filter(|i| i.last_played.is_some())
                .max_by(|a, b| a.last_played.cmp(&b.last_played))
        })
        .or_else(|| instances.first())
        .map(|i| i.name.clone())
        .ok_or_else(|| "Keine Instanz gefunden".to_string())?;
    let keys = read_keybind_lines(&options_file(data_dir, &source))
        .ok_or_else(|| format!("options.txt von {} fehlt", source))?;
    if keys.is_empty() {
        return Err(format!("Keine Keybinds in {} gefunden", source));
    }
    let mut synced = 0usize;
    instances.sort_by(|a, b| a.name.cmp(&b.name));
    for inst in &instances {
        if inst.name == source {
            continue;
        }
        let path = options_file(data_dir, &inst.name);
        let existing = std::fs::read_to_string(&path).unwrap_or_default();
        let kept: Vec<&str> = existing.lines().filter(|l| !l.starts_with("key_")).collect();
        let mut content = kept.join("\n");
        if !content.ends_with('\n') && !content.is_empty() {
            content.push('\n');
        }
        for k in &keys {
            content.push_str(k);
            content.push('\n');
        }
        if std::fs::write(&path, content).is_ok() {
            synced += 1;
        }
    }
    Ok(synced)
}

fn lookup_saved(data_dir: &Path, ip: &str) -> Option<SavedServer> {
    let key = canonical_server_key(ip);
    merged_servers(data_dir)
        .into_iter()
        .find(|s| canonical_server_key(&s.ip) == key)
}

fn status_offline(ip: &str, name: &str, icon: Option<String>, ping_ms: u64) -> Value {
    json!({
        "ip": ip,
        "name": name,
        "online": false,
        "players_online": 0,
        "players_max": 0,
        "motd_clean": "",
        "motd_html": "",
        "ping_ms": ping_ms,
        "icon": icon,
    })
}

fn fetch_status(target: &str) -> Result<(Value, u64), String> {
    let url = if let Some(colon) = target.rfind(':') {
        if target[colon+1..].chars().all(|c| c.is_ascii_digit()) {
            format!(
                "https://api.mcstatus.io/v2/status/java/{}:{}",
                urlencoding::encode(&target[..colon]),
                &target[colon+1..]
            )
        } else {
            format!(
                "https://api.mcstatus.io/v2/status/java/{}",
                urlencoding::encode(target)
            )
        }
    } else {
        format!(
            "https://api.mcstatus.io/v2/status/java/{}",
            urlencoding::encode(target)
        )
    };
    let client = reqwest::blocking::Client::builder()
        .timeout(std::time::Duration::from_secs(5))
        .connect_timeout(std::time::Duration::from_secs(4))
        .user_agent(crate::USER_AGENT)
        .build()
        .map_err(|e| e.to_string())?;
    let start = Instant::now();
    let resp = client
        .get(&url)
        .send()
        .map_err(|e| e.to_string())?;
    if !resp.status().is_success() {
        return Err(format!("HTTP {}", resp.status()));
    }
    let v: Value = resp.json().map_err(|e| e.to_string())?;
    let ping_ms = start.elapsed().as_millis() as u64;
    Ok((v, ping_ms))
}

pub fn get_server_status(
    data_dir: &Path,
    instance_name: Option<&str>,
    server_ip: Option<&str>,
) -> Value {
    let mut ip: Option<String> = None;
    if let Some(s) = server_ip.map(str::trim).filter(|s| !s.is_empty()) {
        if !is_voice_ip(s) {
            ip = Some(s.to_string());
        }
    }
    if ip.is_none() {
        if let Some(name) = instance_name {
            let instances: Vec<Instance> = crate::utils::load_json(
                &crate::utils::instances_file(data_dir),
                vec![],
            );
            if let Some(inst) = instances.iter().find(|i| i.name == name) {
                if let Some(s) = inst.server.as_deref().map(str::trim).filter(|s| !s.is_empty()) {
                    if !is_voice_ip(s) {
                        ip = Some(s.to_string());
                    }
                }
            }
        }
    }
    let ip = match ip {
        Some(i) => i,
        None => return json!({ "ip": null }),
    };

    let saved = lookup_saved(data_dir, &ip);
    let saved_name = saved
        .as_ref()
        .map(|s| s.name.trim().to_string())
        .filter(|n| !n.is_empty())
        .filter(|n| !is_generic_server_name(n, &ip))
        .unwrap_or_else(|| ip.clone());
    let saved_icon = saved.and_then(|s| s.icon);

    let target = ip.clone();

    match fetch_status(&target) {
        Ok((v, ping_ms)) => {
            let online = v.get("online").and_then(Value::as_bool).unwrap_or(false);
            if !online {
                return status_offline(&ip, &saved_name, saved_icon, ping_ms);
            }
            let players = v.get("players");
            let players_online = players
                .and_then(|p| p.get("online"))
                .and_then(Value::as_u64)
                .unwrap_or(0);
            let players_max = players
                .and_then(|p| p.get("max"))
                .and_then(Value::as_u64)
                .unwrap_or(0);
            let motd = v.get("motd");
            let motd_clean = motd
                .and_then(|m| m.get("clean"))
                .and_then(Value::as_str)
                .unwrap_or("")
                .to_string();
            let motd_html = motd
                .and_then(|m| m.get("html"))
                .and_then(Value::as_str)
                .unwrap_or("")
                .to_string();
            let icon = v
                .get("icon")
                .and_then(Value::as_str)
                .map(|s| s.to_string())
                .or(saved_icon);
            json!({
                "ip": ip,
                "name": saved_name,
                "online": true,
                "players_online": players_online,
                "players_max": players_max,
                "motd_clean": motd_clean,
                "motd_html": motd_html,
                "ping_ms": ping_ms,
                "icon": icon,
            })
        }
        Err(_) => status_offline(&ip, &saved_name, saved_icon, 0),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn roundtrip_servers_dat() {
        let servers = vec![
            SavedServer {
                name: "MC PVP".to_string(),
                ip: "mcpvp.club".to_string(),
                icon: None,
            },
            SavedServer {
                name: "GommeHD".to_string(),
                ip: "gommehd.net".to_string(),
                icon: Some("data:image/png;base64,AAAA".to_string()),
            },
        ];
        let bytes = write_servers_dat(&servers);
        let parsed = parse_servers_dat(&bytes);
        assert_eq!(parsed.len(), 2);
        assert_eq!(parsed[0].name, "MC PVP");
        assert_eq!(parsed[0].ip, "mcpvp.club");
        assert_eq!(parsed[0].icon, None);
        assert_eq!(parsed[1].name, "GommeHD");
        assert_eq!(
            parsed[1].icon.as_deref(),
            Some("data:image/png;base64,AAAA")
        );
    }

    #[test]
    fn parse_real_global_servers() {
        let path = dirs::home_dir()
            .unwrap()
            .join(".local/share/kollegenclient/global_servers.dat");
        if let Ok(bytes) = std::fs::read(path) {
            let parsed = parse_servers_dat(&bytes);
            assert!(!parsed.is_empty(), "global_servers.dat parsed empty");
            assert!(
                parsed.iter().any(|s| s.ip.contains("mcpvp")),
                "mcpvp.club missing"
            );
            assert!(
                parsed.iter().all(|s| !s.ip.is_empty()),
                "empty ip in parsed list"
            );
        }
    }

    #[test]
    fn canonical_key_strips_port() {
        assert_eq!(canonical_server_key("MC.Craft.net:25565"), "mc.craft.net");
        assert_eq!(canonical_server_key("mc.craft.net"), "mc.craft.net");
    }
}

