package com.librecar.rust

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.librecar.rust.data.CalendarEventItem
import com.librecar.rust.data.CalendarProviderHelper
import com.librecar.rust.data.CarLocationState
import com.librecar.rust.data.CarModelPreset
import com.librecar.rust.data.CarPreferences
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.LocalAudioPlayer
import com.librecar.rust.data.LocalCalendarReminder
import com.librecar.rust.data.LocalPlayerState
import com.librecar.rust.data.LocationTracker
import com.librecar.rust.data.MapVisualMode
import com.librecar.rust.data.NetworkClients
import com.librecar.rust.data.PlaceSearchResult
import com.librecar.rust.data.RadioStation
import com.librecar.rust.data.RouteOption
import com.librecar.rust.data.SpotifyClient
import com.librecar.rust.data.SpotifyPlaybackState
import com.librecar.rust.data.SpotifyTrack
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.data.TouchScale
import com.librecar.rust.data.VoiceAssistant
import com.librecar.rust.data.VoiceUiState
import com.librecar.rust.data.WeatherData
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.stringsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class CarTab {
    DASHBOARD,
    MUSIC,
    RADIO,
    YOUTUBE,
    FLAPPY_BIRD,
}

enum class MusicSourceTab {
    BUILT_IN,
    RADIO,
    SPOTIFY,
}

@Immutable
data class CarUiState(
    val activeTab: CarTab = CarTab.DASHBOARD,
    val layoutMode: LayoutMode = LayoutMode.SPLIT_DASH,
    val taskbarPosition: TaskbarPosition = TaskbarPosition.LEFT,
    val splitRatio: Float = 0.62f,
    val touchScale: TouchScale = TouchScale.STANDARD,
    val lowEndMode: Boolean = false,
    val terrain3dEnabled: Boolean = true,
    val highRefresh120Hz: Boolean = true,
    val detectedDisplayHz: Int = 60,
    val carModelId: String = CarModelPreset.VW_GOLF.id,
    val carModelName: String = CarModelPreset.VW_GOLF.displayName,
    val vehicleMassKg: Double = CarModelPreset.VW_GOLF.massKg,
    val tankCapacityLiters: Double = CarModelPreset.VW_GOLF.tankLiters,
    val preferSpeedRoute: Boolean = false,
    val mapVisualMode: MapVisualMode = MapVisualMode.SATELLITE_3D_HYBRID,
    val tilt3dEnabled: Boolean = true,
    val followCamera: Boolean = true,
    val accentPreset: AccentPreset = AccentPreset.MATERIAL_YOU,
    val appLang: AppLang = AppLang.AUTO,
    val voiceLang: AppLang = AppLang.AUTO,

    val showWeatherDrawer: Boolean = false,
    val showCalendarDrawer: Boolean = false,
    val showLayoutDrawer: Boolean = false,
    val showVoiceOverlay: Boolean = false,
    val statusBanner: String? = null,

    val searchQuery: String = "",
    val searchResults: List<PlaceSearchResult> = emptyList(),
    val isSearchingPlace: Boolean = false,
    val destinationName: String? = null,
    val routeOptions: List<RouteOption> = emptyList(),
    val selectedRouteIndex: Int = 0,
    val isRouting: Boolean = false,

    val weather: WeatherData? = null,
    val calendarEvents: List<CalendarEventItem> = emptyList(),
    val localReminders: List<LocalCalendarReminder> = emptyList(),

    val musicTab: MusicSourceTab = MusicSourceTab.BUILT_IN,
    val spotifyClientId: String = "",
    val spotifyConnected: Boolean = false,
    val spotifyState: SpotifyPlaybackState = SpotifyPlaybackState(),
    val spotifyTracks: List<SpotifyTrack> = emptyList(),
    val spotifyArt: Bitmap? = null,
    val spotifyStatusMsg: String? = null,

    val youtubeUrl: String = "https://m.youtube.com",
    val youtubePassengerConfirmed: Boolean = false,

    val flappyBestScore: Int = 0,
)

