use serde::Deserialize;
use std::collections::HashMap;

use super::forge_meta::ForgeLibrary;

#[derive(Debug, Deserialize)]
pub struct ForgeInstallProfile {
    #[serde(rename = "hideExtract")]
    pub hide_extract: Option<bool>,
    pub spec: Option<i32>,
    pub profile: Option<String>,
    pub version: String,
    pub path: Option<String>,
    pub minecraft: String,
    #[serde(rename = "serverJarPath")]
    pub server_jar_path: Option<String>,
    pub data: HashMap<String, ForgeDataEntry>,
    pub processors: Vec<ForgeProcessor>,
    pub libraries: Vec<ForgeLibrary>,
}

#[derive(Debug, Deserialize)]
pub struct ForgeDataEntry {
    pub client: String,
    pub server: String,
}

#[derive(Debug, Deserialize)]
pub struct ForgeProcessor {
    pub sides: Option<Vec<String>>,
    pub jar: String,
    pub classpath: Option<Vec<String>>,
    pub args: Option<Vec<String>>,
    pub outputs: Option<HashMap<String, String>>,
}
