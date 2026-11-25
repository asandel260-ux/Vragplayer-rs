use crate::library;
use crate::model::SortType;
use crate::state::{AppState, LibraryTab, Screen};
use crate::ui::screens_home::handle_song_action;
use crate::ui::theme::{
    card_frame, elevated_frame, pill_button, primary_action_button, render_screen_top_bar,
    render_section_header, render_song_row, secondary_action_button, StudioPalette,
};
use eframe::egui::{self, CornerRadius, RichText, ScrollArea, Stroke, Ui};

#[allow(clippy::too_many_arguments)]
pub fn render_library_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    time_sec: f64,
    liked_only: bool,
    new_playlist_draft: &mut String,
    rename_draft: &mut Option<(String, String)>,
) {
    ui.add_space(6.0);

    // Sub-view: Selected Playlist Detail
    if !liked_only {
        if let Some(pid) = state.selected_playlist_id.clone() {
            render_playlist_detail(ui, state, &pid, palette, time_sec, rename_draft);
            return;
        }
        if let Some((col_title, col_songs)) = state.selected_collection.clone() {
            render_collection_detail(ui, state, &col_title, &col_songs, palette, time_sec);
            return;
        }
    }

    // Header
    if liked_only {
        let mut back = false;
        render_screen_top_bar(ui, "Favorites", Some(&mut back), palette);
        if back {
            state.navigate_back();
            return;
        }
    } else {
        ui.horizontal(|ui| {
            ui.label(
                RichText::new("Library")
                    .size(24.0)
                    .strong()
                    .color(palette.text_primary),
            );
            ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                if ui
                    .add(
                        egui::Button::new(
                            RichText::new("Rescan")
                                .size(13.0)
                                .strong()
                                .color(palette.text_primary),
                        )
                        .fill(palette.surface_elevated)
                        .stroke(Stroke::new(1.0_f32, palette.border))
                        .corner_radius(CornerRadius::same(12))
                        .min_size(egui::vec2(72.0, 36.0)),
                    )
                    .clicked()
                {
                    state.load_media(true);
                    state.show_message(format!("Scanned {} tracks", state.songs.len()));
                }
            });
        });
    }

    ui.add_space(8.0);

    let show_covers = state.persisted.settings.show_covers;

    if liked_only {
        let favs = state.liked_song_list();
        ui.horizontal(|ui| {
            if primary_action_button(ui, "Shuffle Favorites", palette).clicked() && !favs.is_empty()
            {
                state.play_all_shuffled(&favs, "Favorites");
                state.navigate_to(Screen::Player);
            }
            ui.label(
                RichText::new(format!("{} songs", favs.len()))
                    .size(13.0)
                    .color(palette.text_secondary),
            );
        });
        ui.add_space(10.0);

        ScrollArea::vertical().show(ui, |ui| {
            if favs.is_empty() {
                card_frame(palette, 16, 16, 20).show(ui, |ui| {
                    ui.vertical_centered(|ui| {
                        ui.label(
                            RichText::new("No favorites yet")
                                .size(16.0)
                                .strong()
                                .color(palette.text_primary),
                        );
                        ui.label(
                            RichText::new("Tap the heart icon on any track to save it here.")
                                .size(13.0)
                                .color(palette.text_secondary),
                        );
                    });
                });
            } else {
                for song in &favs {
                    let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
                    let act = render_song_row(
                        ui,
                        song,
                        is_cur,
                        state.is_playing,
                        true,
                        show_covers,
                        false,
                        time_sec,
                        palette,
                    );
                    handle_song_action(state, song.clone(), &favs, "Favorites", None, act);
                    ui.add_space(6.0);
                }
            }
            ui.add_space(16.0);
        });
        return;
    }

    // Segmented Library Tabs
    ScrollArea::horizontal().show(ui, |ui| {
        ui.horizontal(|ui| {
            for tab in LibraryTab::ALL {
                let selected = state.library_tab == tab;
                if pill_button(ui, tab.label(), selected, palette).clicked() {
                    state.library_tab = tab;
                }
            }
        });
    });

    ui.add_space(10.0);

    match state.library_tab {
        LibraryTab::Songs => {
            // Sort & Shuffle bar
            ui.horizontal_wrapped(|ui| {
                let songs_snapshot = state.songs.clone();
                if primary_action_button(ui, "Shuffle All", palette).clicked()
                    && !songs_snapshot.is_empty()
                {
                    state.play_all_shuffled(&songs_snapshot, "Library");
                    state.navigate_to(Screen::Player);
                }
                for st in SortType::ALL {
                    let selected = state.sort_type == st;
                    let label = if selected {
                        format!("{} {}", st.label(), if state.sort_ascending { "↑" } else { "↓" })
                    } else {
                        st.label().to_string()
                    };
                    if pill_button(ui, &label, selected, palette).clicked() {
                        if selected {
                            state.set_sorting(st, !state.sort_ascending);
                        } else {
                            state.set_sorting(st, true);
                        }
                    }
                }
            });

            ui.add_space(10.0);
            let songs_snapshot = state.songs.clone();
            ScrollArea::vertical().show(ui, |ui| {
                for song in &songs_snapshot {
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
                    handle_song_action(state, song.clone(), &songs_snapshot, "Library", None, act);
                    ui.add_space(6.0);
                }
                ui.add_space(16.0);
            });
        }
        LibraryTab::Playlists => {
            // Create Playlist Bar
            card_frame(palette, 16, 12, 10).show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.add(
                        egui::TextEdit::singleline(new_playlist_draft)
                            .hint_text("New playlist name...")
                            .desired_width((ui.available_width() - 104.0).max(100.0)),
                    );
                    if ui
                        .add_enabled(
                            !new_playlist_draft.trim().is_empty(),
                            egui::Button::new(
                                RichText::new("Create")
                                    .size(13.5)
                                    .strong()
                                    .color(palette.on_accent),
                            )
                            .fill(palette.accent)
                            .corner_radius(CornerRadius::same(12))
                            .min_size(egui::vec2(86.0, 36.0)),
                        )
                        .clicked()
                    {
                        let name = new_playlist_draft.clone();
                        state.create_playlist(&name, Vec::new());
                        new_playlist_draft.clear();
                    }
                });
            });

            ui.add_space(10.0);
            let playlists = state.persisted.playlists.clone();
            ScrollArea::vertical().show(ui, |ui| {
                if playlists.is_empty() {
                    card_frame(palette, 16, 16, 20).show(ui, |ui| {
                        ui.vertical_centered(|ui| {
                            ui.label(
                                RichText::new("No playlists yet")
                                    .size(16.0)
                                    .strong()
                                    .color(palette.text_primary),
                            );
                            ui.label(
                                RichText::new(
                                    "Create a playlist above or add songs from any track menu.",
                                )
                                .size(13.0)
                                .color(palette.text_secondary),
                            );
                        });
                    });
                } else {
                    for pl in &playlists {
                        card_frame(palette, 16, 14, 12).show(ui, |ui| {
                            ui.set_min_width(ui.available_width());
                            ui.horizontal(|ui| {
                                ui.vertical(|ui| {
                                    ui.label(
                                        RichText::new(&pl.name)
                                            .size(16.0)
                                            .strong()
                                            .color(palette.text_primary),
                                    );
                                    ui.label(
                                        RichText::new(format!("{} songs", pl.song_ids.len()))
                                            .size(12.5)
                                            .color(palette.text_secondary),
                                    );
                                });
                                ui.with_layout(
                                    egui::Layout::right_to_left(egui::Align::Center),
                                    |ui| {
                                        if pill_button(ui, "Open", false, palette).clicked() {
                                            state.selected_playlist_id = Some(pl.id.clone());
                                        }
                                        if pill_button(ui, "Play", true, palette).clicked() {
                                            state.play_playlist_sequential(&pl.id);
                                            state.navigate_to(Screen::Player);
                                        }
                                    },
                                );
                            });
                        });
                        ui.add_space(8.0);
                    }
                }
                ui.add_space(16.0);
            });
        }
        LibraryTab::Albums => {
            let groups = library::group_by_album(&state.songs);
            render_collection_list(ui, state, groups, "Album", palette);
        }
        LibraryTab::Artists => {
            let groups = library::group_by_artist(&state.songs);
            render_collection_list(ui, state, groups, "Artist", palette);
        }
        LibraryTab::Folders => {
            let groups = library::group_by_folder(&state.songs);
            render_collection_list(ui, state, groups, "Folder", palette);
        }
    }
}

