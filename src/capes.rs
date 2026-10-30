use base64::Engine;
use image::codecs::gif::GifDecoder;
use image::codecs::png::PngEncoder;
use image::AnimationDecoder;
use serde::{Deserialize, Serialize};
use std::io::Cursor;
use std::path::{Path, PathBuf};
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct CapeEntry {
    pub id: String,
    pub name: String,
    pub creator: String,
    pub category: String, // "community", "my_capes", "vanilla", "gekauft"
    pub uses: u32,
    #[serde(default)]
    pub favorite: bool,
    #[serde(default)]
    pub animated: bool,
    #[serde(default = "default_fps")]
    pub fps: u32,
    #[serde(default)]
    pub frames: Vec<String>,
    pub preview: String,
}

fn default_fps() -> u32 {
    10
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct CapeStoreData {
    pub active_cape: Option<String>,
    pub capes: Vec<CapeEntry>,
    #[serde(default)]
    pub favorites: Vec<String>,
}

fn capes_dir(data_dir: &Path) -> PathBuf {
    data_dir.join("capes")
}

fn capes_json_file(data_dir: &Path) -> PathBuf {
    capes_dir(data_dir).join("capes.json")
}

pub fn load_capes(data_dir: &Path) -> CapeStoreData {
    let p = capes_json_file(data_dir);
    if let Ok(s) = std::fs::read_to_string(&p) {
        if let Ok(mut data) = serde_json::from_str::<CapeStoreData>(&s) {
            let before = data.capes.len();
            data.capes.retain(|c| !c.id.starts_with("comm_"));
            for c in &mut data.capes {
                if data.favorites.contains(&c.id) {
                    c.favorite = true;
                }
            }
            if data.capes.len() != before {
                save_capes(data_dir, &data);
            }
            return data;
        }
    }
    let store = CapeStoreData {
        active_cape: None,
        capes: Vec::new(),
        favorites: Vec::new(),
    };
    save_capes(data_dir, &store);
    store
}

pub fn save_capes(data_dir: &Path, data: &CapeStoreData) {
    let dir = capes_dir(data_dir);
    let _ = std::fs::create_dir_all(&dir);
    if let Ok(s) = serde_json::to_string_pretty(data) {
        let _ = std::fs::write(capes_json_file(data_dir), s);
    }
}

#[allow(dead_code)]
pub fn get_active_cape(data_dir: &Path) -> Option<CapeEntry> {
    let data = load_capes(data_dir);
    let active_id = data.active_cape.as_ref()?;
    data.capes.into_iter().find(|c| &c.id == active_id)
}

pub fn set_active_cape(data_dir: &Path, cape_id: Option<String>) -> Result<(), String> {
    let mut data = load_capes(data_dir);
    data.active_cape = cape_id.clone();
    save_capes(data_dir, &data);

    // Sync active cape to instance config so in-game mod reads it immediately
    sync_active_cape_to_instances(data_dir, &data, cape_id.as_deref());
    Ok(())
}

pub fn toggle_favorite(data_dir: &Path, cape_id: &str) -> bool {
    let mut data = load_capes(data_dir);
    let mut new_state = false;
    if let Some(pos) = data.favorites.iter().position(|f| f == cape_id) {
        data.favorites.remove(pos);
        new_state = false;
    } else {
        data.favorites.push(cape_id.to_string());
        new_state = true;
    }
    if let Some(c) = data.capes.iter_mut().find(|c| c.id == cape_id) {
        c.favorite = new_state;
    }
    save_capes(data_dir, &data);
    new_state
}

pub fn delete_custom_cape(data_dir: &Path, cape_id: &str) -> Result<(), String> {
    let mut data = load_capes(data_dir);
    data.capes.retain(|c| c.id != cape_id || c.category != "my_capes");
    if data.active_cape.as_deref() == Some(cape_id) {
        data.active_cape = None;
    }
    save_capes(data_dir, &data);
    Ok(())
}

fn ensure_minecraft_cape_format(dyn_img: &image::DynamicImage, elytra_mode: Option<&str>) -> image::RgbaImage {
    let (w, h) = (dyn_img.width(), dyn_img.height());
    // If already 2:1 aspect ratio (standard cape dimensions 64x32, 128x64, 256x128, etc.)
    if w == h * 2 && w >= 64 {
        if w <= 2048 {
            return dyn_img.to_rgba8();
        } else {
            return dyn_img.resize_exact(2048, 1024, image::imageops::FilterType::Lanczos3).to_rgba8();
        }
    }
    if w == 22 && h == 17 {
        return dyn_img.to_rgba8();
    }

    // Determine target HD cape resolution without downscaling
    let cape_w = if w >= 1000 || h >= 800 {
        2048u32
    } else if w >= 400 || h >= 300 {
        1024u32
    } else {
        512u32
    };
    let cape_h = cape_w / 2;
    let mut out = image::RgbaImage::new(cape_w, cape_h);

    let scale = cape_w / 64; // e.g. 32, 16, or 8

    // Standard Minecraft cape UV layout:
    // Back face (outwards visible behind player): x = 12 * scale, y = 1 * scale, w = 10 * scale, h = 16 * scale
    // Front face (inwards towards player spine): x = 1 * scale, y = 1 * scale, w = 10 * scale, h = 16 * scale
    let back_x = 12 * scale;
    let back_y = 1 * scale;
    let cape_part_w = 10 * scale;
    let cape_part_h = 16 * scale;

    let resized_back = dyn_img
        .resize_to_fill(cape_part_w, cape_part_h, image::imageops::FilterType::Lanczos3)
        .to_rgba8();

    // Cape Back (visible behind player)
    image::imageops::overlay(&mut out, &resized_back, back_x as i64, back_y as i64);

    // Cape Front (facing body), slightly darkened
    let mut front = resized_back.clone();
    for p in front.pixels_mut() {
        p.0[0] = (p.0[0] as f32 * 0.75) as u8;
        p.0[1] = (p.0[1] as f32 * 0.75) as u8;
        p.0[2] = (p.0[2] as f32 * 0.75) as u8;
    }
    image::imageops::overlay(&mut out, &front, (1 * scale) as i64, (1 * scale) as i64);

    // Fill edges/borders (top, bottom, left, right)
    for x in 0..cape_part_w {
        let top_col = *resized_back.get_pixel(x, 0);
        let bot_col = *resized_back.get_pixel(x, cape_part_h - 1);
        for dy in 0..scale {
            out.put_pixel(1 * scale + x, dy, top_col);
            out.put_pixel(11 * scale + x, dy, bot_col);
        }
    }
    for y in 0..cape_part_h {
        let left_col = *resized_back.get_pixel(0, y);
        let right_col = *resized_back.get_pixel(cape_part_w - 1, y);
        for dx in 0..scale {
            out.put_pixel(dx, 1 * scale + y, left_col);
            out.put_pixel(11 * scale + dx, 1 * scale + y, right_col);
        }
    }

    // Map Elytra wings:
    // Left wing outside: x = 36 * scale, y = 2 * scale, w = 10 * scale, h = 20 * scale
    // Left wing inside:  x = 24 * scale, y = 2 * scale, w = 10 * scale, h = 20 * scale
    let el_w = 10 * scale;
    let el_h = 20 * scale;

    let mode = elytra_mode.unwrap_or("normal");
    let el_source = match mode {
        "mirrored" => dyn_img.fliph(),
        "rotate90" => dyn_img.rotate90(),
        "rotate180" => dyn_img.rotate180(),
        "rotate270" => dyn_img.rotate270(),
        "split" => {
            let half_w = dyn_img.width() / 2;
            if half_w > 0 {
                dyn_img.crop_imm(0, 0, half_w, dyn_img.height())
            } else {
                dyn_img.clone()
            }
        }
        _ => dyn_img.clone(),
    };

    let el_img = el_source
        .resize_to_fill(el_w, el_h, image::imageops::FilterType::Lanczos3)
        .to_rgba8();

    // Outside face of left wing (x: 36*scale, y: 2*scale)
    image::imageops::overlay(&mut out, &el_img, (36 * scale) as i64, (2 * scale) as i64);

    // Inside face of left wing (x: 24*scale, y: 2*scale) slightly dimmed
    let mut el_inside = el_img.clone();
    for p in el_inside.pixels_mut() {
        p.0[0] = (p.0[0] as f32 * 0.75) as u8;
        p.0[1] = (p.0[1] as f32 * 0.75) as u8;
        p.0[2] = (p.0[2] as f32 * 0.75) as u8;
    }
    image::imageops::overlay(&mut out, &el_inside, (24 * scale) as i64, (2 * scale) as i64);

    // Elytra borders
    for x in 0..el_w {
        let top_col = *el_img.get_pixel(x, 0);
        let bot_col = *el_img.get_pixel(x, el_h - 1);
        for dy in 0..(2 * scale) {
            out.put_pixel(24 * scale + x, dy, top_col);
            out.put_pixel(34 * scale + x, dy, bot_col);
        }
    }
    for y in 0..el_h {
        let side_col = *el_img.get_pixel(0, y);
        for dx in 0..(2 * scale) {
            out.put_pixel(22 * scale + dx, 2 * scale + y, side_col);
            out.put_pixel(34 * scale + dx, 2 * scale + y, side_col);
        }
    }

    out
}

pub fn upload_cape(
    data_dir: &Path,
    name: &str,
    images: Vec<String>, // Base64 data URLs or raw base64
    fps: u32,
    elytra_mode: Option<&str>,
) -> Result<CapeEntry, String> {
    if images.is_empty() {
        return Err("Keine Bilddaten übergeben".to_string());
    }

    let mut parsed_frames: Vec<String> = Vec::new();
    let animated;
    let effective_fps = if fps == 0 { 10 } else { fps };

    // Check if the first image is a GIF
    let first = &images[0];
    let raw_bytes = decode_b64(first)?;
    if raw_bytes.starts_with(b"GIF87a") || raw_bytes.starts_with(b"GIF89a") {
        // Decode GIF frames
        let decoder = GifDecoder::new(Cursor::new(&raw_bytes))
            .map_err(|e| format!("GIF-Dekodierung fehlgeschlagen: {}", e))?;
        let frames = decoder.into_frames();
        let collected_frames = frames
            .collect_frames()
            .map_err(|e| format!("GIF-Frames konnten nicht gelesen werden: {}", e))?;

        for f in collected_frames {
            let buffer = f.into_buffer();
            let dyn_img = image::DynamicImage::ImageRgba8(buffer);
            let cape_img = ensure_minecraft_cape_format(&dyn_img, elytra_mode);
            let mut out = Vec::new();
            let encoder = PngEncoder::new(&mut out);
            image::ImageEncoder::write_image(
                encoder,
                cape_img.as_raw(),
                cape_img.width(),
                cape_img.height(),
                image::ExtendedColorType::Rgba8,
            )
            .map_err(|e| format!("Frame-Konvertierung: {}", e))?;
            parsed_frames.push(format_data_url(&out));
        }
        animated = parsed_frames.len() > 1;
    } else {
        // Non-GIF: could be multiple images for an animated cape or single PNG/JPG
        for img in &images {
            let b = decode_b64(img)?;
            let dyn_img = image::load_from_memory(&b)
                .map_err(|e| format!("Bildformat konnte nicht erkannt werden: {}", e))?;
            let cape_img = ensure_minecraft_cape_format(&dyn_img, elytra_mode);
            let mut out = Vec::new();
            let encoder = PngEncoder::new(&mut out);
            image::ImageEncoder::write_image(
                encoder,
                cape_img.as_raw(),
                cape_img.width(),
                cape_img.height(),
                image::ExtendedColorType::Rgba8,
            )
            .map_err(|e| format!("PNG-Kodierung: {}", e))?;
            parsed_frames.push(format_data_url(&out));
        }
        animated = parsed_frames.len() > 1;
    }

    if parsed_frames.is_empty() {
        return Err("Keine gültigen Frames gefunden".to_string());
    }

    let id = format!("my_cape_{}", now_millis());
    let preview = parsed_frames[0].clone();
    let entry = CapeEntry {
        id: id.clone(),
        name: if name.trim().is_empty() {
            "Mein Umhang".to_string()
        } else {
            name.trim().to_string()
        },
        creator: "Du".to_string(),
        category: "my_capes".to_string(),
        uses: 1,
        favorite: false,
        animated,
        fps: effective_fps,
        frames: parsed_frames,
        preview,
    };

    let mut data = load_capes(data_dir);
    data.capes.insert(0, entry.clone());
    data.active_cape = Some(id);
    save_capes(data_dir, &data);

    sync_active_cape_to_instances(data_dir, &data, data.active_cape.as_deref());
    Ok(entry)
}

fn decode_b64(input: &str) -> Result<Vec<u8>, String> {
    let clean = input.split(',').last().unwrap_or(input).trim();
    base64::engine::general_purpose::STANDARD
        .decode(clean)
        .map_err(|e| format!("Base64-Fehler: {}", e))
}

fn format_data_url(bytes: &[u8]) -> String {
    format!(
        "data:image/png;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    )
}

fn now_millis() -> u128 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_millis())
        .unwrap_or(0)
}

