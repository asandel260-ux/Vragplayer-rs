pub mod screens_home;
pub mod screens_library;
pub mod screens_player;
pub mod screens_tools;
pub mod theme;

use crate::model::format_file_size;
use crate::state::{AppState, Screen};
use crate::ui::theme::{
    apply_studio_visuals, card_frame, paint_ambient_backdrop, pill_button, primary_action_button,
    StudioPalette,
};
use eframe::egui::{self, Color32, CornerRadius, Frame, Margin, RichText, Stroke, Vec2};
use std::path::PathBuf;
use std::time::Duration;

const TOP_TABS: [(Screen, &str); 4] = [
    (Screen::Home, "Listen"),
    (Screen::Search, "Search"),
    (Screen::Library, "Library"),
    (Screen::Stats, "Stats"),
];

pub struct VragApp {
    pub state: AppState,
    new_playlist_draft: String,
    rename_playlist_draft: Option<(String, String)>,
    save_queue_name: String,
    folder_input: String,
    modal_new_playlist_name: String,
    modal_show_new_playlist: bool,
}

impl VragApp {
    pub fn new(_cc: &eframe::CreationContext<'_>, data_dir: PathBuf) -> Self {
        Self {
            state: AppState::new(data_dir),
            new_playlist_draft: String::new(),
            rename_playlist_draft: None,
            save_queue_name: String::new(),
            folder_input: String::new(),
            modal_new_playlist_name: String::new(),
            modal_show_new_playlist: false,
        }
    }
}

