use crate::error::Result;
use async_trait::async_trait;
use std::sync::Arc;

#[async_trait]
pub trait PostInitializationHandler {
    async fn on_state_ready(&self, app_handle: Arc<tauri::AppHandle>) -> Result<()>;
}
