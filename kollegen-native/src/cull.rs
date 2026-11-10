#[inline(always)]
pub fn box_in_frustum(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    planes: &[f32; 24],
) -> i32 {
    let mut fully_inside = true;
    let mut i = 0;
    while i < 24 {
        let a = planes[i] as f64;
        let b = planes[i + 1] as f64;
        let c = planes[i + 2] as f64;
        let d = planes[i + 3] as f64;

        let px = if a > 0.0 { max_x } else { min_x };
        let py = if b > 0.0 { max_y } else { min_y };
        let pz = if c > 0.0 { max_z } else { min_z };

        if a * px + b * py + c * pz + d < 0.0 {
            return 0;
        }

        if fully_inside {
            let nx = if a > 0.0 { min_x } else { max_x };
            let ny = if b > 0.0 { min_y } else { max_y };
            let nz = if c > 0.0 { min_z } else { max_z };
            if a * nx + b * ny + c * nz + d < 0.0 {
                fully_inside = false;
            }
        }
        i += 4;
    }
    if fully_inside {
        2
    } else {
        1
    }
}

pub fn batch_cull_boxes(
    aabbs: &[f64],
    count: usize,
    planes: &[f32; 24],
    results: &mut [bool],
) -> i32 {
    let mut visible_count = 0;
    let limit = count.min(aabbs.len() / 6).min(results.len());
    for i in 0..limit {
        let base = i * 6;
        let min_x = aabbs[base];
        let min_y = aabbs[base + 1];
        let min_z = aabbs[base + 2];
        let max_x = aabbs[base + 3];
        let max_y = aabbs[base + 4];
        let max_z = aabbs[base + 5];

        let state = box_in_frustum(min_x, min_y, min_z, max_x, max_y, max_z, planes);
        let vis = state != 0;
        results[i] = vis;
        if vis {
            visible_count += 1;
        }
    }
    visible_count
}
