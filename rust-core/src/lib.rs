pub mod eco_route;
pub mod flappy;
pub mod telemetry;
pub mod visualizer;
pub mod voice_nlp;

use std::sync::Mutex;

use jni::objects::{JClass, JFloatArray, JString};
use jni::sys::{jboolean, jdouble, jfloat, jint, jlong, jstring, JNI_TRUE};
use jni::JNIEnv;

use eco_route::{analyze_eco_routes, EcoRouteRequest};
use flappy::FlappyEngine;
use telemetry::TelemetryEngine;
use visualizer::step_visualizer;
use voice_nlp::parse_voice_command;

static TELEMETRY: Mutex<Option<TelemetryEngine>> = Mutex::new(None);
static FLAPPY: Mutex<Option<FlappyEngine>> = Mutex::new(None);
static VIS_BARS: Mutex<[f32; 16]> = Mutex::new([0.08; 16]);

fn with_telemetry<F, R>(f: F) -> R
where
    F: FnOnce(&mut TelemetryEngine) -> R,
{
    let mut guard = TELEMETRY.lock().unwrap_or_else(|e| e.into_inner());
    let engine = guard.get_or_insert_with(|| TelemetryEngine::new(None));
    f(engine)
}

fn with_flappy<F, R>(f: F) -> R
where
    F: FnOnce(&mut FlappyEngine) -> R,
{
    let mut guard = FLAPPY.lock().unwrap_or_else(|e| e.into_inner());
    let engine = guard.get_or_insert_with(FlappyEngine::new);
    f(engine)
}

fn to_jstring(env: &mut JNIEnv, text: &str) -> jstring {
    env.new_string(text)
        .map(|s| s.into_raw())
        .unwrap_or(std::ptr::null_mut())
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_initEngine(
    mut env: JNIEnv,
    _class: JClass,
    saved_state_json: JString,
) -> jstring {
    let saved: Option<String> = if saved_state_json.is_null() {
        None
    } else {
        env.get_string(&saved_state_json).ok().map(|s| s.into())
    };

    let snap_json = {
        let mut guard = TELEMETRY.lock().unwrap_or_else(|e| e.into_inner());
        let engine = TelemetryEngine::new(saved.as_deref());
        let json = serde_json::to_string(&engine.snapshot()).unwrap_or_else(|_| "{}".into());
        *guard = Some(engine);
        json
    };

    to_jstring(&mut env, &snap_json)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_ingestGps(
    mut env: JNIEnv,
    _class: JClass,
    lat: jdouble,
    lon: jdouble,
    altitude_m: jdouble,
    speed_mps: jdouble,
    bearing_deg: jfloat,
    accuracy_m: jdouble,
    timestamp_ms: jlong,
) -> jstring {
    let snap = with_telemetry(|t| {
        t.ingest_gps(
            lat,
            lon,
            altitude_m,
            speed_mps,
            bearing_deg,
            accuracy_m,
            timestamp_ms,
        )
    });
    let json = serde_json::to_string(&snap).unwrap_or_else(|_| "{}".into());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_setRouteRemaining(
    _env: JNIEnv,
    _class: JClass,
    remaining_meters: jdouble,
    expected_duration_sec: jdouble,
) {
    with_telemetry(|t| t.set_route_remaining(remaining_meters, expected_duration_sec));
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_setFuelLiters(
    mut env: JNIEnv,
    _class: JClass,
    liters: jdouble,
    capacity_liters: jdouble,
) -> jstring {
    let snap = with_telemetry(|t| {
        t.set_fuel_liters(liters, capacity_liters);
        t.snapshot()
    });
    let json = serde_json::to_string(&snap).unwrap_or_else(|_| "{}".into());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_fillTank(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let snap = with_telemetry(|t| {
        t.fill_tank();
        t.snapshot()
    });
    let json = serde_json::to_string(&snap).unwrap_or_else(|_| "{}".into());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_resetTripA(
    _env: JNIEnv,
    _class: JClass,
) {
    with_telemetry(|t| t.reset_trip_a());
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_resetTripB(
    _env: JNIEnv,
    _class: JClass,
) {
    with_telemetry(|t| t.reset_trip_b());
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_exportTelemetryState(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    let json = with_telemetry(|t| t.export_persisted_json());
    to_jstring(&mut env, &json)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_analyzeEcoRoutes(
    mut env: JNIEnv,
    _class: JClass,
    routes_json: JString,
) -> jstring {
    let input: String = env
        .get_string(&routes_json)
        .map(|s| s.into())
        .unwrap_or_default();

    let out = match serde_json::from_str::<EcoRouteRequest>(&input) {
        Ok(mut req) => {
            if req.fuel_available_liters < 0.0 {
                req.fuel_available_liters = with_telemetry(|t| t.fuel_liters());
            }
            let resp = analyze_eco_routes(&req);
            serde_json::to_string(&resp).unwrap_or_else(|_| "{}".into())
        }
        Err(_) => "{}".into(),
    };
    to_jstring(&mut env, &out)
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_parseVoiceCommand(
    mut env: JNIEnv,
    _class: JClass,
    text: JString,
    preferred_lang: JString,
) -> jstring {
    let raw_text: String = env.get_string(&text).map(|s| s.into()).unwrap_or_default();
    let lang: String = env
        .get_string(&preferred_lang)
        .map(|s| s.into())
        .unwrap_or_else(|_| "auto".to_string());
    let res = parse_voice_command(&raw_text, &lang);
    let json = serde_json::to_string(&res).unwrap_or_else(|_| "{}".into());
    to_jstring(&mut env, &json)
}

/// Zero-allocation 240 TPS step filling a pre-allocated FloatArray(15).
#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_flappyStepInto(
    env: JNIEnv,
    _class: JClass,
    dt_sec: jfloat,
    flap: jboolean,
    out_buf: JFloatArray,
) {
    let state = with_flappy(|f| f.step(dt_sec, flap == JNI_TRUE));
    let _ = env.set_float_array_region(&out_buf, 0, &state);
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_flappyReset(
    _env: JNIEnv,
    _class: JClass,
) {
    with_flappy(|f| f.reset());
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_flappySetBest(
    _env: JNIEnv,
    _class: JClass,
    best: jint,
) {
    with_flappy(|f| f.set_best_score(best.max(0) as u32));
}

#[no_mangle]
pub extern "system" fn Java_com_librecar_rust_nativeengine_RustEngine_stepVisualizerInto(
    env: JNIEnv,
    _class: JClass,
    playing: jboolean,
    phase_ms: jlong,
    dt_sec: jfloat,
    out_buf: JFloatArray,
) {
    let mut guard = VIS_BARS.lock().unwrap_or_else(|e| e.into_inner());
    step_visualizer(&mut guard, playing == JNI_TRUE, phase_ms, dt_sec);
    let _ = env.set_float_array_region(&out_buf, 0, &*guard);
}
