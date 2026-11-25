use crate::audio_engine::AudioEngine;
use crate::dsp::DspParams;
use crate::library;
use crate::model::{
    calendar_date_and_hour, now_unix_secs, EqualizerPreset, PersistedState, Playlist, RepeatMode,
    Song, SortType, ThemeMode,
};
use crate::storage;
use std::collections::HashMap;
use std::path::PathBuf;
use std::time::{Duration, Instant};

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Screen {
    Home,
    Search,
    Library,
    Stats,
    Settings,
    Player,
    Liked,
    Equalizer,
    SleepTimer,
    Queue,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum LibraryTab {
    Songs,
    Playlists,
    Albums,
    Artists,
    Folders,
}

impl LibraryTab {
    pub const ALL: [LibraryTab; 5] = [
        LibraryTab::Songs,
        LibraryTab::Playlists,
        LibraryTab::Albums,
        LibraryTab::Artists,
        LibraryTab::Folders,
    ];

    pub fn label(self) -> &'static str {
        match self {
            LibraryTab::Songs => "Songs",
            LibraryTab::Playlists => "Playlists",
            LibraryTab::Albums => "Albums",
            LibraryTab::Artists => "Artists",
            LibraryTab::Folders => "Folders",
        }
    }
}

pub struct AppState {
    pub data_dir: PathBuf,
    pub persisted: PersistedState,
    pub audio: AudioEngine,

    // Library & Queue
    pub songs: Vec<Song>,
    pub queue: Vec<Song>,
    pub active_queue_source: String,
    pub active_playlist_id: Option<String>,
    pub current_song: Option<Song>,
    pub is_playing: bool,
    pub repeat_mode: RepeatMode,
    pub shuffle_enabled: bool,
    pub sort_type: SortType,
    pub sort_ascending: bool,

    // Navigation & UI overlays
    pub screen: Screen,
    pub back_stack: Vec<Screen>,
    pub library_tab: LibraryTab,
    pub selected_playlist_id: Option<String>,
    pub selected_collection: Option<(String, Vec<Song>)>,
    pub song_to_add_to_playlist: Option<Song>,
    pub song_info_target: Option<Song>,

    // Search
    pub search_query: String,

    // Sleep Timer
    pub sleep_timer_end: Option<Instant>,
    pub sleep_after_current_track: bool,

    // Toast / Status message
    pub status_message: Option<(String, Instant)>,

    // Internal timers & deterministic PRNG
    last_second_tick: Instant,
    buffered_play_seconds: u32,
    buffered_play_date: String,
    rng_state: u64,
    next_playlist_seq: u64,
}

impl AppState {
    pub fn new(data_dir: PathBuf) -> Self {
        let mut persisted = storage::load_state(&data_dir);
        let now_secs = now_unix_secs();
        let (today, yesterday, _) = calendar_date_and_hour(now_secs);

        // Normalize daily / streak counters on startup
        if persisted.stats.last_played_date != today {
            persisted.stats.today_play_time_minutes = 0.0;
        }
        if persisted.stats.streak_last_date != today
            && persisted.stats.streak_last_date != yesterday
        {
            persisted.stats.current_streak_days = 0;
        }

        let sort_type = persisted.sort_type.unwrap_or(SortType::Name);
        let sort_ascending = persisted.sort_ascending.unwrap_or(true);

        let audio = AudioEngine::new();
        let mut state = Self {
            data_dir,
            persisted,
            audio,
            songs: Vec::new(),
            queue: Vec::new(),
            active_queue_source: "Library".to_string(),
            active_playlist_id: None,
            current_song: None,
            is_playing: false,
            repeat_mode: RepeatMode::Off,
            shuffle_enabled: false,
            sort_type,
            sort_ascending,
            screen: Screen::Home,
            back_stack: Vec::new(),
            library_tab: LibraryTab::Songs,
            selected_playlist_id: None,
            selected_collection: None,
            song_to_add_to_playlist: None,
            song_info_target: None,
            search_query: String::new(),
            sleep_timer_end: None,
            sleep_after_current_track: false,
            status_message: None,
            last_second_tick: Instant::now(),
            buffered_play_seconds: 0,
            buffered_play_date: today,
            rng_state: now_secs ^ 0x9E37_79B9_7F4A_7C15,
            next_playlist_seq: now_secs,
        };

        state.sync_dsp_to_engine();
        state.load_media(true);
        state
    }