fn render_collection_list(
    ui: &mut Ui,
    state: &mut AppState,
    groups: Vec<(String, Vec<crate::model::Song>)>,
    kind: &str,
    palette: &StudioPalette,
) {
    ScrollArea::vertical().show(ui, |ui| {
        for (name, tracks) in groups {
            card_frame(palette, 16, 14, 12).show(ui, |ui| {
                ui.set_min_width(ui.available_width());
                ui.horizontal(|ui| {
                    ui.vertical(|ui| {
                        ui.label(
                            RichText::new(&name)
                                .size(15.5)
                                .strong()
                                .color(palette.text_primary),
                        );
                        ui.label(
                            RichText::new(format!("{} • {} songs", kind, tracks.len()))
                                .size(12.5)
                                .color(palette.text_secondary),
                        );
                    });
                    ui.with_layout(egui::Layout::right_to_left(egui::Align::Center), |ui| {
                        if pill_button(ui, "Open", false, palette).clicked() {
                            state.selected_collection = Some((name.clone(), tracks.clone()));
                        }
                        if pill_button(ui, "Play", true, palette).clicked() {
                            if let Some(first) = tracks.first().cloned() {
                                state.play_from_list(first, &tracks, &name, None, false);
                                state.navigate_to(Screen::Player);
                            }
                        }
                    });
                });
            });
            ui.add_space(8.0);
        }
        ui.add_space(16.0);
    });
}

