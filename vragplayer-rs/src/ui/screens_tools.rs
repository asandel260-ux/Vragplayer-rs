use crate::dsp::EQ_BAND_LABELS;
use crate::model::{
    format_duration_fast, EqualizerPreset, ThemeMode, ACCENT_SWATCHES,
};
use crate::state::{AppState, Screen};
use crate::ui::screens_home::handle_song_action;
use crate::ui::theme::{
    card_frame, elevated_frame, pill_button, primary_action_button, render_screen_top_bar,
    render_section_header, render_song_row, secondary_action_button, StudioPalette,
};
use eframe::egui::{self, Color32, CornerRadius, Pos2, Rect, RichText, ScrollArea, Sense, Stroke, StrokeKind, Ui, Vec2};

pub fn render_equalizer_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
) {
    ui.add_space(6.0);
    let mut back = false;
    render_screen_top_bar(ui, "Studio Equalizer & DSP", Some(&mut back), palette);
    if back {
        state.navigate_back();
        return;
    }

    ui.add_space(10.0);
    ScrollArea::vertical().show(ui, |ui| {
        // Master Toggle
        card_frame(palette, 18, 16, 12).show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                ui.vertical(|ui| {
                    ui.label(
                        RichText::new("5-Band Parametric Biquad DSP")
                            .size(16.0)
                            .strong()
                            .color(palette.text_primary),
                    );
                    ui.label(
                        RichText::new("Real-time stereo filtering processed in pure Rust")
                            .size(12.5)
                            .color(palette.text_secondary),
                    );
                });
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    let mut eq_on = state.persisted.settings.eq_enabled;
                    if ui.checkbox(&mut eq_on, if eq_on { "ON" } else { "OFF" }).changed() {
                        state.set_eq_enabled(eq_on);
                    }
                });
            });
        });

        ui.add_space(12.0);

        // Visual EQ Response Curve
        card_frame(palette, 18, 14, 14).show(ui, |ui| {
            let width = ui.available_width().max(180.0);
            let height = 110.0;
            let (rect, _) = ui.allocate_exact_size(Vec2::new(width, height), Sense::hover());
            let painter = ui.painter_at(rect);

            painter.rect_filled(rect, CornerRadius::same(12), palette.bg);
            // 0 dB center line
            let mid_y = rect.center().y;
            painter.line_segment(
                [Pos2::new(rect.left() + 8.0, mid_y), Pos2::new(rect.right() - 8.0, mid_y)],
                Stroke::new(1.0, palette.border),
            );

            let bands = state.persisted.settings.eq_bands;
            let active = state.persisted.settings.eq_enabled;
            let curve_color = if active {
                palette.accent
            } else {
                palette.text_secondary
            };

            let mut points = Vec::with_capacity(5);
            for (i, &db) in bands.iter().enumerate() {
                let x = rect.left() + 24.0 + (i as f32 / 4.0) * (rect.width() - 48.0);
                let norm = (db / 12.0).clamp(-1.0, 1.0);
                let y = mid_y - norm * (rect.height() * 0.38);
                points.push(Pos2::new(x, y));
            }
            for seg in points.windows(2) {
                painter.line_segment([seg[0], seg[1]], Stroke::new(2.5, curve_color));
            }
            for p in points {
                painter.circle_filled(p, 5.0, curve_color);
                painter.circle_stroke(p, 5.0, Stroke::new(1.5, palette.bg));
            }
        });

        ui.add_space(12.0);

        // Preset Pills
        render_section_header(ui, "Presets", None, palette);
        ui.add_space(6.0);
        ui.horizontal_wrapped(|ui| {
            let active_preset = state.persisted.settings.eq_preset;
            for preset in EqualizerPreset::PRESETS {
                if pill_button(ui, preset.label(), active_preset == preset, palette).clicked() {
                    state.apply_eq_preset(preset);
                    if !state.persisted.settings.eq_enabled && preset != EqualizerPreset::Flat {
                        state.set_eq_enabled(true);
                    }
                }
            }
        });

        ui.add_space(12.0);

        // 5-Band Sliders
        card_frame(palette, 18, 16, 14).show(ui, |ui| {
            for (idx, label) in EQ_BAND_LABELS.iter().enumerate() {
                let mut db = state.persisted.settings.eq_bands[idx];
                ui.horizontal(|ui| {
                    ui.allocate_ui(Vec2::new(58.0, 24.0), |ui| {
                        ui.label(
                            RichText::new(*label)
                                .size(13.0)
                                .strong()
                                .color(palette.text_primary),
                        );
                    });
                    if ui
                        .add(egui::Slider::new(&mut db, -12.0..=12.0).show_value(false))
                        .changed()
                    {
                        state.set_eq_band(idx, db);
                    }
                    ui.label(
                        RichText::new(format!("{:+.1} dB", db))
                            .size(12.5)
                            .color(palette.accent),
                    );
                });
                ui.add_space(4.0);
            }
        });

        ui.add_space(12.0);

        // Bass Boost, Crossfade, Skip Silence
        card_frame(palette, 18, 16, 14).show(ui, |ui| {
            let mut bass = state.persisted.settings.bass_boost_strength;
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new("Bass Boost")
                        .size(14.0)
                        .strong()
                        .color(palette.text_primary),
                );
                if ui
                    .add(egui::Slider::new(&mut bass, 0..=1000).show_value(false))
                    .changed()
                {
                    state.set_bass_boost(bass);
                }
                ui.label(
                    RichText::new(format!("{}%", bass / 10))
                        .size(13.0)
                        .color(palette.accent),
                );
            });

            ui.add_space(10.0);

            let mut cf = state.persisted.settings.crossfade_seconds;
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new("Crossfade")
                        .size(14.0)
                        .strong()
                        .color(palette.text_primary),
                );
                if ui
                    .add(egui::Slider::new(&mut cf, 0..=8).show_value(false))
                    .changed()
                {
                    state.set_crossfade_seconds(cf);
                }
                ui.label(
                    RichText::new(if cf == 0 {
                        "Off".to_string()
                    } else {
                        format!("{}s", cf)
                    })
                    .size(13.0)
                    .color(palette.accent),
                );
            });

            ui.add_space(10.0);

            let mut skip = state.persisted.settings.skip_silence;
            if ui
                .checkbox(&mut skip, "Skip Silence (accelerates silent passages)")
                .changed()
            {
                state.set_skip_silence(skip);
            }
        });

        ui.add_space(18.0);
    });
}

