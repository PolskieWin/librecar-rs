package com.librecar.rust.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.nativeengine.TelemetrySnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class CarLocationState(
    val lat: Double = 52.2297,
    val lon: Double = 21.0122,
    val hasGpsFix: Boolean = false,
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
)

class LocationTracker(
    private val context: Context,
    private val prefs: CarPreferences,
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _state = MutableStateFlow(
        CarLocationState(telemetry = RustEngine.safeInit(prefs.telemetryStateJson))
    )
    val state: StateFlow<CarLocationState> = _state.asStateFlow()

    private var listening = false

    private val gpsListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            val ageNs = SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos
            // Ignore stale cached locations (>12s old) so app startup never jumps the km counter
            if (ageNs > 12_000_000_000L) {
                if (!_state.value.hasGpsFix) {
                    _state.value = _state.value.copy(
                        lat = location.latitude,
                        lon = location.longitude,
                        hasGpsFix = true,
                    )
                }
                return
            }
            ingestLocation(
                lat = location.latitude,
                lon = location.longitude,
                alt = location.altitude,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else -1.0,
                bearing = if (location.hasBearing()) location.bearing else -1f,
                accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 12.0,
                tsMs = location.time,
            )
        }

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    // Network provider is used strictly to center the initial map viewport; never feeds odometer/km telemetry!
    private val networkCenterListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (!_state.value.hasGpsFix) {
                _state.value = _state.value.copy(
                    lat = location.latitude,
                    lon = location.longitude,
                )
            }
        }

        @Deprecated("Deprecated in Android API")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (listening || !hasLocationPermission()) return
        listening = true
        runCatching {
            val last = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            if (last != null) {
                _state.value = _state.value.copy(
                    lat = last.latitude,
                    lon = last.longitude,
                    hasGpsFix = last.provider == LocationManager.GPS_PROVIDER,
                )
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 750L, 1.5f, gpsListener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 15f, networkCenterListener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        if (listening) {
            runCatching { lm.removeUpdates(gpsListener) }
            runCatching { lm.removeUpdates(networkCenterListener) }
            listening = false
        }
        persistTelemetry()
    }

    fun setRoutePathForEta(distanceMeters: Double, durationSec: Double) {
        RustEngine.safeSetRouteRemaining(distanceMeters, durationSec)
        val snap = _state.value.telemetry
        _state.value = _state.value.copy(
            telemetry = snap.copy(
                remainingKm = distanceMeters / 1000.0,
                etaMinutes = kotlin.math.ceil(durationSec / 60.0).toInt(),
            )
        )
    }

    fun clearRoute() {
        RustEngine.safeSetRouteRemaining(0.0, 0.0)
        _state.value = _state.value.copy(
            telemetry = _state.value.telemetry.copy(remainingKm = 0.0, etaMinutes = 0)
        )
    }

    fun setFuelLiters(liters: Double, capacityLiters: Double = prefs.tankCapacityLiters) {
        val updated = RustEngine.safeSetFuelLiters(liters, capacityLiters)
        _state.value = _state.value.copy(telemetry = updated)
        persistTelemetry()
    }

    fun adjustFuelDelta(deltaLiters: Double) {
        val cur = _state.value.telemetry
        val cap = prefs.tankCapacityLiters
        setFuelLiters((cur.fuelLiters + deltaLiters).coerceIn(0.0, cap), cap)
    }

    fun fillTank() {
        val cap = prefs.tankCapacityLiters
        setFuelLiters(cap, cap)
    }

    fun resetTripA() {
        RustEngine.safeResetTripA()
        _state.value = _state.value.copy(
            telemetry = _state.value.telemetry.copy(tripAKm = 0.0, maxSpeedKmh = 0.0, movingSeconds = 0L)
        )
        persistTelemetry()
    }

    fun resetTripB() {
        RustEngine.safeResetTripB()
        _state.value = _state.value.copy(telemetry = _state.value.telemetry.copy(tripBKm = 0.0))
        persistTelemetry()
    }

    fun ingestLocation(
        lat: Double,
        lon: Double,
        alt: Double,
        speedMps: Double,
        bearing: Float,
        accuracy: Double,
        tsMs: Long,
    ) {
        val snap = RustEngine.safeIngestGps(lat, lon, alt, speedMps, bearing, accuracy, tsMs)
        _state.value = _state.value.copy(
            lat = lat,
            lon = lon,
            hasGpsFix = true,
            telemetry = snap,
        )
    }

    fun persistTelemetry() {
        RustEngine.safeExportTelemetryState()?.let { prefs.telemetryStateJson = it }
    }
}
