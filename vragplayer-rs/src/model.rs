use serde::{Deserialize, Serialize};
use std::collections::{BTreeMap, BTreeSet};
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Song {
    pub id: u64,
    pub title: String,
    pub artist: String,
    pub album: String,
    pub duration_ms: u64,
    pub path: PathBuf,
    pub year: u32,
    pub composer: String,
    pub date_added: u64,
    pub size_bytes: u64,
    pub mime_type: String,
    pub track_number: u32,
    pub genre: String,
    pub is_built_in_demo: bool,
}

impl Song {
    pub fn folder_name(&self) -> String {
        if self.is_built_in_demo {
            return "Built-in Studio".to_string();
        }
        self.path
            .parent()
            .and_then(|p| p.file_name())
            .and_then(|n| n.to_str())
            .filter(|s| !s.is_empty())
            .map(|s| s.to_string())
            .unwrap_or_else(|| "Local Storage".to_string())
    }

    pub fn format_label(&self) -> String {
        let lower = self.mime_type.to_ascii_lowercase();
        if lower.contains("flac") {
            "FLAC".to_string()
        } else if lower.contains("wav") {
            "WAV".to_string()
        } else if lower.contains("ogg") || lower.contains("vorbis") {
            "OGG".to_string()
        } else if lower.contains("mp4") || lower.contains("m4a") || lower.contains("aac") {
            "AAC / M4A".to_string()
        } else if lower.contains("opus") {
            "OPUS".to_string()
        } else if lower.contains("mpeg") || lower.contains("mp3") {
            "MP3".to_string()
        } else if let Some((_, subtype)) = lower.split_once('/') {
            subtype.to_ascii_uppercase()
        } else {
            "AUDIO".to_string()
        }
    }

    pub fn duration_formatted(&self) -> String {
        format_duration_fast(self.duration_ms)
    }

    pub fn artwork_key(&self) -> &str {
        if !self.album.trim().is_empty() {
            &self.album
        } else {
            &self.title
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize)]
pub struct Playlist {
    pub id: String,
    pub name: String,
    pub song_ids: Vec<u64>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum RepeatMode {
    Off,
    All,
    One,
}

impl RepeatMode {
    pub fn next(self) -> Self {
        match self {
            RepeatMode::Off => RepeatMode::All,
            RepeatMode::All => RepeatMode::One,
            RepeatMode::One => RepeatMode::Off,
        }
    }

