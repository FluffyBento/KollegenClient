use serde::{Deserialize, Serialize};
use std::cell::Cell;

wit_bindgen::generate!({
    path: "wit",
    world: "horizon-mod",
});

use exports::horizon::mods::events::{Guest, Registration};
use horizon::mods::{chat, game, hud, input, log, render, storage, ui};

const CONFIG_KEY: &str = "kollegen_config_v4";
const WAYPOINTS_KEY: &str = "kollegen_waypoints_v1";
const SNAKE_BEST_KEY: &str = "kollegen_snake_best_v1";
const TETRIS_BEST_KEY: &str = "kollegen_tetris_best_v1";

const POST_WGSL_COMMON: &str = r#"
fn kol_luma(v: vec3<f32>) -> f32 {
    return dot(v, vec3<f32>(0.2126, 0.7152, 0.0722));
}

fn kol_aces(x: vec3<f32>) -> vec3<f32> {
    let a = 2.1;
    let b = 0.15;
    let c = 2.03;
    let d = 0.8;
    let e = 0.25;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), vec3<f32>(0.0), vec3<f32>(1.0));
}

fn kol_reinhard(v: vec3<f32>) -> vec3<f32> {
    let l = kol_luma(v);
    let tv = v / (vec3<f32>(1.0) + v);
    return mix(v / (1.0 + l), tv, tv);
}

fn kol_uncharted(x: vec3<f32>) -> vec3<f32> {
    let A = 0.28;
    let B = 0.29;
    let C = 0.10;
    let D = 0.20;
    let E = 0.025;
    let F = 0.35;
    return ((x * (A * x + C * B) + D * E) / (x * (A * x + B) + D * F)) - E / F;
}

fn apply_kollegen_post(uv: vec2<f32>, raw_col: vec3<f32>, d: f32) -> vec3<f32> {
    var col = raw_col;
    let sky = is_sky(d);
    let fullbright = frame.mod_params[0].x;
    if (fullbright > 0.5 && !sky) {
        let boosted = pow(max(col, vec3<f32>(0.0)), vec3<f32>(0.42)) + vec3<f32>(0.08);
        col = max(col, boosted);
    }

    if (frame.mod_params[1].z > 0.5) {
        let ssao = frame.mod_params[3].z;
        if (!sky && ssao > 0.01) {
            let px = vec2<f32>(0.0014, 0.0024);
            var occ = 0.0;
            let offs = array<vec2<f32>, 6>(
                vec2<f32>(1.5, 0.5), vec2<f32>(-1.5, -0.5),
                vec2<f32>(0.5, 1.5), vec2<f32>(-0.5, -1.5),
                vec2<f32>(1.2, -1.2), vec2<f32>(-1.2, 1.2)
            );
            for (var i = 0; i < 6; i = i + 1) {
                let su = clamp(uv + offs[i] * px, vec2<f32>(0.0), vec2<f32>(1.0));
                let sd = depth_at(su);
                let diff = abs(d - sd);
                if (sd > d && diff > 0.0001 && diff < 0.02) {
                    occ = occ + 1.0;
                }
            }
            let ao = clamp(1.0 - (occ / 6.0) * ssao * 0.55, 0.35, 1.0);
            col = col * ao;
        }

        let depth_fog = frame.mod_params[3].w;
        if (!sky && depth_fog > 0.01) {
            let dist_v = length(world_offset(uv, d));
            let fog_f = clamp((dist_v - 48.0) / 220.0, 0.0, 0.65) * depth_fog;
            col = mix(col, frame.fog_color.rgb, fog_f);
        }

        let bloom = frame.mod_params[3].y;
        if (bloom > 0.01) {
            let px = vec2<f32>(0.0022, 0.0038);
            var b_acc = vec3<f32>(0.0);
            var w_sum = 0.0;
            let boff = array<vec2<f32>, 8>(
                vec2<f32>(1.0, 0.0), vec2<f32>(-1.0, 0.0),
                vec2<f32>(0.0, 1.0), vec2<f32>(0.0, -1.0),
                vec2<f32>(1.0, 1.0), vec2<f32>(-1.0, 1.0),
                vec2<f32>(1.0, -1.0), vec2<f32>(-1.0, -1.0)
            );
            for (var i = 0; i < 8; i = i + 1) {
                let s = textureSampleLevel(scene_color, linear_sampler, clamp(uv + boff[i] * px, vec2<f32>(0.0), vec2<f32>(1.0)), 0.0).rgb;
                if (s.r + s.g + s.b > 2.15) {
                    b_acc = b_acc + max(s - vec3<f32>(0.75), vec3<f32>(0.0));
                    w_sum = w_sum + 1.0;
                }
            }
            if (w_sum > 0.0) {
                col = col + (b_acc / w_sum) * bloom * 0.42;
            }
        }

        let exposure = frame.mod_params[2].x;
        if (abs(exposure - 1.0) > 0.001) {
            col = col * exposure;
        }

        let warmth = frame.mod_params[2].y;
        if (abs(warmth - 1.0) > 0.001) {
            if (warmth > 1.0) {
                let t = warmth - 1.0;
                col = vec3<f32>(col.r + col.r * t * 0.12, col.g + col.g * t * 0.04, col.b - col.b * t * 0.08);
            } else {
                let t = 1.0 - warmth;
                col = vec3<f32>(col.r - col.r * t * 0.08, col.g, col.b + col.b * t * 0.12);
            }
        }

        var luma = kol_luma(max(col, vec3<f32>(0.0)));
        let vibrance = frame.mod_params[1].x;
        if (abs(vibrance - 1.0) > 0.001) {
            let mx = max(col.r, max(col.g, col.b));
            let mn = min(col.r, min(col.g, col.b));
            let factor = 1.0 + (vibrance - 1.0) * (1.0 - clamp(mx - mn, 0.0, 1.0));
            col = mix(vec3<f32>(luma), col, max(0.0, factor));
            luma = kol_luma(max(col, vec3<f32>(0.0)));
        }

        let sat = frame.mod_params[0].y;
        if (abs(sat - 1.0) > 0.001) {
            col = mix(vec3<f32>(luma), col, sat);
        }

        let contrast = frame.mod_params[0].z;
        if (abs(contrast - 1.0) > 0.001) {
            col = (col - vec3<f32>(0.5)) * contrast + vec3<f32>(0.5);
        }

        let bright = frame.mod_params[0].w;
        if (abs(bright - 1.0) > 0.001) {
            col = col * bright;
        }

        col = max(col, vec3<f32>(0.0));

        let tonemap = frame.mod_params[2].w;
        if (tonemap > 0.5) {
            if (tonemap < 1.5) {
                col = kol_aces(col);
            } else if (tonemap < 2.5) {
                col = kol_reinhard(col);
            } else {
                col = clamp(kol_uncharted(col), vec3<f32>(0.0), vec3<f32>(1.0));
            }
        }

        let gamma = frame.mod_params[3].x;
        if (abs(gamma - 1.0) > 0.001 && gamma > 0.1) {
            col = pow(clamp(col, vec3<f32>(0.0), vec3<f32>(1.0)), vec3<f32>(1.0 / gamma));
        }

        let vignette = frame.mod_params[2].z;
        if (vignette > 0.001) {
            let dv = length(uv - vec2<f32>(0.5));
            let vig = clamp(1.0 - dv * dv * vignette * 1.5, 0.0, 1.0);
            col = col * vig;
        }
    }

    return clamp(col, vec3<f32>(0.0), vec3<f32>(1.0));
}
"#;

const POST_WGSL_BASE_MAIN: &str = r#"
@fragment
fn fs_main(in: StageIn) -> @location(0) vec4<f32> {
    let zoom = max(frame.mod_params[1].y, 1.0);
    let uv = clamp((in.uv - vec2<f32>(0.5, 0.5)) / zoom + vec2<f32>(0.5, 0.5), vec2<f32>(0.0, 0.0), vec2<f32>(1.0, 1.0));
    let raw = textureSampleLevel(scene_color, linear_sampler, uv, 0.0).rgb;
    let d = depth_at(uv);
    return vec4<f32>(apply_kollegen_post(uv, raw, d), 1.0);
}
"#;

const POST_WGSL_HISTORY_MAIN: &str = r#"
@fragment
fn fs_main(in: StageIn) -> @location(0) vec4<f32> {
    let zoom = max(frame.mod_params[1].y, 1.0);
    let uv = clamp((in.uv - vec2<f32>(0.5, 0.5)) / zoom + vec2<f32>(0.5, 0.5), vec2<f32>(0.0, 0.0), vec2<f32>(1.0, 1.0));
    var col = textureSampleLevel(scene_color, linear_sampler, uv, 0.0).rgb;
    let d = depth_at(uv);
    let blur = frame.mod_params[1].w;
    if (frame.previous_camera_position.w > 0.5 && blur > 0.01 && zoom <= 1.01) {
        let wp = world_offset(uv, d) + (frame.camera_position.xyz - frame.previous_camera_position.xyz);
        let prev_clip = frame.previous_projection * frame.previous_view * vec4<f32>(wp, 1.0);
        if (prev_clip.w > 0.0001) {
            let prev_uv = vec2<f32>(prev_clip.x / prev_clip.w * 0.5 + 0.5, 0.5 - prev_clip.y / prev_clip.w * 0.5);
            let cu = clamp(prev_uv, vec2<f32>(0.0), vec2<f32>(1.0));
            let hist = textureSampleLevel(history_picture, linear_sampler, cu, 0.0).rgb;
            let motion = clamp(length(prev_uv - uv) * 18.0, 0.0, 1.0);
            let blend = clamp(blur * (0.2 + 0.65 * motion), 0.0, 0.85);
            col = mix(col, hist, blend);
        }
    }
    return vec4<f32>(apply_kollegen_post(uv, col, d), 1.0);
}
"#;

#[derive(Clone, Copy)]
struct Theme {
    id: &'static str,
    name: &'static str,
    bg: u32,
    panel: u32,
    panel2: u32,
    border: u32,
    accent: u32,
    accent2: u32,
    text: u32,
    muted: u32,
    danger: u32,
    green: u32,
    sharp: bool,
}

fn p_theme(
    id: &'static str,
    name: &'static str,
    bg: i32,
    panel: i32,
    panel2: i32,
    border: i32,
    accent: i32,
    accent2: i32,
    text: i32,
    muted: i32,
    danger: i32,
    green: i32,
    sharp: bool,
) -> Theme {
    Theme {
        id,
        name,
        bg: bg as u32,
        panel: panel as u32,
        panel2: panel2 as u32,
        border: border as u32,
        accent: accent as u32,
        accent2: accent2 as u32,
        text: text as u32,
        muted: muted as u32,
        danger: danger as u32,
        green: green as u32,
        sharp,
    }
}

fn all_themes() -> [Theme; 9] {
    [
        p_theme("default", "Default", -15921902, -15066588, -14606034, -13356998, -678365, -34304, -792104, -4609652, -42130, -12663699, false),
        p_theme("dark", "Dark", -16119286, -15461356, -14803426, -13816531, -4487428, -6461232, -1513240, -5197648, -3185031, -16524602, false),
        p_theme("nord", "Nord", -13749184, -12893614, -12366754, -11774358, -7814960, -8281663, -2564375, -4669232, -4234902, -6046068, false),
        p_theme("dracula", "Dracula", -14144970, -12302502, -12302502, -10325340, -4353031, -34362, -460558, -4342339, -43691, -11470213, false),
        p_theme("gruvbox", "Gruvbox", -14145496, -12830666, -11515579, -10068908, -2647775, -98279, -1320014, -5727868, -308940, -4670682, false),
        p_theme("tokyo-night", "Tokyo Night", -15066330, -14407621, -13683638, -12893599, -8740105, -4482313, -4142347, -5656106, -559474, -6369686, false),
        p_theme("catppuccin-mocha", "Catppuccin", -14803410, -13553084, -12236966, -10986640, -671001, -400721, -3287308, -4537634, -816216, -5839967, false),
        p_theme("high-contrast", "High Contrast", -16777216, -15066598, -13421773, -1, -256, -22016, -1, -3355444, -65536, -16711936, false),
        p_theme("minecraft", "Minecraft", -15461356, -14540254, -15198184, -11842742, -11141291, -13730510, -1, -5592406, -43691, -11141291, true),
    ]
}

fn find_theme(id: &str) -> Theme {
    let list = all_themes();
    list.into_iter().find(|t| t.id == id).unwrap_or(list[0])
}

#[derive(Clone, Serialize, Deserialize)]
#[serde(default)]
struct Config {
    theme_id: String,
    glass_opacity: f32,
    hud_corner: u8,
    hud_coords: bool,
    hud_nether: bool,
    hud_direction: bool,
    hud_compass_bar: bool,
    hud_fps: bool,
    hud_speed: bool,
    hud_daycounter: bool,
    hud_clock: bool,
    hud_health: bool,
    hud_held: bool,
    kollegen_shader: bool,
    shader_preset: u8,
    shader_sat_on: bool,
    saturation: f32,
    shader_vib_on: bool,
    vibrance: f32,
    shader_con_on: bool,
    contrast: f32,
    shader_bri_on: bool,
    brightness: f32,
    shader_exp_on: bool,
    exposure: f32,
    shader_bloom_on: bool,
    bloom: f32,
    shader_warm_on: bool,
    warmth: f32,
    shader_vig_on: bool,
    vignette: f32,
    shader_blur_on: bool,
    blur_strength: f32,
    tone_map: u8,
    gamma: f32,
    ssao: f32,
    depth_fog: f32,
    fullbright: bool,
    color_sat: bool,
    motion_blur: bool,
    zoom_factor: f32,
    zoom_hold_mode: bool,
    afk_enabled: bool,
    afk_delay_secs: u32,
    afk_speed: f32,
    afk_distance: f32,
    waypoints_enabled: bool,
    wp_in_compass: bool,
    auto_death_wp: bool,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            theme_id: "default".to_string(),
            glass_opacity: 0.62,
            hud_corner: 0,
            hud_coords: false,
            hud_nether: false,
            hud_direction: false,
            hud_compass_bar: true,
            hud_fps: false,
            hud_speed: false,
            hud_daycounter: false,
            hud_clock: false,
            hud_health: false,
            hud_held: false,
            kollegen_shader: false,
            shader_preset: 0,
            shader_sat_on: true,
            saturation: 1.40,
            shader_vib_on: true,
            vibrance: 1.30,
            shader_con_on: true,
            contrast: 1.15,
            shader_bri_on: false,
            brightness: 1.05,
            shader_exp_on: true,
            exposure: 1.10,
            shader_bloom_on: true,
            bloom: 0.40,
            shader_warm_on: true,
            warmth: 1.08,
            shader_vig_on: false,
            vignette: 0.15,
            shader_blur_on: false,
            blur_strength: 0.40,
            tone_map: 1,
            gamma: 1.18,
            ssao: 0.40,
            depth_fog: 0.35,
            fullbright: false,
            color_sat: false,
            motion_blur: false,
            zoom_factor: 4.0,
            zoom_hold_mode: true,
            afk_enabled: true,
            afk_delay_secs: 20,
            afk_speed: 0.6,
            afk_distance: 4.0,
            waypoints_enabled: true,
            wp_in_compass: true,
            auto_death_wp: true,
        }
    }
}

