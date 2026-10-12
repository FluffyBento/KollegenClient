use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Debug, Clone, PartialEq)]
pub enum SkinModelVariant {
    #[serde(rename = "classic")]
    Classic,
    #[serde(rename = "slim")]
    Slim,
}

impl std::fmt::Display for SkinModelVariant {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        match self {
            SkinModelVariant::Classic => write!(f, "classic"),
            SkinModelVariant::Slim => write!(f, "slim"),
        }
    }
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct ProfileSourceData {
    pub query: String, // Benutzername oder UUID
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct UrlSourceData {
    pub url: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct FilePathSourceData {
    pub path: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct Base64SourceData {
    pub base64_content: String,
}

#[derive(Serialize, Deserialize, Debug, Clone)]
#[serde(tag = "type", content = "details")]
pub enum SkinSource {
    Profile(ProfileSourceData),
    Url(UrlSourceData),
    FilePath(FilePathSourceData),
    Base64(Base64SourceData),
}

#[derive(Serialize, Deserialize, Debug, Clone)]
pub struct AddLocalSkinCommandPayload {
    pub source: SkinSource,
    pub target_skin_name: String,
    pub target_skin_variant: SkinModelVariant,
    pub description: Option<String>,
}