    pub fn navigate_to(&mut self, target: Screen) {
        if self.screen == target {
            return;
        }
        self.back_stack.push(self.screen.clone());
        if self.back_stack.len() > 24 {
            self.back_stack.remove(0);
        }
        self.screen = target;
    }

    pub fn navigate_top_tab(&mut self, tab: Screen) {
        self.back_stack.clear();
        self.selected_playlist_id = None;
        self.selected_collection = None;
        self.screen = tab;
    }

    pub fn navigate_back(&mut self) {
        if let Some(prev) = self.back_stack.pop() {
            self.screen = prev;
        } else {
            self.screen = Screen::Home;
        }
    }

    pub fn show_message(&mut self, msg: impl Into<String>) {
        self.status_message = Some((msg.into(), Instant::now() + Duration::from_secs(3)));
    }

    pub fn persist(&self) {
        let _ = storage::save_state(&self.data_dir, &self.persisted);
    }

    pub fn load_media(&mut self, include_demo_if_empty: bool) {
        let mut scanned = library::scan_library(
            &self.data_dir,
            &self.persisted.settings.scan_directories,
            include_demo_if_empty,
        );
        library::sort_songs(&mut scanned, self.sort_type, self.sort_ascending);
        self.songs = scanned;

        if self.queue.is_empty() && !self.songs.is_empty() {
            self.queue = self.songs.clone();
            self.active_queue_source = "Library".to_string();
        }
    }

    pub fn add_scan_directory(&mut self, raw_path: &str) {
        let trimmed = raw_path.trim();
        if trimmed.is_empty() {
            return;
        }
        let pb = PathBuf::from(trimmed);
        if !self.persisted.settings.scan_directories.contains(&pb) {
            self.persisted.settings.scan_directories.push(pb);
            self.persist();
            self.load_media(true);
            self.show_message("Added music folder & rescanned library");
        } else {
            self.show_message("Folder already in scan list");
        }
    }

    pub fn remove_scan_directory(&mut self, idx: usize) {
        if idx < self.persisted.settings.scan_directories.len() {
            self.persisted.settings.scan_directories.remove(idx);
            self.persist();
            self.load_media(true);
            self.show_message("Removed folder & rescanned library");
        }
    }

    /// Periodic UI frame tick: handles track completion, sleep timer, error toasts, and listening time stats.
    pub fn tick(&mut self) {
        if let Some((_, expires)) = &self.status_message {
            if Instant::now() >= *expires {
                self.status_message = None;
            }
        }

        if let Some(err) = self.audio.take_error() {
            self.is_playing = false;
            self.show_message(err);
        }

        if let Some(end) = self.sleep_timer_end {
            if Instant::now() >= end {
                self.sleep_timer_end = None;
                self.audio.set_playing(false);
                self.is_playing = false;
                self.flush_play_time();
                self.show_message("Sleep timer ended — playback paused");
            }
        }

        if self.audio.take_track_finished() {
            self.handle_track_finished();
        }

        if self.last_second_tick.elapsed() >= Duration::from_secs(1) {
            self.last_second_tick = Instant::now();
            if self.is_playing {
                self.tick_play_second(now_unix_secs());
            }
        }
    }

    fn handle_track_finished(&mut self) {
        if self.sleep_after_current_track {
            self.sleep_after_current_track = false;
            self.audio.set_playing(false);
            self.is_playing = false;
            self.flush_play_time();
            self.show_message("Paused at end of track");
            return;
        }

        match self.repeat_mode {
            RepeatMode::One => {
                if let Some(song) = self.current_song.clone() {
                    self.audio.play_file(song.path.clone(), false);
                    self.is_playing = true;
                    self.record_track_play(song.id, now_unix_secs());
                }
            }
            RepeatMode::All => {
                self.advance_next_internal(true, true);
            }
            RepeatMode::Off => {
                self.advance_next_internal(false, true);
            }
        }
    }

