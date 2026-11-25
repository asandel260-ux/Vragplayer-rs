use crate::model::{now_unix_secs, Song};
use std::f64::consts::PI;
use std::fs::{self, File};
use std::io::{BufWriter, Write};
use std::path::Path;

pub const SAMPLE_RATE: u32 = 16_000;
pub const DURATION_SECONDS: u32 = 12;

pub struct DemoSpec {
    pub id: u64,
    pub file_name: &'static str,
    pub title: &'static str,
    pub artist: &'static str,
    pub album: &'static str,
    pub genre: &'static str,
    pub year: u32,
    pub track_number: u32,
    pub chords_hz: [[f64; 4]; 4],
    pub arp_pattern: [usize; 6],
    pub tempo_bpm: f64,
}

pub const DEMO_SPECS: [DemoSpec; 3] = [
    DemoSpec {
        id: 90_001,
        file_name: "amber_horizon.wav",
        title: "Amber Horizon",
        artist: "Kora & Loom",
        album: "Evening Sketches",
        genre: "Ambient",
        year: 2025,
        track_number: 1,
        chords_hz: [
            [220.0, 261.63, 329.63, 392.00],
            [174.61, 220.0, 261.63, 329.63],
            [196.00, 246.94, 293.66, 392.00],
            [164.81, 196.00, 246.94, 329.63],
        ],
        arp_pattern: [0, 1, 2, 3, 2, 1],
        tempo_bpm: 78.0,
    },
    DemoSpec {
        id: 90_002,
        file_name: "midnight_velvet.wav",
        title: "Midnight Velvet",
        artist: "Nocturne",
        album: "After Hours",
        genre: "Lo-Fi",
        year: 2025,
        track_number: 2,
        chords_hz: [
            [146.83, 220.00, 261.63, 329.63],
            [196.00, 246.94, 329.63, 349.23],
            [130.81, 196.00, 246.94, 329.63],
            [110.00, 196.00, 261.63, 329.63],
        ],
        arp_pattern: [0, 2, 1, 3, 1, 2],
        tempo_bpm: 86.0,
    },
    DemoSpec {
        id: 90_003,
        file_name: "glass_rain.wav",
        title: "Glass Rain",
        artist: "Maren Solberg",
        album: "Quiet Rooms",
        genre: "Acoustic",
        year: 2024,
        track_number: 3,
        chords_hz: [
            [174.61, 220.00, 261.63, 349.23],
            [130.81, 164.81, 196.00, 261.63],
            [146.83, 174.61, 220.00, 293.66],
            [116.54, 174.61, 233.08, 293.66],
        ],
        arp_pattern: [0, 3, 2, 1, 2, 3],
        tempo_bpm: 74.0,
    },
];

/// Synthesizes 3 lightweight offline studio demo WAV tracks in `base_dir/vrag_samples`
/// so playback can always be verified immediately when a device has 0 local audio files.
pub fn ensure_demo_songs(base_dir: &Path) -> Vec<Song> {
    let demo_dir = base_dir.join("vrag_samples");
    let _ = fs::create_dir_all(&demo_dir);

    let now_sec = now_unix_secs();
    let mut songs = Vec::with_capacity(DEMO_SPECS.len());

    for (idx, spec) in DEMO_SPECS.iter().enumerate() {
        let path = demo_dir.join(spec.file_name);
        let needs_write = match fs::metadata(&path) {
            Ok(meta) => meta.len() < 1024,
            Err(_) => true,
        };
        if needs_write {
            let _ = write_synthesized_wav(&path, spec);
        }
        let size_bytes = fs::metadata(&path).map(|m| m.len()).unwrap_or(0);
        songs.push(Song {
            id: spec.id,
            title: spec.title.to_string(),
            artist: spec.artist.to_string(),
            album: spec.album.to_string(),
            duration_ms: (DURATION_SECONDS as u64) * 1000,
            path,
            year: spec.year,
            composer: spec.artist.to_string(),
            date_added: now_sec.saturating_sub((idx as u64) * 3600),
            size_bytes,
            mime_type: "audio/wav".to_string(),
            track_number: spec.track_number,
            genre: spec.genre.to_string(),
            is_built_in_demo: true,
        });
    }

    songs
}