class CarPlayViewModel(application: Application) : AndroidViewModel(application) {
    val prefs = CarPreferences(application)
    val locationTracker = LocationTracker(application, prefs)
    val localAudio = LocalAudioPlayer(application)
    val spotifyClient = SpotifyClient(prefs)

    private val initialModelPreset = CarModelPreset.fromId(prefs.carModelId)
    private val initialCarName = if (initialModelPreset == CarModelPreset.CUSTOM) {
        prefs.customCarModelName.ifBlank { initialModelPreset.displayName }
    } else {
        initialModelPreset.displayName
    }

    private val _ui = MutableStateFlow(
        CarUiState(
            layoutMode = prefs.layoutMode,
            taskbarPosition = prefs.taskbarPosition,
            splitRatio = prefs.splitRatio,
            touchScale = prefs.touchScale,
            lowEndMode = prefs.lowEndMode,
            terrain3dEnabled = prefs.terrain3dEnabled,
            highRefresh120Hz = prefs.highRefresh120Hz,
            carModelId = prefs.carModelId,
            carModelName = initialCarName,
            vehicleMassKg = prefs.vehicleMassKg,
            tankCapacityLiters = prefs.tankCapacityLiters,
            mapVisualMode = prefs.mapVisualMode,
            accentPreset = prefs.accentPreset,
            appLang = prefs.appLang,
            voiceLang = prefs.voiceLang,
            localReminders = prefs.loadReminders(),
            spotifyClientId = prefs.spotifyClientId,
            spotifyConnected = spotifyClient.isConnected(),
            flappyBestScore = prefs.flappyBestScore,
        )
    )
    val ui: StateFlow<CarUiState> = _ui.asStateFlow()

    val locationState: StateFlow<CarLocationState> = locationTracker.state
    val localPlayerState: StateFlow<LocalPlayerState> = localAudio.state

    val voiceAssistant = VoiceAssistant(application) { recognized ->
        processVoiceText(recognized)
    }
    val voiceState: StateFlow<VoiceUiState> = voiceAssistant.state

    private var lowFuelSpokenNotified = false

    init {
        RustEngine.safeFlappySetBest(prefs.flappyBestScore)
        locationTracker.start()
        refreshWeather()
        refreshCalendar()
        if (localAudio.hasAudioPermission()) {
            viewModelScope.launch { localAudio.scanDeviceTracks() }
        }
        startOptimizedLoops()
    }

    private fun startOptimizedLoops() {
        viewModelScope.launch {
            var tick = 0
            while (isActive) {
                if (localAudio.state.value.isPlaying) {
                    localAudio.pollProgress()
                }
                if (tick % 12 == 0) {
                    locationTracker.persistTelemetry()
                }
                val spotifyStep = if (_ui.value.lowEndMode) 6 else 3
                if (tick % spotifyStep == 0 && _ui.value.spotifyConnected && _ui.value.musicTab == MusicSourceTab.SPOTIFY) {
                    pollSpotify()
                }
                val telem = locationState.value.telemetry
                if (_ui.value.routeOptions.isNotEmpty()) {
                    refreshRouteReachability(telem.fuelLiters)
                }
                if (telem.isLowFuel && !lowFuelSpokenNotified) {
                    lowFuelSpokenNotified = true
                    val isPl = stringsFor(_ui.value.appLang).isPolish
                    val msg = if (isPl) {
                        "Uwaga, niski poziom paliwa. Zasięg około ${telem.estimatedRangeKm.roundToInt()} kilometrów."
                    } else {
                        "Low fuel warning. About ${telem.estimatedRangeKm.roundToInt()} kilometers range remaining."
                    }
                    voiceAssistant.speak(msg, if (isPl) "pl" else "en")
                } else if (!telem.isLowFuel) {
                    lowFuelSpokenNotified = false
                }
                tick++
                delay(1000L)
            }
        }
    }

    fun onPermissionsUpdated() {
        locationTracker.start()
        refreshCalendar()
        if (localAudio.hasAudioPermission()) {
            viewModelScope.launch { localAudio.scanDeviceTracks() }
        }
    }