impl Config {
    fn apply_shader_preset(&mut self, idx: u8) {
        self.shader_preset = idx % 8;
        match self.shader_preset {
            0 => {
                self.shader_sat_on = true; self.saturation = 1.40;
                self.shader_vib_on = true; self.vibrance = 1.30;
                self.shader_con_on = true; self.contrast = 1.15;
                self.shader_bri_on = false; self.brightness = 1.05;
                self.shader_exp_on = true; self.exposure = 1.10;
                self.shader_bloom_on = true; self.bloom = 0.40;
                self.shader_warm_on = true; self.warmth = 1.08;
                self.shader_vig_on = false; self.vignette = 0.15;
                self.tone_map = 1; self.gamma = 1.18;
                self.ssao = 0.40; self.depth_fog = 0.35;
            }
            1 => {
                self.shader_sat_on = true; self.saturation = 1.25;
                self.shader_vib_on = true; self.vibrance = 1.20;
                self.shader_con_on = true; self.contrast = 1.15;
                self.shader_bri_on = false; self.brightness = 1.02;
                self.shader_exp_on = true; self.exposure = 1.06;
                self.shader_bloom_on = true; self.bloom = 0.30;
                self.shader_warm_on = true; self.warmth = 1.06;
                self.shader_vig_on = false; self.vignette = 0.12;
                self.tone_map = 1; self.gamma = 1.16;
                self.ssao = 0.50; self.depth_fog = 0.45;
            }
            2 => {
                self.shader_sat_on = true; self.saturation = 1.22;
                self.shader_vib_on = true; self.vibrance = 1.18;
                self.shader_con_on = true; self.contrast = 1.16;
                self.shader_bri_on = false; self.brightness = 1.00;
                self.shader_exp_on = true; self.exposure = 1.04;
                self.shader_bloom_on = true; self.bloom = 0.20;
                self.shader_warm_on = true; self.warmth = 1.02;
                self.shader_vig_on = false; self.vignette = 0.10;
                self.tone_map = 2; self.gamma = 1.18;
                self.ssao = 0.45; self.depth_fog = 0.35;
            }
            3 => {
                self.shader_sat_on = true; self.saturation = 1.32;
                self.shader_vib_on = true; self.vibrance = 1.26;
                self.shader_con_on = true; self.contrast = 1.18;
                self.shader_bri_on = false; self.brightness = 1.02;
                self.shader_exp_on = true; self.exposure = 1.08;
                self.shader_bloom_on = true; self.bloom = 0.35;
                self.shader_warm_on = true; self.warmth = 1.05;
                self.shader_vig_on = true; self.vignette = 0.12;
                self.tone_map = 1; self.gamma = 1.20;
                self.ssao = 0.60; self.depth_fog = 0.40;
            }
            4 => {
                self.shader_sat_on = true; self.saturation = 1.18;
                self.shader_vib_on = true; self.vibrance = 1.15;
                self.shader_con_on = true; self.contrast = 1.08;
                self.shader_bri_on = false; self.brightness = 1.04;
                self.shader_exp_on = true; self.exposure = 1.06;
                self.shader_bloom_on = true; self.bloom = 0.30;
                self.shader_warm_on = true; self.warmth = 1.10;
                self.shader_vig_on = false; self.vignette = 0.10;
                self.tone_map = 2; self.gamma = 1.15;
                self.ssao = 0.35; self.depth_fog = 0.50;
            }
            5 => {
                self.shader_sat_on = true; self.saturation = 1.55;
                self.shader_vib_on = true; self.vibrance = 1.45;
                self.shader_con_on = true; self.contrast = 1.20;
                self.shader_bri_on = true; self.brightness = 1.04;
                self.shader_exp_on = true; self.exposure = 1.12;
                self.shader_bloom_on = true; self.bloom = 0.35;
                self.shader_warm_on = true; self.warmth = 1.06;
                self.shader_vig_on = false; self.vignette = 0.10;
                self.tone_map = 1; self.gamma = 1.18;
                self.ssao = 0.30; self.depth_fog = 0.25;
            }
            6 => {
                self.shader_sat_on = true; self.saturation = 1.12;
                self.shader_vib_on = true; self.vibrance = 1.12;
                self.shader_con_on = true; self.contrast = 1.24;
                self.shader_bri_on = false; self.brightness = 0.98;
                self.shader_exp_on = true; self.exposure = 1.02;
                self.shader_bloom_on = true; self.bloom = 0.45;
                self.shader_warm_on = true; self.warmth = 1.04;
                self.shader_vig_on = true; self.vignette = 0.28;
                self.tone_map = 3; self.gamma = 1.22;
                self.ssao = 0.55; self.depth_fog = 0.45;
            }
            _ => {
                self.shader_sat_on = true; self.saturation = 1.35;
                self.shader_vib_on = true; self.vibrance = 1.28;
                self.shader_con_on = true; self.contrast = 1.12;
                self.shader_bri_on = true; self.brightness = 1.06;
                self.shader_exp_on = true; self.exposure = 1.12;
                self.shader_bloom_on = true; self.bloom = 0.40;
                self.shader_warm_on = true; self.warmth = 1.16;
                self.shader_vig_on = false; self.vignette = 0.12;
                self.tone_map = 1; self.gamma = 1.14;
                self.ssao = 0.30; self.depth_fog = 0.30;
            }
        }
    }
}

