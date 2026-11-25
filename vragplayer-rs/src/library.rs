use crate::demo_synth;
use crate::model::{mime_from_path, stable_hash64, Song, SortType};
use std::collections::{BTreeMap, HashSet};
use std::fs::{self, File};
use std::path::{Path, PathBuf};
use std::time::UNIX_EPOCH;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::{MetadataOptions, MetadataRevision, StandardTagKey};
use symphonia::core::probe::Hint;

const SUPPORTED_EXTENSIONS: [&str; 8] = ["mp3", "flac", "wav", "ogg", "oga", "m4a", "aac", "opus"];

pub fn default_music_directories() -> Vec<PathBuf> {
    let mut dirs = Vec::new();

    #[cfg(target_os = "android")]
    {
        dirs.push(PathBuf::from("/storage/emulated/0/Music"));
        dirs.push(PathBuf::from("/storage/emulated/0/Download"));
        dirs.push(PathBuf::from("/sdcard/Music"));
    }

    #[cfg(not(target_os = "android"))]
    {
        if let Ok(home) = std::env::var("HOME") {
            let home_path = PathBuf::from(home);
            dirs.push(home_path.join("Music"));
            dirs.push(home_path.join("Downloads"));
        }
    }

    dirs
}

/// Scans device music folders + custom user folders, parses tags via Symphonia,
/// and synthesizes the 3 built-in offline studio demo tracks if 0 files are found.
pub fn scan_library(
    data_dir: &Path,
    extra_dirs: &[PathBuf],
    include_built_in_demo_if_empty: bool,
) -> Vec<Song> {
    let mut roots = default_music_directories();
    for dir in extra_dirs {
        if !roots.contains(dir) {
            roots.push(dir.clone());
        }
    }

    let mut songs = Vec::new();
    let mut seen_keys = HashSet::new();

    for root in roots {
        if root.is_dir() {
            scan_directory_iterative(&root, 4, &mut songs, &mut seen_keys);
        }
    }

    if songs.is_empty() && include_built_in_demo_if_empty {
        songs.extend(demo_synth::ensure_demo_songs(data_dir));
    }

    songs
}

fn scan_directory_iterative(
    root: &Path,
    max_depth: usize,
    songs: &mut Vec<Song>,
    seen_keys: &mut HashSet<String>,
) {
    let mut stack: Vec<(PathBuf, usize)> = vec![(root.to_path_buf(), 0)];
    while let Some((dir, depth)) = stack.pop() {
        let Ok(entries) = fs::read_dir(&dir) else {
            continue;
        };
        for entry in entries.flatten() {
            let path = entry.path();
            let Ok(file_type) = entry.file_type() else {
                continue;
            };
            if file_type.is_dir() {
                if depth < max_depth {
                    let is_hidden = path
                        .file_name()
                        .and_then(|n| n.to_str())
                        .map(|s| s.starts_with('.'))
                        .unwrap_or(false);
                    if !is_hidden {
                        stack.push((path, depth + 1));
                    }
                }
            } else if file_type.is_file() && is_supported_audio_file(&path) {
                if let Some(song) = read_song_metadata(&path) {
                    let dedup_key = format!(
                        "{}|{}|{}",
                        song.title.to_ascii_lowercase(),
                        song.size_bytes,
                        song.duration_ms
                    );
                    if seen_keys.insert(dedup_key) {
                        songs.push(song);
                    }
                }
            }
        }
    }
}

pub fn is_supported_audio_file(path: &Path) -> bool {
    path.extension()
        .and_then(|e| e.to_str())
        .map(|ext| {
            let lower = ext.to_ascii_lowercase();
            SUPPORTED_EXTENSIONS.iter().any(|&s| s == lower)
        })
        .unwrap_or(false)
}

