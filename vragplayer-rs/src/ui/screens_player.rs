use crate::model::{format_duration_fast, RepeatMode};
use crate::state::{AppState, Screen};
use crate::ui::theme::{
    card_frame, elevated_frame, paint_track_artwork, pill_button, render_screen_top_bar,
    render_seek_bar, StudioPalette,
};
use eframe::egui::{self, CornerRadius, RichText, ScrollArea, Sense, Stroke, Ui, Vec2};

pub fn render_mini_player(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
) {
    let Some(song) = state.current_song.clone() else {
        return;
    };
    let show_covers = state.persisted.settings.show_covers;
    let dur_ms = state.audio.duration_ms().max(song.duration_ms);
    let pos_ms = state.audio.position_ms().min(dur_ms);
    let frac = if dur_ms > 0 {
        (pos_ms as f32 / dur_ms as f32).clamp(0.0, 1.0)
    } else {
        0.0
    };

    card_frame(palette, 18, 10, 8).show(ui, |ui| {
        ui.set_min_width(ui.available_width());

        // Thin live progress bar at top of mini player
        let (bar_rect, _) =
            ui.allocate_exact_size(Vec2::new(ui.available_width(), 3.0), Sense::hover());
        let painter = ui.painter_at(bar_rect);
        painter.rect_filled(bar_rect, CornerRadius::same(2), palette.surface_elevated);
        let fill_rect = egui::Rect::from_min_size(
            bar_rect.min,
            Vec2::new(bar_rect.width() * frac, bar_rect.height()),
        );
        painter.rect_filled(fill_rect, CornerRadius::same(2), palette.accent);

        ui.add_space(6.0);
        ui.horizontal(|ui| {
            let art_resp = paint_track_artwork(
                ui,
                Some(&song),
                44.0,
                10,
                show_covers,
                state.is_playing,
                time_sec,
                palette,
            );
            if art_resp.clicked() {
                state.navigate_to(Screen::Player);
            }

            ui.add_space(6.0);
            let text_w = (ui.available_width() - 144.0).max(80.0);
            let info_resp = ui
                .allocate_ui_with_layout(
                    Vec2::new(text_w, 44.0),
                    egui::Layout::top_down(egui::Align::LEFT),
                    |ui| {
                        ui.add(
                            egui::Label::new(
                                RichText::new(&song.title)
                                    .size(14.5)
                                    .strong()
                                    .color(palette.text_primary),
                            )
                            .truncate(),
                        );
                        ui.add(
                            egui::Label::new(
                                RichText::new(&song.artist)
                                    .size(12.0)
                                    .color(palette.text_secondary),
                            )
                            .truncate(),
                        );
                    },
                )
                .response
                .interact(Sense::click());
            if info_resp.clicked() {
                state.navigate_to(Screen::Player);
            }

            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if ui
                    .add(
                        egui::Button::new(
                            RichText::new("⏭")
                                .size(17.0)
                                .color(palette.text_primary),
                        )
                        .frame(false)
                        .min_size(Vec2::splat(38.0)),
                    )
                    .clicked()
                {
                    state.play_next();
                }

                let play_icon = if state.is_playing { "⏸" } else { "▶" };
                if ui
                    .add(
                        egui::Button::new(
                            RichText::new(play_icon)
                                .size(18.0)
                                .strong()
                                .color(palette.on_accent),
                        )
                        .fill(palette.accent)
                        .corner_radius(CornerRadius::same(20))
                        .min_size(Vec2::splat(40.0)),
                    )
                    .clicked()
                {
                    state.toggle_playback();
                }

                if ui
                    .add(
                        egui::Button::new(
                            RichText::new("⏮")
                                .size(17.0)
                                .color(palette.text_primary),
                        )
                        .frame(false)
                        .min_size(Vec2::splat(38.0)),
                    )
                    .clicked()
                {
                    state.play_previous();
                }
            });
        });
    });
}

