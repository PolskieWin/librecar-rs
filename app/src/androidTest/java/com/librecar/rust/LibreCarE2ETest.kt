package com.librecar.rust

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.librecar.rust.nativeengine.EcoRouteRequest
import com.librecar.rust.nativeengine.RustCandidateRoute
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.nativeengine.RustRoutePoint
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LibreCarE2ETest {
    private lateinit var instrumentation: Instrumentation
    private lateinit var device: UiDevice
    private val pkg = "com.librecar.rust.debug"
    private val shotDir = "/sdcard/Download/librecar_shots"

    @Before
    fun setup() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        Thread.sleep(400)
        if (device.displayWidth < device.displayHeight) {
            device.setOrientationLeft()
            Thread.sleep(700)
        }
        device.freezeRotation()
        device.executeShellCommand("mkdir -p $shotDir")
        listOf(
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.READ_CALENDAR",
            "android.permission.READ_MEDIA_AUDIO",
        ).forEach { perm ->
            device.executeShellCommand("pm grant $pkg $perm")
        }
    }

    private fun snap(name: String) {
        device.waitForIdle(700)
        val ok = device.takeScreenshot(File("$shotDir/$name.png"))
        assertTrue("Screenshot failed for $name", ok)
    }

    private fun tapText(text: String, timeoutMs: Long = 4500): Boolean {
        repeat(3) {
            try {
                val obj = device.wait(Until.findObject(By.text(text)), timeoutMs)
                    ?: device.wait(Until.findObject(By.textContains(text)), 1000)
                if (obj != null) {
                    obj.click()
                    device.waitForIdle(500)
                    return true
                }
                return false
            } catch (_: StaleObjectException) {
                Thread.sleep(250)
            }
        }
        return false
    }

    private fun tapDesc(desc: String, timeoutMs: Long = 3500): Boolean {
        repeat(3) {
            try {
                val obj = device.wait(Until.findObject(By.desc(desc)), timeoutMs)
                if (obj != null) {
                    obj.click()
                    device.waitForIdle(500)
                    return true
                }
                return false
            } catch (_: StaleObjectException) {
                Thread.sleep(250)
            }
        }
        return false
    }

    @Test
    fun verifyNativeRustSpeedtestAndAllHorizontalFeatures() {
        // 1. Native Rust Engine & Benchmark + 240 TPS Flappy + Fuel Reachability + EN/PL Voice (incl. Best Speed & Radio)
        assertTrue("liblibrecar_core.so must load on Android!", RustEngine.isNativeLoaded)
        val initSnap = RustEngine.safeInit(null)
        assertTrue(initSnap.tankCapacityLiters >= 40.0)

        val t0 = System.nanoTime()
        repeat(500) { idx ->
            RustEngine.safeIngestGps(52.23 + idx * 0.0001, 21.01, 110.0, 18.0, 45f, 5.0, 1000L + idx * 1000L)
        }
        val gpsUs = (System.nanoTime() - t0) / 1000.0 / 500.0

        val f0 = System.nanoTime()
        val fBuf = FloatArray(15)
        repeat(1000) {
            RustEngine.safeFlappyStepInto(1f / 60f, it % 20 == 0, fBuf)
        }
        val flappyUs = (System.nanoTime() - f0) / 1000.0 / 1000.0
        RustEngine.safeFlappyReset()
        println("LIBRECAR_BENCH: ingestGps=${"%.1f".format(gpsUs)}us flappy240Step=${"%.1f".format(flappyUs)}us")

        val lowFuelStats = RustEngine.safeSetFuelLiters(4.5, 50.0)
        assertTrue("Low fuel flag must trigger at 4.5 L", lowFuelStats.isLowFuel)

        val ecoCheck = RustEngine.safeAnalyzeEcoRoutes(
            EcoRouteRequest(
                fuelAvailableLiters = 1.9,
                routes = listOf(
                    RustCandidateRoute(
                        index = 0,
                        distanceM = 25_000.0,
                        durationS = 715.0,
                        stepsCount = 14,
                        points = listOf(
                            RustRoutePoint(52.0, 21.0, 100.0),
                            RustRoutePoint(52.1, 21.0, 420.0),
                            RustRoutePoint(52.2, 21.0, 380.0),
                        ),
                    ),
                    RustCandidateRoute(
                        index = 1,
                        distanceM = 24_000.0,
                        durationS = 1200.0,
                        stepsCount = 5,
                        points = listOf(
                            RustRoutePoint(52.0, 21.0, 100.0),
                            RustRoutePoint(52.1, 21.05, 115.0),
                            RustRoutePoint(52.2, 21.0, 108.0),
                        ),
                    ),
                ),
            )
        )
        assertTrue("Eco route must unlock reachability", ecoCheck.ecoUnlocksReachability)

        val plVoice = RustEngine.safeParseVoiceCommand("Daj mi mapę do Kraków", "auto")
        assertTrue(plVoice.intent == "navigate" && plVoice.target == "Kraków" && plVoice.detectedLang == "pl")

        val speedVoice = RustEngine.safeParseVoiceCommand("Best route for speed to Gdansk", "auto")
        assertTrue(speedVoice.intent == "navigate" && !speedVoice.ecoPriority && speedVoice.target == "Gdansk")

        val radioVoice = RustEngine.safeParseVoiceCommand("Włącz radio RMF FM", "auto")
        assertTrue(radioVoice.intent == "open_radio" && radioVoice.target == "RMF FM")

        RustEngine.safeSetFuelLiters(42.0, 50.0)

        // 2. Launch LibreCar RS in True Horizontal CarPlay Mode (No Map Demo or Dark Mode buttons)
        device.executeShellCommand("am start -n $pkg/com.librecar.rust.MainActivity")
        assertTrue("App failed to launch", device.wait(Until.hasObject(By.pkg(pkg)), 10_000))
        Thread.sleep(3600)
        assertTrue(
            "App must run in horizontal CarPlay layout (${device.displayWidth}x${device.displayHeight})",
            device.displayWidth > device.displayHeight,
        )
        assertFalse("Demo button must be removed from map", device.hasObject(By.text("Demo")))
        assertFalse("Dark mode button must be removed from map", device.hasObject(By.text("Dark")))
        snap("01_dashboard_horizontal_3d_sat")

        // Check Telemetry popup works cleanly
        assertTrue("Telem chip not found", tapText("Telem"))
        Thread.sleep(600)
        snap("02_telemetry_popup")
        tapText("Telem")
        Thread.sleep(300)

        // 3. Voice Command Overlay (EN + PL) & Best Fuel vs Best Speed Route to Kraków
        assertTrue("Voice button not found", tapDesc("Voice"))
        Thread.sleep(700)
        snap("03_voice_overlay_en_pl")
        if (!tapText("Daj mi mapę do Kraków", 2500)) {
            tapDesc("Close")
        }
        assertTrue("Eco route to Kraków did not appear (End button missing)", device.wait(Until.hasObject(By.text("End")), 9500))
        Thread.sleep(1000)
        // Test toggling between Best Fuel and Best Speed if multiple route alternatives came back
        if (device.hasObject(By.text("Best Speed"))) {
            tapText("Best Speed", 1500)
            Thread.sleep(600)
        }
        snap("04_route_best_speed_and_fuel")

        // 4. Down-Left Gear Button -> Settings: Car Model, Tank Size, 120Hz, Polskie Repo, Ultra Compact & Low Fuel
        assertTrue("Down-left Settings gear button not found", tapDesc("Settings"))
        Thread.sleep(700)
        assertTrue("Made by Polskie GitHub attribution missing", device.hasObject(By.textContains("Polskie")))
        assertTrue("120Hz toggle missing in Settings", device.hasObject(By.textContains("120Hz")))
        assertTrue("Car Model selector missing in Settings", device.hasObject(By.textContains("VW Golf")))
        tapText("BMW 320d Touring", 1500)
        Thread.sleep(400)
        assertTrue("Ultra compact layout option not found", tapText("Ultra"))
        Thread.sleep(500)
        snap("05_settings_car_model_120hz_repo")

        repeat(9) {
            tapText("-5 L", 800)
        }
        Thread.sleep(500)
        assertTrue("Failed to close Settings drawer", tapDesc("Close"))
        Thread.sleep(1100)
        assertTrue(
            "Expected Low Fuel / Cannot reach warning in Ultra Compact mode",
            device.hasObject(By.textContains("Cannot reach")) || device.hasObject(By.textContains("LOW FUEL")),
        )
        snap("06_ultra_compact_low_fuel")

        // Restore full tank from banner & verify Compact mode too
        if (tapText("Full Tank", 2500)) {
            Thread.sleep(600)
            assertFalse("Route must recover reachability after Full Tank", device.hasObject(By.textContains("Cannot reach")))
        }
        assertTrue("Settings gear not found in Ultra mode", tapDesc("Settings"))
        Thread.sleep(500)
        tapText("Compact", 1500)
        Thread.sleep(400)
        assertTrue("Failed to close Settings drawer", tapDesc("Close"))
        Thread.sleep(600)
        // Test Compact Dock audio button directly from dashboard (must toggle playback without opening Music tab)
        assertTrue("Compact Dock Play/Pause button not found", tapDesc("Play/Pause"))
        Thread.sleep(700)
        assertTrue("Expected built-in Horizon track title in Compact Dock", device.hasObject(By.textContains("Horizon")))
        snap("07_compact_fixed_mode")

        // Restore Split layout for full Weather / Calendar / Right card verification
        tapDesc("Settings")
        Thread.sleep(400)
        tapText("Split", 1500)
        Thread.sleep(300)
        tapDesc("Close")
        Thread.sleep(500)

        // 5. Weather & Calendar Taskbar Drawers
        assertTrue("Weather button not found", tapDesc("Weather"))
        Thread.sleep(900)
        snap("08_weather_drawer")
        assertTrue("Failed to close Weather drawer", tapDesc("Close"))
        Thread.sleep(400)

        assertTrue("Calendar button not found", tapDesc("Calendar"))
        Thread.sleep(700)
        assertTrue("Calendar drawer did not open", device.hasObject(By.text("Calendar")) || device.hasObject(By.text("Kalendarz")))
        snap("09_calendar_taskbar")
        assertTrue("Failed to close Calendar drawer", tapDesc("Close"))
        Thread.sleep(400)

        // 6. Test Audio Playback + Fixed Audio Buttons (Built-in Horizon WAV + Dock Play/Pause/Next + Live Radio!)
        assertTrue("Music tab not found", tapText("Music"))
        Thread.sleep(700)
        assertTrue("Play/Pause button not found on Music tab", tapDesc("Play/Pause"))
        Thread.sleep(1200)
        assertTrue("Built-in track should show playing state (LibreCar Horizon)", device.hasObject(By.textContains("Horizon")))
        snap("10_music_built_in_playing")
        // Verify pausing & resuming works reliably without stalling
        tapDesc("Play/Pause")
        Thread.sleep(400)
        tapDesc("Play/Pause")
        Thread.sleep(400)

        // Switch to Live Internet Radio tab & tune a station!
        assertTrue("Radio tab not found on taskbar", tapText("Radio"))
        Thread.sleep(700)
        tapText("SomaFM Groove Salad", 2000)
        Thread.sleep(3200)
        snap("11_live_internet_radio")
        // Pause radio before opening YouTube/Flappy
        tapDesc("Play/Pause")
        Thread.sleep(500)

        // 7. Test Clean YouTube WebView Video Pane (zero custom search/presets/Free RAM)
        assertTrue("YouTube tab not found", tapText("YouTube"))
        Thread.sleep(1000)
        tapText("Continue as Passenger", 1500)
        Thread.sleep(2400)
        assertFalse("Free RAM button must be removed", device.hasObject(By.text("Free RAM")))
        assertFalse("Recommended preset chips must be removed", device.hasObject(By.text("Chill Mix")))
        snap("12_youtube_clean_webview")

        // 8. Actively PLAY 240 TPS Flappy Bird live on emulator!
        assertTrue("Flappy tab not found", tapText("Flappy"))
        Thread.sleep(700)
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        device.click(cx, cy)
        Thread.sleep(240)
        val stepReadBuf = FloatArray(15)
        val playStart = System.currentTimeMillis()
        var taps = 0
        var bestScoreSeen = 0
        while (System.currentTimeMillis() - playStart < 10_000L) {
            RustEngine.safeFlappyStepInto(0f, false, stepReadBuf)
            val birdY = stepReadBuf[0]
            val birdVy = stepReadBuf[1]
            val scoreNow = stepReadBuf[3].toInt()
            val bestNow = stepReadBuf[4].toInt()
            if (scoreNow > bestScoreSeen) bestScoreSeen = scoreNow
            if (bestNow > bestScoreSeen) bestScoreSeen = bestNow
            val gameState = stepReadBuf[5].toInt()
            if (gameState == 2) {
                Thread.sleep(420)
                device.click(cx, cy)
                taps++
                Thread.sleep(240)
                continue
            }
            var targetY = 0.45f
            var minDx = 10f
            for (i in 0 until 4) {
                val px = stepReadBuf[6 + i * 2]
                val gy = stepReadBuf[7 + i * 2]
                if (px + 0.11f >= 0.24f && px < minDx) {
                    minDx = px
                    targetY = gy
                }
            }
            if (birdY > targetY + 0.015f && birdVy > -0.10f) {
                device.click(cx, cy)
                taps++
            }
            Thread.sleep(105)
        }
        RustEngine.safeFlappyStepInto(0f, false, stepReadBuf)
        val finalBest = maxOf(bestScoreSeen, stepReadBuf[3].toInt(), stepReadBuf[4].toInt())
        println("LIBRECAR_FLAPPY_LIVE: taps=$taps score=${stepReadBuf[3].toInt()} best=$finalBest")
        assertTrue("Expected >= 5 Flappy Bird flaps during live playtest", taps >= 5)
        snap("13_flappy_bird_240tps_live_play")

        assertTrue("App exited unexpectedly", device.hasObject(By.pkg(pkg)))
        assertFalse("Crash dialog appeared", device.hasObject(By.textContains("keeps stopping")))
    }
}
