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
        // Force CarPlay landscape orientation
        device.setOrientationNatural()
        device.executeShellCommand("settings put system accelerometer_rotation 0")
        device.executeShellCommand("settings put system user_rotation 1")
        device.executeShellCommand("mkdir -p /sdcard/librecar_shots")
        // Pre-grant runtime permissions so E2E flow tests every feature immediately
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
        device.waitForIdle(800)
        device.takeScreenshot(File("/sdcard/librecar_shots/$name.png"))
    }

    private fun tapText(text: String, timeoutMs: Long = 5000): Boolean {
        val obj = device.wait(Until.findObject(By.text(text)), timeoutMs)
            ?: device.wait(Until.findObject(By.textContains(text)), 1500)
        return if (obj != null) {
            obj.click()
            device.waitForIdle(700)
            true
        } else {
            false
        }
    }

    @Test
    fun verifyNativeRustAndWalkThroughAllFeatures() {
        // 1. Verify real Rust JNI library loaded & math/fuel/voice work on Android
        assertTrue("liblibrecar_core.so must load on Android!", RustEngine.isNativeLoaded)
        val initStats = RustEngine.safeInit(null)
        assertTrue(initStats.tankCapacityLiters >= 40.0)

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

        // Reset tank back to 42L for clean UI showcase first
        RustEngine.safeSetFuelLiters(42.0, 50.0)

        // 2. Launch LibreCar RS UI on Android 14
        device.executeShellCommand("am start -n $pkg/com.librecar.rust.MainActivity")
        assertTrue("App failed to launch", device.wait(Until.hasObject(By.pkg(pkg)), 10_000))
        Thread.sleep(2500)
        snap("01_dashboard_3d_sat")

        // 3. Toggle Demo Drive & check Map styles
        tapText("Demo")
        Thread.sleep(2200)
        snap("02_demo_drive_kmh")

        tapText("Satellite")
        Thread.sleep(1000)
        tapText("Dark")
        Thread.sleep(1000)
        snap("03_map_dark_amoled")
        tapText("3D Sat")
        Thread.sleep(1200)

        // 4. Test Voice Command Overlay (Polish navigation & Low Fuel command)
        val micBtn = device.findObject(By.desc("Voice"))
        micBtn?.click()
        Thread.sleep(800)
        snap("04_voice_overlay_en_pl")
        // Tap quick Polish example chip: "Daj mi mapę do Kraków" or English "Give me the map to Warsaw"
        if (!tapText("Give me the map to Warsaw", 2000)) {
            tapText("Close", 1000)
        }
        Thread.sleep(3500)
        snap("05_route_eco_track")

        // 5. Test Low-Fuel Warning & Reachability Check via quick fuel pill
        if (tapText("km", 1500)) {
            Thread.sleep(500)
        }
        // Open Layout/Settings and drop fuel to trigger Low Fuel Warning + Check Reachability
        tapText("Layout")
        Thread.sleep(700)
        snap("06_settings_layout")
        repeat(8) {
            tapText("-5 L", 1000)
        }
        Thread.sleep(500)
        // Close settings
        device.pressBack()
        Thread.sleep(900)
        snap("07_low_fuel_warning")

        // Restore full tank from Low Fuel banner
        tapText("Full Tank", 1500)
        Thread.sleep(500)

        // 6. Test Weather & Calendar Taskbar Drawers
        tapText("°")
        Thread.sleep(1200)
        snap("08_weather_drawer")
        device.pressBack()
        Thread.sleep(500)

        // Tap top clock/calendar area
        val calBtn = device.findObject(By.textContains("202")) ?: device.findObject(By.textContains(":"))
        calBtn?.click()
        Thread.sleep(800)
        snap("09_calendar_taskbar")
        device.pressBack()
        Thread.sleep(500)

        // 7. Test Music Tab (Local & Spotify)
        tapText("Music")
        Thread.sleep(800)
        snap("10_music_built_in")
        tapText("Spotify")
        Thread.sleep(800)
        snap("11_music_spotify_api")

        // 8. Test YouTube Tab & Free RAM
        tapText("YouTube")
        Thread.sleep(2200)
        snap("12_youtube_player")
        tapText("Free RAM", 1500)
        Thread.sleep(600)

        // 9. Test Flappy Bird Tab (play a few taps)
        tapText("Flappy")
        Thread.sleep(700)
        val cx = device.displayWidth / 2
        val cy = device.displayHeight / 2
        repeat(6) {
            device.click(cx, cy)
            Thread.sleep(220)
        }
        snap("13_flappy_bird")

        // 10. Verify zero crash / app still foregrounded
        assertTrue("App exited unexpectedly", device.hasObject(By.pkg(pkg)))
        assertFalse("Crash dialog appeared", device.hasObject(By.textContains("keeps stopping")))
    }
}