    fun selectTab(tab: CarTab) {
        val nextMusicTab = when {
            tab == CarTab.RADIO -> MusicSourceTab.RADIO
            tab == CarTab.MUSIC && _ui.value.musicTab == MusicSourceTab.RADIO -> MusicSourceTab.BUILT_IN
            else -> _ui.value.musicTab
        }
        _ui.value = _ui.value.copy(
            activeTab = tab,
            musicTab = nextMusicTab,
            showWeatherDrawer = false,
            showCalendarDrawer = false,
            showLayoutDrawer = false,
        )
    }

    fun toggleWeatherDrawer() {
        val next = !_ui.value.showWeatherDrawer
        _ui.value = _ui.value.copy(
            showWeatherDrawer = next,
            showCalendarDrawer = false,
            showLayoutDrawer = false,
        )
        if (next) refreshWeather()
    }

    fun toggleCalendarDrawer() {
        val next = !_ui.value.showCalendarDrawer
        _ui.value = _ui.value.copy(
            showCalendarDrawer = next,
            showWeatherDrawer = false,
            showLayoutDrawer = false,
        )
        if (next) refreshCalendar()
    }

    fun toggleLayoutDrawer() {
        _ui.value = _ui.value.copy(
            showLayoutDrawer = !_ui.value.showLayoutDrawer,
            showWeatherDrawer = false,
            showCalendarDrawer = false,
        )
    }

    fun openVoiceOverlay(autoStartListening: Boolean) {
        _ui.value = _ui.value.copy(showVoiceOverlay = true)
        if (autoStartListening && voiceAssistant.hasMicPermission()) {
            voiceAssistant.startListening(_ui.value.voiceLang)
        }
    }

    fun closeVoiceOverlay() {
        voiceAssistant.stopListening()
        _ui.value = _ui.value.copy(showVoiceOverlay = false)
    }

    fun updateSearchQuery(q: String) {
        _ui.value = _ui.value.copy(searchQuery = q)
    }