    fn advance_next_internal(&mut self, wrap: bool, auto_transition: bool) {
        if self.queue.is_empty() {
            self.is_playing = false;
            return;
        }
        let cur_idx = self
            .current_song
            .as_ref()
            .and_then(|c| self.queue.iter().position(|s| s.id == c.id));

        let next_idx = if self.shuffle_enabled && self.queue.len() > 1 {
            let mut pick = (self.next_rand() as usize) % self.queue.len();
            if Some(pick) == cur_idx {
                pick = (pick + 1) % self.queue.len();
            }
            Some(pick)
        } else {
            match cur_idx {
                Some(i) if i + 1 < self.queue.len() => Some(i + 1),
                Some(_) if wrap => Some(0),
                None => Some(0),
                _ => None,
            }
        };

        if let Some(idx) = next_idx {
            self.play_song_at_index_internal(idx, auto_transition);
        } else {
            self.is_playing = false;
            self.audio.set_playing(false);
            self.flush_play_time();
        }
    }

    // --- Single-Pass Playback & Strict Playlist Queue Isolation ---

    pub fn play_from_list(
        &mut self,
        song: Song,
        source_list: &[Song],
        source_label: &str,
        playlist_id: Option<String>,
        shuffle: bool,
    ) {
        let mut final_queue: Vec<Song> = if source_list.is_empty() {
            vec![song.clone()]
        } else {
            source_list.to_vec()
        };

        if shuffle && final_queue.len() > 1 {
            let mut rest: Vec<Song> = final_queue
                .into_iter()
                .filter(|s| s.id != song.id)
                .collect();
            self.shuffle_slice(&mut rest);
            let mut combined = Vec::with_capacity(rest.len() + 1);
            combined.push(song.clone());
            combined.extend(rest);
            final_queue = combined;
        }

        let start_idx = final_queue
            .iter()
            .position(|s| s.id == song.id)
            .unwrap_or(0);

        self.queue = final_queue;
        self.active_queue_source = source_label.to_string();
        self.active_playlist_id = playlist_id;
        self.shuffle_enabled = false;
        self.play_song_at_index_internal(start_idx, false);
    }

    pub fn play_playlist_sequential(&mut self, playlist_id: &str) {
        let Some(pl) = self
            .persisted
            .playlists
            .iter()
            .find(|p| p.id == playlist_id)
            .cloned()
        else {
            return;
        };
        let list = self.resolve_playlist_songs(&pl);
        if list.is_empty() {
            self.show_message(format!("\"{}\" has no songs yet", pl.name));
            return;
        }
        let first = list[0].clone();
        self.play_from_list(first, &list, &pl.name, Some(pl.id), false);
    }

    pub fn play_playlist_random(&mut self, playlist_id: &str) {
        let Some(pl) = self
            .persisted
            .playlists
            .iter()
            .find(|p| p.id == playlist_id)
            .cloned()
        else {
            return;
        };
        let mut list = self.resolve_playlist_songs(&pl);
        if list.is_empty() {
            self.show_message(format!("\"{}\" has no songs yet", pl.name));
            return;
        }
        self.shuffle_slice(&mut list);
        let first = list[0].clone();
        self.play_from_list(first, &list, &pl.name, Some(pl.id), false);
    }

    pub fn play_all_shuffled(&mut self, source_list: &[Song], source_label: &str) {
        if source_list.is_empty() {
            return;
        }
        let mut list = source_list.to_vec();
        self.shuffle_slice(&mut list);
        let first = list[0].clone();
        self.play_from_list(first, &list, source_label, None, false);
    }

    pub fn play_song_at_index(&mut self, index: usize) {
        self.play_song_at_index_internal(index, false);
    }

    fn play_song_at_index_internal(&mut self, index: usize, auto_transition: bool) {
        let Some(song) = self.queue.get(index).cloned() else {
            return;
        };
        self.audio.play_file(song.path.clone(), auto_transition);
        self.is_playing = true;
        self.record_current_song_change(song);
    }

    pub fn toggle_playback(&mut self) {
        if self.current_song.is_none() {
            let seed = if !self.queue.is_empty() {
                self.queue.clone()
            } else {
                self.songs.clone()
            };
            if !seed.is_empty() {
                self.play_all_shuffled(&seed, "Shuffled");
            }
            return;
        }
        let next = !self.is_playing;
        self.is_playing = next;
        self.audio.set_playing(next);
        if !next {
            self.flush_play_time();
        }
    }

