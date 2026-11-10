package com.librecar.rust.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.librecar.rust.CarUiState
import com.librecar.rust.data.LayoutMode
import com.librecar.rust.data.TaskbarPosition
import com.librecar.rust.data.TouchScale
import com.librecar.rust.data.VoiceUiState
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun OverlayShell(
    title: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xB8000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(22.dp))
                .background(CardBlack)
                .border(1.dp, HairlineBorder, RoundedCornerShape(22.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(34.dp).clip(CircleShape).background(ElevatedBlack)) {
                    Icon(CarIcons.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
            content()
        }
    }
}

@Composable
fun WeatherDrawer(
    ui: CarUiState,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    val w = ui.weather
    OverlayShell(title = s.weatherTitle, onClose = onClose) {
        if (w == null) {
            Text(
                text = if (s.isPolish) "Pobieranie pogody z Open-Meteo..." else "Loading live Open-Meteo weather...",
                color = Color(0xFF9CA3AF),
                fontSize = 13.sp,
            )
            ChipBtn(text = "Refresh", selected = true, onClick = onRefresh)
        } else {
            val (condLabel, roadTip) = describeWeather(w.weatherCode, w.tempC, w.windKmh, s.isPolish)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color(0xFFFFD54F), modifier = Modifier.size(40.dp))
                    Column {
                        Text("${w.tempC.roundToInt()}°C • $condLabel", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp)
                        Text("${s.feelsLike}: ${w.apparentTempC.roundToInt()}°C • ${s.windLabel}: ${w.windKmh.roundToInt()} km/h • ${s.rainLabel}: ${w.precipProb}%", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                    }
                }
                ChipBtn(text = "↻", selected = false, onClick = onRefresh)
            }

            // Road Safety Advisory
            Text(
                text = roadTip,
                color = EcoGreen,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(EcoGreen.copy(alpha = 0.12f))
                    .padding(10.dp),
            )

            // 8-hour strip
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                w.hourly.forEach { h ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(h.timeLabel, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                        Text("${h.tempC.roundToInt()}°", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${h.rainProb}%", color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun CalendarDrawer(
    ui: CarUiState,
    hasCalendarPerm: Boolean,
    onRequestCalendarPerm: () -> Unit,
    onAddReminder: (String, String, String) -> Unit,
    onDeleteReminder: (Long) -> Unit,
    onDriveToLocation: (String) -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    var titleInput by remember { mutableStateOf("") }
    var locInput by remember { mutableStateOf("") }
    var timeInput by remember { mutableStateOf("18:00") }
    val dateHeader = remember { SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date()) }

    OverlayShell(title = s.calendarTitle, onClose = onClose) {
        Text(dateHeader, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 14.sp)

        if (!hasCalendarPerm) {
            ChipBtn(text = s.grantCalendarPerm, selected = true, onClick = onRequestCalendarPerm)
        }

        // Quick Local Trip Reminder Form
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CompactField(value = titleInput, hint = s.eventTitleHint, onValue = { titleInput = it }, modifier = Modifier.weight(1f))
            CompactField(value = locInput, hint = s.eventLocHint, onValue = { locInput = it }, modifier = Modifier.weight(1f))
            CompactField(value = timeInput, hint = "18:00", onValue = { timeInput = it }, modifier = Modifier.width(64.dp))
            ChipBtn(
                text = "+",
                selected = true,
                onClick = {
                    if (titleInput.isNotBlank()) {
                        onAddReminder(titleInput, locInput, timeInput)
                        titleInput = ""
                        locInput = ""
                    }
                },
            )
        }

        if (ui.calendarEvents.isEmpty()) {
            Text(s.noEvents, color = Color(0xFF9CA3AF), fontSize = 13.sp)
        } else {
            LazyColumn(
                modifier = Modifier.height(210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(ui.calendarEvents) { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ev.timeLabel} • ${ev.title}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            if (ev.location.isNotBlank()) {
                                Text(ev.location, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            if (ev.location.isNotBlank()) {
                                ChipBtn(text = s.driveToEvent, selected = true, onClick = { onDriveToLocation(ev.location) })
                            }
                            if (ev.isLocalReminder) {
                                ChipBtn(text = "✕", selected = false, onClick = { onDeleteReminder(ev.id) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LayoutSettingsDrawer(
    ui: CarUiState,
    onSetLayoutMode: (LayoutMode) -> Unit,
    onSetTaskbarPos: (TaskbarPosition) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSetTouchScale: (TouchScale) -> Unit,
    onSetLowEndMode: (Boolean) -> Unit,
    onSetTerrain3d: (Boolean) -> Unit,
    onSetAccent: (AccentPreset) -> Unit,
    onSetAppLang: (AppLang) -> Unit,
    onSetVoiceLang: (AppLang) -> Unit,
    onSaveSpotifyId: (String) -> Unit,
    onConnectSpotify: () -> Unit,
    onDisconnectSpotify: () -> Unit,
    onResetTripA: () -> Unit,
    onResetTripB: () -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    var spotifyIdBox by remember(ui.spotifyClientId) { mutableStateOf(ui.spotifyClientId) }

    OverlayShell(title = s.layoutSettingsTitle, onClose = onClose) {
        Column(
            modifier = Modifier
                .height(340.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Rust Native + Zero Tracker Status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(ElevatedBlack)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (RustEngine.isNativeLoaded) s.rustStatusReady else s.rustStatusFallback,
                        color = if (RustEngine.isNativeLoaded) EcoGreen else Color(0xFFFFAB40),
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                    )
                    Text(s.privacyBadge, color = Color(0xFF9CA3AF), fontSize = 10.sp)
                }
            }

            // Layout Mode
            SettingGroupTitle(s.layoutModeLabel)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LayoutMode.entries.forEach { mode ->
                    ChipBtn(
                        text = if (s.isPolish) mode.labelPl else mode.labelEn,
                        selected = ui.layoutMode == mode,
                        onClick = { onSetLayoutMode(mode) },
                    )
                }
            }

            // Taskbar Position
            SettingGroupTitle(s.taskbarPosLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TaskbarPosition.entries.forEach { pos ->
                    ChipBtn(
                        text = if (s.isPolish) pos.labelPl else pos.labelEn,
                        selected = ui.taskbarPosition == pos,
                        onClick = { onSetTaskbarPos(pos) },
                    )
                }
            }

            // Map Split Ratio
            SettingGroupTitle("${s.splitRatioLabel}: ${(ui.splitRatio * 100).roundToInt()}%")
            Slider(
                value = ui.splitRatio,
                onValueChange = onSetSplitRatio,
                valueRange = 0.45f..0.75f,
                colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary),
            )

            // Touch Scale
            SettingGroupTitle(s.touchScaleLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TouchScale.entries.forEach { sc ->
                    ChipBtn(
                        text = if (s.isPolish) sc.labelPl else sc.labelEn,
                        selected = ui.touchScale == sc,
                        onClick = { onSetTouchScale(sc) },
                    )
                }
            }

            // Low-End Mode & 3D Terrain switches
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.lowEndModeLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text(s.lowEndModeDesc, color = Color(0xFF9CA3AF), fontSize = 11.sp)
                }
                Switch(checked = ui.lowEndMode, onCheckedChange = onSetLowEndMode)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.terrain3dLabel, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                Switch(checked = ui.terrain3dEnabled, onCheckedChange = onSetTerrain3d)
            }

            // Material You Black Accent
            SettingGroupTitle(s.accentThemeLabel)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AccentPreset.entries.forEach { preset ->
                    ChipBtn(
                        text = if (s.isPolish) preset.labelPl else preset.labelEn,
                        selected = ui.accentPreset == preset,
                        onClick = { onSetAccent(preset) },
                    )
                }
            }

            // UI Language & Voice Language
            SettingGroupTitle(s.languageLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLang.entries.forEach { l ->
                    ChipBtn(text = l.label, selected = ui.appLang == l, onClick = { onSetAppLang(l) })
                }
            }

            SettingGroupTitle(s.voiceLangLabel)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AppLang.entries.forEach { l ->
                    ChipBtn(text = l.label, selected = ui.voiceLang == l, onClick = { onSetVoiceLang(l) })
                }
            }

            // Spotify API PKCE Setup
            SettingGroupTitle(s.spotifyClientIdLabel)
            Text(s.spotifySetupHint, color = Color(0xFF9CA3AF), fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactField(
                    value = spotifyIdBox,
                    hint = "Spotify Client ID",
                    onValue = {
                        spotifyIdBox = it
                        onSaveSpotifyId(it)
                    },
                    modifier = Modifier.weight(1f),
                )
                if (ui.spotifyConnected) {
                    ChipBtn(text = "Disconnect", selected = false, onClick = onDisconnectSpotify)
                } else {
                    ChipBtn(text = s.connectSpotify, selected = true, onClick = onConnectSpotify)
                }
            }

            // Trip Reset Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipBtn(text = "${s.resetTrip} ${s.tripA}", selected = false, onClick = onResetTripA)
                ChipBtn(text = "${s.resetTrip} ${s.tripB}", selected = false, onClick = onResetTripB)
            }
        }
    }
}

@Composable
fun VoiceOverlay(
    voiceState: VoiceUiState,
    onStartListening: () -> Unit,
    onSubmitTextCommand: (String) -> Unit,
    onClose: () -> Unit,
) {
    val s = LocalCarStrings.current
    var manualText by remember { mutableStateOf("") }

    OverlayShell(title = s.voiceTitle, onClose = onClose) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(if (voiceState.isListening) EcoGreen else MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onStartListening),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(34.dp))
            }

            Text(
                text = if (voiceState.isListening) s.voiceListening else s.voiceTapMic,
                color = if (voiceState.isListening) EcoGreen else Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
            )

            if (voiceState.partialText.isNotBlank()) {
                Text("\"${voiceState.partialText}\"", color = MaterialTheme.colorScheme.secondary, fontSize = 14.sp)
            }
            voiceState.errorMsg?.let { err ->
                Text(err, color = Color(0xFFFFAB40), fontSize = 11.sp)
            }

            Text(s.voiceHint, color = Color(0xFF9CA3AF), fontSize = 11.sp)

            // Quick voice command chips (EN + PL)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.voiceExamples.forEach { ex ->
                    ChipBtn(text = ex, selected = false, onClick = { onSubmitTextCommand(ex) })
                }
            }

            // Text fallback for noisy cabins / AOSP head units
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactField(value = manualText, hint = s.voiceTapMic, onValue = { manualText = it }, modifier = Modifier.weight(1f))
                ChipBtn(
                    text = s.go,
                    selected = true,
                    onClick = { if (manualText.isNotBlank()) onSubmitTextCommand(manualText) },
                )
            }
        }
    }
}