fn render_collection_detail(
    ui: &mut Ui,
    state: &mut AppState,
    title: &str,
    tracks: &[crate::model::Song],
    palette: &StudioPalette,
    time_sec: f64,
) {
    let mut back = false;
    render_screen_top_bar(ui, title, Some(&mut back), palette);
    if back {
        state.selected_collection = None;
        return;
    }

    ui.add_space(8.0);
    ui.horizontal(|ui| {
        if primary_action_button(ui, "Play All", palette).clicked() {
            if let Some(first) = tracks.first().cloned() {
                state.play_from_list(first, tracks, title, None, false);
                state.navigate_to(Screen::Player);
            }
        }
        if secondary_action_button(ui, "Shuffle", palette).clicked() && !tracks.is_empty() {
            state.play_all_shuffled(tracks, title);
            state.navigate_to(Screen::Player);
        }
    });
    ui.add_space(10.0);

    let show_covers = state.persisted.settings.show_covers;
    ScrollArea::vertical().show(ui, |ui| {
        for song in tracks {
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
            handle_song_action(state, song.clone(), tracks, title, None, act);
            ui.add_space(6.0);
        }
        ui.add_space(16.0);
    });
}

fn render_playlist_detail(
    ui: &mut Ui,
    state: &mut AppState,
    playlist_id: &str,
    palette: &StudioPalette,
    time_sec: f64,
    rename_draft: &mut Option<(String, String)>,
) {
    let Some(pl) = state
        .persisted
        .playlists
        .iter()
        .find(|p| p.id == playlist_id)
        .cloned()
    else {
        state.selected_playlist_id = None;
        return;
    };

    let mut back = false;
    render_screen_top_bar(ui, &pl.name, Some(&mut back), palette);
    if back {
        state.selected_playlist_id = None;
        *rename_draft = None;
        return;
    }

    let tracks = state.resolve_playlist_songs(&pl);
    ui.add_space(8.0);

    let mut deleted = false;
    ui.horizontal_wrapped(|ui| {
        if primary_action_button(ui, "Play Sequential", palette).clicked() {
            state.play_playlist_sequential(&pl.id);
            if !tracks.is_empty() {
                state.navigate_to(Screen::Player);
            }
        }
        if secondary_action_button(ui, "Play Random", palette).clicked() {
            state.play_playlist_random(&pl.id);
            if !tracks.is_empty() {
                state.navigate_to(Screen::Player);
            }
        }
        if pill_button(ui, "Rename", false, palette).clicked() {
            *rename_draft = Some((pl.id.clone(), pl.name.clone()));
        }
        if pill_button(ui, "Clear", false, palette).clicked() {
            state.clear_playlist(&pl.id);
        }
        if pill_button(ui, "Delete", false, palette).clicked() {
            state.delete_playlist(&pl.id);
            deleted = true;
        }
    });
    if deleted {
        return;
    }

    if let Some((target_id, draft_name)) = rename_draft {
        if target_id == &pl.id {
            ui.add_space(8.0);
            let mut commit = false;
            let mut cancel = false;
            elevated_frame(palette, 14, 12, 8).show(ui, |ui| {
                ui.horizontal(|ui| {
                    ui.text_edit_singleline(draft_name);
                    if pill_button(ui, "Save", true, palette).clicked() {
                        commit = true;
                    }
                    if pill_button(ui, "Cancel", false, palette).clicked() {
                        cancel = true;
                    }
                });
            });
            if commit {
                let new_name = draft_name.clone();
                state.rename_playlist(&pl.id, &new_name);
                *rename_draft = None;
            } else if cancel {
                *rename_draft = None;
            }
        }
    }

    ui.add_space(10.0);
    let show_covers = state.persisted.settings.show_covers;

    ScrollArea::vertical().show(ui, |ui| {
        if tracks.is_empty() {
            card_frame(palette, 16, 16, 20).show(ui, |ui| {
                ui.vertical_centered(|ui| {
                    ui.label(
                        RichText::new("Playlist is empty")
                            .size(16.0)
                            .strong()
                            .color(palette.text_primary),
                    );
                    ui.label(
                        RichText::new("Use the '...' menu on any song to add it to this playlist.")
                            .size(13.0)
                            .color(palette.text_secondary),
                    );
                });
            });
        } else {
            for song in &tracks {
                let is_cur = state.current_song.as_ref().map(|c| c.id) == Some(song.id);
                let is_liked = state.is_liked(song.id);
                let act = render_song_row(
                    ui,
                    song,
                    is_cur,
                    state.is_playing,
                    is_liked,
                    show_covers,
                    true,
                    time_sec,
                    palette,
                );
                handle_song_action(
                    state,
                    song.clone(),
                    &tracks,
                    &pl.name,
                    Some(pl.id.clone()),
                    act,
                );
                ui.add_space(6.0);
            }
        }
        ui.add_space(16.0);
    });
}

