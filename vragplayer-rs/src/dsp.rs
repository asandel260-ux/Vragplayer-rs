use std::f32::consts::PI;

/// 5-band graphic equalizer center frequencies (Hz), matching Android AudioFX bands.
pub const EQ_BAND_FREQUENCIES: [f32; 5] = [60.0, 230.0, 910.0, 3_600.0, 14_000.0];
pub const EQ_BAND_LABELS: [&str; 5] = ["60 Hz", "230 Hz", "910 Hz", "3.6 kHz", "14 kHz"];

#[derive(Debug, Clone, Copy, PartialEq)]
pub struct DspParams {
    pub eq_enabled: bool,
    pub bands_db: [f32; 5],
    pub bass_boost_strength: u16, // 0..=1000
    pub crossfade_seconds: u8,    // 0..=8
    pub skip_silence: bool,
    pub speed: f32,               // 0.5..=2.0
}

impl Default for DspParams {
    fn default() -> Self {
        Self {
            eq_enabled: false,
            bands_db: [0.0; 5],
            bass_boost_strength: 0,
            crossfade_seconds: 0,
            skip_silence: false,
            speed: 1.0,
        }
    }
}

/// Direct Form II Transposed stereo biquad filter (zero heap allocation, real-time safe).
#[derive(Debug, Clone, Copy)]
pub struct BiquadFilter {
    b0: f32,
    b1: f32,
    b2: f32,
    a1: f32,
    a2: f32,
    z1_l: f32,
    z2_l: f32,
    z1_r: f32,
    z2_r: f32,
    active: bool,
}

impl Default for BiquadFilter {
    fn default() -> Self {
        Self::identity()
    }
}

impl BiquadFilter {
    pub fn identity() -> Self {
        Self {
            b0: 1.0,
            b1: 0.0,
            b2: 0.0,
            a1: 0.0,
            a2: 0.0,
            z1_l: 0.0,
            z2_l: 0.0,
            z1_r: 0.0,
            z2_r: 0.0,
            active: false,
        }
    }

    pub fn reset(&mut self) {
        self.z1_l = 0.0;
        self.z2_l = 0.0;
        self.z1_r = 0.0;
        self.z2_r = 0.0;
    }

    /// Robert Bristow-Johnson Audio EQ Cookbook peaking EQ filter.
    pub fn configure_peaking(&mut self, sample_rate: f32, freq_hz: f32, q: f32, gain_db: f32) {
        if gain_db.abs() < 0.05 || sample_rate <= 0.0 {
            self.b0 = 1.0;
            self.b1 = 0.0;
            self.b2 = 0.0;
            self.a1 = 0.0;
            self.a2 = 0.0;
            self.active = false;
            return;
        }
        let nyquist = sample_rate * 0.48;
        let fc = freq_hz.clamp(20.0, nyquist.max(20.0));
        let a = 10.0f32.powf(gain_db / 40.0);
        let w0 = 2.0 * PI * fc / sample_rate;
        let cos_w0 = w0.cos();
        let sin_w0 = w0.sin();
        let alpha = sin_w0 / (2.0 * q.max(0.25));

        let b0 = 1.0 + alpha * a;
        let b1 = -2.0 * cos_w0;
        let b2 = 1.0 - alpha * a;
        let a0 = 1.0 + alpha / a;
        let a1 = -2.0 * cos_w0;
        let a2 = 1.0 - alpha / a;

        let inv_a0 = 1.0 / a0;
        self.b0 = b0 * inv_a0;
        self.b1 = b1 * inv_a0;
        self.b2 = b2 * inv_a0;
        self.a1 = a1 * inv_a0;
        self.a2 = a2 * inv_a0;
        self.active = true;
    }

    /// Low-shelf filter for smooth sub-bass boost.
    pub fn configure_low_shelf(&mut self, sample_rate: f32, freq_hz: f32, gain_db: f32) {
        if gain_db.abs() < 0.05 || sample_rate <= 0.0 {
            self.b0 = 1.0;
            self.b1 = 0.0;
            self.b2 = 0.0;
            self.a1 = 0.0;
            self.a2 = 0.0;
            self.active = false;
            return;
        }
        let nyquist = sample_rate * 0.45;
        let fc = freq_hz.clamp(20.0, nyquist.max(20.0));
        let a = 10.0f32.powf(gain_db / 40.0);
        let w0 = 2.0 * PI * fc / sample_rate;
        let cos_w0 = w0.cos();
        let sin_w0 = w0.sin();
        let sqrt_a = a.sqrt();
        let alpha = sin_w0 / 2.0 * ((a + 1.0 / a) * (1.0 / 0.707 - 1.0) + 2.0).max(0.01).sqrt();

        let b0 = a * ((a + 1.0) - (a - 1.0) * cos_w0 + 2.0 * sqrt_a * alpha);
        let b1 = 2.0 * a * ((a - 1.0) - (a + 1.0) * cos_w0);
        let b2 = a * ((a + 1.0) - (a - 1.0) * cos_w0 - 2.0 * sqrt_a * alpha);
        let a0 = (a + 1.0) + (a - 1.0) * cos_w0 + 2.0 * sqrt_a * alpha;
        let a1 = -2.0 * ((a - 1.0) + (a + 1.0) * cos_w0);
        let a2 = (a + 1.0) + (a - 1.0) * cos_w0 - 2.0 * sqrt_a * alpha;

        let inv_a0 = 1.0 / a0;
        self.b0 = b0 * inv_a0;
        self.b1 = b1 * inv_a0;
        self.b2 = b2 * inv_a0;
        self.a1 = a1 * inv_a0;
        self.a2 = a2 * inv_a0;
        self.active = true;
    }

