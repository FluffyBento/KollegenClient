use serde::{Deserialize, Serialize};

#[derive(Debug, Serialize, Deserialize)]
pub struct ForgeVersion {
    #[serde(rename = "_comment")]
    pub comment: Option<Vec<String>>,
    pub id: String,
    pub time: String,
    #[serde(rename = "releaseTime")]
    pub release_time: String,
    #[serde(rename = "inheritsFrom")]
    pub inherits_from: String,
    pub r#type: String,
    pub logging: Option<ForgeLogging>,
    #[serde(rename = "mainClass")]
    pub main_class: String,
    pub libraries: Vec<ForgeLibrary>,
    #[serde(default)]
    pub arguments: Option<ForgeArguments>,
    #[serde(rename = "minecraftArguments")]
    pub minecraft_arguments: Option<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ForgeArguments {
    #[serde(default)]
    pub game: Vec<String>,
    #[serde(default)]
    pub jvm: Vec<String>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ForgeLogging {
    // Empty for now, as the JSON shows an empty object
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ForgeLibrary {
    pub name: String,
    #[serde(default)]
    pub downloads: Option<ForgeLibraryDownloads>,
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
pub struct ForgeLibraryDownloads {
    pub artifact: Option<ForgeDownloadInfo>,
    #[serde(default)]
    pub classifiers: std::collections::HashMap<String, ForgeDownloadInfo>,
}

#[derive(Debug, Serialize, Deserialize)]
pub struct ForgeDownloadInfo {
    pub path: String,
    pub url: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    pub size: i64,
}
