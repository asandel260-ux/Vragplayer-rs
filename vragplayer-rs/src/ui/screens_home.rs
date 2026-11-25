use crate::library;
use crate::state::{AppState, LibraryTab, Screen};
use crate::ui::theme::{
    card_frame, elevated_frame, paint_track_artwork, pill_button, primary_action_button,
    render_section_header, render_song_row, secondary_action_button, SongRowAction, StudioPalette,
};
use eframe::egui::{self, CornerRadius, RichText, ScrollArea, Sense, Stroke, Ui, Vec2};

pub fn render_home_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
) {
    ScrollArea::vertical().show(ui, |ui| {
        ui.add_space(6.0);

        // Top Greeting Header
        ui.horizontal(|ui| {
            ui.vertical(|ui| {
                ui.label(
                    RichText::new("VragPlayer Studio")
                        .size(12.5)
                        .strong()
                        .color(palette.accent),
                );
                ui.label(
                    RichText::new(&state.persisted.settings.username)
                        .size(26.0)
                        .strong()
                        .color(palette.text_primary),
                );
            });
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if ui
                    .add(
                        egui::Button::new(
                            RichText::new("Settings")
                                .size(13.0)
                                .strong()
                                .color(palette.text_primary),
                        )
                        .fill(palette.surface_elevated)
                        .stroke(Stroke::new(1.0_f32, palette.border))
                        .corner_radius(CornerRadius::same(14))
                        .min_size(egui::vec2(84.0, 38.0)),
                    )
                    .clicked()
                {
                    state.navigate_to(Screen::Settings);
                }

                if ui
                    .add(
                        egui::Button::new(
                            RichText::new("EQ")
                                .size(13.0)
                                .strong()
                                .color(if state.persisted.settings.eq_enabled {
                                    palette.on_accent
                                } else {
                                    palette.text_primary
                                }),
                        )
                        .fill(if state.persisted.settings.eq_enabled {
                            palette.accent
                        } else {
                            palette.surface_elevated
                        })
                        .stroke(Stroke::new(1.0_f32, palette.border))
                        .corner_radius(CornerRadius::same(14))
                        .min_size(egui::vec2(52.0, 38.0)),
                    )
                    .clicked()
                {
                    state.navigate_to(Screen::Equalizer);
                }
            });
        });

        ui.add_space(12.0);

        // Hero Studio Card
        let current_song = state.current_song.clone();
        let all_songs = state.songs.clone();
        let show_covers = state.persisted.settings.show_covers;

        card_frame(palette, 20, 16, 16).show(ui, |ui| {
            ui.set_min_width(ui.available_width());
            ui.horizontal(|ui| {
                let hero_song = current_song.as_ref().or_else(|| all_songs.first());
                let art_resp = paint_track_artwork(
                    ui,
                    hero_song,
                    82.0,
                    16,
                    show_covers,
                    state.is_playing,
                    time_sec,
                    palette,
                );
                if art_resp.clicked() && current_song.is_some() {
                    state.navigate_to(Screen::Player);
                }

                ui.add_space(10.0);
                ui.vertical(|ui| {
                    let badge = if state.is_playing {
                        "NOW PLAYING"
                    } else if current_song.is_some() {
                        "PAUSED"
                    } else {
                        "READY TO LISTEN"
                    };
                    ui.label(
                        RichText::new(badge)
                            .size(11.0)
                            .strong()
                            .color(palette.accent),
                    );
                    let title = hero_song
                        .map(|s| s.title.as_str())
                        .unwrap_or("Your Local Studio");
                    let subtitle = hero_song
                        .map(|s| format!("{} • {}", s.artist, s.album))
                        .unwrap_or_else(|| format!("{} tracks ready offline", all_songs.len()));
                    ui.label(
                        RichText::new(title)
                            .size(19.0)
                            .strong()
                            .color(palette.text_primary),
                    );
                    ui.label(
                        RichText::new(subtitle)
                            .size(13.0)
                            .color(palette.text_secondary),
                    );
                    ui.add_space(8.0);
                    ui.horizontal_wrapped(|ui| {
                        let play_label = if state.is_playing {
                            "Open Player"
                        } else {
                            "Shuffle Play"
                        };
                        if primary_action_button(ui, play_label, palette).clicked() {
                            if state.is_playing && current_song.is_some() {
                                state.navigate_to(Screen::Player);
                            } else if !all_songs.is_empty() {
                                state.play_all_shuffled(&all_songs, "Shuffled");
                                state.navigate_to(Screen::Player);
                            }
                        }
                        if secondary_action_button(ui, "Queue", palette).clicked() {
                            state.navigate_to(Screen::Queue);
                        }
                    });
                });
            });
        });

        ui.add_space(14.0);

        // Quick Access Tiles
        render_section_header(ui, "Quick Access", None, palette);
        ui.add_space(6.0);
        ui.horizontal_wrapped(|ui| {
            let liked_count = state.persisted.liked_songs.len();
            let playlist_count = state.persisted.playlists.len();
            let sleep_label = if state.sleep_timer_end.is_some() || state.sleep_after_current_track {
                "Sleep: Active".to_string()
            } else {
                "Sleep Timer".to_string()
            };

            if quick_tile(ui, "Favorites", &format!("{} tracks", liked_count), palette).clicked() {
                state.navigate_to(Screen::Liked);
            }
            if quick_tile(
                ui,
                "Playlists",
                &format!("{} lists", playlist_count),
                palette,
            )
            .clicked()
            {
                state.library_tab = LibraryTab::Playlists;
                state.navigate_top_tab(Screen::Library);
            }
            if quick_tile(
                ui,
                "Equalizer",
                state.persisted.settings.eq_preset.label(),
                palette,
            )
            .clicked()
            {
                state.navigate_to(Screen::Equalizer);
            }
            if quick_tile(ui, "Timer", &sleep_label, palette).clicked() {
                state.navigate_to(Screen::SleepTimer);
            }
        });

        ui.add_space(16.0);

        // Recently Played Horizontal Strip
        let recents = state.recently_played_songs();
        if !recents.is_empty() {
            render_section_header(
                ui,
                "Recently Played",
                Some(&format!("{} tracks", recents.len())),
                palette,
            );
            ui.add_space(8.0);
            ScrollArea::horizontal().show(ui, |ui| {
                ui.horizontal(|ui| {
                    for song in &recents {
                        let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
                        elevated_frame(palette, 16, 10, 10).show(ui, |ui| {
                            ui.set_width(132.0);
                            let resp = ui
                                .vertical(|ui| {
                                    let art = paint_track_artwork(
                                        ui,
                                        Some(song),
                                        112.0,
                                        12,
                                        show_covers,
                                        is_cur && state.is_playing,
                                        time_sec,
                                        palette,
                                    );
                                    ui.add_space(6.0);
                                    ui.add(
                                        egui::Label::new(
                                            RichText::new(&song.title)
                                                .size(13.5)
                                                .strong()
                                                .color(if is_cur {
                                                    palette.accent
                                                } else {
                                                    palette.text_primary
                                                }),
                                        )
                                        .truncate(),
                                    );
                                    ui.add(
                                        egui::Label::new(
                                            RichText::new(&song.artist)
                                                .size(11.5)
                                                .color(palette.text_secondary),
                                        )
                                        .truncate(),
                                    );
                                    art
                                })
                                .inner;
                            if resp.clicked() {
                                state.play_from_list(
                                    song.clone(),
                                    &recents,
                                    "Recently Played",
                                    None,
                                    false,
                                );
                                state.navigate_to(Screen::Player);
                            }
                        });
                        ui.add_space(6.0);
                    }
                });
            });
            ui.add_space(16.0);
        }

        // Library Highlights
        render_section_header(
            ui,
            "Your Tracks",
            Some(&format!("{} total", all_songs.len())),
            palette,
        );
        ui.add_space(8.0);

        for song in all_songs.iter().take(12) {
            let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
            let is_liked = state.is_liked(song.id);
            let act = render_song_row(
                ui,
                song,
                is_cur,
                state.is_playing,
                is_liked,
                show_covers,
                false,
                time_sec,
                palette,
            );
            handle_song_action(state, song.clone(), &all_songs, "Library", None, act);
            ui.add_space(6.0);
        }

        ui.add_space(16.0);
    });
}

