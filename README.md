<p align="center">
  <img src="logo.svg" width="96" height="96" alt="LibreCar RS" />
</p>

# LibreCar RS

Open-source (MIT) CarPlay-style driving dashboard for Android 14+, built with a native Rust core and a pure-black Material You interface. Zero remote trackers, zero analytics — local vehicle telemetry only.

Created by **Polskie** · [github.com/PolskieWin/librecar-rs](https://github.com/PolskieWin/librecar-rs)

## Features

- **Native Rust Core (`rust-core/`)**:
  - `telemetry.rs`: GPS speed smoothing (`km/h`), ETA calculation, Trip A/B and persistent odometer (`km`), plus fuel tank tracking, range estimation, and low-fuel alerts.
  - `eco_route.rs`: Evaluates candidate OSRM tracks against Open-Meteo elevation profiles, vehicle mass, aerodynamic drag, rolling resistance, and stop-and-go losses to highlight both **Best Fuel** and **Best Speed** routes and verify destination fuel reachability.
  - `voice_nlp.rs`: Offline English and Polish (`pl-PL`) voice intent engine supporting navigation (`"Best route for speed to Gdansk"`, `"Daj mi mapę do Kraków"`), fuel commands (`"Ustaw paliwo 8 litrów"`, `"Tankuję do pełna"`), music & loop control, radio, YouTube, Flappy Bird, Geometry Dash, weather, calendar, and layouts.
  - `flappy.rs`: Zero-allocation 240 TPS Flappy Bird physics engine tuned for lighter, relaxed CarPlay breaks.
  - `geometry_dash.rs`: Zero-allocation 240 TPS Geometry Dash cube runner with tap/hold-to-chain-jump, solid landable blocks, and spike obstacles.
- **Fast 3D Satellite Maps (`MapLibre OpenGL`)**: Esri World Imagery + clean Esri Transportation overlay (`prefetchZoomDelta = 0`), HTTP/2 connection pooling (`tileHttpClient`), Terrarium DEM terrain elevation mesh, cached map view across tab switches, and clear **Best Fuel** (green) / **Best Speed** (cyan) route comparison.
- **Music, Live Radio & Spotify API**: Built-in `MediaStore` + bundled audio track player with **Loop** (`All` / `1` / `Off`) and Shuffle, **Live Internet Car Radio** (RMF FM, Radio Eska, Radio Nowy Świat, SomaFM, Radio Paradise + custom stream URLs), and OAuth 2.0 PKCE Spotify Web API control (`librecar://spotify-callback`).
- **YouTube & Mini-Games**: Full-bleed WebView YouTube video player with hardware fullscreen playback support and passenger safety notice above 10 km/h, plus 240 TPS **Flappy Bird** and **Geometry Dash**.
- **Layouts, Down-Left Gear Settings & 120Hz Support**:
  - Taskbar app dock with a dedicated **down-left Gear button** for Layout modes (**Split**, **Compact**, **Ultra Compact**, **Focus**, **50/50**, **Vertical**), Car Model presets & mass/tank capacity, Fuel Tank controls, and **120Hz High Refresh Display** mode selection.
- **Taskbar Weather & Calendar**: Cached Open-Meteo forecasts with road advisories and read-only local `CalendarContract` schedule + quick reminders.
- **Low-End & Launch Optimization**: Lazy two-tier cache (`CarCache.kt`), pooled HTTP/2 client, zero-GC JNI buffers, R8 full mode, 16KB ELF alignment, and low-RAM performance mode.

## Spotify API Setup (Optional)

1. Create a free app on [developer.spotify.com](https://developer.spotify.com/dashboard).
2. Add the redirect URI `librecar://spotify-callback`.
3. Paste your Client ID under **Gear Settings** and select **Connect Spotify**.

## Building from Source

```bash
cd rust-core && cargo test
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release
cd .. && ./gradlew assembleRelease
```

## License

Released under the [MIT License](LICENSE).
