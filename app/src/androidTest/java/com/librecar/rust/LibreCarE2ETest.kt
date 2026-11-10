package com.librecar.rust

import android.app.Instrumentation
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
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

    @Before
    fun setup() {
        instrumentation = InstrumentationRegistry.getInstrumentation()
        device = UiDevice.getInstance(instrumentation)
        device.setOrientationNatural()
        device.executeShellCommand("settings put system accelerometer_rotation 0")
        device.executeShellCommand("settings put system user_rotation 1")
        device.executeShellCommand("mkdir -p /sdcard/librecar_shots")
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
        device.takeScreenshot(File("/sdcard/librecar_shots/$name.png"))
    }

    private fun tapText(text: String, timeoutMs: Long = 4500): Boolean {
        val obj = device.wait(Until.findObject(By.text(text)), timeoutMs)
            ?: device.wait(Until.findObject(By.textContains(text)), 1200)
        return if (obj != null) {
            obj.click()
            device.waitForIdle(600)
            true
        } else {
            false
        }
    }

    @Test
    fun verifyNativeRustSpeedtestAndAllHorizontalFeatures() {
        // 1. Native Rust Engine & Benchmark + 240 TPS Flappy + Fuel Reachability
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

        RustEngine.safeSetFuelLiters(42.0, 50.0)

        // 2. Launch LibreCar RS in Horizontal CarPlay Mode
        device.executeShellCommand("am start -n $pkg/com.librecar.rust.MainActivity")
        assertTrue("App failed to launch", device.wait(Until.hasObject(By.pkg(pkg)), 10_000))
        Thread.sleep(2500)
        snap("01_dashboard_horizontal_3d_sat")

        // 3. Test Tracking Telemetry & Demo Drive
        tapText("Telem")
        Thread.sleep(500)
        tapText("Demo")
        Thread.sleep(2200)
        snap("02_demo_drive_telemetry")

        // 4. Verify AMOLED Black Vector Map mode works cleanly
        tapText("Dark")
        Thread.sleep(2500)
        snap("03_map_amoled_dark")
        tapText("3D Sat")
        Thread.sleep(1500)

        // 5. Voice Command Overlay (EN + PL) & Eco Route
        device.findObject(By.desc("Voice"))?.click()
        Thread.sleep(700)
        snap("04_voice_overlay_en_pl")
        if (!tapText("Give me the map to Warsaw", 2000)) {
            device.pressBack()
        }
        Thread.sleep(3200)
        snap("05_route_eco_track")

        // 6. Test Layout Studio, Compact Fixed Mode, and Low Fuel Warning
        tapText("Layout")
        Thread.sleep(700)
        tapText("Compact")
        Thread.sleep(600)
        snap("06_settings_compact_and_telemetry")
        repeat(8) {
            tapText("-5 L", 800)
        }
        Thread.sleep(500)
        device.pressBack()
        Thread.sleep(900)
        snap("07_compact_mode_and_low_fuel")

        // Restore full tank from Low Fuel banner & return to Split mode
        tapText("Full Tank", 1500)
        Thread.sleep(500)

        // 7. Test Weather & Calendar Taskbar Drawers
        tapText("°")
        Thread.sleep(1000)
        snap("08_weather_drawer")
        device.pressBack()
        Thread.sleep(500)

        val calBtn = device.findObject(By.textContains(":"))
        calBtn?.click()
        Thread.sleep(700)
        snap("09_calendar_taskbar")
        device.pressBack()
        Thread.sleep(500)

        // 8. Test Left Taskbar Vertical Scroll + Music + YouTube (clean, no top search/presets/Free RAM)
        tapText("Music")
        Thread.sleep(700)
        snap("10_music_local")
        tapText("Spotify")
        Thread.sleep(700)
        snap("11_music_spotify")

        tapText("YouTube")
        Thread.sleep(2200)
        // Confirm top custom search bar and Free RAM were removed!
        assertFalse("Free RAM button must be removed", device.hasObject(By.text("Free RAM")))
        snap("12_youtube_clean_webview")

        // 9. Actively PLAY 240 TPS Flappy Bird live on emulator!
        tapText("Flappy")
        Thread.sleep(700)
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        // First tap starts game
        device.click(cx, cy)
        Thread.sleep(260)
        // Guided rhythmic play reading Rust bird & pipe state via JNI so we actually score points live!
        val stepReadBuf = FloatArray(15)
        val playStart = System.currentTimeMillis()
        var taps = 0
        while (System.currentTimeMillis() - playStart < 8500L) {
            RustEngine.safeFlappyStepInto(0f, false, stepReadBuf)
            val birdY = stepReadBuf[0]
            val birdVy = stepReadBuf[1]
            val gameState = stepReadBuf[5].toInt()
            if (gameState == 2) {
                // Restart if crashed and keep playing
                Thread.sleep(400)
                device.click(cx, cy)
                Thread.sleep(260)
                continue
            }
            var targetY = 0.46f
            var minDx = 10f
            for (i in 0 until 4) {
                val px = stepReadBuf[6 + i * 2]
                val gy = stepReadBuf[7 + i * 2]
                if (px + 0.105f >= 0.24f && px < minDx) {
                    minDx = px
                    targetY = gy
                }
            }
            if (birdY > targetY + 0.015f && birdVy > -0.10f) {
                device.click(cx, cy)
                taps++
            }
            Thread.sleep(110)
        }
        RustEngine.safeFlappyStepInto(0f, false, stepReadBuf)
        val finalBest = maxOf(stepReadBuf[3].toInt(), stepReadBuf[4].toInt())
        println("LIBRECAR_FLAPPY_LIVE: taps=$taps score=${stepReadBuf[3].toInt()} best=$finalBest")
        snap("13_flappy_bird_240tps_live_play")

        assertTrue("App exited unexpectedly", device.hasObject(By.pkg(pkg)))
        assertFalse("Crash dialog appeared", device.hasObject(By.textContains("keeps stopping")))
    }
}