pub fn render_search_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
) {
    ui.add_space(6.0);
    ui.label(
        RichText::new("Search")
            .size(24.0)
            .strong()
            .color(palette.text_primary),
    );
    ui.add_space(8.0);

    // Search Input Row
    card_frame(palette, 16, 12, 8).show(ui, |ui| {
        ui.horizontal(|ui| {
            let resp = ui.add(
                egui::TextEdit::singleline(&mut state.search_query)
                    .hint_text("Search songs, artists, albums, genres...")
                    .desired_width((ui.available_width() - 68.0).max(100.0))
                    .frame(false),
            );
            if resp.lost_focus() && ui.input(|i| i.key_pressed(egui::Key::Enter)) {
                let q = state.search_query.clone();
                state.add_recent_search(&q);
            }
            if !state.search_query.is_empty()
                && ui
                    .add(
                        egui::Button::new(RichText::new("Clear").size(12.5).color(palette.accent))
                            .frame(false),
                    )
                    .clicked()
            {
                state.search_query.clear();
            }
        });
    });

    ui.add_space(12.0);
    let show_covers = state.persisted.settings.show_covers;

    ScrollArea::vertical().show(ui, |ui| {
        if state.search_query.trim().is_empty() {
            // Recent Searches
            let recents = state.persisted.recent_searches.clone();
            if !recents.is_empty() {
                ui.horizontal(|ui| {
                    ui.label(
                        RichText::new("Recent Searches")
                            .size(16.0)
                            .strong()
                            .color(palette.text_primary),
                    );
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if ui
                            .button(RichText::new("Clear all").size(12.0).color(palette.accent))
                            .clicked()
                        {
                            state.clear_recent_searches();
                        }
                    });
                });
                ui.add_space(6.0);
                ui.horizontal_wrapped(|ui| {
                    for term in &recents {
                        if pill_button(ui, term, false, palette).clicked() {
                            state.search_query = term.clone();
                        }
                    }
                });
                ui.add_space(14.0);
            }

            // Browse Genres
            let mut genres: Vec<String> = state
                .songs
                .iter()
                .map(|s| s.genre.clone())
                .collect();
            genres.sort();
            genres.dedup();
            if !genres.is_empty() {
                render_section_header(ui, "Browse Genres", None, palette);
                ui.add_space(8.0);
                ui.horizontal_wrapped(|ui| {
                    for g in &genres {
                        if pill_button(ui, g, false, palette).clicked() {
                            state.search_query = g.clone();
                        }
                    }
                });
            }
        } else {
            let results = library::search_songs(&state.songs, &state.search_query);
            render_section_header(
                ui,
                "Results",
                Some(&format!("{} matches", results.len())),
                palette,
            );
            ui.add_space(8.0);

            if results.is_empty() {
                card_frame(palette, 16, 16, 20).show(ui, |ui| {
                    ui.vertical_centered(|ui| {
                        ui.label(
                            RichText::new("No matching tracks")
                                .size(16.0)
                                .strong()
                                .color(palette.text_primary),
                        );
                        ui.label(
                            RichText::new("Try searching by artist, album, or genre.")
                                .size(13.0)
                                .color(palette.text_secondary),
                        );
                    });
                });
            } else {
                let query_snapshot = state.search_query.clone();
                for song in &results {
                    let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
                    let is_liked = state.is_liked(song.id);
                    let act = render_song_row(
                        ui,
                        song,
                        is_cur,
                        state.is_playing,
                        is_liked,
                        show_covers,
                        false,
                        time_sec,
                        palette,
                    );
                    if act == SongRowAction::Play {
                        state.add_recent_search(&query_snapshot);
                        state.register_search_played(song.id);
                    }
                    handle_song_action(state, song.clone(), &results, "Search", None, act);
                    ui.add_space(6.0);
                }
            }
        }
        ui.add_space(16.0);
    });
}

