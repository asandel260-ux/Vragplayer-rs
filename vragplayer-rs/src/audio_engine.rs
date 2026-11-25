use crate::dsp::{compute_crossfade_gain, DspChain, DspParams};
use cpal::traits::{DeviceTrait, HostTrait, StreamTrait};
use std::fs::File;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, AtomicU64, Ordering};
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::{Duration, Instant};
use symphonia::core::audio::SampleBuffer;
use symphonia::core::codecs::DecoderOptions;
use symphonia::core::errors::Error as SymphoniaError;
use symphonia::core::formats::FormatOptions;
use symphonia::core::io::MediaSourceStream;
use symphonia::core::meta::MetadataOptions;
use symphonia::core::probe::Hint;

struct SharedPlayback {
    /// Interleaved stereo PCM samples `[L0, R0, L1, R1, ...]` at `source_sample_rate`.
    stereo_samples: Vec<f32>,
    source_sample_rate: f64,
    /// Current playback head in source stereo frames (fractional for speed/resampling).
    frame_pos: f64,
    total_frames: usize,
    is_playing: bool,
    fade_in_enabled: bool,
    dsp_params: DspParams,
    /// Monotonic generation ID to cancel stale background decodes.
    load_gen: u64,
    /// Software fallback wall clock when no hardware audio output exists (e.g., headless CI).
    software_last_tick: Option<Instant>,
    last_error: Option<String>,
}

impl Default for SharedPlayback {
    fn default() -> Self {
        Self {
            stereo_samples: Vec::new(),
            source_sample_rate: 44_100.0,
            frame_pos: 0.0,
            total_frames: 0,
            is_playing: false,
            fade_in_enabled: false,
            dsp_params: DspParams::default(),
            load_gen: 0,
            software_last_tick: None,
            last_error: None,
        }
    }
}

pub struct AudioEngine {
    shared: Arc<Mutex<SharedPlayback>>,
    track_finished: Arc<AtomicBool>,
    hardware_active: Arc<AtomicBool>,
    shutdown: Arc<AtomicBool>,
    pos_ms_atomic: Arc<AtomicU64>,
    dur_ms_atomic: Arc<AtomicU64>,
}

impl AudioEngine {
    pub fn new() -> Self {
        let shared = Arc::new(Mutex::new(SharedPlayback::default()));
        let track_finished = Arc::new(AtomicBool::new(false));
        let hardware_active = Arc::new(AtomicBool::new(false));
        let shutdown = Arc::new(AtomicBool::new(false));
        let pos_ms_atomic = Arc::new(AtomicU64::new(0));
        let dur_ms_atomic = Arc::new(AtomicU64::new(0));

        let shared_bg = Arc::clone(&shared);
        let finished_bg = Arc::clone(&track_finished);
        let hw_bg = Arc::clone(&hardware_active);
        let shutdown_bg = Arc::clone(&shutdown);
        let pos_bg = Arc::clone(&pos_ms_atomic);
        let dur_bg = Arc::clone(&dur_ms_atomic);

        // Dedicated thread owns the `cpal::Stream` so it never moves across threads.
        let _ = thread::Builder::new()
            .name("vrag-audio-out".to_string())
            .spawn(move || {
                run_audio_output_thread(
                    shared_bg,
                    finished_bg,
                    hw_bg,
                    shutdown_bg,
                    pos_bg,
                    dur_bg,
                );
            });

        Self {
            shared,
            track_finished,
            hardware_active,
            shutdown,
            pos_ms_atomic,
            dur_ms_atomic,
        }
    }