    #[inline]
    pub fn process_stereo(&mut self, left: f32, right: f32) -> (f32, f32) {
        if !self.active {
            return (left, right);
        }
        let out_l = self.b0 * left + self.z1_l;
        self.z1_l = self.b1 * left - self.a1 * out_l + self.z2_l;
        self.z2_l = self.b2 * left - self.a2 * out_l;

        let out_r = self.b0 * right + self.z1_r;
        self.z1_r = self.b1 * right - self.a1 * out_r + self.z2_r;
        self.z2_r = self.b2 * right - self.a2 * out_r;

        // Guard against denormals / NaN propagation
        if !out_l.is_finite() || !out_r.is_finite() {
            self.reset();
            return (left, right);
        }
        (out_l, out_r)
    }
}

/// Complete real-time stereo audio DSP chain: 5-band EQ + Bass Boost + Soft Limiter.
#[derive(Debug, Clone)]
pub struct DspChain {
    sample_rate: f32,
    eq_bands: [BiquadFilter; 5],
    bass_shelf: BiquadFilter,
    params: DspParams,
}

impl DspChain {
    pub fn new(sample_rate: f32) -> Self {
        Self {
            sample_rate: sample_rate.max(8_000.0),
            eq_bands: [BiquadFilter::identity(); 5],
            bass_shelf: BiquadFilter::identity(),
            params: DspParams::default(),
        }
    }

    pub fn update_params(&mut self, params: DspParams) {
        if self.params == params {
            return;
        }
        self.params = params;
        if params.eq_enabled {
            for (i, filter) in self.eq_bands.iter_mut().enumerate() {
                let db = params.bands_db[i].clamp(-12.0, 12.0);
                filter.configure_peaking(self.sample_rate, EQ_BAND_FREQUENCIES[i], 1.15, db);
            }
            let bass_db = (params.bass_boost_strength.min(1000) as f32 / 1000.0) * 10.0;
            self.bass_shelf
                .configure_low_shelf(self.sample_rate, 85.0, bass_db);
        } else {
            for filter in &mut self.eq_bands {
                filter.configure_peaking(self.sample_rate, 1000.0, 1.0, 0.0);
            }
            self.bass_shelf
                .configure_low_shelf(self.sample_rate, 85.0, 0.0);
        }
    }

    pub fn reset_state(&mut self) {
        for f in &mut self.eq_bands {
            f.reset();
        }
        self.bass_shelf.reset();
    }

    #[inline]
    pub fn process_frame(&mut self, left: f32, right: f32, envelope_gain: f32) -> (f32, f32) {
        let (mut l, mut r) = (left, right);
        if self.params.eq_enabled {
            for band in &mut self.eq_bands {
                (l, r) = band.process_stereo(l, r);
            }
            (l, r) = self.bass_shelf.process_stereo(l, r);
        }
        l *= envelope_gain;
        r *= envelope_gain;
        // Soft saturation limiter prevents harsh digital clipping at high EQ boost
        (soft_limit(l), soft_limit(r))
    }
}

#[inline]
fn soft_limit(sample: f32) -> f32 {
    if sample.abs() <= 0.92 {
        sample
    } else {
        sample.tanh()
    }
}

/// Computes smooth crossfade envelope gain (0.05..=1.0) near track boundaries.
pub fn compute_crossfade_gain(
    pos_ms: u64,
    duration_ms: u64,
    crossfade_seconds: u8,
    fade_in_enabled: bool,
) -> f32 {
    if crossfade_seconds == 0 {
        return 1.0;
    }
    let fade_ms = (crossfade_seconds as u64) * 1000;
    if duration_ms <= fade_ms.saturating_mul(2) {
        return 1.0;
    }
    let mut gain = 1.0f32;
    if fade_in_enabled && pos_ms < fade_ms {
        gain = gain.min((pos_ms as f32 / fade_ms as f32).clamp(0.05, 1.0));
    }
    let remaining = duration_ms.saturating_sub(pos_ms);
    if remaining < fade_ms {
        gain = gain.min((remaining as f32 / fade_ms as f32).clamp(0.05, 1.0));
    }
    gain
}