pub fn sync_active_cape_to_instances(data_dir: &Path, store: &CapeStoreData, active_id: Option<&str>) {
    // Write config/kollegen-cape.json to all known instances so companion mod can read it
    let instances_file = crate::utils::instances_file(data_dir);
    let instances = crate::utils::load_json::<Vec<crate::types::Instance>>(&instances_file, vec![]);

    let cape_data = active_id
        .and_then(|id| store.capes.iter().find(|c| c.id == id))
        .cloned();

    // Fetch user's real vanilla capes if authenticated
    let mut vanilla_capes: Vec<serde_json::Value> = Vec::new();
    let mc_prof = crate::skins::minecraft_profile(data_dir);
    if let Some(capes_arr) = mc_prof.get("capes").and_then(|c| c.as_array()) {
        for vc in capes_arr {
            if let Some(id_str) = vc.get("id").and_then(|s| s.as_str()) {
                let alias = vc.get("alias").and_then(|s| s.as_str()).unwrap_or("Vanilla Cape");
                let url = vc.get("url").and_then(|s| s.as_str()).unwrap_or("");
                vanilla_capes.push(serde_json::json!({
                    "id": format!("vanilla_{}", id_str),
                    "vanillaId": id_str,
                    "name": alias,
                    "author": "Mojang",
                    "category": "vanilla",
                    "texture": url,
                    "preview": url,
                    "frames": [url],
                    "uses": 1,
                    "favorite": false
                }));
            }
        }
    }

    let mut all_json_capes: Vec<serde_json::Value> = store
        .capes
        .iter()
        .map(|c| serde_json::to_value(c).unwrap_or_default())
        .collect();
    all_json_capes.extend(vanilla_capes.clone());

    let json_val = serde_json::json!({
        "active": active_id,
        "cape": cape_data,
        "all_capes": all_json_capes,
        "vanilla_capes": vanilla_capes,
    });
    let json_str = serde_json::to_string_pretty(&json_val).unwrap_or_else(|_| "{}".to_string());

    let global_cfg = data_dir.join("capes");
    let _ = std::fs::create_dir_all(&global_cfg);
    let _ = std::fs::write(global_cfg.join("kollegen-cape.json"), &json_str);

    for inst in &instances {
        let inst_dir = crate::utils::instance_dir(data_dir, &inst.name);
        let cfg = inst_dir.join("config");
        let _ = std::fs::create_dir_all(&cfg);
        let _ = std::fs::write(cfg.join("kollegen-cape.json"), &json_str);
    }
}

