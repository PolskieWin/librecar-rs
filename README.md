# LibreCar RS — Android 14+ CarPlay in Rust

**LibreCar RS** is an ultra-lightweight, **100% free, zero-tracker** CarPlay-style driving dashboard for **Android 14+** (compatible down to Android 10 / low-end head units), featuring an edge-to-edge **Material You Pure Black OLED** interface and a native **Rust** core engine.

---

## Core Features

- **Native Rust Core (`rust-core/`)**:
  - **Telemetry & KM Counter (`telemetry.rs`)**: Haversine distance (`f64`), stationary GPS jitter gate, EMA-smoothed `km/h` speedometer, dynamic ETA (*Time to Drive*), Trip A / Trip B / Total Odometer `km` persistence, and Eco Driving Score.
  - **Best Track for Fuel Saving (`eco_route.rs`)**: Physics-based fuel consumption model evaluating aerodynamic drag, rolling resistance, elevation climbs/descents (Open-Meteo Elevation API), and intersection stop-and-go penalties across OSRM candidate routes. Highlights the **Best Fuel-Saving Track** in glowing green (`L/100km`, `% fuel saved`, `CO₂`).
  - **Bilingual Voice NLP (`voice_nlp.rs`)**: Full **English & Polish (Polski)** command parsing with Polish diacritic folding (`"Give me the map to Warsaw"`, `"Daj mi mapę do Krakowa"`, `"Najlepsza trasa paliwowa do Gdańska"`, `"Zagraj w Flappy Bird"`, `"Jaka jest pogoda"`, `"Wyzeruj licznik kilometrów"`).
  - **60fps Flappy Bird (`flappy.rs`)**: Zero-GC-allocation Rust physics engine for passengers / parked breaks.
- **3D Satellite Maps (`MapLibre OpenGL`)**:
  - **3D Satellite Hybrid** (Esri World Imagery + dark street labels + 60° tilt + 3D Terrarium DEM relief)
  - **3D Satellite Pure** & **AMOLED Night** (ultra-low bandwidth mode)
  - Built-in **Demo Drive** simulator to test speed, ETA, and 3D tracking indoors.
- **Built-in Music Player + Spotify Web API**:
  - Local device audio player (`MediaStore` + OS `MediaPlayer` — zero external codec bloat) + Rust 16-bar live visualizer.
  - **Spotify Web API (OAuth 2.0 PKCE)**: Connect free with your own Spotify Developer Client ID (`redirect_uri: librecar://spotify-callback`), search tracks, control active Spotify devices, or play 30s previews directly in-app.
- **YouTube CarPlay Player**:
  - Lazy-instantiated hardware-accelerated WebView (0 MB RAM when unused) + **Free RAM** instant unload button + driving safety confirmation above 10 km/h.
- **Calendar Taskbar & Weather Advisory**:
  - Live clock, local `CalendarContract` schedule + quick trip reminders with 1-tap **Eco Drive** navigation.
  - Live **Open-Meteo** weather & road safety warnings (no API key, no tracking).
- **Adjustable Layout Mode & Ultra-Fast Low-End Mode**:
  - Layouts: `CarPlay Split`, `Drive Focus`, `50/50 Media`, `Portrait Stack`.
  - Taskbar placement: `Left (LHD)`, `Right (RHD)`, `Bottom Dock`.
  - Split ratio slider, gloves-friendly touch scale, and **Low-End Mode** (auto-detected on low-RAM devices via `ActivityManager.isLowRamDevice`, disables 3D terrain mesh & extra animations, caps map pixelRatio to 1.0).

---

## Zero-Tracker & Privacy Guarantee

- **No Google Play Services**, no Firebase, no Crashlytics, no Ads, no analytics SDKs.
- Open-source public APIs used only on-demand: **Photon/Nominatim** (geocoding), **OSRM** (routing), **Open-Meteo** (weather & elevation), **Esri/Carto/AWS Terrarium** (map tiles), and optional user-initiated **Spotify PKCE**.

---

## Spotify API Setup (Optional, Free)

1. Go to [developer.spotify.com](https://developer.spotify.com/dashboard) and create an app.
2. Add Redirect URI: `librecar://spotify-callback`.
3. Copy your **Client ID** into **LibreCar RS -> Layout / Settings -> Spotify Client ID** and tap **Connect Spotify (PKCE)**.

---

## Building from Source

```bash
# 1. Run Rust unit tests
cd rust-core && cargo test

# 2. Cross-compile Rust native engine for Android (requires Android NDK + cargo-ndk)
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o ../app/src/main/jniLibs build --release

# 3. Build Release APKs (R8 shrunk + split ABIs)
cd ..
./gradlew assembleRelease
```
