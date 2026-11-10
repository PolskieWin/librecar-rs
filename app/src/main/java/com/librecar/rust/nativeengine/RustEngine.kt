package com.librecar.rust.nativeengine

import androidx.compose.runtime.Immutable
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Immutable
@Serializable
data class TelemetrySnapshot(
    @SerialName("speed_kmh") val speedKmh: Double = 0.0,
    @SerialName("avg_speed_kmh") val avgSpeedKmh: Double = 0.0,
    @SerialName("max_speed_kmh") val maxSpeedKmh: Double = 0.0,
    @SerialName("trip_a_km") val tripAKm: Double = 0.0,
    @SerialName("trip_b_km") val tripBKm: Double = 0.0,
    @SerialName("odometer_km") val odometerKm: Double = 0.0,
    @SerialName("moving_seconds") val movingSeconds: Long = 0L,
    @SerialName("remaining_km") val remainingKm: Double = 0.0,
    @SerialName("eta_minutes") val etaMinutes: Int = 0,
    @SerialName("eco_drive_score") val ecoDriveScore: Int = 92,
    @SerialName("bearing_deg") val bearingDeg: Float = 0f,
    @SerialName("altitude_m") val altitudeM: Double = 0.0,
    @SerialName("fuel_liters") val fuelLiters: Double = 42.0,
    @SerialName("tank_capacity_liters") val tankCapacityLiters: Double = 50.0,
    @SerialName("avg_l_per_100km") val avgLPer100Km: Double = 6.4,
    @SerialName("estimated_range_km") val estimatedRangeKm: Double = 656.0,
    @SerialName("is_low_fuel") val isLowFuel: Boolean = false,
)

@Serializable
data class RustRoutePoint(
    val lat: Double,
    val lon: Double,
    @SerialName("elev_m") val elevM: Double = 0.0,
)

@Serializable
data class RustCandidateRoute(
    val index: Int,
    @SerialName("distance_m") val distanceM: Double,
    @SerialName("duration_s") val durationS: Double,
    @SerialName("steps_count") val stepsCount: Int,
    val points: List<RustRoutePoint>,
)

@Serializable
data class EcoRouteRequest(
    @SerialName("vehicle_mass_kg") val vehicleMassKg: Double = 1380.0,
    @SerialName("fuel_available_liters") val fuelAvailableLiters: Double = 42.0,
    val routes: List<RustCandidateRoute>,
)

@Immutable
@Serializable
data class EvaluatedRoute(
    val index: Int,
    @SerialName("distance_km") val distanceKm: Double,
    @SerialName("duration_min") val durationMin: Double,
    @SerialName("estimated_liters") val estimatedLiters: Double,
    @SerialName("l_per_100km") val lPer100Km: Double,
    @SerialName("co2_grams") val co2Grams: Int,
    @SerialName("elevation_gain_m") val elevationGainM: Double,
    @SerialName("elevation_loss_m") val elevationLossM: Double,
    @SerialName("sharp_turns") val sharpTurns: Int,
    @SerialName("eco_score") val ecoScore: Int,
    @SerialName("is_best_fuel_saving") val isBestFuelSaving: Boolean,
    @SerialName("is_fastest") val isFastest: Boolean,
    @SerialName("fuel_saved_vs_fastest_pct") val fuelSavedVsFastestPct: Float,
    @SerialName("can_reach") val canReach: Boolean = true,
    @SerialName("fuel_margin_liters") val fuelMarginLiters: Double = 0.0,
    @SerialName("reachability_en") val reachabilityEn: String = "",
    @SerialName("reachability_pl") val reachabilityPl: String = "",
    @SerialName("summary_en") val summaryEn: String,
    @SerialName("summary_pl") val summaryPl: String,
)

@Immutable
@Serializable
data class EcoRouteResponse(
    @SerialName("best_fuel_index") val bestFuelIndex: Int = 0,
    @SerialName("fastest_index") val fastestIndex: Int = 0,
    @SerialName("eco_unlocks_reachability") val ecoUnlocksReachability: Boolean = false,
    val routes: List<EvaluatedRoute> = emptyList(),
)

@Immutable
@Serializable
data class VoiceCommandResult(
    val intent: String = "unknown",
    val target: String = "",
    @SerialName("detected_lang") val detectedLang: String = "en",
    @SerialName("eco_priority") val ecoPriority: Boolean = false,
    @SerialName("reply_en") val replyEn: String = "",
    @SerialName("reply_pl") val replyPl: String = "",
)