@Composable
private fun SettingGroupTitle(text: String) {
    Text(text, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
}

@Composable
private fun ChipBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else ElevatedBlack)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary else HairlineBorder, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 7.dp),
    )
}

@Composable
private fun CompactField(value: String, hint: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(ElevatedBlack)
            .border(1.dp, HairlineBorder, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) {
            Text(hint, color = Color(0xFF6B7280), fontSize = 12.sp, maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun describeWeather(code: Int, tempC: Double, windKmh: Double, isPl: Boolean): Pair<String, String> {
    val label = when (code) {
        0 -> if (isPl) "Bezchmurnie" else "Clear Sky"
        1, 2, 3 -> if (isPl) "Zachmurzenie" else "Partly Cloudy"
        45, 48 -> if (isPl) "Mgła" else "Fog"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> if (isPl) "Deszcz" else "Rain"
        71, 73, 75, 85, 86 -> if (isPl) "Śnieg" else "Snow"
        95, 96, 99 -> if (isPl) "Burza" else "Thunderstorm"
        else -> if (isPl) "Zmienna" else "Variable"
    }
    val tip = when {
        tempC <= 1.5 -> if (isPl) "Uwaga na gołoledź — jedź płynnie i zachowaj dystans" else "Freezing risk — watch for black ice & brake smoothly"
        code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) ->
            if (isPl) "Mokra nawierzchnia — wydłuż odstęp i unikaj gwałtownego hamowania" else "Wet roads — increase following distance & coast smoothly"
        code in listOf(45, 48) ->
            if (isPl) "Ograniczona widoczność (mgła) — włącz światła mijania/przeciwmgłowe" else "Low visibility (fog) — reduce speed & keep lights on"
        windKmh > 45 ->
            if (isPl) "Silny wiatr boczny — trzymaj kierownicę pewnie" else "Strong crosswinds — stay steady in lane"
        else ->
            if (isPl) "Dobre warunki do jazdy eko — utrzymuj stałą prędkość" else "Good eco-driving conditions — cruise smoothly to save fuel"
    }
    return label to tip
}