fn read_song_metadata(path: &Path) -> Option<Song> {
    let meta = fs::metadata(path).ok()?;
    let size_bytes = meta.len();
    if size_bytes < 512 {
        return None;
    }

    let date_added = meta
        .modified()
        .ok()
        .and_then(|t| t.duration_since(UNIX_EPOCH).ok())
        .map(|d| d.as_secs())
        .unwrap_or(0);

    let fallback_title = path
        .file_stem()
        .and_then(|s| s.to_str())
        .filter(|s| !s.trim().is_empty())
        .unwrap_or("Unknown Track")
        .to_string();

    let mut title = fallback_title;
    let mut artist = "Unknown Artist".to_string();
    let mut album = "Single".to_string();
    let mut composer = "Unknown Composer".to_string();
    let mut genre = "Other".to_string();
    let mut year = 0u32;
    let mut track_number = 0u32;
    let mut duration_ms = 0u64;

    if let Ok(file) = File::open(path) {
        let mss = MediaSourceStream::new(Box::new(file), Default::default());
        let mut hint = Hint::new();
        if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
            hint.with_extension(ext);
        }

        if let Ok(mut probed) = symphonia::default::get_probe().format(
            &hint,
            mss,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        ) {
            if let Some(track) = probed.format.default_track() {
                if let (Some(n_frames), Some(sr)) =
                    (track.codec_params.n_frames, track.codec_params.sample_rate)
                {
                    if sr > 0 {
                        duration_ms = n_frames.saturating_mul(1000) / (sr as u64);
                    }
                }
            }

            if let Some(rev) = probed.format.metadata().current() {
                apply_tags(
                    rev,
                    &mut title,
                    &mut artist,
                    &mut album,
                    &mut composer,
                    &mut genre,
                    &mut year,
                    &mut track_number,
                );
            }
            if let Some(md) = probed.metadata.get() {
                if let Some(rev) = md.current() {
                    apply_tags(
                        rev,
                        &mut title,
                        &mut artist,
                        &mut album,
                        &mut composer,
                        &mut genre,
                        &mut year,
                        &mut track_number,
                    );
                }
            }
        }
    }

    if let Some((first_artist, _)) = artist.split_once(';') {
        let trimmed = first_artist.trim();
        if !trimmed.is_empty() {
            artist = trimmed.to_string();
        }
    }

    let id = stable_hash64(&path.to_string_lossy()) & 0x7FFF_FFFF_FFFF_FFFF;

    Some(Song {
        id: id.max(1),
        title,
        artist,
        album,
        duration_ms: duration_ms.max(1000),
        path: path.to_path_buf(),
        year,
        composer,
        date_added,
        size_bytes,
        mime_type: mime_from_path(path).to_string(),
        track_number,
        genre,
        is_built_in_demo: false,
    })
}

#[allow(clippy::too_many_arguments)]
fn apply_tags(
    rev: &MetadataRevision,
    title: &mut String,
    artist: &mut String,
    album: &mut String,
    composer: &mut String,
    genre: &mut String,
    year: &mut u32,
    track_number: &mut u32,
) {
    for tag in rev.tags() {
        let val = tag.value.to_string();
        let trimmed = val.trim();
        if trimmed.is_empty() || trimmed == "<unknown>" {
            continue;
        }
        match tag.std_key {
            Some(StandardTagKey::TrackTitle) => *title = trimmed.to_string(),
            Some(StandardTagKey::Artist) => *artist = trimmed.to_string(),
            Some(StandardTagKey::Album) => *album = trimmed.to_string(),
            Some(StandardTagKey::Composer) => *composer = trimmed.to_string(),
            Some(StandardTagKey::Genre) => *genre = trimmed.to_string(),
            Some(StandardTagKey::Date) => {
                if let Some(y) = trimmed.get(0..4).and_then(|s| s.parse::<u32>().ok()) {
                    *year = y;
                }
            }
            Some(StandardTagKey::TrackNumber) => {
                let first = trimmed.split('/').next().unwrap_or(trimmed);
                if let Ok(n) = first.parse::<u32>() {
                    *track_number = n;
                }
            }
            _ => {}
        }
    }
}

pub fn sort_songs(songs: &mut [Song], sort_type: SortType, ascending: bool) {
    songs.sort_by(|a, b| {
        let ord = match sort_type {
            SortType::Name => a
                .title
                .to_ascii_lowercase()
                .cmp(&b.title.to_ascii_lowercase()),
            SortType::Artist => a
                .artist
                .to_ascii_lowercase()
                .cmp(&b.artist.to_ascii_lowercase())
                .then_with(|| {
                    a.title
                        .to_ascii_lowercase()
                        .cmp(&b.title.to_ascii_lowercase())
                }),
            SortType::Duration => a.duration_ms.cmp(&b.duration_ms),
            SortType::Recent => a.date_added.cmp(&b.date_added),
        };
        if ascending {
            ord
        } else {
            ord.reverse()
        }
    });
}

pub fn search_songs(songs: &[Song], query: &str) -> Vec<Song> {
    let q = query.trim().to_ascii_lowercase();
    if q.is_empty() {
        return Vec::new();
    }
    songs
        .iter()
        .filter(|s| {
            s.title.to_ascii_lowercase().contains(&q)
                || s.artist.to_ascii_lowercase().contains(&q)
                || s.album.to_ascii_lowercase().contains(&q)
                || s.genre.to_ascii_lowercase().contains(&q)
        })
        .cloned()
        .collect()
}

pub fn group_by_album(songs: &[Song]) -> Vec<(String, Vec<Song>)> {
    let mut map: BTreeMap<String, Vec<Song>> = BTreeMap::new();
    for s in songs {
        map.entry(s.album.clone()).or_default().push(s.clone());
    }
    map.into_iter().collect()
}

pub fn group_by_artist(songs: &[Song]) -> Vec<(String, Vec<Song>)> {
    let mut map: BTreeMap<String, Vec<Song>> = BTreeMap::new();
    for s in songs {
        map.entry(s.artist.clone()).or_default().push(s.clone());
    }
    map.into_iter().collect()
}

pub fn group_by_folder(songs: &[Song]) -> Vec<(String, Vec<Song>)> {
    let mut map: BTreeMap<String, Vec<Song>> = BTreeMap::new();
    for s in songs {
        map.entry(s.folder_name()).or_default().push(s.clone());
    }
    map.into_iter().collect()
}
