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
        let state = box_in_frustum(
            aabbs[base],
            aabbs[base + 1],
            aabbs[base + 2],
            aabbs[base + 3],
            aabbs[base + 4],
            aabbs[base + 5],
            planes,
        );
        let vis = state != 0;
        results[i] = vis;
        if vis {
            visible_count += 1;
        }
    }
    visible_count
}

#[cfg(target_arch = "x86_64")]
#[target_feature(enable = "avx2,fma")]
unsafe fn batch_cull_u8_avx2(
    aabbs: &[f64],
    limit: usize,
    planes: &[f32; 24],
    results: &mut [u8],
) -> i32 {
    use std::arch::x86_64::*;

    let mut visible_count = 0i32;
    let zero = _mm256_setzero_pd();
    let mut i = 0usize;

    while i + 4 <= limit {
        let b0 = i * 6;
        let b1 = b0 + 6;
        let b2 = b0 + 12;
        let b3 = b0 + 18;

        let min_x = _mm256_setr_pd(aabbs[b0], aabbs[b1], aabbs[b2], aabbs[b3]);
        let min_y = _mm256_setr_pd(aabbs[b0 + 1], aabbs[b1 + 1], aabbs[b2 + 1], aabbs[b3 + 1]);
        let min_z = _mm256_setr_pd(aabbs[b0 + 2], aabbs[b1 + 2], aabbs[b2 + 2], aabbs[b3 + 2]);
        let max_x = _mm256_setr_pd(aabbs[b0 + 3], aabbs[b1 + 3], aabbs[b2 + 3], aabbs[b3 + 3]);
        let max_y = _mm256_setr_pd(aabbs[b0 + 4], aabbs[b1 + 4], aabbs[b2 + 4], aabbs[b3 + 4]);
        let max_z = _mm256_setr_pd(aabbs[b0 + 5], aabbs[b1 + 5], aabbs[b2 + 5], aabbs[b3 + 5]);

        let mut inside_mask = 0x0Fi32;
        let mut p = 0usize;
        while p < 24 {
            let a = planes[p] as f64;
            let b = planes[p + 1] as f64;
            let c = planes[p + 2] as f64;
            let d = planes[p + 3] as f64;

            let va = _mm256_set1_pd(a);
            let vb = _mm256_set1_pd(b);
            let vc = _mm256_set1_pd(c);
            let vd = _mm256_set1_pd(d);

            let px = if a > 0.0 { max_x } else { min_x };
            let py = if b > 0.0 { max_y } else { min_y };
            let pz = if c > 0.0 { max_z } else { min_z };

            let sum = _mm256_fmadd_pd(va, px, _mm256_fmadd_pd(vb, py, _mm256_fmadd_pd(vc, pz, vd)));
            let cmp = _mm256_cmp_pd(sum, zero, _CMP_GE_OQ);
            inside_mask &= _mm256_movemask_pd(cmp);
            if inside_mask == 0 {
                break;
            }
            p += 4;
        }

        let r0 = (inside_mask & 1) as u8;
        let r1 = ((inside_mask >> 1) & 1) as u8;
        let r2 = ((inside_mask >> 2) & 1) as u8;
        let r3 = ((inside_mask >> 3) & 1) as u8;
        results[i] = r0;
        results[i + 1] = r1;
        results[i + 2] = r2;
        results[i + 3] = r3;
        visible_count += (r0 + r1 + r2 + r3) as i32;
        i += 4;
    }

    while i < limit {
        let base = i * 6;
        let vis = box_in_frustum(
            aabbs[base],
            aabbs[base + 1],
            aabbs[base + 2],
            aabbs[base + 3],
            aabbs[base + 4],
            aabbs[base + 5],
            planes,
        ) != 0;
        let v = u8::from(vis);
        results[i] = v;
        visible_count += v as i32;
        i += 1;
    }

    visible_count
}

