pub mod audio_engine;
pub mod demo_synth;
pub mod dsp;
pub mod library;
pub mod model;
pub mod state;
pub mod storage;
pub mod ui;

#[cfg(target_os = "android")]
#[no_mangle]
pub fn android_main(app: winit::platform::android::activity::AndroidApp) {
    android_logger::init_once(
        android_logger::Config::default().with_max_level(log::LevelFilter::Info),
    );

    let data_dir = app
        .internal_data_path()
        .unwrap_or_else(storage::default_data_dir);

    let options = eframe::NativeOptions {
        android_app: Some(app),
        ..Default::default()
    };

    if let Err(err) = eframe::run_native(
        "VragPlayer",
        options,
        Box::new(move |cc| Ok(Box::new(ui::VragApp::new(cc, data_dir)))),
    ) {
        log::error!("Failed to launch VragPlayer on Android: {}", err);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::dsp::{compute_crossfade_gain, DspChain, DspParams};
    use crate::model::{
        calendar_date_and_hour, format_duration_fast, format_file_size, EqualizerPreset, SortType,
    };
    use std::path::PathBuf;

    fn temp_test_dir(tag: &str) -> PathBuf {
        let dir = std::env::temp_dir().join(format!(
            "vragplayer_rs_test_{}_{}",
            tag,
            model::now_unix_secs()
        ));
        let _ = std::fs::remove_dir_all(&dir);
        let _ = std::fs::create_dir_all(&dir);
        dir
    }

    #[test]
    fn formats_duration_and_file_size_accurately() {
        assert_eq!(format_duration_fast(0), "0:00");
        assert_eq!(format_duration_fast(65_000), "1:05");
        assert_eq!(format_duration_fast(3_661_000), "1:01:01");

        assert_eq!(format_file_size(0), "—");
        assert_eq!(format_file_size(512), "512 B");
        assert_eq!(format_file_size(4096), "4 KB");
        assert_eq!(format_file_size(2 * 1024 * 1024), "2.0 MB");
    }

    #[test]
    fn civil_calendar_date_and_hour_matches_epoch_and_modern_dates() {
        let (today, yesterday, hour) = calendar_date_and_hour(0);
        assert_eq!(today, "1970-01-01");
        assert_eq!(yesterday, "1969-12-31");
        assert_eq!(hour, 0);

        // 2025-03-01 14:00:00 UTC = 1740837600
        let (d, prev, h) = calendar_date_and_hour(1_740_837_600);
        assert_eq!(d, "2025-03-01");
        assert_eq!(prev, "2025-02-28");
        assert_eq!(h, 14);
    }

    #[test]
    fn demo_wav_synthesizer_produces_valid_decodable_audio() {
        let dir = temp_test_dir("wav_synth");
        let songs = demo_synth::ensure_demo_songs(&dir);
        assert_eq!(songs.len(), 3);

        for song in &songs {
            assert!(song.path.exists());
            let (samples, sr) = audio_engine::decode_file_to_stereo_f32(&song.path)
                .expect("synthesized WAV must decode cleanly via Symphonia");
            assert_eq!(sr, demo_synth::SAMPLE_RATE);
            assert!(samples.len() > 10_000);
        }
        let _ = std::fs::remove_dir_all(&dir);
    }

    #[test]
    fn dsp_chain_eq_and_bass_boost_remain_finite_and_bounded() {
        let mut chain = DspChain::new(44_100.0);
        chain.update_params(DspParams {
            eq_enabled: true,
            bands_db: EqualizerPreset::Bass.bands(),
            bass_boost_strength: 1000,
            crossfade_seconds: 4,
            skip_silence: false,
            speed: 1.0,
        });

        for i in 0..2000 {
            let phase = (i as f32) * 2.0 * std::f32::consts::PI * 100.0 / 44_100.0;
            let s = phase.sin() * 0.8;
            let (l, r) = chain.process_frame(s, s, 1.0);
            assert!(l.is_finite() && r.is_finite());
            assert!(l.abs() <= 1.0 && r.abs() <= 1.0);
        }

        assert!((compute_crossfade_gain(500, 12_000, 2, true) - 0.25).abs() < 0.01);
        assert!((compute_crossfade_gain(6_000, 12_000, 2, true) - 1.0).abs() < 0.01);
        assert!((compute_crossfade_gain(11_500, 12_000, 2, true) - 0.25).abs() < 0.01);
    }

    #[test]
    fn app_state_playlists_queue_isolation_and_persistence_work() {
        let dir = temp_test_dir("state_flow");
        {
            let mut st = state::AppState::new(dir.clone());
            assert_eq!(st.songs.len(), 3);

            // Sort descending by Name
            st.set_sorting(SortType::Name, false);
            assert_eq!(st.songs[0].title, "Midnight Velvet");

            // Create playlist with 2 songs & verify isolated playback queue
            let id1 = st.songs[0].id;
            let id2 = st.songs[1].id;
            let pl = st
                .create_playlist("Night Drive", vec![id1, id2])
                .expect("playlist created");
            st.play_playlist_sequential(&pl.id);
            assert_eq!(st.queue.len(), 2);
            assert_eq!(st.active_queue_source, "Night Drive");

            // Toggle favorite & record play stats
            st.toggle_like(id1);
            assert!(st.is_liked(id1));
            assert!(st.persisted.stats.lifetime_plays >= 1);
        }

        // Reload persisted state from disk
        let reloaded = storage::load_state(&dir);
        assert_eq!(reloaded.playlists.len(), 1);
        assert_eq!(reloaded.playlists[0].name, "Night Drive");
        assert_eq!(reloaded.liked_songs.len(), 1);
        let _ = std::fs::remove_dir_all(&dir);
    }
}
