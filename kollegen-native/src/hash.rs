const SEED: u64 = 0xf135_7aea_2e62_a9c5;

#[inline(always)]
pub fn fast_hash_pos(x: i32, y: i32, z: i32) -> u64 {
    let mut h = 0u64;
    h = h.wrapping_add(x as u32 as u64).wrapping_mul(SEED);
    h = h.wrapping_add(y as u32 as u64).wrapping_mul(SEED);
    h = h.wrapping_add(z as u32 as u64).wrapping_mul(SEED);
    h.rotate_left(26)
}

#[inline(always)]
pub fn fast_hash_chunk(chunk_x: i32, chunk_z: i32) -> u64 {
    let mut h = 0u64;
    h = h.wrapping_add(chunk_x as u32 as u64).wrapping_mul(SEED);
    h = h.wrapping_add(chunk_z as u32 as u64).wrapping_mul(SEED);
    h.rotate_left(26)
}