pub fn render_player_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
) {
    ui.add_space(6.0);
    let mut back = false;
    render_screen_top_bar(ui, "Now Playing", Some(&mut back), palette);
    if back {
        state.navigate_back();
        return;
    }

    let Some(song) = state.current_song.clone() else {
        ui.add_space(24.0);
        card_frame(palette, 20, 20, 28).show(ui, |ui| {
            ui.vertical_centered(|ui| {
                ui.label(
                    RichText::new("Nothing playing")
                        .size(18.0)
                        .strong()
                        .color(palette.text_primary),
                );
                ui.add_space(6.0);
                ui.label(
                    RichText::new("Choose any track from Home or Library to begin playback.")
                        .size(13.5)
                        .color(palette.text_secondary),
                );
            });
        });
        return;
    };

    let show_covers = state.persisted.settings.show_covers;
    let is_liked = state.is_liked(song.id);
    let dur_ms = state.audio.duration_ms().max(song.duration_ms);
    let pos_ms = state.audio.position_ms().min(dur_ms);
    let speed = state.persisted.settings.playback_speed;
    let sleep_rem = state.sleep_remaining_ms();

    ScrollArea::vertical().show(ui, |ui| {
        ui.vertical_centered(|ui| {
            ui.add_space(6.0);
            ui.label(
                RichText::new(format!(
                    "PLAYING FROM {} • {}",
                    state.active_queue_source.to_ascii_uppercase(),
                    song.format_label()
                ))
                .size(11.5)
                .strong()
                .color(palette.accent),
            );
            ui.add_space(14.0);

            // Large Studio Artwork
            let art_size = (ui.available_width() - 48.0).clamp(180.0, 290.0);
            paint_track_artwork(
                ui,
                Some(&song),
                art_size,
                26,
                show_covers,
                state.is_playing,
                time_sec,
                palette,
            );

            ui.add_space(18.0);
            ui.label(
                RichText::new(&song.title)
                    .size(23.0)
                    .strong()
                    .color(palette.text_primary),
            );
            ui.add_space(3.0);
            ui.label(
                RichText::new(format!("{} • {}", song.artist, song.album))
                    .size(14.5)
                    .color(palette.text_secondary),
            );

            if sleep_rem > 0 || state.sleep_after_current_track {
                ui.add_space(6.0);
                let sleep_txt = if state.sleep_after_current_track {
                    "Sleep timer: End of track".to_string()
                } else {
                    format!("Sleep timer: {}", format_duration_fast(sleep_rem))
                };
                ui.label(
                    RichText::new(sleep_txt)
                        .size(12.0)
                        .strong()
                        .color(palette.accent),
                );
            }
        });

        ui.add_space(16.0);

        // Seek bar & timestamps
        card_frame(palette, 18, 16, 12).show(ui, |ui| {
            if let Some(seek_ms) = render_seek_bar(ui, pos_ms, dur_ms, palette) {
                state.seek_to(seek_ms);
            }
            ui.horizontal(|ui| {
                ui.label(
                    RichText::new(format_duration_fast(pos_ms))
                        .size(12.0)
                        .color(palette.text_secondary),
                );
                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.label(
                        RichText::new(format_duration_fast(dur_ms))
                            .size(12.0)
                            .color(palette.text_secondary),
                    );
                });
            });
        });

        ui.add_space(16.0);

        // Primary Transport Row
        ui.vertical_centered(|ui| {
            ui.horizontal(|ui| {
                let total_w = 56.0 * 4.0 + 68.0 + 32.0;
                let pad = ((ui.available_width() - total_w) * 0.5).max(0.0);
                ui.add_space(pad);

                // Shuffle
                if transport_btn(
                    ui,
                    "⇄",
                    48.0,
                    state.shuffle_enabled,
                    false,
                    palette,
                )
                .clicked()
                {
                    state.toggle_shuffle_mode();
                }

                // Previous
                if transport_btn(ui, "⏮", 52.0, false, false, palette).clicked() {
                    state.play_previous();
                }

                // Play / Pause
                let play_glyph = if state.is_playing { "⏸" } else { "▶" };
                if transport_btn(ui, play_glyph, 66.0, true, true, palette).clicked() {
                    state.toggle_playback();
                }

                // Next
                if transport_btn(ui, "⏭", 52.0, false, false, palette).clicked() {
                    state.play_next();
                }

                // Repeat
                let rep_glyph = match state.repeat_mode {
                    RepeatMode::Off => "↻",
                    RepeatMode::All => "↻A",
                    RepeatMode::One => "↻1",
                };
                if transport_btn(
                    ui,
                    rep_glyph,
                    48.0,
                    state.repeat_mode != RepeatMode::Off,
                    false,
                    palette,
                )
                .clicked()
                {
                    state.toggle_repeat_mode();
                }
            });
        });

        ui.add_space(16.0);

        // Playback Speed Selector
        elevated_frame(palette, 16, 12, 10).show(ui, |ui| {
            ui.horizontal_wrapped(|ui| {
                ui.label(
                    RichText::new("Speed")
                        .size(13.0)
                        .strong()
                        .color(palette.text_secondary),
                );
                for &sp in &[0.75f32, 1.0, 1.25, 1.5, 2.0] {
                    let active = (speed - sp).abs() < 0.05;
                    if pill_button(ui, &format!("{:.2}x", sp), active, palette).clicked() {
                        state.set_playback_speed(sp);
                    }
                }
            });
        });

        ui.add_space(12.0);

        // Utility Actions Bar
        card_frame(palette, 18, 12, 12).show(ui, |ui| {
            ui.horizontal_wrapped(|ui| {
                let fav_label = if is_liked { "♥ Favorited" } else { "♡ Favorite" };
                if pill_button(ui, fav_label, is_liked, palette).clicked() {
                    state.toggle_like(song.id);
                }
                if pill_button(ui, "Queue", false, palette).clicked() {
                    state.navigate_to(Screen::Queue);
                }
                if pill_button(
                    ui,
                    "Equalizer",
                    state.persisted.settings.eq_enabled,
                    palette,
                )
                .clicked()
                {
                    state.navigate_to(Screen::Equalizer);
                }
                if pill_button(
                    ui,
                    "Sleep Timer",
                    sleep_rem > 0 || state.sleep_after_current_track,
                    palette,
                )
                .clicked()
                {
                    state.navigate_to(Screen::SleepTimer);
                }
                if pill_button(ui, "+ Playlist", false, palette).clicked() {
                    state.song_to_add_to_playlist = Some(song.clone());
                }
                if pill_button(ui, "Track Info", false, palette).clicked() {
                    state.song_info_target = Some(song.clone());
                }
            });
        });

        ui.add_space(20.0);
    });
}

fn transport_btn(
    ui: &mut Ui,
    glyph: &str,
    size: f32,
    active: bool,
    primary: bool,
    palette: &StudioPalette,
) -> egui::Response {
    let fill = if primary {
        palette.accent
    } else if active {
        palette.accent_soft
    } else {
        palette.surface_elevated
    };
    let fg = if primary {
        palette.on_accent
    } else if active {
        palette.accent
    } else {
        palette.text_primary
    };
    let stroke = if primary {
        Stroke::NONE
    } else {
        Stroke::new(1.0, if active { palette.accent } else { palette.border })
    };
    ui.add(
        egui::Button::new(
            RichText::new(glyph)
                .size(if primary { 24.0 } else { 17.0 })
                .strong()
                .color(fg),
        )
        .fill(fill)
        .stroke(stroke)
        .corner_radius(CornerRadius::same((size * 0.5) as u8))
        .min_size(Vec2::splat(size)),
    )
}
