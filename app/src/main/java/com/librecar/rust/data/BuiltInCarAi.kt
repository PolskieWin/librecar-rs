package com.librecar.rust.data

import com.librecar.rust.nativeengine.TelemetrySnapshot
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Zero-dependency offline-first Built-in Car AI Assistant (EN + PL).
 * Answers natural conversational queries from live telemetry, eco-route physics, weather & calendar.
 */
object BuiltInCarAi {

    private val enJokes = listOf(
        "Why did the car apply for a job? Because it wanted a little extra drive.",
        "I tried to catch fog on the highway earlier, but I mist.",
        "Keep cruising smoothly around 75 km/h — aerodynamic drag drops sharply and saves up to 15% fuel.",
    )

    private val plJokes = listOf(
        "Dlaczego samochód nie lubi pod górę? Bo zawsze musi wrzucić niższy bieg i ciężko wzdycha.",
        "Płynna jazda około 75 km/h zmniejsza opór powietrza i potrafi oszczędzić do 15 procent paliwa.",
        "Dlaczego mechanik świetnie opowiada żarty? Bo zawsze ma dobry zapłon.",
    )

    fun buildLiveContextSummary(
        carModelName: String,
        telemetry: TelemetrySnapshot,
        weather: WeatherData?,
        events: List<CalendarEventItem>,
        destinationName: String?,
        activeRoute: RouteOption?,
    ): String = buildString {
        append("Car: ").append(carModelName)
        append(", Speed: ").append(telemetry.speedKmh.roundToInt()).append(" km/h")
        append(", Fuel: ").append(telemetry.fuelLiters).append('/').append(telemetry.tankCapacityLiters).append(" L")
        append(" (Range ~").append(telemetry.estimatedRangeKm.roundToInt()).append(" km)")
        append(", Avg burn: ").append(telemetry.avgLPer100Km).append(" L/100km")
        append(", Eco score: ").append(telemetry.ecoDriveScore).append('%')
        append(", Trip A: ").append(telemetry.tripAKm).append(" km, Odo: ").append(telemetry.odometerKm).append(" km")
        if (destinationName != null && activeRoute != null) {
            val ev = activeRoute.evaluation
            append(". Route to ").append(destinationName)
            append(": ").append(ev.distanceKm).append(" km, ~").append(ev.durationMin.roundToInt()).append(" min, ")
            append(ev.estimatedLiters).append(" L needed, reachable=").append(ev.canReach)
        }
        if (weather != null) {
            append(". Weather: ").append(weather.tempC.roundToInt()).append("C, wind ")
                .append(weather.windKmh.roundToInt()).append(" km/h, rain prob ").append(weather.precipProb).append('%')
        }
        val nextEv = events.firstOrNull()
        if (nextEv != null) {
            append(". Next event: ").append(nextEv.timeLabel).append(" ").append(nextEv.title)
            if (nextEv.location.isNotBlank()) append(" @ ").append(nextEv.location)
        }
    }

