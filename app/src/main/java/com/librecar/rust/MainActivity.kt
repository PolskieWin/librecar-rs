package com.librecar.rust

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.librecar.rust.data.CalendarProviderHelper
import com.librecar.rust.data.CarLocationState
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.LocalPlayerState
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.data.VoiceUiState
import com.librecar.rust.ui.components.CalendarDrawer
import com.librecar.rust.ui.components.CarTaskbar
import com.librecar.rust.ui.components.LayoutSettingsDrawer
import com.librecar.rust.ui.components.VoiceOverlay
import com.librecar.rust.ui.components.WeatherDrawer
import com.librecar.rust.ui.screens.FlappyBirdPane
import com.librecar.rust.ui.screens.MapPane
import com.librecar.rust.ui.screens.MusicPane
import com.librecar.rust.ui.screens.YouTubePane
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LibreCarTheme
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
import com.librecar.rust.ui.theme.PureBlack
import com.librecar.rust.ui.theme.stringsFor
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val vm: CarPlayViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        intent?.data?.let { vm.handleSpotifyRedirect(it) }

        setContent {
            val ui by vm.ui.collectAsState()
            val loc by vm.locationState.collectAsState()
            val localMusic by vm.localPlayerState.collectAsState()
            val voiceState by vm.voiceState.collectAsState()
            val strings = stringsFor(ui.appLang)

            val context = LocalContext.current
            val permLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestMultiplePermissions()
            ) {
                vm.onPermissionsUpdated()
            }

            LaunchedEffect(Unit) {
                if (!vm.locationTracker.hasLocationPermission()) {
                    permLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        )
                    )
                }
            }

            LaunchedEffect(ui.highRefresh120Hz, ui.lowEndMode) {
                val curDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display else @Suppress("DEPRECATION") windowManager.defaultDisplay
                val modes = curDisplay?.supportedModes ?: emptyArray()
                val curMode = curDisplay?.mode
                val sameRes = modes.filter { curMode == null || (it.physicalWidth == curMode.physicalWidth && it.physicalHeight == curMode.physicalHeight) }
                val highestMode = sameRes.maxByOrNull { it.refreshRate }
                val hardwareMaxHz = highestMode?.refreshRate?.roundToInt() ?: 60
                vm.updateDetectedDisplayHz(hardwareMaxHz)

                val wantHigh = ui.highRefresh120Hz && !ui.lowEndMode
                val targetMode = if (wantHigh) {
                    highestMode
                } else {
                    sameRes.minByOrNull { kotlin.math.abs(it.refreshRate - 60f) }
                }
                val lp = window.attributes
                if (targetMode != null) {
                    lp.preferredDisplayModeId = targetMode.modeId
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    lp.preferredRefreshRate = if (wantHigh) 120f else 60f
                }
                window.attributes = lp
            }

            LaunchedEffect(ui.statusBanner) {
                if (ui.statusBanner != null) {
                    delay(4500L)
                    vm.dismissBanner()
                }
            }

            LibreCarTheme(accentPreset = ui.accentPreset) {
                CompositionLocalProvider(LocalCarStrings provides strings) {
                    CarPlayRoot(
                        ui = ui,
                        loc = loc,
                        localMusic = localMusic,
                        voiceState = voiceState,
                        hasLocationPerm = vm.locationTracker.hasLocationPermission(),
                        hasAudioPerm = vm.localAudio.hasAudioPermission(),
                        hasCalendarPerm = CalendarProviderHelper.hasCalendarPermission(context),
                        onRequestLocation = {
                            permLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION,
                                )
                            )
                        },
                        onRequestAudio = {
                            val p = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.READ_MEDIA_AUDIO
                            } else {
                                Manifest.permission.READ_EXTERNAL_STORAGE
                            }
                            permLauncher.launch(arrayOf(p))
                        },
                        onRequestCalendar = {
                            permLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR))
                        },
                        onRequestMicAndOpenVoice = {
                            if (vm.voiceAssistant.hasMicPermission()) {
                                vm.openVoiceOverlay(autoStartListening = true)
                            } else {
                                permLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                                vm.openVoiceOverlay(autoStartListening = false)
                            }
                        },
                        onLaunchSpotifyAuth = {
                            vm.getSpotifyAuthIntent()?.let { startActivity(it) }
                        },
                        vm = vm,
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.data?.let { uri ->
            lifecycleScope.launch { vm.handleSpotifyRedirect(uri) }
        }
    }
}

