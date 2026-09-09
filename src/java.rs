

use anyhow::{anyhow, Result};
use log::{info, warn};
use std::fs;
use std::path::{Path, PathBuf};
use std::process::Command;
use std::time::Duration;


fn java_exe() -> &'static str {
    if cfg!(target_os = "windows") { "java.exe" } else { "java" }
}















pub fn sanitize_java_env(cmd: &mut Command) {
    cmd.env_remove("LD_LIBRARY_PATH");
    cmd.env_remove("LD_PRELOAD");
}





pub fn find_java(data_dir: &Path, required_version: u32) -> Result<String> {
    let exe = java_exe();

    
    let mut exact: Vec<PathBuf> = Vec::new();
    exact.push(data_dir.join(format!("jre-{}", required_version)).join("bin").join(exe));
    if let Ok(home) = std::env::var("JAVA_HOME") {
        exact.push(PathBuf::from(home).join("bin").join(exe));
    }
    exact.push(data_dir.join("jre").join("bin").join(exe));

    for p in &exact {
        if p.exists() && java_major(p) == Some(required_version) {
            return Ok(p.to_string_lossy().into_owned());
        }
    }

    
    let which_cmd = if cfg!(target_os = "windows") { "where" } else { "which" };
    if let Ok(output) = Command::new(which_cmd).arg("java").output() {
        if output.status.success() {
            let path = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !path.is_empty() && java_major(Path::new(&path)) == Some(required_version) {
                return Ok(path);
            }
        }
    }

    
    
    
    let mut candidates: Vec<PathBuf> = Vec::new();
    if let Ok(entries) = std::fs::read_dir(data_dir) {
        for e in entries.flatten() {
            let name = e.file_name().to_string_lossy().to_string();
            if let Some(n) = name.strip_prefix("jre-") {
                if n.parse::<u32>().is_ok() {
                    candidates.push(e.path().join("bin").join(exe));
                }
            }
        }
    }
    if let Ok(home) = std::env::var("JAVA_HOME") {
        candidates.push(PathBuf::from(home).join("bin").join(exe));
    }
    candidates.push(data_dir.join("jre").join("bin").join(exe));
    if let Ok(output) = Command::new(which_cmd).arg("java").output() {
        if output.status.success() {
            let path = String::from_utf8_lossy(&output.stdout)
                .lines()
                .next()
                .unwrap_or("")
                .trim()
                .to_string();
            if !path.is_empty() {
                candidates.push(PathBuf::from(path));
            }
        }
    }

    let mut probed: Vec<String> = Vec::new();
    let mut best: Option<(u32, PathBuf)> = None;
    for c in &candidates {
        if c.as_os_str().is_empty() || !c.exists() {
            continue;
        }
        if let Some(major) = java_major(c) {
            if major >= required_version {
                let better = match best {
                    Some((m, _)) => major < m,
                    None => true,
                };
                if better {
                    best = Some((major, c.clone()));
                }
            }
        } else {
            probed.push(format!("{} (Probe fehlgeschlagen)", c.display()));
        }
    }

    if let Some((major, p)) = best {
        warn!(
            "Java {} nicht exakt gefunden, verwende verfügbares Java {} als Fallback.",
            required_version, major
        );
        return Ok(p.to_string_lossy().into_owned());
    }

    let detail = if probed.is_empty() {
        String::new()
    } else {
        format!(" Geprüfte Kandidaten: {}", probed.join("; "))
    };
    Err(anyhow!(
        "Java {} nicht gefunden. Bitte JRE herunterladen oder JAVA_HOME setzen.{}",
        required_version, detail
    ))
}