    fun answerOffline(
        query: String,
        isPolish: Boolean,
        carModelName: String,
        telemetry: TelemetrySnapshot,
        weather: WeatherData?,
        events: List<CalendarEventItem>,
        destinationName: String?,
        activeRoute: RouteOption?,
    ): String {
        val q = foldAscii(query)

        tryEvalMath(query)?.let { mathResult ->
            return if (isPolish) "Wynik to $mathResult." else "That equals $mathResult."
        }

        if (q.contains("joke") || q.contains("bored") || q.contains("funny") ||
            q.contains("zart") || q.contains("dowcip") || q.contains("nudzi")
        ) {
            val idx = abs(q.hashCode() + (telemetry.movingSeconds / 5).toInt()) % enJokes.size
            return if (isPolish) plJokes[idx] else enJokes[idx]
        }

        if (q.contains("fuel") || q.contains("tank") || q.contains("range") || q.contains("reach") ||
            q.contains("paliw") || q.contains("bak") || q.contains("zasieg") || q.contains("dojad") || q.contains("starczy")
        ) {
            if (activeRoute != null && destinationName != null) {
                val ev = activeRoute.evaluation
                return if (isPolish) {
                    val tail = if (ev.canReach) "Spokojnie dojedziesz (zostanie ok. ${ev.fuelMarginLiters} L)."
                    else "Uwaga, brakuje około ${abs(ev.fuelMarginLiters)} L — zatankuj po drodze."
                    "Do $destinationName jest ${ev.distanceKm} km (ok. ${ev.estimatedLiters} L). Masz ${telemetry.fuelLiters} L. $tail"
                } else {
                    val tail = if (ev.canReach) "You can reach it with ~${ev.fuelMarginLiters} L left."
                    else "You are short by ~${abs(ev.fuelMarginLiters)} L — plan a fuel stop."
                    "$destinationName is ${ev.distanceKm} km away (~${ev.estimatedLiters} L). You have ${telemetry.fuelLiters} L. $tail"
                }
            }
            return if (isPolish) {
                "W baku masz ${telemetry.fuelLiters} z ${telemetry.tankCapacityLiters} L. Zasięg około ${telemetry.estimatedRangeKm.roundToInt()} km przy spalaniu ${telemetry.avgLPer100Km} L/100km."
            } else {
                "You have ${telemetry.fuelLiters} of ${telemetry.tankCapacityLiters} L left, giving ~${telemetry.estimatedRangeKm.roundToInt()} km range at ${telemetry.avgLPer100Km} L/100km."
            }
        }

        if (q.contains("eco") || q.contains("score") || q.contains("tip") || q.contains("save") ||
            q.contains("spal") || q.contains("oszczed") || q.contains("porad")
        ) {
            val grade = when {
                telemetry.ecoDriveScore >= 90 -> if (isPolish) "świetny" else "excellent"
                telemetry.ecoDriveScore >= 75 -> if (isPolish) "dobry" else "good"
                else -> if (isPolish) "do poprawy" else "aggressive"
            }
            return if (isPolish) {
                "Twój wynik Eko dla $carModelName to ${telemetry.ecoDriveScore}% ($grade), średnie spalanie ${telemetry.avgLPer100Km} L/100km. Utrzymuj równą prędkość i unikaj gwałtownego przyspieszania."
            } else {
                "Your $carModelName Eco score is ${telemetry.ecoDriveScore}% ($grade) averaging ${telemetry.avgLPer100Km} L/100km. Hold steady throttle between 60-85 km/h to save up to 15% fuel."
            }
        }

        if (q.contains("weather") || q.contains("rain") || q.contains("wind") || q.contains("temp") || q.contains("road") ||
            q.contains("pogod") || q.contains("deszcz") || q.contains("pada") || q.contains("wiatr") || q.contains("drog")
        ) {
            if (weather == null) {
                return if (isPolish) "Dane pogodowe są ładowane. Sprawdź zakładkę Pogoda za chwilę."
                else "Weather data is still refreshing. Tap Weather on the taskbar in a moment."
            }
            val advisory = when {
                weather.precipProb >= 45 -> if (isPolish) "Możliwy deszcz — zwiększ odstęp." else "Rain likely (${weather.precipProb}%) — leave extra braking distance."
                weather.tempC <= 2.0 -> if (isPolish) "Uwaga na przymrozki i śliską nawierzchnię." else "Near freezing — watch out for black ice."
                else -> if (isPolish) "Dobre warunki drogowe." else "Dry, clear driving conditions."
            }
            return if (isPolish) {
                "Teraz ${weather.tempC.roundToInt()}°C, wiatr ${weather.windKmh.roundToInt()} km/h, szansa opadów ${weather.precipProb}%. $advisory"
            } else {
                "Currently ${weather.tempC.roundToInt()}°C with ${weather.windKmh.roundToInt()} km/h wind and ${weather.precipProb}% rain chance. $advisory"
            }
        }

        if (q.contains("calendar") || q.contains("schedule") || q.contains("event") || q.contains("meeting") || q.contains("today") ||
            q.contains("kalendarz") || q.contains("spotkan") || q.contains("dzisiaj") || q.contains("plan")
        ) {
            val ev = events.firstOrNull()
            return if (ev == null) {
                if (isPolish) "Nie masz zaplanowanych wydarzeń na najbliższy czas."
                else "Your schedule is completely clear today."
            } else if (isPolish) {
                val place = if (ev.location.isNotBlank()) " w ${ev.location}" else ""
                "Najbliższe wydarzenie: ${ev.title} o ${ev.timeLabel}$place."
            } else {
                val place = if (ev.location.isNotBlank()) " at ${ev.location}" else ""
                "Next on your schedule: ${ev.title} at ${ev.timeLabel}$place."
            }
        }

        if (q.contains("speed") || q.contains("trip") || q.contains("km") || q.contains("odo") || q.contains("car") ||
            q.contains("predk") || q.contains("tras") || q.contains("licznik") || q.contains("aut")
        ) {
            return if (isPolish) {
                "$carModelName: jedziesz ${telemetry.speedKmh.roundToInt()} km/h, Trasa A ${telemetry.tripAKm} km, licznik ${telemetry.odometerKm} km, rekord ${telemetry.maxSpeedKmh.roundToInt()} km/h."
            } else {
                "$carModelName: current speed ${telemetry.speedKmh.roundToInt()} km/h, Trip A ${telemetry.tripAKm} km, Odometer ${telemetry.odometerKm} km, max ${telemetry.maxSpeedKmh.roundToInt()} km/h."
            }
        }

        if (q.contains("hello") || q.contains("hi") || q.contains("hey") || q.contains("czesc") || q.contains("hej") || q.contains("dzien dobry")) {
            return if (isPolish) {
                "Cześć! Jestem wbudowanym asystentem AI LibreCar. Zapytaj mnie o paliwo, pogodę, trasę, kalendarz albo poproś o nawigację lub radio."
            } else {
                "Hey! I'm your built-in LibreCar AI. Ask me about fuel range, route safety, weather, your calendar, or tell me where to drive."
            }
        }

        return if (isPolish) {
            "Wbudowane AI LibreCar: masz ${telemetry.fuelLiters} L paliwa (~${telemetry.estimatedRangeKm.roundToInt()} km zasięgu), wynik Eko ${telemetry.ecoDriveScore}%. Możesz zapytać o pogodę, trasę, spalanie lub dodać klucz Gemini w Ustawieniach dla pytań ogólnych."
        } else {
            "Built-in LibreCar AI: ${telemetry.fuelLiters} L fuel (~${telemetry.estimatedRangeKm.roundToInt()} km range), ${telemetry.ecoDriveScore}% Eco score. Ask about fuel, weather, schedule, math, or add a Gemini API key in Settings for open web knowledge."
        }
    }

    private fun tryEvalMath(raw: String): String? {
        val m = Regex("""(-?\d+(?:\.\d+)?)\s*([+\-*/x×])\s*(-?\d+(?:\.\d+)?)""").find(raw) ?: return null
        val a = m.groupValues[1].toDoubleOrNull() ?: return null
        val op = m.groupValues[2]
        val b = m.groupValues[3].toDoubleOrNull() ?: return null
        val res = when (op) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "×" -> a * b
            "/" -> if (b != 0.0) a / b else return null
            else -> return null
        }
        return if (abs(res - res.roundToInt()) < 1e-6) {
            res.roundToInt().toString()
        } else {
            "%.2f".format(java.util.Locale.US, res)
        }
    }

    private fun foldAscii(s: String): String = s.lowercase()
        .replace('ą', 'a').replace('ć', 'c').replace('ę', 'e')
        .replace('ł', 'l').replace('ń', 'n').replace('ó', 'o')
        .replace('ś', 's').replace('ź', 'z').replace('ż', 'z')
}
