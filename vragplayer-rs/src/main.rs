use eframe::egui;
use vragplayer_rs::{storage, ui::VragApp};

fn main() -> eframe::Result<()> {
    let data_dir = storage::default_data_dir();
    let options = eframe::NativeOptions {
        viewport: egui::ViewportBuilder::default()
            .with_title("VragPlayer")
            .with_inner_size([430.0, 860.0])
            .with_min_inner_size([360.0, 620.0]),
        ..Default::default()
    };

    eframe::run_native(
        "VragPlayer",
        options,
        Box::new(move |cc| Ok(Box::new(VragApp::new(cc, data_dir)))),
    )
}