    pub fn play_next(&mut self) {
        self.advance_next_internal(true, false);
    }

    pub fn play_previous(&mut self) {
        if self.audio.position_ms() > 3000 {
            self.audio.seek_ms(0);
            return;
        }
        if self.queue.is_empty() {
            return;
        }
        let cur_idx = self
            .current_song
            .as_ref()
            .and_then(|c| self.queue.iter().position(|s| s.id == c.id))
            .unwrap_or(0);
        let prev_idx = if cur_idx > 0 {
            cur_idx - 1
        } else {
            self.queue.len() - 1
        };
        self.play_song_at_index_internal(prev_idx, false);
    }

    pub fn stop_playback(&mut self) {
        self.audio.stop();
        self.is_playing = false;
        self.flush_play_time();
    }

    pub fn seek_to(&mut self, position_ms: u64) {
        self.audio.seek_ms(position_ms);
    }

    pub fn toggle_repeat_mode(&mut self) {
        self.repeat_mode = self.repeat_mode.next();
        self.show_message(format!("Repeat: {}", self.repeat_mode.label()));
    }

    pub fn toggle_shuffle_mode(&mut self) {
        self.shuffle_enabled = !self.shuffle_enabled;
        self.show_message(if self.shuffle_enabled {
            "Shuffle enabled"
        } else {
            "Shuffle disabled"
        });
    }

    pub fn set_playback_speed(&mut self, speed: f32) {
        self.persisted.settings.playback_speed = speed.clamp(0.5, 2.0);
        self.sync_dsp_to_engine();
        self.persist();
    }

    // --- Queue Manipulation ---

    pub fn play_next_in_queue(&mut self, song: Song) {
        let cur_idx = self
            .current_song
            .as_ref()
            .and_then(|c| self.queue.iter().position(|s| s.id == c.id));
        let insert_idx = cur_idx.map(|i| i + 1).unwrap_or(0).min(self.queue.len());
        let title = song.title.clone();
        self.queue.insert(insert_idx, song);
        self.show_message(format!("Playing \"{}\" next", title));
    }

    pub fn add_to_queue(&mut self, song: Song) {
        self.queue.push(song);
        self.show_message("Added to queue");
    }

    pub fn move_queue_item(&mut self, from: usize, to: usize) {
        if from >= self.queue.len() || to >= self.queue.len() || from == to {
            return;
        }
        let item = self.queue.remove(from);
        self.queue.insert(to, item);
    }

    pub fn remove_queue_item(&mut self, index: usize) {
        if index >= self.queue.len() {
            return;
        }
        let removed = self.queue.remove(index);
        if self.queue.is_empty() {
            self.stop_playback();
            self.current_song = None;
        } else if self.current_song.as_ref().map(|s| s.id) == Some(removed.id) {
            let next_idx = index.min(self.queue.len() - 1);
            self.play_song_at_index_internal(next_idx, false);
        }
    }

    pub fn clear_upcoming_queue(&mut self) {
        if let Some(current) = self.current_song.clone() {
            self.queue = vec![current];
            self.show_message("Cleared upcoming songs");
        }
    }

    pub fn save_queue_as_playlist(&mut self, name: &str) {
        let trimmed = name.trim();
        if trimmed.is_empty() || self.queue.is_empty() {
            return;
        }
        let mut ids = Vec::new();
        for s in &self.queue {
            if !ids.contains(&s.id) {
                ids.push(s.id);
            }
        }
        self.create_playlist(trimmed, ids);
    }

    // --- Favorites & Playlists ---

    pub fn toggle_like(&mut self, song_id: u64) {
        let added = if self.persisted.liked_songs.contains(&song_id) {
            self.persisted.liked_songs.remove(&song_id);
            false
        } else {
            self.persisted.liked_songs.insert(song_id);
            true
        };
        self.persist();
        self.show_message(if added {
            "Added to Favorites"
        } else {
            "Removed from Favorites"
        });
    }

