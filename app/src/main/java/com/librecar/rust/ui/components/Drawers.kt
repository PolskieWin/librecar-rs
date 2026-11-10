package com.librecar.rust.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.librecar.rust.nativeengine.DriveStats
import com.librecar.rust.nativeengine.RustEngine
import com.librecar.rust.ui.theme.AccentPreset
import com.librecar.rust.ui.theme.AppLang
import com.librecar.rust.ui.theme.CardBlack
import com.librecar.rust.ui.theme.CarIcons
import com.librecar.rust.ui.theme.EcoAmber
import com.librecar.rust.ui.theme.EcoGreen
import com.librecar.rust.ui.theme.ElevatedBlack
import com.librecar.rust.ui.theme.HairlineBorder
import com.librecar.rust.ui.theme.LocalCarStrings
import com.librecar.rust.ui.theme.MutedText
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
    BackHandler(onBack = onClose)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xBD000000))
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .clip(RoundedCornerShape(18.dp))
                .background(CardBlack)
                .border(0.75.dp, HairlineBorder, RoundedCornerShape(18.dp))
                .clickable(enabled = false) {}
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                IconButton(onClick = onClose, modifier = Modifier.size(32.dp).clip(CircleShape).background(ElevatedBlack)) {
                    Icon(CarIcons.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(16.dp))
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
                text = if (s.isPolish) "Pobieranie pogody z Open-Meteo..." else "Fetching Open-Meteo weather...",
                color = MutedText,
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
                    Icon(CarIcons.WeatherSun, contentDescription = null, tint = Color.White, modifier = Modifier.size(36.dp))
                    Column {
                        Text("${w.tempC.roundToInt()}°C · $condLabel", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                        Text("${s.feelsLike} ${w.apparentTempC.roundToInt()}°C · ${s.windLabel} ${w.windKmh.roundToInt()} km/h · ${s.rainLabel} ${w.precipProb}%", color = MutedText, fontSize = 12.sp)
                    }
                }
                ChipBtn(text = "Refresh", selected = false, onClick = onRefresh)
            }

            Text(
                text = roadTip,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .padding(10.dp),
            )

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                w.hourly.forEach { h ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(h.timeLabel, color = MutedText, fontSize = 11.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                        Text("${h.tempC.roundToInt()}°", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, style = TextStyle(fontFeatureSettings = "tnum"))
                        Text("${h.rainProb}%", color = MutedText, fontSize = 10.sp)
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
        Text(dateHeader, color = MutedText, fontWeight = FontWeight.Medium, fontSize = 13.sp)

        if (!hasCalendarPerm) {
            ChipBtn(text = s.grantCalendarPerm, selected = true, onClick = onRequestCalendarPerm)
        }

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
            Text(s.noEvents, color = MutedText, fontSize = 13.sp)
        } else {
            LazyColumn(
                modifier = Modifier.height(210.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(ui.calendarEvents) { ev ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ElevatedBlack)
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${ev.timeLabel} · ${ev.title}", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                            if (ev.location.isNotBlank()) {
                                Text(ev.location, color = MutedText, fontSize = 11.sp)
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
    stats: DriveStats,
    onSetLayoutMode: (LayoutMode) -> Unit,
    onSetTaskbarPos: (TaskbarPosition) -> Unit,
    onSetSplitRatio: (Float) -> Unit,
    onSetTouchScale: (TouchScale) -> Unit,
    onSetLowEndMode: (Boolean) -> Unit,
    onSetTerrain3d: (Boolean) -> Unit,
    onSetAccent: (AccentPreset) -> Unit,
    onSetAppLang: (AppLang) -> Unit,
    onSetVoiceLang: (AppLang) -> Unit,
    onAdjustFuelDelta: (Double) -> Unit,
    onFillTank: () -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(ElevatedBlack)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (RustEngine.isNativeLoaded) s.rustStatusReady else s.rustStatusFallback,
                        color = if (RustEngine.isNativeLoaded) EcoGreen else EcoAmber,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                    Text(s.privacyBadge, color = MutedText, fontSize = 10.sp)
                }
            }

            // Fuel Level & Range Calibration
            SettingGroupTitle("${s.fuelLevelLabel}: ${stats.fuelLiters} / ${stats.tankCapacityLiters} L (${s.fuelRangeLabel} ~${stats.estimatedRangeKm.roundToInt()} km)")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChipBtn(text = "-5 L", selected = false, onClick = { onAdjustFuelDelta(-5.0) })
                ChipBtn(text = "+5 L", selected = false, onClick = { onAdjustFuelDelta(5.0) })
                ChipBtn(text = s.fillTankLabel, selected = true, onClick = onFillTank)
            }

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

            SettingGroupTitle("${s.splitRatioLabel}: ${(ui.splitRatio * 100).roundToInt()}%")
            Slider(
                value = ui.splitRatio,
                onValueChange = onSetSplitRatio,
                valueRange = 0.45f..0.75f,
                colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White),
            )

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

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(s.lowEndModeLabel, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                    Text(s.lowEndModeDesc, color = MutedText, fontSize = 11.sp)
                }
                Switch(checked = ui.lowEndMode, onCheckedChange = onSetLowEndMode)
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(s.terrain3dLabel, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                Switch(checked = ui.terrain3dEnabled, onCheckedChange = onSetTerrain3d)
            }

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

            SettingGroupTitle(s.spotifyClientIdLabel)
            Text(s.spotifySetupHint, color = MutedText, fontSize = 11.sp)
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
                    .size(66.dp)
                    .clip(CircleShape)
                    .background(if (voiceState.isListening) EcoGreen else Color.White)
                    .clickable(onClick = onStartListening),
                contentAlignment = Alignment.Center,
            ) {
                Icon(CarIcons.Mic, contentDescription = null, tint = Color.Black, modifier = Modifier.size(30.dp))
            }

            Text(
                text = if (voiceState.isListening) s.voiceListening else s.voiceTapMic,
                color = if (voiceState.isListening) EcoGreen else Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
            )

            if (voiceState.partialText.isNotBlank()) {
                Text("\"${voiceState.partialText}\"", color = Color.White, fontSize = 14.sp)
            }
            voiceState.errorMsg?.let { err ->
                Text(err, color = EcoAmber, fontSize = 11.sp)
            }

            Text(s.voiceHint, color = MutedText, fontSize = 11.sp)

            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                s.voiceExamples.forEach { ex ->
                    ChipBtn(text = ex, selected = false, onClick = { onSubmitTextCommand(ex) })
                }
            }

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
    Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
}