    pub fn play_file(&self, path: PathBuf, auto_transition_fade_in: bool) {
        self.track_finished.store(false, Ordering::Release);
        let next_gen = {
            let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
            guard.load_gen = guard.load_gen.wrapping_add(1);
            guard.stereo_samples.clear();
            guard.total_frames = 0;
            guard.frame_pos = 0.0;
            guard.is_playing = true;
            guard.fade_in_enabled = auto_transition_fade_in;
            guard.software_last_tick = Some(Instant::now());
            guard.last_error = None;
            guard.load_gen
        };
        self.pos_ms_atomic.store(0, Ordering::Release);

        let shared_clone = Arc::clone(&self.shared);
        let dur_clone = Arc::clone(&self.dur_ms_atomic);
        let _ = thread::Builder::new()
            .name("vrag-audio-decode".to_string())
            .spawn(move || match decode_file_to_stereo_f32(&path) {
                Ok((samples, sample_rate)) => {
                    let total_frames = samples.len() / 2;
                    let duration_ms = if sample_rate > 0 {
                        ((total_frames as u64).saturating_mul(1000)) / (sample_rate as u64)
                    } else {
                        0
                    };
                    let mut guard = shared_clone.lock().unwrap_or_else(|e| e.into_inner());
                    if guard.load_gen == next_gen {
                        guard.stereo_samples = samples;
                        guard.source_sample_rate = (sample_rate.max(8_000)) as f64;
                        guard.total_frames = total_frames;
                        guard.frame_pos = 0.0;
                        guard.software_last_tick = Some(Instant::now());
                        dur_clone.store(duration_ms, Ordering::Release);
                    }
                }
                Err(err) => {
                    let mut guard = shared_clone.lock().unwrap_or_else(|e| e.into_inner());
                    if guard.load_gen == next_gen {
                        guard.is_playing = false;
                        guard.last_error = Some(err);
                    }
                }
            });
    }

    pub fn set_playing(&self, playing: bool) {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        guard.is_playing = playing;
        guard.software_last_tick = if playing { Some(Instant::now()) } else { None };
    }

    pub fn stop(&self) {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        guard.is_playing = false;
        guard.frame_pos = 0.0;
        guard.software_last_tick = None;
        self.pos_ms_atomic.store(0, Ordering::Release);
    }

    pub fn seek_ms(&self, position_ms: u64) {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        let sr = guard.source_sample_rate.max(8_000.0);
        let target_frame = ((position_ms as f64) * sr / 1000.0)
            .clamp(0.0, guard.total_frames.saturating_sub(1) as f64);
        guard.frame_pos = target_frame;
        self.pos_ms_atomic.store(position_ms, Ordering::Release);
    }

    pub fn update_dsp(&self, params: DspParams) {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        guard.dsp_params = params;
    }

    pub fn position_ms(&self) -> u64 {
        if !self.hardware_active.load(Ordering::Acquire) {
            self.advance_software_clock();
        }
        self.pos_ms_atomic.load(Ordering::Acquire)
    }

    pub fn duration_ms(&self) -> u64 {
        self.dur_ms_atomic.load(Ordering::Acquire)
    }

    pub fn is_playing(&self) -> bool {
        let guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        guard.is_playing
    }

    pub fn take_track_finished(&self) -> bool {
        self.track_finished.swap(false, Ordering::AcqRel)
    }

    pub fn take_error(&self) -> Option<String> {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        guard.last_error.take()
    }

    pub fn is_hardware_audio_active(&self) -> bool {
        self.hardware_active.load(Ordering::Acquire)
    }

    fn advance_software_clock(&self) {
        let mut guard = self.shared.lock().unwrap_or_else(|e| e.into_inner());
        if !guard.is_playing || guard.total_frames == 0 {
            guard.software_last_tick = None;
            return;
        }
        let now = Instant::now();
        if let Some(prev) = guard.software_last_tick {
            let dt_secs = now.duration_since(prev).as_secs_f64();
            let speed = guard.dsp_params.speed.clamp(0.5, 2.0) as f64;
            guard.frame_pos += dt_secs * guard.source_sample_rate * speed;
            if guard.frame_pos >= guard.total_frames as f64 {
                guard.frame_pos = guard.total_frames as f64;
                guard.is_playing = false;
                self.track_finished.store(true, Ordering::Release);
            }
            let pos_ms = ((guard.frame_pos / guard.source_sample_rate.max(8_000.0)) * 1000.0) as u64;
            self.pos_ms_atomic.store(pos_ms, Ordering::Release);
        }
        guard.software_last_tick = Some(now);
    }
}