pub fn render_queue_screen(
    ui: &mut Ui,
    state: &mut AppState,
    palette: &StudioPalette,
    _time_sec: f64,
    save_queue_name: &mut String,
) {
    ui.add_space(6.0);
    let mut back = false;
    render_screen_top_bar(ui, "Playback Queue", Some(&mut back), palette);
    if back {
        state.navigate_back();
        return;
    }

    ui.add_space(6.0);
    render_section_header(
        ui,
        &format!("Source: {}", state.active_queue_source),
        Some(&format!("{} tracks", state.queue.len())),
        palette,
    );
    ui.add_space(8.0);

    // Save queue as playlist & Clear upcoming
    card_frame(palette, 16, 12, 10).show(ui, |ui| {
        ui.horizontal_wrapped(|ui| {
            ui.add(
                egui::TextEdit::singleline(save_queue_name)
                    .hint_text("Save queue as playlist...")
                    .desired_width(170.0),
            );
            if pill_button(ui, "Save", true, palette).clicked()
                && !save_queue_name.trim().is_empty()
            {
                let name = save_queue_name.clone();
                state.save_queue_as_playlist(&name);
                save_queue_name.clear();
            }
            if pill_button(ui, "Clear Upcoming", false, palette).clicked() {
                state.clear_upcoming_queue();
            }
        });
    });

    ui.add_space(10.0);

    let queue_len = state.queue.len();
    let queue_snapshot = state.queue.clone();
    let current_id = state.current_song.as_ref().map(|s| s.id);

    let mut action_play: Option<usize> = None;
    let mut action_move: Option<(usize, usize)> = None;
    let mut action_remove: Option<usize> = None;

    ScrollArea::vertical().show(ui, |ui| {
        if queue_snapshot.is_empty() {
            card_frame(palette, 16, 16, 20).show(ui, |ui| {
                ui.vertical_centered(|ui| {
                    ui.label(
                        RichText::new("Queue is empty")
                            .size(16.0)
                            .strong()
                            .color(palette.text_primary),
                    );
                });
            });
        } else {
            for (idx, song) in queue_snapshot.iter().enumerate() {
                let is_cur = current_id == Some(song.id);
                let fill = if is_cur {
                    palette.accent_soft
                } else {
                    palette.surface
                };
                egui::Frame::new()
                    .fill(fill)
                    .stroke(Stroke::new(
                        1.0_f32,
                        if is_cur { palette.accent } else { palette.border },
                    ))
                    .corner_radius(CornerRadius::same(14))
                    .inner_margin(egui::Margin::symmetric(12, 8))
                    .show(ui, |ui| {
                        ui.set_min_width(ui.available_width());
                        ui.horizontal(|ui| {
                            ui.label(
                                RichText::new(format!("{:02}", idx + 1))
                                    .size(12.5)
                                    .color(palette.text_secondary),
                            );
                            if ui
                                .add(
                                    egui::Button::new(
                                        RichText::new(format!("{} — {}", song.title, song.artist))
                                            .size(14.0)
                                            .strong()
                                            .color(if is_cur {
                                                palette.accent
                                            } else {
                                                palette.text_primary
                                            }),
                                    )
                                    .frame(false),
                                )
                                .clicked()
                            {
                                action_play = Some(idx);
                            }

                            ui.with_layout(
                                egui::Layout::right_to_left(egui::Align::Center),
                                |ui| {
                                    if ui
                                        .add(
                                            egui::Button::new(
                                                RichText::new("x")
                                                    .size(13.0)
                                                    .color(palette.text_secondary),
                                            )
                                            .frame(false)
                                            .min_size(egui::vec2(28.0, 28.0)),
                                        )
                                        .clicked()
                                    {
                                        action_remove = Some(idx);
                                    }
                                    if idx + 1 < queue_len
                                        && ui
                                            .add(
                                                egui::Button::new(
                                                    RichText::new("↓")
                                                        .size(14.0)
                                                        .color(palette.text_primary),
                                                )
                                                .frame(false)
                                                .min_size(egui::vec2(28.0, 28.0)),
                                            )
                                            .clicked()
                                    {
                                        action_move = Some((idx, idx + 1));
                                    }
                                    if idx > 0
                                        && ui
                                            .add(
                                                egui::Button::new(
                                                    RichText::new("↑")
                                                        .size(14.0)
                                                        .color(palette.text_primary),
                                                )
                                                .frame(false)
                                                .min_size(egui::vec2(28.0, 28.0)),
                                            )
                                            .clicked()
                                    {
                                        action_move = Some((idx, idx - 1));
                                    }
                                },
                            );
                        });
                    });
                ui.add_space(6.0);
            }
        }
        ui.add_space(16.0);
    });

    if let Some(idx) = action_play {
        state.play_song_at_index(idx);
    } else if let Some((from, to)) = action_move {
        state.move_queue_item(from, to);
    } else if let Some(idx) = action_remove {
        state.remove_queue_item(idx);
    }
}