    pub fn is_liked(&self, song_id: u64) -> bool {
        self.persisted.liked_songs.contains(&song_id)
    }

    pub fn liked_song_list(&self) -> Vec<Song> {
        self.songs
            .iter()
            .filter(|s| self.persisted.liked_songs.contains(&s.id))
            .cloned()
            .collect()
    }

    pub fn resolve_playlist_songs(&self, playlist: &Playlist) -> Vec<Song> {
        let map: HashMap<u64, &Song> = self.songs.iter().map(|s| (s.id, s)).collect();
        playlist
            .song_ids
            .iter()
            .filter_map(|id| map.get(id).copied().cloned())
            .collect()
    }

    pub fn recently_played_songs(&self) -> Vec<Song> {
        let map: HashMap<u64, &Song> = self.songs.iter().map(|s| (s.id, s)).collect();
        let list: Vec<Song> = self
            .persisted
            .recently_played_ids
            .iter()
            .filter_map(|id| map.get(id).copied().cloned())
            .collect();
        if list.is_empty() {
            self.songs.iter().take(6).cloned().collect()
        } else {
            list
        }
    }

    pub fn create_playlist(&mut self, name: &str, initial_song_ids: Vec<u64>) -> Option<Playlist> {
        let sanitized = name.trim().to_string();
        if sanitized.is_empty() {
            return None;
        }
        self.next_playlist_seq = self.next_playlist_seq.wrapping_add(1);
        let mut unique_ids = Vec::new();
        for id in initial_song_ids {
            if !unique_ids.contains(&id) {
                unique_ids.push(id);
            }
        }
        let created = Playlist {
            id: format!("pl_{}", self.next_playlist_seq),
            name: sanitized.clone(),
            song_ids: unique_ids,
        };
        self.persisted.playlists.push(created.clone());
        self.persist();
        self.show_message(format!("Created \"{}\"", sanitized));
        Some(created)
    }

    pub fn rename_playlist(&mut self, id: &str, new_name: &str) {
        let sanitized = new_name.trim().to_string();
        if sanitized.is_empty() {
            return;
        }
        if let Some(pl) = self.persisted.playlists.iter_mut().find(|p| p.id == id) {
            pl.name = sanitized.clone();
            if self.active_playlist_id.as_deref() == Some(id) {
                self.active_queue_source = sanitized.clone();
            }
            self.persist();
            self.show_message(format!("Renamed to \"{}\"", sanitized));
        }
    }

    pub fn delete_playlist(&mut self, id: &str) {
        let removed_name = self
            .persisted
            .playlists
            .iter()
            .find(|p| p.id == id)
            .map(|p| p.name.clone());
        self.persisted.playlists.retain(|p| p.id != id);
        if self.active_playlist_id.as_deref() == Some(id) {
            self.active_playlist_id = None;
        }
        if self.selected_playlist_id.as_deref() == Some(id) {
            self.selected_playlist_id = None;
        }
        self.persist();
        if let Some(name) = removed_name {
            self.show_message(format!("Deleted \"{}\"", name));
        }
    }

    pub fn add_song_to_playlist(&mut self, playlist_id: &str, song_id: u64) {
        let mut added = false;
        let mut pl_name = String::new();
        if let Some(pl) = self
            .persisted
            .playlists
            .iter_mut()
            .find(|p| p.id == playlist_id)
        {
            pl_name = pl.name.clone();
            if !pl.song_ids.contains(&song_id) {
                pl.song_ids.push(song_id);
                added = true;
            }
        }
        self.persist();
        if added && self.active_playlist_id.as_deref() == Some(playlist_id) {
            if let Some(song) = self.songs.iter().find(|s| s.id == song_id).cloned() {
                self.queue.push(song);
            }
        }
        if !pl_name.is_empty() {
            self.show_message(if added {
                format!("Added to \"{}\"", pl_name)
            } else {
                format!("Already in \"{}\"", pl_name)
            });
        }
    }

    pub fn remove_song_from_playlist(&mut self, playlist_id: &str, song_id: u64) {
        if let Some(pl) = self
            .persisted
            .playlists
            .iter_mut()
            .find(|p| p.id == playlist_id)
        {
            pl.song_ids.retain(|&id| id != song_id);
        }
        self.persist();
        if self.active_playlist_id.as_deref() == Some(playlist_id) {
            if let Some(idx) = self.queue.iter().position(|s| s.id == song_id) {
                self.remove_queue_item(idx);
            }
        }
        self.show_message("Removed from playlist");
    }

