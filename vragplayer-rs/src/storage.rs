use crate::model::PersistedState;
use std::fs;
use std::path::{Path, PathBuf};

const STATE_FILE_NAME: &str = "vrag_state.json";

pub fn default_data_dir() -> PathBuf {
    if let Ok(xdg) = std::env::var("XDG_DATA_HOME") {
        if !xdg.trim().is_empty() {
            return PathBuf::from(xdg).join("vragplayer");
        }
    }
    if let Ok(home) = std::env::var("HOME") {
        if !home.trim().is_empty() {
            return PathBuf::from(home).join(".vragplayer");
        }
    }
    std::env::temp_dir().join("vragplayer")
}

pub fn load_state(data_dir: &Path) -> PersistedState {
    let path = data_dir.join(STATE_FILE_NAME);
    match fs::read_to_string(&path) {
        Ok(raw) => serde_json::from_str::<PersistedState>(&raw).unwrap_or_default(),
        Err(_) => PersistedState::default(),
    }
}

pub fn save_state(data_dir: &Path, state: &PersistedState) -> std::io::Result<()> {
    fs::create_dir_all(data_dir)?;
    let path = data_dir.join(STATE_FILE_NAME);
    let tmp_path = data_dir.join("vrag_state.json.tmp");
    let json = serde_json::to_string_pretty(state)
        .map_err(|e| std::io::Error::new(std::io::ErrorKind::Other, e.to_string()))?;
    fs::write(&tmp_path, json)?;
    fs::rename(&tmp_path, &path)?;
    Ok(())
}