pub fn batch_cull_u8(
    aabbs: &[f64],
    count: usize,
    planes: &[f32; 24],
    results: &mut [u8],
) -> i32 {
    let limit = count.min(aabbs.len() / 6).min(results.len());
    #[cfg(target_arch = "x86_64")]
    {
        if is_x86_feature_detected!("avx2") && is_x86_feature_detected!("fma") {
            return unsafe { batch_cull_u8_avx2(aabbs, limit, planes, results) };
        }
    }
    let mut visible_count = 0;
    for i in 0..limit {
        let base = i * 6;
        let vis = box_in_frustum(
            aabbs[base],
            aabbs[base + 1],
            aabbs[base + 2],
            aabbs[base + 3],
            aabbs[base + 4],
            aabbs[base + 5],
            planes,
        ) != 0;
        let v = u8::from(vis);
        results[i] = v;
        visible_count += v as i32;
    }
    visible_count
}

#[inline(always)]
fn chunk_in_frustum_f32(
    min_x: f64,
    min_y: f64,
    min_z: f64,
    max_x: f64,
    max_y: f64,
    max_z: f64,
    planes: &[f32; 24],
) -> bool {
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
            return false;
        }
        i += 4;
    }
    true
}

pub fn cull_and_sort_lod_chunks(
    chunks: &[i32],
    count: usize,
    px: i32,
    pz: i32,
    vanilla_dist_sq: i64,
    max_dist_sq: i64,
    planes: Option<&[f32; 24]>,
    out_packed: &mut [u64],
) -> usize {
    let limit = count.min(chunks.len() / 4).min(out_packed.len());
    let mut written = 0usize;

    for i in 0..limit {
        let base = i * 4;
        let cx = chunks[base];
        let cz = chunks[base + 1];
        let min_y = chunks[base + 2] as f64;
        let max_y = chunks[base + 3] as f64;

        let dx = (cx - px) as i64;
        let dz = (cz - pz) as i64;
        let d_sq = dx * dx + dz * dz;
        if d_sq > max_dist_sq || d_sq <= vanilla_dist_sq {
            continue;
        }

        if let Some(p) = planes {
            let x0 = (cx as f64) * 16.0;
            let z0 = (cz as f64) * 16.0;
            if !chunk_in_frustum_f32(x0, min_y, z0, x0 + 16.0, max_y, z0 + 16.0, p) {
                continue;
            }
        }

        let clamped_dsq = (d_sq.min(u32::MAX as i64) as u64) << 32;
        out_packed[written] = clamped_dsq | (i as u64);
        written += 1;
    }

    out_packed[..written].sort_unstable_by(|a, b| (b >> 32).cmp(&(a >> 32)));
    written
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_batch_cull_matches_scalar() {
        let mut planes = [0.0f32; 24];
        planes[0] = 1.0; planes[3] = 10.0;
        planes[4] = -1.0; planes[7] = 10.0;
        planes[8] = 0.0; planes[9] = 1.0; planes[11] = 10.0;
        planes[12] = 0.0; planes[13] = -1.0; planes[15] = 10.0;
        planes[18] = 1.0; planes[19] = 10.0;
        planes[22] = -1.0; planes[23] = 10.0;

        let aabbs = vec![
            -1.0, -1.0, -1.0, 1.0, 1.0, 1.0,
            20.0, 0.0, 0.0, 22.0, 2.0, 2.0,
            0.0, 0.0, 0.0, 2.0, 2.0, 2.0,
            0.0, 0.0, -15.0, 1.0, 1.0, -12.0,
            -5.0, -5.0, -5.0, 5.0, 5.0, 5.0,
        ];
        let mut bools = vec![false; 5];
        let mut bytes = vec![0u8; 5];

        let s_vis = batch_cull_boxes(&aabbs, 5, &planes, &mut bools);
        let simd_vis = batch_cull_u8(&aabbs, 5, &planes, &mut bytes);

        assert_eq!(s_vis, simd_vis);
        assert_eq!(s_vis, 3);
        for i in 0..5 {
            assert_eq!(bytes[i] != 0, bools[i]);
        }
    }

    #[test]
    fn test_lod_cull_and_sort_back_to_front() {
        let chunks = vec![
            10, 10, 0, 64,
            100, 100, 0, 64,
            20, 20, 0, 64,
            5, 5, 0, 64,
        ];
        let mut out = vec![0u64; 4];
        let n = cull_and_sort_lod_chunks(&chunks, 4, 0, 0, 100, 4096, None, &mut out);
        assert_eq!(n, 2);
        assert_eq!((out[0] & 0xFFFF_FFFF) as usize, 2);
        assert_eq!((out[1] & 0xFFFF_FFFF) as usize, 0);
    }
}
