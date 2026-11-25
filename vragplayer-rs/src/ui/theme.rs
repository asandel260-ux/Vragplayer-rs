use crate::model::{stable_hash64, Song, ThemeMode};
use eframe::egui::{self, Color32, CornerRadius, FontId, Frame, Margin, Pos2, Rect, RichText, Sense, Stroke, StrokeKind, Ui, Vec2};

#[derive(Debug, Clone, Copy)]
pub struct StudioPalette {
    pub bg: Color32,
    pub surface: Color32,
    pub surface_elevated: Color32,
    pub border: Color32,
    pub text_primary: Color32,
    pub text_secondary: Color32,
    pub accent: Color32,
    pub accent_soft: Color32,
    pub on_accent: Color32,
    pub is_light: bool,
    pub glass: bool,
}

impl StudioPalette {
    pub fn from_settings(mode: ThemeMode, accent_rgb: [u8; 3], glass: bool) -> Self {
        let accent = Color32::from_rgb(accent_rgb[0], accent_rgb[1], accent_rgb[2]);
        let accent_soft = Color32::from_rgba_unmultiplied(accent_rgb[0], accent_rgb[1], accent_rgb[2], 46);
        let luma = (0.299 * accent_rgb[0] as f32)
            + (0.587 * accent_rgb[1] as f32)
            + (0.114 * accent_rgb[2] as f32);
        let on_accent = if luma > 165.0 {
            Color32::from_rgb(12, 14, 18)
        } else {
            Color32::WHITE
        };

        match mode {
            ThemeMode::DarkStudio => Self {
                bg: Color32::from_rgb(13, 14, 19),
                surface: if glass {
                    Color32::from_rgba_unmultiplied(24, 27, 36, 212)
                } else {
                    Color32::from_rgb(23, 25, 33)
                },
                surface_elevated: if glass {
                    Color32::from_rgba_unmultiplied(34, 38, 50, 224)
                } else {
                    Color32::from_rgb(31, 34, 45)
                },
                border: Color32::from_rgba_unmultiplied(255, 255, 255, 26),
                text_primary: Color32::from_rgb(245, 246, 250),
                text_secondary: Color32::from_rgb(156, 163, 179),
                accent,
                accent_soft,
                on_accent,
                is_light: false,
                glass,
            },
            ThemeMode::OledBlack => Self {
                bg: Color32::from_rgb(0, 0, 0),
                surface: if glass {
                    Color32::from_rgba_unmultiplied(15, 16, 20, 220)
                } else {
                    Color32::from_rgb(13, 14, 18)
                },
                surface_elevated: Color32::from_rgb(22, 24, 30),
                border: Color32::from_rgba_unmultiplied(255, 255, 255, 22),
                text_primary: Color32::from_rgb(248, 249, 252),
                text_secondary: Color32::from_rgb(148, 155, 170),
                accent,
                accent_soft,
                on_accent,
                is_light: false,
                glass,
            },
            ThemeMode::LightStudio => Self {
                bg: Color32::from_rgb(244, 245, 249),
                surface: if glass {
                    Color32::from_rgba_unmultiplied(255, 255, 255, 226)
                } else {
                    Color32::from_rgb(255, 255, 255)
                },
                surface_elevated: Color32::from_rgb(235, 238, 246),
                border: Color32::from_rgba_unmultiplied(15, 23, 42, 24),
                text_primary: Color32::from_rgb(18, 21, 30),
                text_secondary: Color32::from_rgb(96, 104, 122),
                accent,
                accent_soft,
                on_accent,
                is_light: true,
                glass,
            },
            ThemeMode::MidnightViolet => Self {
                bg: Color32::from_rgb(15, 11, 26),
                surface: if glass {
                    Color32::from_rgba_unmultiplied(29, 22, 48, 214)
                } else {
                    Color32::from_rgb(26, 20, 44)
                },
                surface_elevated: Color32::from_rgb(39, 30, 64),
                border: Color32::from_rgba_unmultiplied(216, 180, 254, 32),
                text_primary: Color32::from_rgb(247, 244, 255),
                text_secondary: Color32::from_rgb(174, 162, 204),
                accent,
                accent_soft,
                on_accent,
                is_light: false,
                glass,
            },
        }
    }
}