    pub fn label(self) -> &'static str {
        match self {
            RepeatMode::Off => "Off",
            RepeatMode::All => "Repeat All",
            RepeatMode::One => "Repeat One",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum SortType {
    Name,
    Artist,
    Duration,
    Recent,
}

impl SortType {
    pub const ALL: [SortType; 4] = [
        SortType::Name,
        SortType::Artist,
        SortType::Duration,
        SortType::Recent,
    ];

    pub fn label(self) -> &'static str {
        match self {
            SortType::Name => "Name",
            SortType::Artist => "Artist",
            SortType::Duration => "Duration",
            SortType::Recent => "Recent",
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum EqualizerPreset {
    Flat,
    Bass,
    Treble,
    Vocal,
    Acoustic,
    Electronic,
    LateNight,
    Custom,
}

impl EqualizerPreset {
    pub const PRESETS: [EqualizerPreset; 7] = [
        EqualizerPreset::Flat,
        EqualizerPreset::Bass,
        EqualizerPreset::Treble,
        EqualizerPreset::Vocal,
        EqualizerPreset::Acoustic,
        EqualizerPreset::Electronic,
        EqualizerPreset::LateNight,
    ];

    pub fn label(self) -> &'static str {
        match self {
            EqualizerPreset::Flat => "Flat",
            EqualizerPreset::Bass => "Bass",
            EqualizerPreset::Treble => "Treble",
            EqualizerPreset::Vocal => "Vocal",
            EqualizerPreset::Acoustic => "Acoustic",
            EqualizerPreset::Electronic => "Electronic",
            EqualizerPreset::LateNight => "Late Night",
            EqualizerPreset::Custom => "Custom",
        }
    }

    pub fn bands(self) -> [f32; 5] {
        match self {
            EqualizerPreset::Flat => [0.0, 0.0, 0.0, 0.0, 0.0],
            EqualizerPreset::Bass => [8.0, 5.0, 1.0, 0.0, -1.0],
            EqualizerPreset::Treble => [-1.0, 0.0, 2.0, 6.0, 9.0],
            EqualizerPreset::Vocal => [-2.0, 1.0, 7.0, 4.0, 1.0],
            EqualizerPreset::Acoustic => [3.0, 2.0, 3.0, 4.0, 2.0],
            EqualizerPreset::Electronic => [6.0, 3.0, -1.0, 4.0, 7.0],
            EqualizerPreset::LateNight => [2.0, 1.0, -1.0, -2.0, -3.0],
            EqualizerPreset::Custom => [0.0, 0.0, 0.0, 0.0, 0.0],
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize)]
pub enum ThemeMode {
    DarkStudio,
    OledBlack,
    LightStudio,
    MidnightViolet,
}

impl ThemeMode {
    pub const ALL: [ThemeMode; 4] = [
        ThemeMode::DarkStudio,
        ThemeMode::OledBlack,
        ThemeMode::LightStudio,
        ThemeMode::MidnightViolet,
    ];

    pub fn label(self) -> &'static str {
        match self {
            ThemeMode::DarkStudio => "Dark Studio",
            ThemeMode::OledBlack => "OLED Black",
            ThemeMode::LightStudio => "Light Studio",
            ThemeMode::MidnightViolet => "Midnight Violet",
        }
    }

    pub fn is_light(self) -> bool {
        matches!(self, ThemeMode::LightStudio)
    }
}

pub const ACCENT_SWATCHES: [(&str, [u8; 3]); 6] = [
    ("Crimson", [0xFA, 0x2D, 0x48]),
    ("Electric Cyan", [0x22, 0xD3, 0xEE]),
    ("Amber Gold", [0xF5, 0x9E, 0x0B]),
    ("Emerald", [0x10, 0xB9, 0x81]),
    ("Ultraviolet", [0xA8, 0x55, 0xF7]),
    ("Cobalt", [0x3B, 0x82, 0xF6]),
];

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct ListeningStats {
    pub lifetime_plays: u32,
    pub total_play_time_minutes: f32,
    pub today_play_time_minutes: f32,
    pub current_streak_days: u32,
    pub hourly_activity: [u32; 24],
    pub song_play_counts: BTreeMap<u64, u32>,
    pub last_played_date: String,
    pub streak_last_date: String,
}

impl Default for ListeningStats {
    fn default() -> Self {
        Self {
            lifetime_plays: 0,
            total_play_time_minutes: 0.0,
            today_play_time_minutes: 0.0,
            current_streak_days: 0,
            hourly_activity: [0; 24],
            song_play_counts: BTreeMap::new(),
            last_played_date: String::new(),
            streak_last_date: String::new(),
        }
    }
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AppSettings {
    pub username: String,
    pub theme_mode: ThemeMode,
    pub accent_rgb: [u8; 3],
    pub show_covers: bool,
    pub glassmorphism_enabled: bool,
    pub eq_enabled: bool,
    pub eq_preset: EqualizerPreset,
    pub eq_bands: [f32; 5],
    pub bass_boost_strength: u16,
    pub crossfade_seconds: u8,
    pub skip_silence: bool,
    pub playback_speed: f32,
    pub scan_directories: Vec<PathBuf>,
}

impl Default for AppSettings {
    fn default() -> Self {
        Self {
            username: "My Music".to_string(),
            theme_mode: ThemeMode::DarkStudio,
            accent_rgb: ACCENT_SWATCHES[0].1,
            show_covers: true,
            glassmorphism_enabled: true,
            eq_enabled: false,
            eq_preset: EqualizerPreset::Flat,
            eq_bands: [0.0; 5],
            bass_boost_strength: 0,
            crossfade_seconds: 0,
            skip_silence: false,
            playback_speed: 1.0,
            scan_directories: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct PersistedState {
    pub settings: AppSettings,
    pub stats: ListeningStats,
    pub liked_songs: BTreeSet<u64>,
    pub playlists: Vec<Playlist>,
    pub recently_played_ids: Vec<u64>,
    pub recent_searches: Vec<String>,
    pub search_played_ids: Vec<u64>,
    pub sort_type: Option<SortType>,
    pub sort_ascending: Option<bool>,
}

pub fn format_duration_fast(duration_ms: u64) -> String {
    let total_seconds = duration_ms / 1000;
    let hours = total_seconds / 3600;
    let minutes = (total_seconds % 3600) / 60;
    let seconds = total_seconds % 60;
    if hours > 0 {
        format!("{}:{:02}:{:02}", hours, minutes, seconds)
    } else {
        format!("{}:{:02}", minutes, seconds)
    }
}

pub fn format_file_size(bytes: u64) -> String {
    if bytes == 0 {
        return "—".to_string();
    }
    if bytes < 1024 {
        return format!("{} B", bytes);
    }
    let kb = bytes / 1024;
    if kb < 1024 {
        return format!("{} KB", kb);
    }
    let tenths_mb = (bytes.saturating_mul(10) + (1024 * 1024 / 2)) / (1024 * 1024);
    format!("{}.{} MB", tenths_mb / 10, tenths_mb % 10)
}

pub fn now_unix_secs() -> u64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs())
        .unwrap_or(0)
}

/// Converts a Unix timestamp (seconds) into `(today_yyyy_mm_dd, yesterday_yyyy_mm_dd, hour_0_23)`
/// using Howard Hinnant's civil-from-days algorithm (UTC, zero external dependencies).
pub fn calendar_date_and_hour(unix_secs: u64) -> (String, String, usize) {
    let days = (unix_secs / 86_400) as i64;
    let hour = ((unix_secs % 86_400) / 3600) as usize;
    let today = format_civil_days(days);
    let yesterday = format_civil_days(days - 1);
    (today, yesterday, hour.min(23))
}

fn format_civil_days(days_since_epoch: i64) -> String {
    let z = days_since_epoch + 719_468;
    let era = if z >= 0 { z } else { z - 146_096 } / 146_097;
    let doe = (z - era * 146_097) as u64;
    let yoe = (doe - doe / 1460 + doe / 36524 - doe / 146_096) / 365;
    let y = (yoe as i64) + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
    let mp = (5 * doy + 2) / 153;
    let d = doy - (153 * mp + 2) / 5 + 1;
    let m = if mp < 10 { mp + 3 } else { mp - 9 };
    let year = y + if m <= 2 { 1 } else { 0 };
    format!("{:04}-{:02}-{:02}", year, m, d)
}

/// Stable FNV-1a 64-bit hash for deterministic song IDs and artwork palettes.
pub fn stable_hash64(input: &str) -> u64 {
    let mut hash: u64 = 0xcbf2_9ce4_8422_2325;
    for byte in input.as_bytes() {
        hash ^= *byte as u64;
        hash = hash.wrapping_mul(0x0000_0100_0000_01b3);
    }
    hash
}

pub fn mime_from_path(path: &Path) -> &'static str {
    match path
        .extension()
        .and_then(|e| e.to_str())
        .map(|s| s.to_ascii_lowercase())
        .as_deref()
    {
        Some("mp3") => "audio/mpeg",
        Some("flac") => "audio/flac",
        Some("wav") => "audio/wav",
        Some("ogg") | Some("oga") => "audio/ogg",
        Some("opus") => "audio/opus",
        Some("m4a") | Some("mp4") | Some("aac") => "audio/mp4",
        _ => "audio/mpeg",
    }
}
