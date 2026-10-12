use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeVersion {
    #[serde(rename = "_comment")]
    pub comment: Option<Vec<String>>,
    pub id: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: String,
    pub r#type: String,
    pub logging: Option<NeoForgeLogging>,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    pub libraries: Vec<NeoForgeLibrary>,
    #[serde(default)]
    pub arguments: Option<NeoForgeArguments>,
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeArguments {
    #[serde(default)]
    pub game: Vec<String>,
    #[serde(default)]
    pub jvm: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeLogging {
    // Empty for now, as the JSON shows an empty object
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeLibrary {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<NeoForgeLibraryDownloads>,
    #[serde(default)]
    pub url: Option<String>,
    #[serde(default)]
    pub checksums: Option<Vec<String>>,
    #[serde(default)]
    pub serverreq: bool,
    #[serde(default)]
    pub clientreq: bool,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeLibraryDownloads {
    pub artifact: Option<NeoForgeDownloadInfo>,
    #[serde(default)]
    pub classifiers: std::collections::HashMap<String, NeoForgeDownloadInfo>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct NeoForgeDownloadInfo {
    pub path: String,
    pub url: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    pub size: i64,
}