object RustEngine {
    val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val isNativeLoaded: Boolean = try {
        System.loadLibrary("librecar_core")
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    private external fun initEngine(savedStateJson: String?): String
    private external fun ingestGps(
        lat: Double,
        lon: Double,
        altitudeM: Double,
        speedMps: Double,
        bearingDeg: Float,
        accuracyM: Double,
        timestampMs: Long,
    ): String

    private external fun setRouteRemaining(remainingMeters: Double, expectedDurationSec: Double)
    private external fun setFuelLiters(liters: Double, capacityLiters: Double): String
    private external fun fillTank(): String
    private external fun resetTripA()
    private external fun resetTripB()
    private external fun exportTelemetryState(): String
    private external fun analyzeEcoRoutes(routesJson: String): String
    private external fun parseVoiceCommand(text: String, preferredLang: String): String
    private external fun flappyStepInto(dtSec: Float, flap: Boolean, outBuf: FloatArray)
    private external fun flappyReset()
    private external fun flappySetBest(best: Int)
    private external fun stepVisualizerInto(playing: Boolean, phaseMs: Long, dtSec: Float, outBuf: FloatArray)

    fun safeInit(savedStateJson: String?): TelemetrySnapshot {
        if (!isNativeLoaded) return TelemetrySnapshot()
        return runCatching {
            json.decodeFromString<TelemetrySnapshot>(initEngine(savedStateJson))
        }.getOrDefault(TelemetrySnapshot())
    }

    fun safeIngestGps(
        lat: Double,
        lon: Double,
        altitudeM: Double,
        speedMps: Double,
        bearingDeg: Float,
        accuracyM: Double,
        timestampMs: Long,
    ): TelemetrySnapshot {
        if (!isNativeLoaded) {
            return TelemetrySnapshot(speedKmh = (speedMps * 3.6).coerceAtLeast(0.0), bearingDeg = bearingDeg)
        }
        return runCatching {
            val raw = ingestGps(lat, lon, altitudeM, speedMps, bearingDeg, accuracyM, timestampMs)
            json.decodeFromString<TelemetrySnapshot>(raw)
        }.getOrDefault(TelemetrySnapshot())
    }

    fun safeSetRouteRemaining(remainingMeters: Double, expectedDurationSec: Double) {
        if (isNativeLoaded) {
            setRouteRemaining(remainingMeters, expectedDurationSec)
        }
    }

    fun safeSetFuelLiters(liters: Double, capacityLiters: Double): TelemetrySnapshot {
        if (!isNativeLoaded) return TelemetrySnapshot(fuelLiters = liters, tankCapacityLiters = capacityLiters)
        return runCatching {
            json.decodeFromString<TelemetrySnapshot>(setFuelLiters(liters, capacityLiters))
        }.getOrDefault(TelemetrySnapshot())
    }

    fun safeFillTank(): TelemetrySnapshot {
        if (!isNativeLoaded) return TelemetrySnapshot(fuelLiters = 50.0, tankCapacityLiters = 50.0, isLowFuel = false)
        return runCatching {
            json.decodeFromString<TelemetrySnapshot>(fillTank())
        }.getOrDefault(TelemetrySnapshot())
    }

    fun safeResetTripA() {
        if (isNativeLoaded) resetTripA()
    }

    fun safeResetTripB() {
        if (isNativeLoaded) resetTripB()
    }

    fun safeExportTelemetryState(): String? {
        if (!isNativeLoaded) return null
        return runCatching { exportTelemetryState() }.getOrNull()
    }

    fun safeAnalyzeEcoRoutes(request: EcoRouteRequest): EcoRouteResponse {
        if (!isNativeLoaded || request.routes.isEmpty()) return EcoRouteResponse()
        return runCatching {
            val payload = json.encodeToString(request)
            val res = analyzeEcoRoutes(payload)
            json.decodeFromString<EcoRouteResponse>(res)
        }.getOrDefault(EcoRouteResponse())
    }

    fun safeParseVoiceCommand(text: String, preferredLang: String): VoiceCommandResult {
        if (!isNativeLoaded) {
            return VoiceCommandResult(target = text, replyEn = "Native library unavailable", replyPl = "Biblioteka natywna niedostępna")
        }
        return runCatching {
            val raw = parseVoiceCommand(text, preferredLang)
            json.decodeFromString<VoiceCommandResult>(raw)
        }.getOrDefault(VoiceCommandResult(target = text))
    }

    fun safeFlappyStepInto(dtSec: Float, flap: Boolean, outBuf: FloatArray) {
        if (!isNativeLoaded) {
            outBuf[0] = 0.46f
            outBuf[14] = 0.175f
            return
        }
        flappyStepInto(dtSec, flap, outBuf)
    }

    fun safeFlappyReset() {
        if (isNativeLoaded) flappyReset()
    }

    fun safeFlappySetBest(best: Int) {
        if (isNativeLoaded) flappySetBest(best)
    }

    fun safeStepVisualizerInto(playing: Boolean, phaseMs: Long, dtSec: Float, outBuf: FloatArray) {
        if (!isNativeLoaded) {
            for (i in outBuf.indices) outBuf[i] = 0.1f
            return
        }
        stepVisualizerInto(playing, phaseMs, dtSec, outBuf)
    }
}