pub fn download_jre_internal(version: u32) -> Result<String> {
    let data_dir = crate::utils::get_project_dirs()?;
    let jre_dir = data_dir.join(format!("jre-{}", version));

    let os_name = if cfg!(target_os = "linux") {
        "linux"
    } else if cfg!(target_os = "windows") {
        "windows"
    } else if cfg!(target_os = "macos") {
        "mac"
    } else {
        "linux"
    };

    let arch = if cfg!(target_arch = "x86_64") {
        "x64"
    } else if cfg!(target_arch = "aarch64") {
        "aarch64"
    } else {
        "x64"
    };

    let url = format!(
        "https://api.adoptium.net/v3/binary/latest/{}/ga/{}/{}/jre/hotspot/normal/eclipse",
        version, os_name, arch
    );

    info!("Downloading bundled JRE {}...", version);
    let client = reqwest::blocking::Client::builder()
        .user_agent(crate::USER_AGENT)
        .timeout(Duration::from_secs(120))
        .build()?;

    let resp = client.get(&url).send()?;
    if !resp.status().is_success() {
        return Err(anyhow!("JRE-Download fehlgeschlagen: HTTP {}", resp.status()));
    }

    let data = resp.bytes()?;
    fs::create_dir_all(&jre_dir)?;

    
    if os_name == "windows" {
        use std::io::Cursor;
        let mut zip = zip::ZipArchive::new(Cursor::new(data))?;
        zip.extract(&jre_dir)?;
    } else {
        use std::io::Cursor;
        let gz = flate2::read::GzDecoder::new(Cursor::new(data));
        let mut tar = tar::Archive::new(gz);
        tar.unpack(&jre_dir)?;
    }

    
    
    
    let java_bin = jre_dir.join("bin").join(java_exe());
    if !java_bin.exists() {
            if let Some(found) = find_java_bin(&jre_dir) {
                
                
                
                
                if let Some(root) = found.parent().and_then(|b| b.parent()) {
                    if root != jre_dir {
                    if let Ok(entries) = std::fs::read_dir(&root) {
                        for e in entries.flatten() {
                            let dest = jre_dir.join(e.file_name());
                            let _ = std::fs::rename(e.path(), &dest);
                        }
                    }
                    let _ = std::fs::remove_dir_all(&root);
                }
            }
            
            let nested = jre_dir.join("jre");
            if nested.is_dir() {
                if let Ok(entries) = std::fs::read_dir(&nested) {
                    for e in entries.flatten() {
                        let dest = jre_dir.join(e.file_name());
                        let _ = std::fs::rename(e.path(), &dest);
                    }
                }
                let _ = std::fs::remove_dir_all(&nested);
            }
        }
    }

    
    
    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        if java_bin.exists() {
            if let Ok(mut perms) = std::fs::metadata(&java_bin).map(|m| m.permissions()) {
                perms.set_mode(0o755);
                let _ = std::fs::set_permissions(&java_bin, perms);
            }
        }
    }

    if !java_bin.exists() {
        return Err(anyhow!(
            "JRE wurde heruntergeladen, aber java konnte nicht gefunden werden."
        ));
    }

    Ok(java_bin.to_string_lossy().into_owned())
}


fn find_java_bin(dir: &Path) -> Option<PathBuf> {
    let bin = dir.join("bin").join(java_exe());
    if bin.exists() {
        return Some(bin);
    }
    if let Ok(entries) = std::fs::read_dir(dir) {
        for e in entries.flatten() {
            let p = e.path();
            if p.is_dir() {
                if let Some(f) = find_java_bin(&p) {
                    return Some(f);
                }
            }
        }
    }
    None
}


fn java_major(java_path: &Path) -> Option<u32> {
    
    
    
    
    
    
    
    for sanitized in [true, false] {
        let mut cmd = Command::new(java_path);
        if sanitized {
            sanitize_java_env(&mut cmd);
        }
        if let Some(major) = parse_java_major(&mut cmd) {
            return Some(major);
        }
    }
    None
}


fn parse_java_major(cmd: &mut Command) -> Option<u32> {
    let output = cmd.arg("-version").output().ok()?;
    let text = String::from_utf8_lossy(&output.stderr);
    let line = text.lines().next()?;
    let ver = line.split('"').nth(1)?;
    if ver.starts_with("1.") {
        
        return ver[2..3].parse::<u32>().ok();
    }
    ver.split('.').next()?.parse::<u32>().ok()
}