fn quick_tile(ui: &mut Ui, title: &str, subtitle: &str, palette: &StudioPalette) -> egui::Response {
    let desired = Vec2::new(146.0, 62.0);
    let (rect, response) = ui.allocate_exact_size(desired, Sense::click());
    let painter = ui.painter_at(rect);

    let fill = if response.hovered() {
        palette.accent_soft
    } else {
        palette.surface
    };
    painter.rect_filled(rect, CornerRadius::same(16), fill);
    painter.rect_stroke(
        rect,
        CornerRadius::same(16),
        Stroke::new(1.0_f32, palette.border),
        egui::StrokeKind::Inside,
    );

    painter.text(
        egui::pos2(rect.left() + 14.0, rect.top() + 21.0),
        egui::Align2::LEFT_CENTER,
        title,
        egui::FontId::proportional(14.5),
        palette.text_primary,
    );
    painter.text(
        egui::pos2(rect.left() + 14.0, rect.top() + 43.0),
        egui::Align2::LEFT_CENTER,
        subtitle,
        egui::FontId::proportional(12.0),
        palette.text_secondary,
    );

    response
}

pub fn handle_song_action(
    state: &mut AppState,
    song: crate::model::Song,
    source_list: &[crate::model::Song],
    source_label: &str,
    playlist_id: Option<String>,
    action: SongRowAction,
) {
    match action {
        SongRowAction::None => {}
        SongRowAction::Play => {
            state.play_from_list(song, source_list, source_label, playlist_id, false);
            state.navigate_to(Screen::Player);
        }
        SongRowAction::ToggleLike => {
            state.toggle_like(song.id);
        }
        SongRowAction::PlayNext => {
            state.play_next_in_queue(song);
        }
        SongRowAction::AddToQueue => {
            state.add_to_queue(song);
        }
        SongRowAction::AddToPlaylist => {
            state.song_to_add_to_playlist = Some(song);
        }
        SongRowAction::ShowInfo => {
            state.song_info_target = Some(song);
        }
        SongRowAction::RemoveFromContext => {
            if let Some(pid) = playlist_id {
                state.remove_song_from_playlist(&pid, song.id);
            }
        }
    }
}
