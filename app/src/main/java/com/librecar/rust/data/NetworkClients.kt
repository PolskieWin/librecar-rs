package com.librecar.rust.data

import androidx.compose.runtime.Immutable
import com.librecar.rust.nativeengine.EcoRouteRequest
import com.librecar.rust.nativeengine.EvaluatedRoute
import com.librecar.rust.nativeengine.RustCandidateRoute
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.nativeengine.RustRoutePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale
import kotlin.math.max

@Immutable
data class PlaceSearchResult(
    val name: String,
    val subtitle: String,
    val lat: Double,
    val lon: Double,
)

@Immutable
data class RouteOption(
    val index: Int,
    val points: List<Pair<Double, Double>>,
    val nextManeuver: String,
    val evaluation: EvaluatedRoute,
)

@Immutable
data class WeatherHourlyItem(
    val timeLabel: String,
    val tempC: Double,
    val weatherCode: Int,
    val rainProb: Int,
)

@Immutable
data class WeatherData(
    val tempC: Double,
    val apparentTempC: Double,
    val windKmh: Double,
    val weatherCode: Int,
    val precipProb: Int,
    val hourly: List<WeatherHourlyItem>,
)

object NetworkClients {
    private const val USER_AGENT = "LibreCarRS/1.1 (https://github.com/PolskieWin/librecar-rs)"
    private const val GEO_TTL_MS = 24L * 60L * 60L * 1000L
    private const val ROUTE_TTL_MS = 30L * 60L * 1000L
    private const val ELEV_TTL_MS = 7L * 24L * 60L * 60L * 1000L
    private const val WEATHER_TTL_MS = 20L * 60L * 1000L

    suspend fun searchPlaces(
        query: String,
        lang: String,
        biasLat: Double?,
        biasLon: Double?,
    ): List<PlaceSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(q, "UTF-8")
        val langParam = if (lang == "pl") "pl" else "en"
        val photonLang = if (lang == "pl") "default" else "en"
        val gridKey = if (biasLat != null && biasLon != null) {
            "%.1f,%.1f".format(Locale.US, biasLat, biasLon)
        } else "none"
        val bias = if (biasLat != null && biasLon != null) "&lat=$biasLat&lon=$biasLon" else ""

        val photonRaw = CarCache.fetchCachedGet(
            url = "https://photon.komoot.io/api/?q=$encoded&limit=5&lang=$photonLang$bias",
            cacheKey = "geo_photon_v2_${photonLang}_${gridKey}_${q.lowercase()}",
            ttlMs = GEO_TTL_MS,
            userAgent = USER_AGENT,
        )
        val photonResults = parsePhoton(photonRaw)
        if (photonResults.isNotEmpty()) return@withContext photonResults