pub fn write_synthesized_wav(path: &Path, spec: &DemoSpec) -> std::io::Result<()> {
    let total_samples = (SAMPLE_RATE * DURATION_SECONDS) as usize;
    let data_bytes = (total_samples * 2) as u32;
    let byte_rate = SAMPLE_RATE * 2;
    let header = build_wav_header(data_bytes, SAMPLE_RATE, 1, 16, byte_rate);

    let chord_duration_samples = (total_samples / spec.chords_hz.len()).max(1);
    let step_duration_sec = 60.0 / (spec.tempo_bpm * 2.0);
    let step_samples = ((step_duration_sec * SAMPLE_RATE as f64) as usize).max(1);
    let fade_samples = (SAMPLE_RATE / 2) as usize;

    let file = File::create(path)?;
    let mut out = BufWriter::with_capacity(16_384, file);
    out.write_all(&header)?;

    let mut buffer = [0u8; 4096];
    let mut buf_idx = 0usize;

    for i in 0..total_samples {
        let t = (i as f64) / (SAMPLE_RATE as f64);
        let chord_idx = (i / chord_duration_samples).min(spec.chords_hz.len() - 1);
        let chord = &spec.chords_hz[chord_idx];
        let pos_in_chord = ((i % chord_duration_samples) as f64) / (chord_duration_samples as f64);

        let chord_env = if pos_in_chord < 0.08 {
            pos_in_chord / 0.08
        } else if pos_in_chord > 0.92 {
            (1.0 - pos_in_chord) / 0.08
        } else {
            1.0
        };

        let mut pad = 0.0;
        for &freq in chord {
            pad += (2.0 * PI * freq * t).sin() * 0.15;
        }
        let bass = (2.0 * PI * (chord[0] * 0.5) * t).sin() * 0.22;

        let step_index = (i / step_samples) % spec.arp_pattern.len();
        let note_idx = spec.arp_pattern[step_index] % chord.len();
        let step_progress = ((i % step_samples) as f64) / (step_samples as f64);
        let pluck_env = if step_progress < 0.03 {
            step_progress / 0.03
        } else {
            (-4.5 * (step_progress - 0.03)).exp()
        };
        let arp_freq = chord[note_idx] * 2.0;
        let pluck = (2.0 * PI * arp_freq * t).sin() * 0.18 * pluck_env;

        let global_env = if i < fade_samples {
            (i as f64) / (fade_samples as f64)
        } else if i > total_samples - fade_samples {
            ((total_samples - i) as f64) / (fade_samples as f64)
        } else {
            1.0
        };

        let sample = (((pad + bass) * chord_env + pluck) * global_env * 0.85).clamp(-1.0, 1.0);
        let pcm = (sample * (i16::MAX as f64)) as i16;
        let le = pcm.to_le_bytes();
        buffer[buf_idx] = le[0];
        buffer[buf_idx + 1] = le[1];
        buf_idx += 2;

        if buf_idx >= buffer.len() {
            out.write_all(&buffer[..buf_idx])?;
            buf_idx = 0;
        }
    }

    if buf_idx > 0 {
        out.write_all(&buffer[..buf_idx])?;
    }
    out.flush()
}

pub fn build_wav_header(
    data_size: u32,
    sample_rate: u32,
    channels: u16,
    bits_per_sample: u16,
    byte_rate: u32,
) -> [u8; 44] {
    let total_size = data_size.saturating_add(36);
    let block_align = channels * (bits_per_sample / 8);
    let mut header = [0u8; 44];
    header[0..4].copy_from_slice(b"RIFF");
    header[4..8].copy_from_slice(&total_size.to_le_bytes());
    header[8..12].copy_from_slice(b"WAVE");
    header[12..16].copy_from_slice(b"fmt ");
    header[16..20].copy_from_slice(&16u32.to_le_bytes());
    header[20..22].copy_from_slice(&1u16.to_le_bytes());
    header[22..24].copy_from_slice(&channels.to_le_bytes());
    header[24..28].copy_from_slice(&sample_rate.to_le_bytes());
    header[28..32].copy_from_slice(&byte_rate.to_le_bytes());
    header[32..34].copy_from_slice(&block_align.to_le_bytes());
    header[34..36].copy_from_slice(&bits_per_sample.to_le_bytes());
    header[36..40].copy_from_slice(b"data");
    header[40..44].copy_from_slice(&data_size.to_le_bytes());
    header
}