impl eframe::App for VragApp {
    fn update(&mut self, ctx: &egui::Context, _frame: &mut eframe::Frame) {
        self.state.tick();

        let palette = StudioPalette::from_settings(
            self.state.persisted.settings.theme_mode,
            self.state.persisted.settings.accent_rgb,
            self.state.persisted.settings.glassmorphism_enabled,
        );
        apply_studio_visuals(ctx, &palette);

        let time_sec = ctx.input(|i| i.time);
        paint_ambient_backdrop(ctx, &palette, self.state.is_playing, time_sec);

        if self.state.is_playing
            || self.state.sleep_timer_end.is_some()
            || self.state.status_message.is_some()
        {
            ctx.request_repaint_after(Duration::from_millis(120));
        }

        let is_player_screen = self.state.screen == Screen::Player;
        let use_nav_rail = ctx.screen_rect().width() >= 600.0 && !is_player_screen;

        // Side NavigationRail on tablet / desktop (>= 600dp)
        if use_nav_rail {
            egui::SidePanel::left("vrag_nav_rail")
                .resizable(false)
                .exact_width(116.0)
                .frame(
                    Frame::new()
                        .fill(palette.surface)
                        .stroke(Stroke::new(1.0_f32, palette.border))
                        .inner_margin(Margin::symmetric(10, 14)),
                )
                .show(ctx, |ui| {
                    ui.label(
                        RichText::new("VragPlayer")
                            .size(15.0)
                            .strong()
                            .color(palette.accent),
                    );
                    ui.add_space(16.0);
                    for (tab_screen, label) in &TOP_TABS {
                        let selected = self.state.screen == *tab_screen;
                        if ui
                            .add(
                                egui::Button::new(
                                    RichText::new(*label).size(14.0).strong().color(if selected {
                                        palette.on_accent
                                    } else {
                                        palette.text_primary
                                    }),
                                )
                                .fill(if selected {
                                    palette.accent
                                } else {
                                    Color32::TRANSPARENT
                                })
                                .corner_radius(CornerRadius::same(12))
                                .min_size(Vec2::new(96.0, 44.0)),
                            )
                            .clicked()
                        {
                            self.state.navigate_top_tab(tab_screen.clone());
                        }
                        ui.add_space(6.0);
                    }
                });
        }

        // Bottom Panel: MiniPlayer + Mobile Bottom Navigation Bar
        if !is_player_screen {
            egui::TopBottomPanel::bottom("vrag_bottom_chrome")
                .frame(
                    Frame::new()
                        .fill(Color32::TRANSPARENT)
                        .inner_margin(Margin::symmetric(10, 6)),
                )
                .show(ctx, |ui| {
                    if self.state.current_song.is_some() {
                        screens_player::render_mini_player(ui, &mut self.state, &palette, time_sec);
                        ui.add_space(6.0);
                    }

                    if !use_nav_rail {
                        card_frame(&palette, 20, 8, 6).show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            let btn_w = ((ui.available_width() - 18.0) / 4.0).max(56.0);
                            ui.horizontal(|ui| {
                                for (tab_screen, label) in &TOP_TABS {
                                    let selected = self.state.screen == *tab_screen;
                                    if ui
                                        .add(
                                            egui::Button::new(
                                                RichText::new(*label)
                                                    .size(13.0)
                                                    .strong()
                                                    .color(if selected {
                                                        palette.on_accent
                                                    } else {
                                                        palette.text_secondary
                                                    }),
                                            )
                                            .fill(if selected {
                                                palette.accent
                                            } else {
                                                Color32::TRANSPARENT
                                            })
                                            .corner_radius(CornerRadius::same(14))
                                            .min_size(Vec2::new(btn_w, 44.0)),
                                        )
                                        .clicked()
                                    {
                                        self.state.navigate_top_tab(tab_screen.clone());
                                    }
                                }
                            });
                        });
                    }
                });
        }

        // Main Content Pane
        egui::CentralPanel::default()
            .frame(
                Frame::new()
                    .fill(if palette.glass {
                        Color32::TRANSPARENT
                    } else {
                        palette.bg
                    })
                    .inner_margin(Margin::symmetric(14, 10)),
            )
            .show(ctx, |ui| {
                // Transient status banner
                if let Some((msg, _)) = self.state.status_message.clone() {
                    Frame::new()
                        .fill(palette.accent)
                        .corner_radius(CornerRadius::same(12))
                        .inner_margin(Margin::symmetric(12, 6))
                        .show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.label(
                                RichText::new(msg)
                                    .size(13.0)
                                    .strong()
                                    .color(palette.on_accent),
                            );
                        });
                    ui.add_space(6.0);
                }

                match self.state.screen.clone() {
                    Screen::Home => {
                        screens_home::render_home_screen(ui, &mut self.state, &palette, time_sec);
                    }
                    Screen::Search => {
                        screens_home::render_search_screen(ui, &mut self.state, &palette, time_sec);
                    }
                    Screen::Library => {
                        screens_library::render_library_screen(
                            ui,
                            &mut self.state,
                            &palette,
                            time_sec,
                            false,
                            &mut self.new_playlist_draft,
                            &mut self.rename_playlist_draft,
                        );
                    }
                    Screen::Liked => {
                        screens_library::render_library_screen(
                            ui,
                            &mut self.state,
                            &palette,
                            time_sec,
                            true,
                            &mut self.new_playlist_draft,
                            &mut self.rename_playlist_draft,
                        );
                    }
                    Screen::Stats => {
                        screens_tools::render_stats_screen(ui, &mut self.state, &palette, time_sec);
                    }
                    Screen::Settings => {
                        screens_tools::render_settings_screen(
                            ui,
                            &mut self.state,
                            &palette,
                            &mut self.folder_input,
                        );
                    }
                    Screen::Player => {
                        screens_player::render_player_screen(
                            ui,
                            &mut self.state,
                            &palette,
                            time_sec,
                        );
                    }
                    Screen::Equalizer => {
                        screens_tools::render_equalizer_screen(ui, &mut self.state, &palette);
                    }
                    Screen::SleepTimer => {
                        screens_tools::render_sleep_timer_screen(ui, &mut self.state, &palette);
                    }
                    Screen::Queue => {
                        screens_library::render_queue_screen(
                            ui,
                            &mut self.state,
                            &palette,
                            time_sec,
                            &mut self.save_queue_name,
                        );
                    }
                }
            });

        // Global Modal: Add to Playlist
        if let Some(target_song) = self.state.song_to_add_to_playlist.clone() {
            let mut close_modal = false;
            egui::Window::new("Add to Playlist")
                .collapsible(false)
                .resizable(false)
                .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
                .default_width(310.0)
                .max_width(310.0)
                .show(ctx, |ui| {
                    ui.label(
                        RichText::new(&target_song.title)
                            .size(13.5)
                            .strong()
                            .color(palette.accent),
                    );
                    ui.add_space(8.0);

                    let playlists = self.state.persisted.playlists.clone();
                    if playlists.is_empty() && !self.modal_show_new_playlist {
                        ui.label(
                            RichText::new("No playlists yet. Create one below.")
                                .size(13.0)
                                .color(palette.text_secondary),
                        );
                    } else {
                        for pl in &playlists {
                            if ui
                                .add(
                                    egui::Button::new(
                                        RichText::new(format!(
                                            "{} ({} tracks)",
                                            pl.name,
                                            pl.song_ids.len()
                                        ))
                                        .color(palette.text_primary),
                                    )
                                    .fill(palette.surface_elevated)
                                    .corner_radius(CornerRadius::same(10))
                                    .min_size(Vec2::new(ui.available_width(), 38.0)),
                                )
                                .clicked()
                            {
                                self.state.add_song_to_playlist(&pl.id, target_song.id);
                                close_modal = true;
                            }
                            ui.add_space(4.0);
                        }
                    }

                    if self.modal_show_new_playlist {
                        ui.add_space(6.0);
                        ui.add(
                            egui::TextEdit::singleline(&mut self.modal_new_playlist_name)
                                .hint_text("Playlist name..."),
                        );
                    }

                    ui.add_space(10.0);
                    ui.horizontal(|ui| {
                        if self.modal_show_new_playlist {
                            if primary_action_button(ui, "Create & Add", &palette).clicked()
                                && !self.modal_new_playlist_name.trim().is_empty()
                            {
                                let name = self.modal_new_playlist_name.clone();
                                self.state.create_playlist(&name, vec![target_song.id]);
                                self.modal_new_playlist_name.clear();
                                self.modal_show_new_playlist = false;
                                close_modal = true;
                            }
                        } else if primary_action_button(ui, "New Playlist", &palette).clicked() {
                            self.modal_show_new_playlist = true;
                        }

                        if pill_button(ui, "Cancel", false, &palette).clicked() {
                            self.modal_show_new_playlist = false;
                            close_modal = true;
                        }
                    });
                });

            if close_modal {
                self.state.song_to_add_to_playlist = None;
            }
        }

        // Global Modal: Track Details Sheet
        if let Some(info_song) = self.state.song_info_target.clone() {
            let mut close_info = false;
            egui::Window::new("Track Details")
                .collapsible(false)
                .resizable(false)
                .anchor(egui::Align2::CENTER_CENTER, Vec2::ZERO)
                .default_width(320.0)
                .max_width(320.0)
                .show(ctx, |ui| {
                    info_row(ui, "Title", &info_song.title, &palette);
                    info_row(ui, "Artist", &info_song.artist, &palette);
                    info_row(ui, "Album", &info_song.album, &palette);
                    info_row(ui, "Duration", &info_song.duration_formatted(), &palette);
                    info_row(ui, "Format", &info_song.format_label(), &palette);
                    info_row(ui, "Genre", &info_song.genre, &palette);
                    info_row(
                        ui,
                        "Year",
                        &if info_song.year > 0 {
                            info_song.year.to_string()
                        } else {
                            "—".to_string()
                        },
                        &palette,
                    );
                    info_row(ui, "Size", &format_file_size(info_song.size_bytes), &palette);
                    info_row(ui, "Folder", &info_song.folder_name(), &palette);
                    ui.add_space(10.0);
                    if primary_action_button(ui, "Close", &palette).clicked() {
                        close_info = true;
                    }
                });
            if close_info {
                self.state.song_info_target = None;
            }
        }
    }
}

fn info_row(ui: &mut egui::Ui, label: &str, value: &str, palette: &StudioPalette) {
    ui.horizontal(|ui| {
        ui.label(
            RichText::new(format!("{}:", label))
                .size(13.0)
                .strong()
                .color(palette.text_secondary),
        );
        ui.label(
            RichText::new(value)
                .size(13.0)
                .color(palette.text_primary),
        );
    });
}
