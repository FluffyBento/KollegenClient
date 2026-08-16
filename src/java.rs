// Java utilities for the Kollegen Client launcher

use anyhow::{anyhow, Result};
use log::info;
use std::fs;
use std::path::Path;
use std::process::Command;
use std::time::Duration;

/// Finds a Java executable matching the required major version.
/// Looks for a versioned bundled JRE first, then checks JAVA_HOME, the
/// generic bundled JRE, and the system PATH (verifying the actual version).
pub fn find_java(data_dir: &Path, required_version: u32) -> Result<String> {
    // 1. Versioned bundled JRE (e.g. jre-21/bin/java)
    let versioned = data_dir
        .join(format!("jre-{}", required_version))
        .join("bin")
        .join(if cfg!(target_os = "windows") {
            "java.exe"
        } else {
            "java"
        });
    if versioned.exists() {
        return Ok(versioned.to_string_lossy().into_owned());
    }

    // 2. JAVA_HOME (verify version)
    if let Ok(java_home) = std::env::var("JAVA_HOME") {
        let java = std::path::PathBuf::from(java_home).join("bin").join("java");
        if java.exists() && java_major(&java) == Some(required_version) {
            return Ok(java.to_string_lossy().into_owned());
        }
    }

    // 3. Generic bundled JRE (verify version)
    let bundled = data_dir.join("jre").join("bin").join("java");
    if bundled.exists() && java_major(&bundled) == Some(required_version) {
        return Ok(bundled.to_string_lossy().into_owned());
    }

    // 4. System PATH (verify version)
    if let Ok(output) = Command::new("which").arg("java").output() {
        if output.status.success() {
            let path = String::from_utf8_lossy(&output.stdout).trim().to_string();
            if !path.is_empty() && java_major(Path::new(&path)) == Some(required_version) {
                return Ok(path);
            }
        }
    }

    Err(anyhow!(
        "Java {} nicht gefunden. Bitte JRE herunterladen oder JAVA_HOME setzen.",
        required_version
    ))
}

/// Downloads a bundled JRE for the given major version into a versioned
/// directory (e.g. jre-21). Returns the path to the java executable.
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

    // Extract and fix directory layout
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

    // TAR.GZ archives from Adoptium extract into a subdirectory like jdk-21.x.x-jre/
    // We need to flatten that so jre-21/bin/java exists
    let java_bin = jre_dir.join("bin").join(if cfg!(target_os = "windows") {
        "java.exe"
    } else {
        "java"
    });
    if !java_bin.exists() {
        if let Ok(entries) = std::fs::read_dir(&jre_dir) {
            for entry in entries.flatten() {
                let path = entry.path();
                if path.is_dir() && path.join("bin").join("java").exists() {
                    for sub_entry in std::fs::read_dir(&path).unwrap().flatten() {
                        let src = sub_entry.path();
                        let dest = jre_dir.join(sub_entry.file_name());
                        std::fs::rename(&src, &dest).ok();
                    }
                    let _ = std::fs::remove_dir(&path);
                    break;
                }
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

/// Returns the major version of a java executable (e.g. 21, 17, 8).
fn java_major(java_path: &Path) -> Option<u32> {
    let output = Command::new(java_path).arg("-version").output().ok()?;
    let text = String::from_utf8_lossy(&output.stderr);
    let line = text.lines().next()?;
    let ver = line.split('"').nth(1)?;
    if ver.starts_with("1.") {
        // Legacy style: 1.8.0_411 -> 8
        return ver[2..3].parse::<u32>().ok();
    }
    ver.split('.').next()?.parse::<u32>().ok()
}
