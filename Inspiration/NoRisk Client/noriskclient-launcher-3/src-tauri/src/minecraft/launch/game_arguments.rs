use crate::config::{ProjectDirsExt, LAUNCHER_DIRECTORY};
use crate::minecraft::dto::piston_meta::{ArgumentValue, ComplexArgument, GameArgument};
use crate::minecraft::minecraft_auth::Credentials;
use std::path::PathBuf;

pub struct GameArguments {
    credentials: Option<Credentials>,
    version_id: String,
    game_directory: PathBuf,
    version_type: String,
    asset_index_id: String,
}

impl GameArguments {
    pub fn new(
        credentials: Option<Credentials>,
        version_id: String,
        game_directory: PathBuf,
        version_type: String,
        asset_index_id: String,
    ) -> Self {
        Self {
            credentials,
            version_id,
            game_directory,
            version_type,
            asset_index_id,
        }
    }

    fn should_apply_argument(argument: &ComplexArgument) -> bool {
        for rule in &argument.rules {
            if rule.action == "allow" {
                if let Some(features) = &rule.features {
                    // Only apply if ALL features match our settings
                    // Currently we're not in demo mode, don't have custom resolution,
                    // and don't have any QuickPlay features enabled
                    if features.is_demo_user == Some(true) {
                        return false; // We're not in demo mode
                    }
                    if features.has_custom_resolution == Some(true) {
                        return false; // We don't have custom resolution
                    }
                    if features.has_quick_plays_support == Some(true) {
                        return false; // We don't have QuickPlay support
                    }
                    if features.is_quick_play_singleplayer == Some(true) {
                        return false; // We don't have QuickPlay singleplayer
                    }
                    if features.is_quick_play_multiplayer == Some(true) {
                        return false; // We don't have QuickPlay multiplayer
                    }
                    if features.is_quick_play_realms == Some(true) {
                        return false; // We don't have QuickPlay realms
                    }
                }
                return true; // If we get here, no features prevented us from applying
            }
        }
        false
    }

    fn process_argument_value(value: &ArgumentValue) -> Vec<String> {
        match value {
            ArgumentValue::Single(s) => vec![s.clone()],
            ArgumentValue::Multiple(v) => v.clone(),
        }
    }

    pub fn replace_variables(&self, arg: &str) -> String {
        arg.replace(
            "${auth_player_name}",
            &self
                .credentials
                .as_ref()
                .map(|c| c.username.clone())
                .unwrap_or_else(|| "Player".to_string()),
        )
        .replace("${version_name}", &self.version_id)
        .replace("${game_directory}", &self.game_directory.to_string_lossy())
        .replace(
            "${assets_root}",
            &LAUNCHER_DIRECTORY
                .meta_dir()
                .join("assets")
                .to_string_lossy(),
        )
        .replace(
            "${game_assets}",
            &LAUNCHER_DIRECTORY
                .meta_dir()
                .join("assets")
                .to_string_lossy(),
        )
        .replace("${assets_index_name}", &self.asset_index_id)
        .replace(
            "${auth_uuid}",
            &self
                .credentials
                .as_ref()
                .map(|c| c.id.to_string())
                .unwrap_or_else(|| "00000000-0000-0000-0000-000000000000".to_string()),
        )
        .replace(
            "${auth_access_token}",
            &self
                .credentials
                .as_ref()
                .map(|c| c.access_token.clone())
                .unwrap_or_else(|| "0".to_string()),
        )
        .replace("${clientid}", "c4502edb-87c6-40cb-b595-64a280cf8906")
        .replace("${auth_xuid}", "0")
        .replace("${user_type}", "msa")
        .replace("${version_type}", &self.version_type)
        .replace("${user_properties}", "{}")
    }

    pub fn process_arguments(&self, arguments: &[GameArgument]) -> Vec<String> {
        let mut processed_args = Vec::new();

        for arg in arguments {
            match arg {
                GameArgument::Simple(s) => {
                    let processed_arg = self.replace_variables(s);
                    processed_args.push(processed_arg);
                }
                GameArgument::Complex(complex) => {
                    if Self::should_apply_argument(complex) {
                        let values = Self::process_argument_value(&complex.value);
                        processed_args.extend(values);
                    }
                }
            }
        }

        processed_args
    }
}
