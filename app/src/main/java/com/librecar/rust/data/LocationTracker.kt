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
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.nativeengine.TelemetrySnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

@Immutable
data class CarLocationState(
    val lat: Double = 52.2297,
    val lon: Double = 21.0122,
    val hasGpsFix: Boolean = false,
    val isDemoSim: Boolean = false,
    val telemetry: TelemetrySnapshot = TelemetrySnapshot(),
)

class LocationTracker(
    private val context: Context,
    private val prefs: CarPreferences,
    private val scope: CoroutineScope,
) {
    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _state = MutableStateFlow(
        CarLocationState(telemetry = RustEngine.safeInit(prefs.telemetryStateJson))
    )
    val state: StateFlow<CarLocationState> = _state.asStateFlow()

    private var demoJob: Job? = null
    private var simPoints: List<Pair<Double, Double>> = emptyList()
    private var listening = false

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (_state.value.isDemoSim) return
            ingestLocation(
                lat = location.latitude,
                lon = location.longitude,
                alt = location.altitude,
                speedMps = if (location.hasSpeed()) location.speed.toDouble() else -1.0,
                bearing = if (location.hasBearing()) location.bearing else -1f,
                accuracy = if (location.hasAccuracy()) location.accuracy.toDouble() else 10.0,
                tsMs = location.time,
                fromDemo = false,
            )
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
                    hasGpsFix = true,
                )
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 750L, 1.5f, listener, Looper.getMainLooper())
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 4000L, 15f, listener, Looper.getMainLooper())
            }
        }
    }

    fun stop() {
        if (listening) {
            runCatching { lm.removeUpdates(listener) }
            listening = false
        }
        persistTelemetry()
    }

    fun setRoutePathForEta(points: List<Pair<Double, Double>>, distanceMeters: Double, durationSec: Double) {
        simPoints = points
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
        simPoints = emptyList()
        RustEngine.safeSetRouteRemaining(0.0, 0.0)
        _state.value = _state.value.copy(
            telemetry = _state.value.telemetry.copy(remainingKm = 0.0, etaMinutes = 0)
        )
    }

    fun setFuelLiters(liters: Double, capacityLiters: Double = _state.value.telemetry.tankCapacityLiters) {
        val updated = RustEngine.safeSetFuelLiters(liters, capacityLiters)
        _state.value = _state.value.copy(telemetry = updated)
        persistTelemetry()
    }

    fun adjustFuelDelta(deltaLiters: Double) {
        val cur = _state.value.telemetry
        setFuelLiters((cur.fuelLiters + deltaLiters).coerceIn(0.0, cur.tankCapacityLiters), cur.tankCapacityLiters)
    }

    fun fillTank() {
        val updated = RustEngine.safeFillTank()
        _state.value = _state.value.copy(telemetry = updated)
        persistTelemetry()
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

    fun toggleDemoDrive() {
        if (_state.value.isDemoSim) {
            stopDemoDrive()
        } else {
            startDemoDrive()
        }
    }

    private fun startDemoDrive() {
        demoJob?.cancel()
        val path = if (simPoints.size >= 2) {
            simPoints
        } else {
            val baseLat = _state.value.lat
            val baseLon = _state.value.lon
            listOf(
                baseLat to baseLon,
                baseLat + 0.005 to baseLon + 0.006,
                baseLat + 0.011 to baseLon + 0.012,
                baseLat + 0.015 to baseLon + 0.004,
                baseLat + 0.008 to baseLon - 0.005,
                baseLat to baseLon,
            )
        }
        _state.value = _state.value.copy(isDemoSim = true)
        demoJob = scope.launch {
            var segIdx = 0
            var distAlongSegM = 0.0
            var simTime = System.currentTimeMillis()
            val dtSec = 0.8
            while (isActive && _state.value.isDemoSim) {
                val p0 = path[segIdx % path.size]
                val p1 = path[(segIdx + 1) % path.size]
                val segLenM = haversineMeters(p0.first, p0.second, p1.first, p1.second).coerceAtLeast(1.0)
                val frac = (distAlongSegM / segLenM).coerceIn(0.0, 1.0)
                val lat = p0.first + (p1.first - p0.first) * frac
                val lon = p0.second + (p1.second - p0.second) * frac
                val bearing = computeBearing(p0.first, p0.second, p1.first, p1.second)
                val speedMps = 17.5 + 3.0 * sin(segIdx * 0.7 + frac * Math.PI)
                ingestLocation(lat, lon, 118.0, speedMps, bearing, 4.0, simTime, fromDemo = true)

                var curLen = segLenM
                distAlongSegM += speedMps * dtSec
                while (distAlongSegM >= curLen && path.size >= 2) {
                    distAlongSegM -= curLen
                    segIdx = (segIdx + 1) % path.size
                    val np0 = path[segIdx]
                    val np1 = path[(segIdx + 1) % path.size]
                    curLen = haversineMeters(np0.first, np0.second, np1.first, np1.second).coerceAtLeast(1.0)
                }
                simTime += (dtSec * 1000.0).toLong()
                delay((dtSec * 1000.0).toLong())
            }
        }
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val rLat1 = Math.toRadians(lat1)
        val rLat2 = Math.toRadians(lat2)
        val dLat = rLat2 - rLat1
        val dLon = Math.toRadians(lon2 - lon1)
        val sLat = sin(dLat * 0.5)
        val sLon = sin(dLon * 0.5)
        val a = sLat * sLat + cos(rLat1) * cos(rLat2) * sLon * sLon
        return 6_371_000.0 * 2.0 * Math.asin(Math.sqrt(a).coerceAtMost(1.0))
    }

    fun stopDemoDrive() {
        demoJob?.cancel()
        demoJob = null
        _state.value = _state.value.copy(isDemoSim = false)
    }

    private fun ingestLocation(
        lat: Double,
        lon: Double,
        alt: Double,
        speedMps: Double,
        bearing: Float,
        accuracy: Double,
        tsMs: Long,
        fromDemo: Boolean,
    ) {
        val snap = RustEngine.safeIngestGps(lat, lon, alt, speedMps, bearing, accuracy, tsMs)
        _state.value = _state.value.copy(
            lat = lat,
            lon = lon,
            hasGpsFix = true,
            isDemoSim = fromDemo,
            telemetry = snap,
        )
    }

    fun persistTelemetry() {
        RustEngine.safeExportTelemetryState()?.let { prefs.telemetryStateJson = it }
    }

    private fun computeBearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r1 = Math.toRadians(lat1)
        val r2 = Math.toRadians(lat2)
        val dLon = Math.toRadians(lon2 - lon1)
        val y = sin(dLon) * cos(r2)
        val x = cos(r1) * sin(r2) - sin(r1) * cos(r2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360.0) % 360.0).toFloat()
    }
}