#[derive(Clone, Serialize, Deserialize)]
struct Waypoint {
    name: String,
    x: f64,
    y: f64,
    z: f64,
    dimension: String,
    is_death: bool,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum ModId {
    KollegenShader,
    ColorSat,
    Fullbright,
    MotionBlur,
    Zoom,
    AfkCam,
    HudCoords,
    HudFps,
    HudDirection,
    HudDayCounter,
    HudClock,
    HudSpeed,
    HudHealth,
    HudHeld,
    Waypoints,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum OverlayView {
    Modules,
    HudEditor,
    ThemePicker,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum ExtraGame {
    None,
    Tetris,
    Snake,
    Connect4,
    TicTacToe,
}

#[derive(Clone, Copy)]
enum SliderTarget {
    GlassOpacity,
    Saturation,
    Vibrance,
    Contrast,
    Brightness,
    Exposure,
    Bloom,
    Warmth,
    Vignette,
    BlurStrength,
    Gamma,
    Ssao,
    DepthFog,
    ZoomFactor,
    AfkDelay,
    AfkSpeed,
    AfkDistance,
}

#[derive(Clone, Copy)]
enum BoolTarget {
    HudCoords,
    HudNether,
    HudFps,
    HudDirection,
    HudCompassBar,
    HudDayCounter,
    HudClock,
    HudSpeed,
    HudHealth,
    HudHeld,
    WpInCompass,
    AutoDeathWp,
    ZoomHoldMode,
    ShaderSat,
    ShaderVib,
    ShaderCon,
    ShaderBri,
    ShaderExp,
    ShaderBloom,
    ShaderWarm,
    ShaderVig,
    ShaderBlur,
}

#[derive(Clone, Copy)]
enum ModeTarget {
    ShaderPreset,
    ToneMap,
}

#[derive(Clone, Copy)]
struct HitRect {
    x: f32,
    y: f32,
    w: f32,
    h: f32,
    action: Action,
}

#[derive(Clone, Copy)]
enum Action {
    Close,
    Tab(u8),
    ToggleOverlay(OverlayView),
    SetTheme(usize),
    ToggleMod(ModId),
    OpenDetail(ModId),
    CloseDetail,
    PageMods(i8),
    PageDetail(i8),
    ToggleBool(BoolTarget),
    CycleMode(ModeTarget),
    SetSliderRatio(SliderTarget, f32),
    StepSlider(SliderTarget, i8),
    CycleCorner,
    FocusSearch,
    ClearSearch,
    FocusWpInput,
    AddWaypointNow,
    DeleteWaypoint(usize),
    PageWaypoints(i8),
    OpenGame(ExtraGame),
    CloseGame,
    SnakeReset,
    TetrisReset,
    TetrisMove(i8),
    TetrisRotate,
    TetrisDrop,
    Connect4Col(usize),
    Connect4Reset,
    TttCell(usize),
    TttReset,
}

#[derive(Clone, Copy, PartialEq, Eq)]
enum PostMode {
    Off,
    Base,
    History,
}

struct SnakeState {
    body: Vec<(i32, i32)>,
    dir: (i32, i32),
    food: (i32, i32),
    score: u32,
    best: u32,
    dead: bool,
    step_ticks: u32,
    rng: u32,
}

impl SnakeState {
    fn new(best: u32) -> Self {
        Self {
            body: vec![(8, 6), (7, 6), (6, 6)],
            dir: (1, 0),
            food: (12, 6),
            score: 0,
            best,
            dead: false,
            step_ticks: 0,
            rng: 0x9E37_79B9,
        }
    }

    fn next_rand(&mut self) -> i32 {
        self.rng ^= self.rng << 13;
        self.rng ^= self.rng >> 17;
        self.rng ^= self.rng << 5;
        (self.rng as i32).abs()
    }

    fn step(&mut self) {
        if self.dead {
            return;
        }
        let head = self.body[0];
        let nx = head.0 + self.dir.0;
        let ny = head.1 + self.dir.1;
        if !(0..16).contains(&nx) || !(0..12).contains(&ny) || self.body.contains(&(nx, ny)) {
            self.dead = true;
            return;
        }
        self.body.insert(0, (nx, ny));
        if (nx, ny) == self.food {
            self.score += 1;
            if self.score > self.best {
                self.best = self.score;
                let _ = storage::set(SNAKE_BEST_KEY, &self.best.to_le_bytes());
            }
            for _ in 0..32 {
                let fx = self.next_rand() % 16;
                let fy = self.next_rand() % 12;
                if !self.body.contains(&(fx, fy)) {
                    self.food = (fx, fy);
                    break;
                }
            }
        } else {
            self.body.pop();
        }
    }
}

const TETROMINOS: [[(i32, i32); 4]; 7] = [
    [(0, 1), (1, 1), (2, 1), (3, 1)],
    [(1, 0), (2, 0), (1, 1), (2, 1)],
    [(1, 0), (0, 1), (1, 1), (2, 1)],
    [(1, 0), (2, 0), (0, 1), (1, 1)],
    [(0, 0), (1, 0), (1, 1), (2, 1)],
    [(0, 0), (0, 1), (1, 1), (2, 1)],
    [(2, 0), (0, 1), (1, 1), (2, 1)],
];

const TETRIS_COLORS: [u32; 7] = [
    0xFF38BDF8, 0xFFFACC15, 0xFFC084FC, 0xFF4ADE80, 0xFFF87171, 0xFF60A5FA, 0xFFFB923C,
];

struct TetrisState {
    board: [[u8; 10]; 18],
    piece: usize,
    cells: [(i32, i32); 4],
    px: i32,
    py: i32,
    score: u32,
    lines: u32,
    best: u32,
    over: bool,
    fall_ticks: u32,
    rng: u32,
}

impl TetrisState {
    fn new(best: u32) -> Self {
        let mut s = Self {
            board: [[0; 10]; 18],
            piece: 0,
            cells: TETROMINOS[0],
            px: 3,
            py: 0,
            score: 0,
            lines: 0,
            best,
            over: false,
            fall_ticks: 0,
            rng: 0xA511_E9B3,
        };
        s.spawn();
        s
    }

    fn next_rand(&mut self) -> usize {
        self.rng ^= self.rng << 13;
        self.rng ^= self.rng >> 17;
        self.rng ^= self.rng << 5;
        (self.rng as usize) % 7
    }

    fn fits(&self, cells: &[(i32, i32); 4], px: i32, py: i32) -> bool {
        for &(cx, cy) in cells {
            let x = px + cx;
            let y = py + cy;
            if !(0..10).contains(&x) || y >= 18 {
                return false;
            }
            if y >= 0 && self.board[y as usize][x as usize] != 0 {
                return false;
            }
        }
        true
    }

    fn spawn(&mut self) {
        self.piece = self.next_rand();
        self.cells = TETROMINOS[self.piece];
        self.px = 3;
        self.py = 0;
        if !self.fits(&self.cells, self.px, self.py) {
            self.over = true;
        }
    }

    fn shift(&mut self, dx: i32) {
        if !self.over && self.fits(&self.cells, self.px + dx, self.py) {
            self.px += dx;
        }
    }

    fn rotate(&mut self) {
        if self.over || self.piece == 1 {
            return;
        }
        let mut rot = [(0, 0); 4];
        for (i, &(cx, cy)) in self.cells.iter().enumerate() {
            rot[i] = (2 - cy, cx);
        }
        for kick in [0, -1, 1] {
            if self.fits(&rot, self.px + kick, self.py) {
                self.cells = rot;
                self.px += kick;
                return;
            }
        }
    }

    fn lock(&mut self) {
        for &(cx, cy) in &self.cells {
            let x = self.px + cx;
            let y = self.py + cy;
            if (0..10).contains(&x) && (0..18).contains(&y) {
                self.board[y as usize][x as usize] = (self.piece as u8) + 1;
            }
        }
        let mut cleared = 0u32;
        let mut dst = 17i32;
        for src in (0..18).rev() {
            if self.board[src].iter().all(|&c| c != 0) {
                cleared += 1;
            } else {
                self.board[dst as usize] = self.board[src];
                dst -= 1;
            }
        }
        while dst >= 0 {
            self.board[dst as usize] = [0; 10];
            dst -= 1;
        }
        if cleared > 0 {
            self.lines += cleared;
            self.score += match cleared {
                1 => 100,
                2 => 300,
                3 => 500,
                _ => 800,
            };
            if self.score > self.best {
                self.best = self.score;
                let _ = storage::set(TETRIS_BEST_KEY, &self.best.to_le_bytes());
            }
        }
        self.spawn();
    }

    fn step_down(&mut self) {
        if self.over {
            return;
        }
        if self.fits(&self.cells, self.px, self.py + 1) {
            self.py += 1;
        } else {
            self.lock();
        }
    }

    fn hard_drop(&mut self) {
        if self.over {
            return;
        }
        while self.fits(&self.cells, self.px, self.py + 1) {
            self.py += 1;
        }
        self.lock();
    }
}

struct Connect4State {
    board: [[u8; 7]; 6],
    status: &'static str,
    done: bool,
}

impl Connect4State {
    fn new() -> Self {
        Self {
            board: [[0; 7]; 6],
            status: "Du bist Gelb — wähle eine Spalte!",
            done: false,
        }
    }

    fn drop_in(&mut self, col: usize, player: u8) -> Option<usize> {
        if col >= 7 {
            return None;
        }
        for row in (0..6).rev() {
            if self.board[row][col] == 0 {
                self.board[row][col] = player;
                return Some(row);
            }
        }
        None
    }

    fn has_win(&self, p: u8) -> bool {
        for r in 0..6 {
            for c in 0..7 {
                if c + 3 < 7 && (0..4).all(|k| self.board[r][c + k] == p) {
                    return true;
                }
                if r + 3 < 6 && (0..4).all(|k| self.board[r + k][c] == p) {
                    return true;
                }
                if r + 3 < 6 && c + 3 < 7 && (0..4).all(|k| self.board[r + k][c + k] == p) {
                    return true;
                }
                if r >= 3 && c + 3 < 7 && (0..4).all(|k| self.board[r - k][c + k] == p) {
                    return true;
                }
            }
        }
        false
    }

    fn board_full(&self) -> bool {
        (0..7).all(|c| self.board[0][c] != 0)
    }

    fn play(&mut self, col: usize) {
        if self.done || self.drop_in(col, 1).is_none() {
            return;
        }
        if self.has_win(1) {
            self.status = "Du gewinnst!";
            self.done = true;
            return;
        }
        if self.board_full() {
            self.status = "Unentschieden!";
            self.done = true;
            return;
        }
        let mut pick = None;
        for target in [2u8, 1u8] {
            for c in 0..7 {
                if let Some(r) = self.drop_in(c, target) {
                    let win = self.has_win(target);
                    self.board[r][c] = 0;
                    if win {
                        pick = Some(c);
                        break;
                    }
                }
            }
            if pick.is_some() {
                break;
            }
        }
        if pick.is_none() {
            for c in [3, 2, 4, 1, 5, 0, 6] {
                if self.board[0][c] == 0 {
                    pick = Some(c);
                    break;
                }
            }
        }
        if let Some(c) = pick {
            let _ = self.drop_in(c, 2);
        }
        if self.has_win(2) {
            self.status = "KI gewinnt!";
            self.done = true;
        } else if self.board_full() {
            self.status = "Unentschieden!";
            self.done = true;
        } else {
            self.status = "Dein Zug (Gelb)";
        }
    }
}

struct Companion {
    cfg: Config,
    waypoints: Vec<Waypoint>,
    menu_open: bool,
    tab: u8,
    overlay: OverlayView,
    detail: Option<ModId>,
    mod_page: usize,
    detail_page: usize,
    wp_page: usize,
    search_query: String,
    search_focused: bool,
    wp_input: String,
    wp_input_focused: bool,
    zoom_held: bool,
    zoom_toggled: bool,
    afk_active: bool,
    afk_angle: f32,
    idle_ticks: u32,
    last_pos: Option<(f64, f64, f64, f32, f32)>,
    speed_bps: f64,
    prev_health: f32,
    fps_frames: u32,
    fps_acc: f32,
    fps_display: u32,
    post_mode: PostMode,
    last_params: [f32; 16],
    last_hud: Vec<(String, u32)>,
    last_corner: u8,
    buttons: Vec<HitRect>,
    view_offset_set: bool,
    extra_game: ExtraGame,
    snake: SnakeState,
    tetris: TetrisState,
    connect4: Connect4State,
    ttt_board: [u8; 9],
    ttt_status: &'static str,
}

thread_local! {
    static STATE: std::cell::RefCell<Option<Companion>> = const { std::cell::RefCell::new(None) };
    static TICK_GUARD: Cell<bool> = const { Cell::new(false) };
}

fn with_mod<R>(f: impl FnOnce(&mut Companion) -> R) -> Option<R> {
    STATE.with(|s| s.borrow_mut().as_mut().map(f))
}

impl Companion {
    fn load() -> Self {
        let cfg = storage::get(CONFIG_KEY)
            .and_then(|b| serde_json::from_slice::<Config>(&b).ok())
            .unwrap_or_default();
        let waypoints = storage::get(WAYPOINTS_KEY)
            .and_then(|b| serde_json::from_slice::<Vec<Waypoint>>(&b).ok())
            .unwrap_or_default();
        let snake_best = storage::get(SNAKE_BEST_KEY)
            .and_then(|b| b.try_into().ok().map(u32::from_le_bytes))
            .unwrap_or(0);
        let tetris_best = storage::get(TETRIS_BEST_KEY)
            .and_then(|b| b.try_into().ok().map(u32::from_le_bytes))
            .unwrap_or(0);
        Self {
            cfg,
            waypoints,
            menu_open: false,
            tab: 0,
            overlay: OverlayView::Modules,
            detail: None,
            mod_page: 0,
            detail_page: 0,
            wp_page: 0,
            search_query: String::new(),
            search_focused: false,
            wp_input: String::new(),
            wp_input_focused: false,
            zoom_held: false,
            zoom_toggled: false,
            afk_active: false,
            afk_angle: 0.0,
            idle_ticks: 0,
            last_pos: None,
            speed_bps: 0.0,
            prev_health: 20.0,
            fps_frames: 0,
            fps_acc: 0.0,
            fps_display: 0,
            post_mode: PostMode::Off,
            last_params: [-1.0; 16],
            last_hud: Vec::new(),
            last_corner: 255,
            buttons: Vec::new(),
            view_offset_set: false,
            extra_game: ExtraGame::None,
            snake: SnakeState::new(snake_best),
            tetris: TetrisState::new(tetris_best),
            connect4: Connect4State::new(),
            ttt_board: [0; 9],
            ttt_status: "Du bist X — wähle ein Feld!",
        }
    }

    fn theme(&self) -> Theme {
        find_theme(&self.cfg.theme_id)
    }

    fn save_config(&self) {
        if let Ok(bytes) = serde_json::to_vec(&self.cfg) {
            let _ = storage::set(CONFIG_KEY, &bytes);
        }
    }

    fn save_waypoints(&self) {
        if let Ok(bytes) = serde_json::to_vec(&self.waypoints) {
            let _ = storage::set(WAYPOINTS_KEY, &bytes);
        }
    }

    fn hud_corner(&self) -> hud::Corner {
        match self.cfg.hud_corner % 4 {
            1 => hud::Corner::TopRight,
            2 => hud::Corner::BottomLeft,
            3 => hud::Corner::BottomRight,
            _ => hud::Corner::TopLeft,
        }
    }

    fn corner_label(&self) -> &'static str {
        match self.cfg.hud_corner % 4 {
            1 => "Oben Rechts",
            2 => "Unten Links",
            3 => "Unten Rechts",
            _ => "Oben Links",
        }
    }

    fn sync_render(&mut self) {
        let zoom_active = if self.cfg.zoom_hold_mode {
            self.zoom_held
        } else {
            self.zoom_toggled
        };
        let zoom_val = if zoom_active {
            self.cfg.zoom_factor.clamp(1.0, 16.0)
        } else {
            1.0
        };
        let shader_on = self.cfg.kollegen_shader;
        let grade_on = shader_on || self.cfg.color_sat;
        let blur_on = self.cfg.motion_blur || (shader_on && self.cfg.shader_blur_on);
        let want_mode = if blur_on {
            PostMode::History
        } else if self.cfg.fullbright || grade_on || zoom_val > 1.01 {
            PostMode::Base
        } else {
            PostMode::Off
        };

        if want_mode == PostMode::Off {
            if self.post_mode != PostMode::Off {
                render::set_history(false);
                render::clear_post();
                self.post_mode = PostMode::Off;
                self.last_params = [-1.0; 16];
            }
            return;
        }

        if want_mode != self.post_mode {
            match want_mode {
                PostMode::History => {
                    render::set_history(true);
                    let wgsl = format!(
                        "@group(0) @binding(17) var history_picture: texture_2d<f32>;\n{}\n{}",
                        POST_WGSL_COMMON, POST_WGSL_HISTORY_MAIN
                    );
                    render::set_post(&wgsl);
                }
                PostMode::Base => {
                    render::set_history(false);
                    let wgsl = format!("{}\n{}", POST_WGSL_COMMON, POST_WGSL_BASE_MAIN);
                    render::set_post(&wgsl);
                }
                PostMode::Off => {}
            }
            self.post_mode = want_mode;
            self.last_params = [-1.0; 16];
        }

        let sat = if (shader_on && self.cfg.shader_sat_on) || self.cfg.color_sat { self.cfg.saturation } else { 1.0 };
        let vib = if (shader_on && self.cfg.shader_vib_on) || self.cfg.color_sat { self.cfg.vibrance } else { 1.0 };
        let con = if (shader_on && self.cfg.shader_con_on) || self.cfg.color_sat { self.cfg.contrast } else { 1.0 };
        let bri = if (shader_on && self.cfg.shader_bri_on) || self.cfg.color_sat { self.cfg.brightness } else { 1.0 };
        let exp = if shader_on && self.cfg.shader_exp_on { self.cfg.exposure } else { 1.0 };
        let warm = if shader_on && self.cfg.shader_warm_on { self.cfg.warmth } else { 1.0 };
        let vig = if shader_on && self.cfg.shader_vig_on { self.cfg.vignette } else { 0.0 };
        let bloom = if shader_on && self.cfg.shader_bloom_on { self.cfg.bloom } else { 0.0 };
        let tone = if shader_on { self.cfg.tone_map as f32 } else { 0.0 };
        let gamma = if shader_on { self.cfg.gamma } else { 1.0 };
        let ssao = if shader_on { self.cfg.ssao } else { 0.0 };
        let fog = if shader_on { self.cfg.depth_fog } else { 0.0 };

        let params = [
            if self.cfg.fullbright { 1.0 } else { 0.0 },
            sat,
            con,
            bri,
            vib,
            zoom_val,
            if grade_on { 1.0 } else { 0.0 },
            if blur_on { self.cfg.blur_strength } else { 0.0 },
            exp,
            warm,
            vig,
            tone,
            gamma,
            bloom,
            ssao,
            fog,
        ];
        if params != self.last_params {
            render::set_params(&params);
            self.last_params = params;
        }
    }

    fn clear_afk_offset(&mut self) {
        if self.view_offset_set {
            render::set_view_offset(0.0, 0.0, 0.0);
            self.view_offset_set = false;
        }
        self.afk_active = false;
    }

    fn add_waypoint_here(&mut self, name_raw: &str, is_death: bool) -> Result<String, String> {
        let p = game::get_player().ok_or("Nicht in einer Welt")?;
        let trimmed = name_raw.trim();
        let name = if trimmed.is_empty() {
            format!("Wegpunkt {}", self.waypoints.len() + 1)
        } else {
            trimmed.to_string()
        };
        if is_death {
            self.waypoints.retain(|w| !w.is_death);
        } else {
            self.waypoints.retain(|w| !w.name.eq_ignore_ascii_case(&name));
        }
        let wp = Waypoint {
            name: name.clone(),
            x: p.x,
            y: p.y,
            z: p.z,
            dimension: p.dimension,
            is_death,
        };
        let msg = format!("Wegpunkt '{}' ({:.0}, {:.0}, {:.0}) gespeichert.", wp.name, wp.x, wp.y, wp.z);
        self.waypoints.push(wp);
        self.save_waypoints();
        Ok(msg)
    }

    fn is_mod_enabled(&self, id: ModId) -> bool {
        match id {
            ModId::KollegenShader => self.cfg.kollegen_shader,
            ModId::ColorSat => self.cfg.color_sat,
            ModId::Fullbright => self.cfg.fullbright,
            ModId::MotionBlur => self.cfg.motion_blur,
            ModId::Zoom => if self.cfg.zoom_hold_mode { self.zoom_held } else { self.zoom_toggled },
            ModId::AfkCam => self.cfg.afk_enabled,
            ModId::HudCoords => self.cfg.hud_coords,
            ModId::HudFps => self.cfg.hud_fps,
            ModId::HudDirection => self.cfg.hud_direction,
            ModId::HudDayCounter => self.cfg.hud_daycounter,
            ModId::HudClock => self.cfg.hud_clock,
            ModId::HudSpeed => self.cfg.hud_speed,
            ModId::HudHealth => self.cfg.hud_health,
            ModId::HudHeld => self.cfg.hud_held,
            ModId::Waypoints => self.cfg.waypoints_enabled,
        }
    }

    fn toggle_mod(&mut self, id: ModId) {
        match id {
            ModId::KollegenShader => self.cfg.kollegen_shader = !self.cfg.kollegen_shader,
            ModId::ColorSat => self.cfg.color_sat = !self.cfg.color_sat,
            ModId::Fullbright => self.cfg.fullbright = !self.cfg.fullbright,
            ModId::MotionBlur => self.cfg.motion_blur = !self.cfg.motion_blur,
            ModId::Zoom => {
                if self.cfg.zoom_hold_mode {
                    self.cfg.zoom_hold_mode = false;
                    self.zoom_toggled = true;
                } else {
                    self.zoom_toggled = !self.zoom_toggled;
                }
            }
            ModId::AfkCam => {
                self.cfg.afk_enabled = !self.cfg.afk_enabled;
                if !self.cfg.afk_enabled {
                    self.clear_afk_offset();
                }
            }
            ModId::HudCoords => self.cfg.hud_coords = !self.cfg.hud_coords,
            ModId::HudFps => self.cfg.hud_fps = !self.cfg.hud_fps,
            ModId::HudDirection => self.cfg.hud_direction = !self.cfg.hud_direction,
            ModId::HudDayCounter => self.cfg.hud_daycounter = !self.cfg.hud_daycounter,
            ModId::HudClock => self.cfg.hud_clock = !self.cfg.hud_clock,
            ModId::HudSpeed => self.cfg.hud_speed = !self.cfg.hud_speed,
            ModId::HudHealth => self.cfg.hud_health = !self.cfg.hud_health,
            ModId::HudHeld => self.cfg.hud_held = !self.cfg.hud_held,
            ModId::Waypoints => self.cfg.waypoints_enabled = !self.cfg.waypoints_enabled,
        }
        self.save_config();
    }

    fn toggle_bool(&mut self, b: BoolTarget) {
        match b {
            BoolTarget::HudCoords => self.cfg.hud_coords = !self.cfg.hud_coords,
            BoolTarget::HudNether => self.cfg.hud_nether = !self.cfg.hud_nether,
            BoolTarget::HudFps => self.cfg.hud_fps = !self.cfg.hud_fps,
            BoolTarget::HudDirection => self.cfg.hud_direction = !self.cfg.hud_direction,
            BoolTarget::HudCompassBar => self.cfg.hud_compass_bar = !self.cfg.hud_compass_bar,
            BoolTarget::HudDayCounter => self.cfg.hud_daycounter = !self.cfg.hud_daycounter,
            BoolTarget::HudClock => self.cfg.hud_clock = !self.cfg.hud_clock,
            BoolTarget::HudSpeed => self.cfg.hud_speed = !self.cfg.hud_speed,
            BoolTarget::HudHealth => self.cfg.hud_health = !self.cfg.hud_health,
            BoolTarget::HudHeld => self.cfg.hud_held = !self.cfg.hud_held,
            BoolTarget::WpInCompass => self.cfg.wp_in_compass = !self.cfg.wp_in_compass,
            BoolTarget::AutoDeathWp => self.cfg.auto_death_wp = !self.cfg.auto_death_wp,
            BoolTarget::ZoomHoldMode => self.cfg.zoom_hold_mode = !self.cfg.zoom_hold_mode,
            BoolTarget::ShaderSat => { self.cfg.shader_sat_on = !self.cfg.shader_sat_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderVib => { self.cfg.shader_vib_on = !self.cfg.shader_vib_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderCon => { self.cfg.shader_con_on = !self.cfg.shader_con_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderBri => { self.cfg.shader_bri_on = !self.cfg.shader_bri_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderExp => { self.cfg.shader_exp_on = !self.cfg.shader_exp_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderBloom => { self.cfg.shader_bloom_on = !self.cfg.shader_bloom_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderWarm => { self.cfg.shader_warm_on = !self.cfg.shader_warm_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderVig => { self.cfg.shader_vig_on = !self.cfg.shader_vig_on; self.cfg.shader_preset = 0; }
            BoolTarget::ShaderBlur => { self.cfg.shader_blur_on = !self.cfg.shader_blur_on; self.cfg.shader_preset = 0; }
        }
        self.save_config();
    }

    fn slider_bounds(target: SliderTarget) -> (f32, f32, f32) {
        match target {
            SliderTarget::GlassOpacity => (0.25, 0.95, 0.05),
            SliderTarget::Saturation => (0.0, 3.0, 0.05),
            SliderTarget::Vibrance => (0.5, 2.5, 0.05),
            SliderTarget::Contrast => (0.5, 2.0, 0.02),
            SliderTarget::Brightness => (0.5, 2.0, 0.05),
            SliderTarget::Exposure => (0.5, 2.0, 0.02),
            SliderTarget::Bloom => (0.0, 1.0, 0.05),
            SliderTarget::Warmth => (0.7, 1.4, 0.02),
            SliderTarget::Vignette => (0.05, 1.0, 0.05),
            SliderTarget::BlurStrength => (0.05, 0.95, 0.05),
            SliderTarget::Gamma => (0.8, 1.6, 0.02),
            SliderTarget::Ssao => (0.0, 1.0, 0.05),
            SliderTarget::DepthFog => (0.0, 1.0, 0.05),
            SliderTarget::ZoomFactor => (1.0, 16.0, 0.5),
            SliderTarget::AfkDelay => (5.0, 120.0, 5.0),
            SliderTarget::AfkSpeed => (0.1, 2.0, 0.1),
            SliderTarget::AfkDistance => (2.0, 8.0, 0.5),
        }
    }

    fn get_slider_val(&self, target: SliderTarget) -> f32 {
        match target {
            SliderTarget::GlassOpacity => self.cfg.glass_opacity,
            SliderTarget::Saturation => self.cfg.saturation,
            SliderTarget::Vibrance => self.cfg.vibrance,
            SliderTarget::Contrast => self.cfg.contrast,
            SliderTarget::Brightness => self.cfg.brightness,
            SliderTarget::Exposure => self.cfg.exposure,
            SliderTarget::Bloom => self.cfg.bloom,
            SliderTarget::Warmth => self.cfg.warmth,
            SliderTarget::Vignette => self.cfg.vignette,
            SliderTarget::BlurStrength => self.cfg.blur_strength,
            SliderTarget::Gamma => self.cfg.gamma,
            SliderTarget::Ssao => self.cfg.ssao,
            SliderTarget::DepthFog => self.cfg.depth_fog,
            SliderTarget::ZoomFactor => self.cfg.zoom_factor,
            SliderTarget::AfkDelay => self.cfg.afk_delay_secs as f32,
            SliderTarget::AfkSpeed => self.cfg.afk_speed,
            SliderTarget::AfkDistance => self.cfg.afk_distance,
        }
    }

    fn set_slider_val(&mut self, target: SliderTarget, raw: f32) {
        let (min, max, step) = Self::slider_bounds(target);
        let snapped = (((raw - min) / step).round() * step + min).clamp(min, max);
        match target {
            SliderTarget::GlassOpacity => self.cfg.glass_opacity = snapped,
            SliderTarget::Saturation => { self.cfg.saturation = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Vibrance => { self.cfg.vibrance = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Contrast => { self.cfg.contrast = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Brightness => { self.cfg.brightness = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Exposure => { self.cfg.exposure = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Bloom => { self.cfg.bloom = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Warmth => { self.cfg.warmth = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Vignette => { self.cfg.vignette = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::BlurStrength => self.cfg.blur_strength = snapped,
            SliderTarget::Gamma => { self.cfg.gamma = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::Ssao => { self.cfg.ssao = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::DepthFog => { self.cfg.depth_fog = snapped; self.cfg.shader_preset = 0; }
            SliderTarget::ZoomFactor => self.cfg.zoom_factor = snapped,
            SliderTarget::AfkDelay => self.cfg.afk_delay_secs = snapped.round() as u32,
            SliderTarget::AfkSpeed => self.cfg.afk_speed = snapped,
            SliderTarget::AfkDistance => self.cfg.afk_distance = snapped,
        }
        self.save_config();
    }

    fn handle_input_events(&mut self) {
        let events = input::poll();
        let mut redraw = false;
        for ev in events {
            match ev {
                input::Event::KeyDown(name) => match name.as_str() {
                    "menu" => {
                        if self.menu_open {
                            self.menu_open = false;
                            self.search_focused = false;
                            self.wp_input_focused = false;
                            ui::close();
                        } else if game::get_player().is_some() {
                            self.menu_open = true;
                            redraw = true;
                        }
                    }
                    "zoom" => {
                        if self.cfg.zoom_hold_mode {
                            self.zoom_held = true;
                        } else {
                            self.zoom_toggled = !self.zoom_toggled;
                        }
                    }
                    "quick_wp" => {
                        if !self.menu_open && self.cfg.waypoints_enabled {
                            match self.add_waypoint_here("", false) {
                                Ok(msg) => chat::show(&msg),
                                Err(e) => chat::show(&e),
                            }
                        }
                    }
                    _ => {}
                },
                input::Event::KeyUp(name) => {
                    if name == "zoom" && self.cfg.zoom_hold_mode {
                        self.zoom_held = false;
                    }
                }
                input::Event::ScreenClosed => {
                    self.menu_open = false;
                    self.search_focused = false;
                    self.wp_input_focused = false;
                }
                input::Event::ScreenClick(click) => {
                    if click.button == 0 && self.menu_open {
                        self.handle_click(click.x, click.y);
                        redraw = true;
                    }
                }
                _ => {}
            }
        }

        if self.menu_open {
            for t in input::poll_typed() {
                if self.handle_game_key(&t) {
                    redraw = true;
                    continue;
                }
                if self.search_focused {
                    match t {
                        input::Typed::Text(s) => {
                            for ch in s.chars() {
                                if self.search_query.len() < 24 && !ch.is_control() {
                                    self.search_query.push(ch);
                                    self.detail = None;
                                    self.overlay = OverlayView::Modules;
                                    self.mod_page = 0;
                                    redraw = true;
                                }
                            }
                        }
                        input::Typed::Key(k) if k == "key.keyboard.backspace" => {
                            self.search_query.pop();
                            self.mod_page = 0;
                            redraw = true;
                        }
                        _ => {}
                    }
                } else if self.tab == 2 && self.wp_input_focused {
                    match t {
                        input::Typed::Text(s) => {
                            for ch in s.chars() {
                                if self.wp_input.len() < 22 && !ch.is_control() {
                                    self.wp_input.push(ch);
                                    redraw = true;
                                }
                            }
                        }
                        input::Typed::Key(k) => match k.as_str() {
                            "key.keyboard.backspace" => {
                                self.wp_input.pop();
                                redraw = true;
                            }
                            "key.keyboard.enter" => {
                                let label = self.wp_input.clone();
                                self.wp_input.clear();
                                self.wp_input_focused = false;
                                if let Ok(msg) = self.add_waypoint_here(&label, false) {
                                    chat::show(&msg);
                                }
                                redraw = true;
                            }
                            _ => {}
                        },
                    }
                }
            }
        } else {
            let _ = input::poll_typed();
        }

        if self.menu_open {
            match self.extra_game {
                ExtraGame::Snake => {
                    self.snake.step_ticks += 1;
                    if self.snake.step_ticks >= 4 {
                        self.snake.step_ticks = 0;
                        self.snake.step();
                        redraw = true;
                    }
                }
                ExtraGame::Tetris => {
                    self.tetris.fall_ticks += 1;
                    let interval = (12u32).saturating_sub(self.tetris.lines / 5).max(4);
                    if self.tetris.fall_ticks >= interval {
                        self.tetris.fall_ticks = 0;
                        self.tetris.step_down();
                        redraw = true;
                    }
                }
                _ => {}
            }
        }

        if self.menu_open && (redraw || ui::cursor().is_some()) {
            self.render_menu();
        }
    }

    fn handle_game_key(&mut self, t: &input::Typed) -> bool {
        match self.extra_game {
            ExtraGame::Snake => {
                match t {
                    input::Typed::Text(s) => match s.to_ascii_lowercase().as_str() {
                        "w" if self.snake.dir != (0, 1) => self.snake.dir = (0, -1),
                        "s" if self.snake.dir != (0, -1) => self.snake.dir = (0, 1),
                        "a" if self.snake.dir != (1, 0) => self.snake.dir = (-1, 0),
                        "d" if self.snake.dir != (-1, 0) => self.snake.dir = (1, 0),
                        "r" => self.snake = SnakeState::new(self.snake.best),
                        _ => {}
                    },
                    input::Typed::Key(k) => match k.as_str() {
                        "key.keyboard.up" if self.snake.dir != (0, 1) => self.snake.dir = (0, -1),
                        "key.keyboard.down" if self.snake.dir != (0, -1) => self.snake.dir = (0, 1),
                        "key.keyboard.left" if self.snake.dir != (1, 0) => self.snake.dir = (-1, 0),
                        "key.keyboard.right" if self.snake.dir != (-1, 0) => self.snake.dir = (1, 0),
                        "key.keyboard.enter" => self.snake = SnakeState::new(self.snake.best),
                        _ => {}
                    },
                }
                true
            }
            ExtraGame::Tetris => {
                match t {
                    input::Typed::Text(s) => match s.to_ascii_lowercase().as_str() {
                        "a" => self.tetris.shift(-1),
                        "d" => self.tetris.shift(1),
                        "w" => self.tetris.rotate(),
                        "s" => self.tetris.step_down(),
                        " " => self.tetris.hard_drop(),
                        "r" => self.tetris = TetrisState::new(self.tetris.best),
                        _ => {}
                    },
                    input::Typed::Key(k) => match k.as_str() {
                        "key.keyboard.left" => self.tetris.shift(-1),
                        "key.keyboard.right" => self.tetris.shift(1),
                        "key.keyboard.up" => self.tetris.rotate(),
                        "key.keyboard.down" => self.tetris.step_down(),
                        "key.keyboard.space" => self.tetris.hard_drop(),
                        "key.keyboard.enter" => self.tetris = TetrisState::new(self.tetris.best),
                        _ => {}
                    },
                }
                true
            }
            _ => false,
        }
    }

    fn handle_click(&mut self, cx: f32, cy: f32) {
        let mut clicked = None;
        for b in &self.buttons {
            if cx >= b.x && cx <= b.x + b.w && cy >= b.y && cy <= b.y + b.h {
                clicked = Some(b.action);
                break;
            }
        }
        let Some(action) = clicked else {
            self.search_focused = false;
            self.wp_input_focused = false;
            return;
        };
        self.search_focused = matches!(action, Action::FocusSearch);
        self.wp_input_focused = matches!(action, Action::FocusWpInput);

        match action {
            Action::Close => {
                self.menu_open = false;
                ui::close();
            }
            Action::Tab(t) => {
                self.tab = t;
                self.overlay = OverlayView::Modules;
                self.detail = None;
                self.detail_page = 0;
                self.mod_page = 0;
                self.extra_game = ExtraGame::None;
                self.search_query.clear();
            }
            Action::ToggleOverlay(ov) => {
                self.overlay = if self.overlay == ov { OverlayView::Modules } else { ov };
                self.detail = None;
                self.detail_page = 0;
                self.extra_game = ExtraGame::None;
            }
            Action::SetTheme(idx) => {
                let themes = all_themes();
                if let Some(t) = themes.get(idx) {
                    self.cfg.theme_id = t.id.to_string();
                    self.save_config();
                }
            }
            Action::ToggleMod(id) => self.toggle_mod(id),
            Action::OpenDetail(id) => {
                self.detail = Some(id);
                self.detail_page = 0;
                self.overlay = OverlayView::Modules;
            }
            Action::CloseDetail => {
                self.detail = None;
                self.detail_page = 0;
            }
            Action::PageMods(dir) => {
                if dir < 0 {
                    self.mod_page = self.mod_page.saturating_sub(1);
                } else {
                    self.mod_page += 1;
                }
            }
            Action::PageDetail(dir) => {
                if dir < 0 {
                    self.detail_page = self.detail_page.saturating_sub(1);
                } else {
                    self.detail_page += 1;
                }
            }
            Action::ToggleBool(b) => self.toggle_bool(b),
            Action::CycleMode(m) => {
                match m {
                    ModeTarget::ShaderPreset => {
                        let next = (self.cfg.shader_preset + 1) % 8;
                        self.cfg.apply_shader_preset(next);
                    }
                    ModeTarget::ToneMap => {
                        self.cfg.tone_map = (self.cfg.tone_map + 1) % 4;
                        self.cfg.shader_preset = 0;
                    }
                }
                self.save_config();
            }
            Action::SetSliderRatio(target, ratio) => {
                let (min, max, _) = Self::slider_bounds(target);
                self.set_slider_val(target, min + ratio.clamp(0.0, 1.0) * (max - min));
            }
            Action::StepSlider(target, dir) => {
                let (_, _, step) = Self::slider_bounds(target);
                let cur = self.get_slider_val(target);
                self.set_slider_val(target, cur + (dir as f32) * step);
            }
            Action::CycleCorner => {
                self.cfg.hud_corner = (self.cfg.hud_corner + 1) % 4;
                self.save_config();
            }
            Action::FocusSearch | Action::FocusWpInput => {}
            Action::ClearSearch => {
                self.search_query.clear();
                self.mod_page = 0;
            }
            Action::AddWaypointNow => {
                let label = self.wp_input.clone();
                self.wp_input.clear();
                self.wp_input_focused = false;
                if let Ok(msg) = self.add_waypoint_here(&label, false) {
                    chat::show(&msg);
                }
            }
            Action::DeleteWaypoint(idx) => {
                if idx < self.waypoints.len() {
                    self.waypoints.remove(idx);
                    self.save_waypoints();
                    let max_page = self.waypoints.len().saturating_sub(1) / 4;
                    if self.wp_page > max_page {
                        self.wp_page = max_page;
                    }
                }
            }
            Action::PageWaypoints(dir) => {
                let max_page = self.waypoints.len().saturating_sub(1) / 4;
                if dir < 0 {
                    self.wp_page = self.wp_page.saturating_sub(1);
                } else if self.wp_page < max_page {
                    self.wp_page += 1;
                }
            }
            Action::OpenGame(g) => {
                self.extra_game = g;
                match g {
                    ExtraGame::Snake => self.snake = SnakeState::new(self.snake.best),
                    ExtraGame::Tetris => self.tetris = TetrisState::new(self.tetris.best),
                    ExtraGame::Connect4 => self.connect4 = Connect4State::new(),
                    ExtraGame::TicTacToe => {
                        self.ttt_board = [0; 9];
                        self.ttt_status = "Du bist X — wähle ein Feld!";
                    }
                    ExtraGame::None => {}
                }
            }
            Action::CloseGame => self.extra_game = ExtraGame::None,
            Action::SnakeReset => self.snake = SnakeState::new(self.snake.best),
            Action::TetrisReset => self.tetris = TetrisState::new(self.tetris.best),
            Action::TetrisMove(dx) => self.tetris.shift(dx as i32),
            Action::TetrisRotate => self.tetris.rotate(),
            Action::TetrisDrop => self.tetris.hard_drop(),
            Action::Connect4Col(c) => self.connect4.play(c),
            Action::Connect4Reset => self.connect4 = Connect4State::new(),
            Action::TttCell(idx) => self.play_ttt_cell(idx),
            Action::TttReset => {
                self.ttt_board = [0; 9];
                self.ttt_status = "Du bist X — wähle ein Feld!";
            }
        }
    }

    fn play_ttt_cell(&mut self, idx: usize) {
        if idx >= 9 || self.ttt_board[idx] != 0 || ttt_winner(&self.ttt_board) != 0 {
            return;
        }
        self.ttt_board[idx] = 1;
        if ttt_winner(&self.ttt_board) == 1 {
            self.ttt_status = "Du gewinnst! (X)";
            return;
        }
        if self.ttt_board.iter().all(|&c| c != 0) {
            self.ttt_status = "Unentschieden!";
            return;
        }
        let mut pick = None;
        for target in [2u8, 1u8] {
            for i in 0..9 {
                if self.ttt_board[i] == 0 {
                    self.ttt_board[i] = target;
                    let win = ttt_winner(&self.ttt_board) == target;
                    self.ttt_board[i] = 0;
                    if win {
                        pick = Some(i);
                        break;
                    }
                }
            }
            if pick.is_some() {
                break;
            }
        }
        if pick.is_none() && self.ttt_board[4] == 0 {
            pick = Some(4);
        }
        if pick.is_none() {
            for i in [0, 2, 6, 8, 1, 3, 5, 7] {
                if self.ttt_board[i] == 0 {
                    pick = Some(i);
                    break;
                }
            }
        }
        if let Some(ai) = pick {
            self.ttt_board[ai] = 2;
        }
        match ttt_winner(&self.ttt_board) {
            2 => self.ttt_status = "KI gewinnt! (O)",
            _ if self.ttt_board.iter().all(|&c| c != 0) => self.ttt_status = "Unentschieden!",
            _ => self.ttt_status = "Dein Zug (X)",
        }
    }

    fn hovered(&self, x: f32, y: f32, w: f32, h: f32) -> bool {
        ui::cursor().is_some_and(|c| c.x >= x && c.x <= x + w && c.y >= y && c.y <= y + h)
    }

    fn fill_shape(draws: &mut Vec<ui::Draw>, sharp: bool, x: f32, y: f32, w: f32, h: f32, color: u32) {
        if sharp || w < 6.0 || h < 6.0 {
            draws.push(ui::Draw::Fill((ui::Rect { x, y, w, h }, color)));
            return;
        }
        draws.push(ui::Draw::Fill((ui::Rect { x: x + 2.0, y, w: w - 4.0, h: 1.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x: x + 1.0, y: y + 1.0, w: w - 2.0, h: 1.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x, y: y + 2.0, w, h: h - 4.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x: x + 1.0, y: y + h - 2.0, w: w - 2.0, h: 1.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x: x + 2.0, y: y + h - 1.0, w: w - 4.0, h: 1.0 }, color)));
    }

    fn ring_shape(draws: &mut Vec<ui::Draw>, sharp: bool, x: f32, y: f32, w: f32, h: f32, color: u32) {
        let inset = if sharp { 0.0 } else { 2.0 };
        draws.push(ui::Draw::Fill((ui::Rect { x: x + inset, y, w: w - inset * 2.0, h: 1.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x: x + inset, y: y + h - 1.0, w: w - inset * 2.0, h: 1.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x, y: y + inset, w: 1.0, h: h - inset * 2.0 }, color)));
        draws.push(ui::Draw::Fill((ui::Rect { x: x + w - 1.0, y: y + inset, w: 1.0, h: h - inset * 2.0 }, color)));
    }

    fn draw_pill_btn(
        &mut self,
        draws: &mut Vec<ui::Draw>,
        th: Theme,
        x: f32,
        y: f32,
        w: f32,
        h: f32,
        bg: u32,
        border: u32,
        fg_rgb: u32,
        label: &str,
        centered: bool,
        action: Action,
    ) {
        let hov = self.hovered(x, y, w, h);
        let fill = if hov { mix_argb(bg, th.accent, 0.16) } else { bg };
        Self::fill_shape(draws, th.sharp, x, y, w, h, fill);
        Self::ring_shape(draws, th.sharp, x, y, w, h, if hov { th.accent } else { border });
        let tx = if centered { x + w * 0.5 } else { x + 7.0 };
        draws.push(ui::Draw::Text((label.to_string(), ui::Point { x: tx, y: y + (h - 8.0) * 0.5 }, fg_rgb & 0x00FF_FFFF, centered)));
        self.buttons.push(HitRect { x, y, w, h, action });
    }

    fn draw_switch(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, on: bool, action: Action) {
        let w = 26.0;
        let h = 13.0;
        let hov = self.hovered(x, y, w, h);
        let track_base = if on {
            mix_argb(th.accent, th.accent2, 0.35)
        } else {
            with_alpha(th.panel2, 0.85)
        };
        let track = if hov { brighten_argb(track_base) } else { track_base };
        Self::fill_shape(draws, th.sharp, x, y, w, h, track);
        Self::ring_shape(draws, th.sharp, x, y, w, h, if on { th.accent } else { with_alpha(th.border, 0.8) });
        let knob_x = if on { x + w - 11.0 } else { x + 2.0 };
        Self::fill_shape(draws, th.sharp, knob_x, y + 2.0, 9.0, 9.0, 0xFFF8FAFC);
        self.buttons.push(HitRect { x, y, w, h, action });
    }

    fn catalog_mods(&self) -> Vec<(u8, ModId, &'static str, &'static str, bool)> {
        vec![
            (0, ModId::KollegenShader, "Kollegen Shader", "Bloom, Tonemapping, SSAO & Farben", true),
            (0, ModId::ColorSat, "Color Saturation", "Farbsättigung, Kontrast & Vibrance", true),
            (0, ModId::Fullbright, "Fullbright", "Permanente Helligkeit (WGSL)", false),
            (0, ModId::MotionBlur, "Motion Blur", "Flüssige Bewegungsunschärfe", true),
            (0, ModId::Zoom, "Zoom", "Zoomt heran (Taste C)", true),
            (0, ModId::AfkCam, "AFK-Kamera", "Kreiskamera bei Inaktivität", true),
            (1, ModId::HudCoords, "Koordinaten", "X / Y / Z, Dimension & Nether", true),
            (1, ModId::HudFps, "FPS", "Aktuelle Bildwiederholrate", false),
            (1, ModId::HudDirection, "Richtung", "Kompass-Leiste oder Blickrichtung", true),
            (1, ModId::HudDayCounter, "Tageszähler", "Aktueller Minecraft-Tag", false),
            (1, ModId::HudClock, "Uhrzeit (Welt)", "Aktuelle Spielwelt-Tageszeit", false),
            (1, ModId::HudSpeed, "Speed", "Bewegungsgeschwindigkeit in m/s", false),
            (1, ModId::HudHealth, "Leben & Hunger", "Herzen und Sättigung anzeigen", false),
            (1, ModId::HudHeld, "Gehaltenes Item", "Aktuelles Haupthand-Item & Anzahl", false),
            (2, ModId::Waypoints, "Wegpunkte", "Wegpunkte & Todespunkte (Welt)", true),
        ]
    }

    fn mod_meta(id: ModId) -> (&'static str, &'static str) {
        match id {
            ModId::KollegenShader => ("Kollegen Shader", "Eigen anpassbarer Post-Shader mit Presets, Bloom, ACES & SSAO."),
            ModId::ColorSat => ("Color Saturation", "Passt Farbsättigung, Kontrast, Helligkeit und Vibrance an."),
            ModId::Fullbright => ("Fullbright", "Erhellt dunkle Bereiche lokal per WGSL Post-Processing."),
            ModId::MotionBlur => ("Motion Blur", "Bewegungsunschärfe über Horizon History-Buffer."),
            ModId::Zoom => ("Zoom", "Zoomt per Taste C heran (Halten oder Umschalten)."),
            ModId::AfkCam => ("AFK-Kamera", "Rotierende Außenkamera nach Inaktivität."),
            ModId::HudCoords => ("Koordinaten", "Zeigt X / Y / Z, Dimension und Nether-Koordinaten."),
            ModId::HudFps => ("FPS", "Zeigt die geglättete Bildwiederholrate im HUD."),
            ModId::HudDirection => ("Richtung", "Blickrichtung als Kompass-Leiste mit Wegpunkt-Pfeil."),
            ModId::HudDayCounter => ("Tageszähler", "Zeigt den aktuellen Tag der Welt an."),
            ModId::HudClock => ("Uhrzeit (Welt)", "Zeigt die aktuelle Ingame-Uhrzeit (HH:MM) an."),
            ModId::HudSpeed => ("Speed", "Bewegungsgeschwindigkeit in Blöcken pro Sekunde."),
            ModId::HudHealth => ("Leben & Hunger", "Zeigt verbleibende Lebenspunkte und Hunger."),
            ModId::HudHeld => ("Gehaltenes Item", "Zeigt Name und Stapelgröße des Items in der Hand."),
            ModId::Waypoints => ("Wegpunkte", "Speichert Wegpunkte & Todespunkte mit Entfernung und Pfeil."),
        }
    }

    fn draw_mod_card(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, h: f32, id: ModId, title: &str, desc: &str, has_settings: bool) {
        let on = self.is_mod_enabled(id);
        let hov = self.hovered(x, y, w, h);
        let base = with_alpha(th.panel, (self.cfg.glass_opacity * 0.92).clamp(0.42, 0.92));
        let card_bg = if on {
            mix_argb(base, th.accent, if hov { 0.20 } else { 0.12 })
        } else if hov {
            with_alpha(th.panel2, (self.cfg.glass_opacity + 0.12).clamp(0.45, 0.95))
        } else {
            base
        };
        let border = if hov {
            th.accent
        } else if on {
            with_alpha(th.accent, 0.72)
        } else {
            with_alpha(th.border, 0.78)
        };
        Self::fill_shape(draws, th.sharp, x, y, w, h, card_bg);
        if !th.sharp {
            draws.push(ui::Draw::Fill((ui::Rect { x: x + 2.0, y: y + 1.0, w: w - 4.0, h: 1.0 }, 0x1AFFFFFF)));
        }
        Self::ring_shape(draws, th.sharp, x, y, w, h, border);
        if on {
            draws.push(ui::Draw::Fill((ui::Rect { x: x + 1.0, y: y + 7.0, w: 2.0, h: h - 14.0 }, th.accent)));
        }
        draws.push(ui::Draw::Text((title.to_string(), ui::Point { x: x + 9.0, y: y + 7.0 }, th.text & 0x00FF_FFFF, false)));
        draws.push(ui::Draw::Text((desc.to_string(), ui::Point { x: x + 9.0, y: y + 20.0 }, th.muted & 0x00FF_FFFF, false)));
        if has_settings {
            draws.push(ui::Draw::Text(("Einstellungen >".to_string(), ui::Point { x: x + 9.0, y: y + 32.0 }, th.accent & 0x00FF_FFFF, false)));
        }
        self.draw_switch(draws, th, x + w - 33.0, y + 6.0, on, Action::ToggleMod(id));
        let click_act = if has_settings { Action::OpenDetail(id) } else { Action::ToggleMod(id) };
        self.buttons.push(HitRect { x, y, w: w - 35.0, h, action: click_act });
        self.buttons.push(HitRect { x: x + w - 35.0, y: y + 20.0, w: 35.0, h: h - 20.0, action: click_act });
    }

    fn draw_setting_toggle(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, label: &str, val: bool, target: BoolTarget) {
        let bg = with_alpha(th.panel, 0.78);
        Self::fill_shape(draws, th.sharp, x, y, w, 22.0, bg);
        Self::ring_shape(draws, th.sharp, x, y, w, 22.0, with_alpha(th.border, 0.75));
        draws.push(ui::Draw::Text((label.to_string(), ui::Point { x: x + 8.0, y: y + 7.0 }, th.text & 0x00FF_FFFF, false)));
        self.draw_switch(draws, th, x + w - 34.0, y + 4.5, val, Action::ToggleBool(target));
    }

    fn draw_setting_mode(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, label: &str, val: &str, target: ModeTarget) {
        let bg = with_alpha(th.panel, 0.78);
        Self::fill_shape(draws, th.sharp, x, y, w, 22.0, bg);
        Self::ring_shape(draws, th.sharp, x, y, w, 22.0, with_alpha(th.border, 0.75));
        draws.push(ui::Draw::Text((label.to_string(), ui::Point { x: x + 8.0, y: y + 7.0 }, th.text & 0x00FF_FFFF, false)));
        self.draw_pill_btn(draws, th, x + w - 146.0, y + 3.0, 140.0, 16.0, with_alpha(th.panel2, 0.90), with_alpha(th.accent, 0.65), th.accent, val, true, Action::CycleMode(target));
    }

    fn draw_setting_slider(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, label: &str, val_label: String, target: SliderTarget) {
        let (min, max, _) = Self::slider_bounds(target);
        let cur = self.get_slider_val(target);
        let ratio = ((cur - min) / (max - min).max(0.001)).clamp(0.0, 1.0);

        let bg = with_alpha(th.panel, 0.78);
        Self::fill_shape(draws, th.sharp, x, y, w, 22.0, bg);
        Self::ring_shape(draws, th.sharp, x, y, w, 22.0, with_alpha(th.border, 0.75));
        draws.push(ui::Draw::Text((label.to_string(), ui::Point { x: x + 8.0, y: y + 7.0 }, th.text & 0x00FF_FFFF, false)));

        let minus_x = x + w - 160.0;
        let bar_x = minus_x + 17.0;
        let bar_w = 88.0;
        let bar_y = y + 9.0;
        let plus_x = bar_x + bar_w + 4.0;
        let val_x = plus_x + 33.0;

        self.draw_pill_btn(draws, th, minus_x, y + 4.0, 14.0, 14.0, with_alpha(th.panel2, 0.90), with_alpha(th.border, 0.8), th.text, "-", true, Action::StepSlider(target, -1));

        Self::fill_shape(draws, th.sharp, bar_x, bar_y, bar_w, 4.0, with_alpha(th.panel2, 0.95));
        let fill_w = (bar_w * ratio).max(2.0);
        Self::fill_shape(draws, th.sharp, bar_x, bar_y, fill_w, 4.0, th.accent);
        let thumb_x = bar_x + (bar_w - 6.0) * ratio;
        Self::fill_shape(draws, th.sharp, thumb_x, bar_y - 2.0, 6.0, 8.0, 0xFFF8FAFC);

        let segments = 16usize;
        let seg_w = bar_w / segments as f32;
        for s in 0..segments {
            let sr = (s as f32 + 0.5) / segments as f32;
            self.buttons.push(HitRect {
                x: bar_x + s as f32 * seg_w,
                y: y + 3.0,
                w: seg_w,
                h: 16.0,
                action: Action::SetSliderRatio(target, sr),
            });
        }

        self.draw_pill_btn(draws, th, plus_x, y + 4.0, 14.0, 14.0, with_alpha(th.panel2, 0.90), with_alpha(th.border, 0.8), th.text, "+", true, Action::StepSlider(target, 1));
        draws.push(ui::Draw::Text((val_label, ui::Point { x: val_x, y: y + 7.0 }, th.accent & 0x00FF_FFFF, true)));
    }

    fn preset_name(idx: u8) -> &'static str {
        match idx % 8 {
            1 => "BSL Shaders",
            2 => "Complementary Reim.",
            3 => "Complementary Unb.",
            4 => "Bliss Shaders",
            5 => "Vibrant",
            6 => "Cinematic Kino",
            7 => "Warm & Sonnig",
            _ => "Benutzerdefiniert",
        }
    }

    fn tonemap_name(idx: u8) -> &'static str {
        match idx % 4 {
            1 => "Beryl ACES",
            2 => "Reinhard-Jodie",
            3 => "Uncharted 2",
            _ => "Linear",
        }
    }

    fn render_detail(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, h: f32, id: ModId) {
        let (title, desc) = Self::mod_meta(id);
        let on = self.is_mod_enabled(id);
        self.draw_pill_btn(draws, th, x, y, 56.0, 18.0, with_alpha(th.panel2, 0.90), with_alpha(th.border, 0.8), th.text, "< Zurück", true, Action::CloseDetail);
        draws.push(ui::Draw::Text((title.to_string(), ui::Point { x: x + 64.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));
        self.draw_switch(draws, th, x + w - 28.0, y + 3.0, on, Action::ToggleMod(id));
        draws.push(ui::Draw::Text((desc.to_string(), ui::Point { x, y: y + 23.0 }, th.muted & 0x00FF_FFFF, false)));

        let sy = y + 38.0;
        match id {
            ModId::KollegenShader => {
                let page = self.detail_page.min(3);
                self.detail_page = page;
                match page {
                    0 => {
                        self.draw_setting_mode(draws, th, x, sy, w, "Shader-Profil", Self::preset_name(self.cfg.shader_preset), ModeTarget::ShaderPreset);
                        self.draw_setting_mode(draws, th, x, sy + 25.0, w, "Tone Mapping", Self::tonemap_name(self.cfg.tone_map), ModeTarget::ToneMap);
                        self.draw_setting_toggle(draws, th, x, sy + 50.0, w, "Farbsättigung aktiv", self.cfg.shader_sat_on, BoolTarget::ShaderSat);
                        self.draw_setting_slider(draws, th, x, sy + 75.0, w, "Sättigung", format!("{:.0}%", self.cfg.saturation * 100.0), SliderTarget::Saturation);
                        self.draw_setting_toggle(draws, th, x, sy + 100.0, w, "Vibrance aktiv", self.cfg.shader_vib_on, BoolTarget::ShaderVib);
                        self.draw_setting_slider(draws, th, x, sy + 125.0, w, "Vibrance-Stärke", format!("{:.2}", self.cfg.vibrance), SliderTarget::Vibrance);
                    }
                    1 => {
                        self.draw_setting_toggle(draws, th, x, sy, w, "Kontrast aktiv", self.cfg.shader_con_on, BoolTarget::ShaderCon);
                        self.draw_setting_slider(draws, th, x, sy + 25.0, w, "Kontrast", format!("{:.2}", self.cfg.contrast), SliderTarget::Contrast);
                        self.draw_setting_toggle(draws, th, x, sy + 50.0, w, "Helligkeit aktiv", self.cfg.shader_bri_on, BoolTarget::ShaderBri);
                        self.draw_setting_slider(draws, th, x, sy + 75.0, w, "Helligkeit", format!("{:.2}", self.cfg.brightness), SliderTarget::Brightness);
                        self.draw_setting_toggle(draws, th, x, sy + 100.0, w, "Belichtung aktiv", self.cfg.shader_exp_on, BoolTarget::ShaderExp);
                        self.draw_setting_slider(draws, th, x, sy + 125.0, w, "Belichtung", format!("{:.2}", self.cfg.exposure), SliderTarget::Exposure);
                    }
                    2 => {
                        self.draw_setting_toggle(draws, th, x, sy, w, "Bloom aktiv", self.cfg.shader_bloom_on, BoolTarget::ShaderBloom);
                        self.draw_setting_slider(draws, th, x, sy + 25.0, w, "Bloom-Stärke", format!("{:.0}%", self.cfg.bloom * 100.0), SliderTarget::Bloom);
                        self.draw_setting_toggle(draws, th, x, sy + 50.0, w, "Farbtemperatur aktiv", self.cfg.shader_warm_on, BoolTarget::ShaderWarm);
                        self.draw_setting_slider(draws, th, x, sy + 75.0, w, "Temperatur", format!("{:.2}", self.cfg.warmth), SliderTarget::Warmth);
                        self.draw_setting_toggle(draws, th, x, sy + 100.0, w, "Kino-Vignette", self.cfg.shader_vig_on, BoolTarget::ShaderVig);
                        self.draw_setting_slider(draws, th, x, sy + 125.0, w, "Vignetten-Stärke", format!("{:.0}%", self.cfg.vignette * 100.0), SliderTarget::Vignette);
                    }
                    _ => {
                        self.draw_setting_toggle(draws, th, x, sy, w, "Shader Motion Blur", self.cfg.shader_blur_on, BoolTarget::ShaderBlur);
                        self.draw_setting_slider(draws, th, x, sy + 25.0, w, "Blur-Stärke", format!("{:.0}%", self.cfg.blur_strength * 100.0), SliderTarget::BlurStrength);
                        self.draw_setting_slider(draws, th, x, sy + 50.0, w, "Gamma-Korrektur", format!("{:.2}", self.cfg.gamma), SliderTarget::Gamma);
                        self.draw_setting_slider(draws, th, x, sy + 75.0, w, "Ambient Occlusion", format!("{:.0}%", self.cfg.ssao * 100.0), SliderTarget::Ssao);
                        self.draw_setting_slider(draws, th, x, sy + 100.0, w, "Tiefennebel", format!("{:.0}%", self.cfg.depth_fog * 100.0), SliderTarget::DepthFog);
                    }
                }
                self.draw_pill_btn(draws, th, x, y + h - 18.0, 56.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "< Seite", true, Action::PageDetail(-1));
                draws.push(ui::Draw::Text((format!("Shader Seite {}/4", page + 1), ui::Point { x: x + w * 0.5, y: y + h - 14.0 }, th.muted & 0x00FF_FFFF, true)));
                self.draw_pill_btn(draws, th, x + w - 56.0, y + h - 18.0, 56.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "Seite >", true, Action::PageDetail(1));
            }
            ModId::ColorSat => {
                self.draw_setting_slider(draws, th, x, sy, w, "Sättigung", format!("{:.0}%", self.cfg.saturation * 100.0), SliderTarget::Saturation);
                self.draw_setting_slider(draws, th, x, sy + 26.0, w, "Kontrast", format!("{:.2}", self.cfg.contrast), SliderTarget::Contrast);
                self.draw_setting_slider(draws, th, x, sy + 52.0, w, "Helligkeit", format!("{:.2}", self.cfg.brightness), SliderTarget::Brightness);
                self.draw_setting_slider(draws, th, x, sy + 78.0, w, "Vibrance", format!("{:.2}", self.cfg.vibrance), SliderTarget::Vibrance);
            }
            ModId::MotionBlur => {
                self.draw_setting_slider(draws, th, x, sy, w, "Stärke", format!("{:.0}%", self.cfg.blur_strength * 100.0), SliderTarget::BlurStrength);
            }
            ModId::Zoom => {
                self.draw_setting_slider(draws, th, x, sy, w, "Zoom-Faktor", format!("{:.1}x", self.cfg.zoom_factor), SliderTarget::ZoomFactor);
                self.draw_setting_toggle(draws, th, x, sy + 26.0, w, "Taste halten (C)", self.cfg.zoom_hold_mode, BoolTarget::ZoomHoldMode);
            }
            ModId::AfkCam => {
                self.draw_setting_slider(draws, th, x, sy, w, "AFK-Zeit (s)", format!("{}s", self.cfg.afk_delay_secs), SliderTarget::AfkDelay);
                self.draw_setting_slider(draws, th, x, sy + 26.0, w, "Rotations-Tempo", format!("{:.1}", self.cfg.afk_speed), SliderTarget::AfkSpeed);
                self.draw_setting_slider(draws, th, x, sy + 52.0, w, "Kamera-Abstand", format!("{:.1}", self.cfg.afk_distance), SliderTarget::AfkDistance);
            }
            ModId::HudCoords => {
                self.draw_setting_toggle(draws, th, x, sy, w, "Nether / Overworld Umrechnung", self.cfg.hud_nether, BoolTarget::HudNether);
                let lbl = format!("HUD-Ecke: {}", self.corner_label());
                self.draw_pill_btn(draws, th, x, sy + 26.0, w, 22.0, with_alpha(th.panel2, 0.85), th.accent, th.text, &lbl, true, Action::CycleCorner);
            }
            ModId::HudDirection => {
                self.draw_setting_toggle(draws, th, x, sy, w, "Kompass-Leiste", self.cfg.hud_compass_bar, BoolTarget::HudCompassBar);
                self.draw_setting_toggle(draws, th, x, sy + 26.0, w, "Wegpunkt im Kompass anzeigen", self.cfg.wp_in_compass, BoolTarget::WpInCompass);
            }
            ModId::Waypoints => {
                self.draw_setting_toggle(draws, th, x, sy, w, "Im Kompass anzeigen", self.cfg.wp_in_compass, BoolTarget::WpInCompass);
                self.draw_setting_toggle(draws, th, x, sy + 26.0, w, "Todes-Wegpunkte automatisch", self.cfg.auto_death_wp, BoolTarget::AutoDeathWp);
            }
            _ => {}
        }
    }

    fn render_hud_editor(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32) {
        self.draw_pill_btn(draws, th, x, y, 66.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Module", true, Action::ToggleOverlay(OverlayView::HudEditor));
        draws.push(ui::Draw::Text(("HUD-Editor & Position".to_string(), ui::Point { x: x + 74.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));

        let lbl = format!("HUD-Position: {}", self.corner_label());
        self.draw_pill_btn(draws, th, x, y + 24.0, w, 22.0, with_alpha(th.panel2, 0.88), th.accent, th.text, &lbl, true, Action::CycleCorner);

        let toggles = [
            ("Koordinaten", self.cfg.hud_coords, BoolTarget::HudCoords),
            ("Nether / OW", self.cfg.hud_nether, BoolTarget::HudNether),
            ("FPS", self.cfg.hud_fps, BoolTarget::HudFps),
            ("Richtung / Kompass", self.cfg.hud_direction, BoolTarget::HudDirection),
            ("Tageszähler", self.cfg.hud_daycounter, BoolTarget::HudDayCounter),
            ("Uhrzeit (Welt)", self.cfg.hud_clock, BoolTarget::HudClock),
            ("Speed (m/s)", self.cfg.hud_speed, BoolTarget::HudSpeed),
            ("Leben & Hunger", self.cfg.hud_health, BoolTarget::HudHealth),
            ("Gehaltenes Item", self.cfg.hud_held, BoolTarget::HudHeld),
        ];
        let col_w = (w - 8.0) * 0.5;
        for (i, &(label, val, tgt)) in toggles.iter().enumerate() {
            let col = (i % 2) as f32;
            let row = (i / 2) as f32;
            self.draw_setting_toggle(draws, th, x + col * (col_w + 8.0), y + 52.0 + row * 25.0, col_w, label, val, tgt);
        }
    }

    fn render_theme_picker(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32) {
        self.draw_pill_btn(draws, th, x, y, 66.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Module", true, Action::ToggleOverlay(OverlayView::ThemePicker));
        draws.push(ui::Draw::Text(("Erscheinungsbild & Themes".to_string(), ui::Point { x: x + 74.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));

        self.draw_setting_slider(draws, th, x, y + 24.0, w, "Glas-Transparenz", format!("{:.0}%", self.cfg.glass_opacity * 100.0), SliderTarget::GlassOpacity);

        let themes = all_themes();
        let card_w = (w - 16.0) / 3.0;
        let card_h = 46.0;
        for (i, t) in themes.iter().enumerate() {
            let col = (i % 3) as f32;
            let row = (i / 3) as f32;
            let cx = x + col * (card_w + 8.0);
            let cy = y + 54.0 + row * (card_h + 6.0);
            let active = self.cfg.theme_id == t.id;
            let hov = self.hovered(cx, cy, card_w, card_h);
            let bg = if active {
                mix_argb(with_alpha(t.panel, 0.92), t.accent, 0.20)
            } else {
                with_alpha(t.panel, 0.86)
            };
            let border = if hov || active { t.accent } else { t.border };
            Self::fill_shape(draws, t.sharp, cx, cy, card_w, card_h, bg);
            Self::ring_shape(draws, t.sharp, cx, cy, card_w, card_h, border);
            draws.push(ui::Draw::Text((t.name.to_string(), ui::Point { x: cx + 7.0, y: cy + 7.0 }, t.text & 0x00FF_FFFF, false)));
            Self::fill_shape(draws, t.sharp, cx + 7.0, cy + 25.0, 12.0, 10.0, t.bg);
            Self::fill_shape(draws, t.sharp, cx + 22.0, cy + 25.0, 12.0, 10.0, t.accent);
            Self::fill_shape(draws, t.sharp, cx + 37.0, cy + 25.0, 12.0, 10.0, t.accent2);
            Self::fill_shape(draws, t.sharp, cx + 52.0, cy + 25.0, 12.0, 10.0, t.green);
            self.buttons.push(HitRect { x: cx, y: cy, w: card_w, h: card_h, action: Action::SetTheme(i) });
        }
    }

    fn render_waypoints_tab(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32, h: f32) {
        let in_bg = if self.wp_input_focused { with_alpha(th.panel2, 0.95) } else { with_alpha(th.panel, 0.85) };
        let in_border = if self.wp_input_focused { th.accent } else { th.border };
        let in_txt = if self.wp_input.is_empty() {
            if self.wp_input_focused { "Name tippen..._".to_string() } else { "Wegpunkt-Name...".to_string() }
        } else if self.wp_input_focused {
            format!("{}_", self.wp_input)
        } else {
            self.wp_input.clone()
        };
        let in_col = if self.wp_input.is_empty() && !self.wp_input_focused { th.muted } else { th.text };
        self.draw_pill_btn(draws, th, x, y, w - 96.0, 20.0, in_bg, in_border, in_col, &in_txt, false, Action::FocusWpInput);
        self.draw_pill_btn(draws, th, x + w - 90.0, y, 90.0, 20.0, with_alpha(th.green, 0.35), th.green, th.text, "+ Hier setzen", true, Action::AddWaypointNow);

        self.draw_setting_toggle(draws, th, x, y + 24.0, w, "Todes-Wegpunkte automatisch", self.cfg.auto_death_wp, BoolTarget::AutoDeathWp);

        let p = game::get_player();
        let start = self.wp_page * 4;
        let total = self.waypoints.len();
        for row in 0..4 {
            let idx = start + row;
            if idx >= total {
                break;
            }
            let wy = y + 50.0 + (row as f32) * 26.0;
            Self::fill_shape(draws, th.sharp, x, wy, w, 22.0, with_alpha(th.panel, 0.78));
            Self::ring_shape(draws, th.sharp, x, wy, w, 22.0, with_alpha(th.border, 0.75));
            let wp = &self.waypoints[idx];
            let dist = match p.as_ref() {
                Some(pl) if pl.dimension == wp.dimension => {
                    let dx = wp.x - pl.x;
                    let dy = wp.y - pl.y;
                    let dz = wp.z - pl.z;
                    format!("{:.0}m", (dx * dx + dy * dy + dz * dz).sqrt())
                }
                _ => short_dim(&wp.dimension).to_string(),
            };
            let line = format!("{} ({:.0}/{:.0}/{:.0}) [{}]", wp.name, wp.x, wp.y, wp.z, dist);
            let col = if wp.is_death { th.danger } else { th.text };
            draws.push(ui::Draw::Text((line, ui::Point { x: x + 8.0, y: wy + 7.0 }, col & 0x00FF_FFFF, false)));
            self.draw_pill_btn(draws, th, x + w - 24.0, wy + 3.0, 20.0, 16.0, with_alpha(th.danger, 0.30), th.danger, th.text, "X", true, Action::DeleteWaypoint(idx));
        }

        let max_page = total.saturating_sub(1) / 4 + 1;
        self.draw_pill_btn(draws, th, x, y + h - 18.0, 58.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "< Zurück", true, Action::PageWaypoints(-1));
        draws.push(ui::Draw::Text((format!("Seite {}/{} ({} WPs)", self.wp_page + 1, max_page, total), ui::Point { x: x + w * 0.5, y: y + h - 14.0 }, th.muted & 0x00FF_FFFF, true)));
        self.draw_pill_btn(draws, th, x + w - 58.0, y + h - 18.0, 58.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "Weiter >", true, Action::PageWaypoints(1));
    }

    fn render_extras(&mut self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32) {
        match self.extra_game {
            ExtraGame::None => {
                draws.push(ui::Draw::Text(("Extras & Minispiele".to_string(), ui::Point { x, y: y + 2.0 }, th.accent & 0x00FF_FFFF, false)));
                draws.push(ui::Draw::Text(("Direkt im Kollegen-Menü spielbar — lokal & mit Bestscores".to_string(), ui::Point { x, y: y + 14.0 }, th.muted & 0x00FF_FFFF, false)));
                let cw = (w - 8.0) * 0.5;
                let bg = with_alpha(th.panel, 0.82);
                self.draw_pill_btn(draws, th, x, y + 32.0, cw, 54.0, bg, th.border, th.text, "Tetris (A/D/W/Space)", true, Action::OpenGame(ExtraGame::Tetris));
                self.draw_pill_btn(draws, th, x + cw + 8.0, y + 32.0, cw, 54.0, bg, th.border, th.text, "Snake (WASD / Pfeile)", true, Action::OpenGame(ExtraGame::Snake));
                self.draw_pill_btn(draws, th, x, y + 94.0, cw, 54.0, bg, th.border, th.text, "Vier Gewinnt (vs KI)", true, Action::OpenGame(ExtraGame::Connect4));
                self.draw_pill_btn(draws, th, x + cw + 8.0, y + 94.0, cw, 54.0, bg, th.border, th.text, "Tic-Tac-Toe (vs KI)", true, Action::OpenGame(ExtraGame::TicTacToe));
            }
            ExtraGame::Tetris => {
                self.draw_pill_btn(draws, th, x, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Zurück", true, Action::CloseGame);
                draws.push(ui::Draw::Text((format!("TETRIS  Score: {}  Linien: {}  Rekord: {}", self.tetris.score, self.tetris.lines, self.tetris.best), ui::Point { x: x + 64.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));
                self.draw_pill_btn(draws, th, x + w - 56.0, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "Neustart", true, Action::TetrisReset);

                let cell = 10.0;
                let bx = x + 30.0;
                let by = y + 24.0;
                Self::fill_shape(draws, th.sharp, bx - 2.0, by - 2.0, 10.0 * cell + 4.0, 18.0 * cell + 4.0, with_alpha(th.bg, 0.92));
                Self::ring_shape(draws, th.sharp, bx - 2.0, by - 2.0, 10.0 * cell + 4.0, 18.0 * cell + 4.0, th.border);
                for r in 0..18 {
                    for c in 0..10 {
                        let v = self.tetris.board[r][c];
                        if v != 0 {
                            let col = TETRIS_COLORS[((v - 1) as usize) % 7];
                            draws.push(ui::Draw::Fill((ui::Rect { x: bx + c as f32 * cell + 1.0, y: by + r as f32 * cell + 1.0, w: cell - 2.0, h: cell - 2.0 }, col)));
                        }
                    }
                }
                if !self.tetris.over {
                    let pcol = TETRIS_COLORS[self.tetris.piece % 7];
                    for &(cx, cy) in &self.tetris.cells {
                        let gx = self.tetris.px + cx;
                        let gy = self.tetris.py + cy;
                        if (0..10).contains(&gx) && (0..18).contains(&gy) {
                            draws.push(ui::Draw::Fill((ui::Rect { x: bx + gx as f32 * cell + 1.0, y: by + gy as f32 * cell + 1.0, w: cell - 2.0, h: cell - 2.0 }, pcol)));
                        }
                    }
                } else {
                    draws.push(ui::Draw::Text(("Game Over!".to_string(), ui::Point { x: bx + 50.0, y: by + 84.0 }, th.danger & 0x00FF_FFFF, true)));
                }
                let ctrl_x = bx + 118.0;
                draws.push(ui::Draw::Text(("Steuerung:".to_string(), ui::Point { x: ctrl_x, y: by + 4.0 }, th.text & 0x00FF_FFFF, false)));
                draws.push(ui::Draw::Text(("A/D = Links/Rechts".to_string(), ui::Point { x: ctrl_x, y: by + 18.0 }, th.muted & 0x00FF_FFFF, false)));
                draws.push(ui::Draw::Text(("W = Drehen, S = Runter".to_string(), ui::Point { x: ctrl_x, y: by + 30.0 }, th.muted & 0x00FF_FFFF, false)));
                draws.push(ui::Draw::Text(("Leertaste = Drop".to_string(), ui::Point { x: ctrl_x, y: by + 42.0 }, th.muted & 0x00FF_FFFF, false)));
                self.draw_pill_btn(draws, th, ctrl_x, by + 62.0, 42.0, 22.0, with_alpha(th.panel2, 0.88), th.border, th.text, "<", true, Action::TetrisMove(-1));
                self.draw_pill_btn(draws, th, ctrl_x + 48.0, by + 62.0, 42.0, 22.0, with_alpha(th.panel2, 0.88), th.border, th.text, "Dreh", true, Action::TetrisRotate);
                self.draw_pill_btn(draws, th, ctrl_x + 96.0, by + 62.0, 42.0, 22.0, with_alpha(th.panel2, 0.88), th.border, th.text, ">", true, Action::TetrisMove(1));
                self.draw_pill_btn(draws, th, ctrl_x, by + 90.0, 138.0, 22.0, with_alpha(th.accent, 0.35), th.accent, th.text, "Hard Drop", true, Action::TetrisDrop);
            }
            ExtraGame::Snake => {
                self.draw_pill_btn(draws, th, x, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Zurück", true, Action::CloseGame);
                draws.push(ui::Draw::Text((format!("SNAKE   Score: {}   Rekord: {}", self.snake.score, self.snake.best), ui::Point { x: x + 64.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));
                self.draw_pill_btn(draws, th, x + w - 56.0, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "Neustart", true, Action::SnakeReset);

                let cell = 13.0;
                let bx = x + (w - 16.0 * cell) * 0.5;
                let by = y + 26.0;
                Self::fill_shape(draws, th.sharp, bx - 2.0, by - 2.0, 16.0 * cell + 4.0, 12.0 * cell + 4.0, with_alpha(th.bg, 0.92));
                Self::ring_shape(draws, th.sharp, bx - 2.0, by - 2.0, 16.0 * cell + 4.0, 12.0 * cell + 4.0, th.border);
                draws.push(ui::Draw::Fill((ui::Rect { x: bx + self.snake.food.0 as f32 * cell + 1.0, y: by + self.snake.food.1 as f32 * cell + 1.0, w: cell - 2.0, h: cell - 2.0 }, th.danger)));
                for (i, &(sx, sy)) in self.snake.body.iter().enumerate() {
                    let col = if i == 0 { th.accent } else { th.green };
                    draws.push(ui::Draw::Fill((ui::Rect { x: bx + sx as f32 * cell + 1.0, y: by + sy as f32 * cell + 1.0, w: cell - 2.0, h: cell - 2.0 }, col)));
                }
                if self.snake.dead {
                    draws.push(ui::Draw::Text(("Game Over! (R / Neustart)".to_string(), ui::Point { x: bx + 104.0, y: by + 72.0 }, th.danger & 0x00FF_FFFF, true)));
                }
            }
            ExtraGame::Connect4 => {
                self.draw_pill_btn(draws, th, x, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Zurück", true, Action::CloseGame);
                draws.push(ui::Draw::Text((self.connect4.status.to_string(), ui::Point { x: x + 64.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));
                self.draw_pill_btn(draws, th, x + w - 56.0, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "Neu", true, Action::Connect4Reset);

                let cell = 24.0;
                let bx = x + (w - 7.0 * cell) * 0.5;
                let by = y + 42.0;
                for c in 0..7 {
                    self.draw_pill_btn(draws, th, bx + c as f32 * cell + 2.0, y + 22.0, cell - 4.0, 16.0, with_alpha(th.panel2, 0.88), th.border, th.text, "v", true, Action::Connect4Col(c));
                }
                Self::fill_shape(draws, th.sharp, bx - 3.0, by - 3.0, 7.0 * cell + 6.0, 6.0 * cell + 6.0, 0xCC1E3A8A);
                for r in 0..6 {
                    for c in 0..7 {
                        let col = match self.connect4.board[r][c] {
                            1 => th.accent,
                            2 => th.danger,
                            _ => with_alpha(th.bg, 0.85),
                        };
                        Self::fill_shape(draws, false, bx + c as f32 * cell + 3.0, by + r as f32 * cell + 3.0, cell - 6.0, cell - 6.0, col);
                        self.buttons.push(HitRect { x: bx + c as f32 * cell, y: by, w: cell, h: 6.0 * cell, action: Action::Connect4Col(c) });
                    }
                }
            }
            ExtraGame::TicTacToe => {
                self.draw_pill_btn(draws, th, x, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "< Zurück", true, Action::CloseGame);
                draws.push(ui::Draw::Text((self.ttt_status.to_string(), ui::Point { x: x + 64.0, y: y + 5.0 }, th.accent & 0x00FF_FFFF, false)));
                self.draw_pill_btn(draws, th, x + w - 56.0, y, 56.0, 18.0, with_alpha(th.panel2, 0.88), th.border, th.text, "Neu", true, Action::TttReset);

                let ox = x + (w - 138.0) * 0.5;
                let oy = y + 34.0;
                for i in 0..9 {
                    let col_i = (i % 3) as f32;
                    let row_i = (i / 3) as f32;
                    let (lbl, tc) = match self.ttt_board[i] {
                        1 => ("X", th.accent),
                        2 => ("O", th.danger),
                        _ => ("", th.muted),
                    };
                    self.draw_pill_btn(draws, th, ox + col_i * 47.0, oy + row_i * 47.0, 43.0, 43.0, with_alpha(th.panel, 0.86), th.border, tc, lbl, true, Action::TttCell(i));
                }
            }
        }
    }

    fn render_horizon_info(&self, draws: &mut Vec<ui::Draw>, th: Theme, x: f32, y: f32, w: f32) {
        draws.push(ui::Draw::Text(("Horizon WASM API 0.1 Status".to_string(), ui::Point { x, y: y + 2.0 }, th.accent & 0x00FF_FFFF, false)));
        let lines = [
            "Alle verfügbaren Module laufen nativ ohne Hacks:",
            "• Grafik: Kollegen Shader, Color Saturation, Fullbright, Motion Blur, Zoom, AFK",
            "• HUD: Koordinaten, FPS, Kompass, Tageszähler, Weltzeit, Speed, HP, Item",
            "• Welt & Extras: Wegpunkte, Todespunkte, Tetris, Snake, Vier Gewinnt, TTT",
            "",
            "In Horizon API 0.1 technisch nicht exponiert (daher nicht gefaked):",
            "• Gameplay / Performance (AutoSprint, Armor/PotionHUD, Keystrokes, Culling)",
            "• Chat-Filter, Shop, Kosmetika, Capes, Emotes & Haustiere",
        ];
        for (i, line) in lines.iter().enumerate() {
            let bg = if i == 0 || i == 5 { with_alpha(th.panel2, 0.72) } else { with_alpha(th.panel, 0.50) };
            if !line.is_empty() {
                Self::fill_shape(draws, th.sharp, x, y + 18.0 + i as f32 * 20.0, w, 17.0, bg);
                let col = if i == 0 || i == 5 { th.text } else { th.muted };
                draws.push(ui::Draw::Text(((*line).to_string(), ui::Point { x: x + 6.0, y: y + 23.0 + i as f32 * 20.0 }, col & 0x00FF_FFFF, false)));
            }
        }
    }

    fn render_menu(&mut self) {
        self.buttons.clear();
        let th = self.theme();
        let mut draws: Vec<ui::Draw> = Vec::new();
        let pw = 472.0f32;
        let ph = 264.0f32;
        let px = -pw * 0.5;
        let py = -ph * 0.5;

        draws.push(ui::Draw::Fill((ui::Rect { x: -1400.0, y: -1400.0, w: 2800.0, h: 2800.0 }, 0x6606060C)));

        let shell_bg = with_alpha(th.bg, self.cfg.glass_opacity.clamp(0.35, 0.95));
        Self::fill_shape(&mut draws, th.sharp, px, py, pw, ph, shell_bg);
        if !th.sharp {
            draws.push(ui::Draw::Fill((ui::Rect { x: px + 2.0, y: py + 1.0, w: pw - 4.0, h: 1.0 }, 0x22FFFFFF)));
        }
        Self::ring_shape(&mut draws, th.sharp, px, py, pw, ph, with_alpha(th.border, 0.88));

        draws.push(ui::Draw::Sprite(("gui/kollegen_logo".to_string(), ui::Rect { x: px + 10.0, y: py + 5.0, w: 72.0, h: 24.0 }, 0xFFFFFFFF)));

        let sbg = if self.search_focused { with_alpha(th.panel2, 0.95) } else { with_alpha(th.panel, 0.82) };
        let sbord = if self.search_focused { th.accent } else { with_alpha(th.border, 0.85) };
        let stxt = if self.search_query.is_empty() {
            if self.search_focused { "Suche..._".to_string() } else { "Modul suchen".to_string() }
        } else if self.search_focused {
            format!("{}_", self.search_query)
        } else {
            self.search_query.clone()
        };
        let scol = if self.search_query.is_empty() && !self.search_focused { th.muted } else { th.text };
        self.draw_pill_btn(&mut draws, th, px + 92.0, py + 7.0, 206.0, 19.0, sbg, sbord, scol, &stxt, false, Action::FocusSearch);
        if !self.search_query.is_empty() {
            self.draw_pill_btn(&mut draws, th, px + 281.0, py + 9.5, 14.0, 14.0, th.panel2, th.border, th.muted, "x", true, Action::ClearSearch);
        }

        let hud_active = self.overlay == OverlayView::HudEditor;
        let theme_active = self.overlay == OverlayView::ThemePicker;
        self.draw_pill_btn(&mut draws, th, px + 306.0, py + 7.0, 56.0, 19.0, if hud_active { with_alpha(th.accent, 0.30) } else { with_alpha(th.panel2, 0.85) }, if hud_active { th.accent } else { th.border }, th.text, "HUD", true, Action::ToggleOverlay(OverlayView::HudEditor));
        self.draw_pill_btn(&mut draws, th, px + 368.0, py + 7.0, 68.0, 19.0, if theme_active { with_alpha(th.accent, 0.30) } else { with_alpha(th.panel2, 0.85) }, if theme_active { th.accent } else { th.border }, th.text, "Themes", true, Action::ToggleOverlay(OverlayView::ThemePicker));
        self.draw_pill_btn(&mut draws, th, px + 442.0, py + 7.0, 20.0, 19.0, with_alpha(th.danger, 0.32), th.danger, th.text, "X", true, Action::Close);

        draws.push(ui::Draw::Fill((ui::Rect { x: px + 10.0, y: py + 32.0, w: pw - 20.0, h: 1.0 }, 0x22FFFFFF)));

        let sbx = px + 10.0;
        let sby = py + 39.0;
        let sbw = 96.0;
        let sbh = ph - 49.0;
        Self::fill_shape(&mut draws, th.sharp, sbx, sby, sbw, sbh, with_alpha(th.panel, 0.56));
        Self::ring_shape(&mut draws, th.sharp, sbx, sby, sbw, sbh, with_alpha(th.border, 0.60));

        let cats = ["Grafik", "HUD", "Welt", "Extras", "Horizon"];
        for (i, label) in cats.iter().enumerate() {
            let cy_i = sby + 6.0 + (i as f32) * 26.0;
            let active = self.overlay == OverlayView::Modules && self.tab == i as u8 && self.search_query.is_empty();
            let hov = self.hovered(sbx + 5.0, cy_i, sbw - 10.0, 22.0);
            let draw_bg = if active {
                th.accent
            } else if hov {
                with_alpha(th.panel2, 0.72)
            } else {
                0
            };
            let fg = if active { th.bg } else if hov { th.text } else { th.muted };
            if draw_bg != 0 {
                Self::fill_shape(&mut draws, th.sharp, sbx + 5.0, cy_i, sbw - 10.0, 22.0, draw_bg);
            }
            draws.push(ui::Draw::Text(((*label).to_string(), ui::Point { x: sbx + 13.0, y: cy_i + 7.0 }, fg & 0x00FF_FFFF, false)));
            self.buttons.push(HitRect { x: sbx + 5.0, y: cy_i, w: sbw - 10.0, h: 22.0, action: Action::Tab(i as u8) });
        }
        draws.push(ui::Draw::Text((th.name.to_string(), ui::Point { x: sbx + 10.0, y: sby + sbh - 14.0 }, th.muted & 0x00FF_FFFF, false)));

        let cx = sbx + sbw + 10.0;
        let cy = sby;
        let cw = pw - sbw - 30.0;
        let ch = sbh;

        if self.overlay == OverlayView::HudEditor {
            self.render_hud_editor(&mut draws, th, cx, cy, cw);
        } else if self.overlay == OverlayView::ThemePicker {
            self.render_theme_picker(&mut draws, th, cx, cy, cw);
        } else if let Some(detail_id) = self.detail {
            self.render_detail(&mut draws, th, cx, cy, cw, ch, detail_id);
        } else if !self.search_query.is_empty() || matches!(self.tab, 0 | 1) {
            let q = self.search_query.to_ascii_lowercase();
            let filtered: Vec<_> = self
                .catalog_mods()
                .into_iter()
                .filter(|(c, _, title, desc, _)| {
                    if !q.is_empty() {
                        title.to_ascii_lowercase().contains(&q) || desc.to_ascii_lowercase().contains(&q)
                    } else {
                        *c == self.tab
                    }
                })
                .collect();

            if filtered.is_empty() {
                draws.push(ui::Draw::Text(("Keine passenden Module gefunden.".to_string(), ui::Point { x: cx + cw * 0.5, y: cy + 60.0 }, th.muted & 0x00FF_FFFF, true)));
            } else {
                let per_page = 6usize;
                let max_page = filtered.len().saturating_sub(1) / per_page;
                if self.mod_page > max_page {
                    self.mod_page = max_page;
                }
                let start = self.mod_page * per_page;
                let card_w = (cw - 8.0) * 0.5;
                let card_h = 46.0;
                for (slot, &(_, id, title, desc, has_cfg)) in filtered.iter().skip(start).take(per_page).enumerate() {
                    let col_i = (slot % 2) as f32;
                    let row_i = (slot / 2) as f32;
                    self.draw_mod_card(&mut draws, th, cx + col_i * (card_w + 8.0), cy + row_i * (card_h + 7.0), card_w, card_h, id, title, desc, has_cfg);
                }
                if max_page > 0 {
                    self.draw_pill_btn(&mut draws, th, cx, cy + ch - 18.0, 56.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "< Zurück", true, Action::PageMods(-1));
                    draws.push(ui::Draw::Text((format!("Seite {}/{}", self.mod_page + 1, max_page + 1), ui::Point { x: cx + cw * 0.5, y: cy + ch - 14.0 }, th.muted & 0x00FF_FFFF, true)));
                    self.draw_pill_btn(&mut draws, th, cx + cw - 56.0, cy + ch - 18.0, 56.0, 16.0, with_alpha(th.panel2, 0.85), th.border, th.text, "Weiter >", true, Action::PageMods(1));
                }
            }
        } else if self.tab == 2 {
            self.render_waypoints_tab(&mut draws, th, cx, cy, cw, ch);
        } else if self.tab == 3 {
            self.render_extras(&mut draws, th, cx, cy, cw);
        } else {
            self.render_horizon_info(&mut draws, th, cx, cy, cw);
        }

        ui::show(&draws);
    }

    fn update_hud(&mut self, p: Option<&game::Player>) {
        let Some(p) = p else {
            if !self.last_hud.is_empty() {
                hud::clear();
                self.last_hud.clear();
                self.last_corner = 255;
            }
            return;
        };

        let th = self.theme();
        let text_rgb = th.text & 0x00FF_FFFF;
        let accent_rgb = th.accent & 0x00FF_FFFF;
        let danger_rgb = th.danger & 0x00FF_FFFF;
        let green_rgb = th.green & 0x00FF_FFFF;
        let mut rows: Vec<(String, u32)> = Vec::new();

        if self.cfg.hud_coords {
            rows.push((format!("X: {:.1}  Y: {:.1}  Z: {:.1} [{}]", p.x, p.y, p.z, short_dim(&p.dimension)), text_rgb));
            if self.cfg.hud_nether {
                if p.dimension == "minecraft:overworld" {
                    rows.push((format!("Nether: {:.0} / {:.0}", p.x / 8.0, p.z / 8.0), danger_rgb));
                } else if p.dimension == "minecraft:the_nether" {
                    rows.push((format!("Overworld: {:.0} / {:.0}", p.x * 8.0, p.z * 8.0), green_rgb));
                }
            }
        }

        if self.cfg.hud_fps {
            rows.push((format!("FPS: {}", self.fps_display), text_rgb));
        }

        if self.cfg.hud_direction {
            if self.cfg.hud_compass_bar {
                rows.push((compass_strip(p.yaw), accent_rgb));
            } else {
                rows.push((format!("Blick: {} ({:.0}°)", facing_short(p.yaw), p.yaw), text_rgb));
            }
            if self.cfg.waypoints_enabled && self.cfg.wp_in_compass {
                if let Some((wp, dist)) = self
                    .waypoints
                    .iter()
                    .filter(|w| w.dimension == p.dimension)
                    .map(|w| {
                        let dx = w.x - p.x;
                        let dy = w.y - p.y;
                        let dz = w.z - p.z;
                        (w, (dx * dx + dy * dy + dz * dz).sqrt())
                    })
                    .min_by(|a, b| a.1.total_cmp(&b.1))
                {
                    let arrow = bearing_arrow(p.yaw, wp.x - p.x, wp.z - p.z);
                    let col = if wp.is_death { danger_rgb } else { accent_rgb };
                    rows.push((format!("{} {}: {:.0}m", arrow, wp.name, dist), col));
                }
            }
        }

        if self.cfg.hud_daycounter {
            if let Some(clk) = game::get_clock() {
                rows.push((format!("Tag {}", clk.game_time / 24000), text_rgb));
            }
        }

        if self.cfg.hud_clock {
            if let Some(clk) = game::get_clock() {
                let day_ticks = (clk.day_time % 24000) as u32;
                let total_mins = (day_ticks * 1440) / 24000;
                let hh = ((total_mins / 60) + 6) % 24;
                let mm = total_mins % 60;
                rows.push((format!("Weltzeit: {:02}:{:02}", hh, mm), accent_rgb));
            }
        }

        if self.cfg.hud_speed {
            rows.push((format!("Speed: {:.1} m/s", self.speed_bps), text_rgb));
        }

        if self.cfg.hud_health {
            rows.push((format!("HP: {:.0}  Hunger: {}", p.health, p.food), danger_rgb));
        }

        if self.cfg.hud_held {
            if let Some(item) = input::held() {
                let label = item.custom_name.unwrap_or_else(|| {
                    item.id.strip_prefix("minecraft:").unwrap_or(&item.id).replace('_', " ")
                });
                rows.push((format!("Item: {} x{}", label, item.count), text_rgb));
            }
        }

        rows.truncate(16);
        let corner_idx = self.cfg.hud_corner % 4;
        if rows != self.last_hud || corner_idx != self.last_corner {
            if rows.is_empty() {
                hud::clear();
            } else {
                let lines: Vec<hud::Line> = rows
                    .iter()
                    .map(|(text, color)| hud::Line {
                        text: text.chars().take(256).collect(),
                        color: *color,
                    })
                    .collect();
                hud::set_panel(self.hud_corner(), &lines);
            }
            self.last_hud = rows;
            self.last_corner = corner_idx;
        }
    }
}

fn ttt_winner(b: &[u8; 9]) -> u8 {
    const LINES: [[usize; 3]; 8] = [
        [0, 1, 2], [3, 4, 5], [6, 7, 8],
        [0, 3, 6], [1, 4, 7], [2, 5, 8],
        [0, 4, 8], [2, 4, 6],
    ];
    for [a, c, d] in LINES {
        if b[a] != 0 && b[a] == b[c] && b[a] == b[d] {
            return b[a];
        }
    }
    0
}

fn compass_strip(yaw: f32) -> String {
    let deg = yaw.rem_euclid(360.0);
    let dirs = ["S", "SW", "W", "NW", "N", "NE", "E", "SE"];
    let center = ((deg / 45.0).round() as usize) % 8;
    let left = (center + 7) % 8;
    let right = (center + 1) % 8;
    format!("[ {} . | . {} ({:.0}°) . | . {} ]", dirs[left], dirs[center], deg, dirs[right])
}

fn with_alpha(argb: u32, opacity: f32) -> u32 {
    let base_a = ((argb >> 24) & 0xFF) as f32;
    let a = ((if base_a == 0.0 { 255.0 } else { base_a }) * opacity.clamp(0.0, 1.0)).round() as u32;
    (a << 24) | (argb & 0x00FF_FFFF)
}

fn mix_argb(a: u32, b: u32, t: f32) -> u32 {
    let t = t.clamp(0.0, 1.0);
    let aa = ((a >> 24) & 0xFF) as f32;
    let ar = ((a >> 16) & 0xFF) as f32;
    let ag = ((a >> 8) & 0xFF) as f32;
    let ab = (a & 0xFF) as f32;
    let br = ((b >> 16) & 0xFF) as f32;
    let bg = ((b >> 8) & 0xFF) as f32;
    let bb = (b & 0xFF) as f32;
    let r = (ar + (br - ar) * t).round().clamp(0.0, 255.0) as u32;
    let g = (ag + (bg - ag) * t).round().clamp(0.0, 255.0) as u32;
    let bl = (ab + (bb - ab) * t).round().clamp(0.0, 255.0) as u32;
    ((aa as u32) << 24) | (r << 16) | (g << 8) | bl
}

fn brighten_argb(argb: u32) -> u32 {
    let a = argb & 0xFF00_0000;
    let r = (((argb >> 16) & 0xFF) + 18).min(255);
    let g = (((argb >> 8) & 0xFF) + 18).min(255);
    let b = ((argb & 0xFF) + 18).min(255);
    a | (r << 16) | (g << 8) | b
}

fn short_dim(dim: &str) -> &str {
    match dim {
        "minecraft:overworld" => "Overworld",
        "minecraft:the_nether" => "Nether",
        "minecraft:the_end" => "End",
        other => other.strip_prefix("minecraft:").unwrap_or(other),
    }
}

fn facing_short(yaw: f32) -> &'static str {
    let idx = (((yaw + 180.0).rem_euclid(360.0) / 45.0).round() as usize) % 8;
    ["N", "NE", "E", "SE", "S", "SW", "W", "NW"][idx]
}

fn bearing_arrow(yaw: f32, dx: f64, dz: f64) -> &'static str {
    let target_deg = (-dx).atan2(dz).to_degrees() as f32;
    let rel = (target_deg - yaw + 180.0).rem_euclid(360.0) - 180.0;
    if rel.abs() <= 45.0 {
        "^"
    } else if rel > 45.0 && rel < 135.0 {
        ">"
    } else if rel < -45.0 && rel > -135.0 {
        "<"
    } else {
        "v"
    }
}

struct KollegenCompanion;

impl Guest for KollegenCompanion {
    fn init() -> Result<Registration, String> {
        let mut m = Companion::load();
        input::register_key("menu", "Kollegen Menü", "key.keyboard.right.shift");
        input::register_key("zoom", "Zoom", "key.keyboard.c");
        input::register_key("quick_wp", "Schnell-Wegpunkt", "key.keyboard.n");
        m.sync_render();
        STATE.with(|s| *s.borrow_mut() = Some(m));
        log::write(log::Level::Info, "Kollegen Client Mod initialisiert (Rechts-Shift = Menü).");
        Ok(Registration {
            commands: Vec::new(),
            tick: true,
            frame: true,
            chat: false,
        })
    }

    fn tick(_tick: u64) {
        if TICK_GUARD.get() {
            return;
        }
        TICK_GUARD.set(true);
        with_mod(|m| {
            m.handle_input_events();
            let p = game::get_player();
            if let Some(ref pl) = p {
                if let Some((lx, ly, lz, lyaw, lpitch)) = m.last_pos {
                    let dx = pl.x - lx;
                    let dz = pl.z - lz;
                    m.speed_bps = (dx * dx + dz * dz).sqrt() * 20.0;
                    let moved = (pl.x - lx).abs() > 0.01
                        || (pl.y - ly).abs() > 0.01
                        || (pl.z - lz).abs() > 0.01
                        || (pl.yaw - lyaw).abs() > 0.2
                        || (pl.pitch - lpitch).abs() > 0.2;
                    if moved {
                        m.idle_ticks = 0;
                        if m.afk_active {
                            m.clear_afk_offset();
                        }
                    } else {
                        m.idle_ticks = m.idle_ticks.saturating_add(1);
                    }
                }
                if m.cfg.waypoints_enabled && m.cfg.auto_death_wp && m.prev_health > 0.0 && pl.health <= 0.0 {
                    if let Ok(msg) = m.add_waypoint_here("Tod", true) {
                        chat::show(&msg);
                    }
                }
                m.prev_health = pl.health;
                m.last_pos = Some((pl.x, pl.y, pl.z, pl.yaw, pl.pitch));

                let auto_afk = m.cfg.afk_enabled && m.idle_ticks >= m.cfg.afk_delay_secs.saturating_mul(20);
                if auto_afk {
                    m.afk_active = true;
                } else if m.view_offset_set {
                    m.clear_afk_offset();
                }
            } else {
                m.last_pos = None;
                m.speed_bps = 0.0;
                m.clear_afk_offset();
            }

            m.sync_render();
            m.update_hud(p.as_ref());
        });
        TICK_GUARD.set(false);
    }

    fn frame(seconds: f32) {
        with_mod(|m| {
            m.fps_frames += 1;
            m.fps_acc += seconds;
            if m.fps_acc >= 0.5 {
                m.fps_display = (m.fps_frames as f32 / m.fps_acc).round() as u32;
                m.fps_frames = 0;
                m.fps_acc = 0.0;
            }
            if m.afk_active {
                m.afk_angle = (m.afk_angle + seconds * m.cfg.afk_speed) % std::f32::consts::TAU;
                let r = m.cfg.afk_distance as f64;
                let ox = r * (m.afk_angle as f64).cos();
                let oz = r * (m.afk_angle as f64).sin();
                render::set_view_offset(ox, 1.35, oz);
                m.view_offset_set = true;
            }
        });
    }

    fn command(_name: String, _args: String) -> Result<Option<String>, String> {
        Err("Unbekannter Befehl".to_string())
    }

    fn chat(_text: String) {}
}

export!(KollegenCompanion);
