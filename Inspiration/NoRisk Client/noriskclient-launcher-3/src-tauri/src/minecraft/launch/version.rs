use semver::Version;
use std::cmp::Ordering;

pub fn compare_versions(new: &str, old: &str) -> Ordering {
    // Versuche zuerst Semver
    match (Version::parse(new), Version::parse(old)) {
        (Ok(new_ver), Ok(old_ver)) => {
            // Beide sind gültige Semver-Versionen
            return new_ver.cmp(&old_ver);
        }
        _ => {
            // Mindestens eine ist keine Semver-Version,
            // verwende Maven-Vergleich
            compare_maven_versions(new, old)
        }
    }
}

fn compare_maven_versions(new: &str, old: &str) -> Ordering {
    let new_parts = split_version(new);
    let old_parts = split_version(old);

    for (new_part, old_part) in new_parts.iter().zip(old_parts.iter()) {
        match (new_part.parse::<u32>(), old_part.parse::<u32>()) {
            (Ok(new_num), Ok(old_num)) => {
                if new_num != old_num {
                    return new_num.cmp(&old_num);
                }
            }
            (Ok(_), Err(_)) => return Ordering::Greater,
            (Err(_), Ok(_)) => return Ordering::Less,
            (Err(_), Err(_)) => {
                // Beide sind nicht-numerisch, vergleiche als String
                if new_part != old_part {
                    return new_part.cmp(old_part);
                }
            }
        }
    }

    new_parts.len().cmp(&old_parts.len())
}

fn split_version(version: &str) -> Vec<String> {
    let mut parts = Vec::new();
    let mut current = String::new();

    for c in version.chars() {
        if c.is_digit(10) {
            if !current.is_empty() && !current.chars().last().unwrap().is_digit(10) {
                parts.push(current);
                current = String::new();
            }
        } else {
            if !current.is_empty() && current.chars().last().unwrap().is_digit(10) {
                parts.push(current);
                current = String::new();
            }
        }
        current.push(c);
    }

    if !current.is_empty() {
        parts.push(current);
    }

    parts
}