@Composable
private fun CarPlayRoot(
    ui: CarUiState,
    loc: CarLocationState,
    localMusic: LocalPlayerState,
    voiceState: VoiceUiState,
    hasLocationPerm: Boolean,
    hasAudioPerm: Boolean,
    hasCalendarPerm: Boolean,
    onRequestLocation: () -> Unit,
    onRequestAudio: () -> Unit,
    onRequestCalendar: () -> Unit,
    onRequestMicAndOpenVoice: () -> Unit,
    onLaunchSpotifyAuth: () -> Unit,
    vm: CarPlayViewModel,
) {
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler(enabled = ui.activeTab != CarTab.DASHBOARD) {
        vm.selectTab(CarTab.DASHBOARD)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PureBlack)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(6.dp),
    ) {
        val taskbar: @Composable () -> Unit = {
            CarTaskbar(
                ui = ui,
                onSelectTab = vm::selectTab,
                onToggleWeather = vm::toggleWeatherDrawer,
                onToggleCalendar = vm::toggleCalendarDrawer,
                onToggleLayout = vm::toggleLayoutDrawer,
                onOpenVoice = onRequestMicAndOpenVoice,
            )
        }

        val workspace: @Composable (Modifier) -> Unit = { mod ->
            Box(modifier = mod) {
                when (ui.activeTab) {
                    CarTab.DASHBOARD -> {
                        DashboardContent(
                            ui = ui,
                            loc = loc,
                            localMusic = localMusic,
                            isLandscape = isLandscape,
                            hasLocationPerm = hasLocationPerm,
                            hasAudioPerm = hasAudioPerm,
                            onRequestLocation = onRequestLocation,
                            onRequestAudio = onRequestAudio,
                            onOpenVoice = onRequestMicAndOpenVoice,
                            onLaunchSpotifyAuth = onLaunchSpotifyAuth,
                            vm = vm,
                        )
                    }
                    CarTab.MUSIC, CarTab.RADIO -> {
                        MusicPane(
                            ui = ui,
                            localState = localMusic,
                            compactCard = false,
                            hasAudioPerm = hasAudioPerm,
                            onSelectMusicTab = { mTab ->
                                vm.setMusicSourceTab(mTab)
                                if (mTab == MusicSourceTab.RADIO) vm.selectTab(CarTab.RADIO) else vm.selectTab(CarTab.MUSIC)
                            },
                            onMediaToggle = vm::mediaTogglePlayPause,
                            onMediaNext = vm::mediaNext,
                            onMediaPrev = vm::mediaPrev,
                            onLocalSeek = vm.localAudio::seekTo,
                            onLocalSelectIndex = vm.localAudio::playLocalIndex,
                            onPlayRadioStation = vm::playRadioStation,
                            onAddCustomRadio = vm::addCustomRadioStation,
                            onRequestAudioPerm = onRequestAudio,
                            onScanLocal = { vm.onPermissionsUpdated() },
                            onConnectSpotify = onLaunchSpotifyAuth,
                            onSearchSpotify = vm::searchSpotify,
                            onPlaySpotifyTrack = vm::playSpotifyTrack,
                            onOpenLayoutSettings = vm::toggleLayoutDrawer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    CarTab.YOUTUBE -> {
                        YouTubePane(
                            targetUrl = ui.youtubeUrl,
                            speedKmh = loc.telemetry.speedKmh,
                            passengerConfirmed = ui.youtubePassengerConfirmed,
                            lowEndMode = ui.lowEndMode,
                            onConfirmPassenger = vm::confirmYouTubePassenger,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    CarTab.FLAPPY_BIRD -> {
                        FlappyBirdPane(
                            speedKmh = loc.telemetry.speedKmh,
                            bestScore = ui.flappyBestScore,
                            lowEndMode = ui.lowEndMode,
                            onBestScoreUpdate = vm::updateFlappyBest,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }

        when (ui.taskbarPosition) {
            TaskbarPosition.LEFT -> {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    taskbar()
                    workspace(Modifier.weight(1f).fillMaxHeight())
                }
            }
            TaskbarPosition.RIGHT -> {
                Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    workspace(Modifier.weight(1f).fillMaxHeight())
                    taskbar()
                }
            }
            TaskbarPosition.BOTTOM -> {
                Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    workspace(Modifier.weight(1f).fillMaxWidth())
                    taskbar()
                }
            }
        }

        ui.statusBanner?.let { msg ->
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xF0111115))
                    .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                    .clickable { vm.dismissBanner() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(msg, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        if (ui.showWeatherDrawer) {
            WeatherDrawer(ui = ui, onRefresh = vm::refreshWeather, onClose = vm::toggleWeatherDrawer)
        }
        if (ui.showCalendarDrawer) {
            CalendarDrawer(
                ui = ui,
                hasCalendarPerm = hasCalendarPerm,
                onRequestCalendarPerm = onRequestCalendar,
                onAddReminder = vm::addLocalReminder,
                onDeleteReminder = vm::deleteLocalReminder,
                onDriveToLocation = vm::navigateToEventLocation,
                onClose = vm::toggleCalendarDrawer,
            )
        }
        if (ui.showLayoutDrawer) {
            LayoutSettingsDrawer(
                ui = ui,
                telemetry = loc.telemetry,
                onSetLayoutMode = vm::setLayoutMode,
                onSetTaskbarPos = vm::setTaskbarPosition,
                onSetSplitRatio = vm::setSplitRatio,
                onSetTouchScale = vm::setTouchScale,
                onSetLowEndMode = vm::setLowEndMode,
                onSetTerrain3d = vm::setTerrain3dEnabled,
                onSetHighRefresh120Hz = vm::setHighRefresh120Hz,
                onSelectCarPreset = vm::selectCarPreset,
                onSetCustomCarName = vm::setCustomCarName,
                onSetTankCapacity = vm::setTankCapacity,
                onSetAccent = vm::setAccentPreset,
                onSetAppLang = vm::setAppLang,
                onSetVoiceLang = vm::setVoiceLang,
                onAdjustFuelDelta = vm::adjustFuelDelta,
                onFillTank = vm::fillTank,
                onSaveSpotifyId = vm::saveSpotifyClientId,
                onConnectSpotify = onLaunchSpotifyAuth,
                onDisconnectSpotify = vm::disconnectSpotify,
                onResetTripA = vm.locationTracker::resetTripA,
                onResetTripB = vm.locationTracker::resetTripB,
                onClose = vm::toggleLayoutDrawer,
            )
        }
        if (ui.showVoiceOverlay) {
            VoiceOverlay(
                voiceState = voiceState,
                onStartListening = onRequestMicAndOpenVoice,
                onSubmitTextCommand = vm::processVoiceText,
                onClose = vm::closeVoiceOverlay,
            )
        }
    }
}

@Composable
private fun DashboardContent(
    ui: CarUiState,
    loc: CarLocationState,
    localMusic: LocalPlayerState,
    isLandscape: Boolean,
    hasLocationPerm: Boolean,
    hasAudioPerm: Boolean,
    onRequestLocation: () -> Unit,
    onRequestAudio: () -> Unit,
    onOpenVoice: () -> Unit,
    onLaunchSpotifyAuth: () -> Unit,
    vm: CarPlayViewModel,
) {
    val s = LocalCarStrings.current
    val effectiveMode = if (
        !isLandscape &&
        ui.layoutMode !in listOf(LayoutMode.DRIVE_FOCUS, LayoutMode.COMPACT_FIXED, LayoutMode.ULTRA_COMPACT)
    ) {
        LayoutMode.COMPACT_PORTRAIT
    } else {
        ui.layoutMode
    }

    val mapBlock: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
        MapPane(
            ui = ui,
            loc = loc,
            isCompactHud = compact,
            onQueryChange = vm::updateSearchQuery,
            onSearch = vm::searchDestination,
            onSelectPlace = { place -> vm.navigateToPlace(place) },
            onSelectRoute = vm::selectRouteOption,
            onSetPreferSpeed = vm::setPreferSpeedRoute,
            onClearRoute = vm::clearActiveRoute,
            onSetMapMode = vm::setMapVisualMode,
            onToggleTilt3d = vm::toggleTilt3d,
            onSetFollow = vm::setFollowCamera,
            onResetTripA = vm.locationTracker::resetTripA,
            onAdjustFuelDelta = vm::adjustFuelDelta,
            onFillTank = vm::fillTank,
            onOpenVoice = onOpenVoice,
            onRequestLocationPerm = onRequestLocation,
            hasLocationPerm = hasLocationPerm,
            modifier = mod,
        )
    }

    val musicBlock: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
        MusicPane(
            ui = ui,
            localState = localMusic,
            compactCard = compact,
            hasAudioPerm = hasAudioPerm,
            onSelectMusicTab = vm::setMusicSourceTab,
            onMediaToggle = vm::mediaTogglePlayPause,
            onMediaNext = vm::mediaNext,
            onMediaPrev = vm::mediaPrev,
            onLocalSeek = vm.localAudio::seekTo,
            onLocalSelectIndex = vm.localAudio::playLocalIndex,
            onPlayRadioStation = vm::playRadioStation,
            onAddCustomRadio = vm::addCustomRadioStation,
            onRequestAudioPerm = onRequestAudio,
            onScanLocal = { vm.onPermissionsUpdated() },
            onConnectSpotify = onLaunchSpotifyAuth,
            onSearchSpotify = vm::searchSpotify,
            onPlaySpotifyTrack = vm::playSpotifyTrack,
            onOpenLayoutSettings = vm::toggleLayoutDrawer,
            modifier = mod,
        )
    }

    when (effectiveMode) {
        LayoutMode.ULTRA_COMPACT -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                mapBlock(Modifier.weight(1f).fillMaxWidth(), true)
                CompactMediaDock(
                    ui = ui,
                    localMusic = localMusic,
                    dockHeightDp = 40,
                    showWeatherCal = true,
                    onOpenMedia = {
                        vm.selectTab(if (ui.musicTab == MusicSourceTab.RADIO) CarTab.RADIO else CarTab.MUSIC)
                    },
                    onMediaPrev = vm::mediaPrev,
                    onMediaToggle = vm::mediaTogglePlayPause,
                    onMediaNext = vm::mediaNext,
                    onToggleWeather = vm::toggleWeatherDrawer,
                    onToggleCalendar = vm::toggleCalendarDrawer,
                )
            }
        }
        LayoutMode.COMPACT_FIXED -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(1f).fillMaxWidth(), true)
                CompactMediaDock(
                    ui = ui,
                    localMusic = localMusic,
                    dockHeightDp = 48,
                    showWeatherCal = true,
                    onOpenMedia = {
                        vm.selectTab(if (ui.musicTab == MusicSourceTab.RADIO) CarTab.RADIO else CarTab.MUSIC)
                    },
                    onMediaPrev = vm::mediaPrev,
                    onMediaToggle = vm::mediaTogglePlayPause,
                    onMediaNext = vm::mediaNext,
                    onToggleWeather = vm::toggleWeatherDrawer,
                    onToggleCalendar = vm::toggleCalendarDrawer,
                )
            }
        }
        LayoutMode.DRIVE_FOCUS -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(1f).fillMaxWidth(), false)
                CompactMediaDock(
                    ui = ui,
                    localMusic = localMusic,
                    dockHeightDp = 48,
                    showWeatherCal = false,
                    onOpenMedia = {
                        vm.selectTab(if (ui.musicTab == MusicSourceTab.RADIO) CarTab.RADIO else CarTab.MUSIC)
                    },
                    onMediaPrev = vm::mediaPrev,
                    onMediaToggle = vm::mediaTogglePlayPause,
                    onMediaNext = vm::mediaNext,
                    onToggleWeather = vm::toggleWeatherDrawer,
                    onToggleCalendar = vm::toggleCalendarDrawer,
                )
            }
        }
        LayoutMode.MEDIA_SPLIT -> {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(0.5f).fillMaxHeight(), true)
                musicBlock(Modifier.weight(0.5f).fillMaxHeight(), false)
            }
        }
        LayoutMode.COMPACT_PORTRAIT -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(ui.splitRatio).fillMaxWidth(), true)
                musicBlock(Modifier.weight(1f - ui.splitRatio).fillMaxWidth(), true)
            }
        }
        LayoutMode.SPLIT_DASH -> {
            Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(ui.splitRatio).fillMaxHeight(), false)
                Column(
                    modifier = Modifier
                        .weight(1f - ui.splitRatio)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    // Compact non-stretched music card so Weather & Calendar always fit cleanly in horizontal mode!
                    musicBlock(Modifier.fillMaxWidth(), true)

                    // Weather Card (spacious, clean fit for temperature & wind)
                    val temp = ui.weather?.tempC?.roundToInt()?.let { "$it°C" } ?: "--°"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBlack)
                            .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = vm::toggleWeatherDrawer)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text(temp, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                        }
                        Text(
                            text = ui.weather?.let { "${it.windKmh.roundToInt()} km/h · ${it.precipProb}%" } ?: s.weatherTitle,
                            color = MutedText,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    // Calendar Card
                    val nextEv = ui.calendarEvents.firstOrNull()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(CardBlack)
                            .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                            .clickable(onClick = vm::toggleCalendarDrawer)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(CarIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(nextEv?.title ?: s.upcomingEvents, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(nextEv?.timeLabel ?: s.calendarTitle, color = MutedText, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactMediaDock(
    ui: CarUiState,
    localMusic: LocalPlayerState,
    dockHeightDp: Int,
    showWeatherCal: Boolean,
    onOpenMedia: () -> Unit,
    onMediaPrev: () -> Unit,
    onMediaToggle: () -> Unit,
    onMediaNext: () -> Unit,
    onToggleWeather: () -> Unit,
    onToggleCalendar: () -> Unit,
) {
    val s = LocalCarStrings.current
    val isSpotify = ui.musicTab == MusicSourceTab.SPOTIFY
    val isRadio = ui.musicTab == MusicSourceTab.RADIO || localMusic.isLiveRadio
    val isPlaying = if (isSpotify) ui.spotifyState.isPlaying else localMusic.isPlaying
    val activeTitle = when {
        isSpotify -> ui.spotifyState.track?.title ?: localMusic.currentTitle.ifBlank { "Spotify" }
        isRadio -> localMusic.currentTitle.ifBlank { localMusic.radioStations.firstOrNull()?.name ?: s.navRadio }
        else -> localMusic.currentTitle.ifBlank { localMusic.tracks.firstOrNull()?.title ?: s.navMusic }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dockHeightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardBlack)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onOpenMedia)
                .padding(horizontal = 4.dp, vertical = 4.dp),
        ) {
            Icon(
                imageVector = if (isRadio) CarIcons.Radio else CarIcons.Music,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = activeTitle,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        val btnSize = (dockHeightDp - 8).coerceIn(30, 38).dp
        IconButton(
            onClick = onMediaPrev,
            modifier = Modifier.size(btnSize).clip(CircleShape).background(ElevatedBlack),
        ) {
            Icon(CarIcons.SkipPrev, contentDescription = "Previous", tint = Color.White, modifier = Modifier.size(16.dp))
        }
        IconButton(
            onClick = onMediaToggle,
            modifier = Modifier.size(btnSize).clip(CircleShape).background(Color.White),
        ) {
            Icon(
                imageVector = if (isPlaying) CarIcons.Pause else CarIcons.Play,
                contentDescription = "Play/Pause",
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(
            onClick = onMediaNext,
            modifier = Modifier.size(btnSize).clip(CircleShape).background(ElevatedBlack),
        ) {
            Icon(CarIcons.SkipNext, contentDescription = "Next", tint = Color.White, modifier = Modifier.size(16.dp))
        }

        if (showWeatherCal) {
            Box(modifier = Modifier.width(0.75.dp).height(22.dp).background(HairlineBorder))

            val temp = ui.weather?.tempC?.roundToInt()?.let { "$it°C" } ?: "--°"
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .semantics { contentDescription = "Weather" }
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleWeather)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text(temp, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
            }

            Box(modifier = Modifier.width(0.75.dp).height(22.dp).background(HairlineBorder))

            val nextEv = ui.calendarEvents.firstOrNull()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .semantics { contentDescription = "Calendar" }
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onToggleCalendar)
                    .padding(horizontal = 5.dp, vertical = 4.dp),
            ) {
                Icon(CarIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                Text(nextEv?.timeLabel ?: s.calendarTitle, color = MutedText, fontSize = 11.sp, maxLines = 1)
            }
        }
    }
}