pub fn apply_studio_visuals(ctx: &egui::Context, palette: &StudioPalette) {
    let mut visuals = if palette.is_light {
        egui::Visuals::light()
    } else {
        egui::Visuals::dark()
    };

    visuals.panel_fill = palette.bg;
    visuals.window_fill = palette.surface_elevated;
    visuals.window_stroke = Stroke::new(1.0, palette.border);
    visuals.window_corner_radius = CornerRadius::same(18);
    visuals.extreme_bg_color = palette.bg;
    visuals.faint_bg_color = palette.surface;
    visuals.selection.bg_fill = palette.accent;
    visuals.selection.stroke = Stroke::new(1.0, palette.on_accent);

    visuals.widgets.noninteractive.bg_fill = palette.surface;
    visuals.widgets.noninteractive.fg_stroke = Stroke::new(1.0, palette.text_primary);
    visuals.widgets.noninteractive.corner_radius = CornerRadius::same(10);

    visuals.widgets.inactive.bg_fill = palette.surface_elevated;
    visuals.widgets.inactive.weak_bg_fill = palette.surface_elevated;
    visuals.widgets.inactive.fg_stroke = Stroke::new(1.0, palette.text_primary);
    visuals.widgets.inactive.corner_radius = CornerRadius::same(10);

    visuals.widgets.hovered.bg_fill = palette.accent_soft;
    visuals.widgets.hovered.weak_bg_fill = palette.accent_soft;
    visuals.widgets.hovered.fg_stroke = Stroke::new(1.0, palette.text_primary);
    visuals.widgets.hovered.corner_radius = CornerRadius::same(10);

    visuals.widgets.active.bg_fill = palette.accent;
    visuals.widgets.active.weak_bg_fill = palette.accent;
    visuals.widgets.active.fg_stroke = Stroke::new(1.0, palette.on_accent);
    visuals.widgets.active.corner_radius = CornerRadius::same(10);

    ctx.set_visuals(visuals);
}

pub fn card_frame(palette: &StudioPalette, radius: u8, pad_x: i8, pad_y: i8) -> Frame {
    Frame::new()
        .fill(palette.surface)
        .stroke(Stroke::new(1.0, palette.border))
        .corner_radius(CornerRadius::same(radius))
        .inner_margin(Margin::symmetric(pad_x, pad_y))
}

pub fn elevated_frame(palette: &StudioPalette, radius: u8, pad_x: i8, pad_y: i8) -> Frame {
    Frame::new()
        .fill(palette.surface_elevated)
        .stroke(Stroke::new(1.0, palette.border))
        .corner_radius(CornerRadius::same(radius))
        .inner_margin(Margin::symmetric(pad_x, pad_y))
}

pub fn pill_button(ui: &mut Ui, label: &str, selected: bool, palette: &StudioPalette) -> egui::Response {
    let fill = if selected {
        palette.accent
    } else {
        palette.surface_elevated
    };
    let text_color = if selected {
        palette.on_accent
    } else {
        palette.text_primary
    };
    let stroke = if selected {
        Stroke::NONE
    } else {
        Stroke::new(1.0, palette.border)
    };
    ui.add(
        egui::Button::new(RichText::new(label).size(13.0).strong().color(text_color))
            .fill(fill)
            .stroke(stroke)
            .corner_radius(CornerRadius::same(16))
            .min_size(egui::vec2(0.0, 34.0)),
    )
}

pub fn primary_action_button(ui: &mut Ui, label: &str, palette: &StudioPalette) -> egui::Response {
    ui.add(
        egui::Button::new(
            RichText::new(label)
                .size(14.0)
                .strong()
                .color(palette.on_accent),
        )
        .fill(palette.accent)
        .stroke(Stroke::NONE)
        .corner_radius(CornerRadius::same(14))
        .min_size(egui::vec2(0.0, 44.0)),
    )
}