pub fn render_sleep_timer_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
) {
    ui.add_space(6.0);
    let mut back = false;
    render_screen_top_bar(ui, "Sleep Timer", Some(&mut back), palette);
    if back {
        state.navigate_back();
        return;
    }

    ui.add_space(12.0);
    let remaining_ms = state.sleep_remaining_ms();

    card_frame(palette, 20, 18, 22).show(ui, |ui| {
        ui.set_min_width(ui.available_width());
        ui.vertical_centered(|ui| {
            let status = if state.sleep_after_current_track {
                "End of Track".to_string()
            } else if remaining_ms > 0 {
                format_duration_fast(remaining_ms)
            } else {
                "Off".to_string()
            };
            ui.label(
                RichText::new("AUTO-PAUSE COUNTDOWN")
                    .size(11.5)
                    .strong()
                    .color(palette.accent),
            );
            ui.add_space(6.0);
            ui.label(
                RichText::new(status)
                    .size(38.0)
                    .strong()
                    .color(palette.text_primary),
            );
            ui.add_space(12.0);

            if remaining_ms > 0 || state.sleep_after_current_track {
                ui.horizontal(|ui| {
                    if remaining_ms > 0
                        && secondary_action_button(ui, "+5 min", palette).clicked()
                    {
                        state.extend_sleep_timer_minutes(5);
                    }
                    if primary_action_button(ui, "Cancel Timer", palette).clicked() {
                        state.cancel_sleep_timer();
                    }
                });
            }
        });
    });

    ui.add_space(14.0);
    render_section_header(ui, "Quick Presets", None, palette);
    ui.add_space(8.0);

    ui.horizontal_wrapped(|ui| {
        for &mins in &[5u64, 10, 15, 30, 45, 60] {
            if secondary_action_button(ui, &format!("{} min", mins), palette).clicked() {
                state.start_sleep_timer_minutes(mins);
            }
        }
    });

    ui.add_space(12.0);
    card_frame(palette, 16, 14, 12).show(ui, |ui| {
        let mut after_track = state.sleep_after_current_track;
        if ui
            .checkbox(&mut after_track, "Pause automatically after current track finishes")
            .changed()
        {
            state.sleep_after_current_track = after_track;
            if after_track {
                state.sleep_timer_end = None;
                state.show_message("Will pause at end of current track");
            }
        }
    });
}