    fun searchDestination(query: String = _ui.value.searchQuery) {
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isSearchingPlace = true)
            val loc = locationState.value
            val isPl = stringsFor(_ui.value.appLang).isPolish
            val results = NetworkClients.searchPlaces(q, if (isPl) "pl" else "en", loc.lat, loc.lon)
            _ui.value = _ui.value.copy(isSearchingPlace = false, searchResults = results)
        }
    }

    fun navigateToPlace(place: PlaceSearchResult, preferSpeed: Boolean = _ui.value.preferSpeedRoute) {
        _ui.value = _ui.value.copy(
            searchResults = emptyList(),
            destinationName = place.name,
            searchQuery = place.name,
            isRouting = true,
            preferSpeedRoute = preferSpeed,
            activeTab = CarTab.DASHBOARD,
        )
        viewModelScope.launch {
            val loc = locationState.value
            val isPl = stringsFor(_ui.value.appLang).isPolish
            val routes = NetworkClients.fetchEcoAnalyzedRoutes(
                fromLat = loc.lat,
                fromLon = loc.lon,
                toLat = place.lat,
                toLon = place.lon,
                fuelAvailableLiters = loc.telemetry.fuelLiters,
                vehicleMassKg = _ui.value.vehicleMassKg,
                isPolish = isPl,
            )
            if (routes.isNotEmpty()) {
                val chosen = if (preferSpeed) {
                    routes.firstOrNull { it.evaluation.isFastest } ?: routes.first()
                } else {
                    routes.firstOrNull { it.evaluation.isBestFuelSaving } ?: routes.first()
                }
                val banner = when {
                    !chosen.evaluation.canReach -> {
                        if (isPl) chosen.evaluation.reachabilityPl else chosen.evaluation.reachabilityEn
                    }
                    routes.any { !it.evaluation.canReach } && routes.any { it.evaluation.canReach } -> {
                        stringsFor(_ui.value.appLang).ecoUnlocksBanner
                    }
                    else -> null
                }
                _ui.value = _ui.value.copy(
                    isRouting = false,
                    routeOptions = routes,
                    selectedRouteIndex = chosen.index,
                    statusBanner = banner,
                )
                locationTracker.setRoutePathForEta(
                    distanceMeters = chosen.evaluation.distanceKm * 1000.0,
                    durationSec = chosen.evaluation.durationMin * 60.0,
                )
            } else {
                _ui.value = _ui.value.copy(
                    isRouting = false,
                    statusBanner = if (isPl) "Nie udało się pobrać trasy" else "Route unavailable",
                )
            }
        }
    }

    fun adjustFuelDelta(deltaLiters: Double) {
        locationTracker.adjustFuelDelta(deltaLiters)
        refreshRouteReachability(locationTracker.state.value.telemetry.fuelLiters)
    }

    fun fillTank() {
        locationTracker.fillTank()
        refreshRouteReachability(locationTracker.state.value.telemetry.fuelLiters)
    }

    private fun refreshRouteReachability(fuelAvail: Double) {
        val currentRoutes = _ui.value.routeOptions
        if (currentRoutes.isEmpty()) return
        var anyChanged = false
        val updated = currentRoutes.map { opt ->
            val ev = opt.evaluation
            val est = ev.estimatedLiters
            val canReach = fuelAvail >= est * 1.08
            val margin = ((fuelAvail - est) * 10.0).roundToInt() / 10.0
            val reachEn: String
            val reachPl: String
            if (canReach) {
                val left = maxOf(0.0, margin)
                reachEn = "Reachable (~${"%.1f".format(java.util.Locale.US, left)} L left)"
                reachPl = "Dojedziesz (zostanie ~${"%.1f".format(java.util.Locale.US, left)} L)"
            } else if (fuelAvail >= est) {
                val left = maxOf(0.0, margin)
                reachEn = "Tight fuel margin (~${"%.1f".format(java.util.Locale.US, left)} L left — refuel soon)"
                reachPl = "Mały zapas (~${"%.1f".format(java.util.Locale.US, left)} L — zatankuj po drodze)"
            } else {
                val deficit = maxOf(0.1, ((est - fuelAvail) * 10.0).roundToInt() / 10.0)
                reachEn = "Cannot reach: short ${"%.1f".format(java.util.Locale.US, deficit)} L — Refuel"
                reachPl = "Brak zasięgu: brakuje ${"%.1f".format(java.util.Locale.US, deficit)} L — Zatankuj"
            }
            if (ev.canReach != canReach || ev.reachabilityEn != reachEn) {
                anyChanged = true
                opt.copy(
                    evaluation = ev.copy(
                        canReach = canReach,
                        fuelMarginLiters = margin,
                        reachabilityEn = reachEn,
                        reachabilityPl = reachPl,
                    )
                )
            } else {
                opt
            }
        }
        if (anyChanged) {
            _ui.value = _ui.value.copy(routeOptions = updated)
        }
    }

    fun selectRouteOption(index: Int) {
        val chosen = _ui.value.routeOptions.firstOrNull { it.index == index } ?: return
        _ui.value = _ui.value.copy(
            selectedRouteIndex = index,
            preferSpeedRoute = chosen.evaluation.isFastest && !chosen.evaluation.isBestFuelSaving,
        )
        locationTracker.setRoutePathForEta(
            distanceMeters = chosen.evaluation.distanceKm * 1000.0,
            durationSec = chosen.evaluation.durationMin * 60.0,
        )
    }

    fun setPreferSpeedRoute(preferSpeed: Boolean) {
        _ui.value = _ui.value.copy(preferSpeedRoute = preferSpeed)
        val routes = _ui.value.routeOptions
        if (routes.isNotEmpty()) {
            val target = if (preferSpeed) {
                routes.firstOrNull { it.evaluation.isFastest } ?: routes.first()
            } else {
                routes.firstOrNull { it.evaluation.isBestFuelSaving } ?: routes.first()
            }
            selectRouteOption(target.index)
        }
    }

    fun clearActiveRoute() {
        locationTracker.clearRoute()
        _ui.value = _ui.value.copy(
            destinationName = null,
            routeOptions = emptyList(),
            searchResults = emptyList(),
        )
    }

    fun setMapVisualMode(mode: MapVisualMode) {
        prefs.mapVisualMode = mode
        _ui.value = _ui.value.copy(mapVisualMode = mode)
    }

    fun toggleTilt3d() {
        _ui.value = _ui.value.copy(tilt3dEnabled = !_ui.value.tilt3dEnabled)
    }

    fun setFollowCamera(follow: Boolean) {
        _ui.value = _ui.value.copy(followCamera = follow)
    }

    fun setLayoutMode(mode: LayoutMode) {
        prefs.layoutMode = mode
        _ui.value = _ui.value.copy(layoutMode = mode)
    }

    fun setTaskbarPosition(pos: TaskbarPosition) {
        prefs.taskbarPosition = pos
        _ui.value = _ui.value.copy(taskbarPosition = pos)
    }

    fun setSplitRatio(ratio: Float) {
        prefs.splitRatio = ratio
        _ui.value = _ui.value.copy(splitRatio = ratio)
    }

    fun setTouchScale(scale: TouchScale) {
        prefs.touchScale = scale
        _ui.value = _ui.value.copy(touchScale = scale)
    }

    fun setLowEndMode(enabled: Boolean) {
        prefs.lowEndMode = enabled
        val nextTerrain = if (enabled) false else prefs.terrain3dEnabled
        prefs.terrain3dEnabled = nextTerrain
        _ui.value = _ui.value.copy(lowEndMode = enabled, terrain3dEnabled = nextTerrain)
    }

    fun setTerrain3dEnabled(enabled: Boolean) {
        prefs.terrain3dEnabled = enabled
        _ui.value = _ui.value.copy(terrain3dEnabled = enabled)
    }

    fun setHighRefresh120Hz(enabled: Boolean) {
        prefs.highRefresh120Hz = enabled
        _ui.value = _ui.value.copy(highRefresh120Hz = enabled)
    }

    fun updateDetectedDisplayHz(hz: Int) {
        if (hz > 0 && hz != _ui.value.detectedDisplayHz) {
            _ui.value = _ui.value.copy(detectedDisplayHz = hz)
        }
    }

    fun selectCarPreset(preset: CarModelPreset) {
        prefs.carModelId = preset.id
        prefs.vehicleMassKg = preset.massKg
        prefs.tankCapacityLiters = preset.tankLiters
        val curFuel = locationState.value.telemetry.fuelLiters.coerceAtMost(preset.tankLiters)
        locationTracker.setFuelLiters(curFuel, preset.tankLiters)
        refreshRouteReachability(curFuel)
        val displayName = if (preset == CarModelPreset.CUSTOM) prefs.customCarModelName else preset.displayName
        _ui.value = _ui.value.copy(
            carModelId = preset.id,
            carModelName = displayName,
            vehicleMassKg = preset.massKg,
            tankCapacityLiters = preset.tankLiters,
        )
    }

    fun setCustomCarName(name: String) {
        val clean = name.ifBlank { "Custom Vehicle" }
        prefs.carModelId = CarModelPreset.CUSTOM.id
        prefs.customCarModelName = clean
        _ui.value = _ui.value.copy(carModelId = CarModelPreset.CUSTOM.id, carModelName = clean)
    }

    fun setTankCapacity(liters: Double) {
        val cap = liters.coerceIn(20.0, 140.0)
        prefs.tankCapacityLiters = cap
        val curFuel = locationState.value.telemetry.fuelLiters.coerceAtMost(cap)
        locationTracker.setFuelLiters(curFuel, cap)
        refreshRouteReachability(curFuel)
        _ui.value = _ui.value.copy(tankCapacityLiters = cap)
    }

    fun playRadioStation(station: RadioStation) {
        _ui.value = _ui.value.copy(musicTab = MusicSourceTab.RADIO)
        localAudio.playRadioStation(station)
    }

    fun addCustomRadioStation(name: String, url: String) {
        _ui.value = _ui.value.copy(musicTab = MusicSourceTab.RADIO)
        localAudio.addCustomRadioStation(name, url)
    }

    fun mediaTogglePlayPause() {
        when {
            _ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected -> spotifyTogglePlayPause()
            _ui.value.musicTab == MusicSourceTab.RADIO -> localAudio.togglePlayPause()
            else -> localAudio.togglePlayPause()
        }
    }

    fun mediaNext() {
        when {
            _ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected -> spotifyNext()
            _ui.value.musicTab == MusicSourceTab.RADIO -> localAudio.nextRadio()
            else -> localAudio.next()
        }
    }

    fun mediaPrev() {
        when {
            _ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected -> spotifyPrev()
            _ui.value.musicTab == MusicSourceTab.RADIO -> localAudio.prevRadio()
            else -> localAudio.prev()
        }
    }

    fun setAccentPreset(preset: AccentPreset) {
        prefs.accentPreset = preset
        _ui.value = _ui.value.copy(accentPreset = preset)
    }

    fun setAppLang(lang: AppLang) {
        prefs.appLang = lang
        _ui.value = _ui.value.copy(appLang = lang)
    }

    fun setVoiceLang(lang: AppLang) {
        prefs.voiceLang = lang
        _ui.value = _ui.value.copy(voiceLang = lang)
    }

    fun refreshWeather() {
        viewModelScope.launch {
            val loc = locationState.value
            val w = NetworkClients.fetchWeather(loc.lat, loc.lon)
            if (w != null) _ui.value = _ui.value.copy(weather = w)
        }
    }

    fun refreshCalendar() {
        viewModelScope.launch {
            val events = CalendarProviderHelper.loadUpcomingEvents(
                getApplication(),
                _ui.value.localReminders,
            )
            _ui.value = _ui.value.copy(calendarEvents = events)
        }
    }

    fun addLocalReminder(title: String, location: String, timeLabel: String) {
        if (title.isBlank()) return
        val newItem = LocalCalendarReminder(
            id = System.currentTimeMillis(),
            title = title.trim(),
            location = location.trim(),
            timeLabel = timeLabel.ifBlank { "Today" },
        )
        val updated = _ui.value.localReminders + newItem
        prefs.saveReminders(updated)
        _ui.value = _ui.value.copy(localReminders = updated)
        refreshCalendar()
    }

    fun deleteLocalReminder(id: Long) {
        val updated = _ui.value.localReminders.filterNot { it.id == id }
        prefs.saveReminders(updated)
        _ui.value = _ui.value.copy(localReminders = updated)
        refreshCalendar()
    }

    fun navigateToEventLocation(locationText: String) {
        if (locationText.isBlank()) return
        _ui.value = _ui.value.copy(showCalendarDrawer = false, activeTab = CarTab.DASHBOARD, searchQuery = locationText)
        viewModelScope.launch {
            val loc = locationState.value
            val isPl = stringsFor(_ui.value.appLang).isPolish
            val results = NetworkClients.searchPlaces(locationText, if (isPl) "pl" else "en", loc.lat, loc.lon)
            if (results.isNotEmpty()) {
                navigateToPlace(results.first())
            }
        }
    }

    fun setMusicSourceTab(tab: MusicSourceTab) {
        _ui.value = _ui.value.copy(musicTab = tab)
        if (tab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected) {
            refreshSpotifyLibrary()
        }
    }

    fun saveSpotifyClientId(clientId: String) {
        prefs.spotifyClientId = clientId.trim()
        _ui.value = _ui.value.copy(spotifyClientId = prefs.spotifyClientId)
    }

    fun getSpotifyAuthIntent(): Intent? {
        val url = spotifyClient.buildAuthorizeUrl() ?: return null
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    fun handleSpotifyRedirect(uri: Uri) {
        if (uri.scheme != "librecar" || uri.host != "spotify-callback") return
        val code = uri.getQueryParameter("code") ?: return
        viewModelScope.launch {
            val ok = spotifyClient.exchangeCode(code)
            _ui.value = _ui.value.copy(
                spotifyConnected = ok,
                spotifyStatusMsg = if (ok) "Spotify connected" else "Spotify auth failed",
                musicTab = if (ok) MusicSourceTab.SPOTIFY else _ui.value.musicTab,
            )
            if (ok) {
                pollSpotify()
                refreshSpotifyLibrary()
            }
        }
    }

    fun disconnectSpotify() {
        spotifyClient.disconnect()
        _ui.value = _ui.value.copy(
            spotifyConnected = false,
            spotifyState = SpotifyPlaybackState(),
            spotifyTracks = emptyList(),
            spotifyArt = null,
        )
    }

    fun searchSpotify(query: String) {
        viewModelScope.launch {
            val res = spotifyClient.searchTracks(query)
            _ui.value = _ui.value.copy(spotifyTracks = res)
        }
    }

    fun refreshSpotifyLibrary() {
        viewModelScope.launch {
            val tracks = spotifyClient.fetchSavedOrTopTracks()
            if (tracks.isNotEmpty()) _ui.value = _ui.value.copy(spotifyTracks = tracks)
        }
    }

    fun playSpotifyTrack(track: SpotifyTrack) {
        viewModelScope.launch {
            val ok = spotifyClient.playUri(track.uri)
            if (ok) {
                delay(350)
                pollSpotify()
            } else if (!track.previewUrl.isNullOrBlank()) {
                localAudio.playPreviewUrl(track.previewUrl, track.title, track.artist)
                _ui.value = _ui.value.copy(spotifyStatusMsg = "Playing 30s preview")
            } else {
                _ui.value = _ui.value.copy(spotifyStatusMsg = "Start Spotify on an active device")
            }
        }
    }

    fun spotifyTogglePlayPause() {
        viewModelScope.launch {
            val playing = _ui.value.spotifyState.isPlaying
            spotifyClient.setPlaying(!playing)
            delay(250)
            pollSpotify()
        }
    }

    fun spotifyNext() {
        viewModelScope.launch {
            spotifyClient.skipNext()
            delay(250)
            pollSpotify()
        }
    }

    fun spotifyPrev() {
        viewModelScope.launch {
            spotifyClient.skipPrev()
            delay(250)
            pollSpotify()
        }
    }

    private suspend fun pollSpotify() {
        val st = spotifyClient.fetchPlaybackState() ?: return
        val art = spotifyClient.loadArtwork(st.track?.artworkUrl, _ui.value.lowEndMode)
        _ui.value = _ui.value.copy(spotifyState = st, spotifyArt = art)
    }

    fun openYouTubeQuery(query: String) {
        val url = if (query.isBlank()) {
            "https://m.youtube.com"
        } else {
            "https://m.youtube.com/results?search_query=${Uri.encode(query)}"
        }
        _ui.value = _ui.value.copy(activeTab = CarTab.YOUTUBE, youtubeUrl = url)
    }

    fun confirmYouTubePassenger() {
        _ui.value = _ui.value.copy(youtubePassengerConfirmed = true)
    }

    fun updateFlappyBest(score: Int) {
        if (score > _ui.value.flappyBestScore) {
            prefs.flappyBestScore = score
            _ui.value = _ui.value.copy(flappyBestScore = score)
        }
    }

    fun processVoiceText(rawText: String) {
        val prefLang = _ui.value.voiceLang.code
        val res = RustEngine.safeParseVoiceCommand(rawText, prefLang)
        val isPl = res.detectedLang == "pl" || stringsFor(_ui.value.appLang).isPolish
        var reply = if (isPl) res.replyPl else res.replyEn

        when (res.intent) {
            "navigate" -> {
                if (res.target.isNotBlank()) {
                    _ui.value = _ui.value.copy(
                        activeTab = CarTab.DASHBOARD,
                        searchQuery = res.target,
                        preferSpeedRoute = !res.ecoPriority,
                    )
                    viewModelScope.launch {
                        val loc = locationState.value
                        val found = NetworkClients.searchPlaces(
                            res.target,
                            if (isPl) "pl" else "en",
                            loc.lat,
                            loc.lon,
                        )
                        if (found.isNotEmpty()) {
                            navigateToPlace(found.first(), preferSpeed = !res.ecoPriority)
                        }
                    }
                }
            }
            "open_radio" -> {
                _ui.value = _ui.value.copy(activeTab = CarTab.RADIO, musicTab = MusicSourceTab.RADIO)
                localAudio.playRadioByQuery(res.target)
            }
            "music_play" -> {
                _ui.value = _ui.value.copy(activeTab = CarTab.MUSIC)
                if (_ui.value.spotifyConnected && res.target.isNotBlank()) {
                    _ui.value = _ui.value.copy(musicTab = MusicSourceTab.SPOTIFY)
                    viewModelScope.launch {
                        val hits = spotifyClient.searchTracks(res.target)
                        _ui.value = _ui.value.copy(spotifyTracks = hits)
                        hits.firstOrNull()?.let { playSpotifyTrack(it) }
                    }
                } else {
                    localAudio.playByQuery(res.target)
                }
            }
            "music_pause" -> {
                if (_ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected) {
                    viewModelScope.launch { spotifyClient.setPlaying(false) }
                } else {
                    localAudio.pause()
                }
            }
            "music_resume" -> {
                if (_ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected) {
                    viewModelScope.launch { spotifyClient.setPlaying(true) }
                } else {
                    localAudio.resume()
                }
            }
            "music_next" -> {
                if (_ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected) spotifyNext() else localAudio.next()
            }
            "music_prev" -> {
                if (_ui.value.musicTab == MusicSourceTab.SPOTIFY && _ui.value.spotifyConnected) spotifyPrev() else localAudio.prev()
            }
            "open_youtube" -> openYouTubeQuery(res.target)
            "open_flappy_bird" -> selectTab(CarTab.FLAPPY_BIRD)
            "map_satellite_3d" -> {
                setMapVisualMode(MapVisualMode.SATELLITE_3D_HYBRID)
                _ui.value = _ui.value.copy(tilt3dEnabled = true, activeTab = CarTab.DASHBOARD)
            }
            "show_weather" -> toggleWeatherDrawer()
            "show_calendar" -> toggleCalendarDrawer()
            "reset_km_counter" -> locationTracker.resetTripA()
            "switch_layout" -> setLayoutMode(LayoutMode.fromId(res.target.lowercase()))
            "fill_tank" -> fillTank()
            "set_fuel_liters" -> {
                res.target.toDoubleOrNull()?.let { liters ->
                    locationTracker.setFuelLiters(liters)
                    refreshRouteReachability(locationTracker.state.value.telemetry.fuelLiters)
                }
            }
            "check_fuel_range" -> {
                val telem = locationState.value.telemetry
                val activeRoute = _ui.value.routeOptions.firstOrNull { it.index == _ui.value.selectedRouteIndex }
                reply = if (activeRoute != null) {
                    if (isPl) activeRoute.evaluation.reachabilityPl else activeRoute.evaluation.reachabilityEn
                } else {
                    if (isPl) {
                        "Masz ${telem.fuelLiters} litrów, zasięg około ${telem.estimatedRangeKm.roundToInt()} kilometrów."
                    } else {
                        "You have ${telem.fuelLiters} liters, about ${telem.estimatedRangeKm.roundToInt()} kilometers range."
                    }
                }
            }
        }

        _ui.value = _ui.value.copy(showVoiceOverlay = false, statusBanner = reply)
        voiceAssistant.speak(reply, if (isPl) "pl" else "en")
    }

    fun dismissBanner() {
        _ui.value = _ui.value.copy(statusBanner = null)
    }

    override fun onCleared() {
        super.onCleared()
        locationTracker.stop()
        localAudio.destroy()
        voiceAssistant.destroy()
    }
}
