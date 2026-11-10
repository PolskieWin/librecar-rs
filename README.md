<p align="center">
  <img src="logo.svg" width="96" height="96" alt="LibreCar RS" />
</p>

# LibreCar RS

Open-source (MIT) CarPlay-style driving dashboard for Android 14+, built with a native Rust core and a pure-black Material You interface. Zero trackers, zero telemetry, zero analytics.

## Features

- **Native Rust Core (`rust-core/`)**:
  - `drive_meter.rs`: GPS speed smoothing (`km/h`), ETA calculation, Trip A/B and persistent odometer (`km`), plus fuel tank tracking, range estimation, and low-fuel warnings.
  - `eco_route.rs`: Evaluates candidate OSRM tracks against Open-Meteo elevation profiles, aerodynamic drag, rolling resistance, and stop-and-go losses to highlight the most fuel-efficient track and verify whether the destination is reachable with current fuel.
  - `voice_nlp.rs`: Offline English and Polish (`pl-PL`) voice intent engine supporting navigation (`"Give me the map to..."`, `"Daj mi mapę do..."`), fuel commands (`"Ustaw paliwo 8 litrów"`, `"Tankuję do pełna"`), media, YouTube, Flappy Bird, weather, and calendar.
  - `flappy.rs`: Zero-allocation 60fps Flappy Bird physics engine.
- **3D Satellite Maps (`MapLibre OpenGL`)**: Esri World Imagery satellite tiles, optional Carto dark street labels, 60° 3D camera pitch, Terrarium DEM elevation mesh, and low-bandwidth dark mode.
- **Music & Spotify API**: Built-in local audio player (`MediaStore` + OS `MediaPlayer`) paired with OAuth 2.0 PKCE Spotify Web API control (`librecar://spotify-callback`).
- **YouTube & Idle Game**: Lazy-loaded WebView YouTube player with a one-tap **Free RAM** unload action and passenger safety lock above 10 km/h, plus Flappy Bird for parked breaks.
- **Taskbar Weather & Calendar**: Cached Open-Meteo forecasts with road condition advisories and read-only local `CalendarContract` schedule + quick trip reminders.
- **Caching & Low-End Optimization**: Two-tier memory/disk TTL caching (`CarCache.kt`), connection-pooled OkHttp client, zero-GC JNI array reuse, downscaled artwork bitmaps, R8 full mode, and an auto-detected low-RAM mode.

## Spotify API Setup (Optional)

1. Create a free app on [developer.spotify.com](https://developer.spotify.com/dashboard).
2. Add the redirect URI `librecar://spotify-callback`.
3. Paste your Client ID under **Layout / Settings** and select **Connect Spotify**.

## Building from Source

```bash
cd rust-core && cargo test
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
cd .. && ./gradlew assembleRelease
```

## License

Released under the [MIT License](LICENSE).