        val nomRaw = CarCache.fetchCachedGet(
            url = "https://nominatim.openstreetmap.org/search?format=json&limit=5&accept-language=$langParam&q=$encoded",
            cacheKey = "geo_nom_${langParam}_${q.lowercase()}",
            ttlMs = GEO_TTL_MS,
            userAgent = USER_AGENT,
        )
        parseNominatim(nomRaw)
    }

    private fun parsePhoton(raw: String?): List<PlaceSearchResult> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val features = JSONObject(raw).optJSONArray("features") ?: return emptyList()
            buildList {
                for (i in 0 until features.length()) {
                    val f = features.getJSONObject(i)
                    val coords = f.getJSONObject("geometry").getJSONArray("coordinates")
                    val lon = coords.getDouble(0)
                    val lat = coords.getDouble(1)
                    val props = f.getJSONObject("properties")
                    val name = props.optString("name")
                        .ifBlank { props.optString("street") }
                        .ifBlank { props.optString("city") }
                    val parts = listOfNotNull(
                        props.optString("street").takeIf { it.isNotBlank() && it != name },
                        props.optString("city").takeIf { it.isNotBlank() && it != name },
                        props.optString("country").takeIf { it.isNotBlank() },
                    )
                    if (name.isNotBlank()) {
                        add(PlaceSearchResult(name = name, subtitle = parts.joinToString(", "), lat = lat, lon = lon))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseNominatim(raw: String?): List<PlaceSearchResult> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val disp = obj.optString("display_name")
                    val parts = disp.split(",").map { it.trim() }
                    val name = parts.firstOrNull().orEmpty().ifBlank { disp }
                    val sub = parts.drop(1).take(3).joinToString(", ")
                    add(
                        PlaceSearchResult(
                            name = name,
                            subtitle = sub,
                            lat = obj.getString("lat").toDouble(),
                            lon = obj.getString("lon").toDouble(),
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    suspend fun fetchEcoAnalyzedRoutes(
        fromLat: Double,
        fromLon: Double,
        toLat: Double,
        toLon: Double,
        fuelAvailableLiters: Double,
        isPolish: Boolean,
    ): List<RouteOption> = withContext(Dispatchers.IO) {
        val osrmUrl =
            "https://router.project-osrm.org/route/v1/driving/$fromLon,$fromLat;$toLon,$toLat" +
                "?alternatives=3&steps=true&overview=full&geometries=geojson"
        val rKey = "osrm_%.3f,%.3f_%.3f,%.3f".format(Locale.US, fromLat, fromLon, toLat, toLon)
        val raw = CarCache.fetchCachedGet(osrmUrl, rKey, ROUTE_TTL_MS, USER_AGENT) ?: return@withContext emptyList()

        runCatching {
            val root = JSONObject(raw)
            if (root.optString("code") != "Ok") return@withContext emptyList()
            val routesArr = root.getJSONArray("routes")
            val candidates = mutableListOf<Triple<Int, List<Pair<Double, Double>>, String>>()
            val rustCandidates = mutableListOf<RustCandidateRoute>()

            for (i in 0 until routesArr.length()) {
                val r = routesArr.getJSONObject(i)
                val dist = r.getDouble("distance")
                val dur = r.getDouble("duration")
                val coords = r.getJSONObject("geometry").getJSONArray("coordinates")
                val fullPts = ArrayList<Pair<Double, Double>>(coords.length())
                for (k in 0 until coords.length()) {
                    val c = coords.getJSONArray(k)
                    fullPts.add(c.getDouble(1) to c.getDouble(0))
                }

                var stepsCount = 0
                var firstManeuver = if (isPolish) "Jedź wyznaczoną trasą" else "Follow route"
                val legs = r.optJSONArray("legs")
                if (legs != null && legs.length() > 0) {
                    val steps = legs.getJSONObject(0).optJSONArray("steps")
                    if (steps != null) {
                        stepsCount = steps.length()
                        val targetStepIdx = if (stepsCount > 1) 1 else 0
                        val s = steps.optJSONObject(targetStepIdx)
                        val road = s?.optString("name").orEmpty()
                        val mType = s?.optJSONObject("maneuver")?.optString("type").orEmpty()
                        val mMod = s?.optJSONObject("maneuver")?.optString("modifier").orEmpty()
                        firstManeuver = formatManeuver(mType, mMod, road, isPolish)
                    }
                }

                val sampled = samplePoints(fullPts, 20)
                val elevs = fetchElevations(sampled)
                val rustPts = sampled.mapIndexed { idx, p ->
                    RustRoutePoint(lat = p.first, lon = p.second, elevM = elevs.getOrElse(idx) { 100.0 })
                }

                candidates.add(Triple(i, fullPts, firstManeuver))
                rustCandidates.add(
                    RustCandidateRoute(
                        index = i,
                        distanceM = dist,
                        durationS = dur,
                        stepsCount = stepsCount,
                        points = rustPts,
                    )
                )
            }

            val ecoResponse = RustEngine.safeAnalyzeEcoRoutes(
                EcoRouteRequest(
                    fuelAvailableLiters = fuelAvailableLiters,
                    routes = rustCandidates,
                )
            )
            if (ecoResponse.routes.isEmpty()) {
                candidates.mapIndexed { idx, (i, pts, man) ->
                    val rObj = routesArr.getJSONObject(idx)
                    val dKm = rObj.getDouble("distance") / 1000.0
                    val dMin = rObj.getDouble("duration") / 60.0
                    val estL = dKm * 0.064
                    val canReach = fuelAvailableLiters >= estL * 1.08
                    RouteOption(
                        index = i,
                        points = pts,
                        nextManeuver = man,
                        evaluation = EvaluatedRoute(
                            index = i,
                            distanceKm = dKm,
                            durationMin = dMin,
                            estimatedLiters = estL,
                            lPer100Km = 6.4,
                            co2Grams = (dKm * 153).toInt(),
                            elevationGainM = 0.0,
                            elevationLossM = 0.0,
                            sharpTurns = 0,
                            ecoScore = 82,
                            isBestFuelSaving = idx == 0,
                            isFastest = idx == 0,
                            fuelSavedVsFastestPct = 0f,
                            canReach = canReach,
                            fuelMarginLiters = fuelAvailableLiters - estL,
                            reachabilityEn = if (canReach) "Reachable" else "Refuel needed",
                            reachabilityPl = if (canReach) "Dojedziesz" else "Wymagane tankowanie",
                            summaryEn = "Standard route",
                            summaryPl = "Trasa standardowa",
                        ),
                    )
                }
            } else {
                ecoResponse.routes.mapNotNull { eval ->
                    val cand = candidates.firstOrNull { it.first == eval.index } ?: return@mapNotNull null
                    RouteOption(
                        index = eval.index,
                        points = cand.second,
                        nextManeuver = cand.third,
                        evaluation = eval,
                    )
                }.sortedByDescending { it.evaluation.isBestFuelSaving }
            }
        }.getOrDefault(emptyList())
    }

    private fun samplePoints(pts: List<Pair<Double, Double>>, maxCount: Int): List<Pair<Double, Double>> {
        if (pts.size <= maxCount) return pts
        val step = max(1, pts.size / maxCount)
        val out = mutableListOf<Pair<Double, Double>>()
        var i = 0
        while (i < pts.size) {
            out.add(pts[i])
            i += step
        }
        if (out.last() != pts.last()) out.add(pts.last())
        return out
    }

    private fun fetchElevations(points: List<Pair<Double, Double>>): List<Double> {
        if (points.isEmpty()) return emptyList()
        val lats = points.joinToString(",") { "%.4f".format(Locale.US, it.first) }
        val lons = points.joinToString(",") { "%.4f".format(Locale.US, it.second) }
        val key = "elev_${lats.hashCode()}_${lons.hashCode()}"
        val raw = CarCache.fetchCachedGet(
            url = "https://api.open-meteo.com/v1/elevation?latitude=$lats&longitude=$lons",
            cacheKey = key,
            ttlMs = ELEV_TTL_MS,
            userAgent = USER_AGENT,
        ) ?: return List(points.size) { 100.0 }
        return runCatching {
            val arr = JSONObject(raw).getJSONArray("elevation")
            List(arr.length()) { idx -> arr.optDouble(idx, 100.0) }
        }.getOrDefault(List(points.size) { 100.0 })
    }

    private fun formatManeuver(type: String, modifier: String, road: String, isPl: Boolean): String {
        val dir = when (modifier) {
            "left", "sharp left" -> if (isPl) "Skręć w lewo" else "Turn left"
            "slight left" -> if (isPl) "Lekko w lewo" else "Keep left"
            "right", "sharp right" -> if (isPl) "Skręć w prawo" else "Turn right"
            "slight right" -> if (isPl) "Lekko w prawo" else "Keep right"
            "uturn" -> if (isPl) "Zawróć" else "Make a U-turn"
            else -> when (type) {
                "roundabout", "rotary" -> if (isPl) "Wjedź na rondo" else "Enter roundabout"
                "arrive" -> if (isPl) "Cel podróży" else "Arrive at destination"
                else -> if (isPl) "Jedź prosto" else "Continue straight"
            }
        }
        return if (road.isNotBlank()) "$dir · $road" else dir
    }

    suspend fun fetchWeather(lat: Double, lon: Double): WeatherData? = withContext(Dispatchers.IO) {
        val gridLat = "%.2f".format(Locale.US, lat)
        val gridLon = "%.2f".format(Locale.US, lon)
        val url =
            "https://api.open-meteo.com/v1/forecast?latitude=$gridLat&longitude=$gridLon" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&hourly=temperature_2m,weather_code,precipitation_probability&forecast_hours=8&timezone=auto"
        val raw = CarCache.fetchCachedGet(
            url = url,
            cacheKey = "weather_${gridLat}_${gridLon}",
            ttlMs = WEATHER_TTL_MS,
            userAgent = USER_AGENT,
            allowStaleOnOffline = true,
        ) ?: return@withContext null

        runCatching {
            val root = JSONObject(raw)
            val cur = root.getJSONObject("current")
            val hourlyObj = root.getJSONObject("hourly")
            val times = hourlyObj.getJSONArray("time")
            val temps = hourlyObj.getJSONArray("temperature_2m")
            val codes = hourlyObj.getJSONArray("weather_code")
            val probs = hourlyObj.getJSONArray("precipitation_probability")

            val hourlyList = buildList {
                val count = minOf(times.length(), 8)
                for (i in 0 until count) {
                    val tStr = times.getString(i).substringAfter('T').take(5)
                    add(
                        WeatherHourlyItem(
                            timeLabel = tStr,
                            tempC = temps.getDouble(i),
                            weatherCode = codes.getInt(i),
                            rainProb = probs.optInt(i, 0),
                        )
                    )
                }
            }
            WeatherData(
                tempC = cur.getDouble("temperature_2m"),
                apparentTempC = cur.getDouble("apparent_temperature"),
                windKmh = cur.getDouble("wind_speed_10m"),
                weatherCode = cur.getInt("weather_code"),
                precipProb = hourlyList.firstOrNull()?.rainProb ?: 0,
                hourly = hourlyList,
            )
        }.getOrNull()
    }
}