    pub fn clear_playlist(&mut self, playlist_id: &str) {
        if let Some(pl) = self
            .persisted
            .playlists
            .iter_mut()
            .find(|p| p.id == playlist_id)
        {
            pl.song_ids.clear();
        }
        self.persist();
        if self.active_playlist_id.as_deref() == Some(playlist_id) {
            self.stop_playback();
            self.queue.clear();
            self.current_song = None;
        }
        self.show_message("Playlist cleared");
    }

    // --- Sorting & Search ---

    pub fn set_sorting(&mut self, sort_type: SortType, ascending: bool) {
        self.sort_type = sort_type;
        self.sort_ascending = ascending;
        self.persisted.sort_type = Some(sort_type);
        self.persisted.sort_ascending = Some(ascending);
        library::sort_songs(&mut self.songs, sort_type, ascending);
        self.persist();
    }

    pub fn add_recent_search(&mut self, query: &str) {
        let trimmed = query.trim().to_string();
        if trimmed.is_empty() {
            return;
        }
        self.persisted
            .recent_searches
            .retain(|q| !q.eq_ignore_ascii_case(&trimmed));
        self.persisted.recent_searches.insert(0, trimmed);
        self.persisted.recent_searches.truncate(8);
        self.persist();
    }

    pub fn remove_recent_search(&mut self, query: &str) {
        self.persisted.recent_searches.retain(|q| q != query);
        self.persist();
    }

    pub fn clear_recent_searches(&mut self) {
        self.persisted.recent_searches.clear();
        self.persist();
    }

    pub fn register_search_played(&mut self, song_id: u64) {
        self.persisted.search_played_ids.retain(|&id| id != song_id);
        self.persisted.search_played_ids.insert(0, song_id);
        self.persisted.search_played_ids.truncate(15);
        self.persist();
    }

    // --- DSP, Equalizer, Sleep Timer & Theme ---

    pub fn sync_dsp_to_engine(&self) {
        let s = &self.persisted.settings;
        self.audio.update_dsp(DspParams {
            eq_enabled: s.eq_enabled,
            bands_db: s.eq_bands,
            bass_boost_strength: s.bass_boost_strength,
            crossfade_seconds: s.crossfade_seconds,
            skip_silence: s.skip_silence,
            speed: s.playback_speed,
        });
    }

    pub fn set_eq_enabled(&mut self, enabled: bool) {
        self.persisted.settings.eq_enabled = enabled;
        self.sync_dsp_to_engine();
        self.persist();
    }

    pub fn apply_eq_preset(&mut self, preset: EqualizerPreset) {
        self.persisted.settings.eq_preset = preset;
        if preset != EqualizerPreset::Custom {
            self.persisted.settings.eq_bands = preset.bands();
        }
        self.sync_dsp_to_engine();
        self.persist();
    }

    pub fn set_eq_band(&mut self, idx: usize, db: f32) {
        if idx < 5 {
            self.persisted.settings.eq_bands[idx] = db.clamp(-12.0, 12.0);
            self.persisted.settings.eq_preset = EqualizerPreset::Custom;
            self.sync_dsp_to_engine();
            self.persist();
        }
    }

    pub fn set_bass_boost(&mut self, strength: u16) {
        self.persisted.settings.bass_boost_strength = strength.min(1000);
        self.sync_dsp_to_engine();
        self.persist();
    }

    pub fn set_crossfade_seconds(&mut self, sec: u8) {
        self.persisted.settings.crossfade_seconds = sec.min(8);
        self.sync_dsp_to_engine();
        self.persist();
    }

    pub fn set_skip_silence(&mut self, enabled: bool) {
        self.persisted.settings.skip_silence = enabled;
        self.sync_dsp_to_engine();
        self.persist();
    }

    pub fn set_theme_mode(&mut self, mode: ThemeMode) {
        self.persisted.settings.theme_mode = mode;
        self.persist();
    }