pub fn secondary_action_button(ui: &mut Ui, label: &str, palette: &StudioPalette) -> egui::Response {
    ui.add(
        egui::Button::new(
            RichText::new(label)
                .size(14.0)
                .strong()
                .color(palette.text_primary),
        )
        .fill(palette.surface_elevated)
        .stroke(Stroke::new(1.0, palette.border))
        .corner_radius(CornerRadius::same(14))
        .min_size(egui::vec2(0.0, 44.0)),
    )
}

pub fn paint_ambient_backdrop(ctx: &egui::Context, palette: &StudioPalette, is_playing: bool, time_sec: f64) {
    if !palette.glass {
        return;
    }
    let rect = ctx.screen_rect();
    let painter = ctx.layer_painter(egui::LayerId::background());
    painter.rect_filled(rect, CornerRadius::ZERO, palette.bg);

    let drift = if is_playing { (time_sec * 0.35).sin() as f32 * 36.0 } else { 0.0 };
    let alpha1: u8 = if palette.is_light { 26 } else { 42 };
    let alpha2: u8 = if palette.is_light { 18 } else { 30 };

    let c1 = Color32::from_rgba_unmultiplied(palette.accent.r(), palette.accent.g(), palette.accent.b(), alpha1);
    let c2 = Color32::from_rgba_unmultiplied(
        palette.accent.b().saturating_add(40),
        palette.accent.r().saturating_add(20),
        palette.accent.g().saturating_add(60),
        alpha2,
    );

    let radius1 = rect.width().min(rect.height()) * 0.55;
    let center1 = Pos2::new(rect.left() + rect.width() * 0.18 + drift, rect.top() + rect.height() * 0.16);
    painter.circle_filled(center1, radius1, c1);

    let radius2 = rect.width().min(rect.height()) * 0.48;
    let center2 = Pos2::new(rect.right() - rect.width() * 0.15 - drift, rect.bottom() - rect.height() * 0.22);
    painter.circle_filled(center2, radius2, c2);
}

/// Deterministically paints studio-grade procedural album artwork with vinyl grooves & monogram.
pub fn paint_track_artwork(
    ui: &mut Ui,
    song: Option<&Song>,
    size: f32,
    radius: u8,
    show_covers: bool,
    is_playing: bool,
    time_sec: f64,
    palette: &StudioPalette,
) -> egui::Response {
    let (rect, response) = ui.allocate_exact_size(Vec2::splat(size), Sense::click());
    let painter = ui.painter_at(rect);

    let seed = song
        .map(|s| stable_hash64(s.artwork_key()))
        .unwrap_or(0x5A3C_91E7);

    let (base_col, glow_col) = if show_covers {
        let r1 = 48u8.saturating_add(((seed & 0x7F) as u8).saturating_mul(1));
        let g1 = 44u8.saturating_add((((seed >> 8) & 0x7F) as u8).saturating_mul(1));
        let b1 = 68u8.saturating_add((((seed >> 16) & 0x7F) as u8).saturating_mul(1));
        let r2 = 120u8.saturating_add(((seed >> 24) & 0x7F) as u8);
        let g2 = 75u8.saturating_add(((seed >> 32) & 0x7F) as u8);
        let b2 = 115u8.saturating_add(((seed >> 40) & 0x7F) as u8);
        (
            Color32::from_rgb(r1, g1, b1),
            Color32::from_rgba_unmultiplied(r2, g2, b2, 130),
        )
    } else {
        (palette.surface_elevated, palette.accent_soft)
    };

    painter.rect_filled(rect, CornerRadius::same(radius), base_col);

    // Subtle geometric studio accent glow
    let orb_center = Pos2::new(
        rect.left() + rect.width() * 0.72,
        rect.top() + rect.height() * 0.28,
    );
    painter.circle_filled(orb_center, size * 0.42, glow_col);

    // Subtle concentric vinyl ring
    let center = rect.center();
    painter.circle_stroke(
        center,
        size * 0.34,
        Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 36)),
    );
    painter.circle_stroke(
        center,
        size * 0.20,
        Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 26)),
    );

    // Animated pulse ring when actively playing
    if is_playing {
        let pulse = 0.5 + 0.5 * (time_sec * 3.2).sin() as f32;
        let pulse_alpha = (35.0 + pulse * 55.0) as u8;
        painter.circle_stroke(
            center,
            size * (0.24 + pulse * 0.06),
            Stroke::new(
                1.5,
                Color32::from_rgba_unmultiplied(
                    palette.accent.r(),
                    palette.accent.g(),
                    palette.accent.b(),
                    pulse_alpha,
                ),
            ),
        );
    }

    // Center monogram / musical glyph
    let glyph = song
        .and_then(|s| s.title.chars().next())
        .map(|c| c.to_ascii_uppercase().to_string())
        .unwrap_or_else(|| "V".to_string());

    painter.text(
        center,
        egui::Align2::CENTER_CENTER,
        glyph,
        FontId::proportional((size * 0.36).clamp(14.0, 64.0)),
        Color32::from_rgba_unmultiplied(255, 255, 255, 230),
    );

    painter.rect_stroke(
        rect,
        CornerRadius::same(radius),
        Stroke::new(1.0, Color32::from_rgba_unmultiplied(255, 255, 255, 34)),
        StrokeKind::Inside,
    );

    response
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum SongRowAction {
    None,
    Play,
    ToggleLike,
    PlayNext,
    AddToQueue,
    AddToPlaylist,
    ShowInfo,
    RemoveFromContext,
}