impl Default for AudioEngine {
    fn default() -> Self {
        Self::new()
    }
}

impl Drop for AudioEngine {
    fn drop(&mut self) {
        self.shutdown.store(true, Ordering::Release);
    }
}

fn run_audio_output_thread(
    shared: Arc<Mutex<SharedPlayback>>,
    track_finished: Arc<AtomicBool>,
    hardware_active: Arc<AtomicBool>,
    shutdown: Arc<AtomicBool>,
    pos_ms_atomic: Arc<AtomicU64>,
    dur_ms_atomic: Arc<AtomicU64>,
) {
    let host = cpal::default_host();
    let Some(device) = host.default_output_device() else {
        log::warn!("No default audio output device; running with software playback clock.");
        return;
    };
    let Ok(supported_config) = device.default_output_config() else {
        log::warn!("Unable to query default output config; running with software playback clock.");
        return;
    };

    let sample_format = supported_config.sample_format();
    let stream_config: cpal::StreamConfig = supported_config.into();
    let out_sample_rate = stream_config.sample_rate.0.max(8_000) as f32;
    let out_channels = (stream_config.channels as usize).max(1);

    let err_fn = |err| {
        log::error!("Audio output stream error: {}", err);
    };

    let stream_result = match sample_format {
        cpal::SampleFormat::F32 => {
            let mut dsp = DspChain::new(out_sample_rate);
            let shared_cb = Arc::clone(&shared);
            let finished_cb = Arc::clone(&track_finished);
            let pos_cb = Arc::clone(&pos_ms_atomic);
            let dur_cb = Arc::clone(&dur_ms_atomic);
            device.build_output_stream(
                &stream_config,
                move |data: &mut [f32], _| {
                    render_into_buffer(
                        data.len() / out_channels,
                        out_channels,
                        out_sample_rate,
                        &shared_cb,
                        &mut dsp,
                        &finished_cb,
                        &pos_cb,
                        &dur_cb,
                        |frame_idx, ch, sample| {
                            data[frame_idx * out_channels + ch] = sample;
                        },
                    );
                },
                err_fn,
                None,
            )
        }
        cpal::SampleFormat::I16 => {
            let mut dsp = DspChain::new(out_sample_rate);
            let shared_cb = Arc::clone(&shared);
            let finished_cb = Arc::clone(&track_finished);
            let pos_cb = Arc::clone(&pos_ms_atomic);
            let dur_cb = Arc::clone(&dur_ms_atomic);
            device.build_output_stream(
                &stream_config,
                move |data: &mut [i16], _| {
                    render_into_buffer(
                        data.len() / out_channels,
                        out_channels,
                        out_sample_rate,
                        &shared_cb,
                        &mut dsp,
                        &finished_cb,
                        &pos_cb,
                        &dur_cb,
                        |frame_idx, ch, sample| {
                            let clamped = sample.clamp(-1.0, 1.0);
                            data[frame_idx * out_channels + ch] = (clamped * i16::MAX as f32) as i16;
                        },
                    );
                },
                err_fn,
                None,
            )
        }
        cpal::SampleFormat::U16 => {
            let mut dsp = DspChain::new(out_sample_rate);
            let shared_cb = Arc::clone(&shared);
            let finished_cb = Arc::clone(&track_finished);
            let pos_cb = Arc::clone(&pos_ms_atomic);
            let dur_cb = Arc::clone(&dur_ms_atomic);
            device.build_output_stream(
                &stream_config,
                move |data: &mut [u16], _| {
                    render_into_buffer(
                        data.len() / out_channels,
                        out_channels,
                        out_sample_rate,
                        &shared_cb,
                        &mut dsp,
                        &finished_cb,
                        &pos_cb,
                        &dur_cb,
                        |frame_idx, ch, sample| {
                            let norm = sample.clamp(-1.0, 1.0) * 0.5 + 0.5;
                            data[frame_idx * out_channels + ch] = (norm * u16::MAX as f32) as u16;
                        },
                    );
                },
                err_fn,
                None,
            )
        }
        _ => {
            log::warn!("Unsupported cpal sample format; falling back to software playback clock.");
            return;
        }
    };

    let Ok(stream) = stream_result else {
        log::warn!("Failed to build audio output stream; falling back to software clock.");
        return;
    };
    if stream.play().is_err() {
        log::warn!("Failed to start audio output stream; falling back to software clock.");
        return;
    }

    hardware_active.store(true, Ordering::Release);
    while !shutdown.load(Ordering::Acquire) {
        thread::sleep(Duration::from_millis(80));
    }
    drop(stream);
    hardware_active.store(false, Ordering::Release);
}