    pub fn set_accent_rgb(&mut self, rgb: [u8; 3]) {
        self.persisted.settings.accent_rgb = rgb;
        self.persist();
    }

    pub fn start_sleep_timer_minutes(&mut self, minutes: u64) {
        self.sleep_after_current_track = false;
        self.sleep_timer_end = Some(Instant::now() + Duration::from_secs(minutes * 60));
        self.show_message(format!("Sleep timer set for {} min", minutes));
    }

    pub fn extend_sleep_timer_minutes(&mut self, extra_min: u64) {
        let base = self.sleep_timer_end.unwrap_or_else(Instant::now);
        self.sleep_timer_end = Some(base + Duration::from_secs(extra_min * 60));
        self.show_message(format!("Extended sleep timer by +{} min", extra_min));
    }

    pub fn cancel_sleep_timer(&mut self) {
        self.sleep_timer_end = None;
        self.sleep_after_current_track = false;
        self.show_message("Sleep timer cancelled");
    }

    pub fn sleep_remaining_ms(&self) -> u64 {
        self.sleep_timer_end
            .and_then(|end| end.checked_duration_since(Instant::now()))
            .map(|d| d.as_millis() as u64)
            .unwrap_or(0)
    }

    // --- Stats & Recents ---

    fn record_current_song_change(&mut self, song: Song) {
        let song_id = song.id;
        self.current_song = Some(song);
        self.record_track_play(song_id, now_unix_secs());

        self.persisted
            .recently_played_ids
            .retain(|&id| id != song_id);
        self.persisted.recently_played_ids.insert(0, song_id);
        self.persisted.recently_played_ids.truncate(12);
        self.persist();
    }

    pub fn record_track_play(&mut self, song_id: u64, now_secs: u64) {
        let (today, yesterday, hour) = calendar_date_and_hour(now_secs);
        let stats = &mut self.persisted.stats;
        stats.lifetime_plays = stats.lifetime_plays.saturating_add(1);
        stats.hourly_activity[hour] = stats.hourly_activity[hour].saturating_add(1);
        *stats.song_play_counts.entry(song_id).or_insert(0) += 1;

        if stats.streak_last_date == today {
            stats.current_streak_days = stats.current_streak_days.max(1);
        } else if stats.streak_last_date == yesterday {
            stats.current_streak_days = stats.current_streak_days.saturating_add(1);
        } else {
            stats.current_streak_days = 1;
        }
        stats.streak_last_date = today;
    }

    pub fn tick_play_second(&mut self, now_secs: u64) {
        let (today, _, _) = calendar_date_and_hour(now_secs);
        if today != self.buffered_play_date && self.buffered_play_seconds > 0 {
            self.flush_play_time();
        }
        self.buffered_play_date = today;
        self.buffered_play_seconds += 1;
        if self.buffered_play_seconds >= 30 {
            self.flush_play_time();
        }
    }

    pub fn flush_play_time(&mut self) {
        if self.buffered_play_seconds == 0 {
            return;
        }
        let delta_min = (self.buffered_play_seconds as f32) / 60.0;
        self.buffered_play_seconds = 0;

        let stats = &mut self.persisted.stats;
        let base_today = if stats.last_played_date == self.buffered_play_date {
            stats.today_play_time_minutes
        } else {
            0.0
        };
        stats.total_play_time_minutes += delta_min;
        stats.today_play_time_minutes = base_today + delta_min;
        stats.last_played_date = self.buffered_play_date.clone();
        self.persist();
    }

    // --- SplitMix64 PRNG ---

    fn next_rand(&mut self) -> u64 {
        self.rng_state = self.rng_state.wrapping_add(0x9E37_79B9_7F4A_7C15);
        let mut z = self.rng_state;
        z = (z ^ (z >> 30)).wrapping_mul(0xBF58_476D_1CE4_E5B9);
        z = (z ^ (z >> 27)).wrapping_mul(0x94D0_49BB_1331_11EB);
        z ^ (z >> 31)
    }

    fn shuffle_slice<T>(&mut self, slice: &mut [T]) {
        for i in (1..slice.len()).rev() {
            let j = (self.next_rand() as usize) % (i + 1);
            slice.swap(i, j);
        }
    }
}