#[allow(clippy::too_many_arguments)]
pub fn render_song_row(
    ui: &mut Ui,
    song: &Song,
    is_current: bool,
    is_playing: bool,
    is_liked: bool,
    show_covers: bool,
    allow_remove: bool,
    time_sec: f64,
    palette: &StudioPalette,
) -> SongRowAction {
    let mut action = SongRowAction::None;
    let row_fill = if is_current {
        palette.accent_soft
    } else {
        palette.surface
    };
    let row_stroke = if is_current {
        Stroke::new(1.0, palette.accent)
    } else {
        Stroke::new(1.0, palette.border)
    };

    Frame::new()
        .fill(row_fill)
        .stroke(row_stroke)
        .corner_radius(CornerRadius::same(14))
        .inner_margin(Margin::symmetric(10, 8))
        .show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                let art_resp = paint_track_artwork(
                    ui,
                    Some(song),
                    46.0,
                    10,
                    show_covers,
                    is_current && is_playing,
                    time_sec,
                    palette,
                );
                if art_resp.clicked() {
                    action = SongRowAction::Play;
                }

                ui.add_space(6.0);

                let text_width = (ui.available_width() - 122.0).max(80.0);
                let title_resp = ui
                    .allocate_ui_with_layout(
                        Vec2::new(text_width, 46.0),
                        egui::Layout::top_down(egui::Align::LEFT),
                        |ui| {
                            let title_color = if is_current {
                                palette.accent
                            } else {
                                palette.text_primary
                            };
                            ui.add(
                                egui::Label::new(
                                    RichText::new(&song.title)
                                        .size(15.0)
                                        .strong()
                                        .color(title_color),
                                )
                                .truncate(),
                            );
                            ui.add(
                                egui::Label::new(
                                    RichText::new(format!(
                                        "{} • {} • {}",
                                        song.artist,
                                        song.duration_formatted(),
                                        song.format_label()
                                    ))
                                    .size(12.0)
                                    .color(palette.text_secondary),
                                )
                                .truncate(),
                            );
                        },
                    )
                    .response
                    .interact(Sense::click());

                if title_resp.clicked() {
                    action = SongRowAction::Play;
                }

                ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                    ui.menu_button(
                        RichText::new("...").size(16.0).strong().color(palette.text_secondary),
                        |ui| {
                            if ui.button("Play Now").clicked() {
                                action = SongRowAction::Play;
                                ui.close_menu();
                            }
                            if ui.button("Play Next").clicked() {
                                action = SongRowAction::PlayNext;
                                ui.close_menu();
                            }
                            if ui.button("Add to Queue").clicked() {
                                action = SongRowAction::AddToQueue;
                                ui.close_menu();
                            }
                            if ui.button("Add to Playlist").clicked() {
                                action = SongRowAction::AddToPlaylist;
                                ui.close_menu();
                            }
                            if ui.button("Track Details").clicked() {
                                action = SongRowAction::ShowInfo;
                                ui.close_menu();
                            }
                            if allow_remove && ui.button("Remove").clicked() {
                                action = SongRowAction::RemoveFromContext;
                                ui.close_menu();
                            }
                        },
                    );

                    let heart_label = if is_liked { "♥" } else { "♡" };
                    let heart_color = if is_liked {
                        palette.accent
                    } else {
                        palette.text_secondary
                    };
                    if ui
                        .add(
                            egui::Button::new(RichText::new(heart_label).size(17.0).color(heart_color))
                                .frame(false)
                                .min_size(egui::vec2(36.0, 36.0)),
                        )
                        .clicked()
                    {
                        action = SongRowAction::ToggleLike;
                    }
                });
            });
        });

    action
}