pub fn render_stats_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
) {
    ui.add_space(6.0);
    ui.label(
        RichText::new("Listening Stats")
            .size(24.0)
            .strong()
            .color(palette.text_primary),
    );
    ui.add_space(10.0);

    let stats = state.persisted.stats.clone();
    let show_covers = state.persisted.settings.show_covers;

    ScrollArea::vertical().show(ui, |ui| {
        // KPI Cards
        ui.horizontal_wrapped(|ui| {
            stat_card(
                ui,
                "Lifetime Plays",
                &stats.lifetime_plays.to_string(),
                palette,
            );
            stat_card(
                ui,
                "Total Time",
                &format!("{:.1} min", stats.total_play_time_minutes),
                palette,
            );
            stat_card(
                ui,
                "Today",
                &format!("{:.1} min", stats.today_play_time_minutes),
                palette,
            );
            stat_card(
                ui,
                "Streak",
                &format!("{} days", stats.current_streak_days),
                palette,
            );
        });

        ui.add_space(16.0);
        render_section_header(ui, "24-Hour Activity", Some("Plays per hour (UTC)"), palette);
        ui.add_space(8.0);

        // 24-Hour Bar Chart via Painter
        card_frame(palette, 18, 14, 14).show(ui, |ui| {
            let width = ui.available_width().max(200.0);
            let height = 120.0;
            let (rect, _) = ui.allocate_exact_size(Vec2::new(width, height), Sense::hover());
            let painter = ui.painter_at(rect);

            let max_val = stats.hourly_activity.iter().copied().max().unwrap_or(1).max(1);
            let slot_w = rect.width() / 24.0;
            let bar_w = (slot_w - 3.0).max(2.0);
            let chart_bottom = rect.bottom() - 18.0;
            let chart_h = (rect.height() - 24.0).max(20.0);

            for (h, &count) in stats.hourly_activity.iter().enumerate() {
                let frac = count as f32 / max_val as f32;
                let bh = (frac * chart_h).max(if count > 0 { 4.0 } else { 2.0 });
                let x = rect.left() + (h as f32) * slot_w + 1.5;
                let bar_rect = Rect::from_min_max(
                    Pos2::new(x, chart_bottom - bh),
                    Pos2::new(x + bar_w, chart_bottom),
                );
                let col = if count > 0 {
                    palette.accent
                } else {
                    palette.surface_elevated
                };
                painter.rect_filled(bar_rect, CornerRadius::same(3), col);

                if h % 6 == 0 {
                    painter.text(
                        Pos2::new(x + bar_w * 0.5, rect.bottom() - 6.0),
                        egui::Align2::CENTER_CENTER,
                        format!("{:02}h", h),
                        egui::FontId::proportional(10.0),
                        palette.text_secondary,
                    );
                }
            }
        });

        ui.add_space(16.0);
        render_section_header(ui, "Most Played Tracks", None, palette);
        ui.add_space(8.0);

        let mut ranked: Vec<(crate::model::Song, u32)> = state
            .songs
            .iter()
            .filter_map(|s| {
                stats
                    .song_play_counts
                    .get(&s.id)
                    .copied()
                    .filter(|&c| c > 0)
                    .map(|c| (s.clone(), c))
            })
            .collect();
        ranked.sort_by(|a, b| b.1.cmp(&a.1));
        ranked.truncate(10);

        if ranked.is_empty() {
            card_frame(palette, 16, 16, 20).show(ui, |ui| {
                ui.vertical_centered(|ui| {
                    ui.label(
                        RichText::new("No play history yet")
                            .size(15.5)
                            .strong()
                            .color(palette.text_primary),
                    );
                    ui.label(
                        RichText::new("Start listening to populate your top tracks and activity chart.")
                            .size(13.0)
                            .color(palette.text_secondary),
                    );
                });
            });
        } else {
            let ranked_songs: Vec<crate::model::Song> =
                ranked.iter().map(|(s, _)| s.clone()).collect();
            for (song, plays) in ranked {
                let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
                let is_liked = state.is_liked(song.id);
                ui.label(
                    RichText::new(format!("{} plays", plays))
                        .size(11.5)
                        .strong()
                        .color(palette.accent),
                );
                let act = render_song_row(
                    ui,
                    &song,
                    is_cur,
                    state.is_playing,
                    is_liked,
                    show_covers,
                    false,
                    time_sec,
                    palette,
                );
                handle_song_action(state, song, &ranked_songs, "Most Played", None, act);
                ui.add_space(6.0);
            }
        }
        ui.add_space(16.0);
    });
}

