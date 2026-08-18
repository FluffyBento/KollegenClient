// Data structures and types for the Kollegen Client launcher

use serde::{Deserialize, Serialize};

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Account {
    pub username: String,
    pub uuid: String,
    pub access_token: String,
    pub refresh_token: Option<String>,
    pub expires_at: Option<u64>,
    pub avatar_id: Option<String>,
    pub xuid: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Instance {
    /// Unique id so instance management (delete/join) can always address exactly
    /// one instance, even when several share the same display name (legacy data
    /// without an id falls back to name-based first-match handling).
    #[serde(default)]
    pub id: String,
    pub name: String,
    pub version: String,
    pub loader: String, // Vanilla | Fabric | Forge | NeoForge | Quilt
    pub loader_version: Option<String>,
    pub description: String,
    pub mods: Vec<String>,
    pub vulkan_enabled: bool,
    pub memory_min: String,
    pub memory_max: String,
    pub created_at: String,
    pub last_played: Option<String>,
    pub java_args: Option<String>,
    pub server: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Settings {
    pub java_path: String,
    pub minecraft_dir: String,
    pub memory_min: String,
    pub memory_max: String,
    pub global_vulkan: bool,
    pub lang: String,
    pub theme: String,
    #[serde(default)]
    pub theme_mode: String,
    pub proxy: Option<String>,
    /// Basis-URL des externen Presence-Backends (z. B. "https://presence.kollegen.dev").
    /// Leer = Presence-Feature aus. Umgebungsvariable KOLLEGEN_PRESENCE_BACKEND hat Vorrang.
    #[serde(default)]
    pub presence_backend: String,
    /// Optionales Bearer-Token für das Presence-Backend. Umgebungsvariable
    /// KOLLEGEN_PRESENCE_TOKEN hat Vorrang.
    #[serde(default)]
    pub presence_token: String,
}

impl Default for Settings {
    fn default() -> Self {
        let home = dirs::home_dir().unwrap_or_else(|| std::path::PathBuf::from("."));
        Self {
            java_path: String::new(),
            minecraft_dir: home.join(".minecraft").to_string_lossy().into_owned(),
            memory_min: crate::DEFAULT_MEMORY_MIN.to_string(),
            memory_max: crate::DEFAULT_MEMORY_MAX.to_string(),
            global_vulkan: true,
            lang: "de".to_string(),
            theme: "Limit_Los".to_string(),
            theme_mode: "dark".to_string(),
            proxy: None,
            presence_backend: "http://5.175.192.69:8080".to_string(),
            presence_token: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MojangVersionEntry {
    pub id: String,
    #[serde(rename = "type")]
    pub version_type: String,
    pub url: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct MojangVersionManifest {
    pub versions: Vec<MojangVersionEntry>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionJson {
    #[serde(rename = "mainClass")]
    pub main_class: Option<String>,
    pub assets: Option<String>,
    #[serde(rename = "assetIndex")]
    pub asset_index: Option<AssetIndexInfo>,
    #[serde(default)]
    pub libraries: Vec<LibraryEntry>,
    #[serde(rename = "javaVersion")]
    pub java_version: Option<JavaVersionInfo>,
    #[serde(rename = "downloads")]
    pub downloads: Option<VersionDownloads>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AssetIndexInfo {
    pub url: String,
    pub sha1: String,
    pub size: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LibraryEntry {
    pub name: String,
    pub downloads: Option<LibraryDownloads>,
    pub rules: Option<Vec<LibraryRule>>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LibraryDownloads {
    pub artifact: Option<ArtifactInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ArtifactInfo {
    pub url: String,
    pub path: String,
    pub sha1: String,
    pub size: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct LibraryRule {
    pub action: String,
    pub os: Option<OsRule>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct OsRule {
    pub name: String,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct JavaVersionInfo {
    pub component: String,
    #[serde(rename = "majorVersion")]
    pub major_version: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct VersionDownloads {
    pub client: Option<DownloadInfo>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DownloadInfo {
    pub url: String,
    pub sha1: String,
    pub size: u32,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModrinthProject {
    pub project_id: String,
    pub title: String,
    pub description: Option<String>,
    pub client_side: String,
    pub project_type: String,
    pub latest_version: String,
    pub icon_url: Option<String>,
    pub downloads: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ModEntry {
    pub filename: String,
    pub enabled: bool,
    pub size: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ServerEntry {
    pub name: String,
    pub ip: String,
    pub players: Option<String>,
    pub online: bool,
    pub version: Option<String>,
    pub motd: Option<String>,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct NewsEntry {
    pub title: String,
    pub content: String,
    pub image: Option<String>,
    pub date: String,
    pub url: String,
}
