package com.librecar.rust.data

import android.app.ActivityManager
import android.content.Context
import androidx.core.content.edit
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class LayoutMode(val id: String, val labelEn: String, val labelPl: String) {
    SPLIT_DASH("split", "Split", "Podział"),
    COMPACT_FIXED("compact", "Compact", "Kompakt"),
    DRIVE_FOCUS("focus", "Focus", "Jazda"),
    MEDIA_SPLIT("media", "50/50", "50/50"),
    COMPACT_PORTRAIT("portrait", "Vertical", "Pionowy");

    companion object {
        fun fromId(id: String): LayoutMode = entries.firstOrNull { it.id == id } ?: SPLIT_DASH
    }
}

enum class TaskbarPosition(val id: String, val labelEn: String, val labelPl: String) {
    LEFT("left", "Left", "Lewy"),
    RIGHT("right", "Right", "Prawy"),
    BOTTOM("bottom", "Bottom", "Dolny");

    companion object {
        fun fromId(id: String): TaskbarPosition = entries.firstOrNull { it.id == id } ?: LEFT
    }
}

enum class TouchScale(val id: String, val scale: Float, val labelEn: String, val labelPl: String) {
    COMPACT("compact", 0.90f, "Small", "Mały"),
    STANDARD("standard", 1.0f, "Standard", "Średni"),
    XL_GLOVES("xl", 1.18f, "Large", "Duży");

    companion object {
        fun fromId(id: String): TouchScale = entries.firstOrNull { it.id == id } ?: STANDARD
    }
}

enum class MapVisualMode(val id: String) {
    SATELLITE_3D_HYBRID("sat_hybrid"),
    SATELLITE_3D_PURE("sat_pure"),
    AMOLED_NIGHT("amoled_night");

    companion object {
        fun fromId(id: String): MapVisualMode = entries.firstOrNull { it.id == id } ?: SATELLITE_3D_HYBRID
    }
}

@Serializable
data class LocalCalendarReminder(
    val id: Long,
    val title: String,
    val location: String,
    val timeLabel: String,
)

class CarPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("librecar_prefs", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val systemLowRam: Boolean = runCatching {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }.getOrDefault(false)

    var layoutMode: LayoutMode
        get() = LayoutMode.fromId(prefs.getString("layout_mode", LayoutMode.SPLIT_DASH.id) ?: LayoutMode.SPLIT_DASH.id)
        set(v) = prefs.edit { putString("layout_mode", v.id) }

    var taskbarPosition: TaskbarPosition
        get() = TaskbarPosition.fromId(prefs.getString("taskbar_pos", TaskbarPosition.LEFT.id) ?: TaskbarPosition.LEFT.id)
        set(v) = prefs.edit { putString("taskbar_pos", v.id) }

    var splitRatio: Float
        get() = prefs.getFloat("split_ratio", 0.60f).coerceIn(0.45f, 0.75f)
        set(v) = prefs.edit { putFloat("split_ratio", v.coerceIn(0.45f, 0.75f)) }

    var touchScale: TouchScale
        get() = TouchScale.fromId(prefs.getString("touch_scale", TouchScale.STANDARD.id) ?: TouchScale.STANDARD.id)
        set(v) = prefs.edit { putString("touch_scale", v.id) }

    var lowEndMode: Boolean
        get() = prefs.getBoolean("low_end_mode", systemLowRam)
        set(v) = prefs.edit { putBoolean("low_end_mode", v) }

    var terrain3dEnabled: Boolean
        get() = prefs.getBoolean("terrain_3d", !systemLowRam)
        set(v) = prefs.edit { putBoolean("terrain_3d", v) }

    var mapVisualMode: MapVisualMode
        get() = MapVisualMode.fromId(prefs.getString("map_mode", MapVisualMode.SATELLITE_3D_HYBRID.id) ?: MapVisualMode.SATELLITE_3D_HYBRID.id)
        set(v) = prefs.edit { putString("map_mode", v.id) }

    var accentPreset: AccentPreset
        get() = AccentPreset.fromId(prefs.getString("accent_preset", AccentPreset.MATERIAL_YOU.id) ?: AccentPreset.MATERIAL_YOU.id)
        set(v) = prefs.edit { putString("accent_preset", v.id) }

    var appLang: AppLang
        get() = AppLang.fromCode(prefs.getString("app_lang", AppLang.AUTO.code) ?: AppLang.AUTO.code)
        set(v) = prefs.edit { putString("app_lang", v.code) }

    var voiceLang: AppLang
        get() = AppLang.fromCode(prefs.getString("voice_lang", AppLang.AUTO.code) ?: AppLang.AUTO.code)
        set(v) = prefs.edit { putString("voice_lang", v.code) }

    var telemetryStateJson: String?
        get() = prefs.getString("telemetry_state", null)
        set(v) = prefs.edit { putString("telemetry_state", v) }

    var flappyBestScore: Int
        get() = prefs.getInt("flappy_best", 0)
        set(v) = prefs.edit { putInt("flappy_best", v) }

    var spotifyClientId: String
        get() = prefs.getString("spotify_client_id", "") ?: ""
        set(v) = prefs.edit { putString("spotify_client_id", v.trim()) }

    var spotifyAccessToken: String
        get() = prefs.getString("spotify_access_token", "") ?: ""
        set(v) = prefs.edit { putString("spotify_access_token", v) }

    var spotifyRefreshToken: String
        get() = prefs.getString("spotify_refresh_token", "") ?: ""
        set(v) = prefs.edit { putString("spotify_refresh_token", v) }

    var spotifyExpiresAtMs: Long
        get() = prefs.getLong("spotify_expires_at", 0L)
        set(v) = prefs.edit { putLong("spotify_expires_at", v) }

    var spotifyPkceVerifier: String
        get() = prefs.getString("spotify_pkce_verifier", "") ?: ""
        set(v) = prefs.edit { putString("spotify_pkce_verifier", v) }

    fun loadReminders(): List<LocalCalendarReminder> {
        val raw = prefs.getString("local_reminders", null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<LocalCalendarReminder>>(raw) }.getOrDefault(emptyList())
    }

    fun saveReminders(items: List<LocalCalendarReminder>) {
        prefs.edit { putString("local_reminders", json.encodeToString(items)) }
    }
}