#[allow(clippy::too_many_arguments)]
fn render_into_buffer<F>(
    num_frames: usize,
    out_channels: usize,
    out_sample_rate: f32,
    shared: &Arc<Mutex<SharedPlayback>>,
    dsp: &mut DspChain,
    track_finished: &Arc<AtomicBool>,
    pos_ms_atomic: &Arc<AtomicU64>,
    dur_ms_atomic: &Arc<AtomicU64>,
    mut write_sample: F,
) where
    F: FnMut(usize, usize, f32),
{
    let Ok(mut guard) = shared.try_lock() else {
        for f in 0..num_frames {
            for ch in 0..out_channels {
                write_sample(f, ch, 0.0);
            }
        }
        return;
    };

    dsp.update_params(guard.dsp_params);

    if !guard.is_playing || guard.total_frames < 2 {
        for f in 0..num_frames {
            for ch in 0..out_channels {
                write_sample(f, ch, 0.0);
            }
        }
        return;
    }

    let speed = guard.dsp_params.speed.clamp(0.5, 2.0) as f64;
    let base_step = (guard.source_sample_rate / (out_sample_rate as f64)) * speed;
    let skip_silence = guard.dsp_params.skip_silence;
    let crossfade_sec = guard.dsp_params.crossfade_seconds;
    let fade_in = guard.fade_in_enabled;
    let dur_ms = dur_ms_atomic.load(Ordering::Relaxed);

    for f in 0..num_frames {
        let idx = guard.frame_pos as usize;
        if idx + 1 >= guard.total_frames {
            guard.is_playing = false;
            guard.frame_pos = guard.total_frames as f64;
            track_finished.store(true, Ordering::Release);
            for ch in 0..out_channels {
                write_sample(f, ch, 0.0);
            }
            continue;
        }

        let frac = (guard.frame_pos - (idx as f64)) as f32;
        let i0 = idx * 2;
        let i1 = (idx + 1) * 2;

        let raw_l = guard.stereo_samples[i0]
            + (guard.stereo_samples[i1] - guard.stereo_samples[i0]) * frac;
        let raw_r = guard.stereo_samples[i0 + 1]
            + (guard.stereo_samples[i1 + 1] - guard.stereo_samples[i0 + 1]) * frac;

        let cur_pos_ms =
            ((guard.frame_pos / guard.source_sample_rate.max(8_000.0)) * 1000.0) as u64;
        let env_gain = compute_crossfade_gain(cur_pos_ms, dur_ms, crossfade_sec, fade_in);
        let (out_l, out_r) = dsp.process_frame(raw_l, raw_r, env_gain);

        for ch in 0..out_channels {
            let val = match ch {
                0 => out_l,
                1 => out_r,
                _ => (out_l + out_r) * 0.5,
            };
            write_sample(f, ch, val);
        }

        // Skip-silence gently accelerates through prolonged near-zero passages
        let energy = raw_l.abs().max(raw_r.abs());
        let step = if skip_silence && energy < 0.002 {
            base_step * 3.0
        } else {
            base_step
        };
        guard.frame_pos += step;
    }

    let final_pos_ms =
        ((guard.frame_pos / guard.source_sample_rate.max(8_000.0)) * 1000.0) as u64;
    pos_ms_atomic.store(final_pos_ms, Ordering::Release);
}

