use crate::minecraft::dto::piston_meta::AssetObject;
use serde::{Deserialize, Serialize};
use std::collections::HashMap;

#[derive(Debug, Serialize, Deserialize)]
pub struct NoriskAssets {
    pub objects: HashMap<String, AssetObject>,
}
