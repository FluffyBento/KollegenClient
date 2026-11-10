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
    env.new_string("1.26.2").unwrap_or_else(|_| JString::default())
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
    let mut res_bools = vec![false; n];
    let visible = cull::batch_cull_boxes(&box_buf, n, &p_buf, &mut res_bools);
    let u8_res: Vec<u8> = res_bools.into_iter().map(|b| if b { 1 } else { 0 }).collect();
    let _ = env.set_boolean_array_region(&results, 0, &u8_res);
    visible
}
