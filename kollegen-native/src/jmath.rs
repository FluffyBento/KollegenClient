#[inline(always)]
pub fn fast_sin(x: f64) -> f64 {
    x.sin()
}

#[inline(always)]
pub fn fast_cos(x: f64) -> f64 {
    x.cos()
}

#[inline(always)]
pub fn fast_atan2(y: f64, x: f64) -> f64 {
    y.atan2(x)
}

#[inline(always)]
pub fn fast_hypot3d(dx: f64, dy: f64, dz: f64) -> f64 {
    (dx * dx + dy * dy + dz * dz).sqrt()
}

#[inline(always)]
pub fn fast_wrap_degrees(degrees: f64) -> f64 {
    let mut d = degrees % 360.0;
    if d >= 180.0 {
        d -= 360.0;
    }
    if d < -180.0 {
        d += 360.0;
    }
    d
}