/// Decodes any Symphonia-supported audio file (WAV, MP3, FLAC, OGG, AAC/M4A, ALAC)
/// into interleaved stereo `f32` PCM samples plus the decoded source sample rate.
pub fn decode_file_to_stereo_f32(path: &Path) -> Result<(Vec<f32>, u32), String> {
    let file = File::open(path).map_err(|e| format!("Could not open audio file: {}", e))?;
    let mss = MediaSourceStream::new(Box::new(file), Default::default());

    let mut hint = Hint::new();
    if let Some(ext) = path.extension().and_then(|e| e.to_str()) {
        hint.with_extension(ext);
    }

    let probed = symphonia::default::get_probe()
        .format(
            &hint,
            mss,
            &FormatOptions::default(),
            &MetadataOptions::default(),
        )
        .map_err(|e| format!("Unsupported audio format: {}", e))?;

    let mut format = probed.format;
    let (track_id, codec_params) = {
        let track = format
            .default_track()
            .ok_or_else(|| "No audio track found in file".to_string())?;
        (track.id, track.codec_params.clone())
    };

    let mut decoder = symphonia::default::get_codecs()
        .make(&codec_params, &DecoderOptions::default())
        .map_err(|e| format!("Unsupported codec: {}", e))?;

    let mut sample_rate = codec_params.sample_rate.unwrap_or(44_100);
    let mut stereo_out: Vec<f32> = Vec::new();
    let mut sample_buf: Option<SampleBuffer<f32>> = None;
    let mut sample_buf_frames: u64 = 0;
    let mut sample_buf_channels: usize = 0;

    loop {
        let packet = match format.next_packet() {
            Ok(pkt) => pkt,
            Err(SymphoniaError::IoError(_)) => break,
            Err(SymphoniaError::ResetRequired) => {
                decoder.reset();
                continue;
            }
            Err(_) => break,
        };

        if packet.track_id() != track_id {
            continue;
        }

        match decoder.decode(&packet) {
            Ok(decoded) => {
                let spec = *decoded.spec();
                sample_rate = spec.rate;
                let channels = spec.channels.count().max(1);
                let duration = decoded.capacity() as u64;

                if sample_buf.is_none()
                    || duration > sample_buf_frames
                    || channels != sample_buf_channels
                {
                    sample_buf = Some(SampleBuffer::<f32>::new(duration, spec));
                    sample_buf_frames = duration;
                    sample_buf_channels = channels;
                }

                if let Some(buf) = &mut sample_buf {
                    buf.copy_interleaved_ref(decoded);
                    let interleaved = buf.samples();
                    if channels == 1 {
                        stereo_out.reserve(interleaved.len() * 2);
                        for &s in interleaved {
                            stereo_out.push(s);
                            stereo_out.push(s);
                        }
                    } else if channels == 2 {
                        stereo_out.extend_from_slice(interleaved);
                    } else {
                        for frame in interleaved.chunks(channels) {
                            let l = frame.first().copied().unwrap_or(0.0);
                            let r = frame.get(1).copied().unwrap_or(l);
                            stereo_out.push(l);
                            stereo_out.push(r);
                        }
                    }
                }
            }
            Err(SymphoniaError::DecodeError(_)) => continue,
            Err(SymphoniaError::IoError(_)) => break,
            Err(_) => break,
        }
    }

    if stereo_out.is_empty() {
        return Err("Audio file contained no playable PCM samples".to_string());
    }
    Ok((stereo_out, sample_rate))
}