fn create_default_capes() -> Vec<CapeEntry> {
    Vec::new()
}

/// Generates a valid 64x32 Minecraft cape PNG texture with top/bottom gradient and pixel banner
fn make_procedural_cape(c1: &str, c2: &str, _label: &str) -> String {
    use image::{Rgba, RgbaImage};

    let w = 64;
    let h = 32;
    let mut img = RgbaImage::new(w, h);

    let (r1, g1, b1) = parse_hex_color(c1);
    let (r2, g2, b2) = parse_hex_color(c2);

    for y in 0..h {
        let t = y as f32 / (h - 1) as f32;
        let r = ((1.0 - t) * r1 as f32 + t * r2 as f32) as u8;
        let g = ((1.0 - t) * g1 as f32 + t * g2 as f32) as u8;
        let b = ((1.0 - t) * b1 as f32 + t * b2 as f32) as u8;
        for x in 0..w {
            img.put_pixel(x, y, Rgba([r, g, b, 255]));
        }
    }

    // Add gold/neon border around the cape back panel (standard MC cape layout: back is [1, 1, 10, 16] or scaled)
    for x in 1..11 {
        img.put_pixel(x, 1, Rgba([255, 215, 0, 255]));
        img.put_pixel(x, 16, Rgba([255, 215, 0, 255]));
    }
    for y in 1..17 {
        img.put_pixel(1, y, Rgba([255, 215, 0, 255]));
        img.put_pixel(11, y, Rgba([255, 215, 0, 255]));
    }

    // Add some emblem pixels inside
    let emblem_col = if (r1 as u32 + g1 as u32 + b1 as u32) > 380 {
        Rgba([20, 20, 30, 255])
    } else {
        Rgba([240, 240, 255, 255])
    };
    for y in 6..12 {
        for x in 4..8 {
            if (x + y) % 2 == 0 {
                img.put_pixel(x, y, emblem_col);
            }
        }
    }

    let mut out = Vec::new();
    let encoder = PngEncoder::new(&mut out);
    let _ = image::ImageEncoder::write_image(
        encoder,
        img.as_raw(),
        w,
        h,
        image::ExtendedColorType::Rgba8,
    );
    format_data_url(&out)
}

fn parse_hex_color(hex: &str) -> (u8, u8, u8) {
    let clean = hex.trim_start_matches('#');
    if clean.len() == 6 {
        let r = u8::from_str_radix(&clean[0..2], 16).unwrap_or(128);
        let g = u8::from_str_radix(&clean[2..4], 16).unwrap_or(128);
        let b = u8::from_str_radix(&clean[4..6], 16).unwrap_or(128);
        (r, g, b)
    } else {
        (100, 140, 255)
    }
}