pub fn render_section_header(ui: &mut Ui, title: &str, subtitle: Option<&str>, palette: &StudioPalette) {
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(title)
                .size(18.0)
                .strong()
                .color(palette.text_primary),
        );
        if let Some(sub) = subtitle {
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                ui.label(RichText::new(sub).size(12.5).color(palette.text_secondary));
            });
        }
    });
}

pub fn render_screen_top_bar(
    ui: &mut Ui,
    title: &str,
    on_back: Option<&mut bool>,
    palette: &StudioPalette,
) {
    ui.horizontal(|ui| {
        if let Some(clicked_back) = on_back {
            if ui
                .add(
                    egui::Button::new(
                        RichText::new("< Back")
                            .size(14.0)
                            .strong()
                            .color(palette.text_primary),
                    )
                    .fill(palette.surface_elevated)
                    .stroke(Stroke::new(1.0, palette.border))
                    .corner_radius(CornerRadius::same(12))
                    .min_size(egui::vec2(72.0, 38.0)),
                )
                .clicked()
            {
                *clicked_back = true;
            }
            ui.add_space(6.0);
        }
        ui.label(
            RichText::new(title)
                .size(22.0)
                .strong()
                .color(palette.text_primary),
        );
    });
}

/// Interactive draggable progress / seek bar rendered cleanly via `egui::Painter`.
pub fn render_seek_bar(
    ui: &mut Ui,
    position_ms: u64,
    duration_ms: u64,
    palette: &StudioPalette,
) -> Option<u64> {
    let width = ui.available_width().max(80.0);
    let height = 28.0;
    let (rect, response) = ui.allocate_exact_size(Vec2::new(width, height), Sense::click_and_drag());
    let painter = ui.painter_at(rect);

    let fraction = if duration_ms > 0 {
        (position_ms as f32 / duration_ms as f32).clamp(0.0, 1.0)
    } else {
        0.0
    };

    let track_h = 6.0;
    let track_y = rect.center().y;
    let track_rect = Rect::from_min_max(
        Pos2::new(rect.left(), track_y - track_h * 0.5),
        Pos2::new(rect.right(), track_y + track_h * 0.5),
    );
    painter.rect_filled(track_rect, CornerRadius::same(3), palette.surface_elevated);

    let active_x = rect.left() + width * fraction;
    let active_rect = Rect::from_min_max(
        Pos2::new(rect.left(), track_y - track_h * 0.5),
        Pos2::new(active_x, track_y + track_h * 0.5),
    );
    painter.rect_filled(active_rect, CornerRadius::same(3), palette.accent);

    let thumb_radius = if response.dragged() || response.hovered() {
        8.5
    } else {
        6.5
    };
    painter.circle_filled(Pos2::new(active_x, track_y), thumb_radius, palette.accent);
    painter.circle_stroke(
        Pos2::new(active_x, track_y),
        thumb_radius,
        Stroke::new(2.0, palette.on_accent),
    );

    if (response.clicked() || response.dragged()) && duration_ms > 0 {
        if let Some(pointer) = response.interact_pointer_pos() {
            let new_frac = ((pointer.x - rect.left()) / width).clamp(0.0, 1.0);
            return Some((new_frac as f64 * duration_ms as f64) as u64);
        }
    }
    None
}