pub fn render_settings_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    folder_input: &mut String,
) {
    ui.add_space(6.0);
    let mut back = false;
    render_screen_top_bar(ui, "Settings", Some(&mut back), palette);
    if back {
        state.navigate_back();
        return;
    }

    ui.add_space(10.0);
    ScrollArea::vertical().show(ui, |ui| {
        // Profile
        render_section_header(ui, "Profile", None, palette);
        ui.add_space(6.0);
        card_frame(palette, 16, 14, 12).show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new("Display Name")
                        .size(14.0)
                        .color(palette.text_primary),
                );
                if ui
                    .text_edit_singleline(&mut state.persisted.settings.username)
                    .changed()
                {
                    state.persist();
                }
            });
        });

        ui.add_space(14.0);

        // Theme & Accent
        render_section_header(ui, "Appearance", None, palette);
        ui.add_space(6.0);
        card_frame(palette, 16, 14, 14).show(ui, |ui| {
            ui.label(
                RichText::new("Theme Mode")
                    .size(13.5)
                    .strong()
                    .color(palette.text_secondary),
            );
            ui.add_space(6.0);
            ui.horizontal_wrapped(|ui| {
                let current_mode = state.persisted.settings.theme_mode;
                for mode in ThemeMode::ALL {
                    if pill_button(ui, mode.label(), current_mode == mode, palette).clicked() {
                        state.set_theme_mode(mode);
                    }
                }
            });

            ui.add_space(12.0);
            ui.label(
                RichText::new("Accent Color")
                    .size(13.5)
                    .strong()
                    .color(palette.text_secondary),
            );
            ui.add_space(6.0);
            ui.horizontal_wrapped(|ui| {
                let current_rgb = state.persisted.settings.accent_rgb;
                for (name, rgb) in ACCENT_SWATCHES {
                    let selected = current_rgb == rgb;
                    let swatch_col = Color32::from_rgb(rgb[0], rgb[1], rgb[2]);
                    let (rect, resp) =
                        ui.allocate_exact_size(Vec2::splat(36.0), Sense::click());
                    let painter = ui.painter_at(rect);
                    painter.circle_filled(rect.center(), 14.0, swatch_col);
                    if selected {
                        painter.circle_stroke(
                            rect.center(),
                            17.0,
                            Stroke::new(2.0, palette.text_primary),
                        );
                    }
                    if resp.on_hover_text(name).clicked() {
                        state.set_accent_rgb(rgb);
                    }
                }
            });

            ui.add_space(12.0);
            let mut show_covers = state.persisted.settings.show_covers;
            if ui.checkbox(&mut show_covers, "Show Album Artwork").changed() {
                state.persisted.settings.show_covers = show_covers;
                state.persist();
            }
            let mut glass = state.persisted.settings.glassmorphism_enabled;
            if ui
                .checkbox(&mut glass, "Ambient Glassmorphic Backdrop")
                .changed()
            {
                state.persisted.settings.glassmorphism_enabled = glass;
                state.persist();
            }
        });

        ui.add_space(14.0);

        // Audio & Tools
        render_section_header(ui, "Audio & Playback", None, palette);
        ui.add_space(6.0);
        card_frame(palette, 16, 14, 12).show(ui, |ui| {
            ui.horizontal_wrapped(|ui| {
                if secondary_action_button(ui, "Open Equalizer", palette).clicked() {
                    state.navigate_to(Screen::Equalizer);
                }
                if secondary_action_button(ui, "Sleep Timer", palette).clicked() {
                    state.navigate_to(Screen::SleepTimer);
                }
            });
            ui.add_space(8.0);
            let hw_status = if state.audio.is_hardware_audio_active() {
                "Hardware audio output active (CPAL / Oboe)"
            } else {
                "Software playback clock active (no audio device detected)"
            };
            ui.label(
                RichText::new(hw_status)
                    .size(12.0)
                    .color(palette.text_secondary),
            );
        });

        ui.add_space(14.0);

        // Music Library Folders
        render_section_header(ui, "Music Folders", None, palette);
        ui.add_space(6.0);
        card_frame(palette, 16, 14, 12).show(ui, |ui| {
            ui.horizontal(|ui| {
                ui.add(
                    egui::TextEdit::singleline(folder_input)
                        .hint_text("/storage/emulated/0/Music or custom path...")
                        .desired_width((ui.available_width() - 92.0).max(100.0)),
                );
                if pill_button(ui, "Add", true, palette).clicked()
                    && !folder_input.trim().is_empty()
                {
                    let p = folder_input.clone();
                    state.add_scan_directory(&p);
                    folder_input.clear();
                }
            });

            let dirs = state.persisted.settings.scan_directories.clone();
            if !dirs.is_empty() {
                ui.add_space(8.0);
                let mut remove_idx: Option<usize> = None;
                for (idx, dir) in dirs.iter().enumerate() {
                    elevated_frame(palette, 10, 10, 6).show(ui, |ui| {
                        ui.horizontal(|ui| {
                            ui.label(
                                RichText::new(dir.to_string_lossy().as_ref())
                                    .size(12.5)
                                    .color(palette.text_primary),
                            );
                            ui.with_layout(
                                egui::Layout::right_to_left(egui::Align::Center),
                                |ui| {
                                    if ui.button("Remove").clicked() {
                                        remove_idx = Some(idx);
                                    }
                                },
                            );
                        });
                    });
                    ui.add_space(4.0);
                }
                if let Some(idx) = remove_idx {
                    state.remove_scan_directory(idx);
                }
            }

            ui.add_space(8.0);
            if primary_action_button(ui, "Rescan Library Now", palette).clicked() {
                state.load_media(true);
                state.show_message(format!("Library refreshed: {} tracks", state.songs.len()));
            }
        });

        ui.add_space(14.0);
        ui.label(
            RichText::new("VragPlayer Rust Edition v1.0.0 — Pure Rust + Symphonia + CPAL + egui")
                .size(11.5)
                .color(palette.text_secondary),
        );
        ui.add_space(20.0);
    });
}

fn stat_card(ui: &mut Ui, label: &str, value: &str, palette: &StudioPalette) {
    let (rect, _) = ui.allocate_exact_size(Vec2::new(146.0, 74.0), Sense::hover());
    let painter = ui.painter_at(rect);
    painter.rect_filled(rect, CornerRadius::same(16), palette.surface);
    painter.rect_stroke(
        rect,
        CornerRadius::same(16),
        Stroke::new(1.0, palette.border),
        StrokeKind::Inside,
    );
    painter.text(
        Pos2::new(rect.left() + 14.0, rect.top() + 24.0),
        egui::Align2::LEFT_CENTER,
        label,
        egui::FontId::proportional(12.0),
        palette.text_secondary,
    );
    painter.text(
        Pos2::new(rect.left() + 14.0, rect.top() + 50.0),
        egui::Align2::LEFT_CENTER,
        value,
        egui::FontId::proportional(19.0),
        palette.accent,
    );
}