@Composable
private fun ChipBtn(text: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) Color.Black else Color.White,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (selected) Color.White else ElevatedBlack)
            .border(0.75.dp, if (selected) Color.White else HairlineBorder, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
    )
}

@Composable
private fun CompactField(value: String, hint: String, onValue: (String) -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(ElevatedBlack)
            .border(0.75.dp, HairlineBorder, RoundedCornerShape(9.dp))
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (value.isEmpty()) Text(hint, color = MutedText, fontSize = 12.sp, maxLines = 1)
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
            cursorBrush = SolidColor(Color.White),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun describeWeather(code: Int, tempC: Double, windKmh: Double, isPl: Boolean): Pair<String, String> {
    val label = when (code) {
        0 -> if (isPl) "Bezchmurnie" else "Clear"
        1, 2, 3 -> if (isPl) "Zachmurzenie" else "Cloudy"
        45, 48 -> if (isPl) "Mgła" else "Fog"
        51, 53, 55, 61, 63, 65, 80, 81, 82 -> if (isPl) "Deszcz" else "Rain"
        71, 73, 75, 85, 86 -> if (isPl) "Śnieg" else "Snow"
        95, 96, 99 -> if (isPl) "Burza" else "Storm"
        else -> if (isPl) "Zmienna" else "Variable"
    }
    val tip = when {
        tempC <= 1.5 -> if (isPl) "Ryzyko gołoledzi — zachowaj większy odstęp" else "Freezing risk — watch for black ice"
        code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) ->
            if (isPl) "Mokra nawierzchnia — wydłuż dystans hamowania" else "Wet roads — increase following distance"
        code in listOf(45, 48) ->
            if (isPl) "Ograniczona widoczność — włącz światła" else "Low visibility — reduce speed"
        windKmh > 45 ->
            if (isPl) "Silny wiatr boczny" else "Strong crosswinds"
        else ->
            if (isPl) "Dobre warunki drogowe" else "Clear driving conditions"
    }
    return label to tip
}
