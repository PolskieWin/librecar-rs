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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
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
                    CarTab.MUSIC -> {
                        MusicPane(
                            ui = ui,
                            localState = localMusic,
                            compactCard = false,
                            hasAudioPerm = hasAudioPerm,
                            onSelectMusicTab = vm::setMusicSourceTab,
                            onToggleLocalPlay = vm.localAudio::togglePlayPause,
                            onLocalNext = vm.localAudio::next,
                            onLocalPrev = vm.localAudio::prev,
                            onLocalSeek = vm.localAudio::seekTo,
                            onLocalSelectIndex = vm.localAudio::playLocalIndex,
                            onRequestAudioPerm = onRequestAudio,
                            onScanLocal = { vm.onPermissionsUpdated() },
                            onConnectSpotify = onLaunchSpotifyAuth,
                            onSearchSpotify = vm::searchSpotify,
                            onPlaySpotifyTrack = vm::playSpotifyTrack,
                            onSpotifyToggle = vm::spotifyTogglePlayPause,
                            onSpotifyNext = vm::spotifyNext,
                            onSpotifyPrev = vm::spotifyPrev,
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
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xEE111115))
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
                onSetAccent = vm::setAccentPreset,
                onSetAppLang = vm::setAppLang,
                onSetVoiceLang = vm::setVoiceLang,
                onAdjustFuelDelta = vm.locationTracker::adjustFuelDelta,
                onFillTank = vm.locationTracker::fillTank,
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
    val effectiveMode = if (!isLandscape && ui.layoutMode != LayoutMode.DRIVE_FOCUS && ui.layoutMode != LayoutMode.COMPACT_FIXED) {
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
            onSelectPlace = vm::navigateToPlace,
            onSelectRoute = vm::selectRouteOption,
            onClearRoute = vm::clearActiveRoute,
            onSetMapMode = vm::setMapVisualMode,
            onToggleTilt3d = vm::toggleTilt3d,
            onSetFollow = vm::setFollowCamera,
            onToggleDemoDrive = vm.locationTracker::toggleDemoDrive,
            onResetTripA = vm.locationTracker::resetTripA,
            onAdjustFuelDelta = vm.locationTracker::adjustFuelDelta,
            onFillTank = vm.locationTracker::fillTank,
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
            onToggleLocalPlay = vm.localAudio::togglePlayPause,
            onLocalNext = vm.localAudio::next,
            onLocalPrev = vm.localAudio::prev,
            onLocalSeek = vm.localAudio::seekTo,
            onLocalSelectIndex = vm.localAudio::playLocalIndex,
            onRequestAudioPerm = onRequestAudio,
            onScanLocal = { vm.onPermissionsUpdated() },
            onConnectSpotify = onLaunchSpotifyAuth,
            onSearchSpotify = vm::searchSpotify,
            onPlaySpotifyTrack = vm::playSpotifyTrack,
            onSpotifyToggle = vm::spotifyTogglePlayPause,
            onSpotifyNext = vm::spotifyNext,
            onSpotifyPrev = vm::spotifyPrev,
            onOpenLayoutSettings = vm::toggleLayoutDrawer,
            modifier = mod,
        )
    }

    when (effectiveMode) {
        LayoutMode.COMPACT_FIXED -> {
            // Fixed, non-sticky horizontal CarPlay layout: 3D Map + fixed 48dp compact media/weather/calendar dock
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(1f).fillMaxWidth(), true)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f).clickable { vm.selectTab(CarTab.MUSIC) },
                    ) {
                        Icon(CarIcons.Music, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                        Text(
                            text = localMusic.currentTitle.ifBlank { ui.spotifyState.track?.title ?: s.navMusic },
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(CarIcons.SkipPrev, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { vm.localAudio.prev() })
                    Icon(
                        if (localMusic.isPlaying) CarIcons.Pause else CarIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp).clickable { vm.localAudio.togglePlayPause() },
                    )
                    Icon(CarIcons.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp).clickable { vm.localAudio.next() })

                    Box(modifier = Modifier.width(0.75.dp).height(24.dp).background(HairlineBorder))

                    val temp = ui.weather?.tempC?.roundToInt()?.let { "$it°C" } ?: "--°"
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable(onClick = vm::toggleWeatherDrawer),
                    ) {
                        Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                        Text(temp, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                    }

                    Box(modifier = Modifier.width(0.75.dp).height(24.dp).background(HairlineBorder))

                    val nextEv = ui.calendarEvents.firstOrNull()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable(onClick = vm::toggleCalendarDrawer),
                    ) {
                        Icon(CarIcons.Calendar, contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text(nextEv?.timeLabel ?: s.calendarTitle, color = MutedText, fontSize = 11.sp, maxLines = 1)
                    }
                }
            }
        }
        LayoutMode.DRIVE_FOCUS -> {
            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                mapBlock(Modifier.weight(1f).fillMaxWidth(), false)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(CardBlack)
                        .border(0.75.dp, HairlineBorder, RoundedCornerShape(12.dp))
                        .clickable { vm.selectTab(CarTab.MUSIC) }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                        Icon(CarIcons.Music, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text(
                            text = localMusic.currentTitle.ifBlank { ui.spotifyState.track?.title ?: s.navMusic },
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(CarIcons.SkipPrev, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp).clickable { vm.localAudio.prev() })
                        Icon(
                            if (localMusic.isPlaying) CarIcons.Pause else CarIcons.Play,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(23.dp).clickable { vm.localAudio.togglePlayPause() },
                        )
                        Icon(CarIcons.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(19.dp).clickable { vm.localAudio.next() })
                    }
                }
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
