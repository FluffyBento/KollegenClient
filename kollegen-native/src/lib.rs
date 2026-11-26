pub mod cull;
pub mod hash;
pub mod jmath;
pub mod system;
pub mod threads;

use jni::JNIEnv;
use jni::objects::{JClass, JDoubleArray, JFloatArray, JBooleanArray, JString};
use jni::sys::{jboolean, jclass, jdouble, jint, jlong, JNI_FALSE, JNI_TRUE};

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeInit(
    _env: JNIEnv,
    _class: jclass,
) -> jboolean {
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeIsAvailable(
    _env: JNIEnv,
    _class: jclass,
) -> jboolean {
    JNI_TRUE
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeGetVersion<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    env.new_string("2.0.0").unwrap_or_else(|_| JString::default())
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeGetFeatures<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
) -> JString<'local> {
    let s = system::feature_string();
    env.new_string(s).unwrap_or_else(|_| JString::default())
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFavourCurrentThread(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if threads::favour_current_thread() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeIdleCurrentThread(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    if threads::idle_current_thread() {
        JNI_TRUE
    } else {
        JNI_FALSE
    }
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFastSin(
    _env: JNIEnv,
    _class: JClass,
    rad: jdouble,
) -> jdouble {
    jmath::fast_sin(rad)
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFastCos(
    _env: JNIEnv,
    _class: JClass,
    rad: jdouble,
) -> jdouble {
    jmath::fast_cos(rad)
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFastDistance3D(
    _env: JNIEnv,
    _class: JClass,
    dx: jdouble,
    dy: jdouble,
    dz: jdouble,
) -> jdouble {
    jmath::fast_hypot3d(dx, dy, dz)
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFastHashPos(
    _env: JNIEnv,
    _class: JClass,
    x: jint,
    y: jint,
    z: jint,
) -> jlong {
    hash::fast_hash_pos(x, y, z) as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeGetMemoryRssBytes(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    system::resident_memory_bytes() as jlong
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeFastBoxInFrustum<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    min_x: jdouble,
    min_y: jdouble,
    min_z: jdouble,
    max_x: jdouble,
    max_y: jdouble,
    max_z: jdouble,
    planes: JFloatArray<'local>,
) -> jint {
    let mut p_buf = [0.0f32; 24];
    if env.get_float_array_region(&planes, 0, &mut p_buf).is_err() {
        return 1;
    }
    cull::box_in_frustum(min_x, min_y, min_z, max_x, max_y, max_z, &p_buf)
}

#[no_mangle]
pub extern "system" fn Java_dev_kollegen_client_nativebridge_KollegenNative_nativeBatchFrustumCull<'local>(
    env: JNIEnv<'local>,
    _class: JClass<'local>,
    aabbs: JDoubleArray<'local>,
    count: jint,
    planes: JFloatArray<'local>,
    results: JBooleanArray<'local>,
) -> jint {
    if count <= 0 {
        return 0;
    }
    let mut p_buf = [0.0f32; 24];
    if env.get_float_array_region(&planes, 0, &mut p_buf).is_err() {
        return 0;
    }
    let n = count as usize;
    let mut box_buf = vec![0.0f64; n * 6];
    if env.get_double_array_region(&aabbs, 0, &mut box_buf).is_err() {
        return 0;
    }
    let mut res_u8 = vec![0u8; n];
    let visible = cull::batch_cull_u8(&box_buf, n, &p_buf, &mut res_u8);
    let _ = env.set_boolean_array_region(&results, 0, &res_u8);
    visible
}

#[no_mangle]
pub extern "C" fn kollegen_native_version() -> u32 {
    200
}

#[no_mangle]
pub extern "C" fn kollegen_favour_thread() -> i32 {
    i32::from(threads::favour_current_thread())
}

#[no_mangle]
pub extern "C" fn kollegen_idle_thread() -> i32 {
    i32::from(threads::idle_current_thread())
}

#[no_mangle]
pub extern "C" fn kollegen_rss_bytes() -> u64 {
    system::resident_memory_bytes()
}

#[no_mangle]
pub unsafe extern "C" fn kollegen_batch_cull_f64(
    aabbs_ptr: *const f64,
    count: i32,
    planes_ptr: *const f32,
    out_ptr: *mut u8,
) -> i32 {
    if count <= 0 || aabbs_ptr.is_null() || planes_ptr.is_null() || out_ptr.is_null() {
        return 0;
    }
    let n = count as usize;
    let aabbs = std::slice::from_raw_parts(aabbs_ptr, n * 6);
    let planes: &[f32; 24] = &*(planes_ptr as *const [f32; 24]);
    let out = std::slice::from_raw_parts_mut(out_ptr, n);
    cull::batch_cull_u8(aabbs, n, planes, out)
}

#[no_mangle]
pub unsafe extern "C" fn kollegen_lod_cull_and_sort(
    chunks_ptr: *const i32,
    count: i32,
    px: i32,
    pz: i32,
    vanilla_dist_sq: i64,
    max_dist_sq: i64,
    planes_ptr: *const f32,
    out_ptr: *mut u64,
) -> i32 {
    if count <= 0 || chunks_ptr.is_null() || out_ptr.is_null() {
        return 0;
    }
    let n = count as usize;
    let chunks = std::slice::from_raw_parts(chunks_ptr, n * 4);
    let planes = if planes_ptr.is_null() {
        None
    } else {
        Some(&*(planes_ptr as *const [f32; 24]))
    };
    let out = std::slice::from_raw_parts_mut(out_ptr, n);
    cull::cull_and_sort_lod_chunks(
        chunks,
        n,
        px,
        pz,
        vanilla_dist_sq,
        max_dist_sq,
        planes,
        out,
    ) as i32
}
